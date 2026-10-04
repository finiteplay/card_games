package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/** Totals for one suit count over a run of deals. */
class SurveyTally {
    val byStrategy = AtomicLong()
    val bySearch = AtomicLong()
    val exhausted = AtomicLong()
    val limit = AtomicLong()
    val outOfMemory = AtomicLong()
    val nodes = AtomicLong()
    val done = AtomicLong()

    /**
     * Which seeds actually won, not just how many. A percentage answers "how winnable is this suit
     * count"; the seeds themselves answer "show me one", which is the question asked of a count
     * whose winnable deals are rare enough to be individually interesting — and they are what a
     * future certified catalog for that count would have to start from (`DEALS.md`).
     */
    private val solvedSeedList = java.util.concurrent.ConcurrentLinkedQueue<Long>()

    val solvedSeeds: List<Long> get() = solvedSeedList.sorted()

    val solved: Long get() = byStrategy.get() + bySearch.get()

    fun record(outcome: SolveOutcome, seed: Long? = null) {
        when (outcome.result) {
            SolveResult.SOLVED_BY_STRATEGY -> byStrategy.incrementAndGet()
            SolveResult.SOLVED_BY_SEARCH -> bySearch.incrementAndGet()
            SolveResult.EXHAUSTED -> exhausted.incrementAndGet()
            SolveResult.LIMIT -> limit.incrementAndGet()
            // Not expected at the interactive-sized budgets Survey itself runs with (its default
            // cache never approaches a size that risks this) — counted rather than ignored so a
            // run would still surface it plainly if it ever happened.
            SolveResult.OUT_OF_MEMORY -> outOfMemory.incrementAndGet()
        }
        if (outcome.solved && seed != null) solvedSeedList.add(seed)
        nodes.addAndGet(outcome.nodes)
        done.incrementAndGet()
    }

    /**
     * Deliberately three numbers, not one. `solved` is a floor on winnability — those deals
     * demonstrably have a win. `exhausted` is a ceiling contribution — those were searched out and
     * genuinely have none. `limit` is neither: the search ran out of budget and proved nothing
     * about the deal, so folding it into either would be a claim the run does not support.
     */
    fun summary(label: String, total: Long): String {
        val d = done.get().coerceAtLeast(1)
        return buildString {
            append(label).append(": ").append(done.get()).append('/').append(total).append(" deals")
            append("  won=").append(pct(solved, d))
            append(" (strategy ").append(pct(byStrategy.get(), d))
            append(", search ").append(pct(bySearch.get(), d)).append(')')
            append("  proved-unwinnable=").append(pct(exhausted.get(), d))
            append("  undecided=").append(pct(limit.get(), d))
            if (outOfMemory.get() > 0) append("  out-of-memory=").append(pct(outOfMemory.get(), d))
            append("  nodes/deal=").append(nodes.get() / d)
        }
    }

    private fun pct(n: Long, d: Long): String = String.format("%.2f%%", n * 100.0 / d)
}

/**
 * Runs the solver over a range of seeds at one suit count, across every core.
 *
 * Seeds are the same ones the game deals from (`dealGame`), so a result here is about the deals
 * players actually get rather than about a separate generator.
 */
object Survey {
    private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    fun run(
        suitCount: SuitCount,
        seeds: LongRange,
        limits: SolverLimits,
        threads: Int = Runtime.getRuntime().availableProcessors(),
        progressEvery: Long = 25_000,
        onProgress: (SurveyTally) -> Unit = {},
    ): SurveyTally {
        val tally = SurveyTally()
        val next = AtomicLong(seeds.first)
        val last = seeds.last

        val workers = (0 until threads).map {
            Thread {
                val solver = SpiderSolver(limits)
                while (true) {
                    val seed = next.getAndIncrement()
                    if (seed > last) break
                    val state = dealGame(seed = seed, versions = VERSIONS, suitCount = suitCount)
                    tally.record(solver.solve(state), seed)
                    val done = tally.done.get()
                    if (done % progressEvery == 0L) onProgress(tally)
                }
            }.apply { isDaemon = true; start() }
        }
        workers.forEach { it.join() }
        return tally
    }
}

