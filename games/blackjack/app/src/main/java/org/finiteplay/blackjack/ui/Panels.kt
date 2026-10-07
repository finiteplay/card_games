package org.finiteplay.blackjack.ui

import android.app.Activity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.finiteplay.core.ui.layout.StatTabs
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
        // The panel's column has no spacing of its own: the cards are separated here, by the same
        // 12 dp the other games' statistics screens use.
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(12.dp))
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

/** Which help page is showing. */
private enum class HelpPage { RULES, STRATEGY }

/**
 * How Blackjack is played, for someone who has never played it: the rules in short sections, with
 * each decision — Hit, Stand, Double, Split, Insurance — explained in a paragraph of its own, and a
 * second tab with a simple strategy for when a player starts wondering what to do. The contract is
 * `RULES.md`; this is its plain-language summary.
 */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    var page by remember { mutableStateOf(HelpPage.RULES) }
    FullScreenPanel(
        title = stringResource(R.string.help_title),
        onClose = onClose,
        testTag = "help_screen",
        scrollable = false,
    ) {
        StatTabs(
            options = HelpPage.entries,
            selected = page,
            label = { stringResource(if (it == HelpPage.RULES) R.string.help_tab_rules else R.string.help_tab_strategy) },
            onSelect = { page = it },
            tagPrefix = "help_tab",
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
        ) {
            when (page) {
                HelpPage.RULES -> {
                    HelpSection(stringResource(R.string.help_goal_title), stringResource(R.string.help_goal))
                    HelpSection(stringResource(R.string.help_values_title), stringResource(R.string.help_values))
                    HelpSection(stringResource(R.string.help_round_title), stringResource(R.string.help_round))
                    HelpSection(stringResource(R.string.help_hit_title), stringResource(R.string.help_hit))
                    HelpSection(stringResource(R.string.help_stand_title), stringResource(R.string.help_stand))
                    HelpSection(stringResource(R.string.help_double_title), stringResource(R.string.help_double))
                    HelpSection(stringResource(R.string.help_split_title), stringResource(R.string.help_split))
                    HelpSection(stringResource(R.string.help_insurance_title), stringResource(R.string.help_insurance))
                    HelpSection(stringResource(R.string.help_dealer_title), stringResource(R.string.help_dealer))
                    HelpSection(stringResource(R.string.help_chips_title), stringResource(R.string.help_chips))
                }
                HelpPage.STRATEGY -> {
                    Text(
                        text = stringResource(R.string.help_strategy_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    for (tip in listOf(
                        R.string.help_strategy_1, R.string.help_strategy_2, R.string.help_strategy_3, R.string.help_strategy_4,
                        R.string.help_strategy_5, R.string.help_strategy_6, R.string.help_strategy_7,
                    )) {
                        Text(
                            text = stringResource(tip),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            Text(
                text = stringResource(CoreR.string.company_byline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            )
        }
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
