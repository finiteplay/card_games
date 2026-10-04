package org.finiteplay.spider.ui.game

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
import org.finiteplay.core.session.StatisticsPeriod
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.core.ui.layout.HintUsage
import org.finiteplay.core.ui.layout.ResetStatisticsDialog
import org.finiteplay.core.ui.layout.StatTabs
import org.finiteplay.core.ui.layout.StatisticsBody
import org.finiteplay.core.ui.layout.label
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.storage.SpiderStatistics

/**
 * Statistics for one suit count at a time.
 *
 * The suit-count selector is not a filter that can be turned off: one, two, and four suits are
 * different games sharing a board, and a combined total would describe nobody's play
 * (`DESIGN.md` "Scoring and statistics"). The sections themselves are `core:ui`'s, shared with
 * every other game's statistics screen.
 */
@Composable
fun StatisticsScreen(
    statistics: SpiderStatistics,
    suitCount: SuitCount,
    period: StatisticsPeriod,
    onSuitCountChange: (SuitCount) -> Unit,
    onPeriodChange: (StatisticsPeriod) -> Unit,
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
        StatTabs(SuitCount.entries, suitCount, { stringResource(it.labelRes()) }, onSuitCountChange, "statistics_suits")
        StatTabs(StatisticsPeriod.entries, period, { it.label() }, onPeriodChange, "statistics_period")

        StatisticsBody(
            statistics = statistics.toSession(),
            efficiency = statistics.efficiency,
            hints = HintUsage(statistics.hintsUsed, statistics.hintFreeWins),
            modifier = Modifier.weight(1f),
        )

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
