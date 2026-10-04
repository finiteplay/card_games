package org.finiteplay.spider.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.dp
import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.core.ui.gesture.cardPointerInput
import org.finiteplay.core.ui.card.drawCardBack
import org.finiteplay.core.ui.card.drawCardFace
import org.finiteplay.core.ui.card.drawCenteredCardBadge
import org.finiteplay.core.ui.card.drawDestinationHighlight
import org.finiteplay.core.ui.card.drawEmptySlot
import org.finiteplay.core.ui.card.drawHintHighlight
import org.finiteplay.core.ui.layout.FinitePlayLogo
import org.finiteplay.core.ui.card.suitSpokenLabel
import org.finiteplay.core.ui.theme.CardColors
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.solitaire.ui.CardSize
import org.finiteplay.spider.game.SPIDER_GEOMETRY
import org.finiteplay.spider.game.computeSpiderCardSize
import org.finiteplay.spider.game.spiderCardHeightFittingTallestColumn
import org.finiteplay.spider.game.spiderColumnSpacing
import org.finiteplay.spider.game.spiderTableauOverlap
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.rules.BankedRun
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Width the stock reserves beside the columns in landscape, card plus its gap. */
private val STOCK_GUTTER = 76.dp

/**
 * Column depth the board sizes itself for regardless of what is dealt, so cards keep one size
 * through the opening moves instead of shrinking as columns grow.
 */
private const val PLANNED_COLUMN_DEPTH = 12

/** A sequence lifted off [fromColumn] at [fromIndex], following the pointer. */
internal data class DragInfo(
    val fromColumn: Int,
    val fromIndex: Int,
    val cards: List<TableauCard>,
    val grabOffset: Offset,
    val pointerInBoard: Offset,
)

// Klondike's own constant-speed motion (`Board.kt`), reused as-is for every flight this board
// plays — the bank flight and an ordinary tableau move alike: a straight-line flight covers
// ground at a fixed rate rather than a fixed duration, so a short hop and a long one read as the
// same motion instead of the same clock time, with the same floor and ceiling keeping either
// extreme from being imperceptible or dragging on. Dp, not raw pixels: 160 dp is one inch on any
// device, so this is exactly 5 inches in 0.5 s regardless of screen density, where a raw-pixel
// rate would read as a different real-world speed on every one.
private const val FLIGHT_SPEED_DP_PER_MS = 1.6f
private const val MIN_FLIGHT_MS = 80
private const val MAX_FLIGHT_MS = 450

/**
 * How much of a card's own flight plays before the next one launches — the last card of a
 * staggered group is still most of the way through its predecessors' flights when it starts,
 * which is what reads as one cascading motion rather than several separate ones. Shared by both
 * groups this board stages this way: a bank flight's thirteen cards, and a row deal's ten.
 */
private const val FLIGHT_STAGGER_FRACTION = 0.25f

/** Flight duration for a straight line from [from] to [to] at [FLIGHT_SPEED_DP_PER_MS]. */
private fun durationForFlight(from: Offset, to: Offset, density: Density): Int {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val distanceDp = with(density) { sqrt(dx * dx + dy * dy).toDp().value }
    return (distanceDp / FLIGHT_SPEED_DP_PER_MS).toInt().coerceIn(MIN_FLIGHT_MS, MAX_FLIGHT_MS)
}

/** One card's own flight within a [BankFlight] — [startMs]/[endMs] are offsets into the group's shared clock. */
private data class BankFlightCard(val card: Card, val fromTopLeft: Offset, val startMs: Int, val endMs: Int)

/**
 * One completed run flying from [column] to [suit]'s banked counter, cards in launch order —
 * ace first, king last (`docs`: "starting from lowest ... to highest"). [cardsKingFirst] keeps the
 * tableau's own order too, for reconstructing the column's pre-bank appearance while it plays.
 */
private data class BankFlight(
    val column: Int,
    val suit: Suit,
    val cardsKingFirst: List<Card>,
    val toTopLeft: Offset,
    val cards: List<BankFlightCard>,
) {
    val totalDurationMs: Int get() = cards.last().endMs
}

/** Builds the ace-first, staggered flight for one [BankedRun]. */
private fun buildBankFlight(
    run: BankedRun,
    fromTopLeftKingFirst: List<Offset>,
    toTopLeft: Offset,
    density: Density,
): BankFlight {
    val aceFirst = run.cards.reversed()
    val fromAceFirst = fromTopLeftKingFirst.reversed()
    var nextStart = 0
    val cards = aceFirst.mapIndexed { i, card ->
        val duration = durationForFlight(fromAceFirst[i], toTopLeft, density)
        val start = nextStart
        nextStart += (duration * FLIGHT_STAGGER_FRACTION).toInt()
        BankFlightCard(card, fromAceFirst[i], start, start + duration)
    }
    return BankFlight(run.column, run.suit, run.cards, toTopLeft, cards)
}

