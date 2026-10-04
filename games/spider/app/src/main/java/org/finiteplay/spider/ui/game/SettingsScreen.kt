package org.finiteplay.spider.ui.game

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
import org.finiteplay.core.ui.layout.AppVersionLabel
import org.finiteplay.core.ui.layout.labelRes
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.spider.R
import org.finiteplay.spider.layout.SuitCount

/**
 * Spider's settings, applied immediately except the suit count — which is fixed once a deal is
 * made, so it takes effect on the next New Game (`RULES.md` "Suit counts").
 *
 * Almost none of this file is layout: the panel shell, the grouped cards, the switch and dropdown
 * rows, and every string but the suit-count labels are `core:ui`'s. What is left is the list of
 * settings this game actually has, which is the only part another game could not reuse. That is
 * also why this screen ships in every locale `core:ui` already carries without Spider translating
 * anything (`docs/ARCHITECTURE.md` "Moved later").
 */
@Composable
fun SettingsScreen(
    animationsEnabled: Boolean,
    hintShowsWinningMove: Boolean,
    hintTimeout: HintTimeout,
    restReminderInterval: RestReminderInterval,
    restReminderElapsedSeconds: Int,
    handedness: Handedness,
    soundEnabled: Boolean,
    languageTag: String,
    themeMode: ThemeMode,
    nextSuitCount: SuitCount,
    availableLanguages: List<String>,
    onAnimationsEnabledChange: (Boolean) -> Unit,
    onHintShowsWinningMoveChange: (Boolean) -> Unit,
    onHintTimeoutChange: (HintTimeout) -> Unit,
    onRestReminderIntervalChange: (RestReminderInterval) -> Unit,
    onHandednessChange: (Handedness) -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNextSuitCountChange: (SuitCount) -> Unit,
    onClose: () -> Unit,
) {
    FullScreenPanel(
        title = stringResource(CoreR.string.settings_title),
        onClose = onClose,
        testTag = "settings_screen",
    ) {
        SettingsGroup(stringResource(CoreR.string.settings_group_next_game)) {
            DropdownSettingRow(
                label = stringResource(R.string.setting_suit_count),
                options = SuitCount.entries,
                selected = nextSuitCount,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = onNextSuitCountChange,
                testTag = "setting_suit_count",
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

        AppVersionLabel()
    }
}

internal fun SuitCount.labelRes(): Int = when (this) {
    SuitCount.ONE -> R.string.suit_count_one
    SuitCount.TWO -> R.string.suit_count_two
    SuitCount.FOUR -> R.string.suit_count_four
}
