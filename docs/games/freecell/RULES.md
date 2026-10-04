# FreeCell — Rules

Publisher: [FinitePlay LLC](https://finiteplay.org)

The rules the engine implements. `docs/solitaire/GLOSSARY.md` governs the vocabulary;
`docs/PLATFORM.md` governs everything not specific to this game. Where this file and the code
disagree, this file is right and the code is the bug.

## The layout

One standard 52-card deck, dealt entirely face up into:

- **Tableau** — eight columns. The four leftmost hold seven cards, the four others hold six: 52
  cards in all, dealt in round-robin order (shuffled index 0 to column 0, index 1 to column 1, ...
  wrapping after column 7) until the deck is exhausted. Every card is face up from the moment it
  is dealt; there is no face-down state anywhere in this game, ever.
- **Free cells** — four of them, each holding at most one card of any rank or suit. A free cell
  has no packing rule: whatever is placed there simply sits, alone, until moved again.
- **Foundations** — four, one per suit, built up from Ace to King.

FreeCell has **no stock and no waste**. Nothing is ever dealt after the opening deal, and there is
no drawn card waiting to be placed — the entire game is played from the tableau and the free
cells.

## Building

- A tableau card may be placed on a card **one rank higher and the opposite color** — the same
  predicate Klondike's tableau uses (`docs/solitaire/GLOSSARY.md` "Build"), applied here to a
  fully open board rather than one hiding most of itself.
- **Any card may enter an empty column** — there is no King-only rule. An empty column is a
  resource, not a landing pad reserved for one rank.
- A **free cell** accepts any single card when empty, with no relationship to what is already in
  another free cell or to what is on the tableau.
- A card is **movable alone** whenever it is the last card of its column, or whenever it sits
  alone in a free cell.
- A **sequence** — a descending, alternating-color run — moves as one only via a **supermove**
  (below); the engine has no notion of "drag the run" as a primitive distinct from a supermove of
  size equal to the run's own length.

## Supermove

A supermove is not a new capability: it is every legal outcome of moving a run's cards one at a
time through the free cells and empty tableau columns, offered as a single action so a player is
not made to reproduce it by hand. The engine must accept it exactly when, and only when, that
one-at-a-time sequence would succeed.

Let `free` be the number of currently empty free cells and `empty` be the number of currently
empty tableau columns other than the destination column itself. The maximum sequence length is

`(free + 1) × 2^empty`

for every destination, empty or not. An empty destination is not a waypoint, since the cards are
going there rather than through it — which is exactly why `empty` already excludes it, and why
nothing further is subtracted. With no free cells, one empty waypoint column, and a separate
empty destination, two cards move: the first to the waypoint, the second to the destination, the
first onto the second.

A supermove that exceeds the legal maximum for its destination is not a partial move truncated to
fit; it is illegal in full, exactly as an ordinary single-card move to an illegal destination is.
The engine computes the maximum from the board as it stands at the moment of the move, never from
a cached count — clearing a free cell mid-turn is not itself a move, so no board state exists
where the free-cell count differs from what the tableau shows.

**A supermove is one move**, whatever its length — the same accounting Klondike gives a sequence
move and Spider gives a row deal (`docs/games/klondike/RULES.md` "Scoring",
`docs/games/spider/RULES.md` "Invariants"). The free cells and empty columns it passes through
are never left occupied afterward; they are restored to empty by the same action that used them,
because the whole point of the calculation above is that the intermediate states are only ever
passed through, never rested in.

## Foundations

- Foundations build up by suit, Ace through King.
- A card may move to its foundation from the tableau or from a free cell whenever its rank is
  exactly one above the foundation's current top (or it is an Ace and the foundation is empty).
- **A card never leaves a foundation.** There is no foundation withdrawal and no parked-card rule
  in this game — see `DESIGN.md` "What FreeCell is not" for why.

## Automatic Foundation Moves

Enabled by default, running after each successful player action, never before the player's first
one — the same platform-wide rule Klondike states in full
(`docs/games/klondike/RULES.md` "Automatic Foundation Moves"), adapted with nothing to park:

- Move any accessible Ace or Two automatically.
- Move a higher rank automatically once it is *provably safe*: every foundation already at the
  rank beneath it — the exact predicate Klondike's `isSafeFoundationMove` implements (used there
  for tap, hints, and automation alike), not the weaker opposite-color-pair rule
  `docs/games/klondike/DIFFICULTY_LEVELS.md` calls "the classic rule" for its own Medium-tier
  strategy grading. That weaker rule describes a cautious *player*'s choice and is deliberately
  looser than what the code actually automates; this section is about automation, so it takes
  the predicate the code uses for automation.
- Cascade until no further safe move exists. When several are eligible, check free cells first,
  then tableau columns left to right — free cells hold nothing the player is mid-arrangement
  with, so clearing them first never interferes with a plan the way reordering tableau columns
  might.

The deal itself never depends on this setting: the dealt board always matches the seed alone.

