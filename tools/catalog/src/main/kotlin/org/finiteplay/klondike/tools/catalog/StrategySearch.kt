package org.finiteplay.klondike.tools.catalog

import org.finiteplay.cards.Suit
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove

internal enum class StrategySearchOutcome { WON, DEAD, INCONCLUSIVE }

internal data class StrategySearchResult(
    val outcome: StrategySearchOutcome,
    val packedLine: List<Int>,
    val statesExplored: Int,
)

private enum class VisitResult { WON, DEAD, CAPPED }

private fun offeredBy(move: Int, ruleset: Ruleset): Boolean = when (FastBoard.tapOf(move)) {
    FastBoard.TAP_SETUP -> ruleset.offersSetupMoves
    FastBoard.TAP_WITHDRAW -> ruleset.offersWithdrawal
    else -> true
}

/** Marks every non-looping move that is best under at least one strategy available at [ruleset]. */
internal fun FastBoard.markCumulativePreferred(
    moves: IntArray,
    count: Int,
    ruleset: Ruleset,
    isLoop: BooleanArray,
    selected: BooleanArray,
): Boolean {
    java.util.Arrays.fill(selected, 0, count, false)
    var any = false
    for (active in Ruleset.entries.take(ruleset.ordinal + 1)) {
        var bestRank = Int.MAX_VALUE
        for (index in 0 until count) {
            if (!isLoop[index] && offeredBy(moves[index], active)) {
                bestRank = minOf(bestRank, tapRank(moves[index], active))
            }
        }
        if (bestRank == Int.MAX_VALUE) continue
        for (index in 0 until count) {
            if (!isLoop[index] && offeredBy(moves[index], active) && tapRank(moves[index], active) == bestRank) {
                selected[index] = true
                any = true
            }
        }
    }
    return any
}

/**
 * Finds a line using moves tied for the best available rank under any strategy learned by
 * [ruleset]. The union is deliberate: tiers are cumulative, so learning Easy's restraint
 * does not erase the Trivial strategy that was already available.
 *
 * The search stays much smaller than the legal game tree: it never widens past the current
 * preference group, canonical fingerprints merge column permutations, proven-dead states are
 * transposed, and [maxStates] makes an unfinished traversal explicitly inconclusive.
 */
