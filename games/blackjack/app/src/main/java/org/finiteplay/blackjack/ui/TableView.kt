package org.finiteplay.blackjack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.finiteplay.blackjack.R
import org.finiteplay.blackjack.rules.BlackjackState
import org.finiteplay.blackjack.rules.HandOutcome
import org.finiteplay.blackjack.rules.HandResult
import org.finiteplay.blackjack.rules.HandValue
import org.finiteplay.blackjack.rules.PlayerHand
import org.finiteplay.blackjack.rules.handValue
import org.finiteplay.blackjack.rules.isBlackjackShape
import org.finiteplay.cards.Card
import org.finiteplay.core.ui.card.CardFan
import org.finiteplay.core.ui.chips.ChipStack

/**
 * What the player is shown of a round at one moment. Settlement happens inside one reducer call,
 * but it is *presented* in steps — the hole card turns over, the dealer's draws appear one at a
 * time, and only then do results (`UI_SPEC.md` "Settlement Presentation", "Motion") — so what is
 * drawn is a view over the state, not the state itself.
 */
data class TableView(
    val state: BlackjackState,
    /** How many dealer cards are face up; the up card always is, the hole card once this is 2 or more. */
    val dealerFaceUp: Int,
    /** Whether each hand's result and the round's net may be shown yet. */
    val resultsShown: Boolean,
) {
}

/** Aces and tens aside, the badge a hand wears: its total, "Soft 17", "Bust" or "Blackjack". */
@Composable
private fun badgeText(cards: List<Card>, naturalPays: Boolean): String {
    val value = handValue(cards)
    return when {
        value.busted -> stringResource(R.string.hand_bust)
        naturalPays && isBlackjackShape(cards) -> stringResource(R.string.hand_blackjack)
        value.soft && value.total < 21 -> stringResource(R.string.hand_total_soft, value.total)
        else -> value.total.toString()
    }
}

/** One player hand: its fan, the total badge, its stake, and — once settled — its result. */
@Composable
fun PlayerHandView(
    index: Int,
    hand: PlayerHand,
    result: HandResult?,
    active: Boolean,
    columnWidth: Dp,
    cardWidth: Dp,
    split: Boolean,
) {
    val handWidth = if (split) columnWidth - TableGeometry.COLUMN_GAP.dp else columnWidth
    val dimmed = !active && hand.complete && result == null
    Column(
        modifier = Modifier.width(columnWidth).alpha(if (dimmed) 0.65f else 1f).testTag("hand_$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.width(handWidth), contentAlignment = Alignment.TopCenter) {
            CardFan(hand.cards, cardWidth = cardWidth, handWidth = handWidth, testTag = "hand_${index}_cards")
        }
        // The active hand is marked by an underline the width of its column and a stronger badge.
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(handWidth)
                .height(3.dp)
                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp))
                .testTag(if (active) "active_hand_marker" else "inactive_hand_marker_$index"),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                text = badgeText(hand.cards, naturalPays = !hand.fromSplit),
                style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.testTag("hand_${index}_total"),
            )
            // Only on a split round, where the round's one result mark cannot say which hand won: a
            // tick, a cross or an equals sign, with no figure — the signed net stays on the mark alone.
            if (split && result != null) HandMark(index, result)
        }
        // The bet is stated once, in the HUD. A split round has several stakes, so each hand states its own.
        if (split) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipStack(amount = hand.bet, chipWidth = 18.dp)
                Text(
                    text = stringResource(R.string.status_bet, hand.bet),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.testTag("hand_${index}_stake"),
                )
            }
        }
    }
}

/** A hand's result as a pictogram beside its total: tick for a win, cross for a loss, equals for a push. */
@Composable
private fun HandMark(index: Int, result: HandResult) {
    val (icon, tint, description) = when (result.outcome) {
        HandOutcome.BLACKJACK, HandOutcome.WIN -> Triple(Icons.Filled.Check, Color(0xFF7CE08F), R.string.banner_win)
        HandOutcome.LOSS, HandOutcome.BUST -> Triple(Icons.Filled.Close, Color(0xFFFF8A80), R.string.banner_lose)
        HandOutcome.PUSH -> Triple(Icons.Filled.DragHandle, Color(0xFFB0BEC5), R.string.outcome_push)
    }
    Icon(
        imageVector = icon,
        contentDescription = stringResource(description),
        tint = tint,
        modifier = Modifier.padding(start = 4.dp).size(20.dp).testTag("hand_${index}_mark"),
    )
}

