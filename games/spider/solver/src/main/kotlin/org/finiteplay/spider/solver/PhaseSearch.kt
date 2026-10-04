package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import org.finiteplay.spider.rules.Move

/**
 * The hint's first search, built around the way a player with unlimited undo actually wins a
 * two-suit deal (`docs/games/spider/DESIGN.md` "Hint"): the five row deals split the game into six
 * phases, and the real decision in each is *which organised position to deal from* — a player
 * deals, looks at the ten cards, undoes, and rearranges so the row lands well. A solver already
 * knows the row, so it can do the same thing without the undo.
 *
 * Each level holds up to `width` boards just after a deal. Each is explored without dealing by a
 * bounded best-first search, the best few deal-ready positions it passed through are dealt and
 * scored by the board the row leaves behind, and the best `width` of those go on to the next
 * level. The last phase, with nothing left to deal, searches for the win itself. A failed attempt
 * is retried wider and deeper until the time budget runs out.
 *
 * Like the beam stage of [SpiderSolver], it can find a win but never prove there is none: almost
 * everything it is allowed to reach is discarded unexamined. A line it returns is still only
 * trusted after the caller replays it through the real reducer, as [HintEngine] does.
 */
class PhaseSearch {
    private val undo = UndoRecord()

    /** A board just after a deal, and the moves that reached it. */
    private class Candidate(val board: FastBoard, val path: IntArray, val score: Int)

    /** A winning line from [state], or null when none was found within [maxMillis]. */
    fun certify(state: SpiderState, maxMillis: Long): List<Move>? {
        val deadline = System.nanoTime() + maxMillis * 1_000_000L
        val root = FastBoard.from(state)
        if (root.isWon) return null
        // Per search, not per instance: its arrays grow with the hardest phase it meets, and a
        // HintEngine lives as long as the game screen — that memory should go when the hint does.
        val explorer = PhaseExplorer()
        explorer.deadline = deadline
        var width = START_WIDTH
        var budget = START_BUDGET
        var finalBudget = START_FINAL_BUDGET
        while (System.nanoTime() < deadline) {
            explorer.truncated = false
            val line = attempt(explorer, root, width, budget, finalBudget, deadline)
            if (line != null) return line.map { toRulesMove(FastMove(it)) }
            // Nothing was cut short, so a wider attempt would see exactly the same boards.
            if (!explorer.truncated) return null
            width = minOf(width * 2, MAX_WIDTH)
            budget = minOf(budget * 2, MAX_BUDGET)
            finalBudget = minOf(finalBudget * 2, MAX_FINAL_BUDGET)
        }
        return null
    }

    private fun attempt(
        explorer: PhaseExplorer,
        root: FastBoard,
        width: Int,
        budget: Int,
        finalBudget: Int,
        deadline: Long,
    ): IntArray? {
        var level = listOf(Candidate(root.copy(), IntArray(0), 0))
        while (level.isNotEmpty()) {
            val next = ArrayList<Candidate>()
            val seen = LongHashSet(1 shl 8)
            for (candidate in level) {
                if (System.nanoTime() > deadline) return null
                val lastPhase = candidate.board.stockPos >= candidate.board.stock.size
                val found = explorer.explore(candidate.board, if (lastPhase) finalBudget else budget)
                if (found.isWin) return candidate.path + found.paths[0]
                for (i in found.paths.indices) {
                    val board = candidate.board.copy()
                    for (move in found.paths[i]) applyFast(board, FastMove(move), undo)
                    applyFast(board, FastMove.DEAL_ROW, undo)
                    val path = candidate.path + found.paths[i] + FastMove.DEAL_ROW.packed
                    if (board.isWon) return path
                    if (seen.add(dealKeyOf(board))) next += Candidate(board, path, found.scores[i])
                }
            }
            next.sortByDescending { it.score }
            if (next.size > width) explorer.truncated = true
            level = next.take(width)
        }
        return null
    }

