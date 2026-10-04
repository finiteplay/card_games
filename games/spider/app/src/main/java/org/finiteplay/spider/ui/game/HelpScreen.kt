package org.finiteplay.spider.ui.game

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.FullScreenPanel
import org.finiteplay.spider.R

/**
 * How Spider is played, in the order a new player needs it: the goal first, then what a move is,
 * then the two things that most often surprise someone arriving from Klondike — that the stock
 * deals onto every column at once, and that it refuses while a column is empty.
 *
 * Prose rather than a rules reference. `docs/games/spider/RULES.md` is the contract and stays
 * exhaustive; this is the part a player needs to start.
 */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    FullScreenPanel(
        title = stringResource(R.string.help_title),
        onClose = onClose,
        testTag = "help_screen",
    ) {
        HelpSection(stringResource(R.string.help_goal_title), stringResource(R.string.help_goal))
        HelpSection(stringResource(R.string.help_moving_title), stringResource(R.string.help_moving))
        HelpSection(stringResource(R.string.help_stock_title), stringResource(R.string.help_stock))
        HelpSection(stringResource(R.string.help_suits_title), stringResource(R.string.help_suits))
        HelpSection(stringResource(R.string.help_controls_title), stringResource(R.string.help_controls))
        Text(
            text = stringResource(R.string.catalog_status_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
        )
        Text(
            text = stringResource(CoreR.string.company_byline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
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
