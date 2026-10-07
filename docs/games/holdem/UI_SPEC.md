# Texas Hold'em — Interface Specification

Publisher: [FinitePlay LLC](https://finiteplay.org)

Layouts, controls, geometry and presentation. `DESIGN.md` says what the game is and `RULES.md` how
it plays; `docs/PLATFORM.md` is everything true of every game and is not restated here.
`EXECUTION_PLAN.md` H0 is this file's package; H3's geometry test is what verifies its numbers, and
this file is corrected if that test disagrees.

Where a piece of the interface is Blackjack's (`docs/games/blackjack/UI_SPEC.md`) — the status row's
shape, the action bar, the result mark, chip stacks, the panels — it is reused, not redrawn, and
this file says only what differs (`EXECUTION_PLAN.md` H3, the second-non-solitaire re-check).

## Screens and States

One screen. The table is always in exactly one of these states:

| State | Table | Action bar |
|---|---|---|
| **Opponent's turn** | The acting seat ringed; every other seat's last action on this street | Settings |
| **Your turn** | Your seat ringed; the sizing row above the bar when Bet or Raise is offered | Settings, the legal actions, Hint |
| **Showdown** | Live hands face up, winning cards raised, pots awarded one at a time | Settings |
| **Hand over** | The result mark if you were in the hand; the winners' `+` amounts | Settings, Next hand |
| **Tournament over** | Your finishing place, or the win | Settings, New tournament |
| **Loading** | A progress indicator; nothing touchable | none |

Back closes an open panel or dialog first. With none open it leaves the app; the hand in progress is
already saved, so leaving the app costs nothing and restores exactly (`DESIGN.md` "Persistence").
Leaving the app is not leaving the tournament (`Leave Tournament`).

When a saved hand cannot be read it is replayed from its start (`DESIGN.md` "Persistence"), and a
dismissible banner under the status row says so: *Your saved hand couldn't be opened, so it was
dealt again from the start. No chips were lost.*

## Layout

### Status row

The row at the top, Blackjack's shape. At the leading edge, two lines: the blinds in bold
(`Blinds 50 / 100`) and beneath them the hand number and the next level (`Hand 23 · 75 / 150 in 7
hands`; at the last scheduled level, the doubling it will take). In the trailing corner, as icons:
Help, Statistics, and — while a tournament is in progress — Leave (a flag).

### Seats

Each opponent seat is a rounded box: an **avatar** (a drawn circle in the profile's colour with the
name's initial — never an asset), the **name** (one line, ellipsized), its **style** word in small
type (`tight`, `loose`, `aggressive`, `passive`), and its **stack**. Below them, while the seat is in
the hand, two card backs. Markers sit on the box's corner nearest the table's centre: the dealer
button (`D`), and `SB` / `BB` for the blinds of this hand.

- **The seat's bet** on this street is a small pill — a chip and the figure — straddling the box's
  table-facing edge, so it adds no height. At the street's end it joins the pot.
- **The seat's last action** on this street (`Raise 120`, `Call`, `Check`, `Fold`, `All in`)
  replaces the style word until the street ends. A folded seat is dimmed to 50% and drops its card
  backs.
- **All in** is shown on the stack line in place of `0`.
- **An eliminated seat** stays where it was — the table's shape never changes mid-tournament — greyed
  and reading `Out · 5th`.

Your own seat, at the bottom centre, has no box: your two cards face up and large, and beneath them
one line with your stack and your markers. Your bet pill sits above your cards.

### Table, portrait

Top to bottom: the status row; the **table**; the **sizing row** when Bet or Raise is offered; the
action bar. The hint notice, when shown, is drawn over the table just above your cards, in the gap
between the board band and your seat, so it takes no height of its own (`Hint Presentation`).

The table is four bands:

1. **Top band** — three opponent seats, left to right.
2. **Middle band** — an opponent seat at each edge and, between them, the **pot**: `Pot 1,240` in
   large type, and while side pots exist a second line, `3 pots`.
3. **Board band** — the five board cards, centred, with empty outlines for cards not yet dealt.
4. **Your seat.**

Seats run clockwise from you, as at a real table seen from above: middle-left, top-left, top-centre,
top-right, middle-right. The bands keep their order at every height; spare height goes to the gaps
between them, evenly.

### Table, landscape

The status row spans the width. Below it, the table, then the sizing column when Bet or Raise is
offered, then the action bar as a rail one button wide at the edge nearest the holding hand
(`core/ui`'s `BoardActionBar` in its landscape form). The sizing column is the sizing row's five
controls stacked, the rail's width.

The table is three bands: the top band of three seats; a middle band with an opponent seat at each
edge and the board between them, the pot line under the board; and your seat. Handedness mirrors
the bar's order in portrait and its side in landscape and changes nothing else.

## Action Bar

`core/ui`'s `BoardActionBar`, as in Blackjack: equal-width buttons with two fixed label lines, a
60 dp touch target, Settings first, and an illegal action never shown rather than shown disabled
(`RULES.md` "Legal Actions"). Hint is last whenever it is your turn.

| State | Buttons | Count |
|---|---|---|
| Opponent's turn, Showdown | Settings | 1 |
| Your turn, nothing to call | Settings · Check · Bet · All in · Hint | 5 |
| Your turn, facing a bet | Settings · Fold · Call 40 · Raise · All in · Hint | 6 |
| Facing a bet you can only call | Settings · Fold · Call 40 · Hint | 4 |
| Facing a bet you cannot cover | Settings · Fold · Call all in · Hint | 4 |
| Hand over | Settings · Next hand | 2 |
| Tournament over | Settings · New tournament | 2 |

- **Labels carry their amounts:** `Call 40`, `Bet 60`, `Raise to 120`, `All in 1,240`, so the button
  pressed is the amount committed. Bet and Raise read the sizing row's current amount. Where a label
  and its figure need both lines, the figure takes the second.
- **The widest state fits.** Six equal buttons across 320 dp, less the bar's 8 dp padding on each
  side, are about 50 dp each — over the platform's 48 dp minimum (`docs/PLATFORM.md`
  "Accessibility"), the same arithmetic as Blackjack's widest phase.
- **Next hand** is offered once the last pot has been awarded. Pressing it deals at once; otherwise
  the next hand deals by itself after the pause in `Motion`.

### The sizing row

Shown above the bar whenever Bet or Raise is offered, and only then. Five equal controls: **−** · the
**amount** · **+** · **½ pot** · **Pot**. Across 320 dp they are about 60 dp each.

- **− and +** step by the big blind and repeat while held, Blackjack's bet stepper exactly
  (`BoardAction.repeatOnHold`). Each is disabled at its end of the legal range.
- **The amount** is a figure, not a text field: there is no keyboard. It is the bet, or the raise's
  total, always within the legal range (`RULES.md` "Betting"). Tapping it does nothing.
- **½ pot** and **Pot** set the amount. For a bet, half the pot or the pot. For a raise, the call
  plus half of — or all of — the pot once the call is in it, so `Pot` is the standard pot-sized raise.
  Each is rounded down to a whole chip and clamped to the legal range, and a preset that clamps to
  the whole stack sets the amount to all in.
- **The default amount**, each time the row opens: before the flop with no raise yet, three big
  blinds; before the flop facing a raise, three times its total; after the flop, the ½-pot preset.
  Clamped like the presets.

## Geometry

Cards are content, not controls, and are exempt from the 48 dp minimum; this section is the minimum
card width the platform requires of every game's spec. Card aspect is 5 : 7, as in the other games.
Every face-up card's corner index is fully visible — Hold'em never fans more than two cards — so
there is no fan-step floor.

The **regular** layout needs a table at least 408 dp tall in portrait; below that the **compact**
layout applies, down to 336 dp. Heights are after the status row (44 dp), the sizing row (52 dp)
and the bar (76 dp), with the sizing row always reserved so the table does not jump when it appears:
a 320 × 580 dp window gets the regular table, and a 320 × 508 dp window the compact one.

| | Regular | Compact |
|---|---|---|
| Opponent seat box width | `min(112, (area − 16) ÷ 3)`: 96 dp at 320, 109 dp at 360 | the same |
| Opponent seat box height | 100 dp: name row 28, stack 16, card row 56 | 76 dp: name row 28, stack 16, card row 32 |
| Opponent card backs | 40 dp | 22 dp |
| Board cards | `min(52, (area − 16) ÷ 5)`, floor 40: 52 dp | the same |
| Your cards | 64 dp, the second 70% of a card to the right of the first | 52 dp, the same overlap |
| Gaps between bands | the spare height, evenly, at least 8 dp | at least 6 dp |
| Total at the minimum | 100 + 100 + 73 + 110 + 24 = 407 dp | 76 + 76 + 73 + 93 + 18 = 336 dp |

*area* is the window width less 8 dp margins on each side: 304 dp at 320, 344 dp at 360.

- **An opponent's cards at showdown** are face up at 40 dp — the card floor, the same as Blackjack's
  — two side by side with a 70% step, 68 dp wide, inside a 96 dp box. In the compact layout they are
  taller than the backs they replace, so they cover the stack line and the bottom of the name row
  while shown; the stack reappears with the next hand.
- **Landscape** at 640 × 360 dp: the rail is 76 dp and the sizing column 76 dp, leaving 488 dp for
  the table. The middle band holds two 96 dp seats and the 276 dp board, 468 dp in all. The table's
  height, 316 dp under the status row, takes regular seats and your cards at the compact 52 dp, the
  board and the pot line (93 dp) fitting inside the middle band: 100 + 100 + 93 + 16 = 309 dp.
- Text scales with the system font size; card faces scale with the card (`docs/PLATFORM.md`
  "Accessibility"). A name that no longer fits is ellipsized; a stack figure never is — the style
  word gives way first.

## Opponents' Turns

An opponent's decision is computed at once and shown after a pause (`DESIGN.md` "Interaction"),
so the player can follow the hand:

- The acting seat's ring pulses while it "thinks"; under reduced motion the ring is steady.
- Then its action appears on its seat, its bet pill updates, and the action is announced to
  accessibility services (`Accessibility`).
- Nothing is touchable on the table in the meantime, and the bar holds only Settings — there are no
  pre-action buttons in the first release (`TODO.md`).

## Hint Presentation

Blackjack's, extended by what a poker hint has to say (`DESIGN.md` "Hint"):

- **The suggested button is highlighted** in the bar (`BoardAction.highlighted`; read as
  *Suggested*). When the suggestion is a bet or a raise, the sizing row is set to its amount.
- **A one-line notice** over the table, just above your cards, reads `Equity 42% · Pot odds 25%`: the hand's estimated
  share of the pot and the share of the pot a call would be. With nothing to call it reads the equity
  alone. The words *estimated* and *vs. likely hands* are in its spoken description.
- It stays until you act or the street changes; pressing Hint again hides it.
- Each Hint shown is counted (`Statistics`).

## Showdown and Results

- **Showdown.** Every live opponent's cards turn face up, one seat at a time clockwise from the last
  aggressor. The **winning five cards** of each pot's winner — from the hole and the board — are
  raised 6 dp with a gold outline; every other card on the table dims to 60%. Under each shown hand
  its name: `Flush, king high`, `Two pair, aces and sevens`, `Straight, five high`.
- **Pots are awarded one at a time**, the main pot last (`RULES.md` "Pots and Showdown"). A banner
  over the board names each — `Side pot · 600` — and the winners' seats show `+600`. A split pot
  shows each share on its own seat, the odd chip included.
- **Won without a showdown.** The last seat in shows `+` its winnings; no cards are turned.
- **Your result mark.** Blackjack's result mark, in the middle of the board band, when you were in
  the hand at its end, from your net for the hand: the trophy and `+` the net when you came out
  ahead, the equals sign and `0` when a split pot gave back exactly what you put in, the dissatisfied
  face and `−` the net when you lost at showdown. A hand you folded gets no mark: the chips it cost were yours to
  give up. The mark is not modal and stays until the next hand.
- Confetti on a pot you won outright at showdown, more for a pot of over half the chips in play; a
  lost all-in washes the table red briefly. Skip Animations leaves just the mark.
- **Cues**, all `core/ui`'s existing ones. Each hand opens with the shuffle cue and each card dealt
  plays the deal cue. Check plays the confirm cue; Call, Bet and Raise the chip cue; All in the
  wager-commit cue; Fold the soft move cue; Hint the hint cue. Opponents' actions play the same cues
  as yours. A pot you win plays the win sound, one over half the chips in play the natural-win
  celebration; a lost all-in plays the round-loss cue.

### Elimination and the end

- **An opponent knocked out**: its seat greys and reads `Out · 5th` as the hand ends.
- **You are knocked out**: when the hand's results have been shown, a dialog — *You finished 4th* —
  with the hands you played and your largest pot, and New tournament, Statistics and Close. Closing
  leaves the table as it ended, with New tournament in the bar.
- **You win the tournament**: the family's win celebration and a dialog — *You won the tournament* —
  with the hands it took, and the same three buttons.
- **The cue**: the win sound for a tournament won; the game-over cue for being knocked out.

## Leave Tournament

The flag in the status row asks first: *Leave this tournament? You'll finish 4th, as if you were
knocked out now.* with Leave and Stay; Back is Stay. Leaving ends the tournament at once, with the
same dialog as being knocked out (`RULES.md` "Leaving").

## Statistics

`core/ui`'s `FullScreenPanel` with the shared section cards (`StatSectionCard`, 12 dp apart) — one
pool, no tabs, no period filter, as in Blackjack:

- **Tournaments** — played, won, win rate, average finish, current streak, best streak.
- **Finishes** — one bar per place, 1st to 6th, each with its count; Hold'em's own section in
  `StatisticsBody`'s `middle` slot (`DESIGN.md` "Scoring and statistics").
- **Hands** — hands played, hands won, showdowns won of reached (`12 / 30`), largest pot, voluntarily
  in before the flop (%), raised before the flop (%), hints used.

*Reset Statistics* asks first, with the shared confirmation, and clears every figure. It does not
touch a tournament in progress.

## Help

`FullScreenPanel`, titled *How to play*, with three tabs, all in localized player-facing strings,
each topic a short section of its own:

- **Rules** — the goal; a hand: your cards, the flop, the turn, the river; the actions; the button
  and the blinds; all in and side pots; the showdown; the tournament and its rising blinds; leaving.
- **Hands** — the nine hand rankings, highest first, each with five drawn cards as an example and one
  line saying what it is; then how ties are broken, and that suits never break them.
- **Strategy** — a short introduction and seven plain tips for a new player: play fewer hands from
  early seats; raise rather than call; fold more than feels comfortable; what pot odds are; position;
  watch the opponents' styles; with a short stack, all in or fold. Words, not the Hint.

The publisher's byline closes each.

## Settings

Blackjack's three groups of shared rows: **Appearance** — left-handed, animations, sound, theme,
language; **Breaks** — the rest reminder and the current session's running total; **About** last.
No automatic-moves setting — opponents are other players, not automation (`DESIGN.md`). A break
blocks the table but changes nothing about the hand, which is saved and untimed.

## Motion

Every pause is presentation only; it never changes what happens.

| Step | Animations on | Skip Animations or reduced motion |
|---|---|---|
| A card's flight from the dealer's spot, cards dealt together staggered | 240 ms, 90 ms apart | the card is simply there |
| An opponent's pause before acting | 800 ms; 1,400 ms for a raise, an all-in, or a call of over a third of its stack | 250 ms |
| Bets gathering into the pot at a street's end | 300 ms | at once |
| Before the next street's cards | 500 ms | 200 ms |
| Each opponent's cards turning at showdown | 400 ms apart | 150 ms apart |
| Each pot sliding to its winner | 600 ms, the next 300 ms after | 250 ms apart, no slide |
| Before the next hand deals by itself | 3,000 ms after the last pot | 1,500 ms |

Opponents' actions stay discrete under Skip Animations: each still appears on its own, one after
another, never collapsed into one frame. The deal's flights, the gathering of bets, and the pots'
slides are H8's (`EXECUTION_PLAN.md`); until then those steps simply happen, with the pauses kept.

## Accessibility

Contrast, touch-target, suit-symbol and font-scaling rules are `docs/PLATFORM.md`'s. No state is
conveyed by colour alone: an action, a fold, an elimination and a result each has its words.

- **Each seat** is one node: *Ava, aggressive, 1,240 chips, dealer, bet 80*, then its last action
  on this street. A folded or eliminated seat says so first.
- **Your cards**, **the board** and **the pot** are one node each: *Your cards: ace of spades, king of
  spades*; *Board: queen of hearts, jack of spades, two of clubs*; *Pot 1,240, three pots*.
- **Every action is announced** as it happens, through a polite live region: *Ava raises to 120*,
  *Ben folds*, *The turn: ten of diamonds*. A showdown is announced hand by hand with the hand names,
  then each pot's winner.
- The sizing row's amount is read with its control: *Raise to 120*; − and + say what they step by.
