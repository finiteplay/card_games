# Store listing review v2, group 2: uk, pl-PL, sv-SE, no-NO, da-DK, fi-FI

> **STATUS: MACHINE-TRANSLATED AND MODEL-REVIEWED; NO NATIVE-SPEAKER REVIEW IS PLANNED.**
> The owner accepts the remaining language-quality risk. This report records model QA and
> automated checks; it does not claim native-speaker validation.

Scope: `translations/drafts/{uk,pl-PL,sv-SE,no-NO,da-DK,fi-FI}.json`. Each one covers 5 games plus the shared promise, rest and footer. I checked them against:

- `translations/source/en-US.json`
- `TRANSLATION_GUIDE.md` and `AGENTS.md` ("Family copy pattern")
- the in-app strings in `core/ui`, `games/{klondike,spider,freecell,blackjack,holdem}/…/values-{uk,pl,sv,nb,da,fi}`

The edits were applied by a script, with each change matched exactly once. The JSON was rewritten with `ensure_ascii=False, indent=2`.

**Limits after the edits** (`check_drafts.py`): 0 problems. The remaining warnings are expected:

- number formats: `23 675`, `23.675`, `2 000`, `2000`
- the uk and fi Hold'em short descriptions no longer contain "5" (see the compromises below)

Every title is at most 30 characters, every short description at most 80, and every composed full description at most 2,061 of 4,000.

## Checks that held in every language

These are present in all 30 listings:

- **Family promise:** free; no ads, in-app purchases or subscriptions; no accounts, tracking, data collection or sharing, leaderboards or online features; private, local progress.
- **Rest reminders:** optional, with no medical claims.
- **Blackjack and Hold'em disclosures:**
  - virtual chips with no monetary value
  - no real-money bets, deposits, chip purchases, cash-outs or prizes
  - computer dealer or computer opponents only; no games against real people
  - 18+
- **Klondike:** the certified Trivial–Expert deals and the uncertified Insane level.
- **Hints:** bounded; they do not guarantee wins; the Hold'em hint cannot see hidden cards.
- **Numbers:** 3:2, six decks, 1/3, 1/2/4 suits, 23,675, 2,000, six levels, five opponents, 30 languages.
- **Wording:** no "best", ranking, award, competitor, urgency, price or promotion wording, and no short description says "free".
- **Battery:** "battery-conscious" is never strengthened. It reads: дбайливий до батареї, z myślą o baterii, batterimedveten, batteribevisst, batteribevidst, akkua huomioiva.
- **"Simple":** it always reads as uncluttered (Простий, Prosty, Enkel, Selkeä), never "simplistic".
- **Titles:** they follow `<name>: FinitePlay`, `Blackjack 21: FinitePlay`, `Texas Holdem Poker: FinitePlay`.

## Policy used for the short descriptions

Every short description must keep "no ads" and "offline". Where a complete version did not fit in 80 characters, I used this order of priority:

1. For Blackjack and Hold'em, **virtual chips** win over hints or the opponent count, because they are the gambling disclosure.
2. For FreeCell, **solvable deals** win over hints.

---

## uk (Ukrainian): rating 3.5/5

