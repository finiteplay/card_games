package org.finiteplay.klondike.ui.game

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
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.core.ui.layout.ResetStatisticsDialog
import org.finiteplay.core.ui.layout.HintUsage
import org.finiteplay.core.ui.layout.StatSectionCard
import org.finiteplay.core.ui.layout.StatisticsBody
import org.finiteplay.core.ui.layout.StatTabs
import org.finiteplay.core.ui.layout.StatTileGrid
import org.finiteplay.core.ui.layout.label
import org.finiteplay.klondike.R
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.klondike.storage.GameStatistics
import org.finiteplay.klondike.storage.StatisticsPeriod
import org.finiteplay.klondike.storage.toSession

/**
 * Statistics for one draw mode at a time: a Draw One/Draw Three selector, a
 * Week/Month/All Time tab selection within it, then sections for the summary, the player's
 * wins against each deal's certified solution, win rate per level, hints, and nearest-rank
 * distributions drawn as range graphs (`docs/games/klondike/UI_SPEC.md` "Dialog Behavior";
 * `docs/games/klondike/DESIGN.md` "Statistics" and "Draw-Three Mode": the two modes are never
 * blended into one aggregate). Reset requires explicit confirmation and never runs silently,
 * and always clears *every* mode's and period's history together — there is no per-mode or
 * per-period reset.
 */
@Composable
fun StatisticsScreen(
    statistics: GameStatistics,
    period: StatisticsPeriod,
    onPeriodChange: (StatisticsPeriod) -> Unit,
    drawMode: DrawMode,
    onDrawModeChange: (DrawMode) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    var confirmingReset by remember { mutableStateOf(false) }

    FullScreenPanel(
        title = stringResource(CoreR.string.statistics_title),
        onClose = onClose,
        testTag = "statistics_screen",
        scrollable = false,
    ) {
        StatTabs(DrawMode.entries, drawMode, { it.label() }, onDrawModeChange, "statistics_draw_mode")
        StatTabs(StatisticsPeriod.entries, period, { it.label() }, onPeriodChange, "statistics_period")

        StatisticsBody(
            statistics = statistics.toSession(),
            efficiency = statistics.efficiency,
            hints = HintUsage(statistics.hintsUsed, statistics.hintFreeWins),
            modifier = Modifier.weight(1f),
        ) {
            if (statistics.byLevel.isNotEmpty()) {
                StatSectionCard(stringResource(CoreR.string.stat_section_by_level)) {
                    StatTileGrid(
                        tiles = statistics.byLevel.map { level ->
                            val decided = level.wins + level.losses
                            stringResource(CoreR.string.stat_level_label, stringResource(level.tier.labelRes()), level.wins, decided) to
                                stringResource(CoreR.string.stat_win_rate_value, level.wins * 100.0 / decided)
                        },
                        testTag = "statistics_by_level",
                    )
                }
            }
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

@Composable
private fun DrawMode.label(): String = stringResource(
    when (this) {
        DrawMode.ONE -> R.string.draw_mode_one
        DrawMode.THREE -> R.string.draw_mode_three
    },
)

private fun DifficultyTier.labelRes(): Int = when (this) {
    DifficultyTier.TRIVIAL -> R.string.level_trivial
    DifficultyTier.EASY -> R.string.level_easy
    DifficultyTier.MEDIUM -> R.string.level_medium
    DifficultyTier.HARD -> R.string.level_hard
    DifficultyTier.EXPERT -> R.string.level_expert
    DifficultyTier.INSANE -> R.string.level_insane
}
