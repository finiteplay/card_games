package org.finiteplay.klondike.tools.catalog

import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.solver.optimizeWinningLine
import org.finiteplay.klondike.solver.search.SearchOrdering
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.Solver
import org.finiteplay.klondike.solver.search.SolverLimits

/**
 * Runs every solver in the repository over the same deals and records what each one found,
 * how long it took, and how much it looked at.
 *
 * The point is comparability. Each engine answers a different question and reports it in a
 * different currency, and the differences are exactly what is worth knowing:
 *
 * - **The tier searches** (Trivial through Expert) explore only choices consistent with the
 *   cumulative strategy set. They can say *won*, *dead inside that restricted graph*, or
 *   *inconclusive at the state cap*; none of the latter two proves the deal unwinnable.
 * - **The catalog's depth-first search** explores the full legal choice set under prunings
 *   that all carry an argument, so exhausting it *is* a proof of unwinnability.
 * - **The A\* solver** searches the same full game. It runs under the best ordering fitted on
 *   the frozen training split, not the exact-A* default: exact ordering is provably
 *   shortest and hits a 300,000-node ceiling on *every* fresh deal, which answers nothing.
 *   Certificates are therefore not minimal, so each is passed through `optimizeWinningLine`
 *   and both lengths are recorded. It runs twice, with foundation withdrawal on and off, which
 *   between them say whether a deal can be won without ever un-banking a card.
 *
 * Every budget is recorded in the output because none of these verdicts means anything without
 * it: an inconclusive result is a statement about the budget, not about the deal.
 */
/**
 * Derived from the ladder rather than spelled out, so adding or merging a tier cannot leave the
 * header describing one shape while [measureSeed] writes another. It did exactly that when the
 * five tiers became four: the columns still named `expert_*` while the row had stopped carrying
 * them, and every field after the block silently shifted left by four.
 */
private val RULESET_COLUMNS: String =
    Ruleset.entries.joinToString(",") { tier ->
        val name = tier.name.lowercase()
        "${name}_win,${name}_moves,${name}_choices,${name}_ms"
    }

internal val CSV_HEADER: String =
    "seed," +
        RULESET_COLUMNS + "," +
        "lowest_ruleset,line_mismatch," +
        "dfs_outcome,dfs_nodes,dfs_states,dfs_choices,dfs_hardest_move,dfs_ms," +
        "astar_outcome,astar_nodes,astar_moves,astar_choices,astar_optimized_moves,astar_ms," +
        "astar_nowithdraw_outcome,astar_nowithdraw_nodes,astar_nowithdraw_moves,astar_nowithdraw_ms," +
        "withdrawal_needed,root_choices,face_down_at_deal"

/** Best Optuna training-split result from study `astar-ordering-v1`, trial 63. */
internal val BENCHMARK_ASTAR_ORDERING = SearchOrdering(
    lowerBoundWeight = 43,
    downCardWeight = 94,
    aceBurialWeight = 48,
    neededCardDepthWeight = 51,
)

internal data class BenchmarkResumeState(
    val completedSeeds: Set<Long>,
    val hasInterruptedTrailingRow: Boolean,
    val interruptedTrailingRowStart: Long = 0L,
)

internal fun pendingBenchmarkSeeds(
    seedCount: Int,
    firstSeed: Long,
    completedSeeds: Set<Long>,
): List<Long> = (0 until seedCount).mapTo(ArrayList()) { firstSeed + it }.filterNot(completedSeeds::contains)

/** Reads durable CSV rows only; an incomplete final write is safe to discard and resume after. */
internal fun readBenchmarkResumeState(file: File): BenchmarkResumeState {
    if (!file.exists() || file.length() == 0L) return BenchmarkResumeState(emptySet(), false)

    val lines = file.readLines()
    require(lines.firstOrNull() == CSV_HEADER) {
        "Cannot resume ${file.path}: CSV header does not match SolverBenchmark"
    }
    val fields = CSV_HEADER.count { it == ',' } + 1
    val lastContentIndex = lines.indexOfLast { it.isNotBlank() }
    val completed = linkedSetOf<Long>()
    var interruptedTrailingRow = false
    for (index in 1..lastContentIndex) {
        val line = lines[index]
        if (line.isBlank()) continue
        val seed = line.substringBefore(',').toLongOrNull()
        val valid = line.count { it == ',' } + 1 == fields && seed != null
        if (!valid) {
            require(index == lastContentIndex) {
                "Cannot resume ${file.path}: malformed row ${index + 1} is not the trailing row"
            }
            interruptedTrailingRow = true
            break
        }
        completed += seed
    }
    return BenchmarkResumeState(
        completedSeeds = completed,
        hasInterruptedTrailingRow = interruptedTrailingRow,
        interruptedTrailingRowStart = if (interruptedTrailingRow) trailingRowStart(file) else 0L,
    )
}

