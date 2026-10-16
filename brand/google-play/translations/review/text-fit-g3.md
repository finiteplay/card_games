# Text-fit review, group 3 (tr, el, cs, ro, hu, bg)

**Status: unreviewed machine translation.** These are wording changes proposed and applied by an AI reviewer to fix layout-audit findings. No native speaker has checked them. Treat every new term as a draft until a native speaker has reviewed it (see "Needs a native speaker" at the end).

Input: `fit_groups/g3.json` (22 findings). Sources: `TRANSLATION_GUIDE.md`, every `values-LOC` string file for the five games and `core/ui`, and the store-listing drafts.

## How widths were checked

Word widths were measured with Roboto Medium (from the Android SDK). The unit is pixels at a font size of 100 px, so the numbers only make sense compared with each other. I set the limits from what the audit showed fits and what breaks:

| Box | Fits (measured) | Breaks (measured) | Estimated limit | Target used |
|---|---|---|---|---|
| Bottom bar, 195 px | Beállítások 496, Ρυθμίσεις 466 | Újrajátszás 497, Настройки 509, Επανάληψη 519 | ~496 | <= 430 |
| Hold'em bar, 160 px | (none observed) | Nastavení 448, Nápověda 449, Ρυθμίσεις 466 | ~400 | <= 330 |
| Help or stats tab, 232 px | Strategie 406, Κανόνες 383 | Combinații 483, Стратегия 483, Kombinace 502 | ~460 | <= 390 |
| Dropdown, 504 px (1.15 scale) | Výchozí systémový 864 | Προεπιλογή συστήματος 1131 | ~980 | <= 950 |

No Hold'em template items (key `null`) were in this group.

## tr (Turkish)

No findings in this group. No files changed.

## el (Greek)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_double | games/blackjack/.../values-el/strings.xml | Διπλασιασμός -> **Διπλό** | 632 -> 279 | "Double", short noun form. It matches the noun style of the other buttons (Κάρτα, Στάση). | Διπλάσιο (425; less idiomatic), Διπλασίασε (536; too wide), Ντάμπλ (transliteration) |
| action_replay | core/ui/.../values-el/strings.xml | Επανάληψη -> **Ξανά** | 519 -> 219 | "Again", a common label for replaying the same deal. | Ξαναπαίξε (476; too close to the limit) |
| language_system | core/ui/.../values-el/strings.xml | Προεπιλογή συστήματος -> **Γλώσσα συστήματος** | 1131 -> 941 | "System language", the usual Android wording. | Από το σύστημα (736), Του συστήματος (749): both shorter but less idiomatic |
| help_tab_hands | games/holdem/.../values-el/strings.xml | Συνδυασμοί -> **Χέρια** | 534 -> 263 | "Hands". The app already uses χέρι for a poker hand (Επόμενο χέρι). | (none shorter that keeps "combinations") |
| help_tab_strategy | games/holdem, games/blackjack, games/klondike (values-el) | Στρατηγική -> **Τακτική** | 491 -> 371 | "Tactics", a close common synonym. Changed in all three games so the tab has one name. | Συμβουλές ("Tips", 9 letters) |
| action_settings | core/ui/.../values-el/strings.xml | Ρυθμίσεις (**unchanged**) | 466 | Breaks only in the Hold'em 160 px bar at 1.15 scale. No common, shorter word exists. | Επιλογές (422; still over the ~400 limit) |

Other strings updated to keep the terms consistent:
- `help_double_title` in games/blackjack values-el: Διπλασιασμός -> Διπλό. This is the help section that names the button.
- Help bodies keep the verb "διπλασίασε" and the noun "στρατηγική" where they describe the concept, not the button or tab.

## bg (Bulgarian)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui/.../values-bg/strings.xml | Настройки -> **Опции** | 509 -> 304 | "Options", common in Bulgarian game menus. | (no shorter synonym for Настройки) |
| action_hint | core/ui/.../values-bg/strings.xml | Подсказка -> **Съвет** | 509 -> 290 | "Tip/advice". The Hold'em texts already say "съвета" for the hint. | Помощ (347; clashes with Help) |
| action_replay | core/ui/.../values-bg/strings.xml | Повторение -> **Отново** | 572 -> 348 | "Again". | Повтори (406), Наново (352) |
| language_system | core/ui/.../values-bg/strings.xml | Системен по подразбиране -> **Системен език** | ~1250 -> 709 | "System language". It follows theme_system, which is already Системна. | По подразбиране (drops "system") |
| help_tab_hands | games/holdem/.../values-bg/strings.xml | Комбинации -> **Ръце** | 576 -> 243 | "Hands". The stats already use Ръце (stat_section_hands). | (none) |
| help_tab_strategy | games/holdem, games/blackjack, games/klondike (values-bg) | Стратегия -> **Тактика** | 483 -> 384 | "Tactics". Changed in all three games. | Съвети (would clash with the new Hint name) |