    private companion object {
        /**
         * Boards kept per level on the first attempt; each retry doubles it, and the budgets.
         * Starting at one costs nothing in boards solved and is several times faster on the easy
         * ones, which at one suit is nearly all of them.
         */
        const val START_WIDTH = 1
        /** Expansions per exploration of a phase that still has a row to deal. */
        const val START_BUDGET = 1_000
        /** Expansions for the last phase, which must find the whole remaining win on its own. */
        const val START_FINAL_BUDGET = 10_000
        const val MAX_WIDTH = 64
        const val MAX_BUDGET = 64_000
        const val MAX_FINAL_BUDGET = 320_000
    }
}

/**
 * How good a position looks: suits banked, cards still face down (and a little extra for a column
 * nearly open), empty columns, and how the face-up cards sit — an in-suit build is what the game is
 * won with, an off-suit one is a small help, and any other pair is a break that has to be taken
 * apart later. The weights were tuned on two-suit deals the benchmark did not otherwise measure.
 */
internal fun phaseEvaluate(board: FastBoard): Int {
    var score = board.banked * BANKED_WEIGHT
    for (c in 0 until TABLEAU_COLUMNS) {
        val n = board.len[c]
        if (n == 0) {
            score += EMPTY_WEIGHT
            continue
        }
        val down = board.faceDown[c]
        score -= down * FACE_DOWN_WEIGHT
        if (down < NEARLY_OPEN) score += (NEARLY_OPEN - down) * NEARLY_OPEN_WEIGHT
        val col = board.cards[c]
        for (i in down + 1 until n) {
            val above = col[i - 1]
            val below = col[i]
            score += when {
                board.rankOf(above) != board.rankOf(below) + 1 -> -BREAK_WEIGHT
                board.suitOf(above) == board.suitOf(below) -> SUITED_WEIGHT
                else -> OFF_SUIT_WEIGHT
            }
        }
    }
    return score
}

private const val BANKED_WEIGHT = 5_000
private const val FACE_DOWN_WEIGHT = 30
private const val NEARLY_OPEN = 4
private const val NEARLY_OPEN_WEIGHT = 8
private const val EMPTY_WEIGHT = 60
private const val SUITED_WEIGHT = 20
private const val OFF_SUIT_WEIGHT = 2
private const val BREAK_WEIGHT = 5

/**
 * Charged per move from the phase's start, so of two equally good boards the one reached directly
 * is preferred — without it the search spends its budget on long walks through rearrangements that
 * all score the same.
 */
private const val DEPTH_CHARGE = 5

/** Deal-ready positions a phase hands on to the next level. */
private const val KEEP_PER_PHASE = 6

/**
 * Ceiling on boards generated in one exploration, whatever its expansion budget: each costs about
 * forty bytes across the node arrays, the open list and the transposition set, so this holds one
 * exploration to roughly forty megabytes on a phone.
 */
private const val MAX_GENERATED = 1_000_000

/** What one [PhaseExplorer.explore] call found. */
internal class PhaseFindings(val isWin: Boolean, val paths: List<IntArray>, val scores: IntArray)

/**
 * Bounded best-first search within one phase: never deals, keeps every generated board as a parent
 * index and a move, and rebuilds a board from its parent's snapshot when it is expanded.
 */
internal class PhaseExplorer {
    var deadline = Long.MAX_VALUE

    /** Set when an exploration stopped on its budget rather than running out of boards. */
    var truncated = false

    private var parent = IntArray(1 shl 14)
    private var move = IntArray(1 shl 14)
    private var depthOf = IntArray(1 shl 14)
    private var snapshotAt = IntArray(1 shl 14)
    private var size = 0
    private var heap = LongArray(1 shl 14)
    private var heapSize = 0
    private val pool = SnapshotPool()
    private val undo = UndoRecord()
    private val moves = IntArrayList(64)
    private val path = IntArrayList(256)
    private val board = FastBoard()

    private val keptNodes = IntArray(KEEP_PER_PHASE)
    private val keptScores = IntArray(KEEP_PER_PHASE)
    private val keptFills = arrayOfNulls<IntArray>(KEEP_PER_PHASE)
    private var keptCount = 0

    private val fillUndos = Array(TABLEAU_COLUMNS) { UndoRecord() }
    private val fillMoves = IntArrayList(64)
    private val fillChosen = IntArray(TABLEAU_COLUMNS)

