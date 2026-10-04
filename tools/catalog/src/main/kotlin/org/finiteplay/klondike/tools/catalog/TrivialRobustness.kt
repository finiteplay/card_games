package org.finiteplay.klondike.tools.catalog

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import org.finiteplay.cards.Card
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.canPlaceOnFoundation
import org.finiteplay.klondike.rules.canPlaceOnTableau
import org.finiteplay.klondike.rules.resolveTableauTap
import org.finiteplay.klondike.rules.resolveWasteTap
import org.finiteplay.klondike.rules.validRunStartIndices
import org.finiteplay.klondike.solver.search.LongHashSet

/**
 * Grades Trivial by the criterion in `docs/games/klondike/DIFFICULTY_LEVELS.md`: a deal is
 * Trivial when **every state reachable by obvious moves can still reach a win using obvious
 * moves**. A robustness claim — the player wins while making mistakes — rather than the
 * shipped grade's claim that one deterministic priority order happens to win.
 *
 * Needs no solver. Three properties of the spec's own definitions make this a small,
 * exact graph problem:
 *
 * - **A node is a `state`: tableau and foundations only.** Drawing is free, so pile
 *   rotation is not part of a state, and draw/recycle are not edges. Keying on the full
 *   board instead (as `SearchState.canonicalStateHashOf` does, mixing in stock and waste) makes
 *   every rotation a distinct node and inflates the graph by a factor of the pile size —
 *   measured, that alone blew a 300,000-node ceiling on every seed tried.
 * - **Any pile card is playable**, again because drawing is free: reaching a given card
 *   costs draws, which are not choices. So the pile contributes one edge per card that has
 *   a legal destination, not merely one for the current waste top.
 * - **The graph is acyclic.** Pile size never grows, and with no pile move available the
 *   face-down count plus tableau card count strictly falls on every remaining obvious move
 *   (a reveal flips a card; a foundation play removes one). So a plain memoised DFS is
 *   sound with no cycle handling and no stored edges.
 */

/** Column-order-invariant fingerprint of a **state**: tableau contents and foundations, never the pile. */
private fun stateFingerprint(state: GameState): Long {
    val signatures = LongArray(TABLEAU_COLUMNS)
    for (column in 0 until TABLEAU_COLUMNS) {
        var h = -3750763034362895579L
        for (entry in state.tableau[column]) {
            h = (h xor (entry.card.id.toLong() * 2 + if (entry.faceUp) 1L else 0L)) * 1099511628211L
        }
        signatures[column] = h
    }
    signatures.sort()
    var h = -3750763034362895579L
    for (signature in signatures) h = (h xor signature) * 1099511628211L
    for (suit in state.foundations.keys.sortedBy { it.ordinal }) {
        h = (h xor (3000L + state.foundations.getValue(suit))) * 1099511628211L
    }
    return h
}

/** Advances the pile by one position: draw when the stock has cards, else recycle. */
internal fun advancePile(state: GameState): GameState =
    applyMove(state, if (state.stock.isNotEmpty()) Move.Draw else Move.Recycle)

/** Test seam over the pile sweep alone, so a card dropped from it is visible to a unit test. */
internal fun pileTapMoves(state: GameState): List<Move> =
    obviousTaps(state).filter { it.kind == ObviousTap.Kind.PILE }.map { it.move }

/**
 * Test seam: each pile tap's move paired with the **pile cycle it leaves behind**. Comparing
 * moves alone is not enough — two implementations can emit identical moves from differently
 * rotated states, and the successor's cycle is what decides the next reference move. Both
 * pile-ordering defects found so far were invisible to a move-only comparison.
 */
internal fun pileTapMovesAndCycles(state: GameState): List<Pair<Move, List<Card>>> =
    obviousTaps(state).filter { it.kind == ObviousTap.Kind.PILE }.map { it.move to pileCycle(it.result) }

/** Test seam: every obvious tap rendered the same way FastBoard renders its own, for differencing. */
internal fun obviousTapDescriptionsForTest(state: GameState): List<String> = obviousTaps(state).map { tap ->
    when (val move = tap.move) {
        is Move.TableauToFoundation -> "T${move.fromColumn}>F"
        is Move.TableauToTableau -> "T${move.fromColumn}.${move.fromIndex}>T${move.toColumn}"
        Move.WasteToFoundation -> "W>F"
        is Move.WasteToTableau -> "W>T${move.toColumn}"
        else -> "other($move)"
    }
}

/** Test seam: the fingerprint each obvious tap leads to, in tap order. */
internal fun obviousSuccessorFingerprintsForTest(state: GameState): List<Long> =
    obviousTaps(state).map { fastFingerprintOf(it.result) }

/** [FastBoard.fingerprint] computed over a GameState, so the two representations are comparable. */
internal fun fastFingerprintOf(state: GameState): Long = FastBoard().apply { loadFrom(state) }.fingerprint()

/** Test seam: the reference tap's resulting state, for walking a realistic line. */
internal fun obviousSuccessorForTest(state: GameState): GameState? {
    val taps = obviousTaps(state)
    if (taps.isEmpty()) return null
    val index = referenceIndex(taps)
    return if (index >= 0) taps[index].result else null
}