/**
 * Entry point for the survey, and for the S6 four-suit certification campaign
 * (`docs/games/spider/EXECUTION_PLAN.md` "S6") — the latter lives behind a `campaign` or
 * `campaign-parallel` first argument rather than its own `main`, since the `application` plugin's
 * `mainClass` is fixed to this file's facade class. `campaign` spreads threads across *different*
 * seeds (one thread, one deal each); `campaign-parallel` puts every thread on the *same* seed at
 * once, one seed at a time — [runFourSuitCampaignParallel]'s own doc has the measurement behind why
 * that second shape exists at all.
 *
 * Survey arguments: `<deals> [maxNodes] [maxMillis] [playouts] [ONE|TWO|FOUR] [beamWidth]`. Prints
 * one line per suit count, plus the seeds that actually won.
 *
 * Campaign arguments: `campaign <deals> [maxNodes] [maxMillis] [playouts] [ONE|TWO|FOUR]
 * [cacheCapacityPowerOfTwo] [threads] [maxCacheEntriesMB]`. Prints one CSV line per deal
 * (`CampaignRecord.CSV_HEADER`); defaults to a generous, hours-scale time budget, since the whole
 * point is finding a solution at all rather than staying inside the interactive Hint's own limits —
 * but *not* a large unbounded cache: see the `cacheCapacityPowerOfTwo` default's own comment below
 * for why that specific idea already caused an OOM across every worker thread once, on this very
 * campaign mode. [maxCacheEntriesMB], when given, switches to the bounded, evicting cache instead
 * (`SolverLimits.maxCacheEntries`) — this is the knob for a genuinely long, single-deal attempt at
 * the reference solver's own one-hour-per-deal, bounded-memory shape (measured: at ~2,000,000
 * nodes/sec/thread on this machine, an hour is ~7.2 billion nodes, and an unbounded cache holding
 * that many 8-byte entries would need ~58GB — the bounded cache is what makes a real hour actually
 * fit in a real machine's memory). Sizes at 16 amortized bytes per live entry (a `LongHashSet` at
 * its own ~50% load factor).
 *
 * Nothing here is written to the repo — this measures, it does not generate a catalog; certifying
 * deals for shipping is S5's job and needs the result to be reproducible under a frozen ruleset
 * first.
 */
