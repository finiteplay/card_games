package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.SpiderState
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference

/**
 * A lock-free (CAS-based, never blocks) concurrent transposition cache, for many threads searching
 * the *same* deal at once. `docs/games/spider/EXECUTION_PLAN.md` "S6" once ruled this shape out
 * ("parallelise across hands, not within a search") on the reasoning that a concurrent table on the
 * hottest random-access structure would cost more than it bought; a spike measured that reasoning
 * wrong for this workload — twelve threads sharing one such cache on one deal ran **~7x** the nodes
 * per second of one thread on the same deal, not the near-zero gain contention would predict. The
 * section has the full measurement and the corrected design.
 *
 * **Sharded**, not one flat array, for two independent reasons. First, capacity: a Java array is
 * indexed by a signed 32-bit int, so one array tops out at 2^30 longs (~8.6GB) — confirmed directly,
 * a single-shard spike run's own JVM showed exactly an 8GB working set against a 20GB heap. Several
 * shards is the only way to use a multi-thread run's larger memory budget at all. Second, and the
 * reason to shard even at a size one array could hold: spreading the hash's high bits across
 * independent arrays spreads CAS traffic across independent cache lines instead of funnelling every
 * thread through one.
 *
 * **Non-evicting**, unlike [GenerationalLongHashSet] — not merely unimplemented, but deliberately
 * out of scope for this class: coordinating generation rotation across many concurrent writers
 * safely is a materially harder problem than the single-threaded case that class solves, and the
 * single-threaded bounded campaign's own measurement makes it unnecessary for a first real attempt —
 * its peak was ~17M unique states over 1.46 billion node visits, roughly a 1.2% unique-insertion
 * rate. A cache sized with real headroom over that ratio should not fill within one hour even at
 * several times that node rate. [add] still may not spin forever if a shard *does* fill anyway: it
 * gives up after one full pass of that shard and reports the state as unseen rather than loop —
 * the same direction every approximation in this file already errs in (costing redundant search,
 * never a wrong answer), via [saturated] going true so a caller can tell a "no win" result apart
 * from a genuine, sound proof of one (`parallelSearch`'s own doc has the exhaustion argument this
 * feeds into).
 */
internal class ConcurrentTranspositionCache(shardCountPow2: Int, perShardCapacityPow2: Int) {
    private val shardMask = (1 shl shardCountPow2) - 1
    private val shards = Array(1 shl shardCountPow2) { AtomicLongArray(1 shl perShardCapacityPow2) }
    private val inShardMask = shards[0].length() - 1
    private val shardShift = perShardCapacityPow2

    /** Set once any shard has been probed to a full pass without finding a slot — see the class doc. */
    val saturated = AtomicBoolean(false)

    /** Adds [hash], returning false only when it was already present. */
    fun add(hash: Long): Boolean {
        val key = if (hash == 0L) 1L else hash
        val mixed = (key * -7046029254386353131L) ushr 20
        val shard = shards[(mixed ushr shardShift).toInt() and shardMask]
        val start = mixed.toInt() and inShardMask
        var i = start
        var probes = 0
        while (probes <= inShardMask) {
            val existing = shard.get(i)
            if (existing == key) return false
            if (existing == 0L) {
                if (shard.compareAndSet(i, 0L, key)) return true
                val now = shard.get(i)
                if (now == key) return false
            }
            i = (i + 1) and inShardMask
            probes++
        }
        saturated.set(true)
        return true
    }
}

/** Everything one worker thread mutates in place while searching — never shared across threads. */
private class ParallelWorkerState {
    val undos = Array(600) { UndoRecord() }
    val hashScratch = HashScratch()
    val moves = Array(600) { IntArrayList(64) }
}

/** A shallow subtree handed to one worker: its root board, and the moves that reached it from the true root. */
private class ParallelTask(val board: FastBoard, val pathFromRoot: IntArray)

