# Store listing review v2, group 3: tr-TR, el-GR, cs-CZ, ro, hu-HU, bg

**Status: machine-translated and model-reviewed; no native-speaker review is planned.** The owner accepts the remaining language-quality risk. This report records model QA and automated checks; it does not claim native-speaker validation.

Scope: `translations/drafts/<code>.json` (promise, rest, footer, and title / shortDescription / intro / body for all five games), checked against `translations/source/en-US.json`, `TRANSLATION_GUIDE.md`, `AGENTS.md` (family copy pattern), and each language's in-app strings (`games/*/app/src/main/res/values-LOC/`, plus `core/ui` for Undo, Replay, Hint, themes and rest reminders). The full description was read in exporter order (intro, promise, body, rest, footer).

## Limits after review

All 30 listings pass `check_drafts.py` (0 problems). Remaining warnings are expected: `23 675` / `23.675` and `2 000` / `2.000` are local number formats, and "5" is missing from the el, cs, hu and bg Hold'em short descriptions (see the short-description compromises below; the full text says "five" in words).

Title / short / full character counts:

| Lang | Klondike | Spider | FreeCell | Blackjack | Hold'em |
|---|---|---|---|---|---|
| tr-TR | 30 / 77 / 1609 | 28 / 77 / 1550 | 30 / 79 / 1605 | 24 / 79 / 1396 | 30 / 77 / 1926 |
| el-GR | 30 / 77 / 1847 | 28 / 78 / 1775 | 30 / 77 / 1891 | 24 / 79 / 1698 | 30 / 80 / 2270 |
| cs-CZ | 30 / 73 / 1544 | 28 / 71 / 1431 | 30 / 70 / 1518 | 24 / 79 / 1358 | 30 / 75 / 1841 |
| ro | 30 / 79 / 1684 | 28 / 76 / 1609 | 30 / 75 / 1708 | 24 / 74 / 1534 | 30 / 80 / 2124 |
| hu-HU | 30 / 72 / 1773 | 28 / 73 / 1621 | 30 / 63 / 1740 | 24 / 76 / 1568 | 30 / 72 / 2044 |
| bg | 28 / 80 / 1700 | 26 / 80 / 1602 | 28 / 69 / 1725 | 23 / 76 / 1471 | 30 / 73 / 2014 |

## Checks that passed in all six languages

- Every claim listed in the guide is present in every full text: free; no ads, in-app purchases or subscriptions; no accounts, tracking, data collection or sharing, leaderboards or online features; progress and statistics on the device; offline; virtual chips with no monetary value; no real-money bets, deposits, chip purchases, cash-outs or prizes; computer dealer / five computer opponents only, no matches against real people; 18+; certified Trivial-to-Expert deals versus the uncertified Insane level; a solvable start can still reach a dead end; hints are bounded and not a guarantee; Hold'em hints cannot see hidden cards; optional rest reminders with no medical claim; 3:2; draw 1/3; 1/2/4 suits; 23,675 and 2,000 deals; 30 interface languages.
- No "best", ranking, award, competitor, urgency, price or promotion wording. No short description says "free". No short description strengthens the battery wording (no "long battery life", "optimized", "saving" or numbers).
- Titles follow the family pattern. The solitaire titles keep the English "Solitaire" in tr, el, cs and ro, and use the local word in hu ("Pasziánsz") and bg ("Пасианс"). See the native-speaker lists.

## Decision on app terms that are mistranslations

The brief says to keep the app's term when it is odd and to flag it. I did that for odd but correct terms (cs "sbírky", hu "alap", el "Παράλογο", bg mixed-gender level names). In three cases, though, the app string has the wrong meaning, and copying it would make the listing describe a different feature. There the listing keeps the correct word, and the app string should be fixed:

- **tr** `setting_automatic_moves` = "Otomatik **vakıf** hamleleri" (Klondike, FreeCell). "Vakıf" means a charitable foundation. The Klondike accessibility strings already say "temel" for foundation, and the listing uses "temel yığın".
- **el** `core/ui setting_rest_reminder` = "**Αναμνήσεις** διαλείμματος" ("memories of a break"). It should be "Υπενθυμίσεις διαλείμματος", the listing's term.
- **ro** `core/ui setting_rest_reminder` = "**Amintiri** pentru pauză" ("memories"). It should be "Mementouri de pauză", the listing's term.

