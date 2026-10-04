package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SpiderState
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal
import java.io.File

/**
 * Offline experiment, not a test: drives the real [HintEngine] the way the app does — ask for a
 * hint, play the move, ask again — from boards of certified catalog deals, and reports how often the
 * game is actually won by following it, how long the first hint takes, and any "no solution" verdict
 * (on a catalog deal, which is certified winnable from the opening, that is wrong unless the player
 * has since played something that cannot win).
 *
 * Args: repoRoot suit(one|two|four) millis deals positions, where positions is `open` (the unplayed
 * deal, which the app would prime from the shipped line — so this is the live solver's worst case)
 * or `off:f1,f2` (stand on the shipped line at those fractions and make six plausible moves off it,
 * which is a player who stopped following the hint). `-Dwalks=n` sets the walks per point.
 */
object EngineBench {
    private val versions = GameVersions(1, 1, 1)

    private class Row(val label: String, val outcome: String, val firstMs: Long, val won: Boolean, val steps: Int, val start: SpiderState? = null)

    @JvmStatic
    fun main(args: Array<String>) {
        val repo = File(args[0])
        val suit = SuitCount.valueOf(args[1].uppercase())
        val millis = args[2].toLong()
        val dealCount = args[3].toInt()
        val spec = args[4]
        val walks = System.getProperty("walks")?.toInt() ?: 2
        val only = System.getProperty("only")?.split(",")?.map { it.toInt() }?.toSet()

        val rows = ArrayList<Row>()
        for ((i, seed) in HintBench.catalogSeeds(repo, dealCount, suit).withIndex()) {
            if (only != null && (i + 1) !in only) continue
            val deal = dealGame(seed, versions, suit)
            val starts = ArrayList<Pair<String, SpiderState>>()
            if (spec == "open") {
                starts += "open" to deal
            } else {
                val line = HintBench.shippedLine(repo, seed, deal) ?: continue
                for (f in spec.removePrefix("off:").split(',').map { it.toDouble() }) {
                    val cut = (line.size * f).toInt()
                    var onLine = deal
                    for (m in line.take(cut)) onLine = applyMove(onLine, m)
                    for (w in 0 until walks) {
                        val state = HintBench.humanWalk(onLine, seed * 131 + cut * 7 + w, 6, allowDeal = false) ?: continue
                        starts += "off${"%.2f".format(f)}#$w" to state
                    }
                }
            }
            for ((label, start) in starts) rows += play("${i + 1}:$label", start, millis)
        }
        report(args, rows)
    }

    private fun play(label: String, start: SpiderState, millis: Long): Row {
        val engine = HintEngine(SpiderSolver(HINT_SOLVER_LIMITS.copy(maxMillis = millis)))
        var state = start
        var first: Long = -1
        var steps = 0
        var outcomeName = "?"
        while (!state.isWon && steps < 700) {
            val t0 = System.nanoTime()
            val outcome = engine.hint(state)
            if (first < 0) first = (System.nanoTime() - t0) / 1_000_000
            when (outcome) {
                is HintOutcome.Guidance -> {
                    if (!isLegal(state, outcome.move)) return Row(label, "ILLEGAL", first, false, steps)
                    state = applyMove(state, outcome.move)
                    steps++
                    outcomeName = "guided"
                }
                HintOutcome.NoSolution -> return Row(label, "NO_SOLUTION", first, false, steps, start)
                HintOutcome.Inconclusive -> return Row(label, "inconclusive", first, false, steps)
            }
        }
        return Row(label, if (state.isWon) "guided" else "stuck", first, state.isWon, steps)
    }

    /**
     * Tries to win every board the engine called lost, with the strongest search available for a
     * long time. A win here means the verdict was false.
     */
    private fun verify(rows: List<Row>) {
        val seconds = System.getProperty("verify")?.toLong() ?: return
        var falseVerdicts = 0
        for (r in rows.filter { it.outcome == "NO_SOLUTION" }) {
            val start = r.start ?: continue
            val line = PhaseSearch().certify(start, seconds * 1_000)
            val won = line != null && HintBench.replayWins(start, line)
            if (won) falseVerdicts++
            println("verify ${r.label}: ${if (won) "WON in ${line!!.size} moves -> FALSE NO_SOLUTION" else "no win found in ${seconds}s"}")
        }
        println("== verification: $falseVerdicts false NO_SOLUTION of ${rows.count { it.outcome == "NO_SOLUTION" }}")
    }

    private fun report(args: Array<String>, rows: List<Row>) {
        for (r in rows) println("${r.label}: ${r.outcome} first=${r.firstMs}ms won=${r.won} moves=${r.steps}")
        verify(rows)
        val won = rows.count { it.won }
        val times = rows.map { it.firstMs }.filter { it >= 0 }.sorted()
        fun pct(p: Double) = if (times.isEmpty()) 0 else times[((times.size - 1) * p).toInt()]
        println(
            "== ${args.drop(1).joinToString(" ")}: won by following the hint $won/${rows.size}; " +
                "inconclusive ${rows.count { it.outcome == "inconclusive" }}, NO_SOLUTION ${rows.count { it.outcome == "NO_SOLUTION" }}; " +
                "first hint median ${pct(0.5)}ms, p90 ${pct(0.9)}ms, max ${pct(1.0)}ms",
        )
    }
}
