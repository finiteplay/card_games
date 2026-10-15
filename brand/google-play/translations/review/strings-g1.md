# String review, group 1: es, fr, de, it, pt-rBR, nl

Scope: Blackjack `values-LOC/strings.xml` and Hold'em `values-LOC/{strings, strings_table, strings_panels, strings_integration}.xml`.
Abbreviations: **BJ** = blackjack/strings.xml, **HE** = holdem/strings.xml, **HT** = holdem/strings_table.xml, **HP** = holdem/strings_panels.xml, **HI** = holdem/strings_integration.xml.

Checked after editing with a script that mirrors `LocaleStringsCompletenessTest`. Both games and all six locales have the same key set as the base files. Every `%N$s/%N$d/%N$,d` stays in place, no value is blank, apostrophes are escaped and the XML parses.

How the code fills the parameters. The checks below depend on this.
- BJ `hint_reason_*` `%1$s` (the dealer's up card) is a digit "2".."10", or the core rank name (Ace/Jack/Queen/King) from `core/ui` `rank_*`. Several locales put a gendered article in front of it ("el/la", "le/la", "der/die/das", "il/l'", "o/a"), which gives errors like "die Ass", "le As", "il 8", "o Dama" or "el Reina". I reworded every string where a face card or an 8 can appear, so no article is needed.
- HE `review_ignored_*` `%1$s` is the seat label (`seat_action_fold/call/check/bet/raise/all_in`), not the button label.
- HE `hand_*` `%1$s` comes from `rank_singular` for straight, flush, straight flush and high card, and from `rank_plural` for the others. `leave_body`, `end_title_place`, `seat_out` and `a11y_out` take the `places` array. `cd_finish_bar` takes `place_N`.

---

## es (Spanish)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | help_round | "Tú y el crupier recibís dos cartas, y una del crupier queda boca abajo. … y no hay deshacer." -> "Recibes dos cartas, igual que el crupier, y una de las suyas queda boca abajo. … y no se puede deshacer." | The vosotros form "recibís" is Spain-only and clashes with neutral tú. "no hay deshacer" is not idiomatic. |
| BJ | help_double | "Úsalo cuando tu mano es fuerte pero aún no está terminada." -> "…sea fuerte pero aún no esté terminada." | "cuando" with a future or hypothetical meaning needs the subjunctive. |
| BJ | hint_reason_stand_dealer_weak | "mientras que pedir te pasaría el %4$d%%:" -> "mientras que si pides te pasarías el %4$d%% de las veces:" | "pedir te pasaría" uses "pasar" wrongly. Also brings back "of the time". |
| BJ | hint_figures | "… · Pedir te pasa %2$d%%" -> "… · Si pides, te pasas %2$d%%" | Same misuse of "pasar". |
| BJ | hint_reason_double | "Buen momento: %2$d contra el %1$s del crupier." -> "Buen momento: tienes %2$d y el crupier muestra %1$s." | "el %1$s" breaks for Reina/Jota ("el Reina"). The new wording needs no article. |
| HE | help_leave | "No hay deshacer, porque…" -> "No se puede deshacer, porque…" | Not idiomatic. Now matches BJ. |
| HE | help_strategy_4 | "Conoce las probabilidades del bote." -> "Conoce las pot odds (las probabilidades del bote)." | The Hint and help_hint_note say "pot odds". This ties the two terms together. |
| HT | hand_straight_flush / hand_flush / hand_straight | "…, %1$s alto" -> "…, carta alta %1$s" | "dama alto" and "jota alto" have the wrong gender. The new form is gender-neutral and matches "Carta alta, %1$s". |
| HI | hint_reason_push_short_stack | "lo mejor es all in o retirarse" -> "lo mejor es ir all in o retirarse" | Missing verb. |
| HI | review_ignored_won / _lost / _fold | "la pista sugería %1$s" -> "la pista sugería: %1$s" | The inserted seat label ("Iguala", "Se retira", "Sube 120") only reads as a quoted label with the colon. Matches BJ es. |

### Needs a native speaker
- **Regional target.** Some strings are Spain-style ("1.000", "Has ganado el torneo", "app") and the review strings use the LatAm-style preterite ("Ganaste", "Seguiste"). Decide which one the locale targets.
- `seat_action_fold` "Se retira" is inserted into `review_ignored_won/lost` as "la pista sugería: Se retira". Is that acceptable, or should the review take a separate imperative ("Retírate")? That would need a code change.
- Core `rank_queen` = "Reina" (shared `core/ui`, outside these files), while Hold'em `rank_*` uses "dama" and BJ `help_values` says "la Q". Pick one.
- `hand_bust` "Pasado", `summary_even` "Igualado", `hand_total_soft` "Blando %1$d" (perhaps "%1$d blando"): confirm what players use.
- `review_would_have_drawn` "habría dado As" has no article ("un As").
- `status_hand_next` quantity="many" "en %4$d de manos": fine for millions, but check it is wanted.

### Fit
- The new hand names are longer: "Escalera de color, carta alta rey" is 33 characters against 25 in English.
- `stat_tile_vpip` "Entró voluntariamente antes del flop" is 36 characters against 31.

**Rating: 4/5.** Main weakness: regional inconsistency (vosotros and Spain-style forms mixed with the LatAm preterite) and parameter grammar in hand names and dealer-card sentences.

---

## fr (French)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | hint_reason_double | "Une situation favorable : %2$d contre le %1$s du croupier." -> "Une situation favorable : vous avez %2$d et le croupier montre %1$s." | "le %1$s" gives "le As" and "le Dame". The new wording needs no article. |
| HE | help_actions_fold | "Vous n'êtes éliminé que de cette main." -> "Vous ne sortez que de cette main." | "éliminé" means knocked out of the tournament, which is misleading here. |
| HE | help_strategy_intro | "Elles ne feront pas gagner chaque main" -> "Elles ne vous feront pas gagner chaque main" | "faire gagner" is missing its object. |
| HE | help_strategy_7 | "les petites mises et relances" -> "les petites mises et les suivis" | Mistranslation: the English says "small bets and **calls**". |
| HT | action_fold | "Coucher" -> "Se coucher" | The standard French poker button. Bare "Coucher" reads as "put to bed". |
| HT | leave_body | "Vous finirez à la place %1$s, comme…" -> "Vous finirez %1$s, comme…" | With places = "2e" the old text read "à la place 2e", which is ungrammatical. Now matches end_title_place "Vous avez fini %1$s". |
| HP | cd_finish_bar | "Place %1$s : %2$d" -> "%1$s place : %2$d" | "Place 2e" becomes "2e place". |
| HI | hint_reason_push_short_stack | "tapis ou coucher est le meilleur choix" -> "jouer tapis ou se coucher est le meilleur choix" | Bare nouns as subject. Now matches help_strategy_7 "jouez tapis". |
| HI | hint_reason_semi_bluff | "et vous pouvez encore progresser." -> "et votre main peut encore s'améliorer." | "progresser" is the wrong sense. This is "improve the hand". |

### Needs a native speaker
- **Typography (global, not changed).** Every " :", " ?", " !" and " ;" uses a plain space. On a narrow card the punctuation can wrap onto its own line. Consider a no-break space (U+00A0 or U+202F) across all fr files.
- `hand_bust` "Brûlé" and "se brûler" against "sauter" or "dépasser": which do French blackjack players expect?
- `summary_even` "Égal" for a zero net. Perhaps "À l'équilibre".
- `review_beaten_by` and `hint_threat_item` insert `help_hand_*_title`, so the text reads "face à Une paire", "Une paire 7 %". Is the capital "Une" mid-sentence acceptable?
- `review_followed_won_slim` "Une bonne décision n'a pas toujours de la chance": check whether it reads naturally.
- `places` "1er" is always masculine.

### Fit
- `action_reset_chips` "Réinitialiser les jetons" is 24 characters against 11. It may wrap on a small button.
- `action_fold` is now 10 characters against 4. It is still a single short phrase.
- `stat_tile_vpip` is 33 characters.

**Rating: 4/5.** Main weakness: a few meaning slips in the Hold'em help (calls rendered as raises, "éliminé"), placeholder grammar with ordinals, and plain spaces before French punctuation.

---

## de (German)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | help_goal | "bist du sofort verloren" -> "hast du sofort verloren" | "bist verloren" means "you are doomed". |
| BJ | help_split | "Geteilte Asse bekommen je eine Karte und können nicht weiter gezogen werden." -> "Geteilte Asse bekommen je nur eine Karte, danach kannst du nicht weiter ziehen." | The old text said the aces themselves cannot be drawn. |
| BJ | help_insurance | "kannst du für die Hälfte…versichern" -> "kannst du dich für die Hälfte…versichern" | "versichern" needs the reflexive "dich" here. |
| BJ | help_strategy_2 | "weil er wahrscheinlich überkauft" -> "weil er sich wahrscheinlich überkauft" | Reflexive, as in the rest of the file. |
| BJ | hint_reason_double | "Eine starke Lage: %2$d gegen die %1$s des Gebers." -> "Eine starke Lage: Du hast %2$d, der Geber zeigt %1$s." | "die %1$s" gives "die Ass" and "die König". |
| BJ | review_title, review_followed_won/_lost/_push, review_ignored_won/_lost/_push (7) | "Hinweis" -> "Tipp" ("Rückblick auf den Tipp", "Du bist dem Tipp gefolgt", "der Tipp empfahl") | The button (`core/ui` action_hint) and help_strategy_intro say **Tipp**. The reviews refer to that button. |
| HE | help_actions_fold | "Fold: Gib deine Karten … auf." -> "Fold: deine Karten … aufgeben." | Matches the infinitive style of the other action entries. |
| HE | help_strategy_4 | "für je drei möglichen Gewinn" -> "für je drei, die du gewinnen kannst" | Ungrammatical. |
| HE | help_strategy_6 | "eines losen, aggressiven Spielers" -> "eines loosen, aggressiven Spielers" | The seat tag says "loose". "lose" means slack, not the poker term. |
| HE | help_hint_note | "Zum Hinweis:" -> "Zum Tipp:" | Same Tipp consistency. |
| HT | ann_call | "%1$s called %2$,d" -> "%1$s callt %2$,d" | English past tense. Now matches "foldet", "checkt". |
| HI | review_title, review_followed_won/_lost/_fold, review_ignored_won/_lost/_fold, review_beaten_by, review_followed_won_slim (9) | "Hinweis" -> "Tipp" | Same Tipp consistency. Both nouns are masculine, so "Seine Begründung" still agrees. |

### Needs a native speaker
- BJ `label_dealer` "Geber" (the Skat term) against "Dealer" or "Croupier". Hold'em uses "Dealer". Should Blackjack match?
- `outcome_push` "Push", `review_followed_push` "hattest einen Push": is this understood, or should it be "Unentschieden"?
- `hint_figures` "Eine Karte überkauft dich %2$d%%" is compact but non-reflexive.
- HE hint reasons use English imperatives ("Raise, um…", "Bet, damit…", "Check, um…"). This matches the English buttons, but confirm the tone.
- `help_actions_call` "den Einsatz mitgehen" is colloquial. Perhaps "beim Einsatz mitgehen".

### Fit
- `action_reset_chips` "Chips zurücksetzen" is 18 characters.
- `stat_tile_showdowns_won` and `stat_tile_vpip` are fine.

**Rating: 4/5.** Main weakness: inconsistent naming of the Hint feature (Tipp against Hinweis) and some reflexive and case grammar slips.

---

## it (Italian)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | help_round | "e non c'è annulla." -> "e non si può annullare." | Uses the button name as a noun, which is not idiomatic. |
| BJ | hint_reason_double | "Una situazione forte: %2$d contro il %1$s del banco." -> "Una situazione favorevole: hai %2$d e il banco mostra %1$s." | "il %1$s" gives "il Asso", "il 8" and "il Regina". "situazione forte" is unnatural. |
| BJ | hint_reason_split_pair | "Contro il %1$s del banco, …" -> "Se il banco mostra %1$s, …" | "il 8" should be "l'8". The split can be against an 8. |
| HE | help_showdown | "Se tutti gli altri passano" -> "Se tutti gli altri fanno fold" | "passare" is ambiguous (fold or check). The locale uses "fold" everywhere else. |
| HE | help_leave | "Non c'è annulla, perché" -> "Non si può annullare, perché" | Not idiomatic. |
| HE | help_strategy_6 | "fidati meno in fretta della puntata" -> "credi meno facilmente alla puntata" | Awkward phrasing. |
| HT | hand_straight_flush / hand_flush / hand_straight | "…, %1$s alto" -> "…, carta alta %1$s" | "donna alto" has the wrong gender. Now matches "Carta alta, %1$s". |

### Needs a native speaker
- Core `rank_queen` "Regina" (shared `core/ui`) against "donna" in Hold'em and BJ help.
- Call terminology is mixed: the buttons say "Call", while the help and announcements say "chiamare"/"chiama". Acceptable in Italian poker, but confirm.
- BJ `label_dealer` "Banco", `hand_total_soft` "Morbido %1$d", `summary_even` "Pari": confirm.
- `cd_finish_bar` "Posto %1$s: %2$d" reads "Posto 2º", where "2º posto" is more natural.

### Fit
- `action_deal` "Distribuisci" is 12 characters against 4.
- `stat_tile_vpip` "Entrato volontariamente prima del flop" is 38 characters, the longest in this group.

**Rating: 4/5.** Main weakness: article and elision errors when card names are inserted, and treating the "annulla" button word as a noun.

---

## pt-rBR (Brazilian Portuguese)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | help_round | "e não há desfazer." -> "e não é possível desfazer." | Not idiomatic. |
| BJ | help_double | "Use quando a mão é forte, mas ainda não está pronta." -> "Use quando a mão estiver forte, mas ainda não estiver pronta." | "quando" needs the future subjunctive. |
| BJ | hint_reason_double | "Uma boa situação: %2$d contra o %1$s do dealer." -> "Uma boa situação: você tem %2$d e o dealer mostra %1$s." | "o %1$s" gives "o Dama". |
| HE | help_leave | "Não há desfazer, porque" -> "Não é possível desfazer, porque" | Not idiomatic. Now matches BJ. |

### Needs a native speaker
- `hand_total_soft` "Mole %1$d" and "mão mole": is "soft" more common among Brazilian players?
- `hint_figures` "Pedir estoura você %2$d%%" is understandable but clumsy.
- `summary_even` "Igual"; `review_hindsight_fold_won` "teria ganho" against "teria ganhado".

### Fit
- `action_raise` "Aumentar para" is 13 characters on the first line, against "Raise to" at 8. Check the button width.
- `stat_tile_vpip` is 36 characters.

**Rating: 4.5/5.** Main weakness: a few non-idiomatic constructions ("não há desfazer"), otherwise clean and consistent. The "topo: %1$s" hand-name pattern already avoided the gender problem.

---

## nl (Dutch)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | hint_reason_stand_dealer_weak | "terwijl een kaart jou in %4$d%% bust zou zetten:" -> "terwijl jij met een kaart in %4$d%% van de gevallen bust zou gaan:" | "bust zetten" is not Dutch. The locale uses "bust gaan" everywhere else. |
| BJ | hint_reason_decline_insurance | "de dichte kaart" -> "de gedekte kaart" | The same file uses "gedekte kaart" twice. |
| HE | help_allin | "die erin hebben bijgedragen" -> "die eraan hebben bijgedragen" | The verb takes "bijdragen aan". |
| HE | help_strategy_5 | "Als laatste aan de beurt zijn laat je eerst zien wat iedereen doet" -> "Als je als laatste aan de beurt bent, zie je eerst wat iedereen doet" | "laat je … zien" reads as "you show", which reverses the meaning. |
| HI | hint_reason_free_flop | "zie de flop zonder kosten." -> "bekijk de flop gratis." | The imperative "zie" is stiff. |

### Needs a native speaker
- BJ `label_dealer` "Deler" against "dealer", which Hold'em and Dutch casinos use.
- `hint_figures` "Een kaart bust jou %2$d%%" is Dunglish. Perhaps "Met een kaart bust je %2$d%%".
- BJ `help_split` "kunnen niet verder pakken" makes the aces the ones that "take".
- HE `help_strategy_6` "losse, agressieve speler" against the seat tag "loose".

### Fit
No concerns. The labels are close to English length.

**Rating: 4/5.** Main weakness: anglicised verb constructions around "bust" and one ambiguous "laat je zien" that inverts the meaning.

---

## Cross-locale notes (outside these files)
- `core/ui` `rank_queen` uses Reina (es) and Regina (it), while the Hold'em hand names say dama and donna. Fix this in core so the card names match the hand names.
- Blackjack `review_would_have_drawn` inserts a bare rank name ("Ace") into the sentence. Most languages want an article ("un As", "ein Ass", "een aas"). It is grammatical as a label but reads abruptly.
- The `stat_tile_vpip` tile is 33 to 38 characters in every Romance locale. Check the tile at the smallest phone width.

## Summary
- es: 14 strings changed. fr: 9 strings changed.
- de: 26 strings changed (17 of them are the Hinweis -> Tipp consistency rename). it: 9 strings changed.
- pt-rBR: 4 strings changed. nl: 5 strings changed.
