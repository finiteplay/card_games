# Texas Hold'em — Design

Publisher: [FinitePlay LLC](https://finiteplay.org)

What the game is, and the decisions behind it. `RULES.md` is the rules; `docs/PLATFORM.md` is
everything true of every FinitePlay game and is not restated here.

## Status

This is a design document. What exists today is `EXECUTION_PLAN.md`'s Status table, the only
place in Hold'em's docs kept current.

## The product

No-limit Texas Hold'em, offline, Android only, no networking or analytics — the platform
constraints, unchanged. One app, one game, the fifth in the family and the second that is not a
solitaire.

The player sits at a six-seat table against five computer opponents in a **sit-and-go
tournament**: everyone starts with the same stack, the blinds rise on a fixed schedule, a player
who loses every chip is out, and the tournament ends when one player holds them all
(`RULES.md` "The Tournament"). A tournament is the unit that is won or lost, the way a solitaire
deal is: finishing first is a win, and every other finish is a place.

Chips are play chips only. A tournament starts every player from the same stack and is over in
about an hour, so there is no persistent bankroll to grow, lose, or reset — the shape Blackjack
needed for a game whose round is a bet against the house is not needed here, where the stake is
only ever the tournament itself. No real-money purchase, cash-out, prize, or wagering of any kind,
consistent with `docs/PLATFORM.md`'s no-accounts/no-networking constraints and this repo's
noncommercial license. The store listing declares simulated gambling, as Blackjack's does.

A sit-and-go rather than a cash game for three reasons. It has a clear end and a clear result, which
statistics and the rest reminder can both work with. Rising blinds force the decisions that make the
game — a short stack must gamble — rather than letting a cautious player fold for an hour. And it
keeps opponents' chip stacks meaningful: a cash-game bot that rebuys forever is a bottomless
opponent the player cannot actually beat.

## What Hold'em is not

- **No certified deal, no catalog, no solver.** As with Blackjack (`docs/games/blackjack/DESIGN.md`
  "What Blackjack is not"), hidden cards are the game, and nothing offline solving does changes
  what a player can know. `solitaire/catalog` and `solitaire/ui` are skipped, there is no
  `DEALS.md`, and there is no `games/holdem/solver`. The opponents are not a solver either
  (`Opponents`): they play an estimate, not a proof.
- **No undo.** Every street reveals cards and every opponent's action reveals information; a player
  who could undo a call after seeing the turn would be playing a different game. The reasoning is
  Blackjack's, and so are its consequences: a hand is saved before any card it reveals is shown
  (`Persistence`).
- **No rigged deck.** The deck for a hand is fixed by its seed before the first card is dealt and is
  never reordered by anything that happens afterwards — not by the player's results, not by a
  difficulty, not to make a hand "exciting". Opponents get no stronger or weaker cards than the
  player; the only thing that differs between them is how they play.
- **No opponent sees a hidden card.** An opponent decides from exactly what a player at that seat
  would know: its own two cards, the board, the stacks, the pot, and every action so far. This is
  enforced by type (`Architecture`) and by test (`EXECUTION_PLAN.md` H7), not by convention.
- **No collusion.** Each opponent plays its own stack for itself. Opponents do not coordinate
  against the player, soft-play one another, or treat the player's seat specially.
- **No hand ranks by suit.** Suits never break a tie; a tie splits the pot (`RULES.md` "Hand
  Rankings").

## Interaction

As in Blackjack, nothing is dragged and the player never chooses where a card goes. Play is a
sequence of decisions offered through `core/ui`'s `BoardActionBar` when it is the player's turn,
with the opponents' turns played out between them.

- **Fold, Check, Call, Bet, Raise, All-in** — offered only when legal (`RULES.md` "Legal
  Actions"). An illegal action is never shown rather than shown and refused. Check and Call never
  appear together, and neither do Bet and Raise, so the bar holds at most four actions plus Hint.
- **The bet amount.** Bet and Raise open a sizing row above the bar: a stepper by the big blind
  (holding it repeats, as Blackjack's bet stepper does — `BoardAction.repeatOnHold`) and presets
  for a half pot, the pot, and all-in. The amount is clamped to the legal range, so a confirmed
  amount is always legal (`RULES.md` "Betting").
- **Hint** — on the player's turn (`Hint`).
- **Leave tournament** — from the menu, with a confirmation. Leaving forfeits: the player finishes
  in the place they would take if eliminated now (`RULES.md` "Leaving"). It is never a way to undo a
  hand, because a forfeit is recorded exactly as an elimination is.
- No **Undo** (`What Hold'em is not`).

Opponents act at a readable pace — a short pause before each action, longer for a large decision —
so the player can follow the hand. Skip Animations shortens the pause without making actions
simultaneous: each is still shown, one at a time. The pause is presentation only; it never changes
what an opponent decides.

### Hint

Hold'em's Hint is an estimate, and says so. On the player's turn it shows:

- the player's **equity** — the share of the pot their hand wins on average — against the hands the
  live opponents' actions so far make likely (`Opponents` "Ranges");
- the **pot odds** — what a call costs against what the pot would then hold;
- a **suggested action** with its amount: what the strongest opponent profile (`Opponents`
  "Profiles") would do in the player's seat, with the player's cards.

The hint uses only what the player can see. It never reads an opponent's hole cards or the rest of
the deck, so it can be wrong in exactly the way a good player can be wrong, and the interface never
calls it the right play. Its estimates use a fixed sample count with a fixed seed derived from the
hand, so asking twice at the same point gives the same answer.

A hint used is counted (`Scoring and statistics`) but does not mark a tournament as assisted: unlike a
solitaire hint that can walk a certified line to a win, it carries no guarantee to discount.

## Opponents

Five opponents per tournament, each a **profile** — a fixed set of parameters over one shared
policy — drawn for the tournament from its seed and shown at its seat by a name and an avatar. Names
come from a fixed list of plain first names, never a real person's, and are not translated.

### The policy

One policy, run per decision, from the opponent's view only (`What Hold'em is not`):

- **Preflop**, by starting-hand class (the 169 classes: a pair, or two ranks suited or offsuit),
  position, the action so far, and effective stack in big blinds. Opening, calling and re-raising
  ranges are committed tables. At an effective stack of 15 big blinds or less the tables are
  push-or-fold: all-in or fold, nothing between.
- **Postflop**, by the hand's equity against the opponents' estimated ranges (`Ranges`), the pot
  odds, the hand's draws, and the board's texture (paired, suited, connected). Strong hands bet for
  value, draws semi-bluff in proportion to their outs, weak hands check, fold to pressure, or bluff
  at a rate the profile sets.
- **Sizing** from a small fixed menu — a third of the pot, two thirds, the pot, all-in — chosen by
  hand strength and board texture, so a bet size alone does not give a hand away.
- **Mixed actions are seeded.** Where the policy mixes — bluff or check, call or raise — the choice
  is drawn from a random source seeded by the hand's seed, the seat and the action's index, so the
  same view always yields the same decision. That is what lets a test replay a tournament exactly
  and lets the gate in `EXECUTION_PLAN.md` H7 measure the field.

### Ranges

Each opponent keeps, for every other live seat, a weight over the 1,326 two-card combinations:
cards it can see are removed, and each observed action reweights every combination by how likely
the policy itself would be to take that action with it. Equity is a Monte Carlo estimate against
hands sampled from those weights, at a **fixed sample count**, never time-bounded — a deadline would
make the decision depend on the device, and the same view must always give the same answer.

### Profiles

A profile scales the policy's ranges and frequencies: how many hands it plays (tight to loose), how
often it bets and raises rather than checks and calls (passive to aggressive), how often it bluffs,
and how readily it folds to a large bet. A table mixes profiles so that the field has players to
exploit and players to avoid, and the profile's style is shown at its seat in a word ("tight",
"aggressive") so the player can learn to read it.

There is **one field strength** in the first release; opponent levels are deferred (`TODO.md`).
The bar the field must meet is measurable rather than a feeling: every simple exploit strategy —
always call, always raise, always all-in, play only premium hands — must finish first less often
than an even sixth of the time against it (`EXECUTION_PLAN.md` H7). A field that a fixed trick
beats is not a field worth playing.

## Scoring and statistics

There is no move count and no elapsed time; a tournament is not solved, and blinds rise by hands,
not by the clock (`RULES.md` "The Tournament"), so there is no timer to run.

Statistics are lifetime, one pool:

- **Tournaments:** played, won (finished first), the distribution of finishing places, average
  finish, current and best winning streak.
- **Hands:** played, won (any pot taken, at showdown or not), showdowns reached and won, the largest
  pot won, and the two standard measures of style: how often the player puts chips in voluntarily
  before the flop, and how often they raise before the flop. Blinds posted are not voluntary.
- **Hints used.**

A tournament's outcome is a boolean — first, or not — so its played, won and streak fields fit
`core/session`'s `computeSessionStatistics`; check that against the code before writing a copy
(`AGENTS.md`, the "Moved later" rule). Its best-time and best-moves fields have nothing to measure
and are left empty. The place distribution and the hand-level figures are Hold'em's own section,
passed through `StatisticsBody`'s `middle` slot.

## Architecture

Three modules:

- `games/holdem/rules` — the hand evaluator, the deck and deal, betting and its legal actions,
  pots and side pots, showdown, the tournament's blinds and eliminations, and the one reducer.
  Kotlin/JVM, depends only on `core/cards` and `core/session`, no Android.
- `games/holdem/opponents` — the policy, ranges, equity estimation and profiles, and the Hint built
  on them. Kotlin/JVM, depends on `games/holdem/rules`, no Android. A separate module because it is
  the one part that will be tuned after the rules freeze, and because its only input type is the
  seat's view: a module boundary makes "the opponent cannot see a hidden card" a compile-time fact.
- `games/holdem/app` — the Android application, depending on `core/storage`, `core/ui`, and both of
  the above.

**`SeatView`** is the only type the opponents module receives: the seat's own hole cards, the
board, every stack, the pot, the blinds, the button, and the action history. It has no field from
which another seat's cards or the undealt deck can be read. The hint takes the player's `SeatView`
and nothing else.

`HoldemState` is its own type — the tournament (stacks, button, blind level, hand number,
eliminations with their places) and the hand in progress (seed, hole cards, board, street, bets,
pots, whose turn). Per `docs/ARCHITECTURE.md`'s own rule, this is the **second non-solitaire**, and
the findings Blackjack recorded as one-example findings are due for their re-check here, during
H3: `BlackjackState` against `HoldemState`, Blackjack's chip drawing and result mark, and its
round-level statistics against these. Anything that passes moves to `core/`; anything that does not
is recorded in `docs/ARCHITECTURE.md` as checked.

`core/session`'s `Session<S, E>` fits for its commit-and-log half, as it does for Blackjack. The log
differs from Blackjack's in one way: it records **every seat's action**, the opponents' as well as
the player's. Blackjack's dealer is a fixed rule the reducer can re-derive forever; an opponent's
policy is the part of this game most likely to change between releases, and a saved hand replayed
under a re-tuned policy would diverge. Logging each action makes the log the record, with the
reducer checking each entry for legality on replay. Deals, burns, street changes, pots and showdown
are still consequences the reducer derives from the seed and the log.

## Persistence

`docs/PLATFORM.md` "Persistence" applies unchanged; the pattern is Blackjack's — a store for the
thing in progress, a store for what outlives it, and a write order that keeps them consistent.

- **The hand in progress** is saved through `core/storage`'s `ActiveGameRecordStore<T>` and restored
  by replaying its log. Its `DealParameters<T>` is the hand number. There is no catalog and no timer,
  so `catalogVersion` and `elapsedMillis` are always 0.
- **The tournament and statistics** share one store, written only as a whole, in one atomic write:
  the tournament's seed and its state at the start of the current hand (stacks, button, level, hand
  number, eliminations), and every statistic.
- **Written before shown.** Every action that reveals a card — the deal, a street, a showdown — and
  every opponent action awaits its save before it is displayed.
- **Settled exactly once.** A hand's end writes the tournament's next state and the updated
  statistics together, then clears the hand. On restore, a hand whose number is not the stored
  tournament's current hand has already been recorded and is discarded. The tournament's end is the
  same write, with the tournament cleared and its result counted.
- A corrupt or unreadable hand save is discarded and the hand is **replayed from its start** — the
  same seed, the same deck — rather than voided: chips at the start of the hand are in the tournament
  store, and a hand replayed from its first action shows the player nothing new. A corrupt tournament
  store is discarded as `docs/PLATFORM.md` requires.

## Interface

`UI_SPEC.md` is the authority on the interface; this is its shape. An oval
table: the player's seat at the bottom centre with both cards face up and large, five opponent
seats around the top and sides, each a name, an avatar, a stack, a style word, a dealer-button or
blind marker, and two card backs while in the hand. The board's five cards and the pot (with side
pots listed) in the middle. The current bet in front of each seat. The action bar at the bottom, with
the sizing row above it when Bet or Raise is chosen, and the blind level, its next increase ("blinds
rise in 3 hands"), and the hand number in a status line. Landscape puts the table to one side and
the bar on the rail, as the other games do.

At showdown every live hand is shown, the winning five cards are raised, and the hand's name
("Flush, king high") labels each hand — the one result mark Blackjack settled on for its own result
is the model for the player's.

## Sound, Localization, Accessibility, Performance, Quality

Mechanism, gating, and the shared bar: `docs/PLATFORM.md` "Sound", "Localization",
"Accessibility", "Performance", and "Quality". Hold'em ships only its own strings — hand names,
actions, Help — and the rest merge in from `core/ui`. Every opponent action is announced to
accessibility services as it happens ("Ava raises to 120"), since a screen reader user cannot watch
the chips move.

One budget is Hold'em's own, on top of the platform's: an opponent's decision, including its equity
estimate, completes within 50 ms at p95 on the reference device. The pacing pause hides it; the
budget keeps it from ever being the pause.

## Supporting Specifications

- [RULES.md](RULES.md): hand rankings, the tournament, the deal, betting, legal actions, pots and
  showdown, leaving
- [EXECUTION_PLAN.md](EXECUTION_PLAN.md): work packages H0–H9 and their gates
- [UI_SPEC.md](UI_SPEC.md): screens, seats, layouts and geometry, the action bar and sizing row,
  opponents' turns, hint, showdown and results, leaving, panels, motion, accessibility
- [TODO.md](TODO.md): deferred, out of first-release scope
