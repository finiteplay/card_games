# Platform Specification

Publisher: [FinitePlay LLC](https://finiteplay.org)

What every FinitePlay card game does the same way, whatever the game. A game's own
`docs/games/<game>/DESIGN.md` describes what is particular to it and does not restate
anything here; where the two ever disagree, this file governs and the game's spec is the
bug. The code that implements these rules lives under `core/` — see
[ARCHITECTURE.md](ARCHITECTURE.md) for the module map and the dependency rule.

## Product Constraints

- Android only, offline only.
- No networking, analytics, advertising, accounts, background services, scheduled jobs,
  or wake locks. Lifecycle-triggered local writes are allowed; background timer execution
  is not.
- No personal data leaves the device.
- Support Android 8.0 (API 26) and newer. Compile and target the current stable SDK.

## Deterministic Shuffle

`core/cards` owns the canonical 52-card deck order and the shuffle that turns a seed into
a deal. Both are part of the persisted contract: a saved game stores its seed and shuffle
version and rebuilds the board by replaying, so the same seed must produce the same deck
forever.

- The PRNG is SplitMix64 and the shuffle is a seeded Fisher–Yates over the canonical
  order. Neither may be changed in place — any change to the algorithm, the canonical
  order, or the iteration direction requires a **new shuffle version**, because every
  existing save and every certified deal is stated against the old one.
- Reference vectors (`core/cards` test fixtures) pin the output for known seeds. A change
  that moves them is the signal that a version bump is required, not a test to update.

## Localization

The interface ships in English plus the languages listed in `docs/languages.txt` —
that file is the roster and the count, so neither is restated here.

Every player-facing string is an Android string resource (`stringResource()`), never a
hardcoded literal, so every locale reaches every string uniformly. The strings every card
game needs — action labels, settings, statistics, dialogs, and the spoken rank and suit
names — live in `core/ui` and merge into each app's resources; a game ships only its own.
The only exceptions to full translation are proper nouns and URLs identical in every
language by design (`company_name`, `company_website`, each game's `game_title`, all
`translatable="false"`), and the card-face glyphs Canvas draws directly (`A`, `2`–`10`,
`J`, `Q`, `K`, and the suit pictograms), which stay universal — only the
accessibility-spoken names ("Ace of Hearts") are language-specific.

The in-app language override is applied by wrapping the Activity's base context before
any view inflates (`core/storage`'s `AppLocale`), which is why it is backed by
`SharedPreferences` rather than DataStore: `attachBaseContext` must answer synchronously.

**Translation quality.** The 30 non-English locales were generated as an
infrastructure-and-coverage-first pass — every key translated, every format placeholder
preserved, nothing missing — but the translations are machine-generated and have not been
reviewed by a native speaker. Ukrainian was written with particular care per an explicit
requirement, but even it should not be treated as native-reviewed. No locale should be
presented as release-ready until a native speaker has read everything under its `values-XX/`.

A locale-completeness test is a required gate for every app, and it spans every string file a
locale mirrors rather than one of them: every locale must define exactly the base's
translatable key set (no missing key silently falling back to English,
no stale extra key), and every format string's argument positions must match the base's. It
says nothing about translation quality — only completeness and structural correctness.

## Themes

Two board palettes, both defined in `core/ui`'s `AppColors` and selected by a persisted
setting offering light, dark, follow-the-system, and automatic.

- **Follow-the-system** takes the device's own light/dark setting and tracks it live.
- **Automatic** follows the sun, not the device and not a schedule: it is dark between civil
  dusk and civil dawn where the player is. Civil twilight rather than geometric sunset,
  because the sky stays bright for a good half hour after the sun goes down and a dark screen
  that early is wrong. The location is the *timezone's* reference city, from a copy of tzdb's
  `zone.tab` compiled into `core/ui` — never the device's location, so this costs no location
  permission and no network call. Being a few hundred kilometres off moves dusk by minutes,
  which nothing here depends on; a zone tzdb does not place falls back to a fixed 19:00/07:00
  schedule. The retired zone names (`Europe/Kiev`, `Asia/Calcutta`) are carried alongside the
  canonical ones, because devices still report them.

Neither mode schedules anything: the theme is resolved during composition, so Automatic flips
on the first recomposition after dusk — the next move, in practice — rather than on a timer.
Background work to flip a colour is exactly what the product constraints forbid.

- **Dark.** The card face is deliberately not near-white. WCAG sets no upper bound on
  contrast, but dark-theme practice — Material's dark theme, Apple's HIG, IBM Carbon —
  agrees that a large light surface on a near-black background at 12:1 and up causes
  halation: the shape smears for astigmatic and night-adapted eyes. The face targets
  roughly 8:1 against the board, past AAA's 7:1 and short of the glare.
- **Light.** A casino card table: deep baize green with white cards. Light means the
  *cards* are bright and the room need not be dim — not that the table is a white page,
  which no card game looks right on. The green is dark enough that white sits at about
  7:1 on it, the same band the dark face targets and for the same reason, and a dimmed
  face on a green table would only look grubby. That is also the ceiling on how light the
  table may go: past it white on the table falls out of the AAA band, and the lightest
  action accent falls towards the 3:1 floor. A lighter table is a different accent set,
  tuned darker, rather than the same one on a paler ground.

Accent colors are per-theme, not shared. Both tables are dark enough that accents go
*lighter* than the background, but the light table leaves more room, so its accents keep
real saturation — pushed near-white to maximise contrast they stop reading as distinct
colors, which is the only reason per-action accents exist.

A theme must define the whole surface-container family, not just `surface`. Material draws
menus, dialogs and sheets on `surfaceContainer`, a separate role: a scheme that overrides
`onSurface` but leaves the container at its factory default paints its text on a colour
from a different palette. That is exactly how the settings dropdown ended up white-on-white
the first time the light theme shipped.

## Sound

A short effect plays for a player's own move, every automatic transfer including an automatic
finish, a new-game shuffle, a hint request, a deal, a chip or decision action, a completed sequence,
an invalid action, and a win where the game exposes that event. A persisted
Sound setting (default off — the player opts in) and the app's own
foreground state gate playback independently of each other and of system audio-focus and
volume behavior: `GatedSoundPlayer` (pure Kotlin, unit-tested with no Android dependency)
only forwards to the real player when both are true, so backgrounding or muting silences
sound immediately regardless of what the underlying player would do.

`AndroidSoundPlayer` uses `SoundPool` with `AudioAttributes` tuned for a short,
ducking-tolerant sonification sound — requesting transient audio focus immediately before
playing and abandoning it right after — and plays on the attributes' matching stream, so
it respects that stream's system volume without extra work.

Each move's sound plays the instant that specific card's own step starts, not at commit
time for the whole transaction, so a multi-step cascade sounds once per step, in step
order, staying audibly tied to whichever card is actually moving. Sound and skip
animations are independent settings: a move still plays its sound with animations
skipped, since they address different accessibility needs.

The shipped library uses quiet card and chip Foley for physical actions and restrained generated
cues for hints, confirmations, invalid actions, and wins. `docs/SOUND_ASSETS.md` records the source, license, and checksum
of every file. Cue names in `core:ui` describe reusable events and must remain game-neutral.

## Win Celebration

Every game announces a win — a solitaire's, or a Blackjack round's — with a large **result banner**
(`core/ui`'s `ResultBanner`) and, on a win, **confetti** (`ConfettiBurst`). They are shared, so the
games feel alike:

- **Above everything, and never in the way.** A solitaire's win dialog is a window of its own, so
  its banner and confetti are a second transparent window over it (`CelebrationOverlay`), created
  after the dialog so the dialog's scrim does not dim them. That window takes neither a touch nor
  the focus: the dialog's buttons work exactly as without it.
- **The banner stays as long as the thing it announces.** For a solitaire, until the win dialog is
  closed; for a Blackjack round, a few seconds (`docs/games/blackjack/UI_SPEC.md` "Settlement
  Presentation"). The confetti lasts two seconds and then nothing runs: no idle cost.
- **It does not shake or flash** to say a result, and it is a polite live region, so a screen
  reader announces it. The words on it say the result, so its colour is never the only cue.
- **Skip Animations and system reduced motion** leave the banner, with no spring and no confetti
  (`## Accessibility`).

## Accessibility

- At least 4.5:1 text contrast and 3:1 meaningful graphic contrast
- Controls at least 48 dp
- Cards are content, not controls, and are exempt from the 48 dp minimum — a full tableau
  cannot hold 48 dp cards on a small screen. Each game's UI spec instead guarantees a
  minimum card width and exposed face-up band.
- Suit symbols in addition to color
- Font scaling for every text element except card faces, which scale with card size so
  the board cannot outgrow the screen
- System reduced-motion support merges into the in-app Skip Animations setting (either one
  is enough to trigger it)

**Screen-reader support is out of scope, by decision.** No game here is required to expose
semantic labels or actions for piles and cards, to announce events, or to pass a TalkBack
run, and none is gated on it. The games are played by seeing the board: a solitaire is a
spatial layout of dozens of cards whose whole content is where each one sits relative to the
others, and the project is not taking on making that legible without sight.

Recorded as a decision rather than left as a silent gap, because the requirement was in this
file and was removed. What stays above is not screen-reader support in a smaller form: the
contrast floors, touch targets, suit symbols, font scaling, and reduced-motion rules serve
low-vision, motor, and vestibular needs, and they remain requirements. Any Compose semantics
still present in a game's code are incidental, not a contract, and nothing verifies them.

## Settings: About

Every app's Settings ends with an About group (`core/ui`'s `AboutSettingsGroup`): the running build's version, a link to the publisher's website, a link to the privacy policy, and an Acknowledgements entry that opens a dialog listing the open-source software the app ships and its licences (`COPYRIGHT.txt` is the record, and what it may contain is `AGENTS.md` "Licensing"). The links open in the player's browser; the app itself makes no network request. The privacy policy address is `privacy_policy_url` in `core/ui`'s resources.

## Persistence

Save asynchronously after every committed action, every settings or statistics change,
and every transition to the background. Use an atomic, versioned local format.

- Game state is immutable, and one reducer owns every committed transition. Player actions
  produce new state, which is what makes replay, undo, persistence, and unit testing
  deterministic.
- A missing save starts a new game. A corrupt or unsupported save is discarded without
  deleting valid settings, statistics, or history.
- Never persist a running timer.
- Transient drag and animation state stays out of persisted game state.

How a particular game encodes its board is its own business; see its DESIGN.md.

## Performance

- Release download size: at most 15 MB
- Cold start to interactive: at most 1.5 seconds at p95
- Memory during play: at most 120 MB PSS
- Drag-animation frames meeting the device frame deadline: at least 95%
- Idle CPU: below 1%
- No continuous game loop, network activity, wake locks, periodic jobs, or runtime drawing
  allocations
- Cache card artwork and render only after input or during brief animations

## Quality

Every app runs the same shape of verification, whatever the game:

- Unit tests for the rules, the reducer, persistence and replay, corruption recovery,
  settings, and statistics
- UI and smoke tests for tap, drag and undo (where the game has them), new game, restoration
  after restart, rotation,
  both handedness layouts, both themes, skip animations, and win
  presentation
- Locale completeness (above), lint, and a release build
- Test API 26 and the latest stable API on phone-sized emulators in both orientations.
  Instrumented runs go on an emulator rather than a physical device.
- The project must sync and build with the latest stable Android Studio using the
  checked-in Gradle wrapper and a toolchain-pinned JDK.

Each game's `ACCEPTANCE.md` names the evidence its own release gate requires on top of
this.
