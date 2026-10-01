package com.cyberpad.client.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cyberpad.client.ui.theme.CarbonCard
import com.cyberpad.client.ui.theme.NeonCyan
import kotlin.math.hypot
import kotlin.math.min

private const val DIRECTION_THRESHOLD = 0.38f
private const val DEAD_FRACTION = 0.09f

/** D-Pad 4 arah (+diagonal) bersudut terpotong. Satu Pointer ID, isolasi penuh. */
@Composable
fun CyberpunkDPad(
    modifier: Modifier = Modifier,
    accent: Color = NeonCyan,
    onChange: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit
) {
    var up by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    val callback by rememberUpdatedState(onChange)

    Box(
        modifier = modifier
            .drawBehind {
                val c = Offset(size.width / 2f, size.height / 2f)
                val half = min(size.width, size.height) / 2f
                val a = half * 0.33f      // setengah lebar lengan
                val l = half * 0.98f      // panjang lengan dari pusat
                val cut = a * 0.55f
                val stroke = 1.5.dp.toPx()

                val plus = plusPath(c, a, l, cut)
                drawPath(plus, CarbonCard)

                val pressedStates = booleanArrayOf(up, right, down, left)
                for (q in 0 until 4) {
                    if (pressedStates[q]) {
                        drawPath(armPath(c, a, l, cut, q), accent.copy(alpha = 0.85f))
                    }
                }
                neonStrokePath(plus, accent, stroke, if (up || down || left || right) 1.6f else 1f)

                for (q in 0 until 4) {
                    val tint = if (pressedStates[q]) Color(0xFF0D0E15) else accent.copy(alpha = 0.75f)
                    drawPath(arrowPath(c, l * 0.68f, a * 0.42f, q), tint)
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dead = min(size.width, size.height) * DEAD_FRACTION

                    fun apply(px: Float, py: Float) {
                        val dx = px - cx
                        val dy = py - cy
                        val dist = hypot(dx, dy)
                        var u = false
                        var d = false
                        var l = false
                        var r = false
                        if (dist > dead) {
                            val nx = dx / dist
                            val ny = dy / dist
                            r = nx > DIRECTION_THRESHOLD
                            l = nx < -DIRECTION_THRESHOLD
                            d = ny > DIRECTION_THRESHOLD
                            u = ny < -DIRECTION_THRESHOLD
                        }
                        if (u != up || d != down || l != left || r != right) {
                            up = u
                            down = d
                            left = l
                            right = r
                            callback(u, d, l, r)
                        }
                    }

                    try {
                        apply(first.position.x, first.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            apply(change.position.x, change.position.y)
                        }
                    } finally {
                        up = false
                        down = false
                        left = false
                        right = false
                        callback(false, false, false, false)
                    }
                }
            }
    )
}

/** Rotasi 90 derajat searah jarum jam (koordinat layar, y ke bawah) sebanyak q kali. */
private fun rotate(x: Float, y: Float, q: Int): Offset {
    var rx = x
    var ry = y
    repeat(q) {
        val nx = -ry
        val ny = rx
        rx = nx
        ry = ny
    }
    return Offset(rx, ry)
}

private fun Path.polygon(c: Offset, q: Int, vararg pts: Float) {
    var i = 0
    while (i < pts.size) {
        val p = rotate(pts[i], pts[i + 1], q)
        if (i == 0) moveTo(c.x + p.x, c.y + p.y) else lineTo(c.x + p.x, c.y + p.y)
        i += 2
    }
    close()
}

private fun armPath(c: Offset, a: Float, l: Float, cut: Float, q: Int): Path = Path().apply {
    polygon(
        c, q,
        -a, 0f, -a, -l + cut, -a + cut, -l, a - cut, -l, a, -l + cut, a, 0f
    )
}

private fun arrowPath(c: Offset, distance: Float, size: Float, q: Int): Path = Path().apply {
    polygon(c, q, 0f, -distance - size, -size, -distance + size, size, -distance + size)
}

private fun plusPath(c: Offset, a: Float, l: Float, cut: Float): Path = Path().apply {
    val pts = floatArrayOf(
        -a, -l + cut, -a + cut, -l, a - cut, -l, a, -l + cut, a, -a,
        l - cut, -a, l, -a + cut, l, a - cut, l - cut, a, a, a,
        a, l - cut, a - cut, l, -a + cut, l, -a, l - cut, -a, a,
        -l + cut, a, -l, a - cut, -l, -a + cut, -l + cut, -a, -a, -a
    )
    var i = 0
    while (i < pts.size) {
        if (i == 0) moveTo(c.x + pts[i], c.y + pts[i + 1]) else lineTo(c.x + pts[i], c.y + pts[i + 1])
        i += 2
    }
    close()
}
