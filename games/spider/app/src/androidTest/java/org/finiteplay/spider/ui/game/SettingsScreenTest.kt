package org.finiteplay.spider.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.spider.layout.SuitCount
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Drives Spider's settings through the shared `core:ui` scaffolding. Worth its own test rather
 * than trusting Klondike's: the rows are shared, but which settings exist and what each one does
 * to this game is not, and a wrong binding here (a switch wired to the neighbouring setting) is
 * invisible to any test Klondike runs.
 */
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: SpiderViewModel
    private lateinit var closeLabel: String

    private fun openSettings() {
        viewModel = SpiderViewModel(initialSeed = 1L)
        composeRule.setContent {
            closeLabel = androidx.compose.ui.res.stringResource(org.finiteplay.core.ui.R.string.action_close)
            FinitePlayTheme {
                GameScreen(viewModel = viewModel)
            }
        }
        composeRule.onNodeWithTag("action_settings").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun settingsOpenFromTheActionBar() {
        openSettings()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun handednessSwitchMirrorsTheActionBarWithoutChangingWhatEachActionDoes() {
        openSettings()
        composeRule.onNodeWithTag("setting_left_handed").assertIsOff()

        composeRule.onNodeWithTag("setting_left_handed").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setting_left_handed").assertIsOn()
        assertEquals(Handedness.LEFT, viewModel.settings.handedness)

        closeSettings()

        // The bar reorders but does not rebind: Undo now sits left of New, and is still Undo.
        val undo = composeRule.onNodeWithTag("action_undo").fetchSemanticsNode()
        val new = composeRule.onNodeWithTag("action_new").fetchSemanticsNode()
        assertTrue(
            "left-handed layout must put Undo before New, got undo=${undo.boundsInRoot.left} new=${new.boundsInRoot.left}",
            undo.boundsInRoot.left < new.boundsInRoot.left,
        )
    }

    @Test
    fun eachSwitchBindsToItsOwnSetting() {
        openSettings()

        composeRule.onNodeWithTag("setting_enable_animations").performClick()
        composeRule.waitForIdle()
        assertEquals(false, viewModel.settings.animationsEnabled)
        assertEquals(false, viewModel.settings.soundEnabled)
        assertEquals(true, viewModel.settings.hintShowsWinningMove)

        composeRule.onNodeWithTag("setting_sound").performClick()
        composeRule.waitForIdle()
        assertEquals(true, viewModel.settings.soundEnabled)
        assertEquals(false, viewModel.settings.animationsEnabled)

        composeRule.onNodeWithTag("setting_hint_shows_winning_move").assertIsOn()
        composeRule.onNodeWithTag("setting_hint_shows_winning_move").performClick()
        composeRule.waitForIdle()
        assertEquals(false, viewModel.settings.hintShowsWinningMove)
        assertEquals(true, viewModel.settings.soundEnabled)
    }

    @Test
    fun theSuitCountAppliesToTheNextGameAndNotTheCurrentOne() {
        openSettings()
        val dealtWith = viewModel.session.state.suitCount

        composeRule.onNodeWithTag("setting_suit_count").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting_suit_count_four").performClick()
        composeRule.waitForIdle()

        assertEquals(SuitCount.FOUR, viewModel.nextSuitCount)
        // The board in play was dealt at its own count and must not have changed under the player.
        assertEquals(dealtWith, viewModel.session.state.suitCount)
    }

    @Test
    fun closingReturnsToTheBoard() {
        openSettings()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        closeSettings()

        composeRule.onNodeWithTag("settings_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("stock").assertIsDisplayed()
    }

    @Test
    fun theActionBarIsRightHandedByDefault() {
        openSettings()
        closeSettings()

        val undo = composeRule.onNodeWithTag("action_undo").fetchSemanticsNode()
        val new = composeRule.onNodeWithTag("action_new").fetchSemanticsNode()
        assertTrue(
            "right-handed layout must put Undo nearest the holding hand, after New",
            undo.boundsInRoot.left > new.boundsInRoot.left,
        )
    }

    private fun closeSettings() {
        composeRule.onNodeWithContentDescription(closeLabel).performClick()
        composeRule.waitForIdle()
    }
}
