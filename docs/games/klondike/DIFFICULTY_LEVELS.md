# Difficulty Levels by Required Strategy
Current Status: **implemented** — the ruleset ladder below grades every deal, and "Levels as shipped" says which rung populates which shipped level.

## Levels as shipped

The six **levels** a player sees are cut from the ruleset ladder in this document. The four ruleset-backed levels share a name with the tier that fills them, one to one; the top two have no ruleset at all and are cut from the searches. What is measured and what is shipped remain separate questions — the cut is applied by the catalog build reading the grading file, not by the grader — but a name now means one thing in both.

That cost a merge to arrange. The ladder briefly had five rungs against four ruleset-backed levels, so "a Hard deal" meant the setup move to the grader and foundation withdrawal to a player, and every section below had to say which it meant. Full foundation restraint and the setup move are now one tier, **Medium**, and withdrawal is **Hard**.

**How a deal is graded.** One pass over ten million seeds (`tools/catalog`'s `grade-seeds`) plays each tier's rules against the raw deal with no search assistance and records the lowest tier whose undeviating order wins, that line's length in moves and in choices, and the largest number of mistakes the deal survives under that tier. Deals no ruleset wins go to a second pass (`grade-search`), which runs both engines — the catalog's depth-first search and the A\* solver — under fixed budgets and records what each said. Everything either pass measures is written to `tools/catalog/data/grading/`, which is the **reusable input** to every rebuild: quotas, priorities, and the mapping below can all change without regrading a seed.

> **Every measurement in this file predates two changes to the grader, and none of it has been
> retaken.** First, full foundation restraint reached Medium only, through a `== Ruleset.MEDIUM`
> that should have been `>=`, so the tiers above it graded without the restraint they inherit.
> Second, the ladder merged from five tiers to four. Counts, shares, and shipped seed lists alike
> are therefore one grader behind, and the tables below name tiers under the old five-rung
> scheme. `TODO.md` "Regrade after the inheritance fix" tracks the rerun, which must happen
> before D1b generates any catalog.
>
> Reading the old numbers against the new ladder: what those tables call **Medium** and **Hard**
> are both the current Medium, and what they call **Expert** is the current Hard.

**A level must defeat the one below it for a *player*, not merely for one priority order.**
The grade above comes from each tier's *undeviating* line: a single fixed order, no choices.
A player is not undeviating - offered two obvious moves they take one, and back up if it fails
- so if *any* order of the lower tier's moves wins, the deal plays as that lower tier whatever
the label says. The catalog build therefore gates every ruleset level on the tier below's own
**search** (`searchStrategyLine`, which explores every equally ranked choice) and drops the
deal if it wins.

This was learned the expensive way. Shipping without the gate, and ranking Easy by robustness
so the level opens on the most forgiving deals in a 1.46-million-deal population, produced an
Easy level a player rated *Trivial* on nine of nine rated deals - against one of eleven for the
robustness-graded catalog it replaced. Measured afterwards, every one of those deals was
winnable by obvious moves alone once the player could choose between them. The gate drops
5-24% of candidates per level, and the levels still fill.

**The mapping.**

| Level | Population | Ranked by |
|---|---|---|
| Trivial | Trivial's order wins it | mistakes survived, then line length |
| Easy | Easy's order wins it, Trivial's does not | mistakes survived, then line length |
| Medium | Medium's order wins it: full foundation restraint and the setup move | mistakes survived, then line length |
| Hard | Hard's order wins it: foundation withdrawal | critical choices, then critical points, then line length |
| Expert | No ruleset wins it and **exactly one** of the two searches finds a win | line length, longest first |
| Insane | No ruleset wins it and **neither** search resolves it | seed order |

**What the ten-million-seed pass measured** (31 minutes on twelve threads, 5,429 seeds/s):

| Lowest ruleset that wins | Seeds | Share | Level |
|---|---|---|---|
| Trivial | 2,912,321 | 29.1% | Trivial |
| Easy | 1,463,331 | 14.6% | Easy |
| Medium | 272,461 | 2.7% | Medium |
| Hard | 912,223 | 9.1% | Medium |
| Expert | 121,680 | 1.2% | Hard |
| — none — | 4,317,984 | 43.2% | Expert, Insane, or nothing |

Every level's quota is drawn from a population between twelve and three hundred times its
size, so the catalog is a *selection* rather than everything that qualified — which is the
point of ranking it.

**Selection order is not presentation order.** Ranking picks *which* ten thousand deals a level
ships; it must not decide which one the player meets first. Shipped in rank order, Easy opened
on the ten most forgiving deals of the 1.46 million that qualified — deals #5 and #8 survive two
mistakes where deal #10,000 survives none — and both were reported in play as feeling like
Trivial even though neither is winnable by obvious moves in any order. The build therefore
applies a fixed permutation to each level after selection (`presentationOrder`), so any run of
consecutive deals is a fair sample of its level rather than a walk from the soft end to the hard
one. The permutation is seeded per level from a constant and shuffled with `java.util.Random`,
whose algorithm is specified exactly: deal #5 is the same deal on every device and every
rebuild. Changing that constant renumbers every level and is a catalog version bump.

**All six levels ship their full 10,000**, 60,000 seeds in total, with 50,000 replay-verified
lines behind them; only Insane ships without. The grading files both stages wrote are kept
gzipped in `tools/catalog/data/grading/` and are the input to `build-levels`, so recutting the
levels is a two-minute job rather than a two-hour one.

Reproducing the whole pass, in order — the first two are the two hours, the last is the two minutes:

```powershell
.\gradlew.bat :tools:catalog:installDist
$tool = "tools\catalog\build\install\catalog-tool\bin\catalog-tool.bat"
& $tool grade-seeds 10000000 1 12 100000   # stage A: every seed by lowest winning ruleset (31 min)
& $tool grade-search 10000000 1 12 15000 15000  # stage B: search what no ruleset wins (75 min)
& $tool enrich-hard tools/catalog/data/grading/ruleset_grades.csv 30000 12  # stage C: critical choices (14 min)
& $tool build-levels                        # cut the six levels and write solutions.bin
```

Both scan stages are resumable and write progress files beside their output; run the tool from
a copy of the install directory if the repo will be rebuilt while a scan is in flight, or the
running JVM loses its jar mid-pass.

Mistake budgets across all 5,682,016 deals some ruleset wins: 5,464,822 survive none, 202,049
survive one, 12,590 two, 2,429 three, 119 four, 7 five, none six. Tolerance thins by roughly
an order of magnitude per mistake, so above Easy a level's ranking is effectively "the few
that survive one" followed by the rest.

Full foundation restraint does not carry a tier on its own. It was one rule and a measured step of 2.63%, a quarter the size of the step below it — too thin to ask a player to notice — so it is folded in with the setup move to make one **Medium**, and the levels above it each moved down a rung. That is why the top two levels are cut from the searches rather than from any ruleset, and why the ladder is four tiers against six levels.

The merge is also what put the tier names back in step with the level names. They ran one apart for a while, and the cost was not conceptual: every table, test, and command had to say which of the two senses of "Hard" it meant, and one of them silently didn't.

**Expert is a disagreement between two engines**, not a property of the board in the way the lower levels are. Both searches play the same full game under the same rules; one finding a win the other cannot, inside comparable budgets, is what "the edge of searchability" means operationally. It is a coarse measure and this document does not claim otherwise — but it selects boards that are demonstrably beyond every ruleset and still winnable, which no ruleset criterion can express.

The disagreement is real and close to symmetric rather than one engine simply being weaker: of 15,005 split deals, A\* alone won 7,772 and the depth-first search alone won 7,233. Both engines' certificates therefore ship, and the level's lines run 103 to 357 moves, median 161.

**What the search pass measured** (75 minutes on twelve threads, 12.9 searches/s): 134,445 seeds scanned to find 57,822 that no ruleset wins, of which 17,896 (31%) both engines won, 15,005 (26%) exactly one won, 21,229 (37%) neither resolved, and 3,692 (6.4%) were proven unwinnable and dropped. The pass stops as soon as both levels have their pools, which is why it scans a hundred thousand seeds rather than ten million — searching the whole no-ruleset population would take months of CPU for deals no level needs.

**Critical choices turn out to be zero** on essentially every Hard candidate, which is the same finding "Why the top tiers don't feel as far apart as they rank" reports. Measured over the level's top 30,000 candidates (14 minutes on twelve threads): **29,999 pass none at all, and exactly one passes 18** — the same seed being the only one in thirty thousand whose win is *proven* to need a foundation withdrawal, against 14,468 proven not to need one and 15,531 where the ablation ran out of budget. That one deal opens the level. Everything behind it ties at zero and orders by line length.

The column is measured and recorded regardless of coming back flat: it is what the level is specified to rank by, and a flat measurement is a result rather than a reason to stop taking it. It also quantifies the complaint that Hard and Expert feel alike — on 29,999 of 30,000 boards, nothing at all punishes a wrong move.

### Insane ships uncertified

This is a deliberate exception to `docs/solitaire/CATALOG.md`'s certification contract, and the only one.

Every other level ships a replay-verified winning line. Insane is *defined* as the deals neither search resolved inside its budget, so by construction there is no line to verify and none is shipped. A deal there is **not proven winnable** — only unproven, which is a different thing: the space was never exhausted, so nothing says it cannot be won. Deals a search *proved* unwinnable are excluded from the catalog entirely and never reach any level.

What a player gets on an Insane deal is therefore a board no ruleset and no bounded search has beaten, with hints falling back to the on-device search exactly as they did before solutions shipped. What they may also get is a deal that cannot be won at all.

**How often is measurable, at least as a floor.** Of the 36,593 deals the search pass *did* resolve — no ruleset wins them, and one or both engines returned a verdict — 3,692 were proven unwinnable, 10.1%. The unresolved pool the level ships from is drawn from the same population and is harder by construction, so its share of unwinnable deals is very unlikely to be lower than that and may be considerably higher. A player working through Insane should expect to meet deals that no play could have won. That is the cost of the level existing, and it is accepted knowingly.

## Glossary

Everything through **trap** holds independently of who plays. **Branch considered** and **user-perceived difficulty** describe one played session. Draw-one throughout; see the note under **state**.

**State** — the ordered contents of tableau and foundations. Column order is irrelevant. Stock and waste are excluded: their order is fixed at deal time, and because drawing is free every waste card is always reachable.

> Draw-one only. Under draw-three not every card is reachable from every rotation, so the stock and waste's position becomes part of the state.

**Choice** — a legal move that changes the state. Draw and recycle are not choices. An automatic foundation move is. Undo is not a move and not a choice.

**State fingerprint** — a hash of tableau and foundations, invariant under column order and excluding the stock and waste and their position. `SearchState.canonicalHashOf` implements this definition.

**Loop** — a choice reaching a state fingerprint already seen on the current path.

**Path** — a sequence of choices, containing no loops. Undo removes choices from the path.

**Winning path** — a path ending with every card on the foundations.

**Losing path** — a path on which every remaining choice would loop.

**Critical choice** — a choice after which no winning path exists.

**Losing branch** — a path, or subpath, following a critical choice.

**Strategy** — a named rule for choosing among legal moves, as listed per tier below. Trivial's three rules are the floor: a deal winnable by those alone requires no strategy.

**Strictly required** — of a move type or strategy: every winning path uses it. Tested by ablation — withhold it and check whether any win survives. Not the same as "the ruleset that won happened to use it".

**Detection lag** — the number of moves from a critical choice to the first board where the loss is apparent, i.e. the nearest losing path. A long lag means play runs far into a losing branch before anything reveals it, so more moves are wasted and more backtracking follows to escape it.

**Trap** — relative to a strategy level *and* to how the move is entered: a critical choice whose *losing* option is the one that would actually be taken. Only traps are felt. Where the winning option is what would be taken anyway, the player never learns a choice was there.

> Tap resolution is deterministic (`UI_SPEC.md` "Tap"), so a tapping player does not pick among equal destinations — the rule does, and any critical choice it happens to resolve correctly is invisible. It is not uniformly leftmost: a tableau sequence takes the nearest legal column **to the right**, then a foundation, then the nearest legal column to the left; a waste card takes a safe foundation, else the **leftmost** legal column; a foundation withdrawal takes the leftmost. Drag accepts any legal destination, so a dragging player genuinely chooses — the same board can trap one input method and not the other.

**Branch considered** — a path the player gave up on: by moving into a loop, by undoing back to an earlier state, or by pressing Replay. Consecutive undos count as one discard however far they rewind — only a **choice** ends a discard, so draws interleaved among the undos do not split it into several.

**User-perceived difficulty** — how hard a deal felt to the player who played it. Rises with:

- the number of **moves** made,
- the number of **branches considered**,
- the number and tier of **strategies strictly required** — needing more, and higher, strategies to reach the win is harder,
- whether a **foundation withdrawal** is strictly required, which carries an extra penalty beyond its tier: a card already on a foundation looks finished, so taking it back is counter-intuitive in a way no other move is,
- how early its **traps** fall and how long their **detection lag** runs — an early trap with a long lag wastes the most play and forces the most backtracking, because nothing reveals the mistake until far past the point of making it.

Only traps count here, not critical choices as such: a critical choice navigated correctly — by the player's strategy level, or by tap resolution on their behalf — is invisible and costs nothing. At Trivial to Medium the rules take the first available pile or foundation move on sight, so where that happens to be the winning option the deal reads as *easy* and finishes in fewer moves, not more.

Relative weights are unmeasured. A property of one (player, deal) pair, so the same deal can score differently for two players or for one player twice. Distinct from a deal's **tier**, which is a property of the classifier.

> Moves, not choices — including draws and recycles. The two diverge sharply (a measured deal: 128 moves, 37 choices), so which one perception actually tracks is an open empirical question, not a settled part of the definition.

Choice, branch and state are different kinds and never substitute for one another: a choice is one edge, a branch is a path, a state is what a path arrives at.

A critical choice need not be a hard one. On seed 1, `waste → T3` was critical while nine of the ten legal moves there still won, and 25 moves later the board still offered legal moves with the loss unrevealed — a long detection lag on a choice nothing marked out. Criticality is a fact about the tree, difficulty a fact about the player; conflating them is the tier ladder's central error.

## How a deal is actually graded

Each tier's rules are played against the raw deal with **no search assistance of any
kind**, and the lowest tier that reaches a win within 500 moves names the grade — the
definition given under "Tier of a deal" below, applied literally. A tier winning is
itself the proof the deal is solvable, so nothing needs certifying separately, and
grading a deal costs microseconds.

`tools/catalog/data/solver_benchmark.csv` also measures a broader **strategy-set
reachability diagnostic**. Unlike the shipped grader, that diagnostic explores every
equally ranked choice and retains every lower tier's preferred choices at higher tiers.
It uses canonical-state transposition and a 100,000-state cap; a cap is inconclusive.
Changing this diagnostic does not regrade the shipped catalogs.

Measured win rates over the 100 originally-certified seeds, which is what establishes
that the tiers rank at all:

| Tier | Wins | Median moves when won |
|---|---|---|
| Trivial | 19/100 | 118 |
| Easy | 22/100 | 126 |
| Medium | 23/100 | 151 |
| Hard | 44/100 | 269 |
| Expert | 57/100 | 274 |

Rising win rates are the property the cumulative-strategy premise requires; re-run
`./gradlew.bat :tools:catalog:run --args="purewin"` after any change to the rules and
check the ladder still holds.

### Corrections this document needed once it was measurable

The numbers originally written here were guesses, and two of them were wrong:

- **Indistinguishable-fork budgets (≤1 Hard, ≤3 Expert) are abandoned.** Measured against real
  deals, the rules diverge from a proven winning line 34–111 times per deal — two orders
  of magnitude beyond those caps, which would have made every deal Insane. Tiers are
  separated by *whether the rules win*, not by a deviation allowance.
- **Foundation-withdrawal counts are not used as a criterion.** They were intended to
  separate Expert from Hard, but a search with withdrawal enabled times out on fresh
  deals (the same cost D1s avoids by skipping that move type), so the counts cannot be
  obtained cheaply. Expert still *plays* withdrawals — that is a real part of its
  ruleset and why it wins more deals than Hard — they are simply not counted.
- **Two named rules turned out to be no-ops** and are documented as such rather than
  faked: suit-colour alternation is already guaranteed by the rules themselves
  (`canBuild` never permits a same-colour placement), and Medium's "informed King
  selection" is subsumed by the reveal-depth preference, since a King leaving a buried
  column *is* a reveal move.

Two grading mechanisms were also built, measured, and rejected before the one above;
both are recorded in `TierClassifier`'s own documentation so they are not retried: an
oracle-guided walk (never lost, never converged) and certificate replay (fast, but
ranked the tiers *inverted*, because it measured greediness rather than difficulty).

## Why the top tiers don't feel as far apart as they rank

Reported in play: *Expert feels like Hard, and Insane doesn't feel insane.* `./gradlew.bat :tools:catalog:run --args="diagnose"` measures the **board** rather than the ruleset, over the first ten shipped seeds of each tier (`DifficultyDiagnostics.kt`). It says the complaint is right, and why.

| Tier | Median line | Withdrawals in line | Deals needing a withdrawal | Indistinguishable forks | Critical choices |
|---|---|---|---|---|---|
| Hard | 277 | 0 on every seed | 0/9 resolved | 0 | 0 of 4,308 |
| Expert | 389 | 0 on every seed | 0/6 resolved | 0 | 0 of 3,498 |
| Insane | 141 | 0 on 8 of 10 | 0/8 resolved | 0 | 11 of 3,058 |

- **Expert never actually withdraws.** Foundation-to-tableau withdrawal is the headline move type Expert adds over Hard, and not one Expert-graded seed's own winning line plays a single one — every seed whose check resolved is winnable without withdrawing at all. Whatever separates Expert from Hard on these deals, it is not the move type the tier is defined by. That is precisely why the two feel alike: the grade changes, the play does not.

  The "deals needing a withdrawal" column is the ablation test for **strictly required**: withhold the move type and re-solve. It reads **0 of 23** resolved deals across all three tiers, so the withdrawal penalty in **user-perceived difficulty** contributes nothing anywhere it has been measured. The shipped catalog contains no deal that is hard in that particular way.
- **Nothing punishes a wrong move.** Of every legal move at every sampled position along these winning lines, almost none loses: zero critical choices across all of Hard and Expert, eleven across Insane. Draw-one with unlimited recycling (`RULES.md`) is forgiving enough that a player already on a winning line has to work to leave it. Difficulty is not coming from risk, because there is almost no risk to price.

  Read this as *density*, not absence. It counts critical choices at positions **on a winning line**, sampled 40–60 per deal, so it says a well-played board rarely offers one — not that none exists. The seed-1 deal below found one in real play.
- **There are no indistinguishable forks to count.** Zero at every tier — not because the tiers' rules always know what to do, but because the branches they cannot tell apart all win anyway. The "Corrections" section below abandoned budgets for these for being unmeasurably large; the truth is the opposite and worse for the grade: on shipped deals the count is unmeasurably *small*.

  This says nothing about **critical choices** in the Glossary's sense, which these runs never looked for. A played deal on seed 1 contains one, at a position where nine of ten moves still won — precisely the kind of thing a fork-based count cannot see.

So the ladder above Medium ranks deals by **which ruleset happened to win them**, and on these seeds that is close to independent of anything a player experiences. A tier is a statement about the classifier, not about the board.

Two caveats the numbers carry, neither of which rescues the ladder:

- A branch is probed with the withdrawal-free move set, for the cost reason `TierClassifier.winningLine` documents, so "losing" means *losing without withdrawing* — a conservative reading that can only ever **over**count critical choices, and it still found essentially none. Probes that ran out of budget are reported separately and excluded from the ratio rather than guessed at.
- Median line length is not comparable across these tiers: Insane's line comes from the search (which finds short lines) while Hard's and Expert's come from a ruleset playing out (which wanders). It is evidence about the shipped solutions, not about how long a player's own game runs.

`DEALS.md`'s finding that move count and elapsed time correlate with felt difficulty (r ≈ 0.74–0.75) still stands and is still the strongest signal available. Diagnosing the ladder was this pass's scope; replacing it was not, and nothing here proposes a replacement grade yet.

## Definitions

**Strategy set.** Each tier below defines a set of mechanical rules for choosing the
next move. Tiers are cumulative: every strategy available at a lower tier remains
available at every tier above it. A rule only ever picks among moves that are already
legal under `docs/games/klondike/RULES.md`; it never invents a move the rules don't already allow.

**Tier of a deal.** A deal's tier is the lowest tier whose strategy set can win it from
the raw dealt board — meaning: repeatedly applying that tier's rules, in priority
order, either forces the win outright, or only ever stalls at an **indistinguishable
fork** (below), which some outside decision (a human's judgment, a lucky guess, or an
exhaustive search) resolves correctly. A deal is not Trivial just because Trivial's
rules *can* be applied to it — they have to actually win it without ever needing a
choice the rules themselves can't make.

**Indistinguishable fork.** A moment where two or more legal tableau moves are available,
the active strategy set has no basis to prefer one over the other (they score identically
by every rule it knows), and yet only one of them actually leads to a win — every
other option is a dead end, even though nothing about the board makes that visible yet.
This is the difference between a deal that's merely long and one that actually demands
foresight (or luck, or backtracking) a mechanical rule set cannot supply on its own.

> This was called a "critical choice" until the Glossary above gave that name a different, ruleset-independent meaning. The two are genuinely distinct and both are needed: a **critical choice** is a move that kills every winning path, a fact about the game tree; an **indistinguishable fork** is a position where *a particular tier's rules* cannot tell the winning option from the losing ones, a fact about that tier. A critical choice is invisible to the definition of a tier; an indistinguishable fork is exactly what the tier definition turns on. Anywhere below that discusses what a ruleset can or cannot decide means the fork.

## Trivial

**A Trivial deal must *be* easy, not merely feel easy.** The player wins using only obvious moves *and while making mistakes* — missing a foundation play, taking a different reveal, bringing a pile card down instead. No sequence of obvious moves may lose the game.

Everything below is defined in terms of two things: the set of **obvious moves**, and the **reference order** in which an undeviating player takes them.

### Obvious moves

- any face-up tableau top card, or the waste top, to a foundation when legal — safety ignored,
- any tableau-to-tableau move that flips a face-down card,
- a tableau-to-tableau move that empties a column outright, when that column is a single face-up sequence,
- the waste top to a legal tableau destination,
- draw, and recycle once the stock is empty.

Emptying a column is the only non-revealing tableau move in the set, because freeing a column for a King is as natural to a beginner as turning a card over. General rearrangement stays excluded: no tier below Hard offers it, and admitting all of it is what makes the graph explode.

### Reference priority order

What an undeviating player does, and what the classifier plays. Four rules plus a fallback, applied in order until one applies:

1. If a face-up card can legally move to a foundation, move it there — regardless of whether the move is "safe". This is a stronger, more naive rule than the app's own automatic-moves feature (`RULES.md` "Automatic Foundation Moves"), which only auto-plays *safe* foundation moves.
2. Otherwise, if any tableau-to-tableau move would flip a face-down card, make it — whichever one is legal. Trivial does not compare candidates when more than one reveal move exists; that comparison is Easy's first addition.
3. Otherwise, if a column is a single face-up sequence that can legally move elsewhere, move it and empty the column.
4. Otherwise, if the current waste top card has a legal tableau destination, play it.
5. Otherwise, draw (or recycle if the stock is empty).

Read "highest-priority move **that does not loop**": a rule whose move returns to a state already on the current path falls through to the next rule.

This order decides nothing about the grade — it is one path through the obvious-move graph, and the criterion quantifies over all of them.

### The criterion

Explore every state reachable from the deal using obvious moves alone, tolerating up to *N* mistakes, where a mistake is one departure from the reference order. The deal is Trivial when every state so reachable can still reach a win *within that same move set*; one reachable state that cannot disqualifies it. The shipped tier uses **N = 3**.

Two points the wording is deliberately precise about:

- **Reaching a win, not avoiding a loss.** A path that cycles for ever has not won, so a loop terminates that branch without crediting it.
- **Cycling the stock and waste is never a mistake.** Draw and recycle do not change the state, so a player who overlooks a choice and cycles instead returns to the same state with the same options — the cards come round again. Only *which* obvious move is taken can lose a game, never failing to take one. The budget therefore counts choices and treats draws as free.

### What ships

`:tools:catalog`'s `calibrate-trivial` grades candidates; `build-trivial-tier` writes the seed lists and solutions together, enforcing both catalog invariants by construction.

Measured over **13,000,000 seeds** (63 min on four threads), by the largest number of mistakes each survives:

| Survives | Seeds | Share | In the tier |
|---|---|---|---|
| — obvious play does not win at all | 9,213,174 | 70.87% | no |
| 0 mistakes | 3,645,785 | 28.04% | no |
| 1 mistake | 133,235 | 1.025% | no |
| 2 mistakes | 6,376 | 0.049% | no |
| **3 mistakes** | **951** | 0.0073% | **yes** |
| **4 mistakes** | **405** | 0.0031% | **yes** |
| **5 mistakes** | **59** | 0.00045% | **yes** |
| **6 mistakes** | **15** | 0.00012% | **yes** |

**1,430 seeds shipped under the robustness cut**, ordered most robust first, so the level opened at its most forgiving and tightened as a player worked through it. The level is now cut by minimum ruleset instead and ranked by the same robustness measure ("Levels as shipped"), so the ordering survives and the floor does not. Grown from the original 114-seed, 1,000,000-seed scan to match the ~1,000-deal target the other robustness-graded tiers reach (`TODO.md`); every seed from that first scan survives in the larger one. Playtesting on device confirms the intent: the deals are forgiving to error, which is the property the level exists to provide.

Shipped solutions need no solver. A Trivial deal is by definition one the reference line wins — that is what surviving zero mistakes means — so the shipped line is that play recorded. It includes the draws, since a solution is replayed literally rather than treated as a sequence of choices, and every line is verified by replay through the real reducer before it is admitted.

Trivial through Hard are graded this way. The shipped Expert and Insane levels are not graded by a ruleset at all — no ruleset wins their deals, so they are the residue split by what the two searches say ("Levels as shipped"), which is a coarser measure and the open question `TODO.md` records.

### How grading works

A memoised depth-first walk of the obvious-move graph on an allocation-free board (`FastBoard`), keyed by **state fingerprint**, so distinct subpaths that meet at the same state collapse and loops terminate on their own. No solver is involved: the question is reachability inside the obvious-move graph, not solvability in general.

One entry is kept per *state*, holding the highest budget it is proven robust at. Robustness is monotone, so a state proven at *b* answers every query at *b* or below for free; keying on (state, budget) instead grew memory linearly with the tolerance.

Rejection is cheap, which is what makes million-seed scans practical: a reachable state whose obvious moves are exhausted without a win disqualifies the deal at once, and 90.6% of candidates die in under 100 nodes.

### Invariants and corrections

Two catalog invariants were violated silently, and a player found each by losing a deal that was supposed to be safe. Both are now enforced by construction — a candidate failing either is dropped before export.

- **Every seed has a replay-verified winning line.** A Trivial deal is by definition one the reference line wins, so a seed with no line is proof its *grade* is wrong, not that an asset is missing. This was once logged as a warning and shipped anyway: 164 of 180 seeds failed and the build continued.
- **No seed appears in two tiers.** `INTERIM_SEED_GRADES` resolves duplicates by "last tier wins", so a seed in both Trivial and Insane displays as Insane. Fifteen collided, because the tier was selected on robustness alone without reference to the other lists.

The grading rules themselves needed three corrections, each forced by measurement:

- **A loop is not a win.** Loop edges were briefly treated as success, on the reasoning that going in circles cannot lose. True, and beside the point: a player cycling for ever never wins. It passed seeds whose reference line merely looped — which is exactly what the 164 missing solutions were telling us, and what a played deal then demonstrated after 24 faultless moves.
- **The reference falls through when its move loops.** Disqualifying whenever the top-priority choice loops rejected 19,995 of 20,000 seeds, because the reference player oscillates as soon as column-emptying outranks the stock and waste.
- **Earlier figures were inflated by both defects.** A "1 in 278" rate for six-mistake deals is really 3 per million, and a reported set of 35 unlosable deals was an artefact of the same bug. Recorded rather than edited away: every number here now comes from a scan under the corrected rules.
## Easy

Easy is graded on **the same robustness criterion as Trivial**, under Easy's own priority order, and only on deals **Trivial's order cannot win**. So a player reaching Easy meets deals that genuinely need the tier's rules — not deals Trivial would have played out unaided.

The move set does not change. Every tier offers exactly the same obvious moves, which is what makes the ladder meaningful: the win was always reachable, and the tier is a statement about which order finds it.

### What Easy adds to the priority order

Low-cost tie-breakers that only ever compare moves the board already makes visible — still no lookahead beyond the current board:

1. **Prefer the deepest reveal.** When more than one reveal is available, take the one whose column still has the most face-down cards buried in it. Trivial takes whichever comes first.
2. **Withhold a card the board still needs.** Hold a foundation-eligible card back when some other face-up card has its column as its only legal landing spot right now — a one-step check against the board as it stands, over the movable bottom of each other column plus the card face up on the waste. Cards still in the stock are not considered: that would be planning for what might be drawn, and Easy never looks ahead.
3. **Don't rush the foundation.** Hold a card back if its rank is more than 4 above the lowest foundation — a crude numeric stand-in for "probably still wanted as tableau cover" that costs nothing to check.

> The margin of four is **measured**, not chosen. Sweeping it from 1 to 8 over 20,000 deals, Easy's undeviating line wins most at exactly 4 (7,882 deals) and Hard's too (10,231); tighter costs wins quickly, looser drifts back toward no restraint at all. What the sweep also shows is how *small* the effect is — 4 beats no restraint by 49 deals in 20,000, six tenths of one percent — so "don't rush the foundation" is a real but marginal principle, not the pillar `PUBLIC_STRATEGY_RESEARCH.md` presents.
>
> Against **search** rather than undeviating play the same rule is harmful at every threshold, losing wins and costing up to 2.5x the nodes. Both results are right about different players: a search can back out of a premature bank, a line cannot, so restraint buys insurance against a mistake only the line player can't undo. The tiers are lines, so the rule stays.
>
> Medium is deaf to the knob — its line wins 7,632 deals at every threshold from off down to 3 — because full restraint masks the margin entirely. Every measurement in this block was taken while full restraint reached Medium **only**: `foundationRank` and `pileRank` tested `ruleset == Ruleset.MEDIUM`, an equality that was correct while Medium was the top tier and stopped being correct the moment Hard was added above it. Hard and Expert therefore played with Easy's restraint alone. That is fixed (`>= Ruleset.MEDIUM`, pinned by `RulesetInheritanceTest`), so the Hard figure quoted above is from the tier as it was mis-implemented, and the regrade that settles the real one has not run yet (`TODO.md`).

Withholding **demotes** a foundation play to last resort rather than removing it. The card goes up the moment no other obvious move is offered, because stalling the game to enforce a heuristic is worse than the heuristic being wrong.

> A fourth rule was specified and is vacuous: preferring a destination that keeps alternating colours intact, when a card has more than one legal tableau destination. Under the tap model the player never picks among destinations — tap resolution does (`UI_SPEC.md` "Tap"), deterministically. A rule that chooses between destinations describes a player who drags, and no tier below Hard has one.

Neither restraint rule can fire on a *safe* foundation play. Safety already requires every foundation to be at least at the rank beneath, which puts the lowest within one — well inside rule 3's margin — and leaves no card on the board that could still need the card as cover. So Easy's restraint only ever affects **unsafe** plays, which is the whole point of it.

### What ships

Measured over **500,000 seeds** (13 s on twelve threads), grading only what Trivial's order fails to win:

| Outcome | Seeds | Share | In the tier |
|---|---|---|---|
| Trivial's order already wins it — not graded | 145,780 | 29.2% | no |
| Easy's order does not win it either | 280,736 | 56.1% | no |
| 0 mistakes | 72,131 | 14.4% | no |
| **1 mistake** | **1,324** | 0.26% | **yes** |
| **2 mistakes** | **27** | 0.005% | **yes** |
| **3 mistakes** | **1** | — | **yes** |
| **4 mistakes** | **1** | — | **yes** |

**1,341 seeds shipped under the robustness cut**, at a floor of one mistake, most robust first; twelve more qualified but were already claimed by a higher tier. The level is now cut by minimum ruleset and merely *ranked* by robustness ("Levels as shipped").

That floor is a weaker promise than Trivial's three, and deliberately so — but it is the honest ceiling as well. Easy's order is *better*, so departing from it costs more: the tier loses 98% of its candidates between zero mistakes and one, where Trivial loses 96% between zero and three. A tier that both needs Easy's rules and forgives three mistakes is roughly a one-in-a-million deal.

The 14.4% that Easy wins at zero mistakes is the real measure of the step between the tiers: those deals are unwinnable by Trivial's order and routine under Easy's.

### Disjointness

A third catalog invariant, enforced by construction alongside the two Trivial introduced: **no shipped Easy seed is won by Trivial's undeviating line**. Without it, Easy would fill up with deals Trivial already plays out — the tiers would overlap on exactly the deals that make the step meaningless, and every one of them would be a deal a beginner had already been given.

It is checked first when grading, because it is the cheapest question asked of a seed — a single budget-zero search — and it rejects the largest share.
## Medium

> **Medium has since absorbed the setup move**, which the "Hard" section below measured on its
> own. The two are one tier now, and this section records only the restraint half of it. Medium
> the ruleset and Medium the level are the same thing again ("Levels as shipped").

Easy's rules, plus **full foundation restraint** — and, once the tap model is applied, that turns out to be the whole of it.

The restraint is inherited by every tier above, which is what "Easy's rules, plus" means one rung up as well: Hard and Expert withhold everything Medium withholds and add their own move types on top. `RulesetInheritanceTest` pins that containment across the ladder — it was broken for most of this file's measured history, and the fix postdates the numbers below.

### What Medium adds to the priority order

**Full foundation restraint.** Hold rank 3 and above back from the foundation until sending it is provably safe, unconditionally, rather than only when a one-step check finds an immediate reason not to. This is the "don't rush the foundation" principle from `PUBLIC_STRATEGY_RESEARCH.md`, applied as policy rather than reactively. Medium keeps Easy's two restraint rules as well — it is "Easy's rules, plus" — so it withholds a strict superset of what Easy withholds.

*Provably safe* here is the classic rule: **both opposite-colour foundations already at the rank beneath**. That is deliberately weaker than the app's own `isSafeFoundationMove` (`RULES.md`), which requires *every* suit to be there. The app's rule governs what automation does unasked, where the cost of being wrong is a move the player did not choose; this one describes a player deciding for themselves, and holding a card back for a same-colour foundation buys nothing — nothing of that colour can ever need it as cover.

The tier's other two specified rules are **already true of every tier below it**, and are recorded here rather than implemented twice:

> **King selection.** "When more than one King can move into an empty column, prefer the one whose current column covers the more valuable buried card." Under the tap model this is Easy's deepest-reveal preference: a King leaving a column that has face-down cards beneath it *is* a reveal, and Easy already prefers the reveal with the most buried. What remains — comparing the buried cards' ranks — asks the player to value a card they cannot see.
>
> **Stock and waste tracking.** "Track what has been seen this pass, and what is coming after a recycle, well enough to sequence upcoming waste plays." Every tier already has this, and unconditionally: drawing costs no choices, so the move generator offers *every* placeable stock or waste card at every state, not merely the waste top. A tier cannot be given knowledge of the stock and waste as a distinguishing rule when the model hands it to all of them.

So Medium is one rule. The measurement says one rule is enough.

### What ships

Measured over **1,500,000 seeds** (34 s on twelve threads), grading only what neither Trivial's nor Easy's order wins:

| Outcome | Seeds | Share | In the tier |
|---|---|---|---|
| A lower order already wins it — not graded | 657,406 | 43.8% | no |
| Medium's order does not win it either | 801,948 | 53.5% | no |
| 0 mistakes | 39,463 | 2.63% | no |
| **1 mistake** | **1,093** | 0.073% | **yes** |
| **2 mistakes** | **76** | 0.005% | **yes** |
| **3 mistakes** | **13** | — | **yes** |
| **4 mistakes** | **1** | — | **yes** |

Four-mistake deals are about **1 per million**, too rare for a 1,500,000-seed scan to populate, so the band was filled by a second pass over **21,000,000 seeds** keeping only those. It found 20, which is what the tier now opens on.

**1,201 seeds shipped under the robustness cut**, at a floor of one mistake, most robust first — 20 surviving four, 13 three, 76 two. This ruleset no longer has a level of its own ("Levels as shipped"); its deals ship under Medium alongside the setup move's.

The 2.63% Medium wins at zero mistakes is the step between the tiers. It is a quarter the size of Easy's 14.4% step over Trivial, which is what a single added rule should look like next to three.

### Every lower tier is checked, not just the one below

The disjointness gate takes a **list**: Medium must defeat Trivial's order *and* Easy's. These are different rulesets rather than one rule tightened by degrees, so nothing guarantees that a deal Easy cannot win is one Trivial cannot win either — Easy's restraint can stall a deal Trivial's recklessness happens to push through. Checking only the tier immediately below would have let those through.

Every rule at this tier still either finds a uniquely-best move or admits it has no preference among moves that are, in fact, equally good: nothing here creates an indistinguishable fork.
## Hard

> **This section measures the setup move as its own tier, which it no longer is.** It merged
> into **Medium** when the ladder went to four rungs, so everything below describes half of
> today's Medium; the other half is the restraint in the section above. "Hard" now means
> foundation withdrawal — the section after this one. Nothing here has been re-measured against
> the merged tier.

**Assumption:** from here on, assume the player knows every card in the stock and waste — the full pile, all 24 non-tableau cards — including their exact order, but nothing about which face-down tableau card is which. Nothing about draw-one mechanically hides the pile (recycling never reshuffles it, per `RULES.md`), so this is a realistic ceiling on attention rather than a magic power.

> Under the grading model this assumption is already granted, to every tier. Drawing costs no choices, so the move generator offers *every* placeable stock or waste card at every state, not merely the waste top. It is stated here because it is what makes the tier's tactics reasonable for a human, not because it distinguishes Hard from Medium.

Hard is the **first tier that widens the move set** rather than reordering it. Its defining tactic is the setup move — a tableau move with nothing visibly gained — which is by definition not an obvious move, so no priority order over the existing set can express it.

That breaks the identity every lower tier relies on, and it is worth being explicit that the disjointness gate survives anyway: Hard offers a strict superset of Medium's moves and ranks the additions last, so "no lower order wins this deal" still means exactly what it says.

### The setup move

Split a column's face-up sequence at any index **above** its deepest sequence start, and move the upper part to its tap-resolved destination. Every such index is a legal sequence start and a tap the UI already accepts, so this stays inside the tap model rather than assuming a player who drags.

Splitting a sequence changes exactly one thing about what is available: it uncovers the card immediately beneath the split, which was face-up but buried. The destination gains nothing, since the sequence's top card was already exposed. So the move is **productive** — and Hard offers it — precisely when that newly uncovered card does something:

- it can be banked on a foundation, or
- it gives a home to a card that currently has none, over the movable bottom of every other column and every card in the pile.

The filter is not an optimisation. Admitting setup moves unrestricted collapsed an earlier Hard's win rate to 2/100, below Trivial's, because the ruleset relocated cards for ever instead of progressing. Two structural facts keep the worst of that out of this model, and both are pinned by test rather than trusted: a face-up King is always at the deepest sequence start, since nothing outranks it, so **a split sequence can never carry a King** — and therefore a setup move can never land on an empty column, which accepts nothing else.

Setup moves rank **last**, behind every move that gains something visibly and ahead only of a withheld foundation card. A tier that reaches for a setup move before taking a free card is not playing a plan, it is fidgeting.

> Hard's second specified tactic is **not implemented**, and this is a genuine gap rather than a no-op like the ones Medium retired: *sequentially advancing two suits' foundations in a planned order, purely to clear a blocking card*. It requires holding a plan across several moves, and there is no rank over the moves available at one state that expresses it. The tier as graded is the setup move alone. The measurement below says that alone is a large step; whether the missing tactic would find deals this grading calls unwinnable is untested.

### What ships

Measured over **250,000 seeds** (44 s on twelve threads), grading only what no lower order wins:

| Outcome | Seeds | Share | In the tier |
|---|---|---|---|
| A lower order already wins it — not graded | 116,725 | 46.7% | no |
| Hard's order does not win it either | 110,492 | 44.2% | no |
| 0 mistakes | 21,182 | 8.47% | no |
| **1 mistake** | **1,437** | 0.57% | **yes** |
| **2 mistakes** | **136** | 0.054% | **yes** |
| **3 mistakes** | **20** | 0.008% | **yes** |
| **4 mistakes** | **8** | 0.003% | **yes** |

**1,556 seeds shipped under the robustness cut**, at a floor of one mistake, most robust first. This ruleset now populates the shipped **Medium** level ("Levels as shipped").

The 8.47% Hard wins at zero mistakes is the largest step since Easy's, and more than three times Medium's 2.63%. That is what a *new capability* looks like next to a reordering: Medium learned to hold a card back, Hard learned a move it could not previously make.

## Expert

> **This tier is now called Hard**, and its deals ship as the Hard level ("Levels as shipped").
> It was Expert while the ladder had five rungs. The *level* called Expert is a different thing
> entirely and has no ruleset: it is cut from the deals no order wins and exactly one of DFS and
> A\* does. Read every "Expert" below as today's Hard ruleset.

Hard's rules, plus the willingness to **withdraw a foundation card back to the tableau**
(`RULES.md`: "Allow the top foundation card to return to the tableau") — a move type no tier
below has at all. Two further tactics are specified and **not implemented**: braiding both
same-colour suits through one run, and abandoning foundation restraint once few enough cards
remain unseen. Both need a plan carried across moves, which no priority order over a single
state can express.

### What Expert is graded on

Not robustness — tolerance thins by an order of magnitude per tier and there is none left
here. Two properties of the board instead:

1. **No lower ruleset's order wins it.** As everywhere on the ladder, measured rather than assumed.
2. **The line passes between 2 and 5 critical choices** — moves after which no winning path
   remains. Measured across a million seeds, critical points run from 0 to 18, so the band is a
   genuine window: 634 seeds per million qualify.

### The withdrawal promise, and how it failed twice

Expert was first specified as *"every winning path includes at least one foundation
withdrawal"*. It cannot be delivered, and the record of trying is worth keeping.

The literal reading quantifies over the whole game tree, so establishing it means proving a
negative: that no winning line avoids withdrawal. Over 50,000 seeds the ablation decided 342
candidates and **every one of them was winnable without withdrawing**; the rest never
terminated. The property is close to nonexistent in Klondike, and unprovable where it isn't.

A relaxed reading — *no lower ruleset wins it, and Expert's line withdraws* — is satisfied by
construction, since Expert's move set is Hard's plus withdrawal and every shared move is ranked
identically, so a withdrawal-free Expert line would also be a Hard line. It filters nothing.

**Shipping on that relaxed reading was a mistake, and a player found it on the fourth deal.**
Of the 37 seeds first shipped, 28 were winnable without ever touching a foundation, most
inside ten milliseconds of search. The gate now runs the ablation and drops any deal whose
withdrawal-free win is findable within a bounded search. Three outcomes, treated differently:
a win found is a rejection, an exhausted space is a proof, and a budget exhausted is neither —
accepted, but recorded as *no withdrawal-free win was found*, never as *none exists*.

The earlier failure came from ablating over Hard's **restricted** move set while the player has
the full one, which `Solver.kt` warns against in as many words: "exhausting a restricted space
proves nothing about the full game."

### What ships

**9 seeds shipped under the critical-choice cut**, ordered fewest critical choices first — the
reverse of every tier below, where the graded value is a mistake budget and more is kinder.
Small because the withdrawal gate rejects three quarters of what clears the other two criteria,
and because it was drawn from a 60,000-seed survey rather than the full million. This ruleset
now populates the shipped **Hard** level, cut by minimum ruleset and ranked by critical choices
("Levels as shipped"), so the count above is the old cut and not what Hard ships.

## Insane

Everything else: deals no named ruleset above can win inside 500 moves — in practice
the boards that genuinely need an exhaustive or near-exhaustive search rather than any
heuristic, the same class the on-device hint search's full-move-set final attempt
exists to resolve (`DESIGN.md` "On-Device Hint Search").

These deals are split across the top two shipped levels by what the two searches then say about
them ("Levels as shipped"): where exactly one search finds a win, the deal ships as **Expert**
carrying that search's certificate; where neither resolves it, the deal ships as **Insane** with
no line at all. Only the second of those is uncertified, and "Insane ships uncertified" above
states what that costs a player. An earlier version of this section had every no-ruleset deal
shipping as Insane with a search certificate behind it, which the split made wrong in both
halves.

## Summary

| Tier | Level | New strategies added | How it is graded |
|---|---|---|---|
| Trivial | Trivial | 4 fixed rules, no comparisons | robustness: wins despite 3 mistakes |
| Easy | Easy | Reveal-column preference, one-step foundation withholding, rank-gap-4 restraint | robustness: wins despite 1 mistake, and Trivial's order cannot win it |
| Medium | Medium | Full foundation restraint, **and** productive setup-only moves — the tier that *widens* the move set | robustness: wins despite 1 mistake, and no lower order can win it |
| Hard | Hard | Foundation-to-tableau withdrawal (braiding and mode-switching unimplemented) | 2-5 critical choices, no lower order wins it, no findable withdrawal-free win |
| — | Expert | (none — no ruleset wins these deals) | exactly one of DFS and A\* finds a win |
| — | Insane | (none) | neither search resolves it; ships uncertified |

Four tiers, six levels, and the names line up where both exist. The top two levels have no tier because their definition is that no tier reaches them.

Three grading models are in use. Trivial through Medium are graded on **robustness** — the deal must be won by the tier's rules *and survive mistakes*, and every tier above Trivial must additionally defeat every ruleset below it. Hard is graded on **board properties** instead, robustness having run out by then. What no ruleset wins is left to the two searches, which split it: one search winning ships the deal as Expert with that certificate, neither resolving it ships it as Insane with none.

## Why this replaced node-count grading

The previously shipped grading (`docs/games/klondike/DEALS.md`) scored a deal by how many search nodes
the hint engine's portfolio needed to prove a winning line — a measure of search effort,
not of which strategies a human needs. Ratings collected in play found essentially no
correlation between that grade and how hard a deal actually felt (r ≈ 0.05), while
elapsed time and move count correlated strongly (r ≈ 0.74–0.75). Tier grading targets
that gap directly: it asks which strategies a deal *demands*, and its own move counts
rise with the tier (118 median at Trivial, 274 at Expert), tracking the signal that
actually correlated with felt difficulty.
