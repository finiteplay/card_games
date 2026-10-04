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
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * [SolverLimits.maxCacheEntries] switches `SpiderSolver`'s transposition caches from unbounded to
 * [GenerationalLongHashSet]'s bounded, evicting form. These tests exercise `SpiderSolver.solve`
 * itself under it — not the cache in isolation ([GenerationalLongHashSetTest] already does that —
 * to confirm the integration is wired correctly and the correctness argument in
 * `SolverLimits.maxCacheEntries`'s own doc holds in practice, not just on paper: a real solved
 * board still replays, and a real dead board is still correctly proved dead, at a cache small
 * enough that eviction is forced to happen constantly.
 */
class BoundedCacheIntegrationTest {

    private fun boardOf(columns: List<List<Card>>, banked: Map<Suit, Int> = emptyMap()): SpiderState {
        val tableau = (0 until TABLEAU_COLUMNS).map { i ->
            columns.getOrElse(i) { emptyList() }.map { TableauCard(it, faceUp = true) }
        }
        return SpiderState(
            tableau = tableau,
            stock = emptyList(),
            banked = Suit.entries.associateWith { banked[it] ?: 0 },
            suitCount = SuitCount.FOUR,
            seed = 0L,
            versions = VERSIONS,
            moveCount = 0,
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun `still finds and replays a real solution with a tiny bounded cache forcing constant eviction`() {
        val ranks = Rank.entries.reversed()
        val state = boardOf(
            columns = listOf(
                ranks.dropLast(6).map { Card(Suit.SPADES, it) },
                ranks.takeLast(6).map { Card(Suit.SPADES, it) },
            ),
            banked = mapOf(Suit.SPADES to FastBoard.SEQUENCES_TO_WIN - 1),
        )
        // entriesPerGeneration this small forces GenerationalLongHashSet to rotate generations
        // almost immediately even for a one-move win's tiny reachable set. certify() uses whatever
        // limits the solver was constructed with, so no need to drive the private solve() directly.
        val limits = SolverLimits(maxNodes = 10_000, maxMillis = 2_000, playouts = 0, beamWidth = 0, maxCacheEntries = 16)

        val line = SpiderSolver(limits).certify(state)

        assertTrue("expected a solution under a tiny bounded cache", line != null)
        var replayed = state
        for (move in line!!) {
            assertTrue("move $move illegal against the board it was supposedly found from", isLegal(replayed, move))
            replayed = applyMove(replayed, move)
        }
        assertTrue("the line found under a tiny bounded cache did not replay to a win", replayed.isWon)
    }

    @Test
    fun `still correctly proves a dead board unsolvable with a tiny bounded cache`() {
        val state = boardOf(
            columns = (0 until TABLEAU_COLUMNS).map {
                listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.ACE))
            },
        )
        val limits = SolverLimits(maxNodes = 10_000, maxMillis = 2_000, playouts = 0, beamWidth = 0, maxCacheEntries = 16)

        val outcome = SpiderSolver(limits).solve(state)

        assertEquals(SolveResult.EXHAUSTED, outcome.result)
    }
}
