package org.finiteplay.blackjack.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.theme.ThemeMode
import java.io.File

/**
 * Blackjack's settings (`docs/games/blackjack/UI_SPEC.md` "Settings"). No automatic-moves setting:
 * the dealer's play is the house's own turn, not automation (`DESIGN.md` "What Blackjack is not").
 */
data class BlackjackSettings(
    val animationsEnabled: Boolean,
    val handedness: Handedness,
    val soundEnabled: Boolean,
    val languageTag: String,
    val themeMode: ThemeMode,
    val restReminderInterval: RestReminderInterval,
) {
    companion object {
        val DEFAULT = BlackjackSettings(
            animationsEnabled = true,
            handedness = Handedness.RIGHT,
            soundEnabled = false,
            languageTag = SYSTEM_LANGUAGE,
            themeMode = ThemeMode.SYSTEM,
            restReminderInterval = RestReminderInterval.DEFAULT,
        )
    }
}

/** Persists [BlackjackSettings], applied immediately; a missing or corrupt store reads as [BlackjackSettings.DEFAULT]. */
class BlackjackSettingsStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    val settings: Flow<BlackjackSettings> = dataStore.data.map { prefs ->
        BlackjackSettings(
            animationsEnabled = prefs[ANIMATIONS_ENABLED] ?: BlackjackSettings.DEFAULT.animationsEnabled,
            handedness = prefs[HANDEDNESS]?.let { stored -> Handedness.entries.find { it.name == stored } }
                ?: BlackjackSettings.DEFAULT.handedness,
            soundEnabled = prefs[SOUND_ENABLED] ?: BlackjackSettings.DEFAULT.soundEnabled,
            languageTag = prefs[LANGUAGE_TAG] ?: BlackjackSettings.DEFAULT.languageTag,
            themeMode = prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.find { it.name == stored } }
                ?: BlackjackSettings.DEFAULT.themeMode,
            restReminderInterval = prefs[REST_REMINDER_INTERVAL]?.let { stored ->
                RestReminderInterval.entries.find { it.name == stored }
            } ?: BlackjackSettings.DEFAULT.restReminderInterval,
        )
    }

    suspend fun current(): BlackjackSettings = settings.first()

    suspend fun setAnimationsEnabled(value: Boolean) = edit { it[ANIMATIONS_ENABLED] = value }
    suspend fun setHandedness(value: Handedness) = edit { it[HANDEDNESS] = value.name }
    suspend fun setSoundEnabled(value: Boolean) = edit { it[SOUND_ENABLED] = value }
    suspend fun setLanguageTag(value: String) = edit { it[LANGUAGE_TAG] = value }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[THEME_MODE] = value.name }
    suspend fun setRestReminderInterval(value: RestReminderInterval) = edit { it[REST_REMINDER_INTERVAL] = value.name }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private companion object {
        const val STORE_NAME = "settings"

        // On disk on every installed device: renaming one silently resets that setting.
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val HANDEDNESS = stringPreferencesKey("handedness")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REST_REMINDER_INTERVAL = stringPreferencesKey("rest_reminder_interval")
    }
}
