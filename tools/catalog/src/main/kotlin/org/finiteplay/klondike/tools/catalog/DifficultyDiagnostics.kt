package org.finiteplay.klondike.tools.catalog

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.legalMoves
import org.finiteplay.klondike.rules.validRunStartIndices
import org.finiteplay.klondike.solver.StrategyTier
import org.finiteplay.klondike.solver.candidateMoves
import org.finiteplay.klondike.solver.classifyDeal
import org.finiteplay.klondike.solver.findWinningLine
import org.finiteplay.klondike.solver.search.SearchOrdering
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.Solver
import org.finiteplay.klondike.solver.search.SolverLimits

/**
 * Offline diagnostics for the question `docs/games/klondike/DIFFICULTY_LEVELS.md` currently
 * cannot answer: **why don't the top tiers feel as far apart as they rank?**
 *
 * The shipped grade is "the lowest tier whose rules win it", which measures the
 * *ruleset*, not the board. Two deals can be graded a tier apart because one happened to
 * fall to Hard's tie-breaks and the other needed Expert's, while being equally forgiving
 * to a human. This measures the board directly instead, along a known winning line:
 *
 * - **withdrawals** — how many foundation-to-tableau moves the graded line actually plays,
 *   and whether the deal can be won at all without them ([SeedDiagnostics.needsWithdrawal]).
 *   `DIFFICULTY_LEVELS.md` "Corrections" dropped this as a *grading criterion* because it
 *   could not be obtained cheaply during a full-catalog scan; over thirty seeds it is
 *   affordable, and it is the one thing Expert is supposed to add over Hard.
 * - **indistinguishable forks** — positions where the tier's own top preference group holds
 *   more than one move (so its rules have no basis to prefer either) and the choice
 *   nonetheless decides the game. Note this is *not* the Glossary's **critical choice**,
 *   which is ruleset-independent and needs no fork at all: a critical choice can sit among
 *   nine winning alternatives, where nothing here would ever see it.
 * - **branch survival** — of every legal move at a sampled position, how many keep the
 *   game winnable and how many are proven dead. The dead share is the "trap density" a
 *   player actually feels: a board where most moves still win is forgiving whatever tier
 *   it is graded, and one where most moves lose is punishing whatever tier it is graded.
 *
 * Desktop-only, like everything else in `tools/catalog` — never on `:app`'s classpath.
 */

/** Whether a probed branch keeps the game winnable. [UNKNOWN] is a spent budget, never evidence of either. */
enum class BranchVerdict { WINNABLE, DEAD, UNKNOWN }

/**
 * Per-probe budget. Small on purpose: these fire hundreds of times per seed, and a probe
 * that cannot resolve quickly is recorded [BranchVerdict.UNKNOWN] rather than paid for —
 * an honest gap costs less than a slow answer, and the reported ratios exclude it.
 */
val PROBE_LIMITS = SolverLimits(maxNodes = 40_000L, maxDurationMs = 2_000L)

/** Budget for the one per-seed "is this winnable without ever withdrawing" solve of the raw deal. */
val WITHDRAWAL_CHECK_LIMITS = SolverLimits(maxNodes = 400_000L, maxDurationMs = 20_000L)

private val PROBE_ORDERING = SearchOrdering(lowerBoundWeight = 25, downCardWeight = 150, aceBurialWeight = 25)

/** Positions sampled per seed, spread evenly along the winning line, so a 400-move Insane line costs the same as a 90-move Hard one. */
const val DEFAULT_PROBE_POSITIONS = 60

data class SeedDiagnostics(
    val seed: Long,
    val difficulty: AppDifficulty,
    val lineMoves: Int,
    val withdrawalsInLine: Int,
    /** Null when the check itself ran out of budget — not the same as "no". */
    val needsWithdrawal: Boolean?,
    val positionsProbed: Int,
    val indistinguishableForks: Int,
    /** Positions where exactly one legal move survives and at least one other is proven dead. */
    val forcedPositions: Int,
    val branchesWinnable: Int,
    val branchesDead: Int,
    val branchesUnknown: Int,
) {
    /** Share of *resolved* branches that lose. Unknowns are excluded rather than guessed. */
    val trapRatio: Double
        get() = if (branchesWinnable + branchesDead == 0) 0.0 else branchesDead.toDouble() / (branchesWinnable + branchesDead)
}

