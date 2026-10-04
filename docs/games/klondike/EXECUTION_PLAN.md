# MVP Execution Plan

Publisher: [FinitePlay LLC](https://finiteplay.org)

## Outcome

Ship an Android-only, offline Klondike Solitaire that satisfies `DESIGN.md`, `RULES.md`,
`UI_SPEC.md`, `DEALS.md`, and `ACCEPTANCE.md`: a game usable on API 26 and current Android,
release gates passing, and a release build with no network or background work.

## Status

Every package below was written before it was built and is left in the future tense, because
rewriting a plan into a report loses the record of what was intended. This table is the part
that is kept current — it is the only place in this file that says what is true today.

| Package | State |
|---|---|
| E0–E2b, D1a, D1s | Delivered |
| A1, A2a, A2b, A3 | Delivered |
| S1, S2 | Delivered |
| **Q1 — accessibility and UI hardening** | **Delivered, gate amended.** Screen-reader support was removed from scope (`docs/PLATFORM.md` "Accessibility"), striking the semantics and TalkBack items. `CardFaceFontScaleTest` proves card-face text is pixel-identical across font scales; `ContrastTest` (`:core:ui`) pins the 4.5:1 text and 3:1 graphic floors from `docs/PLATFORM.md` "Accessibility" for both themes' Material text roles, card ink, and the action/difficulty accents; `LargeFontScaleTest` proves the action bar, Settings, and Statistics stay fully reachable at `fontScale = 2` on the narrowest supported width, on a real emulator. All three ran green on `Pixel_9_Pro_XL_Virtual`. |
| **RF — rules freeze** | **Closed.** Rules, shuffle, and automation contracts final; `rulesVersion`/`shuffleVersion` frozen at 1. Taken without Q1's two remaining items, for the reason given under RF. Any change to the frozen surface from here bumps the rules version and reruns D1b in full. |
| **D1b — certified catalog** | **Delivered.** The post-`>= Ruleset.MEDIUM` ten-million-seed regrade produced six 10,000-deal catalogs and 50,000 replay certificates. `build-catalogs` wrote their manifest and `verifyDealCatalogs` passed for all 60,000 records. The app validates the bundled partitions before use, presents the non-playable recovery screen on a validation failure, and traverses the verified per-level assets. The ten-deal framing below is superseded — see the note under D1b. |
| **Q2 — performance and release** | **Outstanding.** There is no `:benchmark` module, no baseline profile, and no `ACCEPTANCE.md`-grade cold-start/memory/frame/idle-CPU evidence — those need a physical Pixel 9 Pro XL and this package's own Macrobenchmark/frame-timing harness, unavailable in every environment this plan has been executed from so far. The merged release manifest is confirmed clean (no network or wake-lock permission, no app-defined background component, matching FreeCell's own F7 finding). An emulator-only pass was also attempted, the same proxy FreeCell's F7 used, with mixed results: download size 7.4 MB (`apkanalyzer`, budget ≤15 MB) and play memory 38.7 MB PSS (`dumpsys meminfo` after a short synthetic session, budget ≤120 MB) are real numbers, comfortably inside budget. Cold start (`adb shell am start -W`, five trials: 2.9–6.5 s, rising rather than settling) and frame rate (`dumpsys gfxinfo` during synthetic `input swipe` gestures: 100% janky, 800–950 ms per frame) are not usable evidence — the swipe gestures likely never engaged the app's own drag detector, and this host's build/emulator load makes both measurements noise rather than signal, the same failure mode Klondike's own performance gate reserves physical hardware to avoid. No budget is asserted passed or failed from either number; they are recorded as an attempt, not evidence. |

Two things in this file are deliberately stale rather than wrong: the module names of the
original tree (below), and D1b's scale. Both are marked where they appear.

## Fixed Implementation Decisions

Module names below are the ones the work packages were written and delivered against.
The tree has since been restructured to host a family of games — `:app` is now
`:games:klondike:app`, `:game` is `:games:klondike:rules`, `:solver` is
`:games:klondike:solver`, and `:deals` split into `:solitaire:catalog` plus the shared
`:core:*` layers. `ARCHITECTURE.md` is authoritative for the current tree; the names here
are left as they were so the delivery record stays accurate.

- Keep `:app` as the Android/Compose module.
- Add `:game`, a Kotlin/JVM module with no Android dependencies.
- Add `:deals`, a Kotlin/JVM module for catalog parsing, traversal, and shared
  validation.
- Add `:tools:catalog`, a desktop-only Kotlin application for solving,
  certificate replay, catalog generation, and integrity reports.
- Add `:benchmark` only after the game flow and stable test tags exist.
- `:app` must not depend on `:tools:catalog`. A build task asserts the app
  runtime classpath excludes it, so solver code cannot ship.
- The solver runs as a forked process with its own heap and worker count. It
  never runs inside the Gradle daemon, at configuration time, or on the default
  `verifyRelease` and CI paths.
- Use immutable game snapshots and a single reducer as the authority for
  committed game transitions.
