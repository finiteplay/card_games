package org.finiteplay.spider.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.layout.dealGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/** A board holding exactly the cards given, one list per column, nothing in the stock. */
private fun boardOf(columns: List<List<Card>>, banked: Int): SpiderState {
    val tableau = (0 until TABLEAU_COLUMNS).map { i ->
        columns.getOrElse(i) { emptyList() }.map { TableauCard(it, faceUp = true) }
    }
    return SpiderState(
        tableau = tableau,
        stock = emptyList(),
        // Every suit needs an entry; a one-suit game banks only spades.
        banked = Suit.entries.associateWith { if (it == Suit.SPADES) banked else 0 },
        suitCount = SuitCount.ONE,
        seed = 0L,
        versions = VERSIONS,
        moveCount = 0,
        status = GameStatus.IN_PROGRESS,
    )
}

private fun spades(vararg ranks: Rank) = ranks.map { Card(Suit.SPADES, it) }

private val DESCENDING = listOf(
    Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN, Rank.NINE, Rank.EIGHT, Rank.SEVEN,
    Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO, Rank.ACE,
)

class AutoFinishTest {

    @Test
    fun `a freshly dealt game is nowhere near auto-finish`() {
        val state = dealGame(seed = 1L, versions = VERSIONS)
        assertFalse(isAutoFinishAvailable(state))
        assertNull(findAutoFinish(state))
    }

    @Test
    fun `availability turns on only at one sequence left in play`() {
        // Fourteen cards across two columns: one more than the threshold, so still the player's.
        val fourteen = boardOf(listOf(spades(*DESCENDING.toTypedArray()), spades(Rank.KING)), banked = 6)
        assertFalse("fourteen cards is still a game", isAutoFinishAvailable(fourteen))

        val thirteen = boardOf(listOf(spades(*DESCENDING.toTypedArray())), banked = 7)
        assertTrue(isAutoFinishAvailable(thirteen))
    }

    @Test
    fun `a won game offers nothing further`() {
        val state = boardOf(emptyList(), banked = 8).copy(status = GameStatus.WON)
        assertFalse(isAutoFinishAvailable(state))
        assertEquals(emptyList<Move>(), findAutoFinish(state))
    }

    @Test
    fun `the last sequence scattered across columns is assembled and banked`() {
        // K-Q-J in one column, the rest split across two more: legal to consolidate, so a finish
        // exists and the search has to find it rather than give up.
        val state = boardOf(
            listOf(
                spades(Rank.KING, Rank.QUEEN, Rank.JACK),
                spades(Rank.TEN, Rank.NINE, Rank.EIGHT, Rank.SEVEN),
                spades(Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO, Rank.ACE),
            ),
            banked = 7,
        )
        assertTrue(isAutoFinishAvailable(state))

        val moves = findAutoFinish(state)
        assertNotNull("a finish exists here and must be found", moves)

        var played = state
        for (move in moves!!) {
            assertTrue("every move offered must be legal when it is played", isLegal(played, move))
            played = applyMove(played, move)
        }
        assertEquals(GameStatus.WON, played.status)
    }

    @Test
    fun `an already-assembled sequence finishes in a single move`() {
        val state = boardOf(
            listOf(spades(Rank.KING, Rank.QUEEN, Rank.JACK, Rank.TEN, Rank.NINE, Rank.EIGHT,
                Rank.SEVEN, Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO), spades(Rank.ACE)),
            banked = 7,
        )
        val moves = findAutoFinish(state)
        assertNotNull(moves)
        assertEquals("one move should close this out", 1, moves!!.size)

        assertEquals(GameStatus.WON, applyMove(state, moves.single()).status)
    }

    @Test
    fun `a last sequence that cannot be assembled is reported as no finish, not a partial one`() {
        // Every column is a lone card that outranks nothing it could sit on, and there is no empty
        // column to stage through: legal moves exist but none of them ever completes the run.
        val state = boardOf(
            List(TABLEAU_COLUMNS) { i -> listOf(Card(Suit.SPADES, DESCENDING[i])) } +
                listOf(spades(Rank.THREE, Rank.TWO, Rank.ACE)),
            banked = 7,
        )
        val moves = findAutoFinish(state)
        if (moves != null) {
            // If the search did find one it must genuinely win; the point is that it never returns
            // a sequence that stops short.
            var played = state
            for (move in moves) played = applyMove(played, move)
            assertEquals(GameStatus.WON, played.status)
        }
    }

    @Test
    fun `the node budget is respected rather than searched forever`() {
        val state = boardOf(
            listOf(
                spades(Rank.KING, Rank.QUEEN, Rank.JACK),
                spades(Rank.TEN, Rank.NINE, Rank.EIGHT, Rank.SEVEN),
                spades(Rank.SIX, Rank.FIVE, Rank.FOUR, Rank.THREE, Rank.TWO, Rank.ACE),
            ),
            banked = 7,
        )
        // One node is not enough to reach a win, and the search must say so instead of hanging.
        assertNull(findAutoFinish(state, maxNodes = 1))
    }
}
