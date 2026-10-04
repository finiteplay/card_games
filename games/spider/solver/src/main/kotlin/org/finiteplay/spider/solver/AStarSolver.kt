package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.TABLEAU_COLUMNS
import java.util.PriorityQueue

/**
 * Weighted best-first (A*-shaped) search over Spider boards, mirroring the shape of Klondike's own
 * weighted A* hint search (`docs/games/klondike/DESIGN.md` "On-Device Hint Search") — expand the
 * board with the lowest `g` (moves so far) plus a weighted heuristic first, rather than Klondike's
 * admissible-lower-bound-plus-progress-signals split, since a useful admissible bound is much
 * harder to state for Spider (a single move can carry an entire run, so "cards not yet home" does
 * not translate into a move count the way Klondike's one-card-at-a-time foundation moves do).
 * Every weight instead comes directly from a principle in this project's own public strategy
 * research (`docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md`), turned into a number — see [Weights].
 *
 * Built after measuring that [SpiderSolver.certify]'s greedy-playout-then-plain-DFS pipeline
 * essentially never solves a two- or four-suit board from scratch within any interactive budget
 * tried, exhausting the node allowance in well under a second rather than timing out — a symptom of
 * blind DFS wandering the far larger two/four-suit state space with no sense of which branch is
 * actually promising. A priority order driven by the same strategy signals a person uses finds a
 * board genuinely close to a win far faster than either blind search — but does **not**, on its
 * own, make a two- or four-suit deal solvable from its raw fresh board within a phone-safe budget:
 * measured directly (`docs/games/spider/DESIGN.md` "Hint"), a sample of ten fresh deals each stayed
 * unsolved even many times over that budget. A person's own solution to such a deal, when there is
 * one, is typically over a hundred moves long, and heuristic error compounds over a line that long
 * regardless of how well-informed each individual step's ordering is — the actual reason `Hint`
 * still restricts its guided mode to one suit (`docs/games/spider/UI_SPEC.md` "Hint") rather than
 * this search closing that gap.
 *
 * Keeps a full board snapshot per open node rather than Klondike's column-sharing compact board:
 * simpler, and affordable at the node budget this search actually runs at (see
 * [MAX_CREATED_NODES]), where each snapshot trims its per-column arrays to their live length
 * instead of the fixed 104-card worst case [FastBoard] itself allocates, which is what keeps
 * memory proportional to the board in play rather than to the deck.
 */
class AStarSolver(private val weights: Weights = Weights()) {

    /**
     * Each term is one principle from `docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md`, turned into
     * an independently tunable weight — changing how eagerly the search chases one signal must not
     * silently rescale the others, the same reasoning behind Klondike's own independent weights.
     */
    data class Weights(
        /** "Uncover face-down cards ahead of almost anything else" — the single most repeated tip. */
        val faceDown: Int = 6,
        /**
         * "Prefer an in-suit build over an off-suit one" — a break is an adjacent face-up pair that
         * does not continue a suited descending run, i.e. exactly the pile that has to be taken
         * apart again later, one card at a time.
         */
        val breaks: Int = 4,
        /** "An empty column is the most valuable thing on the board" — a bonus, so it is subtracted. */
        val emptyColumn: Int = 14,
        /** Sequences still to bank: a coarse stand-in for "how much of the game is left". */
        val unbankedSequence: Int = 30,
        /** "Empty all beneficial moves before dealing a row" — a small drag on boards still holding a lot of stock, so the search does not treat an untouched stock as free progress. */
        val stockRow: Int = 2,
    )

    private class Node(
        val cards: Array<ByteArray>,
        val len: IntArray,
        val faceDown: IntArray,
        val stockPos: Int,
        val banked: Int,
        val parent: Int,
        val move: Int,
        val g: Int,
    )

