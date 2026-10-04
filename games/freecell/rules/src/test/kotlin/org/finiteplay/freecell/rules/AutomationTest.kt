package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    private fun fixture(
        tableau: List<List<Card>> = List(TABLEAU_COLUMNS) { emptyList() },
        freeCells: List<Card?> = List(FREE_CELLS) { null },
        foundations: Map<Suit, Int> = Suit.entries.associateWith { 0 },
    ): FreeCellState = FreeCellState(
        seed = 0,
        versions = versions,
        tableau = tableau,
        freeCells = freeCells,
        foundations = foundations,
        status = GameStatus.IN_PROGRESS,
    )

    @Test
    fun `aces and twos are always safe`() {
        val foundations = Suit.entries.associateWith { 0 }
        assertTrue(isSafeFoundationMove(foundations, Card(Suit.HEARTS, Rank.ACE)))
        assertTrue(isSafeFoundationMove(foundations, Card(Suit.HEARTS, Rank.TWO)))
    }

    @Test
    fun `a higher rank is safe only once every suit reached the rank beneath it`() {
        val foundations = mapOf(Suit.CLUBS to 5, Suit.DIAMONDS to 5, Suit.HEARTS to 5, Suit.SPADES to 5)
        assertTrue("all four suits at 5", isSafeFoundationMove(foundations, Card(Suit.CLUBS, Rank.SIX)))

        val notEveryOne = mapOf(Suit.CLUBS to 5, Suit.DIAMONDS to 5, Suit.HEARTS to 4, Suit.SPADES to 5)
        assertEquals(false, isSafeFoundationMove(notEveryOne, Card(Suit.CLUBS, Rank.SIX)))
    }

    @Test
    fun `the cascade checks free cells before tableau columns`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 3) listOf(Card(Suit.HEARTS, Rank.ACE)) else emptyList() },
            freeCells = listOf(Card(Suit.CLUBS, Rank.ACE), null, null, null),
        )

        val move = findNextSafeAutomaticMove(state)

        assertEquals(Move.FreeCellToFoundation(0), move)
    }

    @Test
    fun `the cascade checks tableau columns left to right`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                when (index) {
                    2 -> listOf(Card(Suit.CLUBS, Rank.ACE))
                    5 -> listOf(Card(Suit.HEARTS, Rank.ACE))
                    else -> emptyList()
                }
            },
        )

        assertEquals(Move.TableauToFoundation(2), findNextSafeAutomaticMove(state))
    }

    @Test
    fun `the cascade runs until no safe card remains, across tableau and free cells alike`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                when (index) {
                    0 -> listOf(Card(Suit.CLUBS, Rank.ACE))
                    1 -> listOf(Card(Suit.DIAMONDS, Rank.ACE))
                    else -> emptyList()
                }
            },
            freeCells = listOf(Card(Suit.HEARTS, Rank.ACE), Card(Suit.SPADES, Rank.ACE), null, null),
        )

        val (result, applied) = runAutomaticFoundationCascade(state)

        assertEquals(4, applied.size)
        assertEquals(Suit.entries.associateWith { 1 }, result.foundations)
        assertTrue(result.tableau.all { it.isEmpty() })
        assertEquals(listOf(null, null, null, null), result.freeCells)
    }

    @Test
    fun `a rank above two waits for every foundation, not just its own`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.CLUBS, Rank.THREE)) else emptyList() },
            foundations = mapOf(Suit.CLUBS to 2, Suit.DIAMONDS to 2, Suit.HEARTS to 2, Suit.SPADES to 1),
        )

        assertNull("spades has not reached 2 yet", findNextSafeAutomaticMove(state))
    }

    @Test
    fun `each cascade transfer counts one move`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.CLUBS, Rank.ACE)) else emptyList() },
        )

        val (result, applied) = runAutomaticFoundationCascade(state)

        assertEquals(1, applied.size)
        assertEquals(state.moveCount + 1, result.moveCount)
    }
}
