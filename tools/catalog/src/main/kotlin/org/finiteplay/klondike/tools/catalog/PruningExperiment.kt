package org.finiteplay.klondike.tools.catalog

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.finiteplay.klondike.board.dealGame

/**
 * Measures whether a candidate strategy rule is **safe** — and how much tree it saves.
 *
 * This exists because of the one asymmetry that has held throughout: finding a win terminates
 * in milliseconds, proving no win exists does not terminate at all. Every attempt in this
 * package to establish a rule by *proof* has run into that wall. Establishing one by
 * *measurement* does not, because both halves of the experiment are positive searches:
 *
 * - search for a win with the full move set,
 * - search for a win with the rule applied,
 * - a rule that loses wins is unsafe; a rule that keeps them and shrinks the search is a
 *   strategy worth writing into the spec.
 *
 * The result is empirical, never a proof: "over N deals this rule cost no wins" is evidence,
 * and the sample size is reported so it can be read as such. That is deliberately weaker than
 * the exchange argument behind safe-foundation forcing, and honest about the difference.
 */
data class WinSearchResult(
    val won: Boolean,
    val nodes: Long,
    /** Distinct states visited. Not the length of anything — see [lineChoices]. */
    val statesExplored: Int,
    /**
     * Choices on the winning line, or 0 when no win was found.
     *
     * Separate from [statesExplored] because the two were once the same field, and the CSV
     * column fed from it reported states as though they were a line length. It went unnoticed
     * because their medians happen to coincide at around ninety on an easy deal.
     */
    val lineChoices: Int,
    /** Highest move category the winning line needed — a cheap read on which strategies it required. */
    val hardestMove: Int,
    /**
     * True when the search finished the whole reachable space rather than running out of
     * budget. With **sound** prunings only, `!won && exhausted` is a proof the deal cannot be
     * won; with a merely-measured rule such as the productive-setup filter it proves nothing,
     * and the caller has to know which it asked for.
     */
    val exhausted: Boolean = false,
    /** A depth cap was hit, so nothing was exhausted whatever the node count says. */
    val depthCapped: Boolean = false,
    /**
     * The winning line as packed `FastBoard` choices, empty when nothing won.
     *
     * Collected on the way back up the successful branch, so it costs one append per choice on
     * the line and nothing at all on a failed search. Needed because a deal only this search
     * wins still has to ship a solution, and the count alone cannot be replayed.
     */
    val winningLine: List<Int> = emptyList(),
)

/** Three-way verdict: a win, a proof there is none, or neither. */
enum class DealClass { WIN, UNWINNABLE, UNKNOWN }

/**
 * Depth-first search for **a** win, ordered by move category so the cheap moves are tried
 * first. Not a shortest-path search: the question here is how much of the tree a rule set has
 * to walk before a win appears, and ordering by category is what a player does.
 *
 * Safe-foundation forcing is always on. It is the one pruning in this package with a proof
 * behind it (the exchange argument in `SearchState.kt`), and measured on the census it cut
 * states eighteenfold, so leaving it off would make every comparison below noise.
 */
fun searchForWin(
    seed: Long,
    rules: Int = 0,
    maxNodes: Long = 200_000L,
    maxDepth: Int = 250,
    foundationSpread: Int = 3,
    deferWithheld: Boolean = false,
): WinSearchResult {
    val board = FastBoard().apply {
        loadFrom(dealGame(seed, D1S_SPIKE_VERSIONS))
        foundationSpreadLimit = foundationSpread
        this.deferWithheld = deferWithheld
    }
    val seen = HashSet<Long>()
    val buffers = Array(maxDepth + 2) { IntArray(1024) }
    var nodes = 0L
    var hardest = 0
    var ranOut = false
    var hitDepth = false
    var winChoices = 0
    // Filled deepest-choice-first as the winning branch unwinds, so it is reversed at the end.
    val winPath = ArrayList<Int>()

    fun descend(depth: Int): Boolean {
        if (board.isWon()) {
            // Reached after exactly `depth` choices, which is the line's length.
            winChoices = depth
            return true
        }
        if (depth >= maxDepth) {
            hitDepth = true
            return false
        }
        if (nodes >= maxNodes) {
            ranOut = true
            return false
        }
        if (!seen.add(board.fingerprint())) return false
        nodes++

        val moves = buffers[depth]
        val count = board.generateChoices(
            moves,
            includeWithdrawal = true,
            canonicaliseEmptyColumns = true,
            forceSafeFoundation = true,
            rules = rules,
        )
        // Cheap moves first: bank, reveal, free a column, pile, setup, withdraw. Insertion sort
        // because the list is short and this runs at every node.
        for (i in 1 until count) {
            val move = moves[i]
            val key = FastBoard.tapOf(move)
            var j = i - 1
            while (j >= 0 && FastBoard.tapOf(moves[j]) > key) {
                moves[j + 1] = moves[j]
                j--
            }
            moves[j + 1] = move
        }
        for (i in 0 until count) {
            val move = moves[i]
            board.make(move)
            val won = try {
                descend(depth + 1)
            } finally {
                if (!board.isWon()) board.unmake() else board.unmake()
            }
            if (won) {
                if (FastBoard.tapOf(move) > hardest) hardest = FastBoard.tapOf(move)
                winPath.add(move)
                return true
            }
        }
        return false
    }

    val won = runCatching { descend(0) }.getOrElse {
        ranOut = true
        false
    }
    return WinSearchResult(
        won = won,
        nodes = nodes,
        statesExplored = seen.size,
        lineChoices = if (won) winChoices else 0,
        hardestMove = hardest,
        exhausted = !ranOut && !hitDepth,
        depthCapped = hitDepth,
        winningLine = if (won) winPath.asReversed().toList() else emptyList(),
    )
}