    /**
     * Runs from [start], recording the winning line into [record] (in play order) when one is
     * found. [start] is never mutated. Shares [canonicalHashOf]'s exact key so a board this
     * search or the plain DFS has already ruled out is never re-explored as if it were new — the
     * two searches see the same board the same way.
     */
    fun solve(start: FastBoard, limits: SolverLimits, deadlineNanos: Long, record: IntArrayList?): SolveOutcome {
        record?.clear()
        var nodes = 0L
        val visited = LongHashSet()
        val scratch = HashScratch()
        val allNodes = ArrayList<Node>(1024)

        // Packs (priority, node index) into one Long so the queue needs no boxed comparator target:
        // priority in the high 32 bits (search priorities never approach Int.MAX_VALUE at these
        // budgets), node index in the low 32.
        fun packed(priority: Int, index: Int): Long = (priority.toLong() shl 32) or (index.toLong() and 0xFFFFFFFFL)
        fun priorityOf(entry: Long): Int = (entry ushr 32).toInt()
        fun indexOf(entry: Long): Int = (entry and 0xFFFFFFFFL).toInt()

        val open = PriorityQueue<Long>(1024, Comparator { a, b -> priorityOf(a) - priorityOf(b) })

        val work = FastBoard(stock = start.stock, stockPos = start.stockPos, banked = start.banked)
        for (c in 0 until TABLEAU_COLUMNS) {
            System.arraycopy(start.cards[c], 0, work.cards[c], 0, start.len[c])
            work.len[c] = start.len[c]
            work.faceDown[c] = start.faceDown[c]
        }
        work.markAllColumnsDirty()

        fun snapshotOf(board: FastBoard, parent: Int, move: Int, g: Int): Node = Node(
            cards = Array(TABLEAU_COLUMNS) { board.cards[it].copyOf(board.len[it]) },
            len = board.len.copyOf(),
            faceDown = board.faceDown.copyOf(),
            stockPos = board.stockPos,
            banked = board.banked,
            parent = parent,
            move = move,
            g = g,
        )

        fun loadIntoWork(node: Node) {
            for (c in 0 until TABLEAU_COLUMNS) {
                System.arraycopy(node.cards[c], 0, work.cards[c], 0, node.cards[c].size)
                work.len[c] = node.len[c]
                work.faceDown[c] = node.faceDown[c]
            }
            work.stockPos = node.stockPos
            work.banked = node.banked
            work.markAllColumnsDirty()
        }

        allNodes.add(snapshotOf(work, parent = -1, move = 0, g = 0))
        visited.add(canonicalHashOf(work, scratch))
        open.add(packed(heuristicOf(work), 0))

        val local = IntArrayList(64)
        val undo = UndoRecord()
        var winner = -1
        // Every open node holds a full trimmed-array board snapshot, unlike the plain DFS's O(depth)
        // make/unmake — so unlike that search, and unlike [SolverLimits.maxNodes] everywhere else in
        // this solver, the number that actually has to stay bounded here is *nodes ever created*
        // (open and expanded together), not nodes expanded: a wide frontier can hold many times the
        // expansion count in unexpanded children. Capped well under the caller's own budget, which
        // exists for the make/unmake searches this one does not share memory behavior with.
        val maxCreatedNodes = minOf(limits.maxNodes, MAX_CREATED_NODES.toLong())
        var outOfMemoryBudget = false

        while (open.isNotEmpty()) {
            if ((nodes and 0x1FF) == 0L && System.nanoTime() > deadlineNanos) break

            val entry = open.poll()
            val index = indexOf(entry)
            val node = allNodes[index]
            nodes++

            loadIntoWork(node)
            if (work.isWon) {
                winner = index
                break
            }

            generateMoves(work, local)
            for (i in 0 until local.size) {
                if (allNodes.size >= maxCreatedNodes) {
                    outOfMemoryBudget = true
                    break
                }
                val move = FastMove(local[i])
                applyFast(work, move, undo)
                val hash = canonicalHashOf(work, scratch)
                if (visited.add(hash)) {
                    val childG = node.g + 1
                    val childIndex = allNodes.size
                    allNodes.add(snapshotOf(work, parent = index, move = local[i], g = childG))
                    open.add(packed(childG + heuristicOf(work), childIndex))
                }
                undoFast(work, undo)
            }
            if (outOfMemoryBudget) break
        }

        if (winner >= 0) {
            var i = winner
            while (allNodes[i].parent >= 0) {
                record?.add(allNodes[i].move)
                i = allNodes[i].parent
            }
            record?.reverse()
            return SolveOutcome(SolveResult.SOLVED_BY_SEARCH, nodes, allNodes[winner].g)
        }
        val exhausted = open.isEmpty() && !outOfMemoryBudget
        return SolveOutcome(if (exhausted) SolveResult.EXHAUSTED else SolveResult.LIMIT, nodes, 0)
    }

    /**
     * Lower is closer to a win. Every term is a direct read of [FastBoard]'s own state — no lookahead,
     * so it is cheap enough to compute once per generated child rather than only once per expansion.
     */
    private fun heuristicOf(board: FastBoard): Int {
        var faceDownTotal = 0
        var breaksTotal = 0
        var emptyColumns = 0
        for (c in 0 until TABLEAU_COLUMNS) {
            faceDownTotal += board.faceDown[c]
            if (board.len[c] == 0) {
                emptyColumns++
                continue
            }
            val col = board.cards[c]
            for (i in board.faceDown[c] + 1 until board.len[c]) {
                val above = col[i - 1]
                val below = col[i]
                if (board.suitOf(above) != board.suitOf(below) || board.rankOf(above) != board.rankOf(below) + 1) {
                    breaksTotal++
                }
            }
        }
        val unbanked = FastBoard.SEQUENCES_TO_WIN - board.banked
        val rowsRemaining = (board.stock.size - board.stockPos) / TABLEAU_COLUMNS
        return weights.faceDown * faceDownTotal +
            weights.breaks * breaksTotal +
            weights.unbankedSequence * unbanked +
            weights.stockRow * rowsRemaining -
            weights.emptyColumn * emptyColumns
    }

    private companion object {
        /**
         * Ceiling on how many board snapshots this search will ever create (open and expanded
         * together) in one call, regardless of [SolverLimits.maxNodes]. Each snapshot is a handful
         * of small objects — ten trimmed `ByteArray`s, two `IntArray`s, and the `Node` wrapper —
         * measured at roughly 500–700 bytes apiece; at this cap the frontier costs on the order of
         * tens of megabytes, which is what actually has to fit, on a phone, alongside everything
         * else the app is holding. Set well below the interactive hint budget's own node count
         * ([HINT_SOLVER_LIMITS] in `HintEngine.kt`) on purpose: that number bounds work for the
         * plain DFS stage, whose O(depth) make/unmake board has no comparable memory cost, and is
         * not a safe number of *stored* boards for this search to target.
         *
         * Measured directly rather than guessed: raising this all the way to 800,000 — many times
         * a phone-safe budget — still did not solve a single fresh two- or four-suit deal in a
         * sample of ten each (`docs/games/spider/DESIGN.md` "Hint"), so a larger cap buys nothing
         * for that case and only adds memory risk. What this budget *does* reliably resolve, fast,
         * is a board genuinely close to a win — the realistic shape of most Hint requests, unlike a
         * fresh, untouched deal.
         */
        const val MAX_CREATED_NODES = 100_000
    }
}
