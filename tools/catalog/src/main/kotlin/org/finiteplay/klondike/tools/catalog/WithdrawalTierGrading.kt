package org.finiteplay.klondike.tools.catalog

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.solver.search.LongHashSet
import org.finiteplay.klondike.solver.search.LongIntHashMap

/**
 * Grades Expert, and does **not** grade it on robustness.
 *
 * Every tier through Hard asks the same question — how much player error survives — and
 * `TODO.md` predicted that question would run out: tolerance thins by an order of magnitude
 * per tier, so at Expert there is almost none left to measure. Expert is therefore graded on
 * two properties of the *board*, both of which are facts about the game tree rather than
 * about a classifier:
 *
 * 1. **Foundation withdrawal is strictly required.** No winning line avoids taking a banked
 *    card back. Established by ablation, exactly as the glossary defines the term: remove
 *    withdrawal from the move set and check that no win survives.
 * 2. **The line has between 2 and 5 critical choices.** A critical choice is a move after
 *    which no winning path remains. Fewer than two and the deal offers nothing to get wrong;
 *    more than five and it is a minefield rather than a hard puzzle.
 *
 * Criterion 1 subsumes the disjointness gate the lower tiers need, and more cheaply than
 * running it. Every move Trivial through Hard can make is a move Expert can make without
 * withdrawing, so "no win without withdrawal" already says no lower ruleset can win the deal —
 * not merely that its priority order fails to find the win, but that the win is not there.
 */

/** Why a deal did or did not land at Expert — bucketed so a zero yield says which gate closed. */
enum class WithdrawalTierReason { QUALIFIES, NO_WINNING_LINE, WON_BY_LOWER_TIER, WINNABLE_WITHOUT_WITHDRAWAL, OUTSIDE_BAND, INCONCLUSIVE }

data class WithdrawalTierVerdict(
    val seed: Long,
    val reason: WithdrawalTierReason,
    /** Critical choices offered along the reference line — moves that would throw the game away. */
    val criticalChoices: Int,
    /** States on the line at which at least one critical choice was on offer. */
    val criticalPoints: Int,
    /** Whether the reference line actually plays a withdrawal, whatever the ablation says. */
    val withdrawsOnLine: Boolean = false,
    val detail: String,
) {
    val qualifies: Boolean get() = reason == WithdrawalTierReason.QUALIFIES
}

private class StateBudgetExceeded : RuntimeException(null, null, false, false)

/**
 * Answers "can a win be reached from here" over one ruleset's move set, with a memo that stays
 * correct in the presence of cycles.
 *
 * Withdrawal is what makes cycles possible: banking a card and taking it back returns the
 * board to a state it has already been in, so the graph is no longer the DAG every tier below
 * Expert relied on. A plain visited-set DFS still answers correctly **for its root**, but the
 * values it caches for intermediate states can be pessimistic — a state whose only route to a
 * win runs back through a node still on the stack gets recorded as losing.
 *
 * That is harmless when the root is the only question asked, and wrong here, because counting
 * critical choices asks the question at every move of every state along a line. So a `false`
 * is cached only when the subtree that produced it never cut at an on-stack node; a `true` is
 * always safe to cache, since it is a witness.
 */
