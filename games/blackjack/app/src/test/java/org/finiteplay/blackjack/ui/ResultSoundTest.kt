package org.finiteplay.blackjack.ui

import org.finiteplay.blackjack.rules.Chips
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.HandResult
import org.finiteplay.blackjack.rules.Settlement
import org.finiteplay.core.ui.sound.SoundEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultSoundTest {
    private fun settlement(outcome: HandOutcome, total: Int) = Settlement(
        hands = listOf(HandResult(outcome, stake = 20, delta = total)),
        insuranceDelta = 0,
        dealerBlackjack = false,
    )

    @Test
    fun `ordinary win uses shared win sound`() {
        assertEquals(SoundEffect.WIN, resultSoundEffect(settlement(HandOutcome.WIN, 20), bankroll = 1_020))
    }

    @Test
    fun `natural blackjack uses its own celebration`() {
        assertEquals(SoundEffect.NATURAL_WIN, resultSoundEffect(settlement(HandOutcome.BLACKJACK, 30), bankroll = 1_030))
    }

    @Test
    fun `negative settlement uses loss sound`() {
        assertEquals(SoundEffect.ROUND_LOSS, resultSoundEffect(settlement(HandOutcome.LOSS, -20), bankroll = 980))
    }

    @Test
    fun `unplayable bankroll uses stronger all-money-lost sound`() {
        assertEquals(
            SoundEffect.GAME_OVER,
            resultSoundEffect(settlement(HandOutcome.LOSS, -10), bankroll = Chips.MIN_BET - 5),
        )
    }

    @Test
    fun `push remains silent`() {
        assertNull(resultSoundEffect(settlement(HandOutcome.PUSH, 0), bankroll = 1_000))
    }

    @Test
    fun `bust sound only fires when the bust count increases`() {
        assertTrue(shouldPlayBustSound(previousBusts = 0, currentBusts = 1))
        assertFalse(shouldPlayBustSound(previousBusts = 1, currentBusts = 1))
        assertFalse(shouldPlayBustSound(previousBusts = 1, currentBusts = 0))
    }
}