---

## tr-TR (Turkish)

### Changes

| Game | Field | Old | New | Reason |
|---|---|---|---|---|
| shared | rest | Aciliyet bonusu ya da süreli günlük ödül yoktur. | Seni acele ettiren bonuslar ya da süreli günlük ödüller yoktur. | "Aciliyet bonusu" is a calque that reads as "emergency bonus"; now "bonuses that make you hurry" |
| FreeCell | short | …FreeCell: çözülebilir, ipucu, reklamsız | …FreeCell: çözülebilir eller, reklamsız (79) | "çözülebilir" had no noun, so it read as "(the game is) solvable", which overstates the claim. The English says "solvable deals"; "çözülebilir eller" plus "ipucu" is 86 |
| FreeCell | body | yasal kapasiteyi oyun senin için hesaplar | kurallara göre kaç kart taşıyabileceğini oyun senin için hesaplar | "yasal kapasite" is a calque ("lawful capacity"); now "how many cards you may move under the rules" |
| Hold'em | body | Yükselen blind'lar | Yükselen blindlar | Same spelling as the app ("Blindlar"); the in-app review removed apostrophes from common-noun loanwords |

### Short-description compromises
- FreeCell: "hints" dropped so that "solvable hands" can be stated correctly (see above). The full text describes the hints.

### Needs a native speaker
- Foundation: listing "temel yığın" against the app setting "vakıf" (an app bug, see above). Confirm "temel" (or "temel yığın") is the word Turkish solitaire players know.
- "Solitaire" in the titles and intros, as opposed to a Turkish word. Most Turkish players appear to search "Solitaire", but confirm.
- "Sade, pil kullanımını gözeten" for "Simple, battery-conscious": confirm it reads as a design aim, not a battery-saving promise.
- Spider "renk" for suit (matches the app); "Papaz'dan As'a".
- Blackjack "batma riski" (app "Battı"): many players say "patlamak". "yumuşak 17" (app "Yumuşak"): many players keep "soft 17".
- Hold'em uses English poker words (Fold, check, call, bet, raise, all-in, equity, pot odds) as the app does. Confirm this is acceptable in store copy.
- "gerçek kişilere karşı maç yoktur": "maç" for a card game; "oyun" may read better.
- The listing uses informal "sen", like the app.

### Rating
4/5. The text is natural and every claim is there. The fixes were one calque in the shared rest paragraph, one short description that overstated a claim, and one calque in FreeCell.

---

## el-GR (Greek)

### Changes