private class WinReachability(
    private val ruleset: Ruleset,
    private val maxStates: Int,
    /**
     * When only the root's answer is wanted, proven-**winning** states need not be stored: the
     * first win unwinds the whole search, so no ancestor ever reads one back. The ablation is
     * exactly that query, and dropping the value array takes a slot from twelve bytes to
     * eight — a third more states for the same memory, on the one search that runs out of it.
     *
     * The criticality pass asks at every move of every state on a line, so it needs both
     * answers and keeps the map.
     */
    lossesOnly: Boolean = false,
) {

    // 0 unknown, 1 proven losing, 2 proven winning. Two parallel primitive arrays cost 12
    // bytes per slot and, at the 30–60% load this grows between, 20–40 bytes per live entry;
    // a boxed HashMap<Long, Boolean> costs about 2.5x that, its 32-byte Node and boxed key
    // dwarfing the eight bytes of fingerprint being stored.
    //
    // It is not enough. Eight million entries still needs a 2^24-slot table — 192 MB, and
    // 288 MB across the resize that reaches it, since grow() holds both arrays while it
    // rehashes. Every worker carries its own memo, so twelve threads is where the ablation
    // actually runs out of room rather than out of time.
    private val decided = if (lossesOnly) null else LongIntHashMap(1 shl 16)
    private val losses = if (lossesOnly) LongHashSet(1 shl 16) else null
    private val onStack = HashSet<Long>()
    private val buffers = Array(FastBoard.MAX_DEPTH) { IntArray(160) }
    private var expanded = 0
    private var cutOnStack = false

    val statesExplored: Int get() = expanded

    private companion object {
        const val LOSING = 1
        const val WINNING = 2
    }

    fun canWin(board: FastBoard, level: Int = 0): Boolean {
        if (board.isWon()) return true
        val key = board.fingerprint()
        if (losses != null) {
            if (losses.contains(key)) return false
        } else {
            when (decided!!.getOrDefault(key, 0)) {
                LOSING -> return false
                WINNING -> return true
            }
        }
        if (key in onStack) {
            cutOnStack = true
            return false
        }
        if (++expanded > maxStates) throw StateBudgetExceeded()

        onStack.add(key)
        val enclosingCut = cutOnStack
        cutOnStack = false

        val taps = buffers[level]
        val count = board.generateTaps(taps, ruleset)
        var won = false
        for (index in 0 until count) {
            board.make(taps[index])
            won = try {
                canWin(board, level + 1)
            } finally {
                board.unmake()
            }
            if (won) break
        }

        onStack.remove(key)
        val cutHere = cutOnStack
        cutOnStack = enclosingCut || cutHere
        if (losses != null) {
            if (!won && !cutHere) losses.add(key)
        } else if (won || !cutHere) {
            decided!!.put(key, if (won) WINNING else LOSING)
        }
        return won
    }
}

/**
 * Searches for a winning line that never withdraws, over Hard's move set.
 *
 * Returns true when one was **found**, false when the whole graph was exhausted without one,
 * and null when the budget ran out first. The distinction matters and must not be collapsed:
 * false is a proof, null is only an absence of evidence.
 *
 * The tier gate treats null as acceptable and false as ideal, but never treats *true* as
 * acceptable — a deal with a quickly-found withdrawal-free win is one a player will also find,
 * which is exactly how a shipped Expert hand was won without ever touching a foundation.
 */
fun winnableWithoutWithdrawal(seed: Long, maxStates: Int = 2_000_000): Boolean? {
    val board = FastBoard().apply { loadFrom(dealGame(seed, D1S_SPIKE_VERSIONS)) }
    return try {
        WinReachability(Ruleset.MEDIUM, maxStates, lossesOnly = true).canWin(board)
    } catch (_: StateBudgetExceeded) {
        null
    } catch (_: StackOverflowError) {
        null
    }
}

/**
 * Checks one deal against both Expert criteria.
 *
 * The cheap criterion runs first: ablating withdrawal and finding a win disqualifies the deal
 * outright, and most deals die there without the line ever being walked.
 */
