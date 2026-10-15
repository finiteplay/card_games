# String review, group 4: hr, sk, ar, iw, hi, in

Scope: Blackjack `values-LOC/strings.xml` and Hold'em `values-LOC/{strings, strings_table, strings_panels, strings_integration}.xml`, compared string by string with English.

Paths below are short: **BJ** = `games/blackjack/app/src/main/res/values-LOC/strings.xml`; **HE/s**, **HE/t**, **HE/p**, **HE/i** = Hold'em `strings.xml`, `strings_table.xml`, `strings_panels.xml`, `strings_integration.xml`.

Validation after editing (a script over all 30 files, checked against English):
- Every file has the same keys as English.
- Every string has the same positional placeholders and types, including `%1$,d`.
- Apostrophes are escaped, there are no bare `&`, and every literal percent sign is `%%`.
- All files are well-formed XML.

The one exception is pre-existing and I left it on purpose: the Arabic `status_hand_next` items `one` and `two` leave out `%4$d` ("بعد يد واحدة" / "بعد يدين"). That is normal Arabic and allowed on Android, and the unit test does not check plurals.

Two problems came up in several locales:

1. **Mixed formal and informal address.** In hr, sk and in, the help, dialogs and seat label used the formal "you" (Vi / Vy / Anda). The hint reasons and the reviews used the informal "you" (ti / ty / kamu). In hr and sk the informal past forms were also masculine only ("Slijedio si", "Riadil si"). I moved the hints and reviews to the formal "you", which these apps already use everywhere else. In hr and sk, the formal past form is also gender-neutral.
2. **The Hint name and the style tags.** I made the Hint's name match the button label in core/ui (sk "Nápoveda", hi "संकेत", in "Petunjuk"). I also made `help_strategy_6` name the opponent-style tags exactly as the table shows them.

---

