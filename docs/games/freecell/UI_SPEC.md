# UI and Interaction Specification

## Principles

The interface is fast, calm, readable, and defaults to a right-handed layout, with a left-handed
mirror available as a setting, following Klondike's own convention
(`docs/games/klondike/UI_SPEC.md` "Right-Handed Layout" / "Left-Handed Layout") rather than
Spider's action-bar-only mirror — FreeCell's top row of free cells and foundations is exactly the
kind of small fixed-pile row Klondike mirrors as a whole, unlike Spider's single full-width stock.
The **board area** renders with the same Compose Canvas primitives Klondike's and Spider's boards
use. Pile and card vocabulary follows `docs/solitaire/GLOSSARY.md`.

## Screens and States

- **Game:** board, moves, timer, and actions
- **Loading:** a centered spinner while the active-game store is checked; no animated splash
- **Settings:** automatic moves, enable animation, hint mode, handedness, sound, theme, language — no draw
  mode, no difficulty, no suit count; FreeCell has none of them
  (`RULES.md` "What FreeCell does not have")
- **Statistics:** one pool (no per-mode split), Week/Month/All Time tabs, summary, distributions,
  sample size, reset
- **Help:** goal, free cells, building, supermove, and controls, in one scroll — FreeCell has no
  difficulty tiers to give a page of their own to, the same reasoning Spider's single-scroll Help
  gives
- **Confirmation:** new game, replay, and statistics reset
- **Win:** the shared win dialog — moves, elapsed time, personal bests
- **No moves:** notice offering undo and new game when the game is stuck
- **Recovery:** unobtrusive notice after discarding a corrupt saved game; the game stays playable
- **Unrecoverable:** non-playable error when the bundled catalog fails verification, mirroring
  Klondike's own — FreeCell ships a certified catalog from the start, unlike Spider's silent
  uncertified fallback (`DEALS.md`)

Modal screens pause the timer. Android back closes the current modal before leaving the game.

## Board Geometry

