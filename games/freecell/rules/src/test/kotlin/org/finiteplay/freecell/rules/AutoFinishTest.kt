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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every fixture here accounts for all 52 cards — split between [FreeCellState.foundations]
 * (already banked) and a handful physically placed in the tableau or free cells — because
 * winning requires every card banked, so a partial-deck board can never reach it and would make
 * every assertion here vacuous.
 */
class AutoFinishTest {

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
    fun `the last card of every suit, one per column, finishes with no parking at all`() {
        val state = fixture(
            foundations = Suit.entries.associateWith { 12 },
            tableau = listOf(
                listOf(Card(Suit.CLUBS, Rank.KING)),
                listOf(Card(Suit.DIAMONDS, Rank.KING)),
                listOf(Card(Suit.HEARTS, Rank.KING)),
                listOf(Card(Suit.SPADES, Rank.KING)),
                emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )

        assertTrue(canAutoFinish(state))
        assertEquals(GameStatus.WON, simulateSweep(state)?.status)
    }

    @Test
    fun `a card needed next but buried under one that is not yet finishes by parking the blocker`() {
        // Diamonds, hearts and spades are complete. Clubs needs its queen then its king, but the
        // king sits on top of the queen: park the king, bank the queen, then bank the king back
        // out of the free cell it was parked in.
        val state = fixture(
            foundations = mapOf(Suit.CLUBS to 11, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 13),
            tableau = listOf(
                listOf(Card(Suit.CLUBS, Rank.QUEEN), Card(Suit.CLUBS, Rank.KING)),
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )

        val moves = findAutoFinish(state)
        assertEquals(
            listOf(
                Move.TableauToFreeCell(0, 0),
                Move.TableauToFoundation(0),
                Move.FreeCellToFoundation(0),
            ),
            moves,
        )
        assertEquals(GameStatus.WON, simulateSweep(state)?.status)
    }

    @Test
    fun `more simultaneous blockers than free cells refuses rather than guessing`() {
        // Every other suit is complete; every remaining card is a club, ace buried at the very
        // bottom under the other twelve in ascending order. Freeing it needs all twelve parked
        // at once — no order of this restricted move set can do that with only four cells.
        val clubsAceToKing = Rank.entries.map { Card(Suit.CLUBS, it) }
        val state = fixture(
            foundations = mapOf(Suit.CLUBS to 0, Suit.DIAMONDS to 13, Suit.HEARTS to 13, Suit.SPADES to 13),
            tableau = listOf(
                clubsAceToKing,
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            ),
        )

        assertFalse(canAutoFinish(state))
        assertNull(simulateSweep(state))
    }

    @Test
    fun `an already-won board is trivially finished`() {
        val won = fixture(foundations = Suit.entries.associateWith { 13 }).copy(status = GameStatus.WON)
        assertEquals(emptyList<Move>(), findAutoFinish(won))
    }
}
