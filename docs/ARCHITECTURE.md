# Architecture

This repo hosts a family of card games sharing one core. Klondike is the first, Spider and
FreeCell followed, and Blackjack is planned; the structure exists so each is an addition rather
than a rewrite. Each game ships as its own Android app.

## Module map

```
core/cards              Card, Rank, Suit, CardColor, canonical deck, deterministic shuffle
core/session            undo stack, append-only log, percentile distributions, the
                        trailing-window filter, the running-timer rule, the
                        game-independent statistics aggregate, elapsed formatting
core/storage            DataStore helpers, per-app locale override, the active-game
                        record store
core/ui                 theme, card rendering, sound, tap/drag recognition, the action
                        bar's shape, panel/settings scaffolding, handedness, the
                        language list, shared strings x31 locales

solitaire/catalog       seeded deal catalog: encoder, loader, format, traversal
solitaire/ui            tableau width- and overlap-fitting geometry, config-driven

games/klondike/rules    board, session, legal moves, automation, auto-finish, tap
                        priority, solution codec
games/klondike/solver   A* search, hint engine, strategy tiers
games/klondike/app      the Android application

games/spider/rules      layout, deal, legal moves, the reducer (two decks, ten columns)
games/spider/solver     catalog certification, and the on-device hint search
games/spider/app        the Android application

games/freecell/rules    layout, deal, legal moves including supermoves, the reducer
games/freecell/solver   deal certification and the on-device hint
games/freecell/app      the Android application

tools/catalog           offline deal generator (desktop only, never on an app classpath)
```

Packages mirror the tree: `org.finiteplay.cards`, `org.finiteplay.core.session`,
`org.finiteplay.core.storage`, `org.finiteplay.core.ui`, `org.finiteplay.solitaire.catalog`,
`org.finiteplay.solitaire.ui`, `org.finiteplay.klondike.*`, `org.finiteplay.spider.*`,
`org.finiteplay.freecell.*`.

## Docs follow the same split

```
docs/ARCHITECTURE.md      this file — the tree, the rule, the recipe
docs/PLATFORM.md          what every game does the same way
docs/solitaire/GLOSSARY.md the vocabulary every solitaire uses
docs/solitaire/CATALOG.md what every *solitaire* does the same way
docs/games/<game>/        that game's DESIGN, RULES, UI_SPEC, DEALS,
                          EXECUTION_PLAN, ACCEPTANCE, TODO
docs/assets/              images the docs embed
```

Background research lives with the game it is about and is marked as not a contract —
Each game's is `docs/games/<game>/PUBLIC_STRATEGY_RESEARCH.md`.

A game's spec never restates the platform spec — it references it. Where the two disagree,
`PLATFORM.md` governs and the game's spec is the bug. The same test applies to a doc as to
a module: if a paragraph would be true of Spider and Blackjack too, it belongs at `docs/`,
not under `docs/games/klondike/`.

## The dependency rule

Dependencies run one way only:

```
games/<game>/app -> games/<game>/{rules,solver} -> solitaire/* -> core/*
```

Nothing under `core/` or `solitaire/` may depend on, or even name, a specific game.
Blackjack is the test that the split is real: it needs `core/cards`, `core/session`,
`core/storage` and `core/ui`, and skips the solitaire layer entirely.

Two build gates enforce this, both in `buildSrc`:

- **`assertNoGameReferences`** — applied to every shared module by the
  `finiteplay.shared-layer` convention plugin, wired into `check`. Fails if any source,
  resource, or comment under `src/` names a game. The check is textual on purpose: a
  compiler error would only catch an import, not `if (game == "klondike")` in a string.
