package org.finiteplay.klondike.solver.search

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move

/**
 * A fast, mutable-free search-only mirror of [GameState], optimized for search node
 * throughput rather than API ergonomics. Cards are represented by their canonical
 * [org.finiteplay.cards.Card.id] (0..51). Never used to certify a win —
 * only [org.finiteplay.klondike.rules.applyMove] replay does that (see
 * `ReplayValidation.kt`).
 *
 * This uses the same compact layout insight as the catalog tool's `FastBoard`: one
 * primitive array per tableau column plus a face-down prefix count, and one pile in
 * draw order plus a stock/waste boundary. A* cannot reuse FastBoard's one mutable
 * make/unmake instance because it retains sibling states in its frontier, but immutable
 * nodes can still share every untouched column and every Draw/Recycle can share the
 * entire pile. That removes most of the nested-array churn of the old split
 * down/up/stock/waste representation without changing the move graph.
 *
 * In [pile], `0 until wasteCount` is the waste bottom-to-top and
 * `wasteCount until pile.size` is the stock next-first. Drawing only advances
 * [wasteCount]; recycling resets it to zero. Because only legal moves are applied,
 * the face-up suffix of every [columns] entry is always a valid run.
 */
class SNode(
    val columns: Array<ByteArray>,
    val downCounts: IntArray,
    val foundations: IntArray,
    val pile: ByteArray,
    val wasteCount: Int,
) {
    val stockSize: Int get() = pile.size - wasteCount
    val wasteSize: Int get() = wasteCount
}

private const val RANKS_PER_SUIT = 13
private const val CARD_COUNT = 52
private const val FNV_OFFSET_BASIS = -3750763034362895579L
private const val FNV_PRIME = 1099511628211L
private const val FOUNDATION_DESTINATION = 1 shl TABLEAU_COLUMNS
private const val TABLEAU_DESTINATIONS = FOUNDATION_DESTINATION - 1

private val CARD_RANK = IntArray(CARD_COUNT) { it % RANKS_PER_SUIT + 1 }
private val CARD_SUIT = IntArray(CARD_COUNT) { it / RANKS_PER_SUIT }
private val CARD_RED = BooleanArray(CARD_COUNT) { CARD_SUIT[it] == 1 || CARD_SUIT[it] == 2 }

private fun rankValue(cardId: Int): Int = CARD_RANK[cardId]
private fun suitOrdinal(cardId: Int): Int = CARD_SUIT[cardId]
private fun isRed(cardId: Int): Boolean = CARD_RED[cardId]
private fun rankValue(cardId: Byte): Int = rankValue(cardId.toInt())
private fun suitOrdinal(cardId: Byte): Int = suitOrdinal(cardId.toInt())
private fun isRed(cardId: Byte): Boolean = isRed(cardId.toInt())

/** Mirrors [org.finiteplay.klondike.rules.isSafeFoundationMove]. */
private fun isSafeFoundationMove(foundations: IntArray, cardId: Int): Boolean {
    val rank = rankValue(cardId)
    if (rank <= 2) return true
    val previous = rank - 1
    return foundations.all { it >= previous }
}

fun isWon(node: SNode): Boolean = node.foundations.all { it == 13 }

/**
 * Cards not yet on a foundation — one term of [movesLowerBound], kept separately
 * because it is also the simplest statement of "how far from won" a board is.
 * Admissible on its own: a move can bank at most one new card, so every one of the
 * `52 - banked` remaining cards needs at least one more move.
 */
fun foundationDeficit(node: SNode): Int = 52 - node.foundations.sum()

/** True when any card in `cards[0 until limit]` shares [cardId]'s suit at a lower rank. */
private fun anySameSuitLower(cards: ByteArray, limit: Int, cardId: Byte): Boolean {
    val suit = suitOrdinal(cardId)
    val rank = rankValue(cardId)
    for (j in 0 until limit) {
        val other = cards[j]
        if (suitOrdinal(other) == suit && rankValue(other) < rank) return true
    }
    return false
}

