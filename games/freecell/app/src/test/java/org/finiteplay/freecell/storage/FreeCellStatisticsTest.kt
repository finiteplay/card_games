package org.finiteplay.freecell.storage

import org.finiteplay.core.session.StatisticsPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val NOW = 1_700_000_000_000L
private const val DAY = 24L * 60 * 60 * 1000

private fun record(
    outcome: FreeCellOutcome,
    daysAgo: Long = 0,
    moves: Int = 100,
    millis: Long = 60_000,
    hints: Int = 0,
    solution: Int = 0,
) =
    FreeCellHistoryRecord(
        gameId = "g$daysAgo-$outcome-$moves",
        resultId = "r$daysAgo-$outcome-$moves",
        outcome = outcome,
        elapsedMillis = millis,
        moveCount = moves,
        timestampMillis = NOW - daysAgo * DAY,
        hintsUsed = hints,
        solutionMoveCount = solution,
    )

class FreeCellStatisticsTest {
    @Test
    fun `an empty history reports nothing rather than a fabricated figure`() {
        val stats = computeFreeCellStatistics(emptyList(), StatisticsPeriod.ALL_TIME, NOW)
        assertEquals(0, stats.session.gamesPlayed)
        assertNull(stats.session.winRate)
    }

    @Test
    fun `wins and losses are both counted, in one pool with no split`() {
        val records = listOf(
            record(FreeCellOutcome.WIN),
            record(FreeCellOutcome.WIN, moves = 101),
            record(FreeCellOutcome.LOSS, moves = 50),
        )
        val stats = computeFreeCellStatistics(records, StatisticsPeriod.ALL_TIME, NOW)
        assertEquals(2, stats.session.wins)
        assertEquals(1, stats.session.losses)
        assertEquals(3, stats.session.gamesPlayed)
    }

    @Test
    fun `the period window excludes older games`() {
        val records = listOf(
            record(FreeCellOutcome.WIN, daysAgo = 1),
            record(FreeCellOutcome.WIN, daysAgo = 20, moves = 102),
        )
        assertEquals(1, computeFreeCellStatistics(records, StatisticsPeriod.WEEK, NOW).session.wins)
        assertEquals(2, computeFreeCellStatistics(records, StatisticsPeriod.MONTH, NOW).session.wins)
        assertEquals(2, computeFreeCellStatistics(records, StatisticsPeriod.ALL_TIME, NOW).session.wins)
    }

    @Test
    fun `bests come from wins alone`() {
        val records = listOf(
            record(FreeCellOutcome.WIN, moves = 200, millis = 90_000),
            // Faster and shorter, but a loss: it may not become the best.
            record(FreeCellOutcome.LOSS, moves = 5, millis = 1_000),
        )
        val stats = computeFreeCellStatistics(records, StatisticsPeriod.ALL_TIME, NOW)
        assertEquals(200, stats.session.bestMoveCount)
        assertEquals(90_000L, stats.session.bestElapsedMillis)
    }

    @Test
    fun `an unfinished played game is counted toward games played when flagged`() {
        val withoutActive = computeFreeCellStatistics(emptyList(), StatisticsPeriod.ALL_TIME, NOW, hasUnfinishedPlayedGame = false)
        val withActive = computeFreeCellStatistics(emptyList(), StatisticsPeriod.ALL_TIME, NOW, hasUnfinishedPlayedGame = true)

        assertEquals(0, withoutActive.session.gamesPlayed)
        assertEquals(1, withActive.session.gamesPlayed)
    }

    @Test
    fun `hints are summed over every game and hint-free wins count only wins`() {
        val records = listOf(
            record(FreeCellOutcome.WIN, hints = 0),
            record(FreeCellOutcome.WIN, moves = 101, hints = 3),
            record(FreeCellOutcome.LOSS, moves = 50, hints = 2),
        )
        val stats = computeFreeCellStatistics(records, StatisticsPeriod.ALL_TIME, NOW)
        assertEquals(5, stats.hintsUsed)
        assertEquals(1, stats.hintFreeWins)
    }

    @Test
    fun `efficiency compares wins on deals that shipped a solution`() {
        val records = listOf(
            record(FreeCellOutcome.WIN, moves = 120, solution = 100),
            record(FreeCellOutcome.WIN, moves = 101, solution = 0),
            record(FreeCellOutcome.LOSS, moves = 10, solution = 100),
        )
        val efficiency = computeFreeCellStatistics(records, StatisticsPeriod.ALL_TIME, NOW).efficiency!!
        assertEquals(1, efficiency.ratioDistribution.sampleSize)
        assertEquals(120L, efficiency.averagePercent)
    }
}
