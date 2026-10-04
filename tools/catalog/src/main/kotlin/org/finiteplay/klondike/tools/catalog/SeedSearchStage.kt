package org.finiteplay.klondike.tools.catalog

import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.solution.SolutionCodec
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.Solver
import org.finiteplay.klondike.solver.search.SolverLimits

/**
 * Stage B: the two levels cut from the searches rather than from a ruleset.
 *
 * Only deals no ruleset wins reach here, and only as many of them as the two levels need.
 * Searching the whole population is not an option — 43% of seeds reach this stage and a
 * single A\* budget runs to eight seconds, so ten million seeds would be months of CPU. The
 * stage therefore walks seeds in order, searching each candidate, and stops as soon as both
 * pools are full.
 *
 * - **Expert** is the deal exactly one of the two searches wins. Two engines over the same
 *   full game disagreeing is what "the edge of searchability" means operationally: one of
 *   them found a line the other could not inside the same budget.
 * - **Insane** is the deal neither resolves. It ships **without a certificate** — see
 *   `DIFFICULTY_LEVELS.md` "Insane ships uncertified", which is a deliberate exception to
 *   the catalog's certification contract, not an oversight.
 *
 * Rows append as they are produced, so an interrupted run keeps everything it proved; a
 * resumed run skips seeds already recorded.
 */
internal const val SEARCH_CSV_HEADER =
    "seed,verdict,dfs_outcome,dfs_moves,dfs_ms,astar_outcome,astar_moves,astar_ms,withdrawal_needed,solution"

/** What the pair of searches said about one deal. */
enum class SearchVerdict {
    /** Exactly one engine found a win — the Expert level's population. */
    EXPERT_SPLIT,

    /** Neither engine resolved it — the Insane level's population, shipped uncertified. */
    INSANE_UNRESOLVED,

    /** Both engines won it: an ordinary search-solvable deal, kept out of both levels. */
    BOTH_WON,

    /** An engine exhausted the space: the deal is provably unwinnable and never ships. */
    PROVEN_LOST,
}

internal data class SearchedSeed(
    val seed: Long,
    val verdict: SearchVerdict,
    val dfsOutcome: String,
    val dfsMoves: Int,
    val dfsMs: Long,
    val astarOutcome: String,
    val astarMoves: Int,
    val astarMs: Long,
    val withdrawalNeeded: String,
    /** The winning line, base64 through the shipped codec, or empty where nothing won. */
    val solution: String,
) {
    fun toCsv(): String = listOf(
        seed, verdict, dfsOutcome, dfsMoves, dfsMs, astarOutcome, astarMoves, astarMs, withdrawalNeeded, solution,
    ).joinToString(",")

    val moves: Int get() = maxOf(dfsMoves, astarMoves)
}

internal fun readSearchedSeeds(file: File): List<SearchedSeed> {
    if (!file.exists()) return emptyList()
    val lines = readGradingLines(file)
    if (lines.firstOrNull() != SEARCH_CSV_HEADER) return emptyList()
    val fields = SEARCH_CSV_HEADER.count { it == ',' } + 1
    return lines.drop(1).mapNotNull { line ->
        val c = line.split(',')
        if (c.size != fields) return@mapNotNull null
        val seed = c[0].toLongOrNull() ?: return@mapNotNull null
        SearchedSeed(
            seed = seed,
            verdict = runCatching { SearchVerdict.valueOf(c[1]) }.getOrNull() ?: return@mapNotNull null,
            dfsOutcome = c[2],
            dfsMoves = c[3].toIntOrNull() ?: 0,
            dfsMs = c[4].toLongOrNull() ?: 0,
            astarOutcome = c[5],
            astarMoves = c[6].toIntOrNull() ?: 0,
            astarMs = c[7].toLongOrNull() ?: 0,
            withdrawalNeeded = c[8],
            solution = c[9],
        )
    }
}

