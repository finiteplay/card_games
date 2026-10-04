package org.finiteplay.klondike.storage

import org.finiteplay.core.storage.preferencesDataStoreAt
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.klondike.board.DrawMode
import java.io.File

/**
 * Which side of the board stock/waste sit on (`docs/games/klondike/UI_SPEC.md` "Right-Handed
 * Layout"/"Left-Handed Layout"). The type itself is `core:ui`'s, since the mirror is a
 * platform-wide accessibility setting rather than anything about this game; re-exported here so
 * the settings this store persists all read from one package.
 */
typealias Handedness = org.finiteplay.core.ui.layout.Handedness

/**
 * Which difficulty the next draw-one deal is taken from (`docs/games/klondike/DEALS.md` "Difficulty
 * Grading (Interim)"). [RANDOM] picks one of the four levels afresh for each new game,
 * which is the closest thing to the old behaviour of simply dealing the next hand
 * regardless of how hard it is. Draw-three ignores this entirely: those deals are
 * random shuffles with no grading behind them at all.
 */
enum class DifficultyPreference { TRIVIAL, EASY, MEDIUM, HARD, EXPERT, INSANE, RANDOM }


/** The MVP settings (`docs/games/klondike/DESIGN.md`, "Interface"). */
data class Settings(
    val automaticMovesEnabled: Boolean,
    val animationsEnabled: Boolean,
    val hintShowsWinningMove: Boolean,
    val hintTimeout: HintTimeout,
    val restReminderInterval: RestReminderInterval,
    val handedness: Handedness,
    val soundEnabled: Boolean,
    val drawMode: DrawMode,
    val difficulty: DifficultyPreference,
    val languageTag: String,
    val themeMode: ThemeMode,
) {
    companion object {
        /** Automatic foundation moves, animations, and Hint's proven-winning-move mode all default on; sound defaults off; handedness and draw mode default to the common case; difficulty defaults to the gentlest level rather than Random, so a first-ever game is winnable on sight. */
        val DEFAULT = Settings(
            automaticMovesEnabled = true,
            animationsEnabled = true,
            hintShowsWinningMove = true,
            hintTimeout = HintTimeout.DEFAULT,
            restReminderInterval = RestReminderInterval.DEFAULT,
            handedness = Handedness.RIGHT,
            soundEnabled = false,
            drawMode = DrawMode.ONE,
            difficulty = DifficultyPreference.TRIVIAL,
            languageTag = SYSTEM_LANGUAGE,
            themeMode = ThemeMode.SYSTEM,
        )
    }
}

/**
 * Persists [Settings], applied immediately and surviving process restart. A missing or
 * corrupt store (the underlying file failed to parse) reads back as [Settings.DEFAULT];
 * see [preferencesDataStoreAt] for how corruption is contained to this file alone.
 */
class SettingsStore(
    directory: File,
    dataStoreFactory: (File, String) -> DataStore<Preferences> = ::preferencesDataStoreAt,
) {
    private val dataStore = dataStoreFactory(directory, STORE_NAME)

    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            automaticMovesEnabled = prefs[AUTOMATIC_MOVES_ENABLED] ?: Settings.DEFAULT.automaticMovesEnabled,
            animationsEnabled = prefs[ANIMATIONS_ENABLED] ?: Settings.DEFAULT.animationsEnabled,
            hintShowsWinningMove = prefs[HINT_SHOWS_WINNING_MOVE] ?: Settings.DEFAULT.hintShowsWinningMove,
            hintTimeout = prefs[HINT_TIMEOUT]?.let { stored -> HintTimeout.entries.find { it.name == stored } }
                ?: Settings.DEFAULT.hintTimeout,
            restReminderInterval = prefs[REST_REMINDER_INTERVAL]?.let { stored ->
                RestReminderInterval.entries.find { it.name == stored }
            } ?: Settings.DEFAULT.restReminderInterval,
            // An unrecognized stored name (a downgrade after a future enum entry was
            // added) falls back to the default rather than crashing on restore.
            handedness = prefs[HANDEDNESS]?.let { stored -> Handedness.entries.find { it.name == stored } }
                ?: Settings.DEFAULT.handedness,
            soundEnabled = prefs[SOUND_ENABLED] ?: Settings.DEFAULT.soundEnabled,
            drawMode = prefs[DRAW_MODE]?.let { stored -> DrawMode.entries.find { it.name == stored } }
                ?: Settings.DEFAULT.drawMode,
            difficulty = prefs[DIFFICULTY]?.let { stored -> DifficultyPreference.entries.find { it.name == stored } }
                ?: Settings.DEFAULT.difficulty,
            languageTag = prefs[LANGUAGE_TAG] ?: Settings.DEFAULT.languageTag,
            themeMode = prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.find { it.name == stored } }
                ?: Settings.DEFAULT.themeMode,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setAutomaticMovesEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTOMATIC_MOVES_ENABLED] = enabled }
    }

    suspend fun setAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { it[ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun setHintShowsWinningMove(enabled: Boolean) {
        dataStore.edit { it[HINT_SHOWS_WINNING_MOVE] = enabled }
    }

    suspend fun setHintTimeout(hintTimeout: HintTimeout) {
        dataStore.edit { it[HINT_TIMEOUT] = hintTimeout.name }
    }

    suspend fun setRestReminderInterval(restReminderInterval: RestReminderInterval) {
        dataStore.edit { it[REST_REMINDER_INTERVAL] = restReminderInterval.name }
    }

    suspend fun setHandedness(handedness: Handedness) {
        dataStore.edit { it[HANDEDNESS] = handedness.name }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[SOUND_ENABLED] = enabled }
    }

    /** Applies to the *next* new game only (`docs/games/klondike/DESIGN.md` "Draw-Three Mode") — draw mode is fixed once a game is dealt. */
    suspend fun setDrawMode(drawMode: DrawMode) {
        dataStore.edit { it[DRAW_MODE] = drawMode.name }
    }

    /** Applies to the *next* new game only, like draw mode: the deal in play was already taken from whichever level was selected when it was dealt. */
    suspend fun setDifficulty(difficulty: DifficultyPreference) {
        dataStore.edit { it[DIFFICULTY] = difficulty.name }
    }

    /** Applies immediately: the caller also hands [languageTag] to the framework, which recreates the activity in the new locale. */
    suspend fun setLanguageTag(languageTag: String) {
        dataStore.edit { it[LANGUAGE_TAG] = languageTag }
    }

    /** Applies immediately: the theme is read from composition, so the next frame already has it. */
    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = themeMode.name }
    }

    companion object {
        private const val STORE_NAME = "settings"
        private val AUTOMATIC_MOVES_ENABLED = booleanPreferencesKey("automatic_moves_enabled")
        private val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        private val HINT_SHOWS_WINNING_MOVE = booleanPreferencesKey("hint_shows_winning_move")
        private val HINT_TIMEOUT = stringPreferencesKey("hint_timeout")
        private val REST_REMINDER_INTERVAL = stringPreferencesKey("rest_reminder_interval")
        private val HANDEDNESS = stringPreferencesKey("handedness")
        private val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val DRAW_MODE = stringPreferencesKey("draw_mode")
        private val DIFFICULTY = stringPreferencesKey("difficulty")
        private val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