/** Cards in the pile that have any legal destination — the exact set the sweep must offer. */
internal fun placeablePileCards(state: GameState): List<Card> =
    (state.stock + state.waste).filter { card ->
        canPlaceOnFoundation(state.foundations, card) ||
            (0 until TABLEAU_COLUMNS).any { canPlaceOnTableau(state.tableau[it], card) }
    }

/**
 * True when moving the run at [fromIndex] off [fromColumn] either exposes a face-down card
 * or empties the column.
 *
 * Both are obvious to a beginner. Freeing a column for a King is as natural as turning a
 * card over, so `fromIndex == 0` — the whole face-up column leaving — is included even
 * though nothing is revealed. This is the *only* non-revealing tableau move admitted:
 * general rearrangement stays out, since no tier below Hard offers it and admitting all of
 * it is what makes the graph explode.
 */
private fun isReveal(state: GameState, fromColumn: Int, fromIndex: Int): Boolean {
    val deepest = validRunStartIndices(state.tableau[fromColumn]).lastOrNull() ?: return false
    return fromIndex == deepest
}

/**
 * One obvious move the player could actually enter, and where it lands.
 *
 * Destinations are **tap-resolved**, not free: the Trivial player taps, and tap resolution
 * is deterministic (`UI_SPEC.md` "Tap"), so they never pick among equal columns — the rule
 * does. Letting the model choose any legal column instead invents deviations no tapping
 * player can make, and measured, it put the graph past 2,000,000 states on every seed.
 */
private data class ObviousTap(val move: Move, val result: GameState, val kind: Kind) {
    /** Declaration order is the reference priority; emptying a column ranks above pile plays. */
    enum class Kind { FOUNDATION, REVEAL, EMPTY_COLUMN, PILE }
}

/**
 * Every obvious tap available at [state]. A pile card other than the waste top is reached
 * by cycling first, which costs draws — not choices — so all of them are available here.
 */
private fun obviousTaps(state: GameState): List<ObviousTap> {
    val out = ArrayList<ObviousTap>(8)

    for (column in 0 until TABLEAU_COLUMNS) {
        val pile = state.tableau[column]
        val top = pile.lastOrNull()?.takeIf { it.faceUp }?.card
        if (top != null && canPlaceOnFoundation(state.foundations, top)) {
            val move = Move.TableauToFoundation(column)
            out.add(ObviousTap(move, applyMove(state, move), ObviousTap.Kind.FOUNDATION))
        }
        for (fromIndex in validRunStartIndices(pile)) {
            if (!isReveal(state, column, fromIndex)) continue
            val resolved = resolveTableauTap(state, column, fromIndex)
            if (resolved is Move.TableauToTableau) {
                val kind = if (fromIndex > 0) ObviousTap.Kind.REVEAL else ObviousTap.Kind.EMPTY_COLUMN
                out.add(ObviousTap(resolved, applyMove(state, resolved), kind))
            }
        }
    }

    // One sweep of the pile, testing each top as it comes by — O(pile) rather than the
    // O(pile^2) of rotating separately to every card.
    //
    // Deliberately *not* replaced by a closed-form rotation. The obvious optimisation is to
    // compute where a card sits in the cycle and jump straight there, but the boundary
    // between stock and waste is genuine state, not a function of which card is on top: it
    // records when the last recycle happened. The same card can be face up with different
    // amounts of waste beneath it, and what lies beneath decides the *next* top, because
    // playing the waste top uncovers the previously drawn card rather than advancing. A
    // fabricated split therefore resumes from the wrong pile position, changing the
    // reference line and the grade. Making rotation O(1) needs the pile modelled as an
    // array with an explicit stock/waste boundary, not a smarter formula over GameState.
    val pileSize = state.stock.size + state.waste.size
    if (pileSize > 0) {
        var rotated = state
        var topsSeen = 0
        // A recycle empties the waste, so the state just after one exposes no top. That step
        // must advance without counting: counting it drops a card from every late-game sweep.
        var guard = 2 * pileSize + 2
        while (topsSeen < pileSize && guard-- > 0) {
            val card = rotated.waste.firstOrNull()
            if (card == null) {
                rotated = advancePile(rotated)
                continue
            }
            if (canPlaceOnFoundation(state.foundations, card) ||
                (0 until TABLEAU_COLUMNS).any { canPlaceOnTableau(state.tableau[it], card) }
            ) {
                resolveWasteTap(rotated)?.let { resolved ->
                    out.add(ObviousTap(resolved, applyMove(rotated, resolved), ObviousTap.Kind.PILE))
                }
            }
            topsSeen++
            rotated = advancePile(rotated)
        }
    }

    return out
}