| Game | Field | Old | New | Reason |
|---|---|---|---|---|
| shared | rest | μπόνους επείγοντος | μπόνους που σας πιέζουν χρονικά | "επείγον" reads as "emergency"; now "bonuses that put you under time pressure" |
| shared | rest | χωρίς πίνακες κατάταξης | χωρίς ανταγωνιστικούς πίνακες κατάταξης | "competitive" was dropped |
| Klondike, Spider, FreeCell | short | με σεβασμό στη μπαταρία | με σεβασμό στην μπαταρία | Spelling: the final -ν stays before μπ |
| Blackjack | short | Απλό offline Blackjack, μπαταρία υπόψη: εικονικές μάρκες, χωρίς διαφημίσεις | Απλό offline Blackjack, σέβεται την μπαταρία: εικονικές μάρκες, χωρίς διαφήμιση (79) | "μπαταρία υπόψη" was telegraphic and unlike the other games. "με σεβασμό στην μπαταρία" makes it 84, so the line uses the verb form of the same idea and the singular "διαφήμιση" (also natural) |
| Hold'em | short | Απλό offline πόκερ, μπαταρία υπόψη: … | Απλό offline πόκερ με σεβασμό στην μπαταρία: εικονικές μάρκες, χωρίς διαφημίσεις (80) | Same battery wording as the solitaires |
| Klondike, FreeCell | body | αυτόματες μεταφορές στις θεμελιώδεις στοίβες | αυτόματες κινήσεις στις βάσεις | "θεμελιώδεις στοίβες" ("fundamental stacks") is a calque; the app says "Αυτόματες κινήσεις βάσης" |
| Klondike, Spider, FreeCell | body | Φωτεινά, σκούρα… θέματα | Ανοιχτά, σκούρα… θέματα | The app's theme name is "Ανοιχτό" |
| Spider | body | να μην είναι πια κερδίσιμη | να μην οδηγεί πια σε νίκη | "κερδίσιμη" is not standard Greek |
| FreeCell | body | υπολογίζει για εσάς τη νόμιμη χωρητικότητα | υπολογίζει για εσάς πόσες κάρτες επιτρέπεται να μετακινήσετε | "νόμιμη χωρητικότητα" is a calque ("lawful capacity") |
| Blackjack | intro | απέναντι σε κράτη υπολογιστή | απέναντι στον υπολογιστή στον ρόλο του κράτη | Without an article, "κράτη" can be read as "states"; also a noun-noun calque |
| Blackjack | body | απέναντι στον κράτη υπολογιστή | απέναντι στον υπολογιστή στον ρόλο του κράτη | Same |
| Blackjack, Hold'em | intro | αγορές μάρκας | αγορές μαρκών | Genitive plural is the natural form |
| Hold'em | body (×2) | την εκτιμώμενη equity / με εκτιμώμενη equity | το εκτιμώμενο equity / με εκτιμώμενο equity | The app treats equity as neuter ("το equity σου") |
| Hold'em | body | Πάσο, τσεκ, κολ, μπετ, ρέιζ… | Πάσο, τσεκ, κολ, ποντάρισμα, ρέιζ… | The app's Bet button is "Πόνταρε", not "μπετ" |
| Hold'em | body | κατάταξη χεριών | κατάταξη συνδυασμών | The app's help tab is "Συνδυασμοί" |

### Short-description compromises
- Klondike (77): "hints" dropped; with "υποδείξεις" the line is 89.
- Spider (78): "hints" dropped; adding it would exceed 80.
- FreeCell (77): "solvable deals" dropped; hints kept.
- Blackjack (79): "hints" dropped; virtual chips and no ads kept.
- Hold'em (80): "5 opponents" dropped; the shortest version with it and "χωρίς διαφημίσεις" is about 90.
- All five use the Latin "offline" to save space; the full text says "χωρίς σύνδεση". Confirm that is acceptable.

