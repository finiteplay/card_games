package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.TABLEAU_COLUMNS

/** How much work a solve may spend before giving up. */
data class SolverLimits(
    val maxNodes: Long = 400_000,
    val maxMillis: Long = 2_000,
    /** Greedy playouts to try before searching. Each is cheap and most easy deals fall to one. */
    val playouts: Int = 24,
    /** Independent receding-horizon attempts. Zero disables the guided-search stage. */
    val guidedAttempts: Int = 0,
    /** Moves examined ahead before guided search commits its best short path. */
    val guidedDepth: Int = 13,
    /** Maximum nodes in one horizon before committing the best position found so far. */
    val guidedNodesPerHorizon: Long = 1_000_000,
    /**
     * How many of a board's moves the beam-limited search will ever try
     * ([SpiderSolver.beamSearch]) — the rest are discarded unexamined, whatever they might have led
     * to. Three because Spider's real branching factor is ten to thirty and its winning lines run
     * past a hundred moves, so a search that keeps every option reaches nowhere near deep enough
     * to find one; ranking by the same public-strategy scoring the greedy playout uses and keeping
     * only the best few is what buys the depth. Zero disables the stage.
     */
    val beamWidth: Int = 3,
    /**
     * Initial size of the transposition cache's backing array, which must be a power of two
     * ([LongHashSet] doubles from here as needed, so this only saves the early rehashes on a run
     * expected to need a large one). Left at the historical default for every existing caller —
     * the interactive Hint path never has reason to change it — and raised only by offline S6
     * campaign runs, which are what a genuinely large cache is for
     * (`docs/games/spider/EXECUTION_PLAN.md` "S6").
     */
    val cacheCapacityPowerOfTwo: Int = 1 shl 20,
    /**
     * When set, the exact search's transposition caches (`visited`, `streamlinedVisited`) become
     * bounded, evicting [GenerationalLongHashSet]s holding roughly this many total live entries
     * between them, instead of an ever-growing [LongHashSet]. `null` (the default, and every
     * existing caller's value) keeps the unbounded cache — this only exists for an S6 campaign run
     * long enough that unbounded growth would itself exhaust memory before the time budget does
     * (`docs/games/spider/EXECUTION_PLAN.md` "S6" has the correctness argument for why eviction is
     * safe here: it can only cost extra nodes, never hide a genuine win or falsify an EXHAUSTED
     * verdict, given the depth cap every recursive search already enforces regardless of the cache).
     * Split 9:1 between the exact and streamlined caches, matching [FourSuitCampaign]'s own 9:1
     * time split between the two stages (`campaignSolve` gives the streamlined pass a tenth of the
     * overall time budget) — the streamlined stage runs for a tenth of the time, so a tenth of the
     * entries is what it can plausibly fill.
     */
    val maxCacheEntries: Long? = null,
    /**
     * Whether a failed beam-limited search falls through to the full-legal DFS and then the A*
     * fallback. True for every existing caller — offline catalog generation wants every stage's
     * best shot at certifying a candidate — except the interactive Hint path, which turns this off
     * to stay fast and cheap: greedy and beam alone, no exhaustive fallback.
     */
    val useDfsAndAStarFallback: Boolean = true,
)

/** Why a solve stopped. Unsolved is not the same as unsolvable, and is never reported as one. */
enum class SolveResult { SOLVED_BY_STRATEGY, SOLVED_BY_SEARCH, EXHAUSTED, LIMIT, OUT_OF_MEMORY }

data class SolveOutcome(
    val result: SolveResult,
    val nodes: Long,
    val moves: Int,
) {
    val solved: Boolean get() = result == SolveResult.SOLVED_BY_STRATEGY || result == SolveResult.SOLVED_BY_SEARCH
    /** True only when the search proved there is no win — [SolveResult.LIMIT] proves nothing. */
    val provedUnsolvable: Boolean get() = result == SolveResult.EXHAUSTED
}

/** [SpiderSolver.certifyWithOutcome]'s result: [line] is non-null exactly when [outcome] is solved. */
data class CertifiedOutcome(val outcome: SolveOutcome, val line: List<org.finiteplay.spider.rules.Move>?)

/**
 * The unbounded [LongHashSet] for every existing caller ([SolverLimits.maxCacheEntries] is `null`
 * unless an S6 campaign run sets it), or a [GenerationalLongHashSet] holding [share] tenths of
 * [SolverLimits.maxCacheEntries] once it is set — [share] is 9 for the exact search's own cache and
 * 1 for the streamlined stage's, matching [FourSuitCampaign]'s own 9:1 time split between them.
 */
private fun newCache(limits: SolverLimits, share: Int): TranspositionCache {
    val maxEntries = limits.maxCacheEntries ?: return LongHashSet(limits.cacheCapacityPowerOfTwo)
    val entries = (maxEntries * share / 10).coerceAtLeast(GENERATIONAL_CACHE_GENERATIONS.toLong())
    val perGeneration = (entries / GENERATIONAL_CACHE_GENERATIONS).toInt().coerceAtLeast(1)
    return GenerationalLongHashSet(entriesPerGeneration = perGeneration, generations = GENERATIONAL_CACHE_GENERATIONS)
}

/** How many generations [newCache] splits a bounded cache's budget across — see [GenerationalLongHashSet]'s own doc for why more generations means smoother (finer-grained) eviction at the same total memory. */
private const val GENERATIONAL_CACHE_GENERATIONS = 8

/**
 * Spider solver: greedy strategy playouts first, then depth-first search on whatever they leave.
 *
 * The two stages answer different questions. A playout follows the heuristics a good human player
 * uses (`docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md`) and never backtracks, so it is enormously
 * cheap and wins the deals that do not need cleverness — which, at one suit, is most of them.
 * Search backtracks and is complete within its budget, so it wins deals with a single narrow line;
 * it is also the only stage that can ever report a deal *unsolvable*, and only when it exhausts
 * the whole space rather than running out of budget.
 *
 * Running strategy first is not just a speed-up: it is what makes a million-deal survey affordable
 * at all, because search is orders of magnitude more expensive per deal and most deals never reach
 * it.
 */
class SpiderSolver(val limits: SolverLimits = SolverLimits()) {
    private val visited: TranspositionCache = newCache(limits, share = 9)
    /** Its own cache, never [visited]: see [certifyStreamlined]'s own doc on why the two must never share one. */
    private val streamlinedVisited: TranspositionCache = newCache(limits, share = 1)
    private val undos = Array(MAX_DEPTH) { UndoRecord() }
    private val path = IntArray(MAX_DEPTH)
    private val hashScratch = HashScratch()
    private val probeUndo = UndoRecord()
    private val aStarSolver = AStarSolver()
    private val followOnScratch = IntArrayList(64)
    private val moveEval = MoveEval()

    /** Live entries in the exact search's transposition cache after the most recent [solve] call — its peak, since the cache only grows within one call. For S6 campaign instrumentation. */
    val cacheSize: Int get() = visited.size