/**
 * The pile in the order a player cycling from the current position meets it, **starting
 * with the card already face up**.
 *
 * The waste top costs zero draws, so it comes first. Then the stock in order. Once the
 * stock empties, `Recycle` sets `stock = waste.reversed()`, so the remaining waste returns
 * oldest-first — every card except the top, which has already been offered.
 *
 * Getting this order wrong does not change *which* moves exist, only which comes first, so
 * it is invisible to any test that merely compares move sets. It still changes results:
 * [referenceIndex] takes the first pile tap, so a rotated order picks a different reference
 * line and grades different deals robust.
 */
internal fun pileCycle(state: GameState): List<Card> {
    val waste = state.waste
    if (waste.isEmpty()) return state.stock
    return ArrayList<Card>(waste.size + state.stock.size).apply {
        add(waste[0])
        addAll(state.stock)
        for (index in waste.indices.reversed()) if (index != 0) add(waste[index])
    }
}

/**
 * The tap an undeviating player makes: a foundation play if one is offered, else a reveal,
 * else a pile card. Everything else available at that state is a **deviation**.
 *
 * Among pile taps this depends on the order [obviousTaps] emits them, and that order is
 * load-bearing rather than incidental. The sweep emits them in **rotation order from the
 * pile's current position**, which is the order a player cycling the pile actually meets
 * them. An earlier version enumerated `stock + waste` instead — an internal layout, not
 * anything a player sees — and picked a different reference line, which graded two seeds
 * robust that are not.
 */
private fun referenceIndex(taps: List<ObviousTap>): Int {
    ObviousTap.Kind.entries.forEach { kind ->
        val index = taps.indexOfFirst { it.kind == kind }
        if (index >= 0) return index
    }
    return -1
}

/**
 * Checks one deal against [ruleset]'s priority order.
 *
 * The move set is the same at every tier, so this is the same graph search throughout; the
 * ruleset decides only which tap is free and which cost a mistake. See [checkTrivialRobustness].
 */
fun checkRobustness(
    seed: Long,
    ruleset: Ruleset,
    maxDeviations: Int = 2,
    maxStates: Int = 400_000,
): TrivialVerdict = runRobustnessCheck(seed, ruleset, maxDeviations, maxStates)

/**
 * True when [ruleset]'s undeviating line wins [seed] outright — robustness at budget zero.
 *
 * This is the gate that keeps the tiers disjoint: a deal Trivial's own order already wins is
 * not Easy, however robust Easy's order proves to be on it.
 */
fun undeviatingLineWins(seed: Long, ruleset: Ruleset, maxStates: Int = 400_000): Boolean =
    checkRobustness(seed, ruleset, maxDeviations = 0, maxStates = maxStates).outcome == TrivialOutcome.ROBUST

enum class TrivialOutcome { ROBUST, FRAGILE, INCONCLUSIVE }

data class TrivialVerdict(
    val seed: Long,
    val outcome: TrivialOutcome,
    val statesExplored: Int,
    val detail: String,
)

private class ExplorationBudgetExceeded : RuntimeException(null, null, false, false)

/** Thrown the moment any reachable state fails, since one failure disqualifies the hand outright. */
private class Disqualified : RuntimeException(null, null, false, false)

/**
 * Checks one deal, tolerating up to [maxDeviations] mistakes.
 *
 * The unbounded criterion — *every* state any sequence of obvious moves can reach must
 * still win — is not computable here: measured, it passes 2,000,000 states on every seed
 * tried and exhausts the heap. [maxDeviations] is therefore a real bound on the claim, not
 * an implementation detail: a ROBUST verdict means "wins despite up to N mistakes", never
 * "wins despite any number".
 *
 * A deviation is taking any tap other than the one an undeviating player would
 * ([referenceIndex]). Following the reference order is free, so the reference line itself
 * can be arbitrarily long. Nodes are keyed by state **and** remaining budget, since the
 * same board with mistakes still to spare is a different question.
 */
fun checkTrivialRobustness(seed: Long, maxDeviations: Int = 2, maxStates: Int = 400_000): TrivialVerdict =
    runRobustnessCheck(seed, Ruleset.TRIVIAL, maxDeviations, maxStates)

