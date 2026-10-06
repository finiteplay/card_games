# UI and Interaction Specification

## Principles

The interface is fast, calm, readable, and defaults to a right-handed layout, with a left-handed mirror available as a setting. The **board area** — the Canvas-drawn surface the piles sit on — renders with Compose Canvas. Pile and card vocabulary throughout this document follows `docs/solitaire/GLOSSARY.md`.

## Screens and States

- **Game:** board, moves, timer, and actions
- **Loading:** static board-colored surface; no animated splash
- **Settings:** automatic moves, enable animation, hint mode, handedness, sound, theme, language, draw mode, and difficulty (the last two start a new game in the chosen mode or level, asking first when a played game would be lost)
- **Statistics:** Draw One/Draw Three tabs, Week/Month/All Time tabs within each, summary, range-graph distributions, sample size, and reset
- **Help:** Rules, Strategy, and Levels pages behind one icon (below)
- **Confirmation:** new game, replay, and statistics reset
- **Win:** final board, summary, and skippable celebration
- **No moves:** notice offering undo and new game when the game is stuck
- **Recovery:** unobtrusive notice after discarding a corrupt saved game; the game stays playable
- **Unrecoverable:** non-playable error when the bundled catalog fails verification, showing expected and actual versions or hashes and offering no gameplay

Modal screens pause the timer. Android back closes the current modal before leaving the game.

## Board Geometry

- Use seven equal tableau columns.
- Preserve a card width-to-height ratio of 5:7.
- Keep at least 4 dp between tableau columns.
- Keep card width at least 40 dp and the exposed face-up band at least 24 dp. Cards are content and are exempt from the 48 dp control minimum; these two numbers replace it.
- Scale cards to fit width first, then reduce vertical overlap to keep every playable card visible.
- Portrait insets the board 4 dp from each side edge, so the outer columns do not sit flush against the screen border; card size is fit to what is left. Landscape needs no such inset — centering its nine-column arrangement already leaves slack at both edges.
- Where a group's own height binds harder than width — landscape, where the tableau runs the full height and the foundations stack four deep with no overlap — cap card size by that height too, and take the smallest of the fits. The tableau's cap tracks the tallest column *currently dealt*, not the tallest the rules allow: a column can reach nineteen cards, and reserving for that permanently would pin every landscape game to the minimum card size to guard against a shape most games never reach. Card size therefore steps down, between moves, as a column crosses each threshold, and every card stays whole.
- Spare height goes to the columns, not to empty table: the face-up step grows first, up to about half a card, then the face-down step up to about a fifth. A freshly dealt column is six face-down cards under one face-up, so without the second of those every column stayed at its minimum and a portrait board huddled at the top of the screen. Both caps still yield on a deep column, where the steps shrink back to fit every card on screen.
- Face-down overlap may be smaller than face-up overlap. The step onto a column's newly revealed first face-up card uses the face-down overlap too, not the face-up one — only a step strictly between two face-up cards gets the larger face-up band.
- Respect status, navigation, display-cutout, and gesture insets.
- Cancel an active drag safely on rotation while preserving game state.

### Status Row

One line: the title at the leading edge, then the deal's difficulty and its number within that level (`docs/games/klondike/DEALS.md` "Interim Pre-RF Seed Source") and the moves/timer, centered in the space the title leaves, with Statistics as an icon in the trailing corner.

