package org.finiteplay.holdem.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.core.ui.card.rankSpokenLabel
import org.finiteplay.core.ui.card.suitSpokenLabel
import org.finiteplay.holdem.R
import org.finiteplay.holdem.opponents.HintReason
import org.finiteplay.holdem.opponents.Threats
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

/**
 * The coach's sentence for a hint (`UI_SPEC.md` "Hint Presentation"). The numbers are whole percents
 * for [equity] and [needed], the count of [outs], and the stack in big blinds; each sentence uses
 * the ones it needs.
 */
@Composable
fun hintReasonText(reason: HintReason, equity: Int, needed: Int, outs: Int, stackBb: Int): String = when (reason) {
    HintReason.PREMIUM_RAISE -> stringResource(R.string.hint_reason_premium_raise)
    HintReason.LATE_POSITION_RAISE -> stringResource(R.string.hint_reason_late_position_raise)
    HintReason.PLAYABLE_RAISE -> stringResource(R.string.hint_reason_playable_raise)
    HintReason.CALL_TO_SEE_FLOP -> stringResource(R.string.hint_reason_call_to_see_flop)
    HintReason.FREE_FLOP -> stringResource(R.string.hint_reason_free_flop)
    HintReason.FOLD_WEAK -> stringResource(R.string.hint_reason_fold_weak)
    HintReason.FOLD_TO_RAISE -> stringResource(R.string.hint_reason_fold_to_raise)
    HintReason.PUSH_SHORT_STACK -> stringResource(R.string.hint_reason_push_short_stack, stackBb)
    HintReason.FOLD_SHORT_STACK -> stringResource(R.string.hint_reason_fold_short_stack, stackBb)
    HintReason.VALUE_BET -> stringResource(R.string.hint_reason_value_bet)
    HintReason.VALUE_RAISE -> stringResource(R.string.hint_reason_value_raise)
    HintReason.SEMI_BLUFF -> stringResource(R.string.hint_reason_semi_bluff, outs)
    HintReason.CALL_GOOD_PRICE -> stringResource(R.string.hint_reason_call_good_price, equity, needed)
    HintReason.CALL_DRAW -> stringResource(R.string.hint_reason_call_draw, outs)
    HintReason.FOLD_BAD_PRICE -> stringResource(R.string.hint_reason_fold_bad_price, equity, needed)
    HintReason.FREE_CARD -> stringResource(R.string.hint_reason_free_card)
    HintReason.POT_CONTROL -> stringResource(R.string.hint_reason_pot_control)
}

/** A hand category's plain name, the one the Help page's ranking uses. */
@Composable
fun categoryNounText(category: HandCategory): String = stringResource(
    when (category) {
        HandCategory.HIGH_CARD -> R.string.help_hand_high_card_title
        HandCategory.PAIR -> R.string.help_hand_pair_title
        HandCategory.TWO_PAIR -> R.string.help_hand_two_pair_title
        HandCategory.THREE_OF_A_KIND -> R.string.help_hand_three_title
        HandCategory.STRAIGHT -> R.string.help_hand_straight_title
        HandCategory.FLUSH -> R.string.help_hand_flush_title
        HandCategory.FULL_HOUSE -> R.string.help_hand_full_house_title
        HandCategory.FOUR_OF_A_KIND -> R.string.help_hand_four_title
        HandCategory.STRAIGHT_FLUSH -> R.string.help_hand_straight_flush_title
    },
)

/** An action in the words a seat's last action uses: `Fold`, `Call`, `Raise 120`. */
@Composable
fun adviceText(action: Action): String = when (action) {
    Action.Fold -> stringResource(R.string.seat_action_fold)
    Action.Check -> stringResource(R.string.seat_action_check)
    Action.Call -> stringResource(R.string.seat_action_call)
    Action.AllIn -> stringResource(R.string.seat_action_all_in)
    is Action.Bet -> stringResource(R.string.seat_action_bet, action.amount)
    is Action.Raise -> stringResource(R.string.seat_action_raise, action.total)
}

/** The hint's lines about what the opponents may hold: the chance one already beats the player, and the likeliest better hands. */
@Composable
fun threatLines(threats: Threats): List<String> {
    val lines = ArrayList<String>(2)
    lines += stringResource(R.string.hint_threat_beat, Math.round(threats.beatsYou * 100).toInt())
    if (threats.best.isNotEmpty()) {
        val items = threats.best.map { (category, p) -> stringResource(R.string.hint_threat_item, categoryNounText(category), Math.round(p * 100).toInt()) }
        lines += stringResource(R.string.hint_threat_list, items.joinToString(" \u00B7 "))
    }
    return lines
}

/** The look back at a hand: what the Hint said, what the player did, and how it came out (`UI_SPEC.md` "Hint Review"). */
@Composable
fun reviewLines(review: HandReview): List<String> {
    val hint = review.hint
    val equity = Math.round(hint.equity * 100).toInt()
    val needed = Math.round(hint.potOdds * 100).toInt()
    val advice = adviceText(hint.suggested)
    val reason = hintReasonText(hint.reason, equity, needed, hint.outs, hint.stackBb)
    // The odds the player had at the moment of the decision, set beside what then happened.
    val slim = equity < SLIM_PERCENT
    val main = when (review.kind) {
        ReviewKind.FOLLOWED_WON ->
            if (slim) stringResource(R.string.review_followed_won_slim, equity) else stringResource(R.string.review_followed_won, equity)
        ReviewKind.FOLLOWED_LOST -> stringResource(R.string.review_followed_lost, equity, 100 - equity)
        ReviewKind.FOLLOWED_FOLD -> stringResource(R.string.review_followed_fold)
        ReviewKind.IGNORED_WON -> stringResource(R.string.review_ignored_won, advice, reason)
        ReviewKind.IGNORED_LOST -> stringResource(R.string.review_ignored_lost, advice, reason)
        ReviewKind.IGNORED_FOLD -> stringResource(R.string.review_ignored_fold, advice, reason)
    }
    val beaten = review.beatenBy?.let { cat -> review.beatenPercent?.let { stringResource(R.string.review_beaten_by, categoryNounText(cat), it) } }
    val chance = when {
        review.kind == ReviewKind.FOLLOWED_FOLD || review.kind == ReviewKind.IGNORED_FOLD -> when (review.hindsightResult) {
            org.finiteplay.holdem.rules.HindsightResult.WON -> stringResource(R.string.review_hindsight_fold_won, equity)
            org.finiteplay.holdem.rules.HindsightResult.TIED -> stringResource(R.string.review_hindsight_fold_tied, equity)
            org.finiteplay.holdem.rules.HindsightResult.LOST -> stringResource(R.string.review_hindsight_fold_lost, equity)
            null -> if (review.kind == ReviewKind.IGNORED_FOLD) stringResource(R.string.review_chance_at_decision, equity) else null
        }
        review.kind == ReviewKind.IGNORED_WON -> stringResource(if (slim) R.string.review_chance_slim_win else R.string.review_chance_at_decision, equity)
        review.kind == ReviewKind.IGNORED_LOST -> stringResource(R.string.review_chance_at_decision, equity)
        else -> null
    }
    return listOfNotNull(main, chance, beaten)
}

/** Below this chance to win, in percent, a win was a long shot and is said to be one. */
private const val SLIM_PERCENT = 40
