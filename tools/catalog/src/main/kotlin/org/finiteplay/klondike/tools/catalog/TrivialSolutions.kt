package org.finiteplay.klondike.tools.catalog

import java.io.File
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.solution.SolutionCodec

/**
 * Shipped winning lines for the Trivial tier.
 *
 * No full-game solver is involved: the legacy reference walk takes the first non-looping move
 * at the tier's best rank, exactly as the grader does. The packed walk is converted to reducer
 * moves and replay-verified before it can ship.
 *
 * Unlike the tap enumeration used for grading, this emits the draws too. A shipped solution
 * is replayed literally through `applyMove`, so reaching a pile card several positions away
 * has to include the draws and recycles that get there, not just the play at the end.
 */

/** Plays the obvious priority order to a win, returning every move including pile cycling. */
fun trivialSolution(seed: Long, maxMoves: Int = 2000): List<Move>? = tierSolution(seed, Ruleset.TRIVIAL, maxMoves)

/** As [trivialSolution], for whichever tier's priority order graded the deal. */
fun tierSolution(seed: Long, ruleset: Ruleset, maxMoves: Int = 2000): List<Move>? {
    val search = referenceStrategyLine(seed, ruleset)
    if (search.outcome != StrategySearchOutcome.WON) return null
    return replayableStrategyLine(seed, search.packedLine)?.takeIf { it.size <= maxMoves }
}

/**
 * Regenerates `solutions.bin` with lines for [trivialSeeds], **merged into** the existing
 * asset rather than replacing it: the other five tiers' solutions are still valid and were
 * expensive to produce, so they are decoded, the previous Trivial entries dropped, and the
 * new ones added.
 *
 * Every generated line is replayed through the real reducer before being admitted, so a
 * solution that does not actually win is reported rather than shipped.
 */
fun writeTrivialSolutions(
    trivialSeeds: List<Long>,
    previousTrivialSeeds: List<Long>,
    assetPath: String,
    ruleset: Ruleset = Ruleset.TRIVIAL,
    log: (String) -> Unit = ::println,
) {
    val asset = File(assetPath)
    val existing = if (asset.exists()) SolutionCodec.decodeCatalog(asset.readBytes()) else emptyMap()
    log("existing catalog holds ${existing.size} solutions")

    val merged = LinkedHashMap<Long, List<Move>>(existing.size + trivialSeeds.size)
    merged.putAll(existing)
    for (seed in previousTrivialSeeds) merged.remove(seed)
    log("dropped ${previousTrivialSeeds.count { it in existing }} superseded $ruleset solutions")

    var failed = 0
    var moves = 0L
    for (seed in trivialSeeds) {
        val line = tierSolution(seed, ruleset)
        if (line == null || !replayWins(seed, line)) {
            failed++
            log("seed=$seed produced no verified winning line")
            continue
        }
        merged[seed] = line
        moves += line.size
    }

    val encoded = SolutionCodec.encodeCatalog(merged)
    asset.parentFile?.mkdirs()
    asset.writeBytes(encoded)
    log("wrote ${merged.size} solutions (${moves} $ruleset moves, ${encoded.size / 1024} KiB) to $assetPath")
    if (failed > 0) log("WARNING: $failed of ${trivialSeeds.size} $ruleset seeds have no solution and will fall back to on-device search")
}

/** Replays a line through the canonical reducer — a solution counts only if it actually wins. */
private fun replayWins(seed: Long, line: List<Move>): Boolean {
    var state = dealGame(seed, D1S_SPIKE_VERSIONS)
    for (move in line) {
        state = runCatching { applyMove(state, move) }.getOrElse { return false }
    }
    return state.isWon
}
