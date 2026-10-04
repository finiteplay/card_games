# Spider — Rules

Publisher: [FinitePlay LLC](https://finiteplay.org)

The rules the engine implements. `docs/solitaire/GLOSSARY.md` governs the vocabulary; `docs/PLATFORM.md` governs everything not specific to this game. Where this file and the code disagree, this file is right and the code is the bug.

## The layout

Two 52-card decks, 104 cards, dealt into:

- **Tableau** — ten columns. The four leftmost hold six cards, the six others hold five: 54 cards in all. Only the last card of each column is face up.
- **Stock** — the remaining 50 cards, face down, dealt ten at a time in five rows.
- **Foundations** — eight of them, each holding one completed King-to-Ace sequence of a single suit. They are never built on a card at a time.

Spider has **no waste**. This is the structural difference from Klondike, and the reason `Move` has no `Draw` or `Recycle`: the stock deals a card to every column at once, face up, and there is nowhere for a drawn card to wait.

## Suit counts

The deal is played with one, two, or four suits, which is the game's difficulty setting:

| Suits | Composition |
|---|---|
| One | Eight sets of spades |
| Two | Four sets each of spades and hearts |
| Four | Two sets of each suit |

The count is fixed when the deal is made and never changes during a game — it is part of the dealt layout, not session state, exactly as Klondike's draw mode is. Every other rule below is identical in all three.

One suit is the default: it is the count a player can actually win on first contact. Every suit count ships a certified catalog guaranteeing each deal it hands out is solvable (`DEALS.md`). New Game deals at whichever count is configured without asking — the count is a setting, and being asked before every deal is a question with the same answer nearly every time. Changing it in Settings applies to the next new game, never the one in play; the status row's own suit-count picker (`UI_SPEC.md` "Status Row") is the exception, since it exists specifically to switch what's on screen right now and deals immediately.

## Building

- A card may be placed on any card **one rank higher**, whatever its suit. Ranks descend; suit is not a constraint on the placement itself.
- An **empty column** accepts any card or any movable sequence. There is no King-only rule; that is Klondike's.
- A card is **movable alone** whenever it is the last card of its column.
- A **sequence** moves as one only when it is descending *and* of a single suit, and only from the point where that run begins. A group descending across suits is not a sequence and cannot be lifted, however it looks on screen.

The asymmetry is the game: a mixed descending group is legal to *make* and illegal to *move*, so building carelessly across suits is how a deal is lost.

## The stock

A **row deal** turns one card face up onto every column at once — ten cards, five times.

- It is legal only when **no column is empty**. This is the standard rule and the one the engine enforces: emptying a column is valuable, and the deal must not be usable to fill it.
- It is legal even when the deal buries a sequence or ends the game. The player is not protected from it.
- The last row deal empties the stock. There is no recycle in Spider: the cards do not come round again.

## Banking

When a column ends in a complete King-to-Ace sequence of one suit, that sequence is **banked**: removed from the tableau to a foundation, and never played again.

Banking is **automatic and unconditional** — it happens as part of the move that completes the sequence, is not offered as a choice, and is not affected by any automation setting. It cannot be declined because there is no reason to decline it: a complete sequence has no further use in the tableau, unlike a Klondike foundation card that a player may still need. For the same reason there is no foundation withdrawal in Spider and no parked card.

A transfer can complete at most one sequence, because it changes one destination column. A **row deal can complete several at once** — it puts a card on every column — and each is banked. Banking never cascades in the sense that matters: no banking creates the conditions for another, because removing thirteen cards exposes a card that was already below them.

## Winning and losing

- The deal is **won** when all eight sequences are banked.
- The deal is **stuck** when no legal move exists: no build, no lift onto an empty column, and no row deal available. Being stuck is not a loss the engine declares — it is a state the interface reports, as in Klondike.

Not every Spider deal is winnable, and Spider makes no claim that a given deal is — the rules do not depend on one. Whether a shipped deal is certified is a property of the catalog, not of these rules: `EXECUTION_PLAN.md` "Status" says whether one exists.

## Moves

The complete move set, which is the whole surface the reducer accepts:

| Move | Meaning |
|---|---|
| `TableauToTableau(fromColumn, fromIndex, toColumn)` | Move the sequence starting at `fromIndex` onto `toColumn` |
| `DealRow` | Turn one card face up onto every column |

Two entries, against Klondike's seven. Everything else Spider does — turning a column's new last card face up, banking a completed sequence, ending the game — is a *consequence* of one of these, applied by the reducer, never requested by the player.

## Invariants

These hold after every committed transition, and the reducer is the only thing that may produce a state at all:

- The state is immutable; every transition returns a new one.
- 104 cards exist at all times, counting tableau, stock and banked foundations. No card is created, destroyed, or duplicated.
- A face-down card is never the last card of a non-empty column: the reducer turns it face up as part of the move that exposed it.
- Only the last card of a column may be face up out of sequence — every card above a face-up card is face up.
- A banked sequence is complete, single-suited, and King-to-Ace.
- The move count increases by exactly one per committed player move, and a row deal is one move however many cards it turns.

## What Spider does not have

Named because a reader coming from Klondike will look for them: no waste, no draw, no recycle, no foundation withdrawal, no parked card, no automatic foundation moves during play, and no per-suit foundation for single cards. The automatic-moves and draw-mode settings do not apply and are not shown.

## The automatic finish

Once thirteen or fewer cards remain in play — one sequence, every other already banked — the game assembles and banks the last one itself, with no prompt. Nothing is being decided at that point: the remaining cards are the last run and the only question is the order they are gathered in.

It runs only after a move the player made, never after an undo, and never while restoring a saved game. Undoing back into a finishable position would otherwise finish it again on the spot, leaving no way to step back through it; a restore replays its own log, and finishing there would append moves the save does not contain.

The finish is searched for, not improvised: if no legal order completes the sequence, nothing moves. The moves it makes are ordinary committed moves, so they count, they are logged, and undo walks back through them one at a time.
