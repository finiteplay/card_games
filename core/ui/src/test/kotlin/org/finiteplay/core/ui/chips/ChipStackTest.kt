package org.finiteplay.core.ui.chips

import org.junit.Assert.assertEquals
import org.junit.Test

class ChipStackTest {
    @Test
    fun `chips break an amount into the fewest of the largest denominations`() {
        assertEquals(listOf(ChipGroup(10, 1)), chipStackOf(10))
        assertEquals(listOf(ChipGroup(100, 1), ChipGroup(10, 3)), chipStackOf(130))
        assertEquals(listOf(ChipGroup(500, 1)), chipStackOf(500))
        assertEquals(listOf(ChipGroup(1000, 1)), chipStackOf(1000))
        assertEquals(listOf(ChipGroup(50, 1), ChipGroup(10, 2)), chipStackOf(70))
    }

    @Test
    fun `chips always add back up to the amount, in a stack small enough to draw`() {
        for (amount in 10..2_000 step 10) {
            val groups = chipStackOf(amount)
            assertEquals(amount, groups.sumOf { it.denomination * it.count })
            assertEquals(true, groups.sumOf { it.count } <= 12)
        }
    }

    @Test
    fun `an odd amount keeps its remainder in small chips`() {
        assertEquals(listOf(ChipGroup(10, 1), ChipGroup(5, 1)), chipStackOf(15))
        assertEquals(listOf(ChipGroup(10, 2), ChipGroup(5, 1)), chipStackOf(25))
    }

    @Test
    fun `no amount is no chips`() {
        assertEquals(emptyList<ChipGroup>(), chipStackOf(0))
        assertEquals(emptyList<ChipGroup>(), chipStackOf(-10))
    }
}
