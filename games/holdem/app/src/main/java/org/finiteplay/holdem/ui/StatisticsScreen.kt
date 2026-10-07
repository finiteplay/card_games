package org.finiteplay.holdem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.core.ui.layout.ResetStatisticsDialog
import org.finiteplay.core.ui.layout.StatSectionCard
import org.finiteplay.core.ui.layout.StatTileGrid
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.holdem.R
import org.finiteplay.holdem.storage.HoldemStatistics

private val PLACE_LABELS = listOf(R.string.place_1, R.string.place_2, R.string.place_3, R.string.place_4, R.string.place_5, R.string.place_6)

/**
 * Statistics, one pool with no tabs (`UI_SPEC.md` "Statistics"): tournaments, the finishes bar for
 * each place, and hands. Reset asks first and touches nothing but the figures.
 */
@Composable
fun StatisticsScreen(
    statistics: HoldemStatistics,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }
    val dash = stringResource(CoreR.string.stat_dash)
    FullScreenPanel(
        title = stringResource(CoreR.string.statistics_title),
        onClose = onClose,
        testTag = "statistics_screen",
    ) {
        Spacer(Modifier.height(12.dp))
        StatSectionCard(stringResource(R.string.stat_section_tournaments)) {
            StatTileGrid(
                tiles = listOf(
                    stringResource(R.string.stat_tile_tournaments_played) to statistics.tournamentsPlayed.toString(),
                    stringResource(R.string.stat_tile_tournaments_won) to statistics.tournamentsWon.toString(),
                    stringResource(CoreR.string.stat_tile_win_rate) to
                        (statistics.winRate?.let { stringResource(CoreR.string.stat_win_rate_value, it * 100) } ?: dash),
                    stringResource(R.string.stat_tile_avg_finish) to
                        (statistics.averageFinish?.let { stringResource(R.string.stat_average_finish_value, it) } ?: dash),
                    stringResource(CoreR.string.stat_tile_streak) to statistics.currentStreak.toString(),
                    stringResource(CoreR.string.stat_tile_longest_streak) to statistics.bestStreak.toString(),
                ),
                testTag = "statistics_tournaments",
            )
        }
        Spacer(Modifier.height(12.dp))
        StatSectionCard(stringResource(R.string.stat_section_finishes)) {
            FinishesBars(statistics.placeCounts)
        }
        Spacer(Modifier.height(12.dp))
        StatSectionCard(stringResource(R.string.stat_section_hands)) {
            StatTileGrid(
                tiles = listOf(
                    stringResource(R.string.stat_tile_hands_played) to statistics.handsPlayed.toString(),
                    stringResource(R.string.stat_tile_hands_won) to statistics.handsWon.toString(),
                    stringResource(R.string.stat_tile_showdowns_won) to
                        stringResource(R.string.stat_showdowns_value, statistics.showdownsWon, statistics.showdownsReached),
                    stringResource(R.string.stat_tile_largest_pot) to statistics.largestPot.toString(),
                    stringResource(R.string.stat_tile_vpip) to
                        (statistics.voluntaryRate?.let { stringResource(CoreR.string.stat_win_rate_value, it * 100) } ?: dash),
                    stringResource(R.string.stat_tile_pfr) to
                        (statistics.preflopRaiseRate?.let { stringResource(CoreR.string.stat_win_rate_value, it * 100) } ?: dash),
                    stringResource(CoreR.string.stat_tile_hints) to statistics.hintsUsed.toString(),
                ),
                testTag = "statistics_hands",
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

/** One bar per place, 1st to 6th, each as long as its share of the most common finish, with its count. */
@Composable
private fun FinishesBars(counts: List<Int>) {
    val most = counts.max().coerceAtLeast(1)
    val bar = LocalAppColors.current.action.statistics
    Column(modifier = Modifier.fillMaxWidth().testTag("statistics_finishes"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        counts.forEachIndexed { index, count ->
            val place = stringResource(PLACE_LABELS[index])
            val description = stringResource(R.string.cd_finish_bar, place, count)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("statistics_finish_${index + 1}")
                    .semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                Text(place, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp).clearAndSetSemantics {})
                Box(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(count.toFloat() / most).height(16.dp).background(bar, MaterialTheme.shapes.small))
                }
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {},
                )
            }
        }
    }
}
