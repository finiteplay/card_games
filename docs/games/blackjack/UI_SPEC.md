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
| **Idle** — before the first Deal, or after Reset | Empty felt; the bet stepper | Settings, Bet −, Bet +, Deal |
| **Insurance** | Four cards dealt, hole face down | Settings, Insure, Decline |
| **Playing** | The hands; the active hand marked | Settings, then each of Hit, Stand, Double, Split that is legal |
| **Settled** | Every card turned over; each hand's result; the round's net | Settings, Bet −, Bet +, Next round (or Reset) |
| **Loading** | A progress indicator; nothing touchable | none |

The Hint action (`DESIGN.md` "Hint") arrives with `EXECUTION_PLAN.md` B6 and is not in the first
release's bar; every number below holds with it added, since the widest phase is six buttons with it
and five without.

Back closes an open panel or dialog first. With none open it leaves the app, and a round in progress
is already saved, so leaving costs nothing and restores exactly (`DESIGN.md` "Persistence").

## Layout

### Status row

The title at the leading edge; centred in the space it leaves, two lines — the bankroll on the first
(`Chips 1,000`), the selected or staked bet on the second (`Bet 100`, with the last settlement's
chip delta appended once settled, `+150` in the positive accent or `−100` in the negative one; the
sign is always printed, so colour is never the only cue). Help and Statistics are icons in the
trailing corner, as in the other games. The row reserves two lines whatever the translation.

### Table, portrait

Top to bottom: the status row; the **dealer** area; the **player** area; the bet stepper's value
(Idle and Settled only); the action bar.

- The dealer area holds one hand, centred, a total badge beneath it. While the hole card is face
  down the badge shows the up card's value alone.
- The player area holds one to four hands in equal-width columns, left to right in play order. Each
  hand has its cards, a total badge (`17`, `Soft 17`, `Bust`, `Blackjack`), its stake, and — once
  settled — its result (`Win +100`). The **active hand** is marked by a bright underline the width
  of its column and a stronger total badge; a hand that is complete and not yet settled is dimmed.
- The two areas split the height remaining between the status row and the action bar, the player
  area taking the larger share, since it is the one that grows.

### Table, landscape

The status row spans the width. Below it the dealer area takes the leading 38% of the table's width
and the player area the other 62%, side by side; at 640 dp wide the table is about 576 dp (the rail
takes the rest), so a four-hand split gets columns of about 85 dp, wider than portrait's 76 dp at
320 dp. Narrower landscape windows keep the 40 dp card floor and let the fan overflow as in portrait.
The action bar is a rail one button wide at the edge nearest the holding hand
(`Handedness`), `core/ui`'s `BoardActionBar` in its landscape form. Handedness mirrors the bar's
order in portrait and its side in landscape and changes nothing else.

## Action Bar

`core/ui`'s `BoardActionBar`, reused for its touch-target shape and fixed two-line labels
(`docs/ARCHITECTURE.md` "Moved later"). Buttons are equal-width and an illegal action is never
shown rather than shown disabled (`RULES.md` "Legal Actions"), so the bar's contents are exactly
`legalDecisions` plus the fixed Settings button.

| Phase | Buttons | Count |
|---|---|---|
| Idle | Settings · Bet − · Bet + · Deal | 4 |
| Insurance | Settings · Insure · Decline | 3 |
| Playing, widest | Settings · Hit · Stand · Double · Split | 5 |
| Settled | Settings · Bet − · Bet + · Next round, or Settings · Reset | 4 / 2 |

**The widest phase fits.** Five equal buttons at 320 dp are 64 dp each before the bar's padding —
over the platform's 48 dp minimum (`docs/PLATFORM.md` "Accessibility"), and the same width Klondike's
five-action bar gives its buttons. With Hint (B6) it is six at 53 dp, still over 48. Labels are one
word where a word exists (`Hit`, `Stand`, `Double`, `Split`, `Insure`, `Decline`) and wrap inside
their two reserved lines where a translation needs it, so a long one never widens its own button.