fun checkWithdrawalTier(
    seed: Long,
    maxStates: Int = 300_000,
    maxSteps: Int = 400,
    /** Set false to survey the candidate population without paying for the proof of a negative. */
    ablate: Boolean = true,
): WithdrawalTierVerdict {
    // Gate order is the whole difficulty of grading this tier. The ablation — proving that
    // *no* winning line avoids withdrawal — is a proof of a negative over Hard's move graph,
    // and that graph runs past eight million states on ordinary deals. It is affordable only
    // because it runs last, on the few seeds that already look like candidates.
    if (!undeviatingLineWins(seed, Ruleset.HARD)) {
        return WithdrawalTierVerdict(seed, WithdrawalTierReason.NO_WINNING_LINE, 0, 0, false, "Expert's own order does not win it")
    }
    for (lower in Ruleset.HARD.tiersBelow) {
        if (undeviatingLineWins(seed, lower)) {
            return WithdrawalTierVerdict(seed, WithdrawalTierReason.WON_BY_LOWER_TIER, 0, 0, false, "$lower's order already wins it")
        }
    }

    val start = dealGame(seed, D1S_SPIKE_VERSIONS)

    // --- criterion 1: no winning line avoids withdrawal ---
    val ablated = FastBoard().apply { loadFrom(start) }
    val withoutWithdrawal = if (!ablate) false else try {
        WinReachability(Ruleset.MEDIUM, maxStates, lossesOnly = true).canWin(ablated)
    } catch (_: StateBudgetExceeded) {
        return WithdrawalTierVerdict(seed, WithdrawalTierReason.INCONCLUSIVE, 0, 0, false, "ablation exceeded $maxStates states")
    } catch (_: StackOverflowError) {
        return WithdrawalTierVerdict(seed, WithdrawalTierReason.INCONCLUSIVE, 0, 0, false, "ablation exceeded the recursion depth")
    }
    if (withoutWithdrawal) {
        return WithdrawalTierVerdict(seed, WithdrawalTierReason.WINNABLE_WITHOUT_WITHDRAWAL, 0, 0, false, "winnable without withdrawing")
    }

    // --- criterion 2: count what could be thrown away along the line ---
    val reach = WinReachability(Ruleset.HARD, maxStates)
    val board = FastBoard().apply { loadFrom(start) }
    val onPath = HashSet<Long>()
    var criticalChoices = 0
    var criticalPoints = 0
    var steps = 0
    var withdrawsOnLine = false

    try {
        while (!board.isWon()) {
            if (steps++ > maxSteps) return WithdrawalTierVerdict(seed, WithdrawalTierReason.NO_WINNING_LINE, 0, 0, false, "line ran past $maxSteps choices")
            onPath.add(board.fingerprint())

            val taps = IntArray(160)
            val count = board.generateTaps(taps, Ruleset.HARD)
            if (count == 0) return WithdrawalTierVerdict(seed, WithdrawalTierReason.NO_WINNING_LINE, 0, 0, false, "dead end on the reference line")

            // The reference is the best-ranked tap that does not return to this line, exactly
            // as the robustness search picks it. Everything else available here is a choice
            // the player could have made instead, and a critical one if it kills the win.
            var reference = -1
            val loops = BooleanArray(count)
            for (index in 0 until count) {
                board.make(taps[index])
                loops[index] = board.fingerprint() in onPath
                board.unmake()
                if (!loops[index] && (reference < 0 || board.tapRank(taps[index], Ruleset.HARD) < board.tapRank(taps[reference], Ruleset.HARD))) {
                    reference = index
                }
            }
            if (reference < 0) return WithdrawalTierVerdict(seed, WithdrawalTierReason.NO_WINNING_LINE, 0, 0, false, "every continuation loops")

            var criticalHere = 0
            for (index in 0 until count) {
                if (index == reference || loops[index]) continue
                board.make(taps[index])
                val survives = try {
                    reach.canWin(board)
                } finally {
                    board.unmake()
                }
                if (!survives) criticalHere++
            }
            criticalChoices += criticalHere
            if (criticalHere > 0) criticalPoints++

            if (FastBoard.tapOf(taps[reference]) == FastBoard.TAP_WITHDRAW) withdrawsOnLine = true
            board.make(taps[reference])
        }
    } catch (_: StateBudgetExceeded) {
        return WithdrawalTierVerdict(seed, WithdrawalTierReason.INCONCLUSIVE, 0, 0, false, "criticality exceeded $maxStates states")
    } catch (_: StackOverflowError) {
        return WithdrawalTierVerdict(seed, WithdrawalTierReason.INCONCLUSIVE, 0, 0, false, "criticality exceeded the recursion depth")
    }

    val qualifies = criticalPoints in MIN_CRITICAL..MAX_CRITICAL
    val detail = if (qualifies) {
        "qualifies with $criticalPoints critical point(s)"
    } else {
        "withdrawal required but $criticalPoints critical point(s) is outside $MIN_CRITICAL..$MAX_CRITICAL"
    }
    return WithdrawalTierVerdict(
        seed,
        if (qualifies) WithdrawalTierReason.QUALIFIES else WithdrawalTierReason.OUTSIDE_BAND,
        criticalChoices,
        criticalPoints,
        withdrawsOnLine,
        detail,
    )
}

/** The band `DIFFICULTY_LEVELS.md` requires of an Expert deal: hard, but not a minefield. */
const val MIN_CRITICAL = 2
const val MAX_CRITICAL = 5

/**
 * Scans seeds for Expert, streaming qualifying ones to [outputPath] as `seed,criticalPoints`.
 *
 * The CSV's second column is the **critical-point count**, not a deviation budget: Expert is
 * not graded on robustness, and the tier is ordered fewest-critical-points first so it opens
 * on its most forgiving hands, the same progression every other tier has.
 */