Three short descriptions (FreeCell, Blackjack, Hold'em) had dropped "без реклами" (no ads), which is a required claim. The body text was sound apart from a few calques and some divergence from the app's terms.

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| shared | rest | `Локальні рахунки й статистика` -> `Локальні результати й статистика` | "рахунки" also reads as bills or accounts, right after the promise says "без облікових записів" (no accounts). |
| klondike, freecell | body | `автоматичні ходи на основи` -> `автоматичні ходи у фундації` | The app's term is "фундація" (setting "Автоматичні ходи у фундацію"; help "чотири фундації"). |
| spider | body | `Кожна включена початкова роздача` -> `Кожна початкова роздача в грі` | "включена" is a calque of "included" and can read as "switched on". |
| freecell | body | `Кожна включена роздача` -> `Кожна роздача в грі` | Same reason. |
| freecell | intro | `Бачте кожну карту й плануйте…` -> `Усі карти відкриті: плануйте…` | "Бачте" is an awkward imperative. The new wording keeps the meaning: every card is visible, so plan. |
| blackjack, holdem | intro | `а справжніх ставок, внесків,` -> `а ставок на справжні гроші, депозитів,` | Restores "real-money bets" exactly. "Депозит" is the standard term for a deposit. |
| blackjack | body | `на м’якому 17` -> `на м’яких 17` | Matches the app's help_dealer ("на м’яких 17"). |
| blackjack | body | `перевірка дилера` -> `перевірка дилером прихованої карти` | "Dealer peek" was unclear. The new wording uses the app's "прихована карта" (hole card). |
| blackjack, holdem | body | `матчів із живими людьми` -> `ігор із живими людьми` | "матч" is a sports word; "гра" is natural for cards. |
| holdem | body | `колл, бет, рейз` -> `колл, ставка, рейз` | The app's Bet button is "Ставка". |
| freecell | short | `…розв’язувані роздачі, підказки` -> `…розв’язувані роздачі, без реклами` (80) | Restores the required "no ads". |
| blackjack | short | `…віртуальні фішки, підказки` -> `…віртуальні фішки, без реклами` (76) | Restores "no ads". |
| holdem | short | `…віртуальні фішки, 5 суперників` -> `…віртуальні фішки, без реклами` (73) | Restores "no ads". |

## pl-PL (Polish): rating 4/5

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| klondike | intro | `dobierz wygodny poziom trudności` -> `ustaw wygodny poziom trudności` | "dobierz" clashed with "dobieranie" (drawing cards) in the same sentence. |
| spider, freecell | body | `Każde zawarte rozdanie` -> `Każde dostępne w grze rozdanie` | "zawarte" is a calque of "included". |
| blackjack, holdem | intro | `a prawdziwych zakładów, wpłat,` -> `a zakładów o prawdziwe pieniądze, wpłat,` | Says "real-money bets" exactly. |
| blackjack | body | `na miękkich 17` -> `na miękkiej 17` | Matches the app ("staje na każdej 17, także miękkiej"). |
| blackjack | body | `sprawdzenie karty przez krupiera` -> `sprawdzenie zakrytej karty przez krupiera` | Uses the app's term "zakryta karta" (hole card). |
| blackjack, holdem | body | `nie ma meczów z prawdziwymi ludźmi` -> `nie ma rozgrywek z prawdziwymi ludźmi` | "mecz" is a sports word. |
| holdem | intro | `turniej Texas Hold'em przy sześciu miejscach` -> `…przy stole na sześć miejsc` | Fixes an unidiomatic "at six seats". |
| freecell | short | `…rozwiązywalne rozdania, podpowiedzi` -> `…rozwiązywalne rozdania, bez reklam` (77) | Restores the required "no ads". |

## sv-SE (Swedish): rating 4/5

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| klondike, freecell | body | `basbuntarna` -> `hemhögarna` | The app's foundation term is "hemhögarna". |
| blackjack | body | `givarens kontroll av kort` -> `givarens kontroll av det dolda kortet` | Uses the app's "dolda kort" (hole card); "dealer peek" was unclear. |
| blackjack, holdem | body | `inga matcher mot riktiga människor` -> `inga partier mot…` | "parti" is the card-game word; "match" is sports. |
| holdem | body | `Fold, check, call, bet, raise och all in` -> `…bet, höj och all in` | The app's Raise button is "Höj (till)". The other four match the app. |

## no-NO (Norwegian Bokmål): rating 3.5/5

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| shared | promise | `forblir privat og lokal` -> `forblir private og lokale` | The adjectives must be plural because two nouns are coordinated. |
| shared | rest | `inviterer deg til å ta en pause` -> `oppfordrer deg til…` | "inviterer" is a calque of "invite". |
| klondike | body | `Utdelinger`, `usertifiserte utdelinger`, `En løsbar startutdeling`, `løsbare utdelinger` -> `Giv`, `usertifiserte giv`, `Et løsbart startgiv`, `løsbare giv` | The app calls a deal "giv" (status "Giv %d", "givene") and never uses "utdeling". Also fixes the gender. |
| spider | body | `Hver medfølgende startutdeling` -> `Hvert medfølgende startgiv`; `23 675 … løsbare utdelinger` -> `… løsbare giv` | Same reason. |
| freecell | body, short | `Hver medfølgende utdeling` -> `Hvert medfølgende giv`; `løsbare utdelinger` -> `løsbare giv` | Same reason. The short description goes from 78 to 71. |
| spider | body | `en senere stilling kan ikke lenger være mulig å vinne` -> `en senere stilling er kanskje ikke lenger mulig å vinne` | **Meaning error.** The old text said a later position "cannot be possible to win". The source says it "may no longer be winnable". |
| klondike, freecell | body | `trekk til bunkene` -> `trekk til grunnbunkene` | The app's foundation term is "grunnbunkene". |
| blackjack | body | `dealerens kikk på kortet` -> `dealerens sjekk av det skjulte kortet` | Uses the app's "skjulte kort" (hole card). |
| blackjack, holdem | body | `ingen kamper mot ekte mennesker` -> `ingen partier mot…` | "kamp" means a fight or sports match. |

"radutdelinger" (Spider, dealing a row) was kept, because it names the act of dealing, not a deal.

## da-DK (Danish): rating 4/5

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| shared | rest | `Indstillelige pausepåmindelser` -> `Indstillelige pauseminder` | The app's setting is "Pauseminder". |
| klondike, freecell | body | `træk til bunkerne` -> `træk til hjemmebunkerne` | The app's foundation term is "hjemmebunker". |
| spider | body | `en senere stilling kan ikke længere være mulig at vinde` -> `en senere stilling kan måske ikke længere vindes` | **Meaning error.** The old text said it "cannot be winnable"; the source says it "may no longer be winnable". |
| blackjack | body | `dealerens kig på kortet` -> `dealerens tjek af det skjulte kort` | Uses the app's "skjulte kort" (hole card). |
| blackjack, holdem | body | `ingen kampe mod rigtige mennesker` -> `ingen partier mod…` | "kamp" is a sports or fight word. |

## fi-FI (Finnish): rating 3.5/5

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| shared | rest | `kutsuvat sinut pitämään tauon` -> `kehottavat pitämään tauon` | "kutsua" (invite) is a calque. |
| klondike | intro | `Valitse nostetaanko` -> `Valitse, nostetaanko` | A comma is required before the indirect question. |
| klondike | intro | `käytä Kumoa-, Toista- tai valinnaista vihjettä` -> `käytä Kumoa- ja Toista-toimintoja tai valinnaista vihjettä` | **Grammar.** The hyphenated forms made it read as "Undo hint, Replay hint or optional hint". |
| spider | intro | `käytä Kumoa-, Toista- ja valinnaisia vihjeitä` -> `käytä Kumoa- ja Toista-toimintoja sekä valinnaisia vihjeitä` | Same grammar issue. |
| klondike, freecell | body | `perustappeihin` -> `perustoihin` | "perustappi" is not a solitaire term. The app's help uses "perusta" ("neljä perustaa"). |
| spider | intro, body | `täydellisiä jonoja`, `täydellisiä saman maan jonoja` -> `kokonaisia sarjoja`, `kokonaisia saman maan sarjoja` | "täydellinen" means perfect, not complete. The app calls a run "sarja". |
| freecell | intro, body | `korttijonoja`, `korttijonojen` -> `korttisarjoja`, `korttisarjojen` | Uses the app's term "sarja". |
| blackjack | body | `Harjoittele päätöksiä ota kortti, jää, tuplaa ja jaa valinnaisten…` -> `Harjoittele päätöksiä – ota kortti, jää, tuplaa ja jaa – valinnaisten…` | Without punctuation, the button names ran into the sentence. |
| blackjack | body | `jakajan kortin tarkistus` -> `jakajan piilokortin tarkistus` | Uses the app's "piilokortti" (hole card). |
| blackjack, holdem | body | `otteluita oikeita ihmisiä vastaan` -> `pelejä oikeita ihmisiä vastaan` | "ottelu" is a sports match. |
| holdem | body | `Fold, check, call, bet, raise ja all in` -> `Fold, check, maksu, panostus, korotus ja all in` | The app's buttons are Fold, Check, Maksa, Panosta, Korota and All in. |
| freecell | short | `…ratkaistavat jaot, vihjeet` -> `…ratkeavat jaot, vihjeet, ei mainoksia` (79) | Restores the required "no ads". "Ratkeava" is shorter and the idiomatic word for a solitaire that comes out. |
| blackjack | short | `…pelimerkit, vihjeet, ei mainoksia` -> `…virtuaalimerkit, ei mainoksia` (72) | Restores "virtual" (the gambling disclosure). |
| holdem | short | `…pelimerkit, 5 vastustajaa, ei mainoksia` -> `…virtuaalimerkit, ei mainoksia` (69) | Same reason. |

"Selkeä" (clear, uncluttered) was kept for "Simple". It fits the guide's meaning better than "yksinkertainen", which can suggest "simplistic".

---

## Short-description compromises

Facts that are still missing from a short description. The full description keeps every one of them.

| Locale | Game | Current short (chars) | Dropped | Shortest complete version |
|---|---|---|---|---|
| uk | FreeCell | `Простий офлайн-FreeCell, дбайливий до батареї: розв’язувані роздачі, без реклами` (80) | hints | 90 |
| uk | Blackjack | `Простий офлайн-блекджек, дбайливий до батареї: віртуальні фішки, без реклами` (76) | hints | 86 |
| uk | Hold'em | `Простий офлайн-покер, дбайливий до батареї: віртуальні фішки, без реклами` (73) | 5 opponents | 87 |
| pl-PL | FreeCell | `Prosty FreeCell offline z myślą o baterii: rozwiązywalne rozdania, bez reklam` (77) | hints | 90 |
| pl-PL | Blackjack | `Prosty Blackjack offline z myślą o baterii: wirtualne żetony, bez reklam` (72), unchanged | hints | 85 |
| fi-FI | Blackjack | `Selkeä, akkua huomioiva offline-Blackjack: virtuaalimerkit, ei mainoksia` (72) | hints | 81 |
| fi-FI | Hold'em | `Selkeä, akkua huomioiva offline-pokeri: virtuaalimerkit, ei mainoksia` (69) | 5 opponents | 84 |

Every sv-SE, no-NO and da-DK short description is complete, as are the uk, pl and fi shorts for Klondike and Spider and the pl Hold'em and fi FreeCell shorts.

If the owner prefers to keep the shared "Simple, battery-conscious" opening off one short description, the dropped facts fit again. I did not do this, because it would break the family pattern.

## Needs a native speaker

**uk**
- Klondike title "Пасьянс Клондайк": Ukrainian players often search for "Косинка". This is a product-naming decision.
- The app's "фундація" for foundation is a calque; "база" or "дім" are more usual. The listing now follows the app, so if the term changes, change it in both.
- "Експертного рівня" was checked and is fine: it is the correct genitive of the app's "Експертний". No divergence.
- "Усі карти відкриті:"
- "ставок на справжні гроші, депозитів"
- "дбайливий до батареї"
- "Простий" (it must not read as primitive)

**pl-PL**
- The app uses "Pas" for both Blackjack Stand and Hold'em Fold, and the listing follows it ("dobierz, pas, podwój i rozdziel"). This is flagged in TODO.md.
- Calques left as they are: "zaczynają się ze zweryfikowanym rozwiązaniem" and "prowadzące podpowiedzi".
- "z myślą o baterii"
- "dostępne w grze"

**sv-SE**
- The app's dealer is "Givare", which sits close to "givar" (the plural of "giv", a deal) used in the solitaire listings. Both words are correct Swedish; the app's term was kept.
- Spider title "Spindelpatiens": confirm it against "Spindelharpan", the Windows name. The short description deliberately keeps "Spider".
- "Klondike Patiens" and "FreeCell Patiens" use English-style capitalization; Swedish would normally write "Klondikepatiens".
- "brådskande bonusar"
- "5 rivaler" (the body says "motståndare")
- "höj" in the Hold'em action list

**no-NO**
- "Spider Kabal": confirm it against "Edderkoppkabal".
- The app itself mixes "giv" and "Velg et spill"; the listing now uses "giv".
- "dealerens sjekk av det skjulte kortet"
- "partier"
- "hastebonuser"

**da-DK**
- "Spider Kabale": confirm it against a Danish local name.
- "giv" as the word for a deal
- "dealerens tjek af det skjulte kort"
- "partier"
- "hastebonusser"

**fi-FI**
- "Selkeä" in the short descriptions
- "virtuaalimerkit" as a compound
- "ratkeavat jaot"
- "perustoihin": the app itself is inconsistent here, with the setting saying "siirrot pinoihin" and the help saying "perusta".
- The noun forms "maksu, panostus, korotus" taken from the app's button verbs
- The dash construction in the Blackjack body

**Every language**
- Read the rendered full listing in order: intro, promise, body, rest, footer.
- Confirm that the gambling disclosures read as explicit and natural.

## Summary

1. All 30 listings in uk, pl, sv, nb, da and fi are within the limits (titles ≤30, shorts ≤80, full ≤2,061) and keep every required claim. I made 75 minimal edits, including two meaning errors in nb and da Spider, missing "no ads" in four short descriptions (three uk, one fi), and app-term alignment such as foundation and hole-card terms.
2. Seven short descriptions (in uk, pl and fi) still trade away hints or "5 opponents" to fit, with "no ads" and virtual chips always kept. The table above lists them.
3. Model quality ratings: uk 3.5, pl 4, sv 4, nb 3.5, da 4, fi 3.5. No native-speaker review is planned; the owner accepts the residual language-quality risk.
