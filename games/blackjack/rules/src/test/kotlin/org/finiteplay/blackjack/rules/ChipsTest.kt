package org.finiteplay.blackjack.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipsTest {
    @Test
    fun `the largest bet is the lower of 500 and what the bankroll covers in whole steps`() {
        assertEquals(500, Chips.maxBetFor(1_000))
        assertEquals(500, Chips.maxBetFor(500))
        assertEquals(490, Chips.maxBetFor(495))
        assertEquals(30, Chips.maxBetFor(37))
        assertEquals(0, Chips.maxBetFor(9))
    }

    @Test
    fun `below the table minimum a round cannot be dealt and a reset is offered`() {
        assertTrue(Chips.needsReset(9))
        assertFalse(Chips.needsReset(10))
    }

    @Test
    fun `settlement lowers a selected bet the bankroll no longer covers`() {
        assertEquals(500, Chips.betAfterSettlement(500, 2_000))
        assertEquals(200, Chips.betAfterSettlement(500, 200))
        assertEquals(40, Chips.betAfterSettlement(100, 47))
        assertEquals("below the minimum the bet is the minimum, for the reset", 10, Chips.betAfterSettlement(100, 5))
    }

    @Test
    fun `the stepper moves by ten and never leaves what the bankroll covers`() {
        assertEquals(110, Chips.steppedBet(100, +1, 1_000))
        assertEquals(90, Chips.steppedBet(100, -1, 1_000))
        assertEquals("not below the minimum", 10, Chips.steppedBet(10, -1, 1_000))
        assertEquals("not above the maximum", 500, Chips.steppedBet(500, +1, 1_000))
        assertEquals("nor above the bankroll", 50, Chips.steppedBet(50, +1, 55))
    }

    @Test
    fun `a bet outside the offered range is refused at deal`() {
        val refused = listOf(5, 15, 510, 1_000).count { bet -> runCatching { startRound(1L, bet, 1_000) }.isFailure }
        assertEquals(4, refused)
    }
}