/** Removes only the final incomplete record so the next append starts a valid CSV row. */
internal fun discardInterruptedTrailingRow(file: File, resume: BenchmarkResumeState) {
    if (!resume.hasInterruptedTrailingRow) return
    require(resume.interruptedTrailingRowStart in 0..file.length()) {
        "Cannot resume ${file.path}: invalid trailing-row offset"
    }
    RandomAccessFile(file, "rw").use { it.setLength(resume.interruptedTrailingRowStart) }
}

private fun trailingRowStart(file: File): Long = RandomAccessFile(file, "r").use { input ->
    for (offset in file.length() - 1 downTo 0) {
        input.seek(offset)
        if (input.read() == '\n'.code) return@use offset + 1
    }
    0L
}

private fun endsWithNewline(file: File): Boolean =
    file.length() == 0L || RandomAccessFile(file, "r").use { input ->
        input.seek(file.length() - 1)
        input.read() == '\n'.code
    }

/** Moves that change the state. Draws and recycles are neither choices nor decisions. */
private fun choicesIn(line: List<Move>) = line.count { it != Move.Draw && it != Move.Recycle }

private fun outcomeName(outcome: SolveOutcome) = when (outcome) {
    is SolveOutcome.Solved -> "WINNABLE"
    is SolveOutcome.Unsolved -> "PROVEN_LOST"
    is SolveOutcome.Timeout -> "INCONCLUSIVE"
    is SolveOutcome.Error -> "ERROR"
}

fun benchmarkSolvers(
    seedCount: Int = 10_000,
    firstSeed: Long = 1L,
    parallelism: Int = 12,
    dfsNodes: Long = 300_000L,
    astarNodes: Long = 300_000L,
    astarMs: Long = 8_000L,
    outputPath: String = "tools/catalog/data/solver_benchmark.csv",
    progressPath: String = "tools/catalog/data/solver_benchmark_progress.txt",
    log: (String) -> Unit = ::println,
) {
    require(seedCount >= 0) { "seedCount must not be negative" }
    val output = File(outputPath).also { it.parentFile?.mkdirs() }
    val resume = readBenchmarkResumeState(output)
    if (resume.hasInterruptedTrailingRow) discardInterruptedTrailingRow(output, resume)
    val pendingSeeds = pendingBenchmarkSeeds(seedCount, firstSeed, resume.completedSeeds)
    val completedAtStart = seedCount - pendingSeeds.size

    log("benchmarking every solver over $seedCount deals on $parallelism threads")
    log("budgets: dfs $dfsNodes nodes, A* $astarNodes nodes / ${astarMs}ms per direction")
    log("rows stream in completion order, not seed order — sort the CSV if order matters")
    if (completedAtStart > 0) log("resuming: $completedAtStart durable rows found; ${pendingSeeds.size} seeds remaining")
    if (resume.hasInterruptedTrailingRow) log("discarded an interrupted trailing CSV row before resuming")

    val writer = FileWriter(output, true).buffered()
    if (output.length() == 0L) writer.appendLine(CSV_HEADER)
    else if (!endsWithNewline(output)) writer.newLine()
    writer.flush()
    val writeLock = Any()

    val done = AtomicInteger(completedAtStart)
    val next = AtomicInteger(0)
    val failures = AtomicInteger(0)
    val startedAt = System.nanoTime()
    val tally = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    fun bump(key: String) = tally.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()

    fun writeProgress(finished: Boolean) {
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
        val completed = done.get()
        val newlyCompleted = completed - completedAtStart
        val rate = if (elapsed > 0) newlyCompleted.toDouble() / elapsed else 0.0
        File(progressPath).writeText(
            buildString {
                appendLine(if (finished) "status: complete" else "status: running")
                appendLine("seeds: $completed / $seedCount")
                appendLine("elapsed: ${elapsed}s")
                appendLine("rate: %.1f seeds/s".format(rate))
                if (rate > 0 && !finished) appendLine("eta: %.0fs".format((seedCount - completed) / rate))
                if (failures.get() > 0) appendLine("failed this run: ${failures.get()}")
                appendLine()
                for (entry in tally.entries.sortedBy { it.key }) appendLine("${entry.key}: ${entry.value.get()}")
            },
        )
    }
    writeProgress(finished = false)

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        List(parallelism) {
            executor.submit {
                while (true) {
                    val offset = next.getAndIncrement()
                    if (offset >= pendingSeeds.size) break
                    val seed = pendingSeeds[offset]
                    val row = runCatching { measureSeed(seed, dfsNodes, astarNodes, astarMs, ::bump) }
                        .onFailure {
                            failures.incrementAndGet()
                            log("seed $seed failed: ${it.message ?: it::class.simpleName}")
                        }
                        .getOrNull()
                        ?: continue
                    synchronized(writeLock) {
                        writer.appendLine(row)
                        writer.flush()
                    }
                    if (done.incrementAndGet() % 250 == 0) writeProgress(finished = false)
                }
            }
        }.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(24, TimeUnit.HOURS)
        writer.close()
        writeProgress(finished = done.get() == seedCount && failures.get() == 0)
    }

    val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
    log("")
    for (entry in tally.entries.sortedBy { it.key }) log("${entry.key}: ${entry.value.get()}")
    log("")
    log("${done.get()} / $seedCount durable rows in $outputPath after ${elapsed}s")
}

