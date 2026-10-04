package org.finiteplay.freecell.tools.catalog

import org.finiteplay.freecell.solver.SolverLimits

/**
 * Desktop-only entry point for FreeCell's catalog build and gate. Separate from the other games'
 * own entry points on purpose — `:app` must never depend on this module, and a shared entry point
 * would mean either game's build failing could look like another's.
 *
 * `build [target] [firstSeed] [maxNodes] [maxDurationMs] [threads]`: certifies [target] deals,
 * writing the catalog, a solutions blob, and a manifest under `games/freecell/app/src/main/assets`.
 *
 * `verify`: the gate `verifyFreeCellDealCatalogs` runs — re-checks committed evidence, never solves.
 *
 * `shrink`: the one-off re-solve (`FreeCellCatalogShrink.kt`) — re-solves every already-certified
 * seed with `BestFirstSolver` looking for a much shorter line than the original DFS-derived
 * certificate, writes the shrunk certificates back to the solutions blob, and encodes the result
 * as the bit-packed `assets/solutions.bin` the app ships. Not a gate; run by hand.
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "build" -> buildFreeCellCatalog(
            target = args.getOrNull(1)?.toIntOrNull() ?: 10_000,
            firstSeed = args.getOrNull(2)?.toLongOrNull() ?: 1L,
            limits = SolverLimits(
                maxNodes = args.getOrNull(3)?.toLongOrNull() ?: SolverLimits().maxNodes,
                maxDurationMs = args.getOrNull(4)?.toLongOrNull() ?: SolverLimits().maxDurationMs,
            ),
            threads = args.getOrNull(5)?.toIntOrNull() ?: 12,
        )
        "verify" -> {
            val passed = verifyFreeCellCatalog()
            if (!passed) kotlin.system.exitProcess(1)
        }
        "shrink" -> shrinkAndEncodeFreeCellSolutions(
            threads = args.getOrNull(1)?.toIntOrNull() ?: Runtime.getRuntime().availableProcessors(),
        )
        else -> {
            println("usage: build [target] [firstSeed] [maxNodes] [maxDurationMs] [threads] | verify | shrink [threads]")
            kotlin.system.exitProcess(1)
        }
    }
}
