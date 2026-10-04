# Klondike Deal Catalog

The catalog format, the deterministic deal contract, the offline-only certification rule,
and non-repeating traversal are shared by every solitaire and specified in
[CATALOG.md](../../solitaire/CATALOG.md). This file is Klondike's instance of it: the
counts, the versions, the difficulty grading, and the generation pipeline.

## Purpose

The app ships fast, offline, guaranteed-solvable games without ever running a solver on the phone to certify a deal — that only ever happens offline, in this pipeline. (The app does run a different, narrowly scoped on-device search to power the Hint action against whatever board is already in front of the player; see `DESIGN.md` "On-Device Hint Search". That search never certifies a deal and has no bearing on this catalog.)

**The app ships the certified bundle: six 10,000-deal catalogs, with replay certificates for every level except Insane.** It validates all six partitioned assets before dealing a draw-one game; an invalid or missing asset opens the non-playable Unrecoverable screen rather than falling back to an unverified shuffle.

Validation reports remain build artifacts. Solution move sequences **do** ship, as a
packed binary asset — see "Shipped Solutions" below; that reverses this document's
earlier position, which predated hints being expected to follow a known path.

## Retired Interim Seed Source

Before D1b, `GameViewModel` drew draw-one deals from a per-difficulty
interim catalog: **10,000 seeds at each of Trivial, Easy, Medium, Hard, Expert, and Insane**
(`games/klondike/app/.../deal/InterimSeeds{Trivial,Easy,Medium,Hard,Expert,Insane}.kt`, indexed by
`InterimSeedCatalog.kt`), cut from the ten-million-seed grading pass by `tools/catalog`'s
`build-levels` — see `DIFFICULTY_LEVELS.md` "Levels as shipped" for which population feeds
which level and how each is ranked. For every level but Insane, membership is simultaneously
its grade and the proof it is solvable. Which list is used
follows the Settings difficulty selection (`docs/games/klondike/UI_SPEC.md`); "Random" picks
a level per deal.

Each difficulty keeps **its own position**, stepping deal 1, 2, 3, ... through
its own list and wrapping at the end, so switching to Hard for a few games and
back resumes Easy where it was left rather than restarting it or skipping
ahead. Positions persist across a killed-and-restarted app
(`CatalogTraversalStore`). `RandomDealSeedSource` (uniformly random, not
guaranteed solvable) remains available for draw-three, which has no catalog.

This is deliberately lighter than everything below: no version header, no
binary catalog, no committed certificates, no `verifyDealCatalogs` gate, and
 it was generated and swapped in before the rules freeze — none of which the
rest of this document permits for the real catalog. It exists only so MVP
testing deals verified-solvable games instead of fully random ones. **RF has
since closed** (`EXECUTION_PLAN.md` RF), and D1b now supersedes it. The source remains only
as a unit-test fixture while the generated Kotlin lists remain checked in for comparison; the
runtime draws exclusively from the binary catalog assets.

The runtime source keeps the same status-row contract: it shows the active seed's 1-based
position **within its own level's list**, after the level name
(`GameViewModel.dealNumber`), so "Hard #7" means the seventh Hard deal rather
than the seventh of sixty thousand, and a tester can name which deal of which
level reproduces a given issue. Under "Random" the number therefore jumps
between levels rather than counting up, since each level advances separately.
The binary catalog uses the same per-level index, so the display remains valid after D1b.

## Difficulty Grading

Alongside the deal number, the status row shows a level for every draw-one catalog seed,
shown as e.g. "Medium #5" (`GameViewModel.dealDifficulty`,
`docs/games/klondike/UI_SPEC.md`). Draw-three deals are ungraded (they are plain random
shuffles with no solver certification at all — see "Deal certification" in
`DESIGN.md`), so no level shows for them.

