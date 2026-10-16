# Listing review v2, group 4: hr, sk, ar, iw-IL, hi-IN, id

**Status: machine-translated and model-reviewed; no native-speaker review is planned.** The owner accepts the remaining language-quality risk. This report records model QA and automated checks; it does not claim native-speaker validation.

Scope: `translations/drafts/{hr,sk,ar,iw-IL,hi-IN,id}.json`. All five games plus the shared `promise`, `rest` and `footer`. Each field was checked against `source/en-US.json`, `TRANSLATION_GUIDE.md`, `AGENTS.md` and the in-app strings: `core/ui`, `games/{klondike,spider,freecell,blackjack,holdem}/.../values-{hr,sk,ar,iw,hi,in}`.

Limits after the edits (`check_drafts.py`): 0 problems. Every title is 30 characters or fewer, every short description 80 or fewer, and every composed full description 2,066 or fewer (the limit is 4,000). The remaining warnings are expected:
- hr, sk and id: `23,675` and `2,000` now use the local thousands separator.
- hr, sk and hi: Hold'em's "5" was dropped from the short description on purpose (see the compromises below).

Files keep the original CRLF format with no trailing newline.

## Findings across all six languages

1. **Klondike level names were left in English in all six drafts** ("Trivial … Expert", "Insane"). The app shows translated level names, so a player would not find "Insane" on screen. Each listing now uses the app's own level names.
2. **Decimal-comma numbers (hr, sk, id).** In these languages a comma is the decimal separator, so "2,000" reads as "2.0" and "23,675" as "23.675". They are now "2.000" and "23.675" (hr, id) and "2 000" and "23 675" with a no-break space (sk). These match the apps, which use 1.000 / 1 000. ar, iw and hi keep `23,675` and `2,000` in Western digits with a comma. That is correct for them, and hi's Indian grouping gives the same result.
3. **Foundation and animation terms** now match the app's setting labels: hr "temelje", sk "základy", iw "יסודות", hi "आधार पर … स्वचालित चालें", id "Langkah otomatis", ar "الرسوم المتحركة".
4. **Short descriptions:** "hints" and "virtual" are restored wherever they fit (ar Blackjack and FreeCell; iw FreeCell, Blackjack and Hold'em; sk Klondike). Where they do not fit, I kept "virtual chips" rather than "5 opponents" in Hold'em, because the virtual-chip disclosure carries more policy weight for a simulated casino game. None of the short descriptions contain "free" or a slogan. No battery wording was strengthened.
5. **For the English owner, not a translation issue:** the Spider app string `catalog_status_notice` (English and every locale) says "Four-suit deals are not yet certified". The English listing says "23,675 certified-solvable deals across all three suit counts". One of the two is out of date. Please confirm before any listing ships. I did not change the translations.

---

## Croatian (hr)

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| shared | rest | Nema hitnih bonusa ni -> Nema bonusa koji vas požuruju ni | "hitni bonusi" ("urgent bonuses") is a calque. The new wording says what the bonuses do: hurry you |
| shared | rest | Lokalna statistika pomaže vam -> Lokalni rezultati i statistika pomažu vam | "scores" had been dropped |
| klondike | body (3x) | Trivial / Expert / Insane -> Trivijalno / Stručno / Ludo | Match the app's level names |
| klondike, freecell | body | na temeljne hrpe -> na temelje | App: "Automatski potezi na temelje" |
| spider | body | 23,675 -> 23.675 | Decimal comma |
| freecell | body | 2,000 -> 2.000 | Decimal comma |
| blackjack | body | djelitelj staje na mekanih 17 -> djelitelj staje na mekom 17 | Wrong case (genitive plural). App help: "staje na svakom 17, uključujući meki 17" |
| holdem | short | …: žetoni, 5 protivnika, bez oglasa -> …: virtualni žetoni, bez oglasa | Keep "virtual" (see compromises) |

**Short-description compromises (hr)**
- Klondike: "1/3" without "izvlačenje"/"karte". "1/3 karte, savjeti" would be 81 characters.
- FreeCell: "savjeti" (hints) dropped. 87 characters with it.
- Blackjack: "savjeti" dropped. 89 characters with it.
- Hold'em: "5 protivnika" dropped to keep "virtualni žetoni". Both would take 90 characters.
- "blag prema bateriji" (gentle on the battery) is a slightly softer rendering of "battery-conscious". It is allowed, because it does not strengthen the claim. "koji pazi na bateriju" would be closer but longer.

**Needs a native speaker (hr)**
- Titles: "Klondike Pasijans", "Spider Pasijans", "FreeCell Pasijans". Is "Pasijans" what Croatian players search for, or "Solitaire" (or "Pauk" for Spider)?
- "FinitePlay obećanje:" is a calque-like noun phrase ("Obećanje FinitePlaya:"?).
- "u sva tri broja boja" (Spider) is correct but clunky.
- App level names "Trivijalno" / "Ludo" (neuter adverb forms) inside "od razine … do razine Stručno". Check that they read naturally.
- Blackjack: "šest špilova, svaki krug miješaju se iznova" is colloquial; "u svakoj rundi" would match the app's "runda". Also check "provjera djeliteljeve skrivene karte" for dealer peek.
- Hold'em: English glosses "(equity)" / "(pot odds)" next to the app terms "šansa" / "odnos pota". Keep them or drop them?
- Carried over from the app review: "Podijeli" (Split) next to "Podijeli karte" (Deal), and "Djelitelj" (Blackjack) vs "diler" (Hold'em).

**Rating: 3.5/5.** Fluent and faithful. The errors were the English level names, the decimal-comma numbers, one case error and the "urgent bonuses" calque.

---

## Slovak (sk)

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| shared | rest | Lokálne štatistiky vám pomáhajú -> Lokálne skóre a štatistiky vám pomáhajú | "scores" had been dropped |
| klondike | short | 1/3 karty, bez reklám -> 1/3, nápovedy, bez reklám | Restore "hints" (76 characters) |
| klondike | body (3x) | Trivial / Expert / Insane -> úrovne Triviálna až Expertná / Šialená | Match the app's level names |
| klondike, freecell | body | na základné hromady -> na základy | App: "Automatické presuny na základy" |
| spider | intro | so Späť, Prehrať znova a nepovinnými nápovedami -> s funkciami Späť a Prehrať znova a s nepovinnými nápovedami | "so Späť" (preposition plus button name) is ungrammatical |
| freecell | intro | a so Späť a Prehrať znova -> a s funkciami Späť a Prehrať znova | Same |
| spider | body | 23,675 -> 23 675 (no-break space) | Decimal comma; the app writes 1 000 |
| freecell | body | 2,000 -> 2 000 (no-break space) | Same |
| holdem | short | …: žetóny, 5 súperov, bez reklám -> …: virtuálne žetóny, bez reklám | Keep "virtual" (see compromises) |
| holdem | body | (equity) a pot odds -> (equity) a pomer potu (pot odds) | The app's term is "Pomer potu" |
| holdem | body | Nápovedy s odhadovanou šancou a pot odds -> … a pomerom potu | Same |

**Short-description compromises (sk)**
- Klondike: "1/3" without "karty" so that "nápovedy" fits. 82 characters with both.
- Spider: "nápovedy" dropped. 81 characters even without the comma.
- FreeCell: "nápovedy" dropped. 87 characters. "riešiteľné hry" (solvable games) is used, because "rozdania" is longer.
- Blackjack: "nápovedy" dropped. 80 characters as it stands.
- Hold'em: "5 súperov" dropped to keep "virtuálne žetóny". 86 characters with both.
- "šetrný k batérii" would save 4 characters, but "šetriť" means "to save" and would strengthen the claim, so I did not use it.

**Needs a native speaker (sk)**
- Titles keep "Solitaire". Do Slovak players search for "Solitaire", "Solitér" or "Pasiáns"?
- "Triviálna až Expertná" (feminine adjectives agreeing with "úroveň") inside "na úrovniach …".
- "vo všetkých troch počtoch farieb" (Spider) is clunky.
- The app keeps "Check" in English (Hold'em). The listing follows the app.
- "kontrola skrytej karty krupiéra" for dealer peek.

**Rating: 3.5/5.** Natural register. The same systemic errors as hr (level names, number format), plus ungrammatical "so Späť" and untranslated "pot odds" where the app has a Slovak term.

---

## Arabic (ar)

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| klondike | body | «تافه» (Trivial) حتى «خبير» (Expert) / «جنوني» (Insane) -> «تافه» حتى «خبير» / «جنوني» | The app shows only the Arabic names. Latin glosses break the right-to-left flow |
| klondike, freecell | body | وحركات للأوراق -> ورسوم متحركة للأوراق | "حركات" also means "moves" (used in this very list). The app says "الرسوم المتحركة" |
| spider | body | وحركات الأوراق بحركات متحركة -> ونقل الأوراق برسوم متحركة | The same ambiguity, and "movements with moving movements" was redundant |
| spider | body | للصوت والحركات -> للصوت والرسوم المتحركة | Animations |
| holdem | body | والصوت والحركات واللغة -> والصوت والرسوم المتحركة واللغة | Animations |
| spider | intro | بلون واحد (1) … لونين (2) … أربعة ألوان (4) -> parentheses removed | Unnatural, and the digits in parentheses add LTR runs |
| spider | body | • سبايدر بلون واحد -> • سوليتير العنكبوت بلون واحد | Match the title and intro |
| freecell | title | فري سيل سوليتير -> سوليتير فري سيل | Same order as the other titles ("سوليتير كلوندايك", "سوليتير العنكبوت") |
| freecell | short | توزيعات قابلة للحل وبلا إعلانات -> … وتلميحات وبلا إعلانات | Restore "hints" (80 characters) |
| blackjack | short | رقائق افتراضية وبلا إعلانات -> … وتلميحات وبلا إعلانات | Restore "hints" (77 characters) |

**Short-description compromises (ar)**
- Spider: "سبايدر" (transliteration) is kept, because "سوليتير العنكبوت" takes 82 characters. The title and the full description say "العنكبوت".
- Otherwise every English element is present. "بلا إنترنت" (without internet) is used for "offline".

**Needs a native speaker (ar)**
- Spider naming: is "سوليتير العنكبوت" (title) or "سبايدر" what players search for?
- App terms used as they are: "تافه" for Trivial (can read as "worthless"), "جنوني" for Insane, and "لون" for suit, which could be confused with red/black. Kept to match the app.
- "يبقى تقدمك وإحصاءاتك خاصة ومحلية": agreement between a masculine singular and a feminine plural noun.
- The listing says "إحصاءات"; the app says "الإحصائيات". Both are correct.
- RTL rendering of "FinitePlay", "1/3", "1/2/4", "3:2", "23,675", "(equity)", "(pot odds)" and "(All in)" in the Play Console preview.
- Carried over from the app review: Blackjack Stand "ابقَ" vs "توقف", and Hold'em all-in "الكل".

**Rating: 3.5/5.** Good disclosures and register. Ambiguous "حركات" for animations, inconsistent Spider naming, and parentheses that disrupt the RTL flow.

---

## Hebrew (iw-IL)

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| klondike | body (3x) | Trivial / Expert / Insane -> "טריוויאלי" / "מומחה" / "מטורף" | Match the app's level names (they were Latin text inside RTL) |
| klondike, freecell | body | לערימות הבסיס -> ליסודות | App: "מהלכים אוטומטיים ליסודות" |
| spider | intro | צבע אחד (1) … שני צבעים (2) או ארבעה (4) -> צבע אחד … שניים או ארבעה צבעים | Unnatural parenthesised digits |
| blackjack | body | המחלק עוצר על 17 רך -> המחלק עוצר ב-17 רך | "על" is a calque of "on". The app help: "עוצר בכל 17, כולל 17 רך" |
| freecell | short | חלוקות פתירות, בלי פרסומות -> חלוקות פתירות, רמזים, בלי פרסומות | Restore "hints" (71 characters) |
| blackjack | short | אסימונים וירטואליים, בלי פרסומות -> …, רמזים, בלי פרסומות | Restore "hints" (80 characters) |
| holdem | short | אסימונים, 5 יריבים -> אסימונים וירטואליים, 5 יריבים | Restore "virtual" (79 characters) |

**Short-description compromises (iw):** none. Every short description now carries all the English elements.

**Needs a native speaker (iw)**
- Title word order differs: "סוליטר קלונדייק" (Solitaire first) vs "ספיידר סוליטר" and "פריסל סוליטר" (name first). Pick one order, and check whether Hebrew players search for "ספיידר" or "עכביש".
- "צבע" for suit follows the app ("צבע אחד"). Check that it is not read as red/black.
- "בשלושת מספרי הצבעים" (Spider) is awkward.
- Chips: the app and the listing now both say "אסימונים" (the earlier TODO note about "צ'יפים" no longer applies).
- "כשבא לכם" (Klondike intro) is colloquial. Is it acceptable in a store listing?
- RTL rendering of the quoted level names, "FinitePlay", "1/3", "3:2", "-21" and the "(equity)" / "(pot odds)" glosses.

**Rating: 4/5.** The most natural of the group, with correct gender-neutral plural address. The errors were the English level names, one preposition calque and short descriptions that were cut when they did not need to be.

---

## Hindi (hi-IN)

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| klondike | body (3x) | Trivial / Expert / Insane -> "साधारण" / "विशेषज्ञ" / "अत्यधिक" | Match the app's level names (they were Latin text) |
| klondike, freecell | body | वैकल्पिक अपने-आप फ़ाउंडेशन में चालें -> आधार पर वैकल्पिक स्वचालित चालें | App: "आधार पर स्वचालित चालें" |
| spider | intro | राजा से इक्के तक -> बादशाह से इक्के तक | The app's King is "बादशाह" |
| spider | intro | …, पूर्ववत करें, फिर से खेलें और मदद चाहिए तो वैकल्पिक संकेत के साथ। -> …; साथ में पूर्ववत करें, फिर से खेलें और मदद चाहिए तो वैकल्पिक संकेत भी हैं। | The old clause had no predicate |
| blackjack, holdem | intro | असली पैसे की बेट -> असली पैसे के दाँव | "दाँव" is the natural wager word, and the Blackjack app uses it ("दाँव 10 के चरणों में") |
| blackjack | body | वैकल्पिक … संकेतों के साथ … अभ्यास करें, जो कारण … समझाते हैं -> … अभ्यास करें, वैकल्पिक … संकेतों के साथ जो कारण … समझाते हैं | The relative clause "जो" was separated from "संकेत" |
| blackjack | body | छह डेक, हर राउंड में -> छह डेक, जो हर राउंड में | Sentence fragment |
| blackjack | body | ब्लैकजैक 3:2 पर भुगतान करता है -> ब्लैकजैक पर 3:2 का भुगतान मिलता है | "Blackjack pays at 3:2" was a calque. Now matches the corrected app help |
| holdem | short | …: चिप्स, 5 विरोधी, बिना विज्ञापन -> …: वर्चुअल चिप्स, बिना विज्ञापन | Keep "virtual" (see compromises) |
| holdem | body | अनुमानित इक्विटी और पॉट ऑड्स -> जीत की अनुमानित संभावना (इक्विटी) और पॉट ऑड्स | The app's term for equity is "जीत की संभावना" |
| holdem | body | • अनुमानित इक्विटी और पॉट ऑड्स वाले संकेत -> • जीत की अनुमानित संभावना और पॉट ऑड्स वाले संकेत | Same |
| holdem | body | बाद में उसे फिर शुरू करें -> बाद में वहीं से जारी रखें | "फिर शुरू करें" can read as "restart", which is the opposite of "return to it" |

**Short-description compromises (hi)**
- FreeCell: "संकेत" dropped. 82 characters with it.
- Blackjack: "संकेत" dropped. 83 characters with it.
- Hold'em: "5 विरोधी" dropped to keep "वर्चुअल चिप्स". 82 characters with both.
- The cause is that "बैटरी का ध्यान रखने वाला" takes 24 characters. A shorter form such as "बैटरी-सजग" would free space in every Hindi line, but it needs native approval.

**Needs a native speaker (hi)**
- **Rest reminders:** the app's `setting_rest_reminder` is "आराम अनुस्ठापक". This is not a Hindi word (probably a garbled "अनुस्मारक"). The listing keeps the draft's "आराम के रिमाइंडर" rather than copy a non-word. **The app string should be fixed** ("आराम रिमाइंडर" or "आराम अनुस्मारक"), and then the listing aligned with it.
- Hold'em title stays in Latin script ("Texas Holdem Poker: FinitePlay"). In Devanagari, "टेक्सास होल्डम पोकर: FinitePlay" is 31 characters. The other four titles use Devanagari. Is the mix acceptable?
- App level names: "साधारण" ("ordinary") for Trivial and "अत्यधिक" ("excessive") for Insane are both odd. The listing follows the app.
- Deal is "बाँट" in the listing; the app uses बाज़ी / बँटवारा / डील in different places. Pick one.
- "1/3 पत्ते" vs "कार्ड": both words occur in the app and the listing.
- Western digits (23,675, 2,000, 3:2, 18) were kept on purpose.
- "डीलर द्वारा छिपे कार्ड की जाँच" for dealer peek.

**Rating: 3.5/5.** Disclosures and register were good. There were grammar slips in Spider and Blackjack, an ambiguous "restart", a mismatch with the app's King and equity terms, and English level names.

---

## Indonesian (id)

| Game | Field | Old -> New | Reason |
|---|---|---|---|
| shared | promise | pengumpulan atau pembagian data -> pengumpulan atau berbagi data | "pembagian" reads as "division/distribution"; "berbagi data" is the usual phrase for data sharing |
| klondike | short | tarik 1/3 -> ambil 1/3 | The app's draw mode is "Ambil Satu / Ambil Tiga" (same length, 80) |
| klondike | intro | Pilih tarik 1 atau tarik 3 kartu -> Pilih mode Ambil Satu atau Ambil Tiga | The app's labels |
| klondike | body | Aturan tarik 1 kartu dan tarik 3 kartu -> Aturan Ambil Satu dan Ambil Tiga | Same |
| klondike | body (3x) | Trivial / Expert / Insane -> Sepele / Ahli / Gila | Match the app's level names |
| klondike, freecell | body | Gerakan otomatis ke fondasi -> Langkah otomatis ke fondasi | App setting label |
| spider | intro | dari King hingga As -> dari Raja hingga As | The core/ui card name is "Raja"; the draft mixed English King with Indonesian As |
| spider | body | 23,675 -> 23.675 | Decimal comma; the app writes 1.000 |
| freecell | body | 2,000 -> 2.000 | Same |
| blackjack | body | blackjack membayar 3:2 -> blackjack dibayar 3:2 | Calque of "pays". The app help says "Dibayar 2 banding 1" |

**Short-description compromises (id)**
- FreeCell: "petunjuk" dropped, and "bisa diselesaikan" has no noun ("solvable", without "deals"). 88 characters with hints.
- Blackjack: "petunjuk" dropped. 84 characters with it. "sederhana, peduli baterai" (80) would fit, but it breaks the "yang peduli baterai" pattern of the other four.
- Hold'em keeps every element (79 characters).

**Needs a native speaker (id)**
- "peduli baterai" for battery-conscious. Make sure it does not read as "hemat baterai" (battery-saving).
- Hold'em terms stay in English (Fold, Check, Call, Bet, Raise, All in, equity, pot odds, side pot), as in the app.
- "Pembagian kartu" vs the app's "Kocokan" / "Permainan" for deals.
- Blackjack's "Ambil / Tahan": many Indonesian players say Hit / Stand (from the app review).
- "bonus yang mendesak" for urgency bonuses.

**Rating: 4/5.** Clean, consistent "Anda" register and accurate disclosures. The errors were the number format, the English level names, mixed King/As and a few app-term mismatches.

---

## Not changed on purpose
- I checked the 18+, 3:2, no-real-money, computer-only, hint-limit, certified/uncertified and rest-reminder wording in all 30 game entries. They were faithful, so they are unchanged apart from the edits above.
- I did not rewrite acceptable phrasing for taste. Clunky but correct items are listed under "Needs a native speaker".

## Summary
76 edits across 6 files. The systemic errors were English Klondike level names (all 6), decimal-comma numbers (hr, sk, id) and app-term mismatches. Lost "hints"/"virtual" was restored where it fits within 80 characters.
All drafts pass the limits (title ≤ 30, short ≤ 80, full ≤ 2,066). Ratings: iw 4, id 4, hr 3.5, sk 3.5, ar 3.5, hi 3.5. Open items: the Hindi app's "आराम अनुस्ठापक" (to fix in the app) and the Spider four-suit certification conflict between the app and the English listing.
No native-speaker review is planned; the owner accepts the residual language-quality risk. The report records model QA and unresolved wording concerns without claiming native validation.
