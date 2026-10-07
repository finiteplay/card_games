package org.finiteplay.holdem.ui

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The table's sizes in dp, as `docs/games/holdem/UI_SPEC.md` "Geometry" states them. Pure numbers so a
 * JVM test can hold the spec to them without a device.
 */
object TableGeometry {
    const val MIN_CARD_WIDTH = 40f
    const val SIDE_MARGIN = 8f

    const val STATUS_ROW = 44f
    const val SIZING_ROW = 52f
    const val ACTION_BAR = 76f

    /** Portrait table heights: regular from here up, compact from [COMPACT_MIN_HEIGHT], squeezed below. */
    const val REGULAR_MIN_HEIGHT = 408f
    const val COMPACT_MIN_HEIGHT = 336f

    const val MAX_SEAT_WIDTH = 112f
    const val SEAT_NAME_ROW = 28f
    const val SEAT_STACK_ROW = 16f
    const val REGULAR_SEAT_CARD_ROW = 56f
    const val COMPACT_SEAT_CARD_ROW = 32f
    const val REGULAR_OPPONENT_CARD_WIDTH = 40f
    const val COMPACT_OPPONENT_CARD_WIDTH = 22f

    const val MAX_BOARD_CARD_WIDTH = 52f
    const val BOARD_CARD_GAP = 4f
    const val REGULAR_YOUR_CARD_WIDTH = 64f
    const val COMPACT_YOUR_CARD_WIDTH = 52f

    /** Your second card sits this share of a card to the right of the first; opponents' shown cards likewise. */
    const val STEP_FRACTION = 0.7f

    /** Your stack-and-markers line, under your cards. */
    const val YOUR_LINE = 20f

    const val REGULAR_MIN_GAP = 8f
    const val COMPACT_MIN_GAP = 6f

    const val RAIL_WIDTH = 76f
    const val SIZING_COLUMN_WIDTH = 76f
    const val LANDSCAPE_SEAT_WIDTH = 96f

    enum class Density { Regular, Compact, Squeezed }

    /** A card's height at 5 : 7. */
    fun cardHeight(cardWidth: Float): Float = cardWidth * 7f / 5f

    /** Distance between the left edges of two cards laid out with the 70% step. */
    fun step(cardWidth: Float): Float = cardWidth * STEP_FRACTION

    /** Width of two cards laid out with the 70% step. */
    fun pairWidth(cardWidth: Float): Float = cardWidth + step(cardWidth)

    fun areaWidth(screenWidth: Float): Float = screenWidth - 2 * SIDE_MARGIN

    /** Portrait table height: the screen less the status row, the always-reserved sizing row and the bar. */
    fun tableHeight(screenHeight: Float): Float = screenHeight - STATUS_ROW - SIZING_ROW - ACTION_BAR

    /** An opponent seat box's width: `min(112, (area − 16) ÷ 3)`. */
    fun seatWidth(areaWidth: Float): Float = min(MAX_SEAT_WIDTH, (areaWidth - 16f) / 3f)

    /** Board card width: `min(52, (area − 16) ÷ 5)`, floored at 40. */
    fun boardCardWidth(areaWidth: Float): Float =
        max(MIN_CARD_WIDTH, min(MAX_BOARD_CARD_WIDTH, (areaWidth - 16f) / 5f))

    fun density(tableHeight: Float): Density = when {
        tableHeight >= REGULAR_MIN_HEIGHT -> Density.Regular
        tableHeight >= COMPACT_MIN_HEIGHT -> Density.Compact
        else -> Density.Squeezed
    }

    /**
     * Portrait table, top to bottom: top band, middle band, board band, your seat, with [gap] between
     * each pair. Every size is in dp.
     */
    data class Portrait(
        val density: Density,
        val seatWidth: Float,
        val seatHeight: Float,
        val opponentCardWidth: Float,
        val boardCardWidth: Float,
        val yourCardWidth: Float,
        val boardBandHeight: Float,
        val yourSeatHeight: Float,
        val gap: Float,
    ) {
        val yourStep: Float get() = step(yourCardWidth)
        val bandsHeight: Float get() = 2 * seatHeight + boardBandHeight + yourSeatHeight

        /** The four bands and the three gaps between them. */
        val totalHeight: Float get() = bandsHeight + 3 * gap
    }

