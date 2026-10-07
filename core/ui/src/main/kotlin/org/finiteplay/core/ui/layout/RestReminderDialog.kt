package org.finiteplay.core.ui.layout

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.ui.R

/**
 * Offered once continuous play has run long enough to count (`docs/PLATFORM.md`
 * "Rest Reminders"): a game has been in the foreground for most of the player's own configured
 * interval. [playedSeconds] is how long, said in words in the player's language ("30 minutes",
 * "1 hour 30 minutes"): this is a sentence a person reads, not a stopwatch.
 *
 * Dismissing without a choice counts as "keep playing" — the reminder is informational, not a
 * gate — but the two labeled buttons exist so declining is a deliberate choice, not an
 * accidental tap on the scrim.
 */
@Composable
fun RestReminderDialog(playedSeconds: Int, onTakeBreak: () -> Unit, onKeepPlaying: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("rest_reminder_dialog"),
        onDismissRequest = onKeepPlaying,
        title = { Text(stringResource(R.string.rest_reminder_title)) },
        text = { Text(stringResource(R.string.rest_reminder_body, humanDuration(playedSeconds))) },
        confirmButton = {
            TextButton(onClick = onTakeBreak, modifier = Modifier.testTag("rest_reminder_take_break")) {
                Text(stringResource(R.string.rest_reminder_take_break))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepPlaying, modifier = Modifier.testTag("rest_reminder_keep_playing")) {
                Text(stringResource(R.string.rest_reminder_keep_playing))
            }
        },
    )
}
