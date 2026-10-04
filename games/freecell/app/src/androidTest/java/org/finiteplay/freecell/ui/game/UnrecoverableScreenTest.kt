package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Rule
import org.junit.Test

class UnrecoverableScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheReasonAndInvokesRetry() {
        var retried = false
        composeRule.setContent {
            FinitePlayTheme {
                UnrecoverableScreen(reason = "bad magic value", onRetry = { retried = true })
            }
        }

        composeRule.onNodeWithTag("unrecoverable_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("unrecoverable_reason").assertIsDisplayed()
        composeRule.onNodeWithText("bad magic value").assertIsDisplayed()

        composeRule.onNodeWithText("Try again").performClick()
        assert(retried)
    }
}
