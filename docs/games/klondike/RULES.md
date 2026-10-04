# Klondike Rules

Split out of `DESIGN.md` for readability: this file is the authoritative
statement of how the game itself plays — legal moves, draw modes, automation,
scoring, and session lifecycle. `DESIGN.md` covers everything else (product,
interaction/hint-search behavior, interface, sound, localization,
accessibility, architecture, persistence, performance, quality, delivery).
Both are equally authoritative per `CLAUDE.md`.

## Rules

- Use a standard 52-card deck and seven tableau columns.
- Build tableau stacks downward in alternating colors.
- Move any valid face-up sequence as one action.
- Only a King or King-led sequence may enter an empty column.
- Automatically flip an exposed tableau card.
- Build foundations by suit from Ace through King.
- Allow the top foundation card to return to the tableau — a **foundation withdrawal**, known
  traditionally as *worrying back* (`docs/solitaire/GLOSSARY.md`).
- Draw one card from stock in draw-one mode, or up to three at once in draw-three mode (`DrawMode`); either way, only the resulting waste top is playable, and stock/waste recycling behaves identically in both.
- Recycle the waste without shuffling or limiting passes.
- Draw-one deals are selected from the solvability-guaranteed certified catalogs the app ships (`DEALS.md`). Draw-three deals are plain random shuffles with no such guarantee yet — see "Draw-Three Mode" below and `docs/games/klondike/TODO.md`.
- The certified catalog ships one partition per difficulty level; `DEALS.md` has the counts and how each is certified.
- Support variable-size catalogs so they can expand after initial validation without changing runtime behavior or file format.
- Generate and solve catalogs offline against the exact shipped rules; the app never solves to certify a deal — only the offline `tools/catalog` pipeline does that. It may run a narrower, bounded on-device search to power the Hint action; see `DESIGN.md` "On-Device Hint Search".
- Select deals without repetition until the active catalog is exhausted, then begin a new randomized traversal.
- Placing all 52 cards on foundations wins the game.

## Draw-Three Mode

An additional stock/waste mode alongside draw-one (`docs/games/klondike/TODO.md`'s "Draw-Three Mode"
item, mechanically implemented; deal certification is not — see below), selected via
Settings' "Draw three" toggle and applied to the *next* New Game only: draw mode is
fixed once a game is dealt, carried on `GameState.drawMode` itself rather than as a
live per-session toggle like automatic moves. Every other rule — tableau, foundations,
scoring, automation, win detection — is identical in both modes; only how stock moves
to waste changes.

- A draw moves up to three cards from stock to waste at once (fewer near the end of a
  pass, when the stock holds 1 or 2). Only the resulting waste top is ever playable,
  same as draw-one. The last card taken is the one placed on top.
- The waste pile itself shows up to 3 cards fanned (a small diagonal cascade) with the
  actually playable card fully on top and unobscured, rather than a single flat card —
  so a draw reads as a batch, not just a single visible replacement. Draw-one only
  ever has one card to show there, so this is a no-op for it.
- Recycling is unchanged and still unlimited in both modes.
- New Game deals under whichever mode Settings currently has selected. Replay always
  preserves the mode the game being replayed was actually dealt with, even if the
  Settings toggle has changed since — the same preservation principle draw-one's
  automatic-moves setting already gets.
- History and statistics are tracked per mode and never blended: the Statistics
  screen's Draw One/Draw Three selector, alongside the Week/Month/All Time tabs,
  reads one mode's history at a time. Reset still clears both modes together — there
  is no per-mode reset.
- The Hint action's on-device search (`DESIGN.md` "On-Device Hint Search") supports
  both modes: `SearchOrdering`'s admissible bound counts stock cards as
  `⌈stock ÷ drawCount⌉` draw moves, not one per card, so it stays a true lower bound
  (and the search stays correct) under draw-three too.

**Deal certification.** Draw-one deals come from the solvability-guaranteed certified
catalogs the app ships (`DEALS.md`). Draw-three deals do
not: they are plain random shuffles, with no solver certification behind them, unlike
every other deal in this game. This is a deliberate, scoped-down first cut — a real
draw-three catalog needs its own D1s/D1b-equivalent pipeline, run separately from
draw-one's, which is real, currently unstarted work (`docs/games/klondike/TODO.md`). Nothing about
this is hidden from the player by the UI; it is a known, documented gap, not a
silently weaker guarantee.

## Automatic Foundation Moves

Automatic movement is enabled by default and runs after each successful action except undo, but never before the player's first action — every deal starts on the raw dealt board, at zero moves, whatever the automatic-moves setting, and the first cascade (if any) happens only as part of the player's own first move, exactly like every cascade after it. It cascades until no safe card remains:

- Always move accessible Aces and Twos.
- Move a higher rank only when all cards of the previous rank are already on foundations.

When several cards are eligible, check waste first and then tableau columns from left to right.

The deal itself never depends on the automatic-moves setting: the dealt board always matches the seed alone, with or without automation.

A card the player moves from a foundation back to the tableau is parked: automation must not return it while it stays uncovered. The park clears when the card is covered, moved again, or the game is replaced. Without this rule, automation would immediately reclaim any withdrawn Ace or Two and the move would be impossible to make.

A player action and its automatic moves form one undo transaction. Undo restores the complete transaction and does not immediately trigger automation.

## Automatic Finish

Once no face-down cards remain, the outcome is decided and the rest is mechanical. The game finishes itself rather than asking for dozens of confirming taps.

- Check the condition after every committed transaction.
- Start the finish only when a foundation-only sweep provably completes: repeatedly move the lowest-ranked accessible card to its foundation, drawing or recycling stock as needed. Verify by simulation first so the board can never stop half-swept.
- If no face-down cards remain but the sweep cannot complete, play continues normally.
- The sweep is one terminal transaction. It stops the timer the moment it begins, so animation length cannot affect the recorded time.
- Count each transfer as one move, matching every other automatic transfer.
- Undo is unavailable once the win is recorded.
- The sweep ignores parked cards. Parking exists to stop automation from taking a card the player still wants; at the finish there is nothing left to want.
- The automatic-moves setting gates the finish. With automation off, the player finishes by hand.

The finish is a transition the rules engine offers and the game screen invokes. Headless replay, catalog validation, and the solver never invoke it, so solution certificates stay valid.

## Game Lifecycle

- Undo is unlimited.
- Replay restores the identical shuffled deal and rules version.
- New game selects the next deal from the current catalog using current settings.
- Confirm replay or new game when an unfinished game has at least one successful player action.
- No confirmation is needed after a win.
- A game starts on its first gameplay action: the first successful scored player action, including a draw or recycle. Hints, invalid actions, and settings do not start it.
- The timer runs exactly while the game has started, the app is foreground, no modal screen is open, and the game is not won. Persist elapsed time only.
- A restored game resumes under the same rule; restoration needs no separate wait-for-action exception.
- Replay and new game reset the timer.
- When no legal move and no productive stock action remain, show a notice offering undo and new game. A stuck game is not a loss on its own, because undo can still recover it.

## Scoring

The score is the total move count; lower is better. Elapsed time is a tie-breaker. The interface labels this value `Moves`; score and move count always mean the same number.

- Count one tap or drag as one move, regardless of stack size.
- Count each automatic foundation transfer as one move.
- Count each stock draw or recycle as one move.
- Count undo as one additional move.
- Do not count card flips, hints, invalid actions, or settings.

Undo restores the board transaction but not its counted moves, then adds one move. For example, undoing a one-move action followed by two automatic transfers changes the move count from 3 to 4.
