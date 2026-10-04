package org.finiteplay.klondike.tools.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AStarTuningTest {

    @Test
    fun `seed split is deterministic and controlled by its frozen salt`() {
        val first = (1L..10_000L).associateWith { tuningSplit(it, salt = 0x51A7L) }
        val repeated = (1L..10_000L).associateWith { tuningSplit(it, salt = 0x51A7L) }
        val differentSalt = (1L..10_000L).associateWith { tuningSplit(it, salt = 0x51A8L) }

        assertEquals(first, repeated)
        assertNotEquals(first, differentSalt)
        assertEquals(7_000, first.values.count { it == TuningSplit.TRAIN })
        assertEquals(1_500, first.values.count { it == TuningSplit.VALIDATION })
        assertEquals(1_500, first.values.count { it == TuningSplit.TEST })
    }

    @Test
    fun `positive score prioritizes coverage then charges capped searches with PAR2`() {
        val score = scorePositiveTrials(
            listOf(
                AStarTrial(seed = 1, outcome = TrialOutcome.SOLVED, nodes = 10, certificateMoves = 80),
                AStarTrial(seed = 2, outcome = TrialOutcome.SOLVED, nodes = 70, certificateMoves = 100),
                AStarTrial(seed = 3, outcome = TrialOutcome.INCONCLUSIVE, nodes = 100, certificateMoves = 0),
            ),
            nodeCaps = listOf(25L, 50L, 100L),
        )

        assertEquals(2, score.solvedAtMaxCap)
        assertEquals(2.0 / 3.0, score.coverageAtMaxCap, 0.000_001)
        assertEquals((10.0 + 70.0 + 200.0) / 3.0, score.par2Nodes, 0.000_001)
        assertEquals((1.0 / 3.0 + 1.0 / 3.0 + 2.0 / 3.0) / 3.0, score.coverageArea, 0.000_001)
    }
}