/**
 * Breadth-first expands [root] into a full, disjoint cut of the game tree at whatever depth first
 * reaches [minFrontier] positions (or a dead end, or [maxDepth]) — every one of every deeper
 * reachable board's root-to-there paths passes through exactly one of the returned tasks, since each
 * expansion step replaces every current frontier board with *all* of its children, never a sample.
 * That completeness is what lets [runParallelPhase]'s canonical-key call claim
 * [SolveResult.EXHAUSTED] at all: a full cut searched exhaustively from every piece, with a sound
 * key and a cache that never gave up early, covers the same ground a single sequential exhaustive
 * search would.
 *
 * [maxDepth] guards against a pathologically low-branching board needing many levels to reach
 * [minFrontier] at all: each level expands *every* current board's *every* move, so the worst case
 * at depth d is (real branching factor)^d tasks, not a sample — a spike measured real four-suit
 * boards reaching a few hundred tasks by depth 2–3 well under [minFrontier]'s usual target, so 4 is
 * headroom over what is actually needed, not a value chosen to be hit routinely.
 */
private fun buildParallelFrontier(root: FastBoard, minFrontier: Int, maxDepth: Int): List<ParallelTask> {
    var frontier = listOf(ParallelTask(root.copy(), IntArray(0)))
    val scratch = IntArrayList(64)
    var depth = 0
    while (frontier.size < minFrontier && depth < maxDepth) {
        val next = ArrayList<ParallelTask>(frontier.size * 8)
        for (task in frontier) {
            generateMoves(task.board, scratch)
            if (scratch.size == 0) {
                next.add(task)
                continue
            }
            val undo = UndoRecord()
            for (i in 0 until scratch.size) {
                val copy = task.board.copy()
                applyFast(copy, FastMove(scratch[i]), undo)
                next.add(ParallelTask(copy, task.pathFromRoot + scratch[i]))
            }
        }
        frontier = next
        depth++
    }
    return frontier
}

/** [parallelSearchNode]'s three-way outcome: a real result, not just win-or-not, is what lets the caller tell "this subtree is provably empty" apart from "budget ran out here". */
private const val CUT_SHORT = -2
private const val GENUINELY_EMPTY = -1

/**
 * Depth-first from [board] (already at [depth] moves from the true root), sharing [cache] and
 * [nodes]/[found]/[deadline]/[maxNodes] with every other worker on this phase. [hashOf] is the
 * canonical (sound) or streamlined (unsound) key depending on which phase is calling — the same
 * choice [SpiderSolver.search]/[SpiderSolver.streamlinedSearch] make, for the same reasons.
 *
 * Returns the winning depth (>= 0), [GENUINELY_EMPTY] when this subtree has no win within the depth
 * cap and was searched to completion up to it, or [CUT_SHORT] when a shared budget (time, nodes, or
 * another worker already finding a win) stopped it instead — the distinction [runParallelPhase]
 * needs to tell a real [SolveResult.EXHAUSTED] apart from [SolveResult.LIMIT]. "Completion up to the
 * depth cap" is the same scope [SpiderSolver.search]'s own `EXHAUSTED` already has — `MAX_DEPTH`'s
 * own doc has the product reasoning for why that scope is the right one, not a compromise.
 */
private fun parallelSearchNode(
    board: FastBoard,
    depth: Int,
    cache: ConcurrentTranspositionCache,
    state: ParallelWorkerState,
    nodes: AtomicLong,
    found: AtomicBoolean,
    deadline: Long,
    maxNodes: Long,
    hashOf: (FastBoard, HashScratch) -> Long,
    record: IntArrayList,
): Int {
    val n = nodes.incrementAndGet()
    if (board.isWon) return depth
    // GENUINELY_EMPTY, not CUT_SHORT: matches SpiderSolver.search's own convention, where hitting
    // MAX_DEPTH returns the same -1 a genuine dead end does, so a caller keeps trying its other
    // siblings instead of giving up on the whole subtree. Getting this wrong here once already cost
    // a real run almost all of its exploration: CUT_SHORT propagates immediately (by design, for the
    // deadline fix below), so treating every 599-deep dive as a budget failure meant one long
    // productive-looking line anywhere in a task's subtree aborted that entire task in a fraction of
    // a second, well before any real breadth was covered.
    if (depth >= 599) return GENUINELY_EMPTY
    if ((n and 0xFFFL) == 0L && (found.get() || n > maxNodes || System.nanoTime() > deadline)) return CUT_SHORT

    if (!cache.add(hashOf(board, state.hashScratch))) return GENUINELY_EMPTY

    val local = state.moves[depth]
    local.clear()
    generateMoves(board, local)
    val undo = state.undos[depth]
    for (i in 0 until local.size) {
        val move = FastMove(local[i])
        applyFast(board, move, undo)
        val result = parallelSearchNode(board, depth + 1, cache, state, nodes, found, deadline, maxNodes, hashOf, record)
        undoFast(board, undo)
        if (result >= 0) {
            record.add(local[i])
            return result
        }
        // Propagated immediately, not just recorded to check after the loop: the S6 incident log
        // (`EXECUTION_PLAN.md`) already caught this exact mistake once in the single-threaded
        // searches — a `for` loop that only *notices* a child was cut short instead of stopping
        // right there still tries every remaining sibling, each diving in fresh and not itself
        // re-checking the clock until its own node count happens to land on the next sampled
        // check. That turned a small budget into a search still running two minutes later there;
        // here, reintroducing the same shape independently in new code, it was thirteen *minutes*
        // across eight threads before a thread dump caught it — worse, since every thread pays the
        // same unbounded-unwind cost at once. `ParallelSearchDeadlineTest` pins this directly.
        if (result == CUT_SHORT || found.get()) return CUT_SHORT
    }
    return GENUINELY_EMPTY
}