fun main(args: Array<String>) {
    if (args.getOrNull(0) == "campaign-parallel") {
        // campaign-parallel <deals> [maxNodes] [maxMillis] [playouts] [ONE|TWO|FOUR] [threadsPerDeal] [cacheGB]
        //
        // Many threads on the *same* hard deal, one seed at a time -- runFourSuitCampaignParallel's
        // own doc has the measurement and reasoning; this mode exists alongside `campaign` (many
        // threads on many *different* deals) rather than replacing it, since which is better depends
        // on whether the goal is surveying many deals or cracking one specific hard one.
        val campaignArgs = args.drop(1)
        val deals = campaignArgs.getOrNull(0)?.toLong() ?: 1L
        val limits = SolverLimits(
            maxNodes = campaignArgs.getOrNull(1)?.toLong() ?: 50_000_000_000L,
            maxMillis = campaignArgs.getOrNull(2)?.toLong() ?: 3_600_000L,
            playouts = campaignArgs.getOrNull(3)?.toInt() ?: 0,
            beamWidth = 0,
        )
        val suitCount = SuitCount.valueOf(campaignArgs.getOrNull(4)?.uppercase() ?: "FOUR")
        val threadsPerDeal = campaignArgs.getOrNull(5)?.toInt() ?: Runtime.getRuntime().availableProcessors()
        val cacheGB = campaignArgs.getOrNull(6)?.toDouble() ?: 16.0
        runFourSuitCampaignParallel(
            suitCount = suitCount,
            seeds = 1L..deals,
            limits = limits,
            threadsPerDeal = threadsPerDeal,
            totalCacheBytes = (cacheGB * 1024 * 1024 * 1024).toLong(),
        )
        return
    }
    if (args.getOrNull(0) == "campaign") {
        val campaignArgs = args.drop(1)
        val deals = campaignArgs.getOrNull(0)?.toLong() ?: 16L
        val limits = SolverLimits(
            // Each visited state costs 8 bytes in the transposition cache, and every thread runs
            // its own — so maxNodes, not cacheCapacityPowerOfTwo, is what actually bounds memory
            // (the capacity is only a sizing *hint*; the cache grows past it as needed). Default
            // sized for this machine's 20g heap (`build.gradle.kts`) at the default thread count:
            // 100,000,000 nodes * 8 bytes * 16 threads ~= 12GB, leaving real headroom for the JVM,
            // GC, and everything else sixteen concurrent searches allocate. A run with a *smaller*
            // thread count can safely raise this — one with more threads must lower it.
            // Measured need: an earlier run's maxNodes=2_000_000_000 requested up to 16GB for a
            // *single* thread's cache alone against a 6g heap, and every worker thread OOMed.
            maxNodes = campaignArgs.getOrNull(1)?.toLong() ?: 100_000_000L,
            maxMillis = campaignArgs.getOrNull(2)?.toLong() ?: 3_600_000L,
            playouts = campaignArgs.getOrNull(3)?.toInt() ?: 0,
            beamWidth = 0,
            // Left at SolverLimits' own small default rather than pre-sized for "campaign scale":
            // LongHashSet allocates its backing array eagerly in its constructor, not lazily, and
            // every SpiderSolver holds *two* of them (visited and streamlinedVisited). A prior
            // version of this default (1 shl 27, ~1.07GB per array) cost ~2.15GB per thread before
            // a single node was searched, at every thread, whether that thread's deal needed it or
            // not -- 16 threads OOMed the 20g heap on startup, before any real work. Measured real
            // usage (a smoke-test run's own peak cache sizes) stayed in the hundreds of thousands
            // of entries, nowhere near 134M -- organic growth via LongHashSet.grow() is cheap
            // (amortized O(1) per insert) and is what actually fits the cache to what a deal turns
            // out to need, rather than guessing upfront and paying for the guess on every thread.
            cacheCapacityPowerOfTwo = campaignArgs.getOrNull(5)?.toInt() ?: SolverLimits().cacheCapacityPowerOfTwo,
            maxCacheEntries = campaignArgs.getOrNull(7)?.toLong()?.let { megabytes -> megabytes * 1024 * 1024 / 16 },
        )
        val suitCount = SuitCount.valueOf(campaignArgs.getOrNull(4)?.uppercase() ?: "FOUR")
        val threads = campaignArgs.getOrNull(6)?.toInt() ?: Runtime.getRuntime().availableProcessors()
        runFourSuitCampaign(suitCount = suitCount, seeds = 1L..deals, limits = limits, threads = threads)
        return
    }

    val deals = args.getOrNull(0)?.toLong() ?: 10_000L
    val limits = SolverLimits(
        maxNodes = args.getOrNull(1)?.toLong() ?: 150_000L,
        maxMillis = args.getOrNull(2)?.toLong() ?: 1_500L,
        playouts = args.getOrNull(3)?.toInt() ?: 30,
        beamWidth = args.getOrNull(5)?.toInt() ?: SolverLimits().beamWidth,
    )
    val only = args.getOrNull(4)?.uppercase()
    println("Spider survey: $deals deals per suit count, limits=$limits, threads=${Runtime.getRuntime().availableProcessors()}")
    for (suitCount in SuitCount.entries.filter { only == null || it.name == only }) {
        val started = System.nanoTime()
        val tally = Survey.run(
            suitCount = suitCount,
            seeds = 1L..deals,
            limits = limits,
            onProgress = { println("  ... " + it.summary(suitCount.name, deals)) },
        )
        val seconds = (System.nanoTime() - started) / 1_000_000_000.0
        println(tally.summary(suitCount.name, deals) + String.format("  %.1fs (%.0f deals/s)", seconds, deals / seconds))
        val won = tally.solvedSeeds
        if (won.isEmpty()) {
            println("  no winnable ${suitCount.name} seed found in this range at these limits")
        } else {
            println("  winnable ${suitCount.name} seeds: " + won.joinToString(", "))
        }
    }
}
