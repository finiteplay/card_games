package org.finiteplay.spider.ui.game

import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.session.SpiderSession
import org.finiteplay.spider.solver.HintOutcome

/**
 * Sees every change to the game and every hint shown, for analysing a real game afterwards — a
 * player's undos included, since they are entries in the log like any move. Only a debug build
 * supplies one (`src/debug`'s `debugMoveRecorder`); release gets null, so nothing is recorded there.
 * Mirrors FreeCell's own `MoveRecorder`.
 */
interface MoveRecorder {
    /**
     * [session] is the game [gameId] now stands at: any log entries not yet recorded for it were
     * just committed (or, on a fresh or restored game, are recorded now). [shownHint] is the
     * guidance on screen when the newest entry was committed.
     */
    fun onCommitted(gameId: String, session: SpiderSession, shownHint: Move?)

    fun onHint(gameId: String, session: SpiderSession, outcome: HintOutcome, elapsedMs: Long)
}
