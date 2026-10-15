# String review, group 2: uk, pl, sv, nb, da, fi

Scope: Blackjack `values-LOC/strings.xml`; Hold'em `values-LOC/strings.xml`, `strings_table.xml`, `strings_panels.xml`, `strings_integration.xml`.
Paths below are abbreviated: **BJ** = blackjack `strings.xml`, **HE** = holdem `strings.xml`, **HE-table**, **HE-panels**, **HE-int** = the other holdem files.

Checked against the English strings and the shared `core/ui` resources. In particular, the Hint button label (`action_hint`) and rank names (`rank_ace` etc.) are what gets inserted into `%1$s`. In Blackjack hints the dealer's up card is a digit (2–10) or a capitalised rank name in nominative form ("Туз", "As", "Ässä"). Hold'em hand names get `rank_singular` / `rank_plural`.

After editing, a script checked every file against the unit-test rules: the XML parses, the key set matches the English translatable keys, each string has the same positional placeholder set and types, there's no bare `%`, and no apostrophe is left unescaped. It found **0 problems**.

---

## uk (Ukrainian)

### Changes
| File | Key | Old → New | Reason |
|---|---|---|---|
| BJ | summary_even | Рівно → Без змін | "Рівно" is a calque and unclear as a net result; "Без змін" reads naturally |
| BJ | help_goal | …що вартує більше за руку дилера… → …що має більше очок, ніж рука дилера… | "вартує" is colloquial/non-standard; points are what's compared |
| BJ | help_strategy_5 | …зупиняйтеся з м’яких 19. → …зупиняйтеся на м’яких 19 і більше. | "з" (from) was unclear; now says "on soft 19 or more" as in the English |
| BJ | hint_reason_stand_strong | %2$d це сильна рука → %2$d — це сильна рука | Missing dash between subject and predicate noun |
| BJ | hint_reason_stand_dealer_weak | Дилер показує %1$s і… → Відкрита карта дилера — %1$s, і він… | "показує" needs the accusative ("туза", "даму"), but the inserted card name is nominative ("Туз", "Дама") |
| BJ | hint_reason_hit_dealer_strong | Дилер показує %1$s і… лише у %5$d%% → Відкрита карта дилера — %1$s, і він… лише в %5$d%% | Same case problem; у/в euphony |
| BJ | hint_reason_double | %2$d проти %1$s дилера → у вас %2$d, а відкрита карта дилера — %1$s | "проти" needs the genitive; it gave "проти Туз дилера" |
| BJ | hint_reason_split_aces | Два тузи це… A-A це… → Два тузи — це… A-A — це… | Missing dashes |
| BJ | hint_reason_split_pair | Проти %1$s дилера… → Коли відкрита карта дилера — %1$s,… | Genitive case problem (see above) |
| BJ | hint_figures | Дилер перебирає в %1$d%% · Карта перебирає вас у %2$d%% → Перебір дилера %1$d%% · Ваш перебір після карти %2$d%% | "Карта перебирає вас" is unnatural; the new label is also shorter |
| BJ | hint_figures_dealer_only | Дилер перебирає в %1$d%% → Перебір дилера %1$d%% | Consistent with hint_figures |
| BJ | review_ignored_won | …шансам це везіння… → …шансам — це везіння… | Missing dash |
| HE | help_strategy_6 | Кожне місце позначено як тайтовий… → Кожного суперника позначено: тайтовий… | Gender clash: neuter "місце" with masculine adjectives |
| HE | help_strategy_7 | З малим стеком → З коротким стеком | "Короткий стек" is the standard poker term |
| HE-int | hint_notice_desc | %1$d відсотків … %2$d відсотків → %1$d%% … %2$d%% | "відсотків" is wrong for 1–4, 21–24…; TalkBack reads "%" with the correct inflection |
| HE-int | hint_notice_free_desc | %1$d відсотків → %1$d%% | Same |
| HE-int | recovery_notice | роздали заново з початку → роздали заново | Tautology |
| HE-int | hint_reason_push_short_stack | Лишилося лише %1$d великих блайндів → Лишилося лише %1$d BB | Plural agreement broke for 1–4 (common short-stack values); BB is already the table marker |
| HE-int | hint_reason_fold_short_stack | same → same | Same |
| HE-int | hint_reason_semi_bluff | (%1$d аутів) → (аутів: %1$d) | Broke for 2–4 outs |
| HE-int | hint_reason_call_draw | (%1$d аутів) → (аутів: %1$d) | Same |
| HE-int | hint_reason_call_good_price | ви вигравате → ви виграєте | Typo/wrong conjugation |
| HE-int | hint_reason_fold_bad_price | Для колу → Для коллу | Spelling consistent with "колл/коллу" elsewhere |
| HE-int | hint_reason_pot_control | чекайте → зробіть чек | "чекайте" means "wait" in Ukrainian: a real misreading |
| HE-int | review_ignored_won | …пораді це везіння… → …пораді — це везіння… | Missing dash |

