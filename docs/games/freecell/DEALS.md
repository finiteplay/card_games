# FreeCell Deal Catalog

The catalog format, the deterministic deal contract, the offline-only certification rule, and
non-repeating traversal are shared by every solitaire and specified in
[CATALOG.md](../../solitaire/CATALOG.md). This file is FreeCell's instance of it, and a catalog is
now bundled and certified — `EXECUTION_PLAN.md`'s Status table says exactly what exists and what
evidence backs it.

## Why FreeCell's catalog problem is smaller than Klondike's or Spider's

Every FreeCell card is visible from the moment the deal completes (`RULES.md`), so there is
nothing a solver has to search *for* the way Klondike's face-down cards or Spider's undealt stock
hide information — only whether a winning line exists among the moves a fully-informed player
could make. That, combined with four free cells' worth of temporary storage and the ability to
empty and refill any column, is why FreeCell decks are famously almost all solvable: the original
Microsoft Windows FreeCell deals (numbered 1–32,000) are winnable with the sole well-known
exception of #11982, and broader exhaustive-solver surveys of random deals put the unsolvable
share at a small fraction of one percent.

**This is a fact worth exploiting, not assuming.** FreeCell has no difficulty ladder the way
Klondike does and no near-zero-yield tier the way Spider's four-suit deals are, so certification
here is expected to be closer to "confirm the overwhelming majority of candidates and discard the
rare loss" than Klondike's "grade and select from a much larger population." Whether that holds
for this game's exact rules and shuffle is for `EXECUTION_PLAN.md`'s F5 package to measure, not
for this document to assume before a solver exists.

## What ships

**One flat, unpartitioned catalog** — partition id 0 (`docs/solitaire/CATALOG.md`
"Partitions") — since FreeCell has no difficulty or suit-count axis to split it by (`RULES.md`
"Free cells and difficulty"). The catalog now ships at the full **10,000**-seed planning target,
the same scale Spider's ONE and TWO catalogs ship at (magic `FRCL`, catalog version 1) — grown
from the first shipped catalog's 2,000 seeds by re-running `buildFreeCellDealCatalogs` with a
bigger target, scanning further into the ascending candidate stream to absorb the solver's own
~70–80% yield (`The solver`, below).

## The solver

`games/freecell/solver` exists: a single well-ordered depth-first search with transposition-table
cycle detection over a canonical fingerprint that collapses column-order and free-cell-order
symmetry (`CanonicalSearchHash.kt`), mirroring the shape of the DFS half of Klondike's and
Spider's own solvers, without Klondike's weighted-A* machinery that exists specifically to handle
the deals a simple search cannot resolve within budget.

**Measured, not assumed:** on the first 30 ascending seeds under the default budget (3,000,000
nodes, 20 seconds), this DFS solves roughly 70%; the rest time out, none are proven unsolvable. A
7×-larger budget resolved none of that timed-out remainder — evidence this search is structurally
stuck on those particular boards, not merely slow. Two ordering alternatives were tried against
this same measurement and rejected: a heuristic ordering by the resulting state's own foundation
deficit performed *worse* (it commits DFS to whichever child looks locally best and lets it sink
arbitrarily deep into one unproductive branch instead of backtracking), and a tight depth cap
intended to bound the occasional very-long certificate made the solve rate dramatically worse
instead (it prunes a genuinely necessary long branch as readily as an unproductive one). The plain
move-type ordering in `Solver.kt` is what measured best of the three.

This is exactly the finding this section originally invited: a simple DFS is a real, working start
— most candidates solve quickly — but is not yet a complete answer for every deal. Generation
(below) is designed around skipping what it cannot resolve rather than blocking on fixing it here;
a better algorithm for the resistant remainder is a candidate improvement, not a blocker, and
belongs in `TODO.md` if generation's own yield turns out to need it.

`games/freecell/solver` will also, later, back the on-device Hint search (`DESIGN.md` "Hint") —
that reuse is F6's job, not this package's. (As delivered, Hint searches with its own best-first
search first and falls back to this DFS; `DESIGN.md` "Hint" says why.)

## Generation

Once the solver exists, generation follows the same shape Klondike's D1b and Spider's S5 both
use:

1. Scan ascending candidate seeds from the shared deterministic shuffle.
2. Solve each candidate.
3. Independently replay every returned line through the real reducer (`isLegal`/`applyMove` from
   `games/freecell/rules`) before accepting it — the solver's own verdict is never shipped
   untrusted.
4. Stop once the target seed count is reached.

A candidate the solver reports unsolvable, or cannot resolve inside its search budget, is skipped
rather than shipped — the same "solved, or not shipped" rule every catalog in this repo follows
(`docs/solitaire/CATALOG.md` "Certification"). FreeCell defines no uncertified level the way
Klondike's Insane or Spider's FOUR does; if the near-total-solvability assumption above turns out
to be wrong at scale, that is a finding for the solver package to report and for this document to
be updated with, not a gap to paper over silently.

