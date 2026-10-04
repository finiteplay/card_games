# FreeCell — Execution Plan

Publisher: [FinitePlay LLC](https://finiteplay.org)

Work packages, in order, each ending on an automated gate. A package is done when its gate
passes — not when the code looks right. `docs/ARCHITECTURE.md` "Adding a game" is the procedure
this plan follows.

## Status

The packages below stay in the future tense they were written in — rewriting a plan into a
report loses the record of what was intended (`CLAUDE.md` "Specifications are authoritative").
This table is the only part of this file kept current, and the only place in FreeCell's docs
that says what exists today.

| Package | State |
|---|---|
| F0 — modules and build scaffolding | **Delivered.** `games/freecell/rules` and `games/freecell/app` exist; the app is a placeholder Compose screen behind the shared theme. `games/freecell/solver` remains uncreated, per this package's own note above. |
| F1 — rules engine | **Delivered.** `FreeCellState`, the deterministic deal, all five move types with supermove legality, the automatic cascade, and the automatic-finish search (`AutoFinish.kt`, restricted to banking and parking moves) are implemented and green under `.\gradlew.bat :games:freecell:rules:test`. `FreeCellSession` carries undo and the automatic-moves setting, mirroring `GameSession`'s shape. Not yet measured: the automatic-finish search's node cap (`MAX_AUTO_FINISH_NODES`), left at a reasoned-but-unmeasured default pending real endgame boards to test it against. |
| F2 — playable board vertical slice | **Delivered, portrait only — landscape is not implemented at all.** `FreeCellBoard` renders one adaptive layout regardless of orientation, closer to Spider's own S3a shape than to the twin portrait/landscape split `UI_SPEC.md` specifies for this game; a real rotation still preserves the board (`FreeCellViewModel` survives it as any `ViewModel` does), it just never draws the landscape arrangement `UI_SPEC.md` "Landscape" describes. Tap (`resolveTableauTap`/`resolveFreeCellTap`, in `:games:freecell:rules`), drag (including a supermove), Undo, Replay, and an unconfirmed New Game on an untouched deal are wired and covered by seven passing tests under `.\gradlew.bat :games:freecell:app:connectedDebugAndroidTest` (`GameScreenTest`, `WinTest`, `RotationTest`), plus manual verification on a real emulator. Card-flight animation followed later, in F8, the same way Klondike's own A1 preceded its A2a. `FreeCellGeometryGateTest` confirms the 32 dp / 22 dp floors from `UI_SPEC.md` "Board Geometry" at 320 dp and 360 dp, including a 19-card column fixture; they are no longer provisional. Not built: Settings, Hint, Statistics, Help, sound, and persistence — all later packages. |
| F3a — persistence and restoration | **Delivered.** `FreeCellActiveGameStore` wraps `core:storage`'s `ActiveGameRecordStore` with a `DealParameters<FreeCellDealParameters>` carrying the automatic-moves setting as it stood at deal time (mirroring Klondike's own `KlondikeDealParameters` — needed for replay to reproduce the same cascade, not for the deal's own shape, which FreeCell has no axis to vary), and `FreeCellLogCodec` (`:games:freecell:rules`) is the five-move-plus-undo/automatic-moves/auto-finish opcode alphabet the save is written in. `FreeCellViewModel` restores before showing an interactive board, discards an unreadable save with a dismissible notice rather than touching any other store, and saves after every commit, the automatic finish, and a foreground-to-background transition. Verified at the store level (`FreeCellActiveGameStoreTest`), on-device (`RestorationTest`, `RotationTest`), and by hand: a real force-stop and relaunch of both a debug build and a signed, minified release build each restored the exact board, free-cell contents, foundation, move count, and undo availability. |
| F3b — settings, statistics, win presentation | **Delivered.** `FreeCellSettingsStore` persists automatic moves, skip animations, handedness, sound, theme, and language; every row in `SettingsScreen` applies immediately (automatic moves live-toggles the session in play via `withAutomaticMoves`) and survives a restart (`FreeCellSettingsStoreTest`). `FreeCellHistoryStore` and `computeFreeCellStatistics` give `StatisticsScreen` one pool with no per-mode split, matching `RULES.md` "Free cells and difficulty"; the win dialog adds a personal-best line from the same aggregate. `StuckNotice` (offering Undo and New Game) appears whenever `isStuck` holds. Settings, Statistics, Help, and the stuck notice all close on Back and pause the timer via `setModalOpen`, verified on-device (`SettingsScreenTest`, `StatisticsScreenTest`, `StuckNoticeTest`, `ModalBackHandlingTest`, 14 new passing cases under `.\gradlew.bat :games:freecell:app:connectedDebugAndroidTest`, 22 total). |
| F4 — instrumented tests | **Delivered.** `.\gradlew.bat :games:freecell:app:connectedDebugAndroidTest` covers every item this package's own gate names — tap and drag including a supermove, undo, new game, restoration, rotation, both themes (`ThemeTest`), both handedness layouts (`SettingsScreenTest`'s action-bar reordering), and win presentation (`WinTest`) — 23 passing cases on a real emulator, no landscape case since F2 does not implement it. |
| RF — rules freeze | **Closed.** `rulesVersion`/`shuffleVersion` stay at 1. The deal contract's shuffle is `core:cards`' own shared, reference-vector-pinned `shuffleDeckIndices`; `dealGame` deals it round-robin exactly as documented and is exercised by `DealTest`. A new `canonicalStateHash(FreeCellState)` (`:games:freecell:rules`) and a fixed, hand-verified reference game from seed 2 — 25 player moves across four of the five move kinds, an automation-setting change, a real automatic cascade, and an undo — pin real gameplay output for the frozen engine (`ReferenceGame.kt`, `ReferenceGameTest`), re-asserted on-device. No open defect against building, free cells, supermove legality, foundations, or automatic moves. Any change to the frozen surface from here bumps the rules version and reruns F5 in full. |
| F5 — solver and certified deal catalog | **Delivered.** `games/freecell/solver` (DFS with transposition-table cycle detection over a symmetry-collapsing canonical hash) certified a real, committed catalog: 2,000 seeds (magic `FRCL`, catalog version 1, partition 0), generated via `:tools:catalog`'s `buildFreeCellDealCatalogs` from the first 2,500 ascending candidates (~80% solved) and independently replay-verified before being written. `.\gradlew.bat :tools:catalog:verifyFreeCellDealCatalogs` passes against the committed `games/freecell/app/src/main/assets/catalogs/freecell.catalog` and its manifest. `:solitaire:catalog`'s own format was generalized to support FreeCell's own magic and its no-draw-mode-byte header dialect (`docs/solitaire/CATALOG.md`), since it previously only ever produced Klondike's. The app loads and validates the bundled catalog at startup via `FreeCellCertifiedDealCatalog`, falling back to the non-playable Unrecoverable screen (with retry) on failure — FreeCell has no uncertified tier — and New Game draws from it through `FreeCellTraversalStore`'s randomized non-repeating position, verified end-to-end against the real bundled asset on a real emulator (`RealCatalogTest`) alongside synthetic-catalog success/failure coverage (`CatalogLoadingTest`, `UnrecoverableScreenTest`) and unit tests for the loader and traversal store — 29 instrumented cases total. `assertAppExcludesSolver` holds for `games/freecell/app` (it depends on `:solitaire:catalog` for reading and traversal, never on `:games:freecell:solver` or `:tools:catalog`). `FREECELL_VERSIONS.catalogVersion` is now 1. See `DEALS.md` "The solver" for the full solver-measurement finding (why 2,000 seeds rather than the 10,000 planning target) and the two rejected ordering alternatives. **Revised after first shipping:** the status row now shows a hand number after all (`UI_SPEC.md` "Status Row" and `DEALS.md` "App integration" both updated) — `FreeCellViewModel.dealNumber` is the seed's 1-based position in the catalog's own committed seed list, the same shape as Klondike's own `dealNumber`, and a separate thing from `FreeCellTraversalStore`'s own randomized traversal position, which still decides only which seed comes next and is still never shown directly. |
| F6 — on-device hint | **Delivered.** `games/freecell/solver`'s `hint(state, limits)` reuses the same DFS the catalog was certified with — stateless, unlike Klondike's own cached `HintEngine`, since this search is fast enough (measured, not guessed: 224 ms average, 2,986 ms worst case) that a cache would add complexity the timing does not need. Budget settled at 1,000,000 nodes / 5 seconds after measuring it against the real catalog; `HintSearchBudgetTest` proves all 2,000 certified deals resolve to Guidance from their fresh board within it — this package's own gate, passing (`.\gradlew.bat :games:freecell:solver:test`). The three outcomes are wired end to end: Guidance pulses the moving card(s) on the board (`drawHintHighlight`, reusing the shared primitive), No-solution and Inconclusive show a dismissible, auto-dismissing notice, and any board change (a move, undo, a fresh deal) clears a shown hint. FreeCell's own Inconclusive carries no ruleset-based suggestion the way Klondike's does — it has no ruleset system to draw one from, a deliberate scope choice given how rarely the outcome is expected to fire. Verified: solver-level unit tests plus 7 new instrumented cases (36 total) on a real emulator. **Revised after first shipping, twice:** an independent search per hint request, with no memory of the last one, had no way to avoid suggesting the exact reversal of the move that had just been played — followed hint after hint, a card visibly shuttled back and forth between two spots instead of progressing. The first revision passed the caller's real last move into `Solver.kt`'s existing exact-inverse penalty (`rootPreviousMove`), narrowing the failure to a single-move undo. The second, requested directly, made `HintEngine` stateful like Klondike's own: a certificate cache (`exactStateHash`, not the search's own symmetry-collapsed `canonicalSearchHash` — a cached move names specific column and free-cell indices, so matching has to mean the literal same board) carries the last winning line found across calls, dropping any round trip the line itself makes back to a repeated board, the same dedup Klondike's own `cacheCertificate` does. A player following hints now walks one continuous, self-consistent plan instead of getting a fresh, possibly-conflicting one each time; the root-level `rootPreviousMove` seeding from the first revision still guards the one search that runs after a cache miss. `HintSearchBudgetTest` now builds one fresh `HintEngine` per catalog seed, matching a new game's own reset. See `DESIGN.md` "Hint" for the full reasoning and what is deliberately narrower than Klondike's own engine (no dead-state cache, no shipped-solution priming). **A third revision:** the cached line was still the DFS's first-found line — 600 to over 5,000 moves on catalog deals — and following it read as a loop on a real game (seed 1924). Hint now searches with a weighted best-first search (`BestFirstSolver`, with the automatic cascade built in) under 100,000 nodes / 2 seconds and falls back to the DFS on what remains of the unchanged 5-second budget; a fallback line is shown one move at a time and never cached. Measured on the catalog's fresh boards: 2,000 of 2,000 still resolve to Guidance (`HintSearchBudgetTest`), all but one through the best-first search, at about 90 moves and 25 ms on average. `HintFollowTest` follows every hint to a win on four deals, including 1924, inside 150 hints with no repeated board; it fails against the DFS-only engine. **A fourth revision:** `HintEngine` gained `primeWithKnownSolution`, and a fresh deal now primes it from `assets/solutions.bin` — the shipped-solution priming the note above once said FreeCell did not have — so the very first Hint request of a game resolves from the cache with no search at all, mirroring Klondike's own `primeHintEngineWithStoredSolution`. Needed the catalog's own certificates to first be re-solved for length (`DEALS.md` "Shipped Solutions"): the original DFS-derived lines were themselves the "loop" problem this section already describes, and priming Hint with one of those directly would have reintroduced it through the front door. |
| F7 — performance and release | **Mostly delivered, one item open.** Manifest inspection (merged release manifest): no network or wake-lock permission, and the only provider/receiver present are AndroidX's own standard startup/profile-install boilerplate, identical to Klondike's — no app-defined background component. The full API 26 / current-API (37) emulator matrix passes at 36/36 instrumented tests on both (`Pixel_5_API_26`, freshly provisioned this pass, and the existing `Pixel_9_Pro_XL_Virtual`); running the matrix surfaced two real races (`RotationTest`, `WinTest` waiting on `waitForIdle` alone for an async catalog/auto-finish coroutine that a slower device outran), fixed with an explicit `waitUntil` on the real condition rather than tightened only for the symptom. Performance, measured on the current-API emulator (`adb`/`apkanalyzer` — no physical device available in this environment): release download size 2.1 MB (`apkanalyzer apk download-size`, budget ≤15 MB); cold start p95 792 ms over 20 trials (`adb shell am start -W`'s TotalTime, budget ≤1.5 s — a first-frame proxy, not a Macrobenchmark time-to-interactive measurement); memory 41.4 MB PSS during active drag play (`dumpsys meminfo`, budget ≤120 MB); idle CPU 0% over a 30 s idle window (`dumpsys cpuinfo`, budget <1%). **Open:** frame-rate compliance measured only ~21% of frames meeting the 60 Hz deadline during repeated drag (`dumpsys gfxinfo framestats`) against a ≥95% budget — almost certainly the emulator's own software-rendered compositing overhead rather than a real app cost (consistent with why Klondike's own acceptance protocol reserves exactly this measurement for a physical device), but not independently verifiable as a pass without one. This one budget needs confirming on physical hardware before F7's gate is fully closed; every other item above is real, measured evidence, not assumed. **Accessibility evidence added, matching Klondike's own Q1 gate**: `CardFaceFontScaleTest` proves FreeCell's card face (drawn by the same shared `core:ui` `CardArt.kt` every game uses) is pixel-identical across font scales, and `LargeFontScaleTest` proves the action bar at the 320 dp minimum width, and Settings and Statistics, stay fully reachable under `fontScale = 2` — four new passing tests on `Pixel_9_Pro_XL_Virtual`. `ContrastTest` (`:core:ui`) already covers FreeCell's contrast floors, since it is shared, unthemed platform code. |
| F8 — card-flight motion | **Delivered.** `FreeCellBoard` now renders every committed move — a tap, a drag including a supermove, the ordinary automatic cascade, undo, and the automatic finish — as a constant-speed flying-card animation from source to destination (`UI_SPEC.md` "Motion"), rather than jumping straight to the landed board, mirroring Klondike's own `Board` (a local `displayState` stepping forward only as each queued flight lands, one flight per pile-to-pile leg) adapted for FreeCell's simpler board (no face-down cards, no waste/stock, no foundation withdrawal to animate a departure from). The automatic finish's search has to run off the main thread (`FreeCellViewModel.runAutoFinishIfAvailable`'s own doc explains why), so its animation is driven by the already-proven move sequence the ViewModel hands the board once the search and its commit both finish (`pendingAutoFinish`), rather than computed inline the way the ordinary cascade is. A supermove's cards fly together as one rigid stack; Skip Animations removes every slide while keeping automatic transfers discrete. Five new instrumented tests (41 total, up from 36) prove an ordinary move, a supermove, an undo reversal, the automatic-finish sweep, and Skip Animations each actually animate (or, for the last, actually skip) rather than only asserting the eventual end state — `AutoFinishFlightAnimationTest` in particular proves the whole async search-to-animation chain end to end: if `pendingAutoFinish` were never consumed, or the board never released `isAutoFinishing` once its own animation of the sweep actually finished playing, the win dialog would never appear and the test would time out rather than pass. |

## Deterministic Deal Contract

Freeze this before F5 generates any catalog, the same ordering Klondike's own contract requires
(`docs/games/klondike/EXECUTION_PLAN.md` "Deterministic Deal Contract"):

- Deck and shuffle: the shared canonical deck order and `shuffleDeckIndices` (`core/cards`),
  unchanged — a single 52-card deck needs no new deck-size parameter, since 52 is already the
  shared default.
- Deal order: shuffled index 0 to tableau column 0, index 1 to column 1, ... index 7 to column 7,
  then wrapping back to column 0, until the deck is exhausted (`RULES.md` "The layout"). Every
  dealt card is face up.
- Canonical pile order for deterministic ties: free cells left to right, tableau columns left to
  right, foundations Clubs/Diamonds/Hearts/Spades.
- Catalog magic: ASCII `FRCL`.
- Format version 1 uses a 64-byte header, matching Klondike's overall size: magic at bytes 0–3;
  unsigned 16-bit format version and header size at 4 and 6; unsigned 32-bit catalog, rules,
  shuffle, and solver versions at 8, 12, 16, and 20; 1-byte partition id at 24 (always `0` —
  FreeCell ships unpartitioned, `DEALS.md`); 3 reserved zero bytes at 25–27; unsigned 32-bit
  record count at 28; the 32-byte SHA-256 payload hash at 32. There is no draw-mode byte: FreeCell
  has nothing for it to name.
- Payload: unsigned 64-bit seeds, stored ascending, deduplicated.
- Reject empty catalogs, unknown versions, header sizes other than 64, nonzero reserved bytes,
  out-of-order or duplicate seeds, length/count mismatches, and trailing bytes.
- Commit reference vectors mapping representative seeds to all 52 cards' pile assignments, the
  same role Klondike's and Spider's own reference vectors play.

Changing any rule that affects solvability requires a new rules version. Changing the deal order
requires a new shuffle version, even though the underlying shuffle algorithm is unchanged,
because the deal order is part of what makes a seed reproduce the same board.

## Work Packages

### F0 — Modules and Build Scaffolding

Depends on: the existing platform (`core/*`, `solitaire/*`).

- Create `games/freecell/rules` (Kotlin/JVM, no Android) and `games/freecell/app` (Android
  application), and add their `include(...)` lines to `settings.gradle.kts`.
  `games/freecell/solver` is **not** created here: it would sit empty until F5 needs it, and
  `docs/ARCHITECTURE.md` "Adding a game" is explicit that a skeleton module is dead weight to
  maintain rather than a preparation — Spider's own `:games:spider:solver` was likewise created
  only with its S5, not up front.
- Wire `games/freecell/app`'s only dependency for now: `core/ui`, for the shared theme the
  scaffold screen renders under. `core/session`, `core/storage`, `games/freecell/rules`, and
  `solitaire/ui` are added in F2 when the real board needs them (mirroring Spider's own S3a,
  which wired its rules module and `solitaire/ui` together with its first playable screen, not
  before); `solitaire/catalog` is added in F3a alongside persistence and traversal. Never the
  reverse dependency, at any point.
- Add `freecell` to `forbiddenNames` in
  `buildSrc/src/main/kotlin/finiteplay.shared-layer.gradle.kts`.
- Give the app its own `applicationId`, `app_name`, `game_title`, and launcher icon
  (`docs/APP_ICON_STYLE.md`), with a minimal scaffold screen — A1's role in Klondike's own plan —
  for F2 to replace with the real board.

**Gate.** All modules build on Windows with the checked-in wrapper. `games/freecell/rules` has no
Android imports. `assertNoGameReferences` passes on every shared module with `freecell` now in
`forbiddenNames`. `.\gradlew.bat check` is green from a clean checkout.

### F1 — Rules Engine

Depends on: F0.

- Implement `FreeCellState`: eight tableau columns, four free cells, four foundations,
  all-face-up by construction (there is no face-down bit to track at all).
- Implement the deterministic deal from the frozen contract above.
- Implement legal move generation and application for all five move types in `RULES.md` "Moves",
  including supermove legality (`(free + 1) × 2^empty`, with `empty` excluding the destination —
  `RULES.md` "Supermove").
- Implement the undo transaction boundary and move counting in `RULES.md` "Game Lifecycle" and
  "Scoring".
- Implement automatic foundation moves and the automatic finish, exactly as `RULES.md` states
  them, including the "never before the player's first action" rule shared with Klondike and
  Spider.
- Implement stuck detection and win detection.

**Gate.** Transition coverage is generated, not hand-listed, the same way Klondike's E1 gate
requires: for every move type crossed with the board conditions that make it legal or not
(free-cell occupancy, empty-column count, rank/color relationship, supermove length at and past
the legal maximum, and an empty destination with and without empty waypoint columns), the test
asserts expected legality, and the resulting case count is committed as a test constant. Undo
restores a player move and its automatic cascade as one transaction, preserves the counted moves,
and adds one. A random legal-action soak preserves every invariant in `RULES.md`
"Invariants" across many seeds. Automatic foundation moves and the automatic finish are proven
correct against constructed fixtures, including a fixture proving the finish never fires before
the player's own first action and never returns a card once it reaches a foundation.

### F2 — Playable Board Vertical Slice

Depends on: F1.

- `games/freecell/app`'s Compose screen: eight tableau columns, four free cells, four
  foundations, portrait and landscape, per `UI_SPEC.md`.
- Tap and drag, including a supermove drag that previews how far the lifted run can legally
  travel before the player commits — the same kind of preview Spider's own drag gives a lifted
  sequence (`docs/games/spider/DESIGN.md` "Interaction").
- Undo, unconfirmed New Game, and an elapsed timer.
- Board geometry assertions at 320 dp and 360 dp per `UI_SPEC.md` "Board Geometry": eight equal
  columns, the card ratio, minimum column gap, minimum card width, and exposed band — on the fresh
  deal and on deep-column fixtures up to the nineteen-card maximum.
- A debug-only fixture loader in `src/debug`, mirroring Klondike's near-win fixture, so a test can
  start from a committed position rather than a played-out game.

**Gate.** An instrumented test on an emulator drives a committed deal from a known seed through a
committed move script to a win, in both orientations: a tap move, a drag move, a supermove, Undo,
a rotation mid-game that preserves the board, and New. `FreeCellGeometryGateTest` passes at 320 dp
and 360 dp including the deep-column fixtures, and either confirms or corrects the provisional
numbers in `UI_SPEC.md` "Board Geometry" — whichever it finds, that document is updated to match,
per `CLAUDE.md` "Specifications are authoritative". Invalid actions preserve state. Playing a
game by hand in both orientations is smoke evidence alongside the gate, not the gate.

### F3a — Persistence and Restoration

Depends on: F2.

- Persist the active game through `core/storage`'s `ActiveGameRecordStore`. The deal-parameter
  payload carries no board-shape axis at all — no suit count, no draw mode — but does need the
  automatic-moves setting *as it stood when the deal was made*, the same way Klondike's own
  `KlondikeDealParameters` carries it alongside draw mode: replay re-runs the cascade from that
  starting value forward through the log's own `SetAutomaticMoves` entries, so restoring without
  it would replay a different game than the one saved.
- A move-log codec for the five-entry move alphabet plus undo.
- Restore before showing an interactive board; recover from a missing or corrupt save without
  touching any other store.

**Gate.** Process restart restores the exact board, free-cell contents, foundations, and undo
stack. A release-variant test round-trips save and restore with minification enabled.

### F3b — Settings, Statistics, and Win Presentation

Depends on: F3a.

- Settings: automatic moves, skip animations, handedness, sound, theme, language.
- Statistics on `core/session`'s shared aggregate: one pool, no per-mode split (`RULES.md` "Free
  cells and difficulty").
- Win presentation: moves, elapsed time, personal bests.
- The no-legal-moves notice offering Undo and New Game.

**Gate.** Settings apply immediately and survive restart. Displayed statistics match the
underlying aggregate. Every screen closes on Back and pauses the timer while open.

### F4 — Instrumented Tests

Depends on: F2, F3a, F3b.

**Gate.** `games/freecell/app:connectedDebugAndroidTest` on an emulator: tap, drag including a
supermove, undo, new game, restoration, rotation, both themes, both handedness layouts, and win
presentation.

### RF — Rules Freeze

Depends on: F1–F4.

The catalog is expensive to regenerate and every certificate depends on the exact rules and
shuffle, so it is certified only after real gameplay — automated and, ideally, by hand — has
exercised the rules, the same ordering Klondike's own RF states the reason for
(`docs/games/klondike/EXECUTION_PLAN.md` "RF — Rules Freeze").

- Declare the rules and shuffle contracts final; freeze `rulesVersion`/`shuffleVersion` at 1.
- Recommit reference vectors and the canonical state hash of a reference game played through the
  frozen engine.

**Gate.** No open defect against building, free cells, supermove legality, foundations,
automatic moves, or the deal contract. The Deterministic Deal Contract section above and the
implementation agree, verified by test. Any change after this point bumps the rules version and
reruns F5 in full.

### F5 — Solver and Certified Deal Catalog

Depends on: RF.

- Create `games/freecell/solver` (Kotlin/JVM, no Android) — deferred from F0, `docs/ARCHITECTURE.md`
  "Adding a game" — and add it to `settings.gradle.kts` and to `games/freecell/app`'s dependencies.
- Implement it starting from the DFS-with-transposition-table approach `DEALS.md` "The solver"
  proposes, and measure whether FreeCell needs more than that.
- Generate candidates ascending from a committed master seed, solve each, and independently
  replay every accepted line through the real `games/freecell/rules` reducer before shipping it
  (`DEALS.md` "Generation").
- Write the binary catalog (partition id 0), its manifest, a validation report, and the
  certificates.
- Add FreeCell's instance of `verifyDealCatalogs`: format, versions, count, hash, uniqueness, and
  certificate replay.

**Gate.** Every shipped seed replays to a win under the frozen rules. No timed-out, unknown,
duplicate, or failed candidate enters the catalog. Normal app startup only verifies and reads the
asset; it never solves. `assertAppExcludesSolver` holds for `games/freecell/app`.

### F6 — On-Device Hint

Depends on: F5.

- Build the bounded on-device search Hint runs (`DESIGN.md` "Hint"), reusing
  `games/freecell/solver`'s search machinery the way Klondike's Hint reuses
  `games/klondike/solver`'s.
- Tune its budget and any ordering weights against the real certified catalog, the way
  Klondike's `HintSearchBudgetTest` pins its own fit — measured here, not guessed, exactly as
  `DESIGN.md` "Hint" says it must be.
- Wire the three-outcome presentation (`UI_SPEC.md` "Hint").

**Gate.** A full-catalog fresh-board budget test, mirroring Klondike's `HintSearchBudgetTest`,
proves every certified deal resolves to Guidance from its raw board within whatever interactive
budget this package settles on. Guidance, no-solution, and inconclusive are each proven distinct
and each replay-verified before being shown.

### F7 — Performance and Release

Depends on: F4, F5, F6.

- Confirm the manifest declares no network or wake-lock permission and no background-capable
  component.
- Run the complete API 26/current emulator matrix.
- Measure release download size, cold start, play memory, frame rate, and idle CPU against
  `docs/PLATFORM.md` "Performance", the same budgets every game in this repo meets.
- Archive the release evidence: command outputs, catalog manifest, emulator/device matrix
  results, and performance measurements — the same evidence Klondike's `ACCEPTANCE.md` names for
  its own release; FreeCell has no separate acceptance document (`TODO.md`).

**Gate.** `.\gradlew.bat verifyDealCatalogs`, `testDebugUnitTest`, `connectedDebugAndroidTest`,
`lintDebug`, and `assembleRelease` all pass from a clean checkout, and every `docs/PLATFORM.md`
"Performance" budget is met.

### F8 — Card-Flight Motion

Depends on: F2.

- Build the constant-speed flying-card animation `UI_SPEC.md` "Motion" describes, reusing
  Klondike's own `Board`'s technique (`games/klondike/app/.../ui/game/Board.kt`): a local
  `displayState` that only steps forward as each queued flight lands, one flight per pile-to-pile
  leg, so a player's move, each automatic cascade transfer, an undo reversal, and the automatic
  finish's sweep each play individually rather than jumping straight to their result.
- Animate a supermove's cards as one rigid stack along the same path, the way Spider's own
  tableau-to-tableau moves already do.
- Wire Skip Animations to remove every slide while keeping each automatic transfer a discrete,
  individually visible step.
- Since `findAutoFinish`'s search has to run off the main thread
  (`FreeCellViewModel.runAutoFinishIfAvailable`'s own reasoning, `RULES.md` "Automatic Finish"),
  drive the sweep's animation from the already-proven move sequence the ViewModel hands the board
  once the search and its commit both finish, rather than computing it synchronously the way the
  ordinary cascade is.

**Gate.** An instrumented test on a real emulator proves each of the following actually animates
rather than only asserting the eventual end state: an ordinary tap-committed move, a supermove
flying as one stack, an undo reversal, and the automatic-finish sweep — the last also proving the
board only releases input once its own animation of the sweep has actually finished playing, not
once the search and commit alone have. A further test proves Skip Animations lands a move within a
few frames rather than over a real flight. No regression in the rest of the instrumented suite.

## Dependency and Parallelization Map

```text
F0  -> F1
F1  -> F2
F2  -> F3a
F3a -> F3b
F2 + F3a + F3b -> F4
F4  -> RF
RF  -> F5
F5  -> F6
F4 + F5 + F6 -> F7
F2  -> F8
```

## Execution Rules

- Implement one work package at a time on the critical path.
- End every package with a commit series that finishes on its green gate.
- Add a failing test before fixing every discovered rules or persistence bug.
- Do not generate the certified catalog before RF. If a rules change is unavoidable after RF,
  bump the rules version and rerun F5 in full.
- Do not start deferred items from `TODO.md` during first-release execution.
- Update this plan and the affected specification when implementation reveals a requirement
  change; do not hide scope changes inside code.
