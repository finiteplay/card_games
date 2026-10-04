package org.finiteplay.klondike.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Rule
import org.junit.Test

class StuckNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun offersUndoAndNewGame() {
        var undone = false
        var newGame = false

        composeRule.setContent {
            FinitePlayTheme {
                StuckNotice(canUndo = true, onUndo = { undone = true }, onNewGame = { newGame = true }, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag("stuck_notice").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()
        assert(undone)
        assert(!newGame)
    }

    @Test
    fun undoIsDisabledWhenThereIsNothingToUndo() {
        composeRule.setContent {
            FinitePlayTheme {
                StuckNotice(canUndo = false, onUndo = {}, onNewGame = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Undo").assertIsNotEnabled()
    }
}
