package org.finiteplay.freecell.layout

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.rules.invariantViolations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `the deal is four columns of seven and four of six`() {
        val state = dealGame(seed = 1L, versions = versions)

        assertEquals(listOf(7, 7, 7, 7, 6, 6, 6, 6), state.tableau.map { it.size })
        assertEquals(emptyList<String>(), invariantViolations(state))
    }

    @Test
    fun `every card in the deal is one of the fifty-two, exactly once`() {
        val state = dealGame(seed = 2L, versions = versions)
        val cards = state.tableau.flatten()

        assertEquals(Card.DECK_SIZE, cards.size)
        assertEquals(Card.CANONICAL_DECK.toSet(), cards.toSet())
    }

    @Test
    fun `the same seed deals the same layout and different seeds do not`() {
        assertEquals(dealGame(5L, versions).tableau, dealGame(5L, versions).tableau)
        assertNotEquals(dealGame(5L, versions).tableau, dealGame(6L, versions).tableau)
    }

    @Test
    fun `free cells and foundations start empty`() {
        val state = dealGame(seed = 3L, versions = versions)

        assertEquals(listOf(null, null, null, null), state.freeCells)
        assertEquals(Suit.entries.associateWith { 0 }, state.foundations)
        assertEquals(FREE_CELLS, state.emptyFreeCells)
        assertEquals(0, state.emptyColumns)
    }

    @Test
    fun `a fresh deal is not won and has played no moves`() {
        val state = dealGame(seed = 7L, versions = versions)

        assertEquals(0, state.moveCount)
        assertTrue(!state.isWon)
    }
}