/** What one phase of the parallel engine did — enough for [parallelSolve] to decide whether to run the next phase, and enough for a caller to build a [CampaignRecord]-shaped report. */
internal class ParallelPhaseResult(
    val solved: Boolean,
    val path: IntArray?,
    val nodes: Long,
    /** True only when a sound key was used, no shard ever saturated, and every task in the frontier ran to genuine completion — a real proof, not a budget expiring quietly. */
    val exhausted: Boolean,
)

/**
 * Runs one phase of the parallel engine: builds a frontier of at least `threads * 20` tasks (enough
 * that a work-stealing queue averages out the tree's real imbalance across many small pieces rather
 * than a few huge ones — the shape the spike validated), then [threads] workers pull tasks from a
 * shared queue until one wins, the queue drains, or the shared budget runs out.
 */
private fun runParallelPhase(
    root: FastBoard,
    cache: ConcurrentTranspositionCache,
    threads: Int,
    maxMillis: Long,
    maxNodes: Long,
    hashOf: (FastBoard, HashScratch) -> Long,
): ParallelPhaseResult {
    val nodes = AtomicLong()
    val found = AtomicBoolean(false)
    val ranOutOfBudget = AtomicBoolean(false)
    val winningPath = AtomicReference<IntArray?>(null)
    val deadline = System.nanoTime() + maxMillis * 1_000_000L
    val queue = ConcurrentLinkedQueue(buildParallelFrontier(root, threads * 20, maxDepth = 4))

    val workers = (0 until threads).map {
        Thread {
            val state = ParallelWorkerState()
            while (true) {
                if (found.get()) break
                if (nodes.get() > maxNodes || System.nanoTime() > deadline) { ranOutOfBudget.set(true); break }
                val task = queue.poll() ?: break
                val record = IntArrayList(256)
                val result = parallelSearchNode(task.board, task.pathFromRoot.size, cache, state, nodes, found, deadline, maxNodes, hashOf, record)
                when {
                    result >= 0 -> {
                        found.set(true)
                        record.reverse()
                        val full = task.pathFromRoot.copyOf(task.pathFromRoot.size + record.size)
                        for (i in 0 until record.size) full[task.pathFromRoot.size + i] = record[i]
                        winningPath.set(full)
                    }
                    result == CUT_SHORT -> ranOutOfBudget.set(true)
                }
            }
        }
    }
    workers.forEach { it.start() }
    workers.forEach { it.join() }

    val path = winningPath.get()
    val exhausted = path == null && !ranOutOfBudget.get() && !cache.saturated.get() && queue.isEmpty()
    return ParallelPhaseResult(solved = path != null, path = path, nodes = nodes.get(), exhausted = exhausted)
}

/**
 * Sizes a [ConcurrentTranspositionCache] to [shareOfTen] tenths of [totalCacheBytes], split into as
 * few shards as fit under one array's own 2^30-entry ceiling (at most sixteen). [shareOfTen] mirrors
 * [SolverLimits.maxCacheEntries]'s own 9:1 exact-vs-streamlined split, for the same reason: the
 * streamlined phase runs for a tenth of the time budget, so a tenth of the memory is what it can
 * plausibly fill. Rounding (shard count is a power of two, and per-shard size is too) means the
 * actual total is never more than requested, only ever a little less.
 */
