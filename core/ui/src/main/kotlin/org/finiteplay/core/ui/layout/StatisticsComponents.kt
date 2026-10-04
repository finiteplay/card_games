package org.finiteplay.core.ui.layout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.finiteplay.core.session.PercentileDistribution
import org.finiteplay.core.session.RECENT_GAMES
import org.finiteplay.core.session.SessionStatistics
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.session.SolutionEfficiency
import org.finiteplay.core.session.StatisticsPeriod
import org.finiteplay.core.ui.R
import org.finiteplay.core.ui.theme.LocalAppColors

/** Localized label for a [StatisticsPeriod] tab. */
@Composable
fun StatisticsPeriod.label(): String = stringResource(
    when (this) {
        StatisticsPeriod.WEEK -> R.string.period_week
        StatisticsPeriod.MONTH -> R.string.period_month
        StatisticsPeriod.ALL_TIME -> R.string.period_all_time
    },
)

/**
 * A row of tabs over [options]; each tab is tagged `"{tagPrefix}_{option name lowercased}"` and
 * the row itself `"{tagPrefix}_tabs"`.
 */
@Composable
fun <T : Enum<T>> StatTabs(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    tagPrefix: String,
) {
    TabRow(selectedTabIndex = options.indexOf(selected), modifier = Modifier.testTag("${tagPrefix}_tabs")) {
        options.forEach { candidate ->
            Tab(
                selected = selected == candidate,
                onClick = { onSelect(candidate) },
                text = { Text(label(candidate)) },
                modifier = Modifier.testTag("${tagPrefix}_${candidate.name.lowercase()}"),
            )
        }
    }
}

/**
 * A titled surface grouping one part of a statistics screen. Sections rather than one long
 * column: a dozen numbers and several graphs read as a wall of text otherwise, and the grouping
 * is what says which numbers belong together.
 */
@Composable
fun StatSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            StatSectionTitle(title)
            content()
        }
    }
}

/**
 * A section heading: small, heavy, letter-spaced, and in the screen's accent. Letter spacing
 * rather than capitals — capitals read badly in Greek and are meaningless in the CJK locales,
 * while tracking is a cue in every script.
 */
@Composable
fun StatSectionTitle(title: String, accent: Color = LocalAppColors.current.action.statistics) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = accent,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/**
 * Label/value pairs as tiles rather than a list of sentences: the number is what a player came to
 * read, so it is the large element and its label sits underneath. Two per row, which fits the
 * longest translated label at the narrowest shipped width.
 */
