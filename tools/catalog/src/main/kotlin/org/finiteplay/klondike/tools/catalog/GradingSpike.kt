package org.finiteplay.klondike.tools.catalog

import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.solver.HintEngine
import org.finiteplay.klondike.solver.HintOutcome
import org.finiteplay.klondike.solver.search.SolverLimits

/**
 * Human-difficulty grading spike (research prototype, not wired into D1b): grades each
 * seed by how much search effort [HintEngine]'s own weighted portfolio needs to prove a
 * winning line from the raw, fresh deal — `HintOutcome.Guidance.nodes`, a byproduct of
 * a search this project already runs and already validates against every catalog deal
 * (`HintSearchBudgetTest`). D1s's own *exact* A* certification search was tried first
 * as the effort signal and rejected: even one node-count-cheap-looking seed needed
 * millions of nodes to find the *provably shortest* line, and others blew past a
 * 120 s/20M-node allowance entirely — exactly the cost the portfolio's weighted search
 * exists to avoid (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"). A naive-random-playout
 * win-rate grader (how often a no-lookahead policy stumbles into a win) was tried
 * before that and is conceptually the strongest proxy for how a deal actually *feels*,
 * but even after fixing two real cycling bugs in the playout policy, 96 of these 100
 * certified seeds still showed a flat 0% win rate across 200 playouts each — not
 * enough differentiation to be usable without materially more heuristic tuning.
 * Portfolio node count is a weaker human-difficulty proxy than either of those would
 * have been (it is partly a search-engineering artifact, not pure human difficulty),
 * but it is fast, already validated to succeed on every one of these seeds, and ready
 * now; revisit the playout approach later if this proves too coarse in practice.
 *
 * Deliberately never invoked from `:app` or any release code path — grading, like
 * certification, only ever happens offline.
 */

/**
 * The same 100 seeds as `:app`'s `INTERIM_SOLVABLE_SEEDS` (pasted, not shared — `:app`
 * depends on `:tools:catalog` never being on its classpath, so the dependency can't run
 * the other way either). Dealing only ever depends on the seed itself
 * (`dealGame`/`shuffleDeckIndices`), never on `GameVersions`, so grading these under
 * [D1S_SPIKE_VERSIONS] reproduces the identical boards the app deals under
 * `GameViewModel.PLACEHOLDER_VERSIONS`.
 */
val INTERIM_SEEDS_FOR_GRADING: List<Long> = listOf(
    2L, 3L, 5L, 7L, 8L, 11L, 12L, 14L, 15L, 16L,
    18L, 20L, 22L, 24L, 29L, 34L, 37L, 40L, 44L, 46L,
    47L, 48L, 49L, 51L, 52L, 53L, 54L, 55L, 58L, 59L,
    60L, 63L, 64L, 68L, 70L, 72L, 73L, 74L, 75L, 78L,
    79L, 81L, 83L, 84L, 86L, 87L, 89L, 91L, 95L, 96L,
    98L, 99L, 100L, 101L, 102L, 103L, 105L, 109L, 111L, 112L,
    113L, 114L, 116L, 117L, 118L, 120L, 121L, 122L, 125L, 130L,
    131L, 132L, 138L, 141L, 142L, 146L, 147L, 148L, 149L, 151L,
    154L, 155L, 158L, 160L, 162L, 164L, 169L, 170L, 171L, 172L,
    173L, 177L, 179L, 181L, 186L, 188L, 190L, 191L, 192L, 193L,
)

/**
 * Matches `GameViewModel.HINT_SOLVER_LIMITS` (`:app`, unreachable from here — same
 * reason [INTERIM_SEEDS_FOR_GRADING] is pasted rather than shared) and
 * `HintSearchBudgetTest.INTERACTIVE_LIMITS` (`:solver`): the interactive on-device hint
 * budget, which every catalog deal is already proven to resolve to [HintOutcome.Guidance]
 * within, from its raw fresh board.
 */