private fun runRobustnessCheck(seed: Long, ruleset: Ruleset, maxDeviations: Int, maxStates: Int): TrivialVerdict {
    val start = dealGame(seed, D1S_SPIKE_VERSIONS)

    // One entry per **state**, holding the highest budget it has been proven robust at —
    // not one per (state, budget). Robustness is monotone: surviving b mistakes implies
    // surviving fewer, so a state proven at b answers every query at b or below for free.
    // Keying on the pair instead re-stored the whole reachable set at every level, which
    // grew the map linearly with the budget (~1.3M entries per level on a deeply robust
    // seed) and was what made high budgets run out of memory rather than out of answers.
    //
    // Failures are never memoised because they never need to be: the first one disqualifies
    // the hand and unwinds the entire search.
    val provenRobustTo = HashMap<Long, Int>()
    // States on the current path. Admitting the column-emptying move broke the acyclicity
    // the search used to rely on: relocating a whole face-up column changes neither the
    // face-down count, the tableau card count nor the pile, so play can return to a board it
    // has already been on.
    //
    // A `path` contains no loops, so an edge back onto the current path is **not a
    // continuation** and is skipped — but skipping is all it is. An earlier version treated a
    // loop as success, reasoning that going round in circles cannot lose. That is true and
    // beside the point: the criterion is *reaches a win*, not *avoids losing*, and a player
    // cycling for ever never wins. It passed seeds whose reference line merely looped, which
    // showed up as 164 of 180 graded deals having no winning line to ship.
    //
    // A state whose every continuation loops is therefore a dead end, exactly as the glossary
    // defines a losing path.
    val onPath = HashSet<Long>()
    var firstFailure: String? = null

    // One board, mutated in place for the whole traversal, and one tap buffer per depth.
    // The search is a depth-first walk of an acyclic graph, so nothing needs copying: make
    // descends, unmake returns. This is what the GameState version could not do, since every
    // move there allocated a fresh immutable board.
    val board = FastBoard().apply { loadFrom(start) }
    // Wide enough for Hard, which adds one setup move per face-up card buried under another.
    // Trivial through Medium never fill a fraction of this; the array is per depth, not per node.
    val tapBuffers = Array(FastBoard.MAX_DEPTH) { IntArray(160) }

    fun visit(level: Int, budget: Int): Boolean {
        if (board.isWon()) return true
        val key = board.fingerprint()
        val provenTo = provenRobustTo[key]
        if (provenTo != null && provenTo >= budget) return true
        if (provenRobustTo.size >= maxStates) throw ExplorationBudgetExceeded()
        onPath.add(key)

        val taps = tapBuffers[level]
        val count = board.generateTaps(taps, ruleset)
        if (count == 0) {
            onPath.remove(key)
            firstFailure = "dead end reachable within $maxDeviations deviation(s): no obvious move, not won"
            throw Disqualified()
        }
        // Which taps actually continue the path: a move landing on a board already on this
        // line is a loop, so it is no continuation at all and is passed over. The reference
        // is therefore the highest-priority tap that does *not* loop — not simply the
        // highest-priority tap. Disqualifying when the top choice loops instead of falling
        // through to the next was measured to reject 19,995 of 20,000 seeds, because the
        // reference player oscillates the moment column-emptying outranks the pile.
        var reference = -1
        var continuations = 0
        val loops = BooleanArray(count)
        for (index in 0 until count) {
            board.make(taps[index])
            loops[index] = board.fingerprint() in onPath
            board.unmake()
            if (!loops[index]) {
                continuations++
                if (reference < 0 || board.tapRank(taps[index], ruleset) < board.tapRank(taps[reference], ruleset)) reference = index
            }
        }
        if (continuations == 0) {
            onPath.remove(key)
            firstFailure = "every continuation loops: a losing path, not a win"
            throw Disqualified()
        }

        // Every non-looping tap the budget allows must lead to a win, the reference for free
        // and the rest for one each. One failure disqualifies the hand outright, so the first
        // unwinds the whole search rather than finishing the sibling loop.
        for (index in 0 until count) {
            if (loops[index]) continue
            val cost = if (index == reference) 0 else 1
            if (cost > budget) continue
            board.make(taps[index])
            val survived = try {
                visit(level + 1, budget - cost)
            } finally {
                board.unmake()
            }
            if (!survived) {
                firstFailure = "a deviation loses (budget $budget remaining)"
                throw Disqualified()
            }
        }
        onPath.remove(key)
        provenRobustTo[key] = budget
        return true
    }

    return try {
        visit(0, maxDeviations)
        TrivialVerdict(seed, TrivialOutcome.ROBUST, provenRobustTo.size, "survives up to $maxDeviations deviation(s); ${provenRobustTo.size} states")
    } catch (_: Disqualified) {
        TrivialVerdict(seed, TrivialOutcome.FRAGILE, provenRobustTo.size, firstFailure ?: "some deviation loses")
    } catch (_: ExplorationBudgetExceeded) {
        TrivialVerdict(seed, TrivialOutcome.INCONCLUSIVE, provenRobustTo.size, "exceeded $maxStates states")
    } catch (_: StackOverflowError) {
        TrivialVerdict(seed, TrivialOutcome.INCONCLUSIVE, provenRobustTo.size, "recursion depth exceeded")
    }
}

