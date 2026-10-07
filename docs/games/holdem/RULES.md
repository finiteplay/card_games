# Texas Hold'em Rules

Split out of `DESIGN.md` for readability: this file is the authoritative statement of how the game
plays — hand rankings, the tournament, the deal, betting, legal actions, pots and showdown, and
leaving. `DESIGN.md` covers everything else (product, interaction, hint, opponents, statistics,
architecture, persistence, interface). Both are equally authoritative per `CLAUDE.md`.

Every rule here is the standard one for no-limit Hold'em except where marked **Simplified**, which
says what is left out and why.

## Hand Rankings

A player's hand is the best five cards from their two hole cards and the five board cards, using
either, both, or neither hole card. From highest:

1. **Straight flush** — five consecutive ranks, one suit. Ace-high is a royal flush, ranked as the
   highest straight flush rather than separately.
2. **Four of a kind**, then the fifth card.
3. **Full house** — three of a kind and a pair; the three decide first.
4. **Flush** — five of one suit, compared card by card from the highest.
5. **Straight** — five consecutive ranks. An Ace is high (10–J–Q–K–A) or low (A–2–3–4–5, the
   lowest straight); never both, so Q–K–A–2–3 is not a straight.
6. **Three of a kind**, then the two highest other cards.
7. **Two pair** — the higher pair, the lower pair, then the fifth card.
8. **One pair**, then the three highest other cards.
9. **High card** — compared card by card from the highest.

- Only the five cards count. A sixth or seventh card never breaks a tie.
- **Suits never rank.** Hands equal on all five cards tie, and tied hands split the pot (`Pots and
  Showdown`).
- There are 7,462 distinct five-card hand values; the evaluator is held to that count and to the
  standard frequency of every category (`EXECUTION_PLAN.md` H2).

## The Tournament

- **Six seats**: the player and five opponents (`DESIGN.md` "Opponents"). The player's seat is
  always drawn at the bottom; which seat holds the first button is drawn from the tournament's seed.
- **Starting stack** 1,500 chips each, 9,000 at the table. Chips are whole numbers; no fraction of a
  chip ever exists.
- **Blinds rise every 10 hands**, counted from the tournament's first hand, on this schedule (small
  blind / big blind):

  | Level | Blinds | Level | Blinds |
  |---|---|---|---|
  | 1 | 10 / 20 | 8 | 200 / 400 |
  | 2 | 15 / 30 | 9 | 300 / 600 |
  | 3 | 25 / 50 | 10 | 400 / 800 |
  | 4 | 50 / 100 | 11 | 600 / 1,200 |
  | 5 | 75 / 150 | 12 | 800 / 1,600 |
  | 6 | 100 / 200 | 13 | 1,000 / 2,000 |
  | 7 | 150 / 300 | 14 on | each doubles the one before |

  No antes. Levels count hands, never time, so nothing in a tournament is timed.
- **Elimination.** A player with no chips when a hand ends is out. Players knocked out in the same
  hand finish in order of the chips they started that hand with, more chips finishing higher; equal
  starting stacks share the higher of the places they span.
- **The end.** The tournament ends when one player holds every chip; that player finishes first. If
  the player is eliminated, the tournament ends for them at that place — the remaining opponents'
  play is not simulated, since no result of it would be shown or counted.
- **Finishing places** run from 1st to 6th: a player finishes in the place equal to the number of
  players still in the tournament, themselves included, when they are knocked out.

## The Button and the Blinds

- Before each hand after the first, the button moves to the next seat to the left still in the
  tournament.
- The small blind is the first live seat to the button's left, and the big blind the next.
- **Heads-up** (two players left): the button posts the small blind, acts first before the flop, and
  last on every later street.
- A player who cannot cover a blind posts what they have and is all in.
- **Simplified.** The button simply moves to the next live seat; the "dead button" rule that keeps a
  player from posting the big blind twice or skipping it after an elimination is not applied. It
  needs per-seat blind history the player never sees, and its effect on a six-seat sit-and-go is a
  blind or two over a tournament.

## The Deal

- One standard 52-card deck, shuffled fresh for every hand from `core/cards`'
  `shuffleDeckIndices(seed, size = DECK_SIZE)`, the shared shuffle unchanged.
