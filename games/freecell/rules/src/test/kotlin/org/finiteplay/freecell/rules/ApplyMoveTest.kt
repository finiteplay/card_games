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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyMoveTest {

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
    fun `applyMove never touches moveCount, unlike the callers that do`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.CLUBS, Rank.ACE)) else emptyList() },
        ).copy(moveCount = 5)

        val next = applyMove(state, Move.TableauToFoundation(0))

        assertEquals("applyMove is a pure transition; scoring is its callers' job", 5, next.moveCount)
    }

    @Test
    fun `an illegal move is refused`() {
        val state = fixture()
        assertThrows(IllegalArgumentException::class.java) {
            applyMove(state, Move.TableauToFoundation(0))
        }
    }

    @Test
    fun `a supermove carries every lifted card in order and clears the source down to fromIndex`() {
        val run = listOf(Card(Suit.CLUBS, Rank.NINE), Card(Suit.HEARTS, Rank.EIGHT), Card(Suit.SPADES, Rank.SEVEN))
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) run else emptyList() },
        )

        val next = applyMove(state, Move.TableauToTableau(fromColumn = 0, fromIndex = 1, toColumn = 1))

        assertEquals(listOf(Card(Suit.CLUBS, Rank.NINE)), next.tableau[0])
        assertEquals(listOf(Card(Suit.HEARTS, Rank.EIGHT), Card(Suit.SPADES, Rank.SEVEN)), next.tableau[1])
    }

    @Test
    fun `tableau to free cell empties the cell's slot from null to the card`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.SPADES, Rank.KING)) else emptyList() },
        )

        val next = applyMove(state, Move.TableauToFreeCell(0, 2))

        assertTrue(next.tableau[0].isEmpty())
        assertEquals(Card(Suit.SPADES, Rank.KING), next.freeCells[2])
    }

    @Test
    fun `free cell to tableau clears the cell back to null`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 1) listOf(Card(Suit.CLUBS, Rank.SEVEN)) else emptyList() },
            freeCells = listOf(Card(Suit.HEARTS, Rank.SIX), null, null, null),
        )

        val next = applyMove(state, Move.FreeCellToTableau(0, 1))

        assertNull(next.freeCells[0])
        assertEquals(listOf(Card(Suit.CLUBS, Rank.SEVEN), Card(Suit.HEARTS, Rank.SIX)), next.tableau[1])
    }

    @Test
    fun `banking every card wins the game`() {
        var state = fixture(foundations = Suit.entries.associateWith { 12 })
        for (suit in Suit.entries) {
            state = state.copy(tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(suit, Rank.KING)) else emptyList() })
            state = applyMove(state, Move.TableauToFoundation(0))
        }
        assertEquals(GameStatus.WON, state.status)
        assertEquals(Card.DECK_SIZE, state.foundations.values.sum())
    }

    @Test
    fun `free cell to foundation also banks and clears the cell`() {
        val state = fixture(
            foundations = Suit.entries.associateWith { 12 }.toMutableMap().apply { put(Suit.CLUBS, 11) },
            freeCells = listOf(Card(Suit.CLUBS, Rank.QUEEN), null, null, null),
        )
        val next = applyMove(state, Move.FreeCellToFoundation(0))
        assertNull(next.freeCells[0])
        assertEquals(12, next.foundations.getValue(Suit.CLUBS))
    }
}
