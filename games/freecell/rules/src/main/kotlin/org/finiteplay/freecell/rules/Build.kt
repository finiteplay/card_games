package org.finiteplay.freecell.rules

import org.finiteplay.cards.Card

/**
 * True when [over] may be placed directly on [under] on the tableau: one rank lower and the
 * opposite colour.
 *
 * This is the same rule Klondike's `canBuild` implements (`games/klondike/rules`'s own
 * `Build.kt`), and the two are checked against each other in `BuildAgreesWithKlondikeTest`. They
 * are kept as separate functions rather than shared code: dependencies run one way, a game's
 * `app` depends on its own `rules` and `solver`, which depend on the shared `solitaire` and
 * `core` layers (`docs/ARCHITECTURE.md`) — never on another game's `rules` — so
 * `games/freecell/rules` cannot depend on `games/klondike/rules`, and there is no existing
 * shared layer for "some solitaires but not Spider": only `core/cards` (every card game) and
 * `solitaire` (every solitaire, which Spider's own `canPlaceOn` already disagrees with).
 * Inventing a new shared module for two games based on a design-time read of the rules would be
 * exactly the mistake `docs/ARCHITECTURE.md` "What moved once a second game existed, and what
 * stayed" warns against — a match is only worth sharing once it is a match between two
 * *implementations*, not two specs.
 */
fun canBuild(under: Card, over: Card): Boolean =
    over.rank.value == under.rank.value - 1 && over.color != under.color