private fun shardedCacheOf(totalCacheBytes: Long, shareOfTen: Int): ConcurrentTranspositionCache {
    val entries = (totalCacheBytes / 8) * shareOfTen / 10
    var shardCountPow2 = 0
    while (shardCountPow2 < 4 && (entries shr shardCountPow2) > (1L shl 30)) shardCountPow2++
    val perShard = (entries shr shardCountPow2).coerceIn(1024L, 1L shl 30)
    val perShardPow2 = (63 - java.lang.Long.numberOfLeadingZeros(perShard)).coerceAtLeast(10)
    return ConcurrentTranspositionCache(shardCountPow2, perShardPow2)
}

/**
 * A many-threads-on-one-deal counterpart to [campaignSolve]: the same streamlined-then-exact two
 * phases (`certifyStreamlined`/`solve`'s own shape), run with [threads] workers sharing one
 * [ConcurrentTranspositionCache] per phase instead of one thread owning its own [LongHashSet].
 * [totalCacheBytes] is split between them by [shardedCacheOf].
 */
internal fun parallelSolve(state: SpiderState, limits: SolverLimits, threads: Int, totalCacheBytes: Long): CampaignRecord {
    val started = System.nanoTime()
    val root = FastBoard.from(state)

    val streamlinedBudgetMs = (limits.maxMillis / 10).coerceAtLeast(1)
    val streamlinedCache = shardedCacheOf(totalCacheBytes, 1)
    val streamlined = runParallelPhase(root, streamlinedCache, threads, streamlinedBudgetMs, limits.maxNodes, ::streamlinedHashOf)
    if (streamlined.solved) {
        return CampaignRecord(
            seed = state.seed,
            suitCount = state.suitCount,
            solved = true,
            solvedBy = "streamlined-parallel",
            wallMs = (System.nanoTime() - started) / 1_000_000,
            nodes = streamlined.nodes,
            peakCacheSize = 0,
            solutionLength = streamlined.path!!.size,
            endedOn = "solved",
        )
    }

    val exactCache = shardedCacheOf(totalCacheBytes, 9)
    val exactStarted = System.nanoTime()
    val exact = runParallelPhase(root, exactCache, threads, limits.maxMillis, limits.maxNodes, ::canonicalHashOf)
    val exactElapsedMs = (System.nanoTime() - exactStarted) / 1_000_000
    val endedOn = when {
        exact.solved -> "solved"
        exact.exhausted -> "exhausted"
        exactElapsedMs >= limits.maxMillis -> "time"
        else -> "nodes"
    }
    return CampaignRecord(
        seed = state.seed,
        suitCount = state.suitCount,
        solved = exact.solved,
        solvedBy = if (exact.solved) "exact-parallel" else "none",
        wallMs = (System.nanoTime() - started) / 1_000_000,
        nodes = streamlined.nodes + exact.nodes,
        peakCacheSize = 0,
        solutionLength = if (exact.solved) exact.path!!.size else null,
        endedOn = endedOn,
    )
}

/** Converts a solved [ParallelPhaseResult]'s packed path to real, replayable moves. */
internal fun ParallelPhaseResult.toRulesMoves(): List<org.finiteplay.spider.rules.Move>? =
    path?.map { toRulesMove(FastMove(it)) }

/**
 * [parallelSolve]'s counterpart to [SpiderSolver.certify]: the same two phases, but handing back
 * the actual winning line as real, replayable moves instead of a [CampaignRecord] summary. Not
 * trusted blindly by any caller here either — the line is only as good as an independent replay
 * through the real reducer confirms it to be, exactly as [SpiderSolver.certify]'s own doc requires.
 */
internal fun parallelCertify(state: SpiderState, limits: SolverLimits, threads: Int, totalCacheBytes: Long): List<org.finiteplay.spider.rules.Move>? {
    val root = FastBoard.from(state)
    val streamlinedBudgetMs = (limits.maxMillis / 10).coerceAtLeast(1)
    val streamlined = runParallelPhase(root, shardedCacheOf(totalCacheBytes, 1), threads, streamlinedBudgetMs, limits.maxNodes, ::streamlinedHashOf)
    if (streamlined.solved) return streamlined.toRulesMoves()

    val exact = runParallelPhase(root, shardedCacheOf(totalCacheBytes, 9), threads, limits.maxMillis, limits.maxNodes, ::canonicalHashOf)
    return if (exact.solved) exact.toRulesMoves() else null
}
