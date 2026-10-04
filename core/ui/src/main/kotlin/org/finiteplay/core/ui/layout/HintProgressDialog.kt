package org.finiteplay.core.ui.layout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.R

/**
 * A hint search running long enough to notice: shown once the search has been running for a
 * second (each game's own `GameViewModel` decides when, not this composable), rather than at the
 * first frame — most hints resolve well under that, and flashing a modal for an instant read as
 * broken rather than helpful.
 *
 * Cancel stops the player from waiting on it, not the search itself: none of the three games'
 * solvers check for cancellation mid-search today (`HintNoticeRow`'s own comment in each game
 * records the same limit for its non-modal loading notice), so [onCancel] discards the eventual
 * result rather than interrupting the CPU work computing it.
 */
@Composable
fun HintProgressDialog(message: String, onCancel: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("hint_progress_dialog"),
        // Not dismissible by tapping outside: an accidental tap on the dimmed scrim shouldn't
        // silently discard a search that only Cancel is meant to give up on.
        onDismissRequest = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("hint_progress_dialog_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
