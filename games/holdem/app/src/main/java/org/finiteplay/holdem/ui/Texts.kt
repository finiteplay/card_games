package org.finiteplay.holdem.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.core.ui.card.rankSpokenLabel
import org.finiteplay.core.ui.card.suitSpokenLabel
import org.finiteplay.holdem.R
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HandCategory
import org.finiteplay.holdem.rules.HistoryEntry
import org.finiteplay.holdem.rules.Street

/** The words the table says, in one place: seats' last actions, hand names, places and what is announced. */

@Composable
fun placeText(place: Int): String = stringArrayResource(R.array.places).getOrElse(place - 1) { place.toString() }

@Composable
fun handNameText(name: HandName): String {
    val singular = stringArrayResource(R.array.rank_singular)
    val plural = stringArrayResource(R.array.rank_plural)
    fun one(rank: Rank) = singular[rank.ordinal]
    fun many(rank: Rank) = plural[rank.ordinal]
    return when (name.category) {
        HandCategory.STRAIGHT_FLUSH ->
            if (name.royal) stringResource(R.string.hand_royal_flush) else stringResource(R.string.hand_straight_flush, one(name.primary))
        HandCategory.FOUR_OF_A_KIND -> stringResource(R.string.hand_four_of_a_kind, many(name.primary))
        HandCategory.FULL_HOUSE -> stringResource(R.string.hand_full_house, many(name.primary), many(name.secondary!!))
        HandCategory.FLUSH -> stringResource(R.string.hand_flush, one(name.primary))
        HandCategory.STRAIGHT -> stringResource(R.string.hand_straight, one(name.primary))
        HandCategory.THREE_OF_A_KIND -> stringResource(R.string.hand_three_of_a_kind, many(name.primary))
        HandCategory.TWO_PAIR -> stringResource(R.string.hand_two_pair, many(name.primary), many(name.secondary!!))
        HandCategory.PAIR -> stringResource(R.string.hand_pair, many(name.primary))
        HandCategory.HIGH_CARD -> stringResource(R.string.hand_high_card, one(name.primary))
    }
}

/** `Raise 120`, `Call`, `Check`, `Fold`, `All in`: a seat's last action on this street. */
@Composable
fun lastActionText(entry: HistoryEntry): String = when {
    entry.action === Action.Fold -> stringResource(R.string.seat_action_fold)
    entry.allIn -> stringResource(R.string.seat_action_all_in)
    entry.action === Action.Check -> stringResource(R.string.seat_action_check)
    entry.action === Action.Call -> stringResource(R.string.seat_action_call)
    entry.action is Action.Bet -> stringResource(R.string.seat_action_bet, entry.streetTotal)
    else -> stringResource(R.string.seat_action_raise, entry.streetTotal)
}

@Composable
fun styleText(style: Style): String = stringResource(
    when (style) {
        Style.TIGHT -> R.string.style_tight
        Style.LOOSE -> R.string.style_loose
        Style.AGGRESSIVE -> R.string.style_aggressive
        Style.PASSIVE -> R.string.style_passive
    },
)

@Composable
fun cardSpoken(card: Card): String = stringResource(R.string.card_spoken, rankSpokenLabel(card.rank), suitSpokenLabel(card.suit))

@Composable
fun cardsSpoken(cards: List<Card>): String {
    val spoken = ArrayList<String>()
    for (card in cards) spoken += cardSpoken(card)
    return spoken.joinToString(", ")
}

/** What an [Announcement] says aloud; [nameOf] gives a seat's name ("You" for the player). */
@Composable
fun announcementText(announcement: Announcement, nameOf: @Composable (Int) -> String): String = when (announcement) {
    is Announcement.Acted -> {
        val who = nameOf(announcement.seat)
        val entry = announcement.entry
        when {
            entry.action === Action.Fold -> stringResource(R.string.ann_fold, who)
            entry.allIn -> stringResource(R.string.ann_all_in, who, entry.streetTotal)
            entry.action === Action.Check -> stringResource(R.string.ann_check, who)
            entry.action === Action.Call -> stringResource(R.string.ann_call, who, entry.chips)
            entry.action is Action.Bet -> stringResource(R.string.ann_bet, who, entry.streetTotal)
            else -> stringResource(R.string.ann_raise, who, entry.streetTotal)
        }
    }
    is Announcement.BoardDealt -> stringResource(
        when (announcement.street) {
            Street.FLOP -> R.string.ann_flop
            Street.TURN -> R.string.ann_turn
            else -> R.string.ann_river
        },
        cardsSpoken(announcement.newCards),
    )
    is Announcement.Showed -> stringResource(R.string.ann_shows, nameOf(announcement.seat), handNameText(announcement.name))
    is Announcement.Won -> {
        val names = ArrayList<String>()
        for (seat in announcement.seats) names += nameOf(seat)
        stringResource(R.string.ann_wins, names.joinToString(" & "), announcement.amount)
    }
}