/**
 * The first [count] seeds of each of [difficulties], as the app itself orders them —
 * pasted from `:app`'s `InterimSeeds*.kt` for the same reason [INTERIM_SEEDS_FOR_GRADING]
 * is pasted: `:app` must never be on this module's classpath, nor this module on `:app`'s.
 */
val FIRST_SEEDS_BY_DIFFICULTY: Map<AppDifficulty, List<Long>> = mapOf(
    AppDifficulty.TRIVIAL to listOf(2L, 16L, 20L, 22L, 46L, 69L, 79L, 81L, 92L, 96L),
    AppDifficulty.EASY to listOf(7L, 8L, 15L, 17L, 18L, 76L, 94L, 112L, 131L, 159L),
    AppDifficulty.MEDIUM to listOf(38L, 74L, 75L, 83L, 212L, 302L, 328L, 369L, 514L, 566L),
    AppDifficulty.HARD to listOf(3L, 5L, 14L, 27L, 28L, 30L, 34L, 37L, 44L, 47L),
    AppDifficulty.EXPERT to listOf(9L, 36L, 48L, 52L, 72L, 87L, 88L, 93L, 105L, 107L),
    AppDifficulty.INSANE to listOf(1L, 4L, 6L, 10L, 11L, 12L, 13L, 19L, 24L, 26L),
)

/**
 * Runs the diagnostics over [seedsByDifficulty] and prints a per-seed table plus a
 * per-difficulty summary. One seed per worker thread; each worker's own probes stay
 * single-threaded, so total parallelism is bounded by [parallelism].
 */
fun reportDifficultyDiagnostics(
    seedsByDifficulty: Map<AppDifficulty, List<Long>> = FIRST_SEEDS_BY_DIFFICULTY,
    probePositions: Int = DEFAULT_PROBE_POSITIONS,
    parallelism: Int = Runtime.getRuntime().availableProcessors(),
    log: (String) -> Unit = ::println,
) {
    val total = seedsByDifficulty.values.sumOf { it.size }
    log("diagnosing $total seeds ($probePositions sampled positions each) across $parallelism threads...")
    log("probes search the withdrawal-free move set, so DEAD means \"lost without withdrawing\" — see diagnoseBranch")

    val executor = Executors.newFixedThreadPool(parallelism)
    val results: Map<AppDifficulty, List<SeedDiagnostics>> = try {
        seedsByDifficulty.mapValues { (difficulty, seeds) ->
            seeds.map { seed -> executor.submit<SeedDiagnostics> { diagnoseSeed(seed, difficulty, probePositions) } }
        }.mapValues { (_, futures) -> futures.map { it.get() } }
    } finally {
        executor.shutdown()
        executor.awaitTermination(30, TimeUnit.MINUTES)
    }

    for ((difficulty, rows) in results) {
        log("")
        log("=== $difficulty ===")
        log("seed | moves | withdrawals | needs wd | forks | forced | winnable | dead | unknown | trap%")
        for (r in rows) {
            log(
                "${r.seed} | ${r.lineMoves} | ${r.withdrawalsInLine} | ${r.needsWithdrawal?.toString() ?: "?"} | " +
                    "${r.indistinguishableForks} | ${r.forcedPositions} | ${r.branchesWinnable} | ${r.branchesDead} | " +
                    "${r.branchesUnknown} | %.1f".format(r.trapRatio * 100),
            )
        }
    }

    log("")
    log("=== Summary (median per difficulty) ===")
    log("difficulty | moves | withdrawals | needing wd | indistinguishable forks | forced | trap%")
    for ((difficulty, rows) in results) {
        if (rows.isEmpty()) continue
        fun median(values: List<Int>) = values.sorted()[values.size / 2]
        val needing = rows.count { it.needsWithdrawal == true }
        val known = rows.count { it.needsWithdrawal != null }
        log(
            "$difficulty | ${median(rows.map { it.lineMoves })} | ${median(rows.map { it.withdrawalsInLine })} | " +
                "$needing/$known | ${median(rows.map { it.indistinguishableForks })} | ${median(rows.map { it.forcedPositions })} | " +
                "%.1f".format(rows.map { it.trapRatio }.sorted()[rows.size / 2] * 100),
        )
    }
}

