package org.finiteplay.freecell.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.card.drawCardFace
import org.finiteplay.core.ui.card.drawDestinationHighlight
import org.finiteplay.core.ui.card.drawEmptySlot
import org.finiteplay.core.ui.card.drawHintHighlight
import org.finiteplay.core.ui.sound.SoundEffect
import org.finiteplay.core.ui.gesture.cardPointerInput
import org.finiteplay.core.ui.layout.FinitePlayLogo
import org.finiteplay.core.ui.theme.CardColors
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.freecell.game.FREECELL_GEOMETRY
import org.finiteplay.freecell.game.computeFreeCellCardSize
import org.finiteplay.freecell.game.freeCellCardHeightFittingTallestColumn
import org.finiteplay.freecell.game.freeCellCardTopOffsets
import org.finiteplay.freecell.game.freeCellColumnSpacing
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.resolveFreeCellTap
import org.finiteplay.freecell.rules.resolveTableauTap
import org.finiteplay.freecell.rules.runAutomaticFoundationCascade
import org.finiteplay.solitaire.ui.CardSize

/** Where a lifted card or sequence came from — the source half of a drag gesture. */
private sealed class DragSource {
    data class Tableau(val column: Int, val fromIndex: Int) : DragSource()
    data class FreeCell(val cell: Int) : DragSource()
}

/** A card or sequence lifted off [source], following the pointer. */
private data class DragInfo(val source: DragSource, val cards: List<Card>, val grabOffset: Offset, val pointerInBoard: Offset)

/** Where a hinted move's own cards currently sit — the source half of [Move], for highlighting. */
private sealed class HintSource {
    data class Tableau(val column: Int, val fromIndex: Int) : HintSource()
    data class FreeCell(val cell: Int) : HintSource()
}

/** Where [move] would lift its card(s) from — a foundation move's own source is still a real
 * tableau column or free cell, so this always has an answer. */
private fun hintSourceOf(state: FreeCellState, move: Move): HintSource = when (move) {
    is Move.TableauToTableau -> HintSource.Tableau(move.fromColumn, move.fromIndex)
    is Move.TableauToFreeCell -> HintSource.Tableau(move.fromColumn, state.tableau[move.fromColumn].lastIndex)
    is Move.TableauToFoundation -> HintSource.Tableau(move.fromColumn, state.tableau[move.fromColumn].lastIndex)
    is Move.FreeCellToTableau -> HintSource.FreeCell(move.cell)
    is Move.FreeCellToFoundation -> HintSource.FreeCell(move.cell)
}

/** Identifies one pile for animation endpoints and departure-hiding — mirrors Klondike's own
 * `PileKey` (`games/klondike/app/.../ui/game/Board.kt`), narrowed to FreeCell's three pile kinds. */
private sealed class PileKey {
    data class TableauColumn(val column: Int) : PileKey()
    data class FreeCell(val cell: Int) : PileKey()
    data class Foundation(val suit: Suit) : PileKey()
}

/**
 * A short flying-card animation for one step of a committed transaction (the player's own move,
 * or one automatic foundation transfer, whether from the ordinary cascade or the automatic-finish
 * sweep) — Klondike's own `MoveAnimation`, narrowed: FreeCell has no face-down cards to reveal and
 * a foundation is never a flight's source (there is no foundation withdrawal,
 * `docs/games/freecell/TODO.md`), so no reveal-delay concept carries over. [sound] does carry
 * over, played the instant this leg's flight actually starts, exactly as Klondike's own does.
 *
 * @param resultState the board exactly as it should look once this step finishes — [FreeCellBoard]
 *   only ever renders a fully-consistent [FreeCellState] (`displayState`), stepping to this value
 *   the instant the flight completes rather than jumping straight to the transaction's final state.
 * @param fromKey/fromIndex which pile (and, for a tableau column, which index onward) this leg's
 *   cards are actually departing, so the static pile render can hide them for the flight's whole
 *   duration instead of showing them at rest under an identical flying copy.
 * @param onComplete run once this leg's flight actually lands — used only by the automatic-finish
 *   sweep's last leg, to release [FreeCellViewModel.isAutoFinishing] exactly when the player can
 *   see the board is actually done moving, not the instant the sweep's own search and commit
 *   finish.
 */
private data class MoveAnimation(
    val cards: List<Card>,
    val fromTopLeft: Offset,
    val toTopLeft: Offset,
    val stepPx: Float,
    val resultState: FreeCellState,
    val durationMs: Int,
    val startDelayMs: Int = 0,
    val sound: SoundEffect? = null,
    val fromKey: PileKey? = null,
    val fromIndex: Int? = null,
    val onComplete: (() -> Unit)? = null,
)

/** Which pile a player's or automatic move departs from. */
private fun sourceKeyFor(move: Move): PileKey = when (move) {
    is Move.TableauToTableau -> PileKey.TableauColumn(move.fromColumn)
    is Move.TableauToFreeCell -> PileKey.TableauColumn(move.fromColumn)
    is Move.TableauToFoundation -> PileKey.TableauColumn(move.fromColumn)
    is Move.FreeCellToTableau -> PileKey.FreeCell(move.cell)
    is Move.FreeCellToFoundation -> PileKey.FreeCell(move.cell)
}

/** Which index in its source tableau column [move] departs from, or null for a free-cell source
 * (a free cell holds at most one card, so hiding it is all-or-nothing). */
