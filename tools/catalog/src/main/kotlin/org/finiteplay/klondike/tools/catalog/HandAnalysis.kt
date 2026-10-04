package org.finiteplay.klondike.tools.catalog

import java.math.BigInteger
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.solver.search.LongIntHashMap

/**
 * Measures one deal's game tree directly, over the **full legal choice set** rather than any
 * tier's restricted subset.
 *
 * Everything else in this package grades deals by playing a ruleset and asking whether it
 * wins. That answers "is this deal hard for *these rules*". This answers a different and more
 * basic question — how big is the thing a player is actually navigating — and it does so
 * without any ruleset, so nothing it reports depends on a priority order being right.
 *
 * Two counts that are easy to conflate and differ by orders of magnitude:
 *
 * - **Choices**: legal moves that change the state. Draw and recycle are excluded, because
 *   they change nothing and decide nothing; that is also why every pile card is offered, not
 *   just the waste top. This is the branching a player really faces.
 * - **Paths**: distinct sequences of choices. These grow as the product of the branching
 *   factors, so they are counted rather than enumerated — a distinction the numbers below make
 *   unarguable.
 *
 * States collapse where paths do not: two different orders of the same independent moves reach
 * one board. That collapse is the only reason any of this is computable, and the gap between
 * the path count and the state count at each depth measures exactly how much it buys.
 */
data class DepthCensus(
    val depth: Int,
    val states: Int,
    val paths: BigInteger,
    val wins: Int,
    val deadEnds: Int,
    val meanChoices: Double,
    val maxChoices: Int,
)

/**
 * Level-synchronous sweep from the deal, one depth of **choices** at a time.
 *
 * States are deduplicated within a depth but not across depths: reaching the same board in
 * four choices and in six is two different path lengths, and the question is about paths of a
 * given length. [maxStates] caps the frontier, and the sweep stops rather than reporting a
 * truncated level as if it were complete — a partial census is worse than a short one.
 */
fun censusByDepth(
    seed: Long,
    maxDepth: Int = 200,
    maxFrontier: Int = 400_000,
    includeWithdrawal: Boolean = true,
    /** Skip an independent move ordered before the one just played. Preserves reachable states. */
    partialOrder: Boolean = false,
    /** Emit one empty destination column rather than one per empty column. Preserves states. */
    canonicaliseEmpty: Boolean = false,
    /** Force a safe foundation move when one exists. **Removes** states, deliberately. */
    forceSafe: Boolean = false,
    /** Disable expensive path multiplicities when only state/edge performance is being measured. */
    countPaths: Boolean = true,
    log: (String) -> Unit = ::println,
): List<DepthCensus> {
    val board = FastBoard()
    board.loadFrom(dealGame(seed, D1S_SPIKE_VERSIONS))

    // Keyed by fingerprint, not by the raw snapshot: the fingerprint is column-order
    // invariant, so two boards differing only in which column stands empty are one state
    // everywhere else in this package and must be one state here too.
    var frontier = listOf(board.snapshot())
    var ways = if (countPaths) listOf(BigInteger.ONE) else null
    var lastMove = intArrayOf(-1)
    var lastMask = intArrayOf(0)

    val census = ArrayList<DepthCensus>()
    val choices = IntArray(1024)
    var edges = 0L

    log("seed $seed — full legal choice set, withdrawal ${if (includeWithdrawal) "on" else "off"}")
    log("pruning: partial-order=$partialOrder empty-column=$canonicaliseEmpty safe-forcing=$forceSafe")
    log("depth | states | paths | edges | redundancy | wins | dead | mean | max")

    for (depth in 0..maxDepth) {
        var totalChoices = 0L
        var maxChoices = 0
        var wins = 0
        var deadEnds = 0
        var levelEdges = 0L
        val expectedNextStates = minOf(maxFrontier.toLong(), maxOf(16L, frontier.size.toLong() * 2)).toInt()
        val index = LongIntHashMap(expectedNextStates)
        val nextStates = ArrayList<ByteArray>()
        val nextWays = if (countPaths) ArrayList<BigInteger>() else null
        val nextMove = ArrayList<Int>()
        val nextMask = ArrayList<Int>()

        for (slot in frontier.indices) {
            board.restore(frontier[slot])
            if (board.isWon()) {
                wins++
                continue
            }
            val count = board.generateChoices(
                choices,
                includeWithdrawal,
                afterMove = if (partialOrder) lastMove[slot] else -1,
                afterMask = lastMask[slot],
                canonicaliseEmptyColumns = canonicaliseEmpty,
                forceSafeFoundation = forceSafe,
            )
            if (count == 0) {
                deadEnds++
                continue
            }
            totalChoices += count
            levelEdges += count
            if (count > maxChoices) maxChoices = count
            for (i in 0 until count) {
                val move = choices[i]
                val mask = board.moveMask(move)
                board.make(move)
                val key = board.fingerprint()
                val existing = index.getOrDefault(key, -1)
                if (existing < 0) {
                    index.put(key, nextStates.size)
                    val child = board.snapshot()
                    nextStates.add(child)
                    if (countPaths) nextWays!!.add(ways!![slot])
                    nextMove.add(move)
                    nextMask.add(mask)
                } else if (countPaths) {
                    nextWays!![existing] = nextWays[existing] + ways!![slot]
                    // Reached a second way: the reduction is only sound relative to a single
                    // preceding move, so drop the filter for this state rather than apply one
                    // arrival's restriction to another's continuations.
                    if (nextMove[existing] != move) nextMove[existing] = -1
                } else if (nextMove[existing] != move) {
                    nextMove[existing] = -1
                }
                board.unmake()
            }
        }

        val expanded = frontier.size - wins - deadEnds
        census.add(
            DepthCensus(
                depth = depth,
                states = frontier.size,
                paths = if (countPaths) ways!!.fold(BigInteger.ZERO, BigInteger::add) else BigInteger.ZERO,
                wins = wins,
                deadEnds = deadEnds,
                meanChoices = if (expanded > 0) totalChoices.toDouble() / expanded else 0.0,
                maxChoices = maxChoices,
            ),
        )
        edges += levelEdges
        val row = census.last()
        val redundancy = if (nextStates.isEmpty()) 0.0 else levelEdges.toDouble() / nextStates.size
        val renderedPaths = if (countPaths) magnitude(row.paths) else "disabled".padStart(13)
        log(
            "%5d | %8d | %s | %9d | %6.2fx | %4d | %4d | %5.1f | %3d".format(
                row.depth, row.states, renderedPaths, levelEdges, redundancy,
                row.wins, row.deadEnds, row.meanChoices, row.maxChoices,
            ),
        )

        if (nextStates.isEmpty()) {
            log("exhausted at depth $depth")
            break
        }
        if (nextStates.size > maxFrontier) {
            log("stopped at depth $depth: next frontier ${nextStates.size} states, over the $maxFrontier cap")
            break
        }
        frontier = nextStates
        ways = nextWays
        lastMove = nextMove.toIntArray()
        lastMask = nextMask.toIntArray()
    }
    log("total edges expanded: $edges")
    return census
}

