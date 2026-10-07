package org.finiteplay.klondike.ui.game

import androidx.compose.foundation.gestures.detectTapGestures
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.gesture.cardPointerInput
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.core.ui.card.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import org.finiteplay.klondike.R
import org.finiteplay.klondike.CARD_WIDTH_TO_HEIGHT_RATIO
import org.finiteplay.klondike.CardSize
import org.finiteplay.klondike.DragSource
import org.finiteplay.klondike.MIN_CARD_WIDTH
import org.finiteplay.klondike.cardHeightFittingTallestColumn
import org.finiteplay.klondike.TOP_ROW_TO_TABLEAU_GAP
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.COLUMN_GAP_FRACTION
import org.finiteplay.klondike.boardSideMarginFor
import org.finiteplay.klondike.columnGapFor
import org.finiteplay.klondike.computeCardSize
import org.finiteplay.klondike.portraitBoardLayout
import org.finiteplay.klondike.computeTableauOverlap
import org.finiteplay.klondike.legalDestinationsForDragSource
import org.finiteplay.klondike.resolveFoundationTap
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.applyPlayerTransition
import org.finiteplay.klondike.rules.canAutoFinish
import org.finiteplay.klondike.rules.canPlaceOnFoundation
import org.finiteplay.klondike.rules.resolveStockTap
import org.finiteplay.klondike.rules.resolveTableauTap
import org.finiteplay.klondike.rules.resolveWasteTap
import org.finiteplay.klondike.rules.runAutomaticFoundationCascade
import org.finiteplay.klondike.storage.Handedness
import org.finiteplay.core.ui.sound.SoundEffect

/** Identifies one pile for hit-testing, highlighting, and invalid-feedback bookkeeping. */
private sealed class PileKey {
    data class Foundation(val suit: Suit) : PileKey()
    data class TableauColumn(val column: Int) : PileKey()
    data object Waste : PileKey()
    data object Stock : PileKey()
}

private data class DragPayload(
    val source: DragSource,
    val cards: List<Card>,
    val legalMoves: List<Move>,
    val originTopLeft: Offset,
    val grabOffset: Offset,
    val pointerInBoard: Offset,
    /** Vertical spacing between stacked cards, matching the source column's own overlap. */
    val stepPx: Float,
)

/**
 * A short flying-card animation for one step of a committed transaction (the
 * player's own move, one automatic foundation transfer, or one grouped leg of an
 * undo reversal).
 *
 * @param stepPx vertical spacing between the stack's cards, matching the relevant
 *   tableau column's own overlap — a fixed fraction instead would visibly compress
 *   or stretch the stack relative to how it was just shown, reading as a shrink.
 * @param resultState the board exactly as it should look once this step finishes —
 *   [Board] only ever renders a fully-consistent [GameState] (see `displayState`),
 *   stepping to this value the instant the flight completes rather than jumping
 *   straight to the transaction's final state. This is what keeps a card that has a
 *   later step still queued (e.g. the card just revealed underneath, itself about to
 *   auto-cascade to a foundation) visible at its old spot instead of vanishing the
 *   moment an earlier, unrelated step commits.
 * @param durationMs how long this specific flight takes, proportional to the distance
 *   it covers ([durationForFlight]) rather than a single fixed duration for every
 *   flight regardless of length.
 * @param startDelayMs a pause before this flight begins, after the previous one lands
 *   — non-zero when the previous step revealed a face-down card face-up on the column
 *   this one departs from (so the reveal is visible for a beat before the automatic
 *   transfer whisks it away), or, under [Board]'s `skipAnimations`, for every
 *   automatic transfer unconditionally (`docs/games/klondike/DESIGN.md` "Automatic Foundation
 *   Moves": still one card at a time, just without the slide).
 * @param sound played the instant this step actually starts (in the `LaunchedEffect`
 *   that plays it), not at commit time — so it stays audibly tied to the card that is
 *   actually moving right then, including for a step still sitting in the queue
 *   behind an earlier one. `null` for undo's reversal and the automatic-finish sweep,
 *   which stay silent per card by design.
 * @param fromKey the pile this leg's cards are actually departing, so the static
 *   pile render (fed by `displayState`, which does not itself advance until the
 *   flight lands) can hide them for the flight's whole duration instead of showing
 *   them sitting in place while an identical-looking flying copy pulls away from on
 *   top of them — the drag-committed path never had this seam, since a drag already
 *   hides its cards the moment it lifts them, well before this ever gets constructed.
 * @param fromIndex which card [fromKey] starts hiding from, when [fromKey] is a
 *   tableau column — every card at or past this index is mid-flight. Unused (the
 *   whole pile is single-card, so hiding it is all-or-nothing) for every other pile.
 */
private data class MoveAnimation(
    val cards: List<Card>,
    val fromTopLeft: Offset,
    val toTopLeft: Offset,
    val stepPx: Float,
    val resultState: GameState,
    val durationMs: Int,
    val startDelayMs: Int = 0,
    val sound: SoundEffect? = null,
    val fromKey: PileKey? = null,
    val fromIndex: Int? = null,
)

/**
 * The pile a drag would land [move] on. Foundation-directed moves don't name a suit
 * on the [Move] itself, so [draggedTopCardSuit] (always the sequence's top card — the only
 * runs that can legally target a foundation are single cards) supplies it.
 */
private fun destinationKeyFor(move: Move, draggedTopCardSuit: Suit): PileKey? = when (move) {
    is Move.TableauToTableau -> PileKey.TableauColumn(move.toColumn)
    is Move.TableauToFoundation -> PileKey.Foundation(draggedTopCardSuit)
    is Move.WasteToTableau -> PileKey.TableauColumn(move.toColumn)
    Move.WasteToFoundation -> PileKey.Foundation(draggedTopCardSuit)
    is Move.FoundationToTableau -> PileKey.TableauColumn(move.toColumn)
    Move.Draw, Move.Recycle -> null
}

/** Which pile a hinted or committed [move] would be played from. */
private fun sourceKeyFor(move: Move): PileKey = when (move) {
    Move.Draw, Move.Recycle -> PileKey.Stock
    is Move.TableauToTableau -> PileKey.TableauColumn(move.fromColumn)
    is Move.TableauToFoundation -> PileKey.TableauColumn(move.fromColumn)
    is Move.WasteToTableau -> PileKey.Waste
    Move.WasteToFoundation -> PileKey.Waste
    is Move.FoundationToTableau -> PileKey.Foundation(move.suit)
}

/** Which pile a hinted or committed [move] would land on, resolved against [state] for foundation suit. */
private fun destinationKeyFor(move: Move, state: GameState): PileKey? = when (move) {
    Move.Draw -> PileKey.Waste
    Move.Recycle -> PileKey.Stock
    is Move.TableauToTableau -> PileKey.TableauColumn(move.toColumn)
    is Move.TableauToFoundation -> state.tableau[move.fromColumn].lastOrNull()?.card?.suit?.let { PileKey.Foundation(it) }
    is Move.WasteToTableau -> PileKey.TableauColumn(move.toColumn)
    Move.WasteToFoundation -> state.waste.firstOrNull()?.suit?.let { PileKey.Foundation(it) }
    is Move.FoundationToTableau -> PileKey.TableauColumn(move.toColumn)
}

/** The card(s) [move] relocates, read from the board *before* the move is applied. */
/** Mirrors `applyMove`'s own `Move.Draw` count, capped separately there against a short stock. */
private fun drawCountFor(drawMode: DrawMode): Int = when (drawMode) {
    DrawMode.ONE -> 1
    DrawMode.THREE -> 3
}

private fun movedCardsFor(move: Move, state: GameState): List<Card> = when (move) {
    is Move.TableauToTableau -> {
        val column = state.tableau[move.fromColumn]
        column.subList(move.fromIndex, column.size).map { it.card }
    }
    is Move.TableauToFoundation -> listOfNotNull(state.tableau[move.fromColumn].lastOrNull()?.card)
    is Move.WasteToTableau, Move.WasteToFoundation -> listOfNotNull(state.waste.firstOrNull())
    is Move.FoundationToTableau -> listOfNotNull(state.foundationTop(move.suit))
    // Bottom-to-top of the resulting flying stack, matching TableauToTableau's own
    // convention above: stock[0] (drawn first) ends up at the bottom of the new
    // waste cards, the last one taken ends up on top.
    Move.Draw -> state.stock.take(drawCountFor(state.drawMode))
    Move.Recycle -> emptyList()
}

