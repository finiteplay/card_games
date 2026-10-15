# Google Play listing review, group 4 (hr, sk, ar, iw-IL, hi-IN, id)

Scope: `blackjack/<code>.md` and `holdem/<code>.md` against `source/*.en-US.md`. The terms were checked against the in-app strings (`values-hr`, `-sk`, `-ar`, `-iw`, `-hi`, `-in`) and against core `action_hint`, which labels the Hint button.

Limits were checked after the edits with `listing_limits.py`, and every file passes. TITLE is 23 (Blackjack) or 27 (Hold'em). SHORT is 72-80. FULL is 902-1485.

All 12 FULL texts keep every safety and policy claim from the source:
- offline play
- virtual chips with no monetary value
- a computer dealer (Blackjack) or computer opponents only (Hold'em)
- no ads and no in-app purchases
- no real-money betting, deposits, cash-outs or prizes
- no online play and no matches against real people
- no account (Blackjack); no account or tracking, with progress and statistics kept on the device (Hold'em)

All 6 Hold'em feature lists keep 10 `•` bullets that match the source items. The titles are unchanged ("Blackjack by FinitePlay" and "Texas Hold’em by FinitePlay").

**Hint naming.** In sk, hi and id, the Hint button (core `action_hint`), Help and Stats use one word: Nápoveda, संकेत and Petunjuk. The review strings use another word: "rada", "हिंट" and "Hint". The listings now use the button word, because that is the label players tap. hr (Savjet), ar (تلميح) and iw (רמז) were already consistent.

**Dealer peek.** Every language now uses the in-app word for the hole card. hr has "skrivena karta", sk "skrytá karta", ar "الورقة المخفية", iw "הקלף הנסתר", hi "छिपा पत्ता" and id "kartu tersembunyi". Each one now says "the dealer checks the hidden card".

---

## hr (Croatian)

**Blackjack** (rating 4/5)
- SHORT: "...Bez reklama, kupnji, pravog novca, online" ended in a bare "online". It is now "Offline blackjack, virtualni žetoni. Bez reklama, kupnji i pravog novca." (72).
- "Vuci, Stani, Udvostruči, Podijeli, Osiguranje, provjeru djelitelja i isplate blackjacka 3:2" became "poteze Vuci, Stani, Udvostruči i Podijeli, osiguranje, provjeru djeliteljeve skrivene karte i isplatu 3:2 za blackjack". "provjeru djelitelja" means "a check of the dealer", which is not the peek. The list also mixed button names with nouns.
- SHORT compromise: "multiplayer" was dropped. With all five strong claims, the best candidates were 82-86 characters ("...i online igre", "...i multiplayera"). The line still opens with "Offline", and the FULL text keeps "Bez igranja online i partija protiv stvarnih ljudi".

**Hold'em** (rating 4/5)
- "Savjet-trener" was invented by the first translator and is not a Croatian compound. The bullet now reads "Savjet koji poput trenera objašnjava svoj prijedlog jednostavnim riječima, uz vašu šansu, odnos pota i vjerojatnost…" (in-app: Savjet, šansa, odnos pota).
- "ciljajte da ostanete posljednji igrač" was a calque. It is now "nastojte ostati posljednji igrač".
- SHORT: ", online igre" became " i online igre" (80). It now reads as a complete list item, "no online play". It keeps the claim, but it is not "online players" word for word (a compromise, see below).

## sk (Slovak)

**Blackjack** (rating 4/5)
- SHORT: the bare ", online" was removed. The line is now "Offline blackjack, virtuálne žetóny. Bez reklám, nákupov a pravých peňazí." (74).
- "Ťahať, Stáť, Zdvojiť, Rozdeliť, Poistiť, kontrolu krupiéra a výplaty blackjacku 3:2" became "ťahy Ťahať, Stáť, Zdvojiť a Rozdeliť, poistenie, kontrolu skrytej karty krupiérom a výplatu 3:2 za blackjack". "kontrola krupiéra" means "inspection of the dealer". "Poistiť" is the verb on the button, but the list needs the noun.
- Hint paragraph: "Rada" became "Nápoveda" to match the button, and "čo Rada povedala" became "čo Nápoveda odporučila". "spolu so šancami" became "spolu s pravdepodobnosťami, o ktoré sa opiera", which matches "the odds behind it".
- "kartové hry pre pokojnú a súkromnú hru" repeated "hry/hru". It is now "na pokojné a súkromné hranie".
- SHORT compromise: "multiplayer" was dropped. "...pravých peňazí, hry online" is 84 characters. The FULL text keeps "Bez hry online a zápasov proti skutočným ľuďom".

**Hold'em** (rating 4/5)
- "Trénerská Rada" became "Nápoveda v úlohe trénera, ktorá svoje rady vysvetľuje…". In the look-back bullet, "s Radou: čo Rada povedala" became "s Nápovedou: čo Nápoveda odporučila".
- "Hrajte cez rastúce blindy" became "Hrajte pri rastúcich blindoch". "cez" reads as "across".
- SHORT not changed (80): "...pravých peňazí, online hry". After "Bez" this is the genitive "no online play", so it is not a bare "online". "online hráčov" would take 83 characters (a compromise, see below).

## ar (Arabic, RTL)

**Blackjack** (rating 4/5)
- "تتضمن …: اطلب، ابقَ، ضاعف، قسّم، التأمين، فحص الموزّع، ومدفوعات بلاك جاك 3:2" became "تتضمن Blackjack by FinitePlay خيارات اطلب وابقَ وضاعف وقسّم، والتأمين، وتحقّق الموزّع من ورقته المخفية، ودفع 3:2 عند البلاك جاك". "فحص الموزّع" reads as "examining the dealer".
- Hint: "مع الاحتمالات" became "مع الاحتمالات التي يستند إليها" (the odds behind it). "بالورقة الظاهرة" became "مع الورقة التي يُظهرها".
- Closing line: "مصمَّمة للعب بهدوء وخصوصية" now gives "designed for", which the old line dropped.
- SHORT not changed (80): "...ومال حقيقي ولعب أونلاين". "multiplayer" is rendered as "online play". A clear "multiplayer" (لعب جماعي / متعدد اللاعبين) also fits only if "أونلاين" goes, and "لعب جماعي" can read as "team play". I kept the clearer claim. SHORT spells "بلاكجاك" as one word, while FULL has "بلاك جاك". The spaced form would make it 81 characters.

**Hold'em** (rating 4/5)
- RTL: line 1 began with the Latin "Texas Hold'em". Wherever the direction is detected per paragraph, that paragraph would be laid out left to right. It now begins "لعبة Texas Hold'em…".
- "نو ليمت" (transliteration) became "بلا حد أقصى للرهان".
- "العب عبر بلايند يرتفع" (a singular calque) became "العب بينما ترتفع قيم البلايند".
- The bullet "…رفع، والكل" ended with the button label alone, which reads as "and everything". It is now "…رفع، والمراهنة بكل الرقائق (الكل)".
- "البوت الجانبي" became "البوتات الجانبية" (side pots, plural).
- SHORT not changed (80). It is faithful ("ولاعبين أونلاين").

## iw-IL (Hebrew, RTL)

**Blackjack** (rating 4/5)
- SHORT: "משחק מקוון" (online game, and loose) became "מולטיפלייר" (multiplayer). This restores the source claim (79).
- RTL: line 16 began with the Latin "Blackjack by FinitePlay". It now begins "המשחק Blackjack by FinitePlay כולל את הפעולות קלף, עצור, הכפל ופצל, ביטוח, בדיקת הקלף הנסתר של המחלק ותשלום 3:2 על בלאק ג'ק". "הצצת המחלק" was a literal "dealer's peek". "the actions" was added so that the button labels "קלף, עצור" are not read as ordinary nouns.
- RTL: the closing line began with "FinitePlay". It is now "ב-FinitePlay יוצרים משחקי קלפים…".

**Hold'em** (rating 4/5)
- SHORT: "שחקנים מקוונים" became "שחקנים אונליין". It now matches "אונליין/אופליין" in FULL, at the same length (79).
- RTL: line 1 became "פוקר Texas Hold'em אופליין…", and the closing line became "ב-FinitePlay יוצרים…".
- "שחקו דרך בליינדים עולים… וכוונו להיות" was a calque. It is now "שחקו כשהבליינדים עולים… ושאפו להיות".
- "רמז מאמן" became "רמז בסגנון מאמן".

## hi-IN (Hindi)

**Blackjack** (rating 4/5)
- SHORT: "ऑनलाइन खेल" became "मल्टीप्लेयर". This restores "multiplayer" (80).
- "डीलर पीक" (transliteration) became "डीलर का छिपा पत्ता जाँचना". "3:2 ब्लैकजैक पेआउट" became "ब्लैकजैक पर 3:2 भुगतान".
- Hint paragraph: "हिंट" became "संकेत", the button word. "समीक्षा बताती है" became "समीक्षा तुलना करती है" ("compares", as in the source).

**Hold'em** (rating 4/5)
- "कोचिंग हिंट" became "कोच जैसा संकेत". In the look-back bullet, "हिंट" became "संकेत", and "क्या होता" became "क्या हुआ होता" ("what would have happened").
- SHORT not changed (79). It is faithful.

## id (Indonesian)

**Blackjack** (rating 4/5)
- SHORT: "main online" became "multipemain". This restores "multiplayer" with the standard Play term (80).
- "Asuransi, intipan Bandar, dan pembayaran blackjack 3:2" became "asuransi, pemeriksaan kartu tersembunyi oleh bandar, dan pembayaran 3:2 untuk blackjack". "intipan" is not blackjack language.
- Hint paragraph: "Hint" became "Petunjuk", the button word. "Bandar" is now lowercase mid-sentence, as in-app.
- Closing line: "dirancang untuk" now gives "designed for".

**Hold'em** (rating 4/5)
- SHORT: a final period was added (78). It is faithful.
- "Mainkan melalui blind yang terus naik" became "Bermainlah saat blind terus naik".
- "Hint pelatih" became "Petunjuk ala pelatih", and the look-back bullet now says "Petunjuk".

---

## SHORT compromises
| Lang | Game | Final SHORT | Dropped or changed |
|---|---|---|---|
| hr | Blackjack | Offline blackjack, virtualni žetoni. Bez reklama, kupnji i pravog novca. | "multiplayer" dropped (best fit 82) |
| hr | Hold'em | …pravog novca i online igre | "online players" rendered as "online play" ("online igrača" = 82) |
| sk | Blackjack | Offline blackjack, virtuálne žetóny. Bez reklám, nákupov a pravých peňazí. | "multiplayer" dropped (best fit 84) |
| sk | Hold'em | …pravých peňazí, online hry | "online players" rendered as "online play" ("online hráčov" = 83) |
| ar | Blackjack | …ومال حقيقي ولعب أونلاين | "multiplayer" rendered as "online play" |
| ar, iw, hi, id | other | | none; the line is faithful |

## Needs a native speaker
- **sk/hi/id, app-wide:** the Hint button says Nápoveda, संकेत and Petunjuk, while the review strings (`review_title` and `review_*`, and in hi/id `help_hint_note`) say "rada", "हिंट" and "Hint". The listings follow the button. The in-app review strings should be aligned too.
- **iw:** the listing says "צ'יפים", while the in-app strings consistently say "אסימונים". I kept "צ'יפים" because it is common poker usage, it helps search, and the SHORT line has no room for the longer word. Confirm the choice.
- **iw:** is "מולטיפלייר" acceptable in store copy, or is "מרובה משתתפים" preferred? The formal form does not fit in SHORT.
- **ar:** is "لعب أونلاين" in Blackjack SHORT acceptable for "multiplayer"? Is "ألعاب ورق مركّزة" right for "focused"? Is "بلا حد أقصى للرهان" right for "no-limit"?
- **hr:** check that "provjeru djeliteljeve skrivene karte" and "Savjet koji poput trenera…" read naturally. Also confirm "stack" in "s istim stackom" (it matches the in-app "kratkim stackom").
- **sk:** check "kontrolu skrytej karty krupiérom" and "Nápoveda v úlohe trénera".
- **hi:** check "डीलर का छिपा पत्ता जाँचना" next to "कार्ड लें" in the same list (पत्ता/कार्ड mix, both used in-app). Should "बेसिक-स्ट्रैटेजी" be "बुनियादी रणनीति"?
- **id:** confirm that "multipemain" and "Petunjuk ala pelatih" read naturally.

## Ratings
hr 4/5, sk 4/5, ar 4/5, iw-IL 4/5, hi-IN 4/5, id 4/5. The drafts were faithful and kept every claim. The main problems were bare or loose "online" in SHORT, a literal "dealer peek", one invented compound (hr), Latin-first paragraphs in RTL, and the Hint name not matching the button in sk/hi/id.

## Summary
- 12 files reviewed and edited in place. All pass the limits (TITLE 23/27, SHORT ≤ 80, FULL ≤ 1485), and all FULL texts keep every safety and policy claim. The Hold'em feature lists keep their 10 bullets.
- SHORT: "multiplayer" was restored in iw, hi and id Blackjack. hr and sk Blackjack now end cleanly without the bare "online", with "multiplayer" dropped. hr and sk Hold'em use "online play". Hebrew "מקוון" is gone.
- Fixed: dealer-peek wording (6 langs), the hr "Savjet-trener", Latin-first RTL lines (ar, iw), and the Hint name, now the button word (sk, hi, id). Open: the in-app Hint naming in sk/hi/id and the Hebrew chips term.