- Eight equal tableau columns.
- Card width-to-height ratio of 5:7, the same as Klondike and Spider.
- Keep at least 4 dp between tableau columns.
- Keep card width at least 32 dp and the exposed band at least 22 dp. Set the same way Spider's
  own were: below what eight columns naturally fit to at 320 dp with the gap and margin fractions
  here, so the floor does not actually override the fit at gate widths. `FreeCellGeometryGateTest`
  (`EXECUTION_PLAN.md`'s F2 gate) confirms both numbers at 320 dp and 360 dp, including on a
  column at the 19-card ceiling below — they are no longer provisional.
- **There is only one overlap step per column.** Klondike and Spider both give a smaller step to
  the face-down portion of a column; FreeCell has no face-down cards anywhere, so every card in a
  column uses the same exposed-band overlap.
- Scale cards to fit the eight-column width first, then the tallest column **currently on the
  board**. A fresh deal's deepest column is seven cards, but building deepens columns: seven dealt
  cards ending in a King, with Queen to Ace built on it, make nineteen. Reserving for nineteen
  permanently would pin every game to the smallest cards, so the fit tracks the current tallest
  column and the overlap shrinks as a column crosses each threshold, the same approach as
  `docs/games/klondike/UI_SPEC.md` "Board Geometry". Every card stays whole and unclipped at every
  legal depth; `FreeCellGeometryGateTest` asserts this on deep-column fixtures up to nineteen.
- Respect status, navigation, display-cutout, and gesture insets.
- Cancel an active drag safely on rotation while preserving game state.

### Status Row

One line in landscape, two in portrait: the title at the leading edge, then the hand number and
the moves and timer, centered in the space the title leaves — the same shape as Klondike's own
status row, minus the difficulty span (FreeCell has nothing to put there). The number is the one link: FreeCell has no difficulty or suit-count level to pick, so it opens the deal picker alone. The deal number is a link to the **deal picker** (`core:ui`'s `DealPickerDialog`): a list of the certified catalog's deals, each marked *not played*, *played* or *won*, Each row also states its moves: the **fewest moves of any win** for a won deal, and the **moves of the game last played** for one not won yet (`DealProgress`); a deal whose moves are unknown shows none. It opens scrolled to the deal on screen, with a filter by those states. Picking one starts it, asking first, as New Game does, when the game on screen has been played and is unfinished — and recording the same loss. Picking the deal already on screen does nothing. *Played* is a first move, not a result; *won* is never undone by playing the deal again. Progress is kept per seed (`core:storage`'s `DealProgressStore`), cleared by Reset Statistics, and does not reach back before it was introduced: games finished earlier are not marked. The hand number is the active seed's stable position in the certified catalog
(`FreeCellViewModel.dealNumber`, `DEALS.md` "App integration"), the same shape as Klondike's and
Spider's own numbered deals.

- **Portrait:** Help and Statistics are icons in the status row's trailing corner, Help first.
- **Landscape:** neither is in the status row; both move to the top of the rail nearest the
  holding hand (below).

### Portrait

- Status row at the top, with Help and Statistics as icons in its trailing corner.
- **Free cells and foundations share the top board row**: four free-cell slots on the side
  Right-Handed Layout gives the tableau's temporary storage, four foundation slots on the other
  side — the same "small fixed piles at the top" placement Klondike gives its own foundations,
  doubled because FreeCell has two such groups instead of one.
- Tableau below, taking the remaining height.
- The FinitePlay wordmark is background, drawn low in the board behind the tableau, exactly as
  Klondike's is.
- Five actions in a bottom bar: Undo, Hint, New, Replay, Settings — the same set and order
  Klondike uses, each an equal weighted share of the row with a two-line label reservation.

### Landscape

- The status row runs the full width above the board.
- Below it the board is flanked by two rails, one action button wide, split the same way
  Klondike's are: the rail nearest the holding hand carries Statistics and Help at its top, Hint
  above Undo at its bottom; the opposite rail carries Settings, Replay, and New.
- **Free cells and foundations form two vertical strips at opposite edges of the board area**,
  mirroring Klondike's single foundations strip doubled: free cells on the tableau's near edge
  (the side Right-Handed Layout gives temporary storage), foundations on the far edge.
- The tableau's eight columns take the remaining width, each centered in an equal-width slot the
  same way Klondike's landscape columns are.
- The FinitePlay wordmark appears as a semi-transparent watermark along the bottom edge, exactly
  as Klondike's does.

## Right-Handed and Left-Handed Layout

Follows Klondike's own convention exactly (`docs/games/klondike/UI_SPEC.md` "Right-Handed
Layout" / "Left-Handed Layout"): Right-Handed is the default, free cells sit on the edge nearest
the holding hand in both orientations, foundations on the opposite side, and Undo/Hint sit closest
to that same edge. Left-Handed mirrors the whole board horizontally, applied immediately and
persisted; tableau column indexes, foundation suit order, and every game rule are unaffected by
handedness, exactly as Klondike states.

## Input

### Tap

- Face-up tableau card: move it and its legal sequence using the priority in `DESIGN.md`
  "Interaction" — nearest legal tableau column to the right, then a provably safe foundation, then
  nearest legal tableau column to the left, then an unsafe foundation, then — for a single card
  only — an empty free cell, both equally last-resort. A tap whose sequence exceeds the supermove
  maximum for every candidate destination finds nothing to do.
- Free-cell card: a provably safe foundation, else the nearest legal tableau column, else an
  unsafe foundation (`DESIGN.md` "Interaction").
- Foundation top: no action — a card never leaves a foundation (`RULES.md`).
- Invalid tap: brief outline or shake; no score.

### Drag

- Begin after crossing the platform touch-slop threshold.
- Lift the selected card, run, or free-cell card above the board.
- Highlight legal destination slots — including every empty free cell and every legal tableau
  column, up to the supermove maximum a lifted run can actually reach — without changing state.
- Commit on a legal destination; an invalid drop returns the lifted cards to their pile at once,
  with a brief invalid highlight on the source, exactly as Klondike's own drag does.
- Count the entire transfer, however many cards, as one move.

### Hint

A Settings toggle ("Intelligent hint", on by default) picks which of two modes the
Hint action is in. A game with no player move behind it follows the deal's certified path whatever this setting says — the line is shipped, so showing it costs no search — and the setting decides again from the first move; a deal that ships no line (and so never reaches this rule) behaves as the setting says. Off, Hint highlights every legal move at once and has no search behind it at
all — tapping Hint again while highlights are showing hides them. Turning this setting on or off
does not change what a hint already on screen shows, only the next request. On (the default),
Hint runs the on-device solver as described below.

Runs the on-device solver search (`DESIGN.md` "Hint") and reports one of Klondike's three
outcomes — a guided move (pulse the cards involved, twice, at their current position), a
no-solution notice, or an inconclusive notice — using the same presentation Klondike's
`UI_SPEC.md` "Hint" describes. Unlike Klondike, Inconclusive carries no suggested-move fallback:
FreeCell has no difficulty-ruleset system to draw one from, and `EXECUTION_PLAN.md`'s F6 package
found the outcome rare enough (every certified deal resolves to Guidance from its fresh board)
that building one solely for this case was not worth it.

## Motion

Follows Klondike's own conventions (`docs/games/klondike/UI_SPEC.md` "Motion") without change:
constant-speed card flights bounded to 80–450 ms at 1.6 dp/ms, the source pile hiding its
departing cards for the flight's duration, automatic moves playing one at a time and never
merged, a skippable win animation of at most three seconds, and turning Enable Animation off removing every
slide and pulse while keeping automatic transfers as discrete, individually visible steps. A
supermove's cards fly together as one rigid stack along the same path, the same way Spider's
tableau-to-tableau moves already do (`docs/games/spider/UI_SPEC.md` "Motion").

## Themes, Accessibility, and Localization

Scope, palettes, contrast floors, font-scaling rules, and the decision that screen-reader support
is out of scope: `docs/PLATFORM.md` "Themes" and "Accessibility". FreeCell adds nothing of its
own here. Its own strings are translated into the same locales the platform ships
(`docs/languages.txt`), checked by `LocaleStringsCompletenessTest`, machine-generated and not yet
native-reviewed — the same caveat Klondike's and Spider's own carry.

## Dialog Behavior

- New and Replay show the current moves and elapsed time before confirmation, required only for
  an unfinished game with at least one player action.
- Settings apply immediately.
- Statistics reset requires explicit confirmation.

## Win Presentation

Stop the timer when all 52 cards reach the foundations, or when the automatic finish begins,
whichever comes first. Show completion time, move count, relevant personal bests, and New
Game/Replay actions. Record the win exactly once, even after rotation, restoration, or repeated
presentation.

The win is announced as well as shown: a large *You win!* banner and confetti over the dialog, the
banner staying until the dialog is closed (`docs/PLATFORM.md` "Win Celebration").
