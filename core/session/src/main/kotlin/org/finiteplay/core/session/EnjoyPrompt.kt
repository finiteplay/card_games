package org.finiteplay.core.session

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** App starts a player has made before they are asked whether they enjoy the game (`docs/PLATFORM.md` "Feedback and rating"). */
const val ENJOY_MIN_SESSIONS = 10

/** Separate calendar days on which the game was played (opened) before the question may be asked. */
const val ENJOY_MIN_DAYS = 3

/** Good results (a solitaire won, a Blackjack round won, a poker tournament finished first or second) before the question. */
const val ENJOY_MIN_GOOD_RESULTS = 3

/** A start less than this long after the last one is the same sitting, not a new session: leaving for a message and coming back. */
val SESSION_GAP_MILLIS: Long = TimeUnit.MINUTES.toMillis(30)

/** After the question was shown and left unanswered, how long before it is shown again. */
const val ENJOY_RETRY_DAYS = 30

/**
 * What the "enjoying this game?" question is decided from: how often and on how many separate days the game has been
 * played, how much of it went well, and whether the question was already put. Only counts and dates: nothing the
 * player did in a game, and nothing that leaves the device.
 */
data class Engagement(
    val sessions: Int = 0,
    val lastStartMillis: Long? = null,
    /** Separate calendar days (in the player's time zone) on which the game was started. */
    val activeDays: Int = 0,
    /** The last of those days, as an epoch day number. */
    val lastActiveDay: Long? = null,
    val goodResults: Int = 0,
    val lastAskedMillis: Long? = null,
    val answered: Boolean = false,
) {
    /**
     * The record after the app starts at [now]: a new session, unless it is the same sitting as the last start, and a
     * new active day, unless the day is one already counted. A clock set back never adds a day: only a later day counts.
     */
    fun afterStart(now: Long, zone: ZoneId = ZoneId.systemDefault()): Engagement {
        val sameSitting = lastStartMillis != null && now - lastStartMillis in 0 until SESSION_GAP_MILLIS
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        val newDay = lastActiveDay == null || day > lastActiveDay
        return copy(
            lastStartMillis = now,
            sessions = if (sameSitting) sessions else sessions + 1,
            activeDays = if (newDay) activeDays + 1 else activeDays,
            lastActiveDay = if (newDay) day else lastActiveDay,
        )
    }

    fun afterGoodResult(): Engagement = copy(goodResults = goodResults + 1)

    /** The question was put at [now]: not asked again for [ENJOY_RETRY_DAYS], unless it was answered, which is for good. */
    fun afterAsked(now: Long): Engagement = copy(lastAskedMillis = now)

    fun afterAnswered(): Engagement = copy(answered = true)
}

/**
 * Whether to ask now, at the end of a game or tournament that went well. All of: at least [ENJOY_MIN_SESSIONS]
 * sessions, played on at least [ENJOY_MIN_DAYS] separate days, at least [ENJOY_MIN_GOOD_RESULTS] good results, never
 * answered, and not asked within [ENJOY_RETRY_DAYS]. The caller decides what a game's end that went well is and calls
 * this only then, with the result just reached already counted.
 */
fun shouldAskEnjoy(engagement: Engagement, now: Long): Boolean {
    if (engagement.answered) return false
    if (engagement.sessions < ENJOY_MIN_SESSIONS) return false
    if (engagement.activeDays < ENJOY_MIN_DAYS) return false
    if (engagement.goodResults < ENJOY_MIN_GOOD_RESULTS) return false
    val asked = engagement.lastAskedMillis
    return asked == null || now - asked >= TimeUnit.DAYS.toMillis(ENJOY_RETRY_DAYS.toLong())
}

/** Whether a poker tournament finish earns a good result: first or second place. */
fun isPodiumFinish(place: Int): Boolean = place in 1..2
