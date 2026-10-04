package org.finiteplay.spider.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.spider.layout.SuitCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Restart re-deals the game in progress from its own seed.
 *
 * The case worth protecting is the last one here: a restart has to reproduce the board that was
 * being played, which is *not* the same as dealing at whatever suit count the settings now say.
 */
class RestartTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: SpiderViewModel
    private lateinit var cancelLabel: String

    private fun setContent() {
        viewModel = SpiderViewModel(initialSeed = SEED)
        composeRule.setContent {
            cancelLabel = androidx.compose.ui.res.stringResource(org.finiteplay.core.ui.R.string.action_cancel)
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
    }

    @Test
    fun restartingAnUntouchedGameNeedsNoConfirmation() {
        setContent()
        val dealt = viewModel.session.state.tableau

        composeRule.onNodeWithTag("action_replay").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pending_action_dialog").assertDoesNotExist()
        assertEquals(dealt, viewModel.session.state.tableau)
        assertEquals(0, viewModel.session.state.moveCount)
    }

    @Test
    fun restartingAPlayedGameAsksFirstAndCanBeCancelled() {
        setContent()
        val dealt = viewModel.session.state.tableau
        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()
        val afterDeal = viewModel.session.state.tableau
        assertNotEquals(dealt, afterDeal)

        composeRule.onNodeWithTag("action_replay").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pending_action_dialog").assertIsDisplayed()

        // Cancelling leaves the game exactly as it was — the whole point of asking.
        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pending_action_dialog").assertDoesNotExist()
        assertEquals(afterDeal, viewModel.session.state.tableau)
    }

    @Test
    fun confirmingRestartReturnsTheSameDealToItsOpeningPosition() {
        setContent()
        val dealt = viewModel.session.state.tableau
        val seed = viewModel.session.state.seed

        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()
        assertNotEquals(dealt, viewModel.session.state.tableau)

        viewModel.restart()
        composeRule.waitForIdle()

        assertEquals("restart must reproduce the opening board", dealt, viewModel.session.state.tableau)
        assertEquals(seed, viewModel.session.state.seed)
        assertEquals(0, viewModel.session.state.moveCount)
        assertEquals(0, viewModel.elapsedSeconds)
        assertTrue("nothing to undo at the start of a deal", !viewModel.session.canUndo)
    }

    @Test
    fun restartKeepsTheDealtSuitCountEvenAfterTheSettingChanges() {
        setContent()
        val dealtWith = viewModel.session.state.suitCount
        val dealt = viewModel.session.state.tableau

        // Change the setting that governs the *next* new game, then restart. Restart must ignore
        // it: taking it here would silently hand back a different game under the name "restart".
        val other = SuitCount.entries.first { it != dealtWith }
        viewModel.pickNextSuitCount(other)
        composeRule.waitForIdle()
        assertEquals(other, viewModel.nextSuitCount)

        viewModel.restart()
        composeRule.waitForIdle()

        assertEquals(dealtWith, viewModel.session.state.suitCount)
        assertEquals(dealt, viewModel.session.state.tableau)
    }

    private companion object {
        const val SEED = 1L
    }
}
