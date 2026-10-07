package org.finiteplay.holdem.rules

/**
 * The pots built from the bottom up, main pot first (`RULES.md` "Pots and Showdown"): each level is
 * a distinct amount some contesting seat has in, and the pot for a level holds, from every seat,
 * what it put in between the level below and this one. A seat is eligible for the pots whose level
 * it paid in full. Chips a folded seat put in stay in the pots they fall in.
 */
fun sidePots(contributions: List<Int>, contesting: List<Boolean>): List<Pot> {
    val levels = contributions.indices.filter { contesting[it] && contributions[it] > 0 }
        .map { contributions[it] }.distinct().sorted()
    val pots = ArrayList<Pot>()
    var below = 0
    for (level in levels) {
        val amount = contributions.sumOf { (it.coerceAtMost(level) - below).coerceAtLeast(0) }
        val eligible = contributions.indices.filter { contesting[it] && contributions[it] >= level }
        pots += Pot(amount, eligible)
        below = level
    }
    // A folded seat cannot hold more than every contesting seat once uncalled bets are returned,
    // so this is only a guard that no chip is left outside a pot.
    val leftover = contributions.sum() - pots.sumOf { it.amount }
    if (leftover > 0 && pots.isNotEmpty()) {
        val top = pots.removeAt(pots.lastIndex)
        pots += top.copy(amount = top.amount + leftover)
    }
    return pots
}

/**
 * Awards each pot to the best hand among its eligible seats (`RULES.md` "Pots and Showdown").
 * Tied hands share equally; chips that do not divide go one at a time to the tied seats in seat
 * order starting from the first seat to the button's left, pot by pot.
 */
internal fun awardPots(pots: List<Pot>, values: Map<Int, HandValue>, button: Int): List<PotAward> =
    pots.map { pot ->
        val best = pot.eligible.maxOf { values.getValue(it) }
        val tied = pot.eligible.filter { values.getValue(it) == best }
            .sortedBy { (it - button - 1 + Contract.SEATS) % Contract.SEATS }
        val share = pot.amount / tied.size
        val odd = pot.amount % tied.size
        PotAward(pot, tied, tied.indices.map { share + if (it < odd) 1 else 0 })
    }

/**
 * Finishing places for seats knocked out in one hand (`RULES.md` "The Tournament"): [aliveBefore]
 * players were in; the survivors take the best places, and the knocked-out seats fill the rest in
 * order of the chips they started the hand with, more chips higher. Equal starting stacks share the
 * higher of the places they span.
 */
internal fun placesForKnockouts(knockedOut: List<Int>, startStacks: List<Int>, aliveBefore: Int): Map<Int, Int> {
    val survivors = aliveBefore - knockedOut.size
    val ordered = knockedOut.sortedByDescending { startStacks[it] }
    val places = HashMap<Int, Int>()
    var index = 0
    while (index < ordered.size) {
        val stack = startStacks[ordered[index]]
        var end = index
        while (end < ordered.size && startStacks[ordered[end]] == stack) end++
        for (i in index until end) places[ordered[i]] = survivors + 1 + index
        index = end
    }
    return places
}
