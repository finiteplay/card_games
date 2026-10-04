package org.finiteplay.klondike.session

import org.finiteplay.core.session.Session
import org.finiteplay.core.session.commit
import org.finiteplay.core.session.finish
import org.finiteplay.core.session.record
import org.finiteplay.core.session.undo
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyPlayerTransition
import org.finiteplay.klondike.rules.canAutoFinish
import org.finiteplay.klondike.rules.isLegal
import org.finiteplay.klondike.rules.rankedHintCandidates
import org.finiteplay.klondike.rules.runAutomaticFoundationCascade
import org.finiteplay.klondike.rules.simulateSweep

/**
 * The transaction layer: Klondike's board, undo, and log, plus what only Klondike has.
 *
 * The board-and-log bookkeeping lives in [Session] (`:core:session`) — the undo stack, the
 * append-only log, and the rule that undo restores the board without erasing its counted moves.
 * What stays here is everything that is Klondike rather than solitaire: the automatic cascade,
 * the automatic finish, the hint cursor, and the move alphabet the log is written in.
 *
 * Automation never runs before the player's first action, even when [automaticMovesEnabled]
 * starts `true` — [start] deals the raw board untouched, and the first cascade (if any) happens
 * as part of the player's first committed move, the same as every move after it
 * (`docs/games/klondike/DESIGN.md` "Automatic Foundation Moves"). [hintCursor] indexes into the
 * current ranked hint candidates and resets on every state-changing operation.
 */
data class GameSession(
    val core: Session<GameState, LogEntry>,
    val automaticMovesEnabled: Boolean,
    val hintCursor: Int = 0,
    val lastPlayerMove: Move? = null,
) {
    val state: GameState get() = core.state
    val undoStack: List<GameState> get() = core.undoStack
    val log: List<LogEntry> get() = core.log

    val hasPlayerActed: Boolean get() = log.any { it is LogEntry.PlayerMove }

    /**
     * Narrower than [Session.canUndo]: a won game keeps its boards on the stack but does not
     * offer them, because the win is already recorded (`RULES.md` "Automatic Finish").
     */
    val canUndo: Boolean get() = core.canUndo && state.status != GameStatus.WON

    companion object {
        fun start(
            seed: Long,
            versions: GameVersions,
            automaticMovesEnabled: Boolean,
            drawMode: DrawMode = DrawMode.ONE,
        ): GameSession = GameSession(
            core = Session.start(dealGame(seed, versions, drawMode)),
            automaticMovesEnabled = automaticMovesEnabled,
        )

        /** Builds a session around an arbitrary board, for fixtures and debug entry points. */
        fun of(
            state: GameState,
            undoStack: List<GameState> = emptyList(),
            automaticMovesEnabled: Boolean = true,
            log: List<LogEntry> = emptyList(),
        ): GameSession = GameSession(Session(state, undoStack, log), automaticMovesEnabled)
    }
}

/** Commits a player-initiated [move], then runs the automatic cascade if enabled. */
fun GameSession.commitMove(move: Move): GameSession {
    require(isLegal(state, move)) { "Illegal move: $move" }

    val afterMove = applyPlayerTransition(state, move).let { it.copy(moveCount = it.moveCount + 1) }
    // The cascade is part of this move's transaction, not a move of its own, so it lands in the
    // same commit and one undo steps back past all of it.
    val afterAutomation = if (automaticMovesEnabled) runAutomaticFoundationCascade(afterMove).first else afterMove

    return copy(
        core = core.commit(afterAutomation, LogEntry.PlayerMove(move)),
        hintCursor = 0,
        lastPlayerMove = move,
    )
}

/**
 * Restores the board to its pre-transaction state without erasing the moves already
 * counted, then adds one (`docs/games/klondike/EXECUTION_PLAN.md`). Never reruns automation. A
 * no-op when [GameSession.canUndo] is false.
 */
fun GameSession.undo(): GameSession {
    if (!canUndo) return this
    return copy(
        core = core.undo(LogEntry.Undo) { prior -> prior.copy(moveCount = state.moveCount + 1) },
        hintCursor = 0,
        lastPlayerMove = null,
    )
}

fun GameSession.withAutomaticMoves(enabled: Boolean): GameSession = copy(
    core = core.record(LogEntry.SetAutomaticMoves(enabled)),
    automaticMovesEnabled = enabled,
)

/** Commits the automatic finish. Requires [canAutoFinish]; clears the undo stack. */
fun GameSession.commitAutoFinish(): GameSession {
    require(canAutoFinish(state)) { "Auto finish is not available" }
    val swept = simulateSweep(state) ?: throw IllegalStateException("Auto finish is not available")
    return copy(
        core = core.finish(swept, LogEntry.AutoFinish),
        hintCursor = 0,
        lastPlayerMove = null,
    )
}

/** The hint at the current cursor position, or `null` when no legal move exists. */
fun GameSession.currentHint(): Move? {
    val candidates = rankedHintCandidates(state)
    if (candidates.isEmpty()) return null
    return candidates[hintCursor % candidates.size]
}

/** Advances the hint cursor to the next ranked candidate, wrapping around. */
fun GameSession.nextHint(): GameSession {
    val candidateCount = rankedHintCandidates(state).size
    if (candidateCount == 0) return this
    return copy(hintCursor = (hintCursor + 1) % candidateCount)
}

/** Replays [log] from a fresh deal, reproducing an identical session deterministically. */
fun replaySession(
    seed: Long,
    versions: GameVersions,
    initialAutomaticMovesEnabled: Boolean,
    log: List<LogEntry>,
    drawMode: DrawMode = DrawMode.ONE,
): GameSession {
    var session = GameSession.start(seed, versions, initialAutomaticMovesEnabled, drawMode)
    for (entry in log) {
        session = when (entry) {
            is LogEntry.PlayerMove -> session.commitMove(entry.move)
            is LogEntry.Undo -> session.undo()
            is LogEntry.SetAutomaticMoves -> session.withAutomaticMoves(entry.enabled)
            is LogEntry.AutoFinish -> session.commitAutoFinish()
        }
    }
    return session
}
