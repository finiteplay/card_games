# Texas Hold'em — Execution Plan

Publisher: [FinitePlay LLC](https://finiteplay.org)

Work packages, in order, each ending on a gate. A package is done when its gate passes — not when
the code looks right. `docs/ARCHITECTURE.md` "Adding a game" (the non-solitaire recipe) is the
procedure this plan follows, with one module more than Blackjack's: the opponents.

## Status

The packages below stay in the future tense they were written in (`CLAUDE.md` "Specifications are
authoritative"). This table is the only part of this file kept current, and the only place in
Hold'em's docs that says what exists today.

| Package | State |
|---|---|
| H0 — interface specification | Not started. `DESIGN.md`, `RULES.md` and this plan are written. |
| H1–H9 | Not started. |

## Deterministic Deal Contract

Hold'em has no catalog, so nothing is certified against this contract. It exists because a saved
hand is restored by replaying its log against its seed, and because H7 measures the opponents over
seeded tournaments that must replay identically (`docs/PLATFORM.md` "Deterministic Shuffle").

- Deck: `core/cards`' canonical deck order and `shuffleDeckIndices(seed, size = DECK_SIZE)`,
  unchanged.
- Hand seed: derived from the tournament seed and the hand number by one fixed mixing function,
  committed with reference vectors. Never the clock or a counter alone.
- Tournament seed: drawn at New Tournament from a cryptographically strong random source. Tests
  substitute fixed seeds through a seam in `src/debug` or test sources, never through a release code
  path.
- Deal order: `RULES.md` "The Deal" — hole cards one at a time from the first live seat to the
  button's left, twice around; burn, flop, burn, turn, burn, river.
- Opponent draws: profiles and the first button come from the tournament seed; every mixed decision
  from the hand seed, the seat and the action index (`DESIGN.md` "The policy").
- The move log records every seat's action: `Fold`, `Check`, `Call`, `Bet(amount)`, `Raise(total)`,
  `AllIn`, each with its seat. The alphabet is part of the contract from H4 on.
- Versions: `rulesVersion` and `shuffleVersion` start at 1; `catalogVersion` is always 0. An
  opponent policy change needs no version: the log, not the policy, is the record (`DESIGN.md`
  "Architecture").
- Commit reference vectors mapping representative seeds to the full 52-card order, and tournament
  seeds to the hand seeds of their first ten hands.

Changing any rule in `RULES.md`, the deal order, the seed mix, or the opcode alphabet requires a new
rules or shuffle version.

## Work Packages

### H0 — Interface Specification

Depends on: `DESIGN.md` and `RULES.md` as they stand.

Write `docs/games/holdem/UI_SPEC.md` and `TODO.md`, following Blackjack's:

- Portrait and landscape layouts: six seats with name, avatar, stack, style word, button and blind
  markers, current bets, card backs; the board; the pot and side pots; the player's cards; the status
  line (hand number, blind level, hands to the next level).
- The action bar in each state of `RULES.md` "Legal Actions", the sizing row with its presets and
  stepper, and Hint — proving on paper that the widest state fits `BoardActionBar` at the platform's
  touch-target minimum at 320 dp.
- The opponents' pacing: the pause before each action, its length for a large decision, and what
  Skip Animations shortens it to.
- Showdown and result presentation: the winning five cards, hand names, a split pot, side pots
  awarded in order, an elimination, and the tournament's end with the player's place.
- Leave tournament and its confirmation, Hint presentation, Statistics, Help (rules, hand rankings,
  and a simple strategy tab), Settings, and motion.
- Anything left out of the first release goes to `TODO.md`, with opponent levels first.

**Gate.** A documentation package: done when `UI_SPEC.md` states a number or a rule for every item
above, and `AGENTS.md` and `DESIGN.md` "Supporting Specifications" list the new files. H3's geometry
test verifies it.

### H1 — Modules and Build Scaffolding

Depends on: H0.

- Create `games/holdem/rules` and `games/holdem/opponents` (Kotlin/JVM, no Android) and
  `games/holdem/app` (Android application); add their `include(...)` lines to `settings.gradle.kts`.
  No `solver` module.
- Add `holdem` to `forbiddenNames` in `buildSrc/src/main/kotlin/finiteplay.shared-layer.gradle.kts`.
- Register the `assertAppExcludes…` tasks Blackjack's app has, so no `solitaire/*` project or
  `tools/catalog` reaches the release classpath.
- `applicationId`, `app_name` ("Texas Hold'em by FinitePlay"), `game_title`, launcher icon
  (`docs/APP_ICON_STYLE.md`), a scaffold screen, and an upload-keystore alias, so the release build
  signs from the start rather than at the end as Blackjack's did.

**Gate.** All modules build on Windows with the checked-in wrapper. Neither JVM module has an
Android import. The layering tasks fail when a `solitaire/*` dependency is added and pass without
it. `.\gradlew.bat check` is green from a clean checkout.

### H2 — Rules Engine

Depends on: H1.

- The hand evaluator (`RULES.md` "Hand Rankings"): best five of seven, a value that orders hands
  totally and ties exactly.
- `HoldemState` and the deal from the contract above.
- Betting: first to act, minimum bet and raise, the short all-in rule, the big blind's option, the
  end of a round, uncalled bets, the run-out (`RULES.md` "Betting").
- Legal actions with their amount ranges (`RULES.md` "Legal Actions").
- Pots, side pots, showdown and split pots, odd chips (`RULES.md` "Pots and Showdown").
- The tournament: blinds by schedule, the button, heads-up, eliminations and places, the end, leaving
  (`RULES.md` "The Tournament", "The Button and the Blinds", "Leaving").
- The one reducer on `core/session`'s `Session<S, E>`, logging every seat's action; no undo
  reachable through it.

**Gate.**

- **Evaluator, exhaustively.** All 2,598,960 five-card hands produce exactly 7,462 distinct values
  and the standard count for each category (40 straight flushes, 624 fours, 3,744 full houses, 5,108
  flushes, 10,200 straights, 54,912 threes, 123,552 two pairs, 1,098,240 pairs, 1,302,540 high
  cards). All 133,784,560 seven-card hands give the standard seven-card category counts. The
  seven-card run may be a separately invoked test task if it is too slow for `check`; it is run
  before the package closes either way.
- **Legal actions** from a generated matrix — street × facing a bet or not × whether the betting is
  open to this player × stack against the call and against the minimum raise — with the case count
  committed as a test constant.
- **Side pots** from generated all-in configurations of two to six players with distinct and equal
  stacks: every chip awarded, each pot only to eligible players, odd chips to the right seat.
- **A random legal-action soak** over many seeded tournaments, every seat choosing random legal
  actions, preserving:
  - no card is dealt twice, and the deal follows the contract;
  - the stacks plus the pots always sum to 9,000;
  - every action logged was legal when taken, and nothing illegal is ever offered;
  - a betting round never ends with an unmatched bet from a player who can still act;
  - blinds follow the schedule and the button the live seats;
  - places are a permutation of 1–6 over every seat once the tournament ends;
  - replaying the log from the seed reproduces the state exactly.
- **Constructed fixtures** for each rule a soak cannot reach reliably: a wheel, a board that plays for
  everyone, a three-way split with an odd chip, a short all-in that does not reopen, the big blind's
  option, heads-up order, two eliminations in one hand with equal and unequal starting stacks, a
  player who cannot cover the big blind.

### H3 — Playable Table Vertical Slice

Depends on: H2.

- The table per `UI_SPEC.md`, portrait and landscape, cards drawn with `core/ui`'s
  `drawCardFace`/`drawCardBack`, the action bar showing exactly the legal actions, the sizing row.
- Opponents from a **placeholder** policy — check when free, call small bets, fold to large ones —
  in `games/holdem/opponents` behind the real `SeatView` interface, so H7 replaces the policy without
  touching the app.
- The opponents' pacing; showdown and result presentation; elimination; the tournament's end; New
  Tournament; Leave tournament. State in memory only until H4.
- **The second-non-solitaire re-check** (`DESIGN.md` "Architecture"): read Blackjack's table, chip
  drawing, result mark, flights and statistics before writing Hold'em's, move what both now need to
  `core/`, and record what was checked and stayed in `docs/ARCHITECTURE.md`.

**Gate.** An instrumented test on an emulator plays committed fixed-seed hands, in both orientations,
through: a fold, a check-down to showdown, a bet and a call, a raise and a re-raise, an all-in with a
side pot, a split pot, the player eliminated with their place shown, and a tournament won. Every
hand's chips are asserted to the chip, and the buttons always equal the engine's legal actions. A
geometry test at 320 dp and 360 dp confirms `UI_SPEC.md`'s numbers. `UI_SPEC.md` is corrected if the
test disagrees.

### H4 — Persistence and Restoration

Depends on: H3.

- The hand through `ActiveGameRecordStore`, the hand number as its `DealParameters`, and the log
  codec.
- The tournament-and-statistics store, written whole in one atomic write.
- Written before shown, settled exactly once, and a corrupt hand replayed from its start
  (`DESIGN.md` "Persistence").

**Gate.**

- **Fault injection** at the store seam: a kill before a hand's first save, between an action and
  its save, and between a hand's end write and clearing the hand each restore a correct state — no
  pot paid twice or lost, no hand counted twice in the statistics.
- **A ViewModel test** proves a revealed card and an opponent's action are absent from the displayed
  state until their save completes.
- **On a real emulator**, a process restart and a force-stop mid-hand restore the exact hand, on the
  minified release build.

### H5 — Settings, Statistics, and Help

Depends on: H4.

- Settings: skip animations, handedness, sound, theme, language, the rest reminder. No
  automatic-moves setting; opponents are not automation.
- Statistics per `DESIGN.md` "Scoring and statistics", on `core/session`'s statistics where they fit
  and `core/ui`'s `StatisticsBody`.
- Help: the rules and hand rankings in player-facing strings, and a simple strategy tab, all
  localized (`docs/PLATFORM.md` "Localization").

**Gate.** Settings apply at once and survive restart. Statistics after a committed fixture sequence —
including a leave, a win, a split pot and a hand won without showdown — match values computed
independently in the test. The locale-completeness test passes. Every screen closes on Back.

### H6 — Instrumented Tests

Depends on: H3, H4, H5.

**Gate.** `games/holdem/app:connectedDebugAndroidTest` on an emulator covers `docs/PLATFORM.md`
"Quality" for a game with no drag and no undo: each action, the sizing row, New Tournament,
Leave tournament, restoration, rotation mid-hand, both themes, both handedness layouts, skip
animations, showdown presentation, and the tournament's end.

### RF — Rules Freeze

Depends on: H2–H6.

- Declare `RULES.md` and the contract final; freeze `rulesVersion` and `shuffleVersion` at 1.
- Commit a reference set of seeded hands through the frozen engine — at least each hand category at
  showdown, a side pot, a split with an odd chip, a short all-in, and an elimination — with a
  canonical state hash for each.

**Gate.** No open defect against the evaluator, betting, pots, the tournament, or persistence. The
contract and the implementation agree, verified by test.

### H7 — Opponents and Hint

Depends on: RF.

- The policy, ranges and profiles of `DESIGN.md` "Opponents", replacing H3's placeholder.
- Preflop tables committed as data, generated by a tool in `games/holdem/opponents`' test sources so
  it never reaches the app; the push-or-fold table likewise.
- The Hint (`DESIGN.md` "Hint") on the same policy, wired per `UI_SPEC.md`.
- A headless tournament runner in test sources that seats any mix of policies and plays seeded
  tournaments to the end.

**Gate.**

- **Hidden information.** For random views, permuting every card the seat cannot see — other seats'
  hole cards and the undealt deck — never changes the decision, and the same view always gives the
  same decision.
- **Legality.** Across the H2 soak with opponents in every seat, every decision is a legal action
  with a legal amount, and so is every Hint.
- **The field's strength.** Over 2,000 seeded tournaments each, a single seat playing each exploit —
  always call, always raise the minimum, always all in, raise only with the top 5% of hands and fold
  the rest — against five opponents finishes first less often than one time in six, with the bound
  holding at 95% confidence.
- **Not trivially passive.** One opponent seated against five always-call seats finishes first more
  often than one time in six — a field that only avoids losing has not been built.
- **Push-or-fold monotonicity.** In the committed table, no hand class is pushed in a spot where a
  strictly stronger class folds.
- **Budget.** An opponent decision completes within 50 ms at p95 on the reference device, measured
  on hardware, and recorded as open until it is (`DESIGN.md` "Sound, Localization, …").

### H8 — Card Motion

Depends on: H3.

- Hole cards fly to the seats in deal order, the board deals in place, bets slide to the pot at a
  street's end, pots slide to their winners. Reuse the flight technique from Blackjack's and
  FreeCell's boards — a displayed state that steps forward as each flight lands — rather than
  deriving a new one.
- Skip Animations removes every flight while keeping each action and each street visibly discrete.

**Gate.** Instrumented tests prove the deal, a street, and a pot award each animate, that a pot is
never shown awarded before the river lands, and that Skip Animations lands each step within a few
frames. No regression elsewhere in the suite.

### H9 — Performance and Release

Depends on: H6, H7, H8.

- Confirm no network or wake-lock permission and no background-capable component in the manifest.
- Run the complete API 26 and current-API emulator matrix.
- Measure download size, cold start, play memory, frame rate, idle CPU, and the opponent decision
  budget against `docs/PLATFORM.md` "Performance" and `DESIGN.md`.
- Write `ACCEPTANCE.md` with the evidence.

**Gate.** `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug`, and `assembleRelease` pass
from a clean checkout and every budget is met. A budget only physical hardware can confirm is
recorded as open, not passed.

## Dependency and Parallelization Map

```text
H0  -> H1
H1  -> H2
H2  -> H3
H3  -> H4
H4  -> H5
H3 + H4 + H5 -> H6
H6  -> RF
RF  -> H7
H3  -> H8
H6 + H7 + H8 -> H9
```

## Execution Rules

- One package at a time on the critical path. H8 may run alongside H4–H7, since it touches only the
  table's drawing.
- End every package with a commit series that finishes on its green gate.
- Add a failing test before fixing every discovered rules or persistence bug.
- Never give the opponents module a type through which a hidden card can be read, and never let a
  deck order depend on anything after its shuffle; each would reopen what `DESIGN.md` "What Hold'em
  is not" closes.
- Never add undo, or any path that shows a card or an opponent's action before its save completes.
- Do not start deferred items from `TODO.md` during first-release execution.
- Update this plan and the affected specification when implementation reveals a requirement
  change; do not hide scope changes inside code.
