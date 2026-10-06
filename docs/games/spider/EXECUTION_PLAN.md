# Spider — Execution Plan

Publisher: [FinitePlay LLC](https://finiteplay.org)

Work packages, in order, each ending on an automated gate. A package is done when its gate passes — not when the code looks right. `docs/ARCHITECTURE.md` "Adding a game" is the procedure this plan follows.

## Status

The packages below stay in the future tense they were written in. This table is the only part of this file kept current, and the only place in Spider's docs that says what exists today (`CLAUDE.md` "Specifications are authoritative").

| Package | State |
|---|---|
| S1 — rules engine | **Delivered.** `:games:spider:rules`, gate green. |
| S2 — two-deck shuffle | **Delivered** with S1: `shuffleDeckIndices` takes a deck size, 52-card output unchanged. |
| S3a — playable board | **Delivered.** `:games:spider:app` exists; a suit count, a deal, tap-to-move, drag-to-move, row-deal, undo, New Game, and an elapsed timer are wired, and the drag path is pinned by an instrumented test (`GameScreenTest`) alongside the tap/undo/deal cases. The board now builds on the shared layer rather than its own copies — `core:ui`'s `cardPointerInput` and `BoardActionBar`, `core:session`'s timer rule and `formatElapsed` (`docs/ARCHITECTURE.md` "Moved later"). `UI_SPEC.md` now exists and states Spider's own geometry numbers, most of them still Klondike's inherited placeholders; `SpiderGeometryGateTest` closes the 320 dp/360 dp geometry-assertion half of the gate the spec calls for (it caught and fixed a real ten-column-at-320dp overflow along the way). `RowDealAndWinTest` asserts row-deal refusal on an empty column, an invalid tap (nowhere legal to go) leaving the board untouched, and the win dialog appearing when the last sequence completes, on a hand-built fixture through `SpiderViewModel.loadFixtureForDebugging` (mirroring Klondike's own near-win fixture) rather than a fully played-out game — the same shape Klondike's and FreeCell's own win tests use, since a real solved line runs 111–473 moves at every suit count here (measured from the committed solutions blob), too long for a real-gesture test to play move by move without the flakiness and runtime cost that implies. Extended to cover **every suit count** (a bank is a single-suit run and completing the eighth one wins regardless of how many suits the deck holds, so the identical fixture shape proves it at TWO and FOUR by naming a different `suitCount`) **in both orientations** — 4 new tests alongside the original 2, all 9 passing — closing the "for every suit count, in both orientations" half of the gate's own wording. Spider's own strings are now translated into the same 30 locales Klondike ships (`LocaleStringsCompletenessTest`, mirroring Klondike's own). |
| S3b — persistence | **Delivered.** `SpiderActiveGameStore` on `core:storage`'s `ActiveGameRecordStore`, with `SpiderDealParameters(suitCount)` as the deal payload and Spider's own two-opcode log codec. Restore precedes an interactive board; an unreadable save is discarded and reported. Verified by unit tests and by a real force-stop and relaunch of a minified release APK, which restored the board identically. |
| S3c — settings, statistics, win | **Delivered.** Settings, per-suit-count Statistics on `core:session`'s shared aggregate, Help, win presentation, and Hint. Portrait keeps five labelled actions with Help/Statistics as status-row icons, as Klondike does; landscape splits them across two rails. Spider's own strings are now localized into all 30 languages the platform supports. |
| S4 — instrumented tests | **Substantially delivered.** `:games:spider:app:connectedDebugAndroidTest`, 34 tests, all green: tap/drag/undo/deal (`GameScreenTest`), Restart and New Game each with their discard-confirmation path (`RestartTest`, `NewGameTest`), Settings including both handedness layouts (`SettingsScreenTest`), row-deal refusal / an invalid tap / the win dialog at every suit count, in both portrait and landscape (`RowDealAndWinTest`), a played game surviving persistence through a fresh `SpiderViewModel` (`RestorationTest`), a played game surviving `ActivityScenario.recreate()` through the real `MainActivity` wiring (`RotationTest` — the project's first rotation-survival test, Klondike has none yet either), and both themes resolving to visibly different palettes (`ThemeTest`). `LocaleStringsCompletenessTest` (JVM unit test, not part of the instrumented suite) checks translation completeness the same way Klondike's own does. Not yet done: restoration is orientation-independent (a store round trip, not a layout concern) so was not duplicated for landscape; rotation's landscape variant would need a real device orientation change (`androidx.test.uiautomator`, not currently a dependency) rather than the `LocalConfiguration` override the other tests use, and was left out rather than added under time pressure. **Accessibility evidence added, matching Klondike's and FreeCell's own Q1/F7 gates**: `CardFaceFontScaleTest` proves Spider's card face (the same shared `core:ui` `CardArt.kt` every game uses) is pixel-identical across font scales, and `LargeFontScaleTest` proves the action bar at the 320 dp minimum width, and Settings and Statistics, stay fully reachable under `fontScale = 2` — four new tests, verified passing in isolation on `Pixel_9_Pro_XL_Virtual`. `ContrastTest` (`:core:ui`) already covers Spider's contrast floors, since it is shared, unthemed platform code. The full-suite run is currently unreliable for reasons unrelated to these four tests or to this package — see S6's own in-progress solver work — so this row reports the new tests' own isolated result rather than a full-suite number. |
| S6 — four-suit certification campaign | **Tooling landed (items 1–4); no campaign run yet (item 5).** `SolverLimits.cacheCapacityPowerOfTwo`, `SpiderSolver.certifyStreamlined` (the unsound suit-discarding pass, structurally unable to report `EXHAUSTED`), `SolveResult.OUT_OF_MEMORY`, `campaignSolve`/`runFourSuitCampaign`, and the `campaign` CLI mode are all in and tested (`StreamlinedSearchTest` pins the never-`EXHAUSTED` contract). Still open: an actual campaign run finding and recording a four-suit solution, or the honest finding that none was found at the budgets this machine can reach. |
| S5 — deal catalog | **Delivered.** `:games:spider:solver` (greedy strategy playouts, DFS fallback) certifies ONE and TWO deals, and `tools/catalog` ships their catalogs with a replay-verified solutions blob and a `verifySpiderDealCatalogs` gate. FOUR's catalog, which this solver cannot decide, is certified by an external solver and imported (`docs/games/spider/DEALS.md`, `FOUR_SUIT_CATALOG.md`). **Hint now follows a stored solution**: `games/spider/rules/.../solution/CompactSolutionCodec.kt` bit-packs the verification blob into `games/spider/app/src/main/assets/solutions.bin` (`DEALS.md` "Solutions" has its size), `SpiderSolutionCatalog` loads it lazily, and `SpiderViewModel` primes `HintEngine`'s cache with the stored line on every fresh deal (New Game, Restart, cold-start restore-or-deal), so the first Hint request on a certified deal at any suit count resolves from the cache instead of a live search — closing the reported "Hint on Spider does not show winning move even with setting on" for TWO suits. **The live search behind a deviation was rebuilt since** (`PhaseSearch`, ahead of `SpiderSolver`; `DESIGN.md` "Hint" has the design and the `HintBench` measurements) and now wins most two-suit boards a player reaches off the shipped line, where it used to win few and reported some winnable ones as having no solution. |
| **S7 — performance and release** | **Mostly delivered, one item open.** Klondike's Q2 and FreeCell's F7 are this package's counterparts. Manifest inspection: the merged release manifest declares no network or wake-lock permission, and its only provider/receiver are AndroidX's own standard startup/profile-install boilerplate — no app-defined background component, matching Klondike's and FreeCell's own findings. Performance, measured on a physical Pixel 9 Pro XL (`adb`/`dumpsys`, the same device and methodology `docs/games/freecell/EXECUTION_PLAN.md`'s F7 reserved for its own open item): cold start p95 177 ms over 30 trials (`adb shell am start -W`'s TotalTime, budget ≤1.5 s); memory 52.5 MB PSS on a fresh idle launch, of which 18.4 MB is Graphics (`dumpsys meminfo`, budget ≤120 MB — well inside, and lower than FreeCell's own reading because Spider's board, like Klondike's, keeps most of its cards face down at rest rather than dealing the whole deck face up); idle CPU 0.2% over a 30 s idle window (`dumpsys cpuinfo`, budget <1%). That idle-CPU figure corrects an earlier reading of 3.7%/2.5% from this same investigation: re-measured twice more, with both `dumpsys cpuinfo` and a raw `/proc/<pid>/stat` jiffies-delta check across several trials, and it did not reproduce — attributed to transient post-install JIT/dexopt compilation rather than a real steady-state cost, the same false-positive pattern already seen and discarded in this package's cold-start trials. A code review during that investigation found Spider is alone among the three games in animating its card flights with three hand-rolled `withFrameNanos` polling loops (`SpiderBoard.kt`'s bank, move, and deal flights) rather than Compose's shared `Animatable`/`animateTo` clock the other two use; each loop provably exits once its own duration elapses, so it cannot explain idle drain, but it is worth folding onto the shared clock for consistency when S7's animation work is next touched. **Open:** frame-rate compliance was attempted via `dumpsys gfxinfo` during synthetic `input swipe` gestures, the same proxy Klondike's Q2 and FreeCell's F7 each tried and rejected as noise rather than signal — not usable evidence here either, for the same reason (synthetic swipes don't reliably engage the app's own drag detector). This one budget needs confirming with real gestures before S7's gate is fully closed. |

`UI_SPEC.md` now exists, written against what S3a actually built. `ACCEPTANCE.md` does not exist yet and is written with S4. An empty spec is worth no more than an empty module.

S3 was originally one package with three bullets and a gate that a non-playable app would still
pass — unit tests, lint, a release build, locale-completeness, none of which touches whether a
card can be moved. Split into S3a–S3c once the shared layer (`core:session`, `core:storage`'s
`ActiveGameRecordStore`, `solitaire:ui`) existed to build the three parts against, and each was
given a gate that actually fails on the thing it is about — a non-playable board fails S3a's
gate, a save that loses the suit count fails S3b's.

## S1 — Rules engine

The board, the deal, the legal-move set, and the one reducer that owns every transition, as `RULES.md` states them.

- `:games:spider:rules`, a Kotlin/JVM module depending only on `:core:cards`
- Deterministic two-deck deal from a seed, for all three suit counts
- `legalMoves`, `applyMove`, sequence detection, banking, stuck detection
- Invariant checks callable from tests: card conservation, face-up discipline, banked-sequence shape

**Gate.** `./gradlew.bat :games:spider:rules:test` green, including a random-play soak that asserts every invariant after every move across many seeds.

## S2 — Two-deck shuffle in `core/cards`

Spider is the second example that settles what belongs in the shared layer. The deck-size generalisation is the only change the shared layer needs, and it must leave Klondike's output byte-identical.

- `shuffleDeckIndices(seed, size)` with the 52 default preserved

**Gate.** `:core:cards:test` green, Klondike's existing reference vectors untouched, and `assertNoGameReferences` still passing on `core/*`.

## S3a — Playable board vertical slice

Depends on: S1. The first package that produces something a player can do anything with: a
suit count chosen, a deal on screen, a full game played by hand to a win.

- `:games:spider:app`, one activity, its own `applicationId`, `app_name`, launcher icon.
  Depends on `:core:session` for the reducer/undo/log shape and `:solitaire:ui` for tableau
  geometry — ten columns, Spider's own `TableauGeometryConfig` rather than Klondike's numbers
  (`docs/ARCHITECTURE.md` "Adding a game").
- `SpiderSession`: `core:session`'s `Session<SpiderState, SpiderLogEntry>`, carrying nothing
  beyond commit/undo — no automation, no auto-finish, no hint cursor, because Spider has none
  of those (`RULES.md` "What Spider does not have").
- The suit count is a setting, defaulting to two; New Game deals at it without asking. Changing
  it applies to the next new game, since the count is fixed the moment a deal is made
  (`RULES.md` "Suit counts").
- Canvas board: ten columns, the stock, and the eight banked-foundation slots, in portrait and
  landscape.
- Tap plays a card where it can go; drag chooses, and the interface shows how far a lift
  extends before the player commits (`DESIGN.md` "Interaction") — `sequenceStart` already
  computes the answer; S3a is the package that draws it.
- Deal a row on tap; refused with an explanation while any column is empty, never silently
  doing nothing (`DESIGN.md` "Interaction"; the legality itself is `RULES.md` "The stock").
- Undo, unlimited.
- A notice that the deal is uncertified and may not be winnable — true of every suit count until
  S5 exists for it (`TODO.md`). Not deferred to S3c: a player reaches a played game in this
  package, before Settings or Statistics exist to carry the notice instead. On the board in
  portrait; in landscape it is carried by Help alone, since a permanent full-width band is the
  most expensive row on a short screen and the statement is one tap away either way.

**Gate.** A deal at each suit count is played to a win by tap and drag, in both orientations.
Board geometry assertions at 320 dp and 360 dp: ten equal columns, the card ratio, minimum
column gap, minimum card width, and exposed face-up band (Spider's own numbers, in `UI_SPEC.md`
once this package writes it). Row-deal refusal on an empty column is asserted, not merely
implemented. Invalid actions preserve state.

## S3b — Persistence and restoration

Depends on: S3a.

- Persist the active game through `core:storage`'s `ActiveGameRecordStore`, with
  `SpiderDealParameters(suitCount)` as the deal-parameter payload — the seam that makes
  restore return the deal actually in play rather than the same seed at a different suit
  count (`docs/PLATFORM.md` "Persistence").
- A `SpiderLogEntry` codec for the two-move alphabet (`Move.TableauToTableau`, `Move.DealRow`)
  plus `Undo`, in Spider's own encoding — move-log encoding stays with the game
  (`docs/ARCHITECTURE.md`).
- Restore before showing an interactive board. Recover from a missing or corrupt save without
  touching any other store.

**Gate.** Process restart restores the exact board, banked counts, undo stack, and suit count.
A release-variant test round-trips save and restore with minification enabled.

## S3c — Settings, statistics, and win presentation

Depends on: S3a, S3b.

- Settings: suit count (changing it starts a new game at that count, asking first when a played game would be lost), skip animations, handedness, sound, theme,
  language — no draw mode, no difficulty setting; Spider has neither (`RULES.md` "What Spider
  does not have").
- Statistics kept per suit count, never blended (`DESIGN.md` "Scoring and statistics"), built
  on `core:session`'s `PercentileDistribution` and `filterByPeriod` the way Klondike's own
  aggregation is, with Spider's own wins/losses/streak logic on top.
- Win presentation: moves, elapsed time, personal bests for that suit count.
- The uncertified-deal notice from S3a stays visible wherever a deal is in play; Statistics
  and Help say plainly that no deal here is proven winnable.

**Gate.** Settings apply immediately except suit count. Statistics are correct per suit count
and never blend across counts. A game finished at one suit count never touches another's
record.

## S4 — Instrumented tests

Depends on: S3a, S3b, S3c.

**Gate.** `:games:spider:app:connectedDebugAndroidTest` on an emulator: tap, drag, undo, new game, restoration, rotation, both themes, both handedness layouts, win presentation.

## S5 — Deal catalog

Spider ships uncertified until this lands, and says so in the interface rather than implying every deal is winnable.

- A solver good enough to certify a deal, or a documented decision to ship one-suit only until there is one
- Catalog generation in `tools/catalog`, traversal via `:solitaire:catalog`
- `assertAppExcludesSolver`-style gate so the generator cannot reach the app classpath

**Gate.** `verifyDealCatalogs` for Spider, plus the layering gates.

## S6 — Four-suit certification campaign

**Goal: find winning lines for four-suit deals offline, and measure what finding one costs.** Not a
hint feature and not an on-device concern — this package exists so a four-suit catalog can ship
deals that are *provably* solvable (S5's gap), and so `#2`'s shipped-solution work has lines to
ship. Nothing here may change the interactive Hint budget or any Android code path.

Four-suit currently measures **0 deals decided out of 1,000** at the interactive budget, and 0 of
32 at twenty times it (`DEALS.md` "The solver"). Published work solves this same game — 10 piles,
two decks, four suits, same rules — to ~97% *proven* winnable with **plain DFS and no move
ordering at all**, at one hour per deal (`PUBLIC_STRATEGY_RESEARCH.md` "Academic sources"). A close
read of that solver's source against ours says the difference is not the heuristics we have been
investing in:

| | reference solver | ours, today |
|---|---|---|
| move ordering | **none** | playout scoring, beam(3), A\* weights |
| dominances | none for Spider (`two_decks` disables them) | none |
| transposition store | exact states, `operator==` | 64-bit hashes |
| cache capacity | **100,000,000 states**, LRU eviction | grows, but… |
| cache lifetime | one cache per solve | **cleared at every stage boundary** |
| effective cache | 100M | **≤400k per stage** |
| suit reduction | **discards suit entirely**, as a streamliner | sound relabelling only (≤24×) |
| two-phase | streamliners at 1/10 budget, then exact | none |

So the two candidate levers are **cache size** and a **lossy suit streamliner**, not better move
ordering. Both are cheap.

### Machine characteristics this package is sized against

Ryzen 7 7800X3D — **8 physical cores / 16 threads**, **96 MB L3** (3D V-Cache), **32 GB RAM**.

**The workload is CPU-bound, not memory-bound.** Each node does one hash probe against several
hundred to a few thousand operations of real compute: `canonicalHashOf` alone is three passes over
the cards plus a ten-element sort, `generateMoves` scans every column, and the beam's
`evaluateMove` runs an apply/`boardScore`/`generateMoves`/undo per candidate. There is direct
evidence: making the hash ~4× more expensive (adding suit canonicalisation) *reduced* nodes per
deal from 755k to 517k at a fixed wall clock, which only happens when hashing is a material share
of node cost.

**Parallelising across hands (`Survey.run`, `campaign`) is the right default** — embarrassingly
parallel, already implemented, and scales with cores with no shared state at all.

**Correction: parallelising *within* one search was ruled out here on reasoning that measured
wrong.** This section originally said not to attempt it — a concurrent transposition table on the
hottest random-access structure was expected to cost more in contention than it bought, on top of
wildly unbalanced subtree sizes and duplicated work. A spike measured that reasoning wrong for this
workload: twelve threads sharing one lock-free (CAS-based), sharded transposition cache on the
*same* deal ran **~7x** the nodes per second of one thread on the same deal — not the near-zero
speedup contention would predict — reproduced twice (7.12x, 7.07x). Two things made the difference
the original reasoning didn't have: work-stealing over several hundred small subtrees rather than a
few huge ones (so imbalance averages out instead of stalling on one giant branch), and sharding the
cache across sixteen independent arrays (which turned out to be necessary regardless of contention —
a single Java array is indexed by a signed 32-bit int, so one array tops out at 2^30 longs, ~8.6GB;
several arrays are the only way to reach a multi-thread run's larger memory budget at all).

This is now real, shippable code: `ConcurrentTranspositionCache`, `parallelSolve`/`parallelCertify`
(`ParallelSpiderSearch.kt`), and `runFourSuitCampaignParallel`, reachable as `campaign-parallel`
alongside the unchanged `campaign` — the two modes answer different questions (survey many deals vs.
crack one specific hard one) and neither replaces the other. The parallel cache is deliberately
**non-evicting**, unlike `GenerationalLongHashSet`: coordinating generation rotation across many
concurrent writers safely is a materially harder problem than the single-threaded case, and the
single-threaded bounded campaign's own measurement (below) — ~17M unique states over 1.46 billion
node visits, ~1.2% — says a generously-sized fixed cache should not need eviction within one hour
even at several times that node rate. Its own safety valve (never spin on a full shard; report the
state as unseen and flag `saturated` instead) means an undersized budget degrades to redundant work
rather than a hang or a wrong answer, mirroring every other approximation in this file.

One incident building this, worth recording because it is a repeat: the same shape as the
deadline-overrun incident below (a `for` loop noticing a child was cut short instead of stopping
right there) was reintroduced independently in the new parallel search code, and cost thirteen
minutes across eight threads before a thread dump caught it — worse than the single-threaded version
of the same mistake, since every thread pays the unbounded-unwind cost at once. Fixed the same way
(propagate immediately), pinned by `ParallelSearchDeadlineTest`.

**Memory budget — corrected against what actually runs, not just this machine's total RAM.** The
first version of this section reasoned from the machine's 32 GB and left ~24 GB for caches, giving
~90M states per thread at 16 threads. That arithmetic never checked what JVM heap the campaign
actually runs in: `:games:spider:solver`'s `application` block hardcoded `-Xmx6g`, six times
smaller than assumed. The gap was not academic — an early campaign run set `maxNodes =
2,000,000,000`, which at `LongHashSet`'s 8 bytes/entry requests up to **16 GB for a single thread's
cache alone**, and every worker OOMed. The heap is now raised to `-Xmx20g` (`build.gradle.kts`),
and the campaign CLI's own `maxNodes` default is sized against it directly — 100,000,000 nodes × 8
bytes × 16 threads ≈ 12 GB, leaving headroom for the JVM, GC, and everything else sixteen
concurrent searches allocate (`SolveResult.OUT_OF_MEMORY` reports it as data if a deal still
exceeds this, rather than crashing the run). **`maxNodes`, not `cacheCapacityPowerOfTwo`, is what
actually bounds memory** — the latter only sizes the cache's *initial* array; `LongHashSet` grows
past it on demand regardless, so it is a performance hint, not a ceiling. Fewer threads can safely
raise `maxNodes`; more threads must lower it. L3 residency is a secondary effect here (96 MB holds
~6M entries, and 16 threads sharing one 96 MB L3 all spill regardless), so prefer core utilisation
over keeping the table small.

**A second, unrelated bug surfaced by the same incident, now fixed regardless of the above:**
`LongHashSet`'s open-addressing probe only visits every slot when its backing array size is an
actual power of two. A malformed `cacheCapacityPowerOfTwo` (a command-line typo passing a literal
array size instead of a power of two) broke that invariant and spun a search thread forever — a
real occurrence, not a hypothetical one, and it ran undetected for 1h24m before being noticed and
force-killed. `LongHashSet` now rounds its requested capacity up to the next power of two rather
than trusting the caller, and `runFourSuitCampaign` prints a `#`-prefixed heartbeat every 30s naming
which seed each worker is still on and for how long, so a stuck or merely very slow deal is visible
within seconds rather than silent for the length of its own budget.

### The depth cap stays at 600, deliberately

`MAX_DEPTH = 600` is **not** to be removed. It is a product constraint as much as an implementation
one: `#2` ships a solution for a player to follow tap by tap, and a line longer than ~600 moves is
not something a person will work through. A four-suit deal only reachable by a longer line is not
useful to this product even if a solver could prove it. Record solution lengths (below) so `#2`
knows what it is being asked to present.

### Work items

1. **Long-lived, large transposition cache. Done.** `SolverLimits.cacheCapacityPowerOfTwo`
   (default `1 shl 20`, so every existing caller — including `HintEngine`'s — is byte-for-byte
   unaffected) sizes `SpiderSolver`'s `visited`/`streamlinedVisited` caches' initial backing array;
   `LongHashSet` already doubles from there as needed, so this only saves the early rehashes on a
   run expected to need a large one. A `beamWidth = 0, playouts = 0` run already gets one
   uninterrupted cache for the whole exact search — the beam stage's own clear-then-search block is
   simply skipped, so no change to the clearing logic itself was needed. `SolveResult.OUT_OF_MEMORY`
   reports a cache that outgrew the JVM's heap as data instead of crashing the run.
2. **Lossy suit-discarding streamliner. Done.** `streamlinedHashOf` (`BoardHash.kt`) maps every card
   to rank alone, mirroring the reference solver's `ANY_SUIT` reduction — **unsound**, since it
   merges positions differing in whether a run is liftable, so it may only ever cause a failure to
   find, never a false claim. It runs only inside `SpiderSolver.certifyStreamlined`, against its own
   cache (`streamlinedVisited`, never the exact search's `visited`), and that function is
   structurally unable to return `EXHAUSTED` — every path out of it ends in `SOLVED_BY_SEARCH` or a
   result that claims nothing, enforced by the function's own shape rather than by a caller
   remembering to check. `StreamlinedSearchTest` pins this on a board the *sound* search does
   correctly report `EXHAUSTED` for.
3. **Two-phase run. Done.** `campaignSolve` (`FourSuitCampaign.kt`) tries `certifyStreamlined` at a
   tenth of the caller's time budget first, accepts only a solved result from it, and falls back to
   the exact `solve` with the full budget and cache otherwise — the reference solver's `SMART` shape.
4. **Instrumentation. Done.** `CampaignRecord` carries seed, suit count, wall time, nodes, peak
   cache size, which phase (if either) solved it, solution length for a solved deal, and — for an
   unsolved one — whether it ended on nodes, time, exhaustion, or memory. `runFourSuitCampaign`
   prints one CSV line per deal as it finishes (`CampaignRecord.CSV_HEADER`), reachable via
   `./gradlew.bat :games:spider:solver:run --args="campaign <deals> [maxNodes] [maxMillis]
   [playouts] [ONE|TWO|FOUR] [cacheCapacityPowerOfTwo]"` — a `campaign` first argument rather than a
   second `main`, since the `application` plugin's `mainClass` is fixed to `Survey.kt`'s facade.
5. **Campaign runs — not yet done.** Smallest first, each recording the above: 16 deals × a
   generous budget to find *any* four-suit solution at all; then widen once one exists.
6. **Bounded, evicting transposition cache. Done.** `SolverLimits.maxCacheEntries`, when set,
   switches `visited`/`streamlinedVisited` from the unbounded `LongHashSet` to
   `GenerationalLongHashSet` — the closest practical match to the reference solver's own
   100,000,000-state LRU cache, sized so a genuine one-hour, single-deal, single-thread attempt
   (the reference solver's own shape) fits a real machine's memory instead of growing without
   bound for the whole hour. Eviction here means dropping a whole generation (default 8) at once
   rather than tracking exact per-entry recency; the correctness argument for why forgetting an
   entry can only cost extra nodes, never hide a win or falsify `EXHAUSTED`, is on the class
   itself and does not need re-deriving here.

   **Incident #4, found building this: every recursive search could overrun `maxMillis` by
   orders of magnitude, independent of the cache.** `search`, `beamSearch`, and
   `streamlinedSearch` only call `System.nanoTime()` every 1024 nodes (the syscall has a real
   cost at these node rates), but once that sampled check *did* detect the deadline had passed,
   only the one call that happened to land on it returned early — its caller's for-loop treated
   that `-1` exactly like "no solution down this branch" and tried the next sibling, which
   recursed fresh and would not itself notice the clock until its *own* count landed on a 1024
   boundary, potentially diving arbitrarily deep first. Across a stack of untried siblings at up
   to `MAX_DEPTH` levels, a 2-second budget turned into a search still running two minutes later —
   reproduced directly in a fast JUnit test, then pinned via `jstack` showing genuine `RUNNABLE`
   recursion (not a deadlock) roughly `MAX_DEPTH` frames deep. This affected every existing
   caller, including the interactive Hint path, not just the new bounded cache — it was only this
   package's fast, small-budget reproduction that made the overrun obvious enough to isolate.
   Fixed with a sticky `deadlineExceeded` flag: the sampled check sets it once, and every point a
   search already re-checks its node budget after backtracking now also checks this flag (a plain
   boolean read, no syscall), so a timeout propagates to the top on the very next check at every
   level instead of drifting. `DeadlineTest` pins the fix directly against a real four-suit board
   with a generous node budget, so only the deadline itself can be what stops the search.
7. **Receding-horizon catalog search. Implemented, not yet effective on FOUR.**
   `SpiderSolver.certifyGuided` uses the existing mutable board, packed moves, undo records, and
   hashes to search a reduced depth-13 tree, commit its best short path, and repeat. It keeps only
   the longest suited run per source, one symmetric empty destination, same-suit-first ordering,
   a bounded generational cache retained across horizons, committed-move forcing, preferred-suit
   variations, bounded return points carrying alternate high-scoring paths, and independent
   reducer-replayable certificates. It is opt-in and remains outside
   Hint. FOUR seed 1 still failed at 200,000,000 nodes across sixteen attempts (1,000,000 nodes per
   horizon), so it does not satisfy this package's solve gate and is not wired into catalog builds.

### Gate

`:games:spider:solver:test` green, including a test that the streamliner mode is never reachable
from a code path that can return `EXHAUSTED`, and `CanonicalHashTest`'s existing invariance
properties still passing for the sound mode. Plus: **at least one four-suit seed solved and
replay-verified through the real reducer**, with its node count and solution length recorded in
`DEALS.md`. If no four-suit deal solves at any budget this machine can reach, that is a real
finding and belongs in `DEALS.md` too — with the numbers behind it — rather than a silent absence.

### Out of scope

No Android code, no change to `HINT_SOLVER_LIMITS` or the Hint feature's one-suit gating, no removal
of the depth cap, no dominances (the reference solver has none for Spider, so there is no evidence
they help here). Parallelising within a single search was originally listed here too; the section
above records why that changed.

## S7 — Performance and Release

Depends on: S4. The counterpart of Klondike's Q2 and FreeCell's F7 — every game in this repo meets
the same `docs/PLATFORM.md` "Performance" budgets, so each carries the same category of release
evidence, even though this package was not written down until parity with the other two games'
plans made the gap visible.

- Confirm the manifest has no network or wake-lock permission and no app-declared/background-capable
  service, receiver, job, or scheduled work, the same check Klondike's Q2 and FreeCell's F7 each run.
- Measure release download size, cold start, play memory, frame rate, and idle CPU against
  `docs/PLATFORM.md` "Performance".
- Run the complete API 26/current emulator matrix.
- Archive the release evidence: command outputs, catalog manifest, emulator/device matrix results,
  and performance measurements — the same evidence Klondike's `ACCEPTANCE.md` and FreeCell's own F7
  each name; Spider has no separate acceptance document, the same precedent FreeCell follows
  (`TODO.md`).

**Gate.** `.\gradlew.bat verifySpiderDealCatalogs`, `testDebugUnitTest`, `connectedDebugAndroidTest`,
`lintDebug`, and `assembleRelease` all pass from a clean checkout, and every `docs/PLATFORM.md`
"Performance" budget is met.

**Confirmed so far:** the merged release manifest (`:games:spider:app:assembleRelease`'s packaged
manifest) declares no network or wake-lock permission, and its only provider/receiver are AndroidX's
own standard startup/profile-install boilerplate — no app-defined background component. Everything
else needs a physical device, unavailable in every environment this plan has been executed from so
far.

## Deliberate order

The rules engine comes first because every later package is stated against it, and because it is the only part that can be finished and proven without an emulator. The catalog comes last because a solver is worth building only once the game it certifies is playable — and because an uncertified Spider is an honest product, where an uncertified Klondike would not have been: Klondike's whole promise was a winnable deal at a chosen difficulty.
