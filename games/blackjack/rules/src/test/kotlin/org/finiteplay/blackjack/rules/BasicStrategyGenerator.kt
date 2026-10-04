package org.finiteplay.blackjack.rules

/**
 * The offline generator behind the Hint table (`DESIGN.md` "Hint", `EXECUTION_PLAN.md` B6). It
 * lives in the rules module's *test* sources so it can never reach an app: the app ships only the
 * table it produces.
 *
 * For each Hint input — a pair, a soft total or a hard total, against each dealer up card — it
 * computes the expected value, per unit staked, of each action under the frozen rules: six decks,
 * dealer stands on soft 17, double on any two cards and after a split, split Aces once with one card
 * each, no surrender. The shoe is tracked by composition, so every draw — the player's and the
 * dealer's — has the exact odds given the cards already known, and the dealer is conditioned on not
 * having blackjack, which is what the peek guarantees.
 *
 * Two deliberate simplifications, both small and both surfaced by the chart comparison in the test:
 * a hand is represented by one canonical two-card composition per total (hence "total-dependent"),
 * and a split hand's value ignores further resplitting, valuing it as two independent hands.
 */
class BasicStrategyGenerator {
    /** Expected value of each action for one cell; `null` where the action does not exist for it. */
    data class Evs(val stand: Double, val hit: Double, val double: Double?, val split: Double?)

    private val standMemo = HashMap<String, Double>()
    private val hitMemo = HashMap<String, Double>()
    private val dealerMemo = HashMap<String, DoubleArray>()

    private fun freshCounts(): IntArray = IntArray(11) { v -> if (v == 0) 0 else if (v == 10) 96 else 24 }

    private fun key(counts: IntArray, vararg more: Int) = counts.joinToString(",") + "|" + more.joinToString(",")

    private fun total(sum: Int, aces: Int): Int = if (aces > 0 && sum + 10 <= 21) sum + 10 else sum

    /** The dealer's final-total distribution: indices 0..4 are 17..21, index 5 is a bust. */
    private fun dealer(counts: IntArray, sum: Int, aces: Int, first: Boolean, up: Int): DoubleArray {
        val total = total(sum, aces)
        if (!first && total >= 17) {
            val out = DoubleArray(6)
            if (total > 21) out[5] = 1.0 else out[total - 17] = 1.0
            return out
        }
        return dealerMemo.getOrPut(key(counts, sum, aces, if (first) 1 else 0, up)) {
            // The hole card, when the up card is an Ace or a ten, is conditioned not to make blackjack.
            val forbidden = if (first && up == 1) 10 else if (first && up == 10) 1 else 0
            var pool = 0
            for (v in 1..10) if (v != forbidden) pool += counts[v]
            val result = DoubleArray(6)
            for (v in 1..10) {
                if (v == forbidden || counts[v] == 0) continue
                val p = counts[v].toDouble() / pool
                counts[v]--
                val sub = dealer(counts, sum + v, aces + if (v == 1) 1 else 0, false, up)
                counts[v]++
                for (i in 0..5) result[i] += p * sub[i]
            }
            result
        }
    }

    /** Value of standing on [playerTotal] against the dealer, the shoe being [counts]. */
    private fun stand(counts: IntArray, playerTotal: Int, up: Int): Double {
        if (playerTotal > 21) return -1.0
        return standMemo.getOrPut(key(counts, playerTotal, up)) {
            val d = dealer(counts, up, if (up == 1) 1 else 0, true, up)
            var ev = d[5] // dealer busts
            for (t in 17..21) {
                val p = d[t - 17]
                ev += if (playerTotal > t) p else if (playerTotal < t) -p else 0.0
            }
            ev
        }
    }

    /** Best value of a hand that may still hit or stand but not double. */
    private fun play(counts: IntArray, sum: Int, aces: Int, up: Int): Double {
        val t = total(sum, aces)
        if (t > 21) return -1.0
        if (t == 21) return stand(counts, 21, up)
        return maxOf(stand(counts, t, up), hit(counts, sum, aces, up))
    }

    private fun hit(counts: IntArray, sum: Int, aces: Int, up: Int): Double =
        hitMemo.getOrPut(key(counts, sum, aces, up)) {
            var pool = 0
            for (v in 1..10) pool += counts[v]
            var ev = 0.0
            for (v in 1..10) {
                if (counts[v] == 0) continue
                val p = counts[v].toDouble() / pool
                counts[v]--
                ev += p * play(counts, sum + v, aces + if (v == 1) 1 else 0, up)
                counts[v]++
            }
            ev
        }

    private fun double(counts: IntArray, sum: Int, aces: Int, up: Int): Double {
        var pool = 0
        for (v in 1..10) pool += counts[v]
        var ev = 0.0
        for (v in 1..10) {
            if (counts[v] == 0) continue
            val p = counts[v].toDouble() / pool
            counts[v]--
            ev += p * stand(counts, total(sum + v, aces + if (v == 1) 1 else 0), up)
            counts[v]++
        }
        return 2 * ev
    }

