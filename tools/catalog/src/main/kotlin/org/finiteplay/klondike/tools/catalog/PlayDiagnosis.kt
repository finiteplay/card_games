package org.finiteplay.klondike.tools.catalog

import java.util.Base64
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.session.LogEntry
import org.finiteplay.klondike.session.LogEntryCodec
import org.finiteplay.klondike.session.replaySession

/**
 * Classifies a played game against the Trivial guarantee, move by move.
 *
 * The guarantee is narrower than "this deal cannot be lost": it covers **obvious moves**
 * only, and tolerates a bounded number of departures from the obvious priority order. A
 * player who exceeds that budget, or who makes a move the model never considers at all,
 * has left the region the deal was graded over — the guarantee is not violated, it simply
 * no longer applies.
 *
 * Those two exits are very different and worth separating:
 *
 * - **Deviation** — an obvious move other than the one the priority order would take. Each
 *   costs one from the budget; past it, nothing is claimed.
 * - **Off-model** — a legal move that is not obvious at all, above all a non-revealing
 *   tableau rearrangement. No tier below Hard offers one, so the grading never explored
 *   those branches. A single one voids the guarantee outright rather than spending budget.
 */
enum class MoveClass { REFERENCE, DEVIATION, OFF_MODEL, PILE_CYCLING, UNDO, OTHER }

fun diagnosePlay(
    seed: Long,
    versions: GameVersions,
    drawMode: DrawMode,
    initialAutomaticMovesEnabled: Boolean,
    encodedLog: String,
    log: (String) -> Unit = ::println,
) {
    val entries = LogEntryCodec.decode(Base64.getDecoder().decode(encodedLog))
    log("classifying ${entries.size} log entries for seed=$seed against the Trivial guarantee")
    log("")

    var deviations = 0
    var offModel = 0
    var firstOffModel = -1
    var firstBudgetExceeded = -1
    val notes = ArrayList<String>()

    for (index in entries.indices) {
        val entry = entries[index]
        val move = (entry as? LogEntry.PlayerMove)?.move
        if (move == null) {
            if (entry is LogEntry.Undo) notes.add("${index + 1}. undo")
            continue
        }
        if (move is Move.Draw || move is Move.Recycle) continue

        val state = replaySession(seed, versions, initialAutomaticMovesEnabled, entries.take(index), drawMode).state
        val taps = obviousTapMovesInOrder(state)
        val reference = taps.firstOrNull()
        val classification = when {
            taps.isEmpty() -> MoveClass.OTHER
            move == reference -> MoveClass.REFERENCE
            taps.contains(move) -> MoveClass.DEVIATION
            else -> MoveClass.OFF_MODEL
        }
        when (classification) {
            MoveClass.DEVIATION -> {
                deviations++
                if (deviations == 5 && firstBudgetExceeded < 0) firstBudgetExceeded = index + 1
                notes.add("${index + 1}. $move — DEVIATION #$deviations (reference was $reference)")
            }
            MoveClass.OFF_MODEL -> {
                offModel++
                if (firstOffModel < 0) firstOffModel = index + 1
                notes.add("${index + 1}. $move — OFF-MODEL: not an obvious move at all")
            }
            else -> Unit
        }
    }

    notes.forEach(log)
    log("")
    log("deviations from the obvious order : $deviations")
    log("off-model moves                   : $offModel")
    log("")
    when {
        offModel > 0 -> log(
            "The guarantee stopped applying at move $firstOffModel, the first off-model move: " +
                "the graded region contains only obvious moves, so play left it entirely there.",
        )
        firstBudgetExceeded > 0 -> log(
            "The guarantee stopped applying at move $firstBudgetExceeded, the fifth deviation, " +
                "which is one past the four this deal was graded to tolerate.",
        )
        else -> log("Play stayed inside the guarantee throughout — a loss here would be a genuine grading defect.")
    }
}

/** The obvious taps at [state], reference first — the same order the grader uses. */
private fun obviousTapMovesInOrder(state: org.finiteplay.klondike.board.GameState): List<Move> {
    val board = FastBoard().apply { loadFrom(state) }
    val buffer = IntArray(64)
    val count = board.generateTaps(buffer)
    if (count == 0) return emptyList()
    val referenceIndex = board.referenceTapIndex(buffer, count)
    val moves = (0 until count).map { decodeTap(board, buffer[it]) }
    if (referenceIndex <= 0) return moves
    return listOf(moves[referenceIndex]) + moves.filterIndexed { i, _ -> i != referenceIndex }
}

private fun decodeTap(board: FastBoard, packed: Int): Move = when (FastBoard.kindOf(packed)) {
    FastBoard.KIND_TABLEAU_FOUNDATION -> Move.TableauToFoundation(FastBoard.fieldA(packed))
    FastBoard.KIND_TABLEAU_TABLEAU ->
        Move.TableauToTableau(FastBoard.fieldA(packed), FastBoard.fieldB(packed), FastBoard.fieldC(packed))
    FastBoard.KIND_WASTE_FOUNDATION -> Move.WasteToFoundation
    else -> Move.WasteToTableau(FastBoard.fieldC(packed))
}