/**
 * Admissible A* lower bound on moves still needed, the sum of three counts whose
 * corresponding future moves are pairwise distinct:
 *
 * - [foundationDeficit]: each unbanked card needs its own to-foundation move.
 * - Cards still in stock: each needs its own [org.finiteplay.klondike.rules.Move.Draw]
 *   before anything else can happen to it (a recycle only refills the stock, making the
 *   true draw count higher, never lower).
 * - Same-suit burials: a card lying above a same-suit lower-ranked card in its own
 *   column must move off that column before the lower card can ever be exposed, and
 *   that move can never be its banking move — banking it would need its suit's
 *   foundation to have already passed the very card still buried beneath it. So it is
 *   an extra tableau move beyond the deficit. Counted once per face-down card so
 *   burdened (face-down cards can never share a moving run with cards from beneath
 *   them, so their move-offs are pairwise distinct), but only once for the whole
 *   face-up run (its cards can leave together in one run move, so only one extra move
 *   is provable however many of them are burdened). Within a valid face-up run a
 *   deeper card always outranks a shallower one, so an up card's burial can only ever
 *   be a face-down card below it.
 *
 * The burial term makes the bound admissible but not *consistent*: one banking move
 * can also flip the card beneath it and so retire a burial credit with it, dropping
 * the bound by 2 for a 1-cost move. A* stays exact despite that because the search
 * re-admits a state whenever a cheaper path to it appears (see `bestG` in `Solver.kt`)
 * — admissibility plus re-opening is the classical optimality condition; consistency
 * is only ever an optimization.
 */
/**
 * Cards lying above an ace in their tableau column, summed over every buried ace — a
 * search-ordering progress signal (`SearchOrdering.aceBurialWeight`), not a lower
 * bound: those cards need not each cost an extra move, but boards that keep aces deep
 * are consistently the ones a greedy search should deprioritize.
 */
fun aceBurialCount(node: SNode): Int {
    var count = 0
    for (column in 0 until TABLEAU_COLUMNS) {
        val cards = node.columns[column]
        for (i in cards.indices) {
            if (rankValue(cards[i]) == 1) count += cards.lastIndex - i
        }
    }
    return count
}

/**
 * How deeply buried each foundation's next needed card currently is, summed over the
 * four suits: cards above it in its tableau column, or its index from the top of the
 * waste, or its position in the stock. Another `SearchOrdering` progress signal, not
 * a bound — surfacing the specific cards the foundations are waiting on is what
 * separates genuinely progressing plateau states from busywork reshuffles.
 */
fun neededCardDepth(node: SNode): Int {
    var depth = 0
    for (suit in 0 until 4) {
        val rank = node.foundations[suit]
        if (rank >= RANKS_PER_SUIT) continue
        val neededId = suit * RANKS_PER_SUIT + rank
        depth += burialDepthOf(node, neededId)
    }
    return depth
}

private fun burialDepthOf(node: SNode, cardId: Int): Int {
    for (column in 0 until TABLEAU_COLUMNS) {
        val cards = node.columns[column]
        for (i in cards.indices) {
            if (cards[i].toInt() == cardId) return cards.lastIndex - i
        }
    }
    for (i in 0 until node.wasteCount) {
        if (node.pile[i].toInt() == cardId) return node.wasteCount - 1 - i
    }
    for (i in node.wasteCount until node.pile.size) {
        if (node.pile[i].toInt() == cardId) return i - node.wasteCount
    }
    return 0
}

/**
 * [drawCount] is [org.finiteplay.klondike.board.DrawMode]'s card-per-draw count
 * (1 or 3): the stock term below counts *draw moves* needed, not stock cards, so it
 * stays admissible under draw-three too — `ceil(stock / drawCount)` draws clear the
 * stock, not one draw per card.
 */
fun movesLowerBound(node: SNode, drawCount: Int = 1): Int {
    var bound = 52 + (node.stockSize + drawCount - 1) / drawCount
    for (f in node.foundations) bound -= f
    for (column in 0 until TABLEAU_COLUMNS) {
        val cards = node.columns[column]
        val downCount = node.downCounts[column]
        for (i in 1 until downCount) {
            if (anySameSuitLower(cards, i, cards[i])) bound++
        }
        for (i in downCount until cards.size) {
            if (anySameSuitLower(cards, downCount, cards[i])) {
                bound++
                break
            }
        }
    }
    return bound
}

