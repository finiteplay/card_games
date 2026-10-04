package org.finiteplay.freecell.tools.catalog

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.dealGame
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.session.FreeCellLogCodec
import org.finiteplay.freecell.session.FreeCellLogEntry
import org.finiteplay.freecell.solution.CompactSolutionCodec
import org.finiteplay.freecell.solver.BestFirstSolver
import org.finiteplay.freecell.solver.SolveOutcome
import org.finiteplay.freecell.solver.SolverLimits

/**
 * A generous offline budget for the one-time re-solve below: every seed here is already known
 * solvable (it is in the certified catalog), so the point is not to prove solvability again but
 * to find a *short* line the way [BestFirstSolver] does — with none of the interactive Hint
 * path's wall-clock pressure. Sized the way Klondike's own offline diagnostics scale past their
 * interactive budget (`SaveInspection.kt`'s 8,000,000-node/180s pass against its interactive
 * ~340,000-node/8s one — roughly 23x): [org.finiteplay.freecell.solver.HINT_BEST_FIRST_LIMITS] is
 * 100,000 nodes/2s, so this uses a comparable multiple while staying well short of the wall-clock
 * this one-off run can actually afford across ten thousand seeds.
 */
val FREECELL_SHRINK_LIMITS = SolverLimits(maxNodes = 3_000_000L, maxDurationMs = 30_000L)

/** One seed's before/after comparison for [shrinkFreeCellSolutions]'s report. */
internal data class ShrinkResult(val seed: Long, val chosen: List<Move>, val usedFallback: Boolean)

/**
 * Re-solves every seed in [solutionsPath] with [BestFirstSolver] looking for a much shorter
 * winning line than the length-blind DFS fallback originally certified it with (`HintEngine.kt`'s
 * own doc: "hundreds to thousands of moves on a deal a person wins in about ninety"). Multi-
 * threaded across [threads] workers, mirroring this module's other catalog-generation tools
 * (`scanForCertifiedSeeds`).
 *
 * Every seed is already known solvable — that is what "certified" means — so [BestFirstSolver]
 * is expected to succeed on nearly all of them within [limits]. When it does not (a timeout, or a
 * certificate that fails independent replay), the seed keeps its existing certificate rather than
 * being dropped or left unsolved: a size cost on that one entry, never a correctness or coverage
 * regression.
 *
 * Never trusts the new line on the solver's word alone: every accepted replacement is
 * independently replayed through the real reducer (`replayWinsForReal`) before it displaces the
 * old certificate.
 */
internal fun shrinkFreeCellSolutions(
    solutionsPath: String = DEFAULT_FREECELL_SOLUTIONS_PATH,
    limits: SolverLimits = FREECELL_SHRINK_LIMITS,
    threads: Int = Runtime.getRuntime().availableProcessors(),
    progressEvery: Int = 500,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
): Map<Long, ShrinkResult> {
    val existing = FreeCellSolutionCodec.decode(File(solutionsPath).readBytes())
    val queue = ConcurrentLinkedQueue(existing.keys.sorted())
    val total = existing.size
    val results = ConcurrentSkipListMap<Long, ShrinkResult>()
    val done = AtomicLong(0)

    val workers = (0 until threads).map {
        Thread {
            while (true) {
                val seed = queue.poll() ?: break
                val old = existing.getValue(seed)
                val start = dealGame(seed = seed, versions = FREECELL_CATALOG_VERSIONS)
                val outcome = BestFirstSolver.solve(start, limits)
                val resolved = (outcome as? SolveOutcome.Solved)?.certificate
                    ?.takeIf { it.size < old.size && replayWinsForReal(seed, it) }
                results[seed] = if (resolved != null) {
                    ShrinkResult(seed, resolved, usedFallback = false)
                } else {
                    ShrinkResult(seed, old, usedFallback = true)
                }
                val count = done.incrementAndGet()
                if (count % progressEvery == 0L) onProgress(count.toInt(), total)
            }
        }.apply { isDaemon = true; start() }
    }
    workers.forEach { it.join() }
    return results
}

/** Payload-byte length stats (min/median/p90/p99/max) over a set of lines, using the same
 * [FreeCellLogCodec] measure the pre-existing catalog's own sizes were reported in, so a before
 * and after comparison is apples to apples. */
internal fun payloadByteStats(lines: Collection<List<Move>>): String {
    val sizes = lines.map { FreeCellLogCodec.encode(it.map { move -> FreeCellLogEntry.PlayerMove(move) }).size }
        .sorted()
    fun percentile(p: Double): Int {
        val index = ((sizes.size - 1) * p).toInt().coerceIn(0, sizes.lastIndex)
        return sizes[index]
    }
    return "min=${sizes.first()} median=${percentile(0.5)} p90=${percentile(0.9)} p99=${percentile(0.99)} max=${sizes.last()} (n=${sizes.size})"
}

/**
 * Runs [shrinkFreeCellSolutions], reports before/after size stats, writes the shrunk certificates
 * back to [solutionsPath] (the committed verification artifact), and encodes the result with
 * [CompactSolutionCodec] to [assetPath] — the asset the app actually ships and reads.
 */
fun shrinkAndEncodeFreeCellSolutions(
    solutionsPath: String = DEFAULT_FREECELL_SOLUTIONS_PATH,
    assetPath: String = DEFAULT_FREECELL_SOLUTIONS_ASSET_PATH,
    limits: SolverLimits = FREECELL_SHRINK_LIMITS,
    threads: Int = Runtime.getRuntime().availableProcessors(),
    log: (String) -> Unit = ::println,
) {
    val before = FreeCellSolutionCodec.decode(File(solutionsPath).readBytes())
    log("before (DFS-derived): ${payloadByteStats(before.values)}")

    val started = System.nanoTime()
    val results = shrinkFreeCellSolutions(
        solutionsPath = solutionsPath,
        limits = limits,
        threads = threads,
        onProgress = { done, total -> log("  $done/$total resolved") },
    )
    val seconds = (System.nanoTime() - started) / 1_000_000_000.0
    val fallbacks = results.values.count { it.usedFallback }
    log("resolved ${results.size} seeds in ${"%.1f".format(seconds)}s, $fallbacks kept their old (DFS) certificate")

    val after = results.mapValues { it.value.chosen }
    log("after (BestFirstSolver, with $fallbacks fallbacks): ${payloadByteStats(after.values)}")

    val encoded = FreeCellSolutionCodec.encode(after)
    File(solutionsPath).writeBytes(encoded)
    log("wrote ${encoded.size} bytes to $solutionsPath")

    val compact = CompactSolutionCodec.encodeCatalog(after) { seed -> dealGame(seed, FREECELL_CATALOG_VERSIONS) }
    val assetFile = File(assetPath).apply { parentFile?.mkdirs() }
    assetFile.writeBytes(compact)
    log("wrote ${compact.size} bytes (compact, bit-packed) to $assetPath")
}

internal const val DEFAULT_FREECELL_SOLUTIONS_ASSET_PATH = "games/freecell/app/src/main/assets/solutions.bin"
