# Google Play listing review v2, group 1 (fr-FR, it-IT, pt-BR, nl-NL)

**Status: machine-translated and model-reviewed; no native-speaker review is planned.** The owner
accepts the remaining language-quality risk. These reports record model QA and automated checks;
they do not claim native-speaker validation.

- Scope: `drafts/fr-FR.json`, `drafts/it-IT.json`, `drafts/pt-BR.json`, `drafts/nl-NL.json`, all five
  games plus the shared promise, rest and footer. Each composed full description (intro, promise,
  body, rest, footer) was read in that order.
- Sources checked: `source/en-US.json`, `TRANSLATION_GUIDE.md`, `AGENTS.md` (family copy pattern),
  and the in-app strings in `values-fr`, `values-it`, `values-pt-rBR` and `values-nl` for Klondike,
  Spider, FreeCell, Blackjack, Hold'em and `core/ui`.
- Limits after the edits (`check_drafts.py`): every title is 30 characters or fewer, every short
  description 80 or fewer, and every full description 2,256 or fewer (limit 4,000). The script still
  warns that "23,675" and "2,000" are not found, because the drafts use local formats (23 675,
  23.675). It also warns that "5" is missing in Hold'em, because the short descriptions no longer
  say "5 opponents" (see the compromises below). The intro still says five computer opponents.
- Claims checked in every language: free; no ads, in-app purchases or subscriptions; no accounts,
  tracking, data collection or sharing; offline; progress kept on the device; virtual chips with
  no monetary value; no real-money bets, deposits, chip purchases, cash-outs or prizes; computer
  dealer or opponents only; 18+; certified deals vs the uncertified Klondike Insane level; hints
  have limits and guarantee nothing; Hold'em hints cannot see hidden cards; rest reminders are
  optional, with no medical claims; 3:2; 1/3; 1/2/4; 23,675; 2,000; 30 languages. All were present
  and not strengthened. No "best", ranking, award, urgency or price wording, and no "free" in a
  short description.

## Issues across all four languages (raise before any listing ships)

1. **Spider certification vs the app (affects English too).** Every locale's Spider app, English
   included, shows `catalog_status_notice`: "One- and two-suit deals come from a certified catalog
   ... Four-suit deals are not yet certified." It also has `uncertified_notice`. The listing says
   "23,675 certified-solvable deals across all three suit counts" and "Every included starting deal
   has a verified solution." Both cannot be true. Check this against the shipped catalog before any
   Spider listing goes out. The translations follow the English as written.
2. **The English hint-timeout string conflicts with the guide.** `hint_inconclusive` in English
   (Klondike, Spider, FreeCell) says "This game is likely unsolvable from here". That treats a
   search timeout as evidence. The fr, it, pt and nl strings say "could not determine in time, try
   again or keep playing", which is the more accurate version. This is in-app only and outside
   this task.
3. **Hold'em short description: "virtual chips" instead of "5 opponents".** Neither the faithful
   short description nor any of the four languages fits both in 80 characters. fr and it had kept
   "5 adversaires" / "5 avversari", and pt and nl had kept virtual chips. I changed all four to
   virtual chips. It is the gambling-safety fact, and it matches Blackjack. "5 opponents" without
   "computer" could also suggest online opponents. The intro still says five computer opponents.

## fr-FR

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| all | short, intro, body, promise, rest | normal space before `:` `;` and in 23 675 / 2 000 -> no-break space (U+00A0) | French typography. Stops a line from starting with `:`. Character counts are unchanged. Titles keep the brand pattern `Name: FinitePlay` (see the native list) |
| shared | rest | "vous invitent à faire une pause après" -> "vous invitent à vous arrêter après" | "Rappels de pause ... faire une pause" repeated the same word |
| klondike | intro | "Choisissez de piocher une ou trois cartes, un défi à votre mesure, et utilisez" -> "Piochez une ou trois cartes, choisissez un défi à votre mesure et utilisez" | Ungrammatical zeugma ("choisissez de piocher ..., un défi") |
| klondike | body (x2) | "solvable(s)" -> "soluble(s)" | In French "solvable" means financially solvent, so this was a calque error. "soluble" is the Klondike app's term |
| spider | body | "23 675 donnes certifiées solvables pour les trois nombres de couleurs" -> "23 675 donnes certifiées résolubles pour les trois variantes (1, 2 et 4 couleurs)" | Same calque. "résoluble" is the Spider app's term. "trois nombres de couleurs" was a literal rendering of "suit counts" |
| freecell | short, body | "donnes solvables" / "certifiées solvables" -> "donnes solubles" / "certifiées solubles" | Same calque. "soluble" is the FreeCell app's term and also fits in 80 characters (79) |
| blackjack | intro | "ni retraits, ni prix." -> "ni retraits, ni lots à gagner." | After "achats", "prix" reads as "price". "lots" is the usual French word for prizes |
| blackjack | body | "rebattus à chaque tour" -> "rebattus à chaque manche" | The app's word for a round is "manche". "tour" is used for "your turn" |
| holdem | short | "... : 5 adversaires, sans pub" -> "... : jetons virtuels, sans pub" | See issue 3 |
| holdem | intro | "ni prix." -> "ni lots à gagner." | Same as Blackjack |
| holdem | body | "pots annexes" -> "pots secondaires" | The app uses "Pot secondaire" |