Other strings updated to keep the terms consistent:
- **Settings -> Опции:** `settings_title` (core/ui), `help_rules_help` in klondike ("в Настройки" -> "в Опции"), and `help_suits` in spider (same change).
- **Hint -> Съвет:**
  - core/ui: `setting_hint_shows_winning_move` (Интелигентен съвет), `setting_hint_timeout`, `stat_hints_used`, `stat_tile_hints`, `stat_tile_hint_free_wins` and `stat_section_hints` (Съвети).
  - blackjack: `help_strategy_intro` (Бутонът Съвет), `review_title`, `review_followed_won`, `review_followed_lost`, `review_followed_push`, `review_ignored_won`, `review_ignored_lost` and `review_ignored_push`.
  - holdem: `help_hint_note`, `review_title`, `review_followed_won`, `review_followed_lost`, `review_followed_won_slim`, `review_followed_fold` (now "Даде фолд по съвета и запази..."), `review_ignored_won`, `review_ignored_lost`, `review_ignored_fold` and `review_beaten_by`.
  - freecell and spider: `help_controls_title` and `help_controls`. klondike: `help_rules_help` ("а Съвет винаги").
  - Grammar was adjusted for the change from feminine to masculine (подсказката -> съветът or съвета).
- The Replay dialog title `pending_replay_action` ("Повторение на тази игра") was left as it is. It is a descriptive title, and the button inside the dialog now reads Отново.

## cs (Czech)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui/.../values-cs/strings.xml | Nastavení -> **Volby** | 448 -> 253 | "Options". | Možnosti (418; over the 160 px limit) |
| action_hint | core/ui/.../values-cs/strings.xml | Nápověda -> **Rada** | 449 -> 223 | "Advice/tip". The Hold'em texts already say "radou" and "radě". It also separates Hint from Help, which spider's action_help also calls Nápověda. | Tip (141) |
| help_tab_hands | games/holdem/.../values-cs/strings.xml | Kombinace -> **Pořadí** | 502 -> 291 | "Ranking". The cs listing calls this tab's content "pořadí kombinací". | Hodnoty (379), Ruce (sounds like body hands) |

Other strings updated to keep the terms consistent:
- **Settings -> Volby:** `settings_title`, `help_rules_help` in klondike ("v Nastavení" -> "ve Volbách"), and `help_suits` in spider (same change). "výchozím nastavením" in spider means "default setting" in the general sense and was kept.
- **Hint -> Rada:**
  - core/ui: `setting_hint_shows_winning_move` (Chytrá rada), `setting_hint_timeout` (Časový limit rady), `stat_hints_used`, `stat_tile_hints` and `stat_section_hints` (Rady), and `stat_tile_hint_free_wins` (Výhry bez rady).
  - blackjack: `help_strategy_intro` (Tlačítko Rada), `review_title`, `review_followed_won`, `review_followed_lost`, `review_followed_push`, `review_ignored_won`, `review_ignored_lost` and `review_ignored_push`.
  - holdem: `help_hint_note` (O radě:), `review_title`, `review_followed_won`, `review_followed_lost`, `review_followed_won_slim`, `review_followed_fold` ("Zahodil jsi podle rady a ušetřil..."), `review_ignored_won`, `review_ignored_lost`, `review_ignored_fold` and `review_beaten_by`.
  - freecell and spider: `help_controls_title` and `help_controls`. klondike: `help_rules_help`.
  - Rada is feminine, like nápověda, so the agreement in the existing sentences is unchanged.
- spider `action_help` (Help) stays Nápověda.

## hu (Hungarian)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_replay | core/ui/.../values-hu/strings.xml | Újrajátszás -> **Újra** | 497 -> 178 | "Again". | (none needed) |
| action_undo | core/ui/.../values-hu/strings.xml | Visszavonás -> **Vissza** | 565 -> 297 | "Back", a usual undo label in Hungarian games. | Visszalép (433; fits but with little margin) |
| action_settings | core/ui/.../values-hu/strings.xml | Beállítások -> **Opciók** | 496 -> 311 | "Options". | Beállítás (singular, still too wide for 160 px) |
| theme_system | core/ui/.../values-hu/strings.xml | Rendszer alapértelmezése -> **Rendszer szerint** | 1185 -> 749 | "Following the system". | Rendszerbeállítás |
| language_system | core/ui/.../values-hu/strings.xml | Rendszer alapértelmezés -> **Rendszer szerint** | ~1140 -> 749 | Same wording as the theme dropdown. The "Nyelv" label gives the context. | Rendszer nyelve (732) |

Other strings updated to keep the terms consistent:
- `settings_title` -> Opciók.
- klondike `help_rules_help`: "ezt a Beállításokban" -> "ezt az Opciókban" (the article changes from a to az), and "A visszavonás korlátlan" -> "A visszalépés korlátlan".
- klondike `help_strategy_5`: "A visszavonás" -> "A visszalépés".
- spider `help_suits`: "a Beállításokban" -> "az Opciókban".
- Kept as they are: blackjack `help_round` and holdem `help_leave` ("nincs visszavonás", meaning "there is no undo"). These games have no Undo button, so the sentence describes a concept, not a button. Also kept: `pending_replay_action` ("...újrajátszása"), a descriptive dialog title whose button now reads Újra.

