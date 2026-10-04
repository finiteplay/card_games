package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.cards.Card
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.canPlaceOnTableau
import org.finiteplay.klondike.rules.isSafeFoundationMove
import org.finiteplay.klondike.rules.legalMoves
import org.finiteplay.klondike.rules.validRunStartIndices
import org.finiteplay.klondike.solver.search.LongHashSet
import org.finiteplay.klondike.solver.search.SearchOrdering
import org.finiteplay.klondike.solver.search.SolveOutcome
import org.finiteplay.klondike.solver.search.Solver
import org.finiteplay.klondike.solver.search.SolverLimits
import org.finiteplay.klondike.solver.search.hashOf
import org.finiteplay.klondike.solver.search.snodeFrom

/**
 * Difficulty tier by required strategy, per `docs/games/klondike/DIFFICULTY_LEVELS.md` — cumulative,
 * mechanical move-selection rulesets, in contrast to `:app`'s existing node-count-based
 * `DifficultyTier`. Lives in `:solver` (not `tools/catalog`) so it stays reusable if
 * `:app` ever wants to classify a hand's difficulty on-device — `:solver` is already on
 * `:app`'s classpath, `tools/catalog` never can be.
 */
enum class StrategyTier { TRIVIAL, EASY, MEDIUM, HARD, EXPERT }

/**
 * Budget for the per-step "does this move keep the game winnable" check.
 *
 * Not as small as it looks like it could be. These checks mostly run deep in a proven
 * line, where a win is a short search away — but the ones that actually matter fire
 * exactly when the tier's rules *diverge* from the known line, and an early divergence
 * means solving a board barely a move old, which costs nearly as much as the fresh deal
 * itself. Sizing this for the mid-game case made every early divergence read as
 * "unwinnable" within milliseconds, which silently made the stricter tiers unable to
 * classify anything.
 */
val DEFAULT_ORACLE_LIMITS = SolverLimits(maxNodes = 200_000L, maxDurationMs = 8_000L)

/**
 * Budget for the one *initial* solve of a raw deal. Far larger than the per-step
 * budget, because solving a fresh 52-card board is the single most expensive search
 * this ever does — the same reason `GameViewModel.hintSolverLimits` sizes the
 * on-device hint budget around fresh-deal worst cases rather than typical mid-game ones.
 */
val DEFAULT_INITIAL_LIMITS = SolverLimits(maxNodes = 400_000L, maxDurationMs = 20_000L)

private val ORACLE_ORDERING = SearchOrdering(lowerBoundWeight = 25, downCardWeight = 150, aceBurialWeight = 25)

/**
 * Search configurations tried in order for the one expensive fresh-deal solve, with the
 * share of [DEFAULT_INITIAL_LIMITS] each may spend. Mirrors [HintEngine]'s own portfolio
 * and exists for the same documented reason: no single configuration resolves every real
 * deal within a bounded budget, but the configurations fail on *different* boards, so a
 * sequence of cheaper attempts covers what one expensive attempt cannot. A single
 * configuration here left roughly half of a known-good catalog sample unsolvable and so
 * unclassifiable.
 */
private val OPENING_PORTFOLIO: List<Pair<SearchOrdering, Double>> = listOf(
    SearchOrdering(lowerBoundWeight = 10) to 0.15,
    SearchOrdering(lowerBoundWeight = 25, downCardWeight = 150, aceBurialWeight = 25) to 0.25,
    SearchOrdering(lowerBoundWeight = 25, downCardWeight = 50, aceBurialWeight = 100, neededCardDepthWeight = 50) to 0.2,
    SearchOrdering(lowerBoundWeight = 25, neededCardDepthWeight = 50) to 0.2,
    SearchOrdering(lowerBoundWeight = 25) to 1.0,
)

private const val EASY_RANK_GAP = 4
private const val MODE_SWITCH_PILE_THRESHOLD = 5