    /** Live entries in the streamlined search's own cache after the most recent [certifyStreamlined] call. */
    val streamlinedCacheSize: Int get() = streamlinedVisited.size
    // Per-depth, so the beam at one depth survives the recursion into its own children rather than
    // being clobbered by theirs; preallocated so a hot search loop allocates nothing per node.
    private val beamMoves = Array(MAX_DEPTH) { IntArray(MAX_BEAM_WIDTH) }
    private val beamScores = Array(MAX_DEPTH) { IntArray(MAX_BEAM_WIDTH) }
    // Per-depth move-generation scratch for search/streamlinedSearch/beamSearch, for the same
    // reason as beamMoves/beamScores above: each was a fresh `IntArrayList(64)` allocated on every
    // single recursive call, which at the node rates a long campaign run reaches (millions/sec) is
    // real, sustained garbage-collector pressure for no benefit — the list only needs to survive
    // until this call's for-loop over it finishes, exactly what one slot per depth already gives
    // beamMoves/beamScores.
    private val searchMoves = Array(MAX_DEPTH) { IntArrayList(64) }
    private val streamlinedMoves = Array(MAX_DEPTH) { IntArrayList(64) }
    private val beamCandidates = Array(MAX_DEPTH) { IntArrayList(64) }
    private val guidedCandidates = Array(MAX_GUIDED_DEPTH) { IntArrayList(64) }
    private val guidedPath = IntArray(MAX_GUIDED_DEPTH)
    private val guidedCommitted = BooleanArray(MAX_GUIDED_DEPTH)
    private val guidedStrides = intArrayOf(1, 3, 7, 9)
    private val guidedBestPath = IntArray(MAX_GUIDED_DEPTH)
    private val guidedAlternativePaths = Array(MAX_GUIDED_ALTERNATIVES) { IntArray(MAX_GUIDED_DEPTH) }
    private val guidedAlternativeLengths = IntArray(MAX_GUIDED_ALTERNATIVES)
    private val guidedAlternativeScores = LongArray(MAX_GUIDED_ALTERNATIVES)
    private var guidedAlternativeCount = 0
    private var guidedMinimumScore = Long.MIN_VALUE
    private var guidedBestLength = 0
    private var guidedBestScore = Long.MIN_VALUE
    private var guidedHorizonBaseNodes = 0L
    private var guidedAttemptBaseNodes = 0L

    private class GuidedReturnPoint(
        val board: FastBoard,
        val recordSize: Int,
        val paths: Array<IntArray>,
        val lengths: IntArray,
        var next: Int = 0,
    )
    private var nodes = 0L
    private var deadline = 0L
    /**
     * Set once the sampled clock check ([search], [beamSearch], [streamlinedSearch] only call
     * `System.nanoTime()` every 1024 nodes, not every node, since the syscall itself has a real
     * cost at these node rates) first observes the deadline has passed, then stays true for the
     * rest of this stage. Without this, a recursive search's own for-loop treats a timed-out
     * child's `-1` return exactly like "no solution down this branch" and moves on to the next
     * sibling — which recurses fresh and won't itself re-check the clock until *its own* node
     * count happens to land on a 1024 boundary, potentially diving arbitrarily deep first. Across
     * an entire stack of untried siblings at up to [MAX_DEPTH] levels, that turned "ran out of
     * time" into a search that kept going, largely unthrottled, for far longer than [SolverLimits
     * .maxMillis] — caught by timing `certifyStreamlined` directly against a 2-second budget and
     * finding it still running after two minutes. Checking this flag (a plain boolean read) at
     * every point a search already re-checks its node budget after backtracking makes a timeout
     * propagate all the way to the top on the very next check at *every* level, the same way
     * [stageNodesExhausted] already does.
     */
    private var deadlineExceeded = false
    /**
     * [nodes] as it stood when the current search stage started, so each stage gets the caller's
     * full [SolverLimits.maxNodes] rather than the stages splitting one allowance between them —
     * the same per-stage-budget/shared-deadline split Klondike's own DFS and A* stages use
     * (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"). [nodes] itself keeps accumulating,
     * so the reported total is still the whole solve's.
     */
    private var stageBaseNodes = 0L

    /**
     * Set once [beamSearch] discards a move outside its width. Until then its tree is the full
     * legal tree, and its running dry is as much a proof as the full-legal DFS's; after it, an
     * empty tree proves nothing — the discarded moves were never looked at.
     */
    private var beamPruned = false

    private val stageNodesExhausted: Boolean get() = nodes - stageBaseNodes > limits.maxNodes

    fun solve(state: SpiderState): SolveOutcome = solve(FastBoard.from(state))

    /** Runs one greedy playout and hands back the board it reached. For diagnosis and tests only. */
    internal fun debugPlayout(start: FastBoard, variation: Int = 0): FastBoard {
        nodes = 0
        deadline = System.nanoTime() + 60_000_000_000L
        val board = start.copy()
        playout(board, variation, null)
        return board
    }

    fun solve(start: FastBoard): SolveOutcome = solve(start, record = null)

    /**
     * Solves [state] and hands back the actual winning line as real [org.finiteplay.spider.rules.Move]s,
     * or null when it does not solve within budget.
     *
     * This is what certification needs and plain [solve] does not have: [SolveOutcome] reports that
     * a win exists, not how to reach it. Recording doubles as no extra search — the same playout and
     * the same search run, only now writing each move taken into [record] as they go, which costs one
     * list append per move rather than a second solve.
     *
     * The returned line is exactly what this solver played; it is not re-derived or trusted blindly
     * by the caller — `SpiderCatalogBuild` independently replays it through the real reducer before
     * accepting a seed, which is the actual certification step. This method only makes that replay
     * possible.
     */
    fun certify(state: SpiderState): List<org.finiteplay.spider.rules.Move>? = certifyWithOutcome(state).line

    /**
     * [certify] and [solve] in one search: the verdict and, when it is a win, the line — and
     * within [maxMillis] rather than [SolverLimits.maxMillis] when a caller has already spent part
     * of its own budget elsewhere ([HintEngine]).
     */
    fun certifyWithOutcome(state: SpiderState, maxMillis: Long = limits.maxMillis): CertifiedOutcome {
        val recorded = IntArrayList(256)
        val outcome = solve(FastBoard.from(state), record = recorded, maxMillis = maxMillis)
        val line = if (outcome.solved) (0 until recorded.size).map { toRulesMove(FastMove(recorded[it])) } else null
        return CertifiedOutcome(outcome, line)
    }

    /** Runs only the bounded receding-horizon catalog stage and returns its replayable line. */
    fun certifyGuided(state: SpiderState): List<org.finiteplay.spider.rules.Move>? {
        if (limits.guidedAttempts <= 0) return null
        val recorded = IntArrayList(256)
        nodes = 0
        stageBaseNodes = 0
        deadlineExceeded = false
        deadline = System.nanoTime() + limits.maxMillis * 1_000_000L
        val start = FastBoard.from(state)
        try {
            for (attempt in 0 until limits.guidedAttempts) {
                val board = start.copy()
                recorded.clear()
                guidedAttemptBaseNodes = nodes
                guidedPlayout(board, attempt, recorded)
                if (board.isWon) {
                    return (0 until recorded.size).map { toRulesMove(FastMove(recorded[it])) }
                }
                if (stageNodesExhausted || deadlineExceeded || System.nanoTime() > deadline) return null
            }
        } catch (_: OutOfMemoryError) {
            return null
        }
        return null
    }