- **One seed shuffles one hand.** The tournament's seed is drawn when it starts, from a random source
  the player can neither predict nor repeat; each hand's seed is derived from the tournament's seed
  and the hand number by the fixed mix the contract names (`EXECUTION_PLAN.md` "Deterministic Deal
  Contract"). Seeds are never chosen, shown, or replayed by the player.
- **Deal order**, one card at a time: shuffled index 0 to the first live seat to the button's left
  (the small blind; heads-up, the big blind), then clockwise around the live seats, twice, so every seat holds two cards; then one burn, three flop cards, one burn, the
  turn, one burn, the river — each from the next unused index.
- The deck's order is fixed when the hand is shuffled. A fold never changes which card comes next;
  the board a hand would have dealt is the board it deals, whoever is left.

## Betting

There are four betting rounds: **preflop** after the hole cards, then the **flop**, **turn** and
**river** after each is dealt.

- **First to act.** Preflop, the seat to the big blind's left (heads-up, the button). On later
  streets, the first live seat to the button's left.
- **Bet and raise sizes.** Any whole number of chips within the limits, not only multiples of the big
  blind.
  - The minimum opening bet on a street is the big blind.
  - A raise must raise by at least the largest bet or raise increment already made on this street,
    and by at least the big blind.
  - No maximum but the stack: a player may always go all in.
- **A short all-in does not reopen the betting.** An all-in that raises by less than a full minimum
  raise does not let players who have already acted on this street raise again; facing it, they may
  only call or fold. A player who has not yet acted may raise as normal.
- **The big blind's option.** If preflop action reaches the big blind with no raise, the big blind
  may check or raise.
- **A round ends** when every player who is neither folded nor all in has acted at least once and
  has put in the same amount as the largest bet, or when only one player has not folded.
- **The uncalled part of a bet** is returned to the player who made it before any pot is awarded.
- **No more betting** once at most one player who has not folded still has chips: the remaining
  board cards are dealt with no betting rounds between them, and the hand goes to showdown.

## Legal Actions

On a player's turn, with *c* the chips needed to call and *s* their stack:

- **Fold** — always, when *c* > 0. When *c* = 0, Fold is not offered: Check costs nothing, and
  folding a free hand is a mistake the interface does not invite.
- **Check** — when *c* = 0.
- **Call** — when *c* > 0. If *c* ≥ *s* the call puts the player all in, and it is labelled as a call
  for that amount, not as a separate action.
- **Bet** — when no bet has been made on this street, for any amount from the minimum bet to *s*.
- **Raise** — when a bet has been made, *s* > *c*, and the betting is open to this player (`Betting`,
  the short all-in rule): for any total from the minimum raise to all in. If *s* is below the
  minimum raise, the only raise offered is all in.
- **All-in** — offered as its own button whenever Bet or Raise is, as a shortcut for the maximum.

An action not listed is never shown rather than shown and refused (`DESIGN.md` "Interaction").
There is no Undo (`Leaving`).

## Pots and Showdown

- **Side pots.** When a player is all in for less than others have put in, the pot splits: each pot
  holds, from every player, up to the smallest all-in amount among those still contesting it, and a
  player is eligible only for the pots they paid into in full. Pots are built from the bottom up,
  main pot first.
- **Without a showdown.** When every other player folds, the last player wins every pot they are
  eligible for and their cards are not shown.
- **Showdown.** Every hand still in is shown — there is no mucking — and each pot goes to the best
  hand among the players eligible for it, the main pot last.
- **Split pots.** Tied hands share a pot equally. Chips that do not divide go one at a time to the
  tied players in seat order starting from the first seat to the button's left.
- Every chip is accounted for at every hand's end: the stacks always sum to 9,000.

## Hand and Tournament Lifecycle

- A tournament starts on **New Tournament**: stacks set, profiles and the first button drawn from
  the seed, and the first hand dealt with its blinds posted. Opponents to act before the player do so
  at once; they are other players taking their turns, not automation acting for the player, so
  `AGENTS.md`'s "no automation before the first action" rule does not apply to them.
- Each hand ends when its pots are awarded. The next is dealt automatically after a short pause,
  showing the result first, unless the tournament has ended.
- **There is no Undo.** Every decision is followed by a card or another seat's action the player
  could not have seen before it (`DESIGN.md` "What Hold'em is not").
- There is no timer and no clock on decisions; the player may take as long as they like.

## Leaving

- **Leave tournament** is available at any time, with a confirmation.
- Leaving forfeits: the player finishes in the place they would take if knocked out at that moment
  (`The Tournament`), counted as an elimination in every statistic. Chips they had in the current
  hand are lost with it.
- There is no way to leave a hand and keep the tournament: folding is that.
- A tournament that is left or finished cannot be resumed or replayed; New Tournament starts another
  from a new seed.

## Scoring

No move count and no time. A tournament's result is its finishing place; a hand's is the chips it
moved. Statistics track both (`DESIGN.md` "Scoring and statistics").
