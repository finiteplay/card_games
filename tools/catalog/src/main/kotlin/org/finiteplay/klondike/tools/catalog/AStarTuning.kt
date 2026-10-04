package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.DrawMode
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.solver.search.SNode
import org.finiteplay.klondike.solver.search.SearchOrdering
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.Solver
import org.finiteplay.klondike.solver.search.SolverLimits
import org.finiteplay.klondike.solver.search.aceBurialCount
import org.finiteplay.klondike.solver.search.applySearchMove
import org.finiteplay.klondike.solver.search.foundationDeficit
import org.finiteplay.klondike.solver.search.generateMoves
import org.finiteplay.klondike.solver.search.movesLowerBound
import org.finiteplay.klondike.solver.search.neededCardDepth
import org.finiteplay.klondike.solver.search.snodeFrom
import java.io.File
import java.util.Locale

internal const val ASTAR_TUNING_SPLIT_SALT: Long = 0x51A7L

enum class TuningSplit { TRAIN, VALIDATION, TEST }

enum class SeedLabel { SOLVED, PROVEN_LOST, UNKNOWN }

enum class TrialOutcome { SOLVED, PROVEN_LOST, INCONCLUSIVE, ERROR }

data class TuningSeed(
    val seed: Long,
    val label: SeedLabel,
    val split: TuningSplit,
    val stratum: String,
)

data class AStarTrial(
    val seed: Long,
    val outcome: TrialOutcome,
    val nodes: Long,
    val certificateMoves: Int,
    val certificateChoices: Int = 0,
    val elapsedMs: Long = 0,
)

data class PositiveTuningScore(
    val positiveSeeds: Int,
    val solvedAtMaxCap: Int,
    val coverageAtMaxCap: Double,
    val coverageArea: Double,
    val par2Nodes: Double,
    val p95SolvedNodes: Long,
    val medianCertificateMoves: Int,
)

private fun splitBucket(seed: Long, salt: Long): Int =
    Math.floorMod((seed - 1L) * 7_919L + salt, 10_000L).toInt()

/** Stable 70/15/15 split; 1..10,000 is a permutation of all buckets exactly once. */
fun tuningSplit(seed: Long, salt: Long = ASTAR_TUNING_SPLIT_SALT): TuningSplit = when (splitBucket(seed, salt)) {
    in 0 until 7_000 -> TuningSplit.TRAIN
    in 7_000 until 8_500 -> TuningSplit.VALIDATION
    else -> TuningSplit.TEST
}

private fun percentile95(values: List<Long>): Long {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    val index = kotlin.math.ceil(sorted.size * 0.95).toInt().coerceAtLeast(1) - 1
    return sorted[index]
}

private fun median(values: List<Int>): Int {
    if (values.isEmpty()) return 0
    val sorted = values.sorted()
    return sorted[(sorted.size - 1) / 2]
}

fun scorePositiveTrials(
    trials: List<AStarTrial>,
    nodeCaps: List<Long>,
): PositiveTuningScore {
    require(nodeCaps.isNotEmpty() && nodeCaps.all { it > 0 }) { "node caps must be positive" }
    val sortedCaps = nodeCaps.sorted()
    val maxCap = sortedCaps.last()
    fun solvedWithin(trial: AStarTrial, cap: Long) = trial.outcome == TrialOutcome.SOLVED && trial.nodes <= cap

    val solved = trials.filter { solvedWithin(it, maxCap) }
    val coverageArea = sortedCaps.map { cap -> trials.count { solvedWithin(it, cap) }.toDouble() / trials.size.coerceAtLeast(1) }.average()
    val par2 = trials.sumOf { if (solvedWithin(it, maxCap)) it.nodes.toDouble() else 2.0 * maxCap } / trials.size.coerceAtLeast(1)
    return PositiveTuningScore(
        positiveSeeds = trials.size,
        solvedAtMaxCap = solved.size,
        coverageAtMaxCap = solved.size.toDouble() / trials.size.coerceAtLeast(1),
        coverageArea = coverageArea,
        par2Nodes = par2,
        p95SolvedNodes = percentile95(solved.map { it.nodes }),
        medianCertificateMoves = median(solved.map { it.certificateMoves }),
    )
}

private fun parseCsv(file: File): List<Map<String, String>> {
    val lines = file.readLines().filter { it.isNotBlank() }
    require(lines.isNotEmpty()) { "empty CSV: $file" }
    val header = lines.first().split(',')
    return lines.drop(1).mapNotNull { line ->
        val cells = line.split(',')
        if (cells.size != header.size) null else header.zip(cells).toMap()
    }
}

private fun benchmarkLabel(row: Map<String, String>): SeedLabel {
    val tierWon = listOf("trivial", "easy", "medium", "hard", "expert").any { row["${it}_win"] == "1" }
    val fullSolverWon = row["dfs_outcome"] == "WINNABLE" || row["astar_outcome"] == "WINNABLE"
    if (tierWon || fullSolverWon) return SeedLabel.SOLVED
    val fullProof = row["dfs_outcome"] == "PROVEN_LOST" || row["astar_outcome"] == "PROVEN_LOST"
    return if (fullProof) SeedLabel.PROVEN_LOST else SeedLabel.UNKNOWN
}

