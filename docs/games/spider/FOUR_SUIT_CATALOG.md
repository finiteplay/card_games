# Spider — FOUR-suit catalog population (plspider pipeline)

This documents a second, separate path to a FOUR-suit deal catalog, alongside whatever the
in-repo `SpiderSolver`-based work in `EXECUTION_PLAN.md` "S6" and `DEALS.md` achieves on its own.
`SpiderSolver` cannot decide a FOUR-suit board at all at any budget this machine can reach
(`DEALS.md`'s own measurement); this path instead certifies FOUR-suit deals using an external,
independent solver (**plspider**, not the Solvitaire reference tool despite living in a directory
tree of that name and sharing a Docker image name with it) and never trusts that solver's own
"Won" claim — every certified seed is independently replayed through this repo's real rules engine
before it counts, the same trust model every other certified deal in this repo already uses.

**Status as of this writing**: the campaign reached its full 5,000 target (5,004 seeds certified,
some in-flight workers landing just past the threshold) after several machine-sleep interruptions
mid-run (each one stalled the campaign for hours until it was manually restarted — see
`run_campaign.py`'s own heartbeat log for the gaps). A real, shipped `four.catalog` exists at
`games/spider/app/src/main/assets/catalogs/four.catalog` with **5,004 certified seeds**, verified by
`verifySpiderDealCatalogs` alongside the existing ONE/TWO catalogs (20,447 seeds total across all
three). This file should eventually be merged into `DEALS.md` proper — it lives here for now because
`DEALS.md` had unrelated in-progress edits from another concurrent process at the time this was
written, and editing it would have risked clobbering that work.

## Why an external solver at all

Blake & Gent's Solvitaire (the published reference solver measured at ~98.5% winnable on this same
game — `PUBLIC_STRATEGY_RESEARCH.md` "Academic sources") was one candidate; **plspider** is a
different, independent, unpublished tool (`<plspider>`, a local checkout built from
`plspider.zip`) that turned out to solve real FOUR-suit deals in seconds where this repo's own
solver could not decide them at all even given an hour. Its own documentation warns it can take
"around 15 minutes... including losing games" in its default, unconstrained mode — the fast results
here come from giving it an *exact* deal (`-D`) and a specific seed (`-S`) rather than letting it
choose games itself.

## The pipeline

Four stages, each independently checkable:

1. **Export.** `games/spider/solver/src/test/kotlin/org/finiteplay/spider/solver/
   ExportSolvitaireDeals.kt` — a `main()` taking `outputDir firstSeed lastSeed [suitCount]` — calls
   this repo's own `dealGame(seed, versions, suitCount)` and writes each deal as Solvitaire-style
   JSON (`finiteplay-<suit>-seed-<n>.json`) to `<solvitaire>/deals`. This is
   the *only* place the actual deal comes from; everything downstream is a format conversion or a
   solve attempt on cards this function already produced.
2. **Convert.** `<plspider>/make_finiteplay_decks.py <firstSeed> <lastSeed>
   [suitLabel]` reads that JSON and remaps it into plspider's own column order and `.cards` text
   format (one card per line, 104 lines, uppercase = face up / lowercase = face down) — plspider's
   initial deal gives its four longest columns to positions 1, 4, 7, 10, which is not this repo's
   own column order, so this remapping is load-bearing, not cosmetic. Suit-count aware: the
   composition validation checks against the actual expected suit multiset for `one`/`two`/`four`,
   not a hardcoded four-suit assumption.
3. **Solve.** `run_campaign.py` (below) invokes plspider itself, one seed at a time, via
   `./plspider -A -E -S <seed> -F <output> -D "${cards[@]}"` — **not** `-C` (see "Flags that
   matter" below). Its own `-F` output is appended-to only on completion (win *or* loss reaching a
   `-A` auto-play conclusion); a killed/timed-out attempt leaves no output file at all, which is how
   the orchestrator tells a real loss apart from "never finished."
4. **Verify and certify.** `games/spider/solver/src/test/kotlin/org/finiteplay/spider/solver/
   ReplayPlspiderSolutions.kt` — `main()` taking `inputDir outputDir firstSeed lastSeed` — parses
   plspider's own move notation (`pile#count>pile`, `D` for a stock deal, `NC` completion markers)
   out of a `Won` line, translates plspider's column numbering to this repo's own
   (`plspiderToFinitePlay`), and replays every move through the real `isLegal`/`applyMove` reducer,
   with a hard `check()` on every step. Only a seed that replays cleanly to `state.isWon` gets a
   `verified/solution-<seed>.txt` — plspider's own claim is never trusted further than that. A seed
   whose `seed-N.txt` is missing or never reached a `Won` line is skipped, not an error: most seeds
   in a large scan are expected to end this way.

Then, separately, **packaging**: `tools/catalog/src/main/kotlin/org/finiteplay/spider/tools/
catalog/Main.kt`'s `import-external` verb (`writeCatalogFromExternalSolutions` in
`SpiderCatalogBuild.kt`) reads every `verified/solution-N.txt` in a directory, re-verifies each one
*again* independently, and writes/merges a real `four.catalog` + manifest entry + solutions-blob
entry — the same format `buildSpiderCatalogs` itself produces for ONE/TWO, so the app-facing side
never needs to know which path certified a given suit count.

