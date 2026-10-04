package org.finiteplay.spider.storage

import org.finiteplay.core.session.StatisticsPeriod
import org.finiteplay.spider.layout.SuitCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val NOW = 1_700_000_000_000L
private const val DAY = 24L * 60 * 60 * 1000

private fun record(
    suitCount: SuitCount,
    outcome: SpiderOutcome,
    daysAgo: Long = 0,
    moves: Int = 100,
    millis: Long = 60_000,
    hints: Int = 0,
    solution: Int = 0,
) = SpiderHistoryRecord(
    gameId = "g$daysAgo-$suitCount-$outcome-$moves",
    resultId = "r$daysAgo-$suitCount-$outcome-$moves",
    outcome = outcome,
    suitCount = suitCount,
    elapsedMillis = millis,
    moveCount = moves,
    timestampMillis = NOW - daysAgo * DAY,
    hintsUsed = hints,
    solutionMoveCount = solution,
)

class SpiderStatisticsTest {

    @Test
    fun `suit counts are never blended together`() {
        val records = listOf(
            record(SuitCount.ONE, SpiderOutcome.WIN),
            record(SuitCount.ONE, SpiderOutcome.WIN, moves = 101),
            record(SuitCount.FOUR, SpiderOutcome.LOSS),
        )
        val one = computeSpiderStatistics(records, SuitCount.ONE, StatisticsPeriod.ALL_TIME, NOW)
        val four = computeSpiderStatistics(records, SuitCount.FOUR, StatisticsPeriod.ALL_TIME, NOW)

        assertEquals(2, one.wins)
        assertEquals(0, one.losses)
        assertEquals(0, four.wins)
        assertEquals(1, four.losses)
    }

    @Test
    fun `a suit count with no games reports nothing rather than another count's figures`() {
        val stats = computeSpiderStatistics(
            listOf(record(SuitCount.ONE, SpiderOutcome.WIN)),
            SuitCount.TWO,
            StatisticsPeriod.ALL_TIME,
            NOW,
        )
        assertEquals(0, stats.gamesPlayed)
        assertNull(stats.winRate)
    }

    @Test
    fun `the period window excludes older games`() {
        val records = listOf(
            record(SuitCount.TWO, SpiderOutcome.WIN, daysAgo = 1),
            record(SuitCount.TWO, SpiderOutcome.WIN, daysAgo = 20, moves = 102),
        )
        assertEquals(1, computeSpiderStatistics(records, SuitCount.TWO, StatisticsPeriod.WEEK, NOW).wins)
        assertEquals(2, computeSpiderStatistics(records, SuitCount.TWO, StatisticsPeriod.MONTH, NOW).wins)
        assertEquals(2, computeSpiderStatistics(records, SuitCount.TWO, StatisticsPeriod.ALL_TIME, NOW).wins)
    }

    @Test
    fun `bests come from wins at that suit count alone`() {
        val records = listOf(
            record(SuitCount.TWO, SpiderOutcome.WIN, moves = 200, millis = 90_000),
            // Faster and shorter, but a loss, and at another count: neither may become the best.
            record(SuitCount.TWO, SpiderOutcome.LOSS, moves = 5, millis = 1_000),
            record(SuitCount.ONE, SpiderOutcome.WIN, moves = 10, millis = 2_000),
        )
        val stats = computeSpiderStatistics(records, SuitCount.TWO, StatisticsPeriod.ALL_TIME, NOW)
        assertEquals(200, stats.bestMoveCount)
        assertEquals(90_000L, stats.bestElapsedMillis)
    }

    @Test
    fun `hints used sums across every game in the window, won or lost, at that suit count alone`() {
        val records = listOf(
            record(SuitCount.ONE, SpiderOutcome.WIN, hints = 2),
            record(SuitCount.ONE, SpiderOutcome.LOSS, moves = 30, hints = 1),
            // Another suit count and an older-than-the-window game must not contribute.
            record(SuitCount.TWO, SpiderOutcome.WIN, hints = 5),
            record(SuitCount.ONE, SpiderOutcome.WIN, daysAgo = 20, moves = 40, hints = 9),
        )
        assertEquals(3, computeSpiderStatistics(records, SuitCount.ONE, StatisticsPeriod.WEEK, NOW).hintsUsed)
        assertEquals(12, computeSpiderStatistics(records, SuitCount.ONE, StatisticsPeriod.ALL_TIME, NOW).hintsUsed)
    }

    @Test
    fun `hint-free wins and efficiency come from wins within the chosen suit count`() {
        val records = listOf(
            record(SuitCount.ONE, SpiderOutcome.WIN, moves = 120, hints = 0, solution = 100),
            record(SuitCount.ONE, SpiderOutcome.WIN, moves = 130, hints = 2, solution = 100),
            record(SuitCount.ONE, SpiderOutcome.LOSS, moves = 10, hints = 1, solution = 100),
            record(SuitCount.FOUR, SpiderOutcome.WIN, moves = 500, solution = 100),
        )
        val stats = computeSpiderStatistics(records, SuitCount.ONE, StatisticsPeriod.ALL_TIME, NOW)

        assertEquals(1, stats.hintFreeWins)
        assertEquals(2, stats.efficiency!!.ratioDistribution.sampleSize)
        assertEquals(125L, stats.efficiency!!.averagePercent)
    }
}
