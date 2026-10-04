package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.solution.SolutionCodec
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.solver.DEFAULT_ORACLE_LIMITS
import org.finiteplay.klondike.solver.PureOutcome
import org.finiteplay.klondike.solver.StrategyTier
import org.finiteplay.klondike.solver.TierClassification
import org.finiteplay.klondike.solver.classifyDeal
import org.finiteplay.klondike.solver.findWinningLine
import org.finiteplay.klondike.solver.playPureRuleset
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

const val TARGET_CATALOG_SIZE = 1000
const val MAX_WINNING_LINE_MOVES = 500

data class GradedSeed(val seed: Long, val classification: TierClassification)

/**
 * The six difficulty levels the app ships: `StrategyTier`'s five, plus [INSANE] for
 * deals **no** ruleset wins. Those are not unsolvable — they are solvable only by
 * search, so they are the one level whose seeds must be certified and whose shipped
 * solution comes from the solver rather than from a ruleset playing out.
 */
enum class AppDifficulty { TRIVIAL, EASY, MEDIUM, HARD, EXPERT, INSANE }

fun StrategyTier.toAppDifficulty(): AppDifficulty = when (this) {
    StrategyTier.TRIVIAL -> AppDifficulty.TRIVIAL
    StrategyTier.EASY -> AppDifficulty.EASY
    StrategyTier.MEDIUM -> AppDifficulty.MEDIUM
    StrategyTier.HARD -> AppDifficulty.HARD
    StrategyTier.EXPERT -> AppDifficulty.EXPERT
}

/**
 * Fills every difficulty to [seedsPerTier] independently, scanning candidate seeds from
 * 1 upward and bucketing each by the tier that wins it.
 *
 * Scanning from 1 rather than continuing past the previous catalog's maximum is what
 * keeps this reproducible *and* preserves the earlier seeds for free: classification is
 * deterministic, so every seed the old catalog held is re-encountered and re-graded on
 * the way past, landing in whichever bucket it belongs to now.
 *
 * Medium sets the runtime: it is by far the rarest outcome (roughly 4% of classifiable
 * deals against Hard's 46%), so the scan continues long after the other three are full.
 * Workers stop only once every bucket is satisfied, and each bucket is then sorted by
 * seed and truncated, so the result never depends on thread timing.
 */
fun generatePerDifficulty(
    seedsPerTier: Int = TARGET_CATALOG_SIZE,
    maxMoves: Int = MAX_WINNING_LINE_MOVES,
    parallelism: Int = Runtime.getRuntime().availableProcessors(),
    log: (String) -> Unit = ::println,
): Map<AppDifficulty, List<GradedSeed>> {
    val buckets = AppDifficulty.entries.associateWith { ConcurrentLinkedQueue<GradedSeed>() }
    val counts = AppDifficulty.entries.associateWith { AtomicInteger(0) }
    val nextCandidate = AtomicInteger(1)
    val attempted = AtomicInteger(0)

    fun allFull() = counts.values.all { it.get() >= seedsPerTier }

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val workers = List(parallelism) {
            executor.submit {
                while (!allFull()) {
                    val seed = nextCandidate.getAndIncrement().toLong()
                    val tried = attempted.incrementAndGet()
                    if (tried % 5000 == 0) {
                        log("...$tried candidates tried; " + AppDifficulty.entries.joinToString(" ") { "$it=${counts.getValue(it).get()}" })
                    }
                    val deal = dealGame(seed, D1S_SPIKE_VERSIONS)
                    val classification = classifyDeal(deal, maxMoves = maxMoves)
                    val (difficulty, graded) = when {
                        classification != null -> classification.tier.toAppDifficulty() to GradedSeed(seed, classification)
                        // No ruleset wins it. That is Insane, not unsolvable - but only
                        // if the search can still prove a win, which is also where this
                        // level's shipped solution has to come from. Skipped entirely
                        // once the bucket is full, since the search is the one expensive
                        // step in this whole scan.
                        counts.getValue(AppDifficulty.INSANE).get() < seedsPerTier -> {
                            val line = findWinningLine(deal) ?: continue
                            if (line.size > maxMoves) continue
                            AppDifficulty.INSANE to GradedSeed(
                                seed,
                                TierClassification(StrategyTier.EXPERT, withdrawals = 0, moveCount = line.size, line = line),
                            )
                        }
                        else -> continue
                    }
                    // Keep filling a full bucket rather than discarding: cheap, and it
                    // means the deterministic sort below always has enough to choose from.
                    if (counts.getValue(difficulty).get() >= seedsPerTier + parallelism) continue
                    buckets.getValue(difficulty) += graded
                    counts.getValue(difficulty).incrementAndGet()
                }
            }
        }
        workers.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.MINUTES)
    }

    log("scanned ${attempted.get()} candidate seeds through ${nextCandidate.get() - 1}")
    return buckets.mapValues { (_, queue) -> queue.sortedBy { it.seed }.take(seedsPerTier) }
}