private val RULE_NAMES = listOf(
    FastBoard.RULE_FOUNDATION_SPREAD to "(a) hold a card more than 3 above the lowest foundation",
    FastBoard.RULE_KING_WHEN_COLUMNS_SPARE to "(d) force a King into an empty column when every empty is spoken for",
    FastBoard.RULE_NO_IDLE_LOW_PILE to "(e) skip a 2-5 from the pile that nothing can stack on",
    FastBoard.RULE_NO_IDLE_SETUP to "(f) skip a tableau shuffle that uncovers nothing useful",
)

/**
 * Runs every candidate rule against a baseline over the same deals, one rule at a time and
 * then all together.
 *
 * One at a time matters: a combination that loses wins says nothing about which member did it,
 * and the whole point is to find out which of these are real strategies.
 */
fun evaluatePruningRules(
    seedCount: Int = 500,
    firstSeed: Long = 1L,
    maxNodes: Long = 200_000L,
    parallelism: Int = 8,
    log: (String) -> Unit = ::println,
) {
    val configurations = buildList {
        add(0 to "baseline (safe-foundation forcing only)")
        addAll(RULE_NAMES)
        add(RULE_NAMES.fold(0) { acc, (bit, _) -> acc or bit } to "all rules together")
    }

    log("evaluating ${configurations.size} rule sets over $seedCount deals, $maxNodes nodes each")
    log("a rule is safe here if it costs no wins; the saving is what it buys\n")
    log("%-72s | %6s | %10s | %8s | %8s".format("rule set", "wins", "mean nodes", "mean states", "mean line"))

    var baselineWins = 0
    var baselineNodes = 0L
    val baselineWon = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()

    for ((mask, name) in configurations) {
        val wins = AtomicInteger(0)
        val nodes = AtomicLong(0)
        val states = AtomicLong(0)
        val lineChoices = AtomicLong(0)
        val lost = java.util.concurrent.ConcurrentLinkedQueue<Long>()
        val next = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(parallelism)
        try {
            List(parallelism) {
                executor.submit {
                    while (true) {
                        val offset = next.getAndIncrement()
                        if (offset >= seedCount) break
                        val seed = firstSeed + offset
                        val result = runCatching { searchForWin(seed, mask, maxNodes) }.getOrNull() ?: continue
                        nodes.addAndGet(result.nodes)
                        if (result.won) {
                            wins.incrementAndGet()
                            states.addAndGet(result.statesExplored.toLong())
                            lineChoices.addAndGet(result.lineChoices.toLong())
                            if (mask == 0) baselineWon[seed] = true
                        } else if (mask != 0 && baselineWon[seed] == true) {
                            lost.add(seed)
                        }
                    }
                }
            }.forEach { it.get() }
        } finally {
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.HOURS)
        }

        val won = wins.get()
        log(
            "%-72s | %6d | %10d | %8d | %8d".format(
                name, won, nodes.get() / seedCount,
                if (won > 0) states.get() / won else 0,
                if (won > 0) lineChoices.get() / won else 0,
            ),
        )
        if (mask == 0) {
            baselineWins = won
            baselineNodes = nodes.get()
        } else {
            // Safety is the count of deals the baseline won and this rule set did not — never
            // the difference of the totals. Pruning reorders the search, so a rule can find
            // wins the baseline missed inside the node cap while losing others; netting those
            // off would report an unsafe rule as harmless.
            val broke = lost.size
            val saving = if (nodes.get() > 0) baselineNodes.toDouble() / nodes.get() else 0.0
            log(
                "    -> %s | %.2fx search | net %+d found%s".format(
                    if (broke == 0) "SAFE on this sample" else "UNSAFE: broke $broke deal(s)",
                    saving,
                    won - baselineWins,
                    if (lost.isEmpty()) "" else " | broke: ${lost.sorted().take(6)}",
                ),
            )
        }
    }
}