Checked against the app with no change needed: Annuler, Rejouer, Indice, piocher, donne, fondations,
Trivial…Expert, Démentiel, couleurs, cases libres, croupier, tirer/rester/doubler/séparer, se
brûler, 17 souple, jetons, assurance, se coucher/suivre/miser/relancer/tapis, blindes, équité, cote
du pot.

**Short-description compromises (faithful wording does not fit in 80 characters):**
- Klondike (75): "indices" (hints) dropped. The faithful version is 84 characters.
- Spider (77): "indices" dropped. The faithful version is 86.
- FreeCell (79): "indices" dropped.
- Blackjack (80): "indices" dropped.
- Hold'em (77): "5 adversaires" dropped (issue 3).

**Needs a native speaker:**
- "soucieux de la batterie" for "battery-conscious": natural, or calqued? Don't use "économe en
  batterie", which strengthens the claim.
- "sans pub" in the short descriptions vs "sans publicité" in the full text.
- Titles: "Solitaire Klondike: FinitePlay" uses French word order, while "Spider Solitaire" and
  "FreeCell Solitaire" keep the English order. Check which forms French players search for. In
  French typography the colon takes a space before it, but "Solitaire Klondike : FinitePlay" is 31
  characters, so all titles keep the brand form without the space.
- "soluble" (Klondike, FreeCell) vs "résoluble" (Spider): the two app terms differ, and the listing
  follows each game's app. Pick one if the apps are unified.
- "ni lots à gagner" for "prizes" (the v1 review had "gains"), and "retraits" for "cash-outs".
- "bonus d'urgence", "vérification du blackjack par le croupier" (dealer peek), "checker" in the
  Hold'em features list, "Chaque place commence avec la même pile de jetons", "visez à être".
- "pas de compte, ni suivi, ni collecte..." in the promise: acceptable, or "aucun compte, aucun
  suivi..."?

**Rating: 4/5 after the edits** (3.5 before: the "solvable" calque and the Klondike intro were real errors).

## it-IT

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| klondike | intro | "scegli una sfida a tua misura" -> "trova una sfida a tua misura" | "Scegli ... scegli" was repeated in the same sentence |
| spider | title | "Spider Solitario: FinitePlay" -> "Solitario Spider: FinitePlay" | English word order. It also matched neither "Solitario Klondike/FreeCell" nor the intro's "Solitario Spider" |
| spider | body | "con tutti e tre i numeri di semi" -> "nelle tre varianti (1, 2 e 4 semi)" | Literal rendering of "suit counts" |
| freecell | short | "FreeCell semplice, offline, attento alla batteria: senza pubblicità" -> "FreeCell offline, attento alla batteria: partite risolvibili, senza pubblicità" | The draft had dropped the only gameplay fact. Restored "solvable deals" (78) instead of "semplice", matching the Italian Blackjack line |
| holdem | short | "Poker semplice, offline, attento alla batteria: 5 avversari, senza pubblicità" -> "Poker offline semplice, attento alla batteria: fiches virtuali, senza pubblicità" | Issue 3. 80 characters exactly. The word order is needed to fit |
| holdem | body | "e punta a essere l'ultimo giocatore" -> "e cerca di essere l'ultimo giocatore" | "puntata ... punta" clashed (a betting pun) |
| holdem | body | "gestione dei side pot" -> "gestione dei piatti laterali" | The app uses "Piatto laterale" |
| holdem | body | "classifica delle mani" -> "gerarchia delle mani" | "classifica" is also the promise's word for "leaderboard" ("nessuna classifica") |

