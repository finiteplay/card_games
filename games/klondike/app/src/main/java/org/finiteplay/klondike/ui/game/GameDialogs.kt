package org.finiteplay.klondike.ui.game

import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.WinDialog as SharedWinDialog
import org.finiteplay.core.ui.layout.DiscardGameDialog
import org.finiteplay.core.ui.layout.DiscardingAction
import org.finiteplay.core.ui.theme.LocalAppColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.finiteplay.klondike.R
import org.finiteplay.klondike.deal.DifficultyTier
import org.finiteplay.core.ui.layout.SolutionComparison
import org.finiteplay.klondike.debug.DebugDifficultyRatingCapture
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.storage.DifficultyPreference

/**
 * New Game / Replay confirmation, shown only for an unfinished played game
 * (`GameViewModel.needsConfirmation`). The dialog itself is `core:ui`'s — nothing about naming
 * what a player is about to lose is specific to this game; this maps the local
 * [PendingConfirmation] onto it.
 */
@Composable
fun PendingActionConfirmationDialog(
    pending: PendingConfirmation,
    moveCount: Int,
    elapsedSeconds: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DiscardGameDialog(
        action = if (pending == PendingConfirmation.REPLAY) DiscardingAction.REPLAY else DiscardingAction.NEW_GAME,
        moveCount = moveCount,
        elapsedSeconds = elapsedSeconds,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * Win presentation (`DESIGN.md`/`UI_SPEC.md` "Win Presentation"): completion time, move
 * count, and New Game / Replay actions. Personal bests need S2's statistics store and
 * are not shown yet. The celebration is a brief scale/fade flourish, skipped entirely
 * under [skipAnimations] or by tapping anywhere on the dialog.
 */
@Composable
fun WinDialog(
    state: GameState,
    elapsedSeconds: Int,
    skipAnimations: Boolean,
    dealDifficulty: DifficultyTier?,
    /** Length of this deal's certified line, or 0 where it ships without one. */
    solutionMoveCount: Int,
    onNewGame: () -> Unit,
    onReplay: () -> Unit,
) {
    SharedWinDialog(
        moveCount = state.moveCount,
        elapsedSeconds = elapsedSeconds,
        skipAnimations = skipAnimations,
        onNewGame = onNewGame,
        onReplay = onReplay,
    ) {
        SolutionComparison(state.moveCount, solutionMoveCount)
        DebugDifficultyRatingCapture(
            seed = state.seed,
            moveCount = state.moveCount,
            elapsedSeconds = elapsedSeconds,
            solverTier = dealDifficulty,
        )
    }
}


/**
 * Level picker, opened from the level on the status row so choosing a level does not mean
 * going into Settings for it (`UI_SPEC.md` "Status Row").
 *
 * It carries the same warning the New Game confirmation does, and for the same reason:
 * switching level forfeits the deal in play, and an unfinished game that has been played
 * records a loss (`DESIGN.md` "Statistics"). Shown only when there is something to lose —
 * on a raw board the switch costs nothing, so the dialog says nothing about it.
 *
 * A column of rows rather than a row of chips: seven levels do not fit across a dialog, as
 * the six-way debug rating control demonstrated by clipping.
 */
@Composable
fun LevelPickerDialog(
    selected: DifficultyPreference,
    forfeitsMoveCount: Int?,
    onSelect: (DifficultyPreference) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("level_picker_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_difficulty)) },
        text = {
            Column {
                if (forfeitsMoveCount != null) {
                    Text(
                        text = stringResource(R.string.level_switch_forfeits, forfeitsMoveCount),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                for (option in DifficultyPreference.entries) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, onClick = { onSelect(option) })
                            .padding(vertical = 6.dp)
                            .testTag("level_option_${option.name}"),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = stringResource(option.labelRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

/**
 * One result on the win dialog: the figure large, its name small underneath. The same shape
 * the Statistics screen's tiles use, so a win reads as a row from the record it just joined.
 */
