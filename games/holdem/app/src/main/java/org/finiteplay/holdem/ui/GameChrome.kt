package org.finiteplay.holdem.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.BoardAction
import org.finiteplay.core.ui.layout.BoardActionBar
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.BoardStatusIcon
import org.finiteplay.core.ui.layout.boardStatusColor
import org.finiteplay.core.ui.layout.boardStatusStyle
import org.finiteplay.core.ui.layout.holdToRepeat
import org.finiteplay.core.ui.theme.LocalAppColors
import org.finiteplay.holdem.R
import org.finiteplay.holdem.rules.BlindSchedule
import org.finiteplay.holdem.rules.LegalActions

/**
 * The status row (`UI_SPEC.md` "Status row"): the blinds in bold, the hand and the next level beneath,
 * and Help, Statistics and — while a tournament is in progress — Leave in the trailing corner.
 */
@Composable
internal fun StatusRow(table: TableModel, onHelp: () -> Unit, onStatistics: () -> Unit, onLeave: () -> Unit) {
    val level = table.blinds.level
    val next = BlindSchedule.blinds(level + 1)
    val handsLeft = BlindSchedule.handsLeftAtLevel(table.handNumber)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.status_blinds, table.blinds.small, table.blinds.big),
                style = boardStatusStyle(),
                fontWeight = FontWeight.Bold,
                color = boardStatusColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("status_blinds"),
            )
            Text(
                text = pluralStringResource(R.plurals.status_hand_next, handsLeft, table.handNumber, next.small, next.big, handsLeft),
                style = boardStatusStyle(),
                color = boardStatusColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("status_hand"),
            )
        }
        BoardStatusIcon(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.help_title), LocalAppColors.current.action.replay, "help_button", onHelp)
        BoardStatusIcon(Icons.Filled.BarChart, stringResource(CoreR.string.statistics_title), LocalAppColors.current.action.statistics, "statistics_button", onStatistics)
        if (table.stage != Stage.TOURNAMENT_OVER) {
            BoardStatusIcon(Icons.Filled.Flag, stringResource(R.string.action_leave_tournament), LocalAppColors.current.action.undo, "leave_button", onLeave)
        }
    }
}

/** What the action bar needs, so it is built from state and not from the view model. */
internal data class BarState(
    val stage: Stage,
    val legal: LegalActions?,
    val sizing: Sizing?,
    val busy: Boolean,
)

/**
 * The action bar for the current state (`UI_SPEC.md` "Action Bar"): Settings, and on the player's turn
 * exactly the engine's legal actions, each with its amount in the label. An illegal action is never
 * drawn rather than drawn disabled. Hint is not offered yet: it arrives with the opponents (`EXECUTION_PLAN.md` H7).
 */
@Composable
internal fun ActionBar(
    orientation: BoardOrientation,
    mirrored: Boolean,
    bar: BarState,
    onAction: (org.finiteplay.holdem.rules.Action) -> Unit,
    onNextHand: () -> Unit,
    onNewTournament: () -> Unit,
    onSettings: () -> Unit,
) {
    val accents = LocalAppColors.current.action
    val legal = bar.legal
    val actions = buildList {
        add(action(CoreR.string.action_settings, Icons.Filled.Settings, accents.settings, true, "action_settings", onSettings))
        when (bar.stage) {
            Stage.YOUR_TURN -> if (legal != null) {
                val live = !bar.busy
                if (legal.canFold) add(action(R.string.action_fold, Icons.Filled.Close, accents.undo, live, "action_fold") { onAction(org.finiteplay.holdem.rules.Action.Fold) })
                if (legal.canCheck) add(action(R.string.action_check, Icons.Filled.Check, accents.settings, live, "action_check") { onAction(org.finiteplay.holdem.rules.Action.Check) })
                legal.callAmount?.let { call ->
                    val label = if (legal.toCall > legal.stack) stringResource(R.string.action_call_all_in) else stringResource(R.string.action_call, call)
                    add(boardAction(label, Icons.Filled.TouchApp, accents.new, live, "action_call") { onAction(org.finiteplay.holdem.rules.Action.Call) })
                }
                if (legal.bet != null) {
                    val amount = bar.sizing?.amount ?: legal.bet!!.first
                    add(boardAction(stringResource(R.string.action_bet, amount), Icons.AutoMirrored.Filled.TrendingUp, accents.statistics, live, "action_bet") { bar.sizing?.let { onAction(it.action()) } })
                }
                if (legal.raise != null) {
                    val amount = bar.sizing?.amount ?: legal.raise!!.first
                    add(boardAction(stringResource(R.string.action_raise, amount), Icons.AutoMirrored.Filled.TrendingUp, accents.statistics, live, "action_raise") { bar.sizing?.let { onAction(it.action()) } })
                }
                if (legal.allInTo != null) {
                    add(boardAction(stringResource(R.string.action_all_in, legal.stack), Icons.Filled.Bolt, accents.hint, live, "action_all_in") { onAction(org.finiteplay.holdem.rules.Action.AllIn) })
                }
            }
            Stage.HAND_OVER -> add(action(R.string.action_next_hand, Icons.Filled.PlayArrow, accents.new, true, "action_next_hand", onNextHand))
            Stage.TOURNAMENT_OVER -> add(action(R.string.action_new_tournament, Icons.Filled.Refresh, accents.new, true, "action_new_tournament", onNewTournament))
            Stage.OPPONENT_TURN, Stage.SHOWDOWN -> Unit
        }
    }
    BoardActionBar(orientation = orientation, mirrored = mirrored, actions = actions)
}

