package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.TABLEAU_COLUMNS

/**
 * A Spider board built for search rather than for a screen.
 *
 * The rules engine's [SpiderState] is immutable and made of lists, which is right for a game one
 * move at a time and wrong for a search making millions. This is the same board as a flat byte
 * array per column, mutated in place with an explicit undo — no allocation per node, which is what
 * decides whether a million deals is hours or days.
 *
 * It is a *representation*, not a second rules engine: every move it makes is one the reducer
 * would make, and `FastBoardAgreementTest` plays random games through both and asserts the boards
 * stay identical. If they ever diverge, this is the one that is wrong.
 *
 * Cards are one byte, `suit * 13 + rank`, rank 0 = Ace and 12 = King. Two decks mean duplicates;
 * nothing here needs card identity, only rank and suit.
 */
class FastBoard(
    /** Card codes per column, bottom (index 0) to top. Only the first [len] entries are live. */
    val cards: Array<ByteArray> = Array(TABLEAU_COLUMNS) { ByteArray(MAX_COLUMN) },
    val len: IntArray = IntArray(TABLEAU_COLUMNS),
    /** How many of a column's lowest cards are still face down. */
    val faceDown: IntArray = IntArray(TABLEAU_COLUMNS),
    var stock: ByteArray = ByteArray(0),
    var stockPos: Int = 0,
    var banked: Int = 0,
) {
    val isWon: Boolean get() = banked == SEQUENCES_TO_WIN

    /**
     * Cached rank-only signature per column, and which of them a mutation has invalidated.
     *
     * Both transposition keys (`canonicalHashOf`, `streamlinedHashOf`) start from the same
     * per-column, suit-independent signature, and a profile put the two of them at ~59% of a real
     * search's time — most of it reading all 104 cards, at every node, to rebuild ten signatures of
     * which a move can only have changed one or two. A column's signature depends on nothing
     * outside that column (its cards and its [faceDown] count), so the ones a move did not touch
     * are still correct and are simply reused.
     *
     * Invalidation is deliberately conservative: every mutation marks the columns it could possibly
     * have affected, and anything that rewrites the board wholesale marks all ten
     * ([markAllColumnsDirty]). A stale signature here would merge two genuinely different boards in
     * the transposition cache, which can lose a win *and* falsify an `EXHAUSTED` — so this trades
     * some redundant recomputation for never being wrong, and `IncrementalRankSignatureTest` plays
     * random games asserting every cached value still equals a freshly computed one after every
     * single move.
     */
    private val rankSig = LongArray(TABLEAU_COLUMNS)
    private var dirtyColumns = ALL_COLUMNS_DIRTY

    /** Marks [column]'s cached signature stale. Cheap enough to call without checking first. */
    fun markColumnDirty(column: Int) {
        dirtyColumns = dirtyColumns or (1 shl column)
        dirtyCardColumns = dirtyCardColumns or (1 shl column)
    }

    /** Marks every cached signature stale — for a bulk rewrite of the board's contents. */
    fun markAllColumnsDirty() {
        dirtyColumns = ALL_COLUMNS_DIRTY
        dirtyCardColumns = ALL_COLUMNS_DIRTY
    }

    /** Cached [cardSignatureOf] per column, invalidated by the same marks as [rankSig]. */
    private val cardSig = LongArray(TABLEAU_COLUMNS)
    private var dirtyCardColumns = ALL_COLUMNS_DIRTY

    /**
     * [column]'s exact signature — every card's rank *and* suit, and its face-down count. Unlike
     * [rankSignatureOf] it is not blind to suits, so it suits a key that only has to tell apart
     * positions of one deal rather than collapse suit relabellings.
     */
    fun cardSignatureOf(column: Int): Long {
        val bit = 1 shl column
        if (dirtyCardColumns and bit != 0) {
            var h = SIGNATURE_SEED + faceDown[column] * SIGNATURE_PRIME
            val col = cards[column]
            for (i in 0 until len[column]) {
                h = h * SIGNATURE_PRIME xor (col[i].toLong() + 1L)
            }
            cardSig[column] = h
            dirtyCardColumns = dirtyCardColumns and bit.inv()
        }
        return cardSig[column]
    }

    /**
     * [column]'s rank-only signature: suit-independent, so a suit relabelling cannot move it, and
     * covering exactly this column's face-up/face-down shape and the ranks in it.
     */
    fun rankSignatureOf(column: Int): Long {
        val bit = 1 shl column
        if (dirtyColumns and bit != 0) {
            var h = SIGNATURE_SEED + faceDown[column] * SIGNATURE_PRIME
            val col = cards[column]
            for (i in 0 until len[column]) {
                h = h * SIGNATURE_PRIME xor (rankOf(col[i]).toLong() + 1L)
            }
            rankSig[column] = h
            dirtyColumns = dirtyColumns and bit.inv()
        }
        return rankSig[column]
    }

    fun rankOf(card: Byte): Int = card.toInt() % RANKS
    fun suitOf(card: Byte): Int = card.toInt() / RANKS

    fun topOf(column: Int): Byte = cards[column][len[column] - 1]
    fun isEmpty(column: Int): Boolean = len[column] == 0

    /**
     * Lowest index in [column] from which the run to the top is liftable: face up, descending by
     * one, all one suit. Returns [len] when the column is empty (nothing to lift).
     */
    fun sequenceStart(column: Int): Int {
        val n = len[column]
        if (n == 0) return 0
        val down = faceDown[column]
        var i = n - 1
        val col = cards[column]
        while (i > down) {
            val above = col[i - 1]
            val below = col[i]
            if (suitOf(above) != suitOf(below) || rankOf(above) != rankOf(below) + 1) break
            i--
        }
        return i
    }

    /** Whether the run starting at [fromIndex] of [from] may legally land on [to]. */
    fun canPlace(from: Int, fromIndex: Int, to: Int): Boolean {
        if (from == to) return false
        if (fromIndex < faceDown[from] || fromIndex >= len[from]) return false
        if (len[to] == 0) return true
        return rankOf(topOf(to)) == rankOf(cards[from][fromIndex]) + 1
    }

    /** True when a row deal is available: stock left, and no column empty. */
    fun canDealRow(): Boolean {
        if (stockPos >= stock.size) return false
        for (c in 0 until TABLEAU_COLUMNS) if (len[c] == 0) return false
        return true
    }

    fun copy(): FastBoard {
        val c = Array(TABLEAU_COLUMNS) { cards[it].copyOf() }
        // The copy starts with every signature dirty rather than inheriting this board's cache —
        // correct by construction, and a copy is made once per playout, not once per node.
        return FastBoard(c, len.copyOf(), faceDown.copyOf(), stock, stockPos, banked)
    }

    fun copyFrom(other: FastBoard) {
        for (column in 0 until TABLEAU_COLUMNS) {
            System.arraycopy(other.cards[column], 0, cards[column], 0, other.len[column])
            len[column] = other.len[column]
            faceDown[column] = other.faceDown[column]
        }
        stock = other.stock
        stockPos = other.stockPos
        banked = other.banked
        markAllColumnsDirty()
    }

    companion object {
        const val RANKS = 13
        const val SEQUENCES_TO_WIN = 8

        /** Every column's cache bit set — ten columns, so the low ten bits. */
        private const val ALL_COLUMNS_DIRTY = (1 shl TABLEAU_COLUMNS) - 1
        private const val SIGNATURE_SEED = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        private const val SIGNATURE_PRIME = 0x100000001B3L

        /**
         * Ceiling on a single column's height. Every card could in principle end up in one column,
         * so the only safe bound is the whole deck.
         */
        const val MAX_COLUMN = 104

        fun from(state: SpiderState): FastBoard {
            val board = FastBoard()
            for (c in 0 until TABLEAU_COLUMNS) {
                val column = state.tableau[c]
                board.len[c] = column.size
                board.faceDown[c] = column.count { !it.faceUp }
                for (i in column.indices) {
                    val card = column[i].card
                    board.cards[c][i] = (card.suit.ordinal * RANKS + card.rank.ordinal).toByte()
                }
            }
            board.stock = ByteArray(state.stock.size) { i ->
                val card = state.stock[i]
                (card.suit.ordinal * RANKS + card.rank.ordinal).toByte()
            }
            board.stockPos = 0
            board.banked = state.sequencesBanked
            return board
        }
    }
}