**How a seed is graded, and which level that puts it in, is specified in
`DIFFICULTY_LEVELS.md` "Levels as shipped".** In outline: every seed is graded by the lowest
ruleset of that document's cumulative ladder whose undeviating order wins it, and the deals
no ruleset wins are graded by the two searches instead. A ruleset winning *is* the proof the
deal is solvable, so those seeds need no separate certification pass and a deal grades in
microseconds; only the search-cut levels pay for a search.

The grade shown to the player is the **level**. For the four levels a ruleset reaches, that is
also the ruleset's name — they were briefly out of step and were merged back into line
(`DIFFICULTY_LEVELS.md` "Levels as shipped"). Expert and Insane have no ruleset behind them.

This is the fourth approach tried, and the first grading purely by required
strategy rather than by search cost or playout luck: two node-count-based
proxies (D1s's own *exact* A* search, and the weighted Hint-portfolio node
count that replaced it) and a naive random-playout win-rate grader were all
tried first and are documented in `GradingSpike.kt`'s history, alongside the
finding that motivated moving past them — real player ratings gathered
against the node-count grade showed essentially no correlation with how hard
a deal actually felt (r≈0.05), while elapsed play time and move count both
correlated strongly (r≈0.74–0.75) instead. A strategy-tier grade targets that
signal: it measures how much of a human's actual toolkit a deal demands, and
its own move counts rise with the tier. Two further mechanisms were built and
measured before the current one — an oracle-guided walk and certificate replay —
and both failed for reasons recorded in `TierClassifier`'s documentation so they
are not retried.

Which population feeds which level, the quotas, and the measured output of the
ten-million-seed grading pass are `DIFFICULTY_LEVELS.md` "Levels as shipped" — that is where
the cut is specified and the only place its numbers are stated. The pass starts from seed 1
and is deterministic, so regenerating reproduces the same six lists exactly.

Generated by `tools/catalog`'s `grade-seeds`, `grade-search`, `enrich-hard`, and
`build-levels` (`DIFFICULTY_LEVELS.md` gives the exact invocations) and checked into
`:games:klondike:app` as four files per level — chunked rather than one combined table
because each compiles into its file's static initializer, and ten thousand entries in a
single initializer risks the JVM's 64 KB method limit. The lists are pasted rather than
shared because `:games:klondike:app` must never be reachable from `:tools:catalog`'s
classpath; the rulesets themselves live in `:games:klondike:solver`, which *is* already on
`:games:klondike:app`'s classpath (it powers the on-device hint search), which is what lets
the Hint action reuse the same top ruleset (`DESIGN.md` "On-Device Hint Search").
Regenerate all six together, with `assets/solutions.bin`, after any rules or shuffle change.

## Shipped Solutions

Every catalogued seed **outside Insane** ships with a winning move sequence, in
`games/klondike/app/src/main/assets/solutions.bin` (`CompactSolutionCodec`, in the rules
module so the generator that writes it and the app that reads it share one definition).
Insane ships none by definition (`DIFFICULTY_LEVELS.md` "Insane ships uncertified").

The format stores a **choice list**, not a move list, and packs each choice to the bits it
needs — six for a tableau-to-foundation play, fourteen at the widest. Draws and recycles are
not stored at all: reaching a stock or waste card means drawing until it is on the waste and recycling
when the stock runs out, and which of the two applies is a fact about the board rather than
about the line, so a waste choice stores how far to advance the stock and waste and the decoder replays
the draws against the real board. Between them the two changes cost about a third of every
line and two thirds of what remains.

A **seekable index** — seeds as ascending deltas, each with its block length — is written
ahead of the blocks, so one line decodes without touching the rest. That is what makes a
catalog of tens of thousands of solutions readable at all: decoding the whole thing to answer
for one seed would allocate millions of `Move` objects for a single hint.

Measured on what ships: **50,000 solutions in 5.2 MB**, 110 bytes each against the previous
format's 320, so the asset carries nine times the solutions for three times the size. The
release APK is 7.2 MB against the 15 MB budget (`DESIGN.md` "Performance").

