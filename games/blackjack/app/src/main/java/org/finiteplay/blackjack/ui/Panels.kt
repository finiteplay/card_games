package org.finiteplay.blackjack.ui

import android.app.Activity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.finiteplay.blackjack.R
import org.finiteplay.blackjack.storage.BlackjackStatistics
import org.finiteplay.core.storage.AppLocale
import org.finiteplay.core.storage.SYSTEM_LANGUAGE
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.AppLanguages
import org.finiteplay.core.ui.layout.AboutSettingsGroup
import org.finiteplay.core.ui.layout.DropdownSettingRow
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.layout.Handedness
import org.finiteplay.core.ui.layout.InfoSettingRow
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.core.ui.layout.label
import org.finiteplay.core.ui.layout.ResetStatisticsDialog
import org.finiteplay.core.ui.layout.SettingsGroup
import org.finiteplay.core.ui.layout.StatSectionCard
import org.finiteplay.core.ui.layout.StatTileGrid
import org.finiteplay.core.ui.layout.SwitchSettingRow
import org.finiteplay.core.ui.layout.labelRes
import org.finiteplay.core.ui.theme.ThemeMode

/**
 * Blackjack's settings, applied immediately (`UI_SPEC.md` "Settings"): skip animations,
 * handedness, sound, theme, language, and the version last. No automatic-moves setting, because
 * the dealer's play is the house's own turn and not automation.
 */
@Composable
fun SettingsScreen(viewModel: BlackjackViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val settings = viewModel.settings
    FullScreenPanel(
        title = stringResource(CoreR.string.settings_title),
        onClose = onClose,
        testTag = "settings_screen",
    ) {
        SettingsGroup(stringResource(CoreR.string.settings_group_appearance)) {
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_left_handed),
                checked = settings.handedness == Handedness.LEFT,
                onCheckedChange = { viewModel.setHandedness(if (it) Handedness.LEFT else Handedness.RIGHT) },
                testTag = "setting_left_handed",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_enable_animations),
                checked = settings.animationsEnabled,
                onCheckedChange = viewModel::setAnimationsEnabled,
                testTag = "setting_enable_animations",
            )
            SwitchSettingRow(
                label = stringResource(CoreR.string.setting_sound),
                checked = settings.soundEnabled,
                onCheckedChange = viewModel::setSoundEnabled,
                testTag = "setting_sound",
            )
            DropdownSettingRow(
                label = stringResource(CoreR.string.setting_theme),
                options = ThemeMode.entries,
                selected = settings.themeMode,
                optionLabel = { stringResource(it.labelRes()) },
                onSelect = viewModel::setThemeMode,
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
                    viewModel.setLanguageTag(tag)
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
                onSelect = viewModel::setRestReminderInterval,
                testTag = "setting_rest_reminder",
            )
            InfoSettingRow(
                label = stringResource(CoreR.string.setting_rest_reminder_current_session),
                value = formatElapsed(viewModel.restReminderElapsedSeconds),
                testTag = "setting_rest_reminder_current_session",
            )
        }
        AboutSettingsGroup()
    }
}

/**
 * Statistics, one pool with no tabs (`UI_SPEC.md` "Statistics"): hands played, won, lost, pushed,
 * blackjacks, the bankroll, its high-water mark, lifetime net chips and resets, on the shared tile
 * grid. Not `core:session`'s `SessionStatistics`, whose boolean outcome has no place for a push.
 */
@Composable
fun StatisticsScreen(
    statistics: BlackjackStatistics,
    bankroll: Int,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    FullScreenPanel(
        title = stringResource(CoreR.string.statistics_title),
        onClose = onClose,
        testTag = "statistics_screen",
    ) {
        StatSectionCard(stringResource(R.string.stat_section_hands)) {
            StatTileGrid(
                tiles = listOf(
                    stringResource(R.string.stat_tile_hands) to statistics.handsPlayed.toString(),
                    stringResource(CoreR.string.stat_tile_wins) to statistics.handsWon.toString(),
                    stringResource(CoreR.string.stat_tile_losses) to statistics.handsLost.toString(),
                    stringResource(R.string.stat_tile_pushes) to statistics.handsPushed.toString(),
                    stringResource(R.string.stat_tile_blackjacks) to statistics.blackjacks.toString(),
                ),
                testTag = "statistics_hands",
            )
        }
        StatSectionCard(stringResource(R.string.stat_section_chips)) {
            StatTileGrid(
                tiles = listOf(
                    stringResource(R.string.stat_tile_bankroll) to bankroll.toString(),
                    stringResource(R.string.stat_tile_peak) to statistics.highWater.toString(),
                    stringResource(R.string.stat_tile_net) to signed(statistics.lifetimeNet.toInt()),
                    stringResource(R.string.stat_tile_resets) to statistics.resets.toString(),
                ),
                testTag = "statistics_chips",
            )
        }
        TextButton(
            onClick = { confirmingReset = true },
            modifier = Modifier.align(Alignment.End).testTag("statistics_reset"),
        ) {
            Text(stringResource(CoreR.string.stat_reset_button), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmingReset) {
        ResetStatisticsDialog(
            onConfirm = { confirmingReset = false; onReset() },
            onDismiss = { confirmingReset = false },
        )
    }
}

/** How Blackjack is played, in three short sections; the contract is `RULES.md`. */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    FullScreenPanel(
        title = stringResource(R.string.help_title),
        onClose = onClose,
        testTag = "help_screen",
    ) {
        HelpSection(stringResource(R.string.help_goal_title), stringResource(R.string.help_goal))
        HelpSection(stringResource(R.string.help_decisions_title), stringResource(R.string.help_decisions))
        HelpSection(stringResource(R.string.help_chips_title), stringResource(R.string.help_chips))
        Text(
            text = stringResource(CoreR.string.company_byline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    Text(text = body, style = MaterialTheme.typography.bodyMedium)
}
