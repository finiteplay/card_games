package org.finiteplay.freecell.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.finiteplay.core.session.formatElapsed
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.BoardAction
import org.finiteplay.core.ui.layout.BoardActionBar
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.boardStatusColor
import org.finiteplay.core.ui.layout.boardStatusStyle
import org.finiteplay.core.ui.layout.boardTitleColor
import org.finiteplay.core.ui.layout.boardTitleStyle
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.freecell.R

/**
 * The game's name and what it currently reads: title on the leading edge, the hand number and
 * the moves/time centered in whatever space is left, Help and Statistics in the trailing corner
 * — they describe the game rather than act on it, the same placement Spider's own status row
 * gives them (`GameChrome.kt`). The hand number is FreeCell's own stable identity for the active
 * seed (`FreeCellViewModel.dealNumber`), the same shape as Klondike's and Spider's own numbered
 * deals but with no difficulty or suit-count qualifier to pair it with, since FreeCell has
 * neither. It is a link to the deal picker (`docs/games/freecell/UI_SPEC.md` "Status Row").
 */
@Composable
internal fun StatusRow(
    dealNumber: Int?,
    moves: Int,
    elapsedSeconds: Int,
    won: Boolean,
    onSelectDeal: () -> Unit,
    onHelp: () -> Unit,
    onStatistics: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.game_title),
            style = boardTitleStyle(),
            fontWeight = FontWeight.Bold,
            color = boardTitleColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("game_title"),
        )
        val statusStyle = boardStatusStyle()
        // Reserved on the box, not by padding the text: the row must not grow or shrink between
        // the won state (shorter) and the normal one, or between a hand number being known and
        // not yet known, the same reasoning Klondike's and Spider's own status rows give theirs.
        // Always two lines' worth, whether or not a number is showing yet, so the board below
        // never jumps once the catalog finishes loading.
        val reservedHeight = with(LocalDensity.current) { (statusStyle.lineHeight * 2).toDp() }
        Box(
            modifier = Modifier.weight(1f).heightIn(min = reservedHeight).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val counters = if (won) {
                stringResource(CoreR.string.status_won)
            } else {
                stringResource(CoreR.string.status_moves_time, moves, formatElapsed(elapsedSeconds))
            }
            // The hand number is a link, not merely text: it is the shortest route to choosing
            // another deal, and Statistics is not where a player looks for one.
            val handLink = LinkAnnotation.Clickable(
                tag = "deal",
                styles = TextLinkStyles(SpanStyle(color = LocalAppColors.current.action.statistics, fontWeight = FontWeight.Bold)),
            ) { onSelectDeal() }
            val text = buildAnnotatedString {
                if (dealNumber != null) {
                    withLink(handLink) { append(stringResource(R.string.status_hand_number, dealNumber)) }
                    append("\n")
                }
                append(counters)
            }
            Text(
                text = text,
                style = statusStyle,
                color = boardStatusColor(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("status_row"),
            )
        }
        StatusIcon(Icons.AutoMirrored.Filled.HelpOutline, R.string.help_title, LocalAppColors.current.action.replay, "help_button", onHelp)
        StatusIcon(Icons.Filled.BarChart, CoreR.string.statistics_title, LocalAppColors.current.action.statistics, "statistics_button", onStatistics)
    }
}

@Composable
private fun StatusIcon(icon: ImageVector, contentDescriptionRes: Int, tint: Color, tag: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp).testTag(tag)) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The discarded-save banner (`docs/games/freecell/EXECUTION_PLAN.md` F3a), dismissible. */
@Composable
internal fun RecoveryNotice(onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recovery_notice),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(CoreR.string.action_dismiss))
            }
        }
    }
}

/** The five actions this game has: Settings, Replay, New Game, Hint, Undo. */
@Composable
internal fun ActionBar(
    orientation: BoardOrientation,
    canUndo: Boolean,
    mirrored: Boolean = false,
    onUndo: () -> Unit,
    onReplay: () -> Unit,
    onNewGame: () -> Unit,
    onHint: () -> Unit,
    onSettings: () -> Unit,
) {
    val accents = LocalAppColors.current.action
    BoardActionBar(
        orientation = orientation,
        mirrored = mirrored,
        actions = listOf(
            settingsAction(accents.settings, onSettings),
            replayAction(accents.replay, onReplay),
            newAction(accents.new, onNewGame),
            hintAction(accents.hint, onHint),
            undoAction(accents.undo, canUndo, onUndo),
        ),
    )
}

@Composable
private fun hintAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_hint),
    icon = Icons.Filled.Lightbulb,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_hint",
)

@Composable
private fun settingsAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_settings),
    icon = Icons.Filled.Settings,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_settings",
)

@Composable
private fun replayAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_replay),
    icon = Icons.Filled.Replay,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_replay",
)

@Composable
private fun newAction(color: Color, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_new),
    icon = Icons.Filled.Add,
    accentColor = color,
    enabled = true,
    onClick = onClick,
    testTag = "action_new",
)

@Composable
private fun undoAction(color: Color, enabled: Boolean, onClick: () -> Unit) = BoardAction(
    label = stringResource(CoreR.string.action_undo),
    icon = Icons.AutoMirrored.Filled.Undo,
    accentColor = color,
    enabled = enabled,
    onClick = onClick,
    testTag = "action_undo",
)
