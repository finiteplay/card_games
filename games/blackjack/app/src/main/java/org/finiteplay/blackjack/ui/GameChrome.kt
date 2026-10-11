package org.finiteplay.blackjack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.finiteplay.blackjack.R
import org.finiteplay.blackjack.rules.Decision
import org.finiteplay.core.ui.card.rankSpokenLabel
import org.finiteplay.blackjack.rules.BlackjackHintReason
import org.finiteplay.blackjack.rules.BlackjackAdvice
import org.finiteplay.core.ui.R as CoreR
import org.finiteplay.core.ui.layout.BoardAction
import org.finiteplay.core.ui.layout.BoardActionBar
import org.finiteplay.core.ui.layout.BoardActionRail
import org.finiteplay.core.ui.layout.BoardOrientation
import org.finiteplay.core.ui.layout.BoardStatusIcon
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
    showIcons: Boolean = true,
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
        if (showIcons) {
            StatusIcon(Icons.AutoMirrored.Filled.HelpOutline, R.string.help_title, LocalAppColors.current.action.replay, "help_button", onHelp)
            StatusIcon(Icons.Filled.BarChart, CoreR.string.statistics_title, LocalAppColors.current.action.statistics, "statistics_button", onStatistics)
        }
    }
}

/**
 * Landscape's chrome rail (`UI_SPEC.md` "Table, landscape"): Help, Statistics and Settings stacked on
 * the edge away from the holding hand (Settings at the bottom, the others at the top), so the rail beside the thumb holds only the round's actions.
 */
@Composable
internal fun ChromeRail(
    onHelp: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
) {
    val accents = LocalAppColors.current.action
    BoardActionRail(
        topActions = listOf(
            action(R.string.help_title, Icons.AutoMirrored.Filled.HelpOutline, accents.replay, true, "help_button", onHelp),
            action(CoreR.string.statistics_title, Icons.Filled.BarChart, accents.statistics, true, "statistics_button", onStatistics),
        ),
        bottomActions = listOf(action(CoreR.string.action_settings, Icons.Filled.Settings, accents.settings, true, "action_settings", onSettings)),
    )
}

