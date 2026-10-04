package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.dealGame
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [FastBoard.rankSignatureOf] (and its suit-aware twin [FastBoard.cardSignatureOf]) caches each column's signature and recomputes only what a move marked
 * dirty. That is a real speed-up and a real hazard: a signature left stale would make two genuinely
 * different boards share a transposition key, which can hide a win *and* turn "searched everywhere"
 * into a false [SolveResult.EXHAUSTED] — a wrong answer, not a slow one.
 *
 * So these tests do not check the cache's bookkeeping; they check the only thing that matters, that
 * what it hands back is always what a from-scratch computation would give, after every single move
 * of real games — including the undo path, which is where a missed invalidation is most likely to
 * hide, since it is the path the search spends most of its time on.
 */
class IncrementalRankSignatureTest {

    /** The signature, computed the long way round: straight off this column's cards, no cache. */
    private fun freshSignature(board: FastBoard, column: Int): Long {
        var h = -0x61c8864680b583ebL + board.faceDown[column] * 0x100000001B3L
        val col = board.cards[column]
        for (i in 0 until board.len[column]) {
            h = h * 0x100000001B3L xor (board.rankOf(col[i]).toLong() + 1L)
        }
        return h
    }

    /** [FastBoard.cardSignatureOf], the long way round: suit as well as rank, no cache. */
    private fun freshCardSignature(board: FastBoard, column: Int): Long {
        var h = -0x61c8864680b583ebL + board.faceDown[column] * 0x100000001B3L
        val col = board.cards[column]
        for (i in 0 until board.len[column]) {
            h = h * 0x100000001B3L xor (col[i].toLong() + 1L)
        }
        return h
    }

    private fun assertEveryColumnMatches(board: FastBoard, where: String) {
        for (c in 0 until TABLEAU_COLUMNS) {
            assertEquals(
                "column $c's cached signature went stale $where",
                freshSignature(board, c),
                board.rankSignatureOf(c),
            )
            assertEquals(
                "column $c's cached card signature went stale $where",
                freshCardSignature(board, c),
                board.cardSignatureOf(c),
            )
        }
    }

    @Test
    fun `cached signatures match freshly computed ones through random games, apply and undo`() {
        val moves = IntArrayList(64)
        for (seed in 1L..25L) {
            val suitCount = SuitCount.entries[(seed % 3).toInt()]
            val board = FastBoard.from(dealGame(seed = seed, versions = VERSIONS, suitCount = suitCount))
            val random = Random(seed)
            assertEveryColumnMatches(board, "on the freshly dealt board (seed $seed)")

            repeat(400) { step ->
                generateMoves(board, moves)
                if (moves.size == 0) return@repeat
                val move = FastMove(moves[random.nextInt(moves.size)])
                val undo = UndoRecord()

                applyFast(board, move, undo)
                assertEveryColumnMatches(board, "after applying move $step of seed $seed")

                // Undo, check, then redo — so the assertion covers the board state the search
                // actually backtracks into, not only the forward one.
                undoFast(board, undo)
                assertEveryColumnMatches(board, "after undoing move $step of seed $seed")
                applyFast(board, move, undo)
            }
        }
    }

    @Test
    fun `restoring a snapshot invalidates every column`() {
        val board = FastBoard.from(dealGame(seed = 3L, versions = VERSIONS, suitCount = SuitCount.TWO))
        val pool = SnapshotPool()
        val saved = pool.save(board)
        val moves = IntArrayList(64)
        val undo = UndoRecord()
        repeat(30) {
            generateMoves(board, moves)
            if (moves.size > 0) applyFast(board, FastMove(moves[0]), undo)
        }
        for (c in 0 until TABLEAU_COLUMNS) board.cardSignatureOf(c)

        pool.restore(saved, board)

        assertEveryColumnMatches(board, "after restoring a snapshot")
    }

    @Test
    fun `a dealt row invalidates every column, not just the ones that happened to change`() {
        val board = FastBoard.from(dealGame(seed = 7L, versions = VERSIONS, suitCount = SuitCount.FOUR))
        // Prime every column's cache first, so a missed invalidation would actually be observable
        // rather than hidden behind a still-dirty bit.
        for (c in 0 until TABLEAU_COLUMNS) {
            board.rankSignatureOf(c)
            board.cardSignatureOf(c)
        }

        val undo = UndoRecord()
        applyFast(board, FastMove.DEAL_ROW, undo)

        assertEveryColumnMatches(board, "after a row deal")
    }
}
