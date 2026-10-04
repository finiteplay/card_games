package org.finiteplay.freecell.ui.game

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * F4's theme coverage: the board renders under both themes without a composition failure, and
 * the two themes actually resolve to visibly different backgrounds — the cheapest check that
 * would catch a `FinitePlayTheme(darkTheme = ...)` wiring bug (a forced theme silently ignored,
 * both resolving to the same palette), mirroring Spider's own `ThemeTest`.
 */
class ThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theBoardDisplaysUnderBothThemesWithVisiblyDifferentBackgrounds() {
        val viewModel = FreeCellViewModel(initialSeed = 1L)
        var lightBackground = 0
        var darkBackground = 0
        var dark by mutableStateOf(false)

        composeRule.setContent {
            FinitePlayTheme(darkTheme = dark) {
                if (dark) darkBackground = MaterialTheme.colorScheme.background.toArgb()
                else lightBackground = MaterialTheme.colorScheme.background.toArgb()
                GameScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_root").assertIsDisplayed()

        dark = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_root").assertIsDisplayed()

        assertNotEquals("light and dark themes must not resolve to the same background", lightBackground, darkBackground)
    }
}
