package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * One deal's result from [campaignSolve], carrying exactly what
 * `docs/games/spider/EXECUTION_PLAN.md` "S6" asks to be recorded: which phase (if either) solved
 * it, wall time, nodes, peak transposition-cache size, and — for a solved deal — the solution
 * length in moves. Length matters beyond curiosity: `SpiderSolver`'s `MAX_DEPTH = 600` is a product
 * constraint as much as an implementation one (a line longer than that is not one a player will
 * tap through, per `#2`'s future shipped-solution feature), so a campaign has to know what it is
 * finding, not just whether it found anything.
 */
data class CampaignRecord(
    val seed: Long,
    val suitCount: SuitCount,
    val solved: Boolean,
    /** "streamlined", "exact", or "none". */
    val solvedBy: String,
    val wallMs: Long,
    val nodes: Long,
    val peakCacheSize: Int,
    val solutionLength: Int?,
    /** "solved", "nodes", "time", "exhausted", or "memory" — the reference solver's own outcome vocabulary. */
    val endedOn: String,
) {
    fun toCsvLine(): String =
        listOf(seed, suitCount.name, solved, solvedBy, wallMs, nodes, peakCacheSize, solutionLength ?: "", endedOn)
            .joinToString(",")

    companion object {
        const val CSV_HEADER = "seed,suitCount,solved,solvedBy,wallMs,nodes,peakCacheSize,solutionLength,endedOn"
    }
}

/**
 * Finds a winning line for [state] offline, mirroring the reference solver's `SMART` streamliner
 * shape (Blake & Gent; citations in `docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md` "Academic
 * sources"): a fast, unsound, suit-discarding pass at a tenth of [limits]' time budget
 * ([SpiderSolver.certifyStreamlined]), and — only if that does not solve it — the exact, sound
 * search with the full budget and [limits]' own cache size ([SpiderSolver.solve]).
 *
 * Never touches the interactive Hint path. [limits] is caller-supplied precisely so a campaign
 * run's generous time budget (`maxMillis` in the hour range) can never leak into `HintEngine`'s
 * own — nothing here changes any default. Its cache is deliberately *not* pre-sized large: see
 * `Survey.kt`'s own `cacheCapacityPowerOfTwo` comment for why that specific idea already OOMed
 * every worker thread on this exact campaign mode, at construction time, before a single deal ran.
 *
 * One [SpiderSolver] per deal, not reused across seeds: reusing one across a whole thread's
 * worth of deals would carry the previous deal's peak cache size into the next deal's
 * [SpiderSolver.cacheSize] read.
 *
 * [SpiderSolver]'s own construction — which is where that incident actually happened, allocating
 * both its transposition caches' backing arrays eagerly and outside [SpiderSolver.solve]'s own
 * `OutOfMemoryError` handling — is guarded here too, so a future oversized `limits` (a CLI override
 * of `cacheCapacityPowerOfTwo`, say) reports one deal as [SolveResult.OUT_OF_MEMORY] instead of
 * silently killing the whole worker thread with no record of the deal at all.
 */
fun campaignSolve(state: SpiderState, limits: SolverLimits): CampaignRecord {
    val started = System.nanoTime()
    val solver = try {
        SpiderSolver(limits)
    } catch (_: OutOfMemoryError) {
        return CampaignRecord(
            seed = state.seed,
            suitCount = state.suitCount,
            solved = false,
            solvedBy = "none",
            wallMs = (System.nanoTime() - started) / 1_000_000,
            nodes = 0,
            peakCacheSize = 0,
            solutionLength = null,
            endedOn = "memory",
        )
    }

    val streamlinedBudgetMs = (limits.maxMillis / 10).coerceAtLeast(1)
    val streamlinedRecord = IntArrayList(64)
    val streamlined = solver.certifyStreamlined(state, streamlinedBudgetMs, streamlinedRecord)
    if (streamlined.solved) {
        return CampaignRecord(
            seed = state.seed,
            suitCount = state.suitCount,
            solved = true,
            solvedBy = "streamlined",
            wallMs = (System.nanoTime() - started) / 1_000_000,
            nodes = streamlined.nodes,
            peakCacheSize = solver.streamlinedCacheSize,
            solutionLength = streamlined.moves,
            endedOn = "solved",
        )
    }

    val exactStarted = System.nanoTime()
    val exact = solver.solve(state)
    val exactElapsedMs = (System.nanoTime() - exactStarted) / 1_000_000
    val endedOn = when (exact.result) {
        SolveResult.EXHAUSTED -> "exhausted"
        SolveResult.OUT_OF_MEMORY -> "memory"
        SolveResult.LIMIT -> if (exactElapsedMs >= limits.maxMillis) "time" else "nodes"
        SolveResult.SOLVED_BY_STRATEGY, SolveResult.SOLVED_BY_SEARCH -> "solved"
    }
    return CampaignRecord(
        seed = state.seed,
        suitCount = state.suitCount,
        solved = exact.solved,
        solvedBy = if (exact.solved) "exact" else "none",
        wallMs = (System.nanoTime() - started) / 1_000_000,
        nodes = streamlined.nodes + exact.nodes,
        peakCacheSize = solver.cacheSize,
        solutionLength = if (exact.solved) exact.moves else null,
        endedOn = endedOn,
    )
}

/**
 * Runs [campaignSolve] over a range of seeds at one suit count, across every core — the S6
 * campaign's own driver, parallel across *hands* rather than within one search
 * (`docs/games/spider/EXECUTION_PLAN.md` "S6" records why: the workload is CPU-bound on real
 * per-node compute, not memory-bound, so a single search would need a concurrent transposition
 * table on its hottest random-access structure for no measured benefit). Prints one CSV line per
 * deal as it finishes, so a long campaign is comparable and resumable by seed even if interrupted.
 *
 * Also prints a `#`-prefixed heartbeat every [heartbeatSeconds] naming which seed each worker is
 * still on and how long it has been on it. A per-deal budget in the hour range means a single stuck
 * or merely very slow deal is otherwise silent for that whole hour — this is what makes that visible
 * within seconds instead. Measured need, not a hypothetical one: an earlier campaign run hung for
 * 1h24m on a malformed `cacheCapacityPowerOfTwo` (`LongHashSet`'s own doc has the mechanism) with no
 * indication anything was wrong until the whole command's timeout fired.
 */
fun runFourSuitCampaign(
    suitCount: SuitCount,
    seeds: LongRange,
    limits: SolverLimits,
    threads: Int = Runtime.getRuntime().availableProcessors(),
    heartbeatSeconds: Long = 30,
) {
    // 0 is the heartbeat's own sentinel for "this worker slot is idle" (below); every call site in
    // this codebase seeds from 1, but a future one passing 0 would silently vanish from the display.
    require(seeds.first > 0) { "seeds must start above 0 — 0 is the heartbeat's idle sentinel" }
    val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)
    val next = AtomicLong(seeds.first)
    val last = seeds.last

    println("# Spider S6 campaign: seeds $seeds, suitCount=$suitCount, limits=$limits, threads=$threads")
    println(CampaignRecord.CSV_HEADER)
    val printLock = Any()

    // [currentSeed]/[startedAtNanos] are worker-slot-indexed, not seed-indexed: one pair of entries
    // per thread, overwritten each time that thread picks up a new deal. 0 in [currentSeed] means
    // that slot has not started a deal yet or has finished them all.
    val currentSeed = AtomicLongArray(threads)
    val startedAtNanos = AtomicLongArray(threads)
    val finished = AtomicLong(0)
    val total = last - seeds.first + 1
    val campaignStarted = System.nanoTime()

    val heartbeat = Thread {
        try {
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(heartbeatSeconds * 1000)
                val now = System.nanoTime()
                val elapsedTotal = (now - campaignStarted) / 1_000_000_000
                val running = (0 until threads).mapNotNull { slot ->
                    val seed = currentSeed.get(slot)
                    if (seed == 0L) null else seed to (now - startedAtNanos.get(slot)) / 1_000_000_000
                }
                synchronized(printLock) {
                    println(
                        "# heartbeat: ${finished.get()}/$total done, ${elapsedTotal}s elapsed, " +
                            "in progress: " + running.joinToString(", ") { (seed, s) -> "seed=$seed(${s}s)" },
                    )
                }
            }
        } catch (_: InterruptedException) {
            // Normal shutdown when the campaign finishes; nothing to report.
        }
    }.apply { isDaemon = true; start() }

    val workers = (0 until threads).mapIndexed { slot, _ ->
        Thread {
            while (true) {
                val seed = next.getAndIncrement()
                if (seed > last) break
                currentSeed.set(slot, seed)
                startedAtNanos.set(slot, System.nanoTime())
                val state = dealGame(seed = seed, versions = versions, suitCount = suitCount)
                val record = campaignSolve(state, limits)
                currentSeed.set(slot, 0)
                finished.incrementAndGet()
                synchronized(printLock) { println(record.toCsvLine()) }
            }
        }.apply { isDaemon = true; start() }
    }
    workers.forEach { it.join() }
    heartbeat.interrupt()
}

