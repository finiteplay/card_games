package org.finiteplay.freecell.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Rule
import org.junit.Test

/**
 * Mirrors Klondike's own `LargeFontScaleTest`: every essential control must stay
 * discoverable and on screen at the largest supported font scale on the narrowest supported
 * width, not merely at the default scale a normal test run uses.
 */
class LargeFontScaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: FreeCellViewModel

    private fun launchAtLargeFontScale() {
        viewModel = FreeCellViewModel(initialSeed = 1L)
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                FinitePlayTheme {
                    Box(Modifier.width(320.dp)) {
                        GameScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    @Test
    fun actionBarKeepsEveryActionOnScreenAtLargeFontAndMinimumWidth() {
        launchAtLargeFontScale()

        for (tag in listOf("action_settings", "action_replay", "action_new", "action_hint", "action_undo")) {
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun settingsScreenStaysReachableAtLargeFontScale() {
        launchAtLargeFontScale()

        composeRule.onNodeWithTag("action_settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("setting_automatic_moves").assertIsDisplayed()
    }

    @Test
    fun statisticsScreenStaysReachableAtLargeFontScale() {
        launchAtLargeFontScale()

        composeRule.onNodeWithTag("statistics_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("statistics_screen").assertIsDisplayed()
    }
}
