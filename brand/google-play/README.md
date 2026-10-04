# Google Play publishing resources

This directory contains the English (United States) main-store-listing package for each
released FinitePlay solitaire app. PNG files are upload-ready; SVG files are the editable
masters for feature graphics.

## Package contents

Each game directory contains:

- `listing.md`: title, descriptions, category guidance, image alt text, and Play Console notes.
- `app-icon.png`: 512 x 512, 32-bit PNG, no baked-in corner mask.
- `feature-graphic.png`: 1024 x 500, opaque PNG.
- `screenshots/`: portrait captures of the real app at 1080 x 1920.

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
