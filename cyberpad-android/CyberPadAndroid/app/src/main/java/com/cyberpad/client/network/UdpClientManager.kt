package com.cyberpad.client.network

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.cyberpad.client.input.GamepadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.locks.LockSupport

/** Encoder frame biner 12-byte (Little-Endian, tanpa padding). Nol alokasi. */
object GamepadFrame {
    const val SIZE = 12

    fun pack(state: GamepadState, playerId: Int, out: ByteArray) {
        out[0] = playerId.toByte()
        out[1] = state.buttonsLow.toByte()
        out[2] = state.buttonsHigh.toByte()
        putInt16(out, 3, state.thumbLX)
        putInt16(out, 5, state.thumbLY)
        putInt16(out, 7, state.thumbRX)
        putInt16(out, 9, state.thumbRY)
        out[11] = (((state.triggerR and 0x0F) shl 4) or (state.triggerL and 0x0F)).toByte()
    }

    fun packNeutral(playerId: Int, out: ByteArray) {
        out.fill(0)
        out[0] = playerId.toByte()
    }

    private fun putInt16(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value and 0xFF).toByte()
        out[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}

/**
 * Pengirim UDP 60 Hz. Loop kirim berjalan di Dispatchers.IO memakai satu ByteArray(12) dan satu
 * DatagramPacket yang dipakai ulang (tanpa alokasi di level aplikasi per frame). Frame dikirim
 * setiap tick walau state tidak berubah, sehingga sekaligus berfungsi sebagai heartbeat
 * (server mendaur-ulang slot setelah 3000 ms diam).
 *
 * Ping: hanya terukur jika server membalas (echo) datagram apa pun; jika tidak, pingMs = NO_PING.
 */
class UdpClientManager(context: Context, private val gamepad: GamepadState) {

    enum class LinkStatus { IDLE, LIVE, ERROR }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pingMs = MutableStateFlow(NO_PING)
    val pingMs: StateFlow<Int> = _pingMs.asStateFlow()

    private val _status = MutableStateFlow(LinkStatus.IDLE)
    val status: StateFlow<LinkStatus> = _status.asStateFlow()

    private val txBuffer = ByteArray(GamepadFrame.SIZE)
    private val txPacket = DatagramPacket(txBuffer, txBuffer.size)
    private val rxBuffer = ByteArray(RX_BUFFER_SIZE)
    private val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)

    @Volatile private var probeSentNanos = 0L
    @Volatile private var lastPingNanos = 0L

    private var job: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /** Panggil dari main thread. Aman dipanggil berulang; sesi lama dihentikan lebih dulu. */
    fun start(info: ConnectionInfo, playerId: Int) {
        require(playerId in 1..4) { "playerId harus 1..4" }
        val previous = job
        acquireWifiLock()
        _pingMs.value = NO_PING
        _status.value = LinkStatus.IDLE
        job = scope.launch {
            previous?.cancelAndJoin()
            runSession(info, playerId)
        }
    }

    /** Panggil dari main thread. Frame netral dikirim oleh coroutine sesi sebelum socket ditutup. */
    fun stop() {
        job?.cancel()
        job = null
        releaseWifiLock()
        _status.value = LinkStatus.IDLE
        _pingMs.value = NO_PING
    }

    fun shutdown() {
        stop()
        scope.cancel()
    }

    private fun CoroutineScope.runSession(info: ConnectionInfo, playerId: Int) {
        var socket: DatagramSocket? = null
        try {
            val address = InetAddress.getByName(info.ip)
            val s = DatagramSocket()
            socket = s
            s.soTimeout = RX_TIMEOUT_MS
            try {
                s.trafficClass = TRAFFIC_CLASS_LOW_DELAY
            } catch (e: Exception) {
                // Opsional: tidak semua perangkat mengizinkan.
            }
            s.connect(address, info.port)
            probeSentNanos = 0L
            lastPingNanos = 0L
            launch { receiveLoop(s) }
            sendLoop(s, playerId)
        } catch (e: IOException) {
            _status.value = LinkStatus.ERROR
        } catch (e: SecurityException) {
            _status.value = LinkStatus.ERROR
        } finally {
            socket?.let { s ->
                sendNeutral(s, playerId)
                s.close()
            }
        }
    }

