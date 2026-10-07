package org.finiteplay.holdem.testing

import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The committed seeds still produce what the instrumented gate expects of them. */
class ScenarioCatalogTest {
    @Test
    fun `every committed scenario still occurs headless`() {
        for (scenario in Scenarios.all) {
            assertTrue("${scenario.name} seed ${scenario.seed}", scenario.holds(scenario.run()))
        }
    }

    @Test
    fun `chips are conserved at the end of every hand of every scenario`() {
        for (scenario in Scenarios.all) {
            for (hand in scenario.run().hands) {
                assertEquals(scenario.name, Contract.TOTAL_CHIPS, hand.stacks.sum())
            }
        }
    }

    @Test
    fun `the scenarios end where they say`() {
        assertEquals(Phase.TOURNAMENT_OVER, Scenarios.tournamentWon.run().last.phase)
        assertTrue(Scenarios.eliminated.run().last.playerOut)
        assertEquals(1, Scenarios.fold.run().hands.size)
    }
}
