package org.finiteplay.klondike.rules

import org.finiteplay.cards.Card

/**
 * True when [over] may be placed directly on [under] in a tableau sequence: one rank lower and
 * the opposite colour.
 *
 * This is a Klondike rule, not a fact about cards, which is why it sits here rather than in
 * `core/cards` (`docs/ARCHITECTURE.md` "What deliberately stays game-owned"). Spider settled
 * the question by being the second example: its packing rule is one rank lower at any suit, and
 * the two agree on nothing but the rank step. A shared `canBuild` would have had to grow a
 * parameter naming which game was asking.
 */
fun canBuild(under: Card, over: Card): Boolean =
    over.rank.value == under.rank.value - 1 && over.color != under.color