    /**
     * Diagnostic-only: runs only the greedy strategy-playout stage, with no beam/full-DFS/A*
     * fallback, and reports its own outcome directly rather than falling through the way [solve]
     * does. Not called by any production path (`docs/games/spider/EXECUTION_PLAN.md` "S6" scope) —
     * exists to answer "does greedy alone solve this deal" independently of what a later stage
     * would have found.
     */
    fun solveGreedyOnly(state: SpiderState): SolveOutcome {
        nodes = 0
        deadline = System.nanoTime() + limits.maxMillis * 1_000_000L
        deadlineExceeded = false
        val start = FastBoard.from(state)
        for (attempt in 0 until limits.playouts) {
            val board = start.copy()
            val played = playout(board, attempt, null)
            if (board.isWon) return SolveOutcome(SolveResult.SOLVED_BY_STRATEGY, nodes, played)
            if (System.nanoTime() > deadline) return SolveOutcome(SolveResult.LIMIT, nodes, 0)
        }
        return SolveOutcome(SolveResult.LIMIT, nodes, 0)
    }

    /** [solveGreedyOnly], but returning the actual replayable line rather than just its outcome. */
    fun certifyGreedyOnly(state: SpiderState): List<org.finiteplay.spider.rules.Move>? {
        nodes = 0
        deadline = System.nanoTime() + limits.maxMillis * 1_000_000L
        deadlineExceeded = false
        val start = FastBoard.from(state)
        val recorded = IntArrayList(256)
        for (attempt in 0 until limits.playouts) {
            val board = start.copy()
            recorded.clear()
            playout(board, attempt, recorded)
            if (board.isWon) return (0 until recorded.size).map { toRulesMove(FastMove(recorded[it])) }
            if (System.nanoTime() > deadline) return null
        }
        return null
    }

    /**
     * Diagnostic-only: runs only the beam-limited DFS stage, with no greedy/full-DFS/A* fallback —
     * the isolated counterpart to [solveGreedyOnly], for the same "does this one stage alone solve
     * it" question.
     */
    fun solveBeamOnly(state: SpiderState): SolveOutcome {
        nodes = 0
        deadline = System.nanoTime() + limits.maxMillis * 1_000_000L
        deadlineExceeded = false
        visited.clear()
        stageBaseNodes = 0
        beamPruned = false
        val board = FastBoard.from(state)
        val beamDepth = beamSearch(board, 0, null)
        if (beamDepth >= 0) return SolveOutcome(SolveResult.SOLVED_BY_SEARCH, nodes, beamDepth)
        val proved = !beamPruned && !stageNodesExhausted && System.nanoTime() <= deadline
        return SolveOutcome(if (proved) SolveResult.EXHAUSTED else SolveResult.LIMIT, nodes, 0)
    }

    /** [solveBeamOnly], but returning the actual replayable line rather than just its outcome. */
    fun certifyBeamOnly(state: SpiderState): List<org.finiteplay.spider.rules.Move>? {
        nodes = 0
        deadline = System.nanoTime() + limits.maxMillis * 1_000_000L
        deadlineExceeded = false
        visited.clear()
        stageBaseNodes = 0
        beamPruned = false
        val board = FastBoard.from(state)
        val recorded = IntArrayList(256)
        val beamDepth = beamSearch(board, 0, recorded)
        if (beamDepth < 0) return null
        recorded.reverse()
        return (0 until recorded.size).map { toRulesMove(FastMove(recorded[it])) }
    }

    /**
     * A campaign run's large cache is sized to what this machine can hold, not proven to fit —
     * `docs/games/spider/EXECUTION_PLAN.md` "S6" budgets ~90M states per thread at 16 threads as a
     * rough plan, not a guarantee for every deal. Reporting [SolveResult.OUT_OF_MEMORY] (never
     * crashing the JVM the caller is running many other threads in) is what lets a campaign keep
     * going and record the failure as data rather than losing the whole run.
     */
    private fun solve(start: FastBoard, record: IntArrayList?): SolveOutcome = solve(start, record, limits.maxMillis)

    private fun solve(start: FastBoard, record: IntArrayList?, maxMillis: Long): SolveOutcome =
        try {
            solveInner(start, record, maxMillis)
        } catch (_: OutOfMemoryError) {
            SolveOutcome(SolveResult.OUT_OF_MEMORY, nodes, 0)
        }

    private fun solveInner(start: FastBoard, record: IntArrayList?, maxMillis: Long): SolveOutcome {
        nodes = 0
        deadline = System.nanoTime() + maxMillis * 1_000_000L
        deadlineExceeded = false

        for (attempt in 0 until limits.playouts) {
            val board = start.copy()
            record?.clear()
            val played = playout(board, attempt, record)
            if (board.isWon) return SolveOutcome(SolveResult.SOLVED_BY_STRATEGY, nodes, played)
            if (System.nanoTime() > deadline) return SolveOutcome(SolveResult.LIMIT, nodes, 0)
        }

        if (limits.guidedAttempts > 0) {
            stageBaseNodes = nodes
            for (attempt in 0 until limits.guidedAttempts) {
                val board = start.copy()
                record?.clear()
                guidedAttemptBaseNodes = nodes
                val played = guidedPlayout(board, attempt, record)
                if (board.isWon) return SolveOutcome(SolveResult.SOLVED_BY_SEARCH, nodes, played)
                if (deadlineExceeded || System.nanoTime() > deadline) {
                    return SolveOutcome(SolveResult.LIMIT, nodes, 0)
                }
                if (stageNodesExhausted) break
            }
        }

        // Beam-limited DFS before the full-legal one: it reaches the depth a Spider win actually
        // lives at, which the full-legal search cannot within any budget (see [beamSearch]). Its
        // own fingerprint cache, cleared again below so the full-legal search's exhaustion verdict
        // is still its own — a state this stage pruned was pruned behind a beam, and must not count
        // as "already ruled out" for a search whose whole value is that it rules nothing out.
        beamPruned = limits.beamWidth <= 0
        if (limits.beamWidth > 0) {
            visited.clear()
            stageBaseNodes = nodes
            val beamBoard = start.copy()
            record?.clear()
            val beamDepth = beamSearch(beamBoard, 0, record)
            if (beamDepth >= 0) {
                record?.reverse()
                return SolveOutcome(SolveResult.SOLVED_BY_SEARCH, nodes, beamDepth)
            }
            if (System.nanoTime() > deadline) return SolveOutcome(SolveResult.LIMIT, nodes, 0)
        }

        if (!limits.useDfsAndAStarFallback) {
            val proved = !beamPruned && !stageNodesExhausted
            return SolveOutcome(if (proved) SolveResult.EXHAUSTED else SolveResult.LIMIT, nodes, 0)
        }

        visited.clear()
        stageBaseNodes = nodes
        val board = start.copy()
        record?.clear()
        val depth = search(board, 0, record)
        if (depth >= 0) {
            record?.reverse()
            return SolveOutcome(SolveResult.SOLVED_BY_SEARCH, nodes, depth)
        }
        if (System.nanoTime() > deadline) {
            return SolveOutcome(SolveResult.LIMIT, nodes, 0)
        }
        if (!stageNodesExhausted) {
            // Plain DFS's own transposition set already visited every reachable state without
            // finding a win, which is a complete proof on its own — running the weighted search
            // again over the same reachable set could never find what an exhaustive traversal
            // already ruled out.
            return SolveOutcome(SolveResult.EXHAUSTED, nodes, 0)
        }

        // DFS ran out of node budget without exhausting the board — the common case for a two- or
        // four-suit deal, where blind traversal wanders a state space large enough that neither
        // outcome above is reached before the budget runs out. A strategy-informed search order is
        // what a blind one cannot reproduce; see AStarSolver's own doc for the measurement behind
        // this stage existing at all. Its own fresh node budget, only the wall-clock deadline shared
        // — the same split Klondike's own DFS/A* stages use.
        val spentBeforeAStar = nodes
        val aStarOutcome = aStarSolver.solve(start, limits, deadline, record)
        return aStarOutcome.copy(nodes = aStarOutcome.nodes + spentBeforeAStar)
    }

