package org.finiteplay.holdem.storage

import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.Contract
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.Street

/** The player's seat (`RULES.md` "The Tournament": always drawn at the bottom). */
const val PLAYER_SEAT = 0

/**
 * What one hand says about the player, derived from the hand alone (`DESIGN.md` "Scoring and
 * statistics"). [chipsWon] is what the player took from the pots, their own stake included; blinds
 * posted are not voluntary, and neither is checking the big blind's option.
 */
data class HandFacts(
    val voluntarilyIn: Boolean,
    val raisedPreflop: Boolean,
    val reachedShowdown: Boolean,
    val won: Boolean,
    val wonShowdown: Boolean,
    val chipsWon: Int,
)

/**
 * The facts of the hand in [state], finished or left mid-way. A hand the player leaves has no result:
 * it counts as played, from what they did before leaving, and as nothing won (`RULES.md` "Leaving").
 */
fun handFacts(state: HoldemState): HandFacts {
    require(state.inHand(PLAYER_SEAT)) { "the player holds no cards in this hand" }
    val preflop = state.history.filter { it.street == Street.PREFLOP && it.seat == PLAYER_SEAT }
    val result = state.result
    val chips = result?.payouts?.get(PLAYER_SEAT) ?: 0
    val showdown = result?.showdown == true && state.contesting(PLAYER_SEAT)
    return HandFacts(
        voluntarilyIn = preflop.any { it.action != Action.Fold && it.action != Action.Check },
        // All in is only offered when it raises (`RULES.md` "Legal Actions"), so it is a raise here.
        raisedPreflop = preflop.any { it.action is Action.Raise || it.action is Action.Bet || it.action == Action.AllIn },
        reachedShowdown = showdown,
        won = chips > 0,
        wonShowdown = showdown && chips > 0,
        chipsWon = chips,
    )
}

/**
 * Lifetime statistics, one pool (`DESIGN.md` "Scoring and statistics"). A tournament's played, won
 * and streak figures are `core/session`'s `computeSessionStatistics` over its outcomes; the store
 * keeps the counters rather than every tournament, since nothing else here needs the history, and
 * the test holds the two to the same answers.
 *
 * [placeCounts] holds the finishes, index 0 for 1st; played, won and the average finish derive from it.
 */
data class HoldemStatistics(
    val placeCounts: List<Int> = List(Contract.SEATS) { 0 },
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val showdownsReached: Int = 0,
    val showdownsWon: Int = 0,
    val largestPot: Int = 0,
    val voluntaryHands: Int = 0,
    val preflopRaiseHands: Int = 0,
    val hintsUsed: Int = 0,
) {
    init {
        require(placeCounts.size == Contract.SEATS) { "one count per place" }
    }

    val tournamentsPlayed: Int get() = placeCounts.sum()
    val tournamentsWon: Int get() = placeCounts[0]
    val winRate: Double? get() = tournamentsPlayed.takeIf { it > 0 }?.let { tournamentsWon.toDouble() / it }

    val averageFinish: Double?
        get() = tournamentsPlayed.takeIf { it > 0 }?.let { n ->
            placeCounts.withIndex().sumOf { (i, count) -> (i + 1) * count }.toDouble() / n
        }

    /** Hands the player put chips in before the flop of their own accord, as a share of hands played. */
    val voluntaryRate: Double? get() = handsPlayed.takeIf { it > 0 }?.let { voluntaryHands.toDouble() / it }
    val preflopRaiseRate: Double? get() = handsPlayed.takeIf { it > 0 }?.let { preflopRaiseHands.toDouble() / it }

    fun afterHand(facts: HandFacts): HoldemStatistics = copy(
        handsPlayed = handsPlayed + 1,
        handsWon = handsWon + if (facts.won) 1 else 0,
        showdownsReached = showdownsReached + if (facts.reachedShowdown) 1 else 0,
        showdownsWon = showdownsWon + if (facts.wonShowdown) 1 else 0,
        largestPot = maxOf(largestPot, facts.chipsWon),
        voluntaryHands = voluntaryHands + if (facts.voluntarilyIn) 1 else 0,
        preflopRaiseHands = preflopRaiseHands + if (facts.raisedPreflop) 1 else 0,
    )

    /** A tournament finished in [place], 1 to 6 (a left tournament included, `RULES.md` "Leaving"). */
    fun afterTournament(place: Int): HoldemStatistics {
        require(place in 1..Contract.SEATS) { "no place $place" }
        val streak = if (place == 1) currentStreak + 1 else 0
        return copy(
            placeCounts = placeCounts.mapIndexed { i, count -> if (i == place - 1) count + 1 else count },
            currentStreak = streak,
            bestStreak = maxOf(bestStreak, streak),
        )
    }

    fun hintUsed(): HoldemStatistics = copy(hintsUsed = hintsUsed + 1)
}