/** One [SpiderViewModel.MovedSequence] flying as a single rigid stack — an ordinary tableau move, not a bank. */
private data class MoveFlight(
    val fromColumn: Int,
    val toColumn: Int,
    val cards: List<Card>,
    val fromTopLeft: Offset,
    val toTopLeft: Offset,
    val stepPx: Float,
    val durationMs: Int,
)

/** One card's own flight within a [DealFlight] — [startMs]/[endMs] are offsets into the group's shared clock. */
private data class DealFlightCard(val column: Int, val card: Card, val toTopLeft: Offset, val startMs: Int, val endMs: Int)

/**
 * A row deal: one card from the stock to every column at once, staggered left to right the same
 * way a bank flight staggers ace to king — so ten simultaneous arrivals read as one dealing motion
 * across the row rather than ten separate ones. A column whose own new card also completes a
 * sequence there is left out of [cards] entirely (`SpiderBoard.kt`'s own scope note on
 * [SpiderViewModel.lastMovedSequence] applies here too): its bank flight already shows that card,
 * and a deal-in first would be a redundant arrival ahead of an animation that already reads
 * correctly on its own.
 */
private data class DealFlight(val fromTopLeft: Offset, val cards: List<DealFlightCard>) {
    val totalDurationMs: Int get() = cards.maxOf { it.endMs }
}

/** Builds the left-to-right, staggered flight for one [SpiderViewModel.DealtRow]. */
private fun buildDealFlight(
    fromTopLeft: Offset,
    deals: List<Pair<Int, Card>>,
    toTopLeftFor: (Int) -> Offset?,
    density: Density,
): DealFlight? {
    var nextStart = 0
    val cards = deals.mapNotNull { (column, card) ->
        val toTopLeft = toTopLeftFor(column) ?: return@mapNotNull null
        val duration = durationForFlight(fromTopLeft, toTopLeft, density)
        val start = nextStart
        nextStart += (duration * FLIGHT_STAGGER_FRACTION).toInt()
        DealFlightCard(column, card, toTopLeft, start, start + duration)
    }
    return if (cards.isEmpty()) null else DealFlight(fromTopLeft, cards)
}

/**
 * The board: ten tableau columns, the stock, and a per-suit banked count, in one adaptive
 * layout for both orientations — S3a's vertical slice, not yet the portrait/landscape split
 * `UI_SPEC.md` will eventually specify (`docs/games/spider/EXECUTION_PLAN.md`).
 *
 * Tap plays a card to the leftmost legal destination (`resolveTap`). Drag starts as soon as the
 * pointer travels past touch slop, the same as Klondike's board — `detectDragGestures` and
 * `detectTapGestures` in sibling `pointerInput` blocks disambiguate by distance and do not race.
 * An earlier draft here required a long press first, on the theory that two detectors on one node
 * always conflict; they do not, and the hold made an ordinary drag register as a tap.
 */
