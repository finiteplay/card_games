# Acceptance and Verification

## Release Gate

The MVP is accepted only when all automated checks pass, required manual scenarios pass, catalog evidence is valid, and performance thresholds are met. A narrower test cannot substitute for the evidence named here.

## Planned Commands

The checked-in Gradle wrapper is the entry point:

```powershell
.\gradlew.bat verifyDealCatalogs
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
```

The execution specification may add an aggregate `verifyRelease` task, but it must retain the individual evidence above. Catalog generation is not part of these commands; `verifyDealCatalogs` only verifies committed evidence and never solves.

All five run today. `verifyDealCatalogs` verifies the generated six-catalog bundle: 60,000 unique records and replay certificates for every certified level. The layering gates `assertNoGameReferences` and `assertAppExcludesSolver` run as part of `check`.

## Requirement Matrix

| Area | Required evidence |
|---|---|
| Rules | Pure Kotlin unit tests for every legal and invalid transition |
| Solvable deals | Catalog manifest, hashes, uniqueness check, solution certificates, and replay validation |
| Stock | Unit and UI tests for draw-one, draw-three, and unlimited recycling in both modes |
| Tap and drag | Deterministic priority tests and instrumented gesture tests |
| Automatic moves | Rank-safety, cascade, disabled-setting, raw-deal-regardless-of-setting, parked-card, and atomic-undo tests |
| Automatic finish | Sweep-completes simulation, blocked-board rejection, timer stop, move count, disabled-setting, and replay-isolation tests |
| Hint | Guidance/no-solution/inconclusive outcome tests, an exact-search-vs-brute-force shortest-length test for the unweighted configuration, a full-catalog fresh-board budget gate (`HintSearchBudgetTest`), dead-state and certificate cache reuse tests, and a cheap has-any-legal-move gating test |
| Scoring and timer | Unit tests for every counted action and lifecycle transition |
| Stuck board | Dead-end fixture raises the no-moves state; no false positive while a legal move remains |
| Replay and new game | Seed, draw-mode, confirmation, score, and timer tests |
| Persistence | Move-log replay, restart, background save, version, missing data, and corruption recovery tests, including a release-variant round trip |
| Statistics | Per-mode aggregation, denominator definitions, abandonment, streak, nearest-rank percentile, and reset tests |
| Layout | API 26 and latest API screenshots/tests in portrait and landscape, with numeric card-geometry assertions |
| Accessibility | Touch targets, contrast, suit symbols, font scale, and skip animations. Screen-reader support is out of scope (`docs/PLATFORM.md` "Accessibility") and carries no evidence requirement |
| Themes | Light and dark palettes meet the `PLATFORM.md` contrast bands; the setting's four modes each resolve correctly and persist; system bars follow the resolved theme |
| Localization | `LocaleStringsCompletenessTest`: every locale defines exactly the base translatable key set, format arguments match; the in-app language override applies before any view inflates |
| Help | The three pages open, tab between each other, and close on Back |
| Game archive | The last 100 finished games retain complete move logs, oldest evicted first; statistics read unbounded history and are unaffected by eviction; the confirmed reset clears both |
| Build integrity | `:games:klondike:app` runtime classpath excludes `:tools:catalog`; the debug fixtures are absent from release |
| Privacy and power | Manifest inspection plus absence of network, jobs, services, wake locks, and background timer work |

## Rules Scenarios

Automated tests must prove:

- Deal uses 28 tableau cards and 24 stock cards with correct face states.
- Tableau accepts only descending alternating-color cards or valid sequences.
- Only Kings or King-led sequences enter empty columns.
- Exposed tableau cards flip automatically.
- Foundations build by matching suit from Ace to King.
- Foundation top cards may return to tableau, and automation does not reclaim a parked card while it stays uncovered.
- Only the waste top is playable.
- Recycling restores stock order without shuffling and supports unlimited passes.
- All 52 foundation cards trigger one win.
- The automatic finish starts only when no face-down cards remain and a simulated foundation-only sweep completes; a board with no face-down cards that cannot be swept continues normally.
- The automatic finish never runs during headless replay, catalog validation, or solving, so certificates remain valid.
- Invalid actions preserve state and score.

## Transaction Scenarios

- A tap selects the documented highest-priority destination, including the unsafe-foundation fallback when no tableau destination exists.
- Drag selects the requested legal destination, safe or not.
- A stack transfer counts as one move.
- Each stock action and automatic transfer counts as one move.
- Initial automatic transfers count as moves but do not create an undo record, start the timer, or mark the game played.
- With automatic moves disabled, dealing runs no cascade and the game starts at zero moves.
- One undo reverses the player action and its complete automatic cascade, preserves their counted moves, then adds one move.
- Undo does not immediately rerun automation.
- Hint and invalid actions do not change score.
- Replay reproduces the exact deal and rules version.
- The timer runs exactly while the game has started, the app is foreground, no modal is open, and the game is not won. Restoration adds no separate wait-for-action rule.
- Replaying a persisted move log reproduces an identical board, move count, and undo stack, including logs containing undo entries and an automation toggle.

