package org.finiteplay.core.session

import kotlin.math.ceil

/**
 * A distribution over completed games' elapsed time or move count, using the nearest-rank
 * method: `rank = ceil(percentile * sampleCount)`, clamped to `1..sampleCount`.
 *
 * No game concept anywhere in this file — it is percentile math over a `List<Long>`, which is
 * why it is the one piece of a game's statistics that moved here outright rather than through a
 * game-supplied seam. What each game counts as a sample (only wins? only one draw mode?) is
 * still the game's own choice, made before calling [of].
 */
data class PercentileDistribution(
    val sampleSize: Int,
    val min: Long,
    val p10: Long,
    val p50: Long,
    val p90: Long,
    val max: Long,
) {
    companion object {
        fun of(values: List<Long>): PercentileDistribution? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            fun nearestRank(percentile: Double): Long {
                val rank = ceil(percentile * sorted.size).toInt().coerceIn(1, sorted.size)
                return sorted[rank - 1]
            }
            return PercentileDistribution(
                sampleSize = sorted.size,
                min = sorted.first(),
                p10 = nearestRank(0.10),
                p50 = nearestRank(0.50),
                p90 = nearestRank(0.90),
                max = sorted.last(),
            )
        }
    }
}
