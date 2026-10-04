package org.finiteplay.klondike.solver

import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.GameStateFixtures
import org.finiteplay.klondike.board.GameVersions
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.board.GameStateFixtures.faceUp
import org.finiteplay.klondike.board.GameStateFixtures.withFoundation
import org.finiteplay.klondike.board.GameStateFixtures.withStock
import org.finiteplay.klondike.board.GameStateFixtures.withTableauColumn
import org.finiteplay.cards.Card
import org.finiteplay.cards.Rank
import org.finiteplay.cards.Suit
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.solver.search.SolverLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HintEngineTest {

    private companion object {
        /**
         * What the game screen actually hands the engine (`GameViewModel.hintSolverLimits`,
         * `DESIGN.md` "On-Device Hint Search"), rather than [SolverLimits]'s offline defaults.
         * A hint search that only behaves at 1.5 million nodes is not the one players get.
         * The wall-clock figure is the default of the player-facing "Hint timeout" setting
         * (`core:ui`'s `HintTimeout`, 1–12 s); a player can pick a shorter or longer one.
         */
        val INTERACTIVE_LIMITS = SolverLimits(maxNodes = 340_000L, maxDurationMs = 5_000L)

        /** One thread per seed; the machine this runs on has more cores than that. */
        const val SEED_THREADS = 12
    }

    private fun oneMoveFromWin() = GameStateFixtures.emptyBoard(seed = 42L)
        .withFoundation(Suit.CLUBS, 13)
        .withFoundation(Suit.DIAMONDS, 13)
        .withFoundation(Suit.HEARTS, 13)
        .withFoundation(Suit.SPADES, 12)
        .withTableauColumn(0, listOf(faceUp(Card(Suit.SPADES, Rank.KING))))

    /**
     * Every tableau column holds one face-up card whose rank has no neighbor two ranks
     * away among the others, so no tableau-to-tableau move is ever legal; none is
     * foundation-eligible (foundations start empty and no card here is an Ace); no
     * column is empty, so not even a King could move. The only legal actions are
     * stock draw/recycle, which cycle through finitely many waste/stock arrangements
     * without ever changing the tableau — a small but genuinely unsolvable board.
     */
    private fun deadlockedBoard() = GameStateFixtures.emptyBoard(seed = 7L)
        .withTableauColumn(0, listOf(faceUp(Card(Suit.CLUBS, Rank.THREE))))
        .withTableauColumn(1, listOf(faceUp(Card(Suit.DIAMONDS, Rank.FIVE))))
        .withTableauColumn(2, listOf(faceUp(Card(Suit.CLUBS, Rank.SEVEN))))
        .withTableauColumn(3, listOf(faceUp(Card(Suit.DIAMONDS, Rank.NINE))))
        .withTableauColumn(4, listOf(faceUp(Card(Suit.CLUBS, Rank.JACK))))
        .withTableauColumn(5, listOf(faceUp(Card(Suit.DIAMONDS, Rank.KING))))
        .withTableauColumn(6, listOf(faceUp(Card(Suit.HEARTS, Rank.THREE))))
        .withStock(listOf(Card(Suit.SPADES, Rank.TWO), Card(Suit.SPADES, Rank.FOUR)))

    @Test
    fun `hint guides toward a proven winning move`() {
        val engine = HintEngine()
        val outcome = engine.hint(oneMoveFromWin(), SolverLimits())

        assertEquals(HintOutcome.Guidance(Move.TableauToFoundation(0), 0, 0)::class, outcome::class)
        outcome as HintOutcome.Guidance
        assertEquals(Move.TableauToFoundation(0), outcome.move)
    }

    @Test
    fun `hint reports no solution for a genuinely unsolvable board`() {
        val engine = HintEngine()
        val outcome = engine.hint(deadlockedBoard(), SolverLimits())

        assertTrue(outcome is HintOutcome.NoSolution)
    }

    /**
     * A board the Expert strategy ruleset cannot win, so the hint genuinely reaches the
     * search — seed 40 is D1s-certified solvable but is one of the deals no ruleset
     * plays out (`docs/games/klondike/DIFFICULTY_LEVELS.md`). The near-win fixture is no longer usable
     * for this: the Expert pre-pass wins it outright and never searches at all, which is
     * exactly what the test below this one pins.
     */
    private fun needsRealSearch() = dealGame(40L, GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1))

    @Test
    fun `a repeated hint request on an unchanged board reuses the cached certificate instead of re-solving`() {
        val engine = HintEngine()
        val state = needsRealSearch()

        val first = engine.hint(state, SolverLimits()) as HintOutcome.Guidance
        val second = engine.hint(state, SolverLimits()) as HintOutcome.Guidance

        assertTrue("first call should actually search", first.nodes > 0)
        assertEquals(0L, second.nodes)
        assertEquals(first.move, second.move)
    }

    @Test
    fun `a board the trivial ruleset can win is answered without searching at all`() {
        val outcome = HintEngine().hint(oneMoveFromWin(), SolverLimits()) as HintOutcome.Guidance

        assertEquals(Move.TableauToFoundation(0), outcome.move)
        assertEquals("the trivial pre-pass should answer before any search runs", 0L, outcome.nodes)
    }

    @Test
    fun `hint tries strategies in tier order then DFS then withdrawal A-star`() {
        val stages = mutableListOf<HintStage>()
        val engine = HintEngine(dfsNodeLimit = 1L, stageObserver = stages::add)

        engine.hint(needsRealSearch(), SolverLimits(maxNodes = 3L, maxDurationMs = 8_000L))

        assertEquals(
            listOf(
                HintStage.TRIVIAL,
                HintStage.EASY,
                HintStage.MEDIUM,
                HintStage.HARD,
                HintStage.EXPERT,
                HintStage.DFS,
                HintStage.A_STAR_WITHDRAWAL,
            ),
            stages,
        )
    }

    @Test
    fun `seed 147 remains covered by the staged hint search`() {
        val state = dealGame(147L, GameStateFixtures.TEST_VERSIONS)
        val outcome = HintEngine().hint(state, SolverLimits(maxNodes = 340_000L, maxDurationMs = 8_000L))

        assertTrue("expected guidance, got $outcome", outcome is HintOutcome.Guidance)
    }

    @Test
    fun `an inconclusive result still suggests the move the expert ruleset would play`() {
        // A budget too small to prove anything about a fresh deal, on a board the Expert
        // ruleset cannot win either - so the search gives up and the suggestion is all
        // there is to offer.
        val outcome = HintEngine().hint(needsRealSearch(), SolverLimits(maxNodes = 1L, maxDurationMs = 1L))

        assertTrue("expected an inconclusive verdict, got $outcome", outcome is HintOutcome.Inconclusive)
        assertTrue("inconclusive should still suggest something to try", (outcome as HintOutcome.Inconclusive).suggestion != null)
    }

    @Test
    fun `the dead-state cache makes a repeated unsolvable verdict cheaper to re-derive`() {
        val engine = HintEngine()
        val state = deadlockedBoard()

        val first = engine.hint(state, SolverLimits()) as HintOutcome.NoSolution
        val second = engine.hint(state, SolverLimits()) as HintOutcome.NoSolution

        assertTrue("second exhaustive search should explore no more than the first", second.nodes <= first.nodes)
    }

    /**
     * The shipped-solution path (`docs/games/klondike/DEALS.md` "Shipped Solutions"): a seed's stored
     * winning line is handed to the engine up front, so a player taking each hint and
     * playing it walks that line without the search ever running.
     */
    @Test
    fun `a primed solution answers hints along the whole line without searching`() {
        val start = needsRealSearch()
        val solution = findWinningLine(start) ?: error("fixture seed should be solvable")
        val engine = HintEngine()
        engine.primeWithKnownSolution(start, solution)

        var state = start
        var steps = 0
        while (!state.isWon && steps < solution.size) {
            val outcome = engine.hint(state, SolverLimits())
            assertTrue("step $steps expected guidance, got $outcome", outcome is HintOutcome.Guidance)
            outcome as HintOutcome.Guidance
            assertEquals("step $steps should have cost no search", 0L, outcome.nodes)
            state = applyMove(state, outcome.move)
            steps++
        }
        assertTrue("following the primed line should reach a win, stopped after $steps steps", state.isWon)
    }

    /**
     * Following hints must converge on a win, never cycle. Observed on a device before
     * the Expert pre-pass cached its whole line: each request replayed the ruleset from
     * scratch against a fresh cycle-prevention set, so it proposed the move that undid
     * the previous one and a run shuffled between two columns indefinitely.
     */
    @Test
    fun `taking each hint in turn makes progress instead of undoing the last move`() {
        val engine = HintEngine()
        var state = dealGame(2L, GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1))
        val seen = HashSet<GameState>()
        var steps = 0

        while (!state.isWon && steps < 600) {
            val outcome = engine.hint(state, SolverLimits())
            if (outcome !is HintOutcome.Guidance) break
            assertTrue("hint revisited a board already seen after $steps steps", seen.add(state))
            state = applyMove(state, outcome.move)
            steps++
        }

        assertTrue("following hints should reach a win, stopped after $steps steps", state.isWon)
    }

    @Test
    fun `a primed solution that does not fit the board is ignored rather than trusted`() {
        val engine = HintEngine()
        // A line from a different board: replay rejects it, so the engine must fall back
        // to solving rather than emitting an illegal move.
        engine.primeWithKnownSolution(oneMoveFromWin(), listOf(Move.Draw, Move.Draw, Move.Draw))

        val outcome = engine.hint(oneMoveFromWin(), SolverLimits())

        assertEquals(Move.TableauToFoundation(0), (outcome as HintOutcome.Guidance).move)
    }

    @Test
    fun `reset clears both caches so the next hint re-solves from scratch`() {
        val engine = HintEngine()
        val state = needsRealSearch()

        engine.hint(state, SolverLimits())
        engine.reset()
        val afterReset = engine.hint(state, SolverLimits()) as HintOutcome.Guidance

        assertTrue(afterReset.nodes > 0)
    }

    @Test
    fun `following hints never asks a player to put a run straight back where it came from`() {
        // The reported defect, end to end: press Hint, play the move, press Hint again.
        // The next suggestion must never be the inverse of one just made while nothing
        // else touched either column — that reads as the hint changing its mind.
        //
        // Twelve independent games, one per thread: each has its own engine, its own board and
        // its own history, so the only thing the run shares is the failure list. Played out one
        // after another this single test was 386 of the suite's 435 seconds.
        val failures = java.util.Collections.synchronizedList(ArrayList<String>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(SEED_THREADS)
        try {
            (1L..12L).map { seed -> pool.submit { failures += hintTripFailures(seed) } }.forEach { it.get() }
        } finally {
            pool.shutdown()
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Plays [seed] by hint alone, returning a message for every suggestion that undoes an earlier one. */
    private fun hintTripFailures(seed: Long): List<String> {
        val engine = HintEngine()
        var state = dealGame(seed, GameStateFixtures.TEST_VERSIONS)
        val played = ArrayList<Move>()
        val sequenceTrips = ArrayList<PlayedRun>()
        val failures = ArrayList<String>()

        repeat(120) {
            val outcome = engine.hint(state, INTERACTIVE_LIMITS)
            val move = (outcome as? HintOutcome.Guidance)?.move ?: return@repeat
            if (move is Move.TableauToTableau) {
                val run = state.tableau[move.fromColumn].drop(move.fromIndex).map { it.card }
                val pointless = sequenceTrips.lastOrNull { it.isUndoneBy(move, run, played) }
                if (pointless != null) {
                    failures += "seed $seed, move ${played.size}: hint proposes $move, putting $run straight " +
                        "back where ${pointless.move} took it from, with neither column used in between"
                }
                sequenceTrips += PlayedRun(move, run, played.size)
            }
            played += move
            state = applyMove(state, move)
            if (state.isWon) return@repeat
        }
        return failures
    }

    /** A tableau move already suggested, with the cards it carried and where it sat in the sequence. */
    private class PlayedRun(val move: Move.TableauToTableau, val run: List<Card>, val playedAt: Int) {
        /**
         * True when [next] carries exactly [run] back where this move took it from and
         * neither column was used in between — the shuffle the player experiences as the
         * hint undoing itself.
         *
         * "Used in between" has to mean *any* intervening move touching either column, not
         * just a net change to their contents: staging a run on a column so other runs can
         * be stacked on it and taken off again leaves that column looking untouched at the
         * end, and the trip out and back was real work.
         */
        fun isUndoneBy(next: Move.TableauToTableau, nextSequence: List<Card>, played: List<Move>): Boolean {
            if (next.fromColumn != move.toColumn || next.toColumn != move.fromColumn) return false
            if (nextSequence != run) return false
            return played.drop(playedAt + 1).none {
                val touched = columnsTouched(it)
                move.fromColumn in touched || move.toColumn in touched
            }
        }
    }
}

private fun columnsTouched(move: Move): Set<Int> = when (move) {
    is Move.TableauToTableau -> setOf(move.fromColumn, move.toColumn)
    is Move.TableauToFoundation -> setOf(move.fromColumn)
    is Move.WasteToTableau -> setOf(move.toColumn)
    is Move.FoundationToTableau -> setOf(move.toColumn)
    else -> emptySet()
}