    /**
     * The portrait table for a window [screenWidth] wide and a table [tableHeight] tall.
     *
     * Regular and compact are the spec's. Below [COMPACT_MIN_HEIGHT] the squeeze keeps the compact seats,
     * so opponents' cards stay 22 dp, and shrinks the board's and your cards together a whole dp at a
     * time, down to 40; only then do the gaps shrink, to nothing. Below 288 dp the bands overflow.
     */
    fun portrait(screenWidth: Float, tableHeight: Float): Portrait {
        val area = areaWidth(screenWidth)
        val density = density(tableHeight)
        val regular = density == Density.Regular
        val seatHeight = SEAT_NAME_ROW + SEAT_STACK_ROW +
            if (regular) REGULAR_SEAT_CARD_ROW else COMPACT_SEAT_CARD_ROW
        val minGap = if (regular) REGULAR_MIN_GAP else COMPACT_MIN_GAP
        val board = boardCardWidth(area)

        fun build(boardWidth: Float, yourCard: Float, floorGap: Float): Portrait {
            val boardBand = ceil(cardHeight(boardWidth))
            val yourSeat = ceil(cardHeight(yourCard)) + YOUR_LINE
            val bands = 2 * seatHeight + boardBand + yourSeat
            return Portrait(
                density = density,
                seatWidth = seatWidth(area),
                seatHeight = seatHeight,
                opponentCardWidth = if (regular) REGULAR_OPPONENT_CARD_WIDTH else COMPACT_OPPONENT_CARD_WIDTH,
                boardCardWidth = boardWidth,
                yourCardWidth = yourCard,
                boardBandHeight = boardBand,
                yourSeatHeight = yourSeat,
                gap = max(floorGap, (tableHeight - bands) / 3f),
            )
        }

        if (density != Density.Squeezed) {
            return build(board, if (regular) REGULAR_YOUR_CARD_WIDTH else COMPACT_YOUR_CARD_WIDTH, minGap)
        }
        var card = COMPACT_YOUR_CARD_WIDTH
        while (card > MIN_CARD_WIDTH) {
            val fitted = build(min(board, card), card, COMPACT_MIN_GAP)
            if (fitted.bandsHeight + 3 * COMPACT_MIN_GAP <= tableHeight) return fitted
            card -= 1f
        }
        return build(min(board, MIN_CARD_WIDTH), MIN_CARD_WIDTH, 0f)
    }

    /** Landscape: the rail and the sizing column leave [tableWidth] for the table, [tableHeight] under the status row. */
    data class Landscape(
        val tableWidth: Float,
        val tableHeight: Float,
        val seatWidth: Float,
        val seatHeight: Float,
        val opponentCardWidth: Float,
        val boardCardWidth: Float,
        val yourCardWidth: Float,
        val yourSeatHeight: Float,
        val gap: Float,
    ) {
        val boardWidth: Float get() = 5 * boardCardWidth + 4 * BOARD_CARD_GAP

        /** The middle band's contents: a seat at each edge and the board between them. */
        val middleRowWidth: Float get() = 2 * seatWidth + boardWidth

        /** The three bands and the two gaps between them. */
        val totalHeight: Float get() = 2 * seatHeight + yourSeatHeight + 2 * gap
    }

    fun landscape(screenWidth: Float, screenHeight: Float): Landscape {
        val tableWidth = screenWidth - RAIL_WIDTH - SIZING_COLUMN_WIDTH
        val tableHeight = screenHeight - STATUS_ROW
        val seatHeight = SEAT_NAME_ROW + SEAT_STACK_ROW + REGULAR_SEAT_CARD_ROW
        val board = max(
            MIN_CARD_WIDTH,
            min(MAX_BOARD_CARD_WIDTH, (areaWidth(tableWidth) - 2 * LANDSCAPE_SEAT_WIDTH - 4 * BOARD_CARD_GAP) / 5f),
        )
        val yourSeat = ceil(cardHeight(COMPACT_YOUR_CARD_WIDTH)) + YOUR_LINE
        return Landscape(
            tableWidth = tableWidth,
            tableHeight = tableHeight,
            seatWidth = LANDSCAPE_SEAT_WIDTH,
            seatHeight = seatHeight,
            opponentCardWidth = REGULAR_OPPONENT_CARD_WIDTH,
            boardCardWidth = board,
            yourCardWidth = COMPACT_YOUR_CARD_WIDTH,
            yourSeatHeight = yourSeat,
            gap = max(REGULAR_MIN_GAP, (tableHeight - 2 * seatHeight - yourSeat) / 2f),
        )
    }
}
