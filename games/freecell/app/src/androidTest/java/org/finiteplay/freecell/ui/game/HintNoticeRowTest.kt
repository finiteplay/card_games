package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.finiteplay.core.ui.R as CoreR
import org.junit.Rule
import org.junit.Test

/**
 * `docs/games/freecell/UI_SPEC.md` "Hint": the No-solution / Inconclusive notice is dismissible
 * except while still searching. The Inconclusive message is the longest of the three hint
 * strings — long enough on a real phone width to be worth its own regression check that Dismiss
 * stays on screen rather than pushed out by an unweighted Text.
 */
class HintNoticeRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dismissButtonStaysOnScreenForTheLongInconclusiveMessage() {
        composeRule.setContent {
            HintNoticeRow(hintState = HintUiState.Inconclusive, onDismiss = {})
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(CoreR.string.action_dismiss)).assertIsDisplayed()
    }

    @Test
    fun loadingHasNoDismissButton() {
        composeRule.setContent {
            HintNoticeRow(hintState = HintUiState.Loading, onDismiss = {})
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(CoreR.string.action_dismiss)).assertDoesNotExist()
    }

    @Test
    fun dismissingInvokesTheCallback() {
        var dismissed = false
        composeRule.setContent {
            HintNoticeRow(hintState = HintUiState.NoSolution, onDismiss = { dismissed = true })
        }

        composeRule.onNodeWithTag("hint_notice").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(CoreR.string.action_dismiss)).performClick()
        assert(dismissed)
    }

    @Test
    fun rendersNothingForHiddenOrGuided() {
        composeRule.setContent {
            HintNoticeRow(hintState = HintUiState.Hidden, onDismiss = {})
        }
        composeRule.onNodeWithTag("hint_notice").assertDoesNotExist()
    }
}
