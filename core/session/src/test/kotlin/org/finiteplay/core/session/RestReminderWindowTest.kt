package org.finiteplay.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val INTERVAL_MS = 60 * 60 * 1000L // one hour
private const val RESET_MS = REST_REMINDER_BACKGROUND_RESET_MS

class RestReminderWindowTest {

    @Test
    fun `due once foreground time reaches the interval`() {
        val window = RestReminderWindow.start(0L)
        assertTrue(isRestReminderDue(window, nowMs = INTERVAL_MS, intervalMs = INTERVAL_MS))
    }

    @Test
    fun `not due before foreground time reaches the interval`() {
        val window = RestReminderWindow.start(0L)
        assertFalse(isRestReminderDue(window, nowMs = INTERVAL_MS - 1, intervalMs = INTERVAL_MS))
    }

    @Test
    fun `background time is never counted`() {
        var window = RestReminderWindow.start(0L)
        window = window.foregroundExited(1_000L) // 1s foreground
        // backgrounded for 1 minute -- well under the reset threshold
        window = window.foregroundEntered(1_000L + 60_000L)
        // Only the foreground second counts; the backgrounded minute does not.
        assertEquals(1_000L, window.foregroundMsAsOf(1_000L + 60_000L))
    }

    @Test
    fun `a background stretch under the reset threshold does not clear the accumulated total`() {
        var window = RestReminderWindow.start(0L)
        window = window.foregroundExited(30_000L) // 30s foreground
        window = window.foregroundEntered(30_000L + (RESET_MS - 1)) // backgrounded just under the threshold
        assertEquals(30_000L, window.foregroundMsAsOf(30_000L + (RESET_MS - 1)))
    }

    @Test
    fun `a background stretch at or past the reset threshold clears the accumulated total`() {
        var window = RestReminderWindow.start(0L)
        window = window.foregroundExited(30_000L) // 30s foreground
        val reentryAt = 30_000L + RESET_MS
        window = window.foregroundEntered(reentryAt)
        assertEquals(0L, window.foregroundMsAsOf(reentryAt))
    }

    @Test
    fun `foreground time across several short gaps still accumulates toward the interval`() {
        var window = RestReminderWindow.start(0L)
        var now = 0L
        repeat(10) {
            now += 5 * 60_000L // 5 minutes foreground
            window = window.foregroundExited(now)
            now += 60_000L // 1 minute backgrounded -- under the reset threshold
            window = window.foregroundEntered(now)
        }
        // 10 * 5 minutes = 50 minutes foreground, no single gap long enough to reset.
        assertEquals(50 * 60_000L, window.foregroundMsAsOf(now))
        assertFalse(isRestReminderDue(window, now, INTERVAL_MS))
    }

    @Test
    fun `reentering foreground while already open is a no-op`() {
        val window = RestReminderWindow.start(0L)
        val reentered = window.foregroundEntered(INTERVAL_MS / 2)
        assertEquals(window, reentered)
    }

    @Test
    fun `exiting foreground while already closed is a no-op`() {
        var window = RestReminderWindow.start(0L)
        window = window.foregroundExited(INTERVAL_MS / 2)
        val exitedAgain = window.foregroundExited(INTERVAL_MS)
        assertEquals(window, exitedAgain)
    }

    @Test
    fun `changing the interval keeps the time played when it is still under the new interval`() {
        val window = RestReminderWindow.start(0L)
        val after = window.afterIntervalChange(nowMs = 20 * 60_000L, newIntervalMs = 30 * 60_000L)
        assertEquals(20 * 60_000L, after.foregroundMsAsOf(20 * 60_000L))
    }

    @Test
    fun `changing the interval starts over when the time played already reaches the new interval`() {
        val window = RestReminderWindow.start(0L)
        val now = 30 * 60_000L
        val after = window.afterIntervalChange(nowMs = now, newIntervalMs = 30 * 60_000L)
        // Keeping it would make the reminder due the instant the setting changed.
        assertEquals(0L, after.foregroundMsAsOf(now))
        assertFalse(isRestReminderDue(after, now, 30 * 60_000L))
    }

    @Test
    fun `changing to never keeps the time played, so a later interval can count from it`() {
        val window = RestReminderWindow.start(0L)
        val after = window.afterIntervalChange(nowMs = 45 * 60_000L, newIntervalMs = null)
        assertEquals(45 * 60_000L, after.foregroundMsAsOf(45 * 60_000L))
    }

    @Test
    fun `a kept window keeps its background bookkeeping`() {
        var window = RestReminderWindow.start(0L)
        window = window.foregroundExited(10 * 60_000L)
        val after = window.afterIntervalChange(nowMs = 12 * 60_000L, newIntervalMs = 30 * 60_000L)
        assertEquals(window, after)
    }
}
