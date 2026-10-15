# Translation review, group 3: tr, el, cs, ro, hu, bg

Scope: Blackjack `values-LOC/strings.xml`; Hold'em `values-LOC/strings.xml`, `strings_table.xml`, `strings_panels.xml`, `strings_integration.xml`.
Abbreviations: BJ = blackjack/strings.xml, HS = holdem/strings.xml, HT = holdem/strings_table.xml, HP = holdem/strings_panels.xml, HI = holdem/strings_integration.xml.
Keys, placeholders, `\'`, `%%`, comments and order were kept; no translatable="false" string was touched.

## tr (Turkish)

### Changes

Address: Hold'em, both Hint/Review sets and the Blackjack banners already use informal "sen"; the Blackjack help, the bet prompt and the reset dialog used formal "siz". They were switched to "sen" so each locale uses one form of address.

| File | Key | Old | New | Reason |
|---|---|---|---|---|
| BJ | label_place_bet | Bahsinizi koyun | Bahsini koy | Informal address, like the rest of the locale |
| BJ | reset_offer_title | Fişiniz bitti | Fişlerin bitti | Informal address |
| BJ | reset_offer_body | …karşılayamıyorsunuz… İstatistikleriniz korunur. | …karşılayamıyorsun… İstatistiklerin korunur. | Informal address |
| BJ | help_goal | …el yapın. Geçerseniz batarsınız ve hemen kaybedersiniz. | …el yap. Geçersen batarsın ve hemen kaybedersin. | Informal address |
| BJ | help_chips | başlarsınız / Fişleriniz / sıfırlayabilirsiniz / istatistikleriniz | başlarsın / Fişlerin / sıfırlayabilirsin / istatistiklerin | Informal address |
| BJ | help_round | Bahsinizi koyun ve Dağıt\'a basın. Siz ve krupiye… Sonra kararlarınızı, her seferinde bir elle, verirsiniz ve işiniz bitince… | Bahsini koy ve Dağıt\'a bas. Sen ve krupiye… Sonra kararlarını her seferinde bir el için verirsin ve işin bitince… | Informal address; removed the awkward comma-split "her seferinde bir elle" |
| BJ | help_hit | Bir kart daha alın. Eliniz… edebilirsiniz. | Bir kart daha al. Elin… edebilirsin. | Informal address |
| BJ | help_stand | Elinizi olduğu gibi bırakın ve sıranızı bitirin. | Elini olduğu gibi bırak ve sıranı bitir. | Informal address |
| BJ | help_double | …bahsinizi ikiye katlayın… alın. Sıranız sona erer. Eliniz güçlü ama henüz bitmemişse kullanın. | …bahsini ikiye katla… al. Ardından sıran sona erer. Elin güçlü ama henüz tamamlanmamışsa kullan. | Informal address; "bitmemiş" (not over) read as "the hand has not ended", "tamamlanmamış" says "not finished yet" |
| BJ | help_split | …iki ele bölün… | …iki ele böl… | Informal address |
| BJ | help_insurance | bahsin yarısı kadar sigorta yaptırabilirsiniz | bahsinin yarısı kadar sigorta yaptırabilirsin | Informal address ("half your bet") |
| BJ | help_strategy_intro | İyi şans veren basit bir plan. | Sana iyi bir kazanma şansı veren basit bir plan. | "İyi şans" reads as "good luck", not "good odds" |
| BJ | help_strategy_1 … help_strategy_6 | çekin / durun / katlayın / Katlayamıyorsanız / bölün / bölmeyin / oynayın / yaptırmayın | çek / dur / katla / Katlayamıyorsan / böl / bölme / oyna / yaptırma | Informal address (6 strings) |
| BJ | hint_reason_double | Güçlü bir durum: krupiyenin %1$s karşısında %2$d. | Güçlü bir durum: krupiye %1$s gösteriyor, sende %2$d var. | "krupiyenin 6 karşısında" lacks the possessive suffix the inserted card needs ("6\'sı", "As\'ı"); rewording avoids any suffix on %1$s |
| BJ | hint_reason_split_pair | Krupiyenin %1$s karşısında bölmek… | Krupiye %1$s gösterirken bölmek… | Same suffix problem with %1$s |
| BJ | hint_reason_decline_insurance | …kapalı kart üçte birden azında ona gelir, başa baş olmak için gereken de budur. | …kapalı kart üç seferde birden az on değerindedir, oysa başa baş için en az üçte bir gerekir. | "ona gelir" reads as "comes to him"; the logic (needs at least one in three) was also inverted |
| BJ | review_ignored_won | Şansa karşı kazanmak şanstır, plan değil. | Olasılıklara karşı kazanmak şanstır, plan değil. | "against the odds" was rendered "against luck" ("winning against luck is luck") |
| BJ | review_stand_chances | …zamanın yaklaşık %%%2$d\'inde kazandı, %%%3$d berabere kaldı ve %%%4$d kaybetti. | …yaklaşık %%%2$d ihtimalle kazanıyor, %%%3$d berabere kalıyor ve %%%4$d kaybediyordu. | Fixed suffix "\'inde" is wrong for most numbers (%30\'unda, %40\'ında…); also "kazandı" (it won) misstated a probability |
| HS | help_button | Bu, pot\'u başlatır | Bu, potu başlatır | Turkish spelling: no apostrophe on a common noun (the file also uses "potu", "pota") |
| HS | help_showdown | pot\'u ×3 | potu ×3 | Same |
| HS | help_ties | eşit eller pot\'u paylaşır | eşit eller potu paylaşır | Same |
| HS | help_hand_straight_flush | Bunların en iyisi, ondan ası kadar olanı… | Bunların en iyisi, onludan asa kadar olanı… | "ondan" reads as "from it"; "ası kadar" is the wrong case ("asa kadar") |
| HS | help_hand_straight | onludan ası kadar | onludan asa kadar | Wrong case |
| HS | help_strategy_2 | Raise pot\'u anında… | Raise potu anında… | Apostrophe |
| HS | help_strategy_4 | 75\'lik bir pot\'u … dört seferde bir\'den fazla | 75\'lik bir potu … dört seferde birden fazla | Apostrophes on a common noun and on "bir" |
| HS | help_hint_note | pot odds\'un, yani bir call\'ın pot\'tan alacağı payın yanında | pot odds\'un, yani call için ödeyeceğin miktarın pottaki payının yanında | Said "the share a call takes from the pot"; now "the share of the pot your call costs" |
| HT | action_fold | Pas | Fold | Every other action button and all help/hint text use the English poker word ("Fold: …", "fold et"); "Pas" also means *check* to many Turkish players |
| HT | seat_action_fold | Pas | Fold | Same; it is also the %1$s inserted into the Review lines next to "fold ettin" |
| HT | a11y_folded | Pas geçti | Fold etti | Same term as the button |
| HT | ann_fold | %1$s pas geçti | %1$s fold etti | Same |
| HT | leave_title | Bu turnuvadan ayrılınsın mı? | Turnuvadan ayrılmak istiyor musun? | Impersonal passive optative is unnatural for a dialog question |
| HT | ann_shows | %1$s gösterir: %2$s | %1$s gösterdi: %2$s | Aorist ("shows, habitually") among past-tense announcements |
| HI | hint_notice_desc | …bir call pot\'un yüzde %2$d\'sine mal olur. | …call için ödenen miktar potun yüzde %2$d kadarı. | Fixed suffix "\'sine" is wrong for most numbers; apostrophe on "pot" |
| HI | hint_reason_premium_raise | pot\'u büyütmek | potu büyütmek | Apostrophe |
| HI | hint_reason_call_good_price | zamanın yaklaşık %%%1$d\'ini kazanıyorsun | yaklaşık %%%1$d ihtimalle kazanıyorsun | Fixed suffix after a variable number |
| HI | hint_reason_pot_control | pot\'u küçük tutmak | potu küçük tutmak | Apostrophe |
| HI | review_followed_lost | yaklaşık %%%1$d ile zamanın %%%2$d\'inde kaybedersin. | yaklaşık %%%1$d şansla bile %%%2$d ihtimalle kaybedersin. | Fixed suffix after a variable number |