private fun sourceIndexFor(move: Move, state: FreeCellState): Int? = when (move) {
    is Move.TableauToTableau -> move.fromIndex
    is Move.TableauToFreeCell -> state.tableau[move.fromColumn].lastIndex
    is Move.TableauToFoundation -> state.tableau[move.fromColumn].lastIndex
    is Move.FreeCellToTableau, is Move.FreeCellToFoundation -> null
}

/** Which pile [move] lands on, resolved against [state] (read before the move applies) for a
 * foundation destination's suit. Unlike Klondike's own moves, every FreeCell move has a real
 * destination — there is no draw or recycle to return null for. */
private fun destinationKeyFor(move: Move, state: FreeCellState): PileKey = when (move) {
    is Move.TableauToTableau -> PileKey.TableauColumn(move.toColumn)
    is Move.TableauToFreeCell -> PileKey.FreeCell(move.cell)
    is Move.TableauToFoundation -> PileKey.Foundation(state.tableau[move.fromColumn].last().suit)
    is Move.FreeCellToTableau -> PileKey.TableauColumn(move.toColumn)
    is Move.FreeCellToFoundation -> PileKey.Foundation(state.freeCells[move.cell]!!.suit)
}

/** The card(s) [move] relocates, read from [state] *before* the move is applied. */
private fun movedCardsFor(move: Move, state: FreeCellState): List<Card> = when (move) {
    is Move.TableauToTableau -> state.tableau[move.fromColumn].subList(move.fromIndex, state.tableau[move.fromColumn].size)
    is Move.TableauToFreeCell -> listOf(state.tableau[move.fromColumn].last())
    is Move.TableauToFoundation -> listOf(state.tableau[move.fromColumn].last())
    is Move.FreeCellToTableau -> listOfNotNull(state.freeCells[move.cell])
    is Move.FreeCellToFoundation -> listOfNotNull(state.freeCells[move.cell])
}

/** Which pile [state] currently holds [card] in, for diffing two states around an undo. */
private fun pileOf(state: FreeCellState, card: Card): PileKey {
    for (column in state.tableau.indices) {
        if (card in state.tableau[column]) return PileKey.TableauColumn(column)
    }
    val cell = state.freeCells.indexOf(card)
    if (cell >= 0) return PileKey.FreeCell(cell)
    return PileKey.Foundation(card.suit)
}

// Constant speed rather than a fixed duration, so a short hop and a corner-to-corner flight read
// as the same motion instead of the same clock time — Klondike's own reasoning and numbers,
// carried over "without change" (`docs/games/freecell/UI_SPEC.md` "Motion").
private const val MOVE_SPEED_DP_PER_MS = 1.6f
private const val MIN_MOVE_ANIMATION_MS = 80
private const val MAX_MOVE_ANIMATION_MS = 450

/** The automatic-finish sweep plays this many times the usual speed: the player has no
 * remaining decisions to watch for (`UI_SPEC.md` "Motion", inherited from Klondike's own). */
private const val AUTO_FINISH_SPEED_MULTIPLIER = 3f

/** The "instant" flight duration under Skip Animations: no perceptible slide, but still a real
 * step the queue plays through one at a time. */
private const val SKIP_ANIMATIONS_FLIGHT_MS = 0

/** Stands in for a cascade transfer's own slide under Skip Animations, so a multi-card cascade
 * still reads as a sequence of individual placements rather than one unexplained jump. Never
 * applied to the automatic-finish sweep, which carries no pause at any speed. */
private const val SKIP_ANIMATIONS_STEP_PAUSE_MS = 150

/** Flight duration for a straight line from [from] to [to] at [MOVE_SPEED_DP_PER_MS] times [speedMultiplier]. */
private fun durationForFlight(from: Offset, to: Offset, density: Density, speedMultiplier: Float = 1f): Int {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val distanceDp = with(density) { sqrt(dx * dx + dy * dy).toDp().value }
    val minMs = (MIN_MOVE_ANIMATION_MS / speedMultiplier).toInt()
    val maxMs = (MAX_MOVE_ANIMATION_MS / speedMultiplier).toInt()
    return (distanceDp / (MOVE_SPEED_DP_PER_MS * speedMultiplier)).toInt().coerceIn(minMs, maxMs)
}

/**
 * Alpha for the hint highlight: two 220ms pulses down to near-zero and back to full, then a
 * steady, still-prominent highlight so the suggested cards stay identifiable
 * (`docs/games/freecell/UI_SPEC.md` "Hint"). Zero, and no pulse, when [key] is null or
 * [skipAnimations] is set. Mirrors Klondike's own `rememberHintAlpha`; [key] is whatever the
 * caller wants this pulse to restart on — a single guided [Move] for the proven-winning-move
 * mode, or the whole highlighted list for the highlight-every-legal-move mode — never both at
 * once, so one shared pulse serves either.
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
    }
    return alpha.value
}

/** Clears a shown invalid highlight after a brief flash — Klondike's own timing and shape. */
@Composable
private fun LaunchedInvalidFeedbackReset(onReset: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(220)
        onReset()
    }
}