The level and the number are separate links: the level opens the level picker, the number opens the deal picker. The deal number is a link to the **deal picker** (`core:ui`'s `DealPickerDialog`): a list of the active level's deals, each marked *not played*, *played* or *won*, Each row also states its moves: the **fewest moves of any win** for a won deal, and the **moves of the game last played** for one not won yet (`DealProgress`); a deal whose moves are unknown shows none. It opens scrolled to the deal on screen, with a filter by those states. Picking one starts it, asking first, as New Game does, when the game on screen has been played and is unfinished — and recording the same loss. Picking the deal already on screen does nothing. *Played* is a first move, not a result; *won* is never undone by playing the deal again. Progress is kept per seed (`core:storage`'s `DealProgressStore`), cleared by Reset Statistics, and does not reach back before it was introduced: games finished earlier are not marked. Draw-three has no list to choose from, so its status row has no number and no picker.

- The title is the product name — one line, untranslated.
- Portrait puts the level on one line and the moves/timer on the next, and reserves both
  however short the translation. Landscape runs them side by side and reserves one: it has the
  width to spare and no height to waste, and the spare line was showing as a band of empty
  table above the board. Reserved height and maximum height are separate — a long translation
  may still take a second line rather than ellipsize. Translations vary enough in length (Ukrainian being the longest)
  that letting the line size itself moved everything below it by a line per language. The
  reservation costs a blank line in English and keeps the board, and the actions under it,
  in the same place in every language.
- Statistics is in the row's trailing corner in portrait. In landscape the status row spans
  the full width above the board, so Statistics moves to the top corner of the rail nearest
  the holding hand instead.
- The difficulty keeps its own accent color through a span of the same line rather than
  being a separate view, so the line can wrap between the level and the counters.
- **The level is a link**: tapping it opens the level picker, the same choice Settings
  offers, because Settings is a long way to go to change something already named on
  screen. Only the level span is tappable — the moves and timer beside it are not
  controls, and a link annotation is what separates them within the single Text the
  wrapping rule above requires. The picker warns before it acts whenever switching would
  forfeit a played game (`DESIGN.md` "Statistics"), and says nothing about it on a raw
  board, where the switch costs nothing.
- Statistics sits here, not in the action bar: it reads the record rather than acting on
  the game in play.

### Portrait

- Status row (above) at the top, with Help and Statistics as icons in its trailing corner — Help first, since both read rather than act and Help is what a new player needs first.
- Four fixed-suit foundation slots alone in the top board row, on the side Right-Handed Layout gives them.
- **Stock and waste in a row of their own at the bottom** of the board area, on the edge nearest the holding hand — *while the board can afford it*. Two things yield when it cannot, in this order: the bottom row goes back beside the foundations, then the card shrinks, exactly as landscape's fit already does. Both track the deepest column **currently dealt** rather than the nineteen the rules permit, so an ordinary game keeps its large cards and its bottom row. Something has to yield: on a small phone a nineteen-card column does not fit alongside a bottom row at any card size the rules allow, and a nearly square screen — an unfolded foldable — sizes its cards off a wide edge and has no room for the row at all, which is where the stock and waste ended up drawn against columns six and seven. `PortraitLayoutFitsTest` pins the worst case on every shipped screen size. They sat beside the foundations at the top until a 20:9 phone made that a stretch for the most-tapped pile on the board, and left the bottom half of the screen empty while the top was crowded. This also matches landscape, where the pair is pinned to the bottom of its strip for the same reason.
- Tableau between the two rows, taking all the height they leave.
- The FinitePlay wordmark is **background, not a row**: drawn behind the board, low in it, and above the stock/waste row where there is one. It sat in the layout flow until a nearly square screen showed the cost — a strip of height the board needed more. A freshly dealt board shows it whole; the columns cover it as they grow, which is what being background means.
- Five actions in a bottom bar: Undo, Hint, New, Replay, Settings, each icon tinted a distinct accent color when enabled (`core/ui`'s `ActionAccentColors`, each past the 3:1 contrast floor) so the row is scannable at a glance; a disabled action always falls back to the flat disabled gray regardless of its accent, so color never implies usability
- Each action takes an equal weighted share of the row, and its label reserves two lines. Content-sized buttons let a long translation widen its own button and push the ones after it along, until the last slid off the screen edge.

### Landscape

- The status row runs the full width above the board, over the foundations strip: the title, level, moves, and timer.
- Below it the board is flanked by two rails, each one action button wide. The rail at the edge nearest the player's holding hand — the trailing edge under Right-Handed Layout — carries Statistics and Help at its top and Hint above Undo at its bottom, with the gap between the two groups taking whatever is left — the two in-play actions end in the corner the thumb already rests in.
- In the stock/waste strip, **stock takes the lower position and waste sits above it**. The strip is pinned to the bottom of the board, and of the pair it is stock that gets tapped over and over. Portrait keeps its own order.
- The FinitePlay wordmark appears as a watermark along the bottom edge behind the board — there is no spare row for it, and it must never obscure a card, so it is drawn before the board and kept semi-transparent.
- The opposite rail carries the remaining actions — Settings, Replay, New — in a single column, centered vertically. Splitting the five across two rails is what lets each be a single file: all five stacked in one rail exceed a landscape phone's height.
- The board area is divided into nine equal-width slots — one per tableau column plus one for each flanking strip — and every pile is centered in its own slot. Card size is usually capped by height rather than width (see Board Geometry), so packing the nine at minimum spacing left the tableau crammed together with all the slack at the two edges; equal slots put that slack between the columns instead.
- The four foundation slots form their own vertical strip at the board area's far edge from stock/waste, starting at the top.
- Stock and waste form a vertical strip on the opposite side of the tableau from the foundations, pinned to the bottom of the board area: stock is the most-tapped pile and belongs in the corner the holding hand's thumb already reaches.

## Right-Handed Layout

The default. A Settings toggle (`storage/Handedness`) switches to Left-Handed Layout
below; both describe the same board, mirrored.

- Stock/waste sit at the right of the board area in both orientations; in landscape the Statistics/Hint/Undo rail sits outside it, further right still, and the remaining actions form the left rail.
- Foundations sit on the opposite side of stock/waste from the tableau's perspective: the far side of the top row in portrait, the left strip in landscape.
- Undo and Hint are closest to the right-hand edge in both orientations: the end of the bottom action bar in portrait, the bottom of the right-edge rail in landscape.
- Tableau column indexes and card rules never change.
- Foundation suit order remains stable within its group.

## Left-Handed Layout

The Settings screen's "Left-handed layout" toggle mirrors the board horizontally,
applied immediately and persisted like every other setting. Only where pile groups
and the action rail sit changes — tableau column indexes, foundation suit order
within its group, tap priority, drag behavior, and every game rule are exactly as
described elsewhere in this document and in `DESIGN.md`, unaffected by handedness.

- Stock/waste sit at the left edge in both orientations — the mirror image of
  Right-Handed Layout, including which of the pair (stock or waste) sits closer to
  the edge: stock is always closest to the edge nearest the dominant hand, waste just
  before it.
- Foundations sit on the opposite side of stock/waste from the tableau's
  perspective, same as Right-Handed Layout: the far side of the top row in portrait,
  the right strip in landscape.
- In portrait, Undo and Hint are closest to the left-hand edge of the bottom action
  bar — the whole action row is reversed, not just repositioned, so every action
  keeps its neighbors. In landscape the two rails swap edges: Statistics/Hint/Undo
  moves to the left edge and the remaining actions to the right. Each rail's own
  vertical order is unchanged — Undo stays at the bottom, in the corner nearest the
  holding hand either way.
- Tableau column indexes, card rules, and foundation suit order are exactly as in
  Right-Handed Layout — nothing about handedness ever touches game state or which
  column holds what.

## Input

### Tap

- Stock: draw one card (draw-one) or up to three (draw-three; `RULES.md` "Draw-Three Mode"), or recycle the waste when stock is empty.
- Face-up tableau card: move its run using the priority in `DESIGN.md` — the lowest-index legal tableau destination to the right of its own column; then a legal foundation move (safe or not); then, only if neither exists, the lowest-index legal tableau destination to the left of its own column.
- Face-up waste card: move it using the priority in `DESIGN.md`, which prefers a safe foundation and falls back to an unsafe but legal one only when no tableau destination exists.
- Face-down card: no action unless it is the exposed top card, which flips automatically.
- Foundation top: move to the lowest-index legal tableau destination. The card is then parked, so automation cannot immediately reclaim it.
- Invalid tap: brief outline or shake; no score. A tap only reports invalid when the rules allow no move at all.

### Drag

- Begin after crossing the platform touch-slop threshold.
- Lift the selected card or sequence above the board.
- Highlight legal destination slots without changing state.
- Commit on a legal destination.
- On an invalid drop the lifted cards return to their pile at once and the source pile carries the invalid highlight for 220 ms, rather than flying back: the cards never left the layout, so animating a return would show a move that did not happen.
- Count the entire sequence transfer as one move.

### Hint

A Settings toggle ("Intelligent hint", on by default) picks which of two
modes the Hint action is in. A game with no player move behind it follows the deal's certified path whatever this setting says — the line is shipped, so showing it costs no search — and the setting decides again from the first move; a deal that ships no line (and so never reaches this rule) behaves as the setting says. Off, Hint highlights every legal move at once and has no
search behind it at all — tapping Hint again while highlights are showing hides them.
Turning this setting on or off does not change what a hint already on screen shows,
only the next request. On (the default),
Hint runs the on-device solver as described below.

Hint runs the on-device solver (`DESIGN.md` "On-Device Hint Search") rather than a
local heuristic, and reports one of three outcomes:

- **A guided move**: pulse the card or substack that would move, at its current
  position, twice — destination is not separately highlighted. Keep the highlight
  visible long enough to identify the cards involved. A stock draw/recycle may be
  suggested. A repeated request while one is already showing has nothing new to
  advance to — the search returns the single next step of a proven line, not a
  ranked list — so it has no additional effect.
- **No solution**: a dismissible notice states that no path to a win exists from
  the current board, and auto-dismisses after 4 s if the player leaves it alone.
- **Inconclusive**: a dismissible notice states the search could not tell within
  its time budget, and auto-dismisses after 4 s if the player leaves it alone. The
  move the top strategy ruleset would play is pulsed alongside it, exactly as a
  guided move is, so the player still has something to act on — the notice is what
  marks it a suggestion rather than a proven step (`DESIGN.md` "On-Device Hint
  Search"). When no such move exists, the notice appears alone.
- While the search runs, a brief loading notice is shown; it is not dismissible,
  since the request cannot be cancelled mid-search, only ignored once it returns.
- Every outcome is visible on the board: the pulse for a guided or suggested move, the notice for the other two.

The status row leads with the deal's difficulty and its number within that level — "Medium #5" — tinted with that level's own accent (`ui/theme/Theme.kt`), cool-to-warm as difficulty rises. The level is always spelled out, so the color is emphasis and never the only cue (`DESIGN.md` "Accessibility").

## Motion

- Card moves run at a constant speed rather than a fixed duration, so a short hop and a corner-to-corner flight read as the same motion instead of the same clock time, bounded to 80–450 ms so neither a near-zero-distance nor a full-board flight is imperceptible or drags on. The rate is 1.6 dp/ms — 5 inches in 0.5 s, since 160 dp is one inch on any device — rather than a raw pixel rate, so the real-world speed is the same on every screen density instead of only on whichever one it was tuned against.
- Whichever pile a committed move's cards are actually leaving stops showing them there for the flight's whole duration, tap-committed exactly as drag-committed: the source would otherwise still be drawn at rest while an identical flying copy pulls away from directly on top of it, reading as the card splitting in two rather than sliding cleanly.
- A revealed face-down card pauses 100 ms before an automatic move carries it away, so the reveal itself is visible for a beat.
- Automatic moves, including the automatic finish sweeping every remaining card to the foundations, play one at a time in full — never merged or overlapped into one blurred motion — so each placement change stays individually visible. The automatic-finish sweep plays at three times the constant speed used elsewhere, since the player has no remaining decisions to watch for; the regular cascade after a player move stays at normal speed. A long sweep is not bounded to a fixed total duration as a result; any tap skips to the final board, and the win presentation follows.
- A win animation lasts at most three seconds and is skipped by any tap.
- Turning Enable Animation off removes every slide, pulse, shake, and the win animation, but not the one-at-a-time pacing itself: the player's own move still lands immediately, but each automatic transfer in the following cascade still plays as its own discrete step, just without a slide, separated by a short pause instead — so a multi-card cascade still reads as a sequence of individual placements, not a single unexplained jump to the final board. The automatic-finish sweep is the one exception, exactly as at full speed: its steps carry no added pause under this setting either, so it still resolves quickly.
- Do not start animation while backgrounded.

## Themes

Two themes ship, light and dark, selected by a persisted setting that also offers follow-the-system and time-of-day automatic. Both palettes, and the contrast reasoning that fixes how light the card face and the table may go, are `docs/PLATFORM.md` "Themes"; the tokens themselves are `core/ui`'s `AppColors` and `CardColors`, which are the only place they are written down — a colour list copied into a spec goes stale the first time a palette is tuned, and this one did.

What holds in both, and is this document's own: cards use suit symbols, not colour alone. System bars are transparent and take the light or dark appearance of whichever theme resolved, so a forced-light board never keeps dark-theme bars. Everything Klondike draws on top of the palette — the cloth table, the card backs, the empty-slot token, the per-action accents — is below under "The table and the backs".

### The table and the backs

- The table is **cloth, not a fill** (`core/ui`'s `feltTable`): velvet, meaning a fine directionless pile of lit and shadowed fibres under soft nap clouding, a broad off-centre sheen and a vignette — not a weave, which velvet does not have. Every layer that carries texture is weak — the strongest is 11%, and only the vignette's outermost stop goes past that — because it sits behind playing cards; measured on the board the texture's standard deviation is about 3 of 255, which reads as cloth at arm's length and never as pattern.
- **The grain is a spectrum, not a set of marks.** It is value noise summed over octaves from 1 to 64 px, never dots, threads or blobs: anything stamped on puts all of the contrast at one scale, and one scale is what the eye names — fine ones as dust or sensor noise, coarse ones as lumps or stains. Both failures shipped in earlier passes of this file. The amplitudes follow the falloff *as the eye receives it* rather than the surface's own: at 400+ dpi and arm's length a 1 px fibre is past resolving, so the contrast sits in the 4–40 px band and tapers off either side.
- **Every shadow on the table is the cloth's own colour taken down, never black** — the grain's dark side, the dark nap patches and the vignette alike. Dye keeps its hue in shadow; mixing towards neutral drains the green in patches, and a green that has lost its green in patches reads as a dirty cloth rather than a dark one. For the same reason the light side of the grain is given the longer reach: broad light variation reads as a surface catching the light, broad dark variation as something lying on it.
- The grain is one opaque tile per table — it carries the colour as well as the texture, so it is the base coat too — repeated by a shader, so a frame costs a rect per layer and no per-pixel work. The tile is drawn one texel to one pixel, unrotated and unscaled. **A repeat the player can find is a worse defect than a flat fill**, and two things prevent one: each octave's lattice closes exactly on the tile edge, so the noise wraps by construction rather than by patching a seam, and nothing in the tile is a shape, so there is no landmark to find the period by.
- Card backs are **slate in both themes** — blue held most of the way to grey. A green back on a green table is the same object twice, and a face-down stack has to read as cards lying on cloth; blue on baize is the classic answer because it works, and it sits opposite the table without competing with the red suits.
- The FinitePlay wordmark ships as two drawables, one per theme, chosen by the background's own luminance rather than by the system setting — so a player who forces light or dark in Settings gets the mark that suits what they are looking at. A single tinted mark would suit neither table.
- An empty pile's outline and suit watermark take their own colour token (`CardColors.emptySlot`), from the *table's* family rather than the back's: a slot is a space on the cloth, a face-down card is an object on it.
- **Every suit glyph is drawn into its own layer and tinted through `SrcIn`** — the card face's rank and pips as well as the empty-slot watermark. The suit characters resolve to a colour emoji font on most devices, which paints its own vivid red hearts and slate-grey clubs and ignores the paint colour. Untinted, that put the theme's red on the rank and the font's red on the pip beside it, two different reds on one card, and left the empty slots' black suits at about 1.5:1 against the table.
- The dark theme's red is chosen **for the dimmed card face**, not by darkening the light theme's. Dimming all three channels until a red clears the contrast floor takes the colour out of it: the first attempt read as brick, and visibly weaker than the black suits beside it at 8.4:1. Dropping green and blue almost to nothing instead raises both contrast (4.2:1) and chroma, so red reads as red rather than as dark.

### Screen size

Chrome — icons, labels, status type, touch targets — scales with the screen (`core/ui`'s
`chromeScale`). The board scales by construction, being seven columns across whatever width
there is, so without this a tablet showed doubled cards beside phone-sized buttons.

The curve is `(smallestWidth / 400dp) ^ 0.6`, capped at double: 1.0 on a phone, about 1.5 on an
8-inch tablet, 1.7 on a 10-inch. Two details matter and both were learned by getting them wrong:

- It reads the **smallest** screen width, not the current one. `screenWidthDp` is the long edge
  in landscape, so a phone turned sideways reported 731 dp, took tablet-sized chrome, and grew
  a rail too tall for its own buttons — Undo fell off the bottom of the screen.
- It is **continuous**, not stepped through Material's size classes. An 800 dp tablet sits in
  the "medium" bucket, and a fixed 1.25x there left the chrome visibly behind cards that had
  doubled.

Verified by screenshot across seven size classes from a 720x1280 phone to a 10-inch tablet, in
both orientations.

## Help

Three pages behind one icon — **Rules**, **Strategy**, **Levels** — written for someone who has not played Klondike before. `RULES.md`, `PUBLIC_STRATEGY_RESEARCH.md` and `DIFFICULTY_LEVELS.md` are the authorities; the help screen is their plain-language summary and never contradicts them. Tabs rather than one scroll, because the three are read at different moments: the rules once, the strategy when a player starts losing, the levels when they wonder what the word above the board means.

The levels page colours each level with the same accent the status row uses for it, so the page and the board agree about what "Hard" looks like. Insane's entry says plainly that nobody knows a winning line for those deals, including us.

The pages are translated into every supported locale, one `values-XX/strings_help.xml` each, and the completeness gate spans them alongside `strings.xml`. Each locale reuses the card vocabulary its own strings already ship — its words for foundation, stock, waste, tableau, and each level name — so the pages and the buttons call the same things by the same names. Like the rest of the non-English strings they are machine-generated and not native-reviewed (`TODO.md` "Localization — Native-Speaker Review").

## Accessibility

Scope is `docs/PLATFORM.md` "Accessibility", including its decision that screen-reader
support is out of scope. Klondike's own numbers:

- Every action has a visible label.
- Interactive controls are at least 48 dp. Cards are exempt and meet the board-geometry minimums instead.
- A disabled action reads as disabled from its colour alone — the flat disabled gray, never its accent (`Portrait` above).
- Suit symbols carry suit, never colour alone, so a red/green colour deficiency cannot cost a player the board.
- Font scaling must not obscure timer, score, settings, or statistics. Card face text scales with card size rather than the system font scale.

## Dialog Behavior

- New and Replay show the current moves and elapsed time before confirmation.
- Settings apply immediately. Draw mode and difficulty are fixed once a game is dealt, so choosing a different one starts a new game in it — asking first, as New Game does, when the game on screen has been played and is unfinished, since leaving it records a loss. Choosing the draw mode already in play only records it for later deals. Switching away from a game with no move behind it gives its deal back to its sequence, so switching there and back finds the same hand rather than using one up each time; New Game, which asks for a different deal, does not.
- Difficulty, theme, and language are dropdowns rather than toggles or chip rows; everything else on the screen is a switch. Difficulty has seven options (Trivial, Easy, Medium, Hard, Expert, Insane, Random), theme four (Light, Dark, System default, Automatic), and language thirty-one, which a chip row would wrap into an unreadable block. Random picks a level afresh for each new game. Each language names itself in its own language, so a player who picked one they cannot read can still find their way back; only "System default" is translated. Changing language recreates the activity, which is what applies it. Each level keeps its own place in its own deal list, so switching away and back resumes where that level was left (`docs/games/klondike/DEALS.md`).
- Statistics show one draw mode's results and sample size at a time (the screen's Draw One/Draw Three selector), never both blended together.
- Statistics show wins, losses, and games played as separate values, since an unfinished played game raises games played without affecting win rate.
- Distribution rows show min, p10, p50, p90, and max for time and move count.
- Statistics reset requires explicit confirmation.

## Win Presentation

Stop the timer when all 52 cards reach foundations, or when the automatic finish begins, whichever comes first. Show:

- Completion time
- Move count
- Relevant personal bests
- New Game and Replay actions

Record the win exactly once, even after rotation, restoration, or repeated presentation.

The win is announced as well as shown: a large *You win!* banner and confetti over the dialog, the
banner staying until the dialog is closed (`docs/PLATFORM.md` "Win Celebration").

A debug-only build additionally shows a difficulty-rating prompt in the win dialog (`docs/games/klondike/DEALS.md` "Difficulty Grading (Interim)"); it renders nothing in release builds.
