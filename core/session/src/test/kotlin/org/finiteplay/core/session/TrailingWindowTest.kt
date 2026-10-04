package org.finiteplay.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class TrailingWindowTest {

    private val dayMillis = 24L * 60 * 60 * 1000
    private val now = 100 * dayMillis

    /** Timestamped by its own value, in days, so a test reads as "the record from N days ago". */
    private fun daysAgo(days: Long): Long = now - days * dayMillis

    @Test
    fun `all-time returns every record unfiltered`() {
        val records = listOf(daysAgo(0), daysAgo(10), daysAgo(400))

        assertEquals(records, filterByPeriod(records, StatisticsPeriod.ALL_TIME, now) { it })
    }

    @Test
    fun `week keeps only the trailing seven days`() {
        val records = listOf(daysAgo(0), daysAgo(6), daysAgo(7), daysAgo(8))

        val kept = filterByPeriod(records, StatisticsPeriod.WEEK, now) { it }

        assertEquals(listOf(daysAgo(0), daysAgo(6), daysAgo(7)), kept)
    }

    @Test
    fun `month keeps only the trailing thirty days`() {
        val records = listOf(daysAgo(29), daysAgo(30), daysAgo(31))

        val kept = filterByPeriod(records, StatisticsPeriod.MONTH, now) { it }

        assertEquals(listOf(daysAgo(29), daysAgo(30)), kept)
    }

    @Test
    fun `the window is measured back from now, not a calendar boundary`() {
        // now is day 100; a record from day 95 is 5 days old regardless of what weekday or
        // month-day either falls on.
        val fiveDaysOld = daysAgo(5)

        assertEquals(listOf(fiveDaysOld), filterByPeriod(listOf(fiveDaysOld), StatisticsPeriod.WEEK, now) { it })
    }

    @Test
    fun `the timestamp selector reads an arbitrary record type`() {
        data class Game(val whenPlayed: Long, val label: String)
        val games = listOf(Game(daysAgo(0), "recent"), Game(daysAgo(40), "old"))

        val kept = filterByPeriod(games, StatisticsPeriod.MONTH, now) { it.whenPlayed }

        assertEquals(listOf(Game(daysAgo(0), "recent")), kept)
    }
}