/**
 * The board: four free cells and four foundations in a top row, eight tableau columns below.
 *
 * Tap resolves through `resolveTableauTap`/`resolveFreeCellTap`. Drag starts once the pointer
 * travels past touch slop, in sibling `pointerInput` blocks that disambiguate by distance rather
 * than by holding — `core:ui`'s `cardPointerInput` and its own doc comment record why a
 * long-press-first draft breaks an ordinary drag.
 *
 * Every committed move — tap, drag, the automatic cascade that follows either, and the automatic
 * finish — plays a short flying-card animation from its source pile to its destination
 * (`docs/games/freecell/UI_SPEC.md` "Motion"), rendered from `displayState`, a copy of the
 * ViewModel's own committed state that only steps forward as each queued flight actually lands.
 * This mirrors Klondike's own `Board` (`games/klondike/app/.../ui/game/Board.kt`), adapted for a
 * simpler board (no face-down cards, no waste/stock, no withdrawal from a foundation) and for a
 * finish search that — unlike Klondike's, which is fast enough to run inline — has to run off the
 * main thread (`FreeCellViewModel.runAutoFinishIfAvailable`'s own doc explains why): the automatic
 * finish's animation is driven by [FreeCellViewModel.pendingAutoFinish], set only once the search
 * and its commit have already finished, rather than computed synchronously inside this file the
 * way the ordinary cascade is.
 *
 * A `LaunchedEffect(state)` fallback snaps `displayState` straight to the ViewModel's own state
 * whenever nothing is animating and the two have drifted apart — the only path this actually
 * takes is a state change this composable did not itself trigger an animation for (a debug
 * fixture load, a restore, or a test driving [FreeCellViewModel.dragMove] directly rather than a
 * simulated gesture, as `WinTest` deliberately does) — so such a change is still shown, just
 * without a flight to play.
 */
