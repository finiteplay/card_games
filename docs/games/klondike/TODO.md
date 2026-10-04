# Deferred Work

This list is outside the initial MVP acceptance gate.

## After Initial Catalog Testing

Against the *certified* catalog (D1b), which ships today (`DEALS.md` has its size).

- Expand the draw-one catalog beyond what ships today.
- Target up to 100,000 draw-one seeds.
- Rerun full catalog generation, certificate replay, integrity, size, and traversal gates for every expansion.

## Draw-Three Mode — Deal Certification

Draw-three itself shipped: stock/waste behavior, on-device hint search support,
Settings selection for the next New Game, mode preserved through Replay, history and
statistics tracked separately per mode, and unit/UI test coverage across the engine,
solver, and app layers (`docs/games/klondike/RULES.md` "Draw-Three Mode"). Deliberately not part of
that pass, and still deferred:

- Generate an independently solver-certified draw-three deal catalog, starting with
  10 seeds — its own D1s/D1b-equivalent pipeline, separate from draw-one's (draw-one
  and draw-three solvability are not equivalent; a catalog cannot be shared). Until
  this lands, draw-three deals a plain random, uncertified shuffle.
- Once certified, switch draw-three's seed source from the random fallback
  (`RandomDealSeedSource`) to the real catalog, mirroring `InterimSolvableDealSeedSource`/D1b for draw-one.
- Physical-device smoke tests specific to draw-three.

## Localization — Native-Speaker Review

Localization infrastructure and all 30 non-English translations shipped
(`docs/games/klondike/DESIGN.md` "Localization"): every string resource-backed, every locale
complete and structurally validated by `LocaleStringsCompletenessTest`. Deliberately
not part of that pass, and still deferred:

- Native-speaker review of each of the 30 machine-generated locales — both
  `values-XX/strings.xml` and the help pages in `values-XX/strings_help.xml` — including
  Ukrainian, before that locale is presented as release-ready. The help pages want the most
  attention: they are several hundred words each, and the strategy page in particular leans on
  the card vocabulary each language actually uses (`docs/solitaire/GLOSSARY.md`) rather than a
  literal rendering.
- Correcting any translation found wrong, awkward, or tonally off during that review.

## Difficulty Levels — Measured Findings Not Yet Acted On

A search harness in `:tools:catalog` (`census`, `prune-eval`, `spread-sweep`, `line-sweep`,
`classify-deals`) measures the game tree directly rather than through a ruleset. What it has
established, and what follows from it:

- **A quarter of deals are unclassified.** Over 1,000 seeds with only *sound* prunings: 713 wins found, **36 proven unwinnable**, 251 neither. Proving unwinnability is the wall this whole area keeps hitting, and 36-in-1,000 is the number any dead-state detector has to beat.
- **The productive-setup filter converts 59% of that residue.** Re-run on the 251 unclassified deals it found 148 wins. That is its real value; the "1.56x less search" measured on already-winnable deals badly understated it. **Every rule should be scored by unknowns converted, not by wins preserved** — the earlier evaluations, including the threshold sweep, used the wrong population.
- **Pruning beats deduplication, by two orders of magnitude.** Safe-foundation forcing cut states 18x and bought four extra plies of depth; partial-order reduction on commutative moves cut edges 1.9%, because it can only fire where a state has a unique predecessor and the transposition table already catches the rest. Look for more *forcing* rules with exchange arguments, not more transposition tricks.
- **Paths are hopeless, states are tractable.** Eleven choices into one Expert deal there are 1.5e10 distinct choice sequences and 72,506 states. A winning line holds ~95 choices against ~200 moves, so roughly half of what a player does is pile cycling that decides nothing.

Deferred, and roughly in dependency order:

- ~~**Fix the `== MEDIUM` inheritance bug.**~~ Fixed: `FastBoard.foundationRank` and `pileRank` now test `ruleset >= Ruleset.MEDIUM`, so the Hard and Expert **rulesets** inherit Medium's full foundation restraint as "Medium's rules, plus" always said they did. `RulesetInheritanceTest` pins containment across the whole ladder, on both the tableau and the pile tap, so the next tier added above cannot silently break it the way this one did. **The regrade it implies has not run** — see below.
- ~~**Regrade after the inheritance fix.**~~ Done: the post-fix ten-million-seed ruleset and search passes were rebuilt, Hard enrichment and level selection rerun, and all six 10,000-deal D1b catalogs packaged and verified. The regenerated level lists replace the pre-fix cuts; the binary bundle is ready for the remaining runtime-source switch.

