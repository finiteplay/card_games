package org.finiteplay.klondike.storage

import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.board.DrawMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsTest {

    private fun record(
        gameId: String,
        outcome: Outcome,
        elapsedMillis: Long = 0L,
        moveCount: Int = 0,
        timestampMillis: Long = 0L,
        drawMode: DrawMode = DrawMode.ONE,
    ) = HistoryRecord(
        gameId = gameId,
        resultId = "$gameId:result",
        outcome = outcome,
        elapsedMillis = elapsedMillis,
        moveCount = moveCount,
        timestampMillis = timestampMillis,
        drawMode = drawMode,
    )

    @Test
    fun `an empty history with no active game is entirely empty`() {
        val stats = computeStatistics(emptyList(), hasUnfinishedPlayedGame = false)

        assertEquals(GameStatistics.EMPTY, stats)
    }

    @Test
    fun `win rate is undefined, not zero, until a game has completed`() {
        val stats = computeStatistics(emptyList(), hasUnfinishedPlayedGame = true)

        assertEquals(0, stats.wins)
        assertEquals(0, stats.losses)
        assertEquals(1, stats.gamesPlayed)
        assertNull(stats.winRate)
    }

    @Test
    fun `an unfinished played game raises games played without changing win rate`() {
        val records = listOf(
            record("g1", Outcome.WIN, timestampMillis = 1),
            record("g2", Outcome.WIN, timestampMillis = 2),
            record("g3", Outcome.LOSS, timestampMillis = 3),
        )

        val withoutActive = computeStatistics(records, hasUnfinishedPlayedGame = false)
        val withActive = computeStatistics(records, hasUnfinishedPlayedGame = true)

        assertEquals(3, withoutActive.gamesPlayed)
        assertEquals(4, withActive.gamesPlayed)
        assertEquals(withoutActive.winRate, withActive.winRate)
        assertEquals(2.0 / 3.0, withActive.winRate!!, 1e-9)
    }

    @Test
    fun `win rate is wins over wins plus losses`() {
        val records = listOf(
            record("g1", Outcome.WIN),
            record("g2", Outcome.WIN),
            record("g3", Outcome.WIN),
            record("g4", Outcome.LOSS),
        )

        val stats = computeStatistics(records, hasUnfinishedPlayedGame = false)

        assertEquals(3, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(0.75, stats.winRate!!, 1e-9)
    }

    @Test
    fun `current streak resets on any loss and counts trailing wins`() {
        val records = listOf(
            record("g1", Outcome.WIN, timestampMillis = 1),
            record("g2", Outcome.WIN, timestampMillis = 2),
            record("g3", Outcome.LOSS, timestampMillis = 3),
            record("g4", Outcome.WIN, timestampMillis = 4),
        )

        val stats = computeStatistics(records, hasUnfinishedPlayedGame = false)

        assertEquals(1, stats.currentStreak)
    }

    @Test
    fun `longest streak can exceed the current trailing streak`() {
        val records = listOf(
            record("g1", Outcome.WIN, timestampMillis = 1),
            record("g2", Outcome.WIN, timestampMillis = 2),
            record("g3", Outcome.WIN, timestampMillis = 3),
            record("g4", Outcome.LOSS, timestampMillis = 4),
            record("g5", Outcome.WIN, timestampMillis = 5),
        )

        val stats = computeStatistics(records, hasUnfinishedPlayedGame = false)

        assertEquals(3, stats.longestStreak)
        assertEquals(1, stats.currentStreak)
    }

    @Test
    fun `a history of only losses has a zero current and longest streak`() {
        val records = listOf(
            record("g1", Outcome.LOSS, timestampMillis = 1),
            record("g2", Outcome.LOSS, timestampMillis = 2),
        )

        val stats = computeStatistics(records, hasUnfinishedPlayedGame = false)

        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.longestStreak)
        assertEquals(2, stats.losses)
    }

    @Test
    fun `best time and moves are the minimum among wins only`() {
        val records = listOf(
            record("g1", Outcome.WIN, elapsedMillis = 5_000, moveCount = 120),
            record("g2", Outcome.LOSS, elapsedMillis = 100, moveCount = 3),
            record("g3", Outcome.WIN, elapsedMillis = 3_000, moveCount = 90),
        )

        val stats = computeStatistics(records, hasUnfinishedPlayedGame = false)

        assertEquals(3_000L, stats.bestElapsedMillis)
        assertEquals(90, stats.bestMoveCount)
    }

    @Test
    fun `distributions are null with no completed wins`() {
        val records = listOf(record("g1", Outcome.LOSS))

        val stats = computeStatistics(records, hasUnfinishedPlayedGame = false)

        assertNull(stats.elapsedDistribution)
        assertNull(stats.moveCountDistribution)
    }

    @Test
    fun `a single win yields a single-sample distribution`() {
        val records = listOf(record("g1", Outcome.WIN, elapsedMillis = 42, moveCount = 7))

        val distribution = computeStatistics(records, hasUnfinishedPlayedGame = false).elapsedDistribution!!

        assertEquals(1, distribution.sampleSize)
        assertEquals(42L, distribution.min)
        assertEquals(42L, distribution.p10)
        assertEquals(42L, distribution.p50)
        assertEquals(42L, distribution.p90)
        assertEquals(42L, distribution.max)
    }

    @Test
    fun `nearest-rank percentiles over an even-sized sample`() {
        // 10 values 1..10: rank = ceil(percentile * 10).
        val values = (1L..10L).toList()

        val distribution = PercentileDistribution.of(values)!!

        assertEquals(10, distribution.sampleSize)
        assertEquals(1L, distribution.min)
        assertEquals(1L, distribution.p10) // ceil(0.1*10)=1 -> values[0]
        assertEquals(5L, distribution.p50) // ceil(0.5*10)=5 -> values[4]
        assertEquals(9L, distribution.p90) // ceil(0.9*10)=9 -> values[8]
        assertEquals(10L, distribution.max)
    }

    @Test
    fun `nearest-rank percentiles over an odd-sized sample`() {
        val values = listOf(1L, 2L, 3L, 4L, 5L)

        val distribution = PercentileDistribution.of(values)!!

        assertEquals(1L, distribution.p10) // ceil(0.5)=1 -> values[0]
        assertEquals(3L, distribution.p50) // ceil(2.5)=3 -> values[2]
        assertEquals(5L, distribution.p90) // ceil(4.5)=5 -> values[4]
    }

    @Test
    fun `repeated values are ranked like any other sample`() {
        val values = listOf(5L, 5L, 5L, 5L, 5L)

        val distribution = PercentileDistribution.of(values)!!

        assertEquals(5L, distribution.min)
        assertEquals(5L, distribution.p10)
        assertEquals(5L, distribution.p50)
        assertEquals(5L, distribution.p90)
        assertEquals(5L, distribution.max)
    }

    @Test
    fun `a large history computes without error`() {
        val records = (1..10_000).map { i ->
            record(
                gameId = "g$i",
                outcome = if (i % 3 == 0) Outcome.LOSS else Outcome.WIN,
                elapsedMillis = (i * 37L) % 5_000L,
                moveCount = i % 300,
                timestampMillis = i.toLong(),
            )
        }

        val stats = computeStatistics(records, hasUnfinishedPlayedGame = false)

        assertEquals(10_000, stats.wins + stats.losses)
        assertEquals(stats.wins, stats.elapsedDistribution?.sampleSize)
    }

    private val oneDayMillis = 24L * 60 * 60 * 1000

    @Test
    fun `ALL_TIME returns every record regardless of age`() {
        val now = 1_000_000L
        val records = listOf(
            record("ancient", Outcome.WIN, timestampMillis = 0L),
            record("recent", Outcome.WIN, timestampMillis = now),
        )

        assertEquals(records, filterByPeriod(records, StatisticsPeriod.ALL_TIME, nowMillis = now))
    }

    @Test
    fun `WEEK keeps a record from 6 days ago and drops one from 8 days ago`() {
        val now = 30 * oneDayMillis
        val withinWeek = record("within", Outcome.WIN, timestampMillis = now - 6 * oneDayMillis)
        val beforeWeek = record("before", Outcome.WIN, timestampMillis = now - 8 * oneDayMillis)

        val filtered = filterByPeriod(listOf(withinWeek, beforeWeek), StatisticsPeriod.WEEK, nowMillis = now)

        assertEquals(listOf(withinWeek), filtered)
    }

    @Test
    fun `MONTH keeps a record from 29 days ago and drops one from 31 days ago`() {
        val now = 60 * oneDayMillis
        val withinMonth = record("within", Outcome.WIN, timestampMillis = now - 29 * oneDayMillis)
        val beforeMonth = record("before", Outcome.WIN, timestampMillis = now - 31 * oneDayMillis)

        val filtered = filterByPeriod(listOf(withinMonth, beforeMonth), StatisticsPeriod.MONTH, nowMillis = now)

        assertEquals(listOf(withinMonth), filtered)
    }

    @Test
    fun `a record exactly at the window boundary is kept`() {
        val now = 30 * oneDayMillis
        val atBoundary = record("boundary", Outcome.WIN, timestampMillis = now - 7 * oneDayMillis)

        assertEquals(listOf(atBoundary), filterByPeriod(listOf(atBoundary), StatisticsPeriod.WEEK, nowMillis = now))
    }

    @Test
    fun `filterByDrawMode keeps only the matching mode, never blending the two`() {
        val drawOne = record("one", Outcome.WIN, drawMode = DrawMode.ONE)
        val drawThree = record("three", Outcome.WIN, drawMode = DrawMode.THREE)
        val records = listOf(drawOne, drawThree)

        assertEquals(listOf(drawOne), filterByDrawMode(records, DrawMode.ONE))
        assertEquals(listOf(drawThree), filterByDrawMode(records, DrawMode.THREE))
    }

    @Test
    fun `win rate by level counts decided games per level and omits levels and records without one`() {
        val stats = computeStatistics(
            listOf(
                record("a", Outcome.WIN).copy(difficulty = DifficultyTier.EASY),
                record("b", Outcome.LOSS).copy(difficulty = DifficultyTier.EASY),
                record("c", Outcome.WIN).copy(difficulty = DifficultyTier.HARD),
                record("d", Outcome.WIN),
            ),
            hasUnfinishedPlayedGame = false,
        )

        assertEquals(
            listOf(LevelRecord(DifficultyTier.EASY, 1, 1), LevelRecord(DifficultyTier.HARD, 1, 0)),
            stats.byLevel,
        )
    }

    @Test
    fun `efficiency compares only wins on deals that shipped a solution`() {
        val stats = computeStatistics(
            listOf(
                record("a", Outcome.WIN, moveCount = 120).copy(solutionMoveCount = 100),
                record("b", Outcome.WIN, moveCount = 90).copy(solutionMoveCount = 0),
                record("c", Outcome.LOSS, moveCount = 10).copy(solutionMoveCount = 100),
            ),
            hasUnfinishedPlayedGame = false,
        )

        val efficiency = stats.efficiency!!
        assertEquals(1, efficiency.ratioDistribution.sampleSize)
        assertEquals(120L, efficiency.averagePercent)
        assertEquals(0, efficiency.matchedOrBeaten)
    }
}
