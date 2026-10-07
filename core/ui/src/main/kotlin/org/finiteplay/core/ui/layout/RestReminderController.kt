package org.finiteplay.core.ui.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.finiteplay.core.session.RestReminderWindow
import org.finiteplay.core.session.afterIntervalChange
import org.finiteplay.core.session.foregroundEntered
import org.finiteplay.core.session.foregroundExited
import org.finiteplay.core.session.foregroundMsAsOf
import org.finiteplay.core.session.isRestReminderDue

/**
 * The rest reminder's moving parts for a screen's view model (`docs/PLATFORM.md` "Rest Reminders"):
 * the window of foreground time, the once-a-second ticker that offers a break when it is due, and the
 * countdown of an accepted break. The interval itself is a setting the caller owns; [intervalMinutes]
 * reads it (null is never) and [intervalChanged] says it moved.
 *
 * A break blocks the board and changes nothing about the game in play: the caller's state is saved
 * before it is shown and nothing about it is timed.
 */
class RestReminderController(
    private val scope: CoroutineScope,
    private val intervalMinutes: () -> Int?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var window: RestReminderWindow? = null
    private var tickerJob: Job? = null
    private var breakJob: Job? = null

    var isForeground = true
        private set

    /** True while the reminder's offer is showing. */
    var showReminderDialog by mutableStateOf(false)
        private set

    /** Seconds left in an accepted break, or null when none is running. */
    var breakRemainingSeconds by mutableStateOf<Int?>(null)
        private set

    /** The current window's running total, for Settings' read-only display. */
    var elapsedSeconds by mutableIntStateOf(0)
        private set

    /** Applies a new interval at once: a fresh window if the time played already reaches it. */
    fun intervalChanged(minutes: Int?) {
        val at = now()
        window = (window ?: RestReminderWindow.start(at)).afterIntervalChange(at, minutes?.let { it * 60_000L })
        refreshElapsed()
        syncTicker()
    }

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
        val at = now()
        window = if (foreground) {
            (window ?: RestReminderWindow.start(at)).foregroundEntered(at)
        } else {
            window?.foregroundExited(at)
        }
        refreshElapsed()
        syncTicker()
    }

    /** Accepts the offer: a countdown for half the interval, during which no usage is tracked. */
    fun startBreak() {
        showReminderDialog = false
        val minutes = intervalMinutes() ?: return
        breakRemainingSeconds = (minutes * 60) / 2
        syncTicker()
        breakJob = scope.launch {
            while ((breakRemainingSeconds ?: 0) > 0) {
                delay(1_000)
                breakRemainingSeconds = (breakRemainingSeconds ?: 1) - 1
            }
            endBreak()
        }
    }

    /** Declines the offer; the next is a full interval away, since the window already rolled over. */
    fun dismissReminder() {
        showReminderDialog = false
    }

    /** Ends an accepted break early. */
    fun cancelBreak() {
        breakJob?.cancel()
        endBreak()
    }

    private fun endBreak() {
        breakJob = null
        breakRemainingSeconds = null
        // The break is excluded from usage entirely, so a fresh window starts rather than resuming the old one.
        window = RestReminderWindow.start(now())
        refreshElapsed()
        syncTicker()
    }

    private fun syncTicker() {
        val shouldRun = isForeground && breakRemainingSeconds == null
        if (shouldRun) {
            if (tickerJob?.isActive == true) return
            tickerJob = scope.launch {
                while (isActive) {
                    delay(1_000)
                    refreshElapsed()
                    checkDue()
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    private fun refreshElapsed() {
        val current = window
        elapsedSeconds = if (current == null) 0 else (current.foregroundMsAsOf(now()) / 1_000L).toInt()
    }

    private fun checkDue() {
        val minutes = intervalMinutes() ?: return
        val current = window ?: return
        val at = now()
        if (!isRestReminderDue(current, at, minutes * 60_000L)) return
        showReminderDialog = true
        window = RestReminderWindow.start(at)
        refreshElapsed()
    }
}
