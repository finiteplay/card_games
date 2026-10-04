package org.finiteplay.klondike

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.solitaire.ui.TableauGeometryConfig
import org.finiteplay.solitaire.ui.cardHeightFittingTallestColumn as sharedCardHeightFittingTallestColumn
import org.finiteplay.solitaire.ui.computeCardSize as sharedComputeCardSize
import org.finiteplay.solitaire.ui.computeColumnSpacing as sharedComputeColumnSpacing
import org.finiteplay.solitaire.ui.computeTableauOverlap as sharedComputeTableauOverlap
import org.finiteplay.solitaire.ui.minimumColumnHeight as sharedMinimumColumnHeight

/** Board-geometry minimums from `docs/games/klondike/UI_SPEC.md` ("Board Geometry"). */
const val CARD_WIDTH_TO_HEIGHT_RATIO = 7f / 5f
val MIN_COLUMN_SPACING = 4.dp
val TOP_ROW_TO_TABLEAU_GAP = 48.dp
val MIN_CARD_WIDTH = 40.dp
val MIN_FACE_UP_BAND = 24.dp
private val MIN_FACE_DOWN_BAND = 10.dp
private val MIN_FACE_DOWN_BAND_FLOOR = 4.dp

/**
 * The most face-down cards any one tableau column can hold: the deal's own six, which
 * only ever get turned over, never added to.
 */
const val MAX_FACE_DOWN_IN_COLUMN = 6

/**
 * The tallest a tableau column can get: those six face-down cards under a King-to-Ace
 * sequence built on the card they were dealt beneath.
 */
const val MAX_TABLEAU_COLUMN_CARDS = MAX_FACE_DOWN_IN_COLUMN + 13

/**
 * The face-up step never grows past this fraction of card height, however much room is
 * available: the classic tableau look always overlaps, and at 1.0 the cards would separate
 * into a plain list.
 *
 * Raised from a third to about half. A portrait phone deals seven short columns into a screen
 * twice their height, which left the board crowded at the top and empty below; letting the fan
 * open further spends that room on legibility — a covered card now shows its rank, its suit,
 * and part of its pictogram rather than the corner alone. The cap still bites on a deep
 * column, where the step shrinks back to fit every card on screen.
 */
private const val MAX_FACE_UP_STEP_FRACTION = 0.52f

/**
 * The same ceiling for the face-down step, and deliberately much lower: a face-down card shows
 * nothing, so spreading those apart buys no legibility, only the sense that the board occupies
 * the table. Past about a fifth of a card the column starts to read as a list of gaps.
 */
private const val MAX_FACE_DOWN_STEP_FRACTION = 0.2f

/**
 * Gap between adjacent piles, as a share of the card's own width.
 *
 * Proportional rather than fixed because [MIN_COLUMN_SPACING] is a floor, not a look: 4 dp
 * separates two 47 dp cards on a phone and vanishes between two 110 dp cards on a tablet,
 * where the seven columns ended up touching each other.
 */
const val COLUMN_GAP_FRACTION = 0.10f

/**
 * Board margin at each side, likewise a share of the card — and wider than the gap between
 * columns, so the board reads as sitting on the table rather than being cropped by it.
 */
private const val SIDE_MARGIN_FRACTION = 0.16f

/**
 * Klondike's own numbers wired into the shared tableau-fitting math (`:solitaire:ui`). Every
 * function below is Klondike's original public surface, kept exactly, now delegating to that
 * module rather than repeating the algorithm — `TableauGeometryTest` there pins the algorithm
 * against these same numbers from the other side, and `CardGeometryTest` here still pins them
 * as Klondike's own gate.
 */
private val KLONDIKE_GEOMETRY = TableauGeometryConfig(
    cardWidthToHeightRatio = CARD_WIDTH_TO_HEIGHT_RATIO,
    minColumnSpacing = MIN_COLUMN_SPACING,
    minCardWidth = MIN_CARD_WIDTH,
    minFaceUpBand = MIN_FACE_UP_BAND,
    minFaceDownBand = MIN_FACE_DOWN_BAND,
    minFaceDownBandFloor = MIN_FACE_DOWN_BAND_FLOOR,
    maxFaceUpStepFraction = MAX_FACE_UP_STEP_FRACTION,
    maxFaceDownStepFraction = MAX_FACE_DOWN_STEP_FRACTION,
    columnGapFraction = COLUMN_GAP_FRACTION,
    sideMarginFraction = SIDE_MARGIN_FRACTION,
)

data class CardSize(val width: Dp, val height: Dp)