/** [line] is the winning move sequence the grading tier's rules played, shipped with the seed so hints can follow it. */
data class TierClassification(val tier: StrategyTier, val withdrawals: Int, val moveCount: Int, val line: List<Move> = emptyList())

/**
 * Classifies [start] as the **lowest tier whose rules win it outright**, unaided — the
 * definition `docs/games/klondike/DIFFICULTY_LEVELS.md` gives verbatim ("the lowest tier whose strategy
 * set can win it from the raw dealt board") — or `null` when no tier's rules do, which
 * means the deal is either genuinely unsolvable or beyond Expert (Insane; out of scope).
 *
 * Needs no search whatsoever, and takes microseconds: a tier winning *is itself* the
 * proof the deal is solvable, so nothing has to be certified separately.
 *
 * Two earlier mechanisms were tried and measured before this one, and both failed for
 * instructive reasons worth not repeating:
 *
 * - An **oracle-guided walk**, letting the rules pick any move a search proved kept the
 *   game winnable. It could never lose, but never converged either: every candidate
 *   stays winnable, so it wandered through hundreds of distinct aimless boards and blew
 *   a 500-move cap on deals with a proven 125-move win.
 * - **Certificate replay**, counting how often a tier's rules disagreed with one proven
 *   winning line. Convergent and fast, but it measured the wrong thing — the tiers came
 *   out *inverted* (Trivial 43 disagreements at the median, Expert 72), because the
 *   higher tiers add foundation restraint while a solver certificate is a short greedy
 *   line, so "agreement" really measured greediness, not difficulty.
 */
fun classifyDeal(start: GameState, maxMoves: Int = 500): TierClassification? {
    for (tier in StrategyTier.entries) {
        val run = playPureRuleset(start, tier, maxMoves)
        if (run.outcome == PureOutcome.WON) return TierClassification(tier, run.withdrawals, run.moves, run.line)
    }
    return null
}

/** How a pure, unaided run of a tier's rules ended. */
enum class PureOutcome { WON, STUCK, EXCEEDED_MOVE_CAP }

/**
 * [line] is every move played, in order — a complete winning line when [outcome] is
 * [PureOutcome.WON], and the abandoned prefix otherwise. Shipped alongside each catalogued
 * seed so a hint can follow a known solution instead of re-deriving one (`docs/games/klondike/DEALS.md`).
 */
data class PureRunResult(val outcome: PureOutcome, val moves: Int, val withdrawals: Int = 0, val line: List<Move> = emptyList()) {
    val firstMove: Move? get() = line.firstOrNull()
}

/**
 * Plays [tier]'s rules from [start] with **no** solver help of any kind: at each board
 * take the rules' own top-preference move, and stop at a win, a dead end, or [maxMoves].
 *
 * This is the literal question `docs/games/klondike/DIFFICULTY_LEVELS.md` defines a tier by — "the
 * lowest tier whose strategy set can win it from the raw dealt board" — and it needs no
 * search at all, so it costs microseconds per deal. Cycle prevention is kept (a move
 * back onto an already-visited board is skipped in favour of the next candidate), since
 * without it the rules trivially loop forever and the answer would be an artefact of
 * that rather than of the rules themselves.
 */
fun playPureRuleset(start: GameState, tier: StrategyTier, maxMoves: Int = 500): PureRunResult {
    var state = start
    var withdrawals = 0
    val line = ArrayList<Move>()
    val visited = HashSet<Long>()
    visited += hashOf(snodeFrom(state))

    while (!state.isWon) {
        if (line.size >= maxMoves) return PureRunResult(PureOutcome.EXCEEDED_MOVE_CAP, line.size, withdrawals, line)
        val move = preferenceGroups(state, tier)
            .flatten()
            .firstOrNull { hashOf(snodeFrom(applyMove(state, it))) !in visited }
            ?: return PureRunResult(PureOutcome.STUCK, line.size, withdrawals, line)
        if (move is Move.FoundationToTableau) withdrawals++
        line += move
        state = applyMove(state, move)
        visited += hashOf(snodeFrom(state))
    }
    return PureRunResult(PureOutcome.WON, line.size, withdrawals, line)
}