@Composable
fun SpiderBoard(
    viewModel: SpiderViewModel,
    modifier: Modifier = Modifier,
    /**
     * Landscape puts the stock beside the columns instead of above them: a row of chrome across
     * the top costs height the board has none of when the screen is short, and the banked counters
     * have already moved to the status line for the same reason.
     */
    stockAtSide: Boolean = false,
) {
    val session = viewModel.session
    val state = session.state
    val colors = LocalAppColors.current.card

    // No felt here: the cloth is painted once behind the whole screen, so the header, the board,
    // and the action bar sit on one continuous surface rather than three panels that happen to be
    // adjacent (`GameScreen`).
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Publisher mark on the cloth, behind everything: visible on a fresh deal where the lower
        // board is empty, and quietly covered as columns grow over it.
        FinitePlayLogo(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            widthFraction = 0.45f,
        )

        // Width-fit first, the same as Klondike's own board; then cap by the column actually
        // dealt, not the rules' maximum, so an ordinary game keeps large cards and only a
        // column that has genuinely grown deep gives way (`CardGeometry.kt`'s own reasoning,
        // one pass rather than three — this board has no landscape-specific lane split yet to
        // make the extra passes worth their cost).
        // The columns' own width: beside the stock in landscape, the whole board in portrait.
        val stockGutter = if (stockAtSide) STOCK_GUTTER else 0.dp
        val columnsWidth = maxWidth - stockGutter
        val widthFitCard = computeSpiderCardSize(columnsWidth)

        // The lane the columns get. Landscape hands them the full height, since the stock no
        // longer sits above them.
        val lane = if (stockAtSide) maxHeight else (maxHeight - widthFitCard.height - 24.dp)
        val provisionalLane = lane.coerceAtLeast(widthFitCard.height)

        // Size against a column depth the game will actually reach, not the one on screen right
        // now. A freshly dealt column is six cards deep and would size the cards huge, then shrink
        // them on the first few moves — the resize is more distracting than the smaller card. Half
        // the lane's worth of overlap steps is the depth an ordinary game settles around, so the
        // board looks the same after ten moves as it did after one.
        val dealtDeepest = state.tableau.maxOf { it.size }
        val plannedDeepest = maxOf(dealtDeepest, PLANNED_COLUMN_DEPTH)
        val heightFitHeight = spiderCardHeightFittingTallestColumn(provisionalLane, plannedDeepest)
        val cardWidth = minOf(widthFitCard.width, heightFitHeight / SPIDER_GEOMETRY.cardWidthToHeightRatio)
            .coerceAtLeast(SPIDER_GEOMETRY.minCardWidth)
        val cardSize = CardSize(cardWidth, cardWidth * SPIDER_GEOMETRY.cardWidthToHeightRatio)
        val density = LocalDensity.current
        val cardWidthPx = with(density) { cardSize.width.toPx() }
        val cardHeightPx = with(density) { cardSize.height.toPx() }
        val spacing = spiderColumnSpacing(columnsWidth, cardSize.width)
        val tableauLane = if (stockAtSide) {
            maxHeight
        } else {
            (maxHeight - cardSize.height - 24.dp).coerceAtLeast(cardSize.height)
        }

        // Every flight this board plays is gated on this, all in one place: Settings' own toggle
        // (`docs/games/spider/UI_SPEC.md` "Motion" — "Spider adds nothing of its own here", i.e.
        // Klondike's rule that Skip Animations removes every slide applies unchanged). Unlike
        // Klondike, Spider has no per-move cascade to keep a paced pulse through once slides are
        // gone — a Spider move is one atomic transfer, not several steps — so skipping here is
        // simply every flight below never being built, and every column always rendering `state`
        // directly and instantly.
        val skipAnimations = !viewModel.settings.animationsEnabled
        var boardCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val columnRects = remember { mutableStateMapOf<Int, Rect>() }
        val bankedCounterRects = remember { mutableStateMapOf<Suit, Rect>() }
        var stockRect by remember { mutableStateOf<Rect?>(null) }
        var drag by remember { mutableStateOf<DragInfo?>(null) }

        // Built synchronously in the same composition pass `state` first takes its new value, not
        // inside a LaunchedEffect: that runs on a later dispatch, and a column reconstructed only
        // then would flash to its already-shrunk real content for a frame first, then jump back to
        // full for the flight to start from — the reducer has already banked the run by the time
        // anything here sees this `state`, so the reconstruction is what stands in for it the whole
        // time, from its very first frame.
        //
        // `columnRects`/`bankedCounterRects` describe the layout as it stood for the board that
        // just changed, and the completed run's own cards (`BankedRun.cards`) plus what is left in
        // its column (`state.tableau[run.column]`, already the post-bank remainder) are all this
        // needs to reconstruct exactly how that column looked the instant before banking, without
        // waiting on, or duplicating, the reducer's own work.
        val bankFlights = remember(state) {
            val runs = viewModel.lastBankedRuns
            if (skipAnimations || runs.isEmpty()) {
                emptyList()
            } else {
                runs.mapNotNull { run ->
                    val toRect = bankedCounterRects[run.suit] ?: return@mapNotNull null
                    val columnOrigin = columnRects[run.column]?.topLeft ?: return@mapNotNull null
                    val remainder = state.tableau[run.column]
                    val reconstructed = remainder + run.cards.map { TableauCard(it, faceUp = true) }
                    val faceDownCount = reconstructed.count { !it.faceUp }
                    val faceUpCount = reconstructed.count { it.faceUp }
                    val overlap = spiderTableauOverlap(tableauLane, cardSize.height, faceDownCount, faceUpCount)
                    val offsetsPx = with(density) { cardTopOffsets(reconstructed, overlap).map { it.toPx() } }
                    val runStart = reconstructed.size - run.cards.size
                    val fromTopLeftKingFirst = (runStart until reconstructed.size).map { i ->
                        columnOrigin + Offset(0f, offsetsPx[i])
                    }
                    val toCenter = toRect.center
                    val toTopLeft = Offset(toCenter.x - cardWidthPx / 2f, toCenter.y - cardHeightPx / 2f)
                    buildBankFlight(run, fromTopLeftKingFirst, toTopLeft, density)
                }
            }
        }
        // Reset (via the `remember` key below) the same instant `bankFlights` changes, so a stale
        // elapsed time left over from a previous, already-finished flight can never be compared
        // against a new one's own (likely shorter) duration and wrongly read as already done.
        var bankFlightElapsedMs by remember(bankFlights) { mutableStateOf(0) }

        LaunchedEffect(bankFlights) {
            if (bankFlights.isEmpty()) return@LaunchedEffect
            val totalMs = bankFlights.maxOf { it.totalDurationMs }
            val startFrameNanos = withFrameNanos { it }
            while (bankFlightElapsedMs < totalMs) {
                val nowNanos = withFrameNanos { it }
                bankFlightElapsedMs = (((nowNanos - startFrameNanos) / 1_000_000L).toInt()).coerceAtMost(totalMs)
            }
        }

        // An ordinary tableau move, staged the same way and for the same reason as the bank
        // flight above: built synchronously against the board that just changed, not inside a
        // LaunchedEffect, so the moved run never flashes to its already-landed real content
        // before its own flight has had a chance to start. Null whenever the move also banked a
        // run (`SpiderViewModel.lastMovedSequence`) — the bank flight already shows those cards
        // leaving, and playing a second departure first would be a redundant extra step ahead of
        // an animation that already reads correctly on its own.
        val moveFlight = remember(state) {
            val moved = viewModel.lastMovedSequence
            val fromOrigin = moved?.let { columnRects[it.fromColumn]?.topLeft }
            val toOrigin = moved?.let { columnRects[it.toColumn]?.topLeft }
            if (skipAnimations || moved == null || fromOrigin == null || toOrigin == null) {
                null
            } else {
                // The source, reconstructed with its departing run added back — exactly the bank
                // flight's own technique above — to find where that run's own top-left sits.
                val sourceRemainder = state.tableau[moved.fromColumn]
                val reconstructedSource = sourceRemainder + moved.cards.map { TableauCard(it, faceUp = true) }
                val sourceFaceDownCount = reconstructedSource.count { !it.faceUp }
                val sourceFaceUpCount = reconstructedSource.size - sourceFaceDownCount
                val sourceOverlap = spiderTableauOverlap(tableauLane, cardSize.height, sourceFaceDownCount, sourceFaceUpCount)
                val sourceOffsetsPx = with(density) { cardTopOffsets(reconstructedSource, sourceOverlap).map { it.toPx() } }
                val runStart = reconstructedSource.size - moved.cards.size
                val fromTopLeft = fromOrigin + Offset(0f, sourceOffsetsPx[runStart])
                val stepPx = with(density) { sourceOverlap.faceUpStep.toPx() }

                // The destination already holds the arrived run (`state` is post-move) — its own
                // landing spot is exactly where that run already sits there.
                val destFull = state.tableau[moved.toColumn]
                val destFaceDownCount = destFull.count { !it.faceUp }
                val destFaceUpCount = destFull.size - destFaceDownCount
                val destOverlap = spiderTableauOverlap(tableauLane, cardSize.height, destFaceDownCount, destFaceUpCount)
                val destOffsetsPx = with(density) { cardTopOffsets(destFull, destOverlap).map { it.toPx() } }
                val landingIndex = destFull.size - moved.cards.size
                val toTopLeft = toOrigin + Offset(0f, destOffsetsPx[landingIndex])

                MoveFlight(
                    fromColumn = moved.fromColumn,
                    toColumn = moved.toColumn,
                    cards = moved.cards,
                    fromTopLeft = fromTopLeft,
                    toTopLeft = toTopLeft,
                    stepPx = stepPx,
                    durationMs = durationForFlight(fromTopLeft, toTopLeft, density),
                )
            }
        }
        var moveFlightElapsedMs by remember(moveFlight) { mutableStateOf(0) }

        LaunchedEffect(moveFlight) {
            val flight = moveFlight ?: return@LaunchedEffect
            val startFrameNanos = withFrameNanos { it }
            while (moveFlightElapsedMs < flight.durationMs) {
                val nowNanos = withFrameNanos { it }
                moveFlightElapsedMs = (((nowNanos - startFrameNanos) / 1_000_000L).toInt()).coerceAtMost(flight.durationMs)
            }
        }
        val activeMoveFlight = moveFlight?.takeIf { moveFlightElapsedMs < it.durationMs }

        // A row deal: one flight per column, staggered left to right, sharing the stock as their
        // common departure point — built the same synchronous way as the flights above, and for
        // the same reason. A column the deal also completed a sequence in is left out here (see
        // `DealFlight`'s own doc): its bank flight already covers that card's departure.
        val dealFlight = remember(state) {
            val dealt = viewModel.lastDealtRow
            val fromTopLeft = stockRect?.topLeft
            if (skipAnimations || dealt == null || fromTopLeft == null) {
                null
            } else {
                val bankedColumns = viewModel.lastBankedRuns.map { it.column }.toSet()
                val deals = dealt.cards.mapIndexedNotNull { column, card -> if (column in bankedColumns) null else column to card }
                buildDealFlight(
                    fromTopLeft = fromTopLeft,
                    deals = deals,
                    toTopLeftFor = { column ->
                        val columnOrigin = columnRects[column]?.topLeft
                        if (columnOrigin == null) {
                            null
                        } else {
                            val full = state.tableau[column]
                            val faceDownCount = full.count { !it.faceUp }
                            val faceUpCount = full.size - faceDownCount
                            val overlap = spiderTableauOverlap(tableauLane, cardSize.height, faceDownCount, faceUpCount)
                            val offsetsPx = with(density) { cardTopOffsets(full, overlap).map { it.toPx() } }
                            columnOrigin + Offset(0f, offsetsPx.last())
                        }
                    },
                    density = density,
                )
            }
        }
        var dealFlightElapsedMs by remember(dealFlight) { mutableStateOf(0) }

        LaunchedEffect(dealFlight) {
            val flight = dealFlight ?: return@LaunchedEffect
            val startFrameNanos = withFrameNanos { it }
            while (dealFlightElapsedMs < flight.totalDurationMs) {
                val nowNanos = withFrameNanos { it }
                dealFlightElapsedMs = (((nowNanos - startFrameNanos) / 1_000_000L).toInt()).coerceAtMost(flight.totalDurationMs)
            }
        }
        val activeDealFlight = dealFlight?.takeIf { dealFlightElapsedMs < it.totalDurationMs }

        // Only while an actual flight is still under way — once its own elapsed time passes its
        // total duration this naturally empties without any explicit reset, and every column goes
        // back to rendering `state` directly.
        val activeBankFlights = bankFlights.filter { bankFlightElapsedMs < it.totalDurationMs }
        // Disabled for the automatic finish's entire run, not just while one of its moves is
        // actually mid-flight: `SpiderViewModel.isAutoFinishing` also covers the pause standing in
        // for a flight between commits, closing the gap a fast tap could otherwise land in.
        val inputEnabled = activeBankFlights.isEmpty() &&
            activeMoveFlight == null &&
            activeDealFlight == null &&
            !viewModel.isAutoFinishing

        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { boardCoordinates = it }
                .padding(8.dp),
        ) {
            if (!stockAtSide) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(cardSize.height),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StockPile(
                        rowDealsRemaining = state.rowDealsRemaining,
                        cardSize = cardSize,
                        colors = colors,
                        onTap = { if (inputEnabled) viewModel.tapStock() },
                        hinted = viewModel.hintedStock,
                        onPositioned = { coords ->
                            val board = boardCoordinates
                            if (board != null && coords.isAttached) {
                                stockRect = Rect(board.localPositionOf(coords, Offset.Zero), coords.size.toSize())
                            }
                        },
                    )
                    BankedCounters(
                        banked = state.banked,
                        suits = state.suitCount.suits,
                        colors = colors,
                        onSuitPositioned = { suit, coords ->
                            val board = boardCoordinates
                            if (board != null && coords.isAttached) {
                                bankedCounterRects[suit] = Rect(board.localPositionOf(coords, Offset.Zero), coords.size.toSize())
                            }
                        },
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(tableauLane),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                if (stockAtSide) {
                    // Top-aligned beside the columns, not centred: it is a pile like they are, and
                    // a stock floating at the midpoint of a tall lane reads as unrelated to them.
                    StockPile(
                        rowDealsRemaining = state.rowDealsRemaining,
                        cardSize = cardSize,
                        colors = colors,
                        onTap = { if (inputEnabled) viewModel.tapStock() },
                        hinted = viewModel.hintedStock,
                        onPositioned = { coords ->
                            val board = boardCoordinates
                            if (board != null && coords.isAttached) {
                                stockRect = Rect(board.localPositionOf(coords, Offset.Zero), coords.size.toSize())
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                for (column in 0 until TABLEAU_COLUMNS) {
                    // A column with an active bank flight keeps showing the run it is losing, one
                    // card at a time, instead of jumping straight to its already-shrunk real
                    // content — reconstructed from what is left (`state`, already the post-bank
                    // state) plus the cards the flight is carrying, in the order the tableau held
                    // them.
                    val flight = activeBankFlights.firstOrNull { it.column == column }
                    // An ordinary move touches two columns at once: the source still needs its
                    // departing run added back for the same reason as the bank flight above, and
                    // the destination needs its just-arrived run held back — `state` already has
                    // it there, but the run has not visibly landed yet.
                    val move = activeMoveFlight
                    // A row deal touches every column at once, each with its own single incoming
                    // card — withheld exactly like an ordinary move's own destination, until its
                    // own flight lands.
                    val dealCard = activeDealFlight?.cards?.firstOrNull { it.column == column && dealFlightElapsedMs < it.endMs }
                    val displayedCards = when {
                        flight != null -> state.tableau[column] + flight.cardsKingFirst.map { TableauCard(it, faceUp = true) }
                        move != null && move.fromColumn == column -> state.tableau[column] + move.cards.map { TableauCard(it, faceUp = true) }
                        move != null && move.toColumn == column -> state.tableau[column].dropLast(move.cards.size)
                        dealCard != null -> state.tableau[column].dropLast(1)
                        else -> state.tableau[column]
                    }
                    // Every card the bank flight has already launched (whether still in the air or
                    // already arrived) is hidden from the static column the same way a lifted drag
                    // sequence is — `hidden` in `TableauColumnView` does not care which reason. An
                    // ordinary move's own run hides as one rigid block for its whole flight, the
                    // same as a lifted drag: it has no per-card stagger to phase in with.
                    val launchedCount = flight?.cards?.count { bankFlightElapsedMs >= it.startMs } ?: 0
                    val bankHiddenFromIndex = flight?.let { displayedCards.size - launchedCount }
                    val moveHiddenFromIndex = move?.takeIf { it.fromColumn == column }?.let { displayedCards.size - it.cards.size }
                    TableauColumnView(
                        column = column,
                        cards = displayedCards,
                        cardSize = cardSize,
                        availableHeight = tableauLane,
                        colors = colors,
                        liftedFromIndex = bankHiddenFromIndex ?: moveHiddenFromIndex ?: drag?.takeIf { it.fromColumn == column }?.fromIndex,
                        inputEnabled = inputEnabled,
                        hintedIndices = viewModel.hintedCards.filter { it.column == column }.map { it.index }.toSet(),
                        isInvalid = viewModel.invalidFeedbackColumn == column,
                        onColumnPositioned = { coords ->
                            val board = boardCoordinates
                            if (board != null && coords.isAttached) {
                                columnRects[column] = Rect(board.localPositionOf(coords, Offset.Zero), coords.size.toSize())
                            }
                        },
                        onTapCard = { index -> viewModel.tapCard(column, index) },
                        onDragStartCard = { index, cardTopLeftInColumn, grabOffset ->
                            // `cardTopLeftInColumn` is local to this column's own Box (its x is
                            // always 0), not the board — `columnRects` are the board-relative
                            // frame that translates it, the same frame the drop lookup in
                            // `onDragEnd` below reads `pointerInBoard` against.
                            val columnOrigin = columnRects[column]?.topLeft ?: Offset.Zero
                            drag = DragInfo(
                                fromColumn = column,
                                fromIndex = index,
                                cards = state.tableau[column].subList(index, state.tableau[column].size),
                                grabOffset = grabOffset,
                                pointerInBoard = columnOrigin + cardTopLeftInColumn + grabOffset,
                            )
                        },
                        onDrag = { delta ->
                            drag = drag?.copy(pointerInBoard = drag!!.pointerInBoard + delta)
                        },
                        onDragEnd = {
                            val current = drag
                            drag = null
                            if (current != null) {
                                val target = columnWithMostOverlap(current, cardWidthPx, columnRects)
                                if (target != null) {
                                    viewModel.dragMove(current.fromColumn, current.fromIndex, target)
                                } else {
                                    viewModel.markDragInvalid(current.fromColumn)
                                }
                            }
                        },
                        onDragCancel = {
                            drag = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        drag?.let { info ->
            FloatingSequence(cards = info.cards, cardSize = cardSize, topLeft = info.pointerInBoard - info.grabOffset, colors = colors)
        }

        if (activeBankFlights.isNotEmpty()) {
            val elapsedMs = bankFlightElapsedMs
            for (flight in activeBankFlights) {
                for (flightCard in flight.cards) {
                    // Strictly less than its own end: the moment a card's flight completes it
                    // stops being drawn here at all — arriving is disappearing, not fading out.
                    if (elapsedMs < flightCard.startMs || elapsedMs >= flightCard.endMs) continue
                    val progress = (elapsedMs - flightCard.startMs).toFloat() / (flightCard.endMs - flightCard.startMs)
                    val position = Offset(
                        flightCard.fromTopLeft.x + (flight.toTopLeft.x - flightCard.fromTopLeft.x) * progress,
                        flightCard.fromTopLeft.y + (flight.toTopLeft.y - flightCard.fromTopLeft.y) * progress,
                    )
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                            .size(cardSize.width, cardSize.height),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCardFace(Offset.Zero, size, flightCard.card, colors)
                        }
                    }
                }
            }
        }

        activeMoveFlight?.let { flight ->
            val progress = (moveFlightElapsedMs.toFloat() / flight.durationMs).coerceIn(0f, 1f)
            val topLeft = Offset(
                flight.fromTopLeft.x + (flight.toTopLeft.x - flight.fromTopLeft.x) * progress,
                flight.fromTopLeft.y + (flight.toTopLeft.y - flight.fromTopLeft.y) * progress,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                flight.cards.forEachIndexed { i, card ->
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(topLeft.x.roundToInt(), (topLeft.y + i * flight.stepPx).roundToInt()) }
                            .size(cardSize.width, cardSize.height),
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCardFace(Offset.Zero, size, card, colors)
                        }
                    }
                }
            }
        }

        if (activeDealFlight != null) {
            val elapsedMs = dealFlightElapsedMs
            for (flightCard in activeDealFlight.cards) {
                // Strictly less than its own end: the moment a card's flight completes it stops
                // being drawn here at all — arriving is disappearing into the column, not fading.
                if (elapsedMs < flightCard.startMs || elapsedMs >= flightCard.endMs) continue
                val progress = (elapsedMs - flightCard.startMs).toFloat() / (flightCard.endMs - flightCard.startMs)
                val position = Offset(
                    activeDealFlight.fromTopLeft.x + (flightCard.toTopLeft.x - activeDealFlight.fromTopLeft.x) * progress,
                    activeDealFlight.fromTopLeft.y + (flightCard.toTopLeft.y - activeDealFlight.fromTopLeft.y) * progress,
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                        .size(cardSize.width, cardSize.height),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCardFace(Offset.Zero, size, flightCard.card, colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockPile(
    rowDealsRemaining: Int,
    cardSize: org.finiteplay.solitaire.ui.CardSize,
    colors: CardColors,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    hinted: Boolean = false,
    onPositioned: (LayoutCoordinates) -> Unit = {},
) {
    // Same pulse Hint puts on a tableau card's top: Hint found nothing to lift, so the stock — the
    // only other move left — lights instead of leaving the tap silent.
    val hintAlpha by animateFloatAsState(
        targetValue = if (hinted) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "spiderStockHintAlpha",
    )
    Box(
        modifier = modifier
            .size(cardSize.width, cardSize.height)
            .testTag("stock")
            .onGloballyPositioned(onPositioned)
            .pointerInput(rowDealsRemaining) { detectTapGestures(onTap = { onTap() }) },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (rowDealsRemaining > 0) drawCardBack(Offset.Zero, size, colors) else drawEmptySlot(Offset.Zero, size, colors)
            if (hintAlpha > 0f) drawHintHighlight(Offset.Zero, size, hintAlpha, colors)
            if (rowDealsRemaining > 0) {
                drawCenteredCardBadge(Offset.Zero, size, rowDealsRemaining.toString(), colors.faceDownBackOutline)
            }
        }
    }
}


@Composable
private fun TableauColumnView(
    column: Int,
    cards: List<TableauCard>,
    cardSize: org.finiteplay.solitaire.ui.CardSize,
    availableHeight: Dp,
    colors: CardColors,
    liftedFromIndex: Int?,
    hintedIndices: Set<Int>,
    isInvalid: Boolean,
    onColumnPositioned: (LayoutCoordinates) -> Unit,
    onTapCard: (index: Int) -> Unit,
    onDragStartCard: (index: Int, cardTopLeftInColumn: Offset, grabOffset: Offset) -> Unit,
    onDrag: (delta: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    inputEnabled: Boolean = true,
) {
    val faceDownCount = cards.count { !it.faceUp }
    val faceUpCount = cards.count { it.faceUp }
    val overlap = spiderTableauOverlap(availableHeight, cardSize.height, faceDownCount, faceUpCount)
    val offsets = cardTopOffsets(cards, overlap)
    // Pulses while a hint is showing rather than sitting flat, so it draws the eye without
    // permanently recolouring the board (`docs/PLATFORM.md` "Accessibility" — the animation is
    // a fade, never motion).
    val hintAlpha by animateFloatAsState(
        targetValue = if (hintedIndices.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "spiderHintAlpha",
    )
    // Resolved here rather than inside the gesture callbacks: those run outside any Density
    // receiver, so a Dp cannot convert itself there.
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
        // Drawn behind every card so an empty column is still a legal drop target to look at.
        Canvas(modifier = Modifier.size(cardSize.width, cardSize.height)) {
            drawEmptySlot(Offset.Zero, size, colors)
        }
        cards.forEachIndexed { index, tableauCard ->
            // Every card keeps its place in this loop even while it is being dragged, and hides
            // what it *draws* instead. Dropping the node out of composition here would tear down
            // the coroutine running its own gesture, so the drag it started could never report a
            // drop (`core:ui`'s `cardPointerInput`).
            //
            // `hidden` deliberately does not reach `enabled` below, for the same reason. A card
            // becomes hidden the moment a drag lifts it, so gating the gesture on it removes the
            // pointer-input modifiers from the very node whose gesture is in flight — the same
            // teardown by another route, and the one that made drag a no-op on a real device while
            // an instrumented test that dispatched a whole gesture between recompositions passed.
            val hidden = liftedFromIndex != null && index >= liftedFromIndex
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, offsets[index].roundToPx()) }
                    .size(cardSize.width, cardSize.height)
                    .testTag("card_${column}_$index")
                    .cardPointerInput(
                        enabled = tableauCard.faceUp && inputEnabled,
                        onTap = { onTapCard(index) },
                        onDragStart = { local ->
                            val coords = columnCoordinates
                            if (coords != null && coords.isAttached) {
                                val cardTopLeft = Offset(0f, offsetsPx[index])
                                onDragStartCard(index, cardTopLeft, local)
                            }
                        },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    ),
            ) {
                if (!hidden) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (tableauCard.faceUp) drawCardFace(Offset.Zero, size, tableauCard.card, colors) else drawCardBack(Offset.Zero, size, colors)
                        // Over the face, not under it: the highlight has to read against the card
                        // it marks, and only the exposed band of an overlapped card is visible.
                        if (index in hintedIndices && hintAlpha > 0f) {
                            drawHintHighlight(Offset.Zero, size, hintAlpha, colors)
                        }
                        if (isInvalid && index == cards.lastIndex) {
                            drawDestinationHighlight(Offset.Zero, size, colors.invalid)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingSequence(cards: List<TableauCard>, cardSize: org.finiteplay.solitaire.ui.CardSize, topLeft: Offset, colors: CardColors) {
    val step = cardSize.height * 0.3f
    Box(modifier = Modifier.fillMaxSize()) {
        cards.forEachIndexed { index, tableauCard ->
            Box(
                modifier = Modifier
                    .offset { IntOffset((topLeft.x).roundToInt(), (topLeft.y + step.toPx() * index).roundToInt()) }
                    .size(cardSize.width, cardSize.height),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCardFace(Offset.Zero, size, tableauCard.card, colors)
                }
            }
        }
    }
}


/**
 * The column whose horizontal span overlaps the dragged card's the most, or null when the card
 * doesn't overlap any column at all.
 *
 * Vertical position never enters this: a real finger obscures the exact card being dropped onto,
 * and a drop the width of the board wide but only where the pointer's own single point happens to
 * land is a stricter target than solitaire needs — this is a column choice, not a card choice, so
 * only the horizontal axis is asked. Overlap against the whole card, not a point test against the
 * pointer, so a drop under- or over-shooting a column's centre by less than half a card still
 * lands where most of the card visibly sits, rather than snapping back for having missed by a few
 * pixels.
 */
internal fun columnWithMostOverlap(drag: DragInfo, cardWidthPx: Float, columnRects: Map<Int, Rect>): Int? {
    val cardLeft = drag.pointerInBoard.x - drag.grabOffset.x
    val cardRight = cardLeft + cardWidthPx
    return columnRects.entries
        .map { (column, rect) -> column to (minOf(cardRight, rect.right) - maxOf(cardLeft, rect.left)) }
        .filter { (_, overlap) -> overlap > 0f }
        .maxByOrNull { (_, overlap) -> overlap }
        ?.first
}

/**
 * Cumulative vertical offset of each card's top edge, per `:solitaire:ui`'s
 * [org.finiteplay.solitaire.ui.TableauOverlap]: the step onto a newly revealed first face-up
 * card uses the face-down band, not the face-up one — only a step strictly between two face-up
 * cards gets the larger band.
 */
private fun cardTopOffsets(cards: List<TableauCard>, overlap: org.finiteplay.solitaire.ui.TableauOverlap): List<Dp> {
    if (cards.isEmpty()) return emptyList()
    val offsets = ArrayList<Dp>(cards.size)
    offsets += 0.dp
    for (i in 1 until cards.size) {
        val betweenTwoFaceUp = cards[i].faceUp && cards[i - 1].faceUp
        val step = if (betweenTwoFaceUp) overlap.faceUpStep else overlap.faceDownStep
        offsets += offsets.last() + step
    }
    return offsets
}
