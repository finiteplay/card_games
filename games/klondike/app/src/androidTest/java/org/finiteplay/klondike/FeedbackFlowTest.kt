package org.finiteplay.klondike

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.finiteplay.core.ui.feedback.EnjoyFlow
import org.finiteplay.core.ui.feedback.FeedbackDialog
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The feedback form and the "Enjoying this game?" dialog on a device, where a text field in a dialog works (it does not
 * under Robolectric, so `core/ui`'s own tests leave these flows to this suite).
 */
class FeedbackFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sendStaysOffUntilThereIsAMessage() {
        composeRule.setContent { FinitePlayTheme { FeedbackDialog(onDone = {}) } }

        composeRule.onNodeWithTag("feedback_send").assertIsNotEnabled()
        composeRule.onNodeWithTag("feedback_message").performTextInput("The undo button is hard to find")
        composeRule.onNodeWithTag("feedback_send").assertIsEnabled()
    }

    @Test
    fun theCategoriesAreChoosable_andCancelClosesTheForm() {
        var done = 0
        composeRule.setContent { FinitePlayTheme { FeedbackDialog(onDone = { done++ }) } }

        composeRule.onNodeWithTag("feedback_category_idea").performClick()
        composeRule.onNodeWithTag("feedback_category_other").performClick()
        composeRule.onNodeWithTag("feedback_include_info").performClick()
        composeRule.onNodeWithTag("feedback_cancel").performClick()

        assertEquals(1, done)
    }

    @Test
    fun notReallyLeadsToTheFeedbackForm() {
        val finished = mutableListOf<Boolean>()
        composeRule.setContent { FinitePlayTheme { EnjoyFlow(onFinished = { finished += it }) } }
        composeRule.onNodeWithTag("enjoy_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("enjoy_no").performClick()

        composeRule.onNodeWithTag("feedback_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback_cancel").performClick()
        assertEquals(listOf(true), finished)
    }
}
