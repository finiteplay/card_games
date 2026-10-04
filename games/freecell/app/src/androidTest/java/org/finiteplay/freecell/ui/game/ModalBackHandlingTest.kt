package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.freecell.rules.Move
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Back closes whichever screen is open rather than exiting the app, and the timer pauses for as
 * long as one is (`docs/games/freecell/EXECUTION_PLAN.md` F3b's gate). One representative screen
 * proves the wiring; each screen's own content is `SettingsScreenTest`/`StatisticsScreenTest`'s
 * and `HelpScreen`'s to cover.
 */
class ModalBackHandlingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backClosesSettingsRatherThanExitingTheApp() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("action_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("game_title").assertIsDisplayed()
    }

    @Test
    fun backClosesStatisticsAndHelpTooInsteadOfLeavingThemOpenBehindTheBoard() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }

        composeRule.onNodeWithTag("statistics_button").performClick()
        composeRule.waitForIdle()
        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("statistics_screen").assertDoesNotExist()

        composeRule.onNodeWithTag("help_button").performClick()
        composeRule.waitForIdle()
        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("help_screen").assertDoesNotExist()
    }

    @Test
    fun theTimerDoesNotAdvanceWhileSettingsIsOpen() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        composeRule.setContent {
            FinitePlayTheme { GameScreen(viewModel = viewModel) }
        }
        // The timer only ever runs once the player has acted (`shouldRunTimer`); a fresh, untouched
        // deal would read as paused either way and prove nothing about Settings' own effect on it.
        composeRule.runOnUiThread {
            val column = viewModel.session.state.tableau.indexOfFirst { it.isNotEmpty() }
            viewModel.dragMove(Move.TableauToFreeCell(column, 0))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("action_settings").performClick()
        composeRule.waitForIdle()
        val elapsedWhenOpened = viewModel.elapsedSeconds

        // A real wait: the ticker is a genuine one-second coroutine delay, not the compose test
        // clock's virtual frame time, so only actual elapsed wall time can show it stayed paused.
        Thread.sleep(1_300)
        composeRule.waitForIdle()

        assertEquals(
            "the timer must not advance while a modal screen is open",
            elapsedWhenOpened,
            viewModel.elapsedSeconds,
        )
    }
}
