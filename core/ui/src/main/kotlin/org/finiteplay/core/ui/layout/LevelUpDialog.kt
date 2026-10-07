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
 * Asked once a player has won [wins] games at their level (`docs/PLATFORM.md` "Levels"): move up to
 * the next? [currentLevel] and [nextLevel] are the game's own names for them, already in the
 * player's language. Switching starts a new game at the next level; staying starts one at the same.
 * Either way a new game follows, since the question only ever interrupts the player asking for one.
 *
 * Dismissing it (Back, or a tap outside) is staying: the question is an offer, not a gate.
 */
@Composable
fun LevelUpDialog(
    wins: Int,
    currentLevel: String,
    nextLevel: String,
    onSwitch: () -> Unit,
    onStay: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("level_up_dialog"),
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.level_up_title)) },
        text = { Text(stringResource(R.string.level_up_body, wins, currentLevel, nextLevel)) },
        confirmButton = {
            TextButton(onClick = onSwitch, modifier = Modifier.testTag("level_up_switch")) {
                Text(stringResource(R.string.level_up_switch, nextLevel))
            }
        },
        dismissButton = {
            TextButton(onClick = onStay, modifier = Modifier.testTag("level_up_stay")) {
                Text(stringResource(R.string.level_up_stay))
            }
        },
    )
}
