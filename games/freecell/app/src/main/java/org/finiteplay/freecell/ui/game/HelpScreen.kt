package org.finiteplay.freecell.ui.game

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
import org.finiteplay.freecell.R

/**
 * How FreeCell is played, in the order a new player needs it: the goal, what a free cell is (the
 * one thing a player arriving from Klondike or Spider has not seen), building and the supermove,
 * then the controls.
 *
 * Prose rather than a rules reference. `docs/games/freecell/RULES.md` is the contract and stays
 * exhaustive; this is the part a player needs to start. One scroll, not tabbed pages, mirroring
 * Spider's own `HelpScreen` — FreeCell has no difficulty tiers to give a page of their own to.
 */
@Composable
fun HelpScreen(onClose: () -> Unit) {
    FullScreenPanel(
        title = stringResource(R.string.help_title),
        onClose = onClose,
        testTag = "help_screen",
    ) {
        HelpSection(stringResource(R.string.help_goal_title), stringResource(R.string.help_goal))
        HelpSection(stringResource(R.string.help_free_cells_title), stringResource(R.string.help_free_cells))
        HelpSection(stringResource(R.string.help_moving_title), stringResource(R.string.help_moving))
        HelpSection(stringResource(R.string.help_controls_title), stringResource(R.string.help_controls))
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
