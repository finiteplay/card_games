package org.finiteplay.core.session

/**
 * How long a game must sit backgrounded before [RestReminderWindow.foregroundEntered] treats the
 * play before it as over rather than merely interrupted (`docs/PLATFORM.md` "Rest Reminders"). A
 * short interruption — answering a notification, switching apps for a few seconds — does not
 * reset anything; this is the line past which it does.
 */
const val REST_REMINDER_BACKGROUND_RESET_MS = 5 * 60 * 1000L

/**
 * Accumulated foreground time toward a rest reminder, used to decide when continuous play has run
 * long enough to offer one (`docs/PLATFORM.md` "Rest Reminders"). Background time is never added
 * to the total — only an open foreground segment counts — but a short background stretch does not
 * erase what was already accumulated either: only one lasting at least
 * [REST_REMINDER_BACKGROUND_RESET_MS] does, on the theory that a brief interruption is still the
 * same sitting while a longer one is a new one.
 *
 * Deliberately immutable and timestamp-driven rather than a ticking clock of its own, matching
 * [shouldRunTimer]'s own policy-over-scheduling style: the caller re-evaluates on every input that
 * could change the picture (a foreground/background transition, a periodic check while
 * foreground) and nothing here schedules anything.
 */
data class RestReminderWindow(
    val foregroundMs: Long,
    val openSegmentStartMs: Long?,
    val backgroundSinceMs: Long?,
) {
    companion object {
        /** A fresh window starting right now, already foreground (that is what starting means). */
        fun start(nowMs: Long): RestReminderWindow = RestReminderWindow(0L, nowMs, null)
    }
}

/**
 * Opens a new foreground segment. A no-op if one is already open. Resets the accumulated total to
 * zero first when the background stretch just ending reached [resetAfterMs] — a player who steps
 * away for five minutes or more is starting a new sitting, not continuing the old one.
 */
fun RestReminderWindow.foregroundEntered(
    nowMs: Long,
    resetAfterMs: Long = REST_REMINDER_BACKGROUND_RESET_MS,
): RestReminderWindow {
    if (openSegmentStartMs != null) return this
    val backgroundedFor = backgroundSinceMs?.let { nowMs - it } ?: 0L
    return RestReminderWindow(
        foregroundMs = if (backgroundedFor >= resetAfterMs) 0L else foregroundMs,
        openSegmentStartMs = nowMs,
        backgroundSinceMs = null,
    )
}

/** Folds the currently-open segment into the accumulated total and records when it closed, for [foregroundEntered]'s own check next time. A no-op if none is open. */
fun RestReminderWindow.foregroundExited(nowMs: Long): RestReminderWindow {
    val openedAt = openSegmentStartMs ?: return this
    return copy(foregroundMs = foregroundMs + (nowMs - openedAt), openSegmentStartMs = null, backgroundSinceMs = nowMs)
}

/** Foreground time accumulated so far, including whatever segment is still open at [nowMs]. */
fun RestReminderWindow.foregroundMsAsOf(nowMs: Long): Long =
    foregroundMs + (openSegmentStartMs?.let { nowMs - it } ?: 0L)

/**
 * The window after the player picks a new reminder interval ([newIntervalMs], or null for never).
 * Time already played toward the reminder is kept while it is still under the new interval — a
 * player who has played twenty minutes and moves a thirty-minute reminder to forty is not made to
 * start over. Once the time played already reaches the new interval, keeping it would make the
 * reminder due the instant the setting changed, so the window starts fresh from [nowMs] instead.
 * Choosing never keeps the time played, so choosing an interval again later counts from it.
 */
fun RestReminderWindow.afterIntervalChange(nowMs: Long, newIntervalMs: Long?): RestReminderWindow =
    if (newIntervalMs == null || foregroundMsAsOf(nowMs) < newIntervalMs) this else RestReminderWindow.start(nowMs)

/** Whether [window] has accumulated at least [intervalMs] of foreground time as of [nowMs]. */
fun isRestReminderDue(window: RestReminderWindow, nowMs: Long, intervalMs: Long): Boolean =
    window.foregroundMsAsOf(nowMs) >= intervalMs
