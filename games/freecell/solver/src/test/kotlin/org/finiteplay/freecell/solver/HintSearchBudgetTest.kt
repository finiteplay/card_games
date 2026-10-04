package org.finiteplay.freecell.solver

import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.dealGame
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The interactive hint search's fitness gate (`docs/games/freecell/EXECUTION_PLAN.md` F6): from
 * the raw fresh board of **every** deal in the real, committed catalog, a cold [hint] call must
 * resolve to [HintOutcome.Guidance] within [HINT_SOLVER_LIMITS]. The fresh board is each deal's
 * worst case for this search — nothing about a board reached by real play is harder to search from
 * a shallower position than its own start — so mid-game requests fit a fortiori.
 *
 * Reads the actual shipped asset rather than a pinned seed list: unlike Klondike's own version of
 * this test (frozen against an interim catalog from before its real one existed), FreeCell's
 * catalog was real from F5 onward, so there is no separate interim list to fall out of sync with
 * it. A regenerated catalog is picked up automatically the next time this runs.
 */
class HintSearchBudgetTest {
    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    private fun realCatalogSeeds(): List<Long> {
        val bytes = File("../app/src/main/assets/catalogs/freecell.catalog").readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val recordCount = buffer.getInt(28)
        return (0 until recordCount).map { buffer.getLong(64 + it * 8) }
    }

    @Test
    fun `every catalog deal resolves to guidance within the interactive budget from its fresh board`() {
        val failures = mutableListOf<String>()
        for (seed in realCatalogSeeds()) {
            val state = dealGame(seed, versions)
            // A fresh engine per seed: each is its own new game, with no cached line from any
            // other deal to (wrongly) carry forward.
            val outcome = HintEngine().hint(state, HINT_SOLVER_LIMITS)
            if (outcome !is HintOutcome.Guidance) {
                failures += "seed $seed -> ${outcome::class.simpleName} (${outcome.nodes} nodes)"
            }
        }
        assertEquals("deals whose fresh-board hint failed: $failures", emptyList<String>(), failures)
    }
}