## Versioned rules

Every catalog declares catalog, rules, shuffle, and solver versions, plus record count and a
SHA-256 payload hash, exactly as `docs/solitaire/CATALOG.md` specifies. A seed is valid only for
its declared versions; any change to the shuffle, the rules, or the solver invalidates every
existing certificate and requires regenerating the catalog.

## Shipped Solutions

Every certified seed's winning line ships in `games/freecell/app/src/main/assets/solutions.bin`
(magic `FCSL`, `CompactSolutionCodec` — `games/freecell/rules/.../solution/CompactSolutionCodec.kt`),
so Hint can prime itself with a known solution on a fresh deal instead of searching from scratch,
mirroring Klondike's own `solutions.bin` and `SolutionCatalog`.

This was not always shippable. The DFS fallback generation originally certified every seed with
(`The solver`, above) keeps the *first* winning line it reaches rather than a short one — the same
"hundreds to thousands of moves on a deal a person wins in about ninety" behavior `HintEngine.kt`'s
own doc describes. Measured across the full 10,000-seed catalog, those certificates ran a median
of 4,915 payload bytes, p90 30,278, p99 70,409, and a max near 79,000 bytes — a whole-catalog size
in the hundreds of megabytes, far past the platform's 15 MB total release-download budget
(`docs/PLATFORM.md` "Performance") on encoding efficiency alone, since the problem was certificate
*length*, not the bits spent per move.

The fix re-solves every already-certified seed with `BestFirstSolver` — the same short-line search
Hint's own interactive path already prefers (`HintEngine.kt`) — under a generous one-time offline
budget (3,000,000 nodes / 30 seconds; every seed is already known solvable, so this is not proving
solvability again, only finding a shorter line than the DFS did). Every new line is independently
replayed through the real reducer before it replaces the old one; a seed `BestFirstSolver` cannot
beat within budget keeps its original DFS-derived certificate rather than being dropped (3 of
10,000 did, on the run that produced the shipped asset). Re-solved, the same 10,000 certificates
run a median of 250 payload bytes, p90 281, p99 309 — over an order of magnitude smaller — before
`CompactSolutionCodec`'s bit-packing is even applied. The bit-packed, index-seekable
`assets/solutions.bin` this produces is **1.06 MB** for all 10,000 seeds, comfortably inside the
release-download budget. `tools/catalog:shrinkFreeCellSolutions` is the one-off tool that did this;
it is not a build step, since the shrunk certificates are themselves committed (both to
`tools/catalog/data/freecell_solutions.bin`, the verification artifact, and to the shipped asset).

## Catalog gate

`.\gradlew.bat :tools:catalog:verifyFreeCellDealCatalogs` re-checks committed evidence and never
solves: the catalog loads, header and manifest agree on versions, partition id, record count and
payload hash, seeds are ascending and unique, and a sample of certificates replays to a win under
the current rules. Passing evidence: `games/freecell/app/src/main/assets/catalogs/freecell.catalog`
plus its `manifest.json`, and `tools/catalog/data/freecell_solutions.bin` (kept outside `assets/`
as a verification artifact separate from the shipped, bit-packed `assets/solutions.bin` above —
this one is the flat, seed-indexed form `FreeCellSolutionCodec` reads to replay-check without
re-solving).

`games/freecell/app`'s own `SolutionsAssetTest` is the parallel gate for the shipped asset itself:
it decodes `assets/solutions.bin` directly and independently replays a sample of its lines through
the real reducer, so a future corrupt re-encode fails `check` rather than surfacing to a player as
a missing or wrong hint.

`games/freecell/app`'s release runtime classpath excludes `:tools:catalog`
(`assertAppExcludesSolver`, mirroring Klondike's and Spider's own gate; the app depends on
`:solitaire:catalog` to read and traverse the bundled asset, never on `:games:freecell:solver` or
the generator), so the offline generation and solving code path cannot ship.

## App integration

`FreeCellCertifiedDealCatalog` validates the bundled asset at startup (magic, versions, partition
id); a failure leaves the game on the non-playable Unrecoverable screen behind a retry, since
FreeCell has no uncertified tier to fall back to (`UI_SPEC.md` "Screens and States"). New Game
draws from `FreeCellTraversalStore`'s own sequential position over the catalog
(`org.finiteplay.solitaire.catalog.catalog.DealTraversal`), advancing and persisting the position
each time so a killed-and-restarted app resumes deal 1, deal 2, deal 3, ... where it left off
rather than restarting it. Replay and restoration are unaffected: both redeal or resume the
session's own already-chosen seed, never drawing a new one.

The status row's hand number (`UI_SPEC.md` "Status Row") is the same thing as this traversal
position: `FreeCellViewModel.dealNumber` is the active seed's 1-based position within the
catalog's own committed seed list (`catalog.seeds.indexOf(seed) + 1`), which is exactly the order
the sequential traversal visits it in — the same shape as Klondike's own `dealNumber` (position
within its difficulty tier's list).
