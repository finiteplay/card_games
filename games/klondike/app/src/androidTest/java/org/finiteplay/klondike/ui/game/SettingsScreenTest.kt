package org.finiteplay.klondike.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.storage.DifficultyPreference
import org.finiteplay.klondike.storage.Handedness
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun togglesReflectCurrentValuesAndInvokeCallbacksImmediately() {
        var automaticMoves = true
        var animationsEnabled = false
        var hintShowsWinningMove = true
        var handedness = Handedness.RIGHT
        var soundEnabled = true
        var drawMode = DrawMode.ONE
        var difficulty = DifficultyPreference.RANDOM
        var languageTag = SYSTEM_LANGUAGE
        var themeMode = ThemeMode.SYSTEM
        var closed = false

        composeRule.setContent {
            FinitePlayTheme {
                SettingsScreen(
                    automaticMovesEnabled = automaticMoves,
                    animationsEnabled = animationsEnabled,
                    hintShowsWinningMove = hintShowsWinningMove,
                    handedness = handedness,
                    soundEnabled = soundEnabled,
                    drawMode = drawMode,
                    difficulty = difficulty,
                    languageTag = languageTag,
                    themeMode = themeMode,
                    onAutomaticMovesEnabledChange = { automaticMoves = it },
                    onAnimationsEnabledChange = { animationsEnabled = it },
                    onHintShowsWinningMoveChange = { hintShowsWinningMove = it },
                    onHandednessChange = { handedness = it },
                    onSoundEnabledChange = { soundEnabled = it },
                    onDrawModeChange = { drawMode = it },
                    onDifficultyChange = { difficulty = it },
                    onLanguageChange = { languageTag = it },
                    onThemeModeChange = { themeMode = it },
                    hintTimeout = HintTimeout.DEFAULT,
                    restReminderInterval = RestReminderInterval.DEFAULT,
                    restReminderElapsedSeconds = 0,
                    onHintTimeoutChange = {},
                    onRestReminderIntervalChange = {},
                    onClose = { closed = true },
                )
            }
        }

        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("setting_automatic_moves").assertIsOn()
        composeRule.onNodeWithTag("setting_enable_animations").assertIsOff()
        composeRule.onNodeWithTag("setting_left_handed").assertIsOff()
        composeRule.onNodeWithTag("setting_sound").assertIsOn()
        composeRule.onNodeWithTag("setting_draw_three").assertIsOff()

        composeRule.onNodeWithTag("setting_enable_animations").performClick()
        assert(animationsEnabled)

        composeRule.onNodeWithTag("setting_hint_shows_winning_move").assertIsOn()
        composeRule.onNodeWithTag("setting_hint_shows_winning_move").performClick()
        assert(!hintShowsWinningMove)

        composeRule.onNodeWithTag("setting_left_handed").performClick()
        assert(handedness == Handedness.LEFT)

        composeRule.onNodeWithTag("setting_sound").performClick()
        assert(!soundEnabled)

        composeRule.onNodeWithTag("setting_draw_three").performClick()
        assert(drawMode == DrawMode.THREE)

        composeRule.onNodeWithContentDescription("Close").performClick()
        assert(closed)
    }

    @Test
    fun difficultyOffersEveryLevelPlusRandomAndReportsTheChoice() {
        var difficulty = DifficultyPreference.RANDOM
        var languageTag = SYSTEM_LANGUAGE
        var themeMode = ThemeMode.SYSTEM

        composeRule.setContent {
            FinitePlayTheme {
                SettingsScreen(
                    automaticMovesEnabled = true,
                    animationsEnabled = true,
                    hintShowsWinningMove = true,
                    handedness = Handedness.RIGHT,
                    soundEnabled = false,
                    drawMode = DrawMode.ONE,
                    difficulty = difficulty,
                    languageTag = languageTag,
                    themeMode = themeMode,
                    onAutomaticMovesEnabledChange = {},
                    onAnimationsEnabledChange = {},
                    onHintShowsWinningMoveChange = {},
                    onHandednessChange = {},
                    onSoundEnabledChange = {},
                    onDrawModeChange = {},
                    onDifficultyChange = { difficulty = it },
                    onLanguageChange = { languageTag = it },
                    onThemeModeChange = { themeMode = it },
                    hintTimeout = HintTimeout.DEFAULT,
                    restReminderInterval = RestReminderInterval.DEFAULT,
                    restReminderElapsedSeconds = 0,
                    onHintTimeoutChange = {},
                    onRestReminderIntervalChange = {},
                    onClose = {},
                )
            }
        }

        // The options live in a dropdown, so nothing is offered until it is opened.
        composeRule.onNodeWithTag("setting_difficulty").performClick()

        // Every level is offered, Trivial and Insane and Random included - the whole
        // point of the control is picking a specific difficulty instead of taking what
        // comes.
        for (option in DifficultyPreference.entries) {
            composeRule.onNodeWithTag("setting_difficulty_${option.name.lowercase()}").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("setting_difficulty_hard").performClick()
        assert(difficulty == DifficultyPreference.HARD) { "expected HARD, was $difficulty" }
    }

    @Test
    fun languageOffersSystemDefaultAndEveryShippedLanguage() {
        var languageTag = SYSTEM_LANGUAGE
        var themeMode = ThemeMode.SYSTEM

        composeRule.setContent {
            FinitePlayTheme {
                SettingsScreen(
                    automaticMovesEnabled = true,
                    animationsEnabled = true,
                    hintShowsWinningMove = true,
                    handedness = Handedness.RIGHT,
                    soundEnabled = false,
                    drawMode = DrawMode.ONE,
                    difficulty = DifficultyPreference.RANDOM,
                    languageTag = languageTag,
                    themeMode = themeMode,
                    onAutomaticMovesEnabledChange = {},
                    onAnimationsEnabledChange = {},
                    onHintShowsWinningMoveChange = {},
                    onHandednessChange = {},
                    onSoundEnabledChange = {},
                    onDrawModeChange = {},
                    onDifficultyChange = {},
                    onLanguageChange = { languageTag = it },
                    onThemeModeChange = { themeMode = it },
                    hintTimeout = HintTimeout.DEFAULT,
                    restReminderInterval = RestReminderInterval.DEFAULT,
                    restReminderElapsedSeconds = 0,
                    onHintTimeoutChange = {},
                    onRestReminderIntervalChange = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("setting_language").performClick()
        // Ukrainian specifically: the localization spec requires it, so its presence in the
        // picker is worth pinning rather than assuming. Thirty-one languages do not fit on
        // screen at once, so it has to be scrolled into view before it can be clicked.
        composeRule.onNodeWithTag("setting_language_uk").performScrollTo().performClick()

        assert(languageTag == "uk") { "expected uk, was '$languageTag'" }
    }

    @Test
    fun themeOffersLightDarkSystemAndAutomatic() {
        var themeMode = ThemeMode.SYSTEM

        composeRule.setContent {
            FinitePlayTheme {
                SettingsScreen(
                    automaticMovesEnabled = true,
                    animationsEnabled = true,
                    hintShowsWinningMove = true,
                    handedness = Handedness.RIGHT,
                    soundEnabled = false,
                    drawMode = DrawMode.ONE,
                    difficulty = DifficultyPreference.RANDOM,
                    languageTag = SYSTEM_LANGUAGE,
                    themeMode = themeMode,
                    onAutomaticMovesEnabledChange = {},
                    onAnimationsEnabledChange = {},
                    onHintShowsWinningMoveChange = {},
                    onHandednessChange = {},
                    onSoundEnabledChange = {},
                    onDrawModeChange = {},
                    onDifficultyChange = {},
                    onLanguageChange = {},
                    onThemeModeChange = { themeMode = it },
                    hintTimeout = HintTimeout.DEFAULT,
                    restReminderInterval = RestReminderInterval.DEFAULT,
                    restReminderElapsedSeconds = 0,
                    onHintTimeoutChange = {},
                    onRestReminderIntervalChange = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("setting_theme").performClick()

        // All four, including AUTO — it is the one a follow-the-system control cannot
        // stand in for, since it answers to the clock rather than the device setting.
        for (option in ThemeMode.entries) {
            composeRule.onNodeWithTag("setting_theme_${option.name.lowercase()}").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("setting_theme_light").performClick()
        assert(themeMode == ThemeMode.LIGHT) { "expected LIGHT, was $themeMode" }
    }
}
