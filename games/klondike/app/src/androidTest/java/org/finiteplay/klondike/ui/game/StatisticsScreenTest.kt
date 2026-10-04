package org.finiteplay.klondike.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.storage.GameStatistics
import org.finiteplay.klondike.storage.PercentileDistribution
import org.finiteplay.klondike.storage.StatisticsPeriod
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Rule
import org.junit.Test

class StatisticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysTheSuppliedAggregatesAndClosesOnRequest() {
        var closed = false
        val statistics = GameStatistics.EMPTY.copy(wins = 3, losses = 1, gamesPlayed = 4)

        composeRule.setContent {
            FinitePlayTheme {
                StatisticsScreen(
                    statistics = statistics,
                    period = StatisticsPeriod.ALL_TIME,
                    onPeriodChange = {},
                    drawMode = DrawMode.ONE,
                    onDrawModeChange = {},
                    onReset = {},
                    onClose = { closed = true },
                )
            }
        }

        composeRule.onNodeWithTag("statistics_screen").assertIsDisplayed()
        // The summary is tiles now: a number and its label are separate nodes, and the pair
        // is merged for accessibility, so the assertion is on what a screen reader reads out.
        composeRule.onNodeWithContentDescription("Wins: 3").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Losses: 1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Games played: 4").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Close").performClick()
        assert(closed)
    }

    @Test
    fun resettingRequiresConfirmationBeforeInvokingOnReset() {
        var resetCount = 0

        composeRule.setContent {
            FinitePlayTheme {
                StatisticsScreen(
                    statistics = GameStatistics.EMPTY,
                    period = StatisticsPeriod.ALL_TIME,
                    onPeriodChange = {},
                    drawMode = DrawMode.ONE,
                    onDrawModeChange = {},
                    onReset = { resetCount++ },
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("statistics_reset").performClick()
        composeRule.onNodeWithTag("statistics_reset_confirmation").assertIsDisplayed()
        assert(resetCount == 0)

        composeRule.onNodeWithText("Reset").performClick()
        assert(resetCount == 1)
    }

    @Test
    fun theSuppliedPeriodTabIsSelectedAndTappingAnotherInvokesTheCallback() {
        var selected = StatisticsPeriod.MONTH
        composeRule.setContent {
            FinitePlayTheme {
                StatisticsScreen(
                    statistics = GameStatistics.EMPTY,
                    period = selected,
                    onPeriodChange = { selected = it },
                    drawMode = DrawMode.ONE,
                    onDrawModeChange = {},
                    onReset = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("statistics_period_month").assertIsSelected()
        composeRule.onNodeWithTag("statistics_period_week").performClick()
        assert(selected == StatisticsPeriod.WEEK)
    }

    @Test
    fun theSuppliedDrawModeTabIsSelectedAndTappingTheOtherInvokesTheCallback() {
        var selected = DrawMode.ONE
        composeRule.setContent {
            FinitePlayTheme {
                StatisticsScreen(
                    statistics = GameStatistics.EMPTY,
                    period = StatisticsPeriod.ALL_TIME,
                    onPeriodChange = {},
                    drawMode = selected,
                    onDrawModeChange = { selected = it },
                    onReset = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("statistics_draw_mode_one").assertIsSelected()
        composeRule.onNodeWithTag("statistics_draw_mode_three").performClick()
        assert(selected == DrawMode.THREE)
    }

    @Test
    fun aNonEmptyDistributionRendersTheRangeGraphInsteadOfTextOnly() {
        val statistics = GameStatistics.EMPTY.copy(
            moveCountDistribution = PercentileDistribution(sampleSize = 5, min = 10, p10 = 12, p50 = 20, p90 = 40, max = 50),
        )
        composeRule.setContent {
            FinitePlayTheme {
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
        }

        // Scrolled to first: the screen is a scrolling column of sections now, so on a short
        // screen the distributions sit below the fold and "exists but is not displayed" is a
        // true statement about a screen that is working.
        composeRule.onNodeWithTag("statistics_distribution_graph").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("median 20").performScrollTo().assertIsDisplayed()
    }
}
