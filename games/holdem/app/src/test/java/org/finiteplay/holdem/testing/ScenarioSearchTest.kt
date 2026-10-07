package org.finiteplay.holdem.testing

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Finds the seeds `Scenarios` commits. Off by default; run with `HOLDEM_SEARCH=1` to rewrite
 * `build/scenario-seeds.txt` with the first seed from 1 up that satisfies each scenario.
 */
class ScenarioSearchTest {
    @Test
    fun `search the tournament seeds for each scenario`() {
        assumeTrue(System.getenv("HOLDEM_SEARCH") == "1")
        val out = StringBuilder()
        for (scenario in Scenarios.all) {
            val found = (1L..400_000L).firstOrNull { seed -> scenario.copy(seed = seed).let { it.holds(it.run()) } }
            out.appendLine("${scenario.name} = $found")
        }
        File("build/scenario-seeds.txt").writeText(out.toString())
    }
}
