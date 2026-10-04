package org.finiteplay.spider.game

import org.finiteplay.spider.layout.SuitCount

/**
 * Which deal is "deal number N" at a given suit count.
 *
 * Klondike numbers a deal by its position in its difficulty's certified seed list, so "Hard #7" is
 * the seventh Hard deal and always the same board. Spider has no catalog yet
 * (`docs/games/spider/EXECUTION_PLAN.md` S5), so there is no list to index — but the *player-facing
 * property* that matters is the same: a deal has a number, the number always names the same board,
 * and New Game walks the numbers in order rather than jumping to a random seed.
 *
 * The seed is therefore derived from the number rather than looked up. It is a mixing function, so
 * consecutive deal numbers give unrelated boards; and it takes the suit count, so deal #7 at one
 * suit and deal #7 at four are different boards, which they must be — the same seed at two counts
 * produces two different games and numbering them alike would imply otherwise.
 *
 * These numbers are on screen and in saved games. Changing this function renumbers every deal
 * every player has seen, which is a catalog-version change, not an implementation detail.
 */
object DealSequence {
    /** The first deal a player is given at any suit count. */
    const val FIRST = 1

    fun seedFor(suitCount: SuitCount, dealNumber: Int): Long {
        // SplitMix64's finalizer: cheap, and it decorrelates neighbouring inputs thoroughly, which
        // is the whole requirement here — deal #8 must look nothing like deal #7.
        var z = dealNumber.toLong() * 0x9E3779B97F4A7C15uL.toLong() +
            (suitCount.ordinal + 1L) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return z xor (z ushr 31)
    }
}
