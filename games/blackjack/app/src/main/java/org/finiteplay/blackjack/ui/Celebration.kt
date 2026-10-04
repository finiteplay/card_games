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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
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
import org.finiteplay.core.ui.layout.ConfettiBurst

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

/** The pictogram a round's result is drawn as, and what colours it. */
private fun ResultKind.icon(): ImageVector = when (this) {
    ResultKind.BLACKJACK -> Icons.Filled.Star
    ResultKind.WIN -> Icons.Filled.EmojiEvents
    ResultKind.BUST, ResultKind.LOSE -> Icons.Filled.SentimentVeryDissatisfied
    // Two bars: an equals sign for a round that came out level.
    ResultKind.PUSH, ResultKind.EVEN -> Icons.Filled.DragHandle
}

private fun ResultKind.tint(): Color = when (this) {
    ResultKind.BLACKJACK -> Color(0xFFFFD54F)
    ResultKind.WIN -> Color(0xFF7CE08F)
    ResultKind.BUST, ResultKind.LOSE -> Color(0xFFFF8A80)
    ResultKind.PUSH, ResultKind.EVEN -> Color(0xFFB0BEC5)
}

@Composable
private fun spokenResult(kind: ResultKind): String = stringResource(
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
 * - the one result mark: a pictogram and the round's signed net, in the middle of the table's lower
 *   half. It is the only place the result is stated, and it stays until the next round;
 * - confetti on a win, more of it for a blackjack, thrown from the mark;
 * - a brief red wash on a loss.
 *
 * With [animate] off (Skip Animations, or the system's reduced motion) the mark is simply there: no
 * spring, no confetti, no wash.
 */
@Composable
internal fun ResultCelebration(
    kind: ResultKind,
    net: Int,
    insuranceDelta: Int,
    seed: Long,
    animate: Boolean,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (animate && (kind == ResultKind.BUST || kind == ResultKind.LOSE)) LossWash()
        if (animate && (kind == ResultKind.WIN || kind == ResultKind.BLACKJACK)) ConfettiBurst(seed, if (kind == ResultKind.BLACKJACK) 84 else 44)
        // The middle of the lower half in portrait, below the player's hands; the middle of the table
        // in landscape, where the hands sit side by side.
        Box(
            modifier = Modifier.align(BiasAlignment(0f, if (landscape) 0f else MARK_BIAS)),
            contentAlignment = Alignment.Center,
        ) {
            ResultMark(kind, net, insuranceDelta, animate)
        }
    }
}

/** Where the mark sits vertically in portrait: -1 is the top of the table, 1 the bottom. */
private const val MARK_BIAS = 0.62f

/** The pictogram, the signed net and — when insurance was taken — a shield with its own signed result. */
@Composable
private fun ResultMark(kind: ResultKind, net: Int, insuranceDelta: Int, animate: Boolean) {
    val scale = remember { Animatable(if (animate) 0.3f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(kind, net) {
        if (!animate) return@LaunchedEffect
        launch { alpha.animateTo(1f, tween(110)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    }
    val spoken = spokenResult(kind) + " " + signed(net)
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = Color.Black.copy(alpha = 0.62f),
        border = BorderStroke(3.dp, kind.tint()),
        modifier = Modifier
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
                Icon(kind.icon(), contentDescription = null, tint = kind.tint(), modifier = Modifier.size(52.dp).testTag("result_mark_icon"))
                Text(
                    text = signed(net),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp).testTag("result_mark_amount"),
                )
            }
            if (insuranceDelta != 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("result_mark_insurance")) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = Color(0xFFB0BEC5), modifier = Modifier.size(20.dp))
                    Text(
                        text = signed(insuranceDelta),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (insuranceDelta > 0) Color(0xFF7CE08F) else Color(0xFFFF8A80),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

/** A short red wash across the table after a loss, fading out. */
@Composable
private fun LossWash() {
    val wash = remember { Animatable(0.26f) }
    LaunchedEffect(Unit) { wash.animateTo(0f, tween(700)) }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFB02F26).copy(alpha = wash.value)))
}