internal fun searchStrategyLine(
    seed: Long,
    ruleset: Ruleset,
    maxStates: Int = 100_000,
    maxChoices: Int = FastBoard.MAX_DEPTH - 1,
): StrategySearchResult {
    val board = FastBoard().apply { loadFrom(dealGame(seed, D1S_SPIKE_VERSIONS)) }
    val taps = Array(maxChoices + 1) { IntArray(160) }
    val nextHashes = Array(maxChoices + 1) { LongArray(160) }
    val loops = Array(maxChoices + 1) { BooleanArray(160) }
    val preferred = Array(maxChoices + 1) { BooleanArray(160) }
    val line = IntArray(maxChoices)
    var winningLength = 0
    var statesExplored = 0
    val onPath = HashSet<Long>()
    val provenDead = HashSet<Long>()

    fun visit(depth: Int): VisitResult {
        if (board.isWon()) {
            winningLength = depth
            return VisitResult.WON
        }
        if (depth >= maxChoices || statesExplored >= maxStates) return VisitResult.CAPPED

        val stateHash = board.fingerprint()
        if (stateHash in provenDead) return VisitResult.DEAD
        statesExplored++
        onPath += stateHash

        val moves = taps[depth]
        val hashes = nextHashes[depth]
        val isLoop = loops[depth]
        val selected = preferred[depth]
        val count = board.generateTaps(moves, ruleset)
        for (index in 0 until count) {
            board.make(moves[index])
            val nextHash = board.fingerprint()
            board.unmake()
            hashes[index] = nextHash
            isLoop[index] = nextHash in onPath
        }

        if (!board.markCumulativePreferred(moves, count, ruleset, isLoop, selected)) {
            onPath -= stateHash
            return VisitResult.DEAD
        }

        var capped = false
        var pathDependent = false
        val distinctHashes = LongArray(count)
        var distinctCount = 0
        for (index in 0 until count) {
            if (isLoop[index]) {
                // A loop can make a strategy fall through to a lower rank on this path. That
                // makes any dead verdict ancestry-dependent, so do not transpose it.
                pathDependent = true
                continue
            }
            if (!selected[index]) continue
            val nextHash = hashes[index]
            var duplicate = false
            for (seenIndex in 0 until distinctCount) {
                if (distinctHashes[seenIndex] == nextHash) {
                    duplicate = true
                    break
                }
            }
            if (duplicate) continue
            distinctHashes[distinctCount++] = nextHash

            line[depth] = moves[index]
            board.make(moves[index])
            val child = try {
                visit(depth + 1)
            } finally {
                board.unmake()
            }
            when (child) {
                VisitResult.WON -> {
                    onPath -= stateHash
                    return VisitResult.WON
                }
                VisitResult.CAPPED -> capped = true
                VisitResult.DEAD -> Unit
            }
        }

        onPath -= stateHash
        if (capped) return VisitResult.CAPPED
        // If the preferred group changed because an edge looped onto this particular path,
        // the verdict is ancestry-dependent and cannot safely be reused at another visit.
        if (!pathDependent) provenDead += stateHash
        return VisitResult.DEAD
    }

    val outcome = when (visit(0)) {
        VisitResult.WON -> StrategySearchOutcome.WON
        VisitResult.DEAD -> StrategySearchOutcome.DEAD
        VisitResult.CAPPED -> StrategySearchOutcome.INCONCLUSIVE
    }
    return StrategySearchResult(outcome, line.take(winningLength), statesExplored)
}

/** The legacy single reference line, retained for shipped-catalog compatibility. */
internal fun referenceStrategyLine(
    seed: Long,
    ruleset: Ruleset,
    maxChoices: Int = FastBoard.MAX_DEPTH - 1,
): StrategySearchResult {
    val board = FastBoard().apply { loadFrom(dealGame(seed, D1S_SPIKE_VERSIONS)) }
    val visited = HashSet<Long>()
    val moves = IntArray(160)
    val line = ArrayList<Int>()
    visited += board.fingerprint()

    while (!board.isWon()) {
        if (line.size >= maxChoices) {
            return StrategySearchResult(StrategySearchOutcome.INCONCLUSIVE, emptyList(), visited.size)
        }
        val count = board.generateTaps(moves, ruleset)
        var best = -1
        for (index in 0 until count) {
            board.make(moves[index])
            val loops = board.fingerprint() in visited
            board.unmake()
            if (!loops && (best < 0 || board.tapRank(moves[index], ruleset) < board.tapRank(moves[best], ruleset))) {
                best = index
            }
        }
        if (best < 0) return StrategySearchResult(StrategySearchOutcome.DEAD, emptyList(), visited.size)
        val move = moves[best]
        board.make(move)
        line += move
        visited += board.fingerprint()
    }
    return StrategySearchResult(StrategySearchOutcome.WON, line, visited.size - 1)
}