fun snodeFrom(state: GameState): SNode {
    val columns = Array(TABLEAU_COLUMNS) { column -> state.tableau[column].map { it.card.id.toByte() }.toByteArray() }
    val downCounts = IntArray(TABLEAU_COLUMNS) { column -> state.tableau[column].count { !it.faceUp } }
    val foundations = IntArray(4) { suit -> state.foundations.getValue(Suit.entries[suit]) }
    val pile = ByteArray(state.waste.size + state.stock.size)
    var at = 0
    for (index in state.waste.indices.reversed()) pile[at++] = state.waste[index].id.toByte()
    for (card in state.stock) pile[at++] = card.id.toByte()
    return SNode(columns, downCounts, foundations, pile, state.waste.size)
}

private fun withColumn(columns: Array<ByteArray>, index: Int, value: ByteArray): Array<ByteArray> {
    val copy = columns.copyOf()
    copy[index] = value
    return copy
}

private fun withColumns(
    columns: Array<ByteArray>,
    firstIndex: Int,
    firstValue: ByteArray,
    secondIndex: Int,
    secondValue: ByteArray,
): Array<ByteArray> {
    val copy = columns.copyOf()
    copy[firstIndex] = firstValue
    copy[secondIndex] = secondValue
    return copy
}

private fun withDownCount(counts: IntArray, column: Int, value: Int): IntArray {
    val copy = counts.copyOf()
    copy[column] = value
    return copy
}

private fun withoutPileCard(node: SNode, index: Int): ByteArray {
    val copy = ByteArray(node.pile.size - 1)
    System.arraycopy(node.pile, 0, copy, 0, index)
    System.arraycopy(node.pile, index + 1, copy, index, copy.size - index)
    return copy
}

private fun appendRange(prefix: ByteArray, suffix: ByteArray, suffixStart: Int): ByteArray {
    val result = ByteArray(prefix.size + suffix.size - suffixStart)
    System.arraycopy(prefix, 0, result, 0, prefix.size)
    System.arraycopy(suffix, suffixStart, result, prefix.size, suffix.size - suffixStart)
    return result
}

private fun withFoundation(foundations: IntArray, suit: Int, value: Int): IntArray {
    val copy = foundations.copyOf()
    copy[suit] = value
    return copy
}

/** A candidate move paired with a search-ordering priority; lower is tried first. */
class ScoredMove(val move: Move, val priority: Int)

// --- Packed moves -----------------------------------------------------------
// The search hot path passes moves as packed Ints — generating, scoring, applying,
// and storing a move allocates nothing, which matters enormously on ART, where the
// interactive hint search was measured GC-bound long before it was CPU-bound. A
// [Move] object is only materialized at the edges: certificate reconstruction, and
// the List-returning [generateMoves] wrapper the tests and the brute-force reference
// search use.

/** A [SearchNode][Solver]-root sentinel: no move led to this state. */
const val PACKED_MOVE_NONE = -1

private const val TYPE_DRAW = 0
private const val TYPE_RECYCLE = 1
private const val TYPE_TABLEAU_TO_TABLEAU = 2
private const val TYPE_TABLEAU_TO_FOUNDATION = 3
private const val TYPE_WASTE_TO_TABLEAU = 4
private const val TYPE_WASTE_TO_FOUNDATION = 5
private const val TYPE_FOUNDATION_TO_TABLEAU = 6

private fun packedType(packed: Int): Int = packed and 0x7
fun packedFromColumn(packed: Int): Int = (packed ushr 3) and 0x7
fun packedFromIndex(packed: Int): Int = (packed ushr 6) and 0x1F
fun packedToColumn(packed: Int): Int = (packed ushr 11) and 0x7
private fun packedSuit(packed: Int): Int = (packed ushr 14) and 0x3
fun packedPriority(packed: Int): Int = (packed ushr 16) and 0x7

fun packedIsTableauToTableau(packed: Int): Boolean = packedType(packed) == TYPE_TABLEAU_TO_TABLEAU

private fun pack(type: Int, from: Int = 0, fromIndex: Int = 0, to: Int = 0, suit: Int = 0, priority: Int = 0): Int =
    type or (from shl 3) or (fromIndex shl 6) or (to shl 11) or (suit shl 14) or (priority shl 16)

