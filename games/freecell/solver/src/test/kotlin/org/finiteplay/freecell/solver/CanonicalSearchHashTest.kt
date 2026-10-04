package org.finiteplay.freecell.solver

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanonicalSearchHashTest {
    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    private fun fixture(
        tableau: List<List<Card>>,
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
    fun `permuting which column holds which pile does not change the hash`() {
        val columnA = listOf(Card(Suit.CLUBS, Rank.TWO))
        val columnB = listOf(Card(Suit.HEARTS, Rank.KING), Card(Suit.SPADES, Rank.SIX))
        val empty = emptyList<Card>()

        val original = fixture(List(TABLEAU_COLUMNS) { index -> if (index == 0) columnA else if (index == 1) columnB else empty })
        val swapped = fixture(List(TABLEAU_COLUMNS) { index -> if (index == 0) columnB else if (index == 1) columnA else empty })

        assertEquals(canonicalSearchHash(original), canonicalSearchHash(swapped))
    }

    @Test
    fun `permuting which free cell holds which card does not change the hash`() {
        val empty = List(TABLEAU_COLUMNS) { emptyList<Card>() }
        val original = fixture(empty, freeCells = listOf(Card(Suit.CLUBS, Rank.TWO), Card(Suit.HEARTS, Rank.KING), null, null))
        val swapped = fixture(empty, freeCells = listOf(null, Card(Suit.CLUBS, Rank.TWO), Card(Suit.HEARTS, Rank.KING), null))

        assertEquals(canonicalSearchHash(original), canonicalSearchHash(swapped))
    }

    @Test
    fun `a genuinely different board hashes differently`() {
        val empty = List(TABLEAU_COLUMNS) { emptyList<Card>() }
        val withTwo = fixture(List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.CLUBS, Rank.TWO)) else emptyList() })
        val withThree = fixture(List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.CLUBS, Rank.THREE)) else emptyList() })

        assertNotEquals(canonicalSearchHash(withTwo), canonicalSearchHash(withThree))
        assertNotEquals(canonicalSearchHash(fixture(empty)), canonicalSearchHash(withTwo))
    }

    @Test
    fun `card order within a column is not a symmetry`() {
        val ascending = fixture(
            List(TABLEAU_COLUMNS) { index ->
                if (index == 0) listOf(Card(Suit.CLUBS, Rank.TWO), Card(Suit.CLUBS, Rank.THREE)) else emptyList()
            },
        )
        val descending = fixture(
            List(TABLEAU_COLUMNS) { index ->
                if (index == 0) listOf(Card(Suit.CLUBS, Rank.THREE), Card(Suit.CLUBS, Rank.TWO)) else emptyList()
            },
        )

        assertNotEquals(canonicalSearchHash(ascending), canonicalSearchHash(descending))
    }
}
