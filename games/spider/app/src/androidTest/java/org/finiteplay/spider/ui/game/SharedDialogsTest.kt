package org.finiteplay.spider.ui.game

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.finiteplay.core.ui.layout.LevelUpDialog
import org.finiteplay.core.ui.layout.RestBreakDialog
import org.finiteplay.core.ui.layout.RestReminderDialog
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Dialogs from `core:ui` that every solitaire shows: the rest reminder and break read time in
 * words, and the level-up question has two answers.
 */
class SharedDialogsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theRestReminderSaysHowLongInWordsNotAsAClockReading() {
        composeRule.setContent {
            FinitePlayTheme { RestReminderDialog(playedSeconds = 90 * 60, onTakeBreak = {}, onKeepPlaying = {}) }
        }
        composeRule.onNodeWithTag("rest_reminder_dialog").assertIsDisplayed()
        // One sentence carrying both units, in words: no "1:30:00".
        composeRule.onNodeWithText("1 hour", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("30 minutes", substring = true).assertIsDisplayed()
    }

    @Test
    fun aThirtyMinuteReminderReadsThirtyMinutes() {
        composeRule.setContent {
            FinitePlayTheme { RestReminderDialog(playedSeconds = 30 * 60, onTakeBreak = {}, onKeepPlaying = {}) }
        }
        composeRule.onNodeWithText("playing for 30 minutes", substring = true).assertIsDisplayed()
    }

    @Test
    fun theBreakCountdownIsAClockFaceButIsSpokenInWords() {
        composeRule.setContent { FinitePlayTheme { RestBreakDialog(remainingSeconds = 4 * 60 + 32, onCancel = {}) } }
        composeRule.onNodeWithTag("rest_break_countdown").assert(
            SemanticsMatcher("is spoken as minutes and seconds") { node ->
                node.config[SemanticsProperties.ContentDescription].any { it.contains("4 minutes") && it.contains("32 seconds") }
            },
        )
    }

    @Test
    fun theLevelUpQuestionNamesTheWinsAndBothLevelsAndSwitchesOnYes() {
        var switched = 0
        var stayed = 0
        composeRule.setContent {
            FinitePlayTheme {
                LevelUpDialog(wins = 10, currentLevel = "Easy", nextLevel = "Medium", onSwitch = { switched++ }, onStay = { stayed++ })
            }
        }
        composeRule.onNodeWithTag("level_up_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("won 10 games at Easy", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("move up to Medium", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("level_up_switch").assertTextContains("Medium", substring = true)

        composeRule.onNodeWithTag("level_up_switch").performClick()

        assertEquals(1, switched)
        assertEquals(0, stayed)
    }

    @Test
    fun theLevelUpQuestionStaysOnNotYet() {
        var switched = 0
        var stayed = 0
        composeRule.setContent {
            FinitePlayTheme {
                LevelUpDialog(wins = 10, currentLevel = "One suit", nextLevel = "Two suits", onSwitch = { switched++ }, onStay = { stayed++ })
            }
        }

        composeRule.onNodeWithTag("level_up_stay").performClick()

        assertEquals(0, switched)
        assertEquals(1, stayed)
    }
}