The bet is changed in 10-chip steps, `Bet −10` and `Bet +10`, clamped to the table minimum and to
what the bankroll covers (`RULES.md` "Betting"); a button at its limit is shown disabled rather than
removed, because Idle and Settled have a fixed shape and the player is already looking at it.

## Geometry

Cards are content, not controls, and are exempt from the 48 dp minimum; this section is the
minimum card width and the exposed face-up band the platform requires of every game's spec.

- Card aspect: width : height = 5 : 7.
- **Card width.** One hand: `min(64 dp, available width ÷ 5)`, floor **40 dp**. Two to four hands:
  `min(56 dp, column width × 0.5)`, floor **40 dp**. At 320 dp with four hands the columns are 76 dp
  (after the 8 dp side margins) and the cards 40 dp; at 360 dp the columns are 86 dp and the cards 43 dp.
- **Fan.** Cards in a hand overlap horizontally. The step between card left edges is
  `min(card width × 0.5, (hand width − card width) ÷ (cards − 1))`, floored at **6 dp**. The step is
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
- Text scales with the system font size; card faces scale with the card (`docs/PLATFORM.md`
  "Accessibility").

## Settlement Presentation

Settlement is a transaction (`RULES.md` "Round Lifecycle") and is presented as a result, not a win
dialog — there is no larger game that has been won.

- Nothing modal. The table stays in place, every card face up, and each hand's result sits beneath
  its total badge: `Blackjack +150`, `Win +100`, `Push`, `Lose −100`, `Bust −100`. A doubled hand
  shows its doubled stake.
- A summary line above the action bar states the round's net — `You won 150`, `You lost 100`, or
  `Even` — and, when insurance was taken, its own result (`Insurance paid 100` / `Insurance lost
  50`). The status row shows the same net as its signed delta.
- The dealer's draws are paced (**Motion**, below). Results appear only after the dealer's last card
  has been shown; a player's bust is the exception only in that the bust badge appears at once,
  since the hand is over at that card (`RULES.md` "Settlement").
- A win plays the shared win sound when the net is positive. Pushes and losses are silent.

## The Reset Offer

When a round settles with the bankroll below the table minimum, **Reset** replaces Next round in the
action bar, and a dialog opens once: *Out of chips* — *You can't cover the minimum bet. Reset to
1,000 chips? Your statistics are kept.* with Reset and Not now. Declining leaves the settled table in
place with Reset in the bar; there is no other way forward, so it stays until used — a way out,
never a dead end (`RULES.md` "Round Lifecycle"). Reset sets the bankroll to 1,000 and the bet to 10
and is counted in Statistics.

## Statistics

`core/ui`'s `FullScreenPanel` with the shared tile grid (`StatTileGrid`): hands played, won, lost,
pushed, player blackjacks, bankroll, its high-water mark, lifetime net chips and resets, as one pool
with no tabs — there is no axis to split by and no period filter, because the figures are lifetime
and a bankroll has no "this week" (`DESIGN.md` "Scoring and statistics"). Reset requires
confirmation and clears nothing but the statistics and bankroll history it names; the bankroll
itself is kept.

## Help

`FullScreenPanel`, titled *How to play*, five short sections in player-facing strings, all
localized: the goal; a round's decisions; double and split; insurance and the dealer's rule; chips
and what happens when they run out.

## Settings

Skip animations, handedness, sound, theme, language — in the same groups and with the same shared
rows as the other games — and the version label last. No automatic-moves setting
(`DESIGN.md` "What Blackjack is not"), and no rest reminder in the first release (`TODO.md`).

## Motion

- **Dealer's draws are paced.** After the player's last decision the hole card turns over, then each
  further dealer card appears one at a time, 600 ms apart, and results follow 400 ms after the last.
  Skip Animations (or system reduced motion) shortens each step to 150 ms — the draws stay visibly
  discrete, never collapsed into one frame (`EXECUTION_PLAN.md` B7).
- Card *flights* from the shoe, and the hole-card flip as an animation rather than a step, are B7's
  and are not in the first release (`TODO.md`).

## Accessibility

Contrast, touch-target, suit-symbol and font-scaling rules are `docs/PLATFORM.md`'s. Outcomes are
spelled in words as well as signed numbers; no state is conveyed by colour alone.
