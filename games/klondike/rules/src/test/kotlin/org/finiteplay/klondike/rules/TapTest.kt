package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceDown
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withStock
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStateFixtures.withWaste
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Table-driven coverage of the documented tap priority: the lowest-index legal
 * tableau destination strictly to the right of the source column; then a legal
 * foundation move regardless of safety; then, only when neither exists, the
 * lowest-index legal tableau destination to the left of the source column.
 */
class TapTest {

    private fun board(): GameState = GameStateFixtures.emptyBoard()

    @Test
    fun `a rightward tableau destination wins even when a safe foundation move also exists`() {
        // Clubs Ace is always safe, but Hearts Two (to the right) also legally accepts it.
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.ACE))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.TWO))))

        assertEquals(
            Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 1),
            resolveTableauTap(state, fromColumn = 0, fromIndex = 0),
        )
    }

    @Test
    fun `the lowest-index rightward destination is chosen among several`() {
        val moved = Card(Suit.SPADES, Rank.SIX)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(moved)))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(4, listOf(faceUp(Card(Suit.DIAMONDS, Rank.SEVEN))))

        assertEquals(
            Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 2),
            resolveTableauTap(state, fromColumn = 0, fromIndex = 0),
        )
    }

    @Test
    fun `a leftward destination is used only when no rightward or foundation move exists`() {
        // Hearts Seven, to the left, legally accepts the run; nothing exists to the right
        // and the run is not foundation-eligible.
        val moved = Card(Suit.SPADES, Rank.SIX)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(2, listOf(faceUp(moved)))

        assertEquals(
            Move.TableauToTableau(fromColumn = 2, fromIndex = 0, toColumn = 0),
            resolveTableauTap(state, fromColumn = 2, fromIndex = 0),
        )
    }

    @Test
    fun `foundation is preferred over a leftward destination`() {
        val state = board()
            .withFoundation(Suit.CLUBS, Rank.ACE.value)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.CLUBS, Rank.TWO))))

        assertEquals(Move.TableauToFoundation(fromColumn = 2), resolveTableauTap(state, fromColumn = 2, fromIndex = 0))
    }

    @Test
    fun `the lowest-index leftward destination is chosen among several`() {
        val moved = Card(Suit.SPADES, Rank.SIX)
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.SEVEN))))
            .withTableauColumn(4, listOf(faceUp(moved)))

        assertEquals(
            Move.TableauToTableau(fromColumn = 4, fromIndex = 0, toColumn = 0),
            resolveTableauTap(state, fromColumn = 4, fromIndex = 0),
        )
    }

    @Test
    fun `foundation is used only when no rightward tableau destination exists, safe or not`() {
        // Foundation Clubs already has Ace, so Two is legal but not safe (needs every suit's Ace first).
        val state = board()
            .withFoundation(Suit.CLUBS, Rank.ACE.value)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.TWO))))

        assertEquals(Move.TableauToFoundation(fromColumn = 0), resolveTableauTap(state, fromColumn = 0, fromIndex = 0))
    }

    @Test
    fun `a King run with no rightward empty column and no foundation acceptance resolves to no move`() {
        // Every column to the right is occupied by a card the King cannot land on
        // (Klondike accepts a King run only onto an empty column).
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(3, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(4, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(5, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(6, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))

        assertNull(resolveTableauTap(state, fromColumn = 0, fromIndex = 0))
    }

    @Test
    fun `a multi-card run never falls back to foundation, only a single-card run does`() {
        // The run's bottom card (Eight) would be foundation-eligible on its own, but
        // the single-card foundation fallback never applies to a multi-card run.
        val state = board()
            .withFoundation(Suit.SPADES, Rank.SEVEN.value)
            .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.EIGHT)), faceUp(Card(Suit.HEARTS, Rank.SEVEN))))

        assertNull(resolveTableauTap(state, fromColumn = 0, fromIndex = 0))
    }

    @Test
    fun `invalid tap - no legal destination anywhere returns null`() {
        val state = board()
            .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(1, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(3, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(4, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(5, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
            .withTableauColumn(6, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))

        assertNull(resolveTableauTap(state, fromColumn = 1, fromIndex = 0))
    }

    @Test
    fun `tapping a face-down card resolves to no move`() {
        val state = board().withTableauColumn(0, listOf(faceDown(Card(Suit.CLUBS, Rank.TWO))))

        assertNull(resolveTableauTap(state, fromColumn = 0, fromIndex = 0))
    }

    @Test
    fun `waste tap prefers a safe foundation, then tableau, then an unsafe foundation`() {
        val safe = board().withWaste(listOf(Card(Suit.HEARTS, Rank.ACE)))
        assertEquals(Move.WasteToFoundation, resolveWasteTap(safe))

        val toTableau = board()
            .withWaste(listOf(Card(Suit.SPADES, Rank.SIX)))
            .withTableauColumn(2, listOf(faceUp(Card(Suit.HEARTS, Rank.SEVEN))))
        assertEquals(Move.WasteToTableau(2), resolveWasteTap(toTableau))

        assertNull(resolveWasteTap(board()))
    }

    @Test
    fun `stock tap draws, then recycles once empty, then resolves to no move`() {
        val withStock = board().withStock(listOf(Card(Suit.CLUBS, Rank.ACE)))
        assertEquals(Move.Draw, resolveStockTap(withStock))

        val emptyStockWithWaste = board().withWaste(listOf(Card(Suit.CLUBS, Rank.ACE)))
        assertEquals(Move.Recycle, resolveStockTap(emptyStockWithWaste))

        assertNull(resolveStockTap(board()))
    }
}