### Needs a native speaker
- BJ action_insure "Страхування": a noun among verb buttons. "Застрахувати" would be consistent but longer.
- BJ stat_tile_net "Чисті фішки": calque. Maybe "Підсумок фішок"?
- BJ/HE-int review_title "Погляд назад на підказку": a literal calque. Maybe "Розбір підказки"?
- BJ review_stand_chances "зі стопом на %1$d": awkward; "якщо зупинитися на %1$d" may read better.
- BJ review_would_have_drawn "вона була б %1$s": predicate nominative with a capitalised rank ("Туз") is acceptable but stiff.
- HE help_hand_cards "дві приватні карти, свої закриті карти": calque-ish wording.
- HE-int short-stack hints now use "BB". Confirm this reads naturally to Ukrainian players.
- Poker loanwords (колл, рейз, фолд, еквіті, тайтовий, лузовий): confirm these against current UA player usage.

### Fit (flag only)
action_next_hand "Наступна роздача" (16 vs 9 chars), action_next_round "Наступний раунд", action_insure "Страхування" / action_decline "Відмовитися" (11 vs 6–7), action_reset_chips "Скинути фішки", stat_tile_vpip "Добровільно у грі до флопу".

### Rating: 4/5
**Main weakness:** card names and counts inserted mid-sentence broke grammatical case and plural agreement. There was also one false-friend verb ("чекайте").

---

## pl (Polish)

### Changes
| File | Key | Old → New | Reason |
|---|---|---|---|
| BJ | hand_bust | Przegrana → Fura | "Przegrana" (a loss) was identical to banner_lose and lost the "over 21" meaning. "Fura" is the traditional Polish over-21 term |
| BJ | hint_reason_stand_strong | grozi przegraną → grozi przekroczeniem 21 | Bust ≠ lose |
| BJ | hint_reason_stand_dealer_weak | Krupier pokazuje %1$s i przegrywa… a ty po dobraniu przegrasz w %4$d%% → Odkryta karta krupiera to %1$s i krupier przekracza 21… a ty po dobraniu przekroczysz 21 w %4$d%% | Meaning error: the numbers are bust rates, not loss rates. The old version also used the nominative after "pokazuje" ("pokazuje As") |
| BJ | hint_reason_hit_dealer_strong | Krupier pokazuje %1$s… ryzyka przegranej → Odkryta karta krupiera to %1$s i krupier… ryzyka przekroczenia 21 | Same two problems |
| BJ | hint_reason_hit_cannot_bust | nie możesz przegrać dobraniem → nie przekroczysz 21 | Wrong meaning: you can still lose the round |
| BJ | hint_reason_hit_soft | nie przegra na następnej karcie → nie przekroczy 21 po następnej karcie | Same |
| BJ | hint_reason_double | %2$d przeciw %1$s krupiera → masz %2$d, a odkryta karta krupiera to %1$s | "przeciw" needs the dative ("asowi"), but the nominative was inserted |
| BJ | hint_reason_split_pair | Przeciw %1$s krupiera… → Gdy odkryta karta krupiera to %1$s,… | Same |
| BJ | hint_figures | Krupier przegrywa w %1$d%% · Dobranie przegrywa u ciebie w %2$d%% → Krupier przekracza 21: %1$d%% · Ty po dobraniu: %2$d%% | Wrong meaning plus an ungrammatical second half |
| BJ | hint_figures_dealer_only | Krupier przegrywa w %1$d%% → Krupier przekracza 21: %1$d%% | Wrong meaning |
| BJ | review_would_have_drawn | Dobranie karty dałoby %1$s, czyli razem %2$d. → Następna karta: %1$s, co dałoby razem %2$d. | "dałoby" needs the accusative ("asa", "króla", "damę") |
| HE | help_strategy_7 | Z małym stosem → Z krótkim stackiem | "Stos" is a literal pile; Polish players say "krótki stack" |
| HE-table | end_body_won | Zajęło to %1$d rozdań. → Zwycięstwo po %1$d rozdaniach. | "rozdań" is wrong for 22–24, 32–34…; the locative plural is invariant |
| HE-int | hint_reason_push_short_stack | Zostało tylko %1$d dużych blindów → Zostało ci tylko %1$d BB | Plural agreement broke for 2–4 |
| HE-int | hint_reason_fold_short_stack | same → same | Same |
| HE-int | hint_reason_value_raise | wobec zakładu → wobec stawki | Terminology: "stawka/postaw" everywhere else |
| HE-int | hint_reason_semi_bluff | (%1$d outów): zakład może → (outy: %1$d): stawka może | Plural agreement for 2–4 outs; term consistency |
| HE-int | hint_reason_call_draw | (%1$d outów) → (outy: %1$d) | Plural agreement |

