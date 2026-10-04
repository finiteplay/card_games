# Spider Deal Catalog

The catalog format, the deterministic deal contract, the offline-only certification rule, and
non-repeating traversal are shared by every solitaire and specified in
[CATALOG.md](../../solitaire/CATALOG.md). This file is Spider's instance of it.

## What ships today

**ONE ships a certified 10,000-seed catalog, scanned ascending from seed 1.** TWO ships a
**curated 5,443-seed catalog**, not a scan: seeds were selected from an 85,000-seed survey by move
quality and coverage rather than "first N to certify" — see "TWO's curated selection" below.

FOUR's catalog is not built by `SpiderSolver` at all — this internal solver cannot decide a
four-suit board (not rarely, but never in any run measured, at up to twenty times the interactive
budget; "The solver" below has the numbers) — it is certified separately by an external solver and
imported via `writeCatalogFromExternalSolutions`; its own generation and count are out of scope
here.

| Suit count | Partition id | File | Certified | How |
|---|---|---|---|---|
| ONE | 1 | `one.catalog` | yes | scanned, `SpiderSolver` |
| TWO | 2 | `two.catalog` | yes | curated selection, `SpiderSolver` |
| FOUR | 3 | `four.catalog` | yes | external solver import |

Partition ids are fixed — `docs/solitaire/CATALOG.md` "Partitions" — and are the suit count's
ordinal plus one, the same convention Klondike uses for its difficulty tiers.

**Unlike Klondike's difficulty partitions, Spider's suit-count partitions are not exclusive by
construction.** A seed alone does not name a board — `dealGame(seed, versions, suitCount)` takes
the suit count as part of the shuffle, so seed 42 at ONE and seed 42 at TWO are unrelated boards.
The generator still keeps every shipped seed number unique across the two catalogs (a seed
certified for ONE is never also offered for TWO), purely so a bare seed number is never ambiguous
about which catalog it came from — nothing in the app ever needs that lookup (the active suit
count is always known from the session), but the solutions blob below is keyed by seed alone and
would silently collide without it.

## The solver

`:games:spider:solver` (`SpiderSolver`) certifies a deal in four stages: greedy strategy playouts,
a beam-limited depth-first search, a full-legal transposition-tabled depth-first search, and a
weighted best-first search (`DESIGN.md` "Hint" describes each and why it is there).
`SpiderSolver.certify(state)` returns the actual winning line as real rules-layer `Move`s, not just
a verdict, by recording the chosen move at each step of whichever stage wins.

An opt-in catalog-only stage, `certifyGuided`, implements bounded receding-horizon play: search a
reduced tree to a configurable depth, retain the highest-scoring position, commit that short line,
and repeat. It considers only the longest suited run in each source column, treats empty
destinations as symmetric, prioritizes same-suit destinations, retains a fixed-size generational
transposition cache, forces an irreversible reveal/separation when ordinary score improvement
stalls, retains alternate high-scoring paths as bounded return points for later dead ends, and
varies both column order and a preferred suit across attempts. Returned lines have the
same replay requirement as every other certificate. The stage is disabled by default and is not
part of the interactive Hint budget; its current FOUR measurements are recorded below.

**The per-suit-count win rates behind S5 — ONE about 99.6%, TWO about 11.7%, FOUR effectively
never — are the 1,000,000-deal survey's**, measured when the solver had only the playout and
full-legal DFS stages and before the playout's scoring gained the waterfall/shortest-column/
build-high terms. They are the numbers the shipped catalogs were built against and are correct as
history. **TWO is no longer current**: a 200-deal run at the interactive budget after the beam
stage and the scoring work measures **26.0% (12.0% strategy, 14.0% search)**. The strategy figure
reproducing the old 11.7% is the useful part of that — it says the playout stage behaves as it
always did, and the new search stages roughly doubled the rate on top of it. A full re-survey at
a million deals is what would replace the shipped figure; until then quote 26% as a 200-deal
measurement, not as the catalog's rate.

**FOUR is a different case, and the distinction matters for what may be claimed.** Runs so far:
1,000 deals at the interactive budget (400,000 nodes, 4 s, 40 playouts, beam 3); 32 deals at
roughly twenty times that budget (3,000,000 nodes, 30 s, 200 playouts, at beam 3 and beam 6); and
200 deals after suit-symmetry canonicalisation landed. Every one returned **zero deals decided
either way** — no wins, and no exhaustion proofs either, 100% budget-limited. So the honest
statement about FOUR is not that its deals were measured to be rarely winnable; it is that **this
solver cannot decide a four-suit board at all**.

The receding-horizon stage was also tried on seed 1 at 200,000,000 nodes, depth 13, sixteen varied
attempts, and a 1,000,000-node cap per horizon. It did not find a win. This is a bounded failed
search, not evidence that the deal is unwinnable; an independently replayed solution from the
external research solver proves that seed is winnable under these rules.