fun gradeSearchStage(
    seedCount: Int,
    firstSeed: Long = 1L,
    parallelism: Int = 12,
    expertTarget: Int = 15_000,
    insaneTarget: Int = 15_000,
    dfsNodes: Long = 300_000L,
    astarNodes: Long = 300_000L,
    astarMs: Long = 8_000L,
    outputPath: String = "tools/catalog/data/grading/search_grades.csv",
    censusPath: String = "tools/catalog/data/grading/search_census.txt",
    log: (String) -> Unit = ::println,
) {
    val output = File(outputPath).also { it.parentFile?.mkdirs() }
    val existing = readSearchedSeeds(output)
    val alreadyDone = existing.mapTo(HashSet()) { it.seed }
    val expertFound = AtomicInteger(existing.count { it.verdict == SearchVerdict.EXPERT_SPLIT })
    val insaneFound = AtomicInteger(existing.count { it.verdict == SearchVerdict.INSANE_UNRESOLVED })

    log("searching the deals no ruleset wins, over $seedCount seeds from $firstSeed on $parallelism threads")
    log("budgets: DFS $dfsNodes nodes, A* $astarNodes nodes / ${astarMs}ms")
    log("targets: $expertTarget Expert (exactly one engine wins), $insaneTarget Insane (neither resolves)")
    if (existing.isNotEmpty()) {
        log("resuming: ${existing.size} rows found — ${expertFound.get()} Expert, ${insaneFound.get()} Insane")
    }

    val writer = FileWriter(output, true).buffered()
    if (output.length() == 0L) {
        writer.appendLine(SEARCH_CSV_HEADER)
        writer.flush()
    }
    val writeLock = Any()

    val census = ConcurrentHashMap<String, AtomicLong>()
    fun bump(key: String) = census.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()

    val next = AtomicLong(0)
    val searched = AtomicInteger(existing.size)
    val scanned = AtomicInteger(0)
    val startedAt = System.nanoTime()

    fun writeCensus(finished: Boolean) {
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
        File(censusPath).also { it.parentFile?.mkdirs() }.writeText(
            buildString {
                appendLine(if (finished) "status: complete" else "status: running")
                appendLine("scanned: ${scanned.get()} / $seedCount")
                appendLine("searched: ${searched.get()}")
                appendLine("expert: ${expertFound.get()} / $expertTarget")
                appendLine("insane: ${insaneFound.get()} / $insaneTarget")
                appendLine("elapsed: ${elapsed}s")
                if (elapsed > 0) appendLine("searches/s: %.1f".format(searched.get().toDouble() / elapsed))
                appendLine()
                for (entry in census.entries.sortedBy { it.key }) appendLine("${entry.key}: ${entry.value.get()}")
            },
        )
    }
    writeCensus(finished = false)

    fun poolsFull() = expertFound.get() >= expertTarget && insaneFound.get() >= insaneTarget

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        List(parallelism) {
            executor.submit {
                while (!poolsFull()) {
                    val offset = next.getAndIncrement()
                    if (offset >= seedCount) break
                    val seed = firstSeed + offset
                    scanned.incrementAndGet()
                    if (seed in alreadyDone) continue
                    // The ladder again rather than a list from stage A: five undeviating
                    // playouts cost microseconds, far less than shipping millions of seed
                    // numbers between the stages, and it keeps the two stages independent.
                    if (gradeOneSeed(seed, maxRobustness = 0, maxStates = 400_000) != null) continue
                    val row = runCatching { searchOneSeed(seed, dfsNodes, astarNodes, astarMs) }
                        .onFailure { bump("failed: ${it::class.simpleName}") }
                        .getOrNull() ?: continue
                    bump("verdict ${row.verdict}")
                    when (row.verdict) {
                        SearchVerdict.EXPERT_SPLIT -> expertFound.incrementAndGet()
                        SearchVerdict.INSANE_UNRESOLVED -> insaneFound.incrementAndGet()
                        else -> Unit
                    }
                    synchronized(writeLock) {
                        writer.appendLine(row.toCsv())
                        writer.flush()
                    }
                    if (searched.incrementAndGet() % 250 == 0) writeCensus(finished = false)
                }
            }
        }.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(24, TimeUnit.HOURS)
        writer.close()
        writeCensus(finished = poolsFull())
    }

    val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
    log("")
    for (entry in census.entries.sortedBy { it.key }) log("${entry.key}: ${entry.value.get()}")
    log("")
    log("${searched.get()} searched deals in $outputPath after ${elapsed}s")
}

