# FinitePlay Games

A family of Android-only, offline card games sharing one core. Kotlin + Jetpack Compose,
Canvas-rendered boards, local-only persistence. Publisher: FinitePlay LLC.

Klondike is the first game; Spider and FreeCell followed, and Blackjack, the first game that is
not a solitaire, is under implementation. The repo is structured so each is an
addition rather than a rewrite — when working on Klondike, keep changes that aren't about
Klondike out of Klondike.

## Specifications are authoritative

The docs are the contract; the code follows them. If implementation reveals a requirement
change, update the spec and the game's `EXECUTION_PLAN.md` in the same change — never
resolve a conflict silently in code.

**Four rules, each learned from a way Klondike's docs went wrong.** They cost a reader more
than they cost a writer, which is why they are here rather than in a postmortem.

- **Separate what is true now from what is planned.** `DEALS.md` described a certified
  ten-seed catalog and a live sixty-thousand-seed interim source in one document with nothing
  marking which was which, and a reader had to know the history to tell. Whichever a section
  describes, say so in it.
- **A plan is a record of intent; do not rewrite it into a report.** Klondike's packages are
  still in the future tense on purpose — that is what was meant at the time. Status goes in
  one table at the top, which is the only part kept current.
- **Numbers age faster than prose.** A count that will change belongs in the artifact or is
  named with its source, not asserted in three files that then disagree.
- **Names of modules and files age on any restructure.** Either sweep them or mark them
  historical where they stand; `:app` and `:game` still appear in Klondike's plan, marked.

Docs mirror the module tree: what's true for every game lives at `docs/`, what's true for
one game lives under `docs/games/<game>/`.

**Platform-wide**

- `docs/ARCHITECTURE.md` — module map, the one-way dependency rule, adding a game
- `docs/PLATFORM.md` — localization, themes, sound, accessibility, persistence,
  performance, and the quality bar every app meets

**Every solitaire**

- `docs/solitaire/GLOSSARY.md` — the vocabulary every solitaire uses (tableau, foundation,
  stock, waste, build, sequence, deal) and the terms it deliberately avoids. Specs, code, and
  player-facing strings all follow it.
- `docs/solitaire/CATALOG.md` — deal-catalog format, the deterministic deal contract,
  offline-only certification, traversal. A non-solitaire skips this layer.

**Klondike** (`docs/games/klondike/`)

- `DESIGN.md` — product, interaction, hint search, interface, architecture
- `RULES.md` — legal moves, draw modes, automation, scoring, session lifecycle
- `UI_SPEC.md` — layouts, input, geometry, accessibility
- `DEALS.md` — catalog generation, format, selection
- `DIFFICULTY_LEVELS.md` — the strategy tiers and how deals are graded
- `EXECUTION_PLAN.md` — work packages, gates, deterministic deal contract
- `ACCEPTANCE.md` — release gates and evidence
- `TODO.md` — deferred, out of MVP scope
- `PUBLIC_STRATEGY_RESEARCH.md` — background research, not a contract

**Spider** (`docs/games/spider/`)

- `RULES.md` — the layout, building, the row deal, banking, the invariants
- `DESIGN.md` — what Spider is, what it is not, and what the second game settled
- `UI_SPEC.md` — layouts, input, geometry, accessibility
- `EXECUTION_PLAN.md` — work packages S1–S5 and their gates
- `DEALS.md` — the ONE/TWO certified catalog, the solver, shipped solutions, partitions
- `TODO.md` — deferred, out of first-release scope
- `PUBLIC_STRATEGY_RESEARCH.md` — background research, not a contract

