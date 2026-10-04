package org.finiteplay.klondike.tools.catalog

/**
 * Explains named seeds: what the ladder says about them, and what a human playing obvious
 * moves would find.
 *
 * The two are different questions and the difference is the point of this report. A deal's
 * **level** is the lowest ruleset whose *undeviating* line wins it — one fixed priority order,
 * no choices. A player is not undeviating: offered two obvious moves they pick one, and if the
 * first fails they try the other. `searchStrategyLine` is that player, exploring every equally
 * ranked choice inside a tier's move set.
 *
 * So a deal can be graded Easy — Trivial's single line does not win it — while any player
 * taking obvious moves wins it comfortably, because some *other* order of the same obvious
 * moves does. That deal is labelled Easy and feels Trivial, which is exactly the complaint
 * this report exists to check.
 */
fun explainSeeds(seeds: List<Long>, maxRobustness: Int = 6, log: (String) -> Unit = ::println) {
    log("seed        level     robustness  trivial-line  trivial-search  easy-search  triv-paths  easy-paths  moves")
    var trivialSearchWins = 0
    for (seed in seeds) {
        val graded = runCatching { gradeOneSeed(seed, maxRobustness, maxStates = 400_000) }.getOrNull()
        val trivialLine = runCatching { undeviatingLineWins(seed, Ruleset.TRIVIAL) }.getOrDefault(false)
        val trivialSearch = runCatching { searchStrategyLine(seed, Ruleset.TRIVIAL).outcome }.getOrNull()
        val easySearch = runCatching { searchStrategyLine(seed, Ruleset.EASY).outcome }.getOrNull()
        if (trivialSearch == StrategySearchOutcome.WON) trivialSearchWins++
        log(
            "%-11d %-9s %-11s %-13s %-15s %-12s %-11s %-11s %d".format(
                seed,
                graded?.level?.name ?: "none",
                graded?.robustness?.toString() ?: "-",
                if (trivialLine) "wins" else "no",
                when (trivialSearch) {
                    StrategySearchOutcome.WON -> "WINS"
                    StrategySearchOutcome.DEAD -> "no"
                    else -> "capped"
                },
                if (easySearch == StrategySearchOutcome.WON) "wins" else "no",
                // How often a player who never backs up still wins, choosing at random among
                // moves the tier calls equally good — the forgiveness the levels are cut by.
                "%.0f%%".format(strategyPathWinRate(seed, Ruleset.TRIVIAL) * 100),
                "%.0f%%".format(strategyPathWinRate(seed, Ruleset.EASY) * 100),
                graded?.moves ?: 0,
            ),
        )
    }
    log("")
    log("$trivialSearchWins of ${seeds.size} are winnable by obvious moves alone, once the player")
    log("is allowed to choose between them — which is what makes a level feel like Trivial.")
}
