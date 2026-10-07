package org.finiteplay.blackjack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.finiteplay.blackjack.R
import org.finiteplay.core.ui.chips.ChipStack

/** The number a count reads as while it rolls toward [target]; instantly the target when motion is off. */
@Composable
internal fun rollingCount(target: Int, animate: Boolean): Int =
    if (animate) animateIntAsState(target, animationSpec = tween(durationMillis = 900), label = "rollingCount").value else target

/**
 * The bankroll and the bet as two big pills (`UI_SPEC.md` "Status row"). The bankroll counts up or
 * down to its new figure when a round's result is shown, and the bet pops when it changes. It states
 * no result of its own: the round's signed net is on the result mark, once. The text
 * the old status row printed is still this row's accessibility description, tagged `status_row`.
 */
@Composable
internal fun ChipsHud(bankroll: Int, bet: Int, animate: Boolean, modifier: Modifier = Modifier) {
    val spoken = stringResource(R.string.status_chips, bankroll) + "\n" + stringResource(R.string.status_bet, bet)
    val shownBankroll = rollingCount(bankroll, animate)
    val pop = remember { Animatable(1f) }
    LaunchedEffect(bet) {
        if (animate) {
            pop.snapTo(1.18f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag("status_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudPill {
            Icon(
                imageVector = Icons.Filled.MonetizationOn,
                contentDescription = null,
                tint = Color(0xFFFFC83D),
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = shownBankroll.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp).testTag("bankroll_value"),
            )
        }
        HudPill(modifier = Modifier.scale(pop.value)) {
            ChipStack(amount = bet, chipWidth = 22.dp)
            Text(
                text = bet.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp).testTag("bet_value"),
            )
        }
    }
}

/** A dark, translucent capsule the HUD's figures sit in, readable on the felt in either theme. */
@Composable
private fun HudPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.55f),
        border = BorderStroke(1.5.dp, Color(0xFFFFC83D).copy(alpha = 0.7f)),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/**
 * The empty felt before the first deal: a betting circle with the chosen bet stacked in it, the way
 * a table invites a stake (`UI_SPEC.md` "Empty table"). The figure is not repeated here: the bet is
 * stated once, in the HUD.
 */
@Composable
internal fun BettingCircle(bet: Int, animate: Boolean, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(bet) {
        if (animate) {
            pop.snapTo(1.12f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 380f))
        }
    }
    val ring = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
    Box(contentAlignment = Alignment.Center, modifier = modifier.scale(pop.value).size(168.dp).testTag("betting_circle")) {
        Canvas(modifier = Modifier.size(168.dp)) {
            drawCircle(Color.Black.copy(alpha = 0.18f))
            drawCircle(ring, style = Stroke(width = 3.dp.toPx()))
            drawCircle(ring.copy(alpha = 0.25f), radius = size.minDimension / 2f - 10.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
        }
        ChipStack(amount = bet, chipWidth = 56.dp)
    }
}
