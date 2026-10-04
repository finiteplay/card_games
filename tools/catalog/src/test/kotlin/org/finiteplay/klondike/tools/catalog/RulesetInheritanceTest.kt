package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.rules.canPlaceOnFoundation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ladder is **cumulative**: "every strategy available at a lower tier remains available at
 * every tier above it" (`DIFFICULTY_LEVELS.md` "Definitions"), which is what lets a tier be
 * specified as "the one below, plus" one named addition.
 *
 * `MediumRulesetTest` already pins that for Easy → Medium. This pins it for the whole ladder,
 * because the property is easy to break one rung at a time: a rule written as `ruleset == X`
 * reads correctly while X is the top tier and silently stops being inherited the moment a tier
 * is added above it. That is a defect no ruleset's own test can see — each tier passes in
 * isolation, and only the relationship between two of them is wrong.
 */
class RulesetInheritanceTest {

    /**
     * Withholding a foundation play is the only thing a higher tier can do *less* of, so it is
     * where a broken inheritance shows. Every other addition on the ladder widens the move set,
     * and a wider set cannot be lost by a comparison on the tier.
     */
    @Test
    fun `every tier withholds at least what the tier below it withholds`() {
        val comparisons = HashMap<Pair<Ruleset, Ruleset>, Int>()
        val releases = ArrayList<String>()

        forEachBoard(seeds = 1L..80L) { state, board ->
            for (column in 0 until TABLEAU_COLUMNS) {
                val top = state.tableau[column].lastOrNull()?.takeIf { it.faceUp }?.card ?: continue
                if (!canPlaceOnFoundation(state.foundations, top)) continue

                for (higher in Ruleset.entries) {
                    val lower = Ruleset.entries.getOrNull(higher.ordinal - 1) ?: continue
                    val heldBelow = board.foundationRank(column, lower) == FastBoard.RANK_WITHHELD
                    val heldHere = board.foundationRank(column, higher) == FastBoard.RANK_WITHHELD
                    comparisons.merge(lower to higher, 1, Int::plus)
                    if (heldBelow && !heldHere && releases.size < 5) {
                        releases += "$higher released a rank-${FastBoard.rankOf(top.id)} card that $lower held"
                    }
                }
            }
        }

        assertTrue("no foundation play was compared, so this proves nothing", comparisons.values.sum() > 500)
        for ((pair, count) in comparisons) {
            assertTrue("${pair.first} -> ${pair.second} was never compared", count > 0)
        }
        assertEquals("a tier released what the one below it held: $releases", emptyList<String>(), releases)
    }

    /**
     * The same property for a card coming down from the stock and waste, which reaches
     * `pileRank` rather than `foundationRank` and carries its own copy of the restraint clause —
     * so the two can drift apart, and one being right says nothing about the other.
     */
    @Test
    fun `the pile tap inherits restraint the same way the tableau tap does`() {
        val releases = ArrayList<String>()
        var compared = 0

        forEachBoard(seeds = 1L..80L) { state, board ->
            for (column in 0 until TABLEAU_COLUMNS) {
                val top = state.tableau[column].lastOrNull()?.takeIf { it.faceUp }?.card ?: continue
                if (!canPlaceOnFoundation(state.foundations, top)) continue

                for (higher in Ruleset.entries) {
                    val lower = Ruleset.entries.getOrNull(higher.ordinal - 1) ?: continue
                    val heldBelow = board.pileRank(top.id, toFoundation = true, ruleset = lower) == FastBoard.RANK_WITHHELD
                    val heldHere = board.pileRank(top.id, toFoundation = true, ruleset = higher) == FastBoard.RANK_WITHHELD
                    compared++
                    if (heldBelow && !heldHere && releases.size < 5) {
                        releases += "$higher released a rank-${FastBoard.rankOf(top.id)} pile card that $lower held"
                    }
                }
            }
        }

        assertTrue("no pile play was compared, so this proves nothing", compared > 500)
        assertEquals("a tier released what the one below it held: $releases", emptyList<String>(), releases)
    }

    /**
     * Full foundation restraint is Medium's defining addition and the one the ladder's own
     * summary calls "Easy's rules, plus". Named directly rather than left to the containment
     * checks above, so a regression reports the rule that broke and not just the shape.
     */
    @Test
    fun `full foundation restraint reaches every tier at or above medium`() {
        var firedAbove = 0

        forEachBoard(seeds = 1L..80L) { state, board ->
            for (column in 0 until TABLEAU_COLUMNS) {
                val top = state.tableau[column].lastOrNull()?.takeIf { it.faceUp }?.card ?: continue
                if (!canPlaceOnFoundation(state.foundations, top)) continue
                if (board.foundationRank(column, Ruleset.MEDIUM) != FastBoard.RANK_WITHHELD) continue
                if (board.foundationRank(column, Ruleset.EASY) == FastBoard.RANK_WITHHELD) continue

                // Medium held this and Easy did not, so full restraint is what held it. Every tier
                // above Medium is "Medium's rules, plus", so each must hold it too — taken from
                // the enum rather than listed, so a tier added above is covered on the day it
                // lands. Being listed by hand is how this went unnoticed the first time.
                for (higher in Ruleset.entries.filter { it > Ruleset.MEDIUM }) {
                    assertEquals(
                        "$higher did not inherit Medium's full foundation restraint",
                        FastBoard.RANK_WITHHELD,
                        board.foundationRank(column, higher),
                    )
                }
                firedAbove++
            }
        }

        assertTrue("full restraint never fired, so this proves nothing", firedAbove > 10)
    }
}