/** Gap between two adjacent piles of [cardWidth], never below [MIN_COLUMN_SPACING]. */
fun columnGapFor(cardWidth: Dp): Dp = maxOf(cardWidth * COLUMN_GAP_FRACTION, MIN_COLUMN_SPACING)

/** Inset from each side edge of the board for a board of [cardWidth] cards. */
fun boardSideMarginFor(cardWidth: Dp): Dp = maxOf(cardWidth * SIDE_MARGIN_FRACTION, MIN_COLUMN_SPACING)

/**
 * Card width/height for [columns] equal tableau columns across [availableWidth], scaling
 * to fit width first as `UI_SPEC.md` requires. Below the practical minimum width this
 * still returns [MIN_CARD_WIDTH]; the caller may then overflow, but no shipped layout
 * width does (verified at 320 dp and 360 dp).
 */
fun computeCardSize(availableWidth: Dp, columns: Int = TABLEAU_COLUMNS): CardSize {
    val size = KLONDIKE_GEOMETRY.sharedComputeCardSize(availableWidth, columns)
    return CardSize(size.width, size.height)
}

/**
 * Spacing between adjacent tableau columns for [columns] columns of [cardWidth] across
 * [availableWidth]. Never less than [MIN_COLUMN_SPACING].
 */
fun computeColumnSpacing(availableWidth: Dp, cardWidth: Dp, columns: Int = TABLEAU_COLUMNS): Dp =
    KLONDIKE_GEOMETRY.sharedComputeColumnSpacing(availableWidth, cardWidth, columns)

/**
 * Tallest card that still lets a [tallestColumn]-card column fit [availableHeight]
 * whole, using the tightest bands [computeTableauOverlap] will ever apply: the face-down
 * floor for the deal's own face-down cards, the face-up guarantee for the rest.
 *
 * Fitting card size to width alone is what lets a wide screen — landscape especially —
 * pick a card too tall for the longest column the same board permits, so the column runs
 * off the bottom. Callers take the smaller of this and their width fit.
 *
 * Never returns less than the minimum card, so a full column fits whole from about
 * 368 dp of board height up. Below that it overflows instead of becoming illegible —
 * the same accepted trade-off [computeTableauOverlap] documents.
 */
fun cardHeightFittingTallestColumn(availableHeight: Dp, tallestColumn: Int): Dp =
    KLONDIKE_GEOMETRY.sharedCardHeightFittingTallestColumn(availableHeight, tallestColumn, MAX_FACE_DOWN_IN_COLUMN)

/**
 * Height a column of [faceDownCount] face-down cards under [faceUpCount] face-up ones needs at
 * the minimum bands — the tightest it can be drawn without compressing the face-down step below
 * its ordinary floor.
 *
 * Used by the layout to decide what it can afford: portrait gives stock and waste a row of their
 * own only where a column of ordinary depth still fits above them, since a nearly square screen
 * sizes its cards off the width and can end up with cards too tall for both.
 */
fun minimumColumnHeight(cardHeight: Dp, faceDownCount: Int, faceUpCount: Int): Dp =
    KLONDIKE_GEOMETRY.sharedMinimumColumnHeight(cardHeight, faceDownCount, faceUpCount)

/**
 * Whether portrait can afford to give stock and waste a row of their own at the foot of the
 * board, rather than seating them beside the foundations in the top row.
 *
 * It can when a column of ordinary depth still fits between the two rows. A nearly square
 * screen — an unfolded foldable, a tablet — sizes its cards off the width, so a card there can
 * be half again as tall as on a phone while the board is no taller, and the two rows plus any
 * column at all stop fitting; stock and waste then end up against the tableau.
 *
 * Lives here rather than in the board composable because the layout above it needs the same
 * answer: it is what says whether the wordmark has the foot of the board to itself.
 */
fun portraitStockWasteAtBottom(boardWidth: Dp, boardHeight: Dp): Boolean {
    val card = computeCardSize(boardWidth)
    val topRow = card.height + TOP_ROW_TO_TABLEAU_GAP
    val bottomRow = card.height + TOP_ROW_TO_TABLEAU_GAP
    val column = minimumColumnHeight(card.height, faceDownCount = MAX_FACE_DOWN_IN_COLUMN, faceUpCount = 6)
    return boardHeight >= topRow + MIN_COLUMN_SPACING + bottomRow + column
}

/**
 * Everything portrait's board layout decides from the space it is given: how big a card is,
 * whether stock and waste get a row of their own at the foot, and how much height is left for
 * the tableau between the two rows.
 *
 * One function because the three answers depend on each other — the rows are a card tall, so
 * the card size sets the lane, and the lane is what the card size has to respect.
 */