/**
 * Locally re-derives the automatic-finish sweep's move sequence — mirroring
 * `AutoFinish.kt`'s private `lowestRankAccessibleFoundationMove`/`simulateSweep`,
 * which return only the final state, not the moves along the way — purely to
 * discover what to animate once [GameViewModel.finishAutomaticallyIfReady] has
 * already committed the real sweep via the reducer; that remains the sole source of
 * truth for the committed state itself.
 */
private fun lowestRankAccessibleFoundationMove(state: GameState): Move? {
    var best: Move? = null
    var bestRank = Int.MAX_VALUE
    state.waste.firstOrNull()?.let { card ->
        if (canPlaceOnFoundation(state.foundations, card)) {
            best = Move.WasteToFoundation
            bestRank = card.rank.value
        }
    }
    for (column in 0 until TABLEAU_COLUMNS) {
        val top = state.tableau[column].lastOrNull() ?: continue
        if (canPlaceOnFoundation(state.foundations, top.card) && top.card.rank.value < bestRank) {
            best = Move.TableauToFoundation(column)
            bestRank = top.card.rank.value
        }
    }
    return best
}

private fun sweepMoves(initial: GameState): List<Move> {
    var current = initial
    var progressedSinceLastRecycle = true
    val moves = mutableListOf<Move>()
    while (current.status != GameStatus.WON) {
        val move = lowestRankAccessibleFoundationMove(current)
        val next = when {
            move != null -> {
                progressedSinceLastRecycle = true
                move
            }
            current.stock.isNotEmpty() -> Move.Draw
            current.waste.isNotEmpty() && progressedSinceLastRecycle -> {
                progressedSinceLastRecycle = false
                Move.Recycle
            }
            else -> return moves
        }
        moves += next
        current = applyMove(current, next).copy(moveCount = current.moveCount + 1)
    }
    return moves
}

// Constant speed rather than a fixed duration: a short hop and a corner-to-corner
// flight should read as the same motion, not the same clock time. Bounds keep a
// near-zero-distance flight (e.g. stock to the adjacent waste) from being
// imperceptibly brief, and a full-board flight from dragging on too long.
// Dp, not raw pixels: a flight covers real-world ground at a fixed rate only if the unit
// itself is density-independent. A px/ms constant reads as a different speed on every screen
// density, since the same real-world inch is a different pixel count on each — this is what
// made the animation read as "broken" (`docs/games/klondike/UI_SPEC.md` "Motion").
// 160dp = 1 inch on any device, so 1.6 dp/ms is exactly 5 inches in 0.5s.
private const val MOVE_SPEED_DP_PER_MS = 1.6f
private const val MIN_MOVE_ANIMATION_MS = 80
private const val MAX_MOVE_ANIMATION_MS = 450

/**
 * Equal-width columns the landscape board area is divided into: the seven tableau columns
 * plus the foundations strip and the stock/waste strip that flank them. Every pile is
 * centered in its own slot.
 */
private const val LANDSCAPE_SLOTS = TABLEAU_COLUMNS + 2

/** Diagonal offset (as a fraction of card width) between each fanned waste card and the one above it, in draw-three. */
private const val WASTE_FAN_STEP = 0.06f

/**
 * The automatic-finish sweep (every remaining card to the foundations once the board
 * is provably solved) can be dozens of cards playing strictly serially; at normal
 * speed that's a long wait for something the player no longer has any decisions left
 * in, so it plays at this multiple of the usual speed instead.
 */
private const val AUTO_FINISH_SPEED_MULTIPLIER = 3f

/** Flight duration for a straight line from [from] to [to] at [MOVE_SPEED_DP_PER_MS] times [speedMultiplier]. */
private fun durationForFlight(from: Offset, to: Offset, density: Density, speedMultiplier: Float = 1f): Int {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val distanceDp = with(density) { sqrt(dx * dx + dy * dy).toDp().value }
    val minMs = (MIN_MOVE_ANIMATION_MS / speedMultiplier).toInt()
    val maxMs = (MAX_MOVE_ANIMATION_MS / speedMultiplier).toInt()
    return (distanceDp / (MOVE_SPEED_DP_PER_MS * speedMultiplier)).toInt().coerceIn(minMs, maxMs)
}

/** Pause before an automatic transfer that departs from a column [movesRevealCard] just exposed. */
private const val REVEAL_TO_AUTO_MOVE_DELAY_MS = 100

/**
 * Under `skipAnimations`, each automatic transfer still plays as its own step — one
 * card at a time, never merged with the next — but without a slide: this is the pause
 * that stands in for the slide's own duration, so the sequence stays perceptible
 * instead of resolving in a single frame. Applied unconditionally, taking the larger
 * of this and [REVEAL_TO_AUTO_MOVE_DELAY_MS] when a reveal just happened.
 */
private const val SKIP_ANIMATIONS_STEP_PAUSE_MS = 150

/** The "instant" flight duration under `skipAnimations`: no perceptible slide, but still a real step the queue plays through. */
private const val SKIP_ANIMATIONS_FLIGHT_MS = 0

/** True if committing [move] against [sourceState] auto-flips a face-down card face-up on its source tableau column. */
private fun movesRevealCard(move: Move, sourceState: GameState): Boolean {
    val (column, cutIndex) = when (move) {
        is Move.TableauToTableau -> move.fromColumn to move.fromIndex
        is Move.TableauToFoundation -> move.fromColumn to sourceState.tableau[move.fromColumn].lastIndex
        else -> return false
    }
    val remaining = sourceState.tableau[column].subList(0, cutIndex)
    return remaining.isNotEmpty() && !remaining.last().faceUp
}

/**
 * Alpha for the hint highlight: two 220ms pulses down to near-zero and back to full,
 * then a steady, still-prominent highlight so the suggested cards stay identifiable
 * (`UI_SPEC.md` "Hint"). Zero, and no pulse, when [key] is null or
 * [skipAnimations] is set. [key] is whatever the caller wants this pulse to restart on — a single
 * guided [Move] for the proven-winning-move mode, or the whole highlighted list for the
 * highlight-every-legal-move mode — never both at once, so one shared pulse serves either.
 */
@Composable
private fun rememberHintAlpha(key: Any?, skipAnimations: Boolean): Float {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(key, skipAnimations) {
        if (key == null) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        if (skipAnimations) {
            alpha.snapTo(1f)
            return@LaunchedEffect
        }
        alpha.snapTo(1f)
        repeat(2) {
            alpha.animateTo(0.1f, tween(220))
            alpha.animateTo(1f, tween(220))
        }
        // Settles at full strength, not at 85%: the flash says "here", and what is left has
        // to still say it while the player works out what to do about it. It clears when they
        // move, undo, or ask for another hint - never on its own.
    }
    return alpha.value
}

/** Which pile [card] currently sits in, for diffing two states around an undo. */
private fun pileOf(state: GameState, card: Card): PileKey {
    for (col in state.tableau.indices) {
        if (state.tableau[col].any { it.card == card }) return PileKey.TableauColumn(col)
    }
    if (card in state.waste) return PileKey.Waste
    if (card in state.stock) return PileKey.Stock
    return PileKey.Foundation(card.suit)
}

/**
 * The full interactive board: stock, waste, foundations, and tableau, rendered on
 * Canvas with semantic overlays layered above it. Owns all transient gesture state
 * (drag payload, hover/highlight, invalid-feedback pulses, in-flight move animation)
 * locally via [remember], so none of it survives recomposition from scratch — in
 * particular, an active drag is safely discarded on rotation (`UI_SPEC.md` "Board
 * Geometry").
 *
 * Every committed move (tap or drag) plays a short flying-card animation from its
 * source pile to its destination, so a registered action is always visually obvious —
 * not just a hard cut between two static boards. The board itself is always rendered
 * from `displayState`, a copy of [state] that only steps forward as each queued
 * flight actually finishes, rather than [state] directly (which is already fully
 * resolved, cascades included, the instant a move commits) — this is what keeps a
 * card whose own flight hasn't started yet from disappearing or arriving early.
 */
