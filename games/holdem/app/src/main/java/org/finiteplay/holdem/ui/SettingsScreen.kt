package org.finiteplay.holdem.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.AboutSettingsGroup
import org.finiteplay.core.ui.layout.AppLanguages
import org.finiteplay.core.ui.layout.DropdownSettingRow
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.InfoSettingRow
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.layout.SettingsGroup
import org.finiteplay.core.ui.layout.SwitchSettingRow
import org.finiteplay.core.ui.layout.label
import org.finiteplay.core.ui.layout.labelRes
import org.finiteplay.core.ui.theme.ThemeMode
import org.finiteplay.holdem.storage.HoldemSettings

/**
 * Hold'em's settings, applied immediately (`UI_SPEC.md` "Settings"): Appearance, Breaks, About. No
 * automatic-moves setting, because opponents are other players and not automation.
 */
@Composable
fun SettingsScreen(
    settings: HoldemSettings,
    restReminderElapsedSeconds: Int,
    onHandednessChange: (Handedness) -> Unit,
    onAnimationsChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onRestReminderChange: (RestReminderInterval) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    FullScreenPanel(
        title = stringResource(CoreR.string.settings_title),
        onClose = onClose,
        testTag = "settings_screen",
    ) {
        SettingsGroup(stringResource(CoreR.string.settings_group_appearance)) {
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_left_handed),
                checked = settings.handedness == Handedness.LEFT,
                onCheckedChange = { onHandednessChange(if (it) Handedness.LEFT else Handedness.RIGHT) },
                testTag = "setting_left_handed",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_enable_animations),
                checked = settings.animationsEnabled,
                onCheckedChange = onAnimationsChange,
                testTag = "setting_enable_animations",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_sound),
                checked = settings.soundEnabled,
                onCheckedChange = onSoundChange,
                testTag = "setting_sound",
            )
            DropdownSettingRow(
                label = stringResource(CoreR.string.setting_theme),
                options = ThemeMode.entries,
                selected = settings.themeMode,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = onThemeChange,
                testTag = "setting_theme",
            )
            DropdownSettingRow(
                label = stringResource(CoreR.string.setting_language),
                options = AppLanguages.OPTIONS,
                selected = settings.languageTag,
                optionLabel = { tag ->
                    if (tag == SYSTEM_LANGUAGE) stringResource(CoreR.string.language_system) else AppLanguages.displayName(tag)
                },
                onSelect = { tag ->
                    onLanguageChange(tag)
                    // The locale is read in attachBaseContext, so it only takes hold on a fresh
                    // Activity; recreating is what makes the change visible now.
                    AppLocale.setTag(context, tag)
                    (context as? Activity)?.recreate()
                },
                testTag = "setting_language",
            )
        }
        SettingsGroup(stringResource(CoreR.string.settings_group_breaks)) {
            DropdownSettingRow(
                label = stringResource(CoreR.string.setting_rest_reminder),
                options = RestReminderInterval.entries,
                selected = settings.restReminderInterval,
                optionLabel = { it.label() },
                onSelect = onRestReminderChange,
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
