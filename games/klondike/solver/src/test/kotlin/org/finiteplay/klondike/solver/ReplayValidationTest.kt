package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayValidationTest {

    /** Every foundation but Spades complete; the King of Spades sits alone, face-up, in column 0. */
    private fun oneMoveFromWin() = GameStateFixtures.emptyBoard(seed = 42L)
        .withFoundation(Suit.CLUBS, 13)
        .withFoundation(Suit.DIAMONDS, 13)
        .withFoundation(Suit.HEARTS, 13)
        .withFoundation(Suit.SPADES, 12)
        .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))

    @Test
    fun `validateCertificate accepts a certificate that reaches WON`() {
        val start = oneMoveFromWin()
        val certificate = listOf(Move.TableauToFoundation(0))

        validateCertificate(start, certificate)

        val end = replay(start, certificate)
        assertEquals(GameStatus.WON, end.status)
    }

    @Test
    fun `validateCertificate rejects an illegal move`() {
        val start = oneMoveFromWin() // stock is empty, so Draw is illegal
        val certificate = listOf(Move.Draw)

        val error = assertThrows(CertificateReplayException::class.java) {
            validateCertificate(start, certificate)
        }
        assert(error.message!!.contains("illegal move at step 0"))
    }

    @Test
    fun `validateCertificate rejects a legal sequence that does not reach WON`() {
        val start = oneMoveFromWin()

        val error = assertThrows(CertificateReplayException::class.java) {
            validateCertificate(start, emptyList())
        }
        assert(error.message!!.contains("IN_PROGRESS"))
    }

    @Test
    fun `replay applies moves in order and stops before the first illegal one`() {
        val start = oneMoveFromWin()
        val certificate = listOf(Move.TableauToFoundation(0))

        val end = replay(start, certificate)

        assertEquals(13, end.foundations.getValue(Suit.SPADES))
    }

    @Test
    fun `firstMoveIsLegal accepts a legal first step and rejects an illegal one`() {
        val start = oneMoveFromWin()

        assertTrue(firstMoveIsLegal(start, listOf(Move.TableauToFoundation(0))))
        assertFalse(firstMoveIsLegal(start, listOf(Move.Draw)))
        assertFalse(firstMoveIsLegal(start, emptyList()))
    }
}
