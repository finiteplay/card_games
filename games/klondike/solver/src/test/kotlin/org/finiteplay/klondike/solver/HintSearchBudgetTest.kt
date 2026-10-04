package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.solver.search.SolverLimits
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mirrors the app's `hintSolverLimits` (`GameViewModel.kt`) at the player-facing "Hint timeout"
 * setting's default (`core:ui`'s `HintTimeout.DEFAULT`, 5 s): the two must move together, and
 * this test is the evidence that a candidate budget actually clears the whole catalog at that
 * default rather than only at the longest option a player could pick.
 */
private val INTERACTIVE_LIMITS = SolverLimits(maxNodes = 340_000L, maxDurationMs = 5_000L)

/**
 * The interactive hint search's fitness gate: from the raw fresh board of **every**
 * deal in the interim solvable catalog (`InterimSolvableSeeds.kt` in `:app` — kept in
 * lockstep by hand, both lists being frozen until D1b replaces them wholesale), a
 * cold [HintEngine] must resolve to [HintOutcome.Guidance] within the app's
 * interactive budget. The fresh board is each deal's worst case — the deepest search
 * this game will ever ask for — so mid-game requests fit a fortiori.
 *
 * Node counts are exactly reproducible for a fixed search (expansion order has no
 * timing dependence), so a failure here means the pipeline in [HintEngine] no longer
 * covers the catalog — repair the pipeline or the budget, don't loosen the gate.
 */
class HintSearchBudgetTest {

    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    private val catalogSeeds = listOf(
        2L, 3L, 5L, 7L, 8L, 11L, 12L, 14L, 15L, 16L, 18L, 20L, 22L, 24L, 29L, 34L, 37L, 40L, 44L, 46L,
        47L, 48L, 49L, 51L, 52L, 53L, 54L, 55L, 58L, 59L, 60L, 63L, 64L, 68L, 70L, 72L, 73L, 74L, 75L, 78L,
        79L, 81L, 83L, 84L, 86L, 87L, 89L, 91L, 95L, 96L, 98L, 99L, 100L, 101L, 102L, 103L, 105L, 109L, 111L, 112L,
        113L, 114L, 116L, 117L, 118L, 120L, 121L, 122L, 125L, 130L, 131L, 132L, 138L, 141L, 142L, 146L, 147L, 148L,
        149L, 151L, 154L, 155L, 158L, 160L, 162L, 164L, 169L, 170L, 171L, 172L, 173L, 177L, 179L, 181L, 186L, 188L,
        190L, 191L, 192L, 193L,
    )

    @Test
    fun `every catalog deal resolves to guidance within the interactive budget from its fresh board`() {
        val failures = mutableListOf<String>()
        for (seed in catalogSeeds) {
            val state = dealGame(seed, versions)
            val outcome = HintEngine().hint(state, INTERACTIVE_LIMITS)
            if (outcome !is HintOutcome.Guidance) {
                failures += "seed $seed -> ${outcome::class.simpleName} (${outcome.nodes} nodes)"
            }
        }
        assertEquals("deals whose fresh-board hint failed: $failures", emptyList<String>(), failures)
    }
}
