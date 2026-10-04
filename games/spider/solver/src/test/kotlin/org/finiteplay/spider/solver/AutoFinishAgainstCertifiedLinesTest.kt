package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.finiteplay.spider.rules.applyMove
import org.finiteplay.spider.rules.findAutoFinish
import org.finiteplay.spider.rules.isLegal
import org.junit.Assert.assertTrue
import org.junit.Test

private val VERSIONS = GameVersions(catalogVersion = 0, rulesVersion = 1, shuffleVersion = 1)

/**
 * Regression coverage for a real bug: `findAutoFinish`'s search used to fail on most genuinely
 * winnable thirteen-card endgames, not just hard or unsolvable ones. A plain, unweighted BFS
 * treated a card relocated to any of several interchangeable empty columns as a distinct state
 * once per column, so a board that was "obviously" almost won — a big already-sorted run next to
 * a handful of empty columns — had the *worst* branching factor of any position the search would
 * ever see, and regularly burned through the node budget on true three-move endgames. Fixed by
 * canonicalizing tableau columns before deduping visited states (`AutoFinish.kt`).
 *
 * For a sample of real winnable deals, this replays the solver's own certificate to the exact
 * point where thirteen or fewer cards remain in play, then checks that `findAutoFinish` — the
 * real on-device search, at its real node budget — can still solve from there. The certificate's
 * own tail is independent proof a solution exists, so a null result here is not "this position is
 * hard," it is the search failing on a position it must not fail on.
 */
class AutoFinishAgainstCertifiedLinesTest {
    @Test
    fun `findAutoFinish solves every certified deal's own 13-card checkpoint`() {
        for (suitCount in listOf(SuitCount.ONE, SuitCount.TWO)) {
            var found = 0
            var seed = 1L
            while (found < 50 && seed < 300_000) {
                val state = dealGame(seed = seed, versions = VERSIONS, suitCount = suitCount)
                val solver = SpiderSolver(SolverLimits(maxNodes = 10_000, maxMillis = 1_500, playouts = 150))
                val outcome = solver.solve(state)
                seed++
                if (!outcome.solved) continue
                val line = solver.certify(state) ?: continue
                found++

                var played = state
                var stopIndex = -1
                for ((i, move) in line.withIndex()) {
                    if (!isLegal(played, move)) break
                    played = applyMove(played, move)
                    val cardsInPlay = played.tableau.sumOf { it.size } + played.stock.size
                    if (cardsInPlay <= 13 && !played.isWon) {
                        stopIndex = i
                        break
                    }
                }
                if (stopIndex < 0) continue // this deal's certificate never passed through exactly 13

                val finish = findAutoFinish(played)
                assertTrue(
                    "seed ${seed - 1} ($suitCount): findAutoFinish found no finish for a position " +
                        "the certificate itself proves winnable (tableau=${played.tableau.map { it.size }})",
                    finish != null,
                )
                var check = played
                for (move in finish!!) check = applyMove(check, move)
                assertTrue("seed ${seed - 1} ($suitCount): the returned finish did not win", check.isWon)
            }
        }
    }
}
