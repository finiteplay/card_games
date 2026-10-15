# Store listing review, group 3: tr-TR, el-GR, cs-CZ, ro, hu-HU, bg

Scope: `blackjack/<code>.md` and `holdem/<code>.md` (TITLE, SHORT, FULL), checked against `source/*.en-US.md` and the reviewed in-app strings (`values-tr/-el/-cs/-ro/-hu/-bg`, plus `core/ui` for the Hint label).
Edits were kept small. Titles were already correct in every file ("Blackjack by FinitePlay", "Texas Hold’em by FinitePlay").

## Limits after review (listing_limits.py)

| Game | Lang | Title | Short | Full |
|---|---|---|---|---|
| Blackjack | tr-TR | 23 | 80 | 1112 |
| Blackjack | el-GR | 23 | 80 | 1252 |
| Blackjack | cs-CZ | 23 | 74 | 1070 |
| Blackjack | ro | 23 | 76 | 1149 |
| Blackjack | hu-HU | 23 | 75 | 1207 |
| Blackjack | bg | 23 | 70 | 1111 |
| Hold'em | tr-TR | 27 | 80 (was 86) | 1368 |
| Hold'em | el-GR | 27 | 79 | 1601 |
| Hold'em | cs-CZ | 27 | 70 | 1370 |
| Hold'em | ro | 27 | 72 | 1572 |
| Hold'em | hu-HU | 27 | 71 | 1504 |
| Hold'em | bg | 27 | 79 | 1457 |

All files are now within the limits. Every FULL text keeps all the safety and policy claims: offline; virtual chips with no monetary value; computer dealer or opponents only; no ads; no in-app purchases; no real-money betting, deposits, cash-outs or prizes; no online play or matches against real people; no account. Hold'em also keeps "no tracking" and "progress and statistics stay on the device". All 6 Hold'em lists have the same 10 bullets as the source.

## SHORT policy