## Catalog Gate

For the six draw-one level catalogs (`DEALS.md` "The certified catalog (D1b)"), run by
`.\gradlew.bat verifyDealCatalogs`:

- Every committed catalog loads under the frozen format, and its header agrees with the manifest on catalog, rules, shuffle, and solver versions, partition id, record count, and payload hash
- Valid manifest and SHA-256 hashes, both payload and whole-file
- Seeds within a catalog are ascending and unique, and no seed appears in two levels
- Independently replayed winning certificate for every seed **outside the Insane level**, which ships uncertified by definition (`DIFFICULTY_LEVELS.md` "Insane ships uncertified"). One replay covers both automatic-move settings, since every deal starts on the same raw board regardless of the setting — automation never runs before the player's first action
- The gate replays a per-level sample on every run; the exhaustive replay is a build-time cost paid by `build-levels`, which admits no seed whose line does not win
- No Insane seed was proven unwinnable by either search — unresolved is the level's criterion, disproved is an exclusion from the catalog entirely
- No seed emitted after timeout, unknown result, or invalid replay
- Reference deck vectors identical on catalog tool, JVM tests, and Android tests
- Traversal visits every index once before repeating, verified for 1, 10, and 100,000-seed assets, and keeps a separate position per level
- Generation occurred after the rules freeze recorded in `EXECUTION_PLAN.md`

Full solving occurs only in the offline catalog pipeline. Normal CI verifies committed evidence and representative solution replays; it does not regenerate catalogs.

After initial testing, an expanded catalog passes the same gate using its declared manifest count. Changing catalog size must not require loader, format, selection, or saved-state schema changes.

## Statistics Gate

Only completed games contribute completion distributions. Use nearest rank:

`rank = ceil(percentile × sampleCount)`, clamped to `1..sampleCount`.

Test empty, one-item, odd, even, repeated, and large histories. Statistics show sample size and compute min, p10, p50, p90, and max for elapsed time and move count, one draw mode at a time and never blended (`RULES.md` "Draw-Three Mode").

Denominators are exact: `win rate = wins / (wins + losses)`, and `games played = wins + losses + 1` while an unfinished played game exists. Prove that starting a game raises games played without moving win rate, and that abandoning it converts the increment into a loss exactly once.

## Device Matrix

| Target | Orientation | Purpose |
|---|---|---|
| API 26 phone emulator | Portrait and landscape | Minimum-platform compatibility |
| Latest stable API phone emulator | Portrait and landscape | Current behavior and UI tests |
| Physical Pixel 9 Pro XL | Portrait and landscape | Touch, startup, frame, memory, and power checks |

Test normal and large font scales, both the right-handed and left-handed layouts, both the light and the dark theme, skip animations, and gesture navigation.

## Manual Smoke Test

On the physical Pixel 9 Pro XL:

1. Start a game and verify the draw-one initial deal.
2. Tap and drag legal cards; attempt one invalid move.
3. Request a hint and follow it.
4. Exhaust and recycle the stock.
5. Trigger at least one automatic foundation cascade.
6. Return a foundation card to the tableau and verify automation leaves it there.
7. Undo and verify the complete transaction restores.
8. Rotate during play and verify state remains unchanged.
9. Background and terminate the app, then verify restore and paused timer.
10. Replay and compare the initial deal.
11. Load the debug-only near-win fixture from Settings, expose the last face-down card, and verify the automatic finish sweeps to a single recorded win.

## Performance Gate

- **Download size:** measure the release artifact with Android `apkanalyzer`; result ≤15 MB.
- **Cold start:** Android Macrobenchmark, thirty cold starts on the physical Pixel 9 Pro XL with the committed baseline profile applied; p95 time to interactive ≤1.5 seconds. Record the measurement with and without the profile.
- **Memory:** `adb shell dumpsys meminfo` after five minutes of the committed scripted play sequence; total PSS ≤120 MB.
- **Frames:** Frame-timing benchmark during repeated drag and automatic moves; at least 95% meet the device frame deadline.
- **Idle CPU:** five-minute foreground-idle trace with no animations; process CPU average <1%.
- **Background:** five minutes backgrounded produces no timer advance, network traffic, scheduled job, wake lock, or background service.

Record device model, OS version, build type, screen refresh rate, and measurement output with the release evidence.

## Completion Evidence

Before release, archive:

- Commit identifier
- Gradle, Android Gradle Plugin, JDK, Android Studio, and SDK versions
- Command outputs and test reports
- Catalog manifest and validation report
- Emulator/device matrix results
- Manual smoke-test checklist
- Performance measurements
- Known limitations
