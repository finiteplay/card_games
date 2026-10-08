package org.finiteplay.holdem.ui

import org.finiteplay.cards.Card
import org.finiteplay.holdem.rules.Blinds
import org.finiteplay.holdem.rules.HandValue
import org.finiteplay.holdem.rules.HistoryEntry
import org.finiteplay.holdem.rules.Street

/** What the table is showing, which decides the action bar (`UI_SPEC.md` "Screens and States"). */
enum class Stage { OPPONENT_TURN, YOUR_TURN, SHOWDOWN, HAND_OVER, TOURNAMENT_OVER }

/** How the pot being awarded is named on the banner over the board. */
enum class PotKind { SINGLE, MAIN, SIDE }

data class PotBanner(val kind: PotKind, val amount: Int)

/** The pictogram of the player's result mark: ahead, level, or behind at showdown. */
enum class MarkKind { WIN, EVEN, LOSE }

data class ResultMarkModel(val kind: MarkKind, val net: Int)

/**
 * One seat as shown. [cards] are the hole cards face up, or null while they are hidden (an opponent's
 * until showdown) — a null with [inHand] and not [folded] draws two card backs. Nothing here can
 * carry a card the player has not been shown.
 */
data class SeatModel(
    val stack: Int,
    val bet: Int,
    val inHand: Boolean,
    val folded: Boolean,
    val allIn: Boolean,
    val lastAction: HistoryEntry?,
    val cards: List<Card>?,
    val place: Int?,
    val award: Int,
    val handValue: HandValue?,
    /** The chips this seat won or lost over the whole hand, known only once the hand is over (`UI_SPEC.md` "Showdown and Results"). */
    val net: Int? = null,
) {
    val contesting: Boolean get() = inHand && !folded
}

/**
 * What the player is shown of a hand at one moment. The engine resolves a whole street, or the whole
 * rest of a hand, inside one `act` call, but it is *presented* a step at a time — an action, the
 * bets gathering, each street's cards, each hand turning over, each pot paid — so what is drawn is a
 * view over states that are already committed (`EXECUTION_PLAN.md` H3), never ahead of them.
 */
data class TableModel(
    val handNumber: Int,
    val blinds: Blinds,
    val button: Int,
    val smallBlind: Int,
    val bigBlind: Int,
    val seats: List<SeatModel>,
    val board: List<Card>,
    val pot: Int,
    val potCount: Int,
    /** Chips each seat has in the hand and not yet returned or paid: the pot is their sum. */
    val contributions: List<Int>,
    /** Uncalled chips already given back to each seat, so a later state's return is only the difference. */
    val returned: List<Int>,
    /** The seat ringed: to act, or thinking. */
    val actor: Int?,
    val thinking: Boolean,
    val stage: Stage,
    val banner: PotBanner?,
    /** The cards raised at showdown; every other card dims. Null when nothing is being raised. */
    val highlight: Set<Card>?,
    val mark: ResultMarkModel?,
    val confetti: Int,
    val lossWash: Boolean,
    /** The player's finishing place once the tournament is over for them. */
    val finishingPlace: Int?,
) {
    val street: Street
        get() = when (board.size) {
            0 -> Street.PREFLOP
            3 -> Street.FLOP
            4 -> Street.TURN
            else -> Street.RIVER
        }
}
