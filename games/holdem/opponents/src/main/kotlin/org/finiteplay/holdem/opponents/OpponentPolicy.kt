package org.finiteplay.holdem.opponents

import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.SeatView

/**
 * One opponent's decision procedure. Its only input is what that seat can see ([SeatView]) and a
 * [salt] the caller derives from the hand's seed, the seat and the action's index, so the same view
 * and salt always give the same action and a replayed tournament plays out identically
 * (`docs/games/holdem/DESIGN.md` "The policy"). The salt is a mixed number, not the seed: the policy
 * has no way to learn the deck. The returned action must be legal for [view].
 */
fun interface OpponentPolicy {
    fun decide(view: SeatView, salt: Long): Action
}

/**
 * H3's stand-in until the real policy (H7): check when free, call small bets, fold to large ones.
 * Always legal, deterministic, and enough to play a hand through.
 */
object PlaceholderPolicy : OpponentPolicy {
    override fun decide(view: SeatView, salt: Long): Action {
        val legal = checkNotNull(view.legal) { "seat ${view.seat} is not to act" }
        return when {
            legal.canCheck -> Action.Check
            legal.canCall && legal.toCall * 4 <= legal.stack -> Action.Call
            legal.canFold -> Action.Fold
            else -> Action.Call
        }
    }
}