    /**
     * The streamlined half of S6's two-phase campaign shape
     * (`docs/games/spider/EXECUTION_PLAN.md` "S6", mirroring the reference solver's `SMART` mode):
     * plain DFS over [streamlinedHashOf]'s unsound, suit-discarding key, so two positions differing
     * only in which suit a card carries collapse onto one transposition entry — a reduction well
     * beyond [canonicalHashOf]'s sound relabelling, paid for by no longer being a search that can
     * prove anything.
     *
     * Runs against its own [streamlinedVisited] cache, never [visited] — mixing the two would let an
     * unsound merge poison the exact search's exhaustion verdict, the same reasoning that already
     * keeps the beam stage's cache separate from the full-legal DFS's. No playouts, no beam, no A*:
     * this is one search stage on its own, meant to be tried at a small fraction of a campaign's
     * overall budget before falling back to the exact, sound [solve].
     *
     * **Structurally incapable of reporting [SolveResult.EXHAUSTED].** An unsound key's own search
     * tree emptying out proves nothing about the real game — the position it should have looked at
     * next may have been merged away — so every path out of this function ends in either
     * [SolveResult.SOLVED_BY_SEARCH] (independently replayable and replay-verified by every existing
     * caller of a certificate, exactly as an exact search's line already is) or a result that claims
     * nothing, never a proof of no solution.
     */
    fun certifyStreamlined(state: SpiderState, millisBudget: Long, record: IntArrayList? = null): SolveOutcome =
        try {
            nodes = 0
            deadline = System.nanoTime() + millisBudget * 1_000_000L
            deadlineExceeded = false
            streamlinedVisited.clear()
            val board = FastBoard.from(state)
            record?.clear()
            val depth = streamlinedSearch(board, 0, record)
            if (depth >= 0) {
                record?.reverse()
                SolveOutcome(SolveResult.SOLVED_BY_SEARCH, nodes, depth)
            } else {
                // Whether this ended on the node/time budget or on its own tree emptying out, an
                // unsound key's search proves nothing either way — always LIMIT, never EXHAUSTED.
                SolveOutcome(SolveResult.LIMIT, nodes, 0)
            }
        } catch (_: OutOfMemoryError) {
            SolveOutcome(SolveResult.OUT_OF_MEMORY, nodes, 0)
        }

    private fun streamlinedSearch(board: FastBoard, depth: Int, record: IntArrayList?): Int {
        nodes++
        if (board.isWon) return depth
        if (depth >= MAX_DEPTH - 1) return -1
        if ((nodes and 0x3FF) == 0L && System.nanoTime() > deadline) deadlineExceeded = true
        if (nodes > limits.maxNodes || deadlineExceeded) return -1

        if (!streamlinedVisited.add(streamlinedHashOf(board, hashScratch))) return -1

        val local = streamlinedMoves[depth]
        local.clear()
        generateMoves(board, local)
        val undo = undos[depth]
        for (i in 0 until local.size) {
            val move = FastMove(local[i])
            applyFast(board, move, undo)
            path[depth] = local[i]
            val found = streamlinedSearch(board, depth + 1, record)
            if (found >= 0) {
                record?.add(local[i])
                return found
            }
            undoFast(board, undo)
            if (nodes > limits.maxNodes || deadlineExceeded) return -1
        }
        return -1
    }

    /**
     * One greedy game, played to a win or a dead end without ever taking a move back.
     *
     * [variation] perturbs the choice among close-scoring moves, so repeated playouts are not the
     * same game replayed. A deal that a greedy line wins only from one particular tie-break is
     * common enough that a handful of varied attempts is far better value than one careful one.
     *
     * The visited set is what makes this terminate. Greedy play with no memory oscillates — a card
     * moves A to B, scores the same going back, and the playout spends its whole depth budget
     * shuffling two piles without ever dealing. Refusing a move that returns to a position already
     * seen turns that into progress or a dead end, which are both answers.
     */
    private fun playout(board: FastBoard, variation: Int, record: IntArrayList?): Int {
        val undo = UndoRecord()
        val seen = LongHashSet(1 shl 12)
        val local = IntArrayList(128)
        var played = 0
        var idle = 0
        var random = (variation * 0x9E3779B9L) xor 0xDEADBEEFL
        seen.add(hashOf(board))

        while (played < MAX_DEPTH) {
            nodes++
            if (board.isWon) return played
            generateMoves(board, local)
            if (local.size == 0) return played

            val before = boardScore(board)
            var best = -1
            var bestScore = Int.MIN_VALUE
            var bestProductive = false
            // A flag, not a packed value: DEAL_ROW is packed as -1, which is also the "no move"
            // sentinel, so testing `dealMove >= 0` silently made dealing unreachable.
            var canDeal = false
            for (i in 0 until local.size) {
                val packed = local[i]
                val move = FastMove(packed)
                if (move.isDeal) { canDeal = true; continue }

                // A move back into a position already played is not progress, whatever it scores.
                applyFast(board, move, undo)
                val repeat = !seen.add(hashOf(board))
                undoFast(board, undo)
                if (repeat) continue

                // One apply/undo answers both "is this productive" and "the waterfall" (how many
                // moves it opens up) together, rather than a second temporary application per
                // candidate purely for the lookahead.
                val evaluated = evaluateMove(board, move, before)
                var score = scoreMove(board, move) + evaluated.followOnMoves * FOLLOW_ON_WEIGHT
                if (variation > 0) {
                    random = random * 6364136223846793005L + 1442695040888963407L
                    score += ((random ushr 59).toInt() and 0x7) - 3
                }
                // Productive beats unproductive outright, whatever either scores.
                val productive = evaluated.productive
                if ((productive && !bestProductive) || (productive == bestProductive && score > bestScore)) {
                    bestScore = score
                    bestProductive = productive
                    best = packed
                }
            }

            // "Exhaust the beneficial moves, then deal" — the one point every strategy guide
            // agrees on. Beneficial is the operative word: a move that neither banks, nor exposes
            // a card, nor empties a column, nor builds in suit is just rearranging, and a playout
            // that accepts those wanders through hundreds of distinct-but-pointless positions
            // without ever reaching the stock.
            //
            // The third branch is the one the strategy guides all miss. They say to keep an empty
            // column open at all times, and they say to deal when nothing else helps — but the
            // stock refuses to deal while any column is empty (`RULES.md` "The stock"), so those
            // two pieces of advice deadlock. A greedy player following both to the letter empties
            // a column, cannot deal, and stalls with all fifty stock cards unplayed. Measured: the
            // first version of this playout finished every one-suit deal with stockPos = 0.
            //
            // So when nothing is productive and the stock is blocked, filling an empty column is
            // itself the productive move.
            // Nullable rather than a -1 sentinel: DEAL_ROW packs to -1, so every "is there a
            // move?" test written against -1 quietly treats dealing as "no move" and stops the
            // playout dead. That bug cost two rounds of diagnosis; the type now rules it out.
            val chosen: Int? = when {
                best >= 0 && bestProductive -> best
                canDeal -> FastMove.DEAL_ROW.packed
                else -> {
                    val unblock = if (board.stockPos < board.stock.size) fillEmptyColumn(board, local) else -1
                    when {
                        unblock >= 0 -> unblock
                        // A rearrangement can set up a productive move the greedy player cannot
                        // see, but only a search can tell which one — so a few are allowed and
                        // then the playout admits it is stuck rather than wandering to its depth
                        // limit.
                        best >= 0 && idle < MAX_IDLE -> best
                        else -> null
                    }
                }
            }
            if (chosen == null) return played
            idle = if (bestProductive || FastMove(chosen).isDeal) 0 else idle + 1
            applyFast(board, FastMove(chosen), undo)
            record?.add(chosen)
            played++
        }
        return played
    }

