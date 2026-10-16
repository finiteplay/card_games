# Store screenshots

The localized Play Store phone screenshots are generated, not stored in git. The PNGs
(`brand/google-play/<game>/screenshots-localized/`) are ignored: 31 languages come to about 900 MB, and
regenerating them adds that much to history again. What git keeps is this recipe and
`brand/google-play/screenshots-manifest.json` (versions and SHA-256 of what was produced and uploaded).

## Make a set

1. Emulator (a Pixel-class AVD) booted cold, `adb shell wm size 1080x1920`, font scale 1.0, nothing else
   using it. The window must be 1080 x 1920.
2. Release builds of all five games installed:
   `./gradlew :games:klondike:app:installRelease` (and spider, freecell, blackjack, holdem).
3. `tools/store-screenshots/capture.sh` for every language in `locales.txt`, or
   `tools/store-screenshots/capture.sh fr-FR:fr ar:ar` for some. About two minutes a language. Each language
   starts every app from cleared data, so Blackjack opens on the bet screen. The script turns the emulator's
   demo status bar on and off again. Right-to-left languages mirror the bar, so the taps are mirrored.
4. `python tools/store-screenshots/check.py --write-order --sheets <dir>`: sizes, blank or untranslated
   scenes, missed hint taps, the per-language `screenshot-order.txt`, and a contact sheet per language to look at.
5. `python tools/store-screenshots/manifest.py`; after an upload, `... --uploaded YYYY-MM-DD`.

## When to regenerate

Only when something a player sees in the screenshots changes (layout, strings, icons, a game's look), not on every
build. Compare `versionCode` in the manifest with the app's.

## Known limits

- A hung emulator blocks adb; every call is time-limited, so a run fails instead of stalling. If a language fails,
  delete its folders and capture it again.
- Hold'em and Blackjack deal at random, so those scenes differ from run to run and between languages.
- English uses the same recipe (`en-US`). `tools/publishing/listings.rb` still reads the older English set from
  `brand/google-play/<game>/screenshots/`, which is no longer tracked.