That is worth contrasting with the published figure. Blake & Gent's Solvitaire measures *thoughtful*
Spider — perfect information, which is what a solver has — at 98.487% ± 1.513% winnable, and cites a
stronger result still (`docs/games/spider/PUBLIC_STRATEGY_RESEARCH.md` "Sources"). Their variant
list defines "Spider 1 Suit" and "Spider 2 Suit" relative to the base game, so that figure is the
four-suit deal. **Four-suit deals are therefore almost certainly winnable in the large majority of
cases, and 0% decided is a statement about this solver, not about the deals.** Their solver is also
plain DFS — the distance is in the pruning, not the search paradigm or the hardware.

Suit-symmetry canonicalisation ([canonicalHashOf]) is one of the techniques they name, and adding it
did not move FOUR at all: a ≤4! = 24-fold collapse is a constant factor, and the gap is evidently
not a constant factor. The technique of theirs still untried here is **dominances** — provably safe
forcing rules that replace a branch with a single move — which prune multiplicatively rather than
by a constant, and which they identify as their own novel contribution. That is the next thing to
try, and it is what `TODO.md` records.

## Generation

**ONE** is built by `tools/catalog`'s `org.finiteplay.spider.tools.catalog` package (the
`buildSpiderDealCatalogs` Gradle task, kept separate from Klondike's own catalog tooling so one
game's build failing never looks like the other's):

1. Scans ascending seeds — the same ones the game deals from (`DealSequence`, `Survey.kt`) —
   skipping any seed already certified for another suit count.
2. Solves each candidate with `SpiderSolver.certify`.
3. **Independently replays every returned line through the real reducer** (`isLegal`/`applyMove`
   from `:games:spider:rules`) before accepting it — the solver's own verdict is never shipped
   untrusted, mirroring Klondike's D1b generation.
4. Stops once the target seed count is accepted.

The scan is multi-threaded and stops as soon as the target is reached, so which seeds near the
boundary get fully evaluated depends on thread scheduling — re-running the generator is not
guaranteed to pick the exact same seed set. What ships is never in question regardless: every seed
that ships passed the independent replay in step 3.

Seeds are stored ascending in the catalog file, not in presentation order — the sequential
traversal below walks a suit count in deal-1, deal-2, deal-3 order (`docs/solitaire/CATALOG.md`
"Deal Selection").

### TWO's curated selection

TWO does not use the scan above. An 85,000-seed survey ran both `SpiderSolver`'s greedy-playout
stage and its beam-limited DFS stage independently (`solveGreedyOnly`/`solveBeamOnly`, each without
falling through to the slower DFS/A* stages) and recorded whether each stage solved the deal and in
how many moves. From that survey, seeds were selected into four buckets, each shipped under the
stage that actually solved it (`certifyGreedyOnly`/`certifyBeamOnly`, replayed for real like every
other certificate):

| Bucket | Selection | Shipped count |
|---|---|---|
| 1 | Both stages solve, both under 200 moves, ranked by lowest greedy move count | 100 |
| 2 | Greedy solves, beam does not, under 250 greedy moves | 900 |
| 3 | Beam solves, greedy does not, under 250 beam moves | 1,000 |
| 4 | Either or both solve with a qualifying move count in [250, 350] | 3,444 |

Bucket 4's target was 4,000; buckets 2 and 3 were originally ranked by ascending seed number, which
packed most candidates into the same low seed range ONE's catalog already claims, and 250–290 of
each bucket's seeds were dropped for that partition collision. Buckets 1–3 were backfilled from the
same 85,000-seed survey (excluding seeds already claimed by ONE, FOUR, or the original selection) to
restore their full 100/900/1,000 counts; bucket 4 lost few enough seeds (556 of 4,000) that it
shipped as-is rather than being backfilled to its full target. Total: **5,443 seeds**, all disjoint
from ONE and FOUR.

## Solutions: a verification artifact, and now a shipped asset too

Every certified seed's winning line is written by the same generation pass, using
`SpiderSolutionCodec` (`tools/catalog` — not shared with Klondike's `CompactSolutionCodec`, which
is bit-packed against Klondike's own move alphabet and drops draws/recycles that Spider has no
equivalent of). That format is a flat, seed-indexed list, not seekable the way Klondike's is:
Spider's own log alphabet already packs a whole move into one to four bytes (`SpiderLogCodec`),
and twenty-odd thousand short lines decode in one pass cheaply enough that a seekable index was
not worth a second format for this artifact's own job of backing `verifySpiderDealCatalogs`'s
replay sample.