fun packMove(move: Move): Int = when (move) {
    is Move.Draw -> pack(TYPE_DRAW)
    is Move.Recycle -> pack(TYPE_RECYCLE)
    is Move.TableauToTableau -> pack(TYPE_TABLEAU_TO_TABLEAU, from = move.fromColumn, fromIndex = move.fromIndex, to = move.toColumn)
    is Move.TableauToFoundation -> pack(TYPE_TABLEAU_TO_FOUNDATION, from = move.fromColumn)
    is Move.WasteToTableau -> pack(TYPE_WASTE_TO_TABLEAU, to = move.toColumn)
    is Move.WasteToFoundation -> pack(TYPE_WASTE_TO_FOUNDATION)
    is Move.FoundationToTableau -> pack(TYPE_FOUNDATION_TO_TABLEAU, suit = move.suit.ordinal, to = move.toColumn)
}

fun unpackMove(packed: Int): Move = when (packedType(packed)) {
    TYPE_DRAW -> Move.Draw
    TYPE_RECYCLE -> Move.Recycle
    TYPE_TABLEAU_TO_TABLEAU -> Move.TableauToTableau(packedFromColumn(packed), packedFromIndex(packed), packedToColumn(packed))
    TYPE_TABLEAU_TO_FOUNDATION -> Move.TableauToFoundation(packedFromColumn(packed))
    TYPE_WASTE_TO_TABLEAU -> Move.WasteToTableau(packedToColumn(packed))
    TYPE_WASTE_TO_FOUNDATION -> Move.WasteToFoundation
    else -> Move.FoundationToTableau(Suit.entries[packedSuit(packed)], packedToColumn(packed))
}

/** Mirrors [org.finiteplay.klondike.rules.isInverseOfPreviousMove] over packed moves. */
fun isInverseOfPreviousPacked(previous: Int, candidate: Int): Boolean {
    if (previous == PACKED_MOVE_NONE) return false
    val previousType = packedType(previous)
    val candidateType = packedType(candidate)
    return when {
        previousType == TYPE_TABLEAU_TO_TABLEAU && candidateType == TYPE_TABLEAU_TO_TABLEAU ->
            packedFromColumn(previous) == packedToColumn(candidate) && packedToColumn(previous) == packedFromColumn(candidate)
        previousType == TYPE_TABLEAU_TO_FOUNDATION && candidateType == TYPE_FOUNDATION_TO_TABLEAU ->
            packedToColumn(candidate) == packedFromColumn(previous)
        previousType == TYPE_FOUNDATION_TO_TABLEAU && candidateType == TYPE_TABLEAU_TO_FOUNDATION ->
            packedFromColumn(candidate) == packedToColumn(previous)
        else -> false
    }
}

/** Reusable packed-move list: one per search, [clear]ed at each expansion, grown rarely. */
class MoveBuffer {
    var moves = IntArray(64)
        private set
    var size = 0
        private set

    fun clear() {
        size = 0
    }

    fun add(packed: Int) {
        if (size == moves.size) moves = moves.copyOf(size * 2)
        moves[size++] = packed
    }
}

/** One reusable legality index per search; rebuilt from seven exposed cards per expansion. */
class DestinationMaskBuffer {
    val byCard = ByteArray(CARD_COUNT)
}

private fun rebuildDestinationMasks(node: SNode, masks: ByteArray) {
    masks.fill(0)
    fun add(card: Int, bit: Int) {
        masks[card] = (masks[card].toInt() or bit).toByte()
    }

    for (column in 0 until TABLEAU_COLUMNS) {
        val bit = 1 shl column
        val cards = node.columns[column]
        if (cards.isEmpty()) {
            for (suit in 0 until 4) add(suit * RANKS_PER_SUIT + RANKS_PER_SUIT - 1, bit)
            continue
        }
        val under = cards[cards.lastIndex].toInt()
        val acceptedRank = rankValue(under) - 1
        if (acceptedRank == 0) continue
        val firstSuit = if (isRed(under)) 0 else 1
        val secondSuit = if (isRed(under)) 3 else 2
        add(firstSuit * RANKS_PER_SUIT + acceptedRank - 1, bit)
        add(secondSuit * RANKS_PER_SUIT + acceptedRank - 1, bit)
    }
    for (suit in 0 until 4) {
        val rank = node.foundations[suit]
        if (rank < RANKS_PER_SUIT) add(suit * RANKS_PER_SUIT + rank, FOUNDATION_DESTINATION)
    }
}

