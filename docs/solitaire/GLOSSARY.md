# Solitaire Vocabulary

Publisher: [FinitePlay LLC](https://finiteplay.org)

The words every solitaire in this repo uses for the parts of the game, and the words it
deliberately does not. Card-game literature is not fully consistent, so where a term is
contested this file picks one and says why — the point is that specs, code, and the strings a
player reads all say the same thing.

A game's own spec may add terms for concepts only it has (Klondike's
`docs/games/klondike/DIFFICULTY_LEVELS.md` defines a grading vocabulary of its own). It may
not redefine anything here.

## The parts of a game

**Layout** — the whole arrangement of piles a game deals into. Not "board": that is a
board-game word, and no solitaire literature uses it. The one exception is the Compose
component `Board.kt` and the "board area" it draws, which name a *rendered surface* rather
than a part of the game.

**Pile** — the generic word for any stack of cards in the layout. Every group below is a pile
or a set of them, so "pile" alone never identifies one; it is correct in `PileKey` and in
"pile groups", and wrong as a name for the stock and waste.

**Tableau** — the piles the game is played out in, dealt at the start. A tableau **column** is
one of them.

**Foundation** — a pile built up by suit from the Ace, and the goal of the game. To **bank** a
card is to move it to a foundation.

**Stock** — the undealt cards, face down, that the player draws from.

**Waste** — the face-up pile drawn cards land on. The stock and waste together are **the stock
and waste**, written out; there is no collective noun for the pair in this repo.

**Deal** — both the arrangement a shuffle produces and one playing of it. "This deal", "replay
the deal", "deal #12". Not "hand": a hand is the cards a player holds, which solitaire has
none of.

## What the player does

**Draw** — turn one card (or three) from the stock to the waste. **Draw-one** and
**draw-three** name the two modes. "Turn one/three" means the same thing elsewhere and is not
used here.

**Recycle** — return the waste to the stock when the stock is empty, so the cards come round
again. Print sources say "redeal" for this; digital solitaire says "recycle" almost
universally, and so does the app.

**Build** — place a card on a tableau pile under the game's packing rule (Klondike: descending
rank, alternating colour). The verb is *build*, the noun is a **build** or a **sequence**; the
predicate in code is `canBuild`.

**Sequence** — an ordered, correctly-built group of face-up cards moved as one. Not "run",
which belongs to the rummy family.

**Worrying back** — moving a card from a foundation back to the tableau, the traditional name
for it (Parlett). The rules and code call the move a **foundation withdrawal**, because it is
what the move does; the traditional term is recorded here so a reader who knows it finds it.

**Autoplay** — a rule moving cards to foundations without being asked. Klondike's two forms
are **automatic foundation moves** during play and the **automatic finish** offered once the
game is decided.

## What a deal is

**Solvable** / **winnable** — a deal some line of play wins. The two are used
interchangeably; nothing in this repo depends on the distinction some literature draws
between them.

**Certified** — a deal whose winning line has been found offline and replayed through the real
rules. `docs/solitaire/CATALOG.md` governs; a game may name one level that ships uncertified.

**Certificate** — the stored winning line itself.

## Terms we do not use

| Not used | Why | Use instead |
|---|---|---|
| talon | The literature uses it for the stock in some places and the waste in others, so it identifies neither | stock, waste |
| "the pile" for the stock and waste | Collides with **pile**, the generic word for any stack | the stock and waste |
| run | A rummy-family term | sequence |
| stack (verb) | Colloquial | build |
| hand | The cards a player holds; solitaire has none | deal |
| board | A board-game word | layout (the arrangement), board area (the drawn surface) |
| patience | The British name for the genre; the app ships as Solitaire | solitaire |
