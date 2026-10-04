package org.finiteplay.freecell.ui.game

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.SolutionComparison
import org.finiteplay.core.ui.layout.WinDialog as SharedWinDialog
import org.finiteplay.freecell.R
import kotlin.time.Duration.Companion.milliseconds

/**
 * FreeCell's win presentation: the shared figures plus a personal-best line, shown only where a
 * best actually exists (`docs/games/freecell/UI_SPEC.md` "Win Presentation"). One pool, no
 * per-mode split to pick a best from (`RULES.md` "Free cells and difficulty").
 */
@Composable
fun WinDialog(
    moveCount: Int,
    elapsedSeconds: Int,
    skipAnimations: Boolean,
    bestElapsedMillis: Long?,
    bestMoveCount: Int?,
    /** Length of this deal's certified line, or 0 where it ships without one. */
    solutionMoveCount: Int,
    onNewGame: () -> Unit,
    onReplay: () -> Unit,
) {
    SharedWinDialog(
        moveCount = moveCount,
        elapsedSeconds = elapsedSeconds,
        skipAnimations = skipAnimations,
        onNewGame = onNewGame,
        onReplay = onReplay,
    ) {
        SolutionComparison(moveCount, solutionMoveCount)
        if (bestElapsedMillis != null && bestMoveCount != null) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                stringResource(
                    CoreR.string.stat_best,
                    formatElapsed(bestElapsedMillis.milliseconds.inWholeSeconds.toInt()),
                    stringResource(CoreR.string.stat_best_moves, bestMoveCount),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * "No legal move" notice (`docs/games/freecell/RULES.md` "Game Lifecycle" > stuck). A stuck board
 * is not itself a loss — undo can still recover it — so this offers Undo and New Game rather than
 * ending the game on its own, mirroring Klondike's own `StuckNotice` minus the stock it has none
 * of.
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

/**
 * The Loading, No-solution, and Inconclusive hint outcomes (`docs/games/freecell/UI_SPEC.md`
 * "Hint"): a brief notice, dismissible except while still searching, that auto-dismisses after
 * [HINT_NOTICE_AUTO_DISMISS] if the player leaves it alone. Guidance itself has no row here — the
 * board pulses the suggested cards directly instead — so this returns nothing for
 * [HintUiState.Hidden] and [HintUiState.Guided].
 */
@Composable
internal fun HintNoticeRow(hintState: HintUiState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val text = when (hintState) {
        HintUiState.Loading -> stringResource(R.string.hint_thinking)
        HintUiState.NoSolution -> stringResource(R.string.hint_no_solution)
        HintUiState.Inconclusive -> stringResource(R.string.hint_inconclusive)
        else -> return
    }
    Surface(
        modifier = modifier.padding(24.dp).testTag("hint_notice"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (hintState != HintUiState.Loading) {
                TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_dismiss)) }
            }
        }
    }
}