/** Scans seeds upward from [firstSeed] until [wanted] are ROBUST, reporting each as it lands. */
fun findTrivialSeeds(
    wanted: Int = 1,
    firstSeed: Long = 1L,
    maxDeviations: Int = 2,
    maxStates: Int = 400_000,
    parallelism: Int = 4,
    log: (String) -> Unit = ::println,
) {
    log("searching for $wanted deal(s) that still win after up to $maxDeviations obvious-move mistake(s)")
    log("nodes are tableau+foundations only; draws are free, so any pile card is playable")

    val next = AtomicInteger(0)
    val accepted = ConcurrentLinkedQueue<TrivialVerdict>()
    val tried = AtomicInteger(0)
    val fragile = AtomicInteger(0)
    val inconclusive = AtomicInteger(0)

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val workers = List(parallelism) {
            executor.submit {
                while (accepted.size < wanted) {
                    val seed = firstSeed + next.getAndIncrement()
                    val verdict = runCatching { checkTrivialRobustness(seed, maxDeviations, maxStates) }.getOrElse {
                        TrivialVerdict(seed, TrivialOutcome.INCONCLUSIVE, 0, "error: ${it::class.simpleName}")
                    }
                    when (verdict.outcome) {
                        TrivialOutcome.ROBUST -> {
                            accepted.add(verdict)
                            log("ROBUST seed=${verdict.seed} states=${verdict.statesExplored}")
                        }
                        TrivialOutcome.FRAGILE -> fragile.incrementAndGet()
                        TrivialOutcome.INCONCLUSIVE -> inconclusive.incrementAndGet()
                    }
                    val count = tried.incrementAndGet()
                    if (count % 100 == 0) {
                        log("...$count tried | ${accepted.size} robust | ${fragile.get()} fragile | ${inconclusive.get()} inconclusive")
                    }
                }
            }
        }
        workers.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(60, TimeUnit.MINUTES)
    }

    val sorted = accepted.sortedBy { it.seed }.take(wanted)
    log("")
    log("=== ${sorted.size} Trivial hand(s), robust to any obvious-move deviation ===")
    for (v in sorted) log("seed=${v.seed} states=${v.statesExplored} — ${v.detail}")
    log("tried ${tried.get()}: ${fragile.get()} fragile, ${inconclusive.get()} inconclusive")
}

/**
 * Calibration: for each of [seedCount] seeds, finds the **largest deviation budget it
 * survives**, and reports the distribution.
 *
 * This is the question to answer before choosing a Trivial tolerance. Robustness is
 * monotone in the budget — surviving two mistakes implies surviving one — so the search
 * stops at the first budget that fails. Budget 0 means only the obvious tap order is
 * played, so a deal failing there is not won by obvious play *at all*, which is a different
 * failure from being intolerant of mistakes, and the two are counted separately.
 */
fun calibrateTrivial(
    seedCount: Int = 2000,
    maxBudget: Int = 3,
    firstSeed: Long = 1L,
    parallelism: Int = 4,
    /** Which tier's priority order the undeviating player follows. */
    ruleset: Ruleset = Ruleset.TRIVIAL,
    /**
     * Tiers this deal must **not** already be won by, undeviating — the disjointness gate.
     * Easy passes `[TRIVIAL]` and Medium `[TRIVIAL, EASY]`, so a deal a lower order plays out
     * is counted and discarded rather than graded. Checked first because it is the cheapest
     * question asked of a seed — one budget-0 search each — and it rejects the largest share.
     */
    excludeWonBy: List<Ruleset> = emptyList(),
    // Deliberately far below the single-seed ceiling: every worker holds its own visited map
    // *and* its own on-path set, so a cap sized for one deep audit multiplied across threads
    // exhausts the heap. Scanning wants many cheap verdicts, not one exhaustive one, and a
    // seed too costly to settle here is simply not tier material.
    maxStates: Int = 3_000_000,
    /** Seeds surviving at least this many mistakes are written to [outputPath]; below it, only counted. */
    keepFrom: Int = 2,
    outputPath: String? = null,
    log: (String) -> Unit = ::println,
) {
    log("calibrating $seedCount seeds on $parallelism threads under $ruleset: largest budget each survives (0..$maxBudget)")
    if (excludeWonBy.isNotEmpty()) log("discarding any deal $excludeWonBy already wins undeviating, before grading")
    if (outputPath != null) log("keeping every seed surviving >= $keepFrom to $outputPath")

    // Counters, not a row per seed. Keeping one entry per candidate is fine at thousands and
    // fatal at a hundred million — the list alone would outweigh every search it describes.
    // Only seeds at the interesting budgets are kept, and only a sample of those.
    val counts = AtomicIntegerArray(maxBudget + 2)
    val notable = ConcurrentLinkedQueue<Pair<Long, Int>>()
    // Streamed, not written at the end: a scan can run for hours, and a crash, an OOM or a
    // stopped job would otherwise take every result with it. Flushed per seed so the file is
    // complete-to-date at any moment; the export sorts, so arrival order does not matter.
    val writer = outputPath?.let { path ->
        java.io.File(path).also { it.parentFile?.mkdirs() }.printWriter().also { it.println("seed,budget"); it.flush() }
    }
    val writeLock = Any()
    val notableFrom = if (outputPath != null) keepFrom else maxOf(2, maxBudget - 2)
    val excluded = AtomicInteger(0)
    val done = AtomicInteger(0)
    val next = AtomicInteger(0)
    val startedAt = System.nanoTime()

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val workers = List(parallelism) {
            executor.submit {
                while (true) {
                    val offset = next.getAndIncrement()
                    if (offset >= seedCount) break
                    val seed = firstSeed + offset
                    var best = -1
                    val alreadyWon = excludeWonBy.any { lower ->
                        runCatching { undeviatingLineWins(seed, lower, maxStates) }.getOrDefault(false)
                    }
                    if (alreadyWon) {
                        excluded.incrementAndGet()
                    } else {
                        for (budget in 0..maxBudget) {
                            val outcome = runCatching { checkRobustness(seed, ruleset, budget, maxStates).outcome }.getOrNull()
                            if (outcome == TrivialOutcome.ROBUST) best = budget else break
                        }
                        counts.incrementAndGet(best + 1)
                    }
                    if (best >= notableFrom) {
                        notable.add(seed to best)
                        if (writer != null) synchronized(writeLock) { writer.println("$seed,$best"); writer.flush() }
                    }
                    val completed = done.incrementAndGet()
                    if (completed % 5_000_000 == 0) {
                        val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
                        log("...$completed/$seedCount after ${elapsed}s")
                    }
                }
            }
        }
        workers.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(24, TimeUnit.HOURS)
    }

    val elapsed = (System.nanoTime() - startedAt) / 1_000_000_000L
    log("")
    log("budget survived | seeds")
    if (excludeWonBy.isNotEmpty()) log("— already won by $excludeWonBy, not graded | ${excluded.get()}")
    log("-1 ($ruleset's own order does not win it) | ${counts.get(0)}")
    for (budget in 0..maxBudget) log("$budget | ${counts.get(budget + 1)}")
    log("")
    log("scanned $seedCount seeds in ${elapsed}s")
    for (budget in maxBudget downTo notableFrom) {
        val examples = notable.filter { it.second == budget }.map { it.first }.sorted()
        if (examples.isNotEmpty()) {
            log("survive $budget: ${examples.take(20).joinToString(", ") { "${it}L" }}${if (examples.size > 20) " (+${examples.size - 20} more)" else ""}")
        }
    }

    writer?.close()
    if (outputPath != null) log("wrote ${notable.size} seeds to $outputPath (streamed during the scan)")
}

