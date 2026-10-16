# Text-fit review, groups 2, 4 and 5 (uk pl sv nb da fi / hr sk ar he hi id / vi th ja ko zh-CN zh-TW)

**Status: unreviewed machine translation.** An AI reviewer proposed and applied these wording changes to fix layout-audit findings. No native speaker has checked them. Treat every new term as a draft until a native speaker has reviewed it (see "Needs a native speaker" at the end).

Input: `fit_groups/g2.json` (14 findings), `g4.json` (7), `g5.json` (1). Sources checked: `brand/google-play/TRANSLATION_GUIDE.md`, every `values-LOC` string file of `core/ui` and the five games (`core/session` has no string resources), and the raw audit output (`fit_<game>_<scale>.tsv`).

Locales with no findings in these groups: ar, hi, vi, th, ko, zh-CN, zh-TW. None of their files were changed.

## How widths were checked

Word widths were measured with Roboto Medium from the Android SDK, the font of the button labels (`labelMedium`). The unit is pixels at a font size of 100 px, the same unit as `text-fit-g3.md`. Limits come from what the audit shows breaking and what it shows fitting:

| Box | Fits (measured) | Breaks (measured) | Limit used |
|---|---|---|---|
| Hold'em action bar, 160 px, 1.15 scale | Petunjuk 395, Vyrovnat 398, Dorovnať 409 | Odustani 402, Retirarse 405, Postavke 415, Підказка 429 | about 390 |
| Bottom bar, 194–195 px, 1.15 scale | Підказка 429, Nápověda 452, Asetukset 452 | Ongedaan 456, Скасувати 494, Ustawienia 493, Повторити 512 | about 440 |
| Help or stats tab, 232 px, 1.15 scale | Стратегія 451 | Sepanjang 472, Комбінації 502 | about 450 |
| Settings row label, 463 px, 1.15 scale | – | Taukomuistutukset 868 | about 800 |
| Dropdown value, 504 px | – | 20+ characters ellipsized | <= 12 characters |

The audit sometimes credits the wrong key. When two keys have the same text, it names the first one it finds. hr "Odustani" is listed as `action_cancel`, but the label that breaks is the Hold'em **Fold** button (`action_fold`, same text). Dialog Cancel buttons are not in the 160 px bar.

Every changed button now has one name across the app. When a button was renamed, its screen title (`settings_title`) and every help, hint, dialog or review string that names it were changed too.

## uk (Ukrainian)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui values-uk/strings.xml | Налаштування -> **Опції** | 699 -> 239 | "Options". A common Ukrainian app and game label. Breaks in every game at both scales, so a short word was needed. | Параметри (528; still breaks at 195 and 160), Налаштув. (abbreviation) |
| language_system | core/ui values-uk/strings.xml | Системна за умовчанням -> **Системна** | 22 -> 8 chars | Same wording `theme_system` already uses ("Системна" agrees with both мова and тема). | Мова системи (12 chars, fine too, but differs from the theme wording) |
| action_replay | core/ui values-uk/strings.xml | Повторити -> **Знову** | 512 -> 281 | "Again", the usual label for playing the same deal again. | Переграти (494; still breaks), Ще раз (two words, fits but takes two lines) |
| action_undo | core/ui values-uk/strings.xml | Скасувати -> **Назад** | 494 -> 293 | "Back". Common in Ukrainian card games ("хід назад"), and the undo-arrow icon makes it clear. | Відміна (363; a noun and an odd button label), Відмінити (473; breaks) |
| action_hint | core/ui values-uk/strings.xml | Підказка -> **Порада** | 429 -> 355 | "Tip/advice". Hold'em's help text already calls the hint's suggestion "порада". | Keep Підказка: it breaks only in the Hold'em bar at 1.15, but no shorter synonym exists. |
| help_tab_hands | games/holdem values-uk/strings.xml | Комбінації -> **Руки** | 502 -> 226 | "Hands", the English meaning. Hold'em's uk hint reasons already use "рука" for a poker hand ("Сильна рука", "Слабка рука"). Greek made the same choice in g3. | Комбо (slang), keep Комбінації (breaks) |

