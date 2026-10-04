# Blackjack — Acceptance Evidence

What was run against `EXECUTION_PLAN.md` B4a, B5, B7 and B8 and what it showed. Everything here ran
on **emulators** (the API 26 and a current-API phone AVD) — never on a physical device, as
`AGENTS.md` requires — so a budget that only hardware can settle is recorded as **open**, not passed.

## Gates

| Gate | Result |
|---|---|
| `check` (unit tests, lint, the layering gates, locale completeness) | Green |
| `connectedDebugAndroidTest`, `TableTest` — 18 tests, both orientations, both themes, both handedness layouts, restoration, the Hint, the reset offer and a flight test | Green on the API 26 AVD (16 tests at the time) and the current-API AVD (18) |
| `assembleRelease` | Builds and shrinks (R8, resource shrinking). Signed with the **debug key** via `-Pfiniteplay.signReleaseWithDebugKey=true`; the upload build is blocked on the shared keystore having no `blackjack-upload` alias |
| Neither `solitaire/*` nor `tools/catalog` on the release classpath | Green (`assertAppExcludesSolitaireUi`, `…SolitaireCatalog`, `…ToolsCatalog`, part of `check`) |

## Persistence on a real process (B4a)

On the minified release build, on the emulator: Deal, `am force-stop`, relaunch. The round came back
mid-decision with the same hand (4 against a 7), the same bankroll and the same offered actions. The
same check on the debug build, plus one that ended in a settled round (a win of 15 chips) and one that
ended in a bust: after a force-stop the bankroll held exactly what settlement had paid, with no round
and nothing paid twice. This is the release-variant round trip of both stores with minification on.

## Manifest (B8)

`aapt2 dump permissions` on the release APK lists one permission, AndroidX's
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No `INTERNET`, no `WAKE_LOCK`. No services, and no
scheduled job or alarm is registered for the package (`dumpsys activity services`, `dumpsys jobscheduler`,
`dumpsys alarm`).

## Budgets (`docs/PLATFORM.md` "Performance")

| Budget | Measured | State |
|---|---|---|
| Release download size ≤ 15 MB | 1.5 MB APK | Met |
| Cold start to interactive ≤ 1.5 s at p95 | 435–526 ms over six `am start -W` runs (emulator) | Met on the emulator; **open** for hardware, and six runs are not a p95 |
| Memory during play ≤ 120 MB PSS | 28 MB total PSS | Met on the emulator |
| Animation frames meeting the deadline ≥ 95% | 7% janky of 71 frames across a deal on the emulator's software renderer; 95th percentile 19 ms | **Open** — the emulator cannot settle it, and the sample includes the first frames after launch |
| Idle CPU < 1% | `top` showed the process absent from the busiest five over ten idle seconds | Indicative only; **open** for hardware |
| No continuous loop, network, wake locks, periodic jobs | None found (above); the only timers are the rest-reminder ticker while foreground and the dealer's paced reveal | Met |
| Cards cached, rendered after input or during brief animations | Cards are drawn on a canvas and redrawn on state or animation change | Met by construction; not profiled |

## Not covered

Rotation mid-round on a live activity (the suite supplies a landscape configuration instead), the
rest reminder's dialogs on a device, and anything needing physical hardware.