`HardRulesetTest`'s fixtures are already re-derived and no longer wait on the full pass: a 40,000-seed `survey-expert` (61s at 12 threads) re-qualified the tier from scratch. Two of its five old seeds had left the tier, which is the shape of what the full regrade will do to the shipped lists — but at the scale of six ten-thousand-seed levels rather than five fixtures.
- **Sound dead-state detectors.** The one thing that would unstick Expert grading, catalog yield, and every timing-out proof. A permanently-buried card is the tractable first case: a face-up card whose two possible parents and whose foundation predecessor all lie below it in the same column can never leave, so the column can never be dug out.
- **Grade tiers by minimum-cost winning path.** Label each move with a cost (safe bank 0, reveal 0, pile 0, unsafe bank 1, setup 2, withdrawal 3) and search for the cheapest win; the tier is the highest cost on it. One search replaces five ruleset trials, yields the certificate and the grade together, and measures the glossary's "strictly required" directly. It would have caught the Expert withdrawal failure immediately.
- ~~**Grow the shipped tiers.**~~ Done, differently: every level now ships its full 10,000 from the ten-million-seed pass (`DIFFICULTY_LEVELS.md` "Levels as shipped"), rather than the few hundred to few thousand the per-ruleset robustness cuts yielded. The counts this item named are the old cut and are kept only in each ruleset's own "What ships".

## Difficulty Levels — Extend the Robustness Grading Beyond Hard

Trivial through Hard are now graded by how much player error a deal tolerates, and playtesting on device confirmed Trivial: the deals are forgiving to mistakes, which is what the tier is for. The shipped Expert and Insane levels have no such grade — no ruleset wins their deals, so they are the leftover split by whether one, or neither, of the two searches finds a win. That measures the search rather than the board, which is the same complaint `DIFFICULTY_LEVELS.md` makes about the old ruleset grade, and why Expert felt like Hard.

Easy settled one open question and reframed another. The ladder is **not** one ruleset at decreasing tolerance, as guessed here before it was measured: Easy uses its own priority order, must survive one mistake, and must additionally be a deal Trivial's order cannot win at all. Two axes, not one — which order finds the win, and how much error that order tolerates. The disjointness gate turned out to be the load-bearing half: 29% of seeds are won by Trivial's order outright, and without excluding them Easy would have filled with deals a beginner had already been given.

Deferred, and roughly in dependency order:

- **Give Hard its second tactic, or accept one.** Hard ships graded on the setup move alone. Its other specified tactic — sequentially advancing two suits' foundations in a planned order to clear a blocking card — needs a plan held across several moves, and no rank over one state's moves expresses it. Whether it would reach deals this grading calls unwinnable is untested; the setup move alone already gives an 8.5% step, so the tactic may not be worth a model that can carry plans.
- **Decide what the top levels mean.** Robustness thins fast — Easy loses 98% of its candidates between zero mistakes and one, Medium 97%, Hard 93% — so Expert and Insane will have little tolerance left to grade. Expert's own addition, foundation-to-tableau withdrawal, does widen the move set the way Hard's setup move did, so the same approach may still reach; Insane, defined as the residue, has no ruleset to grade with at all and stays search-certified.
- **Widen Trivial's move set, or reword its promise.** Both Trivial deals lost in testing left the graded region on ordinary tableau rearrangement within twenty-five moves. The grading was right; the model covers a narrower player than exists. Hard now shows the cost is affordable — its productive setup moves did not explode the graph — so admitting them at Trivial is worth measuring before falling back on saying Trivial means "play simply and you will win" rather than "you cannot lose this".

## Additional Themes — Test Coverage

The themes themselves shipped: a light palette alongside the dark one, a persisted setting
offering Light, Dark, System, and time-of-day Automatic (`docs/PLATFORM.md` "Themes"), the
light-theme contrast tokens defined and reasoned about there, and system bars that follow the
resolved theme. `ThemeModeTest` covers the clock boundary Automatic turns on. Deliberately not
part of that pass, and still deferred:

- Instrumented theme-switching coverage: change the setting and assert the board, the modal
  surfaces, and the wordmark all follow it.
- A system-bar test asserting the bar appearance follows the resolved theme rather than the
  system setting.

## iOS

- Extract reusable modules with Kotlin Multiplatform.
- Add Compose Multiplatform or native iOS UI after evaluating final Android behavior.
- Add iOS persistence, lifecycle, sound, accessibility, and performance validation.