fun calibrateWithdrawalTier(
    seedCount: Int = 2000,
    firstSeed: Long = 1L,
    parallelism: Int = 4,
    maxStates: Int = 300_000,
    outputPath: String? = null,
    log: (String) -> Unit = ::println,
) {
    log("grading $seedCount seeds for Expert on $parallelism threads")
    log("criterion 1: no winning line avoids withdrawal; criterion 2: $MIN_CRITICAL..$MAX_CRITICAL critical points")

    val buckets = WithdrawalTierReason.entries.associateWith { AtomicInteger(0) }
    val details = java.util.concurrent.ConcurrentHashMap<String, AtomicInteger>()
    val kept = ConcurrentLinkedQueue<WithdrawalTierVerdict>()
    val done = AtomicInteger(0)
    val next = AtomicInteger(0)
    val startedAt = System.nanoTime()

    val writer = outputPath?.let { path ->
        java.io.File(path).also { it.parentFile?.mkdirs() }.printWriter().also { it.println("seed,criticalPoints"); it.flush() }
    }
    val writeLock = Any()

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val workers = List(parallelism) {
            executor.submit {
                while (true) {
                    val offset = next.getAndIncrement()
                    if (offset >= seedCount) break
                    val seed = firstSeed + offset
                    val verdict = runCatching { checkWithdrawalTier(seed, maxStates) }.getOrElse {
                        WithdrawalTierVerdict(seed, WithdrawalTierReason.INCONCLUSIVE, 0, 0, false, "error: ${it::class.simpleName}")
                    }
                    buckets.getValue(verdict.reason).incrementAndGet()
                    details.computeIfAbsent(verdict.detail.substringBefore(';')) { AtomicInteger(0) }.incrementAndGet()
                    if (verdict.qualifies) {
                        kept.add(verdict)
                        if (writer != null) {
                            synchronized(writeLock) { writer.println("$seed,${verdict.criticalPoints}"); writer.flush() }
                        }
                    }
                    val count = done.incrementAndGet()
                    if (count % 20_000 == 0) {
                        val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
                        log("...$count/$seedCount after ${elapsed}s, ${kept.size} kept")
                    }
                }
            }
        }
        workers.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(24, TimeUnit.HOURS)
        writer?.close()
    }

    val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
    val byPoints = kept.groupingBy { it.criticalPoints }.eachCount().toSortedMap()
    log("")
    for ((reason, count) in buckets) log("$reason | ${count.get()}")
    log("")
    for ((detail, count) in details.entries.sortedByDescending { it.value.get() }.take(8)) log("  $detail: ${count.get()}")
    for ((points, count) in byPoints) log("  $points critical point(s): $count")
    log("")
    log("scanned $seedCount seeds in ${elapsed}s")
    if (outputPath != null) log("wrote ${kept.size} seeds to $outputPath (streamed during the scan)")
}

/**
 * Surveys the candidate population **without** the ablation, to find out what an Expert tier
 * could contain before deciding what to require of it.
 *
 * A candidate is a deal Expert's order wins and no lower order does. The ablation is skipped
 * because it proves a negative and costs millions of states; what is reported instead is what
 * the reference line actually does — whether it withdraws at all, and how many critical points
 * it passes. Those two distributions are what say whether the specified criteria are reachable.
 */
