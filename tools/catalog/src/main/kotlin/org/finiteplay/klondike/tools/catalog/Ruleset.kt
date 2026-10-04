package org.finiteplay.klondike.tools.catalog

/**
 * A strategy tier, as the robustness grader sees it: a **priority order** over the moves the
 * tier offers (`docs/games/klondike/DIFFICULTY_LEVELS.md`).
 *
 * **One ruleset per ruleset-backed level, sharing its name.** The two were allowed to drift
 * apart once — five rulesets feeding four levels, so a deal graded "Hard" shipped as Medium and
 * every document had to say which sense it meant. They are one-to-one again and are meant to
 * stay that way: [levelForRuleset] is the identity, and the only levels without a ruleset are
 * Expert and Insane, which are cut from what DFS and A\* say rather than from any order
 * ("Levels as shipped").
 *
 * Through [EASY] only the order differs. Those tiers offer the player exactly the same moves,
 * so the graph is identical and everything the search relies on — the state fingerprint, the
 * memo, the loop detection — is unchanged; what changes is which tap the *undeviating* player
 * takes, and therefore which departures cost budget. That identity is the whole reason a deal
 * can be Easy rather than Trivial: the win is reachable by obvious moves either way, but only
 * Easy's order finds it.
 *
 * **[MEDIUM] breaks it**, and is the first tier that does. It carries two additions: full
 * foundation restraint, which is a reordering, and the setup move — a tableau move with nothing
 * visibly gained — which is by definition not an obvious move, so the tier cannot be expressed
 * as a reordering of the existing set and has to widen it. The disjointness gate survives the
 * change unharmed: Medium strictly dominates the tiers below, so "no lower order wins this
 * deal" still means what it says.
 *
 * Tiers are cumulative — every strategy available lower down remains available above — which is
 * what lets each be specified as "the one below, plus". `RulesetInheritanceTest` pins it,
 * because a rule written as `ruleset == X` reads correctly while X is the top tier and silently
 * stops being inherited the moment a tier is added above it. That has happened here once.
 */
enum class Ruleset {
    /** Obvious moves in a fixed order, no comparison between them. */
    TRIVIAL,

    /** Plus the deepest-reveal preference and two light foundation-restraint rules. */
    EASY,

    /** Plus full foundation restraint, and the setup move — the first tier to widen the move set. */
    MEDIUM,

    /** Plus foundation withdrawal: taking a banked card back onto the tableau. */
    HARD,
}

/**
 * Whether the tier admits **setup moves**: splitting a face-up run and relocating the upper
 * part, which neither turns a card over nor empties a column. [Ruleset.MEDIUM] up.
 */
val Ruleset.offersSetupMoves: Boolean get() = this >= Ruleset.MEDIUM

/**
 * Whether the tier admits **foundation withdrawal**: taking a banked card back onto the
 * tableau. Only [Ruleset.HARD] does, and its whole definition turns on it — a deal belongs
 * there only when no winning line the tiers below can find avoids it.
 */
val Ruleset.offersWithdrawal: Boolean get() = this >= Ruleset.HARD

/** The tiers below [this] one — every order a deal must already have defeated to belong here. */
val Ruleset.tiersBelow: List<Ruleset> get() = Ruleset.entries.take(ordinal)
