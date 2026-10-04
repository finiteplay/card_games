package org.finiteplay.klondike.tools.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forgiveness measure the Trivial and Easy levels are cut by: not "is there a way through"
 * but "how many of the ways through win".
 */
class StrategyPathWinRateTest {

    @Test
    fun `a rate is a share of the games played, and repeats exactly`() {
        val once = strategyPathWinRate(seed = 7688248L, ruleset = Ruleset.EASY)
        val twice = strategyPathWinRate(seed = 7688248L, ruleset = Ruleset.EASY)

        assertEquals(once, twice, 0f)
        assertTrue("rate $once outside 0..1", once in 0f..1f)
    }

    @Test
    fun `a deal no ruleset wins is never forgiving to that ruleset`() {
        // An Insane deal: neither search resolved it, so no run of trivial moves reaches a win
        // and every sampled game must dead-end.
        assertEquals(0f, strategyPathWinRate(seed = 44371L, ruleset = Ruleset.TRIVIAL), 0f)
    }

    @Test
    fun `winnable-by-some-order and winnable-by-most-orders are different questions`() {
        // The distinction the levels are being recut on. Across the deals a level ships, the
        // tree search says "won" for all of them by construction, while the path rate spreads
        // out — if it did not, it would be measuring the same thing and buying nothing.
        val shipped = readShippedSeeds(CatalogLevel.EASY).take(24)
        val rates = shipped.map { strategyPathWinRate(it, Ruleset.EASY) }

        assertTrue("no shipped Easy deal to measure", rates.isNotEmpty())
        assertTrue(
            "every sampled deal returned the same rate ${rates.first()}, so the measure is not discriminating",
            rates.toSet().size > 1,
        )
    }

    private fun readShippedSeeds(level: CatalogLevel): List<Long> {
        val name = level.name.lowercase().replaceFirstChar { it.uppercase() }
        val dir = java.io.File("../../games/klondike/app/src/main/java/org/finiteplay/klondike/deal")
            .takeIf { it.isDirectory }
            ?: java.io.File("games/klondike/app/src/main/java/org/finiteplay/klondike/deal")
        return java.io.File(dir, "InterimSeeds${name}A.kt").readText()
            .let { Regex("""^\s+(\d+)L,""", RegexOption.MULTILINE).findAll(it).map { m -> m.groupValues[1].toLong() } }
            .toList()
    }
}
