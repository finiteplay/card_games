package org.finiteplay.klondike

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.solver.HintEngine
import org.finiteplay.klondike.solver.search.SearchOrdering
import org.finiteplay.klondike.solver.search.Solver
import org.finiteplay.klondike.solver.search.SolverLimits
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "HintPerf"

/**
 * On-device measurement scaffold, not a gate: prints real search throughput to logcat
 * so the interactive budget's wall-clock assumptions rest on this device class, not on
 * desktop numbers. Kept cheap to re-run after solver changes.
 */
@RunWith(AndroidJUnit4::class)
class HintThroughputTest {

    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun measure() {
        // Warm the JIT on a small board before measuring anything.
        Solver.solve(
            dealGame(2L, versions),
            SolverLimits(maxNodes = 20_000L, maxDurationMs = 30_000L),
            includeFoundationWithdrawal = true,
            ordering = SearchOrdering(lowerBoundWeight = 10),
        )

        // The engine's real path: portfolio on its dedicated thread, measured on the
        // catalog's cheapest, typical, and two most expensive fresh boards.
        for (seed in listOf(2L, 7L, 162L, 116L)) {
            val outcome = HintEngine().hint(
                dealGame(seed, versions),
                SolverLimits(maxNodes = 340_000L, maxDurationMs = 30_000L),
            )
            Log.i(TAG, "portfolio seed=$seed ${outcome::class.simpleName} nodes=${outcome.nodes} ms=${outcome.elapsedMs}")
        }
    }
}