@Composable
private fun action(label: Int, icon: ImageVector, color: Color, enabled: Boolean, tag: String, onClick: () -> Unit) =
    boardAction(stringResource(label), icon, color, enabled, tag, onClick)

private fun boardAction(label: String, icon: ImageVector, color: Color, enabled: Boolean, tag: String, onClick: () -> Unit) = BoardAction(
    label = label,
    icon = icon,
    accentColor = color,
    enabled = enabled,
    onClick = onClick,
    testTag = tag,
)

/**
 * The sizing controls (`UI_SPEC.md` "The sizing row"): − · amount · + · ½ pot · Pot, five equal
 * controls in a row, or stacked in the landscape column. − and + repeat while held, exactly as
 * Blackjack's bet stepper does (`BoardAction.repeatOnHold`'s own modifier).
 */
@Composable
internal fun SizingControls(
    sizing: Sizing,
    horizontal: Boolean,
    onStep: (Int) -> Unit,
    onPreset: (Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val minus = stringResource(R.string.sizing_minus_desc, sizing.step)
    val plus = stringResource(R.string.sizing_plus_desc, sizing.step)
    val amountDesc = if (sizing.raise) stringResource(R.string.sizing_raise_desc, sizing.amount) else stringResource(R.string.sizing_bet_desc, sizing.amount)
    val controls: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> SizingButton(m, "sizing_minus", minus, sizing.canStepDown, repeat = true, onClick = { onStep(-1) }) { Icon(Icons.Filled.Remove) } },
        { m -> SizingAmount(m, sizing.amount, amountDesc) },
        { m -> SizingButton(m, "sizing_plus", plus, sizing.canStepUp, repeat = true, onClick = { onStep(+1) }) { Icon(Icons.Filled.Add) } },
        { m -> SizingButton(m, "sizing_half_pot", stringResource(R.string.sizing_half_pot), true, onClick = { onPreset(Preset.HALF_POT) }) { Label(stringResource(R.string.sizing_half_pot)) } },
        { m -> SizingButton(m, "sizing_pot", stringResource(R.string.sizing_pot), true, onClick = { onPreset(Preset.POT) }) { Label(stringResource(R.string.sizing_pot)) } },
    )
    if (horizontal) {
        Row(modifier = modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            controls.forEach { it(Modifier.weight(1f)) }
        }
    } else {
        Column(modifier = modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            controls.forEach { it(Modifier.fillMaxWidth().height(48.dp)) }
        }
    }
}

@Composable
private fun Icon(icon: ImageVector) {
    androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
}

@Composable
private fun SizingButton(
    modifier: Modifier,
    tag: String,
    description: String,
    enabled: Boolean,
    repeat: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val click = when {
        !enabled -> Modifier
        repeat -> Modifier.holdToRepeat(description, onClick)
        else -> Modifier.clickable(onClickLabel = description, onClick = onClick)
    }
    Surface(
        modifier = modifier.testTag(tag).then(click),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (enabled) 0.55f else 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.8f else 0.3f)),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** The amount: a figure, never a text field; tapping it does nothing. */
@Composable
private fun SizingAmount(modifier: Modifier, amount: Int, description: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("sizing_amount")
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Text(
            text = "%,d".format(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