Checked against the app with no change needed: Annulla, Ripeti, Suggerimento, pesca, partita,
fondazioni, Banale…Esperto, Folle, semi, celle libere, banco, carta/stai/raddoppia/dividi,
sballare, 17 morbido, fiches, mano, Fold/Check/Call/Punta/Rilancia/All in, blind, equity, pot odds.

**Short-description compromises:**
- Klondike (78): "suggerimenti" (hints) dropped. Even with "senza annunci" it needs 89 characters.
- Spider (77): "suggerimenti" dropped.
- FreeCell (78): "semplice" and "suggerimenti" dropped.
- Blackjack (75, unchanged): "semplice" and "suggerimenti" dropped. Restoring only "semplice"
  needs 82 even with "senza annunci".
- Hold'em (80): "5 avversari" dropped; word order "Poker offline semplice".

**Needs a native speaker:**
- "attento alla batteria" for "battery-conscious".
- "Poker offline semplice": natural, or does it need "Poker semplice e offline" (which goes over 80)?
- "senza annunci" (Play's own Italian label is "Contiene annunci") would save 3 characters per short
  description. It is not used, to stay consistent with "senza pubblicità" in the promise.
- "partite" for deals (the app uses "Partita", "distribuzione" and "mano" in different places);
  "prelievi" for "cash-outs"; "controllo del blackjack da parte del banco" (dealer peek); "stack";
  "bonus a tempo" for "urgency bonuses"; the mix of English and Italian action names in the Hold'em
  features list.

**Rating: 4/5.** The draft was already fluent. The errors were the inconsistent Spider title, the
gutted FreeCell short description and the "classifica" clash.

## pt-BR

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| klondike | short | "comprar 1/3 cartas, sem anúncios" -> "comprar 1/3, dicas, sem anúncios" | Restores "hints" (77) |
| klondike | intro | "Escolha comprar uma ou três cartas, um desafio confortável e use" -> "Escolha comprar uma ou três cartas e um desafio confortável, e use" | Zeugma: the list was missing "e" |
| klondike | body | "Regras de comprar uma ou três cartas" -> "Regras de compra de uma ou três cartas" | Ungrammatical |
| spider | body | "nas três quantidades de naipes" -> "nas três modalidades (1, 2 e 4 naipes)" | Literal rendering of "suit counts" |
| holdem | intro, body (x3) | "oponentes" -> "adversários" | The app help uses "adversários" ("cinco adversários") |
| holdem | body | "divisão de side pots" -> "divisão de potes laterais" | The app uses "Pote lateral" |
| holdem | body | "ranking das mãos" -> "hierarquia das mãos" | The promise uses "rankings" for leaderboards ("sem rankings") |

Checked against the app with no change needed: Desfazer, Repetir, Dica, comprar, partida,
Trivial…Especialista, Insano, naipes, células livres, dealer, pedir/parar/dobrar/dividir,
estourar, 17 mole, fichas, rodada, Fold/Check/Pagar/Apostar/Aumentar/All in, blinds, equity, pot
odds.

**Short-description compromises:**
- FreeCell (78): "dicas" (hints) dropped. The faithful version is 85.
- Blackjack (75): "dicas" dropped. The faithful version is 82.
- Hold'em (71): "5 adversários" dropped (86 with it).
- Klondike and Spider keep every fact.

**Needs a native speaker:**
- "atento à bateria" for "battery-conscious".
- "Paciência" in the solitaire titles (assumed to be the Brazilian search term).
- "Escolha comprar uma ou três cartas": "comprar" is the app's draw term but can read as "buy" in a
  listing that also says "no purchases". Check that it reads naturally.
- "jogos solucionáveis" (FreeCell short) vs "partidas" in the body.
- The app mixes "adversários" and "oponente" (`hint_threat_beat`).
- "bônus de urgência", "verificação do blackjack pelo dealer" (dealer peek), "pilha de fichas",
  "Layouts retrato e paisagem".

**Rating: 4/5.**

## nl-NL

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| shared | promise | "geen accounts, tracking, gegevensverzameling of gegevens delen, geen ranglijsten of online functies" -> "geen accounts, tracking, verzameling of delen van gegevens, geen ranglijsten of onlinefuncties" | The list mixed a compound noun with a verb phrase. "online functies" should be written as one word: "onlinefuncties" |
| shared | footer | "die rekening houden met je tijd, je privacy en je aandacht." -> "die je tijd, je privacy en je aandacht respecteren." | "rekening houden met" (take into account) was weaker than "respect" |
| klondike, spider | short | "zonder reclame" -> "geen reclame" | Consistency with the other three games (78 each) |
| spider | body | "Elke meegeleverde startgift heeft" -> "Elke meegeleverde beginstand heeft" | The Spider app calls deals "spel"/"spellen", not "gift" |
| spider | body | "23.675 gecertificeerd oplosbare giften voor alle drie de kleuraantallen" -> "23.675 gecertificeerd oplosbare spellen met één, twee en vier kleuren" | App term, and "kleuraantallen" was a literal rendering |
| spider | body | "animatieinstellingen" -> "animatie-instellingen" | Spelling: two vowels meet at the join, so Dutch needs a hyphen |
| freecell | short | "oplosbare giften" -> "oplosbare spellen" | The FreeCell app uses "Spel #" |
| freecell | intro | "Zie alle kaarten en plan" -> "Bekijk alle kaarten en plan" | The imperative "Zie" is stilted in Dutch |
| freecell | body | "Elke meegeleverde gift begint" / "2.000 gecertificeerd oplosbare giften" -> "Elk meegeleverd spel begint" / "2.000 gecertificeerd oplosbare spellen" | App term |

Klondike keeps "giften" because its app shows "Gift %d". Also checked against the app with no change
needed: Ongedaan maken, Opnieuw spelen, Hint, trekken, stapels, Triviaal…Expert, Waanzinnig,
kleuren, vrije vakken, Heer/Aas, deler, kaart/blijven/verdubbelen/splitsen, bust, zachte 17,
fiches, Fold/Check/Call/Bet/Raise/All-in, blinds, sidepots, potodds, equity.

**Short-description compromises:**
- FreeCell (77): "hints" dropped. The faithful version is 84.
- Blackjack (76): "hints" dropped. The faithful version is 83.
- Hold'em (72): "5 tegenstanders" dropped (89 with it).
- Klondike uses "1/3 kaarten" for "draw 1/3", because "1/3 kaarten trekken" does not fit with hints.

**Needs a native speaker:**
- "batterijbewuste" for "battery-conscious".
- "Klondike/Spider/FreeCell Solitaire" vs "Patience" in the titles.
- "giften" (Klondike) vs "spellen" (Spider, FreeCell). The app itself mixes "Gift", "deal" (deal
  picker) and "Spel". Unify the app first, then the listing.
- "op een dood punt eindigen" for "dead end"; "gecontroleerde oplossing" for "verified solution"
  (the app says "gecertificeerde"/"geverifieerd"); "urgentiebonussen"; "potjes tegen echte mensen";
  "opnames" for "cash-outs" (the app says "uitbetaald"); "zes decks".
- Blackjack "deler" vs Hold'em "dealer" (known app inconsistency, from TODO.md).

**Rating: 4/5 after the edits** (3.5 before, because of the spelling errors and the deal term
that did not match the app).

## App-string observations (no listing change; for the in-app reviewers)

- fr: Klondike and FreeCell say "soluble", Spider says "résoluble".
- it, pt, nl: the word for "deal" changes between status line, deal picker and notices (it
  Partita/distribuzione/mano; pt Partida/distribuição/Jogo; nl Gift/deal/Spel).
- pt Hold'em: "adversários" in help, "oponente" in `hint_threat_beat`.
- All locales: Spider `catalog_status_notice` (issue 1) and English `hint_inconclusive` (issue 2).

## Summary

Edited all four drafts in place (14 fr, 6 it, 6 pt, 8 nl changed lines), fixing real errors: fr "solvable" (means solvent), two zeugmas, nl spelling, the it Spider title, the it FreeCell short description, and leaderboard words reused for hand rankings. App terms were aligned. All limits pass.
Short descriptions still drop "hints" in 12 of the 16 lines that have it in English (pt and nl Klondike and Spider keep it) and "5 opponents" in every Hold'em line; all four Hold'em lines now lead with virtual chips.
Model quality rating: 4/5 for each language. No native-speaker review is planned; the owner accepts the residual language-quality risk. The Spider four-suit certification conflict remains a factual issue to verify against the shipped catalog.