### Needs a native speaker
- BJ hand_bust / help texts "batmak": many Turkish players say "patlamak" for bust; confirm which is more familiar.
- BJ hand_total_soft "Yumuşak %1$d", help "sert 17": literal "soft/hard"; Turkish players often keep "Soft 17 / Hard 17".
- BJ help_split "aynı değerdeyse": English says same *rank*; "değer" (value) suggests 10 and King can be split. Confirm a better word (e.g. "aynı kartsa").
- HT action_fold / seat_action_fold now "Fold": confirm the English loanword over "Pas" / "Çekil"; the help glossary and hints already used "fold".
- HT action_raise "Raise&#10;%1$,d" and sizing_raise_desc drop "to" (raise *to* a total); acceptable since seat labels do the same, but a native may prefer "Raise:" consistently.
- HT seat_won / seat_lost "Kazandı %1$s": verb-first order; "+150 kazandı" may read better.
- HT ann_* with %1$s = "Sen": "Sen fold etti" (third-person verb with "you"), same as English "You folds"; no rewording avoids it.

### Fit
- BJ hint_figures and HI hint_notice_desc are 20–30% longer than English; fine on cards that wrap.
- BJ action_double "İkiye katla" is 2 words for a 1-word button; it fits within the two reserved lines.

### Rating
4/5. Main weakness: Turkish suffixes glued to variable numbers and inserted card names (\'inde, \'sine, "krupiyenin %1$s"), plus mixed formal/informal address between the games.

## el (Greek)

### Changes

Address: Hold'em and the Blackjack result banners ("Κέρδισες!", "Έχασες") use informal "εσύ"; the rest of Blackjack used formal "εσείς". Blackjack was switched to "εσύ" (verbs and pronouns only; wording otherwise kept).

| File | Key | Old | New | Reason |
|---|---|---|---|---|
| BJ | action_reset_chips | Επαναφορά μάρκες | Επαναφορά μαρκών | Grammar: needs the genitive |
| BJ | label_place_bet | Ποντάρετε | Πόνταρε | Informal address |
| BJ | reset_offer_body | Δεν καλύπτετε… | Δεν καλύπτεις… | Informal address |
| BJ | help_goal | Κάντε… ξεπεράσετε… χάνετε | Κάνε… ξεπεράσεις… χάνεις | Informal address |
| BJ | help_chips | Ξεκινάτε… Τα ποντάρισματα… μπορείτε να επαναφέρετε σε 1.000 | Ξεκινάς… Τα πονταρίσματα… μπορείς να κάνεις επαναφορά σε 1.000 | Informal address; accent typo "ποντάρισματα"; "επαναφέρετε" lacked an object |
| BJ | help_round | Ποντάρετε και πατήστε… Εσείς… παίρνετε τις αποφάσεις σας… τελειώσετε | Πόνταρε και πάτα… Εσύ… παίρνεις τις αποφάσεις σου… τελειώσεις | Informal address |
| BJ | help_hit | Πάρτε… Μπορείτε… σας | Πάρε… Μπορείς… σου | Informal address |
| BJ | help_stand | Κρατήστε… τελειώστε τη σειρά σας | Κράτα… τελείωσε τη σειρά σου | Informal address |
| BJ | help_double | διπλασιάστε… πάρτε… σας… Χρησιμοποιήστε το | διπλασίασε… πάρε… σου… Χρησιμοποίησέ το | Informal address |
| BJ | help_split | χωρίστε τες… Μπορείτε να έχετε | χώρισέ τες… Μπορείς να έχεις | Informal address |
| BJ | help_insurance | μπορείτε να ασφαλιστείτε | μπορείς να ασφαλιστείς | Informal address |
| BJ | help_strategy_1 | Παίρνετε… σταματάτε | Παίρνε… σταμάτα | Informal address |
| BJ | help_strategy_2 | σταματήστε… πιθανόν θα υπερβεί. Πάρτε κάρτα | σταμάτα… πιθανόν θα κάνει υπέρβαση. Πάρε κάρτα | Informal address; "θα υπερβεί" has no object, now matches the bust term used everywhere |
| BJ | help_strategy_3 | Διπλασιάστε… μπορείτε, πάρτε | Διπλασίασε… μπορείς, πάρε | Informal address |
| BJ | help_strategy_4 | Διαχωρίστε πάντα… Ποτέ πεντάρια ή δεκάρια· παίξτε τα… | Χώριζε πάντα… Μη χωρίζεις ποτέ πεντάρια ή δεκάρια· παίξε τα… | Informal address; restored the dropped verb ("never split") |
| BJ | help_strategy_5 | πάρτε… σταματήστε από μαλακό 19 | πάρε… σταμάτα από μαλακό 19 και πάνω | Informal address; "or more" was dropped |
| BJ | help_strategy_6 | Μην παίρνετε ποτέ ασφάλιση. | Μην παίρνεις ποτέ ασφάλιση. | Informal address |
| BJ | hint_reason_stand_strong | ρισκάρετε | ρισκάρεις | Informal address |
| BJ | hint_reason_stand_dealer_weak | εσείς θα κάνατε… μείνετε και αφήστε | εσύ θα έκανες… μείνε και άφησε | Informal address |
| BJ | hint_reason_hit_dealer_strong | μείνετε: πάρτε κάρτα | μείνεις: πάρε κάρτα | Informal address |
| BJ | hint_reason_hit_cannot_bust | μπορείτε… πάρτε… βελτιώσετε | μπορείς… πάρε… βελτιώσεις | Informal address |
| BJ | hint_reason_hit_soft | χάνετε… βελτιώστε το χέρι σας | χάνεις… βελτίωσε το χέρι σου | Informal address |
| BJ | hint_reason_double | Εξαιρετική περίπτωση: %2$d απέναντι στο %1$s του κράτη. Διπλασιάστε… πάρτε… αξιοποιήστε την. | Δυνατή θέση: έχεις %2$d και ο κράτης δείχνει %1$s. Διπλασίασε… πάρε… αξιοποίησέ την. | "στο Άσος" breaks (masculine noun, wrong article/case); "strong spot", not "excellent"; informal |
| BJ | hint_reason_split_eights | διαχωρίστε τα… ξεκινήσετε | χώρισέ τα… ξεκινήσεις | Informal address |
| BJ | hint_reason_split_pair | Απέναντι στο %1$s του κράτη, ο διαχωρισμός σας δίνει… | Όταν ο κράτης δείχνει %1$s, ο διαχωρισμός σού δίνει… | Same article/case break with "Άσος"; informal |
| BJ | hint_reason_decline_insurance | …η κλειστή κάρτα είναι δεκάρι λιγότερες από μία στις τρεις φορές, ενώ για ισοσκελισμό θα έπρεπε να είναι. | …η κλειστή κάρτα έχει αξία δέκα λιγότερο από μία στις τρεις φορές, ενώ για να βγεις ίσα θα έπρεπε να έχει τουλάχιστον τόσο συχνά. | "δεκάρι" is only the 10 card, not any ten-value card; the last clause was incomplete |
| BJ | hint_figures | Με νέα κάρτα εσείς %2$d%% | Με νέα κάρτα εσύ %2$d%% | Informal address |
| BJ | review_followed_won / _lost / _push | Ακολουθήσατε… κερδίσατε / χάσατε… Κρίνετε / κρατήσατε… σας | Ακολούθησες… κέρδισες / έχασες… Κρίνε / κράτησες… σου | Informal address (3 strings) |
| BJ | review_ignored_won / _lost / _push | Κερδίσατε / διαλέξατε… χάσατε / εσείς διαλέξατε | Κέρδισες / διάλεξες… έχασες / εσύ διάλεξες | Informal address (3 strings) |
| BJ | review_you_busted | Κάνατε υπέρβαση με %1$d. | Έκανες υπέρβαση με %1$d. | Informal address |
| BJ | review_would_have_drawn | Αν παίρνατε κάρτα… | Αν έπαιρνες κάρτα… | Informal address |
| BJ | review_stand_chances | Όταν αποφασίσατε… | Όταν αποφάσισες… | Informal address |
| HS | help_strategy_6 | …και πίστευε πιο αργά ένα ποντάρισμα από χαλαρό, επιθετικό. | …και μην πιστεύεις εύκολα ένα ποντάρισμα από χαλαρό, επιθετικό παίκτη. | Literal calque "believe more slowly"; adjective had no noun |
| HS | help_strategy_7 | …να διπλασιαστείς. | …να διπλασιάσεις τις μάρκες σου. | "διπλασιαστείς" = "to be doubled yourself" |
| HP | stat_tile_hands_won | Χέρια που κερδήθηκαν | Κερδισμένα χέρια | "κερδήθηκαν" is a non-standard passive; shorter tile label |
| HP | stat_tile_showdowns_won | Σόουνταουν που κερδήθηκαν | Κερδισμένα σόουνταουν | Same |
| HT | rank_singular[2], rank_plural[2] | τρίο / τρίο | τριάρι / τριάρια | Matches the shared card name "Τριάρι" and has a plural; "τρίο" also means three of a kind ("Τρία ίδια, τρίο") |
| HT | hand_straight_flush | Στρέιτ φλας, ψηλό %1$s | Στρέιτ φλας, %1$s ψηλά | "ψηλό" (neuter) does not agree with άσος / ρήγας / βαλές / ντάμα; adverb "ψηλά" needs no agreement |
| HT | hand_flush | Φλας, ψηλό %1$s | Φλας, %1$s ψηλά | Same |
| HT | hand_straight | Στρέιτ, ψηλό %1$s | Στρέιτ, %1$s ψηλά | Same |
| HI | hint_reason_playable_raise | …στο πόνταρισμα | …στο ποντάρισμα | Accent typo |
| HI | hint_reason_value_raise | …σε πόνταρισμα | …σε ποντάρισμα | Accent typo |
| HI | hint_reason_semi_bluff | το πόνταρισμα | το ποντάρισμα | Accent typo |

### Needs a native speaker
- BJ label_dealer "Κράτης" (and every BJ string): the traditional Greek word for the banker in 21; Hold'em uses "ντίλερ". Confirm whether Blackjack players expect "ντίλερ".
- BJ hand_bust / "υπέρβαση": Greek 21 players usually say "κάηκες" / "καμένος"; "Υπέρβαση" is correct but bookish.
- BJ action_stand / help_stand_title "Στάση": "Μένω" (which the hints already imply with "μείνε") may be the more natural button.
- BJ summary_even "Ισόπαλο": means "drawn"; for a net of zero, "Χωρίς κέρδος" or "Στα ίσα" may be clearer.
- HT hand_straight_flush / hand_flush / hand_straight "%1$s ψηλά": confirm this reads naturally (alternative "με ψηλό φύλλο %1$s", longer).
- HT leave_body "στη θέση %1$s" gives "στη θέση 3η"; "στην %1$s θέση" would be more natural but changes the word order.
- HT a11y_bet / seat_action_bet "Πόνταρε %1$,d": works as both imperative and past 3rd person; confirm it reads right on an opponent's seat.

### Fit
- BJ action_double "Διπλασιασμός" (12 letters), action_split "Διαχωρισμός" (11), action_bet_up / action_bet_down "Ποντάρισμα +%1$d" are long for one-word buttons; "Διπλό", "Σπλιτ", "Πόνταρε +%1$d" would be shorter.
- HT action_leave_tournament "Αποχώρηση από το τουρνουά" is about twice the English length.

### Rating
4/5. Main weakness: Blackjack used formal address against an informal Hold'em; a few gender/case clashes when a card name is inserted.

## cs (Czech)

### Changes

Address: Hold'em and the Blackjack Hint/Review lines use "ty"; the Blackjack help, bet prompt and reset dialog used "vy". They were switched to "ty".

| File | Key | Old | New | Reason |
|---|---|---|---|---|
| BJ | label_place_bet | Vsaďte | Vsaď | Informal address |
| BJ | reset_offer_body | Nemůžete pokrýt… | Nemůžeš pokrýt… | Informal address |
| BJ | help_goal | Získejte… aniž byste překročili 21. Pokud ji překročíte, hned prohráváte. | Získej… aniž bys překročil 21. Pokud ji překročíš, hned prohráváš. | Informal address |
| BJ | help_chips | Začínáte… Dojdou-li vám žetony, můžete… | Začínáš… Dojdou-li ti žetony, můžeš… | Informal address |
| BJ | help_values | Spodek, dáma a král mají 10. | Kluk, dáma a král mají 10. | "Spodek" is the German-suited card; the card labels and Hold'em ranks use "Kluk" |
| BJ | help_round | Vsaďte a stiskněte… Vy i krupiér… rozhodujete… skončíte | Vsaď a stiskni… Ty i krupiér… rozhoduješ… skončíš | Informal address |
| BJ | help_hit | Vezměte další kartu. Můžete lízat, dokud je vaše ruka pod 21. | Vezmi si další kartu. Můžeš lízat, dokud je tvoje ruka pod 21. | Informal address |
| BJ | help_stand | Ponechte ruku, jak je, a ukončete svůj tah. | Ponech si ruku, jak je, a ukonči svůj tah. | Informal address |
| BJ | help_double | zdvojnásobte… vezměte… Váš tah… Použijte to | zdvojnásob… vezmi si… Tvůj tah… Použij to | Informal address |
| BJ | help_split | rozdělte… můžete… a už se k nim nelíze. | rozděl… můžeš… a už se k nim nelíže. | Informal address; typo "nelíze" |
| BJ | help_insurance | můžete se pojistit | můžeš se pojistit | Informal address |
| BJ | help_strategy_intro | …který vám dá dobré šance. | …který ti dá dobré šance. | Informal address |
| BJ | help_strategy_1 | lízejte… stůjte | lízej… stůj | Informal address |
| BJ | help_strategy_2 | stůjte… pravděpodobně přeleze. Lízejte… | stůj… pravděpodobně přetáhne. Lízej… | Informal; "přeleze" (climbs over) is not the bust term used everywhere else ("přetáhne") |
| BJ | help_strategy_3 | Zdvojte… Nemůžete-li zdvojit, lízejte. | Zdvoj… Nemůžeš-li zdvojit, lízej. | Informal address |
| BJ | help_strategy_4 | Esa a osmičky vždy dělte. Pětky a desítky nikdy nedělte; hrajte je… | Esa a osmičky vždy rozděl. Pětky a desítky nikdy neděl; hraj je… | Informal; the button says "Rozdělit" |
| BJ | help_strategy_5 | lízejte do měkké 17 a stůjte od měkké 19. | lízej do měkké 17 a stůj od měkké 19 výš. | Informal; restored "or more" |
| BJ | help_strategy_6 | Pojištění nikdy neberte. | Pojištění nikdy neber. | Informal address |
| BJ | hint_reason_double | Silná situace: %2$d proti krupiérovu %1$s. | Silná situace: máš %2$d a krupiér ukazuje %1$s. | "proti krupiérovu Eso/6" needs the dative and gender of the inserted card; rewording keeps %1$s in the nominative |
| BJ | hint_reason_split_pair | Proti krupiérovu %1$s ti rozdělení dá… | Když krupiér ukazuje %1$s, rozdělení ti dá… | Same case problem |
| BJ | hint_reason_decline_insurance | …skrytá karta je desítka méně než jednou ze tří, což by bylo potřeba, aby vyšlo nastejno. | …skrytá karta má hodnotu deset méně než jednou ze tří, a přitom by to muselo být aspoň jednou ze tří, aby to vyšlo nastejno. | "desítka" is only the 10 card; the old clause said "less than one in three" is what is needed |
| BJ | review_would_have_drawn | Kdybys lízl, přišlo by %1$s a měl bys %2$d. | Kdybys lízl, přišla by karta %1$s a měl bys %2$d. | "přišlo" (neuter) only agreed with Eso, not Dvojka/Král; "karta" carries the gender |
| HS | help_strategy_4 | Pokud vyrovnání stojí 25 žetonů a vyhraješ banku 75, | Pokud vyrovnání stojí 25 žetonů a můžeš vyhrát banku 75, | Said "and you win the pot"; it is the pot you can win |
| HS | help_strategy_6 | …a věř pomaleji sázce od loose, agresivního. | …a nevěř tak snadno sázce od loose, agresivního hráče. | Calque "believe more slowly"; adjective had no noun |
| HS | help_strategy_7 | …zdvojnásobit se. | …zdvojnásobit žetony. | "to double yourself" |
| HT | seat_action_bet | Sází %1$,d | Sázka %1$,d | These labels are also the %1$s in "nápověda navrhovala: %1$s"; a 3rd-person verb ("navrhovala: Zahazuje") is wrong there, a noun fits both uses |
| HT | seat_action_raise | Zvyšuje %1$,d | Zvýšení %1$,d | Same |
| HT | seat_action_call | Vyrovnává | Vyrovnání | Same |
| HT | seat_action_fold | Zahazuje | Zahození | Same |
| HT | end_body_won | Trvalo to %1$d her.\n… | Počet her: %1$d.\n… | "her" is wrong for 2–4 ("3 hry"); label form works for any number |
| HI | hint_reason_push_short_stack | Zbývá jen %1$d velkých blindů: … | Máš už jen %1$d BB: … | "velkých blindů" is wrong for 1–4, which short stacks often are; BB is the table's own marker |
| HI | hint_reason_fold_short_stack | Zbývá jen %1$d velkých blindů: … | Máš už jen %1$d BB: … | Same |
| HI | review_followed_fold | …a šetřil jsi žetony na lepší chvíli. | …a ušetřil sis žetony na lepší chvíli. | Perfective "saved", not "were saving" |

### Needs a native speaker
- Hold'em "banka" (pot): most Czech players say "bank" (masc.). The files mix both genders ("Banka", but "½ banku", "do banku"). Pick one and align ~20 strings (sizing_pot, pot_label, banner_*, a11y_pot*, stat_tile_largest_pot, end_body_*, help texts, hint_notice "Šance banky").
- HT seat_action_* nouns ("Sázka 120", "Zahození"): confirm they read well on a seat badge.
- Hold'em uses "hra" for a hand (Další hra, Odehrané hry) and "ruka" in the hints (Slabá ruka); acceptable, but a native may prefer one.
- BJ hand_bust "Přes" vs the verb "přetáhnout" in the hints: both are used by players; confirm.
- Generic masculine ("Řídil ses", "Vyhrál jsi", "kdybys lízl") throughout, the usual Czech default.
- BJ hint_figures "Líznutí tě přetáhne %2$d%%": understandable but stiff; "Při líznutí přetáhneš %2$d%%" may read better.

### Fit
- BJ action_reset_chips "Obnovit žetony" and action_double "Zdvojit" are fine. HT action_leave_tournament "Opustit turnaj" is fine. No string stands out as much longer than English.

### Rating
4/5. Main weakness: case and gender agreement around inserted values (card names, counts of big blinds, seat labels reused as advice), and the bank/banka split.

## ro (Romanian)

### Changes

Address is informal ("tu") in both games, consistently.

| File | Key | Old | New | Reason |
|---|---|---|---|---|
| BJ | action_hit | Cartă | Carte | "Cartă" means a charter; the playing card is "carte" |
| BJ | help_hit_title | Cartă | Carte | Same |
| BJ | action_deal | Împarte cărțile | Dă cărțile | Split is "Împarte"; two buttons both starting "Împarte" are confusing |
| BJ | help_round | …apasă Împarte. | …apasă Dă cărțile. | It named the Split button's label instead of Deal |
| BJ | action_decline | Refuz | Refuză | Every other button is an imperative; "Refuz" reads "I refuse" and also feeds "Indiciul a sugerat: %1$s" |
| BJ | hand_total_soft | Moale %1$d | %1$d moale | Romanian order is noun then adjective, as the help already writes ("17 moale") |
| BJ | help_strategy_4 | Împarte mereu asii și optarii. Nu împărți niciodată cincii sau zecii; joacă-i… | Împarte mereu perechile de ași și de 8. Nu împărți niciodată perechile de 5 sau de 10; joacă-le… | "cincii", "zecii" are not natural card plurals |
| HS | help_goal | Câștigă toți jetoanele… | Câștigă toate jetoanele… | Gender agreement (jetoane is neuter plural) |
| HS | help_button | …se mută un loc spre dreapta… | …se mută un loc în sensul acelor de ceasornic… | "to the right" is not "clockwise" (clockwise passes to each player's left) |
| HS | help_strategy_6 | …și crede mai încet un pariu de la unul lejer, agresiv. | …și fii mai sceptic față de un pariu de la unul lejer, agresiv. | Calque "believe more slowly" |
| HT | end_body_won | A durat %1$d mâini.\n… | Număr de mâini: %1$d.\n… | Numbers of 20 or more need "de" ("25 de mâini"); a winning tournament is usually that long |
| HT | a11y_seat | %1$s, %2$s, %3$,d jetoane | %1$s, %2$s, jetoane: %3$,d | Stacks are almost always 20 or more ("1.500 de jetoane"); label form avoids it |
| HT | a11y_seat_you | Tu, %1$,d jetoane | Tu, jetoane: %1$,d | Same |
| HT | ann_turn | Turnul: %1$s | Turn-ul: %1$s | "Turnul" means "the tower"; matches "River-ul" |
| HI | hint_reason_push_short_stack | Au rămas doar %1$d big blinduri: … | Mai ai doar %1$d BB: … | "1 big blinduri" breaks; BB is the table's own marker |
| HI | hint_reason_fold_short_stack | Au rămas doar %1$d big blinduri: … | Mai ai doar %1$d BB: … | Same |

### Needs a native speaker
- HI review_followed_won, review_hindsight_*, review_chance_* "aveai cam %1$d%% șanse": strictly "%1$d%% de șanse" for values of 20 or more; confirm whether the colloquial form is acceptable.
- HS/HT hand names mix English and Romanian (Straight, Straight flush, Full house vs Culoare, Careu); many Romanian players say "Chintă" for straight. Pick one style.
- BJ action_insure "Asigurare" is a noun among imperatives; "Asigură" would match the other buttons.
- BJ hint_reason_stand_strong "o carte în plus riscă să depășești 21 pentru câștig mic": understandable but clumsy.
- HT places are bare digits ("1"…"6"), so seat_out reads "Eliminat · 3"; "locul 3" is clearer if it fits.

### Fit
- BJ action_reset_chips "Resetează jetoanele" and HT action_leave_tournament "Părăsește turneul" are about 1.5× English; acceptable.

### Rating
4/5. Main weakness: a few wrong words that change meaning (Cartă, Turnul, "spre dreapta") and number agreement with "de" for counts of 20 or more.

## hu (Hungarian)

### Changes

Address: everything is informal ("te") except two Blackjack strings, which were switched.

| File | Key | Old | New | Reason |
|---|---|---|---|---|
| BJ | action_split | Osztás | Felosztás | Split and Deal were both "Osztás": two buttons with the same label |
| BJ | help_split_title | Osztás | Felosztás | Matches the button |
| BJ | help_split | …oszd két kézre… Az osztott ászok… | …oszd fel két kézre… A felosztott ászok… | Same term as the button |
| BJ | help_strategy_4 | …mindig oszd. Ötösöket és tízeseket soha; … | …mindig oszd fel. Az ötösöket és a tízeseket soha ne oszd fel; … | Same term; restored the dropped verb ("never split") |
| BJ | hint_reason_split_aces | …a legjobb pár az osztáshoz | …a legjobb pár a felosztáshoz | Same term |
| BJ | hint_reason_split_eights | …oszd meg őket… | …oszd fel őket… | "megoszt" means "share"; same term as the button |
| BJ | hint_reason_split_pair | …az osztás két jó kezet ad… | …a felosztás két jó kezet ad… | Same term |
| BJ | label_place_bet | Tegye meg a tétjét | Tedd meg a tétedet | Formal "Ön" in an otherwise informal locale |
| BJ | reset_offer_body | Nem tudja fedezni… Visszaállítja… | Nem tudod fedezni… Visszaállítod… | Same |
| BJ | help_chips | …nem vásárolhatók és nem váltható be őket. | …nem vásárolhatók meg és nem válthatók be. | Broken agreement ("váltható be őket") |
| HS | help_actions_all_in | All in: feltesz minden zsetonodat. | All in: felteszed az összes zsetonodat. | Wrong person (3rd instead of 2nd definite) |
| HS | help_strategy_3 | …az eldobott kéz csak azt költi, amit már betettél. | …az eldobott kéz csak annyiba kerül, amennyit már betettél. | "költ" means "spends", not "costs you" |
| HS | help_strategy_6 | …és lassabban higgy egy laza, agresszív játékos tétjének. | …és kevésbé higgy egy laza, agresszív játékos tétjének. | Calque "believe more slowly" |
| HS | help_strategy_7 | …a legjobb esélyed a megduplázódásra. | …a legjobb esélyed a zsetonjaid megduplázására. | "megduplázódás" means "being doubled yourself" |
| HS | help_hint_note | megmutatja az equityd, | megmutatja az equitydet, | Missing accusative ending |
| HT | sizing_minus_desc | Csökkentés %1$,d-vel | Csökkentés ennyivel: %1$,d | A fixed "-vel" is wrong for most amounts (20-szal, 100-zal, 50-nel) |
| HT | sizing_plus_desc | Növelés %1$,d-vel | Növelés ennyivel: %1$,d | Same |
| HT | ann_fold | %1$s eldobja | %1$s eldobja a lapjait | Transitive verb had no object |
| HI | review_beaten_by | Ellene vesztettél: %1$s. | Ez a kéz vert meg: %1$s. | "Ellene" pointed back to nothing; now names the hand that beat you |

### Needs a native speaker
- BJ action_split "Felosztás": confirm the term Hungarian players expect ("Split", "Szétválasztás", "Felezés" are also used).
- BJ help_split "értéke azonos": English says same *rank*; "érték" (value) suggests 10 + King can be split.
- BJ summary_even "Egyenlő": "Nullszaldó" (already used in the insurance hint) may read better as a round result.
- BJ action_decline "Elutasít" and action_hit "Lapot kér" are 3rd-person forms beside noun buttons ("Duplázás", "Biztosítás"); acceptable in Hungarian UIs, but pick one style.
- HS help_strategy_2 "A puszta megadás olcsón engedi mások számára a lapok megnézését": correct but clumsy.

### Fit
- BJ action_reset_chips "Zsetonok visszaállítása" (23 characters) is long for a button; "Új zsetonok" would be shorter.
- HT action_leave_tournament "Kilépés a versenyből" is about 1.3× English; fine.

### Rating
4/5. Main weakness: two identical action labels (Split and Deal) and Hungarian case endings fixed after variable numbers.

## bg (Bulgarian)

### Changes

Address: Hold'em is informal ("ти"); Blackjack was formal ("Вие") throughout. Blackjack was switched to "ти" (verbs and pronouns only; wording otherwise kept unless noted).

| File | Key | Old | New | Reason |
|---|---|---|---|---|
| BJ | label_place_bet | Заложете | Заложи | Informal address |
| BJ | reset_offer_body | Не можете да покриете… | Не можеш да покриеш… | Informal address |
| BJ | help_goal | Вземете… надвишите… Ако надвишите, губите | Вземи… надвишиш… Ако надвишиш, губиш | Informal address |
| BJ | help_chips | Започвате… чиповете ви… можете да ги нулирате | Започваш… чиповете ти… можеш да ги нулираш | Informal address |
| BJ | help_round | Заложете и натиснете… Вие и дилърът… взимате… приключите | Заложи и натисни… Ти и дилърът… взимаш… приключиш | Informal address |
| BJ | help_hit | Вземете… Можете… ръката ви | Вземи… Можеш… ръката ти | Informal address |
| BJ | help_stand | Запазете ръката си и приключете хода си. | Запази ръката си и приключи хода си. | Informal address |
| BJ | help_double | удвоете… вземете… Ходът ви… Използвайте го | удвои… вземи… Ходът ти… Използвай го | Informal address |
| BJ | help_split | разделете ги… всяка със започналия залог. Общо можете… | раздели ги… всяка с първоначалния залог. Общо можеш… | Informal; "започналия залог" ("the bet that started") was a mistranslation of "the original bet" |
| BJ | help_insurance | можете да се застраховате | можеш да се застраховаш | Informal address |
| BJ | help_strategy_intro | …който ви дава добри шансове. | …който ти дава добри шансове. | Informal address |
| BJ | help_strategy_1 | теглете… спирайте | теглѝ… спирай | Informal address |
| BJ | help_strategy_2 | спрете… вероятно ще надвиши. Теглете… | спри… вероятно ще прехвърли. Теглѝ… | Informal; same bust verb as the rest of the game |
| BJ | help_strategy_3 | Удвоявайте… можете… теглете | Удвоявай… можеш… теглѝ | Informal address |
| BJ | help_strategy_4 | разделяйте… не разделяйте… играйте | разделяй… не разделяй… играй | Informal address |
| BJ | help_strategy_5 | теглете… спирайте | теглѝ… спирай | Informal address |
| BJ | help_strategy_6 | Никога не вземайте застраховка. | Никога не взимай застраховка. | Informal address |
| BJ | hint_reason_stand_dealer_weak | вие бихте се прехвърлили… спрете и оставете | ти би се прехвърлил… спри и остави | Informal address |
| BJ | hint_reason_hit_dealer_strong | ако спрете: вземете карта | ако спреш: вземи карта | Informal address |
| BJ | hint_reason_hit_cannot_bust | не можете… вземете… подобрите | не можеш… вземи… подобриш | Informal address |
| BJ | hint_reason_hit_soft | не ви струва… подобрете | не ти струва… подобри | Informal address |
| BJ | hint_reason_double | Удвоете… вземете… извлечете | Удвои… вземи… извлечи | Informal address |
| BJ | hint_reason_split_eights | разделете ги… започнете | раздели ги… започнеш | Informal address |
| BJ | hint_reason_split_pair | …разделянето ви дава… | …разделянето ти дава… | Informal address |
| BJ | hint_reason_decline_insurance | …скритата карта е десетка по-рядко от един път от три, а за равновесие трябва точно това. | …скритата карта е със стойност десет по-рядко от веднъж на три пъти, а за да излезеш на нула, трябва да е поне толкова често. | "десетка" is only the 10 card; the old clause said "less than one in three" is what is needed |
| BJ | hint_figures | Картата ви прехвърля %2$d%% | Картата те прехвърля %2$d%% | Informal address |
| BJ | review_followed_won / _lost / _push | Последвахте… спечелихте / загубихте… Оценявайте / запазихте | Последва… спечели / загуби… Оценявай / запази | Informal address (3 strings) |
| BJ | review_ignored_won / _lost / _push | Спечелихте / избрахте… загубихте / а вие избрахте | Спечели / ти избра… загуби / а ти избра | Informal address (3 strings) |
| BJ | review_you_busted | Прехвърлихте се с %1$d. | Прехвърли се с %1$d. | Informal address |
| BJ | review_would_have_drawn | Ако бяхте взели карта… щяхте да имате %2$d. | Ако беше взел карта… щеше да имаш %2$d. | Informal address |
| BJ | review_stand_chances | Когато решихте… равенство в %3$d%%… | Когато реши… завършваше наравно в %3$d%%… | Informal; the middle clause had no verb |
| HS | help_ties | Две двойки от аса и седмици бият две двойки от попове и дами… Брои се само пет карти… | Два чифта аса и седмици бият два чифта попове и дами… Броят се само пет карти… | The hand is called "Два чифта" in this file ("двойка" is also the 2 card); verb agreement |
| HS | help_strategy_6 | …и вярвай по-бавно на залог от хлабав, агресивен играч. | …и не вярвай лесно на залог от хлабав, агресивен играч. | Calque "believe more slowly" |
| HT | seat_action_raise | Вдига %1$,d | Вдигане %1$,d | This label is also the advice in "подсказката предлагаше: %1$s"; a 3rd-person verb is wrong there. The noun matches "Залог %1$,d" |
| HT | hand_straight_flush | Стрийт флъш, висока %1$s | Стрийт флъш до %1$s | "висока" (feminine) does not agree with асо / поп / вале; "до" needs no agreement |
| HT | hand_straight | Стрийт, висока %1$s | Стрийт до %1$s | Same |
| HT | hand_flush | Флъш, висока %1$s | Флъш с %1$s | Same ("флъш с асо") |
| HI | hint_reason_push_short_stack | Остават само %1$d големи блайнда: … | Имаш само %1$d BB: … | "1 големи блайнда" breaks; BB is the table's own marker |
| HI | hint_reason_fold_short_stack | Остават само %1$d големи блайнда: … | Имаш само %1$d BB: … | Same |
| HI | hint_reason_value_bet | …заложи, за да плащат по-слабите ръце, за да видят още карти. | …заложи, така че по-слабите ръце да плащат, за да видят още карти. | Two "за да" in a row |
| HI | hint_reason_semi_bluff | …а ти още можеш да подобриш. | …а ти още можеш да подобриш ръката си. | Transitive verb had no object |

### Needs a native speaker
- Aces: Blackjack says "ас / асове / два аса", Hold'em and the shared card labels say "асо / аса". Pick one across both games.
- BJ action_reset_chips "Нулиране", reset_offer_body "Да нулираме ли до %1$d чипа?": "нулиране" (zeroing) is the usual UI word for reset, but "reset to 1,000" can read as a contradiction; "Възстанови" / "Нови чипове" may be clearer.
- BJ hand_bust "Прехвърляне": correct; confirm players would not expect "Прехвърли" / "Бъст" on the hand label.
- BJ action_stand "Стоп": fine and short; the help calls it "спиране".
- HT hand_flush "Флъш с %1$s" / hand_straight "Стрийт до %1$s": confirm the phrasing for showdown labels.
- HT end_title_place "Завърши на %1$s място" / end_title_won "Спечели турнира": aorist 2nd singular equals 3rd singular; reads correctly as a title, but "Завърши" could be taken as an imperative.

### Fit
- BJ action_double "Удвояване" (10) and action_split "Разделяне" (9) are fine. HI hint texts are about 1.2× English. No string is a fit concern.

### Rating
4/5. Main weakness: Blackjack used formal address against an informal Hold'em, and adjective agreement broke when a rank name was inserted.

## Summary
tr: 42 strings changed; el: 48 strings changed (36 are the Blackjack switch to informal address).
cs: 35 strings changed; ro: 16 strings changed.
hu: 19 strings changed; bg: 45 strings changed (35 in Blackjack, mostly informal address).
