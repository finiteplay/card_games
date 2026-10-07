package org.finiteplay.blackjack.ui

import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.HandResult
import org.finiteplay.blackjack.rules.Settlement
import org.junit.Assert.assertEquals
import org.junit.Test

class CelebrationTest {
    private fun round(vararg hands: HandResult, insurance: Int = 0) =
        Settlement(hands = hands.toList(), insuranceDelta = insurance, dealerBlackjack = false)

    private fun hand(outcome: HandOutcome, delta: Int) = HandResult(outcome, stake = 100, delta = delta)

    @Test
    fun `a winning round is a win, and a natural makes it a blackjack`() {
        assertEquals(ResultKind.WIN, resultKindOf(round(hand(HandOutcome.WIN, 100))))
        assertEquals(ResultKind.BLACKJACK, resultKindOf(round(hand(HandOutcome.BLACKJACK, 150))))
    }

    @Test
    fun `a losing round is a bust only when every hand busted`() {
        assertEquals(ResultKind.BUST, resultKindOf(round(hand(HandOutcome.BUST, -100))))
        assertEquals(ResultKind.LOSE, resultKindOf(round(hand(HandOutcome.LOSS, -100))))
        // Split: one hand busted, the other simply lost to the dealer's higher total.
        assertEquals(ResultKind.LOSE, resultKindOf(round(hand(HandOutcome.BUST, -100), hand(HandOutcome.LOSS, -100))))
    }

    @Test
    fun `an even round is a push only when every hand pushed`() {
        assertEquals(ResultKind.PUSH, resultKindOf(round(hand(HandOutcome.PUSH, 0))))
        assertEquals(ResultKind.EVEN, resultKindOf(round(hand(HandOutcome.WIN, 100), hand(HandOutcome.LOSS, -100))))
    }

    @Test
    fun `the banner follows the round's net, not one hand`() {
        // A won hand and a bigger lost hand: the player is down, so the banner says so.
        assertEquals(ResultKind.LOSE, resultKindOf(round(hand(HandOutcome.WIN, 100), hand(HandOutcome.LOSS, -200))))
        // Insurance can turn a lost hand into an even round.
        assertEquals(ResultKind.EVEN, resultKindOf(round(hand(HandOutcome.LOSS, -100), insurance = 100)))
    }
}
