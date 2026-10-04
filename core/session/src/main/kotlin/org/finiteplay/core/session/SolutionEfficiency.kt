package org.finiteplay.core.session

import kotlin.math.roundToLong

/**
 * A move count as a percentage of a certified solution's, rounded to the nearest whole percent:
 * 100 means the player matched the shipped line, 250 means two and a half times as many moves.
 * Shared by every statistics screen and win dialog so one game is never described two ways.
 */
fun solutionRatioPercent(moveCount: Int, solutionMoveCount: Int): Long =
    (moveCount.toDouble() * 100 / solutionMoveCount).roundToLong()

/**
 * How a player's wins compare with the certified lines shipped for those deals.
 *
 * Lower is better, unlike the other distributions: the certified line is a floor a player is
 * measured against rather than a target to beat. [matchedOrBeaten] counts wins that took no
 * more moves than the line — possible because the line is a proven winning path, not a proven
 * shortest one.
 */
data class SolutionEfficiency(
    val ratioDistribution: PercentileDistribution,
    val averagePercent: Long,
    val matchedOrBeaten: Int,
)

/**
 * Compares [wins] with their solutions. Only a win on a deal that shipped a solution
 * ([solutionMoveCount] above 0) contributes; null when none did.
 */
fun <T> computeSolutionEfficiency(
    wins: List<T>,
    moveCount: (T) -> Int,
    solutionMoveCount: (T) -> Int,
): SolutionEfficiency? {
    val compared = wins.filter { solutionMoveCount(it) > 0 }
    val ratios = compared.map { solutionRatioPercent(moveCount(it), solutionMoveCount(it)) }
    val distribution = PercentileDistribution.of(ratios) ?: return null
    return SolutionEfficiency(
        ratioDistribution = distribution,
        averagePercent = ratios.average().roundToLong(),
        matchedOrBeaten = ratios.count { it <= 100 },
    )
}