    /**
     * Explores from [root] for up to [budget] expansions. A win ends it at once; otherwise, when
     * [root] still has stock, it returns the best deal-ready positions it passed through, each
     * scored by the board the row deal would leave.
     */
    fun explore(root: FastBoard, budget: Int): PhaseFindings {
        size = 0
        heapSize = 0
        keptCount = 0
        pool.clear()
        val seen = LongHashSet(1 shl 14)
        val stockLeft = root.stockPos < root.stock.size
        seen.add(dealKeyOf(root))
        push(phaseEvaluate(root), newNode(-1, 0, 0))
        var expansions = 0
        while (heapSize > 0) {
            if (expansions >= budget || size >= MAX_GENERATED) {
                truncated = true
                break
            }
            if ((expansions and 0xFF) == 0 && System.nanoTime() > deadline) {
                truncated = true
                break
            }
            val node = pop()
            expansions++
            if (node == 0) {
                board.copyFrom(root)
            } else {
                pool.restore(snapshotAt[parent[node]], board)
                applyFast(board, FastMove(move[node]), undo)
            }
            snapshotAt[node] = pool.save(board)

            if (stockLeft) {
                if (board.canDealRow()) {
                    keep(scoreAfterDeal(board), node, null)
                } else {
                    // An empty column opens a plateau of rearrangements that all outscore filling
                    // it, so best-first order may never reach a deal-ready board at all. A player
                    // just fills the hole and deals; so does this, greedily, without searching it.
                    val fills = fillToDeal(board)
                    if (fills != null) keep(fillScore, node, fills)
                }
            }

            val depth = depthOf[node]
            generateMoves(board, moves)
            for (i in 0 until moves.size) {
                val packed = moves[i]
                if (packed == FastMove.DEAL_ROW.packed) continue
                applyFast(board, FastMove(packed), undo)
                if (board.isWon) {
                    undoFast(board, undo)
                    return PhaseFindings(true, listOf(pathTo(node) + packed), IntArray(0))
                }
                if (seen.add(dealKeyOf(board))) {
                    push(phaseEvaluate(board) - DEPTH_CHARGE * (depth + 1), newNode(node, packed, depth + 1))
                }
                undoFast(board, undo)
            }
        }
        val paths = (0 until keptCount).map { k ->
            val toNode = pathTo(keptNodes[k])
            keptFills[k]?.let { toNode + it } ?: toNode
        }
        return PhaseFindings(false, paths, keptScores.copyOf(keptCount))
    }

    private fun scoreAfterDeal(board: FastBoard): Int {
        applyFast(board, FastMove.DEAL_ROW, undo)
        val score = phaseEvaluate(board)
        undoFast(board, undo)
        return score
    }

    private fun keep(score: Int, node: Int, fills: IntArray?) {
        var pos = keptCount
        while (pos > 0 && keptScores[pos - 1] < score) pos--
        if (pos >= KEEP_PER_PHASE) return
        for (j in minOf(keptCount, KEEP_PER_PHASE - 1) downTo pos + 1) {
            keptScores[j] = keptScores[j - 1]
            keptNodes[j] = keptNodes[j - 1]
            keptFills[j] = keptFills[j - 1]
        }
        keptScores[pos] = score
        keptNodes[pos] = node
        keptFills[pos] = fills
        if (keptCount < KEEP_PER_PHASE) keptCount++
    }

    private var fillScore = 0

