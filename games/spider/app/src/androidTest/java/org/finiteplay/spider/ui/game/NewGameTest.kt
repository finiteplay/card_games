package org.finiteplay.spider.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.spider.layout.SuitCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * New Game deals the next number in the sequence, at whatever suit count Settings currently
 * says — unlike Restart, which reproduces the exact board in play regardless of later setting
 * changes (`RestartTest`). The case worth protecting is that distinction.
 */
class NewGameTest {
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
    fun newGameOnAnUntouchedDealNeedsNoConfirmation() {
        setContent()

        composeRule.onNodeWithTag("action_new").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pending_action_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("stock").assertIsDisplayed()
    }

    @Test
    fun newGameOnAPlayedDealAsksFirstAndCanBeCancelled() {
        setContent()
        composeRule.onNodeWithTag("stock").performClick()
        composeRule.waitForIdle()
        val afterDeal = viewModel.session.state.tableau

        composeRule.onNodeWithTag("action_new").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pending_action_dialog").assertIsDisplayed()

        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pending_action_dialog").assertDoesNotExist()
        assertEquals(afterDeal, viewModel.session.state.tableau)
    }

    @Test
    fun confirmingDealsANewBoardAtTheCurrentlyConfiguredSuitCount() {
        setContent()
        val dealt = viewModel.session.state.tableau

        val other = SuitCount.entries.first { it != viewModel.session.state.suitCount }
        viewModel.pickNextSuitCount(other)
        composeRule.waitForIdle()

        viewModel.requestNewGame()
        composeRule.waitForIdle()

        assertNotEquals("a new game must deal a different board", dealt, viewModel.session.state.tableau)
        assertEquals(other, viewModel.session.state.suitCount)
        assertEquals(0, viewModel.session.state.moveCount)
        assertEquals(0, viewModel.elapsedSeconds)
    }

    private companion object {
        const val SEED = 1L
    }
}
