# Blackjack — Design

Publisher: [FinitePlay LLC](https://finiteplay.org)

What the game is, and the decisions behind it. `RULES.md` is the rules; `docs/PLATFORM.md` is
everything true of every FinitePlay game and is not restated here.

## Status

This is a design document. What exists today is `EXECUTION_PLAN.md`'s Status table, the only
place in Blackjack's docs kept current. `UI_SPEC.md` and `TODO.md` are not yet written; they are
that plan's first package.

## The product

Blackjack, offline, Android only, no networking or analytics — the platform constraints,
unchanged. One app, one game, the fourth in the family and the first that is not a solitaire.

Play against a simulated dealer that follows one fixed, published rule (stand on soft 17, no
deviation, ever) for a stake of play chips only — a persistent bankroll with no real-money
purchase, cash-out, or wagering of any kind, consistent with `docs/PLATFORM.md`'s
no-accounts/no-networking constraints and this repo's noncommercial license. A round is won or
lost the moment it settles; there is no larger "the game" to win the way a solitaire deal is won —
only a bankroll that goes up, down, or below the table minimum, at which point the player is
offered a reset rather than a paywall (`RULES.md` "Round Lifecycle").

## What Blackjack is not

Blackjack is the test that `docs/ARCHITECTURE.md`'s split is real: it is the first game in this
repo built around chance the rules deliberately preserve, rather than eliminate.

- **No certified deal, no catalog, no solver.** A solitaire deal is a puzzle the player either
  solves or doesn't, so the solitaires here certify their deals offline wherever they can, and say
  so in the interface wherever a level ships uncertified. Blackjack has no such invariant to
  certify — the dealer's hole card and every subsequent draw are unknown to the player by design,
  and no amount of offline solving changes that. `solitaire/catalog` and `solitaire/ui` are
  skipped entirely, matching `docs/ARCHITECTURE.md`'s "Adding a game" recipe for a non-solitaire,
  and there is no `games/blackjack/solver` and no `DEALS.md`.
- **No tableau, no foundation, no build rule.** `docs/solitaire/GLOSSARY.md` is a solitaire
  vocabulary and none of it applies here; Blackjack uses its own, standard casino-game vocabulary
  (hand, bust, push, shoe, hole card, up card) that needs no glossary of its own because it is the
  only non-solitaire game in the repo so far.
- **A round, not a deal, is the unit of play.** Where a solitaire deal is dealt once and is won,
  lost, or abandoned, a Blackjack round settles in under a minute and is immediately followed by
  another against the same bankroll. "New Round" (`RULES.md` "Round Lifecycle") names the round,
  not a session that spans many of them — there is no session-level win, no Replay, and no
  abandoning a round part-way.
- **Automatic dealer play is not a convenience — it is the rule.** Klondike's automatic foundation
  moves are an optional cascade the player could in principle do by hand; Blackjack's dealer draws
  under a fixed rule the player never controls and could never opt out of. It is not gated by any
  "automatic moves" setting, because there is no manual alternative — it is not automation
  standing in for the player, it is the house's own turn.
- **No undo.** In a solitaire, a card that undo lets the player see again is part of a puzzle they
  are solving anyway. In Blackjack the card *is* the outcome of the bet: the shoe is fixed for the
  round (`RULES.md` "The Shoe"), so a player who hits, sees a ten, and undoes to stand knows the
  dealer draws that ten. Every Blackjack decision draws or reveals a card, so there is no safe
  subset to keep (`RULES.md` "Round Lifecycle"). The repo-wide undo invariants therefore have
  nothing to apply to here. The same reasoning rules out abandoning a round part-way, and requires
  a round to be saved before any card it draws is shown (`Persistence`).

## Interaction

Nothing here is tapped or dragged the way a solitaire card is — Blackjack deals every card to a
fixed position (the player's hand, a split hand, the dealer's hand) and the player never chooses
where a card goes. Play is a sequence of decisions offered through the action bar (`core/ui`'s
`BoardActionBar`, reused rather than reinvented for its touch-target shape and fixed-line labels —
`docs/ARCHITECTURE.md` "Moved later"):

- **Bet** — a stepper in 10-chip steps adjusts the bet between rounds; fixed once the round starts
  (`RULES.md` "Betting").
- **Deal** / **New Round** — starts a round at the selected bet from a freshly shuffled shoe
  (`RULES.md` "The Shoe"). Offered only when no round is in progress; replaced by the bankroll reset
  when the bankroll is below the table minimum. No Replay.
- **Take Insurance, Decline Insurance** — offered alone, at the insurance decision point only
  (`RULES.md` "Order of Play").
- **Hit, Stand, Double, Split** — offered only when each is currently legal for the active hand
  (`RULES.md` "Legal Actions"). An illegal action is never shown rather than shown and refused —
  the same "never refuse a legal move, never offer an illegal one" spirit Klondike's tap resolution
  follows, adapted from a tap destination to a button's presence.
- **Hint** — during any decision (`Hint`, below).
- No **Undo** (`What Blackjack is not`).

### Hint

Blackjack's Hint is a basic-strategy lookup, not a search. It is conventional total-dependent basic
strategy for the rules in `RULES.md` "Table Rules" — six decks, dealer stands on soft 17, dealer
peeks, double on any two cards, double after split, resplit to four hands, split Aces once with one
card each, no surrender — computed once, offline, into a fixed table. If any rule the table depends
on changes, the table must be regenerated: it is a derived artifact of `RULES.md`, not an
independent design surface.

- **Inputs.** The dealer's up card, and the active hand classified as a pair (two cards of equal
  rank, while Split is legal), a soft total, or a hard total (`RULES.md` "Hand Values"). Nothing
  else — not the exact cards behind a total, and not other hands at the table.