### Needs a native speaker
- BJ hand_bust "Fura": confirm. The alternative is "Ponad 21", which is clearer for beginners.
- BJ action_stand "Pas" (and "pasuj" in the help/hints): in Hold'em, "Pas" is Fold. The oczko tradition supports "pas", but "Stój" would avoid the clash across the two games. That change would touch about 8 strings.
- Gendered address: the past tense is masculine only (Zastosowałeś, wygrałeś, Spasowałeś, Odpadł, Spasował). Decide whether neutral rewording is wanted.
- HE "szanse puli" for pot odds, and "ciasny/luźny" for tight/loose: Polish players often use the English terms.
- HE-int "Mocne draw": the gender of "draw" in Polish varies (mocny/mocne).
- BJ/HE-int review_title "Spojrzenie wstecz na podpowiedź": a calque.

### Fit (flag only)
action_next_hand "Następne rozdanie", action_reset_chips "Zresetuj żetony", action_call "Wyrównaj" (8 vs 4 chars, on a two-line button), stat_tile_vpip "Dobrowolnie w grze przed flopem".

### Rating: 3/5
**Main weakness:** in Blackjack, "bust" was translated as "lose" throughout the hints. The figures were therefore wrong, saying the dealer loses X% when X% is the bust rate. Several card-name insertions also had the wrong case.

---

## sv (Swedish)

### Changes
| File | Key | Old → New | Reason |
|---|---|---|---|
| BJ | help_round | det finns ingen ångra → det går inte att ångra | "ångra" is a verb, not a noun |
| BJ | help_dealer | de tar kort tills de har → hen tar kort tills hen har | Plural "de" for one dealer; Hold'em already uses "hen" |
| BJ | hint_reason_split_aces | varje kan bli 21 → vart och ett kan bli 21 | "varje" can't stand alone as a pronoun |
| BJ | hint_figures | …· Att ta kort gör att du går över %2$d%% → …· Du går över på nytt kort %2$d%% | Clumsy and long for a figures line |
| BJ | review_title | Tillbakablick på Hint → Tillbakablick på tipset | The button is labelled **Tips** in sv (`core action_hint`) and the help already says "Tipsknappen"; "Hint" contradicted it |
| BJ | review_followed_won / _lost / _push | Du följde Hint… → Du följde tipset… | Same (3 strings) |
| BJ | review_ignored_won / _lost | Hint föreslog → tipset / Tipset föreslog | Same (2 strings) |
| BJ | review_ignored_push | Hint föreslog: %1$s och du… → Tipset föreslog: %1$s, och du… | Same; the comma stops "Stanna och du" reading as one phrase |
| BJ | review_would_have_drawn | vilket gör %2$d → vilket hade gett %2$d | Tense (counterfactual) |
| BJ | review_stand_chances | vann Stanna på %1$d → vann att stanna på %1$d | A button label used as a sentence subject |
| HE | help_leave | Det finns ingen ångra → Det går inte att ångra | Same as BJ |
| HE | help_strategy_2 | när den blir syn → när den blir synad | Wrong word ("syn" = sight) |
| HE | help_strategy_4 | en mark → en marker | "mark" means ground or currency; the singular of "marker" is "en marker" |
| HE | help_hint_note | Om Hint: den visar… eftersom den inte… → Om tipset: det visar… eftersom tipset inte… | Tips/Hint consistency; neuter pronoun |
| HE-int | review_title, review_followed_won, review_followed_lost, review_followed_fold, review_ignored_won, review_ignored_lost, review_ignored_fold, review_beaten_by, review_followed_won_slim | Hint → tipset / Tipset | Tips/Hint consistency (9 strings) |