- **`assertAppExcludesSolver`** (every game's `app`) — fails if `:tools:catalog` reaches the
  app's release runtime classpath, so offline catalog generation cannot ship.

`assertNoGameReferences` earned its place on first run: it caught `:solitaire:catalog`
depending *upward* on `:games:klondike:rules` so its solution codec could name Klondike's
`Move` type, plus `KlondikeTheme`/`KlondikeDarkColors` still carrying the game's name
inside `core/ui`. The codec moved to `games/klondike/rules`; the theme was renamed.

## What moved once a second game existed, and what stayed

`core/session` and `solitaire/ui` exist because Spider's rules engine and Klondike's app
between them showed exactly two things were shareable *as written*, not because a shared
shape was designed and then implemented: the undo/log bookkeeping and the tableau-fitting
math. Everything below either moved because of that, or was checked and found not to
qualify — both are recorded, because a plan that only lists what moved cannot be told apart
from one that never checked.

The first two groups below were settled while Spider had no interface; "Moved later" revisits
them now that it has one, and is the more useful half to read first if you are adding a game.

**Moved.**

- **The undo stack, the append-only log, and their commit/undo/record/finish operations**
  (`core/session`'s `Session<S, E>`). Generic over the board and the log-entry type, because
  those are the two things a game owns outright; the bookkeeping between them — a state
  change and its log entry happening together, never separately — is not. `GameSession`
  specialises `Session<GameState, LogEntry>` with Klondike's automation, auto-finish, and
  hint cursor layered on top. There is deliberately no transition protocol in `core/session`:
  no interface a game implements to say how a move applies. The two reducers differ in every
  way that matters, and inventing that shape from outside is the solution-codec mistake
  again, aimed at reducers instead of certificates.
- **Percentile distributions, the trailing-window filter, and the running-timer rule.** Each
  took a bare value (`List<Long>`, a timestamp selector, two booleans) rather than a game's
  own record or session type, which is what let them move without needing a second game's
  version of either to check against.
- **Tableau width- and overlap-fitting geometry** (`solitaire/ui`'s `TableauGeometryConfig`).
  Column count was already a parameter; the dp floors were not; `PLATFORM.md` states the
  *mechanism* (a card-width floor and a face-up band standing in for the 48 dp control
  minimum) but leaves the numbers to each game, so the floors became a parameter too.

**Stayed, re-checked against Spider rather than assumed.**

- **The packing rule.** `canBuild` (descending, alternating colour) is a Klondike/FreeCell
  rule, not a card fact. It sat in `core/cards` until Spider was written and moved to
  `games/klondike/rules` then: Spider's `canPlaceOn` is one rank lower at any suit, so the
  two agree on nothing but the rank step and a shared version would have needed a parameter
  naming the caller. FreeCell is now the second alternating-colour game and kept its own copy,
  pinned to Klondike's by `BuildAgreesWithKlondikeTest`: no layer exists for "some solitaires
  but not Spider", and a module for one two-line predicate was not worth inventing. It is a
  standing decision to revisit if a third game wants it, not a settled finding.
- **`GameState`.** Seven columns, one foundation per suit, a `drawMode`; none of it survives
  contact with Spider's ten columns, banked sequence counts, and no waste at all. There is no
  `solitaire/engine` for that reason, and the finding holds now that Spider is a real board:
  the two share *less* than a shared type would need.
- **The A\* solver.** Its heuristics and `SNode` encoding are shaped around Klondike's seven
  columns and four foundations.
- **`HistoryStore`'s codec and `SettingsStore`.** The format is a versioned on-disk codec that
  would need re-encoding to generalise, and `SettingsStore` would pull a Compose-owning module
  into one that must not depend on Compose. Both are one Klondike example; neither is forced.
- **Move-log encoding.** `Session`'s log entries are shared shape; what a `PlayerMove` entry
  encodes to on disk is not, and must not be — `MoveOpcode` is also the solution-certificate
  alphabet, frozen per game.

The solution codec is the standing counter-example that proves the rule is applied, not
recited: it *was* in the shared layer, the gate showed it did not belong, and it moved out.

**Moved later, once `:games:spider:app` existed.** The section above was written when Spider
was a rules engine with no interface, and several "stayed" findings were correct only under
that condition. A second *app* is a second example for interface code, and re-checking against
one moved these:

- **Combined tap-and-drag recognition** (`core/ui`'s `cardPointerInput`). Nothing in it is
  card- or game-shaped; it exists as one shared modifier because the way it disambiguates —
  by touch-slop distance, not by holding — is the whole correctness argument, and both
  plausible-looking alternatives are broken in ways no screenshot shows.
- **The action bar's shape** (`core/ui`'s `BoardActionBar`, `BoardAction`, `BoardOrientation`).
  Touch targets that survive chrome scaling, labels pinned to a fixed line count so a
  translation cannot reflow the bar, equal-width portrait buttons, rail width. Every one of
  those is a `PLATFORM.md` requirement rather than a Klondike decision. Which actions exist
  stays with each game.
- **`Handedness`** (`core/ui`), which is a platform-wide accessibility setting that happened
  to be declared inside one game's settings store.
- **The game-independent half of `computeStatistics`** (`core/session`'s
  `computeSessionStatistics`). The earlier finding — that the generic part sits *inside* a
  function threaded with Klondike's own fields — described the code accurately but drew the
  wrong conclusion: ten of `GameStatistics`' thirteen fields name no game concept, and the
  three that do (hint counts, a solution-length ratio) are exactly the ones another game
  cannot fill in. The seam is the accessor-lambda one `filterByPeriod` already used.
- **The statistics screen's sections and the solution comparison** (`core/ui`'s `StatisticsBody`,
  `SolutionEfficiencySection`, `SolutionComparison`; `core/session`'s `computeSolutionEfficiency`).
  Klondike, Spider and FreeCell each drew their own tiles and range graphs, and Spider's and
  FreeCell's were plain lines of text; three copies is the point at which the rule's "two
  examples" test is long passed. A game passes what it can measure — hints, a comparison against
  a shipped solution, a section of its own through the `middle` slot — and a game with none of it
  passes nothing.
- **Elapsed-time formatting** (`core/session`'s `formatElapsed`).
- **The full-screen panel shell and the settings rows** (`core/ui`'s `FullScreenPanel`,
  `SettingsGroup`, `SwitchSettingRow`, `DropdownSettingRow`). Settings, Statistics, and Help were
  the same screen structurally in three copies, which is how one gains a scroll the others need
  and another forgets the safe-drawing inset. What each panel *contains* stays with its game.
- **The shipped language list** (`core/ui`'s `AppLanguages`). The tags are the `values-*`
  directories `core/ui` itself ships, so no game owns that list.

**What this is confirmed against.** The apps and rules engines of Klondike, Spider and FreeCell.
Everything under "Moved" and "Moved later" is exercised by a real second caller at the layer
it sits in. The `ActiveGameRecordStore` `DealParameters` seam is now used by Spider and
FreeCell as well as Klondike. Still unexercised by a second layout: `BoardActionBar`'s landscape
rails are rendered by Klondike and Spider, and FreeCell has no landscape layout yet.

**The rule this section is really about.** "Shareable from two examples, not one" is a test
whose *premise expires*. Each finding above is only as good as the second example available
when it was made, so a finding recorded against a game that has since grown an app, a solver,
or a catalog has to be re-checked rather than cited. Re-deriving working code badly because an
old "stayed" note was taken as settled is the failure this ordering is meant to prevent, and it
has already happened once here: the gesture handler above was re-implemented from scratch,
wrongly, twice, while a correct one sat in the other game's board.

## Adding a game

The full procedure — including the module table, the strings rule, and the gates to arm —
is the `add-game` skill (`.claude/skills/add-game/`). The shape of it:

**A solitaire (Spider and FreeCell are the worked examples).** Three modules — `games/<game>/rules`,
`games/<game>/solver` if it needs one, `games/<game>/app` — plus `include(...)` lines in
`settings.gradle.kts`. Depend on `solitaire/catalog` for the seeded deal catalog,
`solitaire/ui` for tableau geometry (supply the game's own `TableauGeometryConfig` — the
numbers are the game's UI spec, not a default), `core/session` for the undo/log shape and
the active-game record's generic fields, and `core/*` for everything else. Supply the game's own strings; the shared ones come from
`core/ui` by resource merging — `core/ui/src/main/res/values/strings.xml` is the count, not
this file. Add the game's name to `forbiddenNames` in
`buildSrc/src/main/kotlin/finiteplay.shared-layer.gradle.kts` — Klondike, Spider, FreeCell
and Blackjack are already listed there.

**A non-solitaire (Blackjack).** `games/<game>/rules` and `games/<game>/app` only. Skip
`solitaire/*` entirely: there is no tableau, no deal catalog, no traversal. Depend on
`core/cards`, `core/session`, `core/storage`, `core/ui`.

Either way, the app module declares its own `applicationId`, `app_name`, and launcher
icon, and wires `assertAppExcludesSolver` if it ships a certified deal catalog.

There is no skeleton Blackjack module. Dead code that compiles but does
nothing would need maintaining and would still not prove the seam; the boundary gate plus
this recipe is the honest version of "prepared".