@Composable
fun Board(
    state: GameState,
    onCommitMove: (Move) -> Boolean,
    modifier: Modifier = Modifier,
    orientation: BoardOrientation = BoardOrientation.PORTRAIT,
    handedness: Handedness = Handedness.RIGHT,
    hintMove: Move? = null,
    // Never both non-empty at once: one Settings toggle (`setting_hint_shows_winning_move`)
    // picks which mode Hint is in. Unlike [hintMove], tapping a highlighted pile never forces
    // one particular move — highlighting doesn't claim any one of them is the "right" one.
    highlightedMoves: List<Move> = emptyList(),
    skipAnimations: Boolean = false,
    automaticMovesEnabled: Boolean = true,
    undoSignal: GameState? = null,
    onUndoSignalConsumed: () -> Unit = {},
    onAnimationsInFlightChanged: (Boolean) -> Unit = {},
    onPlaySound: (SoundEffect) -> Unit = {},
) {
    val isLandscape = orientation == BoardOrientation.LANDSCAPE
    // Applied outside the constraints box so every coordinate below — layout, hit
    // testing, animation — is measured within the inset area and stays consistent.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val cardColors = LocalAppColors.current.card
        // Landscape divides the width into [LANDSCAPE_SLOTS] equal slots — the seven
        // tableau columns plus a strip on each side for the foundations and stock/waste
        // (see the topLeft lambdas below) — so a card is one slot less the column
        // spacing, rather than computeCardSize's seven-across.
        //
        // Width alone is not enough in landscape: the height a wide screen's card gets
        // from the 5:7 ratio can exceed what a full column has room for, and the column
        // then runs off the bottom.
        //
        // The height fit tracks the tallest column actually on the board, not the tallest
        // one the rules allow. Sizing for the theoretical 19-card worst case pins every
        // landscape game to the minimum card, because that column needs nearly the whole
        // screen height at the minimum face-up band — a permanent cost to guard against a
        // rare shape. Tracking the real board keeps cards large for the ordinary game and
        // still shows every card whole when a column does grow that deep; the size then
        // changes a step at a time, between moves, as the column crosses each threshold.
        val cardSize = if (isLandscape) {
            // A slot holds one card plus the gutter it carries, so the card is the slot less its
            // own share — not the slot less a flat 4 dp, which on a tablet left the nine piles
            // touching. Every pile being centred in its slot turns that share into the gap.
            val widthFit = maxOf(maxWidth / LANDSCAPE_SLOTS / (1f + COLUMN_GAP_FRACTION), MIN_CARD_WIDTH)
            val tallestColumn = state.tableau.maxOf { it.size }
            val heightFit = cardHeightFittingTallestColumn(maxHeight, tallestColumn) / CARD_WIDTH_TO_HEIGHT_RATIO
            // The foundations are their own vertical strip in landscape, four whole cards
            // with no overlap to absorb a size the tableau could still have taken — so
            // they bind the card size independently of how deep any column is.
            // Four whole cards and the three gaps between them, where each gap is a share of
            // the card: 4*h + 3*gap = height, and h = w * ratio.
            val foundationsFit = maxHeight / (4 * CARD_WIDTH_TO_HEIGHT_RATIO + 3 * COLUMN_GAP_FRACTION)
            val width = maxOf(minOf(widthFit, heightFit, foundationsFit), MIN_CARD_WIDTH)
            CardSize(width, width * CARD_WIDTH_TO_HEIGHT_RATIO)
        } else {
            // Portrait fits the card to the width *and* to the deepest column currently dealt,
            // the same way landscape does: width alone drew a nineteen-card column past the
            // bottom of a small phone's board area (`PortraitLayoutFitsTest`).
            portraitBoardLayout(maxWidth, maxHeight, state.tableau.maxOf { it.size }).card
        }
        val cardWidthPx = with(density) { cardSize.width.toPx() }
        val cardHeightPx = with(density) { cardSize.height.toPx() }
        val spacingPx = with(density) { columnGapFor(cardSize.width).toPx() }
        // Portrait insets the board itself rather than padding the composable, so every
        // coordinate below — layout, hit testing, animation — stays measured in one space.
        // Landscape needs none: its outer slots hold the flanking piles centred, which is
        // already a margin.
        val boardLeftPx = if (isLandscape) 0f else with(density) { boardSideMarginFor(cardSize.width).toPx() }
        val topRowGapPx = with(density) { TOP_ROW_TO_TABLEAU_GAP.toPx() }
        val topRowHeightPx = cardHeightPx + topRowGapPx
        val boardHeightPx = with(density) { maxHeight.toPx() }
        // Portrait puts stock and waste in a row of their own at the bottom of the board, where
        // the thumb already is, rather than beside the foundations at the top of a 20:9 phone
        // (`UI_SPEC.md` "Portrait"). The reservation is one card plus the fan the draw-three
        // waste trails, and the tableau takes everything between the two rows.
        //
        // **Unless the board is too short to spend a whole row on them.** A nearly square screen
        // — an unfolded foldable, a tablet — sizes its cards off the width, so a card can be half
        // again as tall as on a phone while the board is no taller: the two rows plus a column of
        // any depth then do not fit, and stock and waste end up against the tableau. There they
        // go back beside the foundations, which costs nothing but the row they were given.
        val portraitLayout = if (isLandscape) {
            null
        } else {
            portraitBoardLayout(maxWidth, maxHeight, state.tableau.maxOf { it.size })
        }
        val stockWasteInTopRow = portraitLayout?.stockWasteAtBottom == false
        val tableauHeightPx = if (isLandscape) {
            boardHeightPx
        } else {
            with(density) { portraitLayout!!.tableauLane.toPx() }
        }

        // Right-handed: stock/waste sit at the edge nearest the dominant (right) hand
        // in both orientations, foundations on the opposite side (`UI_SPEC.md`
        // "Right-Handed Layout"). Left-handed mirrors both groups to the opposite
        // edge; tableau column order and indexes are untouched either way — only
        // where the two flanking pile groups sit changes.
        val mirrored = handedness == Handedness.LEFT

        // Landscape x-coordinates: the width is divided into [LANDSCAPE_SLOTS] equal
        // slots — one per tableau column plus one for each flanking strip — and every
        // pile is centered in its own slot. Card size is usually capped by height rather
        // than width (above), so packing the nine at minimum spacing left the tableau
        // crammed together with all the slack piled at the two edges; spreading them
        // across equal slots puts that slack between the columns instead, and centering
        // each strip's cards keeps the foundations and stock/waste over the middle of
        // the space they own. Which real group (foundations vs stock/waste) takes which
        // end slot flips with [mirrored].
        val landscapeSlotWidthPx = with(density) { maxWidth.toPx() } / LANDSCAPE_SLOTS
        fun landscapeSlotLeftPx(slot: Int): Float = slot * landscapeSlotWidthPx + (landscapeSlotWidthPx - cardWidthPx) / 2f
        val landscapeFoundationsStripPx = landscapeSlotLeftPx(if (mirrored) LANDSCAPE_SLOTS - 1 else 0)
        val landscapeStockWasteStripPx = landscapeSlotLeftPx(if (mirrored) 0 else LANDSCAPE_SLOTS - 1)

        /** Top-left for foundation [index] (0..3): a row in portrait, a column in landscape. */
        fun foundationTopLeftFor(index: Int): Offset = if (isLandscape) {
            Offset(landscapeFoundationsStripPx, index * (cardHeightPx + spacingPx))
        } else {
            // Right-handed: leftmost 4 of the 7-wide row. Mirrored: rightmost 4. The row is
            // theirs alone in portrait — stock and waste moved to the bottom — but the four
            // stay on their own side rather than spreading, so the eye still finds them
            // where it did and the handedness mirror keeps meaning something.
            val column = if (mirrored) TABLEAU_COLUMNS - 4 + index else index
            Offset(boardLeftPx + column * (cardWidthPx + spacingPx), 0f)
        }

        /** Top-left for tableau column [column]: below the top row in portrait, full-height and spread across the middle slots in landscape. */
        fun tableauTopLeftFor(column: Int): Offset =
            if (isLandscape) Offset(landscapeSlotLeftPx(column + 1), 0f) else Offset(boardLeftPx + column * (cardWidthPx + spacingPx), topRowHeightPx)

        var boardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val destinationRects = remember { mutableStateMapOf<PileKey, Rect>() }
        var drag by remember { mutableStateOf<DragPayload?>(null) }
        var invalidFeedback by remember { mutableStateOf<PileKey?>(null) }
        fun markInvalid(key: PileKey) {
            invalidFeedback = key
            onPlaySound(SoundEffect.INVALID)
        }
        var moveAnimation by remember { mutableStateOf<MoveAnimation?>(null) }
        var animationQueue by remember { mutableStateOf<List<MoveAnimation>>(emptyList()) }
        // The board's own copy of state, advanced only as each queued flight
        // finishes — see the class doc on [MoveAnimation.resultState].
        var displayState by remember { mutableStateOf(state) }
        // Keyed on moveAnimation itself, not a single shared instance: a fresh
        // Animatable starts at 0f the instant a new leg is set, in the same
        // composition frame — so the flying overlay renders at the source position
        // from frame one. A shared Animatable left over from the previous leg (at
        // 1f) would otherwise render one frame at the *destination* before the
        // LaunchedEffect's snapTo(0f) coroutine catches up — the card would flash
        // into its final spot, snap back to the source, then animate: registering,
        // reverting, then finally moving.
        val animationProgress = remember(moveAnimation) { Animatable(0f) }

        // New card interactions are held off while any flight is queued or playing:
        // resolving a tap/drag against `state` (always fully current) while the
        // board is still visually catching up to it via `displayState` risks acting
        // on cards that don't look like they're there yet.
        val animationsInFlight = moveAnimation != null || animationQueue.isNotEmpty()
        // The game screen holds the win dialog off until every queued flight —
        // including an automatic-finish sweep — has actually finished playing, not
        // just until the committed state says WON; without reporting this back, the
        // dialog would show the instant the state flips, ahead of the animation.
        LaunchedEffect(animationsInFlight) { onAnimationsInFlightChanged(animationsInFlight) }

        // Mutually exclusive by construction (one Settings toggle picks the mode), so one
        // pulse serves either: a single proven move, or every legal move shown at once.
        val hintAlpha = rememberHintAlpha(hintMove ?: highlightedMoves.ifEmpty { null }, skipAnimations)
        val hintSourceKey = hintMove?.let { sourceKeyFor(it) }
        val guidedHintRunStartIndex: Int? = when (hintMove) {
            is Move.TableauToTableau -> hintMove.fromIndex
            is Move.TableauToFoundation -> displayState.tableau[hintMove.fromColumn].lastIndex
            else -> null
        }
        val highlightedSourceKeys: Set<PileKey> = highlightedMoves.map { sourceKeyFor(it) }.toSet()
        // The lowest starting index per tableau column, when that column has more than one
        // legal run: the most-inclusive span, since a single column can only show one
        // highlighted run (`TableauColumnView` draws one contiguous band, same as guided mode).
        val highlightedTableauFromIndex: Map<Int, Int> = buildMap {
            for (move in highlightedMoves) {
                val entry = when (move) {
                    is Move.TableauToTableau -> move.fromColumn to move.fromIndex
                    is Move.TableauToFoundation -> move.fromColumn to displayState.tableau[move.fromColumn].lastIndex
                    else -> continue
                }
                merge(entry.first, entry.second, ::minOf)
            }
        }

        if (invalidFeedback != null) {
            LaunchedInvalidFeedbackReset { invalidFeedback = null }
        }

        /** (faceDownStepPx, faceUpStepPx) for a tableau column currently holding [columnState]. */
        fun tableauOverlapPx(columnState: List<TableauCard>): Pair<Float, Float> {
            val faceDownCount = columnState.count { !it.faceUp }
            val overlap = computeTableauOverlap(
                availableHeight = with(density) { tableauHeightPx.toDp() },
                cardHeight = cardSize.height,
                faceDownCount = faceDownCount,
                faceUpCount = columnState.size - faceDownCount,
            )
            return with(density) { overlap.faceDownStep.toPx() to overlap.faceUpStep.toPx() }
        }

        /**
         * The exact on-screen top-left for the card that would sit at [indexInColumn]
         * in tableau [column] given [columnState]'s current overlap steps — unlike
         * `destinationRects[TableauColumn(column)]`, which is the whole lane's
         * bounding box, always anchored at the lane's top regardless of how deep the
         * column's stack actually is. Without this, a flight into or out of a
         * partially-filled column visibly jumps to/from the top of the lane instead
         * of the card's real position.
         */
        fun tableauCardTopLeft(column: Int, indexInColumn: Int, columnState: List<TableauCard>): Offset {
            val faceDownCount = columnState.count { !it.faceUp }
            val faceUpCount = columnState.size - faceDownCount
            val (faceDownStepPx, faceUpStepPx) = tableauOverlapPx(columnState)
            // Matches computeTableauOverlap/stepAfterIsFaceDown: the step onto the
            // newly revealed first face-up card uses the face-down band too.
            fun stepAfterIsFaceDown(i: Int) = if (faceUpCount == 0) i < faceDownCount - 1 else i < faceDownCount
            var y = 0f
            for (i in 0 until indexInColumn.coerceAtMost(columnState.size)) {
                y += if (stepAfterIsFaceDown(i)) faceDownStepPx else faceUpStepPx
            }
            return Offset(boardLeftPx + column * (cardWidthPx + spacingPx), topRowHeightPx + y)
        }

        /**
         * Vertical stacking spacing for a flight touching [fromKey] and [toKey]:
         * whichever end is an actual tableau column dictates it, so the flying stack
         * doesn't visibly compress or stretch relative to how it was just shown.
         */
        fun stepPxFor(fromKey: PileKey, fromState: GameState, toKey: PileKey?, toState: GameState): Float {
            (fromKey as? PileKey.TableauColumn)?.let { return tableauOverlapPx(fromState.tableau[it.column]).second }
            (toKey as? PileKey.TableauColumn)?.let { return tableauOverlapPx(toState.tableau[it.column]).second }
            return cardHeightPx * 0.18f
        }

        /**
         * The animation endpoint for [key] in [pileState]. Foundation/waste/stock
         * slots are a single fixed box regardless of content, so their reported
         * bounds are always accurate; a tableau column is not, so it resolves through
         * [tableauCardTopLeft] with [indexHint] (defaulting to "append at the end").
         */
        fun pileTopLeft(pileState: GameState, key: PileKey, indexHint: Int? = null): Offset? = when (key) {
            is PileKey.TableauColumn -> {
                val column = pileState.tableau[key.column]
                tableauCardTopLeft(key.column, indexHint ?: column.size, column)
            }
            else -> destinationRects[key]?.topLeft
        }

        /** Enqueues [anims] to play one at a time, after anything already queued. */
        fun enqueueAnimations(anims: List<MoveAnimation>) {
            if (anims.isEmpty()) return
            if (moveAnimation == null) {
                moveAnimation = anims.first()
                animationQueue = animationQueue + anims.drop(1)
            } else {
                animationQueue = animationQueue + anims
            }
        }

        // Each queued flight plays fully, one at a time, before the next starts: every
        // placement change — a player's move, then each automatic foundation transfer,
        // then an undo's reversal — is its own clearly visible animation, never merged
        // with another into one blurred motion. `displayState` steps to the finishing
        // leg's `resultState` right as it completes. Advancing straight to the next
        // queued leg (rather than through a null in-between step) skips an extra
        // recomposition/effect-relaunch cycle per hop — otherwise every leg of a
        // multi-step cascade picks up a second, redundant pause on top of the single
        // unavoidable frame of launch latency, reading as uneven, stop-start motion.
        LaunchedEffect(moveAnimation) {
            val anim = moveAnimation ?: return@LaunchedEffect
            if (anim.startDelayMs > 0) delay(anim.startDelayMs.toLong())
            // Played right as the card actually starts moving, not back at commit
            // time — otherwise a step still sitting in the queue behind an earlier
            // one would sound off well before its own flight (or, under
            // skipAnimations, its own pause) actually begins.
            anim.sound?.let(onPlaySound)
            animationProgress.animateTo(1f, tween(anim.durationMs, easing = LinearEasing))
            displayState = anim.resultState
            moveAnimation = if (animationQueue.isNotEmpty()) {
                animationQueue.first().also { animationQueue = animationQueue.drop(1) }
            } else {
                null
            }
        }

        LaunchedEffect(undoSignal) {
            val preUndoState = undoSignal ?: return@LaunchedEffect
            val moved = Card.CANONICAL_DECK.mapNotNull { card ->
                val from = pileOf(preUndoState, card)
                val to = pileOf(state, card)
                if (from != to) Triple(card, from, to) else null
            }
            val groups = moved.groupBy { it.second to it.third }
            val anims = groups.entries.mapIndexed { index, (pair, entries) ->
                val (fromKey, toKey) = pair
                val cards = entries.map { it.first }
                // Tableau moves always touch the top of a column, so a group's cards
                // are always the last `cards.size` of it on whichever side it's on.
                val fromIndex = (fromKey as? PileKey.TableauColumn)?.let { preUndoState.tableau[it.column].size - cards.size }
                val toIndex = (toKey as? PileKey.TableauColumn)?.let { state.tableau[it.column].size - cards.size }
                val fromPoint = pileTopLeft(preUndoState, fromKey, fromIndex) ?: Offset.Zero
                val toPoint = pileTopLeft(state, toKey, toIndex) ?: fromPoint
                val stepPx = stepPxFor(fromKey, preUndoState, toKey, state)
                // Only the very last leg settles on the true post-undo state; every
                // earlier leg keeps the board looking exactly as it did before undo
                // started, so nothing appears to change ahead of its own flight.
                val isLast = index == groups.size - 1
                MoveAnimation(
                    cards, fromPoint, toPoint, stepPx, if (isLast) state else preUndoState,
                    durationForFlight(fromPoint, toPoint, density),
                    fromKey = fromKey, fromIndex = fromIndex,
                )
            }
            when {
                skipAnimations -> displayState = state
                anims.isNotEmpty() -> enqueueAnimations(anims)
                else -> displayState = state
            }
            onUndoSignalConsumed()
        }

        val hoveredDestination = drag?.let { payload ->
            val focal = payload.pointerInBoard
            val topSuit = payload.cards.last().suit
            destinationRects.entries.firstOrNull { (_, rect) -> rect.contains(focal) }?.takeIf { (key, _) ->
                payload.legalMoves.any { destinationKeyFor(it, topSuit) == key }
            }?.key
        }

        fun reportBounds(key: PileKey, coordinates: LayoutCoordinates) {
            val board = boardCoordinates ?: return
            if (!coordinates.isAttached) return
            val topLeft = board.localPositionOf(coordinates, Offset.Zero)
            destinationRects[key] = Rect(topLeft, coordinates.size.toSize())
        }

        fun beginDrag(source: DragSource, cards: List<Card>, originTopLeft: Offset, grabOffset: Offset, stepPx: Float) {
            drag = DragPayload(
                source = source,
                cards = cards,
                legalMoves = legalDestinationsForDragSource(state, source),
                originTopLeft = originTopLeft,
                grabOffset = grabOffset,
                pointerInBoard = originTopLeft + grabOffset,
                stepPx = stepPx,
            )
        }

        fun updateDrag(delta: Offset) {
            drag = drag?.let { it.copy(pointerInBoard = it.pointerInBoard + delta) }
        }

        /**
         * Commits [move] (expected legal — every call site resolves it from
         * `legalMoves`/`resolve*Tap` first), then queues a flying-card animation for it
         * and, in turn, for every automatic foundation transfer the commit triggers —
         * each played fully before the next starts. The automation cascade is
         * re-derived locally (via the same pure `:game` functions the reducer itself
         * uses) purely to discover per-step endpoints and intermediate states to
         * animate through; [onCommitMove] remains the sole source of truth for the
         * committed state. Falls back to invalid feedback in the defensive case where
         * the engine disagrees.
         *
         * [dragDropOrigin], when given, is where the dragged card's floating overlay
         * actually was at drop — a drag-committed move's animation starts from there
         * instead of the source pile's resting position, so the card continues
         * smoothly from under the player's finger instead of visibly snapping back to
         * its pile first.
         */
        fun commitWithAnimation(move: Move, invalidKey: PileKey, dragDropOrigin: Offset? = null) {
            val playerCards = movedCardsFor(move, state)
            val playerToKey = destinationKeyFor(move, state)
            val playerFromKey = sourceKeyFor(move)
            val playerFromIndex = when (move) {
                is Move.TableauToTableau -> move.fromIndex
                is Move.TableauToFoundation -> state.tableau[move.fromColumn].lastIndex
                else -> null
            }
            val playerToIndex = (playerToKey as? PileKey.TableauColumn)?.let { state.tableau[it.column].size }
            val playerStepPx = stepPxFor(playerFromKey, state, playerToKey, state)
            val playerFromPoint = dragDropOrigin ?: pileTopLeft(state, playerFromKey, playerFromIndex) ?: Offset.Zero
            val playerToPoint = playerToKey?.let { pileTopLeft(state, it, playerToIndex) } ?: playerFromPoint

            if (!onCommitMove(move)) {
                markInvalid(invalidKey)
                return
            }

            var stepState = applyPlayerTransition(state, move).let { it.copy(moveCount = it.moveCount + 1) }
            val anims = mutableListOf<MoveAnimation>()
            anims += MoveAnimation(
                playerCards, playerFromPoint, playerToPoint, playerStepPx, stepState,
                durationMs = if (skipAnimations) SKIP_ANIMATIONS_FLIGHT_MS else durationForFlight(playerFromPoint, playerToPoint, density),
                sound = SoundEffect.MOVE,
                fromKey = playerFromKey,
                fromIndex = playerFromIndex,
            )
            // Non-zero once a step reveals a face-down card face-up, so the *next*
            // step (if it departs from that same column, i.e. immediately auto-moves
            // the just-revealed card) pauses first instead of departing in the same
            // instant the reveal lands.
            var pendingRevealDelay = if (movesRevealCard(move, state)) REVEAL_TO_AUTO_MOVE_DELAY_MS else 0

            if (automaticMovesEnabled) {
                val (_, transfers) = runAutomaticFoundationCascade(stepState)
                for (transferMove in transfers) {
                    val transferCards = movedCardsFor(transferMove, stepState)
                    val toKey = destinationKeyFor(transferMove, stepState)
                    val fromKey = sourceKeyFor(transferMove)
                    // Automatic transfers always source from the waste top or a
                    // tableau top, so the source index — when tableau — is always
                    // that column's last card.
                    val fromIndex = (fromKey as? PileKey.TableauColumn)?.let { stepState.tableau[it.column].lastIndex }
                    val stepPx = stepPxFor(fromKey, stepState, toKey, stepState)
                    val fromPoint = pileTopLeft(stepState, fromKey, fromIndex) ?: Offset.Zero
                    val revealsNext = movesRevealCard(transferMove, stepState)
                    stepState = applyMove(stepState, transferMove).copy(moveCount = stepState.moveCount + 1)
                    val toPoint = toKey?.let { pileTopLeft(stepState, it) } ?: fromPoint
                    // Under skipAnimations every transfer still gets its own pause —
                    // there is no slide to carry the "one at a time" pacing instead.
                    val startDelay = if (skipAnimations) maxOf(pendingRevealDelay, SKIP_ANIMATIONS_STEP_PAUSE_MS) else pendingRevealDelay
                    anims += MoveAnimation(
                        transferCards, fromPoint, toPoint, stepPx, stepState,
                        durationMs = if (skipAnimations) SKIP_ANIMATIONS_FLIGHT_MS else durationForFlight(fromPoint, toPoint, density),
                        startDelayMs = startDelay,
                        sound = SoundEffect.AUTOMATIC_MOVE,
                        fromKey = fromKey,
                        fromIndex = fromIndex,
                    )
                    pendingRevealDelay = if (revealsNext) REVEAL_TO_AUTO_MOVE_DELAY_MS else 0
                }
            }

            // A provably-solved board is swept to the foundations in full — a
            // separate, rarer reducer path from the per-move safe-card cascade above
            // (`finishAutomaticallyIfReady`, called unconditionally after every
            // commit) — so it's checked independently of `automaticMovesEnabled`,
            // exactly like the real path.
            if (canAutoFinish(stepState)) {
                for (sweepMove in sweepMoves(stepState)) {
                    val sweepCards = movedCardsFor(sweepMove, stepState)
                    val toKey = destinationKeyFor(sweepMove, stepState)
                    val fromKey = sourceKeyFor(sweepMove)
                    val fromIndex = (fromKey as? PileKey.TableauColumn)?.let { stepState.tableau[it.column].lastIndex }
                    val stepPx = stepPxFor(fromKey, stepState, toKey, stepState)
                    val fromPoint = pileTopLeft(stepState, fromKey, fromIndex) ?: Offset.Zero
                    val revealsNext = movesRevealCard(sweepMove, stepState)
                    stepState = applyMove(stepState, sweepMove).copy(moveCount = stepState.moveCount + 1)
                    val toPoint = toKey?.let { pileTopLeft(stepState, it) } ?: fromPoint
                    // The sweep never gets skipAnimations's uniform step pause, but every
                    // transfer keeps the quieter automatic-move cue even at the faster pace.
                    anims += MoveAnimation(
                        sweepCards, fromPoint, toPoint, stepPx, stepState,
                        durationMs = if (skipAnimations) {
                            SKIP_ANIMATIONS_FLIGHT_MS
                        } else {
                            durationForFlight(fromPoint, toPoint, density, AUTO_FINISH_SPEED_MULTIPLIER)
                        },
                        startDelayMs = pendingRevealDelay,
                        sound = SoundEffect.AUTOMATIC_MOVE,
                        fromKey = fromKey,
                        fromIndex = fromIndex,
                    )
                    pendingRevealDelay = if (revealsNext) REVEAL_TO_AUTO_MOVE_DELAY_MS else 0
                }
            }

            enqueueAnimations(anims)
        }

        fun endDrag(sourceKey: PileKey) {
            val payload = drag ?: return
            val destKey = hoveredDestination
            val topSuit = payload.cards.last().suit
            val move = destKey?.let { key -> payload.legalMoves.firstOrNull { destinationKeyFor(it, topSuit) == key } }
            drag = null
            if (move != null) {
                commitWithAnimation(move, sourceKey, dragDropOrigin = payload.pointerInBoard - payload.grabOffset)
            } else if (destKey != null || payload.pointerInBoard != payload.originTopLeft + payload.grabOffset) {
                // Released over the board but not on a legal destination: invalid feedback.
                markInvalid(sourceKey)
            }
        }

        fun cancelDrag() {
            drag = null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { boardCoordinates = it },
        ) {
            // Portrait: foundations (left group in the right-handed layout) and
            // stock/waste (right group) share a top row, tableau columns fill the
            // remaining height below. Landscape: foundations form their own strip at
            // the left edge, stock/waste their own strip at the right edge, tableau
            // centered between them (see foundationTopLeftFor/tableauTopLeftFor).
            for ((index, suit) in Suit.entries.withIndex()) {
                val topLeft = foundationTopLeftFor(index)
                FoundationSlot(
                    suit = suit,
                    state = displayState,
                    cardSize = cardSize,
                    topLeftPx = topLeft,
                    isDraggingAway = drag?.source == DragSource.Foundation(suit) || moveAnimation?.fromKey == PileKey.Foundation(suit),
                    isHighlighted = hoveredDestination == PileKey.Foundation(suit),
                    isInvalid = invalidFeedback == PileKey.Foundation(suit),
                    hintAlpha = if (hintSourceKey == PileKey.Foundation(suit) || PileKey.Foundation(suit) in highlightedSourceKeys) hintAlpha else 0f,
                    traversalIndex = 2f + index,
                    onReportBounds = { reportBounds(PileKey.Foundation(suit), it) },
                    onTap = {
                        if (!animationsInFlight) {
                            // A hint highlighting this exact pile takes over the tap:
                            // its proven destination, not the standard tap-priority pick.
                            val move = if (hintMove != null && hintSourceKey == PileKey.Foundation(suit)) {
                                hintMove
                            } else {
                                resolveFoundationTap(state, suit)
                            }
                            if (move == null) markInvalid(PileKey.Foundation(suit)) else commitWithAnimation(move, PileKey.Foundation(suit))
                        }
                    },
                    onDragStart = { local ->
                        if (!animationsInFlight) {
                            val card = state.foundationTop(suit)
                            if (card != null) {
                                beginDrag(DragSource.Foundation(suit), listOf(card), topLeft, local, cardHeightPx * 0.18f)
                            }
                        }
                    },
                    onDrag = ::updateDrag,
                    onDragEnd = { endDrag(PileKey.Foundation(suit)) },
                    onDragCancel = ::cancelDrag,
                )
            }

            // Stock sits closest to the edge nearest the dominant hand (the "palm"
            // side of that hold); the revealed waste sits just before it — to its
            // left of stock in portrait's shared top row (right-handed) or right of
            // stock (mirrored), above it in landscape's vertical strip either way,
            // since landscape's palm-side distinction is horizontal (which strip),
            // not vertical.
            //
            // The landscape pair is pinned to the bottom of its strip, not the top:
            // stock is the most-tapped pile on the board, and the bottom corner is
            // where the holding hand's thumb already sits.
            val wasteTopLeft: Offset
            val stockTopLeft: Offset
            if (isLandscape) {
                // Room for the draw-three fan, which trails down-right of the waste slot;
                // reserved whatever the draw mode, so the pair sits in the same place in
                // both.
                //
                // Stock takes the *lower* of the two, waste above it: the strip is pinned to
                // the bottom of the board, and of the pair it is stock that gets tapped over
                // and over, so stock is the one that belongs in the corner the thumb rests in.
                val wasteBottomPx = boardHeightPx - cardWidthPx * WASTE_FAN_STEP * 2
                wasteTopLeft = Offset(landscapeStockWasteStripPx, wasteBottomPx - 2 * cardHeightPx - spacingPx)
                stockTopLeft = Offset(landscapeStockWasteStripPx, wasteBottomPx - cardHeightPx)
            } else {
                val stockWasteGroupStart = boardLeftPx + if (mirrored) 0f else (TABLEAU_COLUMNS - 2) * (cardWidthPx + spacingPx)
                // The bottom of the board, above the action bar — or back in the top row beside
                // the foundations where the board is too short to give them a row of their own.
                val rowTop = if (stockWasteInTopRow) {
                    0f
                } else {
                    boardHeightPx - cardHeightPx - cardWidthPx * WASTE_FAN_STEP * 2
                }
                if (mirrored) {
                    stockTopLeft = Offset(stockWasteGroupStart, rowTop)
                    wasteTopLeft = Offset(stockWasteGroupStart + cardWidthPx + spacingPx, rowTop)
                } else {
                    wasteTopLeft = Offset(stockWasteGroupStart, rowTop)
                    stockTopLeft = Offset(stockWasteGroupStart + cardWidthPx + spacingPx, rowTop)
                }
            }

            StockSlot(
                state = displayState,
                cardSize = cardSize,
                topLeftPx = stockTopLeft,
                isInvalid = invalidFeedback == PileKey.Stock,
                hintAlpha = if (hintSourceKey == PileKey.Stock || PileKey.Stock in highlightedSourceKeys) hintAlpha else 0f,
                traversalIndex = 0f,
                onReportBounds = { reportBounds(PileKey.Stock, it) },
                onTap = {
                    if (!animationsInFlight) {
                        val move = if (hintMove != null && hintSourceKey == PileKey.Stock) {
                            hintMove
                        } else {
                            resolveStockTap(state)
                        }
                        if (move == null) markInvalid(PileKey.Stock) else commitWithAnimation(move, PileKey.Stock)
                    }
                },
            )

            WasteSlot(
                state = displayState,
                cardSize = cardSize,
                topLeftPx = wasteTopLeft,
                isDraggingAway = drag?.source == DragSource.Waste || moveAnimation?.fromKey == PileKey.Waste,
                isInvalid = invalidFeedback == PileKey.Waste,
                hintAlpha = if (hintSourceKey == PileKey.Waste || PileKey.Waste in highlightedSourceKeys) hintAlpha else 0f,
                traversalIndex = 1f,
                onReportBounds = { reportBounds(PileKey.Waste, it) },
                onTap = {
                    if (!animationsInFlight) {
                        // A hint highlighting the waste card takes over the tap: it may
                        // send the card to a tableau column rather than the safe-
                        // foundation-first destination the standard priority prefers.
                        val move = if (hintMove != null && hintSourceKey == PileKey.Waste) {
                            hintMove
                        } else {
                            resolveWasteTap(state)
                        }
                        if (move == null) markInvalid(PileKey.Waste) else commitWithAnimation(move, PileKey.Waste)
                    }
                },
                onDragStart = { local ->
                    if (!animationsInFlight) {
                        val card = state.waste.firstOrNull()
                        if (card != null) beginDrag(DragSource.Waste, listOf(card), wasteTopLeft, local, cardHeightPx * 0.18f)
                    }
                },
                onDrag = ::updateDrag,
                onDragEnd = { endDrag(PileKey.Waste) },
                onDragCancel = ::cancelDrag,
            )

            for (column in 0 until TABLEAU_COLUMNS) {
                val columnTopLeft = tableauTopLeftFor(column)
                TableauColumnView(
                    column = column,
                    state = displayState,
                    cardSize = cardSize,
                    topLeftPx = columnTopLeft,
                    laneHeightPx = tableauHeightPx,
                    drag = drag,
                    animatingAwayFromIndex = moveAnimation?.takeIf { it.fromKey == PileKey.TableauColumn(column) }?.fromIndex,
                    isHighlighted = hoveredDestination == PileKey.TableauColumn(column),
                    isInvalid = invalidFeedback == PileKey.TableauColumn(column),
                    hintAlpha = if (hintSourceKey == PileKey.TableauColumn(column) || PileKey.TableauColumn(column) in highlightedSourceKeys) hintAlpha else 0f,
                    hintRunStartIndex = guidedHintRunStartIndex ?: highlightedTableauFromIndex[column],
                    traversalIndex = 6f + column,
                    onReportBounds = { reportBounds(PileKey.TableauColumn(column), it) },
                    onTapCard = { fromIndex ->
                        if (!animationsInFlight) {
                            // Tapping exactly the card a hint highlights (the sequence's
                            // start) takes over the tap: its proven destination, not
                            // the standard rightward/foundation/leftward priority.
                            val move = if (hintMove != null && hintSourceKey == PileKey.TableauColumn(column) && fromIndex == guidedHintRunStartIndex) {
                                hintMove
                            } else {
                                resolveTableauTap(state, column, fromIndex)
                            }
                            if (move == null) markInvalid(PileKey.TableauColumn(column)) else commitWithAnimation(move, PileKey.TableauColumn(column))
                        }
                    },
                    onDragStartCard = { fromIndex, local, cardTopLeft ->
                        if (!animationsInFlight) {
                            val cards = state.tableau[column].subList(fromIndex, state.tableau[column].size).map { it.card }
                            val isTopCard = fromIndex == state.tableau[column].lastIndex
                            val stepPx = tableauOverlapPx(state.tableau[column]).second
                            beginDrag(DragSource.Tableau(column, fromIndex, isTopCard), cards, cardTopLeft, local, stepPx)
                        }
                    },
                    onDrag = ::updateDrag,
                    onDragEnd = { endDrag(PileKey.TableauColumn(column)) },
                    onDragCancel = ::cancelDrag,
                )
            }

            // Floating overlay for the card(s) currently being dragged, drawn above every pile.
            drag?.let { payload ->
                val topLeft = payload.pointerInBoard - payload.grabOffset
                val stackHeightPx = cardHeightPx + (payload.cards.size - 1) * payload.stepPx
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset(topLeft.x.toInt(), topLeft.y.toInt()) }
                        .then(with(density) { Modifier.size(cardSize.width, stackHeightPx.toDp()) }),
                ) {
                    payload.cards.forEachIndexed { i, card ->
                        drawCardFace(Offset(0f, i * payload.stepPx), Size(cardWidthPx, cardHeightPx), card, cardColors)
                    }
                }
            }

            // Floating overlay animating a committed move's card(s) from source to
            // destination, so the action is always visibly registered.
            moveAnimation?.let { anim ->
                val currentTopLeft = lerp(anim.fromTopLeft, anim.toTopLeft, animationProgress.value)
                val stackHeightPx = cardHeightPx + (anim.cards.size - 1) * anim.stepPx
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset(currentTopLeft.x.toInt(), currentTopLeft.y.toInt()) }
                        .then(with(density) { Modifier.size(cardSize.width, stackHeightPx.toDp()) }),
                ) {
                    anim.cards.forEachIndexed { i, card ->
                        drawCardFace(Offset(0f, i * anim.stepPx), Size(cardWidthPx, cardHeightPx), card, cardColors)
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.tapOnly(onTap: () -> Unit): Modifier {
    val currentOnTap by rememberUpdatedState(onTap)
    return pointerInput(Unit) { detectTapGestures(onTap = { currentOnTap() }) }
}

@Composable
private fun LaunchedInvalidFeedbackReset(onReset: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(220)
        onReset()
    }
}

@Composable
private fun FoundationSlot(
    suit: Suit,
    state: GameState,
    cardSize: CardSize,
    topLeftPx: Offset,
    isDraggingAway: Boolean,
    isHighlighted: Boolean,
    isInvalid: Boolean,
    hintAlpha: Float,
    traversalIndex: Float,
    onReportBounds: (LayoutCoordinates) -> Unit,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val cardColors = LocalAppColors.current.card
    val actualTopCard = state.foundationTop(suit)
    val description = if (actualTopCard != null) {
        stringResource(R.string.cd_foundation_top_card, rankSpokenLabel(actualTopCard.rank), suitSpokenLabel(actualTopCard.suit))
    } else {
        stringResource(R.string.cd_foundation_empty, suitSpokenLabel(suit))
    }
    val moveToTableauLabel = stringResource(R.string.cd_move_to_tableau)
    Box(
        modifier = Modifier
            .cardSizeOffset(topLeftPx, cardSize)
            .onGloballyPositioned(onReportBounds)
            .semantics {
                contentDescription = description
                role = Role.Button
                this.traversalIndex = traversalIndex
                if (actualTopCard != null) onClick(label = moveToTableauLabel) { onTap(); true }
            }
            .cardPointerInput(enabled = actualTopCard != null, onTap = onTap, onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd, onDragCancel = onDragCancel),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when {
                actualTopCard != null && !isDraggingAway -> drawCardFace(Offset.Zero, size, actualTopCard, cardColors)
                else -> drawEmptySlot(Offset.Zero, size, cardColors, suit.symbol.toString())
            }
            if (isHighlighted) drawDestinationHighlight(Offset.Zero, size, cardColors.destination)
            if (isInvalid) drawDestinationHighlight(Offset.Zero, size, cardColors.invalid)
            if (hintAlpha > 0f) drawHintHighlight(Offset.Zero, size, hintAlpha, cardColors)
        }
    }
}