/**
 * A search-proven winning line from [state], for boards no strategy ruleset wins — the
 * Insane tier. Public because those seeds still need both a solvability proof and a
 * shipped solution, and the search is the only thing that can supply either.
 */
fun findWinningLine(state: GameState, limits: SolverLimits = DEFAULT_INITIAL_LIMITS): List<Move>? =
    openingLine(state, StrategyTier.HARD, limits) ?: openingLine(state, StrategyTier.EXPERT, limits)

/**
 * A proven winning line from the raw deal [state], trying [OPENING_PORTFOLIO]'s
 * configurations in order until one succeeds or the whole budget is spent.
 */
private fun openingLine(state: GameState, tier: StrategyTier, limits: SolverLimits): List<Move>? {
    val fullMoveSet = tier == StrategyTier.EXPERT
    val startNanos = System.nanoTime()
    var nodesUsed = 0L
    for ((ordering, share) in OPENING_PORTFOLIO) {
        val nodesLeft = limits.maxNodes - nodesUsed
        val msLeft = limits.maxDurationMs - (System.nanoTime() - startNanos) / 1_000_000L
        if (nodesLeft <= 0 || msLeft <= 0) break
        val outcome = Solver.solve(
            state,
            SolverLimits(maxNodes = minOf(nodesLeft, (limits.maxNodes * share).toLong()), maxDurationMs = msLeft),
            includeFoundationWithdrawal = fullMoveSet,
            ordering = ordering,
        )
        nodesUsed += outcome.nodes
        when (outcome) {
            is SolveOutcome.Solved -> return outcome.certificate
            // A full-move-set exhaustion is a real proof this board cannot be won;
            // no later configuration will change that, so stop paying for them.
            is SolveOutcome.Unsolved -> if (fullMoveSet) return null
            else -> Unit
        }
    }
    return null
}

/**
 * A proven winning line from [state], or null if none was found within [limits].
 *
 * Always searches the withdrawal-free move set, at every tier. A line found this way is
 * a genuine win under any tier's rules (each tier's move set includes these moves), so a
 * hit is always sound; a miss is merely conservative — a position winnable *only* by
 * withdrawing reads as unwinnable and costs the walk a deviation. That trade is
 * deliberate and measured: with withdrawal enabled these per-step checks time out on
 * essentially every mid-game board, which left the Expert tier unable to classify
 * anything at all, while the restricted search resolves them quickly. Expert still
 * *plays* withdrawals — [preferenceGroups] offers them and the walk takes them; only
 * this lookahead is restricted.
 */
private fun winningLine(state: GameState, tier: StrategyTier, deadCache: LongHashSet, limits: SolverLimits): List<Move>? {
    val outcome = Solver.solveWithCache(
        state,
        limits,
        deadCache,
        includeFoundationWithdrawal = false,
        ordering = ORACLE_ORDERING,
        // Exhausting a restricted move set proves nothing about the full game, so these
        // searches must never write dead states back for a later one to trust.
        recordDeadOnExhaustion = false,
    )
    return (outcome as? SolveOutcome.Solved)?.certificate
}

/**
 * [state]'s legal moves grouped by [tier]'s preference, most-preferred first, per
 * `docs/games/klondike/DIFFICULTY_LEVELS.md`. Empty groups are dropped, so the first entry is always
 * the tier's actual top choice.
 *
 * Two of the doc's named rules are deliberately no-ops here, rather than silently
 * dropped: "respect suit-color alternation" is already guaranteed by the rules
 * themselves (`canBuild` only ever permits an alternating-color placement, so there is
 * never a non-alternating candidate to deprioritize), and Medium's "informed King
 * selection" is subsumed by the reveal-count preference below (a King leaving a column
 * with buried cards *is* a reveal move, ranked by how many it uncovers). Medium's
 * stock/waste tracking is about sequencing information, not about which move wins a
 * tie, so it does not appear as a filter at all.
 */
