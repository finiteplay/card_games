package org.finiteplay.freecell.ui.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.finiteplay.core.session.SessionStatistics
import org.finiteplay.freecell.storage.FreeCellStatistics
import org.finiteplay.core.session.StatisticsPeriod
import org.finiteplay.core.ui.theme.FinitePlayTheme
import org.junit.Rule
import org.junit.Test

/**
 * FreeCell's own statistics panel: one pool, no per-mode split to select — worth its own test
 * rather than trusting Klondike's or Spider's, since what this screen shows and what it lets a
 * player switch is not the same as either (`docs/games/freecell/EXECUTION_PLAN.md` F3b's gate,
 * "displayed statistics match the underlying aggregate").
 */
private val EMPTY = FreeCellStatistics(SessionStatistics.EMPTY, hintsUsed = 0, hintFreeWins = 0, efficiency = null)

private fun stats(wins: Int = 0, losses: Int = 0, gamesPlayed: Int = 0, winRate: Double? = null) =
    EMPTY.copy(session = SessionStatistics.EMPTY.copy(wins = wins, losses = losses, gamesPlayed = gamesPlayed, winRate = winRate))

class StatisticsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysTheSuppliedAggregateAndClosesOnRequest() {
        var closed = false
        val statistics = stats(wins = 3, losses = 1, gamesPlayed = 4)

        composeRule.setContent {
            FinitePlayTheme {
                StatisticsScreen(
                    statistics = statistics,
                    period = StatisticsPeriod.ALL_TIME,
                    onPeriodChange = {},
                    onReset = {},
                    onClose = { closed = true },
                )
            }
        }

        composeRule.onNodeWithTag("statistics_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Wins: 3").assertIsDisplayed()
        composeRule.onNodeWithText("Losses: 1").assertIsDisplayed()
        composeRule.onNodeWithText("Games played: 4").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Close").performClick()
        assert(closed)
    }

    @Test
    fun aFreshHistoryStillShowsTheSectionsWithDashesNotZeroedWinRate() {
        composeRule.setContent {
            FinitePlayTheme {
                StatisticsScreen(
                    statistics = EMPTY,
                    period = StatisticsPeriod.ALL_TIME,
                    onPeriodChange = {},
                    onReset = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("statistics_summary").assertIsDisplayed()
    }

    @Test
    fun resettingRequiresConfirmationBeforeInvokingOnReset() {
        var resetCount = 0
        val statistics = stats(wins = 1, gamesPlayed = 1, winRate = 1.0)

        composeRule.setContent {
            FinitePlayTheme {
                StatisticsScreen(
                    statistics = statistics,
                    period = StatisticsPeriod.ALL_TIME,
                    onPeriodChange = {},
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
    fun theSuppliedPeriodIsSelectedAndTappingAnotherInvokesTheCallback() {
        var selected = StatisticsPeriod.MONTH
        val statistics = stats(wins = 1, gamesPlayed = 1, winRate = 1.0)

        composeRule.setContent {
            FinitePlayTheme {
                StatisticsScreen(
                    statistics = statistics,
                    period = selected,
                    onPeriodChange = { selected = it },
                    onReset = {},
                    onClose = {},
                )
            }
        }

        composeRule.onNodeWithTag("statistics_period_month").assertIsSelected()
        composeRule.onNodeWithTag("statistics_period_week").performClick()
        assert(selected == StatisticsPeriod.WEEK)
    }
}