private fun nodeBin(nodes: Long): String = when {
    nodes <= 0 -> "unmeasured"
    nodes <= 10_000 -> "le10k"
    nodes <= 100_000 -> "le100k"
    nodes <= 300_000 -> "le300k"
    else -> "gt300k"
}

fun prepareAStarTuningSeeds(benchmarkPath: String, outputPath: String) {
    val rows = parseCsv(File(benchmarkPath))
    val seeds = rows.map { row ->
        val label = benchmarkLabel(row)
        val tier = row["lowest_ruleset"].orEmpty().ifBlank { "NONE" }
        val bin = nodeBin(row["astar_nodes"].orEmpty().toLongOrNull() ?: 0)
        val seed = row.getValue("seed").toLong()
        TuningSeed(seed, label, tuningSplit(seed), "${label.name}_${tier}_$bin")
    }.sortedBy { it.seed }

    File(outputPath).also { it.parentFile?.mkdirs() }.printWriter().use { writer ->
        writer.println("seed,label,split,stratum")
        for (seed in seeds) writer.println("${seed.seed},${seed.label},${seed.split},${seed.stratum}")
    }
    println("wrote ${seeds.size} tuning seeds to $outputPath")
}

private fun readTuningSeeds(path: String): List<TuningSeed> = parseCsv(File(path)).map { row ->
    TuningSeed(
        seed = row.getValue("seed").toLong(),
        label = SeedLabel.valueOf(row.getValue("label")),
        split = TuningSplit.valueOf(row.getValue("split")),
        stratum = row.getValue("stratum"),
    )
}

private fun trialOutcome(outcome: SolveOutcome): TrialOutcome = when (outcome) {
    is SolveOutcome.Solved -> TrialOutcome.SOLVED
    is SolveOutcome.Unsolved -> TrialOutcome.PROVEN_LOST
    is SolveOutcome.Timeout -> TrialOutcome.INCONCLUSIVE
    is SolveOutcome.Error -> TrialOutcome.ERROR
}

private fun choicesIn(line: List<Move>): Int = line.count { it != Move.Draw && it != Move.Recycle }

fun evaluateAStarOrdering(
    seedManifestPath: String,
    split: TuningSplit,
    maxSeeds: Int,
    maxNodes: Long,
    maxDurationMs: Long,
    ordering: SearchOrdering,
    includeFoundationWithdrawal: Boolean,
    trialOutputPath: String? = null,
): PositiveTuningScore {
    val seeds = readTuningSeeds(seedManifestPath)
        .asSequence()
        .filter { it.label == SeedLabel.SOLVED && it.split == split }
        .sortedBy { splitBucket(it.seed, ASTAR_TUNING_SPLIT_SALT xor 0x2D31L) }
        .take(maxSeeds)
        .toList()
    require(seeds.isNotEmpty()) { "no SOLVED seeds selected from $seedManifestPath for $split" }

    val limits = SolverLimits(maxNodes = maxNodes, maxDurationMs = maxDurationMs)
    val trials = seeds.mapIndexed { index, tuningSeed ->
        val outcome = Solver.solve(
            dealGame(tuningSeed.seed, D1S_SPIKE_VERSIONS),
            limits,
            includeFoundationWithdrawal = includeFoundationWithdrawal,
            ordering = ordering,
        )
        if ((index + 1) % 50 == 0) println("evaluated ${index + 1}/${seeds.size}")
        val certificate = (outcome as? SolveOutcome.Solved)?.certificate.orEmpty()
        AStarTrial(
            seed = tuningSeed.seed,
            outcome = trialOutcome(outcome),
            nodes = outcome.nodes,
            certificateMoves = certificate.size,
            certificateChoices = choicesIn(certificate),
            elapsedMs = outcome.elapsedMs,
        )
    }

    trialOutputPath?.let { path ->
        File(path).also { it.parentFile?.mkdirs() }.printWriter().use { writer ->
            writer.println("seed,outcome,nodes,certificate_moves,certificate_choices,elapsed_ms")
            for (trial in trials) writer.println(
                "${trial.seed},${trial.outcome},${trial.nodes},${trial.certificateMoves},${trial.certificateChoices},${trial.elapsedMs}",
            )
        }
    }
    return scorePositiveTrials(trials, listOf(maxNodes / 12, maxNodes / 6, maxNodes / 3, maxNodes).filter { it > 0 }.distinct())
}

fun PositiveTuningScore.toJson(): String = String.format(
    Locale.ROOT,
    "{\"positiveSeeds\":%d,\"solvedAtMaxCap\":%d,\"coverageAtMaxCap\":%.9f," +
        "\"coverageArea\":%.9f,\"par2Nodes\":%.3f,\"p95SolvedNodes\":%d,\"medianCertificateMoves\":%d}",
    positiveSeeds,
    solvedAtMaxCap,
    coverageAtMaxCap,
    coverageArea,
    par2Nodes,
    p95SolvedNodes,
    medianCertificateMoves,
)