/**
 * Sweeps the foundation-restraint threshold, under both semantics.
 *
 * At threshold 13 the rule can never fire, so that row is the baseline and no separate control
 * is needed. Reading down a column shows where restraint stops helping and starts costing; the
 * two semantics answer different questions, and conflating them is how "don't rush the
 * foundation" survives as advice without a number attached.
 */
fun sweepFoundationSpread(
    seedCount: Int = 500,
    firstSeed: Long = 1L,
    maxNodes: Long = 200_000L,
    parallelism: Int = 8,
    withProductiveSetup: Boolean = true,
    log: (String) -> Unit = ::println,
) {
    val base = if (withProductiveSetup) FastBoard.RULE_NO_IDLE_SETUP else 0
    log("sweeping the foundation-spread threshold over $seedCount deals, $maxNodes nodes each")
    log("base rules: ${if (withProductiveSetup) "productive-setup filter on (measured safe)" else "none"}")
    log("threshold 13 can never fire, so that row is the control\n")
    log("%-9s | %-6s | %6s | %6s | %11s | %s".format("semantics", "spread", "wins", "broke", "mean nodes", "vs control"))

    val control = HashMap<String, Pair<Int, Long>>()
    for (defer in listOf(false, true)) {
        val name = if (defer) "defer" else "skip"
        var controlWins = 0
        var controlNodes = 0L
        val controlWon = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()
        for (spread in listOf(13, 8, 6, 5, 4, 3, 2, 1)) {
            val rules = base or FastBoard.RULE_FOUNDATION_SPREAD
            val wins = AtomicInteger(0)
            val nodes = AtomicLong(0)
            val broke = java.util.concurrent.ConcurrentLinkedQueue<Long>()
            val next = AtomicInteger(0)
            val executor = Executors.newFixedThreadPool(parallelism)
            try {
                List(parallelism) {
                    executor.submit {
                        while (true) {
                            val offset = next.getAndIncrement()
                            if (offset >= seedCount) break
                            val seed = firstSeed + offset
                            val r = runCatching {
                                searchForWin(seed, rules, maxNodes, foundationSpread = spread, deferWithheld = defer)
                            }.getOrNull() ?: continue
                            nodes.addAndGet(r.nodes)
                            if (r.won) {
                                wins.incrementAndGet()
                                if (spread == 13) controlWon[seed] = true
                            } else if (spread != 13 && controlWon[seed] == true) {
                                broke.add(seed)
                            }
                        }
                    }
                }.forEach { it.get() }
            } finally {
                executor.shutdown()
                executor.awaitTermination(2, TimeUnit.HOURS)
            }
            if (spread == 13) {
                controlWins = wins.get()
                controlNodes = nodes.get()
                control[name] = controlWins to controlNodes
            }
            val delta = if (spread == 13) "control" else {
                "%+d wins, %.2fx search".format(wins.get() - controlWins, controlNodes.toDouble() / nodes.get().coerceAtLeast(1))
            }
            log(
                "%-9s | %-6s | %6d | %6d | %11d | %s".format(
                    name, if (spread == 13) "off" else "<=$spread", wins.get(), broke.size, nodes.get() / seedCount, delta,
                ),
            )
        }
        log("")
    }
}

/**
 * Sweeps the same threshold against **undeviating ruleset play** rather than against search.
 *
 * This is the experiment that governs the tiers, and it asks a different question from
 * [sweepFoundationSpread]. A search can back out of a premature bank; a player following a
 * priority order to the end cannot, so restraint that costs a search time may still be what
 * saves a line. Measuring the two together would have conflated them.
 */