    /**
     * Fills [board]'s empty columns one greedy move at a time (whichever move into an empty column
     * evaluates best), and when that leaves it deal-ready, scores it into [fillScore] and returns
     * the fills. [board] is left exactly as it was.
     */
    private fun fillToDeal(board: FastBoard): IntArray? {
        var applied = 0
        while (applied < TABLEAU_COLUMNS) {
            var hasEmpty = false
            for (c in 0 until TABLEAU_COLUMNS) if (board.len[c] == 0) hasEmpty = true
            if (!hasEmpty) break
            generateMoves(board, fillMoves)
            var best = -1
            var bestScore = Int.MIN_VALUE
            for (i in 0 until fillMoves.size) {
                val candidate = FastMove(fillMoves[i])
                if (candidate.isDeal || board.len[candidate.to] != 0) continue
                applyFast(board, candidate, undo)
                val score = phaseEvaluate(board)
                undoFast(board, undo)
                if (score > bestScore) {
                    bestScore = score
                    best = fillMoves[i]
                }
            }
            if (best == -1) break
            applyFast(board, FastMove(best), fillUndos[applied])
            fillChosen[applied++] = best
        }
        var result: IntArray? = null
        if (applied > 0 && board.canDealRow()) {
            fillScore = scoreAfterDeal(board)
            result = fillChosen.copyOf(applied)
        }
        for (k in applied - 1 downTo 0) undoFast(board, fillUndos[k])
        return result
    }

    private fun pathTo(node: Int): IntArray {
        path.clear()
        var n = node
        while (n > 0) {
            path.add(move[n])
            n = parent[n]
        }
        path.reverse()
        return IntArray(path.size) { path[it] }
    }

    private fun newNode(parentNode: Int, packed: Int, depth: Int): Int {
        if (size == parent.size) {
            val grown = size * 2
            parent = parent.copyOf(grown)
            move = move.copyOf(grown)
            depthOf = depthOf.copyOf(grown)
            snapshotAt = snapshotAt.copyOf(grown)
        }
        parent[size] = parentNode
        move[size] = packed
        depthOf[size] = depth
        return size++
    }

    /** Highest priority pops first; among equals, the newest node, so the search dives. */
    private fun push(priority: Int, node: Int) {
        if (heapSize == heap.size) heap = heap.copyOf(heapSize * 2)
        val key = (-priority.toLong() shl 32) or (Int.MAX_VALUE - node).toLong()
        var i = heapSize++
        heap[i] = key
        while (i > 0) {
            val up = (i - 1) / 2
            if (heap[up] <= heap[i]) break
            val t = heap[up]; heap[up] = heap[i]; heap[i] = t
            i = up
        }
    }

    private fun pop(): Int {
        val top = heap[0]
        heap[0] = heap[--heapSize]
        var i = 0
        while (true) {
            val left = 2 * i + 1
            if (left >= heapSize) break
            val right = left + 1
            val child = if (right < heapSize && heap[right] < heap[left]) right else left
            if (heap[i] <= heap[child]) break
            val t = heap[child]; heap[child] = heap[i]; heap[i] = t
            i = child
        }
        return Int.MAX_VALUE - (top and 0xFFFFFFFFL).toInt()
    }
}

/** Board snapshots packed into one growable byte array; the stock is shared, never copied. */
internal class SnapshotPool {
    private var bytes = ByteArray(1 shl 16)
    private var used = 0

    fun clear() {
        used = 0
    }

    fun save(board: FastBoard): Int {
        var need = 2
        for (c in 0 until TABLEAU_COLUMNS) need += 2 + board.len[c]
        if (used + need > bytes.size) bytes = bytes.copyOf(maxOf(bytes.size * 2, used + need))
        val start = used
        var o = used
        bytes[o++] = board.stockPos.toByte()
        bytes[o++] = board.banked.toByte()
        for (c in 0 until TABLEAU_COLUMNS) {
            val n = board.len[c]
            bytes[o++] = n.toByte()
            bytes[o++] = board.faceDown[c].toByte()
            System.arraycopy(board.cards[c], 0, bytes, o, n)
            o += n
        }
        used = o
        return start
    }

    /** Overwrites [board] with the snapshot at [offset]; [board] must already share its stock. */
    fun restore(offset: Int, board: FastBoard) {
        var o = offset
        board.stockPos = bytes[o++].toInt() and 0xFF
        board.banked = bytes[o++].toInt()
        for (c in 0 until TABLEAU_COLUMNS) {
            val n = bytes[o++].toInt() and 0xFF
            board.len[c] = n
            board.faceDown[c] = bytes[o++].toInt() and 0xFF
            System.arraycopy(bytes, o, board.cards[c], 0, n)
            o += n
        }
        board.markAllColumnsDirty()
    }
}