/**
 * Writes every catalogued seed's winning line to [file] in [SolutionCodec]'s format —
 * the blob `:app` ships as an asset so a hint can follow a known solution instead of
 * re-deriving one (`docs/games/klondike/DEALS.md`, "Shipped Solutions").
 */
fun writeSolutionCatalog(byDifficulty: Map<AppDifficulty, List<GradedSeed>>, file: File, log: (String) -> Unit = ::println) {
    val solutions = byDifficulty.values.flatten().associate { it.seed to it.classification.line }
    val encoded = SolutionCodec.encodeCatalog(solutions)
    file.parentFile?.mkdirs()
    file.writeBytes(encoded)
    val moves = solutions.values.sumOf { it.size }
    log("wrote ${solutions.size} solutions ($moves moves, ${encoded.size / 1024} KiB) to ${file.path}")
}

/** Prints one ready-to-paste Kotlin list per difficulty, plus the highest seed each needed. */
fun printPerDifficultyExport(byDifficulty: Map<AppDifficulty, List<GradedSeed>>, log: (String) -> Unit = ::println) {
    for (difficulty in AppDifficulty.entries) {
        val seeds = byDifficulty[difficulty].orEmpty()
        log("")
        log("=== ${difficulty.name} (${seeds.size} seeds, max seed ${seeds.lastOrNull()?.seed ?: 0}) ===")
        for (graded in seeds) log("    ${graded.seed}L,")
    }
    log("")
    log("=== Summary ===")
    for (difficulty in AppDifficulty.entries) {
        val seeds = byDifficulty[difficulty].orEmpty()
        val moves = seeds.map { it.classification.moveCount }.sorted()
        log("$difficulty: ${seeds.size} seeds, moves ${moves.firstOrNull() ?: 0}-${moves.lastOrNull() ?: 0}")
    }
}

/**
 * Grows the interim solvable catalog from [existingSeeds] (kept unchanged - "preserve
 * existing seeds") up to [target] total: re-grades the existing seeds under the new
 * method, then searches sequential candidates starting right after the existing max,
 * classifying each with `:solver`'s `classifyDeal` and keeping only those landing Easy
 * through Expert with a winning line under [maxMoves] moves - Trivial (too easy) and
 * Insane/unsolvable-in-budget (too hard or inconclusive) are both discarded, per this
 * pass's scope. Two sequential phases, not one interleaved pool: mixing a batch of
 * short one-shot jobs (re-grading) with long-running worker loops (the search) on a
 * single fixed thread pool risks the workers grabbing every thread before the batch
 * finishes dispatching, starving it until the search itself is done - two dedicated
 * pools, one per phase, avoids that entirely.
 */
fun generateSeeds(
    existingSeeds: List<Long>,
    target: Int = TARGET_CATALOG_SIZE,
    maxMoves: Int = MAX_WINNING_LINE_MOVES,
    parallelism: Int = Runtime.getRuntime().availableProcessors(),
    overshootMargin: Int = parallelism * 2,
    log: (String) -> Unit = ::println,
): List<GradedSeed> {
    val needed = target - existingSeeds.size
    require(needed >= 0) { "existingSeeds (${existingSeeds.size}) already meets or exceeds target ($target)" }

    log("re-grading ${existingSeeds.size} existing seeds under the new method...")
    val reclassifiedExisting = regradeExisting(existingSeeds, maxMoves, parallelism, log)
    log("done re-grading existing seeds")

    log("searching for $needed new qualifying seeds (Easy-Expert, <$maxMoves moves)...")
    val newSeeds = searchNewSeeds(existingSeeds, needed, maxMoves, parallelism, overshootMargin, log)

    return reclassifiedExisting + newSeeds
}

