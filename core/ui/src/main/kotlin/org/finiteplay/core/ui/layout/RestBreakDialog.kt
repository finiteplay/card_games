package org.finiteplay.core.ui.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.R
import org.finiteplay.core.ui.theme.LocalAppColors

/**
 * The break itself, once the player has accepted [RestReminderDialog]'s offer: blocks the board
 * for [remainingSeconds] (a countdown the caller ticks down; drawn as a clock face, read aloud in
 * words) rather than just
 * showing a timer alongside play, since a break that does not stop the game is cosmetic. Not
 * dismissible by tapping outside — only [onCancel] ends it early, same restraint as
 * [HintProgressDialog]'s own cancel-only dismissal.
 *
 * The caller owns the countdown and auto-dismisses this once it reaches zero; this composable has
 * no clock of its own, matching [org.finiteplay.core.session.RestReminderWindow]'s own
 * timestamp-driven style.
 */
@Composable
fun RestBreakDialog(remainingSeconds: Int, onCancel: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("rest_break_dialog"),
        onDismissRequest = {},
        title = { Text(stringResource(R.string.rest_break_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.rest_break_cat),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .testTag("rest_break_illustration"),
                )
                Spacer(Modifier.height(4.dp))
                // The countdown is the whole message, so it is the large element in the accent colour, as a
                // clock face; "Back in 4 minutes 32 seconds" is its spoken form, kept for screen readers.
                val remainingLabel = formatElapsed(remainingSeconds)
                val spoken = stringResource(R.string.rest_break_body, humanDuration(remainingSeconds))
                Text(
                    text = remainingLabel,
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.action.new,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = spoken }
                        .testTag("rest_break_countdown"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("rest_break_dialog_cancel")) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