    /**
     * Repeatedly searches a small horizon, commits the best position it found, and searches again.
     * This reaches Spider-length lines without retaining a tree of boards: only the current board,
     * one undo record per depth, and the best short path are kept. Equal-looking alternatives are
     * perturbed between attempts so a poor early commitment is not repeated forever.
     */
    private fun guidedPlayout(board: FastBoard, variation: Int, record: IntArrayList?): Int {
        val committedSeen = LongHashSet(1 shl 12)
        // The original algorithm deliberately forgets old transpositions from a fixed-size table.
        // A bounded generational cache gives the same property without letting one depth-13 pass
        // consume the catalog process's heap.
        val horizonSeen: TranspositionCache = GenerationalLongHashSet(250_000, 8)
        val commitUndo = UndoRecord()
        val candidates = IntArrayList(64)
        val returnPoints = java.util.ArrayDeque<GuidedReturnPoint>()
        var played = 0
        var round = 0
        committedSeen.add(hashOf(board))
        horizonSeen.add(hashOf(board))

        while (played < MAX_DEPTH && !board.isWon) {
            resetGuidedAlternatives(guidedPositionScore(board, variation))
            guidedHorizonBaseNodes = nodes
            val depth = limits.guidedDepth.coerceIn(1, MAX_GUIDED_DEPTH)
            guidedExplore(board, 0, depth, variation, round, horizonSeen, requireCommitted = false, hasCommitted = false)
            if (deadlineExceeded || stageNodesExhausted || guidedAttemptExhausted) return played

            // An empty column can be worth more than every ordinary rearrangement, yet eventually
            // one has to spend it to expose a card or make the next stock deal. When score-only
            // search cannot improve, repeat it while accepting only paths containing a move that
            // cannot simply be undone: a reveal, bank, or separation of an off-rank pair.
            if (guidedBestLength == 0 && board.stockPos < board.stock.size) {
                resetGuidedAlternatives(Long.MIN_VALUE)
                guidedHorizonBaseNodes = nodes
                guidedExplore(board, 0, depth, variation, round, horizonSeen, requireCommitted = true, hasCommitted = false)
                if (deadlineExceeded || stageNodesExhausted || guidedAttemptExhausted) return played
            }

            if (guidedBestLength > 0) {
                if (guidedAlternativeCount > 1) {
                    val paths = Array(guidedAlternativeCount - 1) { alternative ->
                        guidedAlternativePaths[alternative + 1].copyOf()
                    }
                    val lengths = IntArray(guidedAlternativeCount - 1) { guidedAlternativeLengths[it + 1] }
                    returnPoints.addLast(GuidedReturnPoint(board.copy(), record?.size ?: played, paths, lengths))
                    while (returnPoints.size > MAX_GUIDED_RETURN_POINTS) returnPoints.removeFirst()
                }
                played = applyGuidedPath(board, guidedBestPath, guidedBestLength, record, played, commitUndo)
                if (board.isWon || played >= MAX_DEPTH) continue
                if (committedSeen.add(hashOf(board))) {
                    round++
                    continue
                }
            }

            if (guidedBestLength == 0) {
                generateMoves(board, candidates)
                val forced = when {
                    board.canDealRow() -> FastMove.DEAL_ROW.packed
                    board.stockPos < board.stock.size -> fillEmptyColumn(board, candidates)
                    else -> -1
                }
                if (forced != -1) {
                    applyFast(board, FastMove(forced), commitUndo)
                    record?.add(forced)
                    played++
                    if (committedSeen.add(hashOf(board))) {
                        round++
                        continue
                    }
                }
            }

            var restored = false
            while (returnPoints.isNotEmpty() && !restored) {
                val point = returnPoints.peekLast()
                if (point.next >= point.paths.size) {
                    returnPoints.removeLast()
                    continue
                }
                board.copyFrom(point.board)
                record?.truncate(point.recordSize)
                played = point.recordSize
                val alternative = point.next++
                played = applyGuidedPath(
                    board,
                    point.paths[alternative],
                    point.lengths[alternative],
                    record,
                    played,
                    commitUndo,
                )
                restored = committedSeen.add(hashOf(board))
            }
            if (!restored) return played
            round++
        }
        return played
    }

    private fun applyGuidedPath(
        board: FastBoard,
        moves: IntArray,
        length: Int,
        record: IntArrayList?,
        playedBefore: Int,
        undo: UndoRecord,
    ): Int {
        var played = playedBefore
        for (i in 0 until length) {
            val packed = moves[i]
            applyFast(board, FastMove(packed), undo)
            record?.add(packed)
            played++
            if (board.isWon || played >= MAX_DEPTH) break
        }
        return played
    }