/**
 * The winning line a seed is diagnosed along is the one its grade came from: the graded
 * tier's own ruleset play-out for Hard/Expert, and a searched line for Insane, which by
 * definition no ruleset wins. Using each seed's *own* grading line is what makes the
 * numbers comparable to the grade being questioned.
 */
private fun diagnoseSeed(seed: Long, difficulty: AppDifficulty, probePositions: Int): SeedDiagnostics {
    val deal = dealGame(seed, D1S_SPIKE_VERSIONS)
    val tier = if (difficulty == AppDifficulty.INSANE) StrategyTier.EXPERT else StrategyTier.valueOf(difficulty.name)
    val line = classifyDeal(deal, maxMoves = MAX_WINNING_LINE_MOVES)?.line?.takeIf { it.isNotEmpty() }
        ?: findWinningLine(deal)
        ?: return SeedDiagnostics(seed, difficulty, 0, 0, null, 0, 0, 0, 0, 0, 0)

    // A win found without ever withdrawing proves withdrawal is optional on this board.
    // A timeout proves nothing either way, hence the nullable.
    val withoutWithdrawal = Solver.solve(deal, WITHDRAWAL_CHECK_LIMITS, includeFoundationWithdrawal = false, ordering = PROBE_ORDERING)
    val needsWithdrawal = when (withoutWithdrawal) {
        is SolveOutcome.Solved -> false
        is SolveOutcome.Unsolved -> true
        else -> null
    }

    val states = ArrayList<GameState>(line.size)
    var state = deal
    for (move in line) {
        states += state
        state = applyMove(state, move)
    }

    var indistinguishableForks = 0
    var forcedPositions = 0
    var winnable = 0
    var dead = 0
    var unknown = 0
    var probed = 0

    for (index in sampledIndices(states.size, probePositions)) {
        val at = states[index]
        val legal = legalMoves(at)
        if (legal.size < 2) continue
        probed++
        val verdicts = legal.associateWith { diagnoseBranch(at, it) }
        winnable += verdicts.count { it.value == BranchVerdict.WINNABLE }
        dead += verdicts.count { it.value == BranchVerdict.DEAD }
        unknown += verdicts.count { it.value == BranchVerdict.UNKNOWN }

        if (verdicts.count { it.value == BranchVerdict.WINNABLE } == 1 && verdicts.any { it.value == BranchVerdict.DEAD }) {
            forcedPositions++
        }
        // The doc's definition, literally: the tier's rules rank these identically, yet
        // the board does not — so something outside the ruleset has to break the tie.
        val candidates = candidateMoves(at, tier)
        if (candidates.size >= 2) {
            val candidateVerdicts = candidates.map { verdicts[it] }
            if (candidateVerdicts.contains(BranchVerdict.WINNABLE) && candidateVerdicts.contains(BranchVerdict.DEAD)) {
                indistinguishableForks++
            }
        }
    }

    return SeedDiagnostics(
        seed = seed,
        difficulty = difficulty,
        lineMoves = line.size,
        withdrawalsInLine = line.count { it is Move.FoundationToTableau },
        needsWithdrawal = needsWithdrawal,
        positionsProbed = probed,
        indistinguishableForks = indistinguishableForks,
        forcedPositions = forcedPositions,
        branchesWinnable = winnable,
        branchesDead = dead,
        branchesUnknown = unknown,
    )
}

