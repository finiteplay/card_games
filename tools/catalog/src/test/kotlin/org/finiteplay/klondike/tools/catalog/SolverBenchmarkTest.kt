package org.finiteplay.klondike.tools.catalog

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class SolverBenchmarkTest {

    @Test
    fun `benchmark uses the best frozen-training ordering`() {
        assertEquals(43, BENCHMARK_ASTAR_ORDERING.lowerBoundWeight)
        assertEquals(94, BENCHMARK_ASTAR_ORDERING.downCardWeight)
        assertEquals(48, BENCHMARK_ASTAR_ORDERING.aceBurialWeight)
        assertEquals(51, BENCHMARK_ASTAR_ORDERING.neededCardDepthWeight)
    }

    @Test
    fun `resume skips completed seeds regardless of CSV completion order`() {
        assertEquals(
            listOf(11L, 13L),
            pendingBenchmarkSeeds(
                seedCount = 5,
                firstSeed = 10L,
                completedSeeds = setOf(14L, 10L, 12L),
            ),
        )
    }

    @Test
    fun `resume keeps complete rows and discards an interrupted trailing row`() {
        val file = Files.createTempFile("solver-benchmark", ".csv").toFile()
        try {
            file.writeText(
                "$CSV_HEADER\n${benchmarkRow(5)}\n${benchmarkRow(7)}\n8,partial",
            )

            val resume = readBenchmarkResumeState(file)

            assertEquals(setOf(5L, 7L), resume.completedSeeds)
            assertEquals(true, resume.hasInterruptedTrailingRow)
            discardInterruptedTrailingRow(file, resume)
            assertEquals(
                "$CSV_HEADER\n${benchmarkRow(5)}\n${benchmarkRow(7)}\n",
                file.readText(),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `seed 78 records the trained-ordering regression alongside its strategy wins`() {
        val row = CSV_HEADER.split(',').zip(
            measureSeed(
                seed = 78L,
                dfsNodes = 300_000,
                astarNodes = 200_000,
                astarMs = 8_000,
                bump = {},
            ).split(','),
        ).toMap()

        for (ruleset in Ruleset.entries) {
            assertEquals("${ruleset.name} strategy search", "1", row["${ruleset.name.lowercase()}_win"])
        }
        assertEquals("", row["line_mismatch"])
        assertEquals("INCONCLUSIVE", row["astar_outcome"])
        assertEquals("INCONCLUSIVE", row["astar_nowithdraw_outcome"])
    }

    private fun benchmarkRow(seed: Long): String =
        List(CSV_HEADER.count { it == ',' } + 1) { index -> if (index == 0) seed.toString() else "" }
            .joinToString(",")
}
