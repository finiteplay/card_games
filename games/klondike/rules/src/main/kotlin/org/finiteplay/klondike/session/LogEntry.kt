package org.finiteplay.klondike.session

import org.finiteplay.klondike.rules.Move

/**
 * One entry in the append-only committed-transaction log — the same primitive
 * encoding used for solution certificates and the active-game save format
 * (`docs/games/klondike/EXECUTION_PLAN.md`). Replaying a log through [replaySession] must reproduce
 * an identical board, move count, and undo stack.
 */
sealed class LogEntry {
    data class PlayerMove(val move: Move) : LogEntry()
    data object Undo : LogEntry()
    data class SetAutomaticMoves(val enabled: Boolean) : LogEntry()
    data object AutoFinish : LogEntry()
}