data class PortraitBoardLayout(
    val card: CardSize,
    val stockWasteAtBottom: Boolean,
    val tableauLane: Dp,
)

/**
 * Fits the board to [boardWidth] by [boardHeight] with [tallestColumn] cards in the deepest
 * column.
 *
 * Card size is fitted to the width *and* to that column, the way landscape already fitted it:
 * width alone puts a 19-card column 19 dp past the bottom of a small phone's board area, which
 * is the face-up band's guarantee being kept by drawing over whatever is below. Tracking the
 * column that is actually dealt rather than the tallest the rules allow keeps cards large for
 * the ordinary game, and shrinks them a step at a time as a column deepens.
 *
 * Solved in two passes because the reservation depends on the answer: a smaller card frees
 * height, which can let the bottom row back in.
 */
fun portraitBoardLayout(boardWidth: Dp, boardHeight: Dp, tallestColumn: Int): PortraitBoardLayout {
    val faceDown = minOf(tallestColumn - 1, MAX_FACE_DOWN_IN_COLUMN).coerceAtLeast(0)
    val faceUp = (tallestColumn - faceDown).coerceAtLeast(1)

    // Two things give way, in this order. The **bottom row** goes first: stock and waste keep a
    // row of their own only while the deepest column currently dealt still fits above it, and
    // otherwise return to the foundations row, which costs nothing but their own place. Then the
    // **card** shrinks, exactly as landscape's fit already does, until that column fits the lane
    // it is left with.
    //
    // Something has to: on a small phone a nineteen-card column does not fit alongside a bottom
    // row at any card size the rules allow, and the alternative to yielding is drawing the column
    // over whatever sits below it. Both track the column *actually dealt* rather than the
    // nineteen the rules permit, so an ordinary game keeps its large cards and its bottom row.
    var card = computeCardSize(boardWidth)
    repeat(3) {
        val roomWithBottomRow = portraitTableauLane(boardHeight, card.height, stockWasteAtBottom = true)
        val atBottom = roomWithBottomRow >= minimumColumnHeight(card.height, faceDown, faceUp)
        val lane = portraitTableauLane(boardHeight, card.height, atBottom)
        val heightFit = cardHeightFittingTallestColumn(lane, tallestColumn) / CARD_WIDTH_TO_HEIGHT_RATIO
        val width = maxOf(minOf(card.width, heightFit), MIN_CARD_WIDTH)
        card = CardSize(width, width * CARD_WIDTH_TO_HEIGHT_RATIO)
    }

    val atBottom = portraitTableauLane(boardHeight, card.height, stockWasteAtBottom = true) >=
        minimumColumnHeight(card.height, faceDown, faceUp)
    return PortraitBoardLayout(card, atBottom, portraitTableauLane(boardHeight, card.height, atBottom))
}

/** Height left for the tableau once the top row, and the bottom row where there is one, are taken. */
fun portraitTableauLane(boardHeight: Dp, cardHeight: Dp, stockWasteAtBottom: Boolean): Dp {
    val topRow = cardHeight + TOP_ROW_TO_TABLEAU_GAP
    val bottomRow = if (stockWasteAtBottom) cardHeight + TOP_ROW_TO_TABLEAU_GAP else 0.dp
    return boardHeight - topRow - MIN_COLUMN_SPACING - bottomRow
}

data class TableauOverlap(val faceDownStep: Dp, val faceUpStep: Dp)

/**
 * Vertical step between successive card tops in a tableau column holding
 * [faceDownCount] face-down cards under [faceUpCount] face-up cards, fit to
 * [availableHeight]. The face-up step never drops below [MIN_FACE_UP_BAND] (a hard
 * guarantee, not best-effort); the face-down step may shrink further, per
 * `UI_SPEC.md`, down to a small visibility floor. Extra height beyond the minimum
 * layout can grow the face-up step a little for readability, but only up to
 * [MAX_FACE_UP_STEP_FRACTION] of the card height — cards always stay stacked, never
 * fully separated.
 */
fun computeTableauOverlap(availableHeight: Dp, cardHeight: Dp, faceDownCount: Int, faceUpCount: Int): TableauOverlap {
    val overlap = KLONDIKE_GEOMETRY.sharedComputeTableauOverlap(availableHeight, cardHeight, faceDownCount, faceUpCount)
    return TableauOverlap(overlap.faceDownStep, overlap.faceUpStep)
}
