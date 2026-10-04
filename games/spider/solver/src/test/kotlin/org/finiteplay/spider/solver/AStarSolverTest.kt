package org.finiteplay.spider.solver

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [AStarSolver] is the weighted best-first stage `SpiderSolver.solve` falls back to once plain DFS
 * runs out of node budget without exhausting the board (`docs/games/spider/DESIGN.md` "Hint") — its
 * whole reason to exist is resolving a board close to a win far faster than a blind search, so that
 * is what these tests pin, alongside the same "does the line actually replay" property
 * [CertifyTest] holds the plain search to.
 */
class AStarSolverTest {
    private fun replayWinsForReal(state: SpiderState, moves: List<Move>): Boolean {
        var current = state
        for (move in moves) {
            if (!isLegal(current, move)) return false
            current = applyMove(current, move)
        }
        return current.isWon
    }

    private fun boardOf(columns: List<List<Card>>, banked: Map<Suit, Int>, suitCount: SuitCount): SpiderState {
        val tableau = (0 until TABLEAU_COLUMNS).map { i ->
            columns.getOrElse(i) { emptyList() }.map { TableauCard(it, faceUp = true) }
        }
        return SpiderState(
            tableau = tableau,
            stock = emptyList(),
            banked = Suit.entries.associateWith { banked[it] ?: 0 },
            suitCount = suitCount,
            seed = 0L,
            versions = VERSIONS,
            moveCount = 0,
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun solvesAOneMoveWinAlmostInstantly() {
        val ranks = Rank.entries.reversed()
        val state = boardOf(
            columns = listOf(
                ranks.dropLast(6).map { Card(Suit.SPADES, it) },
                ranks.takeLast(6).map { Card(Suit.SPADES, it) },
            ),
            banked = mapOf(Suit.SPADES to FastBoard.SEQUENCES_TO_WIN - 1),
            suitCount = SuitCount.TWO,
        )
        val limits = SolverLimits(maxNodes = 1_000, maxMillis = 1_000, playouts = 0)
        val record = IntArrayList(8)

        val outcome = AStarSolver().solve(FastBoard.from(state), limits, System.nanoTime() + 1_000_000_000L, record)

        assertEquals(SolveResult.SOLVED_BY_SEARCH, outcome.result)
        assertTrue("expanded almost nothing for a one-move win", outcome.nodes < 20)
        val moves = (0 until record.size).map { toFastMove(record[it]) }
        assertTrue("the found line did not replay to a win on the real reducer", replayWinsForReal(state, moves))
    }

    @Test
    fun reportsExhaustedRatherThanCrashingOnADeadEnd() {
        // Every column a lone off-suit pair nothing can build on and nothing can empty: no legal
        // move exists, and there is no stock to fall back on.
        val state = boardOf(
            columns = (0 until TABLEAU_COLUMNS).map {
                listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.ACE))
            },
            banked = emptyMap(),
            suitCount = SuitCount.TWO,
        )
        val limits = SolverLimits(maxNodes = 1_000, maxMillis = 1_000, playouts = 0)

        val outcome = AStarSolver().solve(FastBoard.from(state), limits, System.nanoTime() + 1_000_000_000L, null)

        assertEquals(SolveResult.EXHAUSTED, outcome.result)
    }

    @Test
    fun stopsWithinItsNodeBudgetOnAFreshTwoSuitDeal() {
        // The realistic negative case: a fresh two-suit deal is not expected to solve at any
        // phone-safe budget (`docs/games/spider/DESIGN.md` "Hint"), but the search must still stop
        // cleanly at its own limits rather than run away.
        val state = org.finiteplay.spider.layout.dealGame(seed = 1L, versions = VERSIONS, suitCount = SuitCount.TWO)
        val limits = SolverLimits(maxNodes = 20_000, maxMillis = 5_000, playouts = 0)

        val outcome = AStarSolver().solve(FastBoard.from(state), limits, System.nanoTime() + limits.maxMillis * 1_000_000L, null)

        assertTrue(
            "expected LIMIT or EXHAUSTED, got ${outcome.result}",
            outcome.result == SolveResult.LIMIT || outcome.result == SolveResult.EXHAUSTED,
        )
    }

    private fun toFastMove(packed: Int): Move {
        val move = FastMove(packed)
        return if (move.isDeal) Move.DealRow else Move.TableauToTableau(move.from, move.fromIndex, move.to)
    }
}
