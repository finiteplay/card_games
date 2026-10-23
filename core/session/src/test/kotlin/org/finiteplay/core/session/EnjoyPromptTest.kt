package org.finiteplay.core.session

import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnjoyPromptTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val utc = ZoneOffset.UTC
    private val start = 20_000L * day + TimeUnit.HOURS.toMillis(9)
    private val now = start + 10 * day

    /** A player ready to be asked: ten sessions on three separate days, three good results, never asked. */
    private fun ready() = Engagement(
        sessions = ENJOY_MIN_SESSIONS,
        lastStartMillis = start,
        activeDays = ENJOY_MIN_DAYS,
        lastActiveDay = 20_000L,
        goodResults = ENJOY_MIN_GOOD_RESULTS,
    )

    @Test
    fun `a player with enough sessions, separate days and good results is asked`() {
        assertTrue(shouldAskEnjoy(ready(), now))
    }

    @Test
    fun `nobody is asked before ten sessions`() {
        assertFalse(shouldAskEnjoy(ready().copy(sessions = ENJOY_MIN_SESSIONS - 1), now))
    }

    @Test
    fun `nobody is asked before they have played on three separate days`() {
        assertFalse(shouldAskEnjoy(ready().copy(activeDays = ENJOY_MIN_DAYS - 1), now))
    }

    @Test
    fun `nobody is asked before three good results`() {
        assertFalse(shouldAskEnjoy(ready().copy(goodResults = ENJOY_MIN_GOOD_RESULTS - 1), now))
    }

    @Test
    fun `a player who has never started is not asked`() {
        assertFalse(shouldAskEnjoy(Engagement(goodResults = 20), now))
    }

    @Test
    fun `a player who answered is never asked again`() {
        assertFalse(shouldAskEnjoy(ready().afterAnswered(), now + 1000 * day))
    }

    @Test
    fun `a question left unanswered is asked again after thirty days, not before`() {
        val asked = ready().afterAsked(now)
        assertFalse(shouldAskEnjoy(asked, now + day))
        assertFalse(shouldAskEnjoy(asked, now + ENJOY_RETRY_DAYS * day - 1))
        assertTrue(shouldAskEnjoy(asked, now + ENJOY_RETRY_DAYS * day))
    }

    @Test
    fun `the first start counts as a session and as a day`() {
        val e = Engagement().afterStart(start, utc)
        assertEquals(1, e.sessions)
        assertEquals(1, e.activeDays)
        assertEquals(20_000L, e.lastActiveDay)
    }

    @Test
    fun `a start soon after the last one is the same sitting and the same day`() {
        val e = Engagement().afterStart(start, utc).afterStart(start + SESSION_GAP_MILLIS - 1, utc)
        assertEquals(1, e.sessions)
        assertEquals(1, e.activeDays)
    }

    @Test
    fun `a start after the gap is a new session but on the same day not a new day`() {
        val e = Engagement().afterStart(start, utc).afterStart(start + SESSION_GAP_MILLIS, utc)
        assertEquals(2, e.sessions)
        assertEquals(1, e.activeDays)
    }

    @Test
    fun `starts on three separate days are three days however many sessions each day has`() {
        var e = Engagement()
        for (offset in listOf(0L, 1L, 2L)) {
            e = e.afterStart(start + offset * day, utc)
                .afterStart(start + offset * day + 2 * SESSION_GAP_MILLIS, utc)
        }
        assertEquals(3, e.activeDays)
        assertEquals(6, e.sessions)
    }

    @Test
    fun `late evening and early morning are two days, not one`() {
        val lateEvening = 20_000L * day + TimeUnit.HOURS.toMillis(23) + TimeUnit.MINUTES.toMillis(50)
        val e = Engagement().afterStart(lateEvening, utc)
            .afterStart(lateEvening + TimeUnit.MINUTES.toMillis(40), utc)
        assertEquals(2, e.activeDays)
    }

    @Test
    fun `the day follows the time zone of the player`() {
        val instant = 20_000L * day + TimeUnit.HOURS.toMillis(23)
        assertEquals(20_001L, Engagement().afterStart(instant, ZoneOffset.ofHours(2)).lastActiveDay)
        assertEquals(20_000L, Engagement().afterStart(instant, utc).lastActiveDay)
    }

    @Test
    fun `a clock set back never adds a day`() {
        val e = Engagement().afterStart(start, utc).afterStart(start - 5 * day, utc)
        assertEquals(2, e.sessions)
        assertEquals(1, e.activeDays)
        assertEquals(20_000L, e.lastActiveDay)
    }

    @Test
    fun `only first and second place in a tournament are good results`() {
        assertEquals(listOf(true, true, false, false, false, false), (1..6).map(::isPodiumFinish))
    }
}
