package org.finiteplay.holdem.rules

/**
 * The constants `docs/games/holdem/RULES.md` fixes. Pinned here so a test can hold the code to the
 * document, and so a change to either is a visible one.
 */
object Contract {
    const val SEATS = 6
    const val STARTING_STACK = 1_500
    const val HANDS_PER_LEVEL = 10

    /** Chips in play for the whole tournament: every stack always sums to this. */
    const val TOTAL_CHIPS = SEATS * STARTING_STACK

    const val RULES_VERSION = 1
    const val SHUFFLE_VERSION = 1
}