/** Converts the allocation-free search certificate into reducer moves, including free pile cycling. */
internal fun replayableStrategyLine(seed: Long, packedLine: List<Int>): List<Move>? {
    var state = dealGame(seed, D1S_SPIKE_VERSIONS)
    val board = FastBoard().apply { loadFrom(state) }
    val line = ArrayList<Move>(packedLine.size + 40)

    for (packed in packedLine) {
        val move = when (FastBoard.kindOf(packed)) {
            FastBoard.KIND_TABLEAU_FOUNDATION -> Move.TableauToFoundation(FastBoard.fieldA(packed))
            FastBoard.KIND_TABLEAU_TABLEAU -> Move.TableauToTableau(
                FastBoard.fieldA(packed),
                FastBoard.fieldB(packed),
                FastBoard.fieldC(packed),
            )
            FastBoard.KIND_FOUNDATION_TABLEAU ->
                Move.FoundationToTableau(Suit.entries[FastBoard.fieldA(packed)], FastBoard.fieldC(packed))
            FastBoard.KIND_WASTE_FOUNDATION, FastBoard.KIND_WASTE_TABLEAU -> {
                val targetCard = board.pile[board.pileIndexAt(FastBoard.fieldA(packed))]
                var guard = 2 * (state.stock.size + state.waste.size) + 2
                while (state.waste.firstOrNull()?.id != targetCard && guard-- > 0) {
                    val advance = if (state.stock.isNotEmpty()) Move.Draw else Move.Recycle
                    state = applyMove(state, advance)
                    line += advance
                }
                if (state.waste.firstOrNull()?.id != targetCard) return null
                if (FastBoard.kindOf(packed) == FastBoard.KIND_WASTE_FOUNDATION) {
                    Move.WasteToFoundation
                } else {
                    Move.WasteToTableau(FastBoard.fieldC(packed))
                }
            }
            else -> return null
        }
        state = runCatching { applyMove(state, move) }.getOrElse { return null }
        line += move
        board.make(packed)
    }
    return line.takeIf { state.isWon }
}

/**
 * How often a player who never backs up still wins, when every choice the ruleset calls equally
 * good is made at random.
 *
 * `searchStrategyLine` answers "does *some* order of these moves win", which is the right
 * question for "could this deal have been played at a lower tier". It is the wrong question for
 * how a level feels: a deal where one order in fifty wins and the rest dead-end is punishing,
 * and it was being shipped alongside deals where every order wins, because both answer yes.
 *
 * This plays [paths] complete games instead. At each step the ruleset's tied-best moves are
 * generated and one is taken at random, with no backtracking, until the game is won, no move is
 * offered, or [maxMoves] passes — so the returned share is the chance a player following that
 * tier's own instincts, and choosing arbitrarily whenever it is indifferent, reaches a win.
 *
 * Deterministic in [seed]: the sampling RNG is seeded from it, so a rebuild grades a deal the
 * same way twice.
 */
internal fun strategyPathWinRate(
    seed: Long,
    ruleset: Ruleset,
    paths: Int = FORGIVENESS_PATHS,
    maxMoves: Int = FastBoard.MAX_DEPTH - 1,
): Float {
    val deal = dealGame(seed, D1S_SPIKE_VERSIONS)
    val board = FastBoard()
    val moves = IntArray(160)
    val hashes = LongArray(160)
    val isLoop = BooleanArray(160)
    val selected = BooleanArray(160)
    val choices = IntArray(160)
    val random = java.util.Random(seed)
    var wins = 0

    repeat(paths) {
        board.loadFrom(deal)
        val visited = HashSet<Long>()
        var depth = 0
        while (depth < maxMoves) {
            if (board.isWon()) {
                wins++
                break
            }
            visited += board.fingerprint()
            val count = board.generateTaps(moves, ruleset)
            for (index in 0 until count) {
                board.make(moves[index])
                hashes[index] = board.fingerprint()
                board.unmake()
                // Revisiting a board this path has already seen is the loop a player would
                // notice and stop making; it is not a choice worth counting as one.
                isLoop[index] = hashes[index] in visited
            }
            if (!board.markCumulativePreferred(moves, count, ruleset, isLoop, selected)) break

            var offered = 0
            for (index in 0 until count) if (selected[index]) choices[offered++] = moves[index]
            if (offered == 0) break
            board.make(choices[random.nextInt(offered)])
            depth++
        }
    }
    return wins.toFloat() / paths
}

/**
 * Complete games played per deal when measuring forgiveness. Sixty-four separates "every way
 * through wins" from "most do" finely enough to rank on, and costs about a millisecond a deal —
 * three orders of magnitude below the tree search the level gate runs.
 */
internal const val FORGIVENESS_PATHS = 64