internal fun measureSeed(
    seed: Long,
    dfsNodes: Long,
    astarNodes: Long,
    astarMs: Long,
    bump: (String) -> Unit,
): String {
    val cells = StringBuilder().append(seed)
    var lowest = ""
    val mismatched = ArrayList<String>()

    for (ruleset in Ruleset.entries) {
        val started = System.nanoTime()
        val search = runCatching { searchStrategyLine(seed, ruleset) }.getOrNull()
        val graded = search?.outcome == StrategySearchOutcome.WON
        val line = search?.takeIf { it.outcome == StrategySearchOutcome.WON }
            ?.let { replayableStrategyLine(seed, it.packedLine) }
        val ms = (System.nanoTime() - started) / 1_000_000
        if (graded != (line != null)) mismatched.add(ruleset.name)
        if (search?.outcome == StrategySearchOutcome.INCONCLUSIVE) mismatched.add("${ruleset.name}:CAPPED")
        if (graded && lowest.isEmpty()) lowest = ruleset.name
        cells.append(',').append(if (graded) 1 else 0)
            .append(',').append(line?.size ?: 0)
            .append(',').append(line?.let { choicesIn(it) } ?: 0)
            .append(',').append(ms)
        if (graded) bump("line wins: ${ruleset.name}")
    }
    cells.append(',').append(lowest).append(',').append(mismatched.joinToString("|"))
    if (lowest.isEmpty()) bump("line wins: none")
    if (mismatched.isNotEmpty()) bump("line mismatch")

    val dfsStarted = System.nanoTime()
    val dfs = runCatching { searchForWin(seed, rules = 0, maxNodes = dfsNodes, maxDepth = 400) }.getOrNull()
    val dfsMs = (System.nanoTime() - dfsStarted) / 1_000_000
    val dfsOutcome = when {
        dfs == null -> "ERROR"
        dfs.won -> "WINNABLE"
        dfs.exhausted -> "PROVEN_LOST"
        else -> "INCONCLUSIVE"
    }
    bump("dfs: $dfsOutcome")
    cells.append(',').append(dfsOutcome)
        .append(',').append(dfs?.nodes ?: 0)
        .append(',').append(dfs?.statesExplored ?: 0)
        .append(',').append(dfs?.lineChoices ?: 0)
        .append(',').append(dfs?.hardestMove ?: -1)
        .append(',').append(dfsMs)

    val board = dealGame(seed, D1S_SPIKE_VERSIONS)
    val limits = SolverLimits(maxNodes = astarNodes, maxDurationMs = astarMs)

    val ordering = BENCHMARK_ASTAR_ORDERING
    val full = runCatching { Solver.solve(board, limits, includeFoundationWithdrawal = true, ordering = ordering) }.getOrNull()
    val fullCert = (full as? SolveOutcome.Solved)?.certificate
    val fullName = full?.let { outcomeName(it) } ?: "ERROR"
    bump("A*: $fullName")
    cells.append(',').append(fullName)
        .append(',').append(full?.nodes ?: 0)
        .append(',').append(fullCert?.size ?: 0)
        .append(',').append(fullCert?.let { choicesIn(it) } ?: 0)
        .append(',').append(fullCert?.let { runCatching { optimizeWinningLine(board, it).size }.getOrDefault(0) } ?: 0)
        .append(',').append(full?.elapsedMs ?: 0)

    val noWithdraw = runCatching { Solver.solve(board, limits, includeFoundationWithdrawal = false, ordering = ordering) }.getOrNull()
    val noWithdrawCert = (noWithdraw as? SolveOutcome.Solved)?.certificate
    val noWithdrawName = noWithdraw?.let { outcomeName(it) } ?: "ERROR"
    cells.append(',').append(noWithdrawName)
        .append(',').append(noWithdraw?.nodes ?: 0)
        .append(',').append(noWithdrawCert?.size ?: 0)
        .append(',').append(noWithdraw?.elapsedMs ?: 0)

    // Only the exhausted case proves a withdrawal is required; a spent budget proves nothing,
    // which is the distinction a shipped Expert tier already got wrong once.
    val withdrawalNeeded = when (noWithdraw) {
        is SolveOutcome.Solved -> "no"
        is SolveOutcome.Unsolved -> if (full is SolveOutcome.Solved) "yes" else "deal_is_lost"
        else -> "unknown"
    }
    bump("withdrawal needed: $withdrawalNeeded")
    cells.append(',').append(withdrawalNeeded)

    val fast = FastBoard().apply { loadFrom(board) }
    val rootChoices = fast.generateChoices(IntArray(1024))
    var faceDown = 0
    for (column in 0 until org.finiteplay.klondike.board.TABLEAU_COLUMNS) faceDown += fast.columnDown[column]
    cells.append(',').append(rootChoices).append(',').append(faceDown)

    return cells.toString()
}
