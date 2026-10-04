# FinitePlay Games

Offline card games for Android, built on one shared core and developed by
[FinitePlay LLC](https://finiteplay.org). Kotlin and Jetpack Compose, boards drawn on a
Canvas, everything stored on the device: no accounts, networking, analytics, or background work.

## Games

Each game is its own app, with its own Play listing and its own rules engine.

| Game | App ID | Status |
|---|---|---|
| Klondike | `org.finiteplay.klondike` | Playable |
| Spider (one, two, or four suits) | `org.finiteplay.spider` | Playable |
| FreeCell | `org.finiteplay.freecell` | Playable; the [execution plan](docs/games/freecell/EXECUTION_PLAN.md) says what is left |
| Blackjack | | Planned, not started: the first game that is not a solitaire |

What every app does, and the quality bar it has to meet, is in
[PLATFORM.md](docs/PLATFORM.md): portrait and landscape, light and dark themes, sound,
accessibility, the platform's languages, and persistence that restores a game exactly as it was.
Where a game's spec and the platform disagree, the platform governs.

- **Klondike** has draw-one and draw-three, graded difficulty levels drawn from catalogs of
  deals checked solvable offline, a hint, auto-moves and automatic finish, undo, replay,
  and statistics.
- **Spider** has deals certified offline for one and two suits, and a hint that follows a
  stored solution and falls back to an on-device search once you leave it.
- **FreeCell** has supermoves, certified deals, a hint, and card motion.

## How the repository is organized

One repository holds every game, so what they share changes in one place.
Dependencies run one way, `games/* → solitaire/* → core/*`, and the build fails if a shared
module names a game.

| Module | What it is |
|---|---|
| `core/cards`, `core/session`, `core/storage`, `core/ui` | Reusable by any card game: cards and shuffling, undo and the move log, persistence, theme and card rendering |
| `solitaire/catalog`, `solitaire/ui` | Reusable by any solitaire: deal catalogs, tableau geometry |
| `games/<game>/rules`, `solver`, `app` | One game: its rules engine, its search, and its Android app |
| `tools/catalog` | Desktop-only deal certification; never on an app's classpath |

[ARCHITECTURE.md](docs/ARCHITECTURE.md) has the full module map and how to add a game.

## Documentation

The specifications are the contract and the code follows them.

- Every game: [ARCHITECTURE.md](docs/ARCHITECTURE.md), [PLATFORM.md](docs/PLATFORM.md)
- Every solitaire: [GLOSSARY.md](docs/solitaire/GLOSSARY.md), [CATALOG.md](docs/solitaire/CATALOG.md)
- Per game, under `docs/games/<game>/`: `DESIGN.md`, `RULES.md`, `UI_SPEC.md`, and where it has
  them `DEALS.md` and `EXECUTION_PLAN.md`, whose status table is the one part kept current
  - [Klondike](docs/games/klondike/DESIGN.md), [Spider](docs/games/spider/DESIGN.md),
    [FreeCell](docs/games/freecell/DESIGN.md), [Blackjack](docs/games/blackjack/DESIGN.md)

## Build

Windows, the checked-in Gradle wrapper, and a JDK 17 toolchain. Android 8.0 (API 26) or newer.
Open the project in the latest stable Android Studio, or run:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat check                # unit tests, lint, and the layering gates
.\gradlew.bat assembleRelease
.\gradlew.bat verifyDealCatalogs   # re-checks committed catalogs; never solves
```

Instrumented tests run on an emulator:

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :games:klondike:app:connectedDebugAndroidTest
```

Debug builds install beside release ones as `<app id>.dev`. Debug-only code lives in a
`src/debug` source set, never behind a `BuildConfig` check.

## Versions and tags

Every game has the same `versionName`, set once as `finiteplay.versionName` in
[gradle.properties](gradle.properties) and shown in each app's Settings. A release is tagged
`v<version>`. Each app keeps its own integer `versionCode`, which only ever rises: Play orders
builds by it and rejects one it has already seen.

## Play publishing

Gradle Play Publisher uploads a signed release AAB to an existing Play Console
track. It creates a release in that track; it does not create the Play Console
app, Play App Signing setup, tester lists, countries, or a closed-test track.
Create those once in Play Console first. FinitePlay's closed test is the
`Alpha` track, and its internal smoke-test track is `internal`.

Keep the credentials out of Git. In the ignored root `local.properties`, set:

```properties
finiteplay.upload.keystore=C:\\secure\\finiteplay-upload.p12
finiteplay.upload.keystorePassword=replace-with-the-keystore-password
finiteplay.play.serviceAccountJson=C:\\secure\\play-service-account.json
```

The service account needs Play Console's **Release apps to testing tracks**
permission. Increase the app's `versionCode` before every upload; Play rejects
a version code that was already uploaded for that application ID.

Run the applicable release gates, then upload one game at a time. Be explicit
about the destination track even though the checked-in default is `internal`:

```powershell
# Internal smoke test
.\gradlew.bat :games:klondike:app:publishReleaseBundle --track internal

# Closed test (the Play Console track named "Alpha")
.\gradlew.bat :games:klondike:app:publishReleaseBundle --track alpha
```

Replace `klondike` with `spider` or `freecell` to publish that app. These
commands upload only the AAB; use `publishReleaseApps` only when the Play Store
listing metadata is deliberately part of the release. Do not use a production
track command until a separate production-release decision has been made.

To discover the exact tasks available for an app or the current plugin options:

```powershell
.\gradlew.bat :games:klondike:app:tasks --group publishing
.\gradlew.bat :games:klondike:app:help --task publishReleaseBundle
```

## Publisher

- Company: [FinitePlay](https://finiteplay.org)

## License

Source-available under the [PolyForm Noncommercial License 1.0.0](LICENSE.txt): free for noncommercial use, but not an OSI-approved open source license. See [COPYRIGHT.txt](COPYRIGHT.txt) for third-party notices.
