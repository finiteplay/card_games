package org.finiteplay.core.ui.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** `+150`, `−100`, `0`: the sign is always printed, so colour is never the only cue. */
fun signed(amount: Int): String = when {
    amount > 0 -> "+$amount"
    amount < 0 -> "−${-amount}"
    else -> "0"
}

/**
 * The one result mark: a pictogram and the signed net, in a translucent capsule ringed in [tint].
 * [spoken] is its whole accessibility description, announced politely when it appears. It never
 * intercepts a touch and is not modal; the caller places it. With [animate] off it is simply there.
 * [extra] adds a line under the figure.
 */
@Composable
fun ResultMark(
    icon: ImageVector,
    tint: Color,
    net: Int,
    spoken: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scale = remember { Animatable(if (animate) 0.3f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(icon, net) {
        if (!animate) return@LaunchedEffect
        launch { alpha.animateTo(1f, tween(110)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    }
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = Color.Black.copy(alpha = 0.62f),
        border = BorderStroke(3.dp, tint),
        modifier = modifier
            .scale(scale.value)
            .alpha(alpha.value)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            }
            .testTag("result_mark"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(52.dp).testTag("result_mark_icon"))
                Text(
                    text = signed(net),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp).testTag("result_mark_amount"),
                )
            }
            extra?.invoke(this)
        }
    }
}

/** A short red wash across the table after a loss, fading out. */
@Composable
fun LossWash() {
    val wash = remember { Animatable(0.26f) }
    LaunchedEffect(Unit) { wash.animateTo(0f, tween(700)) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFB02F26).copy(alpha = wash.value)))
}
