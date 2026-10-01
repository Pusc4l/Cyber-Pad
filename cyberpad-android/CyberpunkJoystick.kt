package com.cyberpad.client.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cyberpad.client.ui.theme.CarbonCard
import com.cyberpad.client.ui.theme.NeonCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.min

private const val RING_FRACTION = 0.38f
private const val DEAD_ZONE = 0.06f
private const val TAP_MAX_MS = 200L
private const val TAP_SLOP_FRACTION = 0.25f
private const val DOUBLE_TAP_MS = 320L
private const val CLICK_PULSE_MS = 90L

/**
 * Joystick 360 derajat mengambang: cincin muncul di titik sentuh pertama (dijepit agar tetap
 * di dalam zona) dan resting-nya di tengah zona. Satu Pointer ID per joystick.
 *
 * onChange(x, y): -1..1 dengan y POSITIF = ATAS (konvensi XInput server).
 * Ketuk dua kali cepat pada zona = tekan stick (L3/R3) lewat onStickClick.
 */
@Composable
fun CyberpunkJoystick(
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    onChange: (x: Float, y: Float) -> Unit,
    onStickClick: (pressed: Boolean) -> Unit = {}
) {
    var active by remember { mutableStateOf(false) }
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    val changeCallback by rememberUpdatedState(onChange)
    val clickCallback by rememberUpdatedState(onStickClick)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .drawBehind {
                val ring = min(size.width, size.height) * RING_FRACTION
                val on = active
                val cx = if (on) originX else size.width / 2f
                val cy = if (on) originY else size.height / 2f
                val center = Offset(cx, cy)
                val glow = if (on) 1.4f else 0.7f
                val stroke = 1.5.dp.toPx()

                val outer = octagonPath(center, ring)
                drawPath(outer, CarbonCard.copy(alpha = if (on) 0.85f else 0.55f))
                neonStrokePath(outer, accent, stroke, glow)
                neonStrokePath(octagonPath(center, ring * 0.62f), accent.copy(alpha = 0.45f), stroke * 0.7f, 0.4f)
                drawLine(accent.copy(alpha = 0.25f), Offset(cx - ring, cy), Offset(cx + ring, cy), stroke * 0.6f)
                drawLine(accent.copy(alpha = 0.25f), Offset(cx, cy - ring), Offset(cx, cy + ring), stroke * 0.6f)

                val knob = Offset(cx + knobX, cy + knobY)
                val knobRadius = ring * 0.36f
                drawCircle(accent.copy(alpha = if (on) 0.35f else 0.18f), knobRadius, knob)
                neonCircle(knob, knobRadius, accent, stroke * 1.4f, glow)
                drawCircle(accent, ring * 0.08f, knob)
            }
            .pointerInput(Unit) {
                var lastTapUp = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val ring = min(w, h) * RING_FRACTION
                    val ox = down.position.x.coerceIn(ring, w - ring)
                    val oy = down.position.y.coerceIn(ring, h - ring)
                    originX = ox
                    originY = oy
                    knobX = 0f
                    knobY = 0f
                    active = true

                    var maxTravel = 0f
                    var tapped = false
                    var upTime = 0L

                    fun push(px: Float, py: Float) {
                        var dx = px - ox
                        var dy = py - oy
                        val len = hypot(dx, dy)
                        if (len > maxTravel) maxTravel = len
                        if (len > ring) {
                            val s = ring / len
                            dx *= s
                            dy *= s
                        }
                        knobX = dx
                        knobY = dy
                        var nx = dx / ring
                        var ny = dy / ring
                        val mag = hypot(nx, ny)
                        if (mag < DEAD_ZONE) {
                            nx = 0f
                            ny = 0f
                        } else {
                            val s = (mag - DEAD_ZONE) / (1f - DEAD_ZONE) / mag
                            nx *= s
                            ny *= s
                        }
                        changeCallback(nx, -ny)
                    }

                    try {
                        push(down.position.x, down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                upTime = change.uptimeMillis
                                tapped = (upTime - down.uptimeMillis) < TAP_MAX_MS &&
                                    maxTravel < ring * TAP_SLOP_FRACTION
                                break
                            }
                            push(change.position.x, change.position.y)
                        }
                    } finally {
                        active = false
                        knobX = 0f
                        knobY = 0f
                        changeCallback(0f, 0f)
                    }

                    if (tapped) {
                        if (lastTapUp != 0L && (upTime - lastTapUp) < DOUBLE_TAP_MS) {
                            lastTapUp = 0L
                            scope.launch {
                                clickCallback(true)
                                delay(CLICK_PULSE_MS)
                                clickCallback(false)
                            }
                        } else {
                            lastTapUp = upTime
                        }
                    }
                }
            }
    )
}
