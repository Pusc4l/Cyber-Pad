package com.cyberpad.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberpad.client.input.ButtonsHigh
import com.cyberpad.client.input.ButtonsLow
import com.cyberpad.client.input.GamepadState
import com.cyberpad.client.network.ControllerSession
import com.cyberpad.client.network.PadMode
import com.cyberpad.client.network.UdpClientManager
import com.cyberpad.client.ui.components.CyberpunkButton
import com.cyberpad.client.ui.components.CyberpunkDPad
import com.cyberpad.client.ui.components.CyberpunkJoystick
import com.cyberpad.client.ui.components.CyberpunkTrigger
import com.cyberpad.client.ui.components.cutCornerPath
import com.cyberpad.client.ui.components.cyberGrid
import com.cyberpad.client.ui.components.neonStrokePath
import com.cyberpad.client.ui.theme.CarbonCard
import com.cyberpad.client.ui.theme.CyberFont
import com.cyberpad.client.ui.theme.CyberYellow
import com.cyberpad.client.ui.theme.ElectricPink
import com.cyberpad.client.ui.theme.NeonCyan
import com.cyberpad.client.ui.theme.ObsidianDark
import com.cyberpad.client.ui.theme.TextMuted
import kotlinx.coroutines.delay

/**
 * Layar controller penuh. Siklus hidup jaringan terikat ke lifecycle: ON_START memulai sesi UDP,
 * ON_STOP menghentikannya (frame netral dikirim agar tidak ada tombol "nyangkut" di PC).
 */
@Composable
fun ControllerScreen(
    session: ControllerSession,
    gamepad: GamepadState,
    udp: UdpClientManager,
    onDisconnect: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val pingMs by udp.pingMs.collectAsStateWithLifecycle()
    val status by udp.status.collectAsStateWithLifecycle()

    BackHandler(onBack = onDisconnect)

    DisposableEffect(lifecycleOwner, session) {
        gamepad.reset()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> udp.start(session.info, session.playerId)
                Lifecycle.Event.ON_STOP -> {
                    udp.stop()
                    gamepad.reset()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            udp.stop()
            gamepad.reset()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark)
            .cyberGrid()
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(session.playerId, pingMs, status, gamepad)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LeftCluster(Modifier.weight(1f).fillMaxHeight(), gamepad)
                CenterConsole(Modifier.weight(0.62f).fillMaxHeight(), session, gamepad, onDisconnect)
                RightCluster(Modifier.weight(1f).fillMaxHeight(), session.info.mode, gamepad)
            }
        }
    }
}

@Composable
private fun TopBar(
    playerId: Int,
    pingMs: Int,
    status: UdpClientManager.LinkStatus,
    gamepad: GamepadState
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CyberpunkTrigger("LT", Modifier.weight(0.9f).fillMaxHeight(), ElectricPink, 12.dp) {
            gamepad.setTriggerLeft(it)
        }
        CyberpunkButton("LB", Modifier.weight(1.3f).fillMaxHeight(), NeonCyan, 14.dp, 16.sp) {
            gamepad.setLow(ButtonsLow.LB, it)
        }
        TelemetryHud(playerId, pingMs, status, Modifier.weight(2f))
        CyberpunkButton("RB", Modifier.weight(1.3f).fillMaxHeight(), NeonCyan, 14.dp, 16.sp) {
            gamepad.setLow(ButtonsLow.RB, it)
        }
        CyberpunkTrigger("RT", Modifier.weight(0.9f).fillMaxHeight(), ElectricPink, 12.dp) {
            gamepad.setTriggerRight(it)
        }
    }
}

@Composable
private fun TelemetryHud(
    playerId: Int,
    pingMs: Int,
    status: UdpClientManager.LinkStatus,
    modifier: Modifier
) {
    val linkColor = if (status == UdpClientManager.LinkStatus.ERROR) ElectricPink else CyberYellow
    val linkText = when {
        status == UdpClientManager.LinkStatus.ERROR -> "NO LINK"
        pingMs >= 0 -> "${pingMs} MS"
        else -> "PING --"
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudChip("P$playerId", CyberYellow)
        Spacer(Modifier.width(10.dp))
        HudChip(linkText, linkColor)
    }
}