private fun destinationMask(masks: ByteArray, cardId: Int): Int = masks[cardId].toInt() and 0xFF

/**
 * Every legal move from [node]. [Move.FoundationToTableau] is only generated when
 * [includeFoundationWithdrawal] is `true` — it roughly doubles branching factor at
 * every node with a foundation card exposed, which the D1s/D1b catalog spikes cannot
 * afford across a batch run (see the D1s report). The interactive on-device hint
 * search (`docs/games/klondike/DESIGN.md` "On-Device Hint Search") enables it, because a search that
 * can never try this move can wrongly report a solvable position as having no path to
 * a win.
 *
 * When a *safe* foundation move exists ([isSafeFoundationMove]'s strict rule: every
 * suit's foundation already at the card's rank minus one, or rank one/two), that move
 * is generated **alone** — a forced move, not merely a preferred one. Sound for both
 * of the search's guarantees, by the standard exchange argument: any winning line
 * from this state converts to one at most as long that banks the safe card first
 * (nothing can ever again need it on the tableau — every card that could stack on it
 * is already banked, and re-stacking cards withdrawn from foundations onto it only
 * lengthens a line), so pruning the alternatives can neither lose a shortest
 * certificate nor turn a solvable state unsolvable. This collapses long stretches of
 * the endgame and every safe-banking opportunity mid-game to branching factor 1.
 *
 * Emission order is otherwise meaningless: A* admits every child to its priority
 * queue, so [ScoredMove.priority] only feeds the tie-break penalty, never ordering.
 */
fun generateMoves(node: SNode, includeFoundationWithdrawal: Boolean = false): List<ScoredMove> {
    val buffer = MoveBuffer()
    generateMovesPacked(node, buffer, includeFoundationWithdrawal, DestinationMaskBuffer())
    val out = ArrayList<ScoredMove>(buffer.size)
    for (i in 0 until buffer.size) {
        val packed = buffer.moves[i]
        out += ScoredMove(unpackMove(packed), packedPriority(packed))
    }
    return out
}

/** Compatibility wrapper for non-search callers; the A* hot path supplies reusable scratch. */
fun generateMovesPacked(node: SNode, out: MoveBuffer, includeFoundationWithdrawal: Boolean) =
    generateMovesPacked(node, out, includeFoundationWithdrawal, DestinationMaskBuffer())

