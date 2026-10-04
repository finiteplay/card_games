package org.finiteplay.core.session

/**
 * The part of a player's record that means the same thing in every game: how often they won,
 * how quickly, in how few moves, and how many in a row.
 *
 * A game's own statistics type wraps this and adds what only it can measure — hints taken,
 * a comparison against a certified solution, anything a mode or difficulty splits on. That
 * split is the point: those extra fields are exactly the ones another game cannot fill in,
 * and folding them in here would force every game to carry columns it has no value for.
 *
 * [gamesPlayed] counts an unfinished-but-started game, while [winRate] does not — a game in
 * progress has been played but not decided. [winRate] is null rather than zero when nothing
 * has been decided either way, because "no games yet" and "lost every game" are not the same
 * reading and a zero would show them identically.
 */
data class SessionStatistics(
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
    /** The longest run of consecutive losses anywhere in the window. */
    val longestLossStreak: Int = 0,
    /** Mean elapsed time over wins; null with no win. */
    val averageElapsedMillis: Long? = null,
    /**
     * Win rate over the most recent [RECENT_GAMES] decided games, or null while fewer than
     * that many (plus one) have been decided — before then it would just repeat [winRate].
     * It answers "how am I doing lately" where [winRate] answers "how have I done overall".
     */
    val recentWinRate: Double? = null,
) {
    companion object {
        val EMPTY = SessionStatistics(
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
        )
    }
}

/** How many of the latest decided games [SessionStatistics.recentWinRate] looks at. */
const val RECENT_GAMES = 20

/**
 * Aggregates [records] into the game-independent [SessionStatistics].
 *
 * The accessors keep this free of any record shape: a caller supplies where a timestamp, an
 * outcome, an elapsed time, and a move count live on its own type, exactly as [filterByPeriod]
 * takes a timestamp accessor. Only decided games should be passed; [hasUnfinishedPlayedGame]
 * carries the one in progress, which is not a record yet.
 *
 * Bests and distributions are drawn from wins alone — a lost game's elapsed time measures when
 * the player gave up, not how long the game took, and ranking it against wins would reward
 * quitting early.
 */
fun <T> computeSessionStatistics(
    records: List<T>,
    hasUnfinishedPlayedGame: Boolean,
    timestampMillis: (T) -> Long,
    isWin: (T) -> Boolean,
    elapsedMillis: (T) -> Long,
    moveCount: (T) -> Int,
): SessionStatistics {
    if (records.isEmpty() && !hasUnfinishedPlayedGame) return SessionStatistics.EMPTY

    val chronological = records.sortedBy(timestampMillis)
    val wins = chronological.count(isWin)
    val losses = chronological.size - wins
    val winRecords = chronological.filter(isWin)

    return SessionStatistics(
        wins = wins,
        losses = losses,
        gamesPlayed = wins + losses + if (hasUnfinishedPlayedGame) 1 else 0,
        winRate = if (wins + losses == 0) null else wins.toDouble() / (wins + losses),
        currentStreak = currentStreak(chronological, isWin),
        longestStreak = longestStreak(chronological, isWin),
        bestElapsedMillis = winRecords.minOfOrNull(elapsedMillis),
        bestMoveCount = winRecords.minOfOrNull(moveCount),
        elapsedDistribution = PercentileDistribution.of(winRecords.map(elapsedMillis)),
        moveCountDistribution = PercentileDistribution.of(winRecords.map { moveCount(it).toLong() }),
        longestLossStreak = longestStreak(chronological) { !isWin(it) },
        averageElapsedMillis = if (winRecords.isEmpty()) null else winRecords.sumOf(elapsedMillis) / winRecords.size,
        recentWinRate = if (chronological.size > RECENT_GAMES) {
            chronological.takeLast(RECENT_GAMES).count(isWin).toDouble() / RECENT_GAMES
        } else {
            null
        },
    )
}

/** Consecutive wins ending at the most recent result; any loss resets it to 0. */
private fun <T> currentStreak(chronological: List<T>, isWin: (T) -> Boolean): Int {
    var streak = 0
    for (record in chronological.asReversed()) {
        if (isWin(record)) streak++ else break
    }
    return streak
}

/** The longest run of consecutive wins anywhere in chronological order. */
private fun <T> longestStreak(chronological: List<T>, isWin: (T) -> Boolean): Int {
    var best = 0
    var current = 0
    for (record in chronological) {
        if (isWin(record)) {
            current++
            best = maxOf(best, current)
        } else {
            current = 0
        }
    }
    return best
}
