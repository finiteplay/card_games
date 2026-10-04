package org.finiteplay.freecell.session

import org.finiteplay.freecell.rules.Move

/**
 * One entry in the append-only committed-transaction log (`docs/PLATFORM.md` "Persistence").
 *
 * Three cases, against Klondike's four and Spider's two: FreeCell has an automatic-moves setting
 * to toggle and an automatic finish to record, like Klondike, but no draw mode and nothing else
 * to add a further case for (`docs/games/freecell/RULES.md` "What FreeCell does not have").
 *
 * Encoding this to bytes for the persisted save is F3a's job, not this file's — `RULES.md`
 * defines the log's shape, F3a defines what it looks like on disk.
 */
sealed class FreeCellLogEntry {
    data class PlayerMove(val move: Move) : FreeCellLogEntry()
    data object Undo : FreeCellLogEntry()
    data class SetAutomaticMoves(val enabled: Boolean) : FreeCellLogEntry()
    data object AutoFinish : FreeCellLogEntry()
}
