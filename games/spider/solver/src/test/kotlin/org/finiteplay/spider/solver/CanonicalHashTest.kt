package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.dealGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [canonicalHashOf] merges positions that differ only by relabelling suits. That is worth a great
 * deal — up to 4! = 24 four-suit boards collapsing onto one transposition entry — and it is exactly
 * the kind of reduction that loses solutions silently when it is wrong, since a wrong merge makes
 * the search skip a position it has never actually looked at. Hence a property test over every
 * relabelling rather than a couple of examples.
 */
class CanonicalHashTest {

    /** Every bijection of the four suit labels, as `newLabel[oldSuit]`. */
    private fun allSuitPermutations(): List<IntArray> {
        val out = mutableListOf<IntArray>()
        fun recurse(prefix: List<Int>, remaining: List<Int>) {
            if (remaining.isEmpty()) {
                out.add(prefix.toIntArray())
                return
            }
            for (candidate in remaining) recurse(prefix + candidate, remaining - candidate)
        }
        recurse(emptyList(), (0 until HashScratch.SUITS).toList())
        return out
    }

    /**
     * [board] with every card's suit relabelled through [permutation] — tableau *and* the undealt
     * stock, since a board's future depends on the suits still to be dealt and a relabelling that
     * skipped them would not be the same position at all.
     */
    private fun relabel(board: FastBoard, permutation: IntArray): FastBoard {
        fun remap(card: Byte): Byte {
            val suit = card.toInt() / FastBoard.RANKS
            val rank = card.toInt() % FastBoard.RANKS
            return (permutation[suit] * FastBoard.RANKS + rank).toByte()
        }
        val relabelled = FastBoard(
            cards = Array(TABLEAU_COLUMNS) { c -> ByteArray(FastBoard.MAX_COLUMN) { i -> if (i < board.len[c]) remap(board.cards[c][i]) else 0 } },
            len = board.len.copyOf(),
            faceDown = board.faceDown.copyOf(),
            stock = ByteArray(board.stock.size) { remap(board.stock[it]) },
            stockPos = board.stockPos,
            banked = board.banked,
        )
        return relabelled
    }

    @Test
    fun `relabelling the suits does not change the key, on a fresh four-suit deal`() {
        val board = FastBoard.from(dealGame(seed = 7L, versions = VERSIONS, suitCount = SuitCount.FOUR))
        val expected = canonicalHashOf(board, HashScratch())

        for (permutation in allSuitPermutations()) {
            val hash = canonicalHashOf(relabel(board, permutation), HashScratch())
            assertEquals("relabelling ${permutation.toList()} changed the key", expected, hash)
        }
    }

    /**
     * The same property must hold part-way through a game, not just on a fresh deal: that is where
     * the search actually spends its time, and it is where face-down counts, a partly-consumed
     * stock and banked sequences are all in play at once.
     */
    @Test
    fun `relabelling the suits does not change the key mid-game`() {
        val board = FastBoard.from(dealGame(seed = 11L, versions = VERSIONS, suitCount = SuitCount.FOUR))
        val undo = UndoRecord()
        val moves = IntArrayList(64)
        // Walk a few real moves in, including at least one row deal, so the position under test has
        // an advanced stockPos and a non-trivial shape.
        repeat(12) {
            generateMoves(board, moves)
            if (moves.size == 0) return@repeat
            applyFast(board, FastMove(moves[moves.size - 1]), undo)
        }
        val expected = canonicalHashOf(board, HashScratch())

        for (permutation in allSuitPermutations()) {
            val hash = canonicalHashOf(relabel(board, permutation), HashScratch())
            assertEquals("relabelling ${permutation.toList()} changed the key mid-game", expected, hash)
        }
    }

    /**
     * The other half of the property, and the one a too-aggressive canonical form breaks: boards
     * that are *not* relabellings of each other must keep separate keys. Different deals differ by
     * far more than a suit permutation, so agreement here would mean the key had stopped
     * distinguishing positions at all.
     */
    @Test
    fun `different deals keep different keys`() {
        val scratch = HashScratch()
        val keys = (1L..200L).map {
            canonicalHashOf(FastBoard.from(dealGame(seed = it, versions = VERSIONS, suitCount = SuitCount.FOUR)), scratch)
        }
        assertEquals("distinct four-suit deals collided on the canonical key", keys.size, keys.toSet().size)
    }

    /**
     * A relabelling is the *only* thing the key is allowed to ignore about the suits. Swapping two
     * cards' suits in one column without applying that swap consistently everywhere is not a
     * relabelling, and must change the key — this is the case a tableau-only canonicalisation
     * (one that ignored the undealt stock) would get wrong.
     */
    @Test
    fun `a suit change that is not a consistent relabelling does change the key`() {
        val board = FastBoard.from(dealGame(seed = 13L, versions = VERSIONS, suitCount = SuitCount.FOUR))
        val scratch = HashScratch()
        val before = canonicalHashOf(board, scratch)

        // Relabel the tableau only, leaving the undealt stock alone: a real position, and a
        // different one, since the rows still to come no longer match the board they land on.
        val swap = intArrayOf(1, 0, 2, 3)
        val tableauOnly = relabel(board, swap)
        tableauOnly.stock = board.stock

        assertTrue("fixture must still have stock to deal", board.stockPos < board.stock.size)
        assertNotEquals(
            "a tableau-only relabelling was treated as the same position",
            before,
            canonicalHashOf(tableauOnly, scratch),
        )
    }
}
