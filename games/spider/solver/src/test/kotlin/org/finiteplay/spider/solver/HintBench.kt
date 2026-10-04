package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import org.finiteplay.spider.solution.CompactSolutionCodec
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Offline experiment, not a test: how the hint search fares on the first TWO-suit catalog deals,
 * from the opening and from positions part-way along each deal's shipped solution.
 *
 * Positions: `line:f1,f2` stands on the shipped solution at those fractions of its length;
 * `perturb:f1,f2` does the same and then makes six plausible moves off it ([humanWalk]), which is
 * what a player who deviated from the hint looks like; `walk:n1,n2` plays n moves of [humanWalk]
 * from the deal. `-Donly=1,5` restricts the deals, `-Dwalks=n` the walks per point.
 *
 * Args: repoRoot algorithm millis [deals] [positions]
 */
object HintBench {
    private val versions = GameVersions(1, 1, 1)

    private val suitCount = SuitCount.valueOf((System.getProperty("suit") ?: "two").uppercase())

    fun twoSuitSeeds(repo: File, count: Int): List<Long> = catalogSeeds(repo, count, SuitCount.TWO)

    fun catalogSeeds(repo: File, count: Int, suits: SuitCount): List<Long> {
        val bytes = File(repo, "games/spider/app/src/main/assets/catalogs/${suits.name.lowercase()}.catalog").readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val records = (bytes.size - 64) / 8
        return (0 until minOf(count, records)).map { buffer.getLong(64 + it * 8) }
    }

    fun shippedLine(repo: File, seed: Long, deal: SpiderState): List<Move>? {
        val bytes = File(repo, "games/spider/app/src/main/assets/solutions.bin").readBytes()
        val index = CompactSolutionCodec.readIndex(bytes)
        return CompactSolutionCodec.decodeLine(bytes, index, deal, seed)
    }

    fun replayWins(start: SpiderState, line: List<Move>): Boolean {
        var s = start
        for (m in line) {
            if (!isLegal(s, m)) return false
            s = applyMove(s, m)
        }
        return s.sequencesBanked == 8
    }

    /** Each takes the time budget and returns a solver of one board. */
    val algorithms: Map<String, (Long) -> ((SpiderState) -> List<Move>?)> = mapOf(
        // The hint search as it stood before PhaseSearch: SpiderSolver alone.
        "current" to { millis ->
            val solver = SpiderSolver(HINT_SOLVER_LIMITS.copy(maxMillis = millis));
            { s: SpiderState -> solver.certify(s) }
        },
        "phase" to { millis ->
            val search = PhaseSearch();
            { s: SpiderState -> search.certify(s, millis) }
        },
        // What HintEngine runs: PhaseSearch, then SpiderSolver on what is left of the budget.
        "hint" to { millis ->
            val search = PhaseSearch()
            val solver = SpiderSolver(HINT_SOLVER_LIMITS.copy(maxMillis = millis));
            { s: SpiderState ->
                val started = System.nanoTime()
                search.certify(s, millis * 3 / 4) ?: run {
                    val left = millis - (System.nanoTime() - started) / 1_000_000
                    solver.certifyWithOutcome(s, left.coerceAtLeast(100)).line
                }
            }
        },
    )