- Keep transient drag and animation state out of persisted game state.
- Hint runs the on-device search described in `DESIGN.md` "On-Device Hint
  Search" (superseded the original deterministic ranked-heuristic hint after
  the E2b gate below passed — see the note after that gate).
- Resolve automatic-foundation ties by checking waste first, then tableau
  columns left-to-right. Continue until no safe automatic move remains.
- Every deal starts on the raw dealt board at zero moves, whatever the
  automatic-foundation setting. Automation never runs before the player's own
  first action; that first cascade (if enabled) happens as part of that first
  action's transaction, the same as every cascade after it — there is no
  separate, non-undoable "setup" transition anymore. New/Replay confirmation
  uses `hasPlayerActed`, not a nonzero move count.
- A card the player moves from a foundation back to the tableau is parked:
  automation must not return it while it remains uncovered. The park clears when
  the card is covered, moved again, or the game is replaced. Parked state lives
  in `GameState` so undo and persistence carry it without special cases.
- The automatic finish is a transition the reducer offers and only the game
  screen invokes. Headless replay, `verifyDealCatalogs`, and the solver never
  invoke it, so it cannot change certificate replay. It is gated by the
  automatic-moves setting and starts only after a simulation proves a
  foundation-only sweep completes.
- A tableau tap resolves to the lowest-index legal tableau destination to the
  right of its own column, then a legal-but-unsafe foundation move, then the
  lowest-index legal tableau destination to the left. A waste tap keeps the
  older safe-foundation-first priority. Drag accepts any legal destination.
  Tap therefore never reports "invalid" for a move the engine considers legal.
- The timer runs exactly while the game has started, the app is foreground, no
  modal is open, and the game is not won. There is no separate wait-for-action
  rule after restoration.
- Undo restores the board to its pre-transaction state, does not rerun
  automation, and increments the current move count by one. It does not erase
  points already counted for the undone action or its automatic moves.
- Persist the active game as seed, catalog/rules/shuffle versions, elapsed
  duration, and an append-only primitive move log — the same encoding used for
  solution certificates. Every committed transaction appends one entry,
  including undo, and each entry records its automatic transfers so a mid-game
  automation toggle still replays correctly. Restoration replays the log forward
  through the reducer, rebuilding board, move count, and undo stack as it goes.
- Use separate atomic stores for active game, settings, catalog traversal, and
  history. Corrupt active-game data must not invalidate other stores.
- Give every game and terminal result a stable ID. History writes are
  idempotent upserts so a crash between active-game and history writes cannot
  duplicate a win or abandonment.
- Persist elapsed duration, never a continuously advancing background timer.
- Cards are content, not controls, and are exempt from the 48 dp minimum. They
  instead guarantee a card width of at least 40 dp and an exposed face-up band
  of at least 24 dp. Card face text scales with card size, never with the system
  font scale.

## Deterministic Deal Contract

Freeze this contract before any catalog is generated:

- Canonical deck order: Clubs, Diamonds, Hearts, Spades; Ace through King.
- Canonical pile order for deterministic ties: waste, tableau left-to-right,
  foundations Clubs/Diamonds/Hearts/Spades, then stock.
- PRNG: SplitMix64 with the seed as its initial state and unsigned 64-bit
  overflow modulo 2^64. Each output first advances state by
  `0x9E3779B97F4A7C15`, then uses the standard SplitMix64 mixing constants
  `0xBF58476D1CE4E5B9` and `0x94D049BB133111EB` with shifts 30, 27, and 31.
- Bounded selection for nonzero unsigned bound `b`: calculate
  `threshold = (0UL - b) % b`; draw until `value >= threshold`, then return
  `value % b`.
- Shuffle: Fisher-Yates from index 51 down to 1. At each index `i`, draw
  `j = bounded(i + 1)` and swap the cards at `i` and `j`.
- Deal shuffled index 0 first, in tableau rounds from left to right; the final
  card dealt to each tableau column is face-up. Shuffled indexes 28–51 form
  stock with index 28 as the first draw. The contract describes the raw dealt
  board; setup automation is not part of it.
- Catalog byte order: little-endian.
- Catalog magic: ASCII `KLDK`.
- Format version 1 uses a 64-byte header: magic at bytes 0–3; unsigned 16-bit
  format version and header size at 4 and 6; unsigned 32-bit catalog, rules,
  shuffle, and solver versions at 8, 12, 16, and 20; 1-byte draw mode at 24
  (`1` means draw-one); 1-byte partition id at 25 (`0` means the game ships one
  undivided catalog — `docs/solitaire/CATALOG.md` "Partitions"); 2 reserved zero
  bytes at 26–27; unsigned 32-bit record count at 28; and the 32-byte SHA-256
  payload hash at 32.
- Payload: unsigned 64-bit seeds, stored ascending.
- Reject empty catalogs, unknown versions or draw modes, header sizes other
  than 64, nonzero reserved bytes, out-of-order or duplicate seeds, length/count
  mismatches, and trailing bytes. An unrecognised **partition id is accepted**:
  what it means belongs to the game, and this layer must not reject a partition
  it has never heard of.