/** Renders a path count as a magnitude, since these pass Long within a dozen choices. */
private fun magnitude(value: BigInteger): String {
    if (value < BigInteger.valueOf(1_000_000L)) return value.toString().padStart(13)
    val digits = value.toString()
    return "%.3fe%d".format(digits.take(4).toDouble() / 1000, digits.length - 1).padStart(13)
}

/**
 * Counts the choices available at every state along one ruleset's reference line, which is the
 * cheapest honest picture of what a player walks through: how wide the board is at each step,
 * rather than how wide the whole tree is.
 */
fun reportLineWidth(seed: Long, ruleset: Ruleset = Ruleset.HARD, maxSteps: Int = 400, log: (String) -> Unit = ::println) {
    val board = FastBoard()
    board.loadFrom(dealGame(seed, D1S_SPIKE_VERSIONS))
    val taps = IntArray(1024)
    val choices = IntArray(1024)
    val onPath = HashSet<Long>()
    val widths = ArrayList<Int>()
    var steps = 0

    while (!board.isWon() && steps++ < maxSteps) {
        onPath.add(board.fingerprint())
        widths.add(board.generateChoices(choices, includeWithdrawal = true))
        val count = board.generateTaps(taps, ruleset)
        var reference = -1
        for (index in 0 until count) {
            board.make(taps[index])
            val loops = board.fingerprint() in onPath
            board.unmake()
            if (!loops && (reference < 0 || board.tapRank(taps[index], ruleset) < board.tapRank(taps[reference], ruleset))) {
                reference = index
            }
        }
        if (reference < 0) break
        board.make(taps[reference])
    }

    val sorted = widths.sorted()
    log("seed $seed along $ruleset's line: ${widths.size} choice points, won=${board.isWon()}")
    log("choices per state — min ${sorted.first()}, median ${sorted[sorted.size / 2]}, max ${sorted.last()}, mean %.1f".format(widths.average()))
    var product = BigInteger.ONE
    for (width in widths) product = product.multiply(BigInteger.valueOf(width.toLong()))
    log("product of the branching along this one line alone: ${magnitude(product)}")
}