val INTERACTIVE_GRADING_LIMITS = SolverLimits(maxNodes = 340_000L, maxDurationMs = 8_000L)

/** Human-facing difficulty tier, assigned by node-count percentile band within the graded batch. */
enum class DifficultyTier { EASY, MEDIUM, HARD, EXPERT }

data class SeedGrade(val seed: Long, val nodes: Long, val tier: DifficultyTier)

/**
 * Grades every seed in [seeds] by [HintOutcome.Guidance.nodes] — total portfolio search
 * effort to prove a winning line from the raw deal — then buckets into four tiers by
 * node-count quartile *within this batch* (percentile-relative, not an absolute
 * threshold, matching the project's existing nearest-rank percentile convention:
 * `D1sSpike.percentile`) so the banding stays meaningful as the graded catalog grows,
 * rather than drifting against a fixed cutoff tuned for today's batch size. Every seed
 * here is already `INTERIM_SOLVABLE_SEEDS`-certified and `HintSearchBudgetTest`-proven
 * to resolve within [limits], so anything other than [HintOutcome.Guidance] is a
 * genuine inconsistency worth failing loudly on, not silently skipping.
 */
fun runGradingSpike(seeds: List<Long>, limits: SolverLimits = INTERACTIVE_GRADING_LIMITS, log: (String) -> Unit = ::println): List<SeedGrade> {
    log("Grading ${seeds.size} seeds by hint-portfolio search effort...")
    val nodesBySeed = seeds.map { seed ->
        // A fresh engine per seed: HintEngine's dead-state/certificate caches are
        // meant to carry work forward across hint requests *within one game*, and
        // must not leak between unrelated seeds here.
        val outcome = HintEngine().hint(dealGame(seed, D1S_SPIKE_VERSIONS), limits)
        val nodes = when (outcome) {
            is HintOutcome.Guidance -> outcome.nodes
            else -> error("seed=$seed did not resolve to Guidance within the interactive budget: $outcome")
        }
        log("seed=$seed nodes=$nodes elapsedMs=${outcome.elapsedMs}")
        seed to nodes
    }

    // Ranked by position in a stably-sorted copy, not indexOf(nodes): ties are common
    // (several seeds can resolve at an identical node count, notably 0 for a fast-path
    // trivial board) and indexOf would resolve them all to the *first* matching index,
    // misranking the rest.
    val byNodesAscending = nodesBySeed.withIndex().sortedBy { it.value.second }
    val rankByOriginalIndex = HashMap<Int, Double>(nodesBySeed.size)
    byNodesAscending.forEachIndexed { rank, (originalIndex, _) ->
        rankByOriginalIndex[originalIndex] = rank.toDouble() / (nodesBySeed.size - 1).coerceAtLeast(1)
    }
    fun tierFor(rank: Double): DifficultyTier = when {
        rank < 0.25 -> DifficultyTier.EASY // fewest nodes to prove = easiest
        rank < 0.50 -> DifficultyTier.MEDIUM
        rank < 0.75 -> DifficultyTier.HARD
        else -> DifficultyTier.EXPERT
    }

    val grades = nodesBySeed.mapIndexed { index, (seed, nodes) -> SeedGrade(seed, nodes, tierFor(rankByOriginalIndex.getValue(index))) }

    val sortedNodes = nodesBySeed.map { it.second }.sorted()
    log("")
    log("=== Grading spike summary ===")
    log("mean nodes: %.0f".format(nodesBySeed.map { it.second }.average()))
    log("min nodes: ${sortedNodes.first()}, max nodes: ${sortedNodes.last()}")
    DifficultyTier.entries.forEach { tier ->
        val inTier = grades.filter { it.tier == tier }
        log("$tier: ${inTier.size} seeds, node counts ${inTier.map { it.nodes }}")
    }

    log("")
    log("=== Kotlin export (paste into :app's checked-in grade table) ===")
    for (grade in grades) log("    ${grade.seed}L to DifficultyTier.${grade.tier},")

    return grades
}