    private fun guidedExplore(
        board: FastBoard,
        depth: Int,
        depthLimit: Int,
        variation: Int,
        round: Int,
        seen: TranspositionCache,
        requireCommitted: Boolean,
        hasCommitted: Boolean,
    ) {
        nodes++
        if (board.isWon) {
            rememberGuidedPath(Long.MAX_VALUE, depth)
            return
        }
        if ((nodes and 0x3FF) == 0L && System.nanoTime() > deadline) deadlineExceeded = true
        if (depth >= depthLimit || guidedHorizonExhausted || guidedAttemptExhausted || stageNodesExhausted || deadlineExceeded) {
            if (depth > 0 && (!requireCommitted || hasCommitted)) rememberGuidedPath(guidedPositionScore(board, variation), depth)
            return
        }
        // The committed root was necessarily encountered in the preceding horizon. Revisit that
        // root, but prune repeated descendants against the cache retained across the whole game.
        if (depth > 0 && !seen.add(hashOf(board))) return

        val candidates = guidedCandidates[depth]
        candidates.clear()
        generateGuidedMoves(board, candidates, variation, round, depth)

        if (candidates.size == 0) {
            if (depth > 0 && (!requireCommitted || hasCommitted)) rememberGuidedPath(guidedPositionScore(board, variation), depth)
            return
        }
        val undo = undos[depth]
        for (i in 0 until candidates.size) {
            val packed = candidates[i]
            val move = FastMove(packed)
            if (isImmediateReversal(move, depth)) continue
            val committed = isCommittedMove(board, move)
            guidedPath[depth] = packed
            guidedCommitted[depth] = committed
            applyFast(board, move, undo)
            guidedExplore(
                board,
                depth + 1,
                depthLimit,
                variation,
                round,
                seen,
                requireCommitted,
                hasCommitted || committed || undo.bankedCount > 0,
            )
            undoFast(board, undo)
            if (guidedBestScore == Long.MAX_VALUE || guidedHorizonExhausted || guidedAttemptExhausted || stageNodesExhausted || deadlineExceeded) return
        }
    }

    private val guidedHorizonExhausted: Boolean
        get() = nodes - guidedHorizonBaseNodes >= limits.guidedNodesPerHorizon

    private val guidedAttemptExhausted: Boolean
        get() = nodes - guidedAttemptBaseNodes >=
            (limits.maxNodes / limits.guidedAttempts.coerceAtLeast(1)).coerceAtLeast(1)

    /**
     * The reference search's principal branching reduction: consider only the longest movable
     * suited run in each source column, same-suit destinations first, and at most one empty
     * destination because empty columns are interchangeable before a card is placed on them.
     */
    private fun generateGuidedMoves(
        board: FastBoard,
        out: IntArrayList,
        variation: Int,
        round: Int,
        depth: Int,
    ) {
        var random = variation.toLong() * 6364136223846793005L + round * 1442695040888963407L + depth * 0x9E3779B9L
        random = random xor (random ushr 33)
        val offset = ((random ushr 1) % TABLEAU_COLUMNS).toInt()
        val stride = guidedStrides[((random ushr 17) and 3L).toInt()]
        val destinationOffset = ((random ushr 23) % TABLEAU_COLUMNS).toInt()
        val destinationStride = guidedStrides[((random ushr 29) and 3L).toInt()]
        for (step in 0 until TABLEAU_COLUMNS) {
            val from = (offset + step * stride) % TABLEAU_COLUMNS
            if (board.len[from] == 0) continue
            val fromIndex = board.sequenceStart(from)
            val card = board.cards[from][fromIndex]
            var emptyUsed = fromIndex == 0

            for (pass in 0..1) {
                for (destinationStep in 0 until TABLEAU_COLUMNS) {
                    val to = (destinationOffset + destinationStep * destinationStride) % TABLEAU_COLUMNS
                    if (to == from) continue
                    if (board.len[to] == 0) {
                        if (pass != 1 || emptyUsed) continue
                        emptyUsed = true
                        out.add(FastMove.transfer(from, fromIndex, to).packed)
                        continue
                    }
                    val target = board.topOf(to)
                    if (board.rankOf(target) != board.rankOf(card) + 1) continue
                    val sameSuit = board.suitOf(target) == board.suitOf(card)
                    if ((pass == 0) == sameSuit) out.add(FastMove.transfer(from, fromIndex, to).packed)
                }
            }
        }
    }

    private fun isImmediateReversal(move: FastMove, depth: Int): Boolean {
        if (depth == 0) return false
        val previous = FastMove(guidedPath[depth - 1])
        if (previous.isDeal || guidedCommitted[depth - 1]) return false
        return move.from == previous.to && move.to == previous.from
    }

    private fun isCommittedMove(board: FastBoard, move: FastMove): Boolean {
        if (move.isDeal) return true
        if (move.fromIndex == board.faceDown[move.from] && move.fromIndex > 0) return true
        if (move.fromIndex == 0) return false
        val moved = board.cards[move.from][move.fromIndex]
        val uncovered = board.cards[move.from][move.fromIndex - 1]
        return board.rankOf(uncovered) != board.rankOf(moved) + 1 || board.suitOf(uncovered) != board.suitOf(moved)
    }

    private fun resetGuidedAlternatives(minimumScore: Long) {
        guidedAlternativeCount = 0
        guidedMinimumScore = minimumScore
        guidedBestScore = minimumScore
        guidedBestLength = 0
    }

    private fun rememberGuidedPath(score: Long, length: Int) {
        if (score <= guidedMinimumScore || length == 0) return
        // Return points are useful only when they begin differently. Keeping several endings of
        // the same first choice merely returns to the same branch after it has already failed.
        for (i in 0 until guidedAlternativeCount) {
            if (guidedAlternativePaths[i][0] == guidedPath[0]) {
                if (score <= guidedAlternativeScores[i]) return
                guidedAlternativeScores[i] = score
                guidedAlternativeLengths[i] = length
                System.arraycopy(guidedPath, 0, guidedAlternativePaths[i], 0, length)
                sortGuidedAlternatives()
                syncGuidedBest()
                return
            }
        }
        var position = guidedAlternativeCount
        while (position > 0 && guidedAlternativeScores[position - 1] < score) position--
        if (position >= MAX_GUIDED_ALTERNATIVES) return
        val last = minOf(guidedAlternativeCount, MAX_GUIDED_ALTERNATIVES - 1)
        for (i in last downTo position + 1) {
            guidedAlternativeScores[i] = guidedAlternativeScores[i - 1]
            guidedAlternativeLengths[i] = guidedAlternativeLengths[i - 1]
            System.arraycopy(guidedAlternativePaths[i - 1], 0, guidedAlternativePaths[i], 0, MAX_GUIDED_DEPTH)
        }
        guidedAlternativeScores[position] = score
        guidedAlternativeLengths[position] = length
        System.arraycopy(guidedPath, 0, guidedAlternativePaths[position], 0, length)
        if (guidedAlternativeCount < MAX_GUIDED_ALTERNATIVES) guidedAlternativeCount++
        syncGuidedBest()
    }

