package org.finiteplay.klondike

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun gameScreenIsDisplayed() {
        composeRule
            .onNodeWithTag("app_root")
            .assertIsDisplayed()
    }

    @Test
    fun statusRowIsDisplayed() {
        composeRule
            .onNodeWithTag("status_row")
            .assertIsDisplayed()
    }

    @Test
    fun newActionIsDisplayed() {
        composeRule
            .onNodeWithText("New")
            .assertIsDisplayed()
    }
}
