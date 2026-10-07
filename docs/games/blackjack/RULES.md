# Blackjack Rules

Split out of `DESIGN.md` for readability: this file is the authoritative statement of how the
game itself plays — table rules, betting, the shoe, the order of play, legal actions, the dealer's
play, settlement, and round lifecycle, starting from how a hand is valued. `DESIGN.md` covers everything else (product, interaction and
hint behavior, statistics, architecture, persistence, interface). Both are equally authoritative
per `CLAUDE.md`.

## Hand Values

- Cards 2 through 10 count their rank. Jack, Queen, and King count 10; together with the 10 they are
  the **ten-value cards**. An Ace counts 1 or 11.
- A hand's **total** is the highest sum of its cards that does not exceed 21. At most one Ace can
  count 11, since two would make 22; if even every Ace counting 1 exceeds 21, the total is that sum
  and the hand has busted.
- A total is **soft** when one Ace in it counts 11, and **hard** otherwise — including every hand
  with no Ace, and every hand where counting an Ace as 11 would bust.
- Examples: A–6 is soft 17. A–A–5 is soft 17 (1 + 11 + 5). A–6–10 is hard 17, because an Ace
  counting 11 would make 27. A–A is soft 12. 10–6–A–A is hard 18.

## Table Rules

One fixed rule set — there is no difficulty axis or ruleset picker (`DESIGN.md` "Scoring and
statistics"). `DESIGN.md` "Hint" is derived from exactly these rules, so changing any of them
regenerates its table.

- Six standard 52-card decks, 312 cards, shuffled fresh for every round (`The Shoe`).
- The dealer stands on soft 17 and on every total of 17 or more, and hits every total of 16 or
  less (`Dealer Play`).
- The dealer takes a hole card and peeks for blackjack when the up card is an Ace or a ten-value
  card (`Order of Play`).
- Blackjack — an Ace and a ten-value card as the original two cards of a hand that did not come
  from a split — pays 3:2.
- A player may double down on any two-card hand, for a stake equal to that hand's bet, receiving
  exactly one more card.
- A player may split two cards of equal rank — equal rank, not merely equal value, so a King and a
  Ten do not split — into two hands, each staked at the original bet. Resplitting is allowed up to
  three splits, four hands in all.
- A pair of Aces splits once only. Each Ace receives exactly one card and that hand is complete: it
  cannot be hit, doubled, or resplit, and an Ace with a ten-value card is 21, not blackjack. This is
  the standard casino rule, and it needs no separate "no resplit Aces" clause: a split hand starts
  with one card of the pair, so an Ace pair can only ever be the original deal, and a hand that came
  from splitting Aces is already complete.
- Double after split is allowed on every split hand except split Aces.
- No surrender.
- Insurance is offered when the dealer's up card is an Ace, costs exactly half the original bet,
  and pays 2:1 (`Order of Play`). A player blackjack is offered insurance like any other hand;
  there is no separate even-money prompt, because taking insurance on a blackjack is that choice.
- A hand totaling over 21 busts and loses, whatever the dealer later draws.

## Betting

- Starting bankroll: 1,000 chips. Chips have no cash value and cannot be purchased, wagered for,
  or exchanged for anything (`DESIGN.md` "The product").
- Bets go in steps of 10 chips, from a table minimum of 10 to a maximum of 500 or the largest step
  the bankroll covers, whichever is lower. An even bet keeps every payout whole: on a bet of *b*, a
  blackjack pays 3*b*/2 and insurance costs *b*/2.
- The bet is fixed when Deal is pressed.
- The selected bet carries over between rounds, but never above what the bankroll covers: whenever
  a round settles, a selected bet above the maximum in the first bullet is lowered to that maximum.
  Deal is therefore never offered with a bet the bankroll cannot cover; the player may raise the bet
  again as the bankroll allows. Below the table minimum, see `Round Lifecycle`.
- **Available bankroll** is the bankroll minus every stake already on the table this round — the
  original bet, doubles, split hands, and insurance. Double, Split, and Insurance are offered only
  when the available bankroll covers their stake (`Legal Actions`).
- The bankroll itself changes only when the round settles (`Settlement`).

## The Shoe

- Deal from `core/cards`' `shuffleDeckIndices(seed, size = 6 * DECK_SIZE)` — the shared shuffle,
  unchanged, using the multi-deck `size` parameter Spider's two-deck shuffle already exercises.
- **One seed shuffles one round.** Every round starts from a fresh, full 312-card shoe and discards
  the unused remainder. Every card dealt carries true six-deck odds, and nothing — shoe position,
  penetration, a reshuffle notice, anything a card counter could use — carries from one round to
  the next.
- The seed is drawn at Deal from a random source the player can neither predict nor repeat, and is
  saved with the round (`DESIGN.md` "Persistence"). Unlike a solitaire seed it is never chosen,
  shown, or replayed.
- Deal order: shuffled index 0 to the player, 1 to the dealer face up, 2 to the player, 3 to the
  dealer face down (the hole card). Every later card — a hit, a double's card, a split hand's second
  card, a dealer draw — takes the next unused index.
- Split hands are played left to right. A split hand receives its second card when it becomes the
  active hand, and is played to completion, including any resplit or double, before the next split
  hand receives its second card.

## Order of Play

1. **Deal.** The bet is placed and four cards are dealt (`The Shoe`).
2. **Insurance**, only when the up card is an Ace and the available bankroll covers half the bet:
   the player takes or declines insurance before any other decision. Otherwise this step is
   skipped.
3. **Peek**, only when the up card is an Ace or a ten-value card. If the dealer has blackjack, the
   hole card is turned over and the round settles at once (`Settlement`). Otherwise any insurance
   is lost, the hole card stays face down, and play continues.
4. **Player blackjack.** If the player has blackjack and the dealer does not, the hole card is
   turned over and the round settles at once.
5. **Player decisions**, one hand at a time (`Legal Actions`), until every hand is complete. A hand
   is complete when it stands, doubles, busts, reaches 21, or is a split Ace that has received its
   card. A hand that reaches 21 stands without Stand being pressed.
6. **Dealer play** (`Dealer Play`) and **settlement** (`Settlement`), committed as one transaction
   by the decision that completed the last hand.

## Legal Actions

At step 2 of `Order of Play`:

- **Take Insurance** and **Decline Insurance** — once per round, and nothing else is offered.

At step 5, for the active hand:

- **Hit** — the hand totals under 21.
- **Stand** — always.
- **Double** — the hand holds exactly two cards, is not a split Ace, and the available bankroll
  covers a stake equal to the hand's bet.
- **Split** — the hand holds two cards of equal rank, fewer than three splits have been made this
  round, and the available bankroll covers another bet.

An action absent from this list is never shown, rather than shown and refused (`DESIGN.md`
"Interaction"). There is no Undo (`Round Lifecycle`).

## Dealer Play

The dealer's turn is the house's own fixed turn, not automation standing in for the player, and no
setting affects it (`DESIGN.md` "What Blackjack is not").

- It begins only once every player hand is complete.
- The hole card is turned over. If every player hand has busted, the dealer draws nothing further.
- Otherwise the dealer hits while the total is 16 or less and stands at 17 or more, soft or hard.
- A dealer blackjack never reaches this step; it settles at the peek (`Order of Play`).

## Settlement

Each hand settles on its own stake. Doubling and splitting change the stake, never the rate.

- **Dealer blackjack** (at the peek): a player blackjack pushes; any other hand loses. Insurance, if
  taken, pays 2:1.
- **No dealer blackjack:** insurance, if taken, is lost.
- **Player blackjack**, dealer without one: pays 3:2.
- **Player bust:** loses. The outcome is final at the bust and is shown then, without waiting for
  the dealer.
- **Dealer bust:** every hand that has not busted wins 1:1.
- **Otherwise:** the higher total wins 1:1; equal totals push.
- The bankroll is updated once, by every hand's result and the insurance result together, when the
  round settles.

## Round Lifecycle

- A round starts on Deal, with the bet already chosen. Nothing is dealt before that, so automation
  has no "first action" to wait for.
- **There is no Undo.** Every player decision draws a card or is followed by one being revealed — a
  hit, a double, the next split hand's second card, the peek after insurance, the dealer's play
  after the last hand completes. The shoe is fixed for the round, so undoing any decision would let
  the player see a card and then choose differently. No subset of decisions is safe to undo.
- **A round cannot be abandoned.** Next round is offered only once the current round has settled.
  Abandoning a round after seeing a card would be the same free second look as undo, and nothing
  forces it: Stand is always legal, and a round ends within a few decisions.
- Next round deals at the currently selected bet, which settlement has already lowered to one the
  bankroll covers (`Betting`). There is no Replay: the seed is not repeatable,
  and a settled round has nothing left to attempt.
- There is no timer; nothing about a round is timed (`DESIGN.md` "Scoring and statistics").
- If the bankroll is below the table minimum once a round settles, no round can be dealt. The
  player is offered a reset to the starting 1,000 chips in place of Next round — a way out, never a
  dead end. A reset changes the bankroll and sets the selected bet to the table minimum;
  statistics are kept, and the reset is counted (`DESIGN.md` "Scoring and statistics"). The bankroll
  becomes exactly 1,000: whatever few chips were left (0 to 5, never 10 or more) are not kept.

## Scoring

No move count and no time. The bankroll is the score: each settlement reports its chip delta, and
Statistics tracks the bankroll alongside hand outcomes (`DESIGN.md` "Scoring and statistics").
