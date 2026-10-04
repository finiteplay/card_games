# Klondike Strategy Notes

Reference material only, compiled from public strategy guides — **not** a spec.
Nothing here overrides or informs `docs/games/klondike/DESIGN.md`, `docs/games/klondike/RULES.md`, `docs/games/klondike/UI_SPEC.md`, or any other
file CLAUDE.md lists as authoritative; it exists purely as background reading (e.g.
for anyone drafting in-app tips or hint copy later). See "Sources" at the bottom for
where each idea came from.

Most guides below were written with three-card draw in mind (the more common
default elsewhere) and don't distinguish draw modes explicitly. Tips marked
**[weaker for draw-one]** are ones whose whole rationale is about information or
cards getting buried in groups of three — under this app's draw-one, with
unlimited stock recycling (`docs/games/klondike/RULES.md` "Draw-Three Mode"), every card
surfaces alone and in a fixed order, so the burial/visibility problem those tips
solve is much smaller or doesn't really exist. Nothing is marked wrong for
draw-one, just less load-bearing there.

## Core principles (broad agreement across sources)

- **Uncover hidden cards before anything else.** The best move is usually the one
  that flips a face-down tableau card, not the one that completes a run or empties
  a foundation slot. When several moves are available, prefer the one touching the
  column with the most face-down cards still buried in it.
- **Play Aces and Twos to the foundation immediately.** They never help as tableau
  cover, so there's no reason to hold them back.
- **Beyond Twos, don't rush the foundation.** Several sources single this out as
  the biggest difference between beginners and strong players: a 3 (and higher)
  sent to the foundation early is a card you can no longer use as a landing spot
  for the opposite-color 2s coming off the stock/waste. One phrasing: don't play
  any 3s to the foundation until all four Aces are already up. Foundation piles
  that race ahead of each other also tend to strand same-color tableau cards that
  needed the lagging suit's foundation to advance.
- **Empty tableau columns are precious — spend them only on Kings.** Don't empty a
  column unless a King (or a King-headed run) is available to fill it; an empty
  column with no King to put there is a wasted opportunity, and a rushed King
  placement can trap a more useful card underneath.
- **Exhaust tableau-internal moves before drawing from the stock.** Moving cards
  already on the board first surfaces more information and more options; draw
  only once nothing else is legal.
- **Turn over the first stock card (or run once through the stock) before doing
  anything else.** Several guides frame this as step one: see what you're working
  with before committing to an early move you might regret.
  **[weaker for draw-one]** — under draw-one, drawing *is* looking: each card
  surfaces alone in order whether you treat it as a deliberate "scan" or just
  play normally, so there's no separate scanning phase that gets you information
  a normal first pass wouldn't. The tip earns its keep more under draw-three,
  where a single draw reveals three cards at once and a full pre-scan front-loads
  real information you'd otherwise get three cards at a time.
- **Respect suit-color alternation when a choice exists.** When a card could
  legally go on more than one landing spot, prefer the one that keeps the classic
  alternating-color run intact — it tends to keep more future moves open.
- **Plan the move, not just the card.** A move that looks like a step backward
  (e.g., moving a card that doesn't build toward a foundation) can still be the
  right one if it's what unblocks a longer sequence. Several guides recommend
  visualizing a few moves ahead, or even the rough shape of the endgame, rather
  than taking the first legal move available.

## More specific/advanced tactics

- **When more than one King is available to move into an empty column, look at what
  each one is covering.** Moving the King that's sitting on the more valuable buried
  card (by color, or how deeply other cards are stuck beneath it) is worth more than
  an arbitrary choice — the destination empty column has nothing buried in it by
  definition, so the payoff is entirely about which King's *current* column gets
  unblocked.
- **"Braid" the two same-color suits together where possible** — build with
  Spades/Hearts or Clubs/Diamonds interleaved in the same run, so a whole run can
  go to different foundations without having to be split apart card by card. Only
  worth doing when it doesn't cost you a better move.
- **Prefer playing off the waste pile over the tableau when both look equally
  useful**, since a tableau card not moved now is still available later, while a
  waste card left behind may cycle back under the stock and take a full redraw
  to see again. **[weaker for draw-one]** — the guides that raise this mean
  draw-three specifically: a card that isn't first in its group of three can stay
  hard to reach even after a recycle, since its two companions have to be drawn
  past again first. Draw-one has no grouping penalty — a left-behind card simply
  becomes the top of the waste again, alone, on the very next full pass.
- **Play a waste/stock card to the foundation immediately if it has no tableau
  landing spot**, rather than leaving it on top of the waste where it can get
  buried by the next draw. **[weaker for draw-one]** — same reasoning: in
  draw-three a card left on top gets buried under a fresh group of three on the
  very next draw, and can stay buried mid-group after a recycle; in draw-one it's
  only ever buried one card at a time and resurfaces cleanly, alone, on the next
  pass.
- **Late in the game, switch modes.** Once few unseen cards remain, several
  sources recommend counting what's left (so you can infer what's still hidden)
  and, once the tableau is thin enough, abandoning the conservative
  foundation-restraint rule above and sending everything you can to the
  foundation to actually finish the game.
- **Keep empty columns purposeful.** Every card parked in an empty column should
  be there because it unblocks something specific — using an empty column as a
  junk-drawer risks trapping yourself later, since only a King can ever fill an
  empty column again.

## Where guides disagree or hedge

- How aggressively to hold cards back from the foundation (see "don't rush the
  foundation" above) is the most-repeated advanced tip, but exactly when to
  switch to "send everything up" late-game is necessarily fuzzy and
  board-dependent — no source gives a precise trigger.
- A couple of guides note this app's own headline fact independently: a large
  majority, but not all, of random Klondike deals are actually winnable with
  perfect play — so no strategy guarantees a win on every deal, only improves the
  odds versus undisciplined play.

## Sources

- [A Strategy for Winning Klondike Solitaire — Jupiter Scientific](http://www.jupiterscientific.org/sciinfo/AStrategryForWinningKlondikeSolitaire.html) — the most rigorously structured of the sources found, framed as numbered principles/rules.
- [Winning at Klondike — semicolon.com](https://semicolon.com/Solitaire/Articles/Klondike.html) — the most advanced/nuanced set found, including the foundation-restraint and endgame-counting tips.
- [11 Strategies to Win Solitaire — Solitaire Bliss](https://www.solitairebliss.com/blog/klondike-strategies)
- [Klondike Solitaire - Winning Strategy — BVS Solitaire](https://www.bvssolitaire.com/rules/klondike-solitaire-strategy.htm)
- [Master Klondike Solitaire with these 10 Advanced Strategies — PlayingCardDecks.com](https://playingcarddecks.com/blogs/how-to-play/10-steps-to-winning-klondike-solitaire)
- [Klondike Solitaire Strategy — Arkadium](https://www.arkadium.com/blog/klondike-solitaire-strategy-ak/)
- [11 Strategies to Win At Klondike Solitaire — thesolitaire.com](https://thesolitaire.com/blog/strategies-to-win-klondike-solitaire/)
- [Klondike Solitaire Strategy & Tips: Play Smarter, Win More — solitaire.com](https://solitaire.com/blog/klondike-solitaire-strategy-and-tips/)
- [Solitaire Tips & Strategies – Win More Games — freesolitaire.com](https://www.freesolitaire.com/posts/posts-guides/klondike-solitaire-tips-strategies)

pagat.com, generally the most-cited authority for card game *rules*, does not appear
to currently host a dedicated Klondike strategy page (its rules page for this game
could not be located at the time of writing) — omitted rather than guessed at.
