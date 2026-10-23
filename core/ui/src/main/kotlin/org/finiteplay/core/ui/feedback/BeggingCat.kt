package org.finiteplay.core.ui.feedback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFF263238)
private val Cyan = Color(0xFF26C6DA)
private val Glow = Color(0xFFE0F7FA)
private val Pink = Color(0xFFF48FB1)
private val Sparkle = Color(0xFFAB47BC)

/**
 * A black cat with enormous, glistening eyes and its paws held together, begging: the picture on the "enjoying this
 * game?" question, next to the plea for feedback. Drawn here, flat, in the colours of the rest-reminder cat; decorative.
 */
@Composable
fun BeggingCat(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val u = size.height / 100f
        val cx = size.width / 2f
        fun p(x: Number, y: Number) = Offset(cx + x.toFloat() * u, y.toFloat() * u)
        fun oval(c: Offset, rx: Number, ry: Number, color: Color) {
            val a = rx.toFloat() * u
            val b = ry.toFloat() * u
            drawOval(color, Offset(c.x - a, c.y - b), Size(2 * a, 2 * b))
        }

        // Ears, with the cyan lining.
        for (s in listOf(-1f, 1f)) {
            drawPath(triangle(p(s * 12, 30), p(s * 41, 1.5f), p(s * 46, 40)), Cyan)
            drawPath(triangle(p(s * 14, 30), p(s * 40, 4), p(s * 44, 38)), Ink)
            drawPath(triangle(p(s * 24, 30), p(s * 37, 12), p(s * 39, 34)), Cyan)
        }
        // Head and shoulders, with a thin rim so the black cat shows on a dark dialog.
        oval(p(0, 52), 47.2f, 37.2f, Cyan)
        oval(p(0, 98), 35.2f, 19.2f, Cyan)
        oval(p(0f, 52), 46f, 36f, Ink)
        oval(p(0f, 98), 34f, 18f, Ink)
        // Eyes: wide, glossy, with two highlights each.
        for (s in listOf(-1f, 1f)) {
            val eye = p(s * 20, 48)
            oval(eye, 15f, 17f, Glow)
            oval(eye, 11.5f, 14f, Ink)
            oval(eye, 11.5f, 14f, Cyan.copy(alpha = 0.35f))
            oval(Offset(eye.x - 3 * u, eye.y + 1 * u), 8f, 10.5f, Ink)
            oval(Offset(eye.x - 4 * u, eye.y - 6 * u), 4.6f, 5f, Color.White)
            oval(Offset(eye.x + 4 * u, eye.y + 6 * u), 2.2f, 2.4f, Color.White)
            // Brow raised in the middle, sad.
            val brow = Path().apply {
                moveTo(cx + s * 33 * u, 28 * u)
                lineTo(cx + s * 9 * u, 20 * u)
            }
            drawPath(brow, Cyan, style = Stroke(width = 2.4f * u, cap = StrokeCap.Round))
        }
        // Nose and a small wobbly mouth.
        drawPath(triangle(p(-4f, 62), p(4f, 62), p(0f, 67)), Pink)
        val mouth = Path().apply {
            moveTo(cx, 67 * u)
            quadraticTo(cx - 2 * u, 73 * u, cx - 7 * u, 72 * u)
            moveTo(cx, 67 * u)
            quadraticTo(cx + 2 * u, 73 * u, cx + 7 * u, 72 * u)
        }
        drawPath(mouth, Cyan, style = Stroke(width = 1.6f * u, cap = StrokeCap.Round))
        // Paws held together in front.
        for (s in listOf(-1f, 1f)) oval(p(s * 7, 88), 9f, 7f, Ink)
        for (s in listOf(-1f, 1f)) oval(p(s * 7, 88), 9f, 7f, Cyan.copy(alpha = 0.25f))
        drawLine(Cyan, p(0f, 82), p(0f, 94), 1.4f * u, StrokeCap.Round)
        // A sparkle.
        sparkle(p(56, 22), 9f * u)
    }
}

private fun triangle(a: Offset, b: Offset, c: Offset) = Path().apply {
    moveTo(a.x, a.y)
    lineTo(b.x, b.y)
    lineTo(c.x, c.y)
    close()
}

private fun DrawScope.sparkle(center: Offset, r: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, Sparkle)
}
