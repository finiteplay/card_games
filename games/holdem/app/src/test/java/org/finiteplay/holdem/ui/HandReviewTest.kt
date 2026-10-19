package org.finiteplay.holdem.ui

import org.finiteplay.holdem.opponents.HintReason
import org.finiteplay.holdem.opponents.Threats
import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.HandCategory
import org.finiteplay.holdem.rules.HindsightResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Looking back at a hand played after a Hint (`UI_SPEC.md` "Hint Review"). */
class HandReviewTest {
    private val threats = Threats(0.30, listOf(HandCategory.FLUSH to 0.07, HandCategory.STRAIGHT to 0.03))
    private val hint = HintShown(Action.Call, 0.62, 0.25, nothingToCall = false, reason = HintReason.CALL_GOOD_PRICE, threats = threats)
    private fun use(followed: Boolean, last: Boolean = true) = HintUse(hint, followed, last)

    @Test
    fun `a raise is followed by any raise, and a call only by a call`() {
        assertTrue(followedHint(Action.Raise(300), Action.Bet(100)))
        assertTrue(followedHint(Action.AllIn, Action.Raise(300)))
        assertTrue(followedHint(Action.Call, Action.Call))
        assertFalse(followedHint(Action.Call, Action.Raise(300)))
        assertFalse(followedHint(Action.Fold, Action.Check))
    }

    @Test
    fun `following the hint and winning or losing is judged on the decision`() {
        assertEquals(ReviewKind.FOLLOWED_WON, reviewOf(use(true), 400, false, null)!!.kind)
        assertEquals(ReviewKind.FOLLOWED_LOST, reviewOf(use(true), -400, false, null)!!.kind)
    }

    @Test
    fun `going against the hint is called out whether it worked or not`() {
        assertEquals(ReviewKind.IGNORED_WON, reviewOf(use(false), 400, false, null)!!.kind)
        assertEquals(ReviewKind.IGNORED_LOST, reviewOf(use(false), -400, false, null)!!.kind)
    }

    @Test
    fun `a fold is judged only when it was the hinted decision`() {
        assertEquals(ReviewKind.FOLLOWED_FOLD, reviewOf(use(true), -50, true, null)!!.kind)
        assertEquals(ReviewKind.IGNORED_FOLD, reviewOf(use(false), -50, true, null)!!.kind)
        assertNull(reviewOf(use(true, last = false), -50, true, null))
    }

    @Test
    fun `no hint and a split pot say nothing`() {
        assertNull(reviewOf(null, 400, false, null))
        assertNull(reviewOf(use(true), 0, false, null))
    }

    @Test
    fun `losing to a hand the hint warned about says how likely it was`() {
        val r = reviewOf(use(true), -400, false, HandCategory.FLUSH)!!
        assertEquals(HandCategory.FLUSH, r.beatenBy)
        assertEquals(7, r.beatenPercent)
        // A hand the hint did not list, and any win, add no warning.
        assertNull(reviewOf(use(true), -400, false, HandCategory.FOUR_OF_A_KIND)!!.beatenBy)
        assertNull(reviewOf(use(true), 400, false, HandCategory.FLUSH)!!.beatenBy)
    }

    @Test
    fun `a full house or better is one entry`() {
        val t = Threats(0.1, listOf(HandCategory.FULL_HOUSE to 0.04))
        val h = hint.copy(threats = t)
        assertEquals(4, reviewOf(HintUse(h, true, true), -1, false, HandCategory.FOUR_OF_A_KIND)!!.beatenPercent)
    }

    @Test
    fun `a fold carries what the folded hand would have done, and only a fold does`() {
        val folded = reviewOf(use(true), -50, true, null, HindsightResult.WON)!!
        assertEquals(HindsightResult.WON, folded.hindsightResult)
        assertEquals(HindsightResult.TIED, reviewOf(use(true), -50, true, null, HindsightResult.TIED)!!.hindsightResult)
        assertEquals(HindsightResult.LOST, reviewOf(use(true), -50, true, null, HindsightResult.LOST)!!.hindsightResult)
        assertNull(reviewOf(use(true), -50, true, null)!!.hindsightResult)
        // A hand that was played to the end has nothing to look back on: the cards were shown.
        assertNull(reviewOf(use(true), 400, false, null, HindsightResult.WON)!!.hindsightResult)
    }
}