/** Runs both engines over one deal and classifies what they disagreed about. */
internal fun searchOneSeed(seed: Long, dfsNodes: Long, astarNodes: Long, astarMs: Long): SearchedSeed {
    val board = dealGame(seed, D1S_SPIKE_VERSIONS)

    val dfsStarted = System.nanoTime()
    val dfs = runCatching { searchForWin(seed, rules = 0, maxNodes = dfsNodes, maxDepth = 400) }.getOrNull()
    val dfsMs = (System.nanoTime() - dfsStarted) / 1_000_000
    val dfsOutcome = when {
        dfs == null -> "ERROR"
        dfs.won -> "WINNABLE"
        dfs.exhausted -> "PROVEN_LOST"
        else -> "INCONCLUSIVE"
    }

    val limits = SolverLimits(maxNodes = astarNodes, maxDurationMs = astarMs)
    val astar = runCatching {
        Solver.solve(board, limits, includeFoundationWithdrawal = true, ordering = BENCHMARK_ASTAR_ORDERING)
    }.getOrNull()
    val certificate = (astar as? SolveOutcome.Solved)?.certificate
    val astarOutcome = when (astar) {
        is SolveOutcome.Solved -> "WINNABLE"
        is SolveOutcome.Unsolved -> "PROVEN_LOST"
        is SolveOutcome.Timeout -> "INCONCLUSIVE"
        else -> "ERROR"
    }

    val dfsWon = dfsOutcome == "WINNABLE"
    val astarWon = astarOutcome == "WINNABLE"
    val verdict = when {
        dfsOutcome == "PROVEN_LOST" || astarOutcome == "PROVEN_LOST" -> SearchVerdict.PROVEN_LOST
        dfsWon && astarWon -> SearchVerdict.BOTH_WON
        dfsWon || astarWon -> SearchVerdict.EXPERT_SPLIT
        else -> SearchVerdict.INSANE_UNRESOLVED
    }

    // The withdrawal column only means anything where something won: it asks whether *this*
    // deal's win needs a card taken back off a foundation, and an unresolved deal has no win
    // to ask about. Only an exhausted ablation proves the answer is yes.
    val withdrawalNeeded = if (verdict != SearchVerdict.EXPERT_SPLIT && verdict != SearchVerdict.BOTH_WON) {
        ""
    } else {
        val ablated = runCatching {
            Solver.solve(board, limits, includeFoundationWithdrawal = false, ordering = BENCHMARK_ASTAR_ORDERING)
        }.getOrNull()
        when (ablated) {
            is SolveOutcome.Solved -> "no"
            is SolveOutcome.Unsolved -> "yes"
            else -> "unknown"
        }
    }

    // Half the Expert level is won by the depth-first search alone, and those deals still have
    // to ship a line, so its own winning branch is converted and used where A* found nothing.
    // The conversion adds the draws a certificate is replayed with; a line that does not
    // survive replay is dropped rather than shipped.
    val dfsLine = dfs?.takeIf { it.won && certificate == null }
        ?.let { replayableStrategyLine(seed, it.winningLine) }
        ?.takeIf { replayWinsFromDeal(seed, it) }
    val line = certificate ?: dfsLine
    val solution = line?.let { encodeLine(it) } ?: ""
    return SearchedSeed(
        seed = seed,
        verdict = verdict,
        dfsOutcome = dfsOutcome,
        dfsMoves = dfsLine?.size ?: dfs?.takeIf { it.won }?.lineChoices ?: 0,
        dfsMs = dfsMs,
        astarOutcome = astarOutcome,
        astarMoves = certificate?.size ?: 0,
        astarMs = astar?.elapsedMs ?: 0,
        withdrawalNeeded = withdrawalNeeded,
        solution = solution,
    )
}

/** A line counts only if the real reducer plays it to a win. */
internal fun replayWinsFromDeal(seed: Long, line: List<Move>): Boolean {
    var state = dealGame(seed, D1S_SPIKE_VERSIONS)
    for (move in line) {
        state = runCatching { org.finiteplay.klondike.rules.applyMove(state, move) }.getOrElse { return false }
    }
    return state.isWon
}

/** A certificate as one CSV cell: the shipped codec's bytes, base64'd. */
internal fun encodeLine(line: List<Move>): String =
    java.util.Base64.getEncoder().encodeToString(SolutionCodec.encodeCatalog(mapOf(0L to line)))

internal fun decodeLine(cell: String): List<Move>? = runCatching {
    SolutionCodec.decodeCatalog(java.util.Base64.getDecoder().decode(cell))[0L]
}.getOrNull()
