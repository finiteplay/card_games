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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedSearchTest {
    @Test
    fun `guided search returns a forward legal certificate`() {
        val columns = listOf(
            listOf(Card(Suit.SPADES, Rank.KING), Card(Suit.SPADES, Rank.QUEEN), Card(Suit.SPADES, Rank.JACK)),
            listOf(Card(Suit.SPADES, Rank.TEN), Card(Suit.SPADES, Rank.NINE), Card(Suit.SPADES, Rank.EIGHT)),
            listOf(
                Card(Suit.SPADES, Rank.SEVEN), Card(Suit.SPADES, Rank.SIX), Card(Suit.SPADES, Rank.FIVE),
                Card(Suit.SPADES, Rank.FOUR), Card(Suit.SPADES, Rank.THREE), Card(Suit.SPADES, Rank.TWO),
                Card(Suit.SPADES, Rank.ACE),
            ),
        )
        val start = SpiderState(
            tableau = (0 until TABLEAU_COLUMNS).map { column ->
                columns.getOrElse(column) { emptyList() }.map { TableauCard(it, faceUp = true) }
            },
            stock = emptyList(),
            banked = Suit.entries.associateWith { if (it == Suit.SPADES) 7 else 0 },
            suitCount = SuitCount.ONE,
            seed = 0,
            versions = GameVersions(0, 1, 1),
            moveCount = 0,
            status = GameStatus.IN_PROGRESS,
        )
        val solver = SpiderSolver(
            SolverLimits(
                maxNodes = 10_000,
                maxMillis = 2_000,
                playouts = 0,
                guidedAttempts = 1,
                guidedDepth = 5,
                beamWidth = 0,
            ),
        )

        val line = solver.certifyGuided(start)
        assertNotNull("guided search did not solve the endgame", line)
        var state = start
        for (move in line!!) {
            assertTrue("illegal guided-search move: $move", isLegal(state, move))
            state = applyMove(state, move)
        }
        assertTrue("guided-search certificate did not replay to a real win", state.isWon)
    }
}
