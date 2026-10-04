package org.finiteplay.klondike.ui.game

import org.finiteplay.core.ui.R as CoreR
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.finiteplay.klondike.R

/**
 * "No legal move and no productive stock action" notice (`docs/games/klondike/UI_SPEC.md` "Screens and
 * States" > "No moves"). A stuck board is not itself a loss — undo can still recover
 * it (`DESIGN.md` "Game Lifecycle") — so this offers Undo and New Game rather than
 * ending the game on its own.
 */
@Composable
fun StuckNotice(canUndo: Boolean, onUndo: () -> Unit, onNewGame: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("stuck_notice"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stuck_title)) },
        text = { Text(stringResource(R.string.stuck_body)) },
        confirmButton = {
            TextButton(onClick = onUndo, enabled = canUndo) { Text(stringResource(CoreR.string.action_undo)) }
        },
        dismissButton = {
            TextButton(onClick = onNewGame) { Text(stringResource(CoreR.string.action_new_game)) }
        },
    )
}
