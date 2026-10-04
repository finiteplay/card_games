package org.finiteplay.klondike.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.klondike.R
import org.finiteplay.core.ui.layout.FullScreenPanel

/** Which help page is showing. */
private enum class HelpPage { RULES, STRATEGY, LEVELS }

/**
 * Help for a player who has not played Klondike before: the rules, the strategies everyone
 * knows, and what the six levels actually mean.
 *
 * Written for a beginner rather than for accuracy against the specs — `RULES.md`,
 * `PUBLIC_STRATEGY_RESEARCH.md` and `DIFFICULTY_LEVELS.md` are the authorities, and this
 * screen is their plain-language summary. Where the two would differ, this text simplifies;
 * it never contradicts.
 *
 * Three tabs rather than one long scroll: the three subjects are read at different moments —
 * the rules once, the strategy when a player starts losing, the levels when they wonder what
 * the label above the board means.
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

        TabRow(selectedTabIndex = HelpPage.entries.indexOf(page), modifier = Modifier.testTag("help_tabs")) {
            for (candidate in HelpPage.entries) {
                Tab(
                    selected = page == candidate,
                    onClick = { page = candidate },
                    text = { Text(stringResource(candidate.titleRes())) },
                    modifier = Modifier.testTag("help_tab_${candidate.name.lowercase()}"),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (page) {
                HelpPage.RULES -> RulesPage()
                HelpPage.STRATEGY -> StrategyPage()
                HelpPage.LEVELS -> LevelsPage()
            }
        }
    }
}

private fun HelpPage.titleRes(): Int = when (this) {
    HelpPage.RULES -> R.string.help_tab_rules
    HelpPage.STRATEGY -> R.string.help_tab_strategy
    HelpPage.LEVELS -> R.string.help_tab_levels
}

@Composable
private fun ColumnScope.RulesPage() {
    HelpSection(stringResource(R.string.help_rules_goal_title), stringResource(R.string.help_rules_goal))
    HelpSection(stringResource(R.string.help_rules_board_title), stringResource(R.string.help_rules_board))
    HelpSection(stringResource(R.string.help_rules_moves_title), stringResource(R.string.help_rules_moves))
    HelpSection(stringResource(R.string.help_rules_help_title), stringResource(R.string.help_rules_help))
}

@Composable
private fun ColumnScope.StrategyPage() {
    HelpLead(stringResource(R.string.help_strategy_intro))
    HelpSection(stringResource(R.string.help_strategy_1_title), stringResource(R.string.help_strategy_1))
    HelpSection(stringResource(R.string.help_strategy_2_title), stringResource(R.string.help_strategy_2))
    HelpSection(stringResource(R.string.help_strategy_3_title), stringResource(R.string.help_strategy_3))
    HelpSection(stringResource(R.string.help_strategy_4_title), stringResource(R.string.help_strategy_4))
    HelpSection(stringResource(R.string.help_strategy_5_title), stringResource(R.string.help_strategy_5))
}

/**
 * The levels, each in its own difficulty accent — the same colour the status row uses for that
 * level, so the page and the board agree about what "Hard" looks like.
 */
@Composable
private fun ColumnScope.LevelsPage() {
    val accents = LocalAppColors.current.difficulty
    HelpLead(stringResource(R.string.help_levels_intro))
    HelpSection(stringResource(R.string.help_levels_trivial_title), stringResource(R.string.help_levels_trivial), accents.trivial)
    HelpSection(stringResource(R.string.help_levels_easy_title), stringResource(R.string.help_levels_easy), accents.easy)
    HelpSection(stringResource(R.string.help_levels_medium_title), stringResource(R.string.help_levels_medium), accents.medium)
    HelpSection(stringResource(R.string.help_levels_hard_title), stringResource(R.string.help_levels_hard), accents.hard)
    HelpSection(stringResource(R.string.help_levels_expert_title), stringResource(R.string.help_levels_expert), accents.expert)
    HelpSection(stringResource(R.string.help_levels_insane_title), stringResource(R.string.help_levels_insane), accents.insane)
}

@Composable
private fun HelpLead(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 24.sp,
    )
}

@Composable
private fun HelpSection(title: String, body: String, accent: androidx.compose.ui.graphics.Color? = null) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
