# Spider — Deferred

Publisher: [FinitePlay LLC](https://finiteplay.org)

Out of scope, recorded so each is a decision rather than an omission. Nothing here starts before `EXECUTION_PLAN.md` S1–S4 are delivered — that file's Status table, not this one, says which are.

- **An in-repo four-suit solver.** FOUR's catalog is certified by an external solver and ships with stored solutions, so a four-suit hint follows the shipped line (`FOUR_SUIT_CATALOG.md`, `DEALS.md` "Solutions"). What is missing is a solver here that can decide a four-suit board: a hint after the player leaves that line is Inconclusive, and the catalog cannot be extended or re-certified without the external tool. The blocker is not the win rate being low but the solver being unable to decide a four-suit board **at all** — every run tried returned zero wins *and* zero exhaustion proofs, 100% budget-limited (`DEALS.md` "The solver"), while published work puts thoughtful four-suit Spider near 98% winnable with a plain DFS. So the deals are winnable and the pruning is what is missing. Suit-symmetry canonicalisation has since landed and moved FOUR not at all, as a constant-factor reduction against a gap that is not constant. **The candidate now is dominances**: provably safe forcing rules that replace a whole branch with one move, pruning multiplicatively, and the one technique from that work not yet tried here. Banking a completed K–A run is the obvious first candidate (a completed run offers no landing spot — its top is a King and its base an Ace — so it is pure dead weight while it sits there), but it needs an actual correctness proof before it forces anything: that literature's own warning is that plausible dominances are frequently wrong, including ones in long-standing use.
- **Difficulty beyond the suit count.** Grading Spider deals the way Klondike grades its own needs more than a solved/unsolved verdict.
- **A genuine multi-tier strategy ladder, rather than one playout policy.** Klondike's own deal certification is not search at all — `Ruleset.kt`'s cumulative, named tiers solve most deals with no search whatsoever (`docs/games/klondike/DEALS.md` "Difficulty Grading"). Spider's greedy playout is the same idea in miniature, one policy strengthened with more public-strategy terms rather than several named, cumulative ones, and that difference is measurable: strengthening the single policy moved the two-suit fresh-deal solve rate from zero to a fifth of a small sample (`DESIGN.md` "Hint"), but left four-suit untouched. A real ladder is a larger undertaking than a scoring-function tweak and is deferred until there is evidence it is worth building.
- **Relaxed row deal.** Some implementations allow dealing onto an empty column. The stricter rule is the traditional one and makes emptying a column a real decision; revisit only with evidence from play.
- **Undo limits and scoring.** Spider's traditional score punishes exploration; the platform's statistics do the job without it.
- **Two-deck card art.** The identical twin of a card is indistinguishable, which is correct — but a player tracking a specific card may want the pair marked. Only worth doing if play shows it matters.

## Localization — Native-Speaker Review

All 30 non-English translations shipped (`EXECUTION_PLAN.md` S3a/S3c): every string
resource-backed, every locale complete and structurally validated by
`LocaleStringsCompletenessTest`. Deliberately not part of that pass, and still deferred:

- Native-speaker review of each of the 30 machine-generated locales, before any of them is
  presented as release-ready — the same deferral Klondike's own translations carry
  (`docs/games/klondike/TODO.md` "Localization — Native-Speaker Review"). The help strings want
  the most attention: they lean on the card vocabulary each language actually uses
  (`docs/solitaire/GLOSSARY.md`) rather than a literal rendering.