### Needs a native speaker
- "Κράτης" for the Blackjack dealer (app term; Hold'em uses "ντίλερ"). Confirm Blackjack players would not expect "ντίλερ".
- Battery wording: "με σεβασμό στην μπαταρία" in four games and "σέβεται την μπαταρία" in Blackjack. Confirm both read naturally and as a design aim, not a battery-saving claim.
- Klondike level names come from the app: "Ασήμαντο" (Trivial, literally "insignificant") and "Παράλογο" (Insane, literally "absurd"). Both are odd; "Πολύ εύκολο" and "Ακραίο" may be better in the app. The listing keeps the app's names.
- App bug: rest reminders are "Αναμνήσεις διαλείμματος" ("memories") in `core/ui`; the listing says "υπενθυμίσεις". See the decision above.
- "Solitaire" in the titles, as opposed to "Πασιέντζα", which many Greek players use.
- "Ρήγα… Άσο" for King and Ace; "Χρώματα" for Spider suits (matches the app).
- "αποδόσεις ποτ" and "πλευρικά ποτ" (app terms); "νικητήριο χέρι" ("νικηφόρο" may be more natural); "αγώνες με πραγματικούς ανθρώπους" ("παρτίδες" may be more natural).
- The listing uses formal "σας"; the app now uses informal address. That is normal for store copy, but it is the owner's choice.

### Rating
3.5/5. Accurate overall, but it had the most calques and term mismatches of the group (foundation, equity gender, bet, theme names, the dealer ambiguity) and inconsistent battery wording.

---

## cs-CZ (Czech)

### Changes

| Game | Field | Old | New | Reason |
|---|---|---|---|---|
| shared | rest | Žádné bonusy za naléhavost ani… | Žádné bonusy, které by tě popoháněly, ani… | "bonusy za naléhavost" is a calque; now "bonuses that would push you on" |
| shared | rest | bez žebříčků | bez soutěžních žebříčků | "competitive" was dropped |
| Klondike, FreeCell | body | automatické přesuny na základní hromádky | automatické přesuny do sbírek | Matches the app setting "Automatické přesuny do sbírek" (flagged below) |
| Spider | body | Tvá rozhodnutí ale pořád záleží: pozdější pozice už nemusí být vyhratelná. | Na tvých rozhodnutích ale pořád záleží: pozdější pozice už nemusí vést k výhře. | Grammar: "záležet" takes "na"; "vyhratelná" is colloquial |
| FreeCell | body | hra za tebe spočítá povolenou kapacitu | hra za tebe spočítá, kolik karet smíš přesunout | "povolená kapacita" is a calque |
| Hold'em | body | odhadnutou equity a pot odds | odhadnutou equity a šance banky (pot odds) | The app says "Šance banky"; the English term is kept in brackets for players who know it |
| Hold'em | body | Nápovědy s odhadnutou equity a pot odds | Nápovědy s odhadnutou equity a šancemi banky | App term |
| Hold'em | body | Hraj přes rostoucí blindy | Hraj se stoupajícími blindy | "přes" is a literal "through" |
| Hold'em | body | vypořádání vedlejších banků | vypořádání vedlejších bank | The app uses feminine "banka" ("Vedlejší banka") |
| Hold'em | body | pořadí rukou | pořadí kombinací | The app's help tab is "Kombinace"; "pořadí kombinací" is the usual Czech term |

### Short-description compromises
- Klondike (73): "hints" dropped; with "nápověda" it is 83.
- Spider (71): "hints" dropped; with "nápověda" it is 81.
- FreeCell (70): "solvable deals" dropped (89 with it); hints kept.
- Blackjack (79): "hints" dropped (89 with it).
- Hold'em (75): "5 opponents" dropped (86 with "5 soupeřů").
- All five would fit with "šetrný k baterii" instead of "ohleduplný k baterii". I did not use it because "šetrný" can read as "battery-saving", which the guide forbids. A native speaker may decide otherwise.

### Needs a native speaker
- Foundation: the app's "sbírky" is unusual for solitaire foundations ("cílové hromádky" or "odkládací balíčky" are more common). The listing follows the app; consider changing both.
- Pot odds: the app's "šance banky" is unusual; Czech players mostly say "pot odds". The listing gives both.
- "ohleduplný k baterii": confirm that it reads naturally.
- "Solitaire" in the titles, as opposed to "Solitér" or "pasiáns".
- "Přehrát znovu" for Replay (app term); "měkkých 17" (the app's label is "Měkká 17"); "zápasy proti skutečným lidem" ("hry" may be more natural).
- The app mixes "banka" and "bank" (see `TODO.md`). The listing now uses "banka" only.

### Rating
4/5. Natural and accurate. The fixes were one grammar error, calques and app-term alignment. The short descriptions lose the most content of the group.

---

## ro (Romanian)

### Changes

| Game | Field | Old | New | Reason |
|---|---|---|---|---|
| shared | rest | Memento-urile de pauză | Mementourile de pauză | DOOM spelling: no hyphen, because the final -o is pronounced |
| shared | rest | bonusuri de urgență | bonusuri care să te grăbească | "urgență" reads as "emergency" |
| Hold'em | short | Poker offline simplu, atent la baterie: jetoane virtuale, fără reclame | Poker offline simplu, atent la baterie: jetoane virtuale, 5 rivali, fără reclame (80) | Restores "5 opponents". "5 adversari" (the help text's word) makes it 83 |
| Blackjack | body | împotriva dealerului computerului | împotriva dealerului controlat de computer | "the computer's dealer" was ungrammatical; now the same wording as the intro |
| Hold'em | body | equity-ul estimat și pot odds | equity-ul estimat și șansele potului (pot odds) | The app says "Șansele potului"; the English term is in brackets |
| Hold'em | body | Joacă prin blinduri în creștere | Joacă pe măsură ce blindurile cresc | "prin" is a literal "through" |
| Hold'em | body | Fold, check, call, bet, raise și all-in | Fold, check, call, pariază, mărește și all in | The app's buttons: Fold, Check, Call, Pariază, Mărește la, All in |
| Hold'em | body | decontarea side pot-urilor | decontarea poturilor laterale | App term ("Pot lateral") |
| Hold'em | body | Indicii cu equity estimat și pot odds | Indicii cu equity estimat și șansele potului | App term |

### Short-description compromises
- FreeCell (75): "hints" dropped (84 with "indicii"); solvable deals kept.
- Blackjack (74): "hints" dropped (83 with "indicii").
- Hold'em: "rivali" is used instead of the app's "adversari" so that it fits.

### Needs a native speaker
- Foundation: "fundații" (app term). Confirm it is what Romanian solitaire players use.
- App bug: rest reminders are "Amintiri pentru pauză" ("memories") in `core/ui`; the listing says "Mementouri". See the decision above.
- "5 rivali" in the Hold'em short description, as opposed to "adversari".
- Deals are called "mâini" (Klondike, FreeCell) and "jocuri" (Klondike, Spider). Consider one word ("împărțiri"?).
- "Indiciile caută în tabla curentă": "pe tabla curentă" may be more natural. "Extragere 1/3" for draw 1/3.
- "17 moale" (app term), "Stai" for Stand, "meciuri cu persoane reale" ("partide" may be more natural).
- "Solitaire" in the titles, as opposed to "pasiențe".

### Rating
4/5. Fluent. The changes were one spelling error, the "emergency" calque, a broken genitive, and app-term alignment in Hold'em.

---

## hu-HU (Hungarian)

### Changes

| Game | Field | Old | New | Reason |
|---|---|---|---|---|
| shared | promise | …privátak és helyiek maradnak. | …privátak maradnak, és helyben tárolódnak. | "helyiek" reads as "locals" (people) |
| shared | rest | versenyző ranglisták | versenyre ösztönző ranglisták | "versenyző" means a competitor (person) |
| all five | short | Egyszerű, akkumulátorra figyelő… | Egyszerű, akkura figyelő… | Needed to fit "virtuális zsetonok" in Blackjack (83 with the long form). Changed in all five so the games read the same |
| Blackjack | short | …offline Blackjack: tippek, reklámmentes | …offline Blackjack: virtuális zsetonok, reklámmentes (76) | Restores "virtual chips", the key gambling disclosure, in place of "hints" |
| Klondike, FreeCell | body | automatikus alapkupac-lépések | automatikus lépések az alapba | Matches the app setting "Automatikus lépések az alapba" (flagged below) |
| FreeCell | intro | Láss minden lapot | Lásd az összes lapot | Natural imperative |
| Blackjack | body | a lapot kérés, megállás, duplázás és felosztás döntéseit | a lapkérés, a megállás, a duplázás és a felosztás közötti döntéseket | "lapot kérés" is ungrammatical as a noun |
| Blackjack | body | a puha 17-nél | a lágy 17-nél | The app says "Lágy" |
| Hold'em | body | Minden hely ugyanakkora… | Minden játékos ugyanakkora… | "Every seat" is unnatural (the same fix was made in review v1) |
| Hold'em | body | és legyél az utolsó játékos az asztalnál | és próbálj utolsóként az asztalnál maradni | "aim to" had been dropped, which made it a command |
| Hold'em | body | Téttartományt szabályozó vezérlők | Tétméret-beállítás | "téttartomány" means a bet range, not bet sizing |

### Short-description compromises
- FreeCell (63): "solvable deals" dropped (84 with "megoldható leosztás"); hints kept.
- Blackjack (76): "hints" dropped so that "virtual chips" fits. No wording with both fits (84 at best).
- Hold'em (72): "5 opponents" dropped (82 at best with "5 ellenfél").

### Needs a native speaker
- "akkura figyelő" (colloquial "akku") in place of "akkumulátorra figyelő". Confirm that the register is acceptable in store copy. If it is not, Blackjack must go back to the long form, and the line must then drop "Egyszerű" or "offline" to keep the virtual chips.
- Foundation: the app's "alap" ("lépések az alapba") is unusual; "alapkupac" or "gyűjtőhely" may be more familiar. The listing follows the app; consider changing both.
- "Pasziánsz" in the titles (assumed to be the search norm) and the capital P ("Klondike pasziánsz" in running text).
- "kifizetések" for cash-outs (it can also mean payouts); "túllépés" for bust (app "Bukott"; players also say "besokall").
- "Felosztás" for Split; "Passz" for Check (app terms).
- "valódi emberek elleni meccsek": "játszmák" may be more natural.

### Rating
3.5/5. Readable and accurate. It had several stiff or wrong word choices (helyiek, versenyző, lapot kérés, téttartomány), and the Blackjack short description had dropped the virtual-chip disclosure.

---

## bg (Bulgarian)

### Changes

| Game | Field | Old | New | Reason |
|---|---|---|---|---|
| Klondike | title | Клондайк Пасианс: FinitePlay | Пасианс Клондайк: FinitePlay (28) | Bulgarian order is the generic noun first ("пасианс Паяк", "пасианс Клондайк"); the old order copied English |
| Spider | title | Spider Пасианс: FinitePlay | Пасианс Spider: FinitePlay (26) | Same |
| FreeCell | title | FreeCell Пасианс: FinitePlay | Пасианс FreeCell: FinitePlay (28) | Same |
| Hold'em | title | Холдем Покер: FinitePlay | Texas Holdem Poker: FinitePlay (30) | "Texas" had been dropped. The Cyrillic "Тексас Холдем Покер: FinitePlay" is 31, one over. The family title fits, and the description already writes "Texas Hold'em" in Latin |
| Klondike | intro | класическия Клондайк Пасианс | класическия пасианс Клондайк | Same word order as the title |
| Spider | intro | в Spider Пасианс | в пасианса Spider | Same |
| FreeCell | intro | Виждайте всяка карта | Вижте всички карти | Natural perfective imperative |
| Hold'em | body | пот одсовете / пот одсове | пот оддс | App spelling ("Пот оддс") |
| Hold'em | body | Играйте през растящи блайндове | Играйте при растящи блайндове | "през" is a literal "through" (the same fix was made in review v1) |
| Hold'em | body | ранг на ръцете | ранг на комбинациите | The app's help tab is "Комбинации" |

### Short-description compromises
- Klondike (80) and Spider (80) keep everything.
- FreeCell (69): "solvable deals" dropped (82 with "решими игри"); hints kept.
- Blackjack (76): "hints" dropped (87 with "подсказки").
- Hold'em (73): "5 opponents" dropped (85 with "5 опонента").

### Needs a native speaker
- Titles: "Пасианс Клондайк / Spider / FreeCell" (the Latin game names match the app names and the descriptions), "Блекджек 21" (Cyrillic, against the family's Latin "Blackjack 21"), and "Texas Holdem Poker" (Latin) as opposed to "Тексас Холдем: FinitePlay" (25, drops "Покер"). Also whether "Паяк" should replace "Spider".
- "Семпъл" for "Simple": it was kept because it means plain or unpretentious, not simplistic. Confirm it reads as positive. "Изчистен" (uncluttered) is 2 characters longer and does not fit in Klondike or Spider.
- Klondike level names come from the app and mix genders: "Тривиална", "Безумна" (feminine) but "Лесно", "Експертно" (neuter). The listing writes "от Тривиална до Експертно" as the app does; the app should be made consistent.
- "мек 17" against the app's "Мека 17"; "Карта / Стоп" for Hit/Stand; aces "ас" against "асо" (in-app open item).
- "Подсказките търсят в текущата маса": "текущата позиция" may be more natural. "победен път" may be "печеливш път".
- The listing uses formal "вие"; the app now uses informal address.

### Rating
3.5/5. The body text is good and every claim is there. The weak points were the titles (copied word order, dropped "Texas") and a few app-term mismatches in Hold'em.

---

## Summary

All 30 listings (6 languages × 5 games) are within the limits, and every guide claim is present. 72 minimal edits fixed calques, three grammar or spelling errors, terms that did not match the app, the bg titles (word order, "Texas" restored), the hu Blackjack virtual-chip disclosure, the ro Hold'em "5 opponents", and one tr short description that overstated a claim.
The remaining short-description compromises (mostly "hints" or "5 opponents", once "solvable deals") are listed per language. Three app strings are mistranslations to fix in the app: tr "vakıf", el "Αναμνήσεις", ro "Amintiri".
Model quality ratings: tr 4, el 3.5, cs 4, ro 4, hu 3.5, bg 3.5. No native-speaker review is planned; the owner accepts the residual language-quality risk.
