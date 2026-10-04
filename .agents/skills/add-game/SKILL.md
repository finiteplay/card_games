---
name: add-game
description: Scaffold a new card game in this repo — modules, packages, docs, and gates — following the layering in docs/ARCHITECTURE.md. Use when adding Spider, FreeCell, Blackjack, or any other game alongside Klondike.
---

# Adding a game

Read `docs/ARCHITECTURE.md` and `docs/PLATFORM.md` first. This skill is the procedure;
those two are the contract, and they win where this file drifts from them.

## Decide the shape first

Two questions settle the module list:

1. **Is it a solitaire?** Does it deal from a shuffled deck into a board the player
   solves, needing certified deals and non-repeating traversal? Then it uses
   `:solitaire:catalog`. Blackjack does not — it deals fresh every hand and there is
   nothing to certify.
2. **Does it need a solver?** Only if deals must be proven winnable offline, or the game
   offers hints that prove something. Skip the module otherwise; do not add an empty one.

| Game type | Modules |
|---|---|
| Solitaire with certified deals (Spider, FreeCell) | `rules`, `solver`, `app` + `:solitaire:catalog` |
| Solitaire without certification | `rules`, `app` + `:solitaire:catalog` |
| Non-solitaire (Blackjack) | `rules`, `app`, no solitaire layer |

## Steps

1. **Modules.** Create `games/<game>/{rules,app}` (plus `solver` if needed) and add
   `include(":games:<game>:rules")` etc. to `settings.gradle.kts`. Copy the build files
   from the Klondike equivalents — `rules` is a Kotlin/JVM module with no Android
   dependency, `app` is `com.android.application`. Packages are
   `org.finiteplay.<game>.*`.

2. **Wire the shared layers.** The app depends on `:core:cards`, `:core:session`,
   `:core:storage`, `:core:ui`, its own `rules`, and — if it is a solitaire —
   `:solitaire:catalog` and `:solitaire:ui`. Never the other direction. `:core:session` gives
   the undo/log shape (`Session<S, E>`) and the active-game record store's generic fields;
   `:solitaire:ui` gives tableau geometry against a `TableauGeometryConfig` the game supplies
   with its own numbers — there is no default, because the floors are each game's own UI spec.
   Give the app its own `applicationId`, `app_name`, launcher icon, and `game_title`.

3. **Arm the boundary gate.** Add the game's name to `forbiddenNames` in
   `buildSrc/src/main/kotlin/finiteplay.shared-layer.gradle.kts`. Spider and Blackjack are
   already listed; anything else needs adding, or `core/*` can quietly grow a reference to
   it.

4. **Strings.** Add only the game's own strings. The shared ones — actions, settings,
   statistics, dialogs, spoken rank and suit names — merge in from `:core:ui`
   (`core/ui/src/main/res/values/strings.xml` is the count; it changes, so it is not
   restated here). If you find
   yourself adding a string another card game would want, it belongs in `core/ui` instead,
   across every locale in `docs/languages.txt` plus the `values/` base
   (`docs/PLATFORM.md` "Localization").

5. **Docs.** Create `docs/games/<game>/` with `DESIGN.md`, `RULES.md`, `UI_SPEC.md`,
   `EXECUTION_PLAN.md`, `ACCEPTANCE.md`, `TODO.md`, plus `DEALS.md` if it ships a catalog.
   Do not restate `docs/PLATFORM.md` — reference it. Add the game to the module map in
   `docs/ARCHITECTURE.md` and the spec list in `AGENTS.md`.

6. **Gates.** The app needs the platform's required checks (`docs/PLATFORM.md` "Quality"):
   a locale-completeness test, unit tests for its rules and reducer, instrumented UI tests
   on an emulator, lint, and a release build. If it ships a certified catalog, wire an
   `assertAppExcludesSolver`-style task so the generator cannot reach the app classpath.

## What to resist

- **Generalising from one example.** If the second game's board would fit a shared
  abstraction only after reshaping it, leave both concrete and write down why. `GameState`
  stayed Klondike-owned for exactly this reason — see `docs/ARCHITECTURE.md` "What moved once
  a second game existed, and what stayed". A shared type extracted from two real boards is
  worth more than one guessed from one — that section also names which of today's shared
  modules have only had one real caller so far and should be re-checked once this game's
  own app exists.
- **Skeleton modules.** Do not add an empty `solver` or an unused `catalog` dependency
  "for later". Dead code that compiles but does nothing still has to be maintained.
- **Copying Klondike's rules into the new game.** `canBuild` is a Klondike/FreeCell rule;
  Spider stacks by suit. Write the new game's rules from its own spec.

## Finishing

Run the full gate set before calling it done:

```powershell
.\gradlew.bat check
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :games:<game>:app:connectedDebugAndroidTest
```

`check` includes `assertNoGameReferences` on every shared module, which is what proves the
new game did not leak into `core/` or `solitaire/`.