## Automatic Finish

Once a full foundation sweep from the current board is *provably* achievable — every remaining
card reaches its foundation through some order of moves using only foundation plays, free-cell
placements, and tableau-to-foundation plays, verified by simulation before it starts — the game
finishes itself, exactly as Klondike's automatic finish does
(`docs/games/klondike/RULES.md` "Automatic Finish"), adapted to a game with no face-down cards to
wait out:

- Check the condition after every committed transaction, never before the player's first action.
- The sweep is one terminal transaction, stops the timer the instant it begins, and counts each
  transfer as one move.
- Undo is unavailable once the win is recorded.
- The automatic-moves setting gates the finish, exactly as it gates ongoing automatic foundation
  moves; with it off, the player finishes by hand.

The finish is a transition the rules engine offers and only the game screen invokes — headless
replay, catalog validation, and the solver never invoke it, so solution certificates stay valid.

## Winning and Losing

- The game is **won** when all 52 cards reach the foundations.
- The game is **stuck** when no legal move exists at all: no tableau build, no free-cell
  placement, no supermove, and no foundation play. Being stuck is reported as state, not declared
  a loss on its own — undo can still recover it, the same reasoning Klondike and Spider both
  give.

Not every FreeCell deal is winnable, though the overwhelming majority are (`DEALS.md`); the rules
make no claim either way about a given deal. A winnable deal can still be played into a stuck
position.

## Game Lifecycle

- A game **starts** on its first successful player move. Hints, invalid actions, and settings
  changes do not start it.
- The **timer** runs exactly while the game has started, the app is in the foreground, no modal
  screen is open, and the game is not won. Only elapsed time is persisted; a restored game resumes
  under the same rule, with no separate wait-for-action exception.
- A player move and the automatic foundation moves that follow it form **one undo transaction**.
  Undo restores the complete transaction and does not immediately rerun automation.
- **Undo is unlimited**, and unavailable once the win is recorded.
- **New Game** deals the next seed from the catalog (`DEALS.md`). **Replay** restores the
  identical deal and rules version without advancing traversal. Both reset the move count and the
  timer.
- New Game and Replay ask for **confirmation** only when the game in play is unfinished and has at
  least one successful player move. No confirmation is needed after a win or on an untouched deal.
- Replacing an unfinished, started game with New Game records a loss. A game started by Replay
  contributes nothing to statistics, however it ends, and replacing it records nothing — the same
  rule, and the same reason, as `docs/games/klondike/DESIGN.md` "Statistics".
- When the game is stuck (above), show a notice offering Undo and New Game.

## Scoring

The score is the move count; lower is better, and elapsed time breaks ties. The interface labels
it `Moves`.

- A tap or drag counts one move, whatever it carries — a supermove is one move.
- Each automatic foundation transfer counts one move, including each transfer of the automatic
  finish.
- Undo restores the board but not the moves already counted, then adds one. Undoing a player move
  followed by two automatic transfers takes the count from 3 to 4.
- Hints, invalid actions, and settings changes count nothing.

## Moves

The complete move set:

| Move | Meaning |
|---|---|
| `TableauToTableau(fromColumn, fromIndex, toColumn)` | Move the sequence starting at `fromIndex` (a supermove when longer than one card) onto `toColumn` |
| `TableauToFreeCell(fromColumn, cell)` | Move the top card of `fromColumn` into empty free cell `cell` |
| `TableauToFoundation(fromColumn)` | Move the top card of `fromColumn` to its foundation |
| `FreeCellToTableau(cell, toColumn)` | Move the card in free cell `cell` onto `toColumn` |
| `FreeCellToFoundation(cell)` | Move the card in free cell `cell` to its foundation |

Five entries, against Klondike's seven and Spider's two — there is no draw, no recycle, and no
foundation withdrawal to add one for.

## Invariants

These hold after every committed transition, and the reducer is the only thing that may produce
a state at all:

- The state is immutable; every transition returns a new one.
- 52 cards exist at all times, counting tableau, free cells, and foundations. No card is created,
  destroyed, or duplicated.
- Every card, everywhere, is face up. There is no face-down state in this game's model at all.
- Each free cell holds zero or one card.
- A supermove's intermediate free-cell and empty-column occupancy never persists past the
  transaction that computed it.
- The move count increases by exactly one per committed player move, however many cards a
  supermove carries.

## What FreeCell does not have

Named because a reader coming from Klondike or Spider will look for them: no stock, no waste, no
draw, no recycle, no face-down cards, no King-only empty-column rule, no foundation withdrawal, no
parked card, and no difficulty or suit-count setting (below). The draw-mode and suit-count
settings do not apply and are not shown.

## Free cells and difficulty

The number of free cells is fixed at four and is not a difficulty setting in this release —
unlike Spider's suit count, four free cells is what "FreeCell" traditionally means, and a version
with fewer is a harder variant rather than the base game (`TODO.md`).
