package org.finiteplay.spider.solver

import org.finiteplay.spider.layout.GameVersions
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.layout.dealGame
import org.junit.Assert.assertTrue
import org.junit.Test

class FourSuitLongCampaignTest {
    @Test
    fun verifierAcceptsKnownWinningPackedCertificate() {
        val record = IntArrayList(600)
        val board = FastBoard.from(dealGame(1, GameVersions(0, 1, 1), SuitCount.TWO))
        val solver = SpiderSolver(HINT_SOLVER_LIMITS)
        val solve = SpiderSolver::class.java.getDeclaredMethod("solve", FastBoard::class.java, IntArrayList::class.java).apply { isAccessible = true }
        val outcome = solve.invoke(solver, board, record) as SolveOutcome
        assertTrue(outcome.solved)
        assertTrue(FourSuitLongCampaign.verify(1, SuitCount.TWO, record).isNotEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun verifierRejectsNonWinningLine() {
        FourSuitLongCampaign.verify(1, SuitCount.FOUR, IntArrayList(1))
    }
}