Other strings updated for consistency (uk):
- Settings: `settings_title` -> Опції; klondike `help_rules_help` and spider `help_suits` "в Налаштуваннях" -> "в Опціях".
- Replay: `pending_replay_action` "Повторити цю роздачу" -> "Зіграти цю роздачу знову".
- Undo: klondike `help_rules_help` "Скасовувати ходи можна без обмежень" -> "Повертати ходи назад можна без обмежень"; klondike `help_strategy_5` "Скасування не коштує…" -> "Хід назад не коштує…"; klondike and freecell `stuck_body` "Скасуйте хід" -> "Поверніть хід назад"; blackjack `help_round` "і скасування немає" -> "і повернути хід назад не можна"; holdem `help_leave` "Скасування ходу немає" -> "Повернути хід назад не можна". `action_cancel` (dialog Cancel) stays "Скасувати". So does blackjack `recovery_notice` ("скасовано", meaning cancelled).
- Hint renamed to "порада" everywhere: core `setting_hint_shows_winning_move` (Розумна порада), `setting_hint_timeout` (Тайм-аут поради), `stat_hints_used`, `stat_tile_hints`, `stat_section_hints` (Поради), `stat_tile_hint_free_wins` (Перемоги без порад); blackjack `help_strategy_intro` («Порада»), `review_title`, `review_followed_won/lost/push`, `review_ignored_won/lost/push`; holdem `help_hint_note`, `review_title`, `review_followed_won/lost/fold/won_slim`, `review_ignored_won/lost/fold`, `review_beaten_by`; freecell and spider `help_controls_title`, `help_controls`; klondike `help_rules_help`. "Підказка радила" became "Порада пропонувала", because "порада радила" would repeat the same root.