/**
 * Probes one branch with the **withdrawal-free** move set, the same restriction (and the
 * same trade) `TierClassifier.winningLine` documents: with withdrawal enabled these
 * searches time out on essentially every mid-game board, which would make every verdict
 * [BranchVerdict.UNKNOWN] and the whole report empty. So [BranchVerdict.DEAD] means
 * "cannot be won from here without withdrawing a foundation card", which is a real
 * dead end for every tier below Expert and a conservative one for Expert.
 */
private fun diagnoseBranch(state: GameState, move: Move): BranchVerdict =
    when (Solver.solve(applyMove(state, move), PROBE_LIMITS, includeFoundationWithdrawal = false, ordering = PROBE_ORDERING)) {
        is SolveOutcome.Solved -> BranchVerdict.WINNABLE
        is SolveOutcome.Unsolved -> BranchVerdict.DEAD
        else -> BranchVerdict.UNKNOWN
    }

/**
 * The **obvious** moves at [state]: what a player following Trivial's description would
 * reach for — a foundation play whenever one is legal, any move that flips a face-down
 * card, and playing the waste top onto the tableau. Draw and recycle are excluded (not
 * choices), as is every non-revealing tableau rearrangement, which no tier below Hard
 * offers at all.
 *
 * Deliberately the *union* of Trivial's preference groups rather than its top group: the
 * point is what a player might do while deviating — missing a foundation play and taking a
 * reveal instead, say — not what the ruleset would do if applied perfectly.
 */
private fun obviousChoices(state: GameState): List<Move> = legalMoves(state).filter { move ->
    when (move) {
        is Move.TableauToFoundation, Move.WasteToFoundation, is Move.WasteToTableau -> true
        is Move.TableauToTableau -> {
            val deepestStart = validRunStartIndices(state.tableau[move.fromColumn]).lastOrNull()
            move.fromIndex == deepestStart && move.fromIndex > 0
        }
        else -> false
    }
}

/**
 * Walks [tier]'s own winning line for [seed] and reports every **obvious** move that is a
 * critical choice — the test `DIFFICULTY_LEVELS.md` needs for "a deal must be winnable
 * even when the player deviates". A hit means an ordinary, unremarkable move loses the
 * game outright.
 *
 * Restricted to positions on the winning line, so it is a lower bound: the full
 * requirement quantifies over every position reachable by obvious play, which is a subtree
 * rather than a line and costs far more to enumerate.
 */
fun reportObviousTraps(seeds: List<Long>, tier: StrategyTier, log: (String) -> Unit = ::println) {
    log("obvious-move critical choices along ${tier}'s own line, ${seeds.size} seeds")
    log("(probes are withdrawal-free — correct for a tier that never withdraws)")
    for (seed in seeds) {
        val deal = dealGame(seed, D1S_SPIKE_VERSIONS)
        val line = classifyDeal(deal, maxMoves = MAX_WINNING_LINE_MOVES)?.line
        if (line.isNullOrEmpty()) { log("seed=$seed: no ruleset line"); continue }
        var state = deal
        var found = 0
        line.forEachIndexed { index, played ->
            for (move in obviousChoices(state)) {
                if (diagnoseBranch(state, move) == BranchVerdict.DEAD) {
                    found++
                    log("  seed=$seed move#${index + 1}: OBVIOUS move $move is a critical choice (line played $played)")
                }
            }
            state = applyMove(state, move = played)
        }
        log("seed=$seed: $found obvious critical choice(s) on a ${line.size}-move line")
    }
}

/** [count] indices spread evenly over `0 until size` (all of them when the line is shorter than [count]). */
private fun sampledIndices(size: Int, count: Int): List<Int> {
    if (size <= count) return (0 until size).toList()
    return (0 until count).map { it * (size - 1) / (count - 1).coerceAtLeast(1) }.distinct()
}
