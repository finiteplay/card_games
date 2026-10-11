package org.finiteplay.holdem.opponents

import org.finiteplay.cards.SplitMix64
import org.finiteplay.holdem.rules.HandCategory
import org.finiteplay.holdem.rules.categoryOf
import org.finiteplay.holdem.rules.evaluate

/**
 * What the opponents may already hold against the player, on the cards showing now: the chance that
 * at least one live opponent has a better hand than the player's ([beatsYou]), and, for the hands
 * better than the player's, how likely each is to be the best one out there ([best], most likely
 * first, each at least [MIN_SHOWN]). A full house, four of a kind and a straight flush are one entry,
 * [HandCategory.FULL_HOUSE]. Estimated from the same range model as the hint's equity, so it knows
 * that someone who kept betting is stronger than a random hand.
 */
data class Threats(val beatsYou: Double, val best: List<Pair<HandCategory, Double>>) {
    companion object {
        const val MIN_SHOWN = 0.02
        const val MAX_SHOWN = 2
        const val SAMPLES = 2500

        /** The entry a made hand of [category] falls under in [best]. */
        fun bucket(category: HandCategory): HandCategory =
            if (category.ordinal >= HandCategory.FULL_HOUSE.ordinal) HandCategory.FULL_HOUSE else category
    }
}

/** Null before the flop (nothing shows yet) and when no opponent is left to hold anything. */
internal fun threatsOf(sit: Situation, samples: Int, rng: SplitMix64): Threats? {
    val view = sit.view
    val boardCount = sit.boardIds.size
    if (boardCount < 3 || sit.opponents.isEmpty()) return null
    val samplers = estimateRanges(sit, Profiles.strongest).map(::Sampler)
    if (samplers.any { it.isEmpty }) return null
    val n = samplers.size
    var dead = 0L
    for (c in view.holeCards) dead = dead or (1L shl c.id)
    for (id in sit.boardIds) dead = dead or (1L shl id)

    val mine = IntArray(7)
    mine[0] = view.holeCards[0].id
    mine[1] = view.holeCards[1].id
    val theirs = IntArray(7)
    for (i in 0 until boardCount) {
        mine[2 + i] = sit.boardIds[i]
        theirs[2 + i] = sit.boardIds[i]
    }
    val count = 2 + boardCount
    val mineValue = evaluate(mine, count).strength
    val myBucket = Threats.bucket(categoryOf(org.finiteplay.holdem.rules.HandValue(mineValue)))

    val bestCount = IntArray(HandCategory.entries.size)
    var beat = 0
    var done = 0
    var attempts = 0
    while (done < samples && attempts < samples * 6) {
        attempts++
        var used = dead
        var ok = true
        var bestStrength = -1
        for (j in 0 until n) {
            val k = samplers[j].draw(rng.nextDouble())
            val a = Combos.first[k]
            val b = Combos.second[k]
            if ((used ushr a) and 1L != 0L || (used ushr b) and 1L != 0L) {
                ok = false
                break
            }
            used = used or (1L shl a) or (1L shl b)
            theirs[0] = a
            theirs[1] = b
            val v = evaluate(theirs, count).strength
            if (v > bestStrength) bestStrength = v
        }
        if (!ok) continue
        done++
        if (bestStrength > mineValue) beat++
        bestCount[Threats.bucket(categoryOf(org.finiteplay.holdem.rules.HandValue(bestStrength))).ordinal]++
    }
    if (done == 0) return null
    val best = HandCategory.entries
        .filter { it.ordinal > myBucket.ordinal && it.ordinal <= HandCategory.FULL_HOUSE.ordinal }
        .map { it to bestCount[it.ordinal].toDouble() / done }
        .filter { it.second >= Threats.MIN_SHOWN }
        .sortedByDescending { it.second }
        .take(Threats.MAX_SHOWN)
    return Threats(beat.toDouble() / done, best)
}
