package org.finiteplay.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A minimal record standing in for whatever shape a game keeps its history in — deliberately
 * not the shape any real game uses, so these tests pin the accessor seam rather than one
 * caller's field names.
 */
private data class Result(
    val at: Long,
    val won: Boolean,
    val millis: Long,
    val moves: Int,
)

private fun stats(records: List<Result>, unfinished: Boolean = false) =
    computeSessionStatistics(
        records = records,
        hasUnfinishedPlayedGame = unfinished,
        timestampMillis = { it.at },
        isWin = { it.won },
        elapsedMillis = { it.millis },
        moveCount = { it.moves },
    )

class SessionStatisticsTest {
    @Test
    fun `no records and no game in progress is empty`() {
        assertEquals(SessionStatistics.EMPTY, stats(emptyList()))
    }

    @Test
    fun `win rate is null rather than zero before anything is decided`() {
        val result = stats(emptyList(), unfinished = true)
        assertNull("nothing decided yet is not a zero win rate", result.winRate)
        assertEquals(0, result.wins)
        assertEquals(0, result.losses)
    }

    @Test
    fun `a game in progress counts as played but does not move the win rate`() {
        val records = listOf(Result(1, won = true, millis = 100, moves = 10))
        val settled = stats(records)
        val withUnfinished = stats(records, unfinished = true)

        assertEquals(1, settled.gamesPlayed)
        assertEquals(2, withUnfinished.gamesPlayed)
        assertEquals(settled.winRate, withUnfinished.winRate)
    }

    @Test
    fun `wins and losses split, and the rate is their exact ratio`() {
        val result = stats(
            listOf(
                Result(1, won = true, millis = 100, moves = 10),
                Result(2, won = false, millis = 200, moves = 20),
                Result(3, won = true, millis = 300, moves = 30),
                Result(4, won = true, millis = 400, moves = 40),
            ),
        )
        assertEquals(3, result.wins)
        assertEquals(1, result.losses)
        assertEquals(4, result.gamesPlayed)
        assertEquals(0.75, result.winRate!!, 1e-9)
    }

    @Test
    fun `bests and distributions ignore losses entirely`() {
        // The fastest and shortest games here are both losses; counting them would rank
        // giving up early as a personal best.
        val result = stats(
            listOf(
                Result(1, won = false, millis = 1, moves = 1),
                Result(2, won = true, millis = 500, moves = 50),
                Result(3, won = true, millis = 900, moves = 90),
            ),
        )
        assertEquals(500L, result.bestElapsedMillis)
        assertEquals(50, result.bestMoveCount)
        assertEquals(2, result.elapsedDistribution!!.sampleSize)
        assertEquals(2, result.moveCountDistribution!!.sampleSize)
    }

    @Test
    fun `bests are null when nothing has been won`() {
        val result = stats(listOf(Result(1, won = false, millis = 10, moves = 5)))
        assertNull(result.bestElapsedMillis)
        assertNull(result.bestMoveCount)
    }

    @Test
    fun `current streak counts back from the newest result and a loss resets it`() {
        val won = stats(
            listOf(
                Result(1, won = true, millis = 1, moves = 1),
                Result(2, won = false, millis = 1, moves = 1),
                Result(3, won = true, millis = 1, moves = 1),
                Result(4, won = true, millis = 1, moves = 1),
            ),
        )
        assertEquals(2, won.currentStreak)

        val lostLast = stats(
            listOf(
                Result(1, won = true, millis = 1, moves = 1),
                Result(2, won = false, millis = 1, moves = 1),
            ),
        )
        assertEquals(0, lostLast.currentStreak)
    }

    @Test
    fun `longest streak finds the best run anywhere, not just the current one`() {
        val result = stats(
            listOf(
                Result(1, won = true, millis = 1, moves = 1),
                Result(2, won = true, millis = 1, moves = 1),
                Result(3, won = true, millis = 1, moves = 1),
                Result(4, won = false, millis = 1, moves = 1),
                Result(5, won = true, millis = 1, moves = 1),
            ),
        )
        assertEquals(3, result.longestStreak)
        assertEquals(1, result.currentStreak)
    }

    @Test
    fun `records are ordered by timestamp, not by the order they arrive in`() {
        // Same three results, shuffled: streaks are a chronological notion, so a caller
        // handing them over newest-first must not get a different answer.
        val chronological = listOf(
            Result(1, won = false, millis = 1, moves = 1),
            Result(2, won = true, millis = 1, moves = 1),
            Result(3, won = true, millis = 1, moves = 1),
        )
        assertEquals(stats(chronological), stats(chronological.reversed()))
        assertEquals(2, stats(chronological.reversed()).currentStreak)
    }

    @Test
    fun `longest loss streak and average time are drawn from the right games`() {
        val result = stats(
            listOf(
                Result(1, won = false, millis = 0, moves = 1),
                Result(2, won = false, millis = 0, moves = 1),
                Result(3, won = true, millis = 100, moves = 10),
                Result(4, won = false, millis = 0, moves = 1),
                Result(5, won = true, millis = 300, moves = 10),
            ),
        )
        assertEquals(2, result.longestLossStreak)
        assertEquals(200L, result.averageElapsedMillis)
    }

    @Test
    fun `recent win rate looks only at the latest games and waits for enough of them`() {
        val early = (1..RECENT_GAMES).map { Result(it.toLong(), won = false, millis = 1, moves = 1) }
        assertNull(stats(early).recentWinRate)

        val later = early + (1..RECENT_GAMES / 2).map { Result(100L + it, won = true, millis = 1, moves = 1) }
        assertEquals(0.5, stats(later).recentWinRate!!, 1e-9)
    }
}