    /** Value of one split hand holding [card] and about to receive its second card. */
    private fun splitHand(counts: IntArray, card: Int, up: Int): Double {
        var pool = 0
        for (v in 1..10) pool += counts[v]
        var ev = 0.0
        for (v in 1..10) {
            if (counts[v] == 0) continue
            val p = counts[v].toDouble() / pool
            counts[v]--
            val sum = card + v
            val aces = (if (card == 1) 1 else 0) + (if (v == 1) 1 else 0)
            ev += p * if (card == 1) {
                // Split Aces take one card and are complete.
                stand(counts, total(sum, aces), up)
            } else {
                // Any other split hand may hit, stand or double.
                val t = total(sum, aces)
                if (t >= 21) stand(counts, t, up)
                else maxOf(stand(counts, t, up), hit(counts, sum, aces, up), double(counts, sum, aces, up))
            }
            counts[v]++
        }
        return ev
    }

    /** Values for a hand of [first] and [second] (card values, Ace = 1) against up card [up]. */
    fun evs(first: Int, second: Int, up: Int): Evs {
        // Memos are per cell: states from other cells share nothing worth the memory they cost.
        standMemo.clear(); hitMemo.clear(); dealerMemo.clear()
        val counts = freshCounts()
        counts[up]--
        counts[first]--
        counts[second]--
        val sum = first + second
        val aces = (if (first == 1) 1 else 0) + (if (second == 1) 1 else 0)
        val t = total(sum, aces)
        val standEv = stand(counts, t, up)
        val hitEv = if (t >= 21) -2.0 else hit(counts, sum, aces, up)
        val doubleEv = if (t >= 21) null else double(counts, sum, aces, up)
        val splitEv = if (first == second) 2 * splitHand(counts, first, up) else null
        return Evs(standEv, hitEv, doubleEv, splitEv)
    }

    companion object {
        /** Dealer up cards in column order: 2..10 then Ace (1). */
        val UP_CARDS = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 1)

        /** Gap between best and second-best action below which a cell is called close. */
        const val CLOSE_MARGIN = 0.01

        /** One canonical two-card composition for a hard total of 5..20 (a pair only where no other exists). */
        fun hardComposition(total: Int): Pair<Int, Int> {
            if (total == 20) return 10 to 10
            for (a in minOf(10, total - 2) downTo 2) {
                val b = total - a
                if (b in 2..10 && a != b) return a to b
            }
            return (total / 2) to (total - total / 2)
        }

        /**
         * The table: for hard 5..21, soft 12..21 and pairs A..10, one letter per dealer up card —
         * H hit, S stand, D double else hit, d double else stand, P split.
         */
        fun generate(): Table {
            val gen = BasicStrategyGenerator()
            val close = mutableListOf<String>()

            fun letter(label: String, e: Evs, allowSplit: Boolean): Char {
                val options = buildList {
                    add('S' to e.stand)
                    if (e.hit > -2.0) add('H' to e.hit)
                    e.double?.let { add('D' to it) }
                    if (allowSplit) e.split?.let { add('P' to it) }
                }.sortedByDescending { it.second }
                if (options.size > 1 && options[0].second - options[1].second < CLOSE_MARGIN) close += label
                val best = options.first().first
                return if (best == 'D') (if (e.hit >= e.stand) 'D' else 'd') else best
            }

            val hard = (5..21).associateWith { total ->
                val (a, b) = if (total == 21) (10 to 11 - 1).let { 10 to 10 } else hardComposition(total)
                UP_CARDS.map { up ->
                    val e = if (total == 21) Evs(gen.evs(10, 10, up).stand, -2.0, null, null) else gen.evs(a, b, up)
                    letter("hard $total vs ${upName(up)}", e, allowSplit = false)
                }.joinToString("")
            }
            val soft = (12..21).associateWith { total ->
                val other = total - 11
                UP_CARDS.map { up ->
                    val e = if (total == 21) Evs(gen.evs(1, 10, up).stand, -2.0, null, null) else gen.evs(1, other, up)
                    letter("soft $total vs ${upName(up)}", e, allowSplit = false)
                }.joinToString("")
            }
            val pairs = (1..10).associateWith { value ->
                UP_CARDS.map { up -> letter("pair $value vs ${upName(up)}", gen.evs(value, value, up), allowSplit = true) }.joinToString("")
            }
            return Table(hard, soft, pairs, close)
        }

        private fun upName(up: Int) = if (up == 1) "A" else up.toString()
    }

    data class Table(
        val hard: Map<Int, String>,
        val soft: Map<Int, String>,
        val pairs: Map<Int, String>,
        val closeCells: List<String>,
    )
}
