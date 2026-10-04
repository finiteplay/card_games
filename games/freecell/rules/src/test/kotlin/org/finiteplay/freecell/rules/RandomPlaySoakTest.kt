package org.finiteplay.freecell.rules

import kotlin.random.Random
import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.layout.dealGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cheapest proof the reducer is sound: play many random games and assert every invariant
 * after every move, the same discipline Spider's own `RandomPlaySoakTest` uses.
 */
class RandomPlaySoakTest {

    private val versions = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `random play never breaks an invariant`() {
        for (seed in 1L..150L) {
            var state = dealGame(seed, versions)
            val random = Random(seed)

            repeat(300) {
                val moves = legalMoves(state)
                if (moves.isEmpty()) return@repeat
                val next = applyMove(state, moves[random.nextInt(moves.size)])

                val violations = invariantViolations(next)
                assertEquals("seed $seed at move ${next.moveCount}: $violations", emptyList<String>(), violations)
                state = next
            }
        }
    }

    @Test
    fun `a fresh deal always offers a move`() {
        for (seed in 1L..40L) {
            val state = dealGame(seed, versions)
            assertTrue("seed $seed deals a layout with nothing to do", legalMoves(state).isNotEmpty())
        }
    }

    @Test
    fun `the automatic cascade never breaks an invariant either`() {
        for (seed in 1L..80L) {
            var state = dealGame(seed, versions)
            val random = Random(seed)

            repeat(150) {
                val moves = legalMoves(state)
                if (moves.isEmpty()) return@repeat
                val played = applyMove(state, moves[random.nextInt(moves.size)])
                val (cascaded, _) = runAutomaticFoundationCascade(played)

                val violations = invariantViolations(cascaded)
                assertEquals("seed $seed: $violations", emptyList<String>(), violations)
                state = cascaded
            }
        }
    }
}
