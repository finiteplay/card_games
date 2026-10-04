package org.finiteplay.freecell.session

import org.finiteplay.core.session.Session
import org.finiteplay.core.session.commit
import org.finiteplay.core.session.finish
import org.finiteplay.core.session.record
import org.finiteplay.core.session.undo
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.dealGame
import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.rules.applyMove
import org.finiteplay.freecell.rules.canAutoFinish
import org.finiteplay.freecell.rules.isLegal
import org.finiteplay.freecell.rules.runAutomaticFoundationCascade
import org.finiteplay.freecell.rules.simulateSweep

/**
 * The transaction layer: FreeCell's board, undo, and log, plus the automatic cascade and the
 * automatic finish — the two things it has that Spider's own session does not
 * (`docs/games/spider/session/SpiderSession.kt`), on the same shape Klondike's `GameSession`
 * uses for the same two features.
 *
 * Automation never runs before the player's first action, even when [automaticMovesEnabled]
 * starts `true`: [start] deals the raw board untouched, and the cascade only ever runs inside
 * [commitMove], which requires an actual player move to call it
 * (`docs/games/freecell/RULES.md` "Automatic Foundation Moves").
 */
data class FreeCellSession(
    val core: Session<FreeCellState, FreeCellLogEntry>,
    val automaticMovesEnabled: Boolean,
) {
    val state: FreeCellState get() = core.state
    val undoStack: List<FreeCellState> get() = core.undoStack
    val log: List<FreeCellLogEntry> get() = core.log

    val hasPlayerActed: Boolean get() = log.any { it is FreeCellLogEntry.PlayerMove }

    /**
     * Narrower than [Session.canUndo]: a won game keeps its boards on the stack but does not
     * offer them, because the win is already recorded (`RULES.md` "Automatic Finish").
     */
    val canUndo: Boolean get() = core.canUndo && state.status != GameStatus.WON

    companion object {
        fun start(seed: Long, versions: GameVersions, automaticMovesEnabled: Boolean): FreeCellSession =
            FreeCellSession(Session.start(dealGame(seed, versions)), automaticMovesEnabled)

        /** Builds a session around an arbitrary board, for fixtures and tests. */
        fun of(
            state: FreeCellState,
            undoStack: List<FreeCellState> = emptyList(),
            automaticMovesEnabled: Boolean = true,
            log: List<FreeCellLogEntry> = emptyList(),
        ): FreeCellSession = FreeCellSession(Session(state, undoStack, log), automaticMovesEnabled)
    }
}

/** Commits a player-initiated [move], then runs the automatic cascade if enabled. */
fun FreeCellSession.commitMove(move: Move): FreeCellSession {
    require(isLegal(state, move)) { "illegal move: $move" }

    val afterMove = applyMove(state, move).copy(moveCount = state.moveCount + 1)
    // The cascade is part of this move's transaction, not a move of its own, so it lands in the
    // same commit and one undo steps back past all of it.
    val afterAutomation = if (automaticMovesEnabled) runAutomaticFoundationCascade(afterMove).first else afterMove

    return copy(core = core.commit(afterAutomation, FreeCellLogEntry.PlayerMove(move)))
}

/**
 * Restores the board to its pre-transaction state without erasing the moves already counted,
 * then adds one (`docs/games/freecell/RULES.md` "Game Lifecycle"). Never reruns automation. A
 * no-op when [FreeCellSession.canUndo] is false.
 */
fun FreeCellSession.undo(): FreeCellSession {
    if (!canUndo) return this
    return copy(core = core.undo(FreeCellLogEntry.Undo) { prior -> prior.copy(moveCount = state.moveCount + 1) })
}

fun FreeCellSession.withAutomaticMoves(enabled: Boolean): FreeCellSession = copy(
    core = core.record(FreeCellLogEntry.SetAutomaticMoves(enabled)),
    automaticMovesEnabled = enabled,
)

/** Commits the automatic finish. Requires [canAutoFinish]; clears the undo stack. */
fun FreeCellSession.commitAutoFinish(): FreeCellSession {
    val swept = simulateSweep(state) ?: throw IllegalStateException("Auto finish is not available")
    return commitAutoFinishWith(swept)
}

/**
 * Commits the automatic finish using an already-computed [swept] board, without recomputing
 * [simulateSweep] — for a caller (the app's own view model) that ran the search off the main
 * thread itself and would otherwise pay for it twice. [swept] must be the real result of
 * [simulateSweep] applied to [FreeCellSession.state]; nothing here re-derives or checks that.
 */
fun FreeCellSession.commitAutoFinishWith(swept: FreeCellState): FreeCellSession =
    copy(core = core.finish(swept, FreeCellLogEntry.AutoFinish))

/** Replays [log] from a fresh deal, reproducing an identical session deterministically. */
fun replayFreeCellSession(
    seed: Long,
    versions: GameVersions,
    initialAutomaticMovesEnabled: Boolean,
    log: List<FreeCellLogEntry>,
): FreeCellSession {
    var session = FreeCellSession.start(seed, versions, initialAutomaticMovesEnabled)
    for (entry in log) {
        session = when (entry) {
            is FreeCellLogEntry.PlayerMove -> session.commitMove(entry.move)
            is FreeCellLogEntry.Undo -> session.undo()
            is FreeCellLogEntry.SetAutomaticMoves -> session.withAutomaticMoves(entry.enabled)
            is FreeCellLogEntry.AutoFinish -> session.commitAutoFinish()
        }
    }
    return session
}