## Croatian (hr)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | hand_bust | Preko -> Preko 21 | "Preko" alone ("Over") is unclear as a hand label; matches "otići preko 21" in the hints |
| BJ | help_goal | ruku vredniju od djeliteljeve -> ruku koja vrijedi više od djeliteljeve | "vredniju" is an ekavian (Serbian) form; the rewording avoids the spelling question |
| BJ | help_values | je blackjack i isplaćuje 3 prema 2 -> je blackjack, koji se isplaćuje 3 prema 2 | A blackjack "is paid" (isplaćuje se); it does not "pay" |
| BJ | help_round | pritisnite Podijeli -> pritisnite Podijeli karte | The Deal button says "Podijeli karte"; "Podijeli" is the Split button, so the old text pointed to the wrong button |
| BJ | help_double | Koristite kad je ruka jaka -> Učinite to kad je ruka jaka | "Koristite" had no object |
| BJ | help_insurance | Isplaćuje 2 prema 1 -> Isplaćuje se 2 prema 1 | Same passive fix as help_values |
| BJ | hint_reason_stand_dealer_weak, hint_reason_hit_dealer_strong | Djelitelj pokazuje %1$s … -> Djelitelj pokazuje kartu %1$s …, plus formal "you" (stanite, pustite, stanete, vucite) | %1$s can be "As", which would need the accusative "asa"; "kartu %1$s" avoids the case. Formal address |
| BJ | hint_reason_stand_strong, hit_cannot_bust, hit_soft, double, split_eights, split_pair, decline_insurance, hint_figures | ti/te/odeš/vuci/podijeli… -> vi/vas/odete/vucite/podijelite… | Formal address, same wording otherwise |
| BJ | review_followed_won/lost/push, review_ignored_won/lost/push, review_you_busted | Slijedio si… / Pobijedio si… / Otišao si… -> Slijedili ste… / Pobijedili ste… / Otišli ste… | Formal and gender-neutral; review_followed_push also "dobio neriješeno" -> "odigrali neriješeno" (more natural) |
| BJ | review_would_have_drawn | Vučenje karte donijelo bi %1$s, što daje %2$d. -> Da ste vukli, sljedeća karta bila bi %1$s, što bi dalo %2$d. | "donijelo bi Kralj/As" breaks case; the nominative fits every card name |
| BJ | review_stand_chances | Kad si odlučivao … neriješeno u %3$d%% -> Kad ste odlučivali … završavalo neriješeno u %3$d%% | Formal address; the middle clause had no verb |
| HE/s | help_tournament | žetoni razmjerno postaju kraći, a čekanje skupo -> vaši žetoni razmjerno vrijede sve manje, a čekanje postaje skupo | "Chips get shorter" taken literally is meaningless in Croatian |
| HE/s | help_leave | prerano završava … onamo gdje biste da ste sada ispali -> prijevremeno završava … na mjestu na kojem biste završili da ste sada ispali | "prerano" means "too early" (a reproach); the second clause was incomplete |
| HE/s | help_strategy_1 | a opustite se kako se mjesta približavaju gumbu -> a igrajte više ruku kako … | "opustite se" means "relax", not the poker sense of "loosen up" |
| HE/s | help_strategy_5 | Igranje zadnji omogućuje vam… -> Kad igrate posljednji, prvo vidite… | "Igranje zadnji" is ungrammatical |
| HE/s | help_strategy_6 | Pratite stilove … aggressive ili passive … čvrstog … opuštenog, agresivnog -> Promatrajte stilove … agresivan ili pasivan … tight igrača … igrača koji je loose i agresivan | "Pratite" clashed with "Prati" (Call); the text now matches the seat tags |
| HE/s | help_hint_note | tvoju / osvojiš / tvoja -> vašu / osvojite / vaša | Formal address, like the rest of the help |
| HE/t | style_aggressive, style_passive | aggressive / passive -> agresivan / pasivan | English spelling in Croatian UI; tight and loose stay, as poker jargon |
| HE/t | end_body_won | Trebalo je %1$d ruku. -> Broj odigranih ruku: %1$d. | "%d ruku" is wrong for 2-4, 22-24…; a label format avoids plurals |
| HE/i | hint_reason_push_short_stack, hint_reason_fold_short_stack | Ostalo je samo %1$d velikih blindova -> Ostalo vam je samo %1$d BB | "%d velikih blindova" is wrong for 1 and for 2-4. BB is shown on the table and explained in the help. push_short_stack also "all in ili odustani" -> "all in ili odustajanje" |
| HE/i | hint_reason_semi_bluff, hint_reason_call_draw | (%1$d outsa) -> (outovi: %1$d) | "outsa" is a wrong form and does not agree with the number |
| HE/i | hint_reason_call_good_price | pobjeđuješ oko %1$d%% puta -> pobjeđujete u oko %1$d%% slučajeva | "%% puta" is unnatural; now matches the other strings. Formal address |
| HE/i | premium_raise, late_position_raise, playable_raise, free_flop, fold_to_raise, value_bet, value_raise, fold_bad_price, free_card, pot_control | podigni/igraš/vidi/tvoja/si/uloži/ti/uzmi/čekaj -> podignite/igrate/pogledajte/vaša/ste/uložite/vi/uzmite/čekajte | Formal address |
| HE/i | review_followed_won/lost/fold, review_ignored_won/lost/fold, review_beaten_by, review_hindsight_fold_won/lost, review_chance_at_decision, review_chance_slim_win, review_followed_won_slim | Slijedio si / Odustao si / Izgubio si / Dobio bi / kad si odlučivao / ti je -> Slijedili ste / Odustali ste / Izgubili ste / Dobili biste / kad ste odlučivali / vam je | Formal and gender-neutral |

