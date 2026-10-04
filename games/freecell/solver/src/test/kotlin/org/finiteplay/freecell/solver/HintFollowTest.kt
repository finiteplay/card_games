package org.finiteplay.freecell.solver

import org.finiteplay.freecell.layout.GameVersions
import org.finiteplay.freecell.session.FreeCellLogEntry
import org.finiteplay.freecell.session.FreeCellSession
import org.finiteplay.freecell.session.commitMove
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Following Hint, tap after tap, has to win like a person would: a reported game (seed 1924) followed a
 * several-thousand-move DFS line that shuttled runs between the same columns for dozens of hints.
 */
class HintFollowTest {
    private val versions = GameVersions(catalogVersion = 1, rulesVersion = 1, shuffleVersion = 1)

    @Test
    fun `following every hint wins in a human-length game without revisiting a board`() {
        // 1924 is the reported deal; the rest took the old DFS line 601, over 3,000, and 1,608 hints.
        for (seed in listOf(1924L, 2L, 5L, 4L)) {
            val engine = HintEngine()
            var session = FreeCellSession.start(seed, versions, automaticMovesEnabled = true)
            val seen = HashSet<Long>()
            var hints = 0
            while (!session.state.isWon) {
                assertTrue("seed $seed revisited a board after $hints hints", seen.add(exactStateHash(session.state)))
                assertTrue("seed $seed took more than $MAX_HINTS hints", hints < MAX_HINTS)
                val lastMove = (session.log.lastOrNull() as? FreeCellLogEntry.PlayerMove)?.move
                val outcome = engine.hint(session.state, HINT_SOLVER_LIMITS, lastMove)
                check(outcome is HintOutcome.Guidance) { "seed $seed: expected Guidance after $hints hints, got $outcome" }
                session = session.commitMove(outcome.move)
                hints++
            }
        }
    }

    private companion object {
        const val MAX_HINTS = 150
    }
}
