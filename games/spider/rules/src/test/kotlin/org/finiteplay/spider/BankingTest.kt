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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The claim `RULES.md` makes about how many sequences one move can bank, which the spec
 * originally got wrong: a transfer touches one destination column and can complete one, but a
 * row deal puts a card on every column at once and can complete several.
 */
class BankingTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `one row deal can bank more than one sequence`() {
        // Two columns each holding King-to-Two of a suit, and the stock's next row carrying the
        // two Aces that finish them.
        fun nearlyComplete(suit: Suit) = Rank.entries.reversed().dropLast(1)
            .map { TableauCard(Card(suit, it), faceUp = true) }

        val filler = listOf(TableauCard(Card(Suit.CLUBS, Rank.FIVE), faceUp = true))
        val columns = listOf(nearlyComplete(Suit.SPADES), nearlyComplete(Suit.HEARTS)) +
            List(TABLEAU_COLUMNS - 2) { filler }

        // The row deals left to right, so the two Aces have to be the first two cards of it.
        val row = listOf(Card(Suit.SPADES, Rank.ACE), Card(Suit.HEARTS, Rank.ACE)) +
            List(TABLEAU_COLUMNS - 2) { Card(Suit.CLUBS, Rank.FOUR) }

        val state = SpiderState(
            seed = 0L,
            versions = versions,
            suitCount = SuitCount.FOUR,
            tableau = columns,
            stock = row,
            banked = Suit.entries.associateWith { 0 },
            status = GameStatus.IN_PROGRESS,
        )

        val after = applyMove(state, Move.DealRow)

        assertEquals("both sequences must be banked by the one deal", 2, after.sequencesBanked)
        assertEquals(1, after.banked.getValue(Suit.SPADES))
        assertEquals(1, after.banked.getValue(Suit.HEARTS))
        assertTrue("the banked columns must be empty", after.tableau[0].isEmpty() && after.tableau[1].isEmpty())
        assertEquals("a row deal is one move however much it banks", 1, after.moveCount)
    }
}