@Composable
private fun StatusIcon(icon: ImageVector, contentDescriptionRes: Int, tint: Color, tag: String, onClick: () -> Unit) =
    BoardStatusIcon(icon, stringResource(contentDescriptionRes), tint, tag, onClick)

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
    /** False in landscape, where Settings lives in the chrome rail. */
    includeSettings: Boolean = true,
) {
    val accents = LocalAppColors.current.action
    val actions = buildList {
        if (includeSettings) add(action(CoreR.string.action_settings, Icons.Filled.Settings, accents.settings, true, "action_settings", onSettings))
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
    // Landscape reads top to bottom with the always-present buttons last, anchored to the bottom, so
    // the rarer Split and Double come and go above Stand, Hit and Hint without moving them.
    val ordered = if (orientation == BoardOrientation.LANDSCAPE && bar.table == TableState.PLAYING) {
        val core = listOf("action_stand", "action_hit")
        val extras = marked.filter { it.testTag == "action_split" || it.testTag == "action_double" }
        val hint = marked.filter { it.testTag == "action_hint" }
        val rest = marked - extras.toSet() - hint.toSet()
        extras.sortedBy { if (it.testTag == "action_split") 0 else 1 } +
            rest.sortedBy { core.indexOf(it.testTag) } + hint
    } else {
        marked
    }
    BoardActionBar(orientation = orientation, mirrored = mirrored, actions = ordered, fitHeight = true)
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
    // The bet steppers repeat while held: ten steps of 10 to reach 100 is too many taps.
    repeatOnHold = true,
    label = stringResource(label, arg),
    icon = icon,
    accentColor = color,
    enabled = enabled,
    onClick = onClick,
    testTag = tag,
)

/** A decision in the words of its button. */
@Composable
private fun decisionLabel(decision: Decision): String = stringResource(
    when (decision) {
        Decision.HIT -> R.string.action_hit
        Decision.STAND -> R.string.action_stand
        Decision.DOUBLE -> R.string.action_double
        Decision.SPLIT -> R.string.action_split
        Decision.TAKE_INSURANCE -> R.string.action_insure
        Decision.DECLINE_INSURANCE -> R.string.action_decline
    },
)

private fun percent(p: Double): Int = Math.round(p * 100).toInt()

/** The coach's sentence for a recommendation: why, in the numbers of this spot (`UI_SPEC.md` "Hint Presentation"). */
@Composable
internal fun adviceReasonText(advice: BlackjackAdvice): String {
    // A number card reads as its number ("the dealer's 3"); an Ace or a face card by name.
    val up = if (advice.dealerUp.value in 2..10) advice.dealerUp.value.toString() else rankSpokenLabel(advice.dealerUp)
    val total = advice.total
    val dealerBust = percent(advice.odds.dealerBust)
    val yourBust = percent(advice.odds.yourBust)
    val standWin = percent(advice.odds.standWin)
    return when (advice.reason) {
        BlackjackHintReason.STAND_STRONG -> stringResource(R.string.hint_reason_stand_strong, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.STAND_DEALER_WEAK -> stringResource(R.string.hint_reason_stand_dealer_weak, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.HIT_DEALER_STRONG -> stringResource(R.string.hint_reason_hit_dealer_strong, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.HIT_CANNOT_BUST -> stringResource(R.string.hint_reason_hit_cannot_bust, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.HIT_SOFT -> stringResource(R.string.hint_reason_hit_soft, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.DOUBLE -> stringResource(R.string.hint_reason_double, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.SPLIT_ACES -> stringResource(R.string.hint_reason_split_aces, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.SPLIT_EIGHTS -> stringResource(R.string.hint_reason_split_eights, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.SPLIT_PAIR -> stringResource(R.string.hint_reason_split_pair, up, total, dealerBust, yourBust, standWin)
        BlackjackHintReason.DECLINE_INSURANCE -> stringResource(R.string.hint_reason_decline_insurance, up, total, dealerBust, yourBust, standWin)
    }
}

/**
 * The Hint's notice (`UI_SPEC.md` "Hint Presentation"): why the highlighted button is the one, in a
 * sentence, then the odds behind it.
 */
@Composable
internal fun HintNotice(advice: BlackjackAdvice, modifier: Modifier = Modifier) {
    val reason = adviceReasonText(advice)
    val figures = when {
        advice.reason == BlackjackHintReason.DECLINE_INSURANCE -> null
        advice.odds.yourBust > 0.0 -> stringResource(R.string.hint_figures, percent(advice.odds.dealerBust), percent(advice.odds.yourBust))
        else -> stringResource(R.string.hint_figures_dealer_only, percent(advice.odds.dealerBust))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.72f),
        modifier = modifier.padding(horizontal = 12.dp).testTag("hint_notice").semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(reason, figures).joinToString(" ")
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("hint_reason"),
            )
            if (figures != null) {
                Text(
                    text = figures,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFCC80),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 1.dp).testTag("hint_figures"),
                )
            }
        }
    }
}

/**
 * The look back at a round played after a Hint (`UI_SPEC.md` "Hint Review"): drawn until the next
 * round deals, it separates the decision from the result, since a good decision can lose.
 */
@Composable
internal fun RoundReviewNotice(review: RoundReview, modifier: Modifier = Modifier) {
    val advice = review.advice
    val suggested = decisionLabel(advice.decision)
    val reason = adviceReasonText(advice)
    val main = when (review.kind) {
        ReviewKind.FOLLOWED_WON -> stringResource(R.string.review_followed_won)
        ReviewKind.FOLLOWED_LOST -> stringResource(R.string.review_followed_lost)
        ReviewKind.FOLLOWED_PUSH -> stringResource(R.string.review_followed_push)
        ReviewKind.IGNORED_WON -> stringResource(R.string.review_ignored_won, suggested, reason)
        ReviewKind.IGNORED_LOST -> stringResource(R.string.review_ignored_lost, suggested, reason)
        ReviewKind.IGNORED_PUSH -> stringResource(R.string.review_ignored_push, suggested, reason)
    }
    val details = review.details.map {
        when (it) {
            is ReviewDetail.DealerFinished -> stringResource(R.string.review_dealer_finished, it.total)
            ReviewDetail.DealerBusted -> stringResource(R.string.review_dealer_busted)
            ReviewDetail.DealerBlackjack -> stringResource(R.string.review_dealer_blackjack)
            is ReviewDetail.YouBusted -> stringResource(R.string.review_you_busted, it.total)
        }
    }
    val title = stringResource(R.string.review_title)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f)),
        modifier = modifier.padding(horizontal = 12.dp).testTag("round_review").semantics(mergeDescendants = true) {
            contentDescription = title + ". " + main + " " + details.joinToString(" ")
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
            Text(
                text = main,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp).testTag("review_main"),
            )
            for (line in details) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
