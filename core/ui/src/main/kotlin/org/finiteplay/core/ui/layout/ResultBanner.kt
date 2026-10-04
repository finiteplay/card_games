package org.finiteplay.core.ui.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** What a result banner is tinted to say; the words on it say the same, so colour is never the only cue. */
enum class BannerTone { WIN, GOLD, LOSS, NEUTRAL }

/** A dark body with white type, which reads at over 4.5:1 on every tone, tinted by the result. */
private fun BannerTone.brush(): Brush = when (this) {
    BannerTone.GOLD -> Brush.verticalGradient(listOf(Color(0xFF8E6A00), Color(0xFF5B4300)))
    BannerTone.WIN -> Brush.verticalGradient(listOf(Color(0xFF23893F), Color(0xFF16622C)))
    BannerTone.LOSS -> Brush.verticalGradient(listOf(Color(0xFFB02F26), Color(0xFF7E1E18)))
    BannerTone.NEUTRAL -> Brush.verticalGradient(listOf(Color(0xFF455A64), Color(0xFF2F3E46)))
}

private fun BannerTone.border(): Color = when (this) {
    BannerTone.GOLD -> Color(0xFFFFD54F)
    BannerTone.WIN -> Color(0xFF7CE08F)
    BannerTone.LOSS -> Color(0xFFFF8A80)
    BannerTone.NEUTRAL -> Color(0xFFB0BEC5)
}

/**
 * The large banner a finished game or round is announced with: [word] in heavy type and, where
 * there is a figure to state, [amount] beneath it (a signed chip change; a game with no stake passes
 * none). It springs in, and with [animate] off it is simply there. It is a polite live region, so a
 * screen reader announces it as it appears.
 *
 * Never interactive; where it sits, and for how long, is the caller's.
 */
@Composable
fun ResultBanner(
    word: String,
    tone: BannerTone,
    animate: Boolean,
    modifier: Modifier = Modifier,
    amount: String? = null,
    amountPositive: Boolean = true,
) {
    val scale = remember { Animatable(if (animate) 0.3f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(word) {
        if (!animate) return@LaunchedEffect
        launch { alpha.animateTo(1f, tween(110)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 14.dp,
        border = BorderStroke(3.dp, tone.border()),
        modifier = modifier
            .scale(scale.value)
            .alpha(alpha.value * 0.97f)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("result_banner"),
    ) {
        Column(
            modifier = Modifier.background(tone.brush()).padding(horizontal = 32.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.testTag("result_banner_word"),
            )
            if (amount != null) {
                Text(
                    text = amount,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (amountPositive) Color(0xFFFFE082) else Color(0xFFFFCDD2),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.testTag("result_banner_amount"),
                )
            }
        }
    }
}
