package org.finiteplay.klondike.rules

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.TableauCard
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ApplyMoveTest {

    private val versions = GameStateFixtures.TEST_VERSIONS

    @Test
    fun `moving a run reveals and flips the card beneath it`() {
        val hidden = Card(Suit.CLUBS, Rank.TWO)
        val moved = Card(Suit.HEARTS, Rank.SEVEN)
        val base = GameStateFixtures.emptyBoard(versions = versions)
        val state = base.copy(
            tableau = base.tableau.toMutableList().apply {
                this[0] = listOf(TableauCard(hidden, faceUp = false), TableauCard(moved, faceUp = true))
                this[1] = listOf(TableauCard(Card(Suit.SPADES, Rank.EIGHT), faceUp = true))
            },
        )

        val result = applyMove(state, Move.TableauToTableau(fromColumn = 0, fromIndex = 1, toColumn = 1))

        assertEquals(listOf(TableauCard(hidden, faceUp = true)), result.tableau[0])
        assertEquals(2, result.tableau[1].size)
    }

    @Test
    fun `moving a multi-card run keeps its internal order`() {
        val bottom = Card(Suit.SPADES, Rank.EIGHT)
        val middle = Card(Suit.HEARTS, Rank.SEVEN)
        val top = Card(Suit.CLUBS, Rank.SIX)
        val base = GameStateFixtures.emptyBoard(versions = versions)
        val state = base.copy(
            tableau = base.tableau.toMutableList().apply {
                this[0] = listOf(
                    TableauCard(bottom, faceUp = true),
                    TableauCard(middle, faceUp = true),
                    TableauCard(top, faceUp = true),
                )
                this[1] = listOf(TableauCard(Card(Suit.DIAMONDS, Rank.NINE), faceUp = true))
            },
        )

        val result = applyMove(state, Move.TableauToTableau(fromColumn = 0, fromIndex = 0, toColumn = 1))

        assertEquals(emptyList<TableauCard>(), result.tableau[0])
        assertEquals(
            listOf(Card(Suit.DIAMONDS, Rank.NINE), bottom, middle, top),
            result.tableau[1].map { it.card },
        )
    }

    @Test
    fun `draw moves the stock top to the waste top`() {
        val state = dealGame(seed = 1L, versions = versions)
        val expectedCard = state.stock.first()

        val result = applyMove(state, Move.Draw)

        assertEquals(expectedCard, result.waste.first())
        assertEquals(state.stock.drop(1), result.stock)
    }

    @Test
    fun `recycling restores the original stock draw order for unlimited passes`() {
        var state = dealGame(seed = 7L, versions = versions)
        val originalStockOrder = state.stock

        repeat(originalStockOrder.size) { state = applyMove(state, Move.Draw) }
        assertTrue(state.stock.isEmpty())

        state = applyMove(state, Move.Recycle)
        assertEquals(originalStockOrder, state.stock)
        assertTrue(state.waste.isEmpty())

        // A second full pass must draw in the exact same order again.
        val secondPassDrawOrder = mutableListOf<Card>()
        repeat(originalStockOrder.size) {
            val card = state.stock.first()
            state = applyMove(state, Move.Draw)
            secondPassDrawOrder.add(card)
        }
        assertEquals(originalStockOrder, secondPassDrawOrder)
    }

    @Test
    fun `placing all 52 cards on foundations wins the game`() {
        var almostWon = GameStateFixtures.emptyBoard(versions = versions)
        for (suit in Suit.entries) {
            almostWon = almostWon.withFoundation(suit, if (suit == Suit.SPADES) Rank.QUEEN.value else Rank.KING.value)
        }
        almostWon = almostWon.withTableauColumn(0, listOf(TableauCard(Card(Suit.SPADES, Rank.KING), faceUp = true)))

        val result = applyMove(almostWon, Move.TableauToFoundation(fromColumn = 0))

        assertEquals(GameStatus.WON, result.status)
    }

    @Test
    fun `withdrawing a foundation card returns it face-up to the tableau`() {
        val state = GameStateFixtures.emptyBoard(versions = versions)
            .withFoundation(Suit.HEARTS, Rank.TWO.value)

        val result = applyMove(state, Move.FoundationToTableau(Suit.HEARTS, toColumn = 0))

        assertEquals(listOf(TableauCard(Card(Suit.HEARTS, Rank.TWO), faceUp = true)), result.tableau[0])
        assertEquals(Rank.ACE.value, result.foundations.getValue(Suit.HEARTS))
    }

    @Test
    fun `random legal-move sequences never violate board invariants`() {
        for (seed in 0 until 25) {
            var state = dealGame(seed = seed.toLong(), versions = versions)
            val random = Random(seed)
            assertNoViolations(state, seed, 0)

            repeat(200) { step ->
                val moves = legalMoves(state)
                if (moves.isEmpty()) return@repeat
                state = applyMove(state, moves[random.nextInt(moves.size)])
                assertNoViolations(state, seed, step + 1)
                if (state.status == GameStatus.WON) return@repeat
            }
        }
    }

    private fun assertNoViolations(state: org.finiteplay.klondike.board.GameState, seed: Int, step: Int) {
        val violations = findInvariantViolations(state)
        assertTrue("seed=$seed step=$step violations=$violations", violations.isEmpty())
    }
}
