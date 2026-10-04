package org.finiteplay.spider

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.DECK_CARDS
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.invariantViolations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DealTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `the deal is four columns of six, six of five, and fifty in stock`() {
        val state = dealGame(seed = 1L, versions = versions)

        assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), state.tableau.map { it.size })
        assertEquals(50, state.stock.size)
        assertEquals(5, state.rowDealsRemaining)
        assertEquals(emptyList<String>(), invariantViolations(state))
    }

    @Test
    fun `every column shows its last card and hides the rest`() {
        val state = dealGame(seed = 2L, versions = versions)

        for ((index, column) in state.tableau.withIndex()) {
            assertTrue("column $index ends face down", column.last().faceUp)
            assertEquals("column $index shows more than its last card", 1, column.count { it.faceUp })
        }
    }

    @Test
    fun `each suit count deals a hundred and four cards from the suits it names`() {
        for (suitCount in SuitCount.entries) {
            val state = dealGame(seed = 3L, versions = versions, suitCount = suitCount)
            val cards = state.tableau.flatten().map { it.card } + state.stock

            assertEquals("$suitCount deals the wrong number of cards", DECK_CARDS, cards.size)
            assertEquals("$suitCount uses the wrong suits", suitCount.suits.toSet(), cards.map { it.suit }.toSet())
            for (suit in suitCount.suits) {
                for (rank in Rank.entries) {
                    assertEquals(
                        "$suitCount holds the wrong number of ${rank.name} of ${suit.name}",
                        suitCount.setsPerSuit,
                        cards.count { it == Card(suit, rank) },
                    )
                }
            }
        }
    }

    @Test
    fun `a seed names the same arrangement whatever the suit count`() {
        // The shuffle runs over deck positions and the suit count decides what card each
        // position holds, so the one-suit and four-suit deals of a seed are the same shape.
        val one = dealGame(seed = 4L, versions = versions, suitCount = SuitCount.ONE)
        val four = dealGame(seed = 4L, versions = versions, suitCount = SuitCount.FOUR)

        assertEquals(one.tableau.map { it.size }, four.tableau.map { it.size })
        assertEquals(
            one.tableau.flatten().map { it.card.rank },
            four.tableau.flatten().map { it.card.rank },
        )
    }

    @Test
    fun `the same seed deals the same layout and different seeds do not`() {
        assertEquals(dealGame(5L, versions).tableau, dealGame(5L, versions).tableau)
        assertNotEquals(dealGame(5L, versions).tableau, dealGame(6L, versions).tableau)
    }

    @Test
    fun `a fresh deal is not won and has played no moves`() {
        val state = dealGame(seed = 7L, versions = versions)

        assertEquals(0, state.moveCount)
        assertEquals(0, state.sequencesBanked)
        assertTrue(!state.isWon)
        assertEquals(Suit.entries.toSet(), state.banked.keys)
    }
}
