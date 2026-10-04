package org.finiteplay.spider.storage

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
import org.finiteplay.spider.layout.SuitCount
import java.io.File

/**
 * Spider's settings (`docs/games/spider/EXECUTION_PLAN.md` S3c).
 *
 * Shorter than Klondike's by exactly the settings Spider has no concept of: no automatic moves, no
 * draw mode (`RULES.md` "What Spider does not have"). [nextSuitCount] is the one addition, and
 * changing it here — through Settings — only takes effect on the next New Game, the same as
 * Klondike's own draw mode: the count is fixed the moment a deal is made, so applying it to the
 * board already on screen would either lie or silently redeal. The status row's own suit-count
 * picker is the deliberate exception (`SpiderViewModel.setSuitCount`): reached specifically to
 * switch what's being played right now, it redeals immediately, the same "picking is playing a
 * different hand" call Klondike's difficulty picker makes.
 */
data class SpiderSettings(
    val animationsEnabled: Boolean,
    val hintShowsWinningMove: Boolean,
    val hintTimeout: HintTimeout,
    val restReminderInterval: RestReminderInterval,
    val handedness: Handedness,
    val soundEnabled: Boolean,
    val languageTag: String,
    val themeMode: ThemeMode,
    val nextSuitCount: SuitCount,
) {
    companion object {
        val DEFAULT = SpiderSettings(
            animationsEnabled = true,
            hintShowsWinningMove = true,
            hintTimeout = HintTimeout.DEFAULT,
            restReminderInterval = RestReminderInterval.DEFAULT,
            handedness = Handedness.RIGHT,
            soundEnabled = false,
            languageTag = SYSTEM_LANGUAGE,
            themeMode = ThemeMode.SYSTEM,
            // One suit: it is the count a player can actually win on first contact, and the
            // certified catalog guarantees every deal it hands out is solvable — two and four
            // are worth returning to once Spider itself has landed, not before.
            nextSuitCount = SuitCount.ONE,
        )
    }
}

/**
 * Persists [SpiderSettings], applied immediately and surviving process restart. A missing or
 * corrupt store reads back as [SpiderSettings.DEFAULT]; see [preferencesDataStoreAt] for how
 * corruption is contained to this file alone.
 *
 * This is a separate class from Klondike's rather than a shared one on purpose
 * (`docs/ARCHITECTURE.md`): the settings themselves barely overlap, and the type that would force
 * sharing — [ThemeMode] — lives in `core:ui`, which `core:storage` must not depend on.
 */
class SpiderSettingsStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    val settings: Flow<SpiderSettings> = dataStore.data.map { prefs ->
        SpiderSettings(
            animationsEnabled = prefs[ANIMATIONS_ENABLED] ?: SpiderSettings.DEFAULT.animationsEnabled,
            hintShowsWinningMove = prefs[HINT_SHOWS_WINNING_MOVE] ?: SpiderSettings.DEFAULT.hintShowsWinningMove,
            hintTimeout = prefs[HINT_TIMEOUT]?.let { stored -> HintTimeout.entries.find { it.name == stored } }
                ?: SpiderSettings.DEFAULT.hintTimeout,
            restReminderInterval = prefs[REST_REMINDER_INTERVAL]?.let { stored ->
                RestReminderInterval.entries.find { it.name == stored }
            } ?: SpiderSettings.DEFAULT.restReminderInterval,
            // An unrecognised stored name — a downgrade past an entry a later build added —
            // falls back to the default rather than failing the whole read.
            handedness = prefs[HANDEDNESS]?.let { stored -> Handedness.entries.find { it.name == stored } }
                ?: SpiderSettings.DEFAULT.handedness,
            soundEnabled = prefs[SOUND_ENABLED] ?: SpiderSettings.DEFAULT.soundEnabled,
            languageTag = prefs[LANGUAGE_TAG] ?: SpiderSettings.DEFAULT.languageTag,
            themeMode = prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.find { it.name == stored } }
                ?: SpiderSettings.DEFAULT.themeMode,
            nextSuitCount = prefs[NEXT_SUIT_COUNT]?.let { stored -> SuitCount.entries.find { it.name == stored } }
                ?: SpiderSettings.DEFAULT.nextSuitCount,
        )
    }

    suspend fun current(): SpiderSettings = settings.first()

    suspend fun setAnimationsEnabled(value: Boolean) = edit { it[ANIMATIONS_ENABLED] = value }
    suspend fun setHintShowsWinningMove(value: Boolean) = edit { it[HINT_SHOWS_WINNING_MOVE] = value }
    suspend fun setHintTimeout(value: HintTimeout) = edit { it[HINT_TIMEOUT] = value.name }
    suspend fun setRestReminderInterval(value: RestReminderInterval) = edit { it[REST_REMINDER_INTERVAL] = value.name }
    suspend fun setHandedness(value: Handedness) = edit { it[HANDEDNESS] = value.name }
    suspend fun setSoundEnabled(value: Boolean) = edit { it[SOUND_ENABLED] = value }
    suspend fun setLanguageTag(value: String) = edit { it[LANGUAGE_TAG] = value }
    suspend fun setThemeMode(value: ThemeMode) = edit { it[THEME_MODE] = value.name }
    suspend fun setNextSuitCount(value: SuitCount) = edit { it[NEXT_SUIT_COUNT] = value.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private companion object {
        const val STORE_NAME = "settings"

        // On disk on every installed device: renaming one silently resets that setting.
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val HINT_SHOWS_WINNING_MOVE = booleanPreferencesKey("hint_shows_winning_move")
        val HINT_TIMEOUT = stringPreferencesKey("hint_timeout")
        val REST_REMINDER_INTERVAL = stringPreferencesKey("rest_reminder_interval")
        val HANDEDNESS = stringPreferencesKey("handedness")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NEXT_SUIT_COUNT = stringPreferencesKey("next_suit_count")
    }
}