    private fun CoroutineScope.sendLoop(socket: DatagramSocket, playerId: Int) {
        var nextTick = System.nanoTime()
        var probeCountdown = 0
        var consecutiveErrors = 0
        var live = false
        while (isActive) {
            GamepadFrame.pack(gamepad, playerId, txBuffer)
            try {
                socket.send(txPacket)
                consecutiveErrors = 0
                if (!live) {
                    live = true
                    _status.value = LinkStatus.LIVE
                }
            } catch (e: IOException) {
                consecutiveErrors++
                if (consecutiveErrors == ERROR_THRESHOLD) {
                    live = false
                    _status.value = LinkStatus.ERROR
                }
            }

            if (--probeCountdown <= 0) {
                probeCountdown = FRAMES_PER_PROBE
                armProbe()
            }

            nextTick += FRAME_NANOS
            if (nextTick - System.nanoTime() < -MAX_CATCH_UP_NANOS) nextTick = System.nanoTime()
            while (isActive) {
                val remaining = nextTick - System.nanoTime()
                if (remaining <= 0L) break
                LockSupport.parkNanos(remaining)
            }
        }
    }

    private fun CoroutineScope.receiveLoop(socket: DatagramSocket) {
        while (isActive && !socket.isClosed) {
            try {
                rxPacket.length = rxBuffer.size
                socket.receive(rxPacket)
                val sent = probeSentNanos
                if (sent != 0L) {
                    val now = System.nanoTime()
                    probeSentNanos = 0L
                    lastPingNanos = now
                    _pingMs.value = ((now - sent) / 1_000_000L).toInt().coerceAtLeast(0)
                }
            } catch (e: SocketTimeoutException) {
                // Normal: tidak ada balasan dalam jendela timeout.
            } catch (e: IOException) {
                if (socket.isClosed) return
                LockSupport.parkNanos(RX_ERROR_BACKOFF_NANOS)
            }
        }
    }

    private fun armProbe() {
        val now = System.nanoTime()
        if (_pingMs.value != NO_PING && now - lastPingNanos > PING_STALE_NANOS) {
            _pingMs.value = NO_PING
        }
        val sent = probeSentNanos
        if (sent == 0L || now - sent > PROBE_TIMEOUT_NANOS) probeSentNanos = now
    }

    private fun sendNeutral(socket: DatagramSocket, playerId: Int) {
        GamepadFrame.packNeutral(playerId, txBuffer)
        repeat(NEUTRAL_FRAMES) {
            try {
                socket.send(txPacket)
            } catch (e: IOException) {
                // Abaikan: koneksi memang sedang ditutup.
            }
            try {
                Thread.sleep(NEUTRAL_GAP_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        try {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            val lock = wifi.createWifiLock(lowLatencyMode(), "CyberPad:udp")
            lock.setReferenceCounted(false)
            lock.acquire()
            wifiLock = lock
        } catch (e: SecurityException) {
            wifiLock = null
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (e: RuntimeException) {
            // Lock sudah dilepas sistem.
        }
        wifiLock = null
    }

    @Suppress("DEPRECATION")
    private fun lowLatencyMode(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }

    companion object {
        const val NO_PING = -1
        private const val FRAME_NANOS = 1_000_000_000L / 60L
        private const val MAX_CATCH_UP_NANOS = FRAME_NANOS * 5L
        private const val FRAMES_PER_PROBE = 60
        private const val PROBE_TIMEOUT_NANOS = 1_000_000_000L
        private const val PING_STALE_NANOS = 3_000_000_000L
        private const val RX_TIMEOUT_MS = 250
        private const val RX_BUFFER_SIZE = 64
        private const val RX_ERROR_BACKOFF_NANOS = 50_000_000L
        private const val ERROR_THRESHOLD = 30
        private const val NEUTRAL_FRAMES = 3
        private const val NEUTRAL_GAP_MS = 5L
        private const val TRAFFIC_CLASS_LOW_DELAY = 0x10
    }
}
