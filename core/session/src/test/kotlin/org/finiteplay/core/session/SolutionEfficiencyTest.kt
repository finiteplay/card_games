package org.finiteplay.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private data class Win(val moves: Int, val solution: Int)

private fun efficiency(vararg wins: Win) =
    computeSolutionEfficiency(wins.toList(), { it.moves }, { it.solution })

class SolutionEfficiencyTest {
    @Test
    fun `ratio is a rounded percentage of the solution`() {
        assertEquals(100L, solutionRatioPercent(80, 80))
        assertEquals(250L, solutionRatioPercent(200, 80))
        assertEquals(129L, solutionRatioPercent(112, 87))
    }

    @Test
    fun `wins on deals without a solution are ignored`() {
        assertNull(efficiency(Win(50, 0)))
        assertEquals(1, efficiency(Win(50, 0), Win(100, 50))!!.ratioDistribution.sampleSize)
    }

    @Test
    fun `average and matched-or-beaten count only compared wins`() {
        val result = efficiency(Win(90, 100), Win(100, 100), Win(150, 100), Win(10, 0))!!
        assertEquals(3, result.ratioDistribution.sampleSize)
        assertEquals(113L, result.averagePercent)
        assertEquals(2, result.matchedOrBeaten)
        assertEquals(90L, result.ratioDistribution.min)
    }
}