@Composable
private fun StockSlot(
    state: GameState,
    cardSize: CardSize,
    topLeftPx: Offset,
    isInvalid: Boolean,
    hintAlpha: Float,
    traversalIndex: Float,
    onReportBounds: (LayoutCoordinates) -> Unit,
    onTap: () -> Unit,
) {
    val cardColors = LocalAppColors.current.card
    val description = when {
        state.stock.isNotEmpty() -> stringResource(R.string.cd_stock_with_cards, state.stock.size)
        state.waste.isNotEmpty() -> stringResource(R.string.cd_stock_empty_recycles)
        else -> stringResource(R.string.cd_stock_empty)
    }
    val drawLabel = stringResource(R.string.cd_draw_action)
    Box(
        modifier = Modifier
            .cardSizeOffset(topLeftPx, cardSize)
            .onGloballyPositioned(onReportBounds)
            .semantics {
                contentDescription = description
                role = Role.Button
                this.traversalIndex = traversalIndex
                onClick(label = drawLabel) { onTap(); true }
            }
            .tapOnly(onTap),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (state.stock.isNotEmpty()) drawCardBack(Offset.Zero, size, cardColors) else drawEmptySlot(Offset.Zero, size, cardColors)
            if (isInvalid) drawDestinationHighlight(Offset.Zero, size, cardColors.invalid)
            if (hintAlpha > 0f) drawHintHighlight(Offset.Zero, size, hintAlpha, cardColors)
        }
    }
}

