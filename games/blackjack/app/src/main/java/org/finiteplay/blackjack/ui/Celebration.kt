package org.finiteplay.blackjack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.launch
import org.finiteplay.blackjack.R
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.Settlement

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

/** The banner's colours: a dark body with white type (contrast well over 4.5:1), tinted by the result. */
private fun bannerBrush(kind: ResultKind): Brush = when (kind) {
    ResultKind.BLACKJACK -> Brush.verticalGradient(listOf(Color(0xFF8E6A00), Color(0xFF5B4300)))
    ResultKind.WIN -> Brush.verticalGradient(listOf(Color(0xFF23893F), Color(0xFF16622C)))
    ResultKind.BUST, ResultKind.LOSE -> Brush.verticalGradient(listOf(Color(0xFFB02F26), Color(0xFF7E1E18)))
    ResultKind.PUSH, ResultKind.EVEN -> Brush.verticalGradient(listOf(Color(0xFF455A64), Color(0xFF2F3E46)))
}

private fun bannerBorder(kind: ResultKind): Color = when (kind) {
    ResultKind.BLACKJACK -> Color(0xFFFFD54F)
    ResultKind.WIN -> Color(0xFF7CE08F)
    ResultKind.BUST, ResultKind.LOSE -> Color(0xFFFF8A80)
    ResultKind.PUSH, ResultKind.EVEN -> Color(0xFFB0BEC5)
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
        if (animate && (kind == ResultKind.WIN || kind == ResultKind.BLACKJACK)) Confetti(seed, if (kind == ResultKind.BLACKJACK) 84 else 44)
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
    val scale = remember { Animatable(if (animate) 0.3f else 1f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(kind) {
        if (!animate) return@LaunchedEffect
        launch { alpha.animateTo(1f, tween(110)) }
        launch { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f)) }
    }
    val word = bannerWord(kind)
    val amount = if (net == 0) null else signed(net)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 14.dp,
        border = BorderStroke(3.dp, bannerBorder(kind)),
        modifier = Modifier
            .scale(scale.value)
            .alpha(alpha.value * 0.97f)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("result_banner"),
    ) {
        Column(
            modifier = Modifier.background(bannerBrush(kind)).padding(horizontal = 32.dp, vertical = 8.dp),
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
                    color = if (net > 0) Color(0xFFFFE082) else Color(0xFFFFCDD2),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.testTag("result_banner_amount"),
                )
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

/** One piece of confetti: where it starts, how it is thrown, and how it spins and looks. */
private class Piece(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val spin0: Float,
    val width: Float,
    val height: Float,
    val color: Color,
)

private val CONFETTI = listOf(
    Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFFF44336), Color(0xFF2196F3), Color(0xFFFFFFFF), Color(0xFFE040FB),
)

/** Confetti thrown up and out of the banner and pulled down by gravity, deterministic for a round's [seed]. */
@Composable
private fun Confetti(seed: Long, count: Int) {
    val pieces = remember(seed, count) {
        val random = Random(seed xor 0x5DEECE66DL)
        List(count) {
            Piece(
                angle = (-160f + random.nextFloat() * 140f) * (Math.PI.toFloat() / 180f),
                speed = 420f + random.nextFloat() * 620f,
                spin = (random.nextFloat() - 0.5f) * 900f,
                spin0 = random.nextFloat() * 360f,
                width = 6f + random.nextFloat() * 6f,
                height = 9f + random.nextFloat() * 8f,
                color = CONFETTI[random.nextInt(CONFETTI.size)],
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pieces) { progress.animateTo(1f, tween(durationMillis = 2_000, easing = LinearEasing)) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val seconds = progress.value * 2f
        val density = this.density
        val origin = Offset(size.width / 2f, size.height * 0.46f)
        val gravity = 1_500f * density
        val fade = ((1f - progress.value) / 0.3f).coerceIn(0f, 1f)
        for (piece in pieces) {
            val x = origin.x + kotlin.math.cos(piece.angle) * piece.speed * density * seconds
            val y = origin.y + kotlin.math.sin(piece.angle) * piece.speed * density * seconds + 0.5f * gravity * seconds * seconds
            val w = piece.width * density
            val h = piece.height * density
            rotate(piece.spin0 + piece.spin * seconds, pivot = Offset(x, y)) {
                drawRect(piece.color.copy(alpha = fade), Offset(x - w / 2f, y - h / 2f), Size(w, h))
            }
        }
    }
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
