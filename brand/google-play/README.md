# Google Play publishing resources

This directory contains the English (United States) main-store-listing package for each
FinitePlay game with a local listing package. PNG files are upload-ready; SVG files are the editable
masters for feature graphics.

Agents creating future listings or working in Play Console must read `AGENTS.md` in this
directory first. It defines the family copy pattern, verified-claim rules, and publishing checks.

## Package contents

Each game directory contains:

- `listing.md`: title, descriptions, category guidance, image alt text, and Play Console notes.
- `app-icon.png`: 512 x 512, 32-bit PNG, no baked-in corner mask.
- `feature-graphic.png`: 1024 x 500, opaque PNG.
- `screenshots/`: portrait captures of the real app at 1080 x 1920.
- `screenshot-order.txt`: explicit phone screenshot upload order; gameplay and hints first,
  settings last. Filenames alone do not determine display order.

`localizations.json` contains the Spanish (Spain) and German listing pilot for all five games.
These are an initial localization pilot, not a claim that current acquisition data identifies
these as the highest-demand markets. Keep game limitations in every translation.

`tools/publishing/listings.rb inspect [game ...]` reads current Publisher listings and image
hashes. `update [game ...]` saves canonical English copy, translations, and screenshot order
using the existing publishing account. It does not upload bundles or change tracks. Google
currently requires automatic review submission for these API edits; this is an external write,
not a local export. Hold'em currently requires the Console because this account gets 403.
Use `export [game ...]` to mechanically synchronize Gradle Play Publisher text files under
`games/<game>/app/src/main/play/listings/` from the reviewed Markdown and localization JSON.
Export does not contact Google or load credentials. These text files are generated outputs;
edit the canonical Markdown/JSON, then export again.

## Current Play requirements checked 2026-09-20

- Title: at most 30 characters.
- Short description: at most 80 characters.
- Full description: at most 4,000 characters.
- App icon: 512 x 512, 32-bit PNG, at most 1 MB.
- Feature graphic: 1024 x 500, JPEG or opaque 24-bit PNG.
- Screenshots: at least two; PNG or JPEG; each side 320-3840 px and the long side no more
  than twice the short side. For game merchandising, Google recommends at least three
  portrait 9:16 screenshots at 1080 x 1920 or larger that show gameplay.

Before submission, complete the app-content questionnaires in Play Console. These apps are
offline-only, have no accounts, advertising, analytics, networking, or data leaving the device;
answer the Data safety form from the shipped build and its dependency manifest, not from this
copy alone. The shared publisher privacy policy is https://finiteplay.org/privacy.
