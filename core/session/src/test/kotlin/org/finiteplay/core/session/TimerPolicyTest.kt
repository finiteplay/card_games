package org.finiteplay.core.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerPolicyTest {

    @Test
    fun `runs once started, foreground, no modal, not won`() {
        assertTrue(shouldRunTimer(hasPlayerActed = true, isWon = false, isAppForeground = true, isModalOpen = false))
    }

    @Test
    fun `does not run before the player has acted`() {
        assertFalse(shouldRunTimer(hasPlayerActed = false, isWon = false, isAppForeground = true, isModalOpen = false))
    }

    @Test
    fun `does not run once the game is won`() {
        assertFalse(shouldRunTimer(hasPlayerActed = true, isWon = true, isAppForeground = true, isModalOpen = false))
    }

    @Test
    fun `does not run while backgrounded`() {
        assertFalse(shouldRunTimer(hasPlayerActed = true, isWon = false, isAppForeground = false, isModalOpen = false))
    }

    @Test
    fun `does not run while a modal is open`() {
        assertFalse(shouldRunTimer(hasPlayerActed = true, isWon = false, isAppForeground = true, isModalOpen = true))
    }
}
