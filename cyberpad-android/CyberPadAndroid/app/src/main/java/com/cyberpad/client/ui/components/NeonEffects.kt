package com.cyberpad.client.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cyberpad.client.ui.theme.GridCyan
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Segi-delapan memanjang dengan keempat sudut terpotong (clipped corners). */
fun cutCornerPath(left: Float, top: Float, right: Float, bottom: Float, cut: Float): Path =
    Path().apply {
        moveTo(left + cut, top)
        lineTo(right - cut, top)
        lineTo(right, top + cut)
        lineTo(right, bottom - cut)
        lineTo(right - cut, bottom)
        lineTo(left + cut, bottom)
        lineTo(left, bottom - cut)
        lineTo(left, top + cut)
        close()
    }

/** Segi-delapan beraturan (cincin joystick). */
fun octagonPath(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 8) {
        val angle = (PI / 8.0) + i * (PI / 4.0)
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Garis neon: dua lapis halo transparan + inti solid. */
fun DrawScope.neonStrokePath(path: Path, color: Color, width: Float, glow: Float = 1f) {
    drawPath(path, color.copy(alpha = (0.10f * glow).coerceIn(0f, 1f)), style = Stroke(width * 6f, join = StrokeJoin.Round))
    drawPath(path, color.copy(alpha = (0.22f * glow).coerceIn(0f, 1f)), style = Stroke(width * 3.5f, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(width, join = StrokeJoin.Round))
}

fun DrawScope.neonCircle(center: Offset, radius: Float, color: Color, width: Float, glow: Float = 1f) {
    drawCircle(color.copy(alpha = (0.10f * glow).coerceIn(0f, 1f)), radius, center, style = Stroke(width * 6f))
    drawCircle(color.copy(alpha = (0.22f * glow).coerceIn(0f, 1f)), radius, center, style = Stroke(width * 3.5f))
    drawCircle(color, radius, center, style = Stroke(width))
}

/** Latar grid cyan 5% opasitas. */
fun Modifier.cyberGrid(spacing: Dp = 28.dp, color: Color = GridCyan): Modifier = drawBehind {
    val step = spacing.toPx()
    var x = 0f
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}