/** [generateMoves]'s allocation-free hot-path form; identical emission order. */
fun generateMovesPacked(
    node: SNode,
    out: MoveBuffer,
    includeFoundationWithdrawal: Boolean,
    destinationMasks: DestinationMaskBuffer,
) {
    out.clear()
    val masks = destinationMasks.byCard
    rebuildDestinationMasks(node, masks)

    for (from in 0 until TABLEAU_COLUMNS) {
        val cards = node.columns[from]
        if (cards.size == node.downCounts[from]) continue
        val top = cards[cards.lastIndex]
        if (destinationMask(masks, top.toInt()) and FOUNDATION_DESTINATION != 0 &&
            isSafeFoundationMove(node.foundations, top.toInt())
        ) {
            out.add(pack(TYPE_TABLEAU_TO_FOUNDATION, from = from, priority = 0))
            return
        }
    }
    if (node.wasteCount > 0) {
        val top = node.pile[node.wasteCount - 1]
        if (destinationMask(masks, top.toInt()) and FOUNDATION_DESTINATION != 0 &&
            isSafeFoundationMove(node.foundations, top.toInt())
        ) {
            out.add(pack(TYPE_WASTE_TO_FOUNDATION, priority = 0))
            return
        }
    }

    for (from in 0 until TABLEAU_COLUMNS) {
        val cards = node.columns[from]
        val downCount = node.downCounts[from]
        for (fromIndex in downCount until cards.size) {
            val sequenceBottom = cards[fromIndex]
            val reveals = fromIndex == downCount && downCount > 0
            val empties = fromIndex == 0
            // Empty destination columns are interchangeable — no rule depends on a
            // column's index — so at most one is worth generating per run: the rest
            // land on canonically identical states the closed set would reject after
            // paying full child-expansion cost for each.
            var emptyDestinationSeen = false
            var destinations = destinationMask(masks, sequenceBottom.toInt()) and TABLEAU_DESTINATIONS
            while (destinations != 0) {
                val to = Integer.numberOfTrailingZeros(destinations)
                destinations = destinations and (destinations - 1)
                val toEmpty = node.columns[to].isEmpty()
                if (toEmpty) {
                    // A whole column's run (down empty, moved from its very bottom)
                    // relocating to another empty column only swaps which column
                    // index is "empty" — provably useless, never generated at all.
                    if (empties || emptyDestinationSeen) continue
                }
                if (toEmpty) emptyDestinationSeen = true
                val priority = if (reveals || empties) 1 else 2
                out.add(pack(TYPE_TABLEAU_TO_TABLEAU, from = from, fromIndex = fromIndex, to = to, priority = priority))
            }
        }
        if (cards.size > downCount) {
            val top = cards[cards.lastIndex]
            if (destinationMask(masks, top.toInt()) and FOUNDATION_DESTINATION != 0) {
                val reveals = cards.size - downCount == 1 && downCount > 0
                out.add(pack(TYPE_TABLEAU_TO_FOUNDATION, from = from, priority = if (reveals) 1 else 2))
            }
        }
    }

    if (node.wasteCount > 0) {
        val top = node.pile[node.wasteCount - 1]
        var emptyDestinationSeen = false
        var destinations = destinationMask(masks, top.toInt()) and TABLEAU_DESTINATIONS
        while (destinations != 0) {
            val to = Integer.numberOfTrailingZeros(destinations)
            destinations = destinations and (destinations - 1)
            val toEmpty = node.columns[to].isEmpty()
            if (toEmpty && emptyDestinationSeen) continue
            if (toEmpty) emptyDestinationSeen = true
            out.add(pack(TYPE_WASTE_TO_TABLEAU, to = to, priority = 2))
        }
        if (destinationMask(masks, top.toInt()) and FOUNDATION_DESTINATION != 0) {
            out.add(pack(TYPE_WASTE_TO_FOUNDATION, priority = 2))
        }
    }

    if (includeFoundationWithdrawal) {
        for (suit in 0 until 4) {
            val rank = node.foundations[suit]
            if (rank == 0) continue
            val cardId = suit * RANKS_PER_SUIT + (rank - 1)
            var emptyDestinationSeen = false
            var destinations = destinationMask(masks, cardId) and TABLEAU_DESTINATIONS
            while (destinations != 0) {
                val to = Integer.numberOfTrailingZeros(destinations)
                destinations = destinations and (destinations - 1)
                val toEmpty = node.columns[to].isEmpty()
                if (toEmpty && emptyDestinationSeen) continue
                if (toEmpty) emptyDestinationSeen = true
                out.add(pack(TYPE_FOUNDATION_TO_TABLEAU, suit = suit, to = to, priority = 5))
            }
        }
    }

    if (node.stockSize > 0) out.add(pack(TYPE_DRAW, priority = 3))
    if (node.stockSize == 0 && node.wasteCount > 0) out.add(pack(TYPE_RECYCLE, priority = 4))
}

/** Applies [move] to [node]. Mirrors `applyMove` in `ApplyMove.kt` for the move subset this solver generates. */
fun applySearchMove(node: SNode, move: Move, drawCount: Int = 1): SNode = applySearchMovePacked(node, packMove(move), drawCount)

/**
 * [applySearchMove]'s hot-path form: no [Move] materialization, no pair/tuple
 * allocation. [drawCount] mirrors [org.finiteplay.klondike.board.DrawMode]'s
 * card-per-draw count (default 1); [TYPE_DRAW] moves `min(drawCount, stock.size)`
 * cards, exactly like `ApplyMove.kt`'s `Move.Draw`.
 */
