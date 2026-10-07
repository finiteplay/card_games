package org.finiteplay.holdem.rules

import org.finiteplay.holdem.rules.Fx.checkDown
import org.finiteplay.holdem.rules.Fx.run
import org.finiteplay.holdem.rules.Fx.step

/**
 * Hands built with stacked decks, one per rule a soak cannot reach reliably. [RoundFixturesTest]
 * asserts what each shows; [ReferenceHandsTest] pins each one's final state by hash.
 */
object ReferenceHands {
    val names = listOf(
        "wheel", "board plays", "odd chip split", "short all-in", "big blind option", "heads-up",
        "equal knockouts", "unequal knockouts", "short big blind", "short big blind folded to",
    )

    fun build(name: String): HoldemState = when (name) {
        "wheel" -> wheel()
        "board plays" -> boardPlays()
        "odd chip split" -> oddChipSplit()
        "short all-in" -> shortAllIn()
        "big blind option" -> bigBlindOption()
        "heads-up" -> headsUp()
        "equal knockouts" -> knockouts(listOf(5_000, 1_500, 1_500, 1_000, 0, 0))
        "unequal knockouts" -> knockouts(listOf(4_500, 1_500, 2_000, 1_000, 0, 0))
        "short big blind" -> shortBigBlind()
        "short big blind folded to" -> shortBigBlindFoldedTo()
        else -> error(name)
    }

    /** Heads-up: a wheel beats a pair of kings. Button 0 is the small blind. */
    fun wheel(): HoldemState {
        val t = Fx.tournament(listOf(4_500, 4_500, 0, 0, 0, 0), button = 0)
        return Fx.hand(t, mapOf(0 to "Kh Qs", 1 to "Ac 2d"), "3h 4s 5c Kd 9h")
            .run(0 to Action.Call, 1 to Action.Check)
            .checkDown()
    }

    /** Three players, a royal flush on the board: everyone plays it and the pot splits three ways. */
    fun boardPlays(): HoldemState {
        val t = Fx.tournament(listOf(3_000, 3_000, 3_000, 0, 0, 0), button = 0)
        return Fx.hand(t, mapOf(0 to "2c 3d", 1 to "4h 5d", 2 to "6c 7d"), "Ts Js Qs Ks As").checkDown()
    }

    /** Four players, the small blind folds, three tie on the board: 70 chips do not divide by three. */
    fun oddChipSplit(): HoldemState {
        val t = Fx.tournament(listOf(2_250, 2_250, 2_250, 2_250, 0, 0), button = 0)
        return Fx.hand(t, mapOf(0 to "2c 3d", 1 to "4h 5d", 2 to "6c 7d", 3 to "8c 9d"), "Ts Js Qs Ks As")
            .run(3 to Action.Call, 0 to Action.Call, 1 to Action.Fold, 2 to Action.Check)
            .checkDown()
    }

    /**
     * Four players; the short stack's flop all-in is a raise of 50 over a bet of 100, less than a
     * full raise. [probe] sees the position before each of the decisions that matter.
     */
    fun shortAllIn(probe: (String, HoldemState) -> Unit = { _, _ -> }): HoldemState {
        val t = Fx.tournament(listOf(2_900, 2_900, 2_950, 250, 0, 0), button = 0)
        var s = Fx.hand(t, mapOf(0 to "8c 4d", 1 to "Kc 5d", 2 to "Qh 6d", 3 to "Ah Ad"), "2c 7d 9s Jc 3h")
        s = s.run(3 to Action.Raise(100), 0 to Action.Call, 1 to Action.Call, 2 to Action.Call)
        probe("flop", s)
        s = s.run(1 to Action.Bet(100), 2 to Action.Call)
        probe("facing the bet, short stack to act", s)
        s = s.step(3, Action.AllIn)
        probe("after the short all-in, seat 0 has not acted", s)
        s = s.step(0, Action.Call)
        probe("after the short all-in, seat 1 has acted", s)
        s = s.step(1, Action.Call)
        probe("after the short all-in, seat 2 has acted", s)
        s = s.step(2, Action.Call)
        return s.checkDown()
    }

    /** Three players limp round to the big blind, who raises with the option; the others fold. */
    fun bigBlindOption(probe: (String, HoldemState) -> Unit = { _, _ -> }): HoldemState {
        val t = Fx.tournament(listOf(3_000, 3_000, 3_000, 0, 0, 0), button = 0)
        var s = Fx.hand(t, mapOf(0 to "2c 3d", 1 to "4h 5d", 2 to "6c 7d"), "Ts 8s 9h Kd 2h")
        s = s.run(0 to Action.Call, 1 to Action.Call)
        probe("the big blind's option", s)
        s = s.step(2, Action.Raise(60))
        probe("after the option raise", s)
        return s.run(0 to Action.Fold, 1 to Action.Fold)
    }

    /** Heads-up with the button on seat 1: it posts the small blind, acts first before the flop and last after. */
    fun headsUp(probe: (String, HoldemState) -> Unit = { _, _ -> }): HoldemState {
        val t = Fx.tournament(listOf(4_500, 4_500, 0, 0, 0, 0), button = 1)
        var s = Fx.hand(t, mapOf(0 to "Ac Kd", 1 to "7h 2s"), "Qc Jd Th 3s 4d")
        probe("dealt", s)
        s = s.run(1 to Action.Call, 0 to Action.Check)
        probe("flop", s)
        return s.checkDown()
    }

    /**
     * Seat 0 covers everyone and shoves; seats 1 and 2 call all in and lose; seat 3 folds. Each
     * seat's stack is the hand's starting stack.
     */
    fun knockouts(stacks: List<Int>): HoldemState {
        val t = Fx.tournament(stacks, button = 0)
        return Fx.hand(t, mapOf(0 to "Ah Ad", 1 to "Kc 7d", 2 to "Qd 8h", 3 to "6c 5s"), "2c 5d 9d Jh 3c")
            .run(3 to Action.Fold, 0 to Action.AllIn, 1 to Action.Call, 2 to Action.Call)
    }

    /** The big blind has 15 against a blind of 20: it posts 15 all in and the others still call 20. */
    fun shortBigBlind(): HoldemState {
        val t = Fx.tournament(listOf(3_000, 3_000, 15, 2_985, 0, 0), button = 0)
        return Fx.hand(t, mapOf(0 to "Qc 3d", 1 to "Kh Kd", 2 to "Ah Ad", 3 to "8c 9d"), "2s 4c 6h Td Js")
            .run(3 to Action.Call, 0 to Action.Call, 1 to Action.Call)
            .checkDown()
    }

    /** Folded to the small blind with a short big blind all in: the blinds are the whole pot. */
    fun shortBigBlindFoldedTo(): HoldemState {
        val t = Fx.tournament(listOf(3_000, 3_000, 15, 2_985, 0, 0), button = 0)
        return Fx.hand(t, mapOf(0 to "Qc 3d", 1 to "Kh Kd", 2 to "Ah Ad", 3 to "8c 9d"), "2s 4c 6h Td Js")
            .run(3 to Action.Fold, 0 to Action.Fold, 1 to Action.Fold)
    }
}