- **What it is not.** Not the exact best play. A fresh shoe every round means nothing from earlier
  rounds matters, but cards already seen this round still shift the odds slightly, and a strategy
  keyed on totals ignores that; in a few close cases a play that uses the exact cards does
  marginally better. Total-dependent basic strategy is the published standard, and its loss against
  exact play is a small fraction of a percent. Unlike Klondike's or FreeCell's hint it neither proves
  a line to a win nor claims one: the dealer's hidden cards decide the round.
- **Never an action that is not offered.** Every table entry resolves to a legal action
  (`RULES.md` "Legal Actions"):
  - A double entry says what to do when Double is not offered — the hand holds more than two cards,
    is a split Ace, or the available bankroll cannot cover it: hit, or, for the soft totals where
    the table says so, stand.
  - A pair whose Split is not offered — three splits already made, or the available bankroll cannot
    cover another bet — is looked up as its total instead: A–A as soft 12, any other pair as its hard
    total.
- **Insurance** is always declined. It pays only when the hole card is a ten-value card, which with
  a fresh six-deck shoe and three cards seen is always less likely than the one in three at which it
  would break even, whatever the player's hand.

## Scoring and statistics

There is no move count and no elapsed time — a round is not solved, it is settled, and there is no
timer (`RULES.md` "Round Lifecycle"). The bankroll itself is the running score: shown live, with
the chip delta of the last settlement highlighted.

Statistics track hands played, won, lost, and pushed (each split hand counts as a hand), player
blackjacks, the bankroll's current value, its high-water mark, lifetime net chips, and resets, kept
as one pool with no difficulty axis to split by — the same "one pool" shape
`docs/games/freecell/DESIGN.md` "Scoring and statistics" chose for FreeCell. A bankroll reset
leaves them all in place.

- **Lifetime net chips** is the sum of every settled round's chip delta, insurance included. It may
  be negative, and it is the only figure that still shows what was won or lost once the bankroll has
  been reset. It is kept as its own running sum rather than derived from the bankroll and the reset
  count, because a reset tops up from whatever was left — anything from 0 to 5 chips — so the chips a
  reset adds are not a fixed amount.
- **Resets** counts bankroll resets (`RULES.md` "Round Lifecycle"). A reset changes neither lifetime
  net chips nor the high-water mark.

They do not use `core/session`'s `SessionStatistics`/`computeSessionStatistics`. Its win, loss, and
streak fields are game-independent, but its outcome is a boolean `isWin`, and a push is neither;
counting pushes as losses would misstate every rate and streak built on it. Its best-time,
best-moves, and distribution fields have nothing to measure. Per `docs/ARCHITECTURE.md`'s own rule
this is one example, not a settled finding: re-check it if a second game with a three-way outcome
appears.

## Architecture

Two modules, as `docs/ARCHITECTURE.md` "Adding a game" prescribes for a non-solitaire:

