package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.solver.CertificateReplayException
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.SolverLimits
import org.finiteplay.klondike.solver.search.solveOnLargeStack
import org.finiteplay.klondike.solver.validateCertificate

/**
 * Placeholder versions for this throwaway spike sample only (D1s: "Certify a throwaway
 * sample against the current, still-unfrozen rules version. Do not commit a catalog
 * from this package."). Not the frozen values RF/D1b will use.
 */
val D1S_SPIKE_VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/** Outcome of attempting to certify a single candidate seed. */
sealed class SeedAttempt {
    abstract val seed: Long
    abstract val elapsedMs: Long

    data class Certified(
        override val seed: Long,
        override val elapsedMs: Long,
        val rawCertificate: List<Move>,
        val rawNodes: Long,
    ) : SeedAttempt()

    data class NotCertified(
        override val seed: Long,
        override val elapsedMs: Long,
        val reason: String,
    ) : SeedAttempt()
}

/**
 * Attempts to certify [seed]: solves from the raw deal and independently replays the
 * certificate to confirm it reaches [org.finiteplay.klondike.board.GameStatus.WON].
 * Every game starts on the raw deal regardless of the automatic-moves setting —
 * automation never runs before the player's first action (`docs/games/klondike/DESIGN.md` "Automatic
 * Foundation Moves") — so solving the raw deal once covers both settings; there is no
 * longer a separate cascaded starting board to solve.
 */
fun attemptSeed(seed: Long, limits: SolverLimits): SeedAttempt {
    val startNanos = System.nanoTime()
    fun elapsedMs() = (System.nanoTime() - startNanos) / 1_000_000L

    val rawStart = dealGame(seed, D1S_SPIKE_VERSIONS)
    val rawOutcome = solveOnLargeStack(rawStart, limits)
    if (rawOutcome !is SolveOutcome.Solved) {
        return SeedAttempt.NotCertified(seed, elapsedMs(), "raw search: ${rawOutcome::class.simpleName}")
    }

    try {
        validateCertificate(dealGame(seed, D1S_SPIKE_VERSIONS), rawOutcome.certificate)
    } catch (e: CertificateReplayException) {
        return SeedAttempt.NotCertified(seed, elapsedMs(), "raw certificate failed replay: ${e.message}")
    }

    return SeedAttempt.Certified(
        seed = seed,
        elapsedMs = elapsedMs(),
        rawCertificate = rawOutcome.certificate,
        rawNodes = rawOutcome.nodes,
    )
}

/** Nearest-rank percentile (matches the style `docs/games/klondike/EXECUTION_PLAN.md`'s S2 statistics use). */
fun percentile(sortedAscending: List<Long>, p: Double): Long {
    if (sortedAscending.isEmpty()) return 0L
    val index = (p * (sortedAscending.size - 1)).toInt().coerceIn(0, sortedAscending.size - 1)
    return sortedAscending[index]
}

/**
 * Runs the D1s feasibility spike: generates sequential candidate seeds starting at
 * [firstSeed], searches and independently replay-validates each until [targetCertified]
 * seeds are certified, then reports the gate's required timing statistics. Does not
 * write a catalog (out of scope for D1s).
 */
fun runD1sSpike(
    firstSeed: Long = 1L,
    targetCertified: Int = 10,
    limits: SolverLimits = SolverLimits(),
    log: (String) -> Unit = ::println,
): List<SeedAttempt> {
    val attempts = mutableListOf<SeedAttempt>()
    val certified = mutableListOf<SeedAttempt.Certified>()
    val overallStartNanos = System.nanoTime()

    var seed = firstSeed
    while (certified.size < targetCertified) {
        val attempt = attemptSeed(seed, limits)
        attempts += attempt
        when (attempt) {
            is SeedAttempt.Certified -> {
                certified += attempt
                log(
                    "seed=$seed CERTIFIED raw=${attempt.rawCertificate.size} moves/${attempt.rawNodes} nodes, " +
                        "${attempt.elapsedMs} ms (${certified.size}/$targetCertified)",
                )
            }
            is SeedAttempt.NotCertified -> {
                log("seed=$seed not certified (${attempt.reason}), ${attempt.elapsedMs} ms")
            }
        }
        seed++
    }

    val overallElapsedMs = (System.nanoTime() - overallStartNanos) / 1_000_000L
    val times = attempts.map { it.elapsedMs }.sorted()
    val median = percentile(times, 0.50)
    val p95 = percentile(times, 0.95)
    val msPerCertified = overallElapsedMs.toDouble() / certified.size.coerceAtLeast(1)
    val projected10Ms = msPerCertified * 10

    log("")
    log("=== D1s spike summary ===")
    log("attempted seeds: ${attempts.size}, certified: ${certified.size}/$targetCertified")
    log("median per-seed attempt time: $median ms")
    log("p95 per-seed attempt time: $p95 ms")
    log("observed wall-clock to certify ${certified.size} seeds: $overallElapsedMs ms")
    log(
        "projected wall-clock to regenerate 10 certified seeds after a rules change: " +
            "%.1f s (measured %.0f ms/certified-seed over this run, ${attempts.size} attempts for ${certified.size} certified)"
                .format(projected10Ms / 1000.0, msPerCertified),
    )
    log("certified seeds: ${certified.map { it.seed }}")

    return attempts
}
