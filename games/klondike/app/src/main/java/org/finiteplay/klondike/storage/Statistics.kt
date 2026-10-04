package org.finiteplay.klondike.storage

import org.finiteplay.core.session.SessionStatistics
import org.finiteplay.core.session.SolutionEfficiency
import org.finiteplay.core.session.computeSessionStatistics
import org.finiteplay.core.session.computeSolutionEfficiency
import org.finiteplay.core.session.filterByPeriod as sharedFilterByPeriod
import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.deal.DifficultyTier

/**
 * The nearest-rank percentile distribution (`docs/games/klondike/ACCEPTANCE.md`, "Statistics
 * Gate") is `:core:session`'s — no card, pile, or game concept is in the maths at all. Kept as
 * an alias under this package so every existing import here and in `StatisticsScreen.kt` still
 * resolves; only completed (won) games contribute, which is this file's choice of what to feed
 * it, not something the shared type enforces.
 */
typealias PercentileDistribution = org.finiteplay.core.session.PercentileDistribution

/**
 * Draw-one statistics aggregated from [HistoryStore]'s records (`docs/games/klondike/DESIGN.md`
 * "Statistics"). Denominators are exact: [winRate] is `null` (undefined, not zero) when
 * no game has completed either way yet; [gamesPlayed] adds one, without touching
 * [winRate], while an unfinished played game exists.
 */
data class GameStatistics(
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
    /** Mean elapsed time over wins; null with no win. */
    val averageElapsedMillis: Long?,
    val longestLossStreak: Int,
    /** Win rate over the latest games, or null until there are enough for it to differ from [winRate]. */
    val recentWinRate: Double?,
    /**
     * Wins against the certified solution shipped with each deal. Only wins on a deal that ships
     * one contribute — an Insane deal has none (`docs/games/klondike/DIFFICULTY_LEVELS.md`), and
     * draw-three is uncatalogued entirely.
     */
    val efficiency: SolutionEfficiency?,
    /**
     * Wins and losses per graded level, for the levels with at least one decided game. Draw-three
     * and games recorded before levels were kept have no level and appear nowhere here.
     */
    val byLevel: List<LevelRecord>,
) {
    companion object {
        val EMPTY = GameStatistics(
            wins = 0,
            losses = 0,
            gamesPlayed = 0,
            winRate = null,
            currentStreak = 0,
            longestStreak = 0,
            bestElapsedMillis = null,
            bestMoveCount = null,
            elapsedDistribution = null,
            moveCountDistribution = null,
            hintsUsed = 0,
            hintFreeWins = 0,
            averageElapsedMillis = null,
            longestLossStreak = 0,
            recentWinRate = null,
            efficiency = null,
            byLevel = emptyList(),
        )
    }
}

/**
 * Computes [GameStatistics] from [records]. [hasUnfinishedPlayedGame] should reflect
 * whether the *current* active game has been played (`GameSession.hasPlayerActed`) and
 * is not yet won — it is not itself a [HistoryRecord], since it hasn't been decided.
 */
fun computeStatistics(records: List<HistoryRecord>, hasUnfinishedPlayedGame: Boolean): GameStatistics {
    if (records.isEmpty() && !hasUnfinishedPlayedGame) return GameStatistics.EMPTY

    val shared = computeSessionStatistics(
        records = records,
        hasUnfinishedPlayedGame = hasUnfinishedPlayedGame,
        timestampMillis = { it.timestampMillis },
        isWin = { it.outcome == Outcome.WIN },
        elapsedMillis = { it.elapsedMillis },
        moveCount = { it.moveCount },
    )
    val winRecords = records.filter { it.outcome == Outcome.WIN }

    return GameStatistics(
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
        // What this game measures that the shared aggregate cannot: each reads a hint count, a
        // certified solution or a graded level, and a game with none of those has nothing here.
        hintsUsed = records.sumOf { it.hintsUsed },
        hintFreeWins = winRecords.count { it.hintsUsed == 0 },
        averageElapsedMillis = shared.averageElapsedMillis,
        longestLossStreak = shared.longestLossStreak,
        recentWinRate = shared.recentWinRate,
        efficiency = computeSolutionEfficiency(winRecords, { it.moveCount }, { it.solutionMoveCount }),
        byLevel = DifficultyTier.entries.mapNotNull { tier ->
            val atLevel = records.filter { it.difficulty == tier }
            if (atLevel.isEmpty()) null else LevelRecord(tier, atLevel.count { it.outcome == Outcome.WIN }, atLevel.count { it.outcome == Outcome.LOSS })
        },
    )
}

/** The game-independent part of these statistics, in the shape the shared statistics screen draws. */
fun GameStatistics.toSession() = SessionStatistics(
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

/** Decided games at one graded level. */
data class LevelRecord(val tier: DifficultyTier, val wins: Int, val losses: Int)

/**
 * Which trailing window of [HistoryRecord]s the Statistics screen aggregates over — `:core:session`'s
 * enum, aliased here for the same reason [PercentileDistribution] is: Week/Month/All Time names
 * no card, pile, or game.
 */
typealias StatisticsPeriod = org.finiteplay.core.session.StatisticsPeriod

/**
 * Filters [records] to [period]'s trailing window ending at [nowMillis], delegating the rolling
 * 7-/30-day math to `:core:session`; this wrapper only supplies where a [HistoryRecord]'s
 * timestamp lives.
 */
fun filterByPeriod(records: List<HistoryRecord>, period: StatisticsPeriod, nowMillis: Long): List<HistoryRecord> =
    sharedFilterByPeriod(records, period, nowMillis) { it.timestampMillis }

/**
 * Filters to [drawMode] alone — draw-one and draw-three history are tracked
 * separately (`docs/games/klondike/DESIGN.md` "Draw-Three Mode"), never blended into one aggregate,
 * since the two modes are not comparable difficulty (draw-three's extra-cards-per-draw
 * makes fewer waste cards reachable) and draw-three deals are not even
 * solver-certified the way draw-one's are.
 */
fun filterByDrawMode(records: List<HistoryRecord>, drawMode: DrawMode): List<HistoryRecord> =
    records.filter { it.drawMode == drawMode }
