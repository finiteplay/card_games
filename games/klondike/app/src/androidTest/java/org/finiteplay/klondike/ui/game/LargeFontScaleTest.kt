package org.finiteplay.klondike.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.storage.DifficultyPreference
import org.finiteplay.klondike.storage.GameStatistics
import org.finiteplay.klondike.storage.Handedness
import org.finiteplay.klondike.storage.PercentileDistribution
import org.finiteplay.klondike.storage.StatisticsPeriod
import org.junit.Rule
import org.junit.Test

/**
 * `EXECUTION_PLAN.md` Q1: "Normal and large-font screenshots for the board, Settings, and
 * Statistics have no clipped essential content." A screenshot alone proves nothing without a
 * definition of "clipped"; here that means every essential control and status text stays
 * discoverable and drawn on screen at the largest supported font scale, on the narrowest
 * supported width, rather than being pushed off, collapsed to zero size, or crashing layout.
 * `fontScale = 2f` mirrors Android's own "Accessibility" font size ceiling.
 */
class LargeFontScaleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContentAtLargeFontScale(widthDp: Int? = null, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                FinitePlayTheme {
                    if (widthDp != null) {
                        Box(Modifier.width(widthDp.dp)) { content() }
                    } else {
                        content()
                    }
                }
            }
        }
    }

    @Test
    fun actionBarKeepsEveryLabelOnScreenAtLargeFontAndMinimumWidth() {
        setContentAtLargeFontScale(widthDp = 320) {
            ActionBar(
                orientation = BoardOrientation.PORTRAIT,
                canUndo = true,
                hintEnabled = true,
                landscapeGroup = LandscapeActionGroup.PRIMARY,
                onUndo = {},
                onNew = {},
                onReplay = {},
                onHint = {},
                onSettings = {},
            )
        }

        for (label in listOf("Settings", "Replay", "New", "Hint", "Undo")) {
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun settingsScreenStaysReachableAtLargeFontScale() {
        setContentAtLargeFontScale {
            SettingsScreen(
                automaticMovesEnabled = true,
                animationsEnabled = true,
                hintShowsWinningMove = true,
                handedness = Handedness.RIGHT,
                soundEnabled = true,
                drawMode = DrawMode.ONE,
                difficulty = DifficultyPreference.RANDOM,
                languageTag = SYSTEM_LANGUAGE,
                themeMode = ThemeMode.SYSTEM,
                onAutomaticMovesEnabledChange = {},
                onAnimationsEnabledChange = {},
                onHintShowsWinningMoveChange = {},
                onHandednessChange = {},
                onSoundEnabledChange = {},
                onDrawModeChange = {},
                onDifficultyChange = {},
                onLanguageChange = {},
                onThemeModeChange = {},
                hintTimeout = HintTimeout.DEFAULT,
                restReminderInterval = RestReminderInterval.DEFAULT,
                restReminderElapsedSeconds = 0,
                onHintTimeoutChange = {},
                onRestReminderIntervalChange = {},
                onClose = {},
            )
        }

        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("setting_automatic_moves").assertIsDisplayed()
        composeRule.onNodeWithTag("setting_theme").assertIsDisplayed()
    }

    @Test
    fun statisticsScreenStaysReachableAtLargeFontScale() {
        val statistics = GameStatistics.EMPTY.copy(
            wins = 3,
            losses = 1,
            gamesPlayed = 4,
            moveCountDistribution = PercentileDistribution(sampleSize = 5, min = 10, p10 = 12, p50 = 20, p90 = 40, max = 50),
        )
        setContentAtLargeFontScale {
            StatisticsScreen(
                statistics = statistics,
                period = StatisticsPeriod.ALL_TIME,
                onPeriodChange = {},
                drawMode = DrawMode.ONE,
                onDrawModeChange = {},
                onReset = {},
                onClose = {},
            )
        }

        composeRule.onNodeWithTag("statistics_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics_period_all_time").assertIsDisplayed()
        composeRule.onNodeWithTag("statistics_reset").assertIsDisplayed()
    }
}
