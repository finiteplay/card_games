package org.finiteplay.holdem.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The bet amount as a thin slider across the sizing row (`UI_SPEC.md` "The sizing row"): drag or tap
 * anywhere along it to set the amount, from the smallest legal bet or raise to all in. Drawn by hand
 * because the platform slider insists on a 48 dp touch height and the row is 52 dp with the figure above.
 * The − and + buttons beside it still step by the big blind, and a screen reader adjusts it as a slider.
 */
@Composable
internal fun AmountSlider(
    value: Int,
    min: Int,
    max: Int,
    description: String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(onChange)
    val track = Color.White.copy(alpha = 0.28f)
    val filled = Color(0xFFFFD54F)
    val enabled = max > min
    fun at(x: Float, width: Float, pad: Float): Int {
        val fraction = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
        return (min + fraction * (max - min)).roundToInt().coerceIn(min, max)
    }
    Box(
        modifier = modifier
            .height(28.dp)
            .testTag("sizing_slider")
            .semantics {
                stateDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), min.toFloat()..max.toFloat().coerceAtLeast(min + 1f))
                if (enabled) setProgress { target -> current(target.roundToInt().coerceIn(min, max)); true }
            }
            .pointerInput(min, max) {
                if (!enabled) return@pointerInput
                val pad = 10.dp.toPx()
                detectTapGestures { current(at(it.x, size.width.toFloat(), pad)) }
            }
            .pointerInput(min, max) {
                if (!enabled) return@pointerInput
                val pad = 10.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { current(at(it.x, size.width.toFloat(), pad)) },
                ) { change, _ ->
                    change.consume()
                    current(at(change.position.x, size.width.toFloat(), pad))
                }
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val pad = 10.dp.toPx()
            val y = size.height / 2
            val stroke = 4.dp.toPx()
            val fraction = if (enabled) ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f) else 1f
            val x = pad + fraction * (size.width - 2 * pad)
            drawLine(track, Offset(pad, y), Offset(size.width - pad, y), stroke, StrokeCap.Round)
            drawLine(filled, Offset(pad, y), Offset(x, y), stroke, StrokeCap.Round)
            drawCircle(Color.White, 8.dp.toPx(), Offset(x, y))
            drawCircle(filled, 5.dp.toPx(), Offset(x, y))
        }
    }
}
