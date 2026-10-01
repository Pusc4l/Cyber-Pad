package com.cyberpad.client.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberpad.client.ui.theme.CarbonCard
import com.cyberpad.client.ui.theme.CyberFont
import com.cyberpad.client.ui.theme.ElectricPink
import com.cyberpad.client.ui.theme.NeonCyan
import com.cyberpad.client.ui.theme.ObsidianDark
import kotlin.math.roundToInt

/**
 * Pelacak sentuhan terisolasi: mengikat HANYA satu Pointer ID (jari pertama yang menyentuh
 * komponen ini). Jari lain di komponen lain tidak pernah membatalkan atau mengganggu stream ini.
 */
internal suspend fun PointerInputScope.trackPress(onPress: (down: Boolean) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onPress(true)
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
            }
        } finally {
            onPress(false)
        }
    }
}

/** Tombol neon bersudut terpotong. Umpan balik visual langsung (tanpa animasi) saat sentuh. */
@Composable
fun CyberpunkButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    cut: Dp = 10.dp,
    fontSize: TextUnit = 18.sp,
    onPressedChange: (pressed: Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onPressedChange)
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .graphicsLayer {
                val s = if (pressed) 0.92f else 1f
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val path = cutCornerPath(0f, 0f, size.width, size.height, cut.toPx())
                drawPath(path, if (pressed) accent.copy(alpha = 0.85f) else CarbonCard)
                neonStrokePath(path, accent, 1.5.dp.toPx(), if (pressed) 1.8f else 1f)
            }
            .pointerInput(Unit) {
                trackPress { down ->
                    pressed = down
                    if (down) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    callback(down)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (pressed) ObsidianDark else accent,
            fontFamily = CyberFont,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize
        )
    }
}

/**
 * Pemicu analog LT/RT dengan indikator tekanan progresif. Level 1..15 ditentukan oleh posisi
 * vertikal jari di dalam komponen (atas = ringan, bawah = penuh); level 0 saat dilepas.
 */
@Composable
fun CyberpunkTrigger(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = ElectricPink,
    cut: Dp = 10.dp,
    onLevelChange: (level: Int) -> Unit
) {
    var level by remember { mutableIntStateOf(0) }
    val callback by rememberUpdatedState(onLevelChange)

    Box(
        modifier = modifier
            .drawBehind {
                val path = cutCornerPath(0f, 0f, size.width, size.height, cut.toPx())
                drawPath(path, CarbonCard)
                val fraction = level / 15f
                if (fraction > 0f) {
                    val top = size.height * (1f - fraction)
                    drawRect(
                        color = accent.copy(alpha = 0.55f),
                        topLeft = androidx.compose.ui.geometry.Offset(cut.toPx() * 0.4f, top),
                        size = androidx.compose.ui.geometry.Size(size.width - cut.toPx() * 0.8f, size.height - top)
                    )
                }
                neonStrokePath(path, accent, 1.5.dp.toPx(), if (level > 0) 1.8f else 1f)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun levelFor(y: Float): Int {
                        val f = (y / size.height.toFloat()).coerceIn(0f, 1f)
                        return 1 + (f * 14f).roundToInt()
                    }
                    try {
                        level = levelFor(down.position.y)
                        callback(level)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val next = levelFor(change.position.y)
                            if (next != level) {
                                level = next
                                callback(next)
                            }
                        }
                    } finally {
                        level = 0
                        callback(0)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (level > 0) Color.White else accent,
            fontFamily = CyberFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}
