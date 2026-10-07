package org.finiteplay.holdem.testing

import org.finiteplay.holdem.rules.Action
import org.finiteplay.holdem.rules.Street

/**
 * The fixed-seed tournaments `EXECUTION_PLAN.md` H3's gate plays through the screen, each a tournament
 * seed plus the scripts its seats follow. The seeds were found by searching (`ScenarioSearchTest`) and
 * are committed here; `ScenarioCatalogTest` proves each [holds] still, headless, so a change to the
 * deal or the rules breaks that test rather than silently emptying the instrumented run.
 */
data class Scenario(
    val name: String,
    val seed: Long,
    val player: Script,
    val opponents: (Int) -> Script,
    val maxHands: Int,
    val holds: (Run) -> Boolean,
) {
    fun run(): Run = runHeadless(seed, player, opponents, maxHands)
}

private val callStation = Script { Move.CHECK_OR_CALL }
private val shover = Script { Move.ALL_IN }
private val folder = Script { Move.CHECK_OR_FOLD }

private fun raised(action: Action) = action is Action.Bet || action is Action.Raise || action === Action.AllIn

object Scenarios {
    private fun everyoneCalls(seat: Int) = callStation

    /** Seats 1 and 2 shove, the rest fold: a three-way pot at most, so stacks come apart. */
    private fun twoShovers(seat: Int) = if (seat <= 2) shover else folder

    val fold = Scenario(
        name = "fold",
        seed = 1L,
        player = Script { if (it.toCall > 0) Move.FOLD else Move.CHECK },
        opponents = ::everyoneCalls,
        maxHands = 1,
        holds = { run -> run.hands[0].state.history.any { it.seat == 0 && it.action === Action.Fold } },
    )

    val checkDown = Scenario(
        name = "checkDown",
        seed = 1L,
        player = callStation,
        opponents = ::everyoneCalls,
        maxHands = 1,
        holds = { run ->
            val hand = run.hands[0].state
            hand.result!!.showdown && hand.contesting(0) && hand.history.none { raised(it.action) }
        },
    )

    val betAndCall = Scenario(
        name = "betAndCall",
        seed = 1L,
        player = Script { if (it.street != Street.PREFLOP && it.legal?.bet != null) Move.BET_HALF_POT else Move.CHECK_OR_CALL },
        opponents = ::everyoneCalls,
        maxHands = 1,
        holds = { run ->
            val history = run.hands[0].state.history
            val bet = history.indexOfFirst { it.seat == 0 && it.action is Action.Bet }
            bet >= 0 && history.drop(bet + 1).any { it.seat != 0 && it.action === Action.Call }
        },
    )

    val raiseAndReraise = Scenario(
        name = "raiseAndReraise",
        seed = 1L,
        player = Script { v ->
            if (v.street == Street.PREFLOP && v.currentBet <= v.blinds.big && v.legal?.raise != null) Move.BET_DEFAULT else Move.CHECK_OR_CALL
        },
        opponents = { seat ->
            if (seat != 2) {
                callStation
            } else {
                Script { v ->
                    val raises = v.history.count { raised(it.action) }
                    if (v.street == Street.PREFLOP && raises == 1 && v.legal?.raise != null) Move.MIN_RAISE else Move.CHECK_OR_CALL
                }
            }
        },
        maxHands = 1,
        holds = { run ->
            val history = run.hands[0].state.history
            val first = history.indexOfFirst { it.seat == 0 && it.action is Action.Raise }
            first >= 0 && history.drop(first + 1).any { it.seat == 2 && it.action is Action.Raise }
        },
    )

    val allInWithSidePot = Scenario(
        name = "allInWithSidePot",
        seed = 1620L,
        player = shover,
        opponents = ::twoShovers,
        maxHands = 6,
        holds = { run ->
            run.hands.any { hand ->
                hand.state.result!!.awards.size >= 2 && hand.state.contesting(0) && hand.state.allIn[0]
            }
        },
    )

    val splitPot = Scenario(
        name = "splitPot",
        seed = 36L,
        player = callStation,
        opponents = ::everyoneCalls,
        maxHands = 1,
        holds = { run -> run.hands[0].result.awards.any { it.winners.size >= 2 && 0 in it.winners } },
    )

    val eliminated = Scenario(
        name = "eliminated",
        seed = 36L,
        player = shover,
        opponents = ::twoShovers,
        maxHands = 6,
        holds = { run -> run.last.playerOut && run.last.places[0] in 3..5 && run.hands.size >= 2 },
    )

    val tournamentWon = Scenario(
        name = "tournamentWon",
        seed = 8L,
        player = shover,
        opponents = { shover },
        maxHands = 1,
        holds = { run -> run.last.places[0] == 1 },
    )

    val all = listOf(fold, checkDown, betAndCall, raiseAndReraise, allInWithSidePot, splitPot, eliminated, tournamentWon)
}