> **Amended when D1b was specified against the level catalogs**, before any catalog was
> generated — which is what this contract's own "freeze this before any catalog is
> generated" allows, and the last moment it does. Byte 25 was one of three reserved zero
> bytes and is now the partition id; the header size, every other field's offset, and the
> payload encoding are unchanged. No catalog binary had been written under the original
> reading, so nothing existed to be incompatible with.
- The JSON manifest records both the payload hash from the header and a
  complete-file SHA-256 hash.
- Commit reference vectors that map representative seeds to all 52 card IDs.

Changing any rule that affects solvability requires a new rules version.
Changing the PRNG, bounded-number method, deck order, or shuffle requires a new
shuffle version.

## Work Packages

### E0 — Build and Contract Foundation

Depends on: existing Android scaffold.

- Create the `:game`, `:deals`, and `:tools:catalog` modules.
- Add the `org.jetbrains.kotlin.jvm` plugin at the root, matched to the Compose
  compiler plugin version, and align the Kotlin stdlib with the version AGP's
  built-in Kotlin support resolves for `:app`.
- Add `gradle/libs.versions.toml` and move the existing inline `:app` versions
  into it. Every new dependency is declared there.
- Declare a JDK 17 Java/Kotlin toolchain for all modules rather than
  documenting a JDK, so compilation does not depend on the local default JVM.
- Add an aggregate `verifyRelease` task without replacing the individual
  commands in `ACCEPTANCE.md`.
- Add the CI workflow that runs the `ACCEPTANCE.md` commands it can run without
  a device: assemble, unit tests, lint, and `verifyDealCatalogs`, on the pinned
  JDK, with no network access beyond dependency resolution.
- Add the task that asserts `:app`'s runtime classpath excludes
  `:tools:catalog`.
- Add package boundaries and shared test-fixture builders.
- Encode the deterministic deal contract and empty versioned catalog schema.
- Freeze primitive certificate-move encoding and canonical state hashing,
  including pile order, face state, flips, cascades, draw/recycle, parked cards,
  and foundation-to-tableau moves. This encoding is also the active-game save
  format, so freeze it once for both uses.

Gate:

- All modules build on Windows with the checked-in wrapper.
- `:game` and `:deals` have no Android imports.
- Existing debug, lint, and test tasks remain green, and `assembleRelease`
  succeeds with minification and resource shrinking enabled.
- The classpath assertion fails when `:tools:catalog` is added to `:app`, proving
  the gate works.
- CI runs green from a clean checkout.

### E1 — Rules Engine

Depends on: E0.

- Implement cards, suits, ranks, piles, game versions, status, and immutable
  `GameState`.
- Add state invariants: 52 unique cards, valid pile ownership, valid face
  states, ordered foundations, and legal face-up tableau runs.
- Implement deterministic shuffle and the 28-tableau/24-stock deal.
- Implement legal move generation, validation, and application for tableau,
  waste, and foundations.
- Implement automatic exposed-card flips.
- Implement draw-one, empty-stock recycling, and unlimited passes.
- Implement win detection and replay metadata.

Gate:

- Transition coverage is generated, not hand-listed: for every ordered
  source/destination pile pair in canonical order, crossed with rank delta
  (−1, 0, +1, other), color relationship, destination emptiness, and source face
  state, the test asserts expected legality. Commit the resulting case count as a
  test constant so silently shrinking coverage fails.
- Shuffle reference vectors pass.
- Random legal-action sequences preserve all invariants.
- Draw-one recycling preserves order and never shuffles.

### E2a — Transitions, Automation, and the Solver Contract

Depends on: E1.

- Route taps and explicit drag destinations through the same legal-move API.
- Implement tap priority exactly as specified in `DESIGN.md`, including the
  legal-but-unsafe foundation fallback.
- Use the same safe-foundation predicate for taps, hints, and automation.
- Implement conservative automatic foundation movement with the fixed source
  ordering above, parked-card exclusion, and a setting that gates both ongoing
  automation and deal setup.
- Publish and document the engine surface the solver depends on. D1s and D1b
  need only this package, not scoring or hints.

Gate:

- Automation cascades deterministically, does not run immediately after undo,
  and never returns a parked card.
- Deal setup runs only when automation is enabled; with automation disabled the
  game starts at zero moves on the raw dealt board.
- Table-driven tap-priority tests cover each priority step, including the
  unsafe-foundation fallback and the invalid case where no destination exists.
- The engine surface used by the solver is documented and frozen.

### E2b — Transactions, Scoring, Undo, and Hints

Depends on: E2a.

- Group a player action and its automatic cascade into one undo transaction.
- Implement scoring for player actions, stock operations, automatic transfers,
  flips, invalid actions, hints, and undo.
- Emit the primitive move log from the reducer: every committed transaction,
  including undo, appends one entry with its automatic transfers.
- Implement deterministic hint ranking:
  1. reveal a face-down tableau card;
  2. make a safe foundation move;
  3. move waste to tableau;
  4. improve tableau by maximizing the resulting alternating face-up sequence,
     preferring an emptied column only when another accessible King-led
     sequence can use it;
  5. draw or recycle stock.
- Break hint ties by source pile order, then destination pile order. Exclude the
  inverse of the previous player move when another candidate exists.