### Needs a native speaker
- BJ label_dealer "Givare": Hold'em uses "dealer", and Swedish casinos usually say "dealern" or "croupiern".
- BJ hand_bust "Över": the traditional 21 term is "Tjock".
- HE: the mix of English and Swedish actions (Fold/Check/Call/Bet, but "Höj") is common in practice. Confirm it is intended.
- "Dess skäl" (in the review strings) is slightly formal. Maybe "Skälet:"?
- BJ hint_figures: the new phrase "Du går över på nytt kort" should be checked by a native speaker.

### Fit (flag only)
action_reset_chips "Återställ marker", stat_tile_resets "Återställningar" (15 vs 6 chars), stat_tile_avg_finish "Genomsnittlig placering".

### Rating: 4/5
**Main weakness:** the coaching strings called the feature "Hint" while the button says "Tips". There were also a few word-level slips (syn, mark, ångra).

---

## nb (Norwegian Bokmål)

### Changes
| File | Key | Old → New | Reason |
|---|---|---|---|
| BJ | reset_offer_title | tom for sjetonger → Tom for sjetonger | Missing capital on a dialog title |
| BJ | help_round | det finnes ingen angre → det går ikke an å angre | "angre" is a verb, not a noun |
| BJ | help_dealer | de trekker kort til de har → hen trekker kort til hen har | Plural "de" for one dealer; Hold'em uses "hen" |
| BJ | hint_reason_split_aces | hvert kan bli 21 → hvert av dem kan bli 21 | Bare "hvert" is stiff |
| BJ | hint_figures | Trekk gir bust for deg %2$d%% → Du går bust ved trekk %2$d%% | Unnatural phrasing |
| HE | help_allin | Potene vises → Pottene vises | Typo: the plural of "pott" is "potter"/"pottene" |
| HE | help_leave | Det finnes ingen angre → Det går ikke an å angre | Same as BJ |

### Needs a native speaker
- HE help_strategy_2 "når den blir calt": "callet" may be the more common participle.
- BJ help_dealer: "hen" or "den" for the dealer.
- HE hand names mix English (Straight, Flush, Full house) and Norwegian (Fire like, Tre like). This is common usage, but confirm it.

### Fit (flag only)
action_reset_chips "Nullstill sjetonger", stat_tile_resets "Nullstillinger", label_place_bet "Legg inn innsatsen".

### Rating: 4.5/5
**Main weakness:** small slips: a noun misuse of "angre", one typo, and one lowercase title.

---

## da (Danish)

### Changes
| File | Key | Old → New | Reason |
|---|---|---|---|
| HE-table | you | Dig → Du | Object form used as the seat name. It is also inserted as the subject ("Dig folder") |
| HE-table | your_line | Dig · %1$,d → Du · %1$,d | Same |
| HE-table | a11y_seat_you | Dig, %1$,d jetoner → Du, %1$,d jetoner | Same |
| BJ | help_round | der er ingen fortryd → du kan ikke fortryde | "fortryd" is a verb, not a noun |
| BJ | help_dealer | de tager kort, indtil de har 17 → …ingen valg, men tager kort, indtil hånden er på 17 | Plural "de" for one dealer |
| BJ | hint_reason_split_aces | To es… hvert kan → To esser… hvert af dem kan | Wrong plural (`rank_plural` and the help text use "esser") |
| BJ | hint_figures | Træk giver bust for dig %2$d%% → Du går bust ved træk %2$d%% | Unnatural phrasing |
| HE | help_leave | Det gør det ikke at lukke appen… Der er ingen fortryd → At lukke appen gør ikke det… Du kan ikke fortryde | Awkward double "det"; "fortryd" noun misuse |
| HE | help_strategy_2 | bliver called → bliver callet | Danish participle inflection |

### Needs a native speaker
- HE-table ann_bet "byder": the other announcements use English verbs (caller, raiser). Maybe "better"?
- BJ hint_reason_double "Et stærkt spot": an anglicism, though poker players use it. Hold'em uses "situation".
- HE-table hand_straight_flush / hand_flush / hand_straight "%1$s højt": check agreement with the inserted rank ("es højt", "konge højt").

