package org.finiteplay.core.ui.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.session.solutionRatioPercent
import org.finiteplay.core.ui.R
import org.finiteplay.core.ui.theme.LocalAppColors

/**
 * The win presentation every game here shares: a headline, an optional short flourish, the two
 * figures a player actually wants — how many moves and how long — and the two things worth
 * offering next, another deal or the same one again.
 *
 * Anything a particular game can say beyond that (a difficulty tier, a per-mode best) goes in
 * [extraContent], which sits under the figures; a comparison against a certified solution is
 * [SolutionComparison], which every game's win dialog places there.
 *
 * [skipAnimations] suppresses the flourish, honouring the reduced-motion setting
 * (`docs/PLATFORM.md` "Accessibility"). The dialog cannot be dismissed by tapping away: it is the
 * end of a game, and the two buttons are the only meaningful answers.
 */
@Composable
fun WinDialog(
    moveCount: Int,
    elapsedSeconds: Int,
    skipAnimations: Boolean,
    onNewGame: () -> Unit,
    onReplay: () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    AlertDialog(
        modifier = Modifier.testTag("win_dialog"),
        onDismissRequest = {},
        title = {
            Text(
                stringResource(R.string.win_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = LocalAppColors.current.action.hint,
            )
        },
        text = {
            Column(Modifier.padding(top = 4.dp)) {
                if (!skipAnimations) WinFlourish()

                // Figures rather than sentences: the number is what is being reported, so it
                // carries the size and the colour.
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    WinFigure(stringResource(R.string.stat_moves_title), moveCount.toString())
                    WinFigure(stringResource(R.string.stat_completion_time_title), formatElapsed(elapsedSeconds))
                }
                extraContent()
            }
        },
        confirmButton = { TextButton(onClick = onNewGame) { Text(stringResource(R.string.action_new_game)) } },
        dismissButton = { TextButton(onClick = onReplay) { Text(stringResource(R.string.action_replay)) } },
    )
}

@Composable
private fun WinFlourish() {
    // A minimal, honest reading of "a skippable celebration lasting no more than three seconds":
    // a short scale-in on a suit line, not a choreographed multi-card animation.
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    val scale by animateFloatAsState(
        targetValue = if (grown) 1f else 0.6f,
        animationSpec = tween(durationMillis = 400),
        label = "winFlourishScale",
    )
    Text(
        text = "♠ ♥ ♣ ♦",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 8.dp).scale(scale),
    )
}

@Composable
private fun WinFigure(label: String, value: String) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.action.statistics,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The player's move count beside the certified solution's, as a signed difference rather than a
 * ratio: "+7%" says at a glance that seven per cent more moves were spent, where "107%" has to be
 * read and subtracted first. Zero gets its own words rather than a bare "+0%".
 *
 * Draws nothing where the deal ships no solution ([solutionMoveCount] of 0) — an absent
 * comparison is better than a fabricated one.
 */
@Composable
fun ColumnScope.SolutionComparison(moveCount: Int, solutionMoveCount: Int) {
    if (solutionMoveCount <= 0) return
    val delta = solutionRatioPercent(moveCount, solutionMoveCount) - 100
    val deltaText = when {
        delta > 0 -> stringResource(R.string.win_delta_more, delta)
        delta < 0 -> stringResource(R.string.win_delta_fewer, -delta)
        else -> stringResource(R.string.win_delta_match)
    }
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    Text(
        stringResource(R.string.win_ai_solution, solutionMoveCount),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.win_your_moves, moveCount, deltaText),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (delta > 0) MaterialTheme.colorScheme.onSurface else LocalAppColors.current.action.new,
        modifier = Modifier.testTag("win_vs_solution"),
    )
}