## pl (Polish)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_hint | core/ui values-pl/strings.xml | Podpowiedź -> **Rada** | 551 -> 227 | "Advice/tip". Breaks even at 1.0 scale in every game. Hold'em already says "wbrew radzie". | Wskazówka (534; breaks), Pomoc (taken by the Help button), Podpow. (abbreviation) |
| action_settings | core/ui values-pl/strings.xml | Ustawienia -> **Opcje** | 493 -> 257 | "Options", a standard label in Polish games. | none shorter with the same meaning |
| (Hold'em template) action_call | games/holdem values-pl/strings_table.xml | Wyrównaj&#10;%1$,d (**unchanged**) | 438 | Breaks only in the Hold'em bar at 1.15. The usual alternative "Sprawdź" (387) sits right at the limit and would replace the established "wyrównanie" in about 10 help and hint strings. | Sprawdź (387; borderline), Call (English) |

Other strings updated for consistency (pl):
- Settings: `settings_title` -> Opcje; klondike `help_rules_help` and spider `help_suits` "w Ustawieniach" -> "w Opcjach". Spider "ustawieniem domyślnym" ("the default setting") is a generic noun and was kept.
- Hint renamed to "rada" everywhere: core `setting_hint_shows_winning_move` (Inteligentna rada), `setting_hint_timeout` (Limit czasu rady), `stat_hints_used`, `stat_tile_hints`, `stat_section_hints` (Rady), `stat_tile_hint_free_wins` (Wygrane bez rad); blackjack `help_strategy_intro` (Przycisk Rada), `review_title` (na radę), `review_followed_*` (do rady), `review_ignored_*` (Rada sugerowała); holdem `help_hint_note` (O radzie; "ideę porady" -> "jej ideę"), all `review_*` hint strings, `review_followed_fold` (zgodnie z radą); freecell and spider `help_controls_title`, `help_controls`; klondike `help_rules_help`.

## sv (Swedish)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui values-sv/strings.xml | Inställningar -> **Alternativ** | 557 -> 433 | "Options", the standard Swedish label. Fixes the 195 px bar in all games and the Hold'em bar at 1.0. | Val (144; fits everywhere but is rarely used as a settings label), Inställn. (abbreviation) |

Remaining risk: Alternativ (433) is still over the ~390 limit of the Hold'em bar at 1.15 scale. If the next audit still breaks it there, a native speaker should choose between "Val" and a layout change.

Other strings updated (sv): `settings_title` -> Alternativ; klondike `help_rules_help` and spider `help_suits` "i Inställningar" -> "i Alternativ". "standardinställningen" (generic) kept.

## nb (Norwegian Bokmål)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui values-nb/strings.xml | Innstillinger -> **Valg** | 528 -> 201 | "Options", a common menu label in Norwegian games. | Oppsett (354; also natural, less margin), Alternativer (too wide) |

Other strings updated (nb): `settings_title` -> Valg; klondike `help_rules_help` and spider `help_suits` "i Innstillinger" -> "i Valg". "standardinnstillingen" (generic) kept.

## da (Danish)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui values-da/strings.xml | Indstillinger -> **Valg** | 529 -> 201 | "Options/choices", short and the same as the nb choice. | Opsætning (488; breaks), Valgmuligheder (too wide), Indstil. (abbreviation) |

Other strings updated (da): `settings_title` -> Valg; klondike `help_rules_help` and spider `help_suits` "i Indstillinger" -> "i Valg". "standardindstillingen" (generic) kept.

## fi (Finnish)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui values-fi/strings.xml | Asetukset -> **Valinnat** | 452 -> 369 | "Options", the usual Finnish label for a game options menu. | Säädöt (316; more margin, but suggests "adjustments"), Asetus (singular, odd for a menu) |
| setting_rest_reminder | core/ui values-fi/strings.xml | Taukomuistutukset -> **Taukomuistutus** | 868 -> 729 | Singular compound, 14 characters. A natural name for the setting. | Muistuta tauoista (17 characters, over the cap) |

Other strings updated (fi): `settings_title` -> Valinnat; klondike `help_rules_help` "pois Asetuksista" -> "pois Valinnoista"; spider `help_suits` "vaihda se asetuksista" -> "vaihda se Valinnoista". "oletusasetus" (generic default setting) kept.

## hr (Croatian)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui values-hr/strings.xml | Postavke -> **Opcije** | 415 -> 282 | "Options", common in Croatian games. | none shorter |
| action_cancel (really `action_fold`, games/holdem values-hr/strings_table.xml) | – | Odustani (**unchanged**) | 402 | Breaks only in the Hold'em bar at 1.15, and only just. "Odustati" is the established Croatian poker term for fold and appears in about 10 help, hint and table strings. | Odbaci (314; "discard", would rename the action everywhere), Baci karte, Fold (English) |

Other strings updated (hr): `settings_title` -> Opcije; klondike `help_rules_help` and spider `help_suits` "u Postavkama" -> "u Opcijama". "zadana su postavka" (generic) kept. `action_cancel` (dialogs) unchanged.

## sk (Slovak)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui values-sk/strings.xml | Nastavenia -> **Voľby** | 503 -> 260 | "Options", used in Slovak software. | Možnosti (418; still over the limit) |
| action_hint | core/ui values-sk/strings.xml | Nápoveda -> **Tip** | 452 -> 143 | "Tip", a common hint label in Slovak games. "Nápoveda" also reads as "Help". | Rada (227; "advice"; Hold'em already used "rade") |

Other strings updated (sk):
- Settings: `settings_title` -> Voľby; klondike `help_rules_help` and spider `help_suits` "v Nastaveniach" -> "vo Voľbách".
- Hint renamed to "tip" everywhere: core `setting_hint_shows_winning_move` (Inteligentný tip), `setting_hint_timeout` (Časový limit pre tip), `stat_hints_used`, `stat_tile_hints`, `stat_section_hints` (Tipy), `stat_tile_hint_free_wins` (Výhry bez tipov); blackjack `help_strategy_intro` (Tlačidlo Tip), `review_title`, `review_followed_*` (Riadili ste sa tipom), `review_ignored_*` (Tip navrhoval); holdem `help_hint_note` (O tipe; "za radou" -> "za tipom"), all `review_*` hint strings ("Výhra proti rade" -> "Výhra proti tipu", "Tip ju odhadol"); klondike `help_rules_help`.

## he (Hebrew, values-iw; no values-he folder exists)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| language_system | core/ui values-iw/strings.xml | ברירת מחדל של המערכת -> **לפי המערכת** | 20 -> 10 chars | "As in the system". Short and works for both language and theme. | שפת המערכת ("system language"; fits language only), ברירת המחדל (drops "system") |

Other strings updated (he): `theme_system` had the same 20-character text and shows in the same kind of dropdown. It was changed to the same "לפי המערכת".

## id (Indonesian, values-in)

| Key | File | Old -> New | Width old -> new | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | core/ui values-in/strings.xml | Pengaturan -> **Setelan** | 518 -> 337 | The word Android itself uses in Indonesian for Settings. | Atur (verb), Opsi ("options") |
| period_all_time | core/ui values-in/strings.xml | Sepanjang Waktu -> **Semua** | 472 -> 311 | "All". A natural third tab after Minggu and Bulan, and the same word as the existing `deal_filter_all`. | Semua Waktu (each word fits, but takes two lines in the tab), Seluruhnya |

Other strings updated (id): `settings_title` -> Setelan; klondike `help_rules_help` and spider `help_suits` "di Pengaturan" -> "di Setelan". "pengaturan default" (generic) and blackjack "Pengaturan ulang" ("reset") kept.

## ja (Japanese)

| Key | File | Old -> New | Why | Alternatives considered |
|---|---|---|---|---|
| (Hold'em template) action_all_in | games/holdem values-ja/strings_table.xml | オールイン&#10;%1$,d (**unchanged**) | The amount already has its own line. The problem is that at 1.15 only four kana fit on a line (152 px), so "オールイン" wraps to "オールイ/ン", pushes the amount to a third line, and gets clipped. "オールイン" is the only standard Japanese term; the help text and announcements use it. | 全額 ("full amount"; not a poker term), ALL IN (Latin, unlike the other katakana buttons), half-width ｵｰﾙｲﾝ (dated). |

Recommendation (code, not strings): let Japanese labels wrap only between words, or shrink CJK action labels the way Latin labels already shrink (`shrunkToFitWords` skips Han and kana). The same issue will clip `action_call_all_in` (コール / オールイン) at 1.15. The audit did not reach that state.

## Items left unchanged

| Locale | Key | Text | Reason |
|---|---|---|---|
| pl | action_call (Hold'em template "Wyrównaj&#10;%1$,d") | Wyrównaj | No clearly shorter term. Sprawdź is borderline (387 vs ~390) and would rename the action in about 10 strings. Breaks only at 1.15. |
| hr | action_fold (audit listed it as action_cancel) | Odustani | The established poker term. Over the limit by about 3%, at 1.15 only. |
| ja | action_all_in template | オールイン&#10;%1$,d | No shorter standard term. Needs a layout fix for CJK labels. |

## Verification

A script checked all 40 edited files against backups of the originals: 133 strings changed. Every file parses as XML, and each file keeps its key set, line count, line endings and BOM state. Every changed string keeps the same placeholder multiset (`%1$d`, `%1$s`, `%2$d`, `%%` and so on) and the same number of `%` characters, and no unescaped apostrophe was added. Result: 0 problems. Only these folders were touched: values-uk, values-pl, values-sv, values-nb, values-da, values-fi, values-hr, values-sk, values-iw, values-in. Gradle, git and adb were not run, so re-run the text-fit audit to confirm the fixes.

## Needs a native speaker

- **uk**: Опції (vs Параметри), Назад for Undo (could be read as navigation Back), Знову for Replay, Руки for the Hands tab, and the full hint rename Підказка -> Порада (about 30 strings, including "Порада пропонувала").
- **pl**: Rada for Hint across about 30 strings (less usual than Podpowiedź in solitaire), Opcje; decide whether Hold'em Call should become Sprawdź.
- **sv**: Alternativ may still break in the Hold'em bar at 1.15. Decide on Val or a layout change.
- **nb / da**: Valg as the Settings label and screen title. Danish especially: confirm it reads as "Options", not "choice/election".
- **fi**: Valinnat vs Säädöt; Taukomuistutus (singular) as the setting name.
- **hr**: Opcije; whether Fold should become Odbaci.
- **sk**: Voľby (also means "elections") and the full hint rename Nápoveda -> Tip.
- **he**: לפי המערכת for both the language and theme dropdowns.
- **id**: Setelan, and Semua as the "All Time" statistics tab.
- **ja**: confirm オールイン stays, and approve a layout fix instead of a wording change.

## Summary

Applied 19 of 22 audit fixes (plus 114 consistency edits, 133 strings in 40 files) across uk, pl, sv, nb, da, fi, hr, sk, he and id; ar, hi, vi, th, ko and zh had no findings.
Left 3 unchanged for lack of a good shorter term: pl Hold'em Call "Wyrównaj", hr Fold "Odustani" and ja "オールイン" (needs a CJK layout fix). sv "Alternativ" may still break at 1.15 in Hold'em.
All edits parse, keep placeholders, and are unreviewed machine translation that needs native review and a fresh text-fit audit run.