### Needs a native speaker
- `action_split` "Podijeli" vs `action_deal` "Podijeli karte": the help text is fixed, but two buttons built on the same verb are confusing. Consider "Razdvoji" (Split) or "Dijeli" (Deal).
- `label_dealer` "Djelitelj" (Blackjack) vs `a11y_dealer` / `help_button` "diler" (Hold'em): is it fine for the two games to use different words?
- `hand_four_of_a_kind`, `hand_three_of_a_kind`, `hand_full_house`, `hand_two_pair`: the rank arrays are genitive plural, so these read "Četiri iste, asova". Check that this sounds natural.
- `review_would_have_drawn` and the hints insert core card names that start with a capital (As, Kralj) in mid-sentence.
- `help_tournament` "Završavate na mjestu jednakom broju preostalih igrača" is correct but heavy.
- `style_tight`/`style_loose` stay in English. Check that players expect this.

### Fit
- `hand_straight_flush` / `hand_flush` / `hand_straight` "…, najviša karta: %1$s" is about twice the English length in the showdown label.
- `hand_bust` "Preko 21" is slightly longer than "Bust".
- `stat_tile_vpip` "Dobrovoljno u igri prije flopa" is long for a stat tile (the English is long too).

**Rating: 4/5.** Main weakness: mixed formal/informal address and literal calques in the strategy text. Both are fixed.

---

## Slovak (sk)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | hand_bust | Cez -> Cez 21 | "Cez" ("over") alone is unclear |
| BJ | help_strategy_2 | lebo pravdepodobne prelezie -> lebo pravdepodobne prekročí 21 | "prelezie" is slang ("crawls over"); now matches "prekročiť 21" in help_goal |
| BJ | hint_reason_double, hint_reason_split_pair | proti krupiérovej %1$s -> proti krupiérovej karte %1$s | The feminine adjective did not agree with "Eso" (neuter) or a bare number |
| BJ | hint_reason_stand_strong, stand_dealer_weak, hit_dealer_strong, hit_cannot_bust, hit_soft, double, split_eights, split_pair, decline_insurance, hint_figures | riskuješ/ty by si/staneš/ťahaj/zlepši/zdvoj/rozdeľ/ti/si vyšiel/ťa -> riskujete/vy by ste/zostanete stáť/ťahajte/zlepšite/zdvojte/rozdeľte/vám/ste vyšli/vás | Formal address |
| BJ | review_title | Spätný pohľad na radu -> Spätný pohľad na nápovedu | The button is "Nápoveda" (core/ui), and help_strategy_intro already says "Tlačidlo Nápoveda" |
| BJ | review_followed_won/lost/push, review_ignored_won/lost/push, review_you_busted | Riadil si sa radou… / rada navrhovala / Išiel si -> Riadili ste sa nápovedou… / nápoveda navrhovala / Išli ste | Formal, gender-neutral, and the button name |
| BJ | review_would_have_drawn | Ťahanie karty by prinieslo %1$s, čo dáva %2$d. -> Keby ste ťahali, ďalšia karta by bola %1$s a mali by ste %2$d. | "prinieslo Kráľ" needs the accusative "kráľa"; the nominative fits every card |
| BJ | review_stand_chances | Keď si sa rozhodoval -> Keď ste sa rozhodovali | Formal address |
| HE/s | help_tournament | žetóny sa pomerne zmenšujú -> hodnota vašich žetónov relatívne klesá | Literal "chips shrink" |
| HE/s | help_strategy_1 | a uvoľnite sa, keď … -> a hrajte viac rúk, keď … | "uvoľnite sa" means "relax" |
| HE/s | help_strategy_6 | aggressive alebo passive … od loose, aggressive hráča -> agresívny alebo pasívny … od hráča, ktorý je loose a agresívny | Matches the new tag words |
| HE/s | help_hint_note | O rade: … tvoju … vyhráš … tvoja -> O nápovede: … vašu … vyhráte … vaša | Button name and formal address |
| HE/s | help_strategy_7 | zdvojnásobiť sa -> zdvojnásobiť žetóny | "zdvojnásobiť sa" ("double yourself") is unidiomatic |
| HE/t | style_aggressive, style_passive | aggressive / passive -> agresívny / pasívny | English spelling in Slovak UI |
| HE/t | end_body_won | Trvalo to %1$d rúk. -> Počet odohraných rúk: %1$d. | "%d rúk" is wrong for 2-4 |
| HE/i | hint_reason_push_short_stack, hint_reason_fold_short_stack | Zostáva len %1$d veľkých blindov -> Máte už len %1$d BB | The plural broke for 1 and for 2-4. Formal address |
| HE/i | hint_reason_semi_bluff, hint_reason_call_draw | (%1$d outov) -> (outy: %1$d) | The plural broke for 2-4 |
| HE/i | premium_raise, late_position_raise, playable_raise, free_flop, fold_to_raise, value_bet, value_raise, call_good_price, fold_bad_price, free_card, pot_control | zvýš/hráš/prevezmeš/pozri/tvoja/staviaj/vyhrávaš/ty máš/vezmi/urob check -> zvýšte/hráte/prevezmete/pozrite/vaša/stavte/vyhrávate/vy máte/vezmite/dajte check | Formal address |
| HE/i | review_title, review_followed_won/lost/fold, review_ignored_won/lost/fold, review_beaten_by, review_hindsight_fold_won/lost, review_chance_at_decision, review_chance_slim_win, review_followed_won_slim | rada / Riadil si / Zahodil si / Prehral si / by si vyhral / s asi -> nápoveda / Riadili ste / Zahodili ste / Prehrali ste / by ste vyhrali / s približne | Button name, formal and gender-neutral |

### Needs a native speaker
- `action_check` / `seat_action_check` "Check" stays in English while Fold and Call are Slovak. Is that the norm for Slovak players?
- `rank_jack` "spodok" (core and Hold'em arrays): with French-suited cards, players may expect "dolník" or "J".
- `help_hand_cards` "dve súkromné karty, svoje vlastné karty" is redundant. A short name for hole cards would read better.
- The `hand_*` names with genitive plural ("Štvorica, es"): check that they sound natural.

### Fit
- `hand_straight_flush` "Postupka vo farbe, najvyššia karta: %1$s" is long for the showdown label.
- `action_raise` "Zvýšiť na" plus the number is OK on two lines.

**Rating: 4/5.** Main weakness: the hints did not use the Hint button's name, and the address was mixed. Both are fixed.

---

## Arabic (ar)

The address is consistently informal masculine singular, which is standard in Arabic games, so I left it. I checked RTL rendering: numbers followed by `%` (European terminator) stay attached to the number, and strings that start with a digit still resolve RTL from the first Arabic letter. Latin "A-A", "SB" and "BB" are short LTR runs and display correctly.

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | hand_total_soft | ناعمة %1$d -> %1$d ناعمة | Arabic puts the adjective after the number, as the help does ("17 الناعمة") |
| BJ | help_strategy_4 | كـ 10 و 20 -> كـ10 و20 | Stray spaces after the attached prefixes كـ and و |
| HE/s | help_tournament | فتصبح الرقائق أقصر نسبيًا وينتظر اللاعب بكلفة عالية -> فتقلّ قيمة رقائقك نسبيًا ويصبح الانتظار مكلفًا | Literal "chips get shorter"; "waiting gets expensive" was garbled |
| HE/s | help_strategy_6 | tight أو loose أو aggressive أو passive … الـ tight … loose وaggressive -> متحفظ أو متساهل أو عدواني أو سلبي … المتحفظ … متساهل وعدواني | The seat tags are already shown in Arabic; English words also broke RTL flow |
| HE/s | help_strategy_7 | بحصة قصيرة -> برقائق قليلة | "حصة" means "share/portion", not "stack" |
| HE/s | help_hint_note | احتمال فوزك بالبوت المقدَّر -> الاحتمال المقدَّر لفوزك بالبوت | "estimated" attached to "the pot" instead of to "the chance" |
| HE/t | end_body_won | استغرق الأمر %1$d يد. -> عدد الأيدي: %1$d. | "يد" is wrong for 3-10 (أيدٍ) and 11-99 (يدًا) |
| HE/t | a11y_seat, a11y_seat_you | %3$,d رقاقة -> الرقائق: %3$,d | Same agreement problem for the spoken chip count |
| HE/i | hint_reason_late_position_raise | مركز متأخر -> موقع متأخر | "مركز" is used for finishing place; the help uses "الموقع" for position |
| HE/i | hint_reason_semi_bluff, hint_reason_call_draw | (%1$d أوراق مكمّلة) -> (الأوراق المكمّلة: %1$d) | "أوراق" is wrong for 11+ outs |
| HE/i | hint_reason_call_good_price | المجاراة تستحق -> المجاراة مجدية | "تستحق" needs an object; "مجدية" is the term the help uses |

### Needs a native speaker
- `action_stand` "ابقَ": Arabic blackjack apps often use "توقف". Also, the action bar mixes imperatives (اطلب، ابقَ، ضاعف، قسّم) with nouns (تأمين، رفض، إعادة الرقائق).
- `action_all_in` / `seat_all_in` "الكل": very terse for all-in. Players may know "أول إن" or "كل الرقائق".
- `hint_reason_push_short_stack` / `fold_short_stack` "%1$d بلايند كبير": number agreement is off for 3-10. Consider "%1$d BB" or a "من" construction.
- `style_tight` "متحفظ" / `style_loose` "متساهل" / `style_passive` "سلبي": "سلبي" can also mean "negative".
- `status_hand_next` `zero`/`other` "بعد %4$d يد": fine for 100+, odd for 0 (never shown).

### Fit
- `review_title` "نظرة إلى الوراء على التلميح" is fine for a dialog title.
- No button overflows were found.

**Rating: 4/5.** Main weakness: number-noun agreement around inserted numbers, and English style words in the RTL help. The safe cases are fixed.

---

## Hebrew (iw)

Address is consistently plural (אתם), the modern gender-neutral choice. Buttons use masculine imperatives, which is standard. RTL is fine: "ב-%d%%" and "ל-%d" follow normal Hebrew style with a maqaf before a number.

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | hand_total_soft | רך %1$d -> %1$d רך | Adjective after the number, as in the help ("17 רך") |
| BJ | summary_even | שווה -> מאוזן | "שווה" alone reads as "worth" or "equal"; the round's net is "balanced" |
| BJ | hint_reason_stand_dealer_weak | ואתם הייתם חורגים ב-%4$d%% אם תיקחו קלף -> ואילו קלף נוסף יחריג אתכם ב-%4$d%% מהמקרים | Mixed conditional and future tenses |
| BJ | review_followed_won/lost/push | עשיתם כמו שהרמז ייעץ -> פעלתם לפי הרמז | "ייעץ" is the future tense ("will advise"); a grammar error |
| BJ | review_you_busted | חריגה שלכם עם %1$d. -> חרגתם עם %1$d. | Noun calque; the verb is natural |
| BJ | review_would_have_drawn | לקיחת קלף הייתה מביאה %1$s, ומגיעה ל-%2$d. -> אילו לקחתם קלף, הייתם מקבלים %1$s ומגיעים ל-%2$d. | Awkward nominal phrasing with broken agreement |
| HE/s | help_hand_high_card | אף אחד מהאלה -> אף אחת מהידיים שלמעלה | Ungrammatical |
| HE/s | help_strategy_2 | וכשעושים לה קול ליד שלכם חזקה יותר לעתים קרובות -> וכשעושים לה קול, לעתים קרובות יותר היד שלכם היא החזקה | Garbled clause ("ליד שלכם חזקה") |
| HE/s | help_strategy_6 | tight, loose, aggressive או passive … -> הדוק, משוחרר, תוקפני או פסיבי … | Match the Hebrew seat tags |
| HE/i | review_followed_won, review_followed_lost, review_followed_won_slim | עשיתם כמו שהרמז ייעץ -> פעלתם לפי הרמז | Future-tense error (see above) |
| HE/i | review_followed_fold | כמו שהרמז ייעץ -> כמו שהרמז יעץ | Same tense error, smaller edit |

### Needs a native speaker
- `style_tight` "הדוק" / `style_loose` "משוחרר": Israeli players often say טייט / לוז. The literal words may confuse them.
- `help_strategy_1` "והרפו": "relax" vs "play more hands".
- `action_hit` "קלף": short and common, but check it against "עוד קלף".
- `you` "אתם" as a seat label: plural for one player; consider "את/ה" or keeping "אתם" for consistency.
- `status_hand_next` `one` "בעוד %4$d יד": natural Hebrew is "בעוד יד אחת", but the placeholder must stay.

### Fit
- `action_raise` "העלאה ל-" plus the number on the second line is fine.
- No overflows found.

**Rating: 4/5.** Main weakness: one systematic grammar error (future "ייעץ" used as past) repeated across seven reviews, plus a few awkward nominal calques. All are fixed.

---

## Hindi (hi)

The address is consistently formal (आप). Most poker terms are transliterated, which is how Indian players talk.

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | help_values | और 3 के बदले 2 देता है -> और इस पर 3:2 का भुगतान मिलता है | "3 के बदले 2" means "2 in return for 3", which reverses the payout |
| BJ | help_insurance | यह 2 के बदले 1 देता है, वरना डूब जाता है -> इस पर 2:1 का भुगतान मिलता है, वरना यह डूब जाता है | Same reversed ratio |
| BJ | hint_reason_decline_insurance | … दहला होता है, जबकि बराबरी के लिए इतना चाहिए -> … दस मूल्य का होता है, जबकि घाटे से बचने के लिए कम से कम इतना चाहिए | "दहला" is only the 10 card, not any ten-value card. "बराबरी" is the push term, not "break even" |
| BJ | review_dealer_finished | डीलर %1$d पर खत्म हुआ। -> डीलर का कुल %1$d रहा। | "The dealer finished/ended at 17" reads oddly; "the dealer's total was" |
| BJ | review_title, review_followed_won/lost/push, review_ignored_won/lost/push | हिंट -> संकेत | The button (core/ui `action_hint`) and help_strategy_intro say "संकेत" |
| HE/s | help_hand_cards | बीच में सीधे रखे जाते हैं … दोनों, एक या कोई भी पत्ता -> बीच में खुले रखे जाते हैं … दोनों पत्ते, एक पत्ता या एक भी नहीं | "सीधे" is ambiguous ("straight"). "कोई भी" means "any", not "none", which was a meaning error |
| HE/s | help_tournament | 6वाँ सबसे पहले बाहर होना है और 1ला जीत है -> छठा स्थान सबसे पहले बाहर होने वाले का है और पहला स्थान जीत है | "1ला" is not Hindi |
| HE/s | help_strategy_6 | tight, loose, aggressive या passive … -> सतर्क, ढीला, आक्रामक या निष्क्रिय … | Match the Hindi seat tags |
| HE/s | help_hint_note | हिंट के बारे में -> संकेत के बारे में | Button name |
| HE/t | action_raise | रेज़ तक&#10;%1$,d -> रेज़&#10;%1$,d तक | Hindi postposition order ("400 तक"); the number stays on line 2 |
| HE/t | leave_body | आप %1$s स्थान पर रहेंगे -> आपका स्थान %1$s रहेगा | `places` gives the direct case "दूसरा", but "स्थान पर" needs the oblique "दूसरे" |
| HE/t | end_title_place | आप %1$s स्थान पर रहे -> आपका स्थान: %1$s | Same case problem |
| HE/t | a11y_out | बाहर, %1$s स्थान पर रहे -> बाहर, स्थान: %1$s | Same case problem |
| HE/i | hint_reason_value_bet | पैसे देने पड़ें -> कीमत चुकानी पड़े | Play-money chips, not money |
| HE/i | review_title, review_followed_won/lost/fold, review_ignored_won/lost/fold, review_beaten_by, review_followed_won_slim | हिंट -> संकेत | Button name |

### Needs a native speaker
- `action_hit` "कार्ड लें" uses "कार्ड" while the help uses "पत्ता". The mix is acceptable but inconsistent.
- `action_double` "दोगुना" is translated but `action_split` "स्प्लिट" is transliterated.
- `hint_reason_semi_bluff` "बेट … सकती है" treats bet as feminine, while `ann_bet` "का बेट" treats it as masculine.
- `style_tight` "सतर्क" (cautious) and `style_passive` "निष्क्रिय" (inactive): may players prefer टाइट / पैसिव?
- `hint_reason_call_good_price` "कीमत को सिर्फ़ %2$d%% चाहिए" is understandable but a calque.

### Fit
- `action_raise` "रेज़ / 1,200 तक" fits two lines.
- `stat_tile_vpip` "फ़्लॉप से पहले अपनी मर्ज़ी से शामिल" is long for a tile (the English is long too).

**Rating: 3.5/5.** Main weakness: two reversed payout ratios in the rules, a "none" mistranslated as "any", and ordinal case errors when places are inserted. All are fixed.

---

## Indonesian (in)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | hand_total_soft | Lunak %1$d -> %1$d lunak | Indonesian word order; matches the help ("17 lunak") |
| BJ | banner_win, banner_lose | Kamu menang! / Kamu kalah -> Anda menang! / Anda kalah | The rest of the UI (help, dialogs, seat label "Anda") uses Anda |
| BJ | help_round | tidak ada batalkan -> langkah tidak bisa dibatalkan | Ungrammatical ("there is no cancel" as a verb) |
| BJ | hint_reason_stand_dealer_weak, hint_reason_hit_dealer_strong | %3$d%% dari waktu, … jika kamu ambil kartu, kamu bust -> dalam sekitar %3$d%% kasus, … jika Anda mengambil kartu, Anda bust | "dari waktu" is a calque of "of the time". Anda |
| BJ | hint_reason_hit_cannot_bust, hit_soft, split_pair, hint_figures | Kamu / tanganmu / memberimu / Ambil kartu, kamu -> Anda / tangan Anda / memberi Anda / Ambil kartu: Anda | Anda |
| BJ | review_title, review_followed_won/lost/push, review_ignored_won/lost/push | Hint / Kamu / chip-mu -> Petunjuk / Anda / chip Anda | The button is "Petunjuk" (core/ui), and help_strategy_intro already says so |
| BJ | review_you_busted, review_would_have_drawn | kamu -> Anda | Anda |
| BJ | review_stand_chances | Saat memutuskan, … sekitar %2$d%% dari waktu -> Saat Anda memutuskan, … dalam sekitar %2$d%% kasus | Calque; Anda |
| HE/s | help_leave | Tidak ada batalkan -> Langkah tidak bisa dibatalkan | Ungrammatical |
| HE/s | help_hand_high_card | Tidak ada yang di atas -> Tidak satu pun dari yang di atas | Reads as "nothing is above" |
| HE/s | help_strategy_6 | aggressive, atau passive … loose dan aggressive -> agresif, atau pasif … yang loose dan agresif | English spelling; matches the new tags |
| HE/s | help_hint_note | Tentang Hint: menampilkan equity kamu -> Tentang Petunjuk: menampilkan equity Anda | Button name; Anda |
| HE/t | style_aggressive, style_passive | aggressive / passive -> agresif / pasif | Indonesian has these exact loanwords; tight and loose stay as jargon |
| HE/t | hand_full_house | Full house, %1$s penuh dengan %2$s -> Full house, %1$s dan %2$s | "penuh dengan" means "filled with", a literal calque of "full of" |
| HE/i | premium_raise, late_position_raise, playable_raise, fold_to_raise, value_bet, value_raise, fold_bad_price, free_card | kamu / membuatmu / tanganmu -> Anda / membuat Anda / tangan Anda | Anda |
| HE/i | hint_reason_semi_bluff | kamu masih bisa membaik -> tangan Anda masih bisa membaik | It is the hand that improves, not the player |
| HE/i | hint_reason_call_good_price | kamu menang sekitar %1$d%% dari waktu -> Anda menang dalam sekitar %1$d%% kasus | Calque; Anda |
| HE/i | review_title, review_followed_won/lost/fold, review_ignored_won/lost/fold, review_beaten_by, review_hindsight_fold_won/lost, review_chance_at_decision, review_chance_slim_win, review_followed_won_slim | Hint / Kamu / -mu / dari waktu -> Petunjuk / Anda / … Anda / dalam … kasus | Button name, Anda, calque |

### Needs a native speaker
- Anda vs kamu: I unified on Anda, the usual software register and what the help already used. If the brand wants the casual "kamu" coaching voice, the help and dialogs must change instead.
- `rank_*` in Hold'em are English (jack/queen/king), and the help says "Jack, Queen, dan King", but core/ui speaks the king as "Raja". Pick one set.
- `action_hit` "Ambil" / `action_stand` "Tahan": many Indonesian blackjack players just say Hit / Stand.
- `help_hand_straight_flush` "satu jenis" for suit: is "satu jenis kartu" or "bunga" clearer?

### Fit
- `review_ignored_*` strings get longer with "Petunjuk" (8 letters vs 4), but they are dialog text.
- No button overflows.

**Rating: 3.5/5.** Main weakness: inconsistent register (Anda/kamu), the English "Hint" where the UI says "Petunjuk", and "dari waktu" calques. All are fixed.

---

## Summary
hr 61, sk 58, ar 13, iw 15, hi 29 and in 50 strings changed, for 226 in total.
Most edits in hr, sk and in are mechanical register fixes (formal "you" in the hints and reviews). The real meaning errors were in hi (reversed 3:2 and 2:1 payouts, "none" written as "any"), iw (future "ייעץ" used as past) and hr (help text pointing to the Split button instead of Deal).
All 30 files pass the checks for keys, placeholders, escaping and XML. The only plural items that leave out a placeholder are Arabic `status_hand_next` `one`/`two`, which is intended and not covered by the unit test.