/**
 * The many-threads-on-one-deal counterpart to [runFourSuitCampaign]: seeds are attempted one at a
 * time, not spread across worker threads, and each seed's whole [threadsPerDeal]-thread budget goes
 * at it before moving on — the shape a spike measured giving ~7x real wall-clock throughput over one
 * thread on the same deal (`docs/games/spider/EXECUTION_PLAN.md` "S6"), for a deal specific and hard
 * enough that spreading threads across many *different* seeds instead would not help it.
 *
 * [totalCacheBytes] is [parallelSolve]'s own budget, shared across every worker thread on the
 * current seed (never per-thread — see [ConcurrentTranspositionCache]'s own doc for why one shared,
 * sharded cache is both the point and the size ceiling here).
 *
 * Heartbeat every [heartbeatSeconds] for the same reason [runFourSuitCampaign]'s has one: an
 * hour-scale budget on one deal is otherwise silent for that whole hour if something is wrong.
 */
fun runFourSuitCampaignParallel(
    suitCount: SuitCount,
    seeds: LongRange,
    limits: SolverLimits,
    threadsPerDeal: Int,
    totalCacheBytes: Long,
    heartbeatSeconds: Long = 30,
) {
    require(seeds.first > 0) { "seeds must start above 0" }
    val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    println("# Spider S6 parallel campaign: seeds $seeds, suitCount=$suitCount, limits=$limits, threadsPerDeal=$threadsPerDeal, totalCacheBytes=$totalCacheBytes")
    println(CampaignRecord.CSV_HEADER)

    val campaignStarted = System.nanoTime()
    var currentSeed = seeds.first
    var seedStartedAtNanos = System.nanoTime()

    val heartbeat = Thread {
        try {
            while (!Thread.currentThread().isInterrupted) {
                Thread.sleep(heartbeatSeconds * 1000)
                val now = System.nanoTime()
                val elapsedTotal = (now - campaignStarted) / 1_000_000_000
                val onCurrent = (now - seedStartedAtNanos) / 1_000_000_000
                println("# heartbeat: seed=$currentSeed(${onCurrent}s), ${elapsedTotal}s elapsed")
            }
        } catch (_: InterruptedException) {
            // Normal shutdown when the campaign finishes; nothing to report.
        }
    }.apply { isDaemon = true; start() }

    for (seed in seeds) {
        currentSeed = seed
        seedStartedAtNanos = System.nanoTime()
        val state = dealGame(seed = seed, versions = versions, suitCount = suitCount)
        val record = parallelSolve(state, limits, threadsPerDeal, totalCacheBytes)
        println(record.toCsvLine())
    }
    heartbeat.interrupt()
}
