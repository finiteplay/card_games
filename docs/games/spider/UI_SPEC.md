# UI and Interaction Specification

## Principles

The interface is fast, calm, and readable. The **board area** — the Compose-drawn surface the
piles sit on — uses the same Canvas primitives Klondike's board does (`core/ui`'s card drawing).
Pile and card vocabulary follows `docs/solitaire/GLOSSARY.md`. Where this document is silent,
`docs/PLATFORM.md` governs — themes, localization, sound, accessibility, and persistence are
platform-wide and are not restated here.

## Screens and States

- **Game:** board, moves, timer, and actions
- **Loading:** a centered spinner while the active-game store is checked; no animated splash
- **Settings:** suit count (next New Game only), enable animation, hint mode, handedness, sound,
  theme, language — no draw mode and no difficulty setting, since Spider has neither
  (`RULES.md` "What Spider does not have")
- **Statistics:** one tab per suit count, period tabs within each, wins/losses/streaks, and reset
- **Help:** goal, moving cards, the stock, suit counts, and controls, in one scroll — not tabbed
  pages the way Klondike's Rules/Strategy/Levels are, since Spider has no difficulty tiers to
  give a page of their own to
- **Confirmation:** discarding the game in play, for New Game and Replay (the shared
  `DiscardGameDialog`, `core/ui`)
