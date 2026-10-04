package org.finiteplay.core.ui.layout

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.R

/** Which action is about to discard the game in progress. */
enum class DiscardingAction { NEW_GAME, REPLAY }

/**
 * Confirms an action that throws away the game in progress, naming what will be lost.
 *
 * Only worth showing for a game that has actually been played and is not yet finished — a player
 * who has made no move has nothing to lose, and one who has won is not losing anything either.
 * Deciding that is the caller's; this only asks.
 *
 * The moves and elapsed time are in the body rather than a bare "are you sure" because the whole
 * question is whether *this particular* game is worth keeping, and a player cannot answer that
 * without seeing how far in they are.
 */
@Composable
fun DiscardGameDialog(
    action: DiscardingAction,
    moveCount: Int,
    elapsedSeconds: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isNewGame = action == DiscardingAction.NEW_GAME
    AlertDialog(
        modifier = Modifier.testTag("pending_action_dialog"),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isNewGame) R.string.pending_new_game_action else R.string.pending_replay_action))
        },
        text = { Text(stringResource(R.string.pending_body, moveCount, formatElapsed(elapsedSeconds))) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (isNewGame) R.string.action_new_game else R.string.action_replay))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
