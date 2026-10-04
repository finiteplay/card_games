package org.finiteplay.klondike.storage

import org.finiteplay.klondike.board.GameStatus
import org.finiteplay.klondike.session.GameSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerPolicyTest {

    private fun freshDeal() = GameSession.start(seed = 1L, versions = TEST_VERSIONS, automaticMovesEnabled = true)
    private fun startedGame() = playRealisticSession(seed = 2L)
    // Reaches through `core` deliberately: replacing the board without logging it is not something
    // a session offers, and a fixture forcing a won status is the one place that is reasonable.
    private fun wonGame() = startedGame().let {
        it.copy(core = it.core.copy(state = it.state.copy(status = GameStatus.WON)))
    }

    @Test
    fun `runs once the game has started, foreground, no modal, not won`() {
        assertTrue(shouldRunTimer(startedGame(), isAppForeground = true, isModalOpen = false))
    }

    @Test
    fun `does not run before the first player action, setup automation notwithstanding`() {
        val fresh = freshDeal()
        assertFalse(fresh.hasPlayerActed)
        assertFalse(shouldRunTimer(fresh, isAppForeground = true, isModalOpen = false))
    }

    @Test
    fun `does not run while backgrounded`() {
        assertFalse(shouldRunTimer(startedGame(), isAppForeground = false, isModalOpen = false))
    }

    @Test
    fun `does not run while a modal is open`() {
        assertFalse(shouldRunTimer(startedGame(), isAppForeground = true, isModalOpen = true))
    }

    @Test
    fun `does not run once the game is won`() {
        assertFalse(shouldRunTimer(wonGame(), isAppForeground = true, isModalOpen = false))
    }

    @Test
    fun `a restored session needs no separate wait-for-action exception`() {
        // A session rebuilt by replay reports hasPlayerActed exactly as the live game
        // did, so the same predicate applies immediately after restoration.
        val restoredLikeSession = startedGame()
        assertTrue(restoredLikeSession.hasPlayerActed)
        assertTrue(shouldRunTimer(restoredLikeSession, isAppForeground = true, isModalOpen = false))
    }
}