For every level below Expert the stored line is the one the grading tier's own rules played.
Expert's is the certificate of whichever search found it — which is also *shorter*, because a
search aims at a win while a ruleset merely reaches one.

Decoding preserves the choices and the win, not the original line byte for byte: draws come
back at the last moment that still reaches the card, so a line that drew early decodes with
those draws moved down to their play. The board sequence at every choice is identical, which
is all a replayed hint depends on.

The app loads the asset lazily on first hint, never at startup: nothing needs it until
the player asks, and paying for parsing during cold start would spend the 1.5 s
startup budget on something most sessions never touch. A missing or corrupt asset
degrades to "no stored solutions" — hints then resolve exactly as they did before,
top ruleset then search — so this is an accelerator, never a dependency.

A stored line is handed to `HintEngine` as its certificate cache, which means it is
subject to exactly the same guarantee as a searched one: replayed through the canonical
reducer before being trusted, and silently discarded if any step proves illegal. A
player who takes each hint therefore walks a known winning line with no search at all,
and the moment they deviate the board stops matching and the search takes over.

## Debug-only difficulty rating

A debug-only build (`games/klondike/app/src/debug`, absent from release builds via
source-set separation, not a runtime flag — the same mechanism as the
near-win fixture button, `EXECUTION_PLAN.md` A2a) shows a rating prompt in
the win dialog once a game is won, letting a developer
record their own subjective difficulty tier for that deal next to the
solver's grade for the same seed, to sanity-check the proxy against a human
impression. Ratings are appended as CSV rows to `difficulty_ratings.csv` in
the app's private storage — never networked or synced, per this project's "no
networking, analytics" invariant — and retrieved with
`adb shell run-as org.finiteplay.klondike cat files/difficulty_ratings.csv`
(or `adb pull` against a debuggable install's data directory).

## The certified catalog (D1b)

The six levels are the product; D1b gives them the format, versions, and verification gate.
The rules freeze preceded generation (`EXECUTION_PLAN.md` RF), and the runtime validates the
six bundled assets before it deals a draw-one game.

**Six catalogs, one per level.** Each level is a **partition**
(`docs/solitaire/CATALOG.md` "Partitions") so traversal keeps a position per level and
switching levels does not disturb the others. The ids are fixed — renumbering one moves a
player's saved position into a different level — and are the level's ordinal plus one:

| Level | Partition id | File | Certified |
|---|---|---|---|
| Trivial | 1 | `trivial.catalog` | yes |
| Easy | 2 | `easy.catalog` | yes |
| Medium | 3 | `medium.catalog` | yes |
| Hard | 4 | `hard.catalog` | yes |
| Expert | 5 | `expert.catalog` | yes |
| Insane | 6 | `insane.catalog` | no, by definition |

**The certificates already exist.** `solutions.bin` is the certificate set, written and
replay-verified seed by seed by `build-levels`. D1b packages rather than re-derives:
`tools/catalog`'s `build-catalogs` reads the exported level lists and that asset, selects
nothing, and solves nothing. Anything that changes which seeds ship goes through
`build-levels` first.

**Seeds are stored ascending, not in presentation order.** The sequential traversal already
walks a level in deal-1, deal-2, deal-3 order, which is what the fixed permutation in
`build-levels` exists to do for the Kotlin lists. Once a level is a real catalog, that
permutation is redundant and retires with the interim source.

**`verifyDealCatalogs`** re-checks the committed evidence and never solves: every catalog
loads, header and manifest agree on versions, partition id, record count and payload hash,
no seed appears in two levels, seeds are ascending and unique, and a sample of certificates
per certified level still replays to a win under the current rules.

Measured on the real data, held out of the repo: 60,000 seeds across six catalogs, 80,064
bytes each, 480 KB in total against `solutions.bin`'s 5.2 MB — the seeds are the cheap half.

**Rollout.** Expansion increases the manifest count without changing the catalog format,
loader, selection algorithm, or saved traversal schema. Every expansion reruns the complete
generation and catalog gates. Runtime code always uses the record count declared in the
validated header.

## Versioned Rules

Every catalog declares:

- Catalog version
- Rules version
- Shuffle algorithm version
- Solver version
- Draw mode
- Record count
- SHA-256 content hash

A seed is valid only for its declared rules, shuffle, and draw-mode versions. Saved games retain these versions so replay remains deterministic after upgrades.

## Deterministic Deals

Use a platform-independent pseudorandom generator and Fisher–Yates shuffle. The implementation must:

- Produce identical decks on Android and JVM test tools
- Avoid platform-default random-number generators
- Use fixed-width integer operations
- Include committed reference vectors mapping seeds to complete deck orders

The execution specification selects and documents the exact generator, bounded-number method, byte order, and reference vectors before implementation.

## Offline Generation

The catalog tool:

1. Generates a unique candidate seed.
2. Deals it with the versioned shuffle implementation. Every game starts on this raw
   deal regardless of the automatic-moves setting — automation never runs before the
   player's first action (`RULES.md` "Automatic Foundation Moves") — so there is a
   single starting board to solve, not one per setting.
3. Solves it under the exact Klondike rules and declared draw mode.
4. Replays the returned move sequence with an independent rules validator.
5. Emits the seed only when the replay reaches the win state.
6. Continues until the requested catalog count is reached.

The solver may use substantial desktop compute, running as a forked process with its own heap rather than inside the build. Solver shortcuts must not change legal game behavior. Conservative automatic foundation moves may be applied because they cannot remove a winning path; the validator still checks every resulting transition.

Generation runs only after the rules freeze in `EXECUTION_PLAN.md`. A rules change invalidates every certificate, so the catalog is certified after real gameplay has exercised the rules, not before.

## Catalog Format

Each catalog is a binary asset containing:

- Fixed magic value
- Format version
- Rules and shuffle versions
- Draw mode
- Unsigned 32-bit record count
- The declared number of unsigned 64-bit seeds
- SHA-256 payload hash

The header uses a fixed byte order defined by the execution specification. Unknown versions or invalid hashes are rejected.

Raw seed payload size is `8 × record count` bytes: 80 bytes initially and 800,000 bytes at 100,000 seeds. Only the binary catalog is packaged in the app; the manifest, validation report, and certificates stay build artifacts. The packaged catalog must remain within the app’s 15 MB release-download target.

## Deal Selection

Selection is local, non-repeating, and sequential:

- Maintain traversal state for the active draw-one catalog.
- Traverse all `N` catalog indexes exactly once per cycle, in order: `index(n) = n`.
- Persist catalog version and next position.
- Wrap back to index 0 after `N` selections instead of repeating the last one.
- Replay uses the active game’s original seed and versions without advancing traversal.

If traversal state is missing or corrupt, start a new traversal at index 0. A catalog upgrade starts a new traversal without invalidating an active game.

## Build Artifacts

Catalog generation produces:

- Draw-one binary catalog
- Manifest with versions, counts, hashes, and generation timestamp
- Validation report with solver totals and failures
- Solution certificate for every emitted seed
- Rejected-seed and duplicate counts

Catalog binaries and the manifest are committed. Large solution certificates may be retained as compressed CI artifacts rather than shipped or committed.

## Verification

The catalog gate passes only when:

- Each catalog contains exactly the record count declared by its release milestone and manifest.
- Header and manifest versions agree.
- Payload hashes match.
- Every emitted seed has a successful independently replayed solution certificate — one replay covers both automatic-move settings, since both start from the same raw deal.
- Reference shuffle vectors pass on JVM and Android.
- Representative certificates replay during normal CI.
- Runtime catalog loading performs no solving, networking, or background work.
- The app's runtime classpath excludes `:tools:catalog`, so the offline generation code path cannot ship (the shared `:games:klondike:solver` search engine does ship, to power the on-device hint search — see `DESIGN.md` "On-Device Hint Search").
