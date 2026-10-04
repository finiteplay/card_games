# Klondike Solitaire Design

Publisher: [FinitePlay LLC](https://finiteplay.org)

## Product

An offline Klondike game inspired by classic Windows Solitaire. It should feel immediate, readable, and calm rather than visually elaborate.

## Interaction

- Tap the stock to draw or recycle.
- Tap a face-up tableau card to move its sequence using this priority:
  1. The lowest-index legal tableau destination strictly to the right of its own column
  2. A legal foundation move, safe or not
  3. The lowest-index legal tableau destination to the left of its own column, only when neither of the above exists
- Tap the waste top card to move it using this priority:
  1. Safe foundation
  2. Lowest-index legal tableau destination, left or right
  3. Legal foundation move that is not safe, only when no tableau destination exists
- Tap a foundation's top card to withdraw it to the lowest-index (left-most) legal tableau column.
- Drag cards to choose any specific legal destination, safe or not.
- Give brief visual feedback for invalid actions.

A tableau tap never refuses a legal move: it exhausts rightward tableau, then foundation regardless of safety, then leftward tableau, deterministically and without weighing whether banking the card now is strategically wise — the player chose that card, so tap acts on it rather than second-guessing. The waste tap keeps the older safe-foundation-first priority, since it is not making a rightward-vs-foundation choice about a specific tableau column. Drag remains the way to choose a specific destination deliberately, bypassing this ordering entirely.

Tapping the exact pile (and, on the tableau, the exact card) a shown hint currently highlights overrides this priority entirely: it commits the hint's own proven move instead of whatever the standard priority would otherwise pick, since those can disagree — the waste tap's safe-foundation-first rule, for instance, would otherwise contradict a hint that highlights sending that same card to a tableau column. Tapping any other pile while a hint is showing is unaffected and still resolves through the standard priority above; the override is scoped to the highlighted pile alone. Dragging is unaffected either way — it already lets the player choose a specific destination deliberately.

### On-Device Hint Search

Hint resolves in four stages, cheapest first:

1. **Every strategy ruleset**, in Trivial, Easy, Medium, Hard, Expert order
   (`docs/games/klondike/DIFFICULTY_LEVELS.md`), plays the board out with no search.
   The first ruleset that reaches a win supplies a proven line. These deterministic
   strategies are distinct policies, not a promise that every lower-tier win is also
   an Expert win.
2. **Full-legal depth-first search**, capped at an additional 300,000 nodes.
3. **Full-legal weighted A***, including foundation withdrawal, with the caller's full
   340,000-node allowance. DFS does not reduce it; both searches share the 8-second cap.
4. **The top ruleset's preferred move as a suggestion**, when both searches run out of
   budget without proving anything. It is highlighted alongside the inconclusive
   notice so the player has something to act on, but it is explicitly *not* guidance:
   nothing about the board was proven either way, which is exactly why the search was
   inconclusive. Only the notice text distinguishes the two; the highlight looks the
   same, because both answer "what should I do next".

The search itself is bounded and runs from the board actually on screen — the same
solver technology the offline catalog pipeline uses to certify deals (`tools/catalog`,
`:games:klondike:solver`), but scoped narrowly: it proves something about *this board*, never about
the deal as a whole, and it is never invoked to certify a deal for the catalog. It
reports exactly one of:

- **Guidance**: a full line to a win exists from here — proven, because the found
  line is independently replayed through the canonical rules before being shown, and
  biased toward short lines by the search's cost-so-far term, but *not* guaranteed
  shortest: proving shortest-ness on-device is intractable for real deals within an
  interactive budget (measured at millions of search nodes for a fresh deal, versus
  the low hundreds of thousands the budget allows). The next move on that line is
  shown. Requesting again while it is showing has nothing new to advance to, since
  there is one proven line, not a ranked list to page through — unlike the previous
  one-move heuristic this replaces, which offered ranked candidates to cycle through.
- **No solution**: the reachable state space from the current board was fully explored
  with no win found. This is a proof, not a guess.
- **Inconclusive**: the search's node/time budget ran out before either could be
  established. Reported distinctly from both of the above — the search itself stays
  honest about not knowing, rather than defaulting to a guess. The notice text shown
  for it ("Searching for a win took too long. This game is likely unsolvable from
  here.") is a practical hint to the player, not a restatement of that guarantee: by
  the time the full pipeline exhausts its budget without a proof either way, the board
  has resisted every strategy and both bounded full-legal traversals. That correlates
  with genuine unsolvability, but it remains unknown.

**Search algorithms.** DFS preserves the move generator's productive-first order,
except that an exact inverse of the preceding move is tried last. It uses the same
column-order-invariant transposition key as A*, avoiding equivalent tableau permutations.
Its low memory overhead makes the additional 300,000 nodes cheap.

The final search is weighted A*: it expands the most promising board
first, ranked by `g` (moves so far) plus independently weighted state features. The
first feature is a lower bound on moves still needed — cards not yet on a foundation,
cards still in the stock, and cards buried above a same-suit lower card in their own
column, each provably requiring its own future move. At lower-bound weight 1 with all
other weights zero, the first goal popped is provably shortest — this exact
configuration is what the brute-force optimality test pins. Raising only that weight
is standard weighted A*: the found line is guaranteed within that multiple of
shortest, in exchange for expanding far fewer nodes. Three further independently
weighted terms can be blended in: face-down cards remaining, cards piled above aces,
and how deeply the card each foundation needs next is buried. These are progress
signals the admissible bound cannot justify, so any nonzero mix forfeits the length
guarantee entirely — but on the plateau-heavy boards that defeat every admissible
configuration within budget, they are what finds a certificate at all. Keeping the
coefficients independent is deliberate: changing the lower-bound greediness must not
silently rescale every progress signal.

The transposition table is keyed by a full-state hash that sorts the seven tableau
column signatures, making it deliberately blind to which physical column holds which
pile — no rule depends on a column's index. It still includes stock order, waste order,
and their boundary: move-level A* must distinguish a board before and after Draw. A
separate board-loop fingerprint uses the same sorted tableau signatures and foundations
but excludes the stock and waste and their position. Hashing sorts only reusable scratch storage and
never mutates the immutable board. Klondike positions spend a large share of their
nominal branching on column-order symmetry, so collapsing it removes many equivalent
layouts. When a *safe* foundation move exists (both opposite-color suits already at
the rank beneath it), it is the only move the search tries from that board — banking
it can never cost a win, so the alternatives are provably redundant. The search also,
deliberately, deprioritizes — never forbids — a move that exactly undoes the one
before it, or that crosses the same two columns with the same run for a third time,
so a found line does not visibly shuttle a card back and forth for the player
following it.

The retained A* board is compact but still immutable. Like the offline `FastBoard`, it
stores each tableau as one byte array plus a face-down prefix count, and stores stock
and waste as one draw-order pile plus a boundary. Unlike FastBoard it cannot mutate one
board with make/unmake, because A* keeps sibling states in its frontier. Instead, a
child shares every untouched column; Draw and Recycle share the entire pile and change
only its boundary. This is a storage optimization only: the generator still expands
individual legal moves, including each Draw and Recycle, rather than FastBoard's
strategy-level choices.

The A* ordering uses lower-bound/down-card/ace-burial/needed-card weights
`25/150/25/0`, the tested catalog-coverage configuration. DFS and A* both include foundation withdrawal, so
either may report "no solution" only after exhaustive traversal. A timeout remains
unknown and contributes no states to the dead-state cache. Every found line is
independently replay-validated before being shown.

**Presentation order.** Before a found line is cached or shown, it is reordered for
the player who follows it move by move: tableau moves come before plays from the
waste, and both come before draws and recycles, wherever the line's own dependencies
allow. Only adjacent moves that provably commute are ever swapped — checked by
replaying both orders through the canonical rules to the identical board, never
assumed from move types — so the reordered line keeps exactly the same moves, length,
and final board as the found one. The check is what keeps it honest: an unrelated
tableau move crosses any number of draws to the front, but a waste play can never
cross the draw that exposes its card (playing earlier would play a different card),
so "draw until the needed card appears, play it immediately" comes out naturally and
"rotate the whole stock, then come back for things" does not. This is presentation
only; the search and its budget are untouched by it.

**Removing what the line does not need.** Reordering alone leaves two things a player
experiences as the hint being wrong, because both are invisible to a rule that only
swaps adjacent moves reaching an identical board:

- *A sequence shuffled out and back.* A sequence leaves one column for another and returns
  several moves later with neither column used in between. Because the foundations
  advanced in the meantime the board is never repeated, so the exact-state strip that
  catches ordinary cycles cannot see it, and the player is told to undo work they were
  just told to do.
- *Stock rotation that buys nothing.* The line spins the stock and then returns to
  moving tableau cards without ever playing the card it exposed.

Both are removed by proposing the change and re-proving it: the pair is dropped, or the
rotation is deferred toward the move that consumes it, and the result is kept only if
it still replays through the canonical rules to a win. Nothing reasons about *why* a
change is safe, so this can never turn a proof into a guess — at worst it finds no
improvement and leaves the line alone. It only ever deletes or defers, so the line
never grows.

A round trip that survives is real work: staging a run on a column so other runs can be
stacked on it and taken off again leaves that column looking untouched at the end, and
dropping the trip would break the moves that depended on it. The replay is what tells
the two cases apart, rather than a rule about how the line looks.

The specific weights and shares were fitted empirically
against every deal in the shipped catalog (`HintSearchBudgetTest` keeps the fit
honest): every deal resolves to Guidance from its raw fresh board — the deepest
search the game will ever ask for — within the interactive budget. The search runs
on a dedicated thread at normal priority, not below: on-device measurement found a
below-normal priority thread lands in a scheduler class that cut throughput to
roughly a third on a mid-range phone, which is a bigger cost than the burst risks to
UI smoothness (Android schedules the UI thread above normal on its own).

Two caches carry work forward across hint requests within one game (cleared on New
Game and Replay only — a state's solvability never depends on how the board reached
it, so undo does not clear them): every state a prior exhaustive search proved dead,
seeded into the next search so it is never re-explored; and the last winning line
found, so a live board matching a state on that line resolves instantly instead of
re-searching. Repeated hint requests, the normal way a player uses this feature, get
progressively cheaper as a result.

Unlike the offline catalog search, this search also considers withdrawing a
foundation card back to the tableau — the offline search skips that move type to keep
a batch run of thousands of seeds affordable (see D1s), but skipping it here would let
the search wrongly report "no solution" for a board that only wins through it, which
the honesty of the no-solution result depends on.

The catalog invariant is unaffected by any of this: certifying that a *deal* is
solvable happens exclusively in the offline `tools/catalog` pipeline, never on a
player's device. Only this narrow, board-specific, interactive search runs on-device,
and only to power one player-requested action.

Tap priority and hint search differ deliberately. A tableau tap acts on a card the player already chose, so it prefers advancing that specific card rightward within the tableau, then banks it on a foundation regardless of safety, and only moves it leftward as a last resort. A hint has no such local preference — it suggests whatever move the search proves is a step on a winning line, however locally unhelpful that move might look. Tapping the card a shown hint highlights is the one exception ("Interaction" above): it commits the hint's own move rather than the standard priority, so the player can act on what they were just shown without the two disagreeing.

## Statistics

Track games, per draw mode (`RULES.md` "Draw-Three Mode" — never blended across modes):

- Wins, losses, games played, and win rate
- Current and longest win streak
- Best completion time and fewest moves
- Completion times: min, p10, p50, p90, and max; and the average
- Completion move counts: min, p10, p50, p90, and max
- Longest losing streak, and the win rate over the latest 20 decided games — shown only once more than 20 are decided, since before then it would repeat the overall rate
- **Efficiency against the certified solution:** each win's move count as a percentage of the solution shipped with its deal (100 matches it; lower is better), drawn as a range graph with the average and the count of wins that matched or beat it. A win on a deal that ships no solution — Insane, draw-three — is left out rather than guessed at. The win dialog states the same comparison for the game just won.
- **Win rate by level:** wins and losses per graded level, from the level recorded on each game. Draw-three and games recorded before levels were kept have none and appear in no row.
- Hints taken, and wins taken without one. Hint's *show every legal move* mode (the setting that turns the solver off) is not counted: it lists what is legal without leading anywhere, so it measures nothing about how much help the player asked for. Only a hint that points along a winning line counts.

A game becomes played on its first action. Replacing an unfinished played game counts as a loss, except by starting a Replay (below) — this includes **switching level**, which forfeits the deal in play and deals a new one. Any loss, including abandonment, resets the current win streak.

Switching level is treated as New Game rather than as Replay, because it is a request for a *different* deal: leaving the old one running would answer the opposite of what was asked, and exempting it from the record would make the picker a way to abandon a losing position for free. Selecting the level already in force does nothing at all, for the same reason. A deal still on its raw board records nothing when the level changes — there is no played game to abandon.

Define the denominators exactly, so an in-progress game cannot dodge abandonment or distort the rate:

- `win rate = wins / (wins + losses)`
- `games played = wins + losses + 1` while an unfinished, statistics-eligible played game exists

Retain game history locally and calculate percentiles using the nearest-rank method. Display the completed-game sample size with distribution statistics, each as a range graph (a track spanning min–max, a brighter band spanning p10–p90, a marker at the median) rather than a bare text line — with the identical five numbers also spoken through the graph's content description, so nothing is graph-only information. Reset statistics and history only after confirmation, and always across every period at once; there is no per-period reset.

The Statistics screen offers Week, Month, and All Time tabs, all reading the same underlying history filtered to a trailing window ending at the moment the screen opens — Week and Month are rolling 7-/30-day windows measured back from now, not calendar-boundary weeks/months, so they never jump on the 1st of the month or a fixed weekday. Switching tabs re-filters already-fetched history in memory rather than reading storage again. The tab selection itself is not persisted: every time the screen opens, it starts back at All Time.

A game started by Replay never contributes to statistics — no abandonment loss for the game it replaces, and no win or loss for the replayed game itself, however it ends. Replay exists to retry the same deal, not to farm or distort a solvable catalog's win rate; New Game (a different, also-solvable deal) remains fully recordable either way. A deal chosen from the deal picker counts like a New Game unless it has already been won, in which case it is treated as a Replay and contributes nothing: a deal the player has solved is known solvable, and replaying it by choice would inflate the rate.

## Interface

`UI_SPEC.md` holds the exact arrangement and is the authority on it; the shape of it: portrait puts the status row and the foundations at the top, the tableau below them, stock and waste in a row of their own at the bottom wherever the board can afford it, and the actions in a bottom bar. Landscape runs the status row the full width above the board and flanks the board with two rails one button wide, foundations in a strip at one edge and stock and waste at the other.

The default is a right-handed layout: stock and waste sit on the side nearest the holding hand in both orientations, with Undo and Hint the actions closest to that edge. A left-handed layout setting mirrors all of it (`UI_SPEC.md` "Left-Handed Layout") without touching tableau order, foundation order, or any game rule. The action bar and the rails carry Undo, Hint, New, Replay, and Settings; Statistics and Help are out of that set, as icons placed with the status row rather than among the actions, because both read the record rather than act on the game in play. Settings are automatic foundation moves, skip animations, handedness, sound, theme, language, draw mode, and difficulty.

Difficulty selects which graded deal list a new draw-one game is taken from — Trivial, Easy, Medium, Hard, Expert, Insane, or Random, which picks a level per game (`docs/games/klondike/DEALS.md`). Like draw mode it applies to the next New Game only, since a deal's difficulty is fixed the moment it is dealt. Draw-three ignores it: those deals are uncertified random shuffles with no grading behind them.

Card moves run at a constant speed rather than a fixed duration, bounded to 80–450 ms so neither a short hop nor a full-board flight reads wrong (`UI_SPEC.md` "Motion"). Invalid moves briefly shake or outline the source. A hint pulses the cards that would move twice and then holds them highlighted; the destination is not separately marked. The automatic finish sweeps each remaining card individually at three times normal speed, since the player has no decisions left to watch for, rather than merging them into overlapping motion. A win plays a skippable animation lasting no more than three seconds. Card face corner rank/suit glyphs are set in a bold serif (`Typeface.SERIF`/`BOLD` — a platform font, no bundled asset), sized 10% larger than the MVP's original sans-serif-bold pass, closer to a traditional deck's corner index than a default system font.

Two board themes ship, light and dark, chosen by a persisted setting that also offers follow-the-system and time-of-day automatic: [PLATFORM.md](../../PLATFORM.md) "Themes" governs both palettes and the contrast reasoning behind them. What is Klondike's own — the cloth table, the card backs, the empty-slot token — is in [UI_SPEC.md](UI_SPEC.md) "The table and the backs".

## Sound

Mechanism, gating, and per-step timing: [PLATFORM.md](../../PLATFORM.md) "Sound". The
four assets live in `games/klondike/app/src/main/res/raw`. Move and automatic-transfer
play a quiet, smooth "sh" (a single bandpass-filtered noise swell, not a tone or a
granular riffle); invalid plays a short percussive tap (a fast-decaying low thump plus a
brief click transient); win is a plain synthesized tone.

The automatic-finish sweep is Klondike's one exception to the per-step rule: it plays no
per-card blip at its 3x speed, which would read as a machine-gun burst, and relies on the
win sound alone once it completes.

## Localization

31 languages, the resource discipline, and the completeness gate:
[PLATFORM.md](../../PLATFORM.md) "Localization". Klondike ships only its own strings —
`game_title`, draw-three, difficulty and level names, and the deal status line; the rest
merge in from `core/ui`. `LocaleStringsCompletenessTest` (`:games:klondike:app`) is this
app's instance of the platform gate.

## Accessibility

The contrast floors, touch targets, and reduced-motion rules, and the decision that
screen-reader support is out of scope: [PLATFORM.md](../../PLATFORM.md) "Accessibility".
Klondike's own numbers — the minimum card width and the exposed face-up band that stand in
for the 48 dp control minimum — are in [UI_SPEC.md](UI_SPEC.md) "Board Geometry", and
reduced motion's exact effect on automatic moves is in [UI_SPEC.md](UI_SPEC.md) "Motion".

## Architecture

This repo hosts a family of card games sharing one core, of which Klondike is the first. `ARCHITECTURE.md` holds the full module map, the layering gates, and the recipe for adding a game; the layers as they bear on Klondike:

- `core:cards`: `Card`, `Rank`, `Suit`, the canonical deck, and the deterministic shuffle — no game rules, so `canBuild` is *not* here
- `core:storage`: DataStore plumbing and the per-app locale override
- `core:ui`: theme, card rendering, sound, and the strings every card game needs, across all 31 locales
- `solitaire:catalog`: bundled seed catalogs, catalog format and loader, and local non-repeating traversal
- `games:klondike:rules`: pure Kotlin state, legal moves, automation, auto-finish, tap priority, scoring, undo, and the solution codec
- `games:klondike:solver`: search engine (`SNode`, A*, replay validation) plus the hint engine and strategy tiers; used by both the offline catalog pipeline and the on-device hint search; no Android imports
- `games:klondike:app`: Compose screens, input handling, animation, semantics, Canvas rendering, Android lifecycle, and the active game, history, settings, and statistics stores
- `tools:catalog`: offline catalog generation, solving, validation, and integrity reporting — depends on the solver, never the reverse, and never reaches an app classpath

Dependencies run one way: `games/* → solitaire/* → core/*`. Game state is immutable. User actions produce new state, making replay, undo, persistence, and unit testing deterministic. The rules and solver modules remain platform-independent.

## Persistence

Save cadence, atomicity, the immutable-state rule, corruption recovery, and the privacy
constraints: [PLATFORM.md](../../PLATFORM.md) "Persistence".

Klondike saves the active game as its seed, rules and shuffle versions, elapsed time, and
an append-only log of committed transactions, including undo. Restoring replays the log
through the same reducer, which rebuilds the board, move count, and full undo history.
Saves stay small and append-only however long the game runs, so unlimited undo costs
nothing at rest. The log uses the same primitive move encoding as solution certificates.

Catalog version and traversal state persist too, so new games do not repeat. A freshly
deal is saved immediately, not deferred until the player's first action — the
traversal position already advances the instant a seed is drawn, so a save deferred any
later could let a kill-before-first-move restart silently skip straight to the *next* deal
instead of resuming the one actually on screen.

**Game archive.** The **last 100 finished games** are retained, each with its complete
move log, oldest game evicted first — so how deals are actually played can be studied
offline, the evidence `DIFFICULTY_LEVELS.md` needs and does not currently have. Each entry
keeps everything replay needs (seed, versions, draw mode, the automatic-moves setting the
deal started under, and the log itself) plus its outcome, elapsed time, move count and
timestamp, so an archived game reconstructs every board the player saw rather than merely
describing it.

**The limit counts games, never moves.** A 400-move game occupies exactly one of the
hundred slots and keeps all 400 of its moves; an individual game's log is never shortened,
trimmed, or summarised at any point — not on write, not on eviction, not on export.

This archive is deliberately **not statistics**, and the two must not be conflated:

- Statistics keep reading unbounded history, so evicting an old game can never change a
  win rate, a streak, or a personal best. Only the archive is bounded.
- A game excluded from statistics (a Replay, per "Statistics" above) is still archived,
  flagged as not counting. It is still a real sequence of player decisions, and the
  archive exists to study decisions rather than results.
- The confirmed statistics reset clears the archive too. A reset that wiped visible
  results while quietly keeping every move the player made would not be the reset the
  player asked for.

Local-only, like every other store: never transmitted, and nothing in this project
networks. Reading it back is a debug-build affordance only — release builds compile no
export path at all.

## Performance

Budgets and the no-background-work rule: [PLATFORM.md](../../PLATFORM.md) "Performance".

## Quality

The shared verification shape and the supported API range: [PLATFORM.md](../../PLATFORM.md)
"Quality". Klondike adds:

Automated tests cover dealing, legal and invalid moves, both draw modes' stock recycling,
tap priority, automatic moves, parked cards, automatic finish, atomic undo, scoring, timer
state, stuck detection, win detection, move-log persistence and replay, deterministic
shuffle compatibility, catalog integrity, and non-repeating selection.

The offline catalog pipeline must solve every emitted seed under its declared draw mode
and rules version, reject duplicates, and produce a versioned integrity report. Runtime
and CI tests verify catalog counts, hashes, uniqueness, and representative solution
replays without solving deals on the device.

## Delivery

1. Android project, rule engine, catalog format and loader, and a time-boxed solver feasibility spike
2. Playable board with save and restore
3. Undo, hints, automation, scoring, settings, statistics, and history
4. Accessibility validation, then the rules freeze
5. Certified catalog generated against the frozen rules (`DEALS.md` says what ships)
6. Performance and release validation

Certifying the catalog comes late on purpose. It is the most expensive artifact to regenerate, and a rules change after generation invalidates every certificate, so real gameplay exercises the rules first. `EXECUTION_PLAN.md` holds the ordering and gates.

## Supporting Specifications

- [RULES.md](RULES.md): legal moves, draw modes, automation, scoring, and session lifecycle
- [EXECUTION_PLAN.md](EXECUTION_PLAN.md): dependency-ordered work packages and gates
- [DEALS.md](DEALS.md): catalog generation, format, selection, and proof
- [UI_SPEC.md](UI_SPEC.md): layouts, input, feedback, and accessibility
- [ACCEPTANCE.md](ACCEPTANCE.md): test matrix, performance measurement, and release evidence
- [TODO.md](TODO.md): deferred catalog expansion, draw-three deal certification, native-speaker translation review, the difficulty-level findings not yet acted on, and iOS work
