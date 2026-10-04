package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card
import org.finiteplay.cards.Suit

/**
 * The predicate `automaticFoundationCascade` uses to decide a foundation move is safe enough to
 * make unasked: Aces and Twos are always safe; a higher rank is safe only once every suit has
 * already placed the previous rank.
 *
 * This is the same rule Klondike's `isSafeFoundationMove` implements (`games/klondike/rules/
 * SafeFoundation.kt`) — not the weaker opposite-colour-pair rule
 * `docs/games/klondike/DIFFICULTY_LEVELS.md` calls "the classic rule" for its own Medium-tier
 * strategy grading, which describes a cautious *player*'s choice rather than what the code
 * automates (`docs/games/freecell/RULES.md` "Automatic Foundation Moves").
 */
fun isSafeFoundationMove(foundations: Map<Suit, Int>, card: Card): Boolean {
    if (card.rank.value <= 2) return true
    val previousRank = card.rank.value - 1
    return foundations.values.all { it >= previousRank }
}