/**
 * Walks the whole obvious-move graph of one seed, counting distinct states and timing it.
 * No deviation budget and no winnability question — purely "how big is this graph and how
 * fast can it be enumerated", which is what decides whether the unbounded Trivial criterion
 * is affordable.
 *
 * [maxDepth] caps path length in **choices**. It is reported rather than assumed to bind:
 * the graph is acyclic and a game holds on the order of a hundred choices in total (52
 * foundation plays, ~21 reveals, ~24 pile placements), so a cap in the hundreds or
 * thousands is expected to be slack, leaving *breadth* as the real constraint.
 *
 * Uses [LongHashSet] rather than a boxed `HashSet<Long>`: at tens of millions of states the
 * difference is gigabytes.
 */
fun walkObviousGraph(
    seed: Long,
    maxDepth: Int = 1000,
    maxStates: Int = 40_000_000,
    timeLimitMs: Long = 600_000,
    log: (String) -> Unit = ::println,
) {
    log("walking the obvious-move graph of seed=$seed (depth cap $maxDepth choices, ceiling $maxStates states)")
    val start = dealGame(seed, D1S_SPIKE_VERSIONS)
    val seen = LongHashSet(1 shl 20)
    val stack = ArrayList<Pair<GameState, Int>>()
    var deepest = 0
    var wins = 0
    var deadEnds = 0
    var expansions = 0L
    val startedAt = System.nanoTime()

    fun elapsedMs() = (System.nanoTime() - startedAt) / 1_000_000L

    seen.add(stateFingerprint(start))
    stack.add(start to 0)
    var stoppedBecause = "graph fully enumerated"

    while (stack.isNotEmpty()) {
        if (seen.size >= maxStates) { stoppedBecause = "hit the $maxStates state ceiling"; break }
        if (expansions % 4096 == 0L && elapsedMs() > timeLimitMs) { stoppedBecause = "hit the ${timeLimitMs}ms time limit"; break }

        val (state, depth) = stack.removeAt(stack.size - 1)
        deepest = maxOf(deepest, depth)
        if (state.isWon) { wins++; continue }
        if (depth >= maxDepth) continue

        expansions++
        val taps = obviousTaps(state)
        if (taps.isEmpty()) { deadEnds++; continue }
        for (tap in taps) {
            val fingerprint = stateFingerprint(tap.result)
            if (seen.contains(fingerprint)) continue
            seen.add(fingerprint)
            stack.add(tap.result to depth + 1)
        }
    }

    val ms = elapsedMs().coerceAtLeast(1)
    log("stopped: $stoppedBecause")
    log("distinct states : ${seen.size}")
    log("expansions      : $expansions")
    log("deepest path    : $deepest choices (cap $maxDepth — ${if (deepest >= maxDepth) "BINDING" else "slack, breadth is the constraint"})")
    log("wins seen       : $wins, dead ends: $deadEnds")
    log("elapsed         : ${ms}ms  (${seen.size * 1000L / ms} states/sec)")
}

/**
 * Times the check at each budget over [seedCount] seeds, so scan cost can be projected
 * from measurement rather than guessed. Reports mean wall time and mean node count per
 * budget, plus how often the node ceiling was hit — a budget whose checks mostly go
 * INCONCLUSIVE has no usable timing, and its number must not be extrapolated from.
 */