/** The dealer: one hand, with the hole card face down until it has been turned over. */
@Composable
fun DealerView(view: TableView, cardWidth: Dp, handWidth: Dp) {
    val state = view.state
    val faceUp = if (state.holeRevealed) view.dealerFaceUp.coerceIn(1, state.dealer.size) else 1
    val shown = if (state.holeRevealed) state.dealer.take(faceUp) else state.dealer
    val holeDown = if (!state.holeRevealed || faceUp < 2) 1 else null
    val value: HandValue = handValue(state.dealer.take(faceUp))
    Column(modifier = Modifier.testTag("dealer"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.label_dealer),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // While the hole card is down only the up card's value is shown.
        CardFan(
            cards = (if (holeDown != null) state.dealer.take(2) else shown).mapIndexed { i, card -> if (i == holeDown) null else card },
            cardWidth = cardWidth,
            handWidth = handWidth,
            testTag = "dealer_cards",
        )
        Text(
            text = if (holeDown != null) handValue(state.dealer.take(1)).total.toString()
            else if (value.busted) stringResource(R.string.hand_bust)
            else if (value.soft && value.total < 21) stringResource(R.string.hand_total_soft, value.total)
            else value.total.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp).testTag("dealer_total"),
        )
    }
}

/**
 * The felt: the dealer above, the player's one to four hands below (portrait), or beside it in
 * landscape. Card sizes come from [TableGeometry] against the width each area actually has.
 */
@Composable
fun Table(view: TableView, landscape: Boolean, modifier: Modifier = Modifier) {
    val state = view.state
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val total = maxWidth.value
        val hands = state.hands.size
        // Three or four hands in a table too narrow to give them a 62% share go under the dealer, as in portrait.
        val sideBySide = landscape && !TableGeometry.stackHandsInLandscape(total, hands)
        val playerWidth = if (sideBySide) total * 0.62f else total
        val dealerWidth = if (sideBySide) total * 0.38f else total
        val playerArea = TableGeometry.areaWidth(playerWidth)
        // The areas share the height as the layout below gives it out: whole when side by side, 1 : 1.3 stacked.
        val height = maxHeight.value
        val dealerHeight = if (sideBySide) height else height / 2.3f
        val playerHeight = if (sideBySide) height else height * 1.3f / 2.3f
        val playerCard = minOf(
            TableGeometry.cardWidth(playerArea, hands),
            TableGeometry.cardWidthForHeight(playerHeight, TableGeometry.PLAYER_FURNITURE_HEIGHT),
        ).dp
        val dealerArea = TableGeometry.areaWidth(dealerWidth)
        val dealerCard = minOf(
            TableGeometry.cardWidth(dealerArea, 1).dp.coerceAtMost(playerCard.coerceAtLeast(40.dp) + 8.dp).value,
            TableGeometry.cardWidthForHeight(dealerHeight, TableGeometry.DEALER_FURNITURE_HEIGHT),
        ).dp

        val playerHands: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = TableGeometry.SIDE_MARGIN.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top,
            ) {
                val column = TableGeometry.columnWidth(playerArea, hands).dp
                state.hands.forEachIndexed { index, hand ->
                    PlayerHandView(
                        index = index,
                        hand = hand,
                        result = if (view.resultsShown) state.settlement?.hands?.getOrNull(index) else null,
                        active = !state.isSettled && state.phase == org.finiteplay.blackjack.rules.Phase.PLAYING && index == state.activeHand,
                        columnWidth = column,
                        cardWidth = playerCard,
                        split = hands > 1,
                    )
                }
            }
        }

        if (sideBySide) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(0.38f), contentAlignment = Alignment.Center) {
                    DealerView(view, dealerCard, handWidth = TableGeometry.areaWidth(dealerWidth).dp)
                }
                Box(modifier = Modifier.weight(0.62f), contentAlignment = Alignment.Center) { playerHands() }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DealerView(view, dealerCard, handWidth = TableGeometry.areaWidth(dealerWidth).dp)
                }
                Box(modifier = Modifier.weight(1.3f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { playerHands() }
            }
        }
    }
}

/** The empty felt before the first Deal: a betting circle holding the chosen bet; nothing is dealt until it is placed. */
@Composable
fun EmptyTable(bet: Int, animate: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().widthIn(max = 480.dp).testTag("empty_table"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.label_place_bet),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            BettingCircle(bet = bet, animate = animate)
        }
    }
}