## ro (Romanian)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| period_week | core/ui/.../values-ro/strings.xml | Săptămână -> **7 zile** | 505 -> 237 | The filter is a trailing 7-day window (`WEEK_MILLIS = 7 days` in core/session), so "7 days" is exact. | Săpt. (an abbreviation) |
| help_tab_hands | games/holdem/.../values-ro/strings.xml | Combinații -> **Mâini** | 483 -> 246 | "Hands". The app already uses mână (Mâna următoare, stats Mâini). | (none) |

No other Romanian strings use these terms. Optional follow-up: `period_month` "Lună" could become "30 de zile" to match, since the window is 30 days. It was left unchanged to keep this edit minimal.

## Items left unchanged

| Locale | Key | Current | Reason |
|---|---|---|---|
| el | action_settings | Ρυθμίσεις | No common Greek word is short enough for the Hold'em 160 px button. Επιλογές (422) is still over the ~400 limit. Needs a layout fix in the Hold'em portrait bar (icon only, a smaller minimum text scale, or a wider button). It breaks only at 1.15 scale. |

## Possible overflows outside the audit (not edited)

These strings were not in the audit and were not edited. They measure as likely to overflow:
- **el** `action_split` "Διαχωρισμός" (613) and `help_split_title`. Wider than the old Double label, so it will likely break in the blackjack bar. Candidate: Διαίρεση (418).
- **el** `theme_system` "Προεπιλογή συστήματος" (1131). This is the same text that was ellipsized as language_system at 1.15. Candidate: "Του συστήματος" (749), or another short form.
- **bg** `action_insure` "Застраховка" (596) and `action_split` "Разделяне" (508). Both are over the ~496 limit of the blackjack bar.

## Store listing terms that now differ from the app (listings not edited)

- **el (el-GR.json):** Blackjack body "Διπλασιασμός" (app: Διπλό). Klondike, Spider and FreeCell "Επανάληψη" (app: Ξανά). Hold'em feature line "βοήθεια στρατηγικής" (tab: Τακτική).
- **bg (bg.json):** "Повторение" in the Klondike, Spider and FreeCell features (app: Отново). "подсказки"/"Подсказките" throughout every game (app: Съвет/Съвети). "Настройки за тема, звук..." and "Настройки за звук и анимации" (app: Опции). "помощ за стратегия" (tab: Тактика).
- **cs (cs-CZ.json):** "nápověda/nápovědy" throughout (app: Rada/Rady). "Nastavení motivu, zvuku, animací a jazyka" (app: Volby). The Hold'em feature line "pořadí kombinací" is consistent with the new tab name Pořadí.
- **hu (hu-HU.json):** "Visszavonás, Újrajátszás" (and the -st and -sal forms) in the Klondike, Spider and FreeCell texts (app: Vissza, Újra). "...és nyelvi beállítások" (app: Opciók).
- **ro, tr:** no conflicts.

## Needs a native speaker

1. el: **Διπλό** for Double. Check that it reads naturally on a blackjack button and is not taken as the football-betting "away win".
2. el: **Ξανά** for Replay, and **Τακτική** for the Strategy tab.
3. el: **Γλώσσα συστήματος**. It fits, with only an estimated ~4% margin.
4. bg: **Съвет** for Hint, including about 25 rewritten sentences (gender agreement: съветът, съвета).
5. bg: **Опции** for Settings, including the screen title. Also **Отново**, **Ръце** and **Тактика**.
6. cs: **Volby** for Settings, including the screen title. Many Czech apps use Nastavení, so check whether Volby feels dated.
7. cs: **Rada** for Hint across the stats and review sentences. Check that "O radě:" is not read as "About the council".
8. cs: **Pořadí** for the Hold'em "Hands" tab.
9. hu: **Vissza** for Undo (it could be confused with back navigation), **Opciók** for Settings, and **Rendszer szerint** used for both System default values.
10. ro: **7 zile** for the Week tab, which now sits next to "Lună" and "Tot timpul".

## Verification

- 23 files edited, 92 strings changed (95 substring edits).
- Every edited file parses as XML. The set of keys and the line count are unchanged in each file.
- The placeholder multiset (%d, %1$s, %2$d, %%, and so on) is identical before and after for every changed string.
- No unescaped apostrophes. Files are UTF-8 with no BOM change and original LF line endings.
- Only values-el, values-bg, values-cs, values-hu and values-ro were touched. tr had no findings. Backups are in the session scratchpad (`fit3/bak`).

## Summary

21 of 22 findings fixed with shorter common terms in el, bg, cs, hu and ro. Greek Settings in the Hold'em bar is left unchanged and needs a layout fix.
Hint was renamed in bg (Съвет) and cs (Rada), and Settings in bg, cs and hu (Опции, Volby, Opciók). Help, stats, review and screen-title strings were updated to match, 92 strings in 23 files in total.
All of this is unreviewed machine translation. The store listings for el, bg, cs and hu still use the old terms and the native-speaker list above needs checking before release.
