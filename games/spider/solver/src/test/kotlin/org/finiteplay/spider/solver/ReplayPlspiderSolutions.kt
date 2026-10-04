package org.finiteplay.spider.solver

import java.io.File
import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.Move
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.isLegal

private val plspiderToFinitePlay = intArrayOf(0, 4, 5, 1, 6, 7, 2, 8, 9, 3)
private val pileMove = Regex("(\\d+)#(\\d+)>(\\d+)")
private val completedRun = Regex("(\\d+)C")

/**
 * args: inputDir outputDir firstSeed lastSeed (inclusive). firstSeed/lastSeed default to 1/4 for
 * the original single-batch call shape.
 *
 * A seed whose `seed-N.txt` is missing, or present but never reached a `Won` line (plspider lost,
 * hit its own timeout, or the file is otherwise empty), is skipped rather than treated as an
 * error: a large scan expects most attempts among a huge seed range to end this way, and the whole
 * point of scanning past far more raw seeds than the certified target (`docs/games/spider/DEALS.md`)
 * is that skips are normal, not exceptional.
 *
 * **Known, low-impact, safely-handled limitation:** a `pile#13>pile` token (moving a full
 * King-to-Ace run) has been observed, in a real campaign, to occasionally translate to a negative
 * `fromIndex` — meaning this replica's own tableau column was shorter than 13 cards at that point,
 * so plspider's and this replay's board states had already diverged by then, for a reason not yet
 * root-caused. Measured impact: 2 of 824 real wins in one campaign batch (0.24%). This is caught by
 * the same per-seed `try`/`catch` as any other translation surprise — never silently certified,
 * always reported as `FAILED` and skipped — so the failure mode is lost throughput on a rare seed,
 * never a false certificate. Left unfixed deliberately for now rather than risking a speculative,
 * insufficiently-validated change to this file while a real campaign depends on its correctness;
 * revisit if the rate climbs enough to matter, or once the campaign this file counts against is
 * no longer running.
 */
fun main(args: Array<String>) {
    val inputDirectory = File(args[0])
    val outputDirectory = File(args[1]).also(File::mkdirs)
    val firstSeed = args.getOrNull(2)?.toLong() ?: 1L
    val lastSeed = args.getOrNull(3)?.toLong() ?: 4L

    for (seed in firstSeed..lastSeed) {
        val seedFile = File(inputDirectory, "seed-$seed.txt")
        val wonLine = seedFile.takeIf { it.exists() }
            ?.readLines()
            ?.firstOrNull { it.startsWith("Won") }
        if (wonLine == null) {
            println("SKIPPED seed=$seed (no Won line)")
            continue
        }
        val tokens = wonLine.split(Regex("\\s+"))
        // A large scan is exactly where a rare, previously-unseen plspider notation quirk is most
        // likely to surface — caught per seed (loudly, as FAILED) rather than crashing the whole
        // batch's JVM and losing every already-verified seed's result in the same process.
        try {
            var state = dealGame(seed, GameVersions(0, 1, 1), SuitCount.FOUR)
            val certificate = ArrayList<String>()
            var completionMarkers = 0

            for (token in tokens) {
                when {
                    token == "D" -> {
                        val move = Move.DealRow
                        check(isLegal(state, move)) { "seed $seed: illegal stock deal after ${certificate.size} moves" }
                        state = applyMove(state, move)
                        certificate += "DEAL"
                    }
                    pileMove.matches(token) -> {
                        val (plFrom, count, plTo) = pileMove.matchEntire(token)!!.destructured
                        val from = plspiderToFinitePlay[plFrom.toInt() - 1]
                        val to = plspiderToFinitePlay[plTo.toInt() - 1]
                        val fromIndex = state.tableau[from].size - count.toInt()
                        val move = Move.TableauToTableau(from, fromIndex, to)
                        check(isLegal(state, move)) {
                            "seed $seed: illegal $token translated to $move after ${certificate.size} moves"
                        }
                        state = applyMove(state, move)
                        certificate += "MOVE $from $fromIndex $to"
                    }
                    completedRun.matches(token) -> {
                        completionMarkers++
                        check(state.sequencesBanked >= completionMarkers) {
                            "seed $seed: completion marker $completionMarkers precedes FinitePlay banking"
                        }
                    }
                }
            }

            check(completionMarkers == 8) { "seed $seed: expected 8 completion markers, got $completionMarkers" }
            check(state.isWon) { "seed $seed: plspider line did not replay to a FinitePlay win" }
            File(outputDirectory, "solution-$seed.txt").writeText(buildString {
                appendLine("seed=$seed suitCount=FOUR catalogVersion=0 rulesVersion=1 shuffleVersion=1")
                appendLine("finitePlayMoves=${certificate.size} completedRuns=$completionMarkers verified=true")
                certificate.forEach(::appendLine)
            })
            println("VERIFIED seed=$seed finitePlayMoves=${certificate.size} completedRuns=$completionMarkers")
        } catch (e: IllegalStateException) {
            println("FAILED seed=$seed: ${e.message}")
        }
    }
}
