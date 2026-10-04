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
import org.junit.Test

class TapResolutionTest {

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
    fun `a tableau tap prefers the nearest legal column to the right`() {
        // Columns 1 and 2 hold a same-rank red card each — the wrong rank to build the black
        // seven onto, and blocking what would otherwise be the nearest *empty* column instead —
        // so column 3's own red eight, correctly one rank higher, is the nearest genuinely legal
        // rightward destination for a seven to land on.
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                when (index) {
                    0 -> listOf(Card(Suit.CLUBS, Rank.SEVEN))
                    1 -> listOf(Card(Suit.HEARTS, Rank.SEVEN))
                    2 -> listOf(Card(Suit.DIAMONDS, Rank.SEVEN))
                    3 -> listOf(Card(Suit.HEARTS, Rank.EIGHT))
                    else -> emptyList()
                }
            },
        )
        assertEquals(Move.TableauToTableau(0, 0, 3), resolveTableauTap(state, 0, 0))
    }

    @Test
    fun `a single safe card prefers a safe foundation over a column to the left`() {
        // The last column, so there is no rightward range to check first — every other column is
        // empty and to the left, which the safe foundation must still win over.
        val lastColumn = TABLEAU_COLUMNS - 1
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == lastColumn) listOf(Card(Suit.CLUBS, Rank.ACE)) else emptyList() },
        )
        assertEquals(Move.TableauToFoundation(lastColumn), resolveTableauTap(state, lastColumn, 0))
    }

    @Test
    fun `an unsafe foundation is only the last resort, after a leftward column`() {
        // A three of clubs (never safe on the rank<=2 shortcut) has a leftward column to land on
        // and a foundation move that is legal but not provably safe (other suits still at 0).
        // Every column but the leftward target is filled with a black four, which the three
        // cannot build on, so it cannot be mistaken for a rightward destination either.
        val filler = Card(Suit.CLUBS, Rank.FOUR)
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                when (index) {
                    1 -> emptyList()
                    2 -> listOf(Card(Suit.CLUBS, Rank.THREE))
                    else -> listOf(filler)
                }
            },
            foundations = mapOf(Suit.CLUBS to 2, Suit.DIAMONDS to 0, Suit.HEARTS to 0, Suit.SPADES to 0),
        )
        // Column 1 is the only empty column, and it is to the left of column 2, so it wins over
        // the legal-but-unsafe foundation move.
        assertEquals(Move.TableauToTableau(2, 0, 1), resolveTableauTap(state, 2, 0))
    }

    @Test
    fun `an unsafe foundation is taken when nothing else is legal`() {
        // A three of clubs is never safe on the rank<=2 shortcut, so this genuinely exercises the
        // last-resort branch rather than the "aces and twos are always safe" one. Every other
        // column is filled with a black four, which the three cannot build on (same colour), so
        // no rightward or leftward destination exists at all.
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                if (index == 0) listOf(Card(Suit.CLUBS, Rank.THREE)) else listOf(Card(Suit.CLUBS, Rank.FOUR))
            },
            foundations = mapOf(Suit.CLUBS to 2, Suit.DIAMONDS to 0, Suit.HEARTS to 0, Suit.SPADES to 0),
        )
        assertEquals(Move.TableauToFoundation(0), resolveTableauTap(state, 0, 0))
    }

    @Test
    fun `a multi-card sequence never resolves to a foundation`() {
        val run = listOf(Card(Suit.CLUBS, Rank.THREE), Card(Suit.HEARTS, Rank.TWO))
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) run else emptyList() },
            foundations = mapOf(Suit.CLUBS to 2, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 13),
        )
        // The bottom of the run (three of clubs) is foundation-eligible, but a two-card sequence
        // can never bank as a whole, and every other column is empty so it lands on one instead.
        assertEquals(Move.TableauToTableau(0, 0, 1), resolveTableauTap(state, 0, 0))
    }

    @Test
    fun `a single card parks in an empty free cell when nothing else is legal`() {
        // A seven of clubs with every foundation still at zero (not even legal, let alone safe or
        // unsafe) and every other column filled with a black four it cannot build on — no
        // rightward, leftward, or foundation destination exists at all, so this genuinely
        // exercises the free-cell fallback rather than any earlier branch.
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                if (index == 0) listOf(Card(Suit.CLUBS, Rank.SEVEN)) else listOf(Card(Suit.CLUBS, Rank.FOUR))
            },
            freeCells = listOf(null, Card(Suit.HEARTS, Rank.KING), null, Card(Suit.SPADES, Rank.KING)),
        )
        // The first empty cell, left to right, same as a free-cell tap's own column search order.
        assertEquals(Move.TableauToFreeCell(0, 0), resolveTableauTap(state, 0, 0))
    }

    @Test
    fun `a multi-card sequence never resolves to a free cell either`() {
        // Same shape as the free-cell fallback test above, but the tapped run is two cards — a
        // free cell holds exactly one, so this must still find nothing rather than parking either
        // card alone.
        val run = listOf(Card(Suit.CLUBS, Rank.SEVEN), Card(Suit.HEARTS, Rank.SIX))
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                if (index == 0) run else listOf(Card(Suit.CLUBS, Rank.FOUR))
            },
            freeCells = List(FREE_CELLS) { null },
        )
        assertNull(resolveTableauTap(state, 0, 0))
    }

    @Test
    fun `a single card with no legal move and no empty free cell finds nothing`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                if (index == 0) listOf(Card(Suit.CLUBS, Rank.SEVEN)) else listOf(Card(Suit.CLUBS, Rank.FOUR))
            },
            freeCells = List(FREE_CELLS) { Card(Suit.HEARTS, Rank.KING) },
        )
        assertNull(resolveTableauTap(state, 0, 0))
    }

    @Test
    fun `tapping above the liftable sequence finds nothing`() {
        val column = listOf(Card(Suit.SPADES, Rank.TWO), Card(Suit.CLUBS, Rank.NINE))
        val state = fixture(tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) column else emptyList() })
        assertNull(resolveTableauTap(state, 0, 0))
    }

    @Test
    fun `a free cell tap prefers a safe foundation, then a column, then an unsafe foundation`() {
        val safe = fixture(freeCells = listOf(Card(Suit.HEARTS, Rank.ACE), null, null, null))
        assertEquals(Move.FreeCellToFoundation(0), resolveFreeCellTap(safe, 0))

        val toColumn = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 2) listOf(Card(Suit.CLUBS, Rank.SEVEN)) else emptyList() },
            freeCells = listOf(Card(Suit.HEARTS, Rank.SIX), null, null, null),
            foundations = mapOf(Suit.HEARTS to 0, Suit.CLUBS to 0, Suit.DIAMONDS to 0, Suit.SPADES to 0),
        )
        assertEquals(Move.FreeCellToTableau(0, 0), resolveFreeCellTap(toColumn, 0))

        val nothingElseLegal = fixture(
            tableau = List(TABLEAU_COLUMNS) { listOf(Card(Suit.HEARTS, Rank.SIX)) }, // same colour/rank as the cell card everywhere
            freeCells = listOf(Card(Suit.DIAMONDS, Rank.SIX), null, null, null),
            foundations = mapOf(Suit.DIAMONDS to 5, Suit.CLUBS to 0, Suit.HEARTS to 0, Suit.SPADES to 0),
        )
        assertEquals(Move.FreeCellToFoundation(0), resolveFreeCellTap(nothingElseLegal, 0))
    }

    @Test
    fun `an empty free cell taps to nothing`() {
        assertNull(resolveFreeCellTap(fixture(), 0))
    }
}