@Composable
private fun HudChip(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .drawBehind {
                val path = cutCornerPath(0f, 0f, size.width, size.height, 7.dp.toPx())
                drawPath(path, CarbonCard)
                neonStrokePath(path, accent, 1.5.dp.toPx(), 0.9f)
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = accent,
            fontFamily = CyberFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

/** Kiri: stick kiri di pojok atas-kiri, D-Pad di pojok bawah-dalam (tata letak ala Xbox). */
@Composable
private fun LeftCluster(modifier: Modifier, gamepad: GamepadState) {
    BoxWithConstraints(modifier) {
        val stick: Dp = minOf(maxWidth * 0.56f, maxHeight * 0.60f)
        val dpad: Dp = minOf(maxWidth * 0.44f, maxHeight * 0.46f)
        CyberpunkJoystick(
            modifier = Modifier.align(Alignment.TopStart).size(stick),
            accent = NeonCyan,
            onChange = { x, y -> gamepad.setLeftStick(x, y) },
            onStickClick = { gamepad.setHigh(ButtonsHigh.L3, it) }
        )
        CyberpunkDPad(
            modifier = Modifier.align(Alignment.BottomEnd).size(dpad),
            accent = NeonCyan,
            onChange = { u, d, l, r -> gamepad.setDpad(u, d, l, r) }
        )
    }
}

/** Kanan: cluster A/B/X/Y di pojok atas-kanan, stick kanan di pojok bawah-dalam. */
@Composable
private fun RightCluster(modifier: Modifier, mode: PadMode, gamepad: GamepadState) {
    BoxWithConstraints(modifier) {
        val stick: Dp = minOf(maxWidth * 0.56f, maxHeight * 0.60f)
        val face: Dp = minOf(maxWidth * 0.56f, maxHeight * 0.60f)
        FaceCluster(Modifier.align(Alignment.TopEnd).size(face), mode, gamepad)
        CyberpunkJoystick(
            modifier = Modifier.align(Alignment.BottomStart).size(stick),
            accent = ElectricPink,
            onChange = { x, y -> gamepad.setRightStick(x, y) },
            onStickClick = { gamepad.setHigh(ButtonsHigh.R3, it) }
        )
    }
}

@Composable
private fun FaceCluster(modifier: Modifier, mode: PadMode, gamepad: GamepadState) {
    val ps = mode == PadMode.PS
    val labelA = if (ps) "\u2715" else "A"
    val labelB = if (ps) "\u25CB" else "B"
    val labelX = if (ps) "\u25A1" else "X"
    val labelY = if (ps) "\u25B3" else "Y"

    BoxWithConstraints(modifier) {
        val bs: Dp = minOf(maxWidth, maxHeight) * 0.32f
        CyberpunkButton(labelY, Modifier.align(Alignment.TopCenter).size(bs), ElectricPink, 9.dp, 20.sp) {
            gamepad.setLow(ButtonsLow.Y, it)
        }
        CyberpunkButton(labelA, Modifier.align(Alignment.BottomCenter).size(bs), NeonCyan, 9.dp, 20.sp) {
            gamepad.setLow(ButtonsLow.A, it)
        }
        CyberpunkButton(labelX, Modifier.align(Alignment.CenterStart).size(bs), NeonCyan, 9.dp, 20.sp) {
            gamepad.setLow(ButtonsLow.X, it)
        }
        CyberpunkButton(labelB, Modifier.align(Alignment.CenterEnd).size(bs), ElectricPink, 9.dp, 20.sp) {
            gamepad.setLow(ButtonsLow.B, it)
        }
    }
}

@Composable
private fun CenterConsole(
    modifier: Modifier,
    session: ControllerSession,
    gamepad: GamepadState,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${session.info.mode.name} // ${session.info.ip}:${session.info.port}",
            color = TextMuted,
            fontFamily = CyberFont,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CyberpunkButton("BACK", Modifier.weight(1f).height(40.dp), NeonCyan, 8.dp, 12.sp) {
                gamepad.setLow(ButtonsLow.BACK, it)
            }
            CyberpunkButton("START", Modifier.weight(1f).height(40.dp), NeonCyan, 8.dp, 12.sp) {
                gamepad.setLow(ButtonsLow.START, it)
            }
        }
        Spacer(Modifier.height(14.dp))
        ExitButton(Modifier.fillMaxWidth().height(36.dp), onDisconnect)
    }
}

/** Dua langkah (ketuk, lalu ketuk lagi dalam 2 detik) agar tidak terputus tak sengaja saat bermain. */
@Composable
private fun ExitButton(modifier: Modifier, onDisconnect: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(2000)
            armed = false
        }
    }
    CyberpunkButton(if (armed) "YAKIN?" else "KELUAR", modifier, ElectricPink, 8.dp, 12.sp) { pressed ->
        if (pressed) {
            if (armed) onDisconnect() else armed = true
        }
    }
}