**FreeCell** (`docs/games/freecell/`) — the third game
(`EXECUTION_PLAN.md`'s Status table says what is left)

- `DESIGN.md` — product, interaction, interface, architecture
- `RULES.md` — the deal, free cells, building, supermove, foundations, session lifecycle
- `UI_SPEC.md` — layouts, input, geometry, accessibility
- `DEALS.md` — catalog generation, format, certification
- `EXECUTION_PLAN.md` — work packages and gates
- `TODO.md` — deferred, out of first-release scope

**Blackjack** (`docs/games/blackjack/`) — the fourth game and the first non-solitaire, under
implementation (`EXECUTION_PLAN.md`'s Status table says how far).

- `DESIGN.md` — product, interaction, hint, statistics, architecture, persistence, what
  Blackjack is not (including why it has no undo)
- `RULES.md` — hand values, table rules, betting, the shoe, order of play, legal actions, the
  dealer's play, settlement, round lifecycle
- `UI_SPEC.md` — screens, action bar per phase, card geometry, settlement presentation, the reset offer
- `EXECUTION_PLAN.md` — work packages B0–B8 and their gates
- `ACCEPTANCE.md` — gates run and the release evidence, with what only hardware can settle marked open
- `TODO.md` — deferred, out of first-release scope

**Texas Hold'em** (`docs/games/holdem/`) — the fifth game and the second non-solitaire, designed
but not started (`EXECUTION_PLAN.md`'s Status table says how far); `UI_SPEC.md` and `TODO.md` are
its first package.

- `DESIGN.md` — product (a six-seat sit-and-go against five opponents), interaction, hint, the
  opponents and the bar they must meet, statistics, architecture, persistence
- `RULES.md` — hand rankings, the tournament and its blinds, the deal, betting, legal actions,
  pots and showdown, leaving
- `EXECUTION_PLAN.md` — work packages H0–H9 and their gates

A game's spec never restates the platform spec. Where they disagree, `PLATFORM.md`
governs and the game's spec is the bug.

## Modules

Full map and the "adding a game" recipe: `docs/ARCHITECTURE.md`.

- `:core:cards`, `:core:session`, `:core:storage`, `:core:ui` — reusable by any card game
- `:solitaire:catalog`, `:solitaire:ui` — reusable by any solitaire; Blackjack would skip this layer
- `:games:klondike:{rules,solver,app}` — one game, one app
- `:games:spider:{rules,solver,app}` — the second game; its solver certifies catalogs and powers the on-device hint
- `:games:freecell:{rules,solver,app}` — the third game
- `:games:blackjack:{rules,app}` — the fourth game and the first non-solitaire: no solitaire layer, no solver, ever
- `:games:holdem:{rules,opponents,app}` — planned, not created (`docs/games/holdem/EXECUTION_PLAN.md` H1)
- `:tools:catalog` — desktop only. A `:benchmark` module is planned (Klondike's Q2), not yet created

Dependencies run one way: `games/* → solitaire/* → core/*`. Nothing under `core/` or
`solitaire/` may name a game — `assertNoGameReferences` fails the build if it does, and it
reads comments and strings, not just imports. `:games:klondike:app` depends on
`:games:klondike:solver` (it powers the on-device hint search) but must never depend on
`:tools:catalog` — `assertAppExcludesSolver` asserts this, so the offline
catalog-generation code path cannot ship.

When something looks shareable, check it is shareable *from two examples*, not one.
`GameState`, `canBuild`, and the A* solver all stayed with Klondike for that reason;
`docs/ARCHITECTURE.md` records why.

That test's premise expires. A finding recorded when the second game was only a rules engine
does not survive that game growing an app, and re-checking is not optional: before writing
board, gesture, chrome, or statistics code for a game, read what the other one already has.
`docs/ARCHITECTURE.md` "Moved later" exists because a working gesture handler was re-derived
from scratch — wrongly, twice — while the correct one sat in the other game's board.

## Build

Windows, checked-in wrapper, JDK 17 toolchain.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat check                # unit tests, lint, and the layering gates
.\gradlew.bat assembleRelease
.\gradlew.bat verifyDealCatalogs   # the catalog gate; never solves
```

`verifyDealCatalogs` re-checks every committed catalog (Klondike's six levels, Spider's three
suit counts, FreeCell's) and passes with a notice only for a game that has none committed.
Each app ships its certified catalog and validates it before dealing; Klondike's interim seed
lists are retired (`docs/games/klondike/DEALS.md`).

Instrumented tests run on an emulator, never the physical device:

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :games:klondike:app:connectedDebugAndroidTest
```

`buildConfig = false`, so debug-only code goes in a `src/debug` source set, never behind a
`BuildConfig.DEBUG` check. Release runs R8 with resource shrinking.

Gradle 9.5.0 · AGP 9.3.1 · Kotlin Compose plugin 2.3.20 · compileSdk 36.1 · minSdk 26 ·
targetSdk 36 · configuration cache and build cache on.

## Licensing

The repository is distributed under the PolyForm Noncommercial License 1.0.0
(`LICENSE.txt`; copyright notice in `COPYRIGHT.txt`).

A dependency that ships inside a built app — anything reached by
`implementation`/`api`, or `debugImplementation` since debug builds still ship
to testers — may only carry the Apache License 2.0, the MIT License, or a BSD
license. Build- and test-only tooling (`testImplementation`,
`androidTestImplementation`, Gradle plugins) is never distributed and is not
held to this rule. Check a new dependency's license before adding it and
record it in `COPYRIGHT.txt`.

## Invariants worth restating

Full detail lives in the specs; these are the ones easiest to break by accident.

- Game state is immutable. One reducer owns every committed transition.
- Automation never runs before the player's first action. Every deal starts on the raw
  board at zero moves, whatever the automatic-moves setting; the first cascade (if any)
  happens as part of the player's own first move, not before it.
- A card withdrawn from a foundation is parked — automation must not reclaim it while it
  stays uncovered.
- Undo restores the board but not the moves already counted, then adds one. (Blackjack has no
  undo; `docs/games/blackjack/DESIGN.md` says why.)
- The active game persists as seed + versions + elapsed + move log, restored by replaying
  the log. Never persist a running timer.
- The automatic finish is offered by the reducer and invoked only by the game screen —
  never by headless replay, catalog validation, or the solver.
- Deals are certified offline only, exclusively by `tools/catalog` — the app never solves
  to certify a deal. It does run a bounded, board-specific on-device search to power the
  Hint action (`docs/games/klondike/DESIGN.md` "On-Device Hint Search"); that is a
  narrower, different guarantee and never touches catalog certification.
- No networking, analytics, background services, jobs, or wake locks.

## Working agreements

**Scope.** Deliver what was asked at the scope intended. Make routine judgment calls;
check in only when readings differ materially. Nothing from a game's `TODO.md` during its
MVP. If you think the ask is wrong, say so in a sentence and proceed as asked rather than
quietly widening it. Finish the whole package, and if something can't be finished, do the
rest and state plainly what is missing.

**Delegation.** Subagents multiply cost and latency. Use them for genuinely independent,
sizeable tracks — a wide multi-file investigation, or two work packages in disjoint
modules. Do not use them for a handful of reads and edits, and do not use them to review
or verify your own work; verification belongs in the main loop. Keep spawn counts low.

**Output.** Lead with the outcome. Keep responses and written files proportionate to the
task — no padded sections or redundant summaries. Match new code to the surrounding style,
and only comment to state a constraint the code cannot show.

**Gates are not self-checks.** Every work package ends on its automated gate in the game's
`EXECUTION_PLAN.md`. Those are acceptance criteria — run them. Do not add prompt-level
"double-check your answer" scaffolding on top.

**Bugs get a failing test first**, then the fix.
