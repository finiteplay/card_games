package org.finiteplay.holdem.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class ContractTest {
    @Test
    fun `the table's chips are the six starting stacks`() {
        assertEquals(9_000, Contract.TOTAL_CHIPS)
    }
}
