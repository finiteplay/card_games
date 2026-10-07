package org.finiteplay.holdem.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizingRowTest {
    private fun bet(pot: Int, stack: Int, bigBlind: Int = 20, min: Int = bigBlind) =
        SizingRow.of(pot, 0, min, stack, bigBlind, SizingStreet.Postflop, isRaise = false)

    private fun raise(
        pot: Int,
        toCall: Int,
        minTotal: Int,
        stack: Int,
        street: SizingStreet = SizingStreet.Postflop,
        committed: Int = 0,
        bigBlind: Int = 20,
    ) = SizingRow.of(pot, toCall, minTotal, stack, bigBlind, street, isRaise = true, committed = committed)

    @Test
    fun `a bet is half the pot or the pot, and defaults to half after the flop`() {
        val row = bet(pot = 200, stack = 1000)
        assertEquals(20, row.min)
        assertEquals(1000, row.max)
        assertEquals(100, row.halfPot)
        assertEquals(200, row.pot)
        assertEquals(100, row.defaultAmount)
    }

    @Test
    fun `presets round down to a whole chip`() {
        val row = bet(pot = 101, stack = 1000)
        assertEquals(50, row.halfPot)
        assertEquals(101, row.pot)
        assertEquals(50, row.defaultAmount)
    }

    @Test
    fun `a raise is the call plus half or all of the pot once the call is in`() {
        // 300 in the pot including a 100 bet to call: the pot with the call in is 400.
        val row = raise(pot = 300, toCall = 100, minTotal = 200, stack = 1000)
        assertEquals(200, row.min)
        assertEquals(1000, row.max)
        assertEquals(300, row.halfPot)
        assertEquals(500, row.pot)
        assertEquals(300, row.defaultAmount)
    }

    @Test
    fun `a raise's half pot rounds down when the pot with the call is odd`() {
        val row = raise(pot = 201, toCall = 50, minTotal = 100, stack = 1000)
        assertEquals(50 + 251 / 2, row.halfPot)
        assertEquals(50 + 251, row.pot)
    }

    @Test
    fun `before the flop with no raise the default is three big blinds`() {
        val row = raise(pot = 30, toCall = 20, minTotal = 40, stack = 1500, street = SizingStreet.PreflopUnraised)
        assertEquals(60, row.defaultAmount)
        assertEquals(20 + 50 / 2, row.halfPot)
        assertEquals(20 + 50, row.pot)
    }

    @Test
    fun `before the flop facing a raise the default is three times its total`() {
        val row = raise(pot = 90, toCall = 60, minTotal = 100, stack = 1500, street = SizingStreet.PreflopRaised)
        assertEquals(180, row.defaultAmount)
    }

    @Test
    fun `the big blind facing a raise counts what it already has in`() {
        val row = raise(pot = 110, toCall = 40, minTotal = 100, stack = 1480, street = SizingStreet.PreflopRaised, committed = 20)
        assertEquals(1500, row.max)
        assertEquals(180, row.defaultAmount)
        assertEquals(60 + 150 / 2, row.halfPot)
        assertEquals(60 + 150, row.pot)
    }

    @Test
    fun `a preset under the minimum is raised to it`() {
        val row = bet(pot = 30, stack = 1000)
        assertEquals(20, row.min)
        assertEquals(20, row.halfPot)
        assertEquals(30, row.pot)
        assertEquals(20, row.defaultAmount)
    }

    @Test
    fun `a preset over the stack clamps to the whole stack, which is all in`() {
        val row = bet(pot = 2000, stack = 300)
        assertEquals(300, row.max)
        assertEquals(300, row.pot)
        assertEquals(300, row.halfPot)
        assertEquals(300, row.defaultAmount)
        val raised = raise(pot = 900, toCall = 100, minTotal = 200, stack = 400)
        assertEquals(400, raised.pot)
        assertEquals(400, raised.halfPot)
        assertEquals(400, raised.defaultAmount)
    }

    @Test
    fun `a half pot that fits is left alone while the pot preset clamps`() {
        val row = bet(pot = 500, stack = 300)
        assertEquals(250, row.halfPot)
        assertEquals(300, row.pot)
    }

    @Test
    fun `a stack below the minimum raise leaves only all in`() {
        val row = raise(pot = 400, toCall = 100, minTotal = 200, stack = 150)
        assertEquals(150, row.min)
        assertEquals(150, row.max)
        assertEquals(150, row.halfPot)
        assertEquals(150, row.pot)
        assertEquals(150, row.defaultAmount)
    }

    @Test
    fun `a stack below the big blind can still bet it all`() {
        val row = bet(pot = 40, stack = 15)
        assertEquals(15, row.min)
        assertEquals(15, row.max)
        assertEquals(15, row.defaultAmount)
    }

    @Test
    fun `the preflop defaults clamp to the legal range`() {
        val unraised = raise(pot = 30, toCall = 20, minTotal = 40, stack = 50, street = SizingStreet.PreflopUnraised)
        assertEquals(50, unraised.defaultAmount)
        val raised = raise(pot = 400, toCall = 300, minTotal = 600, stack = 700, street = SizingStreet.PreflopRaised)
        assertEquals(700, raised.defaultAmount)
        val tiny = raise(pot = 30, toCall = 5, minTotal = 40, stack = 1500, street = SizingStreet.PreflopUnraised, bigBlind = 5)
        assertEquals(40, tiny.defaultAmount)
    }

    @Test
    fun `every amount lies within min and max`() {
        for (pot in listOf(0, 1, 37, 300, 5000)) for (stack in listOf(1, 40, 250, 9000)) for (call in listOf(0, 20, 120)) {
            for (street in SizingStreet.entries) {
                val row = SizingRow.of(pot, call, 40, stack, 20, street, isRaise = call > 0)
                for (amount in listOf(row.halfPot, row.pot, row.defaultAmount)) {
                    assertTrue("$amount outside ${row.min}..${row.max}", amount in row.min..row.max)
                }
            }
        }
    }
}
