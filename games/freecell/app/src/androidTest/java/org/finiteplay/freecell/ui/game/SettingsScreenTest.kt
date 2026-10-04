package org.finiteplay.freecell.ui.game

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Drives FreeCell's settings through the shared `core:ui` scaffolding and its own
 * `FreeCellViewModel`, with no store — settings then apply only in memory, which is enough to
 * prove each row is bound to its own setting and applies immediately
 * (`docs/games/freecell/EXECUTION_PLAN.md` F3b's gate); persistence across a restart is proven at
 * the store level (`FreeCellSettingsStoreTest`).
 */
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel
    private lateinit var closeLabel: String

    private fun openSettings() {
        viewModel = FreeCellViewModel(initialSeed = 1L)
        composeRule.setContent {
            closeLabel = stringResource(org.finiteplay.core.ui.R.string.action_close)
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
    fun theAutomaticMovesSwitchAppliesToTheSessionInPlay() {
        openSettings()
        composeRule.onNodeWithTag("setting_automatic_moves").assertIsOn()

        composeRule.onNodeWithTag("setting_automatic_moves").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setting_automatic_moves").assertIsOff()
        assertEquals(false, viewModel.settings.automaticMovesEnabled)
        assertEquals(false, viewModel.session.automaticMovesEnabled)
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

        composeRule.onNodeWithTag("setting_sound").performClick()
        composeRule.waitForIdle()
        assertEquals(true, viewModel.settings.soundEnabled)
        assertEquals(false, viewModel.settings.animationsEnabled)
    }

    @Test
    fun closingReturnsToTheBoard() {
        openSettings()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        closeSettings()

        composeRule.onNodeWithTag("settings_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("game_title").assertIsDisplayed()
    }

    private fun closeSettings() {
        composeRule.onNodeWithContentDescription(closeLabel).performClick()
        composeRule.waitForIdle()
    }
}
