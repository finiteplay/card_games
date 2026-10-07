# Blackjack — Interface Specification

Publisher: [FinitePlay LLC](https://finiteplay.org)

Layouts, controls, geometry and presentation. `DESIGN.md` says what the game is and `RULES.md`
how it plays; `docs/PLATFORM.md` is everything true of every game and is not restated here.
`EXECUTION_PLAN.md` B0 is this file's package; B3's geometry test is what verifies its numbers, and
this file is corrected if that test disagrees.

## Screens and States

One screen. A round is always in exactly one of these states, and the screen shows only what is
true of it:

| State | Table | Action bar |
|---|---|---|
| **Idle** — before the first Deal, or after Reset | Empty felt: *Place your bet* above a betting circle holding the bet as a chip stack | Settings, Bet −10, Bet +10, Deal |
| **Insurance** | Four cards dealt, hole face down | Settings, Insure, Decline, Hint |
| **Playing** | The hands; the active hand marked | Settings, then each of Hit, Stand, Double, Split that is legal, then Hint |
| **Settled** | Every card turned over; the result mark (`Settlement Presentation`) | Settings, Bet −10, Bet +10, Next round — or Settings, Reset chips |
| **Loading** | A progress indicator; nothing touchable | none |

The Hint action (`DESIGN.md` "Hint") is the last button of the bar whenever a decision is on offer,
including insurance; the widest phase is therefore six buttons.

Back closes an open panel or dialog first. With none open it leaves the app, and a round in progress
is already saved, so leaving costs nothing and restores exactly (`DESIGN.md` "Persistence").

When a saved round cannot be restored it is voided (`DESIGN.md` "Persistence"), and a dismissible
banner under the status row says so: *Your saved round couldn't be opened. It was cancelled and no
chips were lost.*

## Layout

### Status row

The row at the top is the title at the leading edge, with Help and Statistics as icons in the
trailing corner, as in the other games. The bankroll and the bet are not in it: they are the **chips
HUD**, a row of two pills **directly above the action bar**, where the thumb already is. The
bankroll is a large gold-ringed pill at the leading edge (a coin and the figure), and the selected
or staked bet a second pill at the trailing edge (a stack of chips in the denominations the bet is
made of, and the figure), with the last settlement's chip delta beside it once settled, `+150` in the positive
accent or `−100` in the negative one; the sign is always printed, so colour is never the only cue.
The bankroll counts up or down to its new figure when a round's result is shown, and the bet pops
when it changes; under Skip Animations both simply change. The pills are dark and translucent, so
their white figures read on the felt in either theme. The HUD is one accessibility node that reads
as `Chips 1,000` / `Bet 100`, as the old two-line row did.

**Chip stacks** are drawn, never an asset: the fewest chips of the largest denominations (1, 5, 10,
50, 100, 500, 1,000 — the small ones because a blackjack pays 3:2 and a bankroll can end on any
figure), one column per denomination, each chip with a light rim so a dark one still reads on the
felt and on the dark pill. They are decorative — the amount is always printed
beside them. The bet's figure is stated **once**, in the HUD: the betting circle holds only
the chips, and a hand states its own stake under its cards only when the round is split, since a
split round has several stakes and the HUD shows their total.

### Table, portrait

Top to bottom: the status row (with the recovery banner under it when shown); the **dealer**
area; the **player** area; the chips HUD; the action bar.

- The dealer area holds one hand, centred, labelled *Dealer* above it and a total badge beneath it.
  While the hole card is face down the badge shows the up card's value alone.
- The player area holds one to four hands in equal-width columns, left to right in play order. Each
  hand has its cards and a total badge (`17`, `Soft 17`, `Bust`, `Blackjack`); in a split round it
  also states its own stake beneath, as a small chip stack and `Bet 20`. No hand prints a result
  figure (`Settlement Presentation`). The **active hand** is marked by a 3 dp underline the width
  of its hand and a larger, bold total badge; a hand that is complete and not yet settled is dimmed.
- The two areas split the height remaining between the status row and the HUD 1 : 1.3, the player
  area taking the larger share, since it is the one that grows.

### Table, landscape

The status row spans the width. Below it the dealer area takes the leading 38% of the table's width
and the player area the other 62%, side by side; at 640 dp wide the table is about 576 dp (the rail
takes the rest), so a four-hand split gets columns of about 85 dp, wider than portrait's 76 dp at
320 dp. Narrower landscape windows keep the 40 dp card floor and let the fan overflow as in portrait.
The chips HUD sits beneath the table, beside the rail. The action bar is a rail one button wide at
the edge nearest the holding hand
(`Handedness`), `core/ui`'s `BoardActionBar` in its landscape form. Handedness mirrors the bar's
order in portrait and its side in landscape and changes nothing else.

## Action Bar

`core/ui`'s `BoardActionBar`, reused for its touch-target shape and fixed two-line labels
(`docs/ARCHITECTURE.md` "Moved later"). Buttons are equal-width and an illegal action is never
shown rather than shown disabled (`RULES.md` "Legal Actions"), so the bar's contents are exactly
`legalDecisions` plus the fixed Settings button.

Bet − and + repeat while held: one step on the press, a second after about 0.4 s, then faster the longer
the finger stays, until it lifts or the bet reaches its limit. A tap is still one step, and
accessibility services get an ordinary click.

| Phase | Buttons | Count |
|---|---|---|
| Idle | Settings · Bet −10 · Bet +10 · Deal | 4 |
| Insurance | Settings · Insure · Decline · Hint | 4 |
| Playing, widest | Settings · Hit · Stand · Double · Split · Hint | 6 |
| Settled | Settings · Bet −10 · Bet +10 · Next round, or Settings · Reset chips | 4 / 2 |

**The widest phase fits.** Six equal buttons at 320 dp are 53 dp each before the bar's padding —
over the platform's 48 dp minimum (`docs/PLATFORM.md` "Accessibility"). Labels are one
word where a word exists (`Hit`, `Stand`, `Double`, `Split`, `Insure`, `Decline`) and wrap inside
their two reserved lines where a translation needs it, so a long one never widens its own button.

The bet is changed in 10-chip steps, `Bet −10` and `Bet +10`, clamped to the table minimum and to
what the bankroll covers (`RULES.md` "Betting"); a button at its limit is shown disabled rather than
removed, because Idle and Settled have a fixed shape and the player is already looking at it.
Next round is disabled until the dealer's reveal has finished and the result is shown, so it cannot
skip past the result.

## Geometry

Cards are content, not controls, and are exempt from the 48 dp minimum; this section is the
minimum card width and the exposed face-up band the platform requires of every game's spec.

- Card aspect: width : height = 5 : 7.
- **Card width.** One hand: `min(64 dp, available width ÷ 5)`, floor **40 dp**. Two to four hands:
  `min(56 dp, column width × 0.5)`, floor **40 dp**. At 320 dp with four hands the columns are 76 dp
  (after the 8 dp side margins) and the cards 40 dp; at 360 dp the columns are 86 dp and the cards 43 dp.
- **Fan.** Cards in a hand overlap horizontally. The step between card left edges is
  `min(card width × 0.7, (hand width − card width) ÷ (cards − 1))`, floored at **6 dp**. The step is
  what exposes each covered card's corner index, so a floor under the corner's width would keep
  every index readable; 6 dp does not, and does not need to — the total badge states the hand, and
  only the last card's face is complete.
- **The longest hand six decks allow** is twenty-one cards, all Aces (`RULES.md` "Hand Values"). It
  is the only hand that long and always the sole hand, since a hand that long cannot have come from
  a split. At 320 dp, with 8 dp side margins, the width is 304 dp: the card is 60.8 dp (`304 ÷ 5`,
  under the 64 dp cap and over the floor), and the step `(304 − 60.8) ÷ 20 = 12.2 dp`, which keeps
  the corner index of every card but the last visible. At 360 dp the width is 344 dp, the card 64 dp
  and the step 14 dp.
- **Four split hands.** A split hand rarely passes five cards; at 40 dp cards in a 72 dp hand width (the 76 dp column less its 4 dp gap) the
  step is `(72 − 40) ÷ 4 = 8 dp` at five cards, and reaches the 6 dp floor at seven. Beyond what
  the floor lets fit, the fan simply runs past its column into the 4 dp gap and the hand's own
  neighbour covers it — drawn in play order, so the later hand is on top. The total badge, which
  does not overlap, carries the information.
- **The dealer's cards** use the one-hand width for the dealer area, but never more than 8 dp wider
  than the player's cards, so a four-hand split does not leave a dealer hand twice their size.
- Text scales with the system font size; card faces scale with the card (`docs/PLATFORM.md`
  "Accessibility").

## Hint Presentation

The table's answer is shown by **highlighting the button to press**, in the action bar itself — no
text line: the suggested button gets a filled, ringed background and bold type, and a screen reader
reads it as *Suggested* (`BoardAction.highlighted`, shared with every game's bar). It stays until the
round changes or a decision is made; pressing Hint again hides it. Nothing else is highlighted and nothing
is counted: there is no hints statistic, since the hint proves nothing (`DESIGN.md` "Hint").

## Settlement Presentation

Settlement is a transaction (`RULES.md` "Round Lifecycle") and is presented as a result, not a win
dialog — there is no larger game that has been won.

- **One result, stated once.** A single *result mark* sits in the middle of the table's lower half
  (in landscape, the middle of the table): a pictogram and the round's signed net, with no words —
  a trophy and `+10` for a win, a star and `+15` for a blackjack, a dissatisfied face and `−10` for
  a loss or a bust, an equals sign and `0` for a push or an even round. Which it is comes from the
  round's net, not any one hand: a split round that wins one hand and loses a bigger one is a loss,
  and a round that nets nothing without every hand pushing — one split hand won and another of the
  same stake lost — is even. The sign is always printed, so
  colour is never the only cue. When insurance was taken a small shield with its own signed result
  sits beneath. The words ("You win!", "Bust"…) are its spoken description only. It is not modal, and
  it stays until the next round.
- **Split hands carry a pictogram, never a figure.** Where a round has several hands the one mark cannot say which won, so each hand's total badge gets a small tick (won), cross (lost or bust) or equals sign (push) once results are shown. A single-hand round has none: the mark says it all. The signed net stays on the result mark alone.
- **Nowhere else.** The hands carry no per-hand result figure, there is no summary line, and the chips
  display shows no delta: the bankroll simply counts to its new figure.
- A win throws confetti from the mark, more for a blackjack; a loss washes the table red briefly.
  Skip Animations leaves just the mark.
- The dealer's draws are paced (**Motion**, below). The mark appears only after the dealer's last
  card has been shown; a player's bust is the exception only in that the hand's `Bust` total badge
  appears at once, since the hand is over at that card (`RULES.md` "Settlement").
- Deal and Next round play the shared shuffle cue. Hit, Double, Split and Hint each have a distinct
  short cue; Stand and Decline share a confirm cue, and Insure and each bet step a chip cue. Dealer
  card reveals use the deal cue. A hand that crosses 21 plays its bust
  cue immediately. An ordinary positive settlement plays the shared win sound, while a natural
  blackjack has its own casino-chip celebration. A losing round plays a descending loss cue; when
  the resulting bankroll cannot cover the minimum bet, a stronger out-of-chips cue replaces it.
  Pushes are silent.

## The Reset Offer

When a round settles with the bankroll below the table minimum, **Reset chips** replaces the bet
stepper and Next round in the action bar, and once the result is shown a dialog opens: *Out of
chips* — *You can't cover the minimum bet. Reset to 1,000 chips? Your statistics are kept.* with
Reset chips and Not now. Back is Not now. Declining leaves the settled table in
place with Reset in the bar; there is no other way forward, so it stays until used — a way out,
never a dead end (`RULES.md` "Round Lifecycle"). Reset sets the bankroll to 1,000 and the bet to 10
and is counted in Statistics.

## Statistics

`core/ui`'s `FullScreenPanel` with two of the shared section cards (`StatSectionCard`, 12 dp apart
as in the other games), each a tile grid (`StatTileGrid`):

- **Hands** — hands, wins, losses, pushes, blackjacks.
- **Chips** — bankroll, highest bankroll, net chips (signed), resets.

One pool with no tabs — there is no axis to split by and no period filter, because the figures are
lifetime and a bankroll has no "this week" (`DESIGN.md` "Scoring and statistics"). *Reset
Statistics* below them asks first, with the shared confirmation, and clears every figure; the
bankroll itself is kept and becomes the new highest bankroll.

## Help

`FullScreenPanel`, titled *How to play*, with two tabs, all in localized player-facing strings:

- **Rules** — ten short sections, each decision in a paragraph of its own: the goal; card values; a
  round; Hit; Stand; Double; Split; Insurance; the dealer's rule; chips and what happens when they
  run out.
- **Strategy** — a short introduction and seven plain tips for a new player. It is a summary in
  words, not the Hint's table.

The publisher's byline closes both.

## Settings

Three groups of the shared rows, as in the other games: **Appearance** — left-handed, animations,
sound, theme, language; **Breaks** — the rest reminder and the current session's running total;
and **About** last (`core/ui`'s `AboutSettingsGroup`) — the version, the website, the privacy policy
and the open-source acknowledgements. No
automatic-moves setting (`DESIGN.md` "What Blackjack is not"). The reminder's dialogs are the shared
ones; accepting a break blocks the board but touches no round, since a round is saved before every
card is shown and nothing about it is timed.

## Motion

- **Dealer's draws are paced.** After the player's last decision the hole card turns over, then each
  further dealer card appears one at a time, 600 ms apart, and results follow 400 ms after the last.
  Skip Animations (or system reduced motion) shortens each step to 150 ms — the draws stay visibly
  discrete, never collapsed into one frame (`EXECUTION_PLAN.md` B7).
- **The result banner and its celebration** are described in "Settlement Presentation"; they run
  only after the results are shown and respect the same Skip Animations and reduced-motion rule.
- **Card flights.** Every card that appears flies from the shoe — a point beyond the table's trailing
  top corner — to its place in 280 ms, in deal order, cards dealt together 140 ms apart; a card
  already down stays put. Under Skip Animations or reduced motion a card is simply there.
- **The bankroll** holds its figure from before the round until the result is shown, then counts to
  the new one; showing it earlier would give the result away.
- The hole card turns over as a step, not a flip animation (`TODO.md`).

## Accessibility

Contrast, touch-target, suit-symbol and font-scaling rules are `docs/PLATFORM.md`'s. Outcomes are
spelled in words as well as signed numbers; no state is conveyed by colour alone.