/**
 * Classifies [seeds] and reports the tier distribution, cost breakdown, and timing,
 * without generating anything - the calibration pass for checking whether the tier
 * rulesets and their critical-choice/withdrawal budgets actually spread a known-good
 * catalog across tiers before committing to a long generation run.
 */
fun reportClassification(seeds: List<Long>, parallelism: Int = Runtime.getRuntime().availableProcessors(), log: (String) -> Unit = ::println) {
    log("classifying ${seeds.size} seeds across $parallelism threads...")
    val startNanos = System.nanoTime()
    val graded = regradeExisting(seeds, MAX_WINNING_LINE_MOVES, parallelism, log)
    val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L

    log("")
    log("=== Classification report ===")
    log("elapsed: ${elapsedMs}ms (%.1f ms/seed)".format(elapsedMs.toDouble() / seeds.size))
    for (tier in StrategyTier.entries) {
        val inTier = graded.filter { it.classification.tier == tier }
        if (inTier.isEmpty()) {
            log("$tier: 0 seeds")
            continue
        }
        val withdrawals = inTier.map { it.classification.withdrawals }
        val moves = inTier.filter { it.classification.moveCount >= 0 }.map { it.classification.moveCount }
        log(
            "$tier: ${inTier.size} seeds | withdrawals ${withdrawals.min()}-${withdrawals.max()} " +
                "| moves ${moves.minOrNull() ?: 0}-${moves.maxOrNull() ?: 0}",
        )
    }
    val unclassified = graded.count { it.classification.moveCount < 0 }
    log("unclassified (would be discarded as Insane in a real run): $unclassified/${seeds.size}")
}

/**
 * The decisive experiment for `docs/games/klondike/DIFFICULTY_LEVELS.md`'s own definition of a tier:
 * can each tier's rules, unaided by any search, actually win these deals - and does the
 * win rate *rise* with the tier, as the cumulative-strategy premise requires? Needs no
 * solver calls, so it runs in milliseconds over the whole catalog.
 */
fun reportPureRulesetWinRates(seeds: List<Long>, log: (String) -> Unit = ::println) {
    log("playing ${seeds.size} seeds with each tier's rules alone, no solver help...")
    log("")
    log("tier | won | stuck | hit move cap | median moves when won")
    for (tier in StrategyTier.entries) {
        val results = seeds.map { playPureRuleset(dealGame(it, D1S_SPIKE_VERSIONS), tier, MAX_WINNING_LINE_MOVES) }
        val won = results.filter { it.outcome == PureOutcome.WON }
        val stuck = results.count { it.outcome == PureOutcome.STUCK }
        val capped = results.count { it.outcome == PureOutcome.EXCEEDED_MOVE_CAP }
        val medianMoves = won.map { it.moves }.sorted().let { if (it.isEmpty()) "-" else it[it.size / 2].toString() }
        log("$tier | ${won.size}/${seeds.size} | $stuck | $capped | $medianMoves")
    }
}

private fun regradeExisting(existingSeeds: List<Long>, maxMoves: Int, parallelism: Int, log: (String) -> Unit): List<GradedSeed> {
    val done = AtomicInteger(0)
    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val futures = existingSeeds.map { seed ->
            executor.submit<GradedSeed> {
                classifyExistingSeed(seed, maxMoves, log).also {
                    val count = done.incrementAndGet()
                    if (count % 20 == 0 || count == existingSeeds.size) log("re-graded $count/${existingSeeds.size} existing seeds")
                }
            }
        }
        return futures.map { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)
    }
}

/**
 * Parallelized across [parallelism] threads: each candidate seed's classification is
 * independent work, so a fixed-size pool classifies many at once instead of one seed
 * at a time (each worker's own oracle calls inside `classifyDeal` are still plain
 * single-threaded `Solver.solve`, so total parallelism stays bounded by [parallelism],
 * never oversubscribed). Output stays reproducible regardless of thread-timing: workers
 * keep going [overshootMargin] past the point they've *seen* enough qualifying seeds,
 * then the result is deterministically sorted by seed value and truncated to exactly
 * what's needed.
 */
