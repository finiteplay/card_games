# Solitaire Deal Catalogs

Publisher: [FinitePlay LLC](https://finiteplay.org)

What every solitaire's bundled deal catalog does the same way. Implemented by
`:solitaire:catalog`; a game that ships certified deals states its own counts, versions,
and difficulty grading in `docs/games/<game>/DEALS.md` and does not restate this file.

A non-solitaire game has no catalog and skips this layer entirely — that is the point of
it being separate from `docs/PLATFORM.md`.

## The deterministic deal contract

A deal is a seed, not a stored board. Given the same seed, shuffle version, and rules
version, the board is byte-identical on every device and every run, forever. This is what
makes a save a seed plus a move log (`PLATFORM.md` "Persistence"), and what makes an
offline solvability proof still true on the player's phone.

Four versions travel together in every catalog header (`CatalogFormat.kt`'s `CatalogHeader`),
and any of them changing invalidates existing certificates:

- **Shuffle version** — the seed-to-deck mapping (`PLATFORM.md` "Deterministic Shuffle")
- **Rules version** — the game's legal moves and automation
- **Solver version** — the search that certified the deals
- **Catalog version** — the bundled set itself

## Format

A catalog is a versioned binary blob checked into the app's assets, little-endian
throughout, carrying a magic marker, the four versions above, the seed count, the seeds,
and a SHA-256 over the payload.

- The loader verifies magic, versions, count, and hash before any deal is drawn. A
  mismatch is not recoverable by retrying: the app enters its non-playable Unrecoverable
  state showing expected versus actual, rather than silently dealing something
  uncertified.
- The header byte after the four versions is a draw-mode byte for a game that has that axis, or
  is folded into the reserved run for one that does not (`CatalogFormat.kt`'s two dialects). A
  game states which it is, and its own magic, in its own deal-catalog spec.
- Seeds are stored ascending and deduplicated. A catalog with a duplicate seed is invalid.
- Solutions, where a game ships them, are a parallel blob keyed by the same seeds, encoded
  against that game's own move alphabet — which is why the codec lives with the game
  rather than here.

## Partitions

A game may ship its deals as several catalogs rather than one, where a property fixed at
deal time splits them into sets that are not interchangeable. The header carries a
one-byte **partition id** for this; `0` means the game does not partition and ships a
single catalog.

The id is opaque here on purpose. This layer knows only that two catalogs with different
ids hold deals a player cannot compare, and that traversal position is kept per partition
so working through one does not advance another. What the id *means* is the game's:
Klondike numbers its six difficulty levels (`docs/games/klondike/DEALS.md`), and Spider
would number its suit counts, which are the two examples that justified the field
existing rather than each game inventing its own scheme.

A partitioned game states in its own spec what each id is and never renumbers one without
a catalog version bump — a saved traversal position names its partition by id, so
renumbering silently moves a player to a different set.

Presentation order is traversal's job, not the payload's. Seeds stay ascending in the
file whatever order the game wants them met in; the sequential traversal below is what
actually walks a partition in deal-1, deal-2, deal-3 order.

## Certification

Deals are certified **offline only**, by the desktop generator, never on the device. The
app must never solve a deal to decide whether to deal it. A game may still run a bounded,
board-specific on-device search for a hint — that proves something about the board in
front of the player, not about the deal, and it never feeds catalog certification.

A build gate asserts the generator's module cannot reach any app's runtime classpath, so
the offline path cannot ship.

A game may ship a level of **uncertified** deals if it says so in its own spec and names the
level: deals no search resolved inside its budget, which are unproven rather than known
unwinnable. Deals a search *proved* unwinnable are never shipped by anyone. Klondike ships
exactly one such level (`docs/games/klondike/DIFFICULTY_LEVELS.md` "Insane ships
uncertified"); everything else in every catalog carries a replay-verified line.

## Traversal

Deal selection is sequential: deal 1, deal 2, deal 3, ... in catalog order, wrapping back to
1 after the last record. The traversal position persists alongside the catalog version, so a
player works through the catalog in a stable, predictable order across restarts and a new
catalog version restarts cleanly at 1 rather than resuming into a different set. A
partitioned game keeps one position per partition, so switching away and back resumes that
partition where it was left rather than restarting it or skipping ahead.

A freshly dealt game is saved immediately, not deferred until the player's first action:
the traversal position advances the instant a seed is drawn, so a later save could let a
kill-before-first-move restart skip straight to the *next* deal instead of resuming the one
on screen.