Where SHORT said "money", it now says "real money" in all 12 files. In several languages the exact "real money **and** multiplayer/online players" wording does not fit in 80 characters. In those, "real money" was kept and "multiplayer/online" was dropped, as the brief's priority order asks. "Offline" stays at the start of every line. These versions were measured and are all over 80, or only fit with a broken bare "online":
cs "…skutečných peněz, online hry." 82; ro "…bani reali, joc online." 81 (Hold'em) and 85 (Blackjack); hu "…valódi pénz, online játék." 83; bg Blackjack "…реални пари, онлайн игра." 82; el "…πραγματικά χρήματα" + "online" 83–84; tr Blackjack "…gerçek para, çok oyunculu yok." 83.

---

## tr-TR (Turkish)

### Blackjack
| Section | Old | New | Why |
|---|---|---|---|
| FULL | krupiyenin kartına bakması | krupiyenin kapalı kartını kontrol etmesi | "Looking at his card" was vague. Dealer peek is "the dealer checks the hole card"; "kapalı kart" is the app's hole-card term |

SHORT unchanged (80): "…Reklam, satın alma, gerçek para, online yok." **Compromise:** "multiplayer" is rendered as "online". "çok oyunculu" / "multiplayer" pushes the line to 82–83.

### Hold'em
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | Çevrimdışı poker, sanal fişler. Reklam, satın alma, gerçek para, çevrimiçi oyuncu yok. (86) | Çevrimdışı poker, sanal fiş. Reklam, satın alma, gerçek para, online oyuncu yok. (80) | Over the limit. Collective singular "sanal fiş" plus "online" (as in the Blackjack SHORT) keep every claim, including "online players" |
| FULL bullet | Pas, Check, Call, Bet, Raise ve All in | Fold, Check, Call, Bet, Raise ve All in | The in-app Fold button is now "Fold" (strings review). "Pas" also reads as *check* |
| FULL bullet | Tavsiyesini sade sözlerle açıklayan bir İpucu | …açıklayan, koç gibi bir İpucu | "coaching" had been dropped |

Rating: **4/5**. Accurate and natural. The only loss is the SHORT compromise in Blackjack.

## el-GR (Greek)

### Blackjack
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …Χωρίς διαφημίσεις, αγορές, χρήματα, online. | …Χωρίς διαφημίσεις, αγορές, αληθινά χρήματα. (80) | Restores "real money". "πραγματικά χρήματα" (the FULL term) is 83, so the shorter synonym "αληθινά" is used |
| FULL | …και μόνο με κράτη υπολογιστή. | …και μόνο με τον υπολογιστή ως κράτη. | "κράτη" without an article is ambiguous with "κράτη" = "states". It now reads "with the computer as the dealer" |
| FULL | έλεγχο της κάρτας από τον κράτη | έλεγχο της κλειστής κάρτας από τον κράτη | Peek: "checking the card" did not say which card. "κλειστή κάρτα" is the app's hole-card term |

**Compromise:** "multiplayer" is dropped from SHORT. "Offline" at the start covers it, and the FULL text states it.

### Hold'em
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …αγορές, χρήματα, online. | …αγορές, πραγματικά χρήματα. (79) | Restores "real money" |

**Compromise:** "online players" is dropped from SHORT; it is covered by "offline" and the FULL text.

Rating: **4/5**. The FULL texts were good. The listing uses polite plural ("Παίξτε", "σας") while the app now uses informal address. That is a normal store-copy register and was left as is.

## cs-CZ (Czech)

### Blackjack
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …Bez reklam, nákupů, peněz, multiplayeru. | …Bez reklam, nákupů, skutečných peněz. (74) | "real money" restored; the line with multiplayer is 84+ |
| FULL | Líznout, Stát, Zdvojit, Rozdělit, Pojistit, nahlédnutí krupiéra do karty | Líznout, Stát, Zdvojit, Rozdělit, pojištění, kontrolu skryté karty krupiérem | Peek: "nahlédnutí do karty" was a calque. "skrytá karta" is the app's term. "Pojistit" is not a button; the app's term is "Pojištění" |
| FULL | jak často krupiér s ukázanou kartou přetáhne | …s danou odkrytou kartou přetáhne | "ukázaná karta" is unidiomatic |
| FULL | Po kole s nápovědou | Po kole s Nápovědou | Feature name capitalised, as in the rest of the text |

### Hold'em
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | Poker offline s virtuálními žetony. Bez reklam, nákupů, peněz a online hráčů. | Offline poker, virtuální žetony. Bez reklam, nákupů, skutečných peněz. (70) | "real money" restored; the line with online players is 81–84. Now parallel to the Blackjack SHORT |
| FULL bullet | …po každé hře s nápovědou | …s Nápovědou | Capitalisation |

**Compromise (both games):** "multiplayer" / "online players" is dropped from SHORT; it is covered by "Offline" and the FULL text.

Rating: **4/5**.

## ro (Romanian)

### Blackjack
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …Fără reclame, achiziții, bani, joc online. | …Fără reclame, achiziții sau bani reali. (76) | "real money" restored; with multiplayer or online it is 81–86 |
| FULL | verificarea cărții de către dealer | verificarea cărții ascunse de către dealer | Peek: the card in question is the hole card ("cartea ascunsă" in the app) |
| FULL | jocuri de cărți concentrate | jocuri de cărți axate pe esențial | "concentrate" reads as "concentrated" (as in juice), not "focused" |

### Hold'em
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …Fără reclame, achiziții, bani, jucători online. | …Fără reclame, achiziții sau bani reali. (72) | "real money" restored; with online players it is 81+ |
| FULL bullet | Fold, Check, Call, pariu, Raise și All in | Fold, Check, Call, Pariază, Mărește și All in | Match the app buttons (action_bet "Pariază", action_raise "Mărește la") |
| FULL bullet | decontarea potului lateral | decontarea poturilor laterale | Plural, as in the source and the app ("poturi laterale") |
| FULL | Joacă prin blindurile care cresc | Joacă cu blinduri tot mai mari | Literal "play through" was unnatural |

**Compromise (both games):** "multiplayer" / "online players" is dropped from SHORT; it is covered by "offline" and the FULL text.

Rating: **4/5**.

## hu-HU (Hungarian)

### Blackjack
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …Nincs reklám, vásárlás, pénz, online. | …Nincs reklám, vásárlás, valódi pénz. (75) | "real money" restored; with multiplayer it is 86–88 |
| FULL | A FinitePlay Blackjack tartalmazza | A Blackjack by FinitePlay tartalmazza | Product name as in the title and source |
| FULL | az osztó lapnézését | az osztó rejtett lapjának ellenőrzését | "lapnézés" is an invented word. Now "the dealer checking his hidden card"; "rejtett lap" is the app's term |
| FULL | A FinitePlay összpontosított kártyajátékokat készít, nyugodt, privát játékhoz. | A FinitePlay letisztult kártyajátékokat készít nyugodt, privát játékhoz. | "összpontosított" (concentrated) is unnatural for "focused" |

### Hold'em
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …Nincs reklám, vásárlás, pénz, online játékos. | …Nincs reklám, vásárlás, valódi pénz. (71) | "real money" restored; with online players it is 83–87 |
| FULL | hatszékes versenyen | hatfős versenyen | "hatszékes" (six-chaired) is unidiomatic |
| FULL | Minden hely … Játssz az emelkedő vakokon át | Minden játékos … Játssz egyre emelkedő vakokkal | Natural phrasing ("every player starts with…", "play with rising blinds") |

**Compromise (both games):** "multiplayer" / "online players" is dropped from SHORT; it is covered by "Offline" and the FULL text.

Rating: **3.5/5**. Several stiff calques ("lapnézés", "hatszékes", "összpontosított") were fixed. The register still needs a native read.

## bg (Bulgarian)

### Blackjack
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …Без реклами, покупки, пари, мултиплейър. | …Без реклами, покупки и реални пари. (70) | "real money" restored; with multiplayer or online play it is 82 |
| FULL | само с дилър компютър | само с компютърен дилър | Natural adjective order |
| FULL | Без игра онлайн | Без онлайн игра | Natural word order |
| FULL | надзърване на дилъра | проверка на скритата карта от дилъра | Peek: "the dealer's peeping" is an unidiomatic calque. Now "the dealer checks the hidden card" ("скритата карта" in the app) |
| FULL | Учете се, докато играете, с по желание Подсказката. | Учете се, докато играете, с Подсказката, която включвате по желание. | Ungrammatical "с по желание Подсказката" |
| FULL | След ръка с подсказка | След ръка с Подсказка | Feature name capitalised |

### Hold'em
| Section | Old | New | Why |
|---|---|---|---|
| SHORT | …Без реклами, покупки, пари, онлайн играчи. (74) | …Без реклами, покупки, реални пари, онлайн игра. (79) | "real money" restored. "онлайн играчи" makes it 81, so "онлайн игра" (online play, the FULL wording) is used |
| FULL | опоненти компютър / пет опонента компютър (3×) | компютърни опоненти / пет компютърни опонента | Natural adjective form |
| FULL | Без игра онлайн | Без онлайн игра | Word order |
| FULL | Играйте през растящите блайндове | Играйте при растящи блайндове | Literal "through" |
| FULL bullet | ръка с подсказка: | ръка с Подсказка: | Capitalisation |

**Compromise:** in the Blackjack SHORT, "multiplayer" is dropped (covered by "Офлайн" and FULL). In the Hold'em SHORT, "online players" became "online play".

Rating: **3.5/5**. Repeated noun-noun calques ("опоненти компютър") and one ungrammatical sentence were fixed.

---

## Hint copy check (all 6)

- **Blackjack:** every language keeps the full hint paragraph. It says the hint suggests the basic-strategy move, explains why in plain words with the odds, gives the dealer bust rate for the up card and the risk of hitting, and offers a short look back after a hinted round that compares the outcome with the advice.
- **Hold'em:** both hint bullets are present in every language: equity, pot odds, the chance that an opponent already holds a better hand, and the look back with what the Hint said, what you did and what would have happened.
- **Terms:** the Hint name matches the app in every language: İpucu, Υπόδειξη, Nápověda, Indiciu, tipp, Подсказка. So do the terms for equity and pot odds and for the bust verbs: batmak, υπέρβαση, přetáhnout, depășește, bukik, прехвърля.

## Needs a native speaker

- **el:** "Κράτης" for the blackjack dealer (as in the app). Confirm that Greek players don't expect "ντίλερ". "μόνο με τον υπολογιστή ως κράτη" was reworded to avoid the "κράτη" = "states" ambiguity.
- **el:** "τακτοποίηση πλευρικών ποτ" (side-pot settlement). Possibly "μοιρασιά".
- **el:** "αληθινά χρήματα" (Blackjack SHORT) vs "πραγματικά χρήματα" (Hold'em SHORT and FULL). Both are natural; the shorter one is used only where needed to fit.
- **tr:** "online yok" / "online oyuncu yok" in SHORT, beside "Çevrimiçi" in FULL. Confirm that the loanword is acceptable store copy.
- **tr:** whether players expect "krupiyenin blackjack kontrolü" or "kapalı kartını kontrol etmesi" for peek.
- **cs:** "kontrolu skryté karty krupiérem" for peek. Also "šance banky" for pot odds (app term).
- **ro:** "axate pe esențial" for "focused". "Un Indiciu de antrenor" for "a coaching Hint".
- **hu:** "az osztó rejtett lapjának ellenőrzése" for peek (it replaces the invented "lapnézés"); some players may know "peek". "letisztult" for "focused". "Osztás" for split (the app term, but it also means "deal").
- **bg:** "проверка на скритата карта от дилъра" for peek. "фокусирани игри" for "focused games".
- **All:** the listings use formal/polite address (except ro and hu, which are informal like their apps). tr and el now differ from their apps, which switched to informal. This is acceptable for store copy but is the owner's choice.

## Ratings

| Lang | Blackjack | Hold'em |
|---|---|---|
| tr-TR | 4 | 4 |
| el-GR | 4 | 4 |
| cs-CZ | 4 | 4 |
| ro | 4 | 4 |
| hu-HU | 3.5 | 3.5 |
| bg | 3.5 | 3.5 |

## Summary

All 12 listings are within limits (tr-TR Hold'em SHORT 86 → 80). Every FULL text keeps every policy claim. Every SHORT now says "real money", and "offline" stays at the start of every SHORT line.
"Multiplayer/online players" could not be kept in SHORT in el, cs, ro, hu or bg Blackjack (tr uses "online"; bg Hold'em uses "online play"). The FULL text states it in every language.
The invented peek terms (hu "lapnézés", el "έλεγχο της κάρτας", bg "надзърване", cs "nahlédnutí") now say "dealer checks the hole card" using each app's term. Bet/raise/fold labels match the reviewed app strings (tr Fold, ro Pariază/Mărește).