private data class RankingFeatures(
    val lowerBound: Int,
    val faceDown: Int,
    val aceBurial: Int,
    val neededDepth: Int,
    val foundationDeficit: Int,
    val stockCards: Int,
    val wasteCards: Int,
)

private fun rankingFeatures(node: SNode, drawCount: Int) = RankingFeatures(
    lowerBound = movesLowerBound(node, drawCount),
    faceDown = node.downCounts.sum(),
    aceBurial = aceBurialCount(node),
    neededDepth = neededCardDepth(node),
    foundationDeficit = foundationDeficit(node),
    stockCards = node.stockSize,
    wasteCards = node.wasteSize,
)

private fun moveType(move: Move): String = when (move) {
    Move.Draw -> "DRAW"
    Move.Recycle -> "RECYCLE"
    is Move.TableauToTableau -> "TABLEAU_TO_TABLEAU"
    is Move.TableauToFoundation -> "TABLEAU_TO_FOUNDATION"
    is Move.WasteToTableau -> "WASTE_TO_TABLEAU"
    Move.WasteToFoundation -> "WASTE_TO_FOUNDATION"
    is Move.FoundationToTableau -> "FOUNDATION_TO_TABLEAU"
}

/**
 * Exports grouped legal-child examples for offline learning-to-rank. The certificate
 * child is a positive preference, while siblings are explicitly only "not observed on
 * this certificate"; the training report must not reinterpret them as proven losses.
 */
fun exportAStarRankingData(
    seedManifestPath: String,
    split: TuningSplit,
    maxSeeds: Int,
    maxStatesPerSeed: Int,
    maxNodes: Long,
    maxDurationMs: Long,
    ordering: SearchOrdering,
    includeFoundationWithdrawal: Boolean,
    outputPath: String,
) {
    val seeds = readTuningSeeds(seedManifestPath)
        .asSequence()
        .filter { it.label == SeedLabel.SOLVED && it.split == split }
        .sortedBy { splitBucket(it.seed, ASTAR_TUNING_SPLIT_SALT xor 0x6A09L) }
        .take(maxSeeds)
        .toList()
    val limits = SolverLimits(maxNodes = maxNodes, maxDurationMs = maxDurationMs)
    var qid = 0L
    var groups = 0
    var examples = 0
    var solvedSeeds = 0

    File(outputPath).also { it.parentFile?.mkdirs() }.printWriter().use { writer ->
        writer.println(
            "qid,seed,split,state_index,label,move_type,move_priority,legal_moves," +
                "lower_bound,face_down,ace_burial,needed_depth,foundation_deficit,stock_cards,waste_cards," +
                "delta_lower_bound,delta_face_down,delta_ace_burial,delta_needed_depth,delta_foundation_deficit," +
                "delta_stock_cards,delta_waste_cards",
        )
        for ((seedIndex, tuningSeed) in seeds.withIndex()) {
            val initial = dealGame(tuningSeed.seed, D1S_SPIKE_VERSIONS)
            val outcome = Solver.solve(
                initial,
                limits,
                includeFoundationWithdrawal = includeFoundationWithdrawal,
                ordering = ordering,
            )
            val certificate = (outcome as? SolveOutcome.Solved)?.certificate ?: continue
            solvedSeeds++
            var state = initial
            val drawCount = if (state.drawMode == DrawMode.THREE) 3 else 1
            for ((stateIndex, chosenMove) in certificate.withIndex()) {
                if (stateIndex >= maxStatesPerSeed) break
                val node = snodeFrom(state)
                val moves = generateMoves(node, includeFoundationWithdrawal)
                require(moves.any { it.move == chosenMove }) {
                    "certificate move $chosenMove missing from generated branches for seed ${tuningSeed.seed} at $stateIndex"
                }
                if (moves.size > 1) {
                    qid++
                    groups++
                    val current = rankingFeatures(node, drawCount)
                    for (scored in moves) {
                        val child = rankingFeatures(applySearchMove(node, scored.move, drawCount), drawCount)
                        writer.println(
                            listOf(
                                qid, tuningSeed.seed, split, stateIndex, if (scored.move == chosenMove) 1 else 0,
                                moveType(scored.move), scored.priority, moves.size,
                                child.lowerBound, child.faceDown, child.aceBurial, child.neededDepth,
                                child.foundationDeficit, child.stockCards, child.wasteCards,
                                child.lowerBound - current.lowerBound,
                                child.faceDown - current.faceDown,
                                child.aceBurial - current.aceBurial,
                                child.neededDepth - current.neededDepth,
                                child.foundationDeficit - current.foundationDeficit,
                                child.stockCards - current.stockCards,
                                child.wasteCards - current.wasteCards,
                            ).joinToString(","),
                        )
                        examples++
                    }
                }
                state = applyMove(state, chosenMove)
            }
            if ((seedIndex + 1) % 10 == 0) println("ranking export ${seedIndex + 1}/${seeds.size}")
        }
    }
    println("wrote $examples examples in $groups groups from $solvedSeeds solved seeds to $outputPath")
}
