package org.finiteplay.holdem.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.DECK_SIZE
import org.finiteplay.cards.SplitMix64
import org.finiteplay.cards.shuffleDeckIndices

/** SplitMix64's own increment, the 64-bit golden ratio: 0x9E3779B97F4A7C15. */
private const val GAMMA = -7046029254386353131L

/**
 * The seed that shuffles one hand (`RULES.md` "The Deal", `EXECUTION_PLAN.md` "Deterministic Deal
 * Contract"): output number `handNumber + 1` of the SplitMix64 stream seeded with the tournament's
 * seed, computed directly as `SplitMix64(tournamentSeed + handNumber * GAMMA).nextULong()`.
 *
 * Hands are numbered from 1. Hand 0 is reserved: its seed draws the tournament's first button
 * ([firstButton]), so nothing about the first button shares a draw with a hand's deck.
 */
fun handSeed(tournamentSeed: Long, handNumber: Int): Long {
    require(handNumber >= 0) { "hand numbers start at 1, was $handNumber" }
    return SplitMix64(tournamentSeed + handNumber.toLong() * GAMMA).nextULong().toLong()
}

/** The seat holding the first button, drawn from the tournament's seed (`RULES.md` "The Tournament"). */
fun firstButton(tournamentSeed: Long): Int =
    SplitMix64(handSeed(tournamentSeed, 0)).nextBounded(Contract.SEATS.toUInt()).toInt()

/** The 52 cards of one hand in dealing order: `core/cards`' shared shuffle, unchanged. */
fun deckFor(handSeed: Long): List<Card> = shuffleDeckIndices(handSeed, DECK_SIZE).map(Card::fromId)