    private fun sortGuidedAlternatives() {
        for (i in 1 until guidedAlternativeCount) {
            var current = i
            while (current > 0 && guidedAlternativeScores[current] > guidedAlternativeScores[current - 1]) {
                val score = guidedAlternativeScores[current - 1]
                guidedAlternativeScores[current - 1] = guidedAlternativeScores[current]
                guidedAlternativeScores[current] = score
                val length = guidedAlternativeLengths[current - 1]
                guidedAlternativeLengths[current - 1] = guidedAlternativeLengths[current]
                guidedAlternativeLengths[current] = length
                val path = guidedAlternativePaths[current - 1]
                guidedAlternativePaths[current - 1] = guidedAlternativePaths[current]
                guidedAlternativePaths[current] = path
                current--
            }
        }
    }

    private fun syncGuidedBest() {
        if (guidedAlternativeCount == 0) return
        guidedBestScore = guidedAlternativeScores[0]
        guidedBestLength = guidedAlternativeLengths[0]
        System.arraycopy(guidedAlternativePaths[0], 0, guidedBestPath, 0, guidedBestLength)
    }

    /** Position value used by guided search; large terms are deliberately lexicographic. */
    private fun guidedPositionScore(board: FastBoard, variation: Int): Long {
        val stockLeft = board.stockPos < board.stock.size
        val suitGoal = variation and 3
        var score = board.banked.toLong() * if (stockLeft) 19_000_000L else 200_000_000L
        for (c in 0 until TABLEAU_COLUMNS) {
            if (board.len[c] == 0) {
                score += if (stockLeft) 200_000_000L else 19_000_000L
                continue
            }
            val hidden = board.faceDown[c].toLong()
            score -= hidden * hidden
            score -= hidden * 100_000L
            score -= board.len[c]
            val cards = board.cards[c]
            var run = 1L
            for (i in board.faceDown[c] + 1 until board.len[c]) {
                val above = cards[i - 1]
                val below = cards[i]
                when {
                    board.rankOf(above) != board.rankOf(below) + 1 -> {
                        run = 1
                        score -= 5
                    }
                    board.suitOf(above) != board.suitOf(below) -> {
                        run = 1
                        score--
                    }
                    else -> {
                        run += if (board.suitOf(below) == suitGoal) 5 else 1
                        score += run * run * 5L
                    }
                }
            }
        }
        return score
    }

    /**
     * How good a position is, in the terms the strategy guides actually argue in: sequences banked,
     * cards still face down, how broken the face-up piles are, and empty columns.
     *
     * A single number rather than a list of special cases, because "is this move progress?" has to
     * hold at every suit count. Naming particular move shapes did not: at one suit every build is
     * in suit, so a rule that counts in-suit building as progress calls *every* move progress and
     * the playout never deals — while a rule that counts only banking and exposing calls almost
     * nothing progress and the playout dumps the whole stock in a few dozen moves. Both were
     * measured here before this replaced them.
     */
    private fun boardScore(board: FastBoard): Int {
        var score = board.banked * 1000
        for (c in 0 until TABLEAU_COLUMNS) {
            score -= board.faceDown[c] * 20
            if (board.len[c] == 0) {
                score += 30
                continue
            }
            // A "break" is any adjacent pair in the face-up part that does not continue a suited
            // run. Breaks are what has to be undone later, so fewer is better.
            val col = board.cards[c]
            for (i in board.faceDown[c] + 1 until board.len[c]) {
                val above = col[i - 1]
                val below = col[i]
                if (board.suitOf(above) != board.suitOf(below) || board.rankOf(above) != board.rankOf(below) + 1) {
                    score -= 5
                }
            }
        }
        return score
    }

    private class MoveEval(var productive: Boolean = false, var followOnMoves: Int = 0)

    /**
     * Whether [move] leaves the board measurably better than it found it, and — the "waterfall"
     * public strategy guides describe (`docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md`: "look for
     * the move that unlocks the most follow-on moves, not the first legal one") — how many legal
     * moves (excluding the row deal, which is available from almost every board and would swamp
     * the count) the resulting board offers. One apply/undo answers both, rather than a second
     * temporary application purely for the lookahead.
     */
    private fun evaluateMove(board: FastBoard, move: FastMove, before: Int): MoveEval {
        val undo = probeUndo
        applyFast(board, move, undo)
        val after = boardScore(board)
        generateMoves(board, followOnScratch)
        var followOnMoves = followOnScratch.size
        if (board.canDealRow()) followOnMoves--
        undoFast(board, undo)
        moveEval.productive = after > before
        moveEval.followOnMoves = followOnMoves
        return moveEval
    }

    /**
     * The best move that fills an empty column, or -1 when none does.
     *
     * A King is much the least bad thing to put there — it is the one card nothing can ever be
     * stacked on, so any other choice leaves the column half-dead as well as occupied — and after
     * that, the move that takes the most cards off a column with buried cards under it.
     */
    private fun fillEmptyColumn(board: FastBoard, candidates: IntArrayList): Int {
        var best = -1
        var bestScore = Int.MIN_VALUE
        for (i in 0 until candidates.size) {
            val move = FastMove(candidates[i])
            if (move.isDeal) continue
            if (board.len[move.to] != 0) continue
            val card = board.cards[move.from][move.fromIndex]
            var score = board.len[move.from] - move.fromIndex
            if (board.rankOf(card) == FastBoard.RANKS - 1) score += 100
            if (move.fromIndex == board.faceDown[move.from]) score += 40
            // Emptying one column to fill another is no help at all when the point is to deal.
            if (move.fromIndex == 0) score -= 500
            if (score > bestScore) {
                bestScore = score
                best = candidates[i]
            }
        }
        return best
    }

    /**
     * How good a move looks to the greedy player, in the order the strategy guides agree on:
     * bank whenever possible, then expose a face-down card, then empty a column, then prefer
     * in-suit builds, and deal only when nothing else is worth doing.
     */
    private fun scoreMove(board: FastBoard, move: FastMove): Int {
        if (move.isDeal) return -1000

        val from = move.from
        val to = move.to
        val fromIndex = move.fromIndex
        val runLength = board.len[from] - fromIndex
        var score = 0

        val card = board.cards[from][fromIndex]
        // Landing on a card that continues the run in suit is the build worth making; landing
        // off-suit makes a pile that has to be taken apart again later. When it has to be
        // off-suit, build high: a pile headed by a high card has more room above it and stays
        // useful longer, one headed low is finished as soon as an Ace lands on it
        // (`docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md`: "if you must build off-suit, build high").
        if (board.len[to] > 0) {
            val target = board.topOf(to)
            if (board.suitOf(target) == board.suitOf(card)) score += 60 else score -= 20 - board.rankOf(target)
        }

        // Emptying the source column outright is the most valuable thing short of banking.
        if (fromIndex == 0) score += 200
        // Exposing a face-down card is the next most valuable, and worth more the more are
        // buried in *this* column — but a small, independent bonus favors a column that is
        // already close to fully open over one that still has a long way to go, so the playout
        // finishes a nearly-open column rather than starting several at once
        // (`docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md`: "work the shortest columns to open
        // one up... the cheapest to clear").
        else if (fromIndex == board.faceDown[from]) {
            score += 100 + board.faceDown[from] * 4
            score += (SHORT_COLUMN_BUDGET - board.faceDown[from]).coerceAtLeast(0)
        }

        // Filling an empty column spends the board's scarcest resource; a King is the least bad
        // thing to spend it on, since nothing can ever be placed on anything else there.
        if (board.len[to] == 0) {
            score -= 120
            if (board.rankOf(card) == FastBoard.RANKS - 1) score += 90
        }

        // Longer runs move more work in one go.
        score += runLength
        return score
    }

