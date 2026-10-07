package org.finiteplay.core.ui.chips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/** One run of identical chips in a stack: [count] chips worth [denomination] each. */
data class ChipGroup(val denomination: Int, val count: Int)

/** The denominations an amount is drawn in, largest first; the smaller ones keep any remainder honest. */
private val DENOMINATIONS = intArrayOf(1_000, 500, 100, 50, 10, 5, 1)

/**
 * [amount] as the fewest chips of the largest denominations, largest first — what a player would
 * see pushed forward. Never more than twelve chips for an amount that is a multiple of ten up to
 * two thousand, so a stack stays small enough to draw. No amount is no chips.
 */
fun chipStackOf(amount: Int): List<ChipGroup> {
    if (amount <= 0) return emptyList()
    var left = amount
    val groups = ArrayList<ChipGroup>()
    for (denomination in DENOMINATIONS) {
        val count = left / denomination
        if (count > 0) groups += ChipGroup(denomination, count)
        left -= count * denomination
    }
    return groups
}

/** A casino colour per denomination; the value is also printed beside every stack, so colour is never the only cue. */
private fun chipColor(denomination: Int): Color = when (denomination) {
    1_000 -> Color(0xFFF2B600)
    500 -> Color(0xFF7E57C2)
    100 -> Color(0xFF455A64)
    50 -> Color(0xFFD13B3B)
    10 -> Color(0xFF1E78D2)
    5 -> Color(0xFFE5779E)
    else -> Color(0xFFECEFF1)
}

/**
 * Chips stacked by denomination, drawn from above: a face with a striped rim and a visible edge, one
 * column per denomination, side by side. Decorative — the amount is always stated in text next to it.
 */
@Composable
fun ChipStack(amount: Int, chipWidth: Dp, modifier: Modifier = Modifier) {
    val groups = chipStackOf(amount)
    if (groups.isEmpty()) return
    val faceHeight = chipWidth * 0.42f
    val edge = chipWidth * 0.14f
    val spacing = chipWidth * 0.82f
    val tallest = groups.maxOf { it.count }
    val width = chipWidth + spacing * (groups.size - 1)
    val height = faceHeight + edge * tallest + edge
    Canvas(modifier = modifier.size(width, height).clearAndSetSemantics { }) {
        val w = chipWidth.toPx()
        val h = faceHeight.toPx()
        val thickness = edge.toPx()
        groups.forEachIndexed { column, group ->
            val face = chipColor(group.denomination)
            val side = Color(
                red = face.red * 0.62f,
                green = face.green * 0.62f,
                blue = face.blue * 0.62f,
            )
            val rim = if (face.luminance() > 0.7f) Color(0xFF455A64) else Color.White
            val left = column * spacing.toPx()
            val baseTop = size.height - h - thickness
            for (k in 0 until group.count) {
                val top = baseTop - k * thickness
                drawOval(side, Offset(left, top + thickness), Size(w, h))
                drawRect(side, Offset(left, top + h / 2f), Size(w, thickness))
                drawOval(face, Offset(left, top), Size(w, h))
                // A light outline, so a dark chip does not vanish into the felt or a dark pill.
                drawOval(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = w * 0.06f),
                )
                val inset = w * 0.12f
                for (stripe in 0 until 6) {
                    drawArc(
                        color = rim,
                        startAngle = stripe * 60f,
                        sweepAngle = 24f,
                        useCenter = false,
                        topLeft = Offset(left + inset, top + inset * 0.42f),
                        size = Size(w - 2 * inset, h - 2 * inset * 0.42f),
                        style = Stroke(width = w * 0.07f),
                    )
                }
            }
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
