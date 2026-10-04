package org.finiteplay.blackjack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.finiteplay.blackjack.R
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.Settlement
import org.finiteplay.core.ui.layout.BannerTone
import org.finiteplay.core.ui.layout.ConfettiBurst
import org.finiteplay.core.ui.layout.ResultBanner

/** What a settled round is called on the big banner. */
enum class ResultKind { BLACKJACK, WIN, PUSH, EVEN, BUST, LOSE }

/**
 * The banner a round earns, from its net rather than any one hand (`UI_SPEC.md` "Settlement
 * Presentation"): a split round that wins one hand and loses a bigger one is a loss. A natural makes
 * a win a Blackjack; Bust is for a lost round in which every hand went over; a Push is a round in
 * which every hand tied, and any other round that nets nothing is Even.
 */
fun resultKindOf(settlement: Settlement): ResultKind {
    val net = settlement.total
    val outcomes = settlement.hands.map { it.outcome }
    return when {
        net > 0 -> if (HandOutcome.BLACKJACK in outcomes) ResultKind.BLACKJACK else ResultKind.WIN
        net < 0 -> if (outcomes.all { it == HandOutcome.BUST }) ResultKind.BUST else ResultKind.LOSE
        outcomes.all { it == HandOutcome.PUSH } && settlement.insuranceDelta == 0 -> ResultKind.PUSH
        else -> ResultKind.EVEN
    }
}

/** How long the banner stays up before it clears the table. The summary line below stays. */
internal const val BANNER_MS = 2_600L

private fun ResultKind.tone(): BannerTone = when (this) {
    ResultKind.BLACKJACK -> BannerTone.GOLD
    ResultKind.WIN -> BannerTone.WIN
    ResultKind.BUST, ResultKind.LOSE -> BannerTone.LOSS
    ResultKind.PUSH, ResultKind.EVEN -> BannerTone.NEUTRAL
}

@Composable
private fun bannerWord(kind: ResultKind): String = stringResource(
    when (kind) {
        ResultKind.BLACKJACK -> R.string.hand_blackjack
        ResultKind.WIN -> R.string.banner_win
        ResultKind.PUSH -> R.string.outcome_push
        ResultKind.EVEN -> R.string.summary_even
        ResultKind.BUST -> R.string.hand_bust
        ResultKind.LOSE -> R.string.banner_lose
    },
)

/**
 * Everything that celebrates (or commiserates) a settled round, layered over the table and never
 * intercepting a touch — nothing here is modal (`UI_SPEC.md` "Settlement Presentation"):
 *
 * - the big banner, which pops in with a spring (and never shakes: a loss is shown plainly);
 * - confetti on a win, more of it for a blackjack, thrown from the banner;
 * - a brief red wash on a loss;
 * - the round's signed net, floating up toward the bankroll it is about to change.
 *
 * With [animate] off (Skip Animations, or the system's reduced motion) the banner is simply there:
 * no spring, no shake, no confetti, no float.
 */
@Composable
internal fun ResultCelebration(
    kind: ResultKind,
    net: Int,
    seed: Long,
    animate: Boolean,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (animate && (kind == ResultKind.BUST || kind == ResultKind.LOSE)) LossWash()
        if (animate && (kind == ResultKind.WIN || kind == ResultKind.BLACKJACK)) ConfettiBurst(seed, if (kind == ResultKind.BLACKJACK) 84 else 44)
        // Portrait: in the gap between the dealer's hand and the player's, so neither is covered. The
        // table gives the dealer a third of its height and the player the rest from the middle down.
        // Landscape puts the hands side by side, so the banner takes the middle.
        Box(
            modifier = Modifier.align(BiasAlignment(0f, if (landscape) 0f else BANNER_BIAS)),
            contentAlignment = Alignment.Center,
        ) {
            Banner(kind, net, animate)
            if (animate && net != 0) FloatingNet(net)
        }
    }
}

/** Where the banner sits vertically in portrait: -1 is the top of the table, 1 the bottom. */
private const val BANNER_BIAS = -0.36f

@Composable
private fun Banner(kind: ResultKind, net: Int, animate: Boolean) {
    ResultBanner(
        word = bannerWord(kind),
        tone = kind.tone(),
        animate = animate,
        amount = if (net == 0) null else signed(net),
        amountPositive = net > 0,
    )
}

/** A short red wash across the table after a loss, fading out. */
@Composable
private fun LossWash() {
    val wash = remember { Animatable(0.26f) }
    LaunchedEffect(Unit) { wash.animateTo(0f, tween(700)) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFB02F26).copy(alpha = wash.value)))
}

/** The round's signed net, rising from the banner toward the bankroll and fading as it arrives. */
@Composable
private fun FloatingNet(net: Int) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(net) {
        kotlinx.coroutines.delay(700)
        progress.animateTo(1f, tween(900))
    }
    if (progress.value <= 0f) return
    Text(
        text = signed(net),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.ExtraBold,
        color = if (net > 0) Color(0xFF7CE08F) else Color(0xFFFF8A80),
        modifier = Modifier
            .offset(y = (-(60f + 260f * progress.value)).dp)
            .alpha(1f - progress.value * progress.value),
    )
}
