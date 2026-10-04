# Spider Strategy Notes

Reference material only, compiled from public strategy guides — **not** a spec. Nothing here overrides or informs `docs/games/spider/DESIGN.md`, `docs/games/spider/RULES.md`, or any other file `CLAUDE.md` lists as authoritative; it exists purely as background reading (for anyone drafting in-app tips, Help copy, or — much later — a solver's move ordering). See "Sources" at the bottom for where each idea came from.

Two things to keep in mind while reading it.

**Spider's guides are noticeably weaker than Klondike's.** Klondike has at least two sources that argue from structure and hold up under scrutiny. Spider's public writing is dominated by SEO listicles that restate each other, and several of them contradict each other on numbers while sounding equally confident. Where that happens it is recorded below rather than resolved.

**Suit count changes which advice applies.** A tip about "build in suit" is vacuous at one suit, where every build is in-suit, and is the whole game at four. Tips are marked **[1-suit: n/a]** where the one-suit game makes them meaningless, since this app ships all three counts (`RULES.md` "Suit counts") and defaults to two.

## Core principles (broad agreement across sources)

- **Uncover face-down cards ahead of almost anything else.** The most repeated tip, and the one every source leads with. A move that flips a card buys new options; a move that only rearranges face-up cards usually does not.
- **An empty column is the most valuable thing on the board.** Near-universal, and stated more strongly than any single tip in the Klondike guides. An empty column lets you break a mixed pile apart, park a run while you dig, and rebuild sequences in suit. Several sources say to aim for one within the first 10–15 moves.
- **Prefer an in-suit build over an off-suit one, almost always.** [1-suit: n/a] Only a same-suit descending run moves as a unit, so an off-suit build is a pile that has to be taken apart again later, one card at a time, and taking it apart needs somewhere to put the cards. Guides describe off-suit columns as "locked".
- **Empty all beneficial moves before dealing a row.** The stock deals onto *every* column at once and there are only five deals, so a row landed on a half-organised board buries work that was nearly done. `BVS` puts it most concretely: get as many cards exposed and arranged in suit order as possible first, and says skipping this markedly reduces the win rate.
- **Look for the move that unlocks the most follow-on moves,** not the first legal one. Called the "waterfall" by one source; the same idea as Klondike's "think a move ahead", but it bites harder here because a row deal can end a chain permanently.
- **Undo is a legitimate tool.** Several guides treat undo as part of play — try a line, see where it stalls, back out. Worth noting because this app offers unlimited undo (`RULES.md`), so nothing here has to be hedged for a limited-undo implementation.

## More specific tactics

- **Work the shortest columns to open one up.** The column with the fewest face-down cards is the cheapest to clear, so it is where an empty column usually comes from.
- **Do not fill an empty column casually, and be wary of Kings.** A King put into an empty column can only ever leave it for another empty column, so it converts the board's most flexible resource into its least. Guides split on the remedy: some say fill with a King or a King-headed run *only*, others say avoid Kings there unless the King is itself blocking something. Both agree the decision deserves a pause.
- **If you must build off-suit, build high.** [1-suit: n/a] An off-suit pile headed by a high card has more room above it and stays useful longer; one headed low is finished as soon as an Ace lands on it and becomes dead weight.
- **Consider keeping a column as a deliberate dumping ground.** [1-suit: n/a] One source suggests conceding a column or two to mixed builds on purpose, as a staging area, rather than letting mixed builds spread across the whole board.
- **Spread work across ranks rather than perfecting one suit.** When the same rank appears in several columns, playing one of each keeps more columns live, instead of driving one column deep and stalling everywhere else.
- **Rearrange into natural builds right after banking a sequence.** Banking frees thirteen cards' worth of room and often an empty column with it; that is the moment to untangle what the mid-game left mixed.

## Where guides disagree, hedge, or are simply unreliable

- **The win-rate numbers do not survive comparison.** Sources variously claim overall winnability of 98.8–99.9%; four-suit theoretical winnability of ~35%; four-suit *actual* play at 20.6% against two-suit at 17.6% — which would make four suits easier than two, and is almost certainly an artefact of who plays which mode rather than a fact about the deals. One source states plainly that no published study measures solvability per suit count and warns against trusting exact figures. **Treat every number in this section as unusable** for anything this project asserts. It is recorded only so nobody re-derives the same mess. If Spider ever needs a real solvability figure it will come from `tools/catalog` measuring it, the way Klondike's did — not from a blog.
- **What to put in an empty column** is the sharpest genuine disagreement (see above). No source gives a rule that settles it; all of them fall back on "it depends on the board".
- **How early to force an empty column.** "Within 10–15 moves" appears, but no source explains where the number comes from, and none addresses what to do on a deal that will not produce one that fast.
- **Whether the one-suit game is worth strategy at all.** Guides mostly treat one suit as a tutorial. Only one distinguishes the three counts carefully, noting one-suit is about efficient ordering rather than suit discipline — which matches the intuition that at one suit the in-suit advice collapses entirely.

## What is missing from all of them

Worth naming, because their absence shapes how much of this is usable:

- Nothing addresses the *row-deal refusal* on an empty column — the rule that you cannot deal while any column is empty (`RULES.md` "The stock"). That rule is in direct tension with "always keep an empty column", and no guide found discusses the trade-off, even though it is the central mid-game decision in this variant.
- Nothing quantifies when to give up on a deal, the equivalent of Klondike's foundation-restraint debate.
- No source distinguishes advice for two suits specifically; it is treated as "four-suit advice, but easier".

## Academic sources (added later, and a correction)

The section above says no published study measures Spider solvability and that any real figure
would have to come from `tools/catalog`. **That was wrong, or has since stopped being true**, and
it is corrected here rather than edited away above so the mistake stays visible:

- [The Winnability of Klondike Solitaire and Many Other Patience Games](https://www.jair.org/index.php/jair/article/view/17167)
  — Blake & Gent, *JAIR*, peer-reviewed ([arXiv](https://arxiv.org/html/1906.12314v6)). Their
  *Solvitaire* solver measures 73 variants of 35 games. **Thoughtful Spider — perfect information,
  which is what a solver has — comes out at 98.487% ± 1.513%**, and they cite a stronger result
  (Robinson, 2020) at 99.9886% ± 0.0114%. Their variant list defines "Spider 1 Suit" and
  "Spider 2 Suit" relative to the base game, so that figure is the four-suit deal. This is a real
  number from a real study and it is nothing like the blog figures above.
- [Solving Patience and Solitaire Games with Good Old Fashioned AI](https://drops.dagstuhl.de/entities/document/10.4230/LIPIcs.CP.2024.1)
  — Gent, invited talk, CP 2024. The technique overview: transposition tables, symmetry in search,
  dominances (forcing a move when it is safe to do so), and streamliners (deliberately incomplete
  restrictions tried first, accepting false negatives, with full search on failure). Warns that
  dominances are easy to get wrong and have been used unproven, including the most widely used one.
- [Solvitaire source](https://github.com/thecharlieblake/Solvitaire) — the solver is plain
  depth-first backtracking search, not A\* or MCTS. Its reported metrics (states searched, unique
  states, backtracks, dominance moves, cache statistics) say where the leverage is.
- [Spider Solitaire is NP-Complete](https://arxiv.org/pdf/1110.1052) — Stern, arXiv. Covers a
  *generalized* Spider; the fixed 10-column two-deck game is finite and so decidable, and the result
  constrains asymptotics rather than this game.
- [Nested Rollout Policy Adaptation for Monte Carlo Tree Search](https://chrisrosin.com/rosin-ijcai11.pdf)
  — Rosin, IJCAI 2011. Not applied to Spider in any source found, but it is the strongest known
  family for long-horizon single-player puzzles and it learns a rollout policy online — which is
  what `SpiderSolver`'s hand-weighted playout is a fixed version of.

What this project has drawn from them so far, and what it has not, is
`docs/games/spider/DEALS.md` "The solver".

## What the hint search took from play (added 2026-10)

A second search of public guides and solver projects added nothing the sections above lack: the
guides restate "like with like", uncover first, protect empty columns, organise before dealing
([SolitaireX](https://solitairex.io/blog/spider-solitaire-strategy-mixed-runs-clean-wins)), and the
open-source solvers found either fail to finish in reasonable time
([willsam100/spiderSolitaireSolver](https://github.com/willsam100/spiderSolitaireSolver)) or take
minutes per game (plspider, whose "committed move" — one that cannot simply be moved back — is
already this solver's `isCommittedMove`). Two habits of a player with unlimited undo, neither in any
guide, are what the rebuilt hint search (`DESIGN.md` "Hint") actually encodes:

- **Peek at the row, then decide where to deal from.** Deal, look at the ten cards, undo, and
  rearrange so they land well. The five deals split the game into six phases, and choosing the
  position to deal from is the decision that matters in each. A solver already knows the stock, so
  it can make that choice without the undo.
- **Fill the hole, then deal.** The rule that the stock refuses to deal onto an empty column
  (noted above as missing from every guide) means an empty column must eventually be spent. A
  search that values empty columns highly never wants to; a player simply fills it and deals.

## Sources

- [Spider Solitaire Strategy — suitedgames.com](https://suitedgames.com/spider/strategy) — the most careful of the sources found: separates the three suit counts, and is the only one to say outright that per-suit-count solvability is unmeasured rather than quoting a number.
- [Spider Solitaire - Winning Strategy — BVS Solitaire](https://www.bvssolitaire.com/rules/spider-solitaire-strategy.htm) — the most concrete on ordering, and the source of the "organise fully before dealing" rule.
- [Spider Solitaire Strategies & Tips — Solitaire Bliss](https://www.solitairebliss.com/blog/spider-advanced-tips) — source of the "waterfall" framing and the build-high-when-off-suit rule.
- [Winning Spider Solitaire Strategies and Expert Tips — Solitaired](https://solitaired.com/guides/how-to-win-spider-solitaire) — the only one that engages directly with the row deal adding a card to every column.
- [4 Suit Spider Solitaire Strategy — 247 Spider Solitaire](https://www.247spidersolitaire.com/news/4-suit-spider-solitaire-strategy-tips-for-winning/)
- [The Top 11 Spider Solitaire Strategy to Win More Games — MPL](https://www.mplgames.com/blog/spider-solitaire-strategy/)
- [Spider Solitaire Strategy: Expert Tips to Win More Games — trysolitaire.com](https://trysolitaire.com/spider-solitaire-strategy)
- [Spider Solitaire Strategies & Tips — freesolitaire.com](https://www.freesolitaire.com/posts/posts-guides/spider-solitaire-tips-strategies)
- [10 Tips for Winning Spider Solitaire Every Time — Classic Solitaire](https://www.classicsolitaire.com/articles/win-spider-solitaire-every-time.html)

Consulted for win rates and **not** relied on, for the reasons in the section above: [Spider Winnability — playspidersolitaireonline.com](https://playspidersolitaireonline.com/spider-winnability), [Spider Solitaire Win Rates — trysolitaire.com](https://trysolitaire.com/spider-win-rates), [Is Spider Solitaire Always Solvable? — MPL](https://www.mplgames.com/blog/is-spider-solitaire-always-solvable/).

As with Klondike, pagat.com — usually the best authority for card game *rules* — does not appear to host a Spider strategy page, and none was invented in its place.