fun sweepRulesetSpread(
    seedCount: Int = 20_000,
    firstSeed: Long = 1L,
    parallelism: Int = 8,
    log: (String) -> Unit = ::println,
) {
    log("sweeping the restraint threshold against undeviating play, $seedCount deals")
    log("counts are deals each tier's line wins outright, with no backtracking\n")
    log("%-8s | %6s | %7s | %7s | %7s".format("spread", "Easy", "Medium", "Hard", "Expert"))

    val original = FastBoard.DEFAULT_FOUNDATION_SPREAD
    try {
        for (spread in listOf(13, 8, 6, 5, 4, 3, 2, 1)) {
            FastBoard.DEFAULT_FOUNDATION_SPREAD = spread
            val counts = listOf(Ruleset.EASY, Ruleset.MEDIUM, Ruleset.HARD).map { ruleset ->
                val wins = AtomicInteger(0)
                val next = AtomicInteger(0)
                val executor = Executors.newFixedThreadPool(parallelism)
                try {
                    List(parallelism) {
                        executor.submit {
                            while (true) {
                                val offset = next.getAndIncrement()
                                if (offset >= seedCount) break
                                val seed = firstSeed + offset
                                if (runCatching { undeviatingLineWins(seed, ruleset) }.getOrDefault(false)) {
                                    wins.incrementAndGet()
                                }
                            }
                        }
                    }.forEach { it.get() }
                } finally {
                    executor.shutdown()
                    executor.awaitTermination(1, TimeUnit.HOURS)
                }
                wins.get()
            }
            log(
                "%-8s | %6d | %7d | %7d | %7d".format(
                    if (spread == 13) "off" else "<=$spread", counts[0], counts[1], counts[2], counts[3],
                ),
            )
        }
    } finally {
        FastBoard.DEFAULT_FOUNDATION_SPREAD = original
    }
    log("\nrestored the shipped threshold: $original")
}

/**
 * Partitions deals three ways, and then looks at what is left.
 *
 * The pruning experiments above only ever measured deals a search already won, which leaves
 * the interesting population — deals with no win found *and* no proof there is none —
 * completely unexamined. That residue is where both the catalog's yield and every timing-out
 * proof in this package actually live.
 *
 * Two passes, and the difference between them is the point:
 *
 * - **sound only** — safe-foundation forcing, partial-order reduction, empty-column
 *   canonicalisation. Each has an argument behind it, so exhausting the space here is a real
 *   proof of unwinnability.
 * - **plus the measured filter** — the productive-setup rule, which cost no wins over a
 *   thousand deals but has no proof. It may crack deals the sound pass could not; a "no win"
 *   from it is not a proof and is never counted as one.
 */
fun classifyDeals(
    seedCount: Int = 1000,
    firstSeed: Long = 1L,
    maxNodes: Long = 1_000_000L,
    parallelism: Int = 8,
    log: (String) -> Unit = ::println,
) {
    log("classifying $seedCount deals, $maxNodes nodes each")
    log("sound prunings prove unwinnability; the measured filter only finds wins\n")

    val sound = java.util.concurrent.ConcurrentHashMap<Long, DealClass>()
    fun pass(rules: Int, label: String, only: Set<Long>?): Triple<Int, Int, Int> {
        val win = AtomicInteger(0)
        val dead = AtomicInteger(0)
        val unknown = AtomicInteger(0)
        val nodes = AtomicLong(0)
        val next = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(parallelism)
        try {
            List(parallelism) {
                executor.submit {
                    while (true) {
                        val offset = next.getAndIncrement()
                        if (offset >= seedCount) break
                        val seed = firstSeed + offset
                        if (only != null && seed !in only) continue
                        val r = runCatching { searchForWin(seed, rules, maxNodes, maxDepth = 400) }.getOrNull() ?: continue
                        nodes.addAndGet(r.nodes)
                        when {
                            r.won -> { win.incrementAndGet(); if (rules == 0) sound[seed] = DealClass.WIN }
                            r.exhausted -> { dead.incrementAndGet(); if (rules == 0) sound[seed] = DealClass.UNWINNABLE }
                            else -> { unknown.incrementAndGet(); if (rules == 0) sound[seed] = DealClass.UNKNOWN }
                        }
                    }
                }
            }.forEach { it.get() }
        } finally {
            executor.shutdown()
            executor.awaitTermination(4, TimeUnit.HOURS)
        }
        log("%-46s | win %5d | proven dead %5d | unknown %5d".format(label, win.get(), dead.get(), unknown.get()))
        return Triple(win.get(), dead.get(), unknown.get())
    }

    pass(0, "sound prunings only (proofs valid)", null)
    val residue = sound.filterValues { it == DealClass.UNKNOWN }.keys
    log("")
    log("residue: ${residue.size} deals with no win and no proof")
    if (residue.isEmpty()) return
    log("re-running just those with the measured productive-setup filter:")
    pass(FastBoard.RULE_NO_IDLE_SETUP, "  + productive-setup filter (finds only)", residue)
}
