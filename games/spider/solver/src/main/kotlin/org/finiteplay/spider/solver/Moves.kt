package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.TABLEAU_COLUMNS

/**
 * A move packed into an Int, so the search's move lists and its path are primitive arrays rather
 * than objects: `from | to shl 4 | fromIndex shl 8`, with [DEAL_ROW] as a reserved value.
 */
@JvmInline
value class FastMove(val packed: Int) {
    val from: Int get() = packed and 0xF
    val to: Int get() = (packed shr 4) and 0xF
    val fromIndex: Int get() = (packed shr 8) and 0xFF
    val isDeal: Boolean get() = packed == DEAL_ROW.packed

    companion object {
        val DEAL_ROW = FastMove(-1)
        fun transfer(from: Int, fromIndex: Int, to: Int) = FastMove(from or (to shl 4) or (fromIndex shl 8))
    }
}

/**
 * What a move did, kept so it can be undone without copying the board.
 *
 * [runLength] is recorded rather than recomputed. An earlier version inferred it by walking the
 * destination's top run, which is wrong exactly when the run lands on a card that continues its
 * sequence — the common case this game is built around — and would have unwound the wrong number
 * of cards.
 *
 * Banking is recorded the same way and for the same reason: one move can bank several columns (a
 * row deal can complete more than one at once), and the thirteen cards that left are known here
 * but not reconstructible afterwards with two decks in play.
 */
class UndoRecord {
    var move: FastMove = FastMove.DEAL_ROW
    var runLength = 0
    var flippedSource = false
    val bankedColumns = IntArray(TABLEAU_COLUMNS)
    val bankedFlipped = BooleanArray(TABLEAU_COLUMNS)
    val bankedCards = Array(TABLEAU_COLUMNS) { ByteArray(FastBoard.RANKS) }
    var bankedCount = 0
}

/**
 * Appends every legal move to [out], productive ones first.
 *
 * Order matters more than completeness for a depth-first search: the earlier a winning line
 * appears, the fewer nodes are spent on lines that lose. Banking moves go first, then moves that
 * expose a face-down card, then the rest; the row deal goes last, since it is irreversible and
 * adds cards to a board the search is trying to empty.
 */
fun generateMoves(board: FastBoard, out: IntArrayList) {
    out.clear()
    var banking = 0
    var exposing = 0

    // Every destination's top rank, four bits per column, [EMPTY_TOP] for an empty one. A run can
    // only land on a card one rank above its own, so this is the whole of what the destination
    // test needs — and packing it into one Long means computing it once per node instead of once
    // per (source card, destination) pair, of which a board with long liftable runs has well over a
    // thousand. Ranks are 0..12, so 15 cannot collide with a real one, and the rank+1 a King asks
    // for (13) matches nothing, which is correct: nothing stacks on a King.
    var tops = 0L
    for (to in 0 until TABLEAU_COLUMNS) {
        val lenTo = board.len[to]
        val top = if (lenTo == 0) EMPTY_TOP else board.rankOf(board.cards[to][lenTo - 1]).toLong()
        tops = tops or (top shl (to * 4))
    }

    for (from in 0 until TABLEAU_COLUMNS) {
        val n = board.len[from]
        if (n == 0) continue
        val start = board.sequenceStart(from)
        val faceDownFrom = board.faceDown[from]
        val column = board.cards[from]
        for (fromIndex in start until n) {
            val runLength = n - fromIndex
            // Both loop-invariant across destinations. `sequenceStart` never returns below
            // faceDown[from] and fromIndex stays below len[from], so canPlace's own two bounds
            // checks cannot fire from here and are not repeated.
            val wanted = board.rankOf(column[fromIndex]) + 1
            val exposes = fromIndex == faceDownFrom && fromIndex > 0
            for (to in 0 until TABLEAU_COLUMNS) {
                if (to == from) continue
                val top = ((tops ushr (to * 4)) and 0xFL).toInt()
                if (top == EMPTY_TOP.toInt()) {
                    // Moving an *entire* column into an empty one changes nothing but which column
                    // it sits in: the same board, one wasted move, and an easy way for a search to
                    // loop. The test is fromIndex == 0, not fromIndex == faceDown: moving just the
                    // face-up run off a column that still has cards under it exposes one, which is
                    // one of the most valuable moves in the game, and an earlier version of this
                    // line discarded every one of them.
                    if (fromIndex == 0) continue
                } else if (top != wanted) {
                    continue
                }

                val move = FastMove.transfer(from, fromIndex, to).packed
                when {
                    banksSequence(board, from, fromIndex, to, runLength) -> {
                        out.insert(banking, move); banking++; exposing++
                    }
                    exposes -> { out.insert(exposing, move); exposing++ }
                    else -> out.add(move)
                }
            }
        }
    }
    if (board.canDealRow()) out.add(FastMove.DEAL_ROW.packed)
}

/** Stands for "no card here" in [generateMoves]'s packed top-rank word; real ranks are 0..12. */
private const val EMPTY_TOP = 15L

/**
 * Whether landing this run on [to] completes a King-to-Ace sequence there.
 *
 * Only an ordering hint — [applyFast] banks by inspecting the board afterwards, so being wrong
 * here costs move order, never correctness.
 */