@Composable
private fun WasteSlot(
    state: GameState,
    cardSize: CardSize,
    topLeftPx: Offset,
    isDraggingAway: Boolean,
    isInvalid: Boolean,
    hintAlpha: Float,
    traversalIndex: Float,
    onReportBounds: (LayoutCoordinates) -> Unit,
    onTap: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val cardColors = LocalAppColors.current.card
    val actualTopCard = state.waste.firstOrNull()
    val description = if (actualTopCard != null) {
        stringResource(R.string.cd_waste_card, rankSpokenLabel(actualTopCard.rank), suitSpokenLabel(actualTopCard.suit))
    } else {
        stringResource(R.string.cd_waste_empty)
    }
    val moveCardLabel = stringResource(R.string.cd_move_card)
    Box(
        modifier = Modifier
            .cardSizeOffset(topLeftPx, cardSize)
            .onGloballyPositioned(onReportBounds)
            .semantics {
                contentDescription = description
                role = Role.Button
                this.traversalIndex = traversalIndex
                if (actualTopCard != null) onClick(label = moveCardLabel) { onTap(); true }
            }
            .cardPointerInput(enabled = actualTopCard != null, onTap = onTap, onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd, onDragCancel = onDragCancel),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (actualTopCard != null && !isDraggingAway) {
                // Draw-three shows up to 3 waste cards fanned (the actually playable
                // one always on top, at the same untouched Offset.Zero every other
                // pile/hit-test/animation-endpoint computation already expects) so a
                // batch of drawn cards reads as a stack, not a single flat card
                // (`docs/games/klondike/DESIGN.md` "Draw-Three Mode"). Draw-one never has more than
                // one card to show here anyway, so this is a no-op for it.
                val fanCount = if (state.drawMode == DrawMode.THREE) minOf(3, state.waste.size) else 1
                for (i in (fanCount - 1) downTo 1) {
                    val fanOffset = size.width * WASTE_FAN_STEP * i
                    drawCardFace(Offset(fanOffset, fanOffset), size, state.waste[i], cardColors)
                }
                drawCardFace(Offset.Zero, size, actualTopCard, cardColors)
            } else {
                drawEmptySlot(Offset.Zero, size, cardColors)
            }
            if (isInvalid) drawDestinationHighlight(Offset.Zero, size, cardColors.invalid)
            if (hintAlpha > 0f) drawHintHighlight(Offset.Zero, size, hintAlpha, cardColors)
        }
    }
}

