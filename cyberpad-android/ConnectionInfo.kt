package com.cyberpad.client.network

import android.net.Uri

enum class PadMode { XBOX, PS }

data class ConnectionInfo(
    val ip: String,
    val port: Int,
    val mode: PadMode,
    val room: String?
)

data class ControllerSession(
    val info: ConnectionInfo,
    val playerId: Int
)

/** Parser payload QR: cyberpad://connect?ip=192.168.1.100&port=8888&mode=xbox&room=ABCD */
object QrPayloadParser {
    const val DEFAULT_PORT = 8888
    private const val SCHEME = "cyberpad"
    private const val HOST = "connect"

    fun parse(raw: String?): ConnectionInfo? {
        if (raw.isNullOrBlank()) return null
        val uri = try {
            Uri.parse(raw.trim())
        } catch (e: Exception) {
            return null
        }
        if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        val host = uri.host ?: uri.authority
        if (!HOST.equals(host, ignoreCase = true)) return null

        val ip = uri.getQueryParameter("ip")?.trim() ?: return null
        if (!isValidIpv4(ip)) return null

        val portParam = uri.getQueryParameter("port")
        val port = if (portParam == null) DEFAULT_PORT else (portParam.trim().toIntOrNull() ?: return null)
        if (port !in 1..65535) return null

        val mode = when (uri.getQueryParameter("mode")?.trim()?.lowercase()) {
            "ps", "ds4", "playstation" -> PadMode.PS
            else -> PadMode.XBOX
        }
        val room = uri.getQueryParameter("room")?.trim()?.takeIf { it.isNotEmpty() }
        return ConnectionInfo(ip, port, mode, room)
    }

    /** Input manual: "192.168.1.100" atau "192.168.1.100:8888". */
    fun fromManual(input: String): ConnectionInfo? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val host = text.substringBefore(':')
        if (!isValidIpv4(host)) return null
        val port = if (text.contains(':')) {
            text.substringAfter(':').toIntOrNull() ?: return null
        } else {
            DEFAULT_PORT
        }
        if (port !in 1..65535) return null
        return ConnectionInfo(host, port, PadMode.XBOX, null)
    }

    fun isValidIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { p ->
            p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } && p.toInt() in 0..255
        }
    }
}
