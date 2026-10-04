package org.finiteplay.klondike.board

import org.finiteplay.cards.Card
import org.finiteplay.cards.shuffleDeckIndices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DealTest {

    private val versions = GameStateFixtures.TEST_VERSIONS

    @Test
    fun `deals 28 tableau cards and 24 stock cards`() {
        val state = dealGame(seed = 42L, versions = versions)

        assertEquals(28, state.tableau.sumOf { it.size })
        assertEquals(24, state.stock.size)
        assertEquals(0, state.waste.size)
    }

    @Test
    fun `each tableau column has one more card than the last and only the top is face-up`() {
        val state = dealGame(seed = 42L, versions = versions)

        state.tableau.forEachIndexed { index, column ->
            assertEquals("column $index size", index + 1, column.size)
            column.dropLast(1).forEach { assertFalse("column $index has a face-up card below the top", it.faceUp) }
            assertTrue("column $index top must be face-up", column.last().faceUp)
        }
    }

    @Test
    fun `every card appears exactly once across the dealt board`() {
        val state = dealGame(seed = 42L, versions = versions)

        val dealt = buildList {
            state.tableau.forEach { column -> column.forEach { add(it.card) } }
            addAll(state.stock)
        }
        assertEquals(Card.DECK_SIZE, dealt.size)
        assertEquals(Card.CANONICAL_DECK.toSet(), dealt.toSet())
    }

    @Test
    fun `deal follows the shuffled index order from the deal contract`() {
        val seed = 12345L
        val shuffled = shuffleDeckIndices(seed).map(Card.Companion::fromId)
        val state = dealGame(seed, versions)

        val expectedOrder = mutableListOf<Card>()
        for (round in 0 until TABLEAU_COLUMNS) {
            for (column in round until TABLEAU_COLUMNS) {
                expectedOrder.add(state.tableau[column][round].card)
            }
        }
        expectedOrder.addAll(state.stock)

        assertEquals(shuffled, expectedOrder)
        assertEquals(shuffled.subList(28, 52), state.stock)
    }
}
