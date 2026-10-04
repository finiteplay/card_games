package org.finiteplay.klondike.solver

import org.finiteplay.cards.Card
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.isLegal

/**
 * Cap on full passes. Each pass fixes everything of its kind it can find, so a line
 * settles in two or three; this only bounds pathological input, where one pass's change
 * keeps exposing another's opportunity.
 */
private const val MAX_PASSES = 6

private fun Move.rotatesStock() = this is Move.Draw || this is Move.Recycle

private fun Move.consumesWaste() = this is Move.WasteToTableau || this is Move.WasteToFoundation

/**
 * Turns a proven winning line into one worth *following*.
 *
 * A solver proves a win; it has no reason to care how the line reads to someone playing
 * one move at a time. Two things it emits that a player experiences as the hint being
 * wrong:
 *
 * - **A run shuffled out and back.** A run leaves column A for column B and returns
 *   several moves later with neither column used in between. The board is never repeated
 *   — the foundations moved on — so the exact-state strip in `HintEngine.cacheCertificate`
 *   cannot see it, and the player is told to undo work they were just told to do.
 * - **Stock rotation that buys nothing.** The line spins the stock and then goes back to
 *   moving tableau cards without ever playing the card it exposed.
 *
 * Every change is accepted only on proof that it is *neutral*: replaying the affected
 * span without the dropped moves has to land on the identical board the original line
 * stood on at the far end. That leaves the whole rest of the line untouched, so it still
 * wins for exactly the reason it did before, and it costs a replay of the span rather
 * than of the line — the difference between a few milliseconds and over a second on a
 * long line, and this runs after the search, outside its budget.
 *
 * Only ever deletes moves or defers a stock rotation later, so the result is never longer
 * than the input, and the whole thing is re-proved at the end: a defect anywhere here
 * costs presentation, never the guarantee. Runs once per solve, after
 * [reorderCertificateForFollowing] has put the line into the order the player sees.
 */
fun optimizeWinningLine(start: GameState, line: List<Move>): List<Move> {
    if (line.size < 2) return line
    var current = line
    var passes = 0
    while (passes++ < MAX_PASSES) {
        val next = tidyStockRotation(start, dropCancellingStockPairs(start, dropRunRoundTrips(start, current)))
        if (next == current) break
        current = next
    }
    return if (replaysToWin(start, current)) current else line
}

/** Replays [line] through the canonical reducer; true only if every step is legal and it wins. */
private fun replaysToWin(start: GameState, line: List<Move>): Boolean {
    var state = start
    for (move in line) {
        if (!isLegal(state, move)) return false
        state = applyMove(state, move)
    }
    return state.status == GameStatus.WON
}

private fun List<Move>.without(vararg indices: Int): List<Move> =
    filterIndexed { index, _ -> index !in indices }

/**
 * The board before each move, plus the final board — so `states[k]` is what move `k`
 * applies to, and `states[line.size]` is where the line ends.
 */
private fun statesAlong(start: GameState, line: List<Move>): List<GameState> {
    val states = ArrayList<GameState>(line.size + 1)
    var state = start
    for (move in line) {
        states += state
        state = applyMove(state, move)
    }
    states += state
    return states
}

/**
 * True when dropping moves [first] and [second] cancels out: replaying what lies between
 * them, without either, reaches exactly the board the line stood on after [second].
 *
 * Landing on the identical board is what makes the rest of the line irrelevant to the
 * decision — every later move applies to the same board it always did, so the line still
 * wins without re-checking a single one of them.
 */
private fun cancelsOut(states: List<GameState>, line: List<Move>, first: Int, second: Int): Boolean {
    var state = states[first]
    for (k in first + 1 until second) {
        val move = line[k]
        if (!isLegal(state, move)) return false
        state = applyMove(state, move)
    }
    return state == states[second + 1]
}

/** The cards [move] carries, as they sit on the board in [state]. */
private fun sequenceMovedBy(state: GameState, move: Move.TableauToTableau): List<Card> =
    state.tableau[move.fromColumn].drop(move.fromIndex).map { it.card }

private fun columnsTouchedBy(move: Move): List<Int> = when (move) {
    is Move.TableauToTableau -> listOf(move.fromColumn, move.toColumn)
    is Move.TableauToFoundation -> listOf(move.fromColumn)
    is Move.WasteToTableau -> listOf(move.toColumn)
    is Move.FoundationToTableau -> listOf(move.toColumn)
    else -> emptyList()
}

/**
 * How many of the first `k` moves touch each column, so "was either column used between
 * these two moves?" is a subtraction rather than a scan.
 *
 * That question is a necessary condition for a round trip to cancel — a run parked so
 * something could be stacked on it, or a column that changed while the run was away,
 * cannot be dropped — and it is far cheaper to ask than replaying the span. Asking it
 * first is what keeps this pass off the critical path on a long line.
 */
private class ColumnTouches(line: List<Move>, columns: Int) {
    private val counts = Array(columns) { IntArray(line.size + 1) }

    init {
        for (k in line.indices) {
            for (column in 0 until columns) counts[column][k + 1] = counts[column][k]
            for (column in columnsTouchedBy(line[k])) counts[column][k + 1]++
        }
    }

    /** True when no move in `[from, until)` touches [column]. */
    fun untouched(column: Int, from: Int, until: Int): Boolean =
        counts[column][until] == counts[column][from]
}

private data class RunTrip(val fromColumn: Int, val toColumn: Int, val run: List<Card>)