**That format is not bundled under `assets/`, and still is not.** Klondike's own format stores a
*choice list* — six to fourteen bits per choice, draws and recycles omitted entirely — and ships
50,000 solutions in 5.2 MB. `SpiderSolutionCodec` stores full moves with no such compaction, and
Spider's solved lines run far longer than a move-minimizing search would produce (a strategy
playout is not trying to minimize moves, only to win): the raw encoding for the committed
20,447-certificate blob (ONE, TWO, and FOUR's full 5,004-seed campaign) comes to 16,728,231 bytes
(~16.0 MB), which alone would exceed the platform's entire 15 MB release-download budget
(`docs/PLATFORM.md` "Performance"). Gzipped it is 6,978,666 bytes (~6.7 MB). So this format stays a **committed build/verification artifact**, at
`tools/catalog/data/spider_solutions.bin.gz`, never a shipped Android asset — mirroring Klondike's
own documented fallback for oversized solution sets (`docs/games/klondike/DEALS.md` "Build
Artifacts": "large solution certificates may be retained as compressed CI artifacts rather than
shipped or committed"). `verifySpiderDealCatalogs` reads it to replay-sample certificates without
re-solving.

**A second format now ships, for the on-device hint-follows-a-known-line feature this section used
to say would arrive later.** `games/spider/rules/.../solution/CompactSolutionCodec.kt` — mirroring
Klondike's own `CompactSolutionCodec` and FreeCell's adaptation of it — bit-packs a choice list
against Spider's own two-move algebra:

- `Move.TableauToTableau`'s `fromColumn`/`toColumn` pair is packed as one 7-bit field (they are
  never equal, so 90 of 128 values are ever used) instead of two independent 4-bit columns.
- Its `fromIndex` is stored as a **delta from `sequenceStart`**, not an absolute pile position —
  zero, costing one bit, whenever the move lifts the whole movable run, which real winning lines
  overwhelmingly do; a shorter lift escalates the same way Klondike's own pile-advance encoding
  does.
- `Move.DealRow` is one bit; nothing about it is derivable the way Klondike's draws are, so it is
  always stored (`RULES.md` "What Spider does not have").

Never trusted past regardless of the source blob's own claims: `CompactSolutionCodec.encodeCatalog`
independently replays every line through the real reducer and drops (never fails on) any seed that
does not actually reach a win.

`tools/catalog`'s `compactSpiderSolutions` (the `compactSpiderSolutions` Gradle task, `compact` CLI
mode) reads the committed verification blob above and re-encodes it this way into
`games/spider/app/src/main/assets/solutions.bin` — no re-solving, since existing certificate
lengths are already realistic and the size problem was purely encoding inefficiency, not
certificate quality. **Result: 5,140,929 bytes**, covering all 20,447 committed certificates (ONE,
TWO, and an externally-solved FOUR partition at its full 5,004-seed campaign target) — a real, if
modest, win from packing the column pair jointly and delta-encoding the lift depth, not a dramatic
one — most of a move's entropy is still which two columns it touches.

`SpiderSolutionCatalog` (`games/spider/app/.../deal/SpiderSolutionCatalog.kt`) loads this asset
lazily, mirroring `SolutionCatalog`/`FreeCellSolutionCatalog` exactly, and `SpiderViewModel` primes
`HintEngine`'s cache with the stored line for every fresh, certified deal at any suit count (New
Game, Restart, and a cold-start restore-or-deal that lands on a raw board), FOUR's 5,004-seed
catalog included. A player who takes each hint then walks
the shipped line with no live search at all; deviating costs nothing, since the cache simply stops
matching and `HintEngine`'s own search resumes exactly as before it was primed
(`docs/games/spider/EXECUTION_PLAN.md` S5).

## Deal numbering and traversal

Once a suit count has a certified catalog, its deal number is the seed's **1-based position in
that catalog's ascending list** — the same convention as Klondike's `dealDifficulty`/`dealNumber`,
not a separate scheme. `SpiderViewModel.seedFor` picks the catalog when one exists for the active
suit count and falls back to the uncertified formula (`DealSequence.seedFor`) only when none is loaded (tests).

`SpiderTraversalStore` keeps the position per suit count exactly as before, restarting each app
launch where it left off; the only change is that `next`/`advancePast` now take the catalog's
record count as a wrap point for a certified suit count, so the number cycles through the ten
thousand seeds instead of walking off the end the way the uncertified formula's unbounded number
does. This mirrors Klondike's own shipped selection — `DifficultyDealSeedSource`, a plain
sequential index wrapping per difficulty — and, since `docs/solitaire/CATALOG.md`'s traversal
contract was made sequential platform-wide, also `:solitaire:catalog`'s own `DealTraversal`,
which FreeCell wires directly into production seed selection.

## Versioned rules

Every catalog declares catalog, rules, shuffle, and solver versions, plus record count and a
SHA-256 payload hash, exactly as `docs/solitaire/CATALOG.md` specifies. A seed is valid only for
its declared versions; any change to the shuffle, the rules, or the solver invalidates every
existing certificate and requires regenerating both catalogs together with `solutions.bin`.

## Catalog gate

`verifySpiderDealCatalogs` (the `tools/catalog` Gradle task) re-checks committed evidence and never
solves: every catalog loads, header and manifest agree on versions, partition id, record count and
payload hash, no seed appears in both catalogs, seeds are ascending and unique, and a sample of
certificates replays to a win under the current rules. It passes with a notice while no manifest is
committed, the same as Klondike's gate does before its own catalog exists.

`:games:spider:app`'s release runtime classpath excludes `:tools:catalog`
(`assertAppExcludesSolver`, mirroring Klondike's own gate), so the offline generation and solving
code path cannot ship.
