# Spider — Design

Publisher: [FinitePlay LLC](https://finiteplay.org)

What the game is, and the decisions behind it. `RULES.md` is the rules; `docs/PLATFORM.md` is everything true of every FinitePlay game and is not restated here.

## The product

Spider, offline, Android only, no networking or analytics — the platform constraints, unchanged. One app, one game, sharing `core/*` with Klondike.

Difficulty is the **suit count**, not a graded catalog: one suit is a puzzle a beginner can finish, four suits is the game experts play. That is the traditional shape of Spider and players arrive expecting it. It also means Spider's difficulty is honest without a solver — the number of suits is a fact about the deal, where Klondike's "Easy" is a claim about how the deal plays, and claims need certifying.

## What Spider is not

Spider is not Klondike with more columns, and the differences are what the code has to respect:

- **No waste.** The stock deals to every column at once. There is no drawn card waiting to be placed, so there is no draw, no recycle, and no waste pile to render or reason about.
- **Building and moving are different rules.** A card may be placed on any card one rank higher, but only a single-suited descending run may be lifted. A player can therefore build something they cannot take apart, which is the game's central tension and has no Klondike equivalent.
- **The foundation is not a target you play to.** Sequences complete in the tableau and leave it whole. There is nothing to bank a single card to, so there is no automatic foundation move, no automatic finish, and no foundation withdrawal.
- **An empty column is the resource.** It accepts anything, so it is worth more than a King's landing spot, and the row deal is blocked while one exists — the one place the rules protect the player from themselves.

## Interaction

Follows Klondike's, because the platform's players are the same people: tap to play a card where it can go, drag to choose, undo, new deal, replay. The pieces that differ:

- **Deal a row** replaces Draw. It is the stock's only action, and it is refused with an explanation while a column is empty rather than silently doing nothing.
- **A sequence is dragged from where it starts**, and the interface shows how far a lift extends before the player commits, since "these three move together and that fourth does not" is not visible from the cards alone.
- **Hint has two modes**, picked by a Settings toggle: a plain highlight of every legal move, or a solver-backed proven step, the same guarantee Klondike's own hint makes. The search proves a *whole remaining game* rather than one step. The guided mode is offered at one suit unconditionally, and at two or four suits for a certified deal, which is every deal New Game deals: the certified catalog's own winning line is shipped on-device (`docs/games/spider/DEALS.md` "Solutions") and primes the hint engine's cache on every fresh deal. Once the player leaves that line a live search takes over: it wins most one- and two-suit boards a player reaches ("Hint" below), but no search here wins a four-suit board, so at four suits a hint off the line comes back Inconclusive (`UI_SPEC.md` "Hint").

## Hint

Two modes, a Settings toggle apart (`UI_SPEC.md` "Hint" has the full interaction). The plain
highlight mode has no search behind it at all. The solver-backed mode's `HintEngine` first checks
a cached winning line — either one a prior search in this game already proved, or one primed from
the certified catalog's own shipped solution for a fresh deal (`DEALS.md` "Solutions") — and only
searches from the live board, off the main thread, when nothing is cached. A
found or primed line is itself cached so later hints read the next step off it instead of
re-solving — the same shape as Klondike's and FreeCell's own `HintEngine`, narrowed to what a
solver that proves a whole line in one call actually needs
(`games/spider/solver/.../HintEngine.kt`).

**What a live search is, now.** `HintEngine` spends three quarters of the player's hint timeout on
`PhaseSearch` and hands what is left to `SpiderSolver`. Phase search goes first because it wins far
more of the boards a player actually reaches and its lines read like a player's; `SpiderSolver`
still wins a few boards phase search misses, usually within a second when it does, and is the only
one of the two that can prove a board lost.

**Phase search** (`:games:spider:solver`'s `PhaseSearch`) is built on how a player with unlimited
undo wins a two-suit deal: deal a row, look at the ten cards, undo, rearrange so the row lands well.
The five row deals split the game into six phases, and the decision that matters in each is *which
organised position to deal from*. A solver knows the stock already — the same view a player has
after peeking with undo — so it does the same thing without the undo:

- Each level holds a few boards just after a deal. Each is explored without dealing by a bounded
  best-first search over a position score (suits banked, cards face down, empty columns, in-suit
  versus off-suit versus broken pairs among the face-up cards), charged per move so a direct route
  beats a long walk through equal-looking rearrangements.
- The best deal-ready positions it passed through are dealt and scored by the board the row leaves
  behind, and the best few go on to the next level. The last phase, with nothing to deal, searches
  for the win itself.
- An empty column makes a plateau of rearrangements that all outscore filling it, which a
  best-first search can wander for millions of expansions without reaching a deal-ready board. A
  player just fills the hole and deals; so does phase search, greedily, at every position it
  expands that has one.
- A failed attempt retries wider and deeper until the timeout. The first attempt is deliberately
  narrow (one board per level), which costs nothing in boards won and answers easy boards — nearly
  every one-suit board — several times faster.

Like `SpiderSolver`'s beam stage it can win but never prove there is no win, and its line is
replayed through the real reducer before a single move of it is shown.

**Measured** with `HintBench` (`:games:spider:solver` tests), single-threaded on the development
desktop at 2.7 s — about what an 8 s timeout buys on a phone, taking a phone as roughly a third of
that machine's speed. "Off the line" boards walk a TWO catalog deal's shipped solution part way and
then make six plausible moves off it, which is what a player who stopped following the hint looks
like:

| boards | before (`SpiderSolver` alone) | now |
|---|---|---|
| TWO deals 1–20, opening | 16/20 | 20/20 |
| TWO deals 1–20, off the line at 10/30/50/70% | 118/158 | 156/158 |
| TWO deals 21–60 (held out from tuning), opening and off the line at 20/40% | 59/116 | 107/116 |
| ONE deals 1–20, opening and on the line at 30% | 39/40 | 40/40 |

The one off-the-line board of deals 1–20 neither search wins was not won at 120 s by either, and
may be lost. `SpiderSolver`'s failures were mostly not slow: its width-3 beam runs dry in tens of
milliseconds on a board off its own line, so a longer timeout never helped it — and until this
change that running dry was reported as a *proof*, so those boards showed "No solution" while
winnable. The beam's exhaustion now counts as a proof only when it never had to discard a move.

**The guided mode is offered at one suit unconditionally, and at two or four only on a certified
deal** — which in production is every deal, since New Game always draws from the catalog, and
there the live search is what answers once the player leaves the primed line. Whether an
*uncertified* two-suit deal should get the guided mode too remains an open product decision; it
is no longer a capability question. At four suits the primed line works, but neither search wins
a four-suit board within an interactive budget, so a hint after leaving it is Inconclusive.

**`SpiderSolver`, the fallback**, is a four-stage pipeline: greedy strategy playouts, then a
**beam-limited DFS**, then plain transposition-pruned DFS, then — only when that runs out of node
budget without exhausting the board — a weighted best-first search (`AStarSolver`). The hint turns
the last two off (`HINT_SOLVER_LIMITS`): both need far more memory and time than a player waiting
on a hint can give them, and they exist for offline catalog generation. The beam keeps only the
three most promising moves per board, ranked by the playout's public-strategy scoring, and
backtracks through those; three was measured on the first twenty two-suit seeds (widths 2/3/4/6
solved 5/8/5/4 from the opening at the time). Before phase search, strengthening the playout's
policy (the "waterfall", shortest column first, build high when off-suit) and adding the beam were
what moved two-suit openings from 0/20 to 8/20 at the old 4 s budget.

## Architecture

Three modules, as `docs/ARCHITECTURE.md` "Adding a game" prescribes for a solitaire:

- `:games:spider:rules` — the layout, the deal, the legal-move set, and the one reducer. Kotlin/JVM, depends only on `:core:cards`, no Android.
- `:games:spider:app` — the Android application.
- `:games:spider:solver` — certifies the ONE/TWO deal catalogs (`DEALS.md`; FOUR's is certified by an external solver) and powers the live search behind the interactive Hint action (`UI_SPEC.md` "Hint"); never a dependency of `:tools:catalog`.

Which of them exist today is `EXECUTION_PLAN.md` "Status", and only there.

`SpiderState` is its own type, not a shared board. Ten columns, two decks, banked counts rather than per-suit foundation ranks, and no waste: nothing about Klondike's `GameState` survives contact with it, which is what `docs/ARCHITECTURE.md` predicted when it kept the board game-owned. Now that two real boards exist, the honest finding is that they share *less* than a shared type would need, not more.

What the second game did settle:

- **`canBuild` moved out of `core/cards`** into `games/klondike/rules`, where `ARCHITECTURE.md` always said it belonged. Spider's packing rule agrees with Klondike's only on the rank step; a shared version would have needed a parameter naming which game was asking.
- **`shuffleDeckIndices` grew a deck-size parameter**, because a two-deck game is the first thing to need one. The algorithm is untouched and 52-card output is byte-identical, so no shuffle version bump.

## Scoring and statistics

Moves and elapsed time, as Klondike. No score: Microsoft's Spider score (500 points, minus one per move) rewards playing slowly and is widely ignored. Statistics are the platform's — games played, won, best time, best moves — kept per suit count, since a four-suit win is not comparable to a one-suit win. Hints taken are also tracked per game and summed across the window shown, the way Klondike counts its own — only a solver-guided hint counts; the plain highlight of movable cards (the setting, or the fallback on a deal with no certified solution) lists what is legal without leading anywhere and is never charged. The statistics screen also compares each win's moves with the certified solution shipped for its deal, as Klondike's does (`docs/games/klondike/DESIGN.md` "Statistics"), for the one- and two-suit deals that ship one; four suits has no catalog and so no comparison. The sections themselves are `core:ui`'s, shared with the other games.