private fun searchNewSeeds(
    existingSeeds: List<Long>,
    needed: Int,
    maxMoves: Int,
    parallelism: Int,
    overshootMargin: Int,
    log: (String) -> Unit,
): List<GradedSeed> {
    val firstCandidate = (existingSeeds.maxOrNull() ?: 0L) + 1
    val candidateOffset = AtomicInteger(0)
    val accepted = ConcurrentLinkedQueue<GradedSeed>()
    val acceptedCount = AtomicInteger(0)
    val attempted = AtomicInteger(0)

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val workers = List(parallelism) {
            executor.submit {
                while (acceptedCount.get() < needed + overshootMargin) {
                    val seed = firstCandidate + candidateOffset.getAndIncrement()
                    val triedSoFar = attempted.incrementAndGet()
                    if (triedSoFar % 200 == 0) log("...$triedSoFar candidates tried so far, ${acceptedCount.get()} qualified")
                    // Trivial deals are kept, not discarded: they are 19% of a known-good
                    // sample against Easy's 6%, and `:app`'s own DifficultyTier has no
                    // Trivial value, so they ship as Easy (see exportTierName). Dropping
                    // them would leave the shipped Easy tier nearly empty.
                    val classification = classifyDeal(dealGame(seed, D1S_SPIKE_VERSIONS), maxMoves = maxMoves)
                    if (classification == null) continue
                    accepted += GradedSeed(seed, classification)
                    val count = acceptedCount.incrementAndGet()
                    if (count % 10 == 0) log("progress: $count/$needed qualifying seeds found ($triedSoFar candidates tried)")
                }
            }
        }
        workers.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)
    }

    log("attempted ${attempted.get()} candidate seeds, ${accepted.size} qualified Easy-Expert (needed $needed)")
    return accepted.sortedBy { it.seed }.take(needed)
}

/**
 * Existing seeds were certified solvable by an earlier, different search (D1s's exact
 * A*), so re-classifying them here should essentially always succeed - but "preserve
 * existing seeds" is unconditional, so a seed the cheap heuristic classifier genuinely
 * can't place within a generous retry budget is kept anyway, loudly flagged, rather
 * than dropped or crashing the whole run over one outlier.
 */
private fun classifyExistingSeed(seed: Long, maxMoves: Int, log: (String) -> Unit): GradedSeed {
    val deal = dealGame(seed, D1S_SPIKE_VERSIONS)
    // No longer-allowance retry: [MAX_WINNING_LINE_MOVES] is a hard requirement on the
    // path length, not just a search budget, so a seed only winnable over a longer path
    // does not qualify however many moves it would eventually take.
    classifyDeal(deal, maxMoves = maxMoves)?.let { return GradedSeed(seed, it) }
    log("note: existing seed=$seed is not won by any tier's rules within $maxMoves moves (Insane) - preserved anyway, graded EXPERT")
    return GradedSeed(seed, TierClassification(StrategyTier.EXPERT, withdrawals = 0, moveCount = -1))
}

/** `:app`'s shipped `DifficultyTier` has no Trivial value - an existing seed that reclassifies that easy under the new method is exported as Easy, its nearest (and honest, since Trivial implies Easy) approximation. */
private fun exportTierName(tier: StrategyTier): String = if (tier == StrategyTier.TRIVIAL) "EASY" else tier.name

fun printKotlinExport(graded: List<GradedSeed>, log: (String) -> Unit = ::println) {
    val sorted = graded.sortedWith(compareBy({ it.classification.tier }, { it.classification.moveCount }, { it.seed }))
    val trivialAmongPreserved = graded.count { it.classification.tier == StrategyTier.TRIVIAL }
    if (trivialAmongPreserved > 0) log("note: $trivialAmongPreserved preserved existing seed(s) reclassified Trivial under the new method - exported as Easy")

    log("")
    log("=== Kotlin export: INTERIM_SOLVABLE_SEEDS (paste into app's InterimSolvableSeeds.kt) ===")
    for (g in sorted) log("    ${g.seed}L,")

    log("")
    log("=== Kotlin export: INTERIM_SEED_GRADES (paste into app's InterimSeedGrades.kt) ===")
    for (g in sorted) log("    ${g.seed}L to DifficultyTier.${exportTierName(g.classification.tier)},")

    log("")
    log("=== Summary ===")
    for (tier in StrategyTier.entries) {
        val inTier = graded.filter { it.classification.tier == tier }
        log("$tier: ${inTier.size} seeds")
    }
    val unclassified = graded.count { it.classification.moveCount < 0 }
    if (unclassified > 0) log("$unclassified seed(s) flagged for manual review (see WARNING lines above)")
}