internal fun preferenceGroups(state: GameState, tier: StrategyTier): List<List<Move>> {
    val legal = legalMoves(state)
    val foundationEligible = legal.filter { it is Move.TableauToFoundation || it is Move.WasteToFoundation }
    val allowedFoundation = restrainedFoundationCandidates(state, foundationEligible, tier)
    val withheldFoundation = foundationEligible.filterNot { it in allowedFoundation }

    val tableauMoves = legal.filterIsInstance<Move.TableauToTableau>()
    val reveals = tableauMoves.filter { isRevealMove(state, it) }
    val (preferredReveals, otherReveals) = partitionReveals(state, reveals, tier)
    val setupMoves = tableauMoves.filterNot { it in reveals }.filter { isProductiveSetup(state, it) }

    val wasteMoves = legal.filterIsInstance<Move.WasteToTableau>()
    val (preferredWaste, otherWaste) = partitionByBraiding(state, wasteMoves, tier)

    val stockMoves = when {
        state.stock.isNotEmpty() -> listOf(Move.Draw)
        state.waste.isNotEmpty() -> listOf(Move.Recycle)
        else -> emptyList()
    }

    return buildList {
        add(allowedFoundation)
        add(preferredReveals)
        add(otherReveals)
        add(preferredWaste)
        add(otherWaste)
        // Setup-only rearrangement is what Hard adds over Medium: a move with no
        // immediate payoff, made for a reveal or foundation play several moves later.
        if (tier >= StrategyTier.HARD) add(setupMoves)
        add(stockMoves)
        // No-stall override (`docs/games/klondike/DIFFICULTY_LEVELS.md`, Easy): a withheld foundation
        // card goes up rather than leave the board stuck - but only once drawing and
        // every tableau option above is exhausted, or restraint would mean nothing.
        add(withheldFoundation)
        if (tier == StrategyTier.EXPERT) add(legal.filterIsInstance<Move.FoundationToTableau>())
    }.filter { it.isNotEmpty() }
}

/**
 * The tier's top preference group — what its rules would actually reach for first.
 *
 * Public so offline diagnostics can ask the question `docs/games/klondike/DIFFICULTY_LEVELS.md`
 * defines an **indistinguishable fork** by: more than one move here means the tier's rules
 * score them identically and have no basis to prefer either. That is a fact about the
 * tier, not about the board — a **critical choice** in the same doc's Glossary sense is
 * ruleset-independent and invisible from here.
 */
fun candidateMoves(state: GameState, tier: StrategyTier): List<Move> =
    preferenceGroups(state, tier).firstOrNull().orEmpty()

private fun restrainedFoundationCandidates(state: GameState, eligible: List<Move>, tier: StrategyTier): List<Move> = when (tier) {
    StrategyTier.TRIVIAL -> eligible
    StrategyTier.EASY -> eligible.filter { !isSoleLandingSpotNeeded(state, it) && !exceedsRankGap(state, cardMovedBy(state, it)) }
    StrategyTier.MEDIUM, StrategyTier.HARD -> eligible.filter { isSafeFoundationMove(state.foundations, cardMovedBy(state, it)) }
    StrategyTier.EXPERT ->
        // Late-game mode-switching: once almost nothing is left unseen, restraint stops
        // earning its keep and everything liftable goes up.
        if (state.stock.size + state.waste.size <= MODE_SWITCH_PILE_THRESHOLD) eligible
        else eligible.filter { isSafeFoundationMove(state.foundations, cardMovedBy(state, it)) }
}