- `games/blackjack/rules` — the shoe, the order of play, legal actions, the dealer's fixed play,
  settlement, and the one reducer. Kotlin/JVM, depends only on `core/cards` and `core/session`, no
  Android.
- `games/blackjack/app` — the Android application, depending on `core/storage`, `core/ui`, and
  `games/blackjack/rules`.

No `games/blackjack/solver`, ever — there is nothing for one to certify (see "What Blackjack is
not"). `solitaire/catalog` and `solitaire/ui` are not dependencies, for the same reason.

`BlackjackState` is its own type — a bet, the seed and shoe position, the dealer's hand with its
hole card's visibility tracked separately from its value, one to four player hands with their
stakes, the insurance stake, and the step of `RULES.md` "Order of Play" the round is at. The
bankroll is not part of it (`Persistence`). Nothing about it is shared with any solitaire
`GameState`, and per `docs/ARCHITECTURE.md`'s own rule this is a finding to re-check once a second
non-solitaire exists, not one to treat as settled from a single example.

`core/session`'s `Session<S, E>` still fits for its commit-and-log half: a state change and its log
entry happen together, and replay rebuilds the round from the log. Its undo half goes unused. The
log records player decisions only — `Hit`, `Stand`, `Double`, `Split`, `TakeInsurance`,
`DeclineInsurance`. The deal, the peek, the dealer's play, and settlement are consequences the
reducer derives from those decisions and the seed, so replay reproduces them rather than reading
them back.

## Persistence

`docs/PLATFORM.md` "Persistence" applies unchanged; what is particular to Blackjack is that there
are two things to save, and that a saved round must never trail what the player has seen.

- **The round in progress** is saved through `core/storage`'s `ActiveGameRecordStore<T>` and
  restored by replaying its log, like a solitaire deal. The bet is the round's `DealParameters<T>`:
  fixed at Deal, and not recoverable from the seed. There is no catalog and no timer, so
  `catalogVersion` and `elapsedMillis` are always 0.
- **The bankroll and statistics** outlive every round and must survive a relaunch with no round
  active, so they share a small store of their own, closer in shape to a persisted setting than to a
  deal parameter. It holds the bankroll, every statistic (`Scoring and statistics`), and the seed of
  the last round it settled, and it is only ever written as a whole, in one atomic write.
- **Written before shown.** Deal, and every decision that draws or reveals a card, awaits its save
  before the card is displayed. Otherwise killing the app between seeing a card and the save landing
  would restore the round from before that card was drawn — undo by force-stop.
- **Settled exactly once.** Stakes are never deducted from the saved bankroll during a round; the
  available bankroll is derived from the round (`RULES.md` "Betting"). Settlement writes the new
  bankroll, the updated statistics, and the round's seed together in that one write, then clears the
  round. On restore, a round whose seed matches the last settled seed has already been recorded and
  is discarded, so a kill between the two writes neither pays nor counts twice, and loses neither
  the result nor its statistics. A bankroll reset is the same kind of write.
- A corrupt or unreadable round save is discarded as `docs/PLATFORM.md` requires. Because its stakes
  were never deducted, the round is void and the bankroll is unaffected.

## Interface

`UI_SPEC.md` is not yet written; this is the shape the interface follows once it is. One dealer
hand across the top of the board, one to four player hands (a split fans them) across the bottom,
the bankroll and current bet always visible, and the action bar in the same position and
touch-target shape `core/ui`'s `BoardActionBar` already gives every other game. At most five
actions are offered at once (Hit, Stand, Double, Split, Hint); `UI_SPEC.md` must confirm those fit
the bar's equal-width portrait buttons at the platform's touch-target minimum.

## Sound, Localization, Accessibility, Performance, Quality

Mechanism, gating, and the shared bar: `docs/PLATFORM.md` "Sound", "Localization",
"Accessibility", "Performance", and "Quality". Blackjack ships only its own strings; the rest
merge in from `core/ui`.

## Supporting Specifications

- [RULES.md](RULES.md): hand values, table rules, betting, the shoe, order of play, legal actions, the dealer's
  play, settlement, and round lifecycle
- [EXECUTION_PLAN.md](EXECUTION_PLAN.md): work packages B0–B8 and their gates
- `UI_SPEC.md`, `TODO.md`: not yet written (`EXECUTION_PLAN.md` B0)
