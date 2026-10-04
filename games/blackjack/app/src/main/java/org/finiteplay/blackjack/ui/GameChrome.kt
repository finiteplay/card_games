package org.finiteplay.blackjack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.finiteplay.blackjack.R
import org.finiteplay.blackjack.rules.Decision
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.BoardAction
import org.finiteplay.core.ui.layout.BoardActionBar
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.boardStatusColor
import org.finiteplay.core.ui.layout.boardStatusStyle
import org.finiteplay.core.ui.layout.boardTitleColor
import org.finiteplay.core.ui.layout.boardTitleStyle
import org.finiteplay.core.ui.theme.LocalAppColors

/**
 * The status row (`UI_SPEC.md` "Status row"): the title, with Help and Statistics in the trailing
 * corner. The bankroll and the bet are not here but in the chips HUD above the action bar, where
 * the thumb is.
 */
@Composable
internal fun StatusRow(
    onHelp: () -> Unit,
    onStatistics: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.game_title),
            style = boardTitleStyle(),
            fontWeight = FontWeight.Bold,
            color = boardTitleColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("game_title"),
        )
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

/** The discarded-save banner: the round could not be read and was voided, costing nothing. Dismissible. */
@Composable
internal fun RecoveryNotice(onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().testTag("recovery_notice")) {
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
            TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_dismiss)) }
        }
    }
}

/** What the action bar needs, so it is built from state and not from the view model. */
data class BarState(
    val table: TableState,
    val legal: Set<Decision>,
    val needsReset: Boolean,
    val canStepBetDown: Boolean,
    val canStepBetUp: Boolean,
    val busy: Boolean,
    val canHint: Boolean = false,
    /** The decision the hint recommends, whose button is highlighted. */
    val suggested: Decision? = null,
)

/**
 * The action bar for the current phase (`UI_SPEC.md` "Action Bar"): exactly the engine's legal
 * decisions plus Settings, and between rounds the bet stepper and Deal (or Reset). An illegal
 * decision is not drawn rather than drawn disabled; only the bet buttons, whose bar has a fixed
 * shape between rounds, are shown disabled at their limits.
 */
@Composable
internal fun ActionBar(
    orientation: BoardOrientation,
    mirrored: Boolean,
    bar: BarState,
    selectedBetStep: Int,
    onDecision: (Decision) -> Unit,
    onBet: (Int) -> Unit,
    onDeal: () -> Unit,
    onReset: () -> Unit,
    onHint: () -> Unit,
    onSettings: () -> Unit,
) {
    val accents = LocalAppColors.current.action
    val actions = buildList {
        add(action(CoreR.string.action_settings, Icons.Filled.Settings, accents.settings, true, "action_settings", onSettings))
        when (bar.table) {
            TableState.LOADING -> Unit
            TableState.INSURANCE -> {
                add(action(R.string.action_insure, Icons.Filled.Shield, accents.statistics, !bar.busy, "action_insure") { onDecision(Decision.TAKE_INSURANCE) })
                add(action(R.string.action_decline, Icons.Filled.Block, accents.undo, !bar.busy, "action_decline") { onDecision(Decision.DECLINE_INSURANCE) })
            }
            TableState.PLAYING -> {
                if (Decision.HIT in bar.legal) add(action(R.string.action_hit, Icons.Filled.TouchApp, accents.new, !bar.busy, "action_hit") { onDecision(Decision.HIT) })
                if (Decision.STAND in bar.legal) add(action(R.string.action_stand, Icons.Filled.PanTool, accents.settings, !bar.busy, "action_stand") { onDecision(Decision.STAND) })
                if (Decision.DOUBLE in bar.legal) add(action(R.string.action_double, Icons.Filled.Layers, accents.statistics, !bar.busy, "action_double") { onDecision(Decision.DOUBLE) })
                if (Decision.SPLIT in bar.legal) add(action(R.string.action_split, Icons.Filled.CallSplit, accents.replay, !bar.busy, "action_split") { onDecision(Decision.SPLIT) })
            }
            TableState.IDLE, TableState.SETTLED -> {
                if (bar.needsReset) {
                    add(action(R.string.action_reset_chips, Icons.Filled.Refresh, accents.new, !bar.busy, "action_reset", onReset))
                } else {
                    add(actionWithArg(R.string.action_bet_down, selectedBetStep, Icons.Filled.Remove, accents.statistics, bar.canStepBetDown, "action_bet_down") { onBet(-1) })
                    add(actionWithArg(R.string.action_bet_up, selectedBetStep, Icons.Filled.Add, accents.statistics, bar.canStepBetUp, "action_bet_up") { onBet(+1) })
                    val label = if (bar.table == TableState.SETTLED) R.string.action_next_round else R.string.action_deal
                    add(action(label, Icons.Filled.PlayArrow, accents.new, !bar.busy, "action_deal", onDeal))
                }
            }
        }
    }
    val withHint = if (bar.canHint) {
        actions + action(CoreR.string.action_hint, Icons.Filled.Lightbulb, accents.hint, true, "action_hint", onHint)
    } else {
        actions
    }
    val suggestedTag = bar.suggested?.let(::decisionTag)
    val marked = withHint.map { if (it.testTag == suggestedTag) it.copy(highlighted = true) else it }
    BoardActionBar(orientation = orientation, mirrored = mirrored, actions = marked)
}

/** The tag of the button that plays [decision]: the Hint answers by highlighting it. */
private fun decisionTag(decision: Decision): String = when (decision) {
    Decision.HIT -> "action_hit"
    Decision.STAND -> "action_stand"
    Decision.DOUBLE -> "action_double"
    Decision.SPLIT -> "action_split"
    Decision.TAKE_INSURANCE -> "action_insure"
    Decision.DECLINE_INSURANCE -> "action_decline"
}

@Composable
private fun action(label: Int, icon: ImageVector, color: Color, enabled: Boolean, tag: String, onClick: () -> Unit) = BoardAction(
    label = stringResource(label),
    icon = icon,
    accentColor = color,
    enabled = enabled,
    onClick = onClick,
    testTag = tag,
)

@Composable
private fun actionWithArg(label: Int, arg: Int, icon: ImageVector, color: Color, enabled: Boolean, tag: String, onClick: () -> Unit) = BoardAction(
    label = stringResource(label, arg),
    icon = icon,
    accentColor = color,
    enabled = enabled,
    onClick = onClick,
    testTag = tag,
)