@Composable
fun FreeCellBoard(
    viewModel: FreeCellViewModel,
    modifier: Modifier = Modifier,
    onAnimationsInFlightChanged: (Boolean) -> Unit = {},
    onPlaySound: (SoundEffect) -> Unit = {},
) {
    val state = viewModel.session.state
    val colors = LocalAppColors.current.card
    val skipAnimations = !viewModel.settings.animationsEnabled

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        FinitePlayLogo(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            widthFraction = 0.45f,
        )

        val widthFitCard = computeFreeCellCardSize(maxWidth)
        val topRowHeight = widthFitCard.height
        val tableauLane = (maxHeight - topRowHeight - 24.dp).coerceAtLeast(widthFitCard.height)

        // The tallest column *currently on the board*, not the 19-card ceiling the rules allow
        // (`docs/games/freecell/UI_SPEC.md` "Board Geometry") — an ordinary game keeps large
        // cards, and only a column that has genuinely grown deep gives way.
        val dealtDeepest = state.tableau.maxOf { it.size }.coerceAtLeast(1)
        val heightFitHeight = freeCellCardHeightFittingTallestColumn(tableauLane, dealtDeepest)
        val cardWidth = minOf(widthFitCard.width, heightFitHeight / FREECELL_GEOMETRY.cardWidthToHeightRatio)
            .coerceAtLeast(FREECELL_GEOMETRY.minCardWidth)
        val cardSize = CardSize(cardWidth, cardWidth * FREECELL_GEOMETRY.cardWidthToHeightRatio)
        val spacing = freeCellColumnSpacing(maxWidth, cardSize.width)

        var boardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val columnRects = remember { mutableStateMapOf<Int, Rect>() }
        val freeCellRects = remember { mutableStateMapOf<Int, Rect>() }
        val foundationRects = remember { mutableStateMapOf<Suit, Rect>() }
        var drag by remember { mutableStateOf<DragInfo?>(null) }
        // The pile a rejected tap or drop just departed from, briefly highlighted — Klondike's
        // own `invalidFeedback`/`markInvalid`.
        var invalidFeedback by remember { mutableStateOf<PileKey?>(null) }
        fun markInvalid(key: PileKey) {
            invalidFeedback = key
            onPlaySound(SoundEffect.INVALID)
        }
        if (invalidFeedback != null) {
            LaunchedInvalidFeedbackReset { invalidFeedback = null }
        }

        var moveAnimation by remember { mutableStateOf<MoveAnimation?>(null) }
        var animationQueue by remember { mutableStateOf<List<MoveAnimation>>(emptyList()) }
        // This board's own copy of state, advanced only as each queued flight finishes — see the
        // class doc on [MoveAnimation.resultState].
        var displayState by remember { mutableStateOf(state) }
        // Keyed on moveAnimation itself, not a single shared instance, so a fresh leg's overlay
        // renders at its own source position from frame one (Klondike's own `Board` explains the
        // one-frame flash a shared Animatable would otherwise cause).
        val animationProgress = remember(moveAnimation) { Animatable(0f) }
        val animationsInFlight = moveAnimation != null || animationQueue.isNotEmpty()
        LaunchedEffect(animationsInFlight) { onAnimationsInFlightChanged(animationsInFlight) }

        val inputEnabled = !viewModel.isAutoFinishing && !animationsInFlight
        val hintMove = viewModel.hint
        val highlightedMoves = viewModel.legalMoveHighlights
        // Mutually exclusive by construction (one Settings toggle picks the mode), so one pulse
        // serves either: a single proven move, or every legal move shown at once.
        val hintAlpha = rememberHintAlpha(hintMove ?: highlightedMoves.ifEmpty { null }, skipAnimations)
        val hintSource = hintMove?.let { hintSourceOf(state, it) }
        val highlightedSources: Set<HintSource> = highlightedMoves.map { hintSourceOf(state, it) }.toSet()
        // The lowest starting index per tableau column, when that column has more than one legal
        // run: the most-inclusive span, since a column can only show one highlighted band.
        val highlightedTableauFromIndex: Map<Int, Int> = buildMap {
            for (source in highlightedSources) {
                if (source is HintSource.Tableau) merge(source.column, source.fromIndex, ::minOf)
            }
        }
        val density = LocalDensity.current
        val cardWidthPx = with(density) { cardSize.width.toPx() }

        /** The overlap step (px) between consecutive cards in a tableau column holding [count] cards. */
        fun tableauStepPx(count: Int): Float {
            if (count < 2) return with(density) { cardSize.height.toPx() } * 0.18f
            val offsets = freeCellCardTopOffsets(count, tableauLane, cardSize.height)
            return with(density) { (offsets[1] - offsets[0]).toPx() }
        }

        /** Vertical stacking spacing for a flight touching [fromKey] (sized [fromCount]) and
         * [toKey] (sized [toCount]): whichever end is a tableau column dictates it, so the flying
         * stack doesn't visibly compress or stretch relative to how it was just shown. */
        fun stepPxFor(fromKey: PileKey, fromCount: Int, toKey: PileKey, toCount: Int): Float {
            (fromKey as? PileKey.TableauColumn)?.let { return tableauStepPx(fromCount) }
            (toKey as? PileKey.TableauColumn)?.let { return tableauStepPx(toCount) }
            return with(density) { cardSize.height.toPx() } * 0.18f
        }

        /**
         * The animation endpoint for [key] as it stands in [pileState]. A free cell or foundation
         * is a single fixed box regardless of content, so its reported bounds are always
         * accurate; a tableau column is not, so it resolves the exact card position at
         * [indexHint] (defaulting to "append at the end") against [pileState]'s own column size —
         * matching Klondike's own `pileTopLeft`, including its use of the *pre*-move column size
         * for an arrival point (the flight's landing spot is computed under the spacing the
         * column had before the new card(s) joined it; `resultState` then takes over with the
         * tightened spacing the instant the flight lands).
         */
        fun pileTopLeft(pileState: FreeCellState, key: PileKey, indexHint: Int? = null): Offset = when (key) {
            is PileKey.TableauColumn -> {
                val column = pileState.tableau[key.column]
                val columnTopLeft = columnRects[key.column]?.topLeft ?: Offset.Zero
                val index = (indexHint ?: column.size).coerceAtLeast(0)
                val offsets = freeCellCardTopOffsets(column.size, tableauLane, cardSize.height)
                val yDp = offsets.getOrElse(index) { offsets.lastOrNull() ?: 0.dp }
                columnTopLeft + Offset(0f, with(density) { yDp.toPx() })
            }
            is PileKey.FreeCell -> freeCellRects[key.cell]?.topLeft ?: Offset.Zero
            is PileKey.Foundation -> foundationRects[key.suit]?.topLeft ?: Offset.Zero
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

        // Each queued flight plays fully, one at a time, before the next starts: a player's move,
        // then each automatic transfer, is its own clearly visible animation, never merged into
        // one blurred motion. `displayState` steps to the finishing leg's `resultState` right as
        // it completes.
        LaunchedEffect(moveAnimation) {
            val anim = moveAnimation ?: return@LaunchedEffect
            if (anim.startDelayMs > 0) delay(anim.startDelayMs.toLong())
            // Played right as the card actually starts moving, not back at commit time —
            // otherwise a step still sitting in the queue behind an earlier one would sound off
            // well before its own flight actually begins.
            anim.sound?.let(onPlaySound)
            animationProgress.animateTo(1f, tween(anim.durationMs, easing = LinearEasing))
            displayState = anim.resultState
            anim.onComplete?.invoke()
            moveAnimation = if (animationQueue.isNotEmpty()) {
                animationQueue.first().also { animationQueue = animationQueue.drop(1) }
            } else {
                null
            }
        }

        // Safety net for a state change this board did not itself queue an animation for — a
        // debug fixture load, a restore, a fresh deal, or a test driving `dragMove` directly
        // (`WinTest`) — so `displayState` never gets stuck showing a stale board. A legitimate,
        // board-driven commit already enqueues its animation synchronously before this can ever
        // observe `moveAnimation` as null for it.
        LaunchedEffect(state) {
            if (moveAnimation == null && animationQueue.isEmpty() && displayState != state) {
                displayState = state
            }
        }

        // The automatic finish's search and commit already happened by the time this fires
        // (`FreeCellViewModel.runAutoFinishIfAvailable`'s own doc explains why the search itself
        // can't run here, synchronously, the way the ordinary cascade below does) — this only
        // replays the already-proven move sequence to discover per-leg endpoints to animate
        // through.
        LaunchedEffect(viewModel.pendingAutoFinish) {
            val pending = viewModel.pendingAutoFinish ?: return@LaunchedEffect
            viewModel.consumeAutoFinishAnimationSignal()
            var stepState = pending.fromState
            val anims = mutableListOf<MoveAnimation>()
            for ((index, sweepMove) in pending.moves.withIndex()) {
                val fromKey = sourceKeyFor(sweepMove)
                val fromIndex = sourceIndexFor(sweepMove, stepState)
                val toKey = destinationKeyFor(sweepMove, stepState)
                val toIndex = (toKey as? PileKey.TableauColumn)?.let { stepState.tableau[it.column].size }
                val fromCount = (fromKey as? PileKey.TableauColumn)?.let { stepState.tableau[it.column].size } ?: 0
                val toCount = (toKey as? PileKey.TableauColumn)?.let { stepState.tableau[it.column].size } ?: 0
                val stepPx = stepPxFor(fromKey, fromCount, toKey, toCount)
                val fromPoint = pileTopLeft(stepState, fromKey, fromIndex)
                val toPoint = pileTopLeft(stepState, toKey, toIndex)
                val cards = movedCardsFor(sweepMove, stepState)
                stepState = applyMove(stepState, sweepMove).copy(moveCount = stepState.moveCount + 1)
                val isLast = index == pending.moves.lastIndex
                anims += MoveAnimation(
                    cards, fromPoint, toPoint, stepPx, stepState,
                    durationMs = if (skipAnimations) SKIP_ANIMATIONS_FLIGHT_MS else durationForFlight(fromPoint, toPoint, density, AUTO_FINISH_SPEED_MULTIPLIER),
                    sound = SoundEffect.AUTOMATIC_MOVE,
                    fromKey = fromKey,
                    fromIndex = fromIndex,
                    onComplete = if (isLast) viewModel::onSweepAnimationFinished else null,
                )
            }
            if (anims.isEmpty()) {
                viewModel.onSweepAnimationFinished()
            } else {
                enqueueAnimations(anims)
            }
        }

        // The reversal's own animation: diffs every card's pile before and after the undo
        // (`preUndoState` vs. the now-current `state`) rather than replaying a move list, since
        // undo restores a whole prior board in one step — mirrors Klondike's own
        // `LaunchedEffect(undoSignal)` exactly.
        LaunchedEffect(viewModel.pendingUndoAnimation) {
            val preUndoState = viewModel.pendingUndoAnimation ?: return@LaunchedEffect
            viewModel.consumeUndoAnimationSignal()
            if (skipAnimations) {
                displayState = state
                return@LaunchedEffect
            }
            val moved = Card.CANONICAL_DECK.mapNotNull { card ->
                val from = pileOf(preUndoState, card)
                val to = pileOf(state, card)
                if (from != to) Triple(card, from, to) else null
            }
            if (moved.isEmpty()) {
                displayState = state
                return@LaunchedEffect
            }
            val groups = moved.groupBy { it.second to it.third }
            val anims = groups.entries.mapIndexed { index, (pair, entries) ->
                val (fromKey, toKey) = pair
                val cards = entries.map { it.first }
                val fromIndex = (fromKey as? PileKey.TableauColumn)?.let { preUndoState.tableau[it.column].size - cards.size }
                val toIndex = (toKey as? PileKey.TableauColumn)?.let { state.tableau[it.column].size - cards.size }
                val fromPoint = pileTopLeft(preUndoState, fromKey, fromIndex)
                val toPoint = pileTopLeft(state, toKey, toIndex)
                val fromCount = (fromKey as? PileKey.TableauColumn)?.let { preUndoState.tableau[it.column].size } ?: 0
                val toCount = (toKey as? PileKey.TableauColumn)?.let { state.tableau[it.column].size } ?: 0
                val stepPx = stepPxFor(fromKey, fromCount, toKey, toCount)
                val isLast = index == groups.size - 1
                MoveAnimation(
                    cards, fromPoint, toPoint, stepPx, if (isLast) state else preUndoState,
                    durationForFlight(fromPoint, toPoint, density),
                    fromKey = fromKey,
                    fromIndex = fromIndex,
                )
            }
            enqueueAnimations(anims)
        }

        /**
         * Commits [move] — expected legal, since every call site resolves it from
         * `resolveTableauTap`/`resolveFreeCellTap` or the drag geometry below first — then queues
         * a flying-card animation for it and, in turn, for every automatic foundation transfer
         * the commit's own cascade triggers, each played fully before the next starts. The
         * cascade is re-derived locally (via the same pure rules function the reducer itself
         * uses, `runAutomaticFoundationCascade`) purely to discover per-step endpoints and
         * intermediate states to animate through; [FreeCellViewModel.dragMove] remains the sole
         * source of truth for the committed state. A drop that turns out illegal (the drag
         * geometry below guesses a destination without checking legality first) simply commits
         * nothing and returns false — the floating drag overlay already vanishes the instant the
         * gesture ends either way, so there is nothing to animate back.
         *
         * [dragDropOrigin], when given, is where the dragged card's floating overlay actually was
         * at drop — a drag-committed move's animation starts from there instead of the source
         * pile's resting position, so the card continues smoothly from under the player's finger.
         *
         * [invalidKey] is the pile to flash — and, once, the sound to play — if [move] turns out
         * illegal after all (the drag geometry below guesses a destination without checking
         * legality first); Klondike's own `markInvalid` plays the same role.
         */
        fun commitWithAnimation(move: Move, invalidKey: PileKey, dragDropOrigin: Offset? = null): Boolean {
            val preState = state
            val playerCards = movedCardsFor(move, preState)
            val playerFromKey = sourceKeyFor(move)
            val playerFromIndex = sourceIndexFor(move, preState)
            val playerToKey = destinationKeyFor(move, preState)
            val playerToIndex = (playerToKey as? PileKey.TableauColumn)?.let { preState.tableau[it.column].size }
            val playerFromCount = (playerFromKey as? PileKey.TableauColumn)?.let { preState.tableau[it.column].size } ?: 0
            val playerToCount = (playerToKey as? PileKey.TableauColumn)?.let { preState.tableau[it.column].size } ?: 0
            val playerStepPx = stepPxFor(playerFromKey, playerFromCount, playerToKey, playerToCount)
            val playerFromPoint = dragDropOrigin ?: pileTopLeft(preState, playerFromKey, playerFromIndex)
            val playerToPoint = pileTopLeft(preState, playerToKey, playerToIndex)

            if (!viewModel.dragMove(move)) {
                markInvalid(invalidKey)
                return false
            }

            var stepState = applyMove(preState, move).copy(moveCount = preState.moveCount + 1)
            val anims = mutableListOf<MoveAnimation>()
            anims += MoveAnimation(
                playerCards, playerFromPoint, playerToPoint, playerStepPx, stepState,
                durationMs = if (skipAnimations) SKIP_ANIMATIONS_FLIGHT_MS else durationForFlight(playerFromPoint, playerToPoint, density),
                sound = SoundEffect.MOVE,
                fromKey = playerFromKey,
                fromIndex = playerFromIndex,
            )

            if (viewModel.session.automaticMovesEnabled) {
                val (_, transfers) = runAutomaticFoundationCascade(stepState)
                for (transferMove in transfers) {
                    val transferCards = movedCardsFor(transferMove, stepState)
                    val fromKey = sourceKeyFor(transferMove)
                    val fromIndex = sourceIndexFor(transferMove, stepState)
                    val toKey = destinationKeyFor(transferMove, stepState)
                    val toIndex = (toKey as? PileKey.TableauColumn)?.let { stepState.tableau[it.column].size }
                    val fromCount = (fromKey as? PileKey.TableauColumn)?.let { stepState.tableau[it.column].size } ?: 0
                    val toCount = (toKey as? PileKey.TableauColumn)?.let { stepState.tableau[it.column].size } ?: 0
                    val stepPx = stepPxFor(fromKey, fromCount, toKey, toCount)
                    val fromPoint = pileTopLeft(stepState, fromKey, fromIndex)
                    val toPoint = pileTopLeft(stepState, toKey, toIndex)
                    stepState = applyMove(stepState, transferMove).copy(moveCount = stepState.moveCount + 1)
                    anims += MoveAnimation(
                        transferCards, fromPoint, toPoint, stepPx, stepState,
                        durationMs = if (skipAnimations) SKIP_ANIMATIONS_FLIGHT_MS else durationForFlight(fromPoint, toPoint, density),
                        startDelayMs = if (skipAnimations) SKIP_ANIMATIONS_STEP_PAUSE_MS else 0,
                        sound = SoundEffect.AUTOMATIC_MOVE,
                        fromKey = fromKey,
                        fromIndex = fromIndex,
                    )
                }
            }

            enqueueAnimations(anims)
            return true
        }

        /**
         * Resolves [info]'s drop point against the free-cell and foundation zones first — both
         * small, precise targets checked by literal containment of the pointer's own position —
         * and falls back to whichever tableau column the lifted card's horizontal span overlaps
         * most, the same rule Spider's own drag uses. A drop matching no legal move for [info]'s
         * source, or one [commitWithAnimation] rejects, flashes the source pile and plays the
         * invalid sound instead of silently snapping the card back.
         */
        fun resolveAndAttemptDrop(info: DragInfo) {
            val point = info.pointerInBoard
            val cardLeft = point.x - info.grabOffset.x
            val freeCellHit = freeCellRects.entries.firstOrNull { (_, rect) -> rect.contains(point) }?.key
            val foundationHit = foundationRects.values.any { it.contains(point) }

            val sourceKey = when (val source = info.source) {
                is DragSource.Tableau -> PileKey.TableauColumn(source.column)
                is DragSource.FreeCell -> PileKey.FreeCell(source.cell)
            }
            val move = when (val source = info.source) {
                is DragSource.Tableau -> when {
                    freeCellHit != null -> Move.TableauToFreeCell(source.column, freeCellHit)
                    foundationHit -> Move.TableauToFoundation(source.column)
                    else -> columnWithMostOverlap(cardLeft, cardWidthPx, columnRects)
                        ?.let { Move.TableauToTableau(source.column, source.fromIndex, it) }
                }
                is DragSource.FreeCell -> when {
                    foundationHit -> Move.FreeCellToFoundation(source.cell)
                    else -> columnWithMostOverlap(cardLeft, cardWidthPx, columnRects)
                        ?.let { Move.FreeCellToTableau(source.cell, it) }
                }
            }
            drag = null
            if (move == null) {
                markInvalid(sourceKey)
            } else {
                commitWithAnimation(move, sourceKey, dragDropOrigin = info.pointerInBoard - info.grabOffset)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { boardCoordinates = it }
                .padding(8.dp),
        ) {
            Row(
                // One flat row of eight equal-width, equally-spaced slots — free cells then
                // foundations — sharing the tableau row's own `cardSize`/`spacing`, so each one
                // lines up directly above the tableau column below it instead of the free-cell
                // and foundation groups only being pushed to the row's own two ends.
                modifier = Modifier.fillMaxWidth().height(cardSize.height),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                for (cell in 0 until FREE_CELLS) {
                    FreeCellSlot(
                        cell = cell,
                        card = displayState.freeCells[cell],
                        colors = colors,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        hidden = (drag?.source as? DragSource.FreeCell)?.cell == cell || moveAnimation?.fromKey == PileKey.FreeCell(cell),
                        hintAlpha = if ((hintSource as? HintSource.FreeCell)?.cell == cell || HintSource.FreeCell(cell) in highlightedSources) hintAlpha else 0f,
                        isInvalid = invalidFeedback == PileKey.FreeCell(cell),
                        inputEnabled = inputEnabled,
                        onTap = {
                            // Tapping the exact pile a shown hint currently highlights commits the
                            // hint's own move instead of the standard tap-priority pick, since the
                            // two can disagree (`docs/games/klondike/DESIGN.md` "Interaction",
                            // mirrored here).
                            val move = if (hintMove != null && (hintSource as? HintSource.FreeCell)?.cell == cell) {
                                hintMove
                            } else {
                                resolveFreeCellTap(state, cell)
                            }
                            if (move == null) markInvalid(PileKey.FreeCell(cell)) else commitWithAnimation(move, PileKey.FreeCell(cell))
                        },
                        onDragStart = { grabOffset ->
                            val card = state.freeCells[cell] ?: return@FreeCellSlot
                            val origin = freeCellRects[cell]?.topLeft ?: Offset.Zero
                            drag = DragInfo(DragSource.FreeCell(cell), listOf(card), grabOffset, origin + grabOffset)
                        },
                        onDrag = { delta -> drag = drag?.copy(pointerInBoard = drag!!.pointerInBoard + delta) },
                        onDragEnd = { drag?.let { resolveAndAttemptDrop(it) } },
                        onDragCancel = { drag = null },
                        onPositioned = { coords ->
                            val board = boardCoordinates
                            if (board != null && coords.isAttached) {
                                freeCellRects[cell] = Rect(board.localPositionOf(coords, Offset.Zero), coords.size.toSize())
                            }
                        },
                    )
                }
                for (suit in Suit.entries) {
                    FoundationSlot(
                        topCard = displayState.foundationTop(suit),
                        suit = suit,
                        colors = colors,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onPositioned = { coords ->
                            val board = boardCoordinates
                            if (board != null && coords.isAttached) {
                                foundationRects[suit] = Rect(board.localPositionOf(coords, Offset.Zero), coords.size.toSize())
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth().height(tableauLane),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                for (column in 0 until TABLEAU_COLUMNS) {
                    val guidedHintFromIndex = (hintSource as? HintSource.Tableau)?.takeIf { it.column == column }?.fromIndex
                    val hintFromIndex = guidedHintFromIndex ?: highlightedTableauFromIndex[column]
                    TableauColumnView(
                        column = column,
                        cards = displayState.tableau[column],
                        cardSize = cardSize,
                        availableHeight = tableauLane,
                        colors = colors,
                        liftedFromIndex = (drag?.source as? DragSource.Tableau)?.takeIf { it.column == column }?.fromIndex,
                        animatingAwayFromIndex = moveAnimation?.takeIf { it.fromKey == PileKey.TableauColumn(column) }?.fromIndex,
                        hintFromIndex = hintFromIndex,
                        hintAlpha = if (hintFromIndex != null) hintAlpha else 0f,
                        isInvalid = invalidFeedback == PileKey.TableauColumn(column),
                        inputEnabled = inputEnabled,
                        onColumnPositioned = { coords ->
                            val board = boardCoordinates
                            if (board != null && coords.isAttached) {
                                columnRects[column] = Rect(board.localPositionOf(coords, Offset.Zero), coords.size.toSize())
                            }
                        },
                        onTapCard = { index ->
                            // Same hint override as the free-cell tap above.
                            val move = if (hintMove != null && guidedHintFromIndex == index) {
                                hintMove
                            } else {
                                resolveTableauTap(state, column, index)
                            }
                            if (move == null) markInvalid(PileKey.TableauColumn(column)) else commitWithAnimation(move, PileKey.TableauColumn(column))
                        },
                        onDragStartCard = { index, cardTopLeftInColumn, grabOffset ->
                            val columnOrigin = columnRects[column]?.topLeft ?: Offset.Zero
                            drag = DragInfo(
                                source = DragSource.Tableau(column, index),
                                cards = state.tableau[column].subList(index, state.tableau[column].size),
                                grabOffset = grabOffset,
                                pointerInBoard = columnOrigin + cardTopLeftInColumn + grabOffset,
                            )
                        },
                        onDrag = { delta -> drag = drag?.copy(pointerInBoard = drag!!.pointerInBoard + delta) },
                        onDragEnd = { drag?.let { resolveAndAttemptDrop(it) } },
                        onDragCancel = { drag = null },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        drag?.let { info ->
            FloatingCards(cards = info.cards, cardSize = cardSize, topLeft = info.pointerInBoard - info.grabOffset, colors = colors)
        }

        // Floating overlay animating a committed move's card(s) from source to destination, so
        // the action is always visibly registered.
        moveAnimation?.let { anim ->
            val currentTopLeft = lerp(anim.fromTopLeft, anim.toTopLeft, animationProgress.value)
            val cardHeightPx = with(density) { cardSize.height.toPx() }
            val stackHeightPx = cardHeightPx + (anim.cards.size - 1) * anim.stepPx
            Canvas(
                modifier = Modifier
                    .testTag("move_flight")
                    .offset { IntOffset(currentTopLeft.x.roundToInt(), currentTopLeft.y.roundToInt()) }
                    .then(with(density) { Modifier.size(cardSize.width, stackHeightPx.toDp()) }),
            ) {
                anim.cards.forEachIndexed { i, card ->
                    drawCardFace(Offset(0f, i * anim.stepPx), Size(cardWidthPx, cardHeightPx), card, colors)
                }
            }
        }
    }
}

@Composable
private fun FreeCellSlot(
    cell: Int,
    card: Card?,
    colors: CardColors,
    hidden: Boolean,
    hintAlpha: Float,
    isInvalid: Boolean,
    inputEnabled: Boolean,
    onTap: () -> Unit,
    onDragStart: (grabOffset: Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        // Sized by the caller (a `weight(1f)` share of the top row, matching every tableau
        // column's own share of the row below it) rather than a fixed `cardSize` here: a fixed
        // width alongside seven others plus their gaps can ask for a hair more than the row
        // actually has once shared padding is accounted for, and unlike a weighted share, a fixed
        // one doesn't get redistributed — it just overflows, and Compose has to shrink *something*
        // to fit, which in practice was always this row's last item.
        modifier = modifier
            .testTag("free_cell_$cell")
            .onGloballyPositioned(onPositioned)
            .cardPointerInput(
                enabled = card != null && inputEnabled,
                onTap = onTap,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (card != null && !hidden) drawCardFace(Offset.Zero, size, card, colors) else drawEmptySlot(Offset.Zero, size, colors)
            if (hintAlpha > 0f) drawHintHighlight(Offset.Zero, size, hintAlpha, colors)
            if (isInvalid) drawDestinationHighlight(Offset.Zero, size, colors.invalid)
        }
    }
}

@Composable
private fun FoundationSlot(
    topCard: Card?,
    suit: Suit,
    colors: CardColors,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        // See `FreeCellSlot`'s own doc on why this is caller-sized rather than a fixed `cardSize`.
        modifier = modifier
            .testTag("foundation_${suit.name}")
            .onGloballyPositioned(onPositioned),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (topCard != null) {
                drawCardFace(Offset.Zero, size, topCard, colors)
            } else {
                drawEmptySlot(Offset.Zero, size, colors, watermark = suit.symbol.toString())
            }
        }
    }
}

@Composable
private fun TableauColumnView(
    column: Int,
    cards: List<Card>,
    cardSize: CardSize,
    availableHeight: androidx.compose.ui.unit.Dp,
    colors: CardColors,
    liftedFromIndex: Int?,
    animatingAwayFromIndex: Int?,
    hintFromIndex: Int?,
    hintAlpha: Float,
    isInvalid: Boolean,
    inputEnabled: Boolean,
    onColumnPositioned: (LayoutCoordinates) -> Unit,
    onTapCard: (index: Int) -> Unit,
    onDragStartCard: (index: Int, cardTopLeftInColumn: Offset, grabOffset: Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsets = freeCellCardTopOffsets(cards.size, availableHeight, cardSize.height)
    val density = LocalDensity.current
    val offsetsPx = remember(offsets, density) { with(density) { offsets.map { it.toPx() } } }
    var columnCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .height(availableHeight)
            .testTag("tableau_column_$column")
            .onGloballyPositioned {
                columnCoordinates = it
                onColumnPositioned(it)
            },
    ) {
        Canvas(modifier = Modifier.size(cardSize.width, cardSize.height)) {
            drawEmptySlot(Offset.Zero, size, colors)
        }
        cards.forEachIndexed { index, card ->
            val hidden = (liftedFromIndex != null && index >= liftedFromIndex) ||
                (animatingAwayFromIndex != null && index >= animatingAwayFromIndex)
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, offsets[index].roundToPx()) }
                    .size(cardSize.width, cardSize.height)
                    .testTag("card_${column}_$index")
                    .cardPointerInput(
                        enabled = inputEnabled,
                        onTap = { onTapCard(index) },
                        onDragStart = { local ->
                            val coords = columnCoordinates
                            if (coords != null && coords.isAttached) {
                                onDragStartCard(index, Offset(0f, offsetsPx[index]), local)
                            }
                        },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    ),
            ) {
                if (!hidden) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCardFace(Offset.Zero, size, card, colors)
                        if (hintFromIndex != null && index >= hintFromIndex) drawHintHighlight(Offset.Zero, size, hintAlpha, colors)
                        if (isInvalid && index == cards.lastIndex) drawDestinationHighlight(Offset.Zero, size, colors.invalid)
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingCards(cards: List<Card>, cardSize: CardSize, topLeft: Offset, colors: CardColors) {
    val step = cardSize.height * 0.3f
    Box(modifier = Modifier.fillMaxSize()) {
        cards.forEachIndexed { index, card ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(topLeft.x.roundToInt(), (topLeft.y + step.toPx() * index).roundToInt()) }
                    .size(cardSize.width, cardSize.height),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) { drawCardFace(Offset.Zero, size, card, colors) }
            }
        }
    }
}

/**
 * The column whose horizontal span overlaps the dragged card's the most, or null when it doesn't
 * overlap any column at all — Spider's own `columnWithMostOverlap`, unchanged: a real finger
 * obscures the card being dropped onto, so only the horizontal axis is asked.
 */
private fun columnWithMostOverlap(cardLeft: Float, cardWidthPx: Float, columnRects: Map<Int, Rect>): Int? {
    val cardRight = cardLeft + cardWidthPx
    return columnRects.entries
        .map { (column, rect) -> column to (minOf(cardRight, rect.right) - maxOf(cardLeft, rect.left)) }
        .filter { (_, overlap) -> overlap > 0f }
        .maxByOrNull { (_, overlap) -> overlap }
        ?.first
}