fun benchmarkTrivial(
    seedCount: Int = 200,
    maxBudget: Int = 3,
    firstSeed: Long = 1L,
    maxStates: Int = 4_000_000,
    log: (String) -> Unit = ::println,
) {
    log("benchmarking $seedCount seeds at budgets 0..$maxBudget (single-threaded, ceiling $maxStates nodes)")
    log("budget | mean ms | median ms | mean nodes | max nodes | inconclusive")
    for (budget in 0..maxBudget) {
        val times = ArrayList<Long>(seedCount)
        var nodes = 0L
        var maxNodes = 0
        var inconclusive = 0
        for (offset in 0 until seedCount) {
            val seed = firstSeed + offset
            val startNanos = System.nanoTime()
            val verdict = runCatching { checkTrivialRobustness(seed, budget, maxStates) }.getOrNull()
            times.add((System.nanoTime() - startNanos) / 1_000_000L)
            if (verdict != null) {
                nodes += verdict.statesExplored
                maxNodes = maxOf(maxNodes, verdict.statesExplored)
                if (verdict.outcome == TrivialOutcome.INCONCLUSIVE) inconclusive++
            }
        }
        times.sort()
        log(
            "$budget | %.1f | %d | %d | %d | %d".format(
                times.average(), times[times.size / 2], nodes / seedCount, maxNodes, inconclusive,
            ),
        )
    }
}

/**
 * Escalates the deviation budget for one seed until it fails or the budget stops mattering.
 *
 * There is a ceiling worth knowing: a path contains at most as many choices as the deal has
 * cards to move, on the order of ninety. A budget at least that large can never be exhausted,
 * so a deal robust there is robust against **any** number of mistakes — the unbounded
 * criterion, decided rather than approximated.
 */
fun reportRobustnessLimit(seed: Long, ceiling: Int = 120, maxStates: Int = 40_000_000, log: (String) -> Unit = ::println) {
    log("escalating the deviation budget for seed=$seed (ceiling $ceiling, node cap $maxStates)")
    log("budget | outcome | nodes | ms")
    var best = -1
    for (budget in 0..ceiling) {
        val started = System.nanoTime()
        val verdict = checkTrivialRobustness(seed, budget, maxStates)
        val ms = (System.nanoTime() - started) / 1_000_000L
        log("$budget | ${verdict.outcome} | ${verdict.statesExplored} | $ms")
        if (verdict.outcome != TrivialOutcome.ROBUST) {
            log("")
            when (verdict.outcome) {
                TrivialOutcome.FRAGILE -> log("seed $seed survives at most $best mistake(s); ${verdict.detail}")
                else -> log("seed $seed survives at least $best; beyond that the check ran out of budget, so nothing is claimed")
            }
            return
        }
        best = budget
    }
    log("")
    log("seed $seed is robust at every budget to $ceiling — more deviations than a game has choices,")
    log("so no sequence of obvious moves can lose it: unconditionally Trivial.")
}

/**
 * A budget no game can exhaust. A path spends one per deviation and holds at most as many
 * choices as there are cards to move — 52 foundation plays, at most 21 reveals, 24 pile
 * placements, so 97 is a hard ceiling and the deepest path measured was 90. A deal robust
 * at this budget is robust against **any** number of mistakes.
 */
const val UNCONDITIONAL_BUDGET = 120

/**
 * Tests seeds at [UNCONDITIONAL_BUDGET] directly — one check each, rather than escalating
 * from zero — to decide whether a deal is unconditionally Trivial: no sequence of obvious
 * moves loses it, however many departures from the obvious order the player makes.
 */
fun reportUnconditional(seeds: List<Long>, maxStates: Int = 40_000_000, log: (String) -> Unit = ::println) {
    log("testing ${seeds.size} seed(s) at budget $UNCONDITIONAL_BUDGET — beyond any game's choice count")
    var unconditional = 0
    for (seed in seeds) {
        val started = System.nanoTime()
        val verdict = checkTrivialRobustness(seed, UNCONDITIONAL_BUDGET, maxStates)
        val ms = (System.nanoTime() - started) / 1_000_000L
        if (verdict.outcome == TrivialOutcome.ROBUST) unconditional++
        log("seed=$seed ${verdict.outcome} states=${verdict.statesExplored} ${ms}ms")
    }
    log("")
    log("$unconditional of ${seeds.size} are unconditionally Trivial")
}

/**
 * Finds deals that **cannot be lost by obvious play at all** — the unbounded criterion,
 * decided rather than approximated.
 *
 * A path spends one budget per deviation and holds at most 97 choices (52 foundation plays,
 * at most 21 reveals, 24 pile placements), so a budget of [UNCONDITIONAL_BUDGET] can never
 * be exhausted: surviving it means no sequence of obvious moves loses the deal.
 *
 * Candidates come from a prior scan rather than from scratch, because robustness is monotone
 * — unconditional deals are a subset of those surviving six mistakes, which are already about
 * 1 in 278. Filtering cheaply first and paying the expensive check only on survivors is what
 * makes this affordable; testing every seed at budget 120 would not be.
 *
 * Results stream to [outputPath] as they are found.
 */
