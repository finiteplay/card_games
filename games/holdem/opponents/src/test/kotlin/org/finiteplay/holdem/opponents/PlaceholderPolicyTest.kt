package org.finiteplay.holdem.opponents

import org.finiteplay.holdem.rules.apply
import org.finiteplay.holdem.rules.Phase
import org.finiteplay.holdem.rules.HoldemState
import org.finiteplay.holdem.rules.startTournament
import org.finiteplay.holdem.rules.seatView
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderPolicyTest {
    @Test
    fun `it plays a hand through with only legal actions`() {
        var state: HoldemState = startTournament(7L).state
        var steps = 0
        while (state.phase == Phase.BETTING && steps++ < 200) {
            val view = state.seatView(state.toAct)
            val action = PlaceholderPolicy.decide(view, 0L)
            assertTrue(view.legal!!.allows(action))
            state = checkNotNull(apply(state, state.toAct, action))
        }
        assertTrue(state.phase != Phase.BETTING)
    }
}
