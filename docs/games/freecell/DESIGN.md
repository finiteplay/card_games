# FreeCell Solitaire — Design

Publisher: [FinitePlay LLC](https://finiteplay.org)

What the game is, and the decisions behind it. `RULES.md` is the rules; `docs/PLATFORM.md` is
everything true of every FinitePlay game and is not restated here.

## Status

This is the design the game was built against, and it still states what FreeCell is. What has
actually been built, and what is left, is `EXECUTION_PLAN.md`'s Status table, the one place
that says.

## The product

FreeCell, offline, Android only — the platform constraints, unchanged. One app, one game, sharing
`core/*` and `solitaire/*` with Klondike and Spider.

Every card is dealt face up from the first move. There is no hidden information anywhere in the
layout, no waste to draw from, and no chance once the deal is made — so whether a game is won is
decided entirely by the player's choices. A winnable deal can still be played into a dead end. The
temporary storage is the four free cells and whatever tableau columns the player manages to empty,
and spending both wisely is what the whole game is about.

## What FreeCell is not

FreeCell shares Klondike's build rule but little else about its shape:

- **No hidden cards, ever.** All 52 cards are face up the moment the deal completes. There is no
  flip, no stock, no waste, and — because nothing is ever hidden — nearly every deal is winnable
  (`DEALS.md`): Klondike and Spider both reject most of their candidate deals during
  certification, FreeCell almost none.
- **Free cells replace the waste as the game's temporary storage**, but they hold *any* card, not
  a drawn one, and a card in a free cell is exactly as available as a card at the foot of a
  column — there is no priority between them the way Klondike's waste top is the only playable
  card. An empty column is worth more than a free cell: it holds a whole sequence rather than one
  card, and each one doubles how long a run a supermove can carry (`RULES.md` "Supermove").
- **Any card, not just a King, may enter an empty column.** Klondike protects the one card that
  can start a new pile; FreeCell has nothing to protect, since a column can be emptied and
  refilled at will and every card is already visible.
- **A legal multi-card move ("supermove") is a shorthand for cards the player could already move
  one at a time** using the free cells and empty tableau columns as waypoints, never a new
  capability — see "Supermove" in `RULES.md`. It exists because typing out fifteen individual
  single-card moves for something the player has already fully planned would make FreeCell nearly
  unplayable by tap or drag.
- **Foundations are a one-way trip.** A card played to a foundation never returns to the tableau,
  unlike Klondike's foundation withdrawal. With every card visible and free cells and empty
  columns already acting as the game's own "put it back for later," a foundation take-back has
  never proven necessary in any mainstream FreeCell implementation, and this game follows that
  precedent. There is accordingly no parked-card rule either, which exists in Klondike solely to
  protect a withdrawn card from automation.

## Interaction

Follows the shared tap/drag model — tap to move a card or a run to its most sensible destination,
drag to choose deliberately, undo, new deal, replay — with the pieces that differ from Klondike:

- **A tableau tap moves the tapped card and every card below it that forms a legal sequence with
  it**, in this order: the nearest legal tableau column to the right of its own column; a
  provably safe foundation (`RULES.md` "Automatic Foundation Moves"); the nearest legal tableau
  column to the left; an unsafe foundation; and, only for a single card (a free cell holds
  exactly one), an empty free cell. A tap whose sequence is too long for the free cells and empty
  columns currently available finds no destination at all, exactly as an illegal move does.
- **A free-cell card taps to a provably safe foundation, else the nearest legal tableau column,
  else an unsafe foundation.** The same order a Klondike waste tap uses.

Unsafe foundation moves come before the free-cell fallback, not after, on purpose: irreversibility
is what "last resort" means here, not order of appearance, and parking is exactly as much a last
resort as an unsafe bank is — both are what a tap reaches only once every other option is gone,
and unsafe was already the established last resort before free-cell parking existed. Also later
than in Klondike, whose tableau tap banks a card safe or not: foundations here are a one-way trip,
and irreversibility does not make a move safe — it makes a premature one more costly, because the
banked card may still be needed to build on and can never come back. A tap still never refuses a
legal move; it only reaches an unsafe foundation, or a free cell, when nothing else is left. Drag
banks a card, or parks one, deliberately whenever the player wants either.
- **Drag** chooses a specific destination, including a shorter or longer supermove than tap would
  pick, up to the legal maximum for that destination.
- **No stock tap.** There is nothing to draw; the interface has no stock pile to render at all.

### Hint

FreeCell ships a solver (`DEALS.md`) to certify its catalog, so Hint follows Klondike's shape
rather than Spider's plain "what can move" highlight: a bounded on-device search reports a proven
guided move, a proof of no solution, or an inconclusive result, replayed through the canonical
rules before being shown, exactly as `docs/games/klondike/DESIGN.md` "On-Device Hint Search"
describes for Klondike. The search looks for a short line first — a weighted best-first search
(`BestFirstSolver`) that applies the same safe automatic cascade the game does after every move —
and falls back to the DFS the catalog was certified with only on the budget that leaves. Both
budgets were tuned against the catalog rather than guessed: `EXECUTION_PLAN.md`'s F6 package
measured them, the way Klondike's own budget was tuned against its catalog.

The DFS alone was the wrong line to follow. It keeps the first win it reaches, which on the
catalog's deals runs from several hundred to several thousand moves where a person wins in about
ninety; followed hint by hint, that read as a loop — runs shuttled between the same columns for
dozens of taps, Kings parked in free cells — even though no board ever exactly repeated. The
best-first line averages about ninety moves. A line from the DFS fallback is shown one move at a
time and never cached, so the next request tries for a short line again. What is fixed regardless
of any future retuning: a reported line is always replay-verified, "no solution" is always a
proof, and "inconclusive" never claims to be either.

`HintEngine` is stateful, mirroring Klondike's own: a certificate cache carries the last winning
line found across calls within the same game, so a player following hints one after another walks
one continuous plan instead of getting a fresh, possibly-conflicting one every time — the next
request reads its move straight off the cached line, with no search at all, for as long as the
live board keeps matching it. Only once the player deviates (a move of their own that the line
didn't call for) does the cache miss and a fresh search run.

A bare independent search has a real failure mode, and one Klondike hit once too: nothing stops a
fresh search from finding, as its very first move, the exact reversal of the move that just
produced the board it is searching from, since that search has no notion of "previous" at its own
starting point. Followed hint after hint, that reads as a card being shuffled back and forth
between two spots forever instead of leading anywhere. Two things close this, matching Klondike's
own two-layer fix in spirit: the certificate cache's own dedup drops any round trip a *found* line
makes back to a board it already stood on (so a cached line never contains the bug), and the real
last move played is still seeded into the search's own exact-inverse penalty at its root
(`HintEngine.hint`'s `lastMove`, `Solver.kt`'s `rootPreviousMove`) for the *first* search after a
cache miss, so a fresh search doesn't open with undoing it either. What's narrower than Klondike's
own engine on purpose: no dead-state cache, since a fresh board resolves in tens of milliseconds
without one (`HintEngine`'s own doc).

A fresh deal now primes that certificate cache from `assets/solutions.bin` before the player asks
for anything, mirroring Klondike's own `solutions.bin`/`SolutionCatalog`/`primeWithKnownSolution`
(`DEALS.md` "Shipped Solutions"): `FreeCellViewModel` looks up the new seed's stored line and hands
it to `HintEngine.primeWithKnownSolution`, which replays it through the real reducer before
trusting it, exactly as a searched line is trusted. The first Hint request of a game then resolves
from that cache with no search at all, for as long as the player keeps following it; the moment
they deviate, the cache misses and the ordinary best-first-then-DFS search above takes over.

## Scoring and statistics

Moves and elapsed time, as the other two games. No score: a card count or a points formula
rewards nothing FreeCell asks the player to optimize for. A supermove counts as one move, exactly
as a Klondike sequence move or a Spider row deal does. Statistics are the platform's shape — games
played, won, best time, best moves, streaks — kept as one pool: FreeCell has no difficulty axis or
suit-count axis to split them by (`RULES.md` "Free cells and difficulty"). Hints taken are tracked per game and summed across the window, counting only a solver-guided hint, never the *show every legal move* highlight. The statistics screen compares each win's moves with the certified solution shipped for its deal, as Klondike's does (`docs/games/klondike/DESIGN.md` "Statistics"). The sections themselves are `core:ui`'s, shared with the other games.

## Architecture

Three modules, as `docs/ARCHITECTURE.md` "Adding a game" prescribes for a certified solitaire:

- `games/freecell/rules` — the layout, the deal, the legal-move set including supermove
  expansion, and the one reducer. Kotlin/JVM, no Android dependency.
- `games/freecell/solver` — certifies deals for the catalog and powers Hint, mirroring
  `games/klondike/solver`'s role for both jobs.
- `games/freecell/app` — the Android application.

`FreeCellState` is its own type. It shares the tableau's descending-alternating-color packing
predicate with Klondike in substance (`canBuild`), and the implementation settled it as two
copies rather than shared code, pinned together by `BuildAgreesWithKlondikeTest`
(`docs/ARCHITECTURE.md` "What moved once a second game existed, and what stayed"). Free cells,
the all-face-up deal, and supermove expansion have no Klondike or Spider equivalent and stay
FreeCell's own.

## Interface

`UI_SPEC.md` holds the exact arrangement. In outline: a top row of four free cells and four
foundations, the eight tableau columns below, actions in a bottom bar in portrait and split rails
in landscape — closer to Klondike's twin-orientation board than to Spider's single adaptive
layout, because FreeCell (like Klondike, unlike Spider) has a top row of small fixed piles that
portrait and landscape place differently.

## Sound, Localization, Accessibility, Performance, Quality

Mechanism, gating, and the shared bar: `docs/PLATFORM.md` "Sound", "Localization",
"Accessibility", "Performance", and "Quality". FreeCell ships only its own strings; the rest merge
in from `core/ui`.

## Supporting Specifications

- [RULES.md](RULES.md): the deal, free cells, building, supermove, foundations, and session
  lifecycle
- [UI_SPEC.md](UI_SPEC.md): layouts, input, geometry, accessibility
- [DEALS.md](DEALS.md): catalog generation, format, and certification
- [EXECUTION_PLAN.md](EXECUTION_PLAN.md): work packages and gates
- [TODO.md](TODO.md): deferred, out of first-release scope