fun scanUnconditional(
    csvPath: String,
    outputPath: String? = null,
    parallelism: Int = 12,
    maxStates: Int = 5_000_000,
    log: (String) -> Unit = ::println,
) {
    val candidates = java.io.File(csvPath).readLines().drop(1)
        .mapNotNull { it.split(',').getOrNull(0)?.trim()?.toLongOrNull() }
    log("testing ${candidates.size} candidates at budget $UNCONDITIONAL_BUDGET on $parallelism threads")

    val writer = outputPath?.let { java.io.File(it).also { f -> f.parentFile?.mkdirs() }.printWriter() }
    writer?.println("seed")
    writer?.flush()
    val writeLock = Any()

    val found = ConcurrentLinkedQueue<Long>()
    val inconclusive = AtomicInteger(0)
    val done = AtomicInteger(0)
    val next = AtomicInteger(0)
    val started = System.nanoTime()

    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val workers = List(parallelism) {
            executor.submit {
                while (true) {
                    val index = next.getAndIncrement()
                    if (index >= candidates.size) break
                    val seed = candidates[index]
                    val verdict = runCatching { checkTrivialRobustness(seed, UNCONDITIONAL_BUDGET, maxStates) }.getOrNull()
                    when (verdict?.outcome) {
                        TrivialOutcome.ROBUST -> {
                            found.add(seed)
                            synchronized(writeLock) { writer?.println(seed); writer?.flush() }
                            log("UNLOSABLE seed=$seed states=${verdict.statesExplored}")
                        }
                        TrivialOutcome.INCONCLUSIVE, null -> inconclusive.incrementAndGet()
                        else -> Unit
                    }
                    val count = done.incrementAndGet()
                    if (count % 25 == 0) log("...$count/${candidates.size}, ${found.size} unlosable, ${inconclusive.get()} inconclusive")
                }
            }
        }
        workers.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(6, TimeUnit.HOURS)
        writer?.close()
    }

    val elapsed = (System.nanoTime() - started) / 1_000_000_000L
    log("")
    log("${found.size} of ${candidates.size} candidates cannot be lost by obvious play (${elapsed}s)")
    log("${inconclusive.get()} were inconclusive at $maxStates states — not a verdict either way")
    log(found.sorted().joinToString(", ") { "${it}L" }.take(2000))
}

/**
 * Re-checks every shipped seed against the budget it was graded at, reporting any that no
 * longer hold. Exists because the tier was generated while [FastBoard.fingerprint] combined
 * column signatures with XOR, under which two empty columns cancelled — a collision that can
 * only ever make the search *over*-report robustness, by treating a state it has not examined
 * as already proven. Cheap enough to run over the whole tier rather than trusting it.
 */
fun verifyTier(csvPath: String, minBudget: Int = 3, parallelism: Int = 6, maxStates: Int = 20_000_000, log: (String) -> Unit = ::println) {
    val rows = java.io.File(csvPath).readLines().drop(1).mapNotNull { line ->
        val parts = line.split(',')
        val seed = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: return@mapNotNull null
        val budget = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@mapNotNull null
        if (budget >= minBudget) seed to budget else null
    }
    log("re-verifying ${rows.size} shipped seeds at their recorded budgets on $parallelism threads")

    val failures = ConcurrentLinkedQueue<String>()
    val done = AtomicInteger(0)
    val next = AtomicInteger(0)
    val executor = Executors.newFixedThreadPool(parallelism)
    try {
        val workers = List(parallelism) {
            executor.submit {
                while (true) {
                    val index = next.getAndIncrement()
                    if (index >= rows.size) break
                    val (seed, budget) = rows[index]
                    val outcome = runCatching { checkTrivialRobustness(seed, budget, maxStates).outcome }.getOrNull()
                    if (outcome != TrivialOutcome.ROBUST) failures.add("seed=$seed recorded=$budget now=$outcome")
                    val count = done.incrementAndGet()
                    if (count % 1000 == 0) log("...$count/${rows.size}, ${failures.size} failures")
                }
            }
        }
        workers.forEach { it.get() }
    } finally {
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.HOURS)
    }

    log("")
    if (failures.isEmpty()) {
        log("all ${rows.size} shipped seeds still hold at their recorded budgets")
    } else {
        log("${failures.size} of ${rows.size} NO LONGER HOLD:")
        failures.take(40).forEach(log)
    }
}

/** Re-checks named seeds at every budget from 0 to [maxBudget] — for auditing individual deals. */
fun auditTrivialSeeds(seeds: List<Long>, maxBudget: Int = 6, maxStates: Int = 400_000, log: (String) -> Unit = ::println) {
    log("auditing ${seeds.size} seed(s) at deviation budgets 0..$maxBudget")
    for (seed in seeds) {
        for (budget in 0..maxBudget) {
            val v = checkTrivialRobustness(seed, budget, maxStates)
            log("seed=$seed budget=$budget ${v.outcome} nodes=${v.statesExplored} — ${v.detail}")
            if (v.outcome != TrivialOutcome.ROBUST) break
        }
    }
}
