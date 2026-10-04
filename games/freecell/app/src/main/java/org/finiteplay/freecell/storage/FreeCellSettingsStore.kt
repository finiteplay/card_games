package org.finiteplay.freecell.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.theme.ThemeMode
import java.io.File

/**
 * FreeCell's settings (`docs/games/freecell/EXECUTION_PLAN.md` F3b).
 *
 * [automaticMovesEnabled] is the one thing Spider's own settings have no equivalent of — FreeCell
 * has an automatic foundation cascade the way Klondike does (`RULES.md` "Automatic Foundation
 * Moves") — and there is no draw mode, no difficulty, and no suit count the way both other games
 * have some version of: FreeCell has none of those axes at all
 * (`RULES.md` "What FreeCell does not have").
 */
data class FreeCellSettings(
    val automaticMovesEnabled: Boolean,
    val animationsEnabled: Boolean,
    val hintShowsWinningMove: Boolean,
    val hintTimeout: HintTimeout,
    val restReminderInterval: RestReminderInterval,
    val handedness: Handedness,
    val soundEnabled: Boolean,
    val languageTag: String,
    val themeMode: ThemeMode,
) {
    companion object {
        val DEFAULT = FreeCellSettings(
            automaticMovesEnabled = true,
            animationsEnabled = true,
            hintShowsWinningMove = true,
            hintTimeout = HintTimeout.DEFAULT,
            restReminderInterval = RestReminderInterval.DEFAULT,
            handedness = Handedness.RIGHT,
            soundEnabled = false,
            languageTag = SYSTEM_LANGUAGE,
            themeMode = ThemeMode.SYSTEM,
        )
    }
}

/**
 * Persists [FreeCellSettings], applied immediately and surviving process restart. A missing or
 * corrupt store reads back as [FreeCellSettings.DEFAULT]; see [preferencesDataStoreAt] for how
 * corruption is contained to this file alone.
 */
class FreeCellSettingsStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    val settings: Flow<FreeCellSettings> = dataStore.data.map { prefs ->
        FreeCellSettings(
            automaticMovesEnabled = prefs[AUTOMATIC_MOVES_ENABLED] ?: FreeCellSettings.DEFAULT.automaticMovesEnabled,
            animationsEnabled = prefs[ANIMATIONS_ENABLED] ?: FreeCellSettings.DEFAULT.animationsEnabled,
            hintShowsWinningMove = prefs[HINT_SHOWS_WINNING_MOVE] ?: FreeCellSettings.DEFAULT.hintShowsWinningMove,
            hintTimeout = prefs[HINT_TIMEOUT]?.let { stored -> HintTimeout.entries.find { it.name == stored } }
                ?: FreeCellSettings.DEFAULT.hintTimeout,
            restReminderInterval = prefs[REST_REMINDER_INTERVAL]?.let { stored ->
                RestReminderInterval.entries.find { it.name == stored }
            } ?: FreeCellSettings.DEFAULT.restReminderInterval,
            // An unrecognised stored name — a downgrade past an entry a later build added —
            // falls back to the default rather than failing the whole read.
            handedness = prefs[HANDEDNESS]?.let { stored -> Handedness.entries.find { it.name == stored } }
                ?: FreeCellSettings.DEFAULT.handedness,
            soundEnabled = prefs[SOUND_ENABLED] ?: FreeCellSettings.DEFAULT.soundEnabled,
            languageTag = prefs[LANGUAGE_TAG] ?: FreeCellSettings.DEFAULT.languageTag,
            themeMode = prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.find { it.name == stored } }
                ?: FreeCellSettings.DEFAULT.themeMode,
        )
    }

    suspend fun current(): FreeCellSettings = settings.first()

    suspend fun setAutomaticMovesEnabled(value: Boolean) = edit { it[AUTOMATIC_MOVES_ENABLED] = value }
    suspend fun setAnimationsEnabled(value: Boolean) = edit { it[ANIMATIONS_ENABLED] = value }
    suspend fun setHintShowsWinningMove(value: Boolean) = edit { it[HINT_SHOWS_WINNING_MOVE] = value }
    suspend fun setHintTimeout(value: HintTimeout) = edit { it[HINT_TIMEOUT] = value.name }
    suspend fun setRestReminderInterval(value: RestReminderInterval) = edit { it[REST_REMINDER_INTERVAL] = value.name }
    suspend fun setHandedness(value: Handedness) = edit { it[HANDEDNESS] = value.name }
    suspend fun setSoundEnabled(value: Boolean) = edit { it[SOUND_ENABLED] = value }
    suspend fun setLanguageTag(value: String) = edit { it[LANGUAGE_TAG] = value }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[THEME_MODE] = value.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private companion object {
        const val STORE_NAME = "settings"

        // On disk on every installed device: renaming one silently resets that setting.
        val AUTOMATIC_MOVES_ENABLED = booleanPreferencesKey("automatic_moves_enabled")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val HINT_SHOWS_WINNING_MOVE = booleanPreferencesKey("hint_shows_winning_move")
        val HINT_TIMEOUT = stringPreferencesKey("hint_timeout")
        val REST_REMINDER_INTERVAL = stringPreferencesKey("rest_reminder_interval")
        val HANDEDNESS = stringPreferencesKey("handedness")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
