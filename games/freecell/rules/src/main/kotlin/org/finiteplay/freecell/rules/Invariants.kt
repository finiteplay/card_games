package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card
import org.finiteplay.freecell.layout.FreeCellState
import org.finiteplay.freecell.layout.GameStatus

/**
 * Everything `docs/games/freecell/RULES.md` "Invariants" says holds after every committed
 * transition, as one check.
 *
 * Public rather than test-only, the same reasoning Spider's own `invariantViolations` gives: a
 * soak test asserting these after every move of many random games is the cheapest proof the
 * reducer is sound.
 *
 * Returns the violations rather than throwing, so a failure can name every one at once.
 */
fun invariantViolations(state: FreeCellState): List<String> {
    val problems = ArrayList<String>()

    val inTableau = state.tableau.sumOf { it.size }
    val inFreeCells = state.freeCells.count { it != null }
    val inFoundations = state.foundations.values.sum()
    val total = inTableau + inFreeCells + inFoundations
    if (total != Card.DECK_SIZE) {
        problems += "$total cards exist: $inTableau in the tableau, $inFreeCells in free cells, $inFoundations banked"
    }

    val present = state.tableau.flatten() + state.freeCells.filterNotNull()
    val duplicates = present.groupingBy { it }.eachCount().filterValues { it > 1 }
    if (duplicates.isNotEmpty()) {
        problems += "duplicate cards in play: ${duplicates.keys}"
    }

    val won = state.status == GameStatus.WON
    if (won != (inFoundations == Card.DECK_SIZE)) {
        problems += "status is ${state.status} with $inFoundations cards banked"
    }

    for ((suit, rankValue) in state.foundations) {
        if (rankValue < 0 || rankValue > 13) {
            problems += "$suit foundation holds rank value $rankValue, outside 0..13"
        }
    }

    return problems
}
