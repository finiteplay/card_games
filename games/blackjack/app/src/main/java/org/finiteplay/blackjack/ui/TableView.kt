package org.finiteplay.blackjack.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import org.finiteplay.core.ui.card.drawCardBack
import org.finiteplay.core.ui.card.drawCardFace
import org.finiteplay.core.ui.theme.LocalAppColors

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

/** A hand's cards fanned on a canvas, with one optionally face down. */
@Composable
fun CardFan(
    cards: List<Card>,
    faceDownIndex: Int?,
    cardWidth: Dp,
    handWidth: Dp,
    modifier: Modifier = Modifier,
    testTag: String,
) {
    val colors = LocalAppColors.current.card
    val step = TableGeometry.fanStep(cardWidth.value, handWidth.value, cards.size)
    val fanWidth = TableGeometry.fanWidth(cardWidth.value, step, cards.size).dp
    val cardHeight = TableGeometry.cardHeight(cardWidth.value).dp
    Canvas(modifier = modifier.width(fanWidth).height(cardHeight).testTag(testTag)) {
        val size = Size(cardWidth.toPx(), cardHeight.toPx())
        cards.forEachIndexed { index, card ->
            val topLeft = Offset(index * step.dp.toPx(), 0f)
            if (index == faceDownIndex) drawCardBack(topLeft, size, colors) else drawCardFace(topLeft, size, card, colors)
        }
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
            CardFan(hand.cards, faceDownIndex = null, cardWidth = cardWidth, handWidth = handWidth, testTag = "hand_${index}_cards")
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
        Text(
            text = badgeText(hand.cards, naturalPays = !hand.fromSplit),
            style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.testTag("hand_${index}_total"),
        )
        Text(
            text = stringResource(R.string.status_bet, hand.bet),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.testTag("hand_${index}_stake"),
        )
        if (result != null) {
            Text(
                text = resultText(result),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = resultColor(result),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("hand_${index}_result"),
            )
        }
    }
}

@Composable
private fun resultColor(result: HandResult): Color = when (result.outcome) {
    HandOutcome.BLACKJACK, HandOutcome.WIN -> LocalAppColors.current.action.new
    HandOutcome.PUSH -> MaterialTheme.colorScheme.onSurfaceVariant
    HandOutcome.LOSS, HandOutcome.BUST -> LocalAppColors.current.action.undo
}

/** `+150`, `−100`, `0`: the sign is always printed, so colour is never the only cue. */
fun signed(amount: Int): String = when {
    amount > 0 -> "+$amount"
    amount < 0 -> "−${-amount}"
    else -> "0"
}

@Composable
fun resultText(result: HandResult): String = when (result.outcome) {
    HandOutcome.BLACKJACK -> stringResource(R.string.outcome_blackjack, signed(result.delta))
    HandOutcome.WIN -> stringResource(R.string.outcome_win, signed(result.delta))
    HandOutcome.PUSH -> stringResource(R.string.outcome_push)
    HandOutcome.LOSS -> stringResource(R.string.outcome_loss, signed(result.delta))
    HandOutcome.BUST -> stringResource(R.string.outcome_bust, signed(result.delta))
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
            cards = if (holeDown != null) state.dealer.take(2) else shown,
            faceDownIndex = holeDown,
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
        val playerWidth = if (landscape) total * 0.62f else total
        val dealerWidth = if (landscape) total * 0.38f else total
        val playerArea = TableGeometry.areaWidth(playerWidth)
        val hands = state.hands.size
        val playerCard = TableGeometry.cardWidth(playerArea, hands).dp
        val dealerArea = TableGeometry.areaWidth(dealerWidth)
        val dealerCard = TableGeometry.cardWidth(dealerArea, 1).dp.coerceAtMost(playerCard.coerceAtLeast(40.dp) + 8.dp)

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

        if (landscape) {
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

/** A placeholder shown on an empty felt, before the first Deal: nothing is dealt until the bet is placed. */
@Composable
fun EmptyTable(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().widthIn(max = 480.dp).testTag("empty_table"), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.label_place_bet),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