/**
 * Drops every pair of tableau moves that carries the same run out to a column and
 * straight back without it having been needed there.
 *
 * Candidates are identified by the cards actually moved, not by the column numbers alone:
 * a run that comes back with a card added to it is a different run and genuinely made the
 * trip for a reason. A round trip that stages a run so other runs can be stacked on it and
 * taken off again is real work too — [cancelsOut] is what tells the two apart, since only
 * the pointless one leaves the board where it found it.
 */
private fun dropRunRoundTrips(start: GameState, line: List<Move>): List<Move> {
    var current = line
    while (true) {
        val states = statesAlong(start, current)
        val touches = ColumnTouches(current, start.tableau.size)
        val trips = HashMap<RunTrip, MutableList<Int>>()
        for ((index, move) in current.withIndex()) {
            if (move !is Move.TableauToTableau) continue
            trips.getOrPut(RunTrip(move.fromColumn, move.toColumn, sequenceMovedBy(states[index], move))) { ArrayList() } += index
        }

        val dropped = current.indices.firstNotNullOfOrNull { index ->
            val move = current[index] as? Move.TableauToTableau ?: return@firstNotNullOfOrNull null
            val run = sequenceMovedBy(states[index], move)
            trips[RunTrip(move.toColumn, move.fromColumn, run)]
                ?.firstOrNull { returnLeg ->
                    returnLeg > index &&
                        touches.untouched(move.fromColumn, index + 1, returnLeg) &&
                        touches.untouched(move.toColumn, index + 1, returnLeg) &&
                        cancelsOut(states, current, index, returnLeg)
                }
                ?.let { current.without(index, it) }
        } ?: return current
        current = dropped
    }
}

/**
 * Drops a Recycle and a later Draw that between them put the stock back where it started.
 *
 * These cancel the way an out-and-back tableau run does, but the two halves are usually
 * separated by real moves, so the board is never repeated and the exact-state strip in
 * `HintEngine.cacheCertificate` — which already handles a rotation that returns the board
 * to itself with nothing in between — cannot see them. Only Recycle/Draw pairs are
 * considered; two Draws never cancel.
 */
private fun dropCancellingStockPairs(start: GameState, line: List<Move>): List<Move> {
    var current = line
    while (true) {
        val states = statesAlong(start, current)
        val recycles = current.indices.filter { current[it] is Move.Recycle }
        if (recycles.isEmpty()) return current
        val draws = current.indices.filter { current[it] is Move.Draw }

        // The waste has to be left exactly as found, so a play off it in between rules the
        // pair out before the span is ever replayed.
        val wastePlays = IntArray(current.size + 1)
        for (k in current.indices) wastePlays[k + 1] = wastePlays[k] + if (current[k].consumesWaste()) 1 else 0

        val dropped = recycles.firstNotNullOfOrNull { recycleAt ->
            draws.firstOrNull { drawAt ->
                val first = minOf(recycleAt, drawAt)
                val second = maxOf(recycleAt, drawAt)
                wastePlays[second] == wastePlays[first + 1] && cancelsOut(states, current, first, second)
            }?.let { current.without(recycleAt, it) }
        } ?: return current
        current = dropped
    }
}

/**
 * Defers each stretch of stock rotation that does not pay for itself, so spinning the
 * stock always ends on playing off it rather than on more tableau shuffling.
 *
 * A stretch not immediately followed by a move taking the exposed card has the moves after
 * it pulled in front, one at a time, for as long as each swap stays neutral. That is a
 * weaker requirement than the one [reorderCertificateForFollowing] uses — it needs each
 * *adjacent pair* to commute, where this only needs the span as a whole to land on the
 * same board — which is why some of these survive reordering and need this pass.
 */
private fun tidyStockRotation(start: GameState, line: List<Move>): List<Move> {
    var current = line
    var i = 0
    while (i < current.size) {
        if (!current[i].rotatesStock()) { i++; continue }
        var end = i
        while (end < current.size && current[end].rotatesStock()) end++

        if (current.getOrNull(end)?.consumesWaste() == true) { i = end; continue }
        val deferred = deferBlock(start, current, i, end)
        if (deferred == null) { i = end; continue }
        current = deferred
    }
    return current
}

/**
 * Pulls following moves in front of the block at `[from, to)` while each swap leaves the
 * board unchanged, stopping at the move that plays off the waste. Null when the block
 * cannot move at all.
 */
private fun deferBlock(start: GameState, line: List<Move>, from: Int, to: Int): List<Move>? {
    // Every accepted swap leaves the board after the span exactly as it was, so the boards
    // this array holds past the block stay correct as it advances and it is computed once.
    val states = statesAlong(start, line)
    var current = line
    var beforeBlock = states[from]
    var blockStart = from
    var blockEnd = to
    var moved = false
    while (blockEnd < current.size && !current[blockEnd].consumesWaste()) {
        val pulled = current[blockEnd]
        val candidate = ArrayList(current).apply { add(blockStart, removeAt(blockEnd)) }
        if (!spanLandsSame(beforeBlock, candidate, blockStart, blockEnd, states[blockEnd + 1])) break
        current = candidate
        beforeBlock = applyMove(beforeBlock, pulled)
        blockStart++
        blockEnd++
        moved = true
    }
    return current.takeIf { moved }
}

/** True when replaying `[from, through]` of [candidate] from [before] stays legal and lands on [expected]. */
private fun spanLandsSame(
    before: GameState,
    candidate: List<Move>,
    from: Int,
    through: Int,
    expected: GameState,
): Boolean {
    var state = before
    for (k in from..through) {
        val move = candidate[k]
        if (!isLegal(state, move)) return false
        state = applyMove(state, move)
    }
    return state == expected
}
