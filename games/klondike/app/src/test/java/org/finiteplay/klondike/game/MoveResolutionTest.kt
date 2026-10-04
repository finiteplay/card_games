package org.finiteplay.klondike

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveResolutionTest {
    private val versions = GameVersions(1, 1, 1)

    private fun stateWith(tableau: List<List<TableauCard>>, foundations: Map<Suit, Int>): GameState = GameState(
        seed = 0L,
        versions = versions,
        tableau = tableau,
        foundations = Suit.entries.associateWith { 0 } + foundations,
        waste = emptyList(),
        stock = emptyList(),
        status = GameStatus.IN_PROGRESS,
    )

    private fun emptyTableau(): List<MutableList<TableauCard>> = List(7) { mutableListOf() }

    @Test
    fun resolveFoundationTapMovesToLowestIndexLegalColumn() {
        val tableau = emptyTableau()
        // Columns 2 and 4 both accept a black Nine (red Ten on top); the resolver must
        // prefer the lower index. Columns 0/1/3 are empty and accept only a King.
        tableau[2].add(TableauCard(Card(Suit.HEARTS, Rank.TEN), faceUp = true))
        tableau[4].add(TableauCard(Card(Suit.DIAMONDS, Rank.TEN), faceUp = true))
        val state = stateWith(tableau, mapOf(Suit.SPADES to Rank.NINE.value))

        val move = resolveFoundationTap(state, Suit.SPADES)

        assertEquals(Move.FoundationToTableau(Suit.SPADES, toColumn = 2), move)
    }

    @Test
    fun resolveFoundationTapReturnsNullWhenFoundationEmpty() {
        val state = stateWith(emptyTableau(), emptyMap())
        assertNull(resolveFoundationTap(state, Suit.CLUBS))
    }

    @Test
    fun resolveFoundationTapReturnsNullWhenNoLegalTableauDestination() {
        // Foundation holds a Queen; no tableau column accepts a black Queen.
        val state = stateWith(emptyTableau(), mapOf(Suit.CLUBS to Rank.QUEEN.value))
        assertNull(resolveFoundationTap(state, Suit.CLUBS))
    }

    @Test
    fun legalDestinationsForDragSourceMatchesOnlyTheDraggedTableauRun() {
        val tableau = emptyTableau()
        tableau[0].add(TableauCard(Card(Suit.SPADES, Rank.NINE), faceUp = true))
        // Red Seven legally stacks on the black Eight in column 1.
        tableau[0].add(TableauCard(Card(Suit.HEARTS, Rank.SEVEN), faceUp = true))
        tableau[1].add(TableauCard(Card(Suit.CLUBS, Rank.EIGHT), faceUp = true))
        val state = stateWith(tableau, emptyMap())

        val destinations = legalDestinationsForDragSource(state, DragSource.Tableau(column = 0, fromIndex = 1, isTopCard = true))

        assertEquals(listOf(Move.TableauToTableau(fromColumn = 0, fromIndex = 1, toColumn = 1)), destinations)
    }

    @Test
    fun legalDestinationsForDragSourceIncludesFoundationOnlyForTopCard() {
        val tableau = emptyTableau()
        tableau[0].add(TableauCard(Card(Suit.CLUBS, Rank.ACE), faceUp = true))
        val state = stateWith(tableau, emptyMap())

        val destinations = legalDestinationsForDragSource(state, DragSource.Tableau(column = 0, fromIndex = 0, isTopCard = true))

        assertTrue(destinations.contains(Move.TableauToFoundation(fromColumn = 0)))
    }
}