### Fit (flag only)
action_reset_chips "Nulstil jetoner", stat_tile_resets "Nulstillinger", stat_tile_avg_finish "Gennemsnitlig placering".

### Rating: 4/5
**Main weakness:** "You" was in the object form ("Dig") on the seat label, the chip line and in announcements. Otherwise only small noun/verb slips.

---

## fi (Finnish)

### Changes
| File | Key | Old → New | Reason |
|---|---|---|---|
| BJ | help_insurance | Se maksaa… ja muuten menetetään. → Vakuutus maksaa… ja muuten se menetetään. | The second clause had no subject |
| BJ | hint_reason_double | %2$d jakajan %1$s vastaan → sinulla on %2$d, ja jakajalla näkyy %1$s | "vastaan" needs the partitive ("ässää"), but the nominative was inserted |
| BJ | hint_reason_split_pair | Jakajan %1$s vastaan… → Kun jakajalla näkyy %1$s,… | Same |
| BJ | review_dealer_finished | Jakaja päätti käteen %1$d. → Jakajan loppusumma: %1$d. | Unidiomatic, and avoids inflecting the number |
| BJ | review_would_have_drawn | olisit saanut %1$s → se olisi ollut %1$s | The object needs the accusative ("ässän", "kuninkaan"); the predicate nominative works with the inserted name |
| HE | help_actions_bet | big blindin… ½ pot ja Pot… Koroituksen → ison blindin… ½ potti ja Potti… Korotuksen | "Koroitus" is a misspelling. The blind term now matches the rest of the help, and the button names match the new labels |
| HE | help_strategy_3 | Fold useammin → Foldaa useammin | An English imperative inside Finnish text; "foldata" is already used |
| HE | help_strategy_4 | Jos maksaminen maksaa 25 → Jos maksaminen vaatii 25 | "Paying costs": an awkward repetition |
| HE | help_hint_note | jonka maksaminen maksaa → jonka maksaminen vaatii | Same |
| HE | help_strategy_7 | mene all in tai fold → mene all in tai foldaa | Finnish imperative |
| HE-table | sizing_half_pot | ½ pot → ½ potti | The only untranslated "pot"; everything else says "potti" |
| HE-table | sizing_pot | Pot → Potti | Same |
| HE-int | hint_notice_desc | maksaminen maksaa → maksaminen vaatii | Same repetition |
| HE-int | hint_reason_push_short_stack | big blindiä… all iniin → isoa blindiä… all in -siirtoon | Term consistency with the help ("iso blindi"); "all iniin" is clumsy |
| HE-int | hint_reason_fold_short_stack | big blindiä → isoa blindiä | Term consistency |

### Needs a native speaker
- BJ action_split "Jaa" vs action_deal "Jaa kortit": the same verb on two buttons, though they never appear together.
- HE-table hand names with partitive plurals ("Neloset: ässiä", "Pari: ässiä", "Täyskäsi: ässiä ja kuninkaita"). "Pari ässiä" without the colon, or nominative plurals, may read better. The array is shared, so all hand names need to be decided together.
- "flop" (help) vs "floppi" (hints) vs "flopia" (stats): pick one.
- help_title is "Pelaaminen" in BJ but "Pelaamisen perusteet" in HE. Align them?
- HE-int hint_notice_desc "%1$d prosenttia" is wrong only for 1 (rare).
- HE-table seat_action_call "Maksoi" is past tense while the other seat actions are nouns.

### Fit (flag only)
action_reset_chips "Nollaa pelimerkit", stat_tile_vpip "Vapaaehtoisesti mukana ennen flopia", stat_tile_avg_finish "Keskimääräinen sijoitus", stat_tile_net "Nettopelimerkit", sizing_raise_desc "Korota summaan" (a11y only).

### Rating: 4/5
**Main weakness:** case marking on inserted card names, and inconsistent mixing of English and Finnish poker terms (pot/potti, big blindi/iso blindi, fold/foldaa).

---

## Summary
- **uk:** 25 changed (BJ 12, HE 2, HE-int 11) · **pl:** 18 changed (BJ 11, HE 1, HE-table 1, HE-int 5)
- **sv:** 26 changed (BJ 13, HE 4, HE-int 9) · **nb:** 7 changed (BJ 5, HE 2)
- **da:** 9 changed (BJ 4, HE 2, HE-table 3) · **fi:** 15 changed (BJ 5, HE 5, HE-table 2, HE-int 3). Total: 100 strings changed.
