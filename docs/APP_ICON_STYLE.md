# FinitePlay application icons

FinitePlay application icons are quiet, clear marks rather than miniature game scenes. Each
icon should identify its application at a glance while remaining visibly part of the same
publisher family.

## Shared construction

- Use a full-bleed midnight-navy background: `#0B1F33`.
- Build one large game-specific symbol from pale cyan (`#C8FAFB`), bright cyan
  (`#08DCE8`), soft white (`#F7FCFF`), and at most one violet accent (`#8E5BEE`).
- Keep shapes solid and borderless. Do not use outlines, rims, drop shadows, text, card
  indices, faces, or fine decorative detail.
- Keep all identifying artwork within Android's central adaptive-icon safe zone. The
  platform, not the artwork, supplies the circle, squircle, or other outer mask.
- Test recognition at 32 px. If a detail disappears there, remove or enlarge it.
- One small four-point cyan sparkle is the recurring FinitePlay family signature. Violet is
  reserved for a single secondary accent, not a competing focal point.

## Game marks

**Klondike Solitaire:** a three-card fan with one large spade. The cards communicate the
classic solitaire family; the single suit avoids the noise of a literal hand of cards.

**Spider Solitaire:** a symmetrical eight-legged mark with a violet diamond on the abdomen.
The broad, rounded geometry should feel calm and approachable rather than realistic or
threatening.

**FreeCell Solitaire:** four rounded cells in a 2×2 grid, one holding a single lifted card
with a small violet accent. Empty and filled cells read at a glance as the game's one
resource — a spot to set a card aside — without drawing a whole tableau.

**Texas Hold'em:** two private cards rising above a simplified oval poker table, with one violet
dealer button. The paired cards distinguish Hold'em from Blackjack's chip and the table oval keeps
the mark readable without turning it into a miniature card scene.

## Deliverables

The Android launchers use separate vector foregrounds over the shared navy background.
`brand/app-icons/` contains the square SVG masters. Future store exports should be rendered
from those masters at the store's required size, full bleed and without baked-in rounded
corners.
