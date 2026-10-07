package org.finiteplay.core.session

/** Wins at one level after which the player is asked whether to move up to the next (`docs/PLATFORM.md`). */
const val LEVEL_UP_WINS = 10

/**
 * The offer made after [wins] wins at [from]: move up to [to]. [L] is a game's own level type — a
 * difficulty tier, a suit count — so nothing here names one.
 */
data class LevelUpOffer<L>(val from: L, val to: L, val wins: Int)

/** The level after [current] in [levels], listed easiest first, or null where [current] is the last. */
fun <L> nextLevel(levels: List<L>, current: L): L? = levels.getOrNull(levels.indexOf(current) + 1)

/**
 * The offer a win earns, if any. A player is asked **once**, at exactly [LEVEL_UP_WINS] wins at the
 * level won, and only while still playing that level ([playing]): one who has already moved on, or
 * plays a mix, has nothing to be asked. There is nothing to move up to from the last level.
 */
fun <L> levelUpOffer(levels: List<L>, won: L, winsAtLevel: Int, playing: L?): LevelUpOffer<L>? {
    if (winsAtLevel != LEVEL_UP_WINS || playing != won) return null
    val next = nextLevel(levels, won) ?: return null
    return LevelUpOffer(from = won, to = next, wins = winsAtLevel)
}
