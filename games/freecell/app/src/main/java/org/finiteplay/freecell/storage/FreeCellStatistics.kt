package org.finiteplay.freecell.storage

import org.finiteplay.core.session.SessionStatistics
import org.finiteplay.core.session.SolutionEfficiency
import org.finiteplay.core.session.StatisticsPeriod
import org.finiteplay.core.session.computeSessionStatistics
import org.finiteplay.core.session.computeSolutionEfficiency
import org.finiteplay.core.session.filterByPeriod

/**
 * `core:session`'s shared aggregate plus the two things only FreeCell's own record can supply:
 * hints taken, and how the player's wins compare with each deal's certified solution.
 */
data class FreeCellStatistics(
    val session: SessionStatistics,
    /** Hints taken across every game in the window, won or lost. */
    val hintsUsed: Int,
    /** Wins reached without taking a single hint. */
    val hintFreeWins: Int,
    val efficiency: SolutionEfficiency?,
)

/**
 * Aggregates [records] over one [period]. Unlike Spider's own version of this function, there is
 * no per-mode split to filter by first — one statistics pool (`docs/games/freecell/RULES.md`
 * "Free cells and difficulty").
 */
fun computeFreeCellStatistics(
    records: List<FreeCellHistoryRecord>,
    period: StatisticsPeriod,
    nowMillis: Long,
    hasUnfinishedPlayedGame: Boolean = false,
): FreeCellStatistics {
    val windowed = filterByPeriod(records, period, nowMillis) { it.timestampMillis }
    val wins = windowed.filter { it.outcome == FreeCellOutcome.WIN }
    return FreeCellStatistics(
        session = computeSessionStatistics(
            records = windowed,
            hasUnfinishedPlayedGame = hasUnfinishedPlayedGame,
            timestampMillis = { it.timestampMillis },
            isWin = { it.outcome == FreeCellOutcome.WIN },
            elapsedMillis = { it.elapsedMillis },
            moveCount = { it.moveCount },
        ),
        hintsUsed = windowed.sumOf { it.hintsUsed },
        hintFreeWins = wins.count { it.hintsUsed == 0 },
        efficiency = computeSolutionEfficiency(wins, { it.moveCount }, { it.solutionMoveCount }),
    )
}
