package org.finiteplay.spider.session

import org.finiteplay.core.session.Session
import org.finiteplay.core.session.commit
import org.finiteplay.core.session.undo
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove

/**
 * Spider's transaction layer: the board, the undo stack, and the log, via `:core:session`'s
 * `Session<S, E>`.
 *
 * There is barely anything here beyond the shared shape itself, because Spider has none of
 * Klondike's extras layered on top of it: no automatic cascade to run after a commit, no
 * automatic finish to offer, no hint cursor before a solver exists
 * (`docs/games/spider/RULES.md` "What Spider does not have"). Where `GameSession` wraps
 * `Session` and adds four things, this wraps it and adds none — which is itself the finding
 * `docs/ARCHITECTURE.md` records: the two games share the bookkeeping and nothing above it.
 */
data class SpiderSession(val core: Session<SpiderState, SpiderLogEntry>) {
    val state: SpiderState get() = core.state
    val undoStack: List<SpiderState> get() = core.undoStack
    val log: List<SpiderLogEntry> get() = core.log

    val canUndo: Boolean get() = core.canUndo

    companion object {
        fun start(seed: Long, versions: GameVersions, suitCount: SuitCount = SuitCount.FOUR): SpiderSession =
            SpiderSession(Session.start(dealGame(seed, versions, suitCount)))
    }
}

/**
 * Commits a player-initiated [move]. `applyMove` itself requires the move be legal, so this
 * neither re-checks nor duplicates that message — one place decides what a legal move is.
 */
fun SpiderSession.commitMove(move: Move): SpiderSession =
    copy(core = core.commit(applyMove(state, move), SpiderLogEntry.PlayerMove(move)))

/**
 * Restores the board to its pre-transaction state without erasing the moves already
 * counted, then adds one (`docs/PLATFORM.md` "Persistence"). A no-op when
 * [SpiderSession.canUndo] is false.
 */
fun SpiderSession.undo(): SpiderSession {
    if (!canUndo) return this
    return copy(core = core.undo(SpiderLogEntry.Undo) { prior -> prior.copy(moveCount = state.moveCount + 1) })
}

/** Replays [log] from a fresh deal, reproducing an identical session deterministically. */
fun replaySpiderSession(
    seed: Long,
    versions: GameVersions,
    log: List<SpiderLogEntry>,
    suitCount: SuitCount = SuitCount.FOUR,
): SpiderSession {
    var session = SpiderSession.start(seed, versions, suitCount)
    for (entry in log) {
        session = when (entry) {
            is SpiderLogEntry.PlayerMove -> session.commitMove(entry.move)
            is SpiderLogEntry.Undo -> session.undo()
        }
    }
    return session
}
