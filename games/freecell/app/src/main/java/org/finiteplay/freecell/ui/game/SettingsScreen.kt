package org.finiteplay.freecell.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.AppLanguages
import org.finiteplay.core.ui.layout.DropdownSettingRow
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.core.ui.layout.InfoSettingRow
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.label
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.layout.SettingsGroup
import org.finiteplay.core.ui.layout.SwitchSettingRow
import org.finiteplay.core.ui.layout.AboutSettingsGroup
import org.finiteplay.core.ui.layout.labelRes
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.freecell.R

/**
 * FreeCell's settings, all applied immediately — there is no "next new game" group the way
 * Klondike's draw mode/difficulty or Spider's suit count need, since FreeCell has none of those
 * axes (`docs/games/freecell/RULES.md` "What FreeCell does not have").
 *
 * Almost none of this file is layout: the panel shell, the grouped cards, the switch and dropdown
 * rows, and every string but this game's own are `core:ui`'s.
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
    languageTag: String,
    themeMode: ThemeMode,
    availableLanguages: List<String>,
    onAutomaticMovesEnabledChange: (Boolean) -> Unit,
    onAnimationsEnabledChange: (Boolean) -> Unit,
    onHintShowsWinningMoveChange: (Boolean) -> Unit,
    onHintTimeoutChange: (HintTimeout) -> Unit,
    onRestReminderIntervalChange: (RestReminderInterval) -> Unit,
    onHandednessChange: (Handedness) -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onClose: () -> Unit,
) {
    FullScreenPanel(
        title = stringResource(CoreR.string.settings_title),
        onClose = onClose,
        testTag = "settings_screen",
    ) {
        SettingsGroup(stringResource(CoreR.string.settings_group_play)) {
            SwitchSettingRow(
                label = stringResource(R.string.setting_automatic_moves),
                checked = automaticMovesEnabled,
                onCheckedChange = onAutomaticMovesEnabledChange,
                testTag = "setting_automatic_moves",
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

        SettingsGroup(stringResource(CoreR.string.settings_group_appearance)) {
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_left_handed),
                checked = handedness == Handedness.LEFT,
                onCheckedChange = { onHandednessChange(if (it) Handedness.LEFT else Handedness.RIGHT) },
                testTag = "setting_left_handed",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_enable_animations),
                checked = animationsEnabled,
                onCheckedChange = onAnimationsEnabledChange,
                testTag = "setting_enable_animations",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_sound),
                checked = soundEnabled,
                onCheckedChange = onSoundEnabledChange,
                testTag = "setting_sound",
            )
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
                options = availableLanguages,
                selected = languageTag,
                optionLabel = { tag ->
                    if (tag == SYSTEM_LANGUAGE) stringResource(CoreR.string.language_system) else AppLanguages.displayName(tag)
                },
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

        AboutSettingsGroup()
    }
}
