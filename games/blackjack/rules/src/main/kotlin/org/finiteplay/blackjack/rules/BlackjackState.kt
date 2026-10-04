package org.finiteplay.blackjack.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank

/** The rules and shuffle versions this build deals under; both frozen at 1 by RF (`EXECUTION_PLAN.md`). */
const val RULES_VERSION = 1
const val SHUFFLE_VERSION = 1

/** The decisions a player makes, spelled as the opcode alphabet a saved round's log is written in. */
enum class Decision(val opcode: Byte) {
    HIT(1),
    STAND(2),
    DOUBLE(3),
    SPLIT(4),
    TAKE_INSURANCE(5),
    DECLINE_INSURANCE(6),
    ;

    companion object {
        fun fromOpcode(opcode: Byte): Decision =
            entries.firstOrNull { it.opcode == opcode } ?: throw IllegalArgumentException("unknown opcode $opcode")
    }
}

/**
 * Where a round is. The peek, player blackjack and dealer play of `RULES.md` "Order of Play" are
 * not resting places — each is something the reducer does on the way to the next decision or to
 * [SETTLED] — so a round is only ever waiting on insurance, waiting on a hand, or over.
 */
enum class Phase { INSURANCE, PLAYING, SETTLED }

/**
 * One player hand. [bet] is its stake and already includes a double. A hand created by a split
 * holds one card until it becomes the active hand and receives its second ([pendingSecondCard]).
 */
data class PlayerHand(
    val cards: List<Card>,
    val bet: Int,
    val doubled: Boolean = false,
    val stood: Boolean = false,
    val fromSplit: Boolean = false,
    val splitAces: Boolean = false,
) {
    val value: HandValue get() = handValue(cards)
    val busted: Boolean get() = value.busted
    val pendingSecondCard: Boolean get() = fromSplit && cards.size == 1

    /** Stood, doubled, busted, at 21, or a split Ace that has its card (`RULES.md` "Order of Play" step 5). */
    val complete: Boolean
        get() = !pendingSecondCard && (stood || doubled || busted || value.total == 21 || splitAces)

    /** Two cards of equal rank, not merely equal value: a King and a Ten do not split. */
    val isPair: Boolean get() = cards.size == 2 && cards[0].rank == cards[1].rank
}

enum class HandOutcome { BLACKJACK, WIN, PUSH, LOSS, BUST }

/** One hand's result: [delta] is the chips it adds to the bankroll, negative for a loss. */
data class HandResult(val outcome: HandOutcome, val stake: Int, val delta: Int)

/**
 * A settled round. [total] is the bankroll's change, every hand and the insurance together —
 * the one number the bankroll is updated by (`RULES.md` "Settlement").
 */
data class Settlement(
    val hands: List<HandResult>,
    val insuranceDelta: Int,
    val dealerBlackjack: Boolean,
) {
    val total: Int get() = hands.sumOf { it.delta } + insuranceDelta
}

/**
 * A round. The bankroll is not in it (`DESIGN.md` "Architecture", "Persistence"): stakes are never
 * deducted from the saved bankroll during a round, so the available bankroll is derived by
 * [availableBankroll] from the bankroll a caller passes in.
 *
 * [shoe] is the whole fresh 312-card shoe for [seed]; [shoePosition] is the next unused index.
 * The dealer's hole card is `dealer[1]`; [holeRevealed] says whether anyone has seen it.
 */
data class BlackjackState(
    val seed: Long,
    val rulesVersion: Int,
    val shuffleVersion: Int,
    val bet: Int,
    val shoe: List<Card>,
    val shoePosition: Int,
    val dealer: List<Card>,
    val holeRevealed: Boolean,
    val hands: List<PlayerHand>,
    val activeHand: Int,
    val insuranceStake: Int,
    val phase: Phase,
    val settlement: Settlement?,
) {
    val dealerUpCard: Card get() = dealer[0]
    val dealerValue: HandValue get() = handValue(dealer)
    val isSettled: Boolean get() = phase == Phase.SETTLED

    /** Cards the player can see: the up card, and the hole card only once it has been turned over. */
    val dealerVisibleCards: List<Card> get() = if (holeRevealed) dealer else dealer.take(1)

    val splitCount: Int get() = hands.size - 1
}

/** Whether [card] is an Ace — the up card that offers insurance. */
internal val Card.isAce: Boolean get() = rank == Rank.ACE