private fun banksSequence(board: FastBoard, from: Int, fromIndex: Int, to: Int, runLength: Int): Boolean {
    // The run is suited and descending already; it can only close a sequence if it ends on an Ace.
    if (board.rankOf(board.cards[from][board.len[from] - 1]) != 0) return false
    if (board.len[to] + runLength < FastBoard.RANKS) return false
    val needed = FastBoard.RANKS - runLength
    if (needed == 0) return board.rankOf(board.cards[from][fromIndex]) == FastBoard.RANKS - 1
    val startIndex = board.len[to] - needed
    if (startIndex < board.faceDown[to]) return false
    val suit = board.suitOf(board.cards[from][fromIndex])
    for (i in 0 until needed) {
        val card = board.cards[to][startIndex + i]
        if (board.suitOf(card) != suit) return false
        if (board.rankOf(card) != FastBoard.RANKS - 1 - i) return false
    }
    return true
}

/** Applies [move], recording what it did in [undo]. */
fun applyFast(board: FastBoard, move: FastMove, undo: UndoRecord) {
    undo.move = move
    undo.flippedSource = false
    undo.bankedCount = 0
    undo.runLength = 0

    if (move.isDeal) {
        for (c in 0 until TABLEAU_COLUMNS) {
            board.cards[c][board.len[c]] = board.stock[board.stockPos + c]
            board.len[c]++
        }
        board.stockPos += TABLEAU_COLUMNS
        board.markAllColumnsDirty()
    } else {
        val from = move.from
        val to = move.to
        val fromIndex = move.fromIndex
        val runLength = board.len[from] - fromIndex
        undo.runLength = runLength
        val dest = board.cards[to]
        val destLen = board.len[to]
        for (i in 0 until runLength) dest[destLen + i] = board.cards[from][fromIndex + i]
        board.len[to] = destLen + runLength
        board.len[from] = fromIndex
        if (board.len[from] > 0 && board.faceDown[from] == board.len[from]) {
            board.faceDown[from]--
            undo.flippedSource = true
        }
        board.markColumnDirty(from)
        board.markColumnDirty(to)
    }
    bankCompleted(board, undo)
}

/** Removes every completed sequence now at the top of a column, recording each for undo. */
private fun bankCompleted(board: FastBoard, undo: UndoRecord) {
    for (c in 0 until TABLEAU_COLUMNS) {
        val n = board.len[c]
        if (n < FastBoard.RANKS) continue
        val start = n - FastBoard.RANKS
        if (start < board.faceDown[c]) continue
        val col = board.cards[c]
        if (board.rankOf(col[start]) != FastBoard.RANKS - 1) continue
        val suit = board.suitOf(col[start])
        var complete = true
        for (i in 1 until FastBoard.RANKS) {
            val card = col[start + i]
            if (board.suitOf(card) != suit || board.rankOf(card) != FastBoard.RANKS - 1 - i) {
                complete = false
                break
            }
        }
        if (!complete) continue

        val slot = undo.bankedCount
        undo.bankedColumns[slot] = c
        for (i in 0 until FastBoard.RANKS) undo.bankedCards[slot][i] = col[start + i]
        board.len[c] = start
        undo.bankedFlipped[slot] = false
        if (board.len[c] > 0 && board.faceDown[c] == board.len[c]) {
            board.faceDown[c]--
            undo.bankedFlipped[slot] = true
        }
        undo.bankedCount++
        board.banked++
        board.markColumnDirty(c)
    }
}

/** Converts a solver-internal [FastMove] to the real reducer's move type, for certification and replay. */
internal fun toRulesMove(move: FastMove): org.finiteplay.spider.rules.Move =
    if (move.isDeal) {
        org.finiteplay.spider.rules.Move.DealRow
    } else {
        org.finiteplay.spider.rules.Move.TableauToTableau(move.from, move.fromIndex, move.to)
    }

/** Exactly reverses [applyFast]. */
fun undoFast(board: FastBoard, undo: UndoRecord) {
    // Banking is undone first because it happened last, and it may have flipped a card the move
    // itself then needs to see back the way it was.
    for (slot in undo.bankedCount - 1 downTo 0) {
        val c = undo.bankedColumns[slot]
        if (undo.bankedFlipped[slot]) board.faceDown[c]++
        val col = board.cards[c]
        val base = board.len[c]
        for (i in 0 until FastBoard.RANKS) col[base + i] = undo.bankedCards[slot][i]
        board.len[c] = base + FastBoard.RANKS
        board.banked--
        board.markColumnDirty(c)
    }

    val move = undo.move
    if (move.isDeal) {
        board.stockPos -= TABLEAU_COLUMNS
        for (c in 0 until TABLEAU_COLUMNS) board.len[c]--
        board.markAllColumnsDirty()
    } else {
        val from = move.from
        val to = move.to
        val fromIndex = move.fromIndex
        val runLength = undo.runLength
        if (undo.flippedSource) board.faceDown[from]++
        val src = board.cards[from]
        val destTop = board.len[to] - runLength
        for (i in 0 until runLength) src[fromIndex + i] = board.cards[to][destTop + i]
        board.len[from] = fromIndex + runLength
        board.len[to] = destTop
        board.markColumnDirty(from)
        board.markColumnDirty(to)
    }
}
