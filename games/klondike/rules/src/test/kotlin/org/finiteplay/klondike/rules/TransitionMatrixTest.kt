package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Generated transition-coverage matrices for the E1 gate. Every source/destination
 * pile kind's legality reduces to one of two predicates: [canPlaceOnTableau] (used by
 * tableau-to-tableau, waste-to-tableau, and foundation-to-tableau destinations) and
 * [canPlaceOnFoundation] (used by tableau-to-foundation and waste-to-foundation
 * sources). Each is exhaustively cross-checked here against an oracle written
 * independently of the implementation, crossing rank delta, color relationship, and
 * destination emptiness. Committing the case count as a constant makes silently
 * shrinking this coverage fail the build.
 */
class TransitionMatrixTest {

    @Test
    fun `tableau destination acceptance matches every destination-top by moving-card combination`() {
        val destinationTops: List<Card?> = listOf(null) + Card.CANONICAL_DECK
        val movingCards = Card.CANONICAL_DECK

        var cases = 0
        for (destTop in destinationTops) {
            val column = destTop?.let { listOf(TableauCard(it, faceUp = true)) } ?: emptyList()
            for (movingCard in movingCards) {
                cases++
                val expected = if (destTop == null) {
                    movingCard.rank == Rank.KING
                } else {
                    movingCard.rank.value == destTop.rank.value - 1 && movingCard.color != destTop.color
                }
                assertEquals(
                    "destTop=$destTop movingCard=$movingCard",
                    expected,
                    canPlaceOnTableau(column, movingCard),
                )
            }
        }

        assertEquals(TABLEAU_ACCEPTANCE_CASE_COUNT, cases)
    }

    @Test
    fun `foundation acceptance matches every foundation-top-rank by card combination`() {
        val topRankValues = 0..Rank.KING.value
        val movingCards = Card.CANONICAL_DECK

        var cases = 0
        for (topRankValue in topRankValues) {
            for (suit in Suit.entries) {
                val foundations = Suit.entries.associateWith { 0 } + (suit to topRankValue)
                for (movingCard in movingCards) {
                    cases++
                    val relevantTop = if (movingCard.suit == suit) topRankValue else 0
                    val expected = movingCard.rank.value == relevantTop + 1
                    assertEquals(
                        "suit=$suit topRankValue=$topRankValue movingCard=$movingCard",
                        expected,
                        canPlaceOnFoundation(foundations, movingCard),
                    )
                }
            }
        }

        assertEquals(FOUNDATION_ACCEPTANCE_CASE_COUNT, cases)
    }

    @Test
    fun `tableau-to-tableau move legality requires the source card to be face-up`() {
        val base = GameStateFixtures.emptyBoard()
        val faceDownState = base.copy(
            tableau = base.tableau.toMutableList().apply {
                this[0] = listOf(TableauCard(Card(Suit.SPADES, Rank.SIX), faceUp = false))
                this[1] = listOf(TableauCard(Card(Suit.HEARTS, Rank.SEVEN), faceUp = true))
            },
        )
        val faceUpState = base.copy(
            tableau = base.tableau.toMutableList().apply {
                this[0] = listOf(TableauCard(Card(Suit.SPADES, Rank.SIX), faceUp = true))
                this[1] = listOf(TableauCard(Card(Suit.HEARTS, Rank.SEVEN), faceUp = true))
            },
        )
        val move = Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 1)

        assertEquals(false, isLegal(faceDownState, move))
        assertEquals(true, isLegal(faceUpState, move))
    }

    companion object {
        private const val TABLEAU_ACCEPTANCE_CASE_COUNT = 53 * 52
        private const val FOUNDATION_ACCEPTANCE_CASE_COUNT = 14 * 4 * 52
    }
}
