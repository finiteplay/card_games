# Blackjack — Execution Plan

Publisher: [FinitePlay LLC](https://finiteplay.org)

Work packages, in order, each ending on a gate. A package is done when its gate passes — not when
the code looks right. `docs/ARCHITECTURE.md` "Adding a game" (the non-solitaire recipe) is the
procedure this plan follows.

## Status

The packages below stay in the future tense they were written in — rewriting a plan into a report
loses the record of what was intended (`CLAUDE.md` "Specifications are authoritative"). This table
is the only part of this file kept current, and the only place in Blackjack's docs that says what
exists today.

| Package | State |
|---|---|
| B0 — interface specification | Done: `UI_SPEC.md` and `TODO.md`. |
| B1 — modules and build scaffolding | Done. `games/blackjack/{rules,app}`; `check` verifies neither reaches `solitaire/*` or `tools/catalog`. The app's release build compiles and shrinks, and stops at signing: the shared upload keystore has no `blackjack-upload` alias yet. |
| B2 — rules engine | Done, gate met: brute-force hand values, a generated legal-decision matrix and settlement table (case counts committed), a 6,000-round soak, constructed fixtures, and reference shoe vectors. |
| B3 — playable table vertical slice | Built, and an emulator suite (`TableTest`) plays fixed-seed rounds through the real screen — hit, double, split with a resplit offered, insurance, a dealer blackjack at the peek, a push, a win, a loss that lowers the bet and a reset — in both orientations, asserting the bankroll to the chip and that the buttons equal the legal actions. Geometry is unit-tested at 320 dp and 360 dp. Split Aces, a dealer bust, and a twenty-one-card hand are covered by unit tests rather than on the emulator. The fixed-seed seam is the view model's constructor, not a `src/debug` loader. |
| B4a — persistence and restoration | Done: fault injection at the store seam, a fresh view model restoring a round, and — on the emulator, on the minified release build — a force-stop mid-decision restoring the same hand, and settlement surviving a force-stop (`ACCEPTANCE.md`). |
| B4b — settings, statistics, help | Done, with the rest reminder the other games have: unit-tested stores and statistics, localized in all 30 locales (machine-generated), and Statistics, Help and Settings open and close on Back on the emulator. |
| B5 — instrumented tests | `TableTest` (18 tests) passes on the current-API emulator and, at 16 tests, on API 26. Rotation mid-round is simulated with a landscape configuration, not by rotating the activity; the rest reminder's dialogs are not covered on the emulator. |
| RF — rules freeze | Done, on the owner's decisions of 2026-10-03 (an Ace pair splits once; a blackjack is a win; Reset discards leftover chips; the rest reminder is in). `ReferenceRoundsTest` pins twelve rounds covering every settlement kind by the canonical state hash, and the contract's constants; versions are frozen at 1. |
| B6 — basic-strategy hint | Done: the generator is in the rules module's test sources, the committed table is checked against it cell for cell, the close cells are listed, the table agrees with the published six-deck stand-on-soft-17 double-after-split chart in every cell, and across random legal play the hint is always an offered action. The app shows it as a notice above the bar. |
| B7 — card motion | Cards fly in from the shoe in deal order and the dealer's draws are paced, each its own flight; Skip Animations puts them straight down. An emulator test proves the flight happens (the hand reads `flying`, then `settled`) and that Skip Animations lands at once. The hole card turns over as a step, not a flip animation. |
| B8 — performance and release | Evidence in `ACCEPTANCE.md`: no network or wake-lock permission, no service or job, 1.5 MB, cold start about 0.5 s and 28 MB PSS on the emulator. The frame-deadline and idle-CPU budgets, and cold-start p95, are **open** for hardware. The upload build needs a `blackjack-upload` keystore alias. |

## Deterministic Shoe Contract

Blackjack has no catalog, so nothing is certified against this contract. It exists for the same
reason a solitaire's does: a saved round is restored by replaying its log against its seed, so the
same seed and log must rebuild the same round forever (`docs/PLATFORM.md` "Deterministic
Shuffle").

- Shoe: `core/cards`' canonical deck order and `shuffleDeckIndices(seed, size = 6 * DECK_SIZE)`,
  unchanged. Shuffled value `v` names the card at canonical index `v % DECK_SIZE`; the six copies of
  a card are interchangeable.
- Deal order: shuffled index 0 to the player, 1 to the dealer face up, 2 to the player, 3 to the
  dealer face down; every later card takes the next unused index (`RULES.md` "The Shoe").
- Seed source: drawn at Deal from a cryptographically strong random source, never from the clock
  or a counter (`RULES.md` "The Shoe"). Tests substitute fixed seeds through a seam in `src/debug`
  or test sources, never through a release code path.
- Versions: `rulesVersion` and `shuffleVersion` start at 1; `catalogVersion` is always 0
  (`DESIGN.md` "Persistence").
- The move-log opcode alphabet — `Hit`, `Stand`, `Double`, `Split`, `TakeInsurance`,
  `DeclineInsurance` — is part of the contract from B4a on.
- Commit reference vectors mapping representative seeds to the first 20 cards of the shoe.

Changing any rule in `RULES.md`, the deal order, or the opcode alphabet requires a new rules or
shuffle version, and a rules change also regenerates the Hint table (B6).

## Work Packages

### B0 — Interface Specification

Depends on: `DESIGN.md` and `RULES.md` as they stand.

`DESIGN.md` "Interface" is a sketch; the board cannot be built against it. Write
`docs/games/blackjack/UI_SPEC.md` and `TODO.md` first, following the shape of the other games' own:

- Portrait and landscape layouts: the dealer's hand, one to four player hands, the active-hand
  marker, per-hand stakes and results, the bankroll, available bankroll, and the last settlement's
  delta.
- The action bar in each phase of `RULES.md` "Order of Play": the bet stepper and Deal between
  rounds, the two insurance actions, the four decisions plus Hint, and the bankroll reset — proving
  on paper that the widest phase fits `BoardActionBar` at the platform's touch-target minimum.
- Card geometry: the card-width floor and hand overlap for four split hands side by side, and for
  the longest hand six decks allow — twenty-one cards, all Aces — at 320 dp and 360 dp.
- Settlement presentation, the reset offer, the Hint presentation, Statistics (`DESIGN.md` "Scoring
  and statistics"), Help, Settings, and motion.
- Anything deliberately left out of the first release goes to `TODO.md`.

**Gate.** A documentation package, so no automated gate of its own: it is done when `UI_SPEC.md`
states a number or a rule for every item above, and `AGENTS.md` and `DESIGN.md` "Supporting
Specifications" list the new files. B3's geometry test is what verifies it.

### B1 — Modules and Build Scaffolding

Depends on: B0.

- Create `games/blackjack/rules` (Kotlin/JVM, no Android) and `games/blackjack/app` (Android
  application), and add their `include(...)` lines to `settings.gradle.kts`. No `solver` module,
  ever (`DESIGN.md` "Architecture").
- Wire `games/blackjack/app`'s only dependency for now: `core/ui`, for the shared theme the scaffold
  screen renders under. `core/cards` and `core/session` arrive with B2, `core/storage` with B4a.
  Never `solitaire/*`, at any point.
- `blackjack` is already in `forbiddenNames`; confirm it rather than adding it.
- Give the app its own `applicationId`, `app_name` ("Blackjack by FinitePlay", matching the
  family's naming), `game_title`, and launcher icon (`docs/APP_ICON_STYLE.md`), with a minimal
  scaffold screen for B3 to replace.

**Gate.** All modules build on Windows with the checked-in wrapper. `games/blackjack/rules` has no
Android imports. Neither module resolves any `solitaire/*` project on any classpath, verified by a
build check rather than by inspection. `.\gradlew.bat check` is green from a clean checkout.

### B2 — Rules Engine

Depends on: B1.

- Hand values: totals, soft and hard, blackjack versus a split-Ace 21, bust (`RULES.md` "Hand
  Values").
- `BlackjackState` (`DESIGN.md` "Architecture") and the shoe from the contract above.
- The order of play as an explicit phase: insurance, peek, player blackjack, player decisions,
  dealer play, settled (`RULES.md` "Order of Play").
- Legal actions, including available-bankroll affordability and the three-split limit (`RULES.md`
  "Legal Actions", "Betting").
- The dealer's play and settlement, committed as one transaction by the decision that completes the
  last hand (`RULES.md` "Dealer Play", "Settlement").
- The one reducer, on `core/session`'s `Session<S, E>`, logging player decisions only. No undo is
  reachable through it.
- The settled-bet adjustment and the reset (`RULES.md` "Betting", "Round Lifecycle"), as pure
  functions over the bankroll; the rules module does not persist anything.

**Gate.**

- **Hand values** are checked against a brute-force evaluator over every multiset of card values a
  hand can reach, up to the twenty-one-card maximum, including the multiple-Ace examples in `RULES.md` "Hand Values".
- **Legal actions** are covered by a generated matrix, not a hand-written list: phase × hand size ×
  pair or not × split Ace or not × split count × whether the available bankroll covers the stake.
  The resulting case count is committed as a test constant.
- **Settlement** is covered by a generated table: player outcome (blackjack, bust, total) × dealer
  outcome (blackjack, bust, total) × doubled or not × insurance taken or not. Every result is
  asserted to the chip.
- **A random legal-action soak** over many seeds and bets preserves every invariant:
  - no card is dealt twice, and the deal order matches the contract;
  - chips are conserved, so the bankroll change equals the sum of the hand and insurance results;
  - the available bankroll is never negative;
  - no hand exceeds four, and a split Ace never takes a second card;
  - the dealer never draws before every hand is complete, or after every hand has busted;
  - replaying the log from the seed reproduces the state exactly.
- **Constructed fixtures** prove the peek settles a dealer blackjack before any decision is
  offered, insurance is offered only on an Ace, and a hand reaching 21 completes without Stand.

### B3 — Playable Table Vertical Slice

Depends on: B2.

- `games/blackjack/app`'s table per `UI_SPEC.md`, portrait and landscape: the hands drawn with
  `core/ui`'s `drawCardFace`/`drawCardBack`, the bet stepper, and the action bar on `BoardActionBar`
  showing exactly the legal actions.
- Deal, the insurance decision, the four decisions, dealer play, settlement, New Round with the
  selected bet adjusted, and the reset offer — bankroll held in memory only until B4a.
- A debug-only fixed-seed and fixture loader in `src/debug`, so a test can deal a known round or
  start at a bankroll just above the minimum.

**Gate.** An instrumented test on an emulator plays committed fixed-seed rounds, in both
orientations, through:

- a hit;
- a double;
- a split, including a resplit;
- split Aces;
- insurance taken and declined, with a peek that finds blackjack and one that does not;
- a dealer bust and a dealer stand;
- a push;
- the bet lowering after a loss;
- a reset from below the minimum.

Every settlement's bankroll change is asserted to the chip. The buttons on screen always equal the
engine's legal actions. A geometry test at 320 dp and 360 dp confirms `UI_SPEC.md`'s floors,
including four split hands and a twenty-one-card hand, and that the widest action phase fits the bar.
`UI_SPEC.md` is corrected if the test disagrees with it.

### B4a — Persistence and Restoration

Depends on: B3.

- The round in progress through `core/storage`'s `ActiveGameRecordStore`, the bet as its
  `DealParameters`, and a log codec for the six-opcode alphabet (`DESIGN.md` "Persistence").
- The bankroll-and-statistics store: the bankroll, every statistic, and the last settled seed,
  always written as a whole in one atomic write.
- Written before shown: Deal and every decision that draws or reveals a card wait for their save to
  complete before the card appears.
- Settled exactly once: settlement's single write comes first, then the round is cleared, and on
  restore a round whose seed equals the last settled seed is discarded.
- Restore before showing an interactive table. A corrupt round save voids the round without
  touching the bankroll store. A corrupt bankroll store is discarded as `docs/PLATFORM.md` requires,
  which starts from 1,000 chips with empty statistics.

**Gate.**

- **Fault injection** at the store seam proves a failure or kill at each point leaves a correct
  state on restore:
  - before the round is saved at Deal;
  - between a decision and its save;
  - between settlement's write and clearing the round.
  In none of these is a result paid twice, lost, or counted twice in the statistics.
- **A ViewModel test** proves a drawn card is absent from the displayed state until its save
  completes.
- **On a real emulator**, process restart restores the exact round mid-decision, and a force-stop
  during play restores it.
- **A release-variant test** round-trips both stores with minification enabled.

### B4b — Settings, Statistics, and Help

Depends on: B4a.

- Settings: skip animations, handedness, sound, theme, language. No automatic-moves setting — the
  dealer's play is not automation (`DESIGN.md` "What Blackjack is not").
- Statistics per `DESIGN.md` "Scoring and statistics": hands played, won, lost, pushed, player
  blackjacks, current bankroll, high-water mark, lifetime net chips, and resets, as one pool, on
  `core/ui`'s `FullScreenPanel`. Not on `core/session`'s `SessionStatistics` (`DESIGN.md` says
  why).
- Help: the table rules in player-facing strings, all localized (`docs/PLATFORM.md`
  "Localization").

**Gate.** Settings apply immediately and survive restart. Statistics displayed after a committed
fixture sequence — including split hands, pushes, insurance, and two resets — match values
computed independently in the test, and lifetime net chips equals the sum of the settlement deltas.
The locale-completeness test passes. Every screen closes on Back.

### B5 — Instrumented Tests

Depends on: B3, B4a, B4b.

**Gate.** `games/blackjack/app:connectedDebugAndroidTest` on an emulator covers what
`docs/PLATFORM.md` "Quality" requires of a game with no drag and no undo:

- tapping each action;
- New Round;
- restoration;
- rotation mid-round;
- both themes;
- both handedness layouts;
- skip animations;
- settlement presentation in place of a win;
- the reset offer.

### RF — Rules Freeze

Depends on: B2–B5.

The Hint table is derived from the exact rules (`DESIGN.md` "Hint"), so it is generated only after
real play — automated and by hand — has exercised them. This is the same ordering the solitaires
use for their catalogs.

- Declare `RULES.md` and the contract above final; freeze `rulesVersion` and `shuffleVersion`
  at 1.
- Commit a reference round set played through the frozen engine — at least a split, split Aces, a
  double, insurance, and each settlement kind — and a canonical state hash for each.

**Gate.** No open defect against hand values, legal actions, dealer play, settlement, betting, or
persistence. The contract and the implementation agree, verified by test. Any later rules change
bumps the rules version and reruns B6.

### B6 — Basic-Strategy Hint

Depends on: RF.

- An offline generator, in `games/blackjack/rules`' test sources so it can never reach the app,
  that computes the expected value of each action for each Hint input (`DESIGN.md` "Hint" —
  pair, soft total, or hard total, against each dealer up card). It covers six decks under the
  frozen rules and conditions on the dealer not having blackjack, which is what the peek guarantees.
- The committed table in `games/blackjack/rules`: each cell names its first choice and, for Double
  and Split cells, the fallback when that action is not offered.
- Wiring Hint in the app per `UI_SPEC.md`.

**Gate.**

- A unit test regenerates the table and asserts the committed one matches, cell for cell,
  including fallbacks.
- A cell whose two best actions are within a small committed margin of each other is listed in the
  test, not left to chance.
- Every difference from a published six-deck, stand-on-soft-17, double-after-split, no-surrender
  chart is either explained or fixed. The chart is background, not the contract.
- Across B2's legal-action soak, Hint's answer is always one of the actions currently offered.
- Insurance is always declined.

### B7 — Card Motion

Depends on: B3.

- Per `UI_SPEC.md`'s motion section: cards fly from the shoe to their hands in deal order, the hole
  card flips at its reveal, and the dealer's draws are paced one at a time. Settlement results
  appear only after the dealer's last card lands.
- Skip Animations removes every flight and flip while keeping the dealer's draws visibly discrete.
- Reuse the flight technique from Klondike's and FreeCell's boards — a displayed state that only
  steps forward as each flight lands — rather than deriving a new one (`AGENTS.md`, the "Moved
  later" rule).

**Gate.** Instrumented tests prove the deal, a hit, the hole-card reveal, and the dealer's draws
each actually animate rather than only asserting the end state, and that settlement results never
appear before the dealer's last card lands. Skip Animations lands each step within a few frames. No
regression in the rest of the suite.

### B8 — Performance and Release

Depends on: B5, B6, B7.

- Confirm the manifest declares no network or wake-lock permission and no background-capable
  component.
- Run the complete API 26 and current-API emulator matrix.
- Measure release download size, cold start, play memory, frame rate, and idle CPU against
  `docs/PLATFORM.md` "Performance".
- Archive the release evidence: command outputs, the matrix results, and the measurements.

**Gate.** `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug`, and `assembleRelease` pass
from a clean checkout, and every `docs/PLATFORM.md` "Performance" budget is met. A budget that can
only be confirmed on physical hardware is recorded as open, not as passed.

## Dependency and Parallelization Map

```text
B0  -> B1
B1  -> B2
B2  -> B3
B3  -> B4a
B4a -> B4b
B3 + B4a + B4b -> B5
B5  -> RF
RF  -> B6
B3  -> B7
B5 + B6 + B7 -> B8
```

## Execution Rules

- Implement one work package at a time on the critical path. B7 may run alongside B4a–B6, since it
  touches only the table's drawing.
- End every package with a commit series that finishes on its green gate.
- Add a failing test before fixing every discovered rules or persistence bug.
- Do not commit the Hint table before RF. If a rules change is unavoidable after RF, bump the rules
  version and rerun B6 in full.
- Never add undo, a mid-round abandon, or any path that shows a card before its save completes;
  each would reopen what `DESIGN.md` "What Blackjack is not" closes.
- Do not start deferred items from `TODO.md` during first-release execution.
- Update this plan and the affected specification when implementation reveals a requirement
  change; do not hide scope changes inside code.