- **Win:** the shared win dialog (`core/ui`'s `WinDialog`) — moves, elapsed time, personal bests
- **Recovery notice:** an unobtrusive banner after discarding a corrupt saved game; the game
  stays playable

Unlike Klondike, there is **no Unrecoverable screen**. A missing or invalid certified catalog
degrades silently to the uncertified formula for every suit count (`SpiderCertifiedDealCatalog`,
`docs/games/spider/DEALS.md`) rather than blocking play — an uncertified Spider is a shippable
product on its own terms (`EXECUTION_PLAN.md` "Deliberate order"), so a catalog failure costs
certification, not the ability to play.

Modal screens pause the timer. Android back closes the current modal before leaving the game.

## Board Geometry

Spider uses **one adaptive layout for both orientations**, not the separate portrait/landscape
arrangements Klondike's board draws — the stock moves from above the columns (portrait) to
beside them (landscape, `stockAtSide`), and the rest of the board's shape does not otherwise
change between the two (`SpiderBoard.kt`).

- Ten equal tableau columns.
- Card width-to-height ratio of 5:7, the same as Klondike's.
- Keep at least 4 dp between tableau columns.
- Keep card width at least 26 dp, the exposed face-up band at least 19 dp, and the exposed
  face-down band at least 9 dp (a 4 dp floor below that on a column deep enough to need it).
  Cards are exempt from the 48 dp control minimum; these numbers replace it.
- Scale cards to fit the ten-column width first, then the tallest column *the board is currently
  planning for* — twelve cards, not the nineteen the rules allow at their absolute deepest, so an
  ordinary game keeps large cards through its opening moves instead of sizing for a depth most
  games never reach and then never using the room (`SpiderBoard.kt`'s `PLANNED_COLUMN_DEPTH`).
- Face-down overlap may be smaller than face-up overlap, and the step onto a column's newly
  revealed first face-up card uses the face-down band — only a step strictly between two face-up
  cards gets the larger one. Both floors are Klondike's own reasoning, reused as-is.

**`minCardWidth` is Spider's own, the rest are still provisional.** Klondike's inherited 40 dp
floor assumes seven columns; ten of them at that floor do not fit a 320-360 dp phone at all (436-
449 dp needed against 320-360 dp available) — `SpiderGeometryGateTest` caught this while writing
this gate. 26 dp is a small margin below the ~27.5 dp ten columns fit to naturally at 320 dp with
the gap and margin fractions below, so the floor never actually overrides the fit at the gate
widths. `minFaceUpBand` (before it was tightened for landscape, below) and the two step-fraction
ceilings remain Klondike's own numbers (`SpiderGeometry.kt`), not yet the product of a design pass
of Spider's own — this board has no separate landscape lane split, and legibility at a ~26 dp
card has not been measured the way Klondike's `PortraitLayoutFitsTest` measures its own worst
case. Two numbers *are* otherwise Spider's own: the face-up and face-down bands were tightened
from Klondike's 24 dp/10 dp to 19 dp/9 dp specifically to buy back vertical space in landscape,
where ten columns and a full-height lane leave the least height to spare of any layout either
game draws.

`SpiderGeometryGateTest` asserts the numbers above at 320 dp and 360 dp, mirroring Klondike's own
`CardGeometryTest` — see "Gate" in `EXECUTION_PLAN.md` S3a. It does not yet assert the full board
layout (stock placement, tableau lane sizing) the way Klondike's `PortraitLayoutFitsTest` does for
its own board — only the tableau-column fitting math `:solitaire:ui` shares with Klondike.

### Status Row

One line in landscape, two in portrait: the title at the leading edge, then the suit count and
deal number and the moves/timer, centered in the space the title leaves
(`docs/games/spider/DEALS.md` "Deal numbering and traversal" for what the deal number means), with
a trailing group that differs by orientation.

- The title is the product name — one line, untranslated, exactly as Klondike's is.
- Portrait reserves two lines — the suit count and deal number on one, the moves/timer on the
  next — however short the translation; landscape reserves one and runs them side by side, since
  it has no height to spare (`GameChrome.kt`'s `singleLine`). The reservation is on the box, not
  the text, so the row never grows or shrinks between the won state (shorter) and the normal one.
- **The trailing group is Help then Statistics in portrait** — both read rather than act, and
  Help is what a new player needs first — **and the banked counters in landscape**, since
  landscape has no spare row for them and Help/Statistics move to the landscape rails instead.
  Portrait never shows banked counters in the status row; they are drawn on the board itself,
  beside the stock.
- The suit count is a link, the same shape as Klondike's own level
  (`docs/games/klondike/UI_SPEC.md` "Status Row"): tapping it opens a picker that deals a new game
  at the chosen count immediately (`SpiderViewModel.setSuitCount`), warning first when the current
  deal has moves that would be forfeited. The deal number beside it is its own link, to the deal picker. The span takes the same three-stop accent Klondike's difficulty label uses
  (`GameChrome.kt`'s `SuitCount.accent`) — one calmest, four warmest — so a glance at the color
  alone hints at which count is in play, though the count is always spelled out beside it too.

The deal number is a link to the **deal picker** (`core:ui`'s `DealPickerDialog`): a list of the active suit count's deals — the whole certified catalog (the first 500 of the endless formula sequence only in a build with no catalog loaded, which is tests), each marked *not played*, *played* or *won*, opening scrolled to the deal on screen, with a filter by those states. Picking one starts it, asking first, as New Game does, when the game on screen has been played and is unfinished — and recording the same loss. Picking the deal already on screen does nothing. *Played* is a first move, not a result; *won* is never undone by playing the deal again. Progress is kept per seed (`core:storage`'s `DealProgressStore`), cleared by Reset Statistics, and does not reach back before it was introduced: games finished earlier are not marked.

### Layout

- The stock sits above the tableau in portrait, in a row with the banked counters
  (`SpiderBoard.kt`); beside the tableau's full-height lane in landscape, top-aligned rather than
  centered, since it reads as one more pile rather than something floating unrelated to them.
- The FinitePlay wordmark is background, drawn low on the cloth behind the board, exactly as
  Klondike's is (`docs/games/klondike/UI_SPEC.md` "Portrait").
- Portrait: five actions in a bottom bar — Settings, Replay, New, Hint, Undo — each an equal
  weighted share of the row.
- Landscape: two single-file rails flank the board. The rail nearest the holding hand carries
  Help and Statistics at its top, Hint above Undo at its bottom; the opposite rail carries
  Settings, Replay, and New, centered.

### Handedness

Handedness mirrors **the action bar only** — `mirrored` on `ActionBar`/`ActionRail`, the same
setting Klondike's own handedness toggle drives. Unlike Klondike, **the board itself does not
mirror**: the stock, the tableau, and the banked counters keep the same side regardless of the
setting. This is a real difference from Klondike's own Right-/Left-Handed Layout, which mirrors
the whole board (`docs/games/klondike/UI_SPEC.md` "Left-Handed Layout") — not yet reconciled,
and not this package's gate to close.

## Input

### Tap

- Stock: deal a row onto every column at once, or refuse with a snackbar while any column is
  empty (`RULES.md` "The stock"; `DESIGN.md` "Interaction").
- Face-up tableau card: move the tapped card and everything below it in its column to the
  nearest legal column to the right, or the leftmost legal column when there is none to the
  right (`resolveTap`). A card only moves if it and everything below it is itself a legal,
  liftable sequence (`isMovableSequence`); otherwise the tap has no destination at all.
- Face-down card: no action unless it is the exposed top card of an emptied lift, which flips
  automatically the moment it is exposed.
- Invalid tap: no legal destination exists for the tapped card; nothing moves, no score, but the
  column briefly flashes and the invalid sound plays, exactly as Klondike's and FreeCell's own
  boards do for a tap they cannot resolve either.

### Drag

- Begin after crossing the platform touch-slop threshold; tap and drag are disambiguated by
  distance in sibling gesture detectors, never by a long-press hold (`SpiderBoard.kt`'s own
  doc comment records that a hold-first draft made an ordinary drag register as a tap).
- Lift the dragged card and everything below it in its column above the board.
- Drop on any column whose top-left frame contains the release point; an invalid drop returns
  the lifted cards to their column at once, with the same brief flash and sound an invalid tap
  gets.
- Count the entire sequence transfer as one move.

### Hint

A Settings toggle ("Hint shows the winning move", on by default) picks which of two modes Hint is
in. The guided mode below is offered at one suit unconditionally and at two suits only for a
certified deal (`SpiderViewModel.dealIsCertified`) — see below for why.

**Off, or when the guided mode is not offered:** Hint highlights **the top card of every liftable
sub-stack that has a legal destination** (`hintedCards`, `Hints.kt`) — a pulse on each, not a
single guided step, since Spider's board can have many simultaneously legal moves and no one of
them is "the" hint the way a proven winning line's next step is. Deliberately not marked: every
card of a marked sub-stack (only its top card lights, once) and destinations themselves (a run
with two legal columns has no single answer, and tapping already walks through them). This mode
has no "no solution" or "inconclusive" outcome, since it answers "what can move" rather than "can
this be won." Tapping Hint again while it is showing hides it.

**On, where the guided mode is offered:** Hint shows the next step of a line proven to win, exactly
the source card pulsing (never a destination) that Klondike's own guided Hint does
(`docs/games/klondike/UI_SPEC.md` "Hint") — either instantly, from a cached line, or after a brief
loading notice while the on-device solver runs, then either the guided pulse or a dismissible No
solution / Inconclusive notice, auto-dismissing after 4 s if left alone. A certified deal (every
deal `New Game` deals in production) resolves instantly: the certified catalog
ships its own proven winning line on-device, and the hint engine is primed with it before the
player's first move (`docs/games/spider/DEALS.md` "Solutions"), so there is nothing to search for
until the player deviates from it. A deviation — or an uncertified one-suit deal, which only a test
fixture produces — pays a live search of the *whole remaining game*, not one step at a time the way
Klondike's and FreeCell's solvers work; at one and two suits it usually answers in well under a
second and wins most boards a player reaches, and a longer Hint timeout wins more of the hard ones
(`docs/games/spider/DESIGN.md` "Hint" has the measurements). A deal that is not from the catalog,
which only tests produce, stays on the plain highlight at two and four suits (an open product
decision at two). At four suits no search here wins a board within an interactive budget, so a
hint after leaving the shipped line ends in the Inconclusive notice. Repeating the request while
one is already showing has nothing new to advance to.

## Motion

- Card moves and the pulse animation follow the same easing and duration conventions as
  Klondike's (`docs/games/klondike/UI_SPEC.md` "Motion"); Spider adds nothing of its own here.
- **An ordinary tableau move — tap or drag — flies its whole run in a straight line from source to
  destination as one rigid stack**, at Klondike's own constant speed (`docs/games/klondike/UI_SPEC.md`
  "Motion" — 1.6 dp/ms, bounded to 80–450 ms), rather than jumping the board straight to the
  post-move state. The source column keeps showing the departing run, and the destination
  withholds the arriving one, for the flight's whole duration — same reasoning as Klondike's own
  fix for this (`docs/games/klondike/UI_SPEC.md` "Motion"). A move that also completes a sequence
  skips this and goes straight to the bank flight below, rather than playing a redundant departure
  first.
- Turning Enable Animation off removes slides and pulses; the automatic finish (below) still plays
  one card at a time, just without a slide.
- **Banking a completed sequence flies its thirteen cards to that suit's banked counter**, one at
  a time, ace first and king last — the opposite of the order they were assembled in, so the run
  visibly unwinds from the top. Each card travels in a straight line at Klondike's own
  constant speed (`docs/games/klondike/UI_SPEC.md` "Motion" — 1.6 dp/ms, bounded to 80–450 ms),
  and disappears the instant it arrives rather than fading. The next card launches once the one
  before it is a quarter of the way through its own flight, so all thirteen read as one cascading
  motion rather than thirteen separate ones. This is a portrait-only effect: landscape's banked
  counters live in the status row, outside the board's own coordinate space, so a bank there
  resolves instantly, the same as before this existed.
- **A row deal flies its ten cards from the stock down to their columns**, one to each, staggered
  left to right the same way the bank flight staggers ace to king — the next card launches once
  the one before it is a quarter through its own flight. Whichever column(s) the deal also
  completes a sequence in are left out of this: their own bank flight already shows that card
  arriving and leaving, and a deal-in first would be a redundant extra step ahead of it.

## Automatic Finish

When every remaining card can reach its bank without exposing a choice, an automatic finish
sweeps them there — `docs/games/spider/RULES.md` states the exact condition. The search that
decides this runs off the main thread (`Dispatchers.Default`), bounded well under a frame budget
even on an unsolvable deep endgame (`AutoFinishCostTest`), after the main-thread version of this
search was found to freeze the game for the better part of a second on a real device.

Each of its own moves commits and animates exactly as if the player had played it — the same move
flight or bank flight, at the same speed — one at a time, with a pause between them standing in
for the flight (`SpiderViewModel` has no visibility into the board's own screen geometry, so it
cannot compute that flight's real duration the way the board itself does). The board stays
non-interactive for the automatic finish's entire run, not just while one of its own flights is
actually in the air, so a fast tap cannot insert a move of its own into the middle of a sequence
the search computed against a board that move would have already changed. Skip Animations removes
the pause the same way it removes every other slide, so the sweep still resolves in one jump when
the player has asked not to watch it.

## Themes, Accessibility, and Localization

Scope, palettes, contrast floors, and font-scaling rules are `docs/PLATFORM.md` "Themes" and
"Accessibility" — platform-wide, and not restated here. Spider's own additions: suit glyphs on
the banked counters carry the suit through a dedicated dark-theme color token
(`blackSuitOnTable`/`redSuitOnTable`), tuned separately from the card face's own suit colors
because the card-face black reads as near-invisible on a dark table. Spider's own strings are
now translated into the same 30 locales Klondike ships, checked by `LocaleStringsCompletenessTest`
— unlike Klondike, Spider keeps its help text in the same `strings.xml` as everything else rather
than a separate `strings_help.xml`, since Help is one scroll (above) rather than tabbed pages.
These translations are machine-generated and not native-speaker reviewed, the same caveat
Klondike's own carry.

## Dialog Behavior

- New Game and Replay show a confirmation only when the game in play has a move already
  recorded and is not won (`needsConfirmation`) — the shared `DiscardGameDialog`.
- Settings apply immediately except suit count, which is fixed the moment a deal is made and so
  only takes effect on the next New Game.
- Statistics show one suit count's results at a time, never blended, with wins, losses, and
  games played kept separate.

## Win Presentation

Stop the timer when every card reaches its bank, or when the automatic finish begins, whichever
comes first. The shared win dialog (`core/ui`'s `WinDialog`) shows completion time, move count,
and personal bests for that suit count. A win is recorded exactly once, even after rotation or
repeated presentation of the dialog.

The board's own state turns won the instant the winning move commits — same as any other win — but
when that move was the automatic finish's own, the dialog itself waits for the finish to actually
finish playing out rather than appearing over a board still mid-flight.
