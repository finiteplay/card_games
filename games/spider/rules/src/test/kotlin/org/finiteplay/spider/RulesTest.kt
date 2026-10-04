package org.finiteplay.spider

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.spider.layout.GameStatus
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.layout.TableauCard
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.canDealRow
import org.finiteplay.spider.rules.canPlaceOn
import org.finiteplay.spider.rules.isLegal
import org.finiteplay.spider.rules.isMovableSequence
import org.finiteplay.spider.rules.legalMoves
import org.finiteplay.spider.rules.sequenceStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    private fun up(suit: Suit, rank: Rank) = TableauCard(Card(suit, rank), faceUp = true)
    private fun down(suit: Suit, rank: Rank) = TableauCard(Card(suit, rank), faceUp = false)

    /** A layout with the given columns and nothing in the stock, for reasoning about one rule at a time. */
    private fun layout(vararg columns: List<TableauCard>, stock: List<Card> = emptyList()): SpiderState {
        val padded = columns.toList() + List(TABLEAU_COLUMNS - columns.size) { emptyList() }
        return SpiderState(
            seed = 0L,
            versions = versions,
            suitCount = SuitCount.FOUR,
            tableau = padded,
            stock = stock,
            banked = Suit.entries.associateWith { 0 },
            status = GameStatus.IN_PROGRESS,
        )
    }

    @Test
    fun `placing ignores suit, and only the rank step matters`() {
        assertTrue(canPlaceOn(Card(Suit.SPADES, Rank.EIGHT), Card(Suit.HEARTS, Rank.SEVEN)))
        assertTrue(canPlaceOn(Card(Suit.SPADES, Rank.EIGHT), Card(Suit.SPADES, Rank.SEVEN)))
        assertFalse(canPlaceOn(Card(Suit.SPADES, Rank.EIGHT), Card(Suit.HEARTS, Rank.SIX)))
        assertFalse(canPlaceOn(Card(Suit.SPADES, Rank.EIGHT), Card(Suit.HEARTS, Rank.NINE)))
    }

    @Test
    fun `a sequence is single-suited, and a mixed descending group is not one`() {
        val mixed = listOf(up(Suit.SPADES, Rank.NINE), up(Suit.HEARTS, Rank.EIGHT), up(Suit.HEARTS, Rank.SEVEN))

        // The eight and seven are hearts and descend, so they lift together; the nine does not
        // join them. This asymmetry — legal to build, illegal to move — is the game.
        assertEquals(1, sequenceStart(mixed))
        assertTrue(isMovableSequence(mixed, 1))
        assertTrue(isMovableSequence(mixed, 2))
        assertFalse(isMovableSequence(mixed, 0))
    }

    @Test
    fun `a face-down card is never part of a sequence`() {
        val column = listOf(down(Suit.SPADES, Rank.NINE), up(Suit.SPADES, Rank.EIGHT))

        assertEquals(1, sequenceStart(column))
        assertFalse(isMovableSequence(column, 0))
    }

    @Test
    fun `an empty column accepts any card, not only a King`() {
        val state = layout(
            listOf(up(Suit.SPADES, Rank.FIVE)),
            emptyList(),
        )

        assertTrue(isLegal(state, Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 1)))
    }

    @Test
    fun `a row deal needs cards in the stock and no empty column`() {
        val stock = List(TABLEAU_COLUMNS) { Card(Suit.SPADES, Rank.TWO) }
        val withEmpty = layout(listOf(up(Suit.SPADES, Rank.FIVE)), stock = stock)
        assertFalse("an empty column must block the deal", canDealRow(withEmpty))

        val full = layout(*Array(TABLEAU_COLUMNS) { listOf(up(Suit.SPADES, Rank.FIVE)) }, stock = stock)
        assertTrue(canDealRow(full))

        val noStock = layout(*Array(TABLEAU_COLUMNS) { listOf(up(Suit.SPADES, Rank.FIVE)) })
        assertFalse(canDealRow(noStock))
    }

    @Test
    fun `moving off a column turns the card it exposes face up`() {
        val state = layout(
            listOf(down(Suit.SPADES, Rank.KING), up(Suit.SPADES, Rank.FOUR)),
            listOf(up(Suit.HEARTS, Rank.FIVE)),
        )

        val after = applyMove(state, Move.TableauToTableau(fromColumn = 0, fromIndex = 1, toColumn = 1))

        assertTrue("the exposed King must be face up", after.tableau[0].last().faceUp)
        assertEquals(2, after.tableau[1].size)
        assertEquals(1, after.moveCount)
    }

    @Test
    fun `completing a King to Ace sequence banks it`() {
        // Twelve of one suit already stacked, with the Ace waiting one column over.
        val stacked = Rank.entries.reversed().dropLast(1).map { up(Suit.SPADES, it) }
        val state = layout(stacked, listOf(up(Suit.SPADES, Rank.ACE)))

        val after = applyMove(state, Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0))

        assertEquals("the sequence must leave the tableau", 0, after.tableau[0].size)
        assertEquals(1, after.sequencesBanked)
        assertEquals(1, after.banked.getValue(Suit.SPADES))
    }

    @Test
    fun `a mixed-suit King to Ace run is not a sequence and is not banked`() {
        val mixed = Rank.entries.reversed().dropLast(1)
            .map { up(if (it == Rank.SEVEN) Suit.HEARTS else Suit.SPADES, it) }
        val state = layout(mixed, listOf(up(Suit.SPADES, Rank.ACE)))

        // The Ace cannot even be lifted onto it as one sequence — but placing it is legal, and
        // the result must stay in the tableau.
        val after = applyMove(state, Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0))

        assertEquals(13, after.tableau[0].size)
        assertEquals(0, after.sequencesBanked)
    }

    @Test
    fun `banking the eighth sequence wins the deal`() {
        val stacked = Rank.entries.reversed().dropLast(1).map { up(Suit.SPADES, it) }
        val nearlyDone = layout(stacked, listOf(up(Suit.SPADES, Rank.ACE)))
            .copy(banked = mapOf(Suit.SPADES to 7, Suit.HEARTS to 0, Suit.CLUBS to 0, Suit.DIAMONDS to 0))

        val after = applyMove(nearlyDone, Move.TableauToTableau(fromColumn = 1, fromIndex = 0, toColumn = 0))

        assertEquals(GameStatus.WON, after.status)
        assertTrue(after.isWon)
        assertEquals(emptyList<Move>(), legalMoves(after))
    }

    @Test
    fun `an illegal move is rejected rather than applied`() {
        val state = layout(
            listOf(up(Suit.SPADES, Rank.FIVE)),
            listOf(up(Suit.HEARTS, Rank.TWO)),
        )

        val failure = runCatching {
            applyMove(state, Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 1))
        }.exceptionOrNull()

        assertTrue("expected an illegal-move rejection, got $failure", failure is IllegalArgumentException)
    }
}