fun surveyWithdrawalCandidates(
    seedCount: Int = 50_000,
    firstSeed: Long = 1L,
    parallelism: Int = 12,
    maxStates: Int = 2_000_000,
    /** Qualifying seeds, appended and flushed as they are found. */
    outputPath: String? = null,
    /**
     * Rewritten every [progressEvery] seeds with the running tally.
     *
     * A file rather than the log, because `gradlew -q` buffers a long run's stdout to the end:
     * the first version of this survey printed two header lines and produced an empty output
     * file for an hour and forty minutes, so there was no way to tell progress from a hang
     * except by watching the process's CPU time.
     */
    progressPath: String? = null,
    progressEvery: Int = 20_000,
    log: (String) -> Unit = ::println,
) {
    log("surveying $seedCount seeds: of the deals Expert wins and no lower tier does,")
    log("how many withdraw on the line, and how many critical points do they pass?")

    val candidates = AtomicInteger(0)
    val withdrew = AtomicInteger(0)
    val inconclusive = AtomicInteger(0)
    val points = java.util.concurrent.ConcurrentHashMap<Int, AtomicInteger>()
    val inBandAndWithdrawing = ConcurrentLinkedQueue<Long>()
    val next = AtomicInteger(0)
    val done = AtomicInteger(0)
    val withdrawalAvoidable = AtomicInteger(0)
    val startedAt = System.nanoTime()

    // Streamed and flushed per hit, for the same reason the calibration scans are: a survey
    // that only reports at the end loses everything to a crash, an OOM, or an operator who
    // decides an hour in that it is taking too long.
    val writer = outputPath?.let { path ->
        java.io.File(path).also { it.parentFile?.mkdirs() }
            .printWriter().also { it.println("seed,criticalPoints,criticalChoices"); it.flush() }
    }
    val writeLock = Any()

    fun writeProgress(finished: Boolean) {
        val path = progressPath ?: return
        val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
        val completed = done.get()
        val rate = if (elapsed > 0) completed / elapsed else 0
        java.io.File(path).writeText(
            buildString {
                appendLine(if (finished) "status: complete" else "status: running")
                appendLine("seeds: $completed / $seedCount")
                appendLine("elapsed: ${elapsed}s")
                appendLine("rate: $rate seeds/s")
                if (rate > 0 && !finished) appendLine("eta: ${(seedCount - completed) / rate}s")
                appendLine("candidates: ${candidates.get()}")
                appendLine("  withdrawing on the line: ${withdrew.get()}")
                appendLine("  no verdict at $maxStates states: ${inconclusive.get()}")
                appendLine("  withdrawal-free win found, rejected: ${withdrawalAvoidable.get()}")
                appendLine("qualifying (withdraws, $MIN_CRITICAL..$MAX_CRITICAL critical points, no withdrawal-free win found): ${inBandAndWithdrawing.size}")
                for (entry in points.entries.sortedBy { it.key }) appendLine("  ${entry.key} critical point(s): ${entry.value.get()}")
            },
        )
    }
    writeProgress(finished = false)

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val workers = List(parallelism) {
            executor.submit {
                while (true) {
                    val offset = next.getAndIncrement()
                    if (offset >= seedCount) break
                    val seed = firstSeed + offset
                    val verdict = runCatching { checkWithdrawalTier(seed, maxStates, ablate = false) }.getOrNull() ?: continue
                    when (verdict.reason) {
                        WithdrawalTierReason.NO_WINNING_LINE, WithdrawalTierReason.WON_BY_LOWER_TIER -> Unit
                        WithdrawalTierReason.INCONCLUSIVE -> { candidates.incrementAndGet(); inconclusive.incrementAndGet() }
                        else -> {
                            candidates.incrementAndGet()
                            if (verdict.withdrawsOnLine) withdrew.incrementAndGet()
                            points.computeIfAbsent(verdict.criticalPoints) { AtomicInteger(0) }.incrementAndGet()
                            // The last gate, and the one a shipped tier was missing: a deal
                            // whose withdrawal-free win a bounded search finds in milliseconds
                            // is a deal a player finds too. Run only on the fraction of a
                            // percent that reach here, so its cost never touches the scan rate.
                            val avoidable = winnableWithoutWithdrawal(seed, maxStates)
                            if (avoidable == true) withdrawalAvoidable.incrementAndGet()
                            if (verdict.withdrawsOnLine && verdict.criticalPoints in MIN_CRITICAL..MAX_CRITICAL && avoidable != true) {
                                inBandAndWithdrawing.add(seed)
                                if (writer != null) {
                                    synchronized(writeLock) {
                                        writer.println("$seed,${verdict.criticalPoints},${verdict.criticalChoices}")
                                        writer.flush()
                                    }
                                }
                            }
                        }
                    }
                    if (done.incrementAndGet() % progressEvery == 0) writeProgress(finished = false)
                }
            }
        }
        workers.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(24, TimeUnit.HOURS)
        writer?.close()
        writeProgress(finished = true)
    }

    val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
    log("")
    log("candidates (Expert wins, no lower tier does) | ${candidates.get()}")
    log("  of those, line plays a withdrawal | ${withdrew.get()}")
    log("  of those, no verdict at $maxStates states | ${inconclusive.get()}")
    log("  of those, a withdrawal-free win was found — rejected | ${withdrawalAvoidable.get()}")
    log("critical points on the line:")
    for (entry in points.entries.sortedBy { it.key }) log("  ${entry.key}: ${entry.value.get()}")
    log("")
    log("withdrawing AND in the $MIN_CRITICAL..$MAX_CRITICAL band | ${inBandAndWithdrawing.size}")
    log(inBandAndWithdrawing.sorted().take(30).joinToString(", ") { "${it}L" })
    log("scanned $seedCount seeds in ${elapsed}s")
}
