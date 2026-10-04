package org.finiteplay.klondike.tools.catalog

import org.finiteplay.cards.Card
import org.finiteplay.klondike.board.GameState
import org.finiteplay.klondike.board.TABLEAU_COLUMNS
import org.finiteplay.klondike.board.dealGame
import org.finiteplay.klondike.rules.Move
import org.finiteplay.klondike.rules.applyMove
import org.finiteplay.klondike.rules.canPlaceOnFoundation
import org.finiteplay.klondike.rules.canPlaceOnTableau
import org.finiteplay.klondike.rules.resolveWasteTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Trivial grading against seeds whose verdicts were measured, not assumed.
 *
 * Every expectation here has been wrong at least once, because the grading model changed
 * underneath it — column-emptying became an obvious move, then loops stopped counting as
 * wins. Each change is legitimate and each moved these numbers, so the tests are re-derived
 * from measurement rather than adjusted until they pass.
 *
 * The failures that reached a player's phone were **not** caught here, which is the point of
 * the two build-time invariants in [buildTrivialTier]: a seed ships only with a
 * replay-verified winning line, and only if no other tier claims it.
 */
class TrivialRobustnessTest {

    /** The most robust seeds in a million, per the corrected model: all survive four mistakes. */
    private val robustAtFour = listOf(359669L, 416448L, 498957L)

    @Test
    fun `the most robust seeds survive four deviations`() {
        for (seed in robustAtFour) {
            assertEquals("seed $seed", TrivialOutcome.ROBUST, checkTrivialRobustness(seed, maxDeviations = 4).outcome)
        }
    }

    @Test
    fun `robustness is monotone, so surviving four implies surviving fewer`() {
        for (seed in robustAtFour) {
            for (budget in 0..3) {
                assertEquals("seed $seed budget $budget", TrivialOutcome.ROBUST, checkTrivialRobustness(seed, budget).outcome)
            }
        }
    }

    /**
     * Seed 1's reference line loops rather than reaching a win. It must grade FRAGILE: a loop
     * is not a victory, and treating one as success is what passed 164 of 180 seeds that had
     * no winning line at all — including the hand a player then lost after 24 faultless moves.
     */
    @Test
    fun `a reference line that only loops is not a win`() {
        val verdict = checkTrivialRobustness(1L, maxDeviations = 0)
        assertEquals(TrivialOutcome.FRAGILE, verdict.outcome)
        assertTrue("expected a loop diagnosis, got: ${verdict.detail}", verdict.detail.contains("loop"))
    }

    /** Seed 2 wins under undeviating play but not with a mistake to spare. */
    @Test
    fun `seed 2 wins undeviating`() {
        assertEquals(TrivialOutcome.ROBUST, checkTrivialRobustness(2L, maxDeviations = 0).outcome)
    }

    /**
     * Every graded seed must have a winning line the solution generator can reproduce. The
     * two are separate implementations of the same reference order and have silently diverged
     * twice; a seed graded robust with no shippable solution means the grade is wrong.
     */
    @Test
    fun `graded seeds have a winning line the solution generator reproduces`() {
        for (seed in robustAtFour) {
            val line = trivialSolution(seed)
            assertTrue("seed $seed produced no line", line != null && line.isNotEmpty())
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            for (move in line!!) state = applyMove(state, move)
            assertTrue("seed $seed line does not win", state.isWon)
        }
    }

    /**
     * Reference pile-tap generator: walks the pile one draw at a time, exactly as a player
     * would. The fast path computes rotation differently, and the two must agree on moves,
     * order, and the pile each successor leaves — two earlier defects emitted identical move
     * sets and differed only in order or in the board left behind.
     */
    private fun referencePileTaps(state: GameState): List<Pair<Move, List<Card>>> {
        val pileSize = state.stock.size + state.waste.size
        if (pileSize == 0) return emptyList()
        val out = ArrayList<Pair<Move, List<Card>>>()
        var rotated = state
        var seen = 0
        var guard = 2 * pileSize + 2
        while (seen < pileSize && guard-- > 0) {
            val card = rotated.waste.firstOrNull()
            if (card == null) {
                rotated = advancePile(rotated)
                continue
            }
            val placeable = canPlaceOnFoundation(state.foundations, card) ||
                (0 until TABLEAU_COLUMNS).any { canPlaceOnTableau(state.tableau[it], card) }
            if (placeable) resolveWasteTap(rotated)?.let { out.add(it to pileCycle(applyMove(rotated, it))) }
            seen++
            rotated = advancePile(rotated)
        }
        return out
    }

    @Test
    fun `the fast pile rotation agrees with a plain draw-by-draw sweep`() {
        var compared = 0
        var withWaste = 0
        for (seed in listOf(1L, 2L, 359669L, 416448L, 498957L, 12345L)) {
            var state = dealGame(seed, D1S_SPIKE_VERSIONS)
            repeat(120) { step ->
                repeat(step % 5) { state = advancePile(state) }
                if (state.waste.isNotEmpty()) withWaste++
                assertEquals("seed $seed step $step", referencePileTaps(state), pileTapMovesAndCycles(state))
                compared++
                state = obviousSuccessorForTest(state) ?: return@repeat
            }
        }
        assertTrue("expected many comparisons, made $compared", compared > 300)
        assertTrue("comparison never saw a non-empty waste, so it proves nothing", withWaste > 80)
    }

    @Test
    fun `the pile sweep offers every placeable pile card, at every rotation`() {
        var state = dealGame(359669L, D1S_SPIKE_VERSIONS)
        repeat(30) {
            assertEquals(
                "rotation $it",
                placeablePileCards(state).size,
                pileTapMoves(state).size,
            )
            state = advancePile(state)
        }
        assertTrue(state.stock.size + state.waste.size > 0)
    }
}