private fun cardMovedBy(state: GameState, move: Move): Card = when (move) {
    is Move.TableauToFoundation -> state.tableau[move.fromColumn].last().card
    Move.WasteToFoundation -> state.waste.first()
    else -> error("cardMovedBy is only defined for foundation moves, got $move")
}

private fun exceedsRankGap(state: GameState, card: Card): Boolean = card.rank.value - state.foundations.values.min() > EASY_RANK_GAP

/** True when some *other* exposed face-up card's only legal tableau destination is the column [move] would empty of its foundation-bound top card. */
private fun isSoleLandingSpotNeeded(state: GameState, move: Move): Boolean {
    val cardColumn = (move as? Move.TableauToFoundation)?.fromColumn ?: return false
    val others = buildList {
        for (col in 0 until TABLEAU_COLUMNS) if (col != cardColumn) state.tableau[col].lastOrNull()?.let { add(it.card) }
        state.waste.firstOrNull()?.let { add(it) }
    }
    return others.any { needer -> (0 until TABLEAU_COLUMNS).filter { canPlaceOnTableau(state.tableau[it], needer) } == listOf(cardColumn) }
}

/**
 * Filters Hard/Expert's setup moves down to ones that could plausibly be *setting
 * something up*, per `docs/games/klondike/DIFFICULTY_LEVELS.md`'s "necessary step toward a reveal or a
 * foundation play two or more moves later".
 *
 * Without this the group is every non-revealing tableau move, which is dominated by
 * relocating a run between columns that uncovers nothing — above all shuffling a King
 * from one empty column to another. Measured, that let Hard and Expert wander until the
 * move cap on 85 of 100 deals while rarely being genuinely stuck, dropping them to a
 * 2-4% win rate against Medium's 23% and inverting the tier ladder they are supposed to
 * extend. A move onto an empty column is kept only when it vacates a column that still
 * has face-down cards under it (real progress); everything else must land on a card,
 * building a longer ordered run rather than merely relocating one.
 */
private fun isProductiveSetup(state: GameState, move: Move.TableauToTableau): Boolean {
    if (state.tableau[move.toColumn].isNotEmpty()) return true
    return state.tableau[move.fromColumn].take(move.fromIndex).any { !it.faceUp }
}

/** True when [move] takes the whole face-up run off a column, exposing a face-down card beneath it. */
private fun isRevealMove(state: GameState, move: Move.TableauToTableau): Boolean {
    val deepestStart = validRunStartIndices(state.tableau[move.fromColumn]).lastOrNull() ?: return false
    return move.fromIndex == deepestStart && move.fromIndex > 0
}

/** Easy and up prefer the reveal that uncovers the most deeply buried column; Trivial does not compare candidates at all. */
private fun partitionReveals(
    state: GameState,
    reveals: List<Move.TableauToTableau>,
    tier: StrategyTier,
): Pair<List<Move>, List<Move>> {
    if (tier == StrategyTier.TRIVIAL || reveals.size < 2) return reveals to emptyList()
    fun buried(move: Move.TableauToTableau) = state.tableau[move.fromColumn].count { !it.faceUp }
    val maxBuried = reveals.maxOf(::buried)
    return reveals.partition { buried(it) == maxBuried }
}

/**
 * Expert-only "braiding": prefer a destination whose face-up run already mixes both
 * suits of its color, so the run can later split across two foundations without being
 * unstacked card by card. A soft nudge the doc itself calls a judgment call, so it only
 * ever reorders candidates, never removes any.
 */
private fun partitionByBraiding(state: GameState, moves: List<Move.WasteToTableau>, tier: StrategyTier): Pair<List<Move>, List<Move>> {
    if (tier != StrategyTier.EXPERT || moves.size < 2) return moves to emptyList()
    val card = state.waste.firstOrNull() ?: return moves to emptyList()
    return moves.partition { move ->
        state.tableau[move.toColumn].any { it.faceUp && it.card.suit != card.suit && it.card.color == card.color }
    }
}
