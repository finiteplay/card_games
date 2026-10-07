package org.finiteplay.holdem.opponents

import org.finiteplay.cards.SplitMix64
import org.finiteplay.holdem.rules.evaluate

/** One opponent's range, ready to draw from: the combinations with weight, and the running total. */
internal class Sampler(weights: DoubleArray) {
    private val combo: IntArray
    private val cumulative: DoubleArray
    val total: Double

    init {
        var n = 0
        for (w in weights) if (w > 0.0) n++
        combo = IntArray(n)
        cumulative = DoubleArray(n)
        var sum = 0.0
        var k = 0
        for (i in weights.indices) if (weights[i] > 0.0) {
            sum += weights[i]
            combo[k] = i
            cumulative[k] = sum
            k++
        }
        total = sum
    }

    val isEmpty get() = combo.isEmpty()

    fun draw(u: Double): Int {
        val target = u * total
        var lo = 0
        var hi = cumulative.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid] < target) lo = mid + 1 else hi = mid
        }
        return combo[lo]
    }
}

/**
 * Monte Carlo equity: the share of the pot [h0] [h1] wins on average against hands drawn from the
 * opponents' [ranges], over boards completed from the cards no one can see. The sample count is
 * fixed, never a deadline (`DESIGN.md` "Ranges"), so the answer depends on the arguments and the
 * random source and nothing else.
 */
internal object Equity {
    fun estimate(
        h0: Int,
        h1: Int,
        board: IntArray,
        ranges: List<DoubleArray>,
        samples: Int,
        rng: SplitMix64,
    ): Double {
        val samplers = ranges.map(::Sampler)
        val n = samplers.size
        var dead = (1L shl h0) or (1L shl h1)
        for (id in board) dead = dead or (1L shl id)
        val boardCount = board.size
        val mine = IntArray(7)
        val theirs = IntArray(7)
        mine[0] = h0
        mine[1] = h1
        theirs.fill(0)
        for (i in 0 until boardCount) {
            mine[2 + i] = board[i]
            theirs[2 + i] = board[i]
        }
        val picks = IntArray(n)
        var share = 0.0
        var done = 0
        var attempts = 0
        val maxAttempts = samples * 6
        while (done < samples && attempts < maxAttempts) {
            attempts++
            var used = dead
            var ok = true
            for (j in 0 until n) {
                val s = samplers[j]
                if (s.isEmpty) {
                    ok = false
                    break
                }
                val k = s.draw(rng.nextDouble())
                val a = Combos.first[k]
                val b = Combos.second[k]
                if ((used ushr a) and 1L != 0L || (used ushr b) and 1L != 0L) {
                    ok = false
                    break
                }
                used = used or (1L shl a) or (1L shl b)
                picks[j] = k
            }
            if (!ok) continue
            for (i in boardCount until 5) {
                var id: Int
                do {
                    id = rng.nextInt(52)
                } while ((used ushr id) and 1L != 0L)
                used = used or (1L shl id)
                mine[2 + i] = id
                theirs[2 + i] = id
            }
            val my = evaluate(mine, 7).strength
            var best = -1
            var tied = 1
            for (j in 0 until n) {
                theirs[0] = Combos.first[picks[j]]
                theirs[1] = Combos.second[picks[j]]
                val v = evaluate(theirs, 7).strength
                if (v > best) {
                    best = v
                    tied = 1
                } else if (v == best) {
                    tied++
                }
            }
            share += when {
                my > best -> 1.0
                my == best -> 1.0 / (tied + 1)
                else -> 0.0
            }
            done++
        }
        return if (done == 0) 0.5 else share / done
    }
}
