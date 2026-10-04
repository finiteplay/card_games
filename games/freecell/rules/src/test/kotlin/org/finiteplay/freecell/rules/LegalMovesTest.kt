package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.freecell.layout.FREE_CELLS
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.TABLEAU_COLUMNS
import org.finiteplay.freecell.layout.dealGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalMovesTest {

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

    // -- canBuild: rank and colour --

    @Test
    fun `canBuild requires one rank lower and the opposite colour`() {
        val blackSeven = Card(Suit.CLUBS, Rank.SEVEN)
        assertTrue(canBuild(blackSeven, Card(Suit.HEARTS, Rank.SIX)))
        assertTrue(canBuild(blackSeven, Card(Suit.DIAMONDS, Rank.SIX)))
        assertFalse("same colour", canBuild(blackSeven, Card(Suit.SPADES, Rank.SIX)))
        assertFalse("wrong rank", canBuild(blackSeven, Card(Suit.HEARTS, Rank.FIVE)))
        assertFalse("wrong direction", canBuild(blackSeven, Card(Suit.HEARTS, Rank.EIGHT)))
    }

    // -- sequenceStart / isMovableSequence --

    @Test
    fun `sequenceStart finds the deepest card of the descending alternating run`() {
        val column = listOf(
            Card(Suit.SPADES, Rank.TWO), // broken from here up: not part of the run
            Card(Suit.CLUBS, Rank.NINE),
            Card(Suit.HEARTS, Rank.EIGHT),
            Card(Suit.SPADES, Rank.SEVEN),
        )
        assertEquals(1, sequenceStart(column))
        assertTrue(isMovableSequence(column, 1))
        assertTrue(isMovableSequence(column, 3))
        assertFalse("above the run's own start, not a valid lift point", isMovableSequence(column, 0))
    }

    @Test
    fun `an empty column has no sequence and a single card is its own`() {
        assertEquals(-1, sequenceStart(emptyList()))
        assertEquals(0, sequenceStart(listOf(Card(Suit.SPADES, Rank.KING))))
    }

    // -- TableauToFreeCell --

    @Test
    fun `a card moves to any empty free cell, never a full one`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.SPADES, Rank.KING)) else emptyList() },
            freeCells = listOf(Card(Suit.HEARTS, Rank.TWO), null, null, null),
        )
        assertFalse("cell 0 is full", isLegal(state, Move.TableauToFreeCell(0, 0)))
        assertTrue(isLegal(state, Move.TableauToFreeCell(0, 1)))
        assertFalse("column 1 is empty", isLegal(state, Move.TableauToFreeCell(1, 2)))
    }

    // -- TableauToFoundation / FreeCellToFoundation --

    @Test
    fun `a foundation takes only the exact next rank of its own suit`() {
        val foundations = mapOf(Suit.CLUBS to 5, Suit.DIAMONDS to 0, Suit.HEARTS to 0, Suit.SPADES to 0)
        assertTrue(canPlaceOnFoundation(foundations, Card(Suit.CLUBS, Rank.SIX)))
        assertFalse("skips a rank", canPlaceOnFoundation(foundations, Card(Suit.CLUBS, Rank.SEVEN)))
        assertFalse("wrong suit", canPlaceOnFoundation(foundations, Card(Suit.DIAMONDS, Rank.SIX)))
        assertTrue("an empty foundation takes its ace", canPlaceOnFoundation(foundations, Card(Suit.HEARTS, Rank.ACE)))
    }

    @Test
    fun `foundation moves come from the tableau top or a free cell, never lower in a column`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index ->
                if (index == 0) listOf(Card(Suit.CLUBS, Rank.ACE), Card(Suit.HEARTS, Rank.TWO)) else emptyList()
            },
        )
        assertFalse("the ace is buried under the two", isLegal(state, Move.TableauToFoundation(0)))

        val cleared = fixture(tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.CLUBS, Rank.ACE)) else emptyList() })
        assertTrue(isLegal(cleared, Move.TableauToFoundation(0)))
    }

    // -- FreeCellToTableau --

    @Test
    fun `a free cell card takes any empty column and only a legally-built non-empty one`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 1) listOf(Card(Suit.CLUBS, Rank.SEVEN)) else emptyList() },
            freeCells = listOf(Card(Suit.HEARTS, Rank.SIX), Card(Suit.SPADES, Rank.SIX), null, null),
        )
        assertTrue("column 0 is empty", isLegal(state, Move.FreeCellToTableau(0, 0)))
        assertTrue("red six builds on black seven", isLegal(state, Move.FreeCellToTableau(0, 1)))
        assertFalse("black six cannot build on black seven", isLegal(state, Move.FreeCellToTableau(1, 1)))
        assertFalse("cell 2 is empty", isLegal(state, Move.FreeCellToTableau(2, 0)))
    }

    // -- Any card, not just a King, may enter an empty column --

    @Test
    fun `an empty column accepts any single card, unlike Klondike's King-only rule`() {
        val state = fixture(
            tableau = List(TABLEAU_COLUMNS) { index -> if (index == 0) listOf(Card(Suit.HEARTS, Rank.FOUR)) else emptyList() },
        )
        assertTrue(isLegal(state, Move.TableauToTableau(0, 0, 1)))
    }

    // -- legalMoves / isStuck sanity on a real deal --

    @Test
    fun `a fresh deal always offers a move and is never stuck`() {
        for (seed in 1L..30L) {
            val state = dealGame(seed, versions)
            assertTrue("seed $seed", legalMoves(state).isNotEmpty())
            assertFalse(isStuck(state))
        }
    }

    @Test
    fun `a won game offers no moves`() {
        val won = fixture(foundations = Suit.entries.associateWith { 13 }).copy(status = GameStatus.WON)
        assertEquals(emptyList<Move>(), legalMoves(won))
    }
}