    /**
     * Depth-first search that only ever tries the [SolverLimits.beamWidth] most promising moves at
     * each board, ranked by the same public-strategy scoring the greedy playout uses, against the
     * same column- and suit-invariant fingerprint cache the plain DFS uses ([canonicalHashOf], [visited]).
     * Returns the winning depth, or -1.
     *
     * This is the stage built for the shape of Spider's own problem rather than borrowed from
     * Klondike. A winning line runs well past a hundred moves while ten to thirty moves are legal
     * at almost every board, so a search that keeps every option cannot reach the depth a win
     * lives at within any budget — measured, repeatedly, on two- and four-suit deals. A greedy
     * playout reaches that depth but never reconsiders, so one bad step ends it. Keeping the best
     * three and backtracking through them is the middle: deep enough to reach a win, narrow enough
     * to afford real backtracking, and it inherits the playout's own strategy ordering for free.
     *
     * **It can never prove a board unsolvable**, and deliberately reports [SolveResult.LIMIT]
     * rather than [SolveResult.EXHAUSTED] when it runs out: the moves outside the beam were
     * discarded without being looked at, so an empty search tree here says nothing about the board
     * — only the full-legal DFS's own exhaustion is a proof (`DESIGN.md` "Hint").
     */
    private fun beamSearch(board: FastBoard, depth: Int, record: IntArrayList?): Int {
        nodes++
        if (board.isWon) return depth
        if (depth >= MAX_DEPTH - 1) return -1
        if ((nodes and 0x3FF) == 0L && System.nanoTime() > deadline) deadlineExceeded = true
        if (stageNodesExhausted || deadlineExceeded) return -1

        if (!visited.add(hashOf(board))) return -1

        val candidates = beamCandidates[depth]
        candidates.clear()
        generateMoves(board, candidates)
        if (candidates.size == 0) return -1

        val width = limits.beamWidth.coerceIn(1, MAX_BEAM_WIDTH)
        val moves = beamMoves[depth]
        val scores = beamScores[depth]
        var kept = 0
        val before = boardScore(board)

        for (i in 0 until candidates.size) {
            val packed = candidates[i]
            val move = FastMove(packed)
            // "Exhaust the beneficial moves, then deal" as a *ranking* rather than a special case:
            // dealing sits below every productive move and above every unproductive one, so it
            // enters the beam exactly when the board has run out of productive moves — and it must
            // be able to enter it, since the fifty cards in the stock cannot be banked unplayed.
            val score = if (move.isDeal) {
                PRODUCTIVE_BONUS - 1
            } else {
                val evaluated = evaluateMove(board, move, before)
                val productive = if (evaluated.productive) PRODUCTIVE_BONUS else 0
                scoreMove(board, move) + evaluated.followOnMoves * FOLLOW_ON_WEIGHT + productive
            }

            var pos = kept
            while (pos > 0 && scores[pos - 1] < score) pos--
            if (pos >= width) {
                beamPruned = true
                continue
            }
            if (kept == width) beamPruned = true
            var j = minOf(kept, width - 1)
            while (j > pos) {
                scores[j] = scores[j - 1]
                moves[j] = moves[j - 1]
                j--
            }
            scores[pos] = score
            moves[pos] = packed
            if (kept < width) kept++
        }

        val undo = undos[depth]
        for (k in 0 until kept) {
            val packed = moves[k]
            applyFast(board, FastMove(packed), undo)
            val found = beamSearch(board, depth + 1, record)
            if (found >= 0) {
                record?.add(packed)
                return found
            }
            undoFast(board, undo)
            if (stageNodesExhausted || deadlineExceeded) return -1
        }
        return -1
    }

    /** Depth-first search with a transposition set. Returns the winning depth, or -1. */
    private fun search(board: FastBoard, depth: Int, record: IntArrayList?): Int {
        nodes++
        if (board.isWon) return depth
        if (depth >= MAX_DEPTH - 1) return -1
        if ((nodes and 0x3FF) == 0L && System.nanoTime() > deadline) deadlineExceeded = true
        if (stageNodesExhausted || deadlineExceeded) return -1

        val hash = hashOf(board)
        if (!visited.add(hash)) return -1

        val local = searchMoves[depth]
        local.clear()
        generateMoves(board, local)
        val undo = undos[depth]
        for (i in 0 until local.size) {
            val move = FastMove(local[i])
            applyFast(board, move, undo)
            path[depth] = local[i]
            val found = search(board, depth + 1, record)
            if (found >= 0) {
                record?.add(local[i])
                return found
            }
            undoFast(board, undo)
            if (stageNodesExhausted || deadlineExceeded) return -1
        }
        return -1
    }

    /** Delegates to the shared [canonicalHashOf] so this search's transposition key is identical to [AStarSolver]'s own. */
    private fun hashOf(board: FastBoard): Long = canonicalHashOf(board, hashScratch)

    private companion object {
        /**
         * Deepest line the search or a playout will follow. Spider games run long — a hundred-odd
         * moves is ordinary — but a line this deep is a loop the transposition set failed to
         * catch rather than progress.
         */
        const val MAX_DEPTH = 600

        /** Consecutive rearranging moves a playout tolerates before calling the position stuck. */
        const val MAX_IDLE = 12

        /**
         * Per legal move the resulting board offers — the "waterfall"
         * (`docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md`: "look for the move that unlocks the
         * most follow-on moves, not the first legal one"). Small relative to banking (1000) or
         * exposing (100+): a tie-breaker among moves already close in the other terms, not a
         * signal that overrides them — a move that exposes a card is still worth more than one
         * that merely leaves more options open.
         */
        const val FOLLOW_ON_WEIGHT = 3

        /**
         * Face-down count below which exposing a card gets an extra "this column is nearly open"
         * bonus, tapering to zero at and above it. Four is small enough that it only distinguishes
         * a column genuinely close to empty from an ordinary one — not a tuned constant, a rough
         * match to "within 10-15 moves" being the shape of advice guides give for reaching an
         * empty column, without literally counting moves here.
         */
        const val SHORT_COLUMN_BUDGET = 4

        /**
         * Added to a move's score in [beamSearch] when it leaves the board measurably better than
         * it found it. Large enough to dominate every other term outright, because "productive
         * beats unproductive whatever either scores" is the one rule the playout already enforced
         * structurally and the beam has to reproduce as a number — the remaining terms only order
         * moves *within* those two groups.
         */
        const val PRODUCTIVE_BONUS = 10_000

        /** Ceiling on [SolverLimits.beamWidth], so the per-depth beam arrays can be preallocated. */
        const val MAX_BEAM_WIDTH = 8

        const val MAX_GUIDED_DEPTH = 16
        const val MAX_GUIDED_ALTERNATIVES = 4
        const val MAX_GUIDED_RETURN_POINTS = 64
    }
}
