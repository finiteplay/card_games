---
name: verify
description: Run this repo's applicable gates — unit tests, lint, layering checks, release build, and, when UI behavior changed, the emulator instrumented suite with screenshots. Use before declaring a work package done, or when asked to validate a change.
---

# Verifying a change

Gates are acceptance criteria, not self-checks: run them, do not reason about whether they
would pass. Each game's `EXECUTION_PLAN.md` names the gate its work package ends on;
`docs/PLATFORM.md` "Quality" names what every app runs regardless.

## Batch the commands

Every command goes through `tools/dev.sh` (gitignored scratch, rewritten per use and run
as `bash tools/dev.sh`). Writing the batch to that one file and running it once is what
keeps a verification pass to a single approval instead of a dozen.

## The gates

```bash
./gradlew.bat check :games:klondike:app:assembleRelease
```

`check` covers unit tests across every module, lint, `assertNoGameReferences` on each
shared module, and `assertAppExcludesSolver`.

## Decide whether UI verification applies

Skip the emulator instrumented suite and visual checks for a logic-only change: one confined
to non-UI Kotlin or desktop tooling, with no changes to app UI code, Compose semantics,
resources, manifests, navigation, input handling, accessibility, localization, or rendering.
Run the applicable unit tests plus `check` and the release build in that case.

Run the emulator instrumented suite and visual checks whenever the change can alter what a
player sees, touches, hears, or has announced to them. If the boundary is unclear, run the
UI suite. In the report, always state explicitly when UI verification was skipped and that
the change met the logic-only criterion.

```bash
export ANDROID_SERIAL=emulator-5554
./gradlew.bat :games:klondike:app:connectedDebugAndroidTest
```

**Emulator, never the physical device** — the physical Pixel is fingerprint-locked, so
`screencap` returns black and the run cannot be trusted. Read `adb shell wm size` before
using any tap coordinates; the emulator and the phone have different resolutions.

The instrumented run uses the orchestrator with `clearPackageData`, so each class starts
from a clean install. Without it a test that changes the language leaves later classes
running in that language, and failures appear in the full suite that pass in isolation.

## Visual checks

Screenshots go to the session scratchpad, not the repo. Give the app ~12 seconds after
`am start` — a shorter wait catches the splash screen rather than the board.

```bash
export MSYS_NO_PATHCONV=1                     # adb paths break under Git Bash otherwise
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
"$ADB" -s emulator-5554 shell settings put system user_rotation 1   # 0 portrait, 1 landscape
"$ADB" -s emulator-5554 shell am force-stop org.finiteplay.klondike
"$ADB" -s emulator-5554 shell am start -n org.finiteplay.klondike/.MainActivity
sleep 12
"$ADB" -s emulator-5554 exec-out screencap -p > "$SHOT/name.png"
```

To check a non-English layout without driving the settings UI, write the locale straight
into the preferences the app reads in `attachBaseContext`:

```bash
"$ADB" -s emulator-5554 push app_locale.xml /data/local/tmp/app_locale.xml
"$ADB" -s emulator-5554 shell "run-as org.finiteplay.klondike \
  cp /data/local/tmp/app_locale.xml /data/data/org.finiteplay.klondike/shared_prefs/app_locale.xml"
```

with the file holding `<string name="language_tag">uk</string>`. Ukrainian is the widest
of the 31 locales and the one that has caught real layout bugs; check both orientations.

## Reporting

State what ran and what it said. A failing gate is reported with its output, not
paraphrased. If a gate was skipped, say which and why — a work package with an unrun gate
is not done.