    /**
     * A plausible, imperfect player: banks, reveals, in-suit builds and column-emptying moves are
     * taken at random among themselves; otherwise it deals, or makes a random rearrangement. Never
     * revisits a position. Returns the position after [steps] moves (null if it got stuck first).
     */
    fun humanWalk(deal: SpiderState, rngSeed: Long, steps: Int, allowDeal: Boolean = true): SpiderState? {
        val rng = java.util.Random(rngSeed)
        val board = FastBoard.from(deal)
        val undo = UndoRecord()
        val moves = IntArrayList(64)
        val seen = HashSet<Long>()
        val scratch = HashScratch()
        seen.add(canonicalHashOf(board, scratch))
        var state = deal
        var idle = 0
        repeat(steps) {
            generateMoves(board, moves)
            val good = ArrayList<Int>()
            val other = ArrayList<Int>()
            var canDeal = false
            for (i in 0 until moves.size) {
                val m = FastMove(moves[i])
                if (m.isDeal) { canDeal = allowDeal; continue }
                applyFast(board, m, undo)
                val fresh = canonicalHashOf(board, scratch) !in seen
                undoFast(board, undo)
                if (!fresh) continue
                val card = board.cards[m.from][m.fromIndex]
                val reveals = m.fromIndex > 0 && m.fromIndex == board.faceDown[m.from]
                val empties = m.fromIndex == 0 && board.len[m.to] > 0
                val suited = board.len[m.to] > 0 && board.suitOf(board.topOf(m.to)) == board.suitOf(card)
                val breaksSuited = m.fromIndex > board.faceDown[m.from] &&
                    board.suitOf(board.cards[m.from][m.fromIndex - 1]) == board.suitOf(card) &&
                    board.rankOf(board.cards[m.from][m.fromIndex - 1]) == board.rankOf(card) + 1
                if ((reveals || empties || suited) && !breaksSuited) good += moves[i] else other += moves[i]
            }
            val pick = when {
                good.isNotEmpty() -> good[rng.nextInt(good.size)]
                canDeal && (other.isEmpty() || idle >= 3 || rng.nextInt(3) == 0) -> FastMove.DEAL_ROW.packed
                other.isNotEmpty() -> other[rng.nextInt(other.size)]
                else -> return null
            }
            idle = if (good.isEmpty() && pick != FastMove.DEAL_ROW.packed) idle + 1 else 0
            applyFast(board, FastMove(pick), undo)
            seen.add(canonicalHashOf(board, scratch))
            state = applyMove(state, toRulesMove(FastMove(pick)))
            if (board.isWon) return null
        }
        return state
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val repo = File(args[0])
        val algorithm = args[1]
        val millis = args[2].toLong()
        val dealCount = args.getOrNull(3)?.toInt() ?: 20
        val spec = args.getOrNull(4) ?: "line:0"
        val kind = spec.substringBefore(':')
        val points = spec.substringAfter(':').split(',').map { it.toDouble() }
        val walks = System.getProperty("walks")?.toInt() ?: 2
        val factory = algorithms[algorithm] ?: error("unknown algorithm $algorithm")
        val solve = factory(millis)

        var solved = 0
        var total = 0
        var totalMs = 0L
        val only = System.getProperty("only")?.split(",")?.map { it.toInt() }?.toSet()
        for ((i, seed) in catalogSeeds(repo, dealCount, suitCount).withIndex()) {
            if (only != null && (i + 1) !in only) continue
            val deal = dealGame(seed, versions, suitCount)
            val line = shippedLine(repo, seed, deal) ?: error("no shipped line for $seed")
            val starts = ArrayList<Pair<String, SpiderState>>()
            for (point in points) {
                if (kind == "line") {
                    val cut = (line.size * point).toInt()
                    var state = deal
                    for (m in line.take(cut)) state = applyMove(state, m)
                    starts += "line ${"%.2f".format(point)} (move $cut/${line.size})" to state
                } else if (kind == "perturb") {
                    val cut = (line.size * point).toInt()
                    var onLine = deal
                    for (m in line.take(cut)) onLine = applyMove(onLine, m)
                    for (w in 0 until walks) {
                        val state = humanWalk(onLine, seed * 131 + cut * 7 + w, 6, allowDeal = false) ?: continue
                        starts += "perturb$w ${"%.2f".format(point)} (move $cut, dealt ${(50 - state.stock.size) / 10}, banked ${state.sequencesBanked})" to state
                    }
                } else {
                    for (w in 0 until walks) {
                        val state = humanWalk(deal, seed * 31 + w, point.toInt()) ?: continue
                        starts += "walk$w ${point.toInt()} (dealt ${(50 - state.stock.size) / 10}, banked ${state.sequencesBanked})" to state
                    }
                }
            }
            for ((label, state) in starts) {
                val started = System.nanoTime()
                val found = solve(state)
                val ms = (System.nanoTime() - started) / 1_000_000
                val ok = found != null && replayWins(state, found)
                total++
                totalMs += ms
                if (ok) solved++
                println(
                    "deal ${i + 1} seed $seed $label: " +
                        (if (ok) "SOLVED len ${found!!.size}" else if (found != null) "BAD LINE" else "fail") + " ${ms}ms",
                )
            }
        }
        println("== $algorithm ${millis}ms: $solved/$total solved, avg ${totalMs / total.coerceAtLeast(1)}ms")
    }
}