@Composable
private fun TableauColumnView(
    column: Int,
    state: GameState,
    cardSize: CardSize,
    topLeftPx: Offset,
    laneHeightPx: Float,
    drag: DragPayload?,
    animatingAwayFromIndex: Int?,
    isHighlighted: Boolean,
    isInvalid: Boolean,
    hintAlpha: Float,
    hintRunStartIndex: Int?,
    traversalIndex: Float,
    onReportBounds: (LayoutCoordinates) -> Unit,
    onTapCard: (fromIndex: Int) -> Unit,
    onDragStartCard: (fromIndex: Int, local: Offset, cardTopLeft: Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val cardColors = LocalAppColors.current.card
    val fullColumn = state.tableau[column]
    // Whichever is live: a drag lifts its cards the moment it starts, before there is any
    // MoveAnimation to speak of; a committed flight (tap or a just-ended drag) has no drag
    // left by the time it plays, but still owns exactly one departing pile. The two never
    // overlap, so which one supplies the index never matters.
    val hiddenSuffixStart = (drag?.source as? DragSource.Tableau)
        ?.takeIf { it.column == column }
        ?.fromIndex
        ?: animatingAwayFromIndex
    // Used for spacing/hint-bounds math only, as if the dragged sequence (shown instead as
    // the floating overlay) weren't there. The render loop below must NOT drop to
    // iterating only this list — see the comment on isHidden.
    val visibleColumn = if (hiddenSuffixStart != null) fullColumn.take(hiddenSuffixStart) else fullColumn

    val faceDownCount = visibleColumn.count { !it.faceUp }
    val faceUpCount = visibleColumn.size - faceDownCount
    val density = LocalDensity.current
    val overlapPx = with(density) {
        val overlap = computeTableauOverlap(
            availableHeight = laneHeightPx.toDp(),
            cardHeight = cardSize.height,
            faceDownCount = faceDownCount,
            faceUpCount = faceUpCount,
        )
        Pair(overlap.faceDownStep.toPx(), overlap.faceUpStep.toPx())
    }

    /**
     * True when the step after card [i] lands on the face-down band: the card above
     * that step is face-down, including the step onto the newly revealed first
     * face-up card (matches [computeTableauOverlap]'s `faceDownSteps`).
     */
    fun stepAfterIsFaceDown(i: Int): Boolean = if (faceUpCount == 0) i < faceDownCount - 1 else i < faceDownCount

    /** Top-of-card y for [idx] within [visibleColumn], same accumulation as the render loop below. */
    fun yAtIndex(idx: Int): Float {
        var y = 0f
        for (i in 0 until idx.coerceIn(0, visibleColumn.size)) {
            y += if (stepAfterIsFaceDown(i)) overlapPx.first else overlapPx.second
        }
        return y
    }

    Box(
        modifier = Modifier
            .laneOffset(topLeftPx, cardSize.width, laneHeightPx)
            .onGloballyPositioned(onReportBounds),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // An empty column shows no placeholder border — just bare space.
            if (isHighlighted) drawDestinationHighlight(Offset.Zero, size.copy(height = cardSize.height.toPx()), cardColors.destination)
            if (isInvalid) drawDestinationHighlight(Offset.Zero, size.copy(height = cardSize.height.toPx()), cardColors.invalid)
            // The hint highlights only the substack that would actually move — from
            // where its sequence starts down to the bottom of the column — not the whole
            // column, and never the destination (`UI_SPEC.md` "Hint").
            if (hintAlpha > 0f && visibleColumn.isNotEmpty()) {
                val topY = yAtIndex(hintRunStartIndex ?: 0)
                val bottomY = yAtIndex(visibleColumn.size - 1) + cardSize.height.toPx()
                drawHintHighlight(Offset(0f, topY), size.copy(height = (bottomY - topY).coerceAtLeast(cardSize.height.toPx())), hintAlpha, cardColors)
            }
        }

        var y = 0f
        // Iterate the full column, not visibleColumn: visibleColumn exists only to
        // drive the spacing/hint math above as if the dragged run weren't there.
        // Actually dropping the dragged card's Box out of this loop — rather than
        // just hiding what it draws — removes it from composition entirely, which
        // tears down the very pointerInput coroutine that started this gesture,
        // silently, before it ever calls onDragEnd/onDragCancel. The drag payload
        // was then stranded forever: this is what made dragging a tableau card onto
        // a foundation (or anywhere) never actually commit. Every card must stay in
        // the exact same loop position across recompositions for Compose to recognize
        // it as the same node and keep its coroutine alive — a Box added elsewhere
        // for the same card is a different node with no memory of the gesture.
        fullColumn.forEachIndexed { index, tableauCard ->
            val isHidden = hiddenSuffixStart != null && index >= hiddenSuffixStart
            val cardTopLeft = Offset(0f, y)
            // The step after this card is a face-down step whenever this card itself
            // is face-down — including the transition onto the newly revealed first
            // face-up card (matches computeTableauOverlap).
            val step = if (!isHidden && stepAfterIsFaceDown(index)) overlapPx.first else overlapPx.second
            val isLast = index == visibleColumn.lastIndex
            val position = index + 1
            val cardDescription = if (tableauCard.faceUp) {
                stringResource(
                    R.string.cd_tableau_card,
                    rankSpokenLabel(tableauCard.card.rank),
                    suitSpokenLabel(tableauCard.card.suit),
                    column + 1,
                    position,
                    visibleColumn.size,
                )
            } else {
                stringResource(R.string.cd_tableau_face_down, column + 1, position, visibleColumn.size)
            }
            val moveLabel = stringResource(R.string.cd_move)
            Box(
                modifier = Modifier
                    // Offset relative to this column's own Box, which is already placed
                    // at topLeftPx — do not add topLeftPx again here.
                    .cardSizeOffset(cardTopLeft, cardSize)
                    .semantics {
                        contentDescription = cardDescription
                        role = Role.Button
                        if (!tableauCard.faceUp) disabled()
                        if (tableauCard.faceUp) onClick(label = moveLabel) { onTapCard(index); true }
                    }
                    .cardPointerInput(
                        enabled = tableauCard.faceUp,
                        onTap = { onTapCard(index) },
                        onDragStart = { local -> onDragStartCard(index, local, topLeftPx + cardTopLeft) },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    ),
            ) {
                if (!isHidden) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (tableauCard.faceUp) {
                            drawCardFace(Offset.Zero, size, tableauCard.card, cardColors)
                        } else {
                            drawCardBack(Offset.Zero, size, cardColors)
                        }
                    }
                }
            }
            if (!isHidden && !isLast) y += step
        }
    }
}

private fun Modifier.cardSizeOffset(topLeftPx: Offset, cardSize: CardSize): Modifier = composedOffsetSize(topLeftPx, cardSize.width, cardSize.height)

private fun Modifier.laneOffset(topLeftPx: Offset, width: Dp, heightPx: Float): Modifier = this
    .offset { IntOffset(topLeftPx.x.toInt(), topLeftPx.y.toInt()) }
    .layout { measurable, constraints ->
        val widthPx = width.toPx().toInt()
        val heightPxInt = heightPx.toInt().coerceAtLeast(0)
        val placeable = measurable.measure(
            constraints.copy(minWidth = widthPx, maxWidth = widthPx, minHeight = heightPxInt, maxHeight = heightPxInt),
        )
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

private fun Modifier.composedOffsetSize(topLeftPx: Offset, width: Dp, height: Dp): Modifier = this
    .offset { IntOffset(topLeftPx.x.toInt(), topLeftPx.y.toInt()) }
    .layout { measurable, constraints ->
        val widthPx = width.toPx().toInt()
        val heightPx = height.toPx().toInt()
        val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx, minHeight = heightPx, maxHeight = heightPx))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