@Composable
fun StatTileGrid(tiles: List<Pair<String, String>>, testTag: String) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (row in tiles.chunked(2)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((label, value) in row) StatTile(label, value, Modifier.weight(1f))
                if (row.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.action.statistics,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/** A titled [PercentileDistribution] drawn as a range graph, or a note when there is none yet. */
@Composable
fun StatDistribution(title: String, distribution: PercentileDistribution?, format: @Composable (Long) -> String) {
    Column(modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (distribution == null) {
            Text(
                stringResource(R.string.stat_no_completed_games),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.stat_sample_size, distribution.sampleSize),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PercentileRangeGraph(distribution, format)
        }
    }
}

/**
 * A single-row range graph in place of a text line of numbers: a full track spanning min to max,
 * a brighter band spanning the 10th to 90th percentile (where most results actually fall), and a
 * marker at the median. The content description carries every value in words, so nothing here is
 * graph-only information.
 */
@Composable
private fun PercentileRangeGraph(distribution: PercentileDistribution, format: @Composable (Long) -> String) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val bandColor = MaterialTheme.colorScheme.primary
    val medianColor = MaterialTheme.colorScheme.onSurface
    val description = stringResource(
        R.string.cd_distribution_range,
        format(distribution.min),
        format(distribution.max),
        format(distribution.p10),
        format(distribution.p50),
        format(distribution.p90),
    )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("statistics_distribution_graph")) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .semantics { contentDescription = description },
        ) {
            val span = (distribution.max - distribution.min).toFloat().coerceAtLeast(1f)
            fun xFor(value: Long): Float = (value - distribution.min) / span * size.width
            val centerY = size.height / 2

            drawLine(
                color = trackColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = bandColor,
                start = Offset(xFor(distribution.p10), centerY),
                end = Offset(xFor(distribution.p90), centerY),
                strokeWidth = 14f,
                cap = StrokeCap.Round,
            )
            drawCircle(color = medianColor, radius = 9f, center = Offset(xFor(distribution.p50), centerY))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(format(distribution.min), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.stat_median, format(distribution.p50)), style = MaterialTheme.typography.labelSmall)
            Text(format(distribution.max), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * How the player's wins compare with the certified lines shipped for those deals: three tiles
 * and the ratio's range graph. Lower is better throughout, and the section says so — the one
 * place on a statistics screen where it is.
 */
@Composable
fun SolutionEfficiencySection(efficiency: SolutionEfficiency?) {
    Text(
        stringResource(R.string.stat_efficiency_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    if (efficiency != null) {
        StatTileGrid(
            tiles = listOf(
                stringResource(R.string.stat_tile_avg_vs_solution) to stringResource(R.string.stat_percent_value, efficiency.averagePercent),
                stringResource(R.string.stat_tile_best_vs_solution) to stringResource(R.string.stat_percent_value, efficiency.ratioDistribution.min),
                stringResource(R.string.stat_tile_matched_solution) to "${efficiency.matchedOrBeaten}/${efficiency.ratioDistribution.sampleSize}",
            ),
            testTag = "statistics_efficiency",
        )
        Column(Modifier.padding(top = 16.dp)) {
            StatDistribution(stringResource(R.string.stat_vs_solution_title), efficiency.ratioDistribution) { percent ->
                stringResource(R.string.stat_percent_value, percent)
            }
        }
    } else {
        StatDistribution(stringResource(R.string.stat_vs_solution_title), null) { "" }
    }
}

/** The confirmation every game's Reset Statistics button shows before it deletes anything. */
@Composable
fun ResetStatisticsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("statistics_reset_confirmation"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stat_reset_title)) },
        text = { Text(stringResource(R.string.stat_reset_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.stat_reset_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The sections every game's statistics screen shares, in the order a player reads them: the
 * summary, the player's wins against each deal's certified solution, whatever a game adds in
 * [middle] (a win rate per difficulty level, say), hints, and the time and move distributions.
 *
 * Scrolls on its own and fills the space it is given, so a caller places it under its tabs with
 * `Modifier.weight(1f)` and puts its Reset button below. A game with no hints to count passes
 * `null` for [hints] and gets no hints section.
 */
@Composable
fun StatisticsBody(
    statistics: SessionStatistics,
    efficiency: SolutionEfficiency?,
    hints: HintUsage?,
    modifier: Modifier = Modifier,
    middle: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val dash = stringResource(R.string.stat_dash)

        StatSectionCard(stringResource(R.string.stat_section_summary)) {
            val tiles = buildList {
                add(stringResource(R.string.stat_tile_wins) to statistics.wins.toString())
                add(stringResource(R.string.stat_tile_losses) to statistics.losses.toString())
                add(
                    stringResource(R.string.stat_tile_win_rate) to
                        (statistics.winRate?.let { stringResource(R.string.stat_win_rate_value, it * 100) } ?: dash),
                )
                add(stringResource(R.string.stat_tile_played) to statistics.gamesPlayed.toString())
                // Only once there are more decided games than the window holds: before that it is
                // the win rate again under another name.
                statistics.recentWinRate?.let {
                    add(
                        stringResource(R.string.stat_tile_recent_win_rate, RECENT_GAMES) to
                            stringResource(R.string.stat_win_rate_value, it * 100),
                    )
                }
                add(stringResource(R.string.stat_tile_streak) to statistics.currentStreak.toString())
                add(stringResource(R.string.stat_tile_longest_streak) to statistics.longestStreak.toString())
                add(stringResource(R.string.stat_tile_loss_streak) to statistics.longestLossStreak.toString())
                add(
                    stringResource(R.string.stat_tile_avg_time) to
                        (statistics.averageElapsedMillis?.let { formatElapsed((it / 1000).toInt()) } ?: dash),
                )
            }
            StatTileGrid(tiles, "statistics_summary")
            Text(
                stringResource(
                    R.string.stat_best,
                    statistics.bestMoveCount?.let { stringResource(R.string.stat_best_moves, it) } ?: dash,
                    statistics.bestElapsedMillis?.let { formatElapsed((it / 1000).toInt()) } ?: dash,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp).testTag("statistics_bests"),
            )
        }

        StatSectionCard(stringResource(R.string.stat_section_efficiency)) {
            SolutionEfficiencySection(efficiency)
        }

        middle()

        if (hints != null) {
            StatSectionCard(stringResource(R.string.stat_section_hints)) {
                StatTileGrid(
                    tiles = listOf(
                        stringResource(R.string.stat_tile_hints) to hints.used.toString(),
                        stringResource(R.string.stat_tile_hint_free_wins) to "${hints.hintFreeWins}/${statistics.wins}",
                    ),
                    testTag = "statistics_hints",
                )
            }
        }

        StatSectionCard(stringResource(R.string.stat_section_distributions)) {
            StatDistribution(stringResource(R.string.stat_completion_time_title), statistics.elapsedDistribution) {
                formatElapsed((it / 1000).toInt())
            }
            StatDistribution(stringResource(R.string.stat_moves_title), statistics.moveCountDistribution) { it.toString() }
        }
    }
}

/** Hints taken across a window's games, and how many of its wins took none. */
data class HintUsage(val used: Int, val hintFreeWins: Int)