fun applySearchMovePacked(node: SNode, packed: Int, drawCount: Int = 1): SNode = when (packedType(packed)) {
    TYPE_DRAW -> {
        val count = drawCount.coerceAtMost(node.stockSize)
        SNode(node.columns, node.downCounts, node.foundations, node.pile, node.wasteCount + count)
    }

    TYPE_RECYCLE -> SNode(node.columns, node.downCounts, node.foundations, node.pile, wasteCount = 0)

    TYPE_TABLEAU_TO_TABLEAU -> {
        val fromColumn = packedFromColumn(packed)
        val toColumn = packedToColumn(packed)
        val from = node.columns[fromColumn]
        val fromIndex = packedFromIndex(packed)
        val newFrom = from.copyOfRange(0, fromIndex)
        val newTo = appendRange(node.columns[toColumn], from, fromIndex)
        // Auto-flip on emptying the face-up run; mirrors `withAutoFlip` in `ApplyMove.kt`.
        val reveals = fromIndex == node.downCounts[fromColumn] && node.downCounts[fromColumn] > 0
        val newDownCounts = if (reveals) {
            withDownCount(node.downCounts, fromColumn, node.downCounts[fromColumn] - 1)
        } else {
            node.downCounts
        }
        SNode(
            withColumns(node.columns, fromColumn, newFrom, toColumn, newTo),
            newDownCounts,
            node.foundations,
            node.pile,
            node.wasteCount,
        )
    }

    TYPE_TABLEAU_TO_FOUNDATION -> {
        val fromColumn = packedFromColumn(packed)
        val from = node.columns[fromColumn]
        val card = from[from.lastIndex]
        val newFrom = from.copyOfRange(0, from.lastIndex)
        val reveals = from.size - node.downCounts[fromColumn] == 1 && node.downCounts[fromColumn] > 0
        val newDownCounts = if (reveals) {
            withDownCount(node.downCounts, fromColumn, node.downCounts[fromColumn] - 1)
        } else {
            node.downCounts
        }
        val newFoundations = withFoundation(node.foundations, suitOrdinal(card), rankValue(card))
        SNode(withColumn(node.columns, fromColumn, newFrom), newDownCounts, newFoundations, node.pile, node.wasteCount)
    }

    TYPE_WASTE_TO_TABLEAU -> {
        val toColumn = packedToColumn(packed)
        val pileIndex = node.wasteCount - 1
        val card = node.pile[pileIndex]
        val newTo = node.columns[toColumn] + card
        SNode(
            withColumn(node.columns, toColumn, newTo),
            node.downCounts,
            node.foundations,
            withoutPileCard(node, pileIndex),
            node.wasteCount - 1,
        )
    }

    TYPE_WASTE_TO_FOUNDATION -> {
        val pileIndex = node.wasteCount - 1
        val card = node.pile[pileIndex]
        val newFoundations = withFoundation(node.foundations, suitOrdinal(card), rankValue(card))
        SNode(node.columns, node.downCounts, newFoundations, withoutPileCard(node, pileIndex), node.wasteCount - 1)
    }

    else -> {
        val suit = packedSuit(packed)
        val toColumn = packedToColumn(packed)
        val rank = node.foundations[suit]
        val cardId = suit * RANKS_PER_SUIT + (rank - 1)
        val newFoundations = withFoundation(node.foundations, suit, rank - 1)
        val newTo = node.columns[toColumn] + cardId.toByte()
        SNode(withColumn(node.columns, toColumn, newTo), node.downCounts, newFoundations, node.pile, node.wasteCount)
    }
}

/**
 * A 64-bit FNV-1a-style rolling hash over the full state, used only as a transposition
 * table / visited-set key. A collision can only cause the search to skip a state it
 * has (falsely) already seen — i.e. it can make the search miss a solution, never
 * fabricate one — because every accepted certificate is independently replayed through
 * the canonical reducer before being admitted (see `ReplayValidation.kt`).
 */
fun hashOf(node: SNode): Long {
    var h = -3750763034362895579L // FNV-1a 64-bit offset basis
    val prime = 1099511628211L

    fun mix(v: Int) {
        h = h xor v.toLong()
        h *= prime
    }

    for (column in 0 until TABLEAU_COLUMNS) {
        val cards = node.columns[column]
        val downCount = node.downCounts[column]
        mix(1000 + downCount)
        for (i in 0 until downCount) mix(cards[i].toInt())
        mix(2000 + cards.size - downCount)
        for (i in downCount until cards.size) mix(cards[i].toInt())
    }
    for (f in node.foundations) mix(3000 + f)
    mix(4000 + node.wasteCount)
    for (i in node.wasteCount - 1 downTo 0) mix(node.pile[i].toInt())
    mix(5000 + node.stockSize)
    for (i in node.wasteCount until node.pile.size) mix(node.pile[i].toInt())

    return h
}

