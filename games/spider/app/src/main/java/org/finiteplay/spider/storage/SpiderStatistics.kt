package org.finiteplay.spider.storage

import org.finiteplay.core.session.PercentileDistribution
import org.finiteplay.core.session.SessionStatistics
import org.finiteplay.core.session.SolutionEfficiency
import org.finiteplay.core.session.StatisticsPeriod
import org.finiteplay.core.session.computeSolutionEfficiency
import org.finiteplay.core.session.computeSessionStatistics
import org.finiteplay.core.session.filterByPeriod as sharedFilterByPeriod
import org.finiteplay.spider.layout.SuitCount

/**
 * `core:session`'s shared aggregate, plus the fields only Spider's own record shape can supply —
 * hints and the comparison against a certified solution (`docs/games/spider/DESIGN.md` "Scoring and statistics"). Mirrors Klondike's
 * own `GameStatistics` wrapper, now that there is something of Spider's own to add: this used to
 * be a bare alias to the shared type, back when there wasn't.
 */
data class SpiderStatistics(
    val wins: Int,
    val losses: Int,
    val gamesPlayed: Int,
    val winRate: Double?,
    val currentStreak: Int,
    val longestStreak: Int,
    val bestElapsedMillis: Long?,
    val bestMoveCount: Int?,
    val elapsedDistribution: PercentileDistribution?,
    val moveCountDistribution: PercentileDistribution?,
    /** Hints taken across every game in the window, won or lost. */
    val hintsUsed: Int,
    /** Wins reached without taking a single hint. */
    val hintFreeWins: Int,
    val longestLossStreak: Int,
    val averageElapsedMillis: Long?,
    /** Win rate over the latest games, or null until there are enough for it to differ from [winRate]. */
    val recentWinRate: Double?,
    /**
     * Wins against the certified solution shipped with each deal. Only a win on a deal that ships
     * one contributes.
     */
    val efficiency: SolutionEfficiency?,
) {
    /** The game-independent part of these statistics, in the shape the shared statistics screen draws. */
    fun toSession() = SessionStatistics(
        wins = wins,
        losses = losses,
        gamesPlayed = gamesPlayed,
        winRate = winRate,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        bestElapsedMillis = bestElapsedMillis,
        bestMoveCount = bestMoveCount,
        elapsedDistribution = elapsedDistribution,
        moveCountDistribution = moveCountDistribution,
        longestLossStreak = longestLossStreak,
        averageElapsedMillis = averageElapsedMillis,
        recentWinRate = recentWinRate,
    )
}

/**
 * Aggregates [records] for one [suitCount] over one [period].
 *
 * Filtering by suit count is not optional and there is no "all counts" total: one, two, and four
 * suits are different games sharing a board, and a win rate averaged across them describes nobody's
 * play (`DESIGN.md` "Scoring and statistics").
 */
fun computeSpiderStatistics(
    records: List<SpiderHistoryRecord>,
    suitCount: SuitCount,
    period: StatisticsPeriod,
    nowMillis: Long,
    hasUnfinishedPlayedGame: Boolean = false,
): SpiderStatistics {
    val windowed = filterBySuitCount(sharedFilterByPeriod(records, period, nowMillis) { it.timestampMillis }, suitCount)
    val shared = computeSessionStatistics(
        records = windowed,
        hasUnfinishedPlayedGame = hasUnfinishedPlayedGame,
        timestampMillis = { it.timestampMillis },
        isWin = { it.outcome == SpiderOutcome.WIN },
        elapsedMillis = { it.elapsedMillis },
        moveCount = { it.moveCount },
    )
    val wins = windowed.filter { it.outcome == SpiderOutcome.WIN }
    return SpiderStatistics(
        wins = shared.wins,
        losses = shared.losses,
        gamesPlayed = shared.gamesPlayed,
        winRate = shared.winRate,
        currentStreak = shared.currentStreak,
        longestStreak = shared.longestStreak,
        bestElapsedMillis = shared.bestElapsedMillis,
        bestMoveCount = shared.bestMoveCount,
        elapsedDistribution = shared.elapsedDistribution,
        moveCountDistribution = shared.moveCountDistribution,
        hintsUsed = windowed.sumOf { it.hintsUsed },
        hintFreeWins = wins.count { it.hintsUsed == 0 },
        longestLossStreak = shared.longestLossStreak,
        averageElapsedMillis = shared.averageElapsedMillis,
        recentWinRate = shared.recentWinRate,
        efficiency = computeSolutionEfficiency(wins, { it.moveCount }, { it.solutionMoveCount }),
    )
}

fun filterBySuitCount(records: List<SpiderHistoryRecord>, suitCount: SuitCount): List<SpiderHistoryRecord> =
    records.filter { it.suitCount == suitCount }
