package org.finiteplay.core.storage

import java.util.concurrent.TimeUnit
import org.finiteplay.core.session.ENJOY_MIN_SESSIONS

/**
 * A player who has used the game for a while: [ENJOY_MIN_SESSIONS] sessions, each half a day after the last, so they fall on
 * several separate days whatever the time zone, then [goodResults] good results. Returns a time just after the last start,
 * for a test's clock. A test passes one good result fewer than the question needs to be the one that makes it due.
 */
suspend fun EngagementStore.seedVeteran(firstStartMillis: Long, goodResults: Int): Long {
    var at = firstStartMillis
    repeat(ENJOY_MIN_SESSIONS) {
        recordStart(at)
        at += TimeUnit.HOURS.toMillis(12)
    }
    repeat(goodResults) { recordGoodResult() }
    return at
}
