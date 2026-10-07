package org.finiteplay.holdem.opponents

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.stream.IntStream
import kotlin.math.sqrt

/**
 * The field's strength (`EXECUTION_PLAN.md` H7): each simple exploit, seated against five opponents
 * drawn from the tournament's seed, wins less than one tournament in six, and one opponent among five
 * always-call seats wins more. Long, so it runs as `fieldStrengthTest`, outside `check`.
 */
class FieldStrengthTest {
    private val n = System.getenv("FIELD_N")?.toInt() ?: 2000
    private val z = 1.96
    private val sixth = 1.0 / 6

    private fun wilson(wins: Int, n: Int): Pair<Double, Double> {
        val p = wins.toDouble() / n
        val d = 1 + z * z / n
        val centre = (p + z * z / (2 * n)) / d
        val half = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / d
        return centre - half to centre + half
    }

    private fun wins(policy: (Long) -> List<OpponentPolicy>, seat: Int, count: Int): Int =
        IntStream.range(0, count).parallel().map { i ->
            val seed = 0x5EED0000L + i
            if (TournamentRunner.play(seed, policy(seed), watch = seat)[seat] == 1) 1 else 0
        }.sum()

    private fun versus(exploit: OpponentPolicy): (Long) -> List<OpponentPolicy> = { seed ->
        listOf(exploit) + drawOpponents(seed).map { ProfilePolicy(it.profile) }
    }

    private fun exploit(name: String, policy: OpponentPolicy) {
        val started = System.currentTimeMillis()
        val w = wins(versus(policy), 0, n)
        val (lo, hi) = wilson(w, n)
        println("FIELD exploit %-14s wins %4d / %d = %.4f  Wilson 95%% [%.4f, %.4f]  (1/6 = %.4f)  %d s".format(name, w, n, w.toDouble() / n, lo, hi, sixth, (System.currentTimeMillis() - started) / 1000))
        assertTrue("$name wins ${w.toDouble() / n}, upper bound $hi is not under 1/6", hi < sixth)
    }

    @Test fun `always call finishes first less than one time in six`() = exploit("always-call", Exploits.alwaysCall)
    @Test fun `always raise the minimum finishes first less than one time in six`() = exploit("min-raise", Exploits.alwaysMinRaise)
    @Test fun `always all in finishes first less than one time in six`() = exploit("all-in", Exploits.alwaysAllIn)
    @Test fun `the top five percent only finishes first less than one time in six`() = exploit("top-5%", Exploits.topFivePercent)

    @Test
    fun `one opponent against five always-call seats finishes first more than one time in six`() {
        val started = System.currentTimeMillis()
        var total = 0
        var w = 0
        for (profile in Profiles.all) {
            val count = n / Profiles.all.size
            val won = wins({ listOf(Exploits.alwaysCall, ProfilePolicy(profile)) + List(4) { Exploits.alwaysCall } }, 1, count)
            val (lo, hi) = wilson(won, count)
            println("FIELD solo %-8s wins %4d / %d = %.4f  Wilson 95%% [%.4f, %.4f]".format(profile.id, won, count, won.toDouble() / count, lo, hi))
            total += count
            w += won
        }
        val (lo, hi) = wilson(w, total)
        println("FIELD solo all profiles wins %d / %d = %.4f  Wilson 95%% [%.4f, %.4f]  %d s".format(w, total, w.toDouble() / total, lo, hi, (System.currentTimeMillis() - started) / 1000))
        assertTrue("lower bound $lo is not over 1/6", lo > sixth)
    }
}
