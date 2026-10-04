package org.finiteplay.klondike.ui.game

import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.DropdownSettingRow
import org.finiteplay.core.ui.layout.InfoSettingRow
import org.finiteplay.core.ui.layout.SettingsGroup
import org.finiteplay.core.ui.layout.SwitchSettingRow
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.layout.label
import org.finiteplay.core.ui.layout.AppVersionLabel
import org.finiteplay.core.ui.layout.labelRes
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.finiteplay.klondike.R
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.storage.DifficultyPreference
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.klondike.storage.Handedness
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.core.ui.layout.AppLanguages

/**
 * Automatic-moves, skip-animations, handedness, sound, and draw-mode controls,
 * applied immediately (`UI_SPEC.md` "Dialog Behavior": "Settings apply immediately";
 * `docs/games/klondike/EXECUTION_PLAN.md` A3) — except draw mode, which is fixed once a game is
 * dealt (`docs/games/klondike/DESIGN.md` "Draw-Three Mode"), so changing it here only takes effect
 * on the *next* New Game, not the game currently in progress.
 *
 * [debugTools] is where the debug build's board affordances live (near-win fixture,
 * archive export). They sit here rather than on the game screen so the board stays free of
 * developer labels; the slot is empty in release, where the composables render nothing.
 */
@Composable
fun SettingsScreen(
    automaticMovesEnabled: Boolean,
    animationsEnabled: Boolean,
    hintShowsWinningMove: Boolean,
    hintTimeout: HintTimeout,
    restReminderInterval: RestReminderInterval,
    restReminderElapsedSeconds: Int,
    handedness: Handedness,
    soundEnabled: Boolean,
    drawMode: DrawMode,
    difficulty: DifficultyPreference,
    languageTag: String,
    themeMode: ThemeMode,
    onAutomaticMovesEnabledChange: (Boolean) -> Unit,
    onAnimationsEnabledChange: (Boolean) -> Unit,
    onHintShowsWinningMoveChange: (Boolean) -> Unit,
    onHintTimeoutChange: (HintTimeout) -> Unit,
    onRestReminderIntervalChange: (RestReminderInterval) -> Unit,
    onHandednessChange: (Handedness) -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onDrawModeChange: (DrawMode) -> Unit,
    onDifficultyChange: (DifficultyPreference) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onClose: () -> Unit,
    debugTools: @Composable () -> Unit = {},
) {
    // Scrollable because the list already fills a phone screen, and the debug build adds two
    // more rows below it.
    FullScreenPanel(
        title = stringResource(CoreR.string.settings_title),
        onClose = onClose,
        testTag = "settings_screen",
    ) {

        // Grouped rather than one flat list: nine controls with no structure read as a
        // wall of switches, and which game a setting affects — this one or the next —
        // is exactly what the grouping makes visible.
        SettingsGroup(stringResource(CoreR.string.settings_group_play)) {
            SwitchSettingRow(
                label = stringResource(R.string.setting_automatic_moves),
                checked = automaticMovesEnabled,
                onCheckedChange = onAutomaticMovesEnabledChange,
                testTag = "setting_automatic_moves",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_left_handed),
                checked = handedness == Handedness.LEFT,
                onCheckedChange = { checked -> onHandednessChange(if (checked) Handedness.LEFT else Handedness.RIGHT) },
                testTag = "setting_left_handed",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_sound),
                checked = soundEnabled,
                onCheckedChange = onSoundEnabledChange,
                testTag = "setting_sound",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_enable_animations),
                checked = animationsEnabled,
                onCheckedChange = onAnimationsEnabledChange,
                testTag = "setting_enable_animations",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_hint_shows_winning_move),
                checked = hintShowsWinningMove,
                onCheckedChange = onHintShowsWinningMoveChange,
                testTag = "setting_hint_shows_winning_move",
            )
            DropdownSettingRow(
                label = stringResource(CoreR.string.setting_hint_timeout),
                options = HintTimeout.entries,
                selected = hintTimeout,
                optionLabel = { it.label() },
                onSelect = onHintTimeoutChange,
                testTag = "setting_hint_timeout",
            )
        }

        SettingsGroup(stringResource(CoreR.string.settings_group_next_game)) {
            SwitchSettingRow(
                label = stringResource(R.string.setting_draw_three),
                checked = drawMode == DrawMode.THREE,
                onCheckedChange = { checked -> onDrawModeChange(if (checked) DrawMode.THREE else DrawMode.ONE) },
                testTag = "setting_draw_three",
            )
            DropdownSettingRow(
                label = stringResource(R.string.setting_difficulty),
                options = DifficultyPreference.entries,
                selected = difficulty,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = onDifficultyChange,
                testTag = "setting_difficulty",
            )
        }

        SettingsGroup(stringResource(CoreR.string.settings_group_appearance)) {
            DropdownSettingRow(
                label = stringResource(CoreR.string.setting_theme),
                options = ThemeMode.entries,
                selected = themeMode,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = onThemeModeChange,
                testTag = "setting_theme",
            )
            DropdownSettingRow(
                label = stringResource(CoreR.string.setting_language),
                options = AppLanguages.OPTIONS,
                selected = languageTag,
                optionLabel = { tag -> if (tag == SYSTEM_LANGUAGE) stringResource(CoreR.string.language_system) else AppLanguages.displayName(tag) },
                onSelect = onLanguageChange,
                testTag = "setting_language",
            )
        }

        SettingsGroup(stringResource(CoreR.string.settings_group_breaks)) {
            DropdownSettingRow(
                label = stringResource(CoreR.string.setting_rest_reminder),
                options = RestReminderInterval.entries,
                selected = restReminderInterval,
                optionLabel = { it.label() },
                onSelect = onRestReminderIntervalChange,
                testTag = "setting_rest_reminder",
            )
            InfoSettingRow(
                label = stringResource(CoreR.string.setting_rest_reminder_current_session),
                value = formatElapsed(restReminderElapsedSeconds),
                testTag = "setting_rest_reminder_current_session",
            )
        }

        debugTools()

        AppVersionLabel()
    }
}

/** One titled group of settings on its own surface. */


/** Reuses the status row's level names so a level reads identically wherever it appears. */
internal fun DifficultyPreference.labelRes(): Int = when (this) {
    DifficultyPreference.TRIVIAL -> R.string.level_trivial
    DifficultyPreference.EASY -> R.string.level_easy
    DifficultyPreference.MEDIUM -> R.string.level_medium
    DifficultyPreference.HARD -> R.string.level_hard
    DifficultyPreference.EXPERT -> R.string.level_expert
    DifficultyPreference.INSANE -> R.string.level_insane
    DifficultyPreference.RANDOM -> R.string.difficulty_random
}


private fun optionTag(option: Any?): String = when (option) {
    is Enum<*> -> option.name.lowercase()
    else -> option.toString().ifEmpty { "system" }.lowercase()
}

