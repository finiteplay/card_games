package org.finiteplay.klondike

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

/** Settings has a Send feedback entry, and it opens the form: reached the way a player reaches it. */
class FeedbackSettingsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun theFormOpensFromSettings() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("action_settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("about_send_feedback").performScrollTo().performClick()

        composeRule.onNodeWithTag("feedback_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback_cancel").performClick()
    }
}
