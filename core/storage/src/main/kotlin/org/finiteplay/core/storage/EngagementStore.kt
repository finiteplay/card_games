package org.finiteplay.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.finiteplay.core.session.Engagement
import java.io.File

/**
 * The counts and dates the "enjoying this game?" question is decided from (`docs/PLATFORM.md` "Feedback and
 * rating"): how many sessions, on how many separate days, how many good results, and whether and when the
 * question was put. Nothing about a game in progress, nothing that leaves the device.
 *
 * Its own file, so a corrupt one can only cost the question its history: it then reads back as a new player.
 */
class EngagementStore(
    directory: File,
    name: String = DEFAULT_NAME,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, name)

    val engagement: Flow<Engagement> = dataStore.data.map(::read)

    suspend fun current(): Engagement = engagement.first()

    /** The app was started at [now]: counted as a session unless it is the same sitting as the last start. */
    suspend fun recordStart(now: Long) = update { it.afterStart(now) }

    /** A solitaire won, a Blackjack round won, or a poker tournament finished first or second. */
    suspend fun recordGoodResult() = update { it.afterGoodResult() }

    /** The question was put at [now]. */
    suspend fun markAsked(now: Long) = update { it.afterAsked(now) }

    /** The player answered, yes or no: the question is not put again. */
    suspend fun markAnswered() = update { it.afterAnswered() }

    /** Applies [change] to what is stored and returns the result, in one atomic edit. */
    private suspend fun update(change: (Engagement) -> Engagement): Engagement {
        var updated = Engagement()
        dataStore.edit { prefs ->
            updated = change(read(prefs))
            write(prefs, updated)
        }
        return updated
    }

    private fun read(prefs: Preferences) = Engagement(
        sessions = (prefs[SESSIONS] ?: 0).coerceAtLeast(0),
        lastStartMillis = prefs[LAST_START],
        activeDays = (prefs[ACTIVE_DAYS] ?: 0).coerceAtLeast(0),
        lastActiveDay = prefs[LAST_ACTIVE_DAY],
        goodResults = (prefs[GOOD_RESULTS] ?: 0).coerceAtLeast(0),
        lastAskedMillis = prefs[LAST_ASKED],
        answered = prefs[ANSWERED] ?: false,
    )

    private fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, value: Engagement) {
        prefs[SESSIONS] = value.sessions
        value.lastStartMillis?.let { prefs[LAST_START] = it }
        prefs[ACTIVE_DAYS] = value.activeDays
        value.lastActiveDay?.let { prefs[LAST_ACTIVE_DAY] = it }
        prefs[GOOD_RESULTS] = value.goodResults
        value.lastAskedMillis?.let { prefs[LAST_ASKED] = it }
        prefs[ANSWERED] = value.answered
    }

    companion object {
        const val DEFAULT_NAME = "engagement"
        private val SESSIONS = intPreferencesKey("sessions")
        private val LAST_START = longPreferencesKey("last_start_millis")
        private val ACTIVE_DAYS = intPreferencesKey("active_days")
        private val LAST_ACTIVE_DAY = longPreferencesKey("last_active_day")
        private val GOOD_RESULTS = intPreferencesKey("good_results")
        private val LAST_ASKED = longPreferencesKey("last_asked_millis")
        private val ANSWERED = booleanPreferencesKey("answered")
    }
}
