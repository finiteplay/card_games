package org.finiteplay.blackjack.ui

/**
 * Card size and hand fan, in dp, as `docs/games/blackjack/UI_SPEC.md` "Geometry" states them. Pure
 * numbers so a JVM test can hold the spec to them at 320 dp and 360 dp without a device.
 */
object TableGeometry {
    /** Card width : height. */
    const val CARD_ASPECT = 5f / 7f

    /** The card-width floor: cards are content, not controls, so the 48 dp touch minimum does not apply. */
    const val MIN_CARD_WIDTH = 40f
    const val MAX_SINGLE_CARD_WIDTH = 64f
    const val MAX_SPLIT_CARD_WIDTH = 56f

    /**
     * The widest a fan's step ever is, as a share of a card: 0.7, so two cards overlap by under a
     * third and the covered card's corner pip still reads. The fan squeezes below this to fit.
     */
    const val MAX_STEP_FRACTION = 0.7f

    /** The fan's step floor; below it the corner indices are unreadable and the total badge carries the hand. */
    const val MIN_FAN_STEP = 6f

    /** Gap between split-hand columns. */
    const val COLUMN_GAP = 4f

    /** Side margin of the player area. */
    const val SIDE_MARGIN = 8f

    /** The width the hands share: the screen less its margins. */
    fun areaWidth(screenWidth: Float): Float = screenWidth - 2 * SIDE_MARGIN

    /** One hand's column width: the whole area alone, an equal share of it when split. */
    fun columnWidth(areaWidth: Float, hands: Int): Float = if (hands <= 1) areaWidth else areaWidth / hands

    /** Width of the cards for [hands] hands in [areaWidth] — one hand: `min(64, area ÷ 5)`; split: `min(56, column × 0.5)`, both floored at 40. */
    fun cardWidth(areaWidth: Float, hands: Int): Float {
        val raw = if (hands <= 1) minOf(MAX_SINGLE_CARD_WIDTH, areaWidth / 5f)
        else minOf(MAX_SPLIT_CARD_WIDTH, columnWidth(areaWidth, hands) * 0.5f)
        return maxOf(MIN_CARD_WIDTH, raw)
    }

    fun cardHeight(cardWidth: Float): Float = cardWidth / CARD_ASPECT

    /** The width a hand's cards may spread across: its column, less the gap a neighbour needs. */
    fun handWidth(areaWidth: Float, hands: Int): Float =
        if (hands <= 1) areaWidth else columnWidth(areaWidth, hands) - COLUMN_GAP

    /** Distance between neighbouring cards' left edges: [MAX_STEP_FRACTION] of a card at most, squeezed to fit, never under the floor. */
    fun fanStep(cardWidth: Float, handWidth: Float, cards: Int): Float {
        if (cards <= 1) return 0f
        return maxOf(MIN_FAN_STEP, minOf(cardWidth * MAX_STEP_FRACTION, (handWidth - cardWidth) / (cards - 1)))
    }

    /** Total width a fan occupies; may exceed [handWidth] once the step has hit its floor. */
    fun fanWidth(cardWidth: Float, step: Float, cards: Int): Float = cardWidth + step * (cards - 1).coerceAtLeast(0)
}