```
./gradlew.bat :tools:catalog:...   # see "Running it" below for the actual invocation shape --
                                    # the application plugin's mainClass is fixed to Klondike's,
                                    # so this needs a staged classpath, not a bare gradle task
```

## Flags that matter

- **`-A -E`, not `-C`.** plspider's own `-h` output: `-C` means "Cycle — play random, mostly
  un-won deals (implies -A -E)". It is **not** "solve the seed given via -S" — using it risked
  plspider wandering into further random games and churning its own large history files
  (`plspider_cshd.txt`/`winspidr_cdhs.txt`, 175–230MB) even after solving the one deal asked for,
  which is the leading suspect for a real concurrency collapse (below). `-A -E` alone keeps
  auto-play and quick-exit-on-solution without the unwanted cycling.
- **`-t` on `docker exec`.** Running plspider without a pseudo-TTY allocated can make it exit early
  with a SIGTTIN-shaped exit code (149) within seconds, before it has done any real search — this
  looks exactly like "instantly loses" and is not that. Any one-off invocation outside
  `run_campaign.py` (which already gets this right via a persistent container, not a bare `docker
  exec` without `-t`... actually confirm this stays `-t`'d if copied elsewhere) needs `-t`.
- **`-U ssss`-style suit configuration is unresolved.** A side investigation (comparing plspider's
  win-path length against this repo's own solver for ONE/TWO-suit deals) found plspider's search
  makes no visible progress on a ONE-suit deal without `-U`, and even with `-U ssss` did not solve a
  deal known-winnable in 136 moves within a 5-minute budget. plspider's own heuristics appear tuned
  around exactly four distinct suits; this path is FOUR-suit only until/unless that is revisited.

## The seed-collision constraint — read before choosing a seed range

**A FOUR-suit campaign must not reuse seed numbers ONE-suit or TWO-suit's catalogs already claim.**
`verifySpiderDealCatalogs`'s own gate rejects a seed appearing in two partitions
(`docs/solitaire/CATALOG.md` "Partitions"), and `dealGame`'s own doc explains why a collision is
even possible: the shuffle is over deck *positions*, and suit count only decides which cards fill
them, so the same seed number names a real, valid, independently-certifiable deal at every suit
count. Nothing stops a naive scan from certifying the same seed number FinitePlay already ships as a
ONE-suit deal.

This was a real incident, not a hypothetical: a first campaign attempt scanned FOUR-suit seeds
starting at 1 — the same range ONE-suit's shipped catalog claims (1 through 10,045) — and 849 of its
854 certified seeds turned out to collide. The seeds were re-scanned from 100,000 onward, safely
above both ONE's (max 10,045) and TWO's (max 95,998) claimed ranges. **Any future FOUR-suit campaign
must start at or above whatever `DumpSeedsKt` (below) currently reports as the highest claimed seed
across every other shipped Spider catalog** — checking this before a long run is much cheaper than
discovering the collision after.

```
# Decode a committed catalog's own seed list without re-solving anything:
java -cp <staged classpath> org.finiteplay.spider.tools.catalog.DumpSeedsKt <path-to.catalog> <output.txt>
```

## Orchestration (`run_campaign.py`)

`<plspider>/run_campaign.py <target> <workers> <timeoutSeconds> <firstSeed>`
scans ascending seeds with `workers` persistent Docker containers (`docker exec`, not `docker run
--rm` per attempt — measured real overhead: ~5.5s container-creation cost per `run --rm` call vs
~0.15s for `exec` into an already-running one; at thousands of attempts that is hours, not seconds),
stopping once `target` seeds are certified. Prints a `# heartbeat` line every 30s: attempts, wins,
certified count, verification watermark, and a rate.

**Memory, not CPU, is the real concurrency limit.** Each plspider instance measured 3–5GB resident.
A 12-worker attempt collapsed to a 99.7% timeout rate — not because 12 processes exceed this
machine's 16 logical cores, but because they exceeded Docker Desktop's WSL2 VM memory ceiling
(uncapped by default, defaults to roughly half of host RAM) and started thrashing. Fixed by
explicitly capping the VM (`%UserProfile%\.wslconfig`, `memory=18GB`) and running **4** workers,
each comfortably fitting with headroom. Do not raise worker count without first confirming
`docker stats` shows real headroom under whatever `.wslconfig` currently allows.

**A real, since-fixed bug worth knowing about if this script is ever touched again**: the
verification pass's "safe to verify up through" watermark must never be the highest seed *claimed*
by a worker (some workers are still mid-attempt when a periodic check fires) — it must be one less
than the lowest *still in-flight* seed. Getting this wrong once orphaned 823 of 824 real wins in one
run (they were never lost, just never checked — a full re-verify over the historical range recovered
all but 2 of them, a separate, unrelated, still-open edge case below). `run_campaign.py`'s current
`in_flight`/`safe_verify_through()` design is the fix; if you see `certified` badly lagging `won` for
an extended stretch (not just a small few-seed lag), suspect this class of bug again before assuming
the campaign is merely slow.

## Known, open, low-priority issue

A `pile#13>pile` token (moving a full King-to-Ace run) has, rarely, translated to a negative
`fromIndex` in `ReplayPlspiderSolutions.kt` — meaning this replica's own board state had already
diverged from plspider's by that point, for a reason not yet root-caused. Measured impact: 2 of 824
wins in one batch (0.24%). Always caught by the per-seed `try`/`catch` there and reported as
`FAILED`, never silently certified — costs a little throughput, never correctness. Left unfixed
deliberately rather than risk an unvalidated change to that file while a real campaign depended on
its correctness; worth revisiting if the rate ever climbs, or once no campaign is actively running
against it.

## Resuming a paused campaign

Progress lives entirely in `<plspider>/reports/campaign-5000/` (`results.csv`
for a raw attempt log, `verified/solution-N.txt` per certified seed) and is never lost by stopping
the containers/orchestrator — `docker stop fp-worker-0..3` followed by killing the Python process is
a clean pause. To resume: find the highest seed number in `results.csv`, then re-run
`run_campaign.py <target> <workers> <timeoutSeconds> <thatSeed + 1>` — the script re-creates its own
containers on start and needs no other state.

To turn accumulated progress into a real, shippable catalog checkpoint at any point (not only once
the full target is reached — every seed in `verified/` is already independently certified regardless
of how many more are still pending):

```
java -cp <staged classpath> org.finiteplay.spider.tools.catalog.MainKt import-external FOUR \
    <plspider>/reports/campaign-5000/verified \
    games/spider/app/src/main/assets/catalogs \
    tools/catalog/data/spider_solutions.bin.gz
```

then `./gradlew.bat :tools:catalog:verifySpiderDealCatalogs` to confirm the result. Re-running this
later against the same (larger) `verified/` directory produces a larger `four.catalog` that replaces
the earlier one — safe to do as many times as useful; nothing about an earlier checkpoint needs to
change first.

("staged classpath": the `tools:catalog` module's own `application` plugin `mainClass` is fixed to
Klondike's entry point, so the module's own `:run`/registered `JavaExec` tasks cannot take arbitrary
CLI args — copy `tools/catalog/build/classes/kotlin/main/*` plus `games/spider/rules`,
`games/spider/solver`, `core/cards`, `core/session`, `solitaire/catalog`'s built jars and a
kotlin-stdlib jar into one directory and invoke `java -cp ".;lib/*" <MainKt>` directly from there.)
