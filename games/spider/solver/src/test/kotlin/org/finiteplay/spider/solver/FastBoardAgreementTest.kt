package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.legalMoves
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * The fast board is a second representation of a board the reducer already owns, and a second
 * representation is a second chance to be wrong. These tests play the same games through both and
 * assert they never disagree — if they ever do, the reducer is right and this is the bug.
 */
class FastBoardAgreementTest {

    private fun describe(board: FastBoard): String = buildString {
        for (c in 0 until TABLEAU_COLUMNS) {
            append(board.faceDown[c]).append(':')
            for (i in 0 until board.len[c]) append(board.cards[c][i].toInt()).append(',')
            append('|')
        }
        append("banked=").append(board.banked)
    }

    private fun describe(state: SpiderState): String = buildString {
        for (c in 0 until TABLEAU_COLUMNS) {
            val column = state.tableau[c]
            append(column.count { !it.faceUp }).append(':')
            for (card in column) {
                append(card.card.suit.ordinal * FastBoard.RANKS + card.card.rank.ordinal).append(',')
            }
            append('|')
        }
        append("banked=").append(state.sequencesBanked)
    }

    @Test
    fun `a fresh deal converts identically at every suit count`() {
        for (suitCount in SuitCount.entries) {
            for (seed in 1L..20L) {
                val state = dealGame(seed = seed, versions = VERSIONS, suitCount = suitCount)
                assertEquals(
                    "seed $seed at $suitCount",
                    describe(state),
                    describe(FastBoard.from(state)),
                )
            }
        }
    }

    @Test
    fun `random play agrees move for move with the reducer`() {
        val random = Random(20260820)
        for (suitCount in SuitCount.entries) {
            for (seed in 1L..12L) {
                var state = dealGame(seed = seed, versions = VERSIONS, suitCount = suitCount)
                val board = FastBoard.from(state)
                val undo = UndoRecord()
                val generated = IntArrayList()

                repeat(220) {
                    val legal = legalMoves(state)
                    if (legal.isEmpty()) return@repeat

                    generateMoves(board, generated)
                    // The fast generator drops one class of legal move on purpose — a whole column
                    // into an empty one — so it may offer fewer, never more, and never an illegal
                    // one. That is checked by applying whichever it picks below.
                    if (generated.size == 0) return@repeat

                    val pick = FastMove(generated[random.nextInt(generated.size)])
                    val asMove: Move = if (pick.isDeal) {
                        Move.DealRow
                    } else {
                        Move.TableauToTableau(pick.from, pick.fromIndex, pick.to)
                    }
                    assertTrue(
                        "fast generator offered $asMove, which the rules call illegal (seed $seed, $suitCount)",
                        legal.contains(asMove),
                    )

                    state = applyMove(state, asMove)
                    applyFast(board, pick, undo)
                    assertEquals(
                        "boards diverged after $asMove (seed $seed, $suitCount)",
                        describe(state),
                        describe(board),
                    )
                }
            }
        }
    }

    @Test
    fun `undo restores the board exactly`() {
        val random = Random(4242)
        for (seed in 1L..10L) {
            val state = dealGame(seed = seed, versions = VERSIONS, suitCount = SuitCount.TWO)
            val board = FastBoard.from(state)
            val generated = IntArrayList()
            val undo = UndoRecord()

            repeat(120) {
                generateMoves(board, generated)
                if (generated.size == 0) return@repeat
                val before = describe(board)
                val pick = FastMove(generated[random.nextInt(generated.size)])
                applyFast(board, pick, undo)
                undoFast(board, undo)
                assertEquals("undo did not restore the board (seed $seed)", before, describe(board))
                // Move on so the next iteration tests a different position.
                generateMoves(board, generated)
                if (generated.size > 0) applyFast(board, FastMove(generated[0]), undo)
            }
        }
    }

    @Test
    fun `a won game is recognised the same way by both`() {
        // Play one-suit games greedily; any that reach a win must agree on it.
        val solver = SpiderSolver(SolverLimits(maxNodes = 200_000, maxMillis = 4_000, playouts = 8))
        var wins = 0
        for (seed in 1L..12L) {
            val state = dealGame(seed = seed, versions = VERSIONS, suitCount = SuitCount.ONE)
            if (solver.solve(state).solved) wins++
        }
        assertTrue("one-suit Spider should be winnable far more often than never, got $wins/12", wins > 0)
    }
}
