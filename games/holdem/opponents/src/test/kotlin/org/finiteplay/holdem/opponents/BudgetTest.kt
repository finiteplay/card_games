package org.finiteplay.holdem.opponents

import org.finiteplay.holdem.rules.Street
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An opponent decision, equity estimate included, on this machine (`EXECUTION_PLAN.md` H7 "Budget").
 * The 50 ms p95 target is the reference phone's and is measured there; here the bar is a fifth of it.
 */
class BudgetTest {
    private fun percentile(sorted: LongArray, p: Double) = sorted[((sorted.size - 1) * p).toInt()] / 1e6

    @Test
    fun `an opponent decision is well under the budget on this machine`() {
        val all = ArrayList<Long>()
        val postflop = ArrayList<Long>()
        val hints = ArrayList<Long>()
        for (round in 0 until 2) for (t in 0 until 60) {
            val opponents = drawOpponents(t.toLong())
            val policies = listOf(ProfilePolicy(Profiles.tag)) + opponents.map { ProfilePolicy(it.profile) }
            val timed = policies.map { p ->
                OpponentPolicy { v, s ->
                    val start = System.nanoTime()
                    val a = p.decide(v, s)
                    val took = System.nanoTime() - start
                    if (round == 1) {
                        all += took
                        if (v.street != Street.PREFLOP) postflop += took
                    }
                    a
                }
            }
            TournamentRunner.play(40_000L + t, timed) { v, _ ->
                if (round == 1 && v.history.size % 5 == 0) {
                    val start = System.nanoTime()
                    hint(v)
                    hints += System.nanoTime() - start
                }
            }
        }
        for ((name, list) in listOf("decision" to all, "postflop decision" to postflop, "hint" to hints)) {
            val sorted = list.toLongArray().also { it.sort() }
            println("BUDGET %-18s n=%d mean=%.3f ms p50=%.3f p95=%.3f p99=%.3f max=%.3f".format(name, sorted.size, sorted.average() / 1e6, percentile(sorted, 0.5), percentile(sorted, 0.95), percentile(sorted, 0.99), sorted.last() / 1e6))
        }
        val sorted = postflop.toLongArray().also { it.sort() }
        assertTrue("postflop p95 ${percentile(sorted, 0.95)} ms", percentile(sorted, 0.95) < 10.0)
        val allSorted = all.toLongArray().also { it.sort() }
        assertTrue(percentile(allSorted, 0.95) < 10.0)
    }
}
