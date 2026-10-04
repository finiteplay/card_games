package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withStock
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStateFixtures.withWaste
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.isLegal
import org.junit.Assert.assertEquals
import org.junit.Test

class CertificateReorderingTest {

    /** Red six can move onto the black seven at any time; the stock draw is unrelated. */
    private fun boardWithIndependentTableauMove() = GameStateFixtures.emptyBoard(seed = 1L)
        .withTableauColumn(0, listOf(faceUp(Card(Suit.HEARTS, Rank.SIX))))
        .withTableauColumn(1, listOf(faceUp(Card(Suit.SPADES, Rank.SEVEN))))
        .withStock(listOf(Card(Suit.CLUBS, Rank.KING), Card(Suit.DIAMONDS, Rank.KING)))

    private fun replay(start: GameState, moves: List<Move>): GameState {
        var state = start
        for (move in moves) {
            check(isLegal(state, move)) { "reordered line became illegal at $move" }
            state = applyMove(state, move)
        }
        return state
    }

    @Test
    fun `a tableau move crosses unrelated draws to the front of the line`() {
        val start = boardWithIndependentTableauMove()
        val found = listOf(Move.Draw, Move.Draw, Move.TableauToTableau(0, 0, 1))

        val reordered = reorderCertificateForFollowing(start, found)

        assertEquals(listOf(Move.TableauToTableau(0, 0, 1), Move.Draw, Move.Draw), reordered)
        assertEquals(replay(start, found), replay(start, reordered))
    }

    @Test
    fun `a waste play never crosses the draw that exposes its card`() {
        // The ace only reaches the waste top via the draw; playing it earlier would
        // play a different card, so the pair must not be treated as commuting.
        val start = GameStateFixtures.emptyBoard(seed = 2L)
            .withWaste(listOf(Card(Suit.HEARTS, Rank.KING)))
            .withStock(listOf(Card(Suit.CLUBS, Rank.ACE)))
        val found = listOf(Move.Draw, Move.WasteToFoundation)

        val reordered = reorderCertificateForFollowing(start, found)

        assertEquals(found, reordered)
    }

    @Test
    fun `a waste play stays pinned through a recycle-and-redraw dig`() {
        // The line buries the five under the queen, recycles, and redraws to reach
        // it. Every draw and the recycle change which card is the waste top, so the
        // waste play must never cross any of them — this pins that the reordering's
        // commute check rejects data-dependent swaps rather than assuming pile types.
        val start = GameStateFixtures.emptyBoard(seed = 3L)
            .withTableauColumn(2, listOf(faceUp(Card(Suit.SPADES, Rank.SIX))))
            .withWaste(listOf(Card(Suit.HEARTS, Rank.FIVE)))
            .withStock(listOf(Card(Suit.DIAMONDS, Rank.QUEEN)))
        val found = listOf(Move.Draw, Move.Recycle, Move.Draw, Move.WasteToTableau(2))

        val reordered = reorderCertificateForFollowing(start, found)

        assertEquals(replay(start, found), replay(start, reordered))
        assertEquals(found, reordered)
    }

    @Test
    fun `reordering preserves the moves, length, and final board of a mixed line`() {
        val start = boardWithIndependentTableauMove()
        val found = listOf(
            Move.Draw,
            Move.TableauToTableau(0, 0, 1),
            Move.Draw,
        )

        val reordered = reorderCertificateForFollowing(start, found)

        assertEquals(found.size, reordered.size)
        assertEquals(found.groupingBy { it }.eachCount(), reordered.groupingBy { it }.eachCount())
        assertEquals(replay(start, found), replay(start, reordered))
        assertEquals(Move.TableauToTableau(0, 0, 1), reordered.first())
    }

    @Test
    fun `an empty or single-move line is returned unchanged`() {
        val start = boardWithIndependentTableauMove()

        assertEquals(emptyList<Move>(), reorderCertificateForFollowing(start, emptyList()))
        assertEquals(listOf(Move.Draw), reorderCertificateForFollowing(start, listOf(Move.Draw)))
    }
}
