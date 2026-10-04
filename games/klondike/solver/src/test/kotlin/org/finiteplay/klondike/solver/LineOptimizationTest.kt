package org.finiteplay.klondike.solver

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withStock
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStateFixtures.withWaste
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A found line is a proof, not advice. These pin the difference: what survives into the
 * hint cache has to be a line a player can follow without being told to undo work they
 * were just told to do, or to spin the stock for nothing.
 */
class LineOptimizationTest {

    /** Every suit one card from home, each king sitting alone on its own column. */
    private fun boardOneKingFromWon(): GameState = GameStateFixtures.emptyBoard(seed = 7L)
        .withFoundation(Suit.SPADES, 12)
        .withFoundation(Suit.HEARTS, 12)
        .withFoundation(Suit.DIAMONDS, 12)
        .withFoundation(Suit.CLUBS, 12)
        .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))
        .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.KING))))
        .withTableauColumn(2, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
        .withTableauColumn(3, listOf(faceUp(Card(Suit.CLUBS, Rank.KING))))

    @Test
    fun `a run shuffled out to a column and back again is dropped, however far apart the two moves are`() {
        val start = boardOneKingFromWon()
        // The reported defect: the king leaves column 0 for the empty column 4 and comes
        // back two moves later, with nothing having touched either column in between.
        // The board is never the same twice — the foundations advanced — so the existing
        // exact-repeat strip cannot see it.
        val found = listOf(
            Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 4),
            Move.TableauToFoundation(1),
            Move.TableauToFoundation(2),
            Move.TableauToTableau(fromColumn = 4, fromIndex = 0, toColumn = 0),
            Move.TableauToFoundation(0),
            Move.TableauToFoundation(3),
        )

        val optimized = optimizeWinningLine(start, found)

        assertEquals(
            listOf(
                Move.TableauToFoundation(1),
                Move.TableauToFoundation(2),
                Move.TableauToFoundation(0),
                Move.TableauToFoundation(3),
            ),
            optimized,
        )
    }

    /** Queen of hearts on the tableau, its king already on the waste; nothing else is left. */
    private fun boardWithKingOnTheWaste(): GameState = GameStateFixtures.emptyBoard(seed = 8L)
        .withFoundation(Suit.SPADES, 13)
        .withFoundation(Suit.DIAMONDS, 13)
        .withFoundation(Suit.CLUBS, 13)
        .withFoundation(Suit.HEARTS, 11)
        .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.QUEEN))))
        .withWaste(listOf(Card(Suit.HEARTS, Rank.KING)))
        .withStock(emptyList())

    @Test
    fun `stock rotation that the line does not actually need is dropped`() {
        val start = boardWithKingOnTheWaste()
        // Recycling and re-drawing puts the king back exactly where it started, so both
        // moves are pure noise — but a tableau move sits between them, so the board is
        // never repeated and the line reads as legitimate progress.
        val found = listOf(
            Move.Recycle,
            Move.TableauToFoundation(0),
            Move.Draw,
            Move.WasteToFoundation,
        )

        val optimized = optimizeWinningLine(start, found)

        assertEquals(listOf(Move.TableauToFoundation(0), Move.WasteToFoundation), optimized)
    }

    @Test
    fun `an optimized line never spins the stock without playing off it`() {
        // The invariant the second report asks for, checked against real Expert lines
        // rather than a fixture: wherever a line rotates the stock, the rotation ends on
        // a move that takes the exposed card, not on more tableau shuffling.
        var checked = 0
        for (seed in 1L..40L) {
            val start = dealGame(seed, GameStateFixtures.TEST_VERSIONS)
            val line = expertLine(start) ?: continue
            checked++
            val optimized = optimizeWinningLine(start, line)

            var i = 0
            while (i < optimized.size) {
                if (!optimized[i].rotatesStock()) { i++; continue }
                var end = i
                while (end < optimized.size && optimized[end].rotatesStock()) end++
                val next = optimized.getOrNull(end)
                assertTrue(
                    "seed $seed: stock rotation at $i..${end - 1} is followed by $next, which plays nothing off the waste",
                    next != null && next.consumesWaste(),
                )
                i = end
            }
        }
        assertTrue("no seed in the range produced a winning line to check", checked > 0)
    }

    @Test
    fun `optimizing a line never becomes quadratic in its length`() {
        // This runs after the search returns, so SolverLimits does not cap it: without a
        // ceiling of its own it could quietly spend more than the budget the portfolio was
        // fitted to. What this guards is the shape of the cost — an earlier cut was quadratic
        // and took 1.4 seconds on a line this one optimizes in about 34 ms.
        //
        // CPU time, not wall clock. The wall-clock version passed alone and failed inside
        // `check` as soon as the build ran test forks in parallel, which measured the rest of
        // the build rather than this code. It is also not stated as a share of the search: the
        // search is fast on these seeds and optimizing legitimately costs more than it does.
        val threads = java.lang.management.ManagementFactory.getThreadMXBean()
        var worstMs = 0L
        var worstSeed = 0L
        for (seed in 1L..40L) {
            val start = dealGame(seed, GameStateFixtures.TEST_VERSIONS)
            val ordered = reorderCertificateForFollowing(start, expertLine(start) ?: continue)
            val startCpu = threads.currentThreadCpuTime
            optimizeWinningLine(start, ordered)
            val ms = (threads.currentThreadCpuTime - startCpu) / 1_000_000
            if (ms > worstMs) { worstMs = ms; worstSeed = seed }
        }
        assertTrue("slowest line was seed $worstSeed at ${worstMs}ms of CPU", worstMs < 150)
    }

    @Test
    fun `optimizing keeps the line a proof`() {
        for (seed in 1L..20L) {
            val start = dealGame(seed, GameStateFixtures.TEST_VERSIONS)
            val line = expertLine(start) ?: continue
            val optimized = optimizeWinningLine(start, line)

            // Still wins, and never got longer: optimization only ever removes or defers.
            validateCertificate(start, optimized)
            assertTrue("seed $seed grew from ${line.size} to ${optimized.size}", optimized.size <= line.size)
        }
    }

    /**
     * The line the Expert ruleset plays out, which is what a hint follows whenever the
     * ruleset can win the board — the path both reports are about. Search-free, so
     * scanning a range of seeds stays a unit test rather than a benchmark.
     */
    private fun expertLine(start: GameState): List<Move>? =
        playPureRuleset(start, StrategyTier.EXPERT, maxMoves = 500)
            .takeIf { it.outcome == PureOutcome.WON }
            ?.line
            ?.takeIf { it.isNotEmpty() }

    private fun Move.rotatesStock() = this is Move.Draw || this is Move.Recycle
    private fun Move.consumesWaste() = this is Move.WasteToTableau || this is Move.WasteToFoundation
}
