# Texas Hold'em — Acceptance Evidence

What was run against `EXECUTION_PLAN.md` H3, H4, H6, H7 and H9 and what it showed. Everything ran on
**emulators** (the API 26 phone AVD) or the development machine — never on a physical device, as
`AGENTS.md` requires — so a budget that only hardware can settle is recorded as **open**, not passed.

## Gates

### Alpha build 2 verification (2026-10-11)

The playable table was merged into `master`. Version code 2, version name 0.3.0:

- `:games:holdem:{rules,opponents,app}:check` passed (the three tasks were invoked explicitly).
- `connectedDebugAndroidTest` passed on the Pixel 9 Pro XL API 37 emulator:
  26 tests, zero failures, errors, or skips, including both scenario orientations,
  action-bar anchors, and table controls. Stale dex inputs referencing the former
  `Solitaire` path initially prevented the build; cleaning the affected generated
  module outputs and rebuilding without caches resolved that build failure.
- `assembleRelease` and `bundleRelease` passed using the configured upload key,
  not the debug key. APK signature verified as FinitePlay LLC; SHA-256 certificate
  `e057f26f0e697aeacdae33a4d38ab661d3e8ccf49d0f15cc4ff480f1bebd5c34`.
- The signed release APK installed on the emulator and cold-launched in 889 ms.
  The live table and betting controls were present, and Settings opened.
- Release permissions contain only the app's AndroidX dynamic-receiver permission;
  there is no network or advertising-ID permission.
- Play committed version code 2 to `alpha` with status `draft`. A completed
  rollout was rejected because the Play app is still a draft. The Console shows
  the committed draft and zero active countries; it is not tester-accessible yet.
- The simulated-gambling content-rating questionnaire was saved with user approval.
  South Korea requires GRAC review. Other setup declarations, store listing,
  countries, testers, and review submission remain incomplete.

The evidence below records the earlier implementation gate run. Its debug-signing
blocker is historical and no longer applies. Hardware budgets and uncovered
acceptance cases remain open; this is Alpha evidence, not a production sign-off.

| Gate | Result |
|---|---|
| `check` (unit tests, lint, the layering gates, locale completeness in 30 locales) | Green |
| `:games:holdem:rules:exhaustiveEvaluatorTest` — all 133,784,560 seven-card hands, standard category counts | Green, about 93 million evaluations a second. The five-card version (2,598,960 hands, 7,462 distinct values) runs in `test` |
| `connectedDebugAndroidTest` — `PortraitScenarioPlayTest` and `LandscapeScenarioPlayTest`, nine each: a fold, a check-down to showdown, a bet and a call, a raise and a re-raise, an all-in with a side pot, a split pot, the player eliminated with their place shown, a tournament won, and the pot line | Green on the API 26 AVD, 18 of 18. One earlier run died when the emulator's own system service crashed (`Can't find service: package`); the emulator was rebooted and the whole suite re-run green |
| `assembleRelease` | Builds and shrinks (R8, resource shrinking). Signed with the **debug key** via `-Pfiniteplay.signReleaseWithDebugKey=true`; the upload build is blocked on the shared keystore having no `holdem-upload` alias |
| Neither `solitaire/*` nor `tools/catalog` on the release classpath | Green (`assertAppExcludesSolitaireUi`, `…SolitaireCatalog`, `…ToolsCatalog`, part of `check`); shown failing once with `:solitaire:ui` added |
| Opponents (H7), on the development machine | Hidden-information permutation, legality over 600 tournaments, and the field-strength gate (first-place rate against one in six: always call 0.95%, always min-raise 2.95%, top 5% only 2.45%, always all-in 5.95%; one opponent against five always-call seats won 53.0%) — see the H7 row of the plan's Status table |

## Persistence on a real process (H4)

On the minified release build, on the emulator, a script played into a hand, waited for the player's
turn again, `am force-stop`ped the app, relaunched it and compared the table the screen reported
(every seat's stack, bets and markers, the pot, the board and the player's cards). The restored table
was **identical in 5 of 5 runs**. The live region's spoken announcement is not compared: it is
transient by design. This is also the release-variant round trip of both stores with minification on.
The fault-injection cases (a kill before a hand's first save, between an action and its save, and
between a hand's end write and clearing the hand) are unit tests at the store seam, and the view model
is tested restarting on the same stores.

## Manifest (H9)

`aapt2 dump permissions` on the release APK lists one permission, AndroidX's
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No `INTERNET`, no `WAKE_LOCK`. One activity, AndroidX's
startup provider and profile-installer receiver, and no service; `dumpsys activity services`,
`dumpsys jobscheduler` and `dumpsys alarm` show nothing for the package.

## Budgets (`docs/PLATFORM.md` "Performance")

| Budget | Measured | State |
|---|---|---|
| Release download size ≤ 15 MB | 2.1 MB APK | Met |
| Cold start to interactive ≤ 1.5 s at p95 | 329–364 ms over five `am start -W` runs (emulator) | Met on the emulator; **open** for hardware, and five runs are not a p95 |
| Memory during play ≤ 120 MB PSS | 31 MB total PSS after a hand's first actions | Met on the emulator |
| Animation frames meeting the deadline ≥ 95% | 18% janky of 187 frames across a bet and the opponents' replies on the emulator's software renderer; 95th percentile 20 ms | **Open** — the emulator cannot settle it, and the sample includes first frames and the thinking-ring pulse |
| Idle CPU < 1% | `top` showed 0.0% for the process while idle | Indicative only; **open** for hardware |
| Opponent decision ≤ 50 ms at p95 (`DESIGN.md`) | 0.59 ms p95 for a postflop decision and 2.7 ms for a hint on the development machine | **Open** on the reference phone |
| No continuous loop, network, wake locks, periodic jobs | None found (above); timers are the rest-reminder ticker while foreground, the opponents' pacing pauses, and the thinking-ring pulse while an opponent is thinking | Met |

## Not covered

- Instrumented tests for both themes, both handedness layouts, rotation mid-hand on a live activity,
  Skip Animations, and each panel opening and closing on Back (`EXECUTION_PLAN.md` H6): the two
  scenario suites above are what exists. Help and Statistics were opened and closed on the emulator
  by hand.
- Card and chip motion beyond the card flights (bets gathering into the pot, pots sliding to their
  winners), and the tests that prove each animates (H8).
- The rest reminder's dialogs on a device, and anything needing physical hardware.
