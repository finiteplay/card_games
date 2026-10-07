package org.finiteplay.holdem.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Side pots from generated all-in configurations (`EXECUTION_PLAN.md` H2 gate). */
class SidePotTest {
    /**
     * The expected payouts, computed by peeling: repeatedly take the smallest remaining stake from
     * everyone still holding one, and award that layer to the best of those who paid it. With no
     * folds this is the same pot structure built another way. Odd chips go in seat order from the
     * button's left.
     */
    private fun peeledPayouts(contributions: List<Int>, values: List<Int>, button: Int): List<Int> {
        val remaining = contributions.toMutableList()
        val payouts = MutableList(contributions.size) { 0 }
        while (remaining.any { it > 0 }) {
            val layer = remaining.filter { it > 0 }.min()
            val holders = remaining.indices.filter { remaining[it] > 0 }
            val amount = layer * holders.size
            val best = holders.maxOf { values[it] }
            val winners = holders.filter { values[it] == best }
            val order = (1..contributions.size).map { (button + it) % contributions.size }.filter { it in winners }
            for ((i, seat) in order.withIndex()) payouts[seat] += amount / order.size + if (i < amount % order.size) 1 else 0
            for (seat in holders) remaining[seat] -= layer
        }
        return payouts
    }

    private fun payoutsOf(awards: List<PotAward>, seats: Int): List<Int> {
        val payouts = MutableList(seats) { 0 }
        for (award in awards) for ((i, seat) in award.winners.withIndex()) payouts[seat] += award.shares[i]
        return payouts
    }

    private fun valueAssignments(n: Int, random: Random): List<List<Int>> = listOf(
        List(n) { 5 },
        List(n) { it },
        List(n) { n - it },
        List(n) { it / 2 },
        List(n) { if (it % 2 == 0) 3 else 1 },
    ) + List(6) { List(n) { random.nextInt(3) } }

    @Test
    fun `every all-in configuration pays every chip, to eligible seats only, odd chips to the right seats`() {
        val random = Random(1234)
        var configurations = 0
        var oddChipPots = 0
        for (n in 2..6) {
            val stakes = listOf(105, 250, 401)
            val total = Math.pow(stakes.size.toDouble(), n.toDouble()).toInt()
            for (code in 0 until total) {
                var rest = code
                val contributions = List(n) { stakes[rest % stakes.size].also { rest /= stakes.size } }
                for (values in valueAssignments(n, random)) for (button in 0 until n) {
                    configurations++
                    val pots = sidePots(contributions, List(n) { true })
                    assertEquals(contributions.sum(), pots.sumOf { it.amount })
                    val awards = awardPots(pots, values.withIndex().associate { it.index to HandValue(it.value) }, button)
                    val payouts = payoutsOf(awards, n)
                    assertEquals("every chip is awarded: $contributions $values", contributions.sum(), payouts.sum())
                    for (award in awards) {
                        assertTrue(award.winners.all { it in award.pot.eligible })
                        assertEquals(award.pot.amount, award.shares.sum())
                        val best = award.pot.eligible.maxOf { values[it] }
                        assertTrue(award.winners.all { values[it] == best })
                        assertEquals(award.pot.eligible.filter { values[it] == best }.toSet(), award.winners.toSet())
                        if (award.pot.amount % award.winners.size != 0) oddChipPots++
                    }
                    assertEquals("$contributions $values button $button", peeledPayouts(contributions, values, button), payouts)
                }
            }
        }
        assertTrue("configurations: $configurations", configurations > 10_000)
        assertTrue("pots with an odd chip: $oddChipPots", oddChipPots > 100)
    }

    @Test
    fun `a seat is eligible only for the pots it paid in full, bottom up`() {
        val pots = sidePots(listOf(100, 300, 300, 50, 300, 200), List(6) { true })
        // 50 x 6, then 50 x 5 (seat 3 is out of it), then 100 x 4, then 100 x 3.
        assertEquals(listOf(300, 250, 400, 300), pots.map { it.amount })
        assertEquals(listOf(listOf(0, 1, 2, 3, 4, 5), listOf(0, 1, 2, 4, 5), listOf(1, 2, 4, 5), listOf(1, 2, 4)), pots.map { it.eligible })
    }

    @Test
    fun `equal stacks make one pot`() {
        val pots = sidePots(List(4) { 500 }, List(4) { true })
        assertEquals(listOf(Pot(2_000, listOf(0, 1, 2, 3))), pots)
    }

    @Test
    fun `a folded seat's chips stay in the pots they fall in and it is eligible for none`() {
        val pots = sidePots(listOf(100, 300, 300, 50), listOf(true, true, true, false))
        assertEquals(listOf(Pot(350, listOf(0, 1, 2)), Pot(400, listOf(1, 2))), pots)
        assertEquals(750, pots.sumOf { it.amount })
        val random = Random(99)
        repeat(2_000) {
            val n = 2 + random.nextInt(5)
            val contributions = List(n) { 10 * (1 + random.nextInt(30)) }
            val live = List(n) { random.nextInt(3) != 0 }
            if (live.none { it }) return@repeat
            val top = contributions.indices.filter { live[it] }.maxOf { contributions[it] }
            val capped = contributions.map { it.coerceAtMost(top) }
            val result = sidePots(capped, live)
            assertEquals(capped.sum(), result.sumOf { it.amount })
            for (pot in result) assertTrue(pot.eligible.all { live[it] })
        }
    }

    @Test
    fun `odd chips go one at a time to the tied seats in seat order from the button's left`() {
        val values = mapOf(1 to HandValue(9), 3 to HandValue(9), 5 to HandValue(9), 0 to HandValue(1))
        // 7 chips three ways, button on seat 4: the order is 5, then 1, then 3.
        val award = awardPots(listOf(Pot(7, listOf(0, 1, 3, 5))), values, button = 4).single()
        assertEquals(listOf(5, 1, 3), award.winners)
        assertEquals(listOf(3, 2, 2), award.shares)
        // 8 chips three ways: two odd chips, to the first two.
        val eight = awardPots(listOf(Pot(8, listOf(1, 3, 5))), values, button = 4).single()
        assertEquals(listOf(3, 3, 2), eight.shares)
        // The button's own seat is last in line.
        val own = awardPots(listOf(Pot(5, listOf(1, 4))), mapOf(1 to HandValue(2), 4 to HandValue(2)), button = 4).single()
        assertEquals(listOf(1, 4), own.winners)
        assertEquals(listOf(3, 2), own.shares)
    }

    @Test
    fun `places for knockouts in one hand`() {
        val stacks = listOf(500, 500, 200, 1000, 0, 0)
        // Six were in, two survive: four knocked out take places 3..6.
        val places = placesForKnockouts(listOf(0, 1, 2, 3), stacks, aliveBefore = 6)
        assertEquals(3, places[3])
        assertEquals(4, places[0])
        assertEquals(4, places[1])
        assertEquals(6, places[2])
        assertEquals(mapOf(2 to 3), placesForKnockouts(listOf(2), stacks, aliveBefore = 3))
    }
}
