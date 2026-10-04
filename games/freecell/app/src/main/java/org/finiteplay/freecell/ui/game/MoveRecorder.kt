package org.finiteplay.freecell.ui.game

import org.finiteplay.freecell.rules.Move
import org.finiteplay.freecell.session.FreeCellSession
import org.finiteplay.freecell.solver.HintOutcome

/**
 * Sees every change to the game and every hint shown, for analysing a real game afterwards. Only a debug build
 * supplies one (`src/debug`'s `debugMoveRecorder`); release gets null, so nothing is recorded there.
 */
interface MoveRecorder {
    /** [session]'s last log entry was just committed — or, with an empty log, [session] was just dealt. [shownHint] is the guidance on screen when it was. */
    fun onCommitted(gameId: String, session: FreeCellSession, shownHint: Move?)

    fun onHint(gameId: String, session: FreeCellSession, outcome: HintOutcome)
}
