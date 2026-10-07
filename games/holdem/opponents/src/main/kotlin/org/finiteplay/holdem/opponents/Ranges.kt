package org.finiteplay.holdem.opponents

import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.evaluate

private const val BINS = 48
private const val FLOOR = 0.05

/**
 * How each combination fares on one board, for judging the actions taken on it: its percentile among
 * the combinations still possible, plus a bonus for cards to come, quantized into [BINS] bins and
 * three draw classes. Cheap on purpose; the decision itself uses the Monte Carlo equity.
 */
private class BoardEval(board: IntArray, count: Int, dead: Long) {
    val bin = IntArray(Combos.COUNT) { -1 }
    val drawClass = IntArray(Combos.COUNT)

    init {
        val ids = IntArray(7)
        for (i in 0 until count) ids[2 + i] = board[i]
        val values = IntArray(Combos.COUNT)
        var m = 0
        for (k in 0 until Combos.COUNT) {
            val a = Combos.first[k]
            val b = Combos.second[k]
            if ((dead ushr a) and 1L != 0L || (dead ushr b) and 1L != 0L) continue
            ids[0] = a
            ids[1] = b
            values[m++] = evaluate(ids, 2 + count).strength
        }
        val sorted = values.copyOf(m)
        sorted.sort()
        val perOut = if (count == 3) 0.024 else if (count == 4) 0.017 else 0.0
        for (k in 0 until Combos.COUNT) {
            val a = Combos.first[k]
            val b = Combos.second[k]
            if ((dead ushr a) and 1L != 0L || (dead ushr b) and 1L != 0L) continue
            ids[0] = a
            ids[1] = b
            val v = evaluate(ids, 2 + count).strength
            val q = (lowerBound(sorted, v) + upperBound(sorted, v)) / 2.0 / m
            val outs = drawOuts(a, b, board, count)
            drawClass[k] = if (outs == 0) 0 else if (outs < 7) 1 else 2
            val e = minOf(0.995, q + outs * perOut)
            bin[k] = minOf(BINS - 1, (e * BINS).toInt())
        }
    }

    private fun lowerBound(a: IntArray, v: Int): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] < v) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun upperBound(a: IntArray, v: Int): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] <= v) lo = mid + 1 else hi = mid
        }
        return lo
    }
}

private val DRAW_OUTS = intArrayOf(0, 4, 9)

/**
 * Each live opponent's weights over the 1,326 combinations (`DESIGN.md` "Ranges"): the cards this
 * seat can see are removed, and each action the opponent took reweights every combination by how
 * likely [model] — the policy itself — is to take that action with it. A floor keeps any hand
 * possible, since the opponent may not be playing the model. Returned in the order of
 * [Situation.opponents].
 */
internal fun estimateRanges(sit: Situation, model: Profile): List<DoubleArray> {
    val view = sit.view
    var dead = 0L
    for (c in view.holeCards) dead = dead or (1L shl c.id)
    for (id in sit.boardIds) dead = dead or (1L shl id)
    val weights = sit.opponents.map {
        DoubleArray(Combos.COUNT) { k ->
            if ((dead ushr Combos.first[k]) and 1L != 0L || (dead ushr Combos.second[k]) and 1L != 0L) 0.0 else 1.0
        }
    }
    val slot = IntArray(6) { -1 }
    for ((i, s) in sit.opponents.withIndex()) slot[s] = i
    val boards = arrayOfNulls<BoardEval>(6)
    val probs = DoubleArray(Q_SIZES)
    val pre = DoubleArray(4)
    val table = Array(3) { DoubleArray(BINS) }
    val classTable = DoubleArray(HandClasses.COUNT)

    for (step in sit.steps) {
        val e = step.entry
        val w = weights.getOrNull(slot[e.seat]) ?: continue
        if (e.action == Action.Fold) continue
        val raising = e.action != Action.Check && e.action != Action.Call && e.streetTotal > step.currentBetBefore
        if (e.street == org.finiteplay.holdem.rules.Street.PREFLOP) {
            val cat = if (!raising) P_CALL else if (e.allIn) P_ALLIN else P_RAISE
            val x = PreCtx(
                effBb = sit.effectiveBb(e.seat),
                nLive = step.liveBefore,
                behind = sit.behind[e.seat],
                raises = step.betsBefore,
                limpers = step.limpersBefore,
                toCall = step.toCallBefore,
                pot = step.potBefore,
                stack = step.stackBefore,
                streetBet = step.streetBetBefore,
                currentBet = step.currentBetBefore,
                raiserBehind = step.lastRaiserBehind,
            )
            for (c in 0 until HandClasses.COUNT) {
                preflopProbs(c, x, model, pre)
                classTable[c] = FLOOR + (1 - FLOOR) * pre[cat]
            }
            var top = 0.0
            for (k in 0 until Combos.COUNT) {
                w[k] *= classTable[Combos.classOf[k]]
                if (w[k] > top) top = w[k]
            }
            if (top > 0) for (k in 0 until Combos.COUNT) w[k] /= top
            continue
        }
        val count = when (e.street) {
            org.finiteplay.holdem.rules.Street.FLOP -> 3
            org.finiteplay.holdem.rules.Street.TURN -> 4
            else -> 5
        }
        val eval = boards[count] ?: BoardEval(sit.boardIds, count, dead).also { boards[count] = it }
        val cat = when {
            !raising -> Q_CALL
            e.allIn -> Q_ALLIN
            else -> {
                val f = (e.streetTotal - step.currentBetBefore).toDouble() / (step.potBefore + step.toCallBefore).coerceAtLeast(1)
                if (f < 0.5) Q_SMALL else if (f < 0.83) Q_MEDIUM else Q_POT
            }
        }
        val x = PostCtx(
            street = e.street,
            toCall = step.toCallBefore,
            pot = step.potBefore,
            currentBet = step.currentBetBefore,
            streetBet = step.streetBetBefore,
            stack = step.stackBefore,
            opponents = step.liveBefore - 1,
            wet = wetness(sit.boardIds, count),
            paired = isPaired(sit.boardIds, count),
        )
        for (dc in 0..2) for (b in 0 until BINS) {
            val eq = Math.pow((b + 0.5) / BINS, x.opponents.coerceAtLeast(1).toDouble())
            postflopProbs(eq, DRAW_OUTS[dc], x, model, probs)
            table[dc][b] = FLOOR + (1 - FLOOR) * probs[cat]
        }
        var top = 0.0
        for (k in 0 until Combos.COUNT) {
            val b = eval.bin[k]
            if (b < 0) continue
            w[k] *= table[eval.drawClass[k]][b]
            if (w[k] > top) top = w[k]
        }
        if (top > 0) for (k in 0 until Combos.COUNT) w[k] /= top
    }
    return weights
}