- Keep a transient hint cursor: repeated requests advance and wrap through the
  ranked candidates; any committed board change, undo, new/replay, or process
  restoration resets it.
- Detect the terminal stuck condition: no legal move and no productive stock
  action. Report it as state; do not record a loss for it.
- Implement the automatic finish: expose `canAutoFinish`, proven by simulating a
  foundation-only sweep to completion, and an `autoFinish` terminal transaction
  that stops the timer, counts each transfer as one move, and ends undo.

Gate:

- One undo restores the complete board transaction and adds exactly one to the
  current move count.
- Initial automation counts its transfers but creates no undo record, timer
  start, or played-game flag.
- Hint tests prove ordering, tie-breaking, reversal avoidance, stock
  suggestions, and no state or score mutation.
- Replaying a move log through the reducer reproduces an identical board, move
  count, and undo stack, including logs that contain undo entries and an
  automation toggle. This de-risks S1 before storage exists.
- Stuck detection is proven on a constructed dead-end fixture and never fires
  while a legal move remains.
- `canAutoFinish` is true for a fully revealed sweepable board, false for a
  fully revealed board whose sweep blocks, and false while any face-down card
  remains. The sweep is atomic: it either completes all 52 cards or does not
  start.

The deterministic hint ranking above and its cursor were superseded after this gate
passed: the Hint action now runs the on-device search described in `DESIGN.md`
"On-Device Hint Search" (`:solver`'s `HintEngine`), which reports a search-proven move,
a proof of no solution, or an inconclusive result, rather than paging through ranked
local heuristics. `rankedHintCandidates`/`findHint` in `:game` remain as the cheap
"does any legal move exist at all" check gating whether the Hint action is enabled.

That search initially timed out on real fresh deals (single-configuration A* needed
millions of nodes against a 200k/8s interactive budget, and on-device the solver
thread's `MIN_PRIORITY` was measured to cut its throughput further under big.LITTLE
scheduling). The current pipeline tries every deterministic strategy from Trivial
through Expert, then full-legal DFS with an additional 300k-node allowance, then
full-legal withdrawal-enabled A* with its full 340k-node allowance. See `DESIGN.md`
"On-Device Hint Search"; `HintSearchBudgetTest` pins fresh-board catalog coverage.

The ordering coefficients are independent effective weights rather than an outer
multiplier around every signal. `tools/catalog` owns the offline `prepare-astar-tuning`,
`eval-astar-ordering`, and `export-astar-ranking` commands: they freeze replay-backed
`SOLVED` / full-exhaustion `PROVEN_LOST` / `UNKNOWN` labels, deterministic
train-validation-test splits, multi-budget coverage and PAR2 measurements, and grouped
legal-child features. The optional Python tools use resumable Optuna trials and CUDA
XGBoost only offline; no ML runtime or dependency reaches the app. A candidate can
replace the shipped A* ordering only after the full training run, validation selection,
one untouched-test evaluation, the whole-catalog hint budget gate, and on-device
throughput/memory gates.

### D1a — Catalog Format, Loader, and Traversal

Depends on: E0; E1 for shuffle reference vectors. Contains no solver.

- Implement the runtime loader with strict bounds and overflow checks.
- Implement non-repeating traversal with persisted start, coprime step, next
  position, and catalog version.
- Build synthetic 1-, 10-, and 100,000-seed assets from fixtures, without
  solving anything.
- Commit reference shuffle vectors and verify them on JVM and Android.

Gate:

- Loader tests reject truncation, overflow, empty/count mismatch, duplicates,
  bad hashes, unknown versions/modes, header sizes other than 64, and trailing
  data.
- Traversal visits every index exactly once per cycle for N = 1, 10, and
  100,000.
- Synthetic assets use the same loader, traversal, and saved-state schema.
- Shuffle vectors pass in the catalog tool, JVM tests, and Android tests.
- An invalid asset produces the non-playable Unrecoverable state; the app never
  falls back to an unverified random deal.
- A release-variant instrumented test loads a synthetic asset with
  minification enabled, so R8 problems surface here rather than at Q2.

### D1s — Solver Feasibility Spike

Depends on: E2a. Time-boxed. Runs in parallel with the A and S tracks.

- Implement the optimized desktop search state, legal move ordering,
  transposition table, cycle detection, and configurable search limits for
  draw-one, unlimited recycling, foundation back-moves, and the shipped
  automatic-move rules.
- Keep the retained A* state compact: byte-packed tableau columns and a single
  stock/waste pile with a boundary, sharing untouched storage between immutable
  children. This must remain a move-level search; strategy choices are a separate
  grading abstraction.
- Report only `solved`, `unsolved`, `timeout`, or `error`; admit only `solved`.
- Emit explicit certificates and replay them through the canonical `:game`
  reducer; the solver uses separate optimized transitions and must not validate
  itself.
- Certify a throwaway sample against the current, still-unfrozen rules version.
  Do not commit a catalog from this package.

Gate:

- Ten sample candidates are certified and replayed successfully, proving the
  approach can produce the MVP catalog.
- Median and p95 solve time per seed are documented, together with the projected
  wall-clock cost of regenerating ten certified seeds after a rules change.

Contingency:

- If search cannot reliably certify deals, stop feature expansion and improve
  state canonicalization and move ordering. Do not weaken the solvable-deal
  requirement or hand-label seeds.

### A1 — Playable Board Vertical Slice

Depends on: E2b and D1a.

- Replace the scaffold screen with a game state holder and responsive board.
- Render cached cards on Canvas with semantic overlays above it.
- Implement portrait layout, landscape right action rail, insets, stock,
  waste, foundations, tableau, moves, and timer.
- Keep foundation order fixed and expose basic semantic labels/actions from
  the first playable slice.
- Provide six labeled 48 dp actions—Undo, Hint, New, Replay, Statistics, and
  Settings—in the portrait bar and landscape right rail, with Undo and Hint
  nearest the right edge. A1 wires Undo and an unconfirmed New. Hint, Replay,
  Statistics, and Settings render as labeled, visibly disabled controls until
  A2a and A3.
- Add stock tap, one-tap move, drag/drop with touch slop, legal destination
  highlighting, and invalid feedback.
- Keep committed state separate from transient gesture/animation state.
- Cancel an active drag safely on rotation.

Gate:

- A catalog deal can be played to a win by tap and drag in both orientations,
  using Undo and New only.
- Layout assertions hold at 320 dp and 360 dp widths: seven columns, 5:7 card
  ratio, at least 4 dp column gaps, card width at least 40 dp, and an exposed
  face-up band of at least 24 dp, with gesture navigation and display
  cutouts/insets applied.
- Disabled controls still expose their label and disabled state to semantics.
- Invalid actions preserve state and score.

### A2a — Game Flow Presentation

Depends on: A1 and E2b.

- Present automatic cascades, atomic undo, hints, replay, and new-game
  confirmation.
- Add 120–160 ms card motion, skip-animations behavior, invalid feedback, hint
  pulses, and a skippable win animation.
- Present setup automation and run automation after successful actions except
  undo.
- Invoke the automatic finish when the engine reports it available, sweeping in
  at most 1.5 seconds with overlapping motion, skippable by any tap, and no
  slide and no added pause under skip animations (unlike the regular cascade,
  which still gets a short pause per step).
- A second hint request advances to the next ranked candidate.
- Confirm New or Replay only for an unfinished played game; show its moves and
  elapsed time. Skip confirmation after a win.
- Android Back closes the active modal before leaving the game.
- Add the near-win fixture for the `ACCEPTANCE.md` device smoke test in a
  `src/debug` source set. `buildConfig` is disabled, so source-set separation —
  not a build flag — keeps it out of release builds. Its entry point, and the
  archive export's, sit at the bottom of Settings rather than on the game screen,
  so no developer label appears on the board.

Gate:

- Instrumented tests cover tap priority, foundation-to-tableau tap and its
  parked-card behavior, legal and invalid drags, exposed-card flips, automation,
  the automatic finish, undo, hint progression, replay, new game, rotation,
  skip animations, and win.
- Any tap skips the win animation.
- Animation stops while backgrounded.
- The release variant contains no fixture entry point.

### A2b — Win Recording and Restoration-Safe Events

Depends on: A2a and S1.

- Implement the idempotent win and abandonment events keyed by stable game and
  result IDs.
- Present the win state from persisted result data so repeated presentation
  cannot record twice.

Gate:

- Exactly one result is recorded across rotation, process restoration, repeated
  win presentation, and crash injection at every write ordering point.

### A3 — Settings, Statistics, and Recovery Screens

Depends on: A2b and S2.

- Implement the Settings screen for automatic moves and skip animations, applied
  immediately.
- Implement the Statistics screen: summary, sample size, nearest-rank
  distributions, and reset confirmation.
- Implement the Recovery notice for a discarded corrupt save, which stays
  playable, and the separate non-playable Unrecoverable state for a failed
  bundled catalog.
- Implement the no-legal-moves notice offering Undo and New Game.

Gate:

- Every screen is reachable, closes on Back, and pauses the timer while open.
- Displayed statistics match the S2 aggregates, including the wins, losses, and
  games-played presentation.
- The Unrecoverable state exposes no gameplay affordance.

### S1 — Persistence, Lifecycle, and Deal Traversal

Depends on: E2b and D1a; may run in parallel with A1.

- Use Preferences DataStore for settings and catalog traversal.
- Persist the active game as seed, versions, elapsed duration, and move log, and
  restore it by forward replay through the reducer.
- Use separate versioned files and independent corruption handlers for active
  game and history.
- Save after every committed transaction, settings/history change, and
  transition to background. Serialize queued writes in reducer commit order.
- Restore before showing an interactive board.
- Replay retains the original seed and catalog/rules/shuffle versions, applies
  current settings, and does not advance catalog traversal.
- Implement the timer predicate, and define the first gameplay action as the
  first successful scored player action, including draw/recycle; hints, invalid
  actions, settings, and setup automation do not qualify.
- Recover from missing, corrupt, or unsupported active-game data without
  deleting settings, traversal, or history.
- Add the R8 keep rules the persisted formats require.

Gate:

- Process restart restores the exact board, score, timer, versions, and undo
  state.
- Background time never advances the game timer, and no separate
  wait-for-action rule is needed after restoration.
- All ten catalog indexes are visited once before repetition.
- Catalog upgrades preserve an active game and start a new traversal.
- Crash-injection tests cover every ordering point between result upsert,
  active-game replacement, and restoration.
- A release-variant test round-trips save and restore with minification
  enabled.

### S2 — Statistics and Settings Logic

Depends on: S1.

- Implement automatic-foundation and skip-animations settings.
- Implement wins, losses, win rate, streaks, bests, history, sample size, and
  nearest-rank min/p10/p50/p90/max distributions.
- Define the denominators exactly: `win rate = wins / (wins + losses)`, and
  `games played = wins + losses + 1` when an unfinished played game exists.
- Count abandonment only after the first gameplay action, and reset the current
  streak on any loss.
- Add statistics reset behavior behind confirmation.

Gate:

- Unit tests cover empty through large histories, repeated values,
  percentiles, streaks, abandonment, one-time win recording, and reset.
- Settings apply immediately and survive process restart.
- An unfinished played game raises games played without changing win rate.

### Q1 — Accessibility and UI Hardening

Depends on: A3.

- Complete semantic labels, actions, traversal order, and event announcements.
- Audit touch targets: the six controls are at least 48 dp; cards are exempt and
  instead meet the A1 geometry minimums.
- Confirm card face text ignores the system font scale while all other text
  honors it.
- Validate suit symbols, contrast, and large font scales.
- Validate both right-handed and left-handed portrait and landscape layouts.

Gate:

- Semantics-tree and accessibility-action tests pass.
- Face-down cards expose pile, position, and face-down state only — never rank
  or suit.
- Hint, invalid, undo, win, moved-sequence length, and destination
  announcements are verified.
- TalkBack manual pass succeeds on the Pixel 9 Pro XL.
- Normal and large-font screenshots for the board, Settings, and Statistics
  have no clipped essential content.
- Skip animations removes all specified movement and celebration, while automatic moves still play one at a time.

**Amended: screen-reader support was removed from scope** (`docs/PLATFORM.md`
"Accessibility"). The first four gate lines above are struck — they are all the screen-reader
surface, and the manual TalkBack pass with them. They stay written down because a reader
should be able to see that this package was cut rather than quietly passed. What remains, and
is what Q1 is now gated on:

- Interactive controls are at least 48 dp; cards are exempt and meet the A1 geometry minimums.
- Card face text ignores the system font scale while all other text honors it. **Proven**:
  `CardFaceFontScaleTest` renders the same card at `fontScale = 1` and `fontScale = 3` and
  asserts pixel-identical output.
- Suit symbols, contrast floors, and large font scales validate. Contrast is **proven**:
  `ContrastTest` (`:core:ui`) computes WCAG relative luminance and pins both themes' Material
  text roles, card ink, and action/difficulty accents past the 4.5:1/3:1 floors
  `docs/PLATFORM.md` "Accessibility" states.
- Both right-handed and left-handed portrait and landscape layouts validate.
- Normal and large-font screenshots for the board, Settings, and Statistics have no clipped
  essential content. **Proven**: `LargeFontScaleTest` renders the action bar at the 320 dp
  minimum width, and Settings and Statistics at full width, under `fontScale = 2`, and asserts
  every essential control and status text (the five action labels, the settings toggles, the
  statistics tabs and reset control) stays discoverable and on screen rather than pushed off
  or collapsed — run on a real `Pixel_9_Pro_XL_Virtual` emulator, not asserted from layout math
  alone.
- Skip animations removes all specified movement and celebration, while automatic moves still
  play one at a time.

### RF — Rules Freeze

Depends on: Q1.

The catalog is the most expensive artifact to regenerate, so it is certified
only after real gameplay has exercised the rules.

- Declare the rules, shuffle, and automation contracts final.
- Recommit reference vectors and the canonical state hash of a reference game
  played through the frozen engine.

Gate:

- No open defect against rules, automation, the safe-foundation predicate, or
  the deal contract.
- The contract section above and the implementation agree, verified by test.
- Any change after this point bumps the rules version and reruns D1b in full.

The rules-and-deal-contract portion of this gate is done: the Deterministic Deal
Contract's PRNG, bounded selection, and shuffle are pinned by
`DECK_SHUFFLE_REFERENCE_VECTORS` (re-asserted from `:game`, `:deals`, and on-device);
canonical pile order is pinned by the hint-ranking tests (`HintsTest`); the deal
itself, transition legality, automation, and the safe-foundation predicate each have
dedicated test coverage (`DealTest`, `TransitionMatrixTest`, `AutomationTest`,
`SafeFoundation`'s callers); and a new reference game — a fixed, hand-verified move
log from seed 2, replayed through `GameSession` and pinned by a frozen
`canonicalStateHash` (`ReferenceGameTest`, re-asserted on-device) — now does for real
gameplay output what the shuffle reference vectors do for the deal. No `FIXME`/`TODO`
marker exists anywhere in `:game` or `:deals`. `rulesVersion`/`shuffleVersion` stay at
1; any future change to what these vectors pin must bump them and rerun D1b in full,
per this gate.

**RF is closed. The rules, shuffle, and automation contracts are final as of this note,
and `rulesVersion` and `shuffleVersion` are frozen at 1.**

The gate, item by item, as it stood when the freeze was taken:

- *No open defect against rules, automation, the safe-foundation predicate, or the deal
  contract.* None. The one known defect in this area of the codebase is the
  `== Ruleset.MEDIUM` inheritance bug, and it is outside the frozen surface: it lived in
  `tools/catalog`'s `FastBoard`, which models a *grading* ruleset and decides which tier a
  deal is labelled, never what the game permits. It has since been fixed, which regraded
  nothing by itself and changed no legal move, so `rulesVersion` is unaffected — but it does
  leave the shipped seed lists one grader behind, and `TODO.md` "Regrade after the
  inheritance fix" tracks the rebuild that D1b should wait for.
- *The contract section above and the implementation agree, verified by test.* The
  Deterministic Deal Contract's PRNG, bounded selection, and shuffle are pinned by
  `DECK_SHUFFLE_REFERENCE_VECTORS`, re-asserted from the rules module, `:solitaire:catalog`,
  and on-device. The catalog header layout — including the partition byte amended into it
  when D1b was re-specified — is pinned field by field by `CatalogEncoderTest`. Canonical
  pile order is pinned by `HintsTest`. The deal, transition legality, automation, and the
  safe-foundation predicate each carry dedicated coverage (`DealTest`,
  `TransitionMatrixTest`, `AutomationTest`, `SafeFoundation`'s callers). `ReferenceGameTest`
  pins a hand-verified move log from seed 2 through a frozen `canonicalStateHash`, on JVM
  and on-device. No `FIXME`/`TODO` marker exists in the rules or catalog modules. Measured
  at the freeze: 172 tests green across `:games:klondike:rules`, `:core:cards`,
  `:solitaire:catalog`, and `:games:klondike:solver`, zero failures.
- *Any change after this point bumps the rules version and reruns D1b in full.* In force
  from now on. This is the whole point of the freeze and the reason D1b comes after it.

**On the Q1 dependency.** Q1's screen-reader half was removed from scope entirely
(`docs/PLATFORM.md` "Accessibility"), which retired the manual TalkBack pass and the
semantics coverage that were the bulk of what was outstanding. At the time RF was taken,
three items of the amended Q1 gate were open — card-face text was not asserted to ignore the
system font scale, the contrast floors were not validated, and the large-font screenshot
matrix was not validated — and RF was taken without them, deliberately. All three are now
closed (`CardFaceFontScaleTest`, `ContrastTest`, `LargeFontScaleTest`), so nothing remains
open under Q1; this note stays as the record of why RF did not wait for them.

The reason the plan made RF depend on Q1 is stated above it: the catalog is expensive to
regenerate, so the rules should be exercised by real gameplay first. That has happened —
the game is playable, has been played on device, and the rules carry the coverage listed
above. What was left of Q1 — font scale, contrast, and the screenshot matrix — could not have
made a legal move illegal, so holding the rules frozen against it would have delayed D1b for
evidence about type size rather than about rules correctness.

### D1b — Certified Ten-Deal Catalog

Depends on: RF, D1s, and D1a.

The delivered package makes `GameViewModel` validate and traverse the six bundled binary
catalog partitions (`docs/games/klondike/DEALS.md` "The certified catalog (D1b)"). A
validation failure opens the non-playable Unrecoverable screen rather than falling back to an
unverified deal. Legacy Kotlin lists remain as unit-test fixtures only.

- Generate candidate seeds from a committed master seed. Parallel workers may
  search candidates, but output accepts solved candidates in candidate ordinal
  order so regeneration remains byte-for-byte reproducible.
- Replay every certificate through the canonical `:game` reducer twice: once
  with automation enabled including setup, and once with automation disabled.
  Both must reach the win, so solvability is guaranteed under either setting.
- Generate candidates until ten unique draw-one seeds validate.
- Write the binary catalog, JSON manifest, validation report, reference
  vectors, rejected counts, and certificates.
- Add `verifyDealCatalogs` for format, versions, count, hash, uniqueness,
  reference vectors, and certificate replay.
- Commit all ten certificates so normal CI can replay the complete MVP catalog.
  Do not package certificates or reports in the app; larger future certificate
  sets may be retained as compressed CI artifacts.

Gate:

- Exactly ten unique seeds replay to a win under both automation settings.
- No timed-out, unknown, duplicate, or failed certificate enters the catalog.
- Normal app startup only verifies and reads the asset; it never solves.
- Catalog verification is deterministic and passes without network access.
- The `:app` classpath assertion still excludes `:tools:catalog`.

**Superseded, by decision, before any of it ran.** The ten-seed shape above was written
when a deal was either certified or not. The interim source overtook it: six graded levels,
ten thousand seeds each, and fifty thousand lines already replay-verified by
`build-levels`. Generating ten fresh seeds would delete the difficulty feature to prove a
format. What D1b certifies instead:

- **Six binary catalogs, one per level**, each a partition (`docs/solitaire/CATALOG.md`)
  carrying its level's ten thousand seeds ascending, plus a JSON manifest with the versions,
  counts, and both hashes per catalog. `tools/catalog`'s `build-catalogs` writes them; it
  selects nothing and solves nothing, because `build-levels` already did both.
- **`solutions.bin` is the certificate set**, already committed and already replay-verified
  seed by seed at build time. D1b does not re-derive it.
- **Insane ships uncertified**, as `DIFFICULTY_LEVELS.md` "Insane ships uncertified" states.
  The manifest marks it, and the gate does not ask it for lines it is defined not to have.
- **Presentation order leaves the payload.** Seeds are stored ascending and the randomized
  start and coprime step give each player their own order, which retires the fixed
  permutation `build-levels` applies to the Kotlin lists.

Revised gate:

- Every committed catalog loads, and its header versions, partition id, record count, and
  payload hash agree with the manifest.
- No seed appears in two levels, and every level's seeds are ascending and unique.
- A sample of certificates per certified level replays to a win under the current rules —
  this is what catches a rules change landing without a regeneration.
- Every seed in a certified level has a certificate; Insane is exempt by definition.
- Normal app startup only verifies and reads the asset; it never solves.
- Catalog verification is deterministic and passes without network access.
- The `:app` classpath assertion still excludes `:tools:catalog`.

### Q2 — Performance, Battery, and Release

Depends on: Q1 and D1b.

- Remove draw-path allocations and avoid broad recomposition.
- Add `:benchmark` with a release-like benchmark build type, plus Macrobenchmark
  and frame-timing coverage, after test tags stabilize.
- Generate and commit a baseline profile, and record its measured effect on
  cold start.
- Commit the scripted five-minute play sequence used for the memory and idle-CPU
  gates; reuse the frame benchmark's drag loop so "representative play" is
  reproducible.
- Confirm the manifest has no network or wake-lock permission and no
  app-declared/background-capable service, receiver, job, or scheduled work.
  Document any required merged-library initialization component.
- Run the complete API 26/current emulator matrix and physical-device smoke
  test.
- Archive the release evidence named in `ACCEPTANCE.md`.

Gate:

- Release download measured with `apkanalyzer` is at most 15 MB.
- Cold-start p95 is at most 1.5 seconds over 30 Pixel 9 Pro XL runs, with the
  baseline profile applied.
- Play memory is at most 120 MB PSS using the committed play sequence.
- At least 95% of measured frames meet the device deadline.
- Five-minute foreground-idle CPU average is below 1%.
- Backgrounding produces no timer advance, network, job, service, or wake lock.
- `verifyDealCatalogs`, unit tests, connected tests, lint, and release assembly
  all pass from a clean checkout.
- Archive raw release-build benchmark, frame, memory, CPU, device OS, and fixed
  refresh-rate outputs, then pass the physical-device release smoke test.

## Dependency and Parallelization Map

```text
E0  -> E1
E1  -> E2a, D1a
E2a -> E2b, D1s
E2b + D1a -> A1, S1
A1  + E2b -> A2a
A2a + S1  -> A2b
S1  -> S2
A2b + S2  -> A3
A3  -> Q1
Q1  -> RF
RF + D1s + D1a -> D1b
Q1 + D1b -> Q2
```

The riskiest work is de-risked early and committed late: D1s proves the solver
can certify deals before the UI exists, while D1b produces the shipped catalog
only after RF, so a rules fix found during A2a or Q1 costs nothing.

Recommended agent split after E2a freezes the engine surface:

- Engine owner: E1, E2a, E2b, and integration authority.
- Catalog owner: D1a, D1s, then D1b.
- Android UI owner: A1, A2a, A2b, A3.
- Storage/quality owner: S1, S2, then Q1/Q2 support.

Each owner works in disjoint modules or packages. The integrating agent runs
the aggregate gates after every merge and owns cross-module contract changes.

## Milestones

1. **Engine proven:** E0–E2b pass on JVM.
2. **Solvability feasible:** D1s certifies its sample and reports per-seed cost.
3. **Playable alpha:** A1 supports a complete manual game.
4. **Feature-complete beta:** A2a, A2b, S1, S2, and A3 gates pass.
5. **Accessible release candidate:** Q1 passes the emulator and device matrix.
6. **Rules frozen and catalog certified:** RF and D1b gates pass.
7. **MVP release:** Q2 evidence is archived and every acceptance gate passes.

## Execution Rules

- Implement one work package at a time on the critical path. Only these overlaps
  are permitted, and only once the upstream contract is tested and frozen:
  D1a with E2a/E2b, D1s with the A and S tracks, A1 with S1, and S2 with A2a.
- End every package with a commit series that finishes on its green gate.
- Add a failing test before fixing every discovered rules or persistence bug.
- Do not generate the certified catalog before RF. If a rules change is
  unavoidable after RF, bump the rules version and rerun D1b in full.
- Do not start deferred items from `TODO.md` during MVP execution.
- Update this plan and the affected specification when implementation reveals
  a requirement change; do not hide scope changes inside code.
