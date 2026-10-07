package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card

/** The four betting rounds (`RULES.md` "Betting"). A showdown happens on the river's board. */
enum class Street { PREFLOP, FLOP, TURN, RIVER }

/**
 * [BETTING]: a seat is to act. [HAND_OVER]: the pots are awarded and the next hand is waiting for
 * [nextHand]. [TOURNAMENT_OVER]: one player holds every chip, or the player left
 * (`RULES.md` "Leaving"); nothing more is dealt.
 */
enum class Phase { BETTING, HAND_OVER, TOURNAMENT_OVER }

/**
 * The tournament as it stood when a hand was dealt (`DESIGN.md` "Persistence": the thing stored
 * between hands). [stacks] are the chips each seat started the hand with, [places] each seat's
 * finishing place or null while still in. [button] is this hand's button; [handNumber] counts from 1.
 */
data class Tournament(
    val seed: Long,
    val handNumber: Int,
    val button: Int,
    val stacks: List<Int>,
    val places: List<Int?>,
    val rulesVersion: Int = Contract.RULES_VERSION,
    val shuffleVersion: Int = Contract.SHUFFLE_VERSION,
) {
    val alive: List<Int> get() = (0 until Contract.SEATS).filter { places[it] == null }
    val blinds: Blinds get() = BlindSchedule.blindsForHand(handNumber)
    val handSeed: Long get() = handSeed(seed, handNumber)

    companion object {
        /** A fresh tournament: equal stacks, the first button drawn from [seed] (`RULES.md` "The Tournament"). */
        fun start(seed: Long): Tournament = Tournament(
            seed = seed,
            handNumber = 1,
            button = firstButton(seed),
            stacks = List(Contract.SEATS) { Contract.STARTING_STACK },
            places = List(Contract.SEATS) { null },
        )
    }
}

/** A pot, main or side, and the seats that paid into it in full (`RULES.md` "Pots and Showdown"). */
data class Pot(val amount: Int, val eligible: List<Int>)

/** A pot awarded: [shares] are the chips each of [winners] took, odd chips included. */
data class PotAward(val pot: Pot, val winners: List<Int>, val shares: List<Int>)

/**
 * A hand's outcome. [payouts] is what each seat took from the pots; [values] the hand each seat
 * showed (empty when the hand ended without a showdown, when no cards are shown); [eliminated] the
 * seats with no chips left, whose places [HoldemState.places] now holds.
 */
data class HandResult(
    val showdown: Boolean,
    val awards: List<PotAward>,
    val payouts: List<Int>,
    val values: Map<Int, HandValue>,
    val eliminated: List<Int>,
)

/**
 * The whole game: the [tournament] as it was when this hand was dealt, and the hand in progress.
 * Immutable; [apply] is the one reducer (`DESIGN.md` "Architecture"). The player is seat 0 and
 * seats run clockwise 0..5.
 *
 * Chips: [stacks] are chips behind, [streetBets] what is in front of each seat on this street,
 * [contributions] what each seat has put into the hand in all. While a hand is being played
 * `stacks` plus `contributions` always sum to [Contract.TOTAL_CHIPS]; once it is awarded the pot
 * has been paid into `stacks` and `contributions` is kept only as a record.
 *
 * A seat is in the hand when it holds hole cards. [lastActionBet] is the bet level a seat last
 * responded to on this street, -1 before it has acted; it is what makes the short all-in rule
 * (`RULES.md` "Betting") exact, see [bettingOpenTo]. [raiseIncrement] is the minimum raise: the
 * largest increment on this street, at least the big blind.
 *
 * [deck] is the hand's whole shuffled deck and so is hidden information: only [SeatView] may leave
 * this module toward an opponent, and it carries nothing of the deck.
 */
data class HoldemState(
    val tournament: Tournament,
    val deck: List<Card>,
    val holeCards: List<List<Card>>,
    val board: List<Card>,
    val street: Street,
    val phase: Phase,
    val stacks: List<Int>,
    val streetBets: List<Int>,
    val contributions: List<Int>,
    val returned: List<Int>,
    val folded: List<Boolean>,
    val allIn: List<Boolean>,
    val lastActionBet: List<Int>,
    val currentBet: Int,
    val raiseIncrement: Int,
    val toAct: Int,
    val history: List<HistoryEntry>,
    val places: List<Int?>,
    val result: HandResult?,
) {
    val button: Int get() = tournament.button
    val blinds: Blinds get() = tournament.blinds
    val handNumber: Int get() = tournament.handNumber

    fun inHand(seat: Int): Boolean = holeCards[seat].isNotEmpty()

    /** In the hand and not folded: still has a claim on a pot. */
    fun contesting(seat: Int): Boolean = inHand(seat) && !folded[seat]

    /** Contesting with chips left to bet: the seats a betting round waits on. */
    fun active(seat: Int): Boolean = contesting(seat) && !allIn[seat]

    val seatsInHand: List<Int> get() = (0 until Contract.SEATS).filter(::inHand)

    /** Heads-up the button is the small blind (`RULES.md` "The Button and the Blinds"). */
    val smallBlindSeat: Int get() = if (seatsInHand.size == 2) button else nextInHand(button)
    val bigBlindSeat: Int get() = nextInHand(smallBlindSeat)

    /** The first seat in the hand clockwise after [seat]. */
    internal fun nextInHand(seat: Int): Int {
        for (k in 1..Contract.SEATS) {
            val candidate = (seat + k) % Contract.SEATS
            if (inHand(candidate)) return candidate
        }
        error("no seat in the hand")
    }

    /** Chips in the pot: everything put in and not yet awarded or returned. */
    val pot: Int get() = if (phase == Phase.BETTING) contributions.sum() else 0

    /** The pots as they stand, main pot first (`RULES.md` "Pots and Showdown"). */
    val pots: List<Pot> get() = if (phase == Phase.BETTING) sidePots(contributions, List(Contract.SEATS, ::contesting)) else emptyList()

    /** What matching the street's bet costs [seat]; may exceed its stack. */
    fun toCall(seat: Int): Int = (currentBet - streetBets[seat]).coerceAtLeast(0)

    /**
     * Whether [seat] may raise now under the short all-in rule (`RULES.md` "Betting"): it has not
     * yet acted, or the bet has risen by at least a full raise since the level it last responded to.
     * Counting the rise since then — not only the last raise — is the standard reading: short
     * all-ins that add up to a full raise reopen the betting.
     */
    fun bettingOpenTo(seat: Int): Boolean =
        lastActionBet[seat] < 0 || currentBet - lastActionBet[seat] >= raiseIncrement

    /** The player has been knocked out or has left. The opponents' play past this is not shown. */
    val playerOut: Boolean get() = places[0] != null
}