/** Order-preserving hash of one tableau column's own down+up sequence, for [canonicalHashOf]. */
private fun columnSignature(cards: ByteArray, downCount: Int): Long {
    var h = (FNV_OFFSET_BASIS xor (1000 + downCount).toLong()) * FNV_PRIME
    for (i in 0 until downCount) h = (h xor cards[i].toLong()) * FNV_PRIME
    h = (h xor (2000 + cards.size - downCount).toLong()) * FNV_PRIME
    for (i in downCount until cards.size) h = (h xor cards[i].toLong()) * FNV_PRIME
    return h
}

private fun compareSwap(values: LongArray, left: Int, right: Int) {
    val leftValue = values[left]
    val rightValue = values[right]
    if (leftValue > rightValue) {
        values[left] = rightValue
        values[right] = leftValue
    }
}

/** Optimal 16-comparator sorting network for the fixed seven-column tableau. */
private fun sortColumnSignatures(values: LongArray) {
    compareSwap(values, 0, 6)
    compareSwap(values, 2, 3)
    compareSwap(values, 4, 5)
    compareSwap(values, 0, 2)
    compareSwap(values, 1, 4)
    compareSwap(values, 3, 6)
    compareSwap(values, 0, 1)
    compareSwap(values, 2, 5)
    compareSwap(values, 3, 4)
    compareSwap(values, 1, 2)
    compareSwap(values, 4, 6)
    compareSwap(values, 2, 3)
    compareSwap(values, 4, 5)
    compareSwap(values, 1, 2)
    compareSwap(values, 3, 4)
    compareSwap(values, 5, 6)
}

/**
 * Board-only fingerprint, invariant under which physical tableau column index holds
 * which pile. It deliberately excludes the stock/waste cards and boundary, so a Draw
 * alone does not change it. Use this only for board-loop analysis, never as A*'s
 * transposition identity: move-level search must distinguish pile positions or its
 * first Draw would be discarded as a duplicate of its parent.
 *
 * Two states differing only by column *order* collapse to the same value. Sound
 * because no rule or move generator ever depends on a column's index, only on its own
 * content (the destination-mask generator is symmetric over
 * every column) — permuting columns can never change what is reachable from a state,
 * so two permutations of the same columns are, for search purposes, the same state.
 * Klondike boards spend a lot of their nominal branching on exactly this symmetry
 * (the same handful of columns get reordered many different ways), so collapsing it
 * is where most of the practical node-count reduction comes from.
 *
 * Sorting is confined to caller-owned scratch; [node] remains immutable.
 */
fun canonicalHashOf(node: SNode): Long = canonicalHashOf(node, LongArray(TABLEAU_COLUMNS))

/**
 * Allocation-free hot-path form. Sorting [signatureScratch] canonicalizes column
 * order; no array owned by [node] is written or reordered.
 */
fun canonicalHashOf(node: SNode, signatureScratch: LongArray): Long {
    require(signatureScratch.size >= TABLEAU_COLUMNS)
    for (column in 0 until TABLEAU_COLUMNS) {
        signatureScratch[column] = columnSignature(node.columns[column], node.downCounts[column])
    }
    sortColumnSignatures(signatureScratch)

    var h = FNV_OFFSET_BASIS
    for (column in 0 until TABLEAU_COLUMNS) h = (h xor signatureScratch[column]) * FNV_PRIME
    for (f in node.foundations) h = (h xor (3000 + f).toLong()) * FNV_PRIME

    return h
}

/**
 * Full column-order-invariant state key for A* and DFS transposition, extending the
 * board-only [canonicalHashOf] with waste order, stock order, and their boundary.
 */
fun canonicalStateHashOf(node: SNode): Long = canonicalStateHashOf(node, LongArray(TABLEAU_COLUMNS))

/** Allocation-free hot-path form; only [signatureScratch] is sorted. */
fun canonicalStateHashOf(node: SNode, signatureScratch: LongArray): Long {
    var h = canonicalHashOf(node, signatureScratch)
    h = (h xor (4000 + node.wasteCount).toLong()) * FNV_PRIME
    for (i in node.wasteCount - 1 downTo 0) h = (h xor node.pile[i].toLong()) * FNV_PRIME
    h = (h xor (5000 + node.stockSize).toLong()) * FNV_PRIME
    for (i in node.wasteCount until node.pile.size) h = (h xor node.pile[i].toLong()) * FNV_PRIME

    return h
}
