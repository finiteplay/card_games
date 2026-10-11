package org.finiteplay.core.ui.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.finiteplay.cards.Card
import org.finiteplay.core.ui.theme.LocalAppColors

/** Whether cards fly in from the dealer's spot: false under Skip Animations and system reduced motion. */
val LocalAnimateCards = compositionLocalOf { true }

/** How long one card's flight to its place takes, and the stagger between cards dealt together. */
const val FLIGHT_MS = 280
const val FLIGHT_STAGGER_MS = 140

/** Card and fan sizes for a hand laid out in a row, pure numbers so a JVM test can hold a spec to them. */
object FanGeometry {
    /** Card width : height. */
    const val CARD_ASPECT = 5f / 7f

    /**
     * The widest a fan's step ever is, as a share of a card: 0.7, so two cards overlap by under a
     * third and the covered card's corner pip still reads. The fan squeezes below this to fit.
     */
    const val MAX_STEP_FRACTION = 0.7f

    /** The fan's step floor; below it the corner indices are unreadable and a badge has to carry the hand. */
    const val MIN_FAN_STEP = 6f

    fun cardHeight(cardWidth: Float): Float = cardWidth / CARD_ASPECT

    /** Distance between neighbouring cards' left edges: [MAX_STEP_FRACTION] of a card at most, squeezed to fit, never under the floor. */
    fun fanStep(cardWidth: Float, handWidth: Float, cards: Int): Float {
        if (cards <= 1) return 0f
        return maxOf(MIN_FAN_STEP, minOf(cardWidth * MAX_STEP_FRACTION, (handWidth - cardWidth) / (cards - 1)))
    }

    /** Total width a fan occupies; may exceed the hand's width once the step has hit its floor. */
    fun fanWidth(cardWidth: Float, step: Float, cards: Int): Float = cardWidth + step * (cards - 1).coerceAtLeast(0)
}

/** The flight progress of each card position in one fan; a class so its animations are made outside composition. */
private class CardFlights {
    val progress = mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>()

    fun ensure(index: Int, animate: Boolean) {
        if (index !in progress) progress[index] = Animatable(if (animate) 0f else 1f)
    }
}

private val RAISED_OUTLINE = Color(0xFFFFD54F)

/**
 * A hand's cards in a row on a canvas. A null entry is a card face down. Each card that appears flies
 * in from a point above and beyond the trailing corner to its place, in the order it was dealt; a card
 * already there stays put. Under [LocalAnimateCards] false a card is simply there.
 *
 * - [step] overrides the fan step (a row of cards that must not overlap); null fits [handWidth].
 * - [slots] reserves empty outlines for positions not yet dealt, up to that many cards in all.
 * - [raisedIndices], when not null, lifts those cards by [raise] with an outline and dims the others.
 *
 * [stateDescription] reads `flying` while any card is still in flight, which lets a test prove the
 * flight happened rather than only checking where the cards ended up.
 */
@Composable
fun CardFan(
    cards: List<Card?>,
    cardWidth: Dp,
    handWidth: Dp,
    testTag: String,
    modifier: Modifier = Modifier,
    step: Dp? = null,
    slots: Int = cards.size,
    raisedIndices: Set<Int>? = null,
    raise: Dp = 0.dp,
) {
    val colors = LocalAppColors.current.card
    val animate = LocalAnimateCards.current
    val positions = maxOf(slots, cards.size)
    val stepDp = step?.value ?: FanGeometry.fanStep(cardWidth.value, handWidth.value, positions)
    val fanWidth = FanGeometry.fanWidth(cardWidth.value, stepDp, positions).dp
    val cardHeight = FanGeometry.cardHeight(cardWidth.value).dp

    val flights = remember { CardFlights() }
    val progress = flights.progress
    val firstNew = cards.indices.firstOrNull { it !in progress }
    cards.indices.forEach { index -> flights.ensure(index, animate) }
    cards.indices.forEach { index ->
        val flight = progress.getValue(index)
        LaunchedEffect(index, animate) {
            if (flight.value < 1f) {
                val order = if (firstNew == null) 0 else (index - firstNew).coerceAtLeast(0)
                if (animate) {
                    delay(order * FLIGHT_STAGGER_MS.toLong())
                    flight.animateTo(1f, tween(FLIGHT_MS))
                } else {
                    flight.snapTo(1f)
                }
            }
        }
    }
    val flying = cards.indices.any { (progress[it]?.value ?: 1f) < 1f }

    Canvas(
        modifier = modifier
            .width(fanWidth)
            .height(cardHeight + raise)
            .testTag(testTag)
            .semantics { stateDescription = if (flying) "flying" else "settled" },
    ) {
        val size = Size(cardWidth.toPx(), cardHeight.toPx())
        val lift = raise.toPx()
        // The dealer's spot sits off the trailing corner: cards leave from there and settle into the row.
        val shoe = Offset(size.width * 2.2f, -size.height * 1.4f)
        for (index in 0 until positions) {
            val card = cards.getOrNull(index)
            val t = if (index < cards.size) progress[index]?.value ?: 1f else 0f
            val raised = raisedIndices?.contains(index) == true
            val target = Offset(index * stepDp.dp.toPx(), if (raised) 0f else lift)
            if (index >= cards.size || t < 1f) {
                drawEmptySlot(Offset(target.x, lift), size, colors)
            }
            if (index >= cards.size || t <= 0f) continue
            val topLeft = Offset(
                target.x + (shoe.x - target.x) * (1f - t),
                target.y + (shoe.y - target.y) * (1f - t),
            )
            if (card == null) drawCardBack(topLeft, size, colors) else drawCardFace(topLeft, size, card, colors)
            if (raisedIndices != null) {
                val radius = CornerRadius(size.width * 0.08f)
                if (raised) {
                    drawRoundRect(RAISED_OUTLINE, topLeft, size, radius, style = Stroke(width = 2.dp.toPx()))
                } else {
                    drawRoundRect(Color.Black.copy(alpha = 0.4f), topLeft, size, radius)
                }
            }
        }
    }
}
