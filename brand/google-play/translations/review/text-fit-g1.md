# Text-fit review, group 1: es, fr, de, it, pt-BR, nl

> **Status: unreviewed machine translation.** These are shorter wordings chosen by an AI reviewer to fix layout findings. A native speaker has not checked them. Do not treat any of these locales as release-ready until one has (see `docs/PLATFORM.md`, "Translation quality").

Input: `fit_groups/g1.json` (15 findings from the text-fit audit at font scales 1.0 and 1.15).
Abbreviations: **CORE** = `core/ui/src/main/res/values-LOC/strings.xml`, **BJ** = `games/blackjack/app/.../values-LOC/strings.xml`, **HE / HT / HP / HI** = Hold'em `strings.xml` / `strings_table.xml` / `strings_panels.xml` / `strings_integration.xml`, **KL-H** = `games/klondike/app/.../values-LOC/strings_help.xml`, **KL** = Klondike `strings.xml`, **SP** = `games/spider/app/.../values-LOC/strings.xml`, **FC** = `games/freecell/app/.../values-LOC/strings.xml`.

Every key in the work list is defined only once per locale (in CORE or in one game), so no per-game duplicates had to be kept in sync. `core/session` has no matching strings.

## How each new word was checked

I didn't run the app (no gradle or adb). Instead I measured each candidate with the real Roboto Medium metrics (Android SDK `Roboto-Medium.ttf`) and modelled the bar label from `BoardActionBar.shrunkToFitWords`. That is M3 `labelMedium`: 12 sp, letter spacing 0.5 sp, 3x density. The model matches the audit's reported widths: it predicts a 146.6 px line for "Relance|r" (audit: 146), 140.8 px for "Retirars|e" (audit: 140) and about 171 px for "Ongedaa|n" (audit: 172).

**Finding for the code owner (not a translation issue):** `shrunkToFitWords` measures the word *with* letter spacing, but then scales only `fontSize` and `lineHeight`. The 0.5 sp letter spacing stays full size, so a word that needs only a small shrink still overflows by a few pixels and breaks. That is why "Ongedaan" (shrink 0.956), "Relancer" (0.904) and "Retirarse" (0.870) break, although they are well above the 0.78 floor. In practice, a label is safe only when it fits *without any shrink*. Every new label below meets that bar at 1.15, except "Relance à", which needs a 0.994 shrink (see fr). Scaling `letterSpacing` by the same factor would remove this whole class of findings. The same model also predicts small overflows for de "Rückgängig" / "Wiederholen" / "Statistiken" at 1.15. The audit did not flag those, so the model may be slightly pessimistic.

Widths below are at font scale 1.15 (rendered px / box px). The worst of Medium and Bold is shown, because a suggested action is drawn bold.

---

## es (Spanish)

| Key | File | Old -> New | Fit @1.15 | Why | Alternatives considered |
|---|---|---|---|---|---|
| language_system | CORE | "Predeterminado del sistema" -> "Según el sistema" | about 61% of the old width; narrower than fr "Par défaut du système", which fits | 26 characters were ellipsized in the one-line dropdown at both scales. "Según el sistema" ("as the system") is a common short label for "follow system". | "Idioma del sistema" (works for language only, not theme); "Del sistema" (terse); "Predeterminado" (drops "system") |
| action_fold | HT | "Retirarse" -> "No ir" | 56 / 160 | This is the PokerStars-ES fold label. It fits the set the app already uses: Pasar / Igualar / Apostar / Subir a. | "Tirar" (colloquial); "Retirar" (not idiomatic without "se"); "Pasar" (rejected: already means Check here) |
| stat_tile_vpip | HP | "Entró voluntariamente antes del flop" -> "Entró por elección antes del flop" | 2 lines, widest 367 / 426 | The old text wrapped to 3 lines ("voluntariamente" alone on line 2) and was clipped. "por elección" (by choice) keeps the meaning "voluntarily". It stays above the 28-character cap, but at 1.15 it lays out in 2 lines with about 14% spare; for comparison, fr's 34-character text fits. | "Voluntario antes del flop" (no verb); "Entró libremente antes del flop" (fits only just); "preflop" wording (term not used elsewhere in es) |

**Updated for consistency (es)**
- CORE `theme_system`: "Predeterminado del sistema" -> "Según el sistema". It is the same English source ("System default"), so the Language and Theme pickers now say the same thing.
- HE `help_actions_fold`: "Retirarse (Fold): …" -> "No ir (Fold): …". This help entry names the button.
- HT `seat_action_fold`: "Se retira" -> "No va". The seat label is also what the hint and review show as the recommended action (`adviceText`, `review_ignored_*` "la pista sugería: %1$s"). It now matches the button.
- HT `ann_fold` (TalkBack): "%1$s se retira" -> "%1$s no va".
- Left on purpose: the prose verbs "retirarse", "te retiraste" and "se retiran" in hint reasons, reviews and rules, and `a11y_folded` "Retirado". They describe folding in plain Spanish and don't name the button.

## fr (French)

| Key | File | Old -> New | Fit @1.15 | Why | Alternatives considered |
|---|---|---|---|---|---|
| (template) action_raise, shown as "Relancer à 60" | HT | "Relancer à&#10;%1$,d" -> "Relance à&#10;%1$,d" | 159.1 / 160 (shrink 0.994): fits, but with about 1 px to spare | aapt turns the `&#10;` into a space, so the label wraps on words and "Relancer" was the word that broke. The noun "Relance" is what the app already shows on seats (`seat_action_raise` "Relance %1$,d"), so the button and the seat label now match. `%1$,d` is unchanged. | "Monter à" (143.8 px, safe, but poker slang and used nowhere else in fr); "Relancer" without "à" (the long word stays long, and "to" is lost) |

**Updated for consistency (fr)**
- HE `help_actions_bet`: "Miser ou Relancer : …" -> "Miser ou Relance : …". This help entry names the buttons.
- Left on purpose: `sizing_raise_desc` "Relancer à %1$,d" (spoken description of the amount control, not a visible label) and the prose verb "relancer/relancez" in hints.

## de (German)

| Key | File | Old -> New | Fit @1.15 | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | CORE | "Einstellungen" -> "Optionen" | 183 / 195 | Common menu name in German games. The old word broke at both scales. | "Setup" (anglicism); "Menü" (different meaning) |
| action_double | BJ | "Verdoppeln" -> "Doppeln" | 166 / 194 | Broke at 1.15 (shrink 0.84). "Doppeln" is used in German blackjack strategy tables. | "Double" (English casino term; the de BJ bar is otherwise German) |

**Updated for consistency (de)**
- CORE `settings_title` (heading of the screen the button opens): "Einstellungen" -> "Optionen".
- KL-H `help_rules_help`: "…in den Einstellungen abschaltest" -> "…in den Optionen abschaltest".
- SP `help_suits`: "ändere sie in den Einstellungen" -> "ändere sie in den Optionen".
- BJ `help_double_title`: "Verdoppeln" -> "Doppeln". It is the help heading for the button.
- Left on purpose: "Standardeinstellung" (means "default", not the screen), and the prose verbs "verdoppelst", "Verdopple" and "verdoppeln" in BJ help and HE strategy.

## it (Italian)

| Key | File | Old -> New | Fit @1.15 | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | CORE | "Impostazioni" -> "Opzioni" | 154 / 195 | Common short menu name in Italian apps and games. The old word broke at both scales. | "Menu" (different meaning); "Imposta" (a verb, odd as a screen name) |
| action_hint | CORE | "Suggerimento" -> "Consiglio" | 191 / 194 | Broke at both scales. "Consiglio" (advice/tip) fits both a solitaire move hint and Hold'em advice. HI already says "Vincere contro il consiglio", and the highlighted-button state is "Consigliato". | "Indizio" (137 px, but it means a puzzle clue); "Aiuto" (means Help); "Aiutino" (too colloquial) |
| stat_tile_vpip | HP | "Entrato volontariamente prima del flop" -> "Entrato per scelta prima del flop" | 2 lines, widest 368 / 426 | Was clipped (3 lines). "per scelta" = by choice, i.e. voluntarily. | "Giocato per scelta prima del flop"; "Entrata volontaria pre-flop" (new term) |

**Updated for consistency (it).** The Hint feature is now called "Consiglio" everywhere:
- CORE `settings_title`: "Impostazioni" -> "Opzioni". KL-H `help_rules_help` and SP `help_suits`: "nelle Impostazioni" -> "nelle Opzioni".
- CORE `setting_hint_shows_winning_move` "Suggerimento intelligente" -> "Consiglio intelligente"; `setting_hint_timeout` "Timeout suggerimento" -> "Timeout consiglio"; `stat_hints_used` "Suggerimenti: %1$d" -> "Consigli: %1$d"; `stat_tile_hints` and `stat_section_hints` "Suggerimenti" -> "Consigli"; `stat_tile_hint_free_wins` "Vittorie senza suggerimenti" -> "Vittorie senza consigli".
- KL-H `help_rules_help`: "e Suggerimento ti mostrerà" -> "e Consiglio ti mostrerà". BJ `help_strategy_intro`: "Il pulsante Suggerimento" -> "Il pulsante Consiglio".
- FC and SP `help_controls_title`: "Tocco, trascinamento e suggerimento" -> "… e consiglio". FC and SP `help_controls`: "Il suggerimento dimostra/illumina" -> "Il consiglio …". HE `help_hint_note`: "Sul suggerimento:" -> "Sul consiglio:".
- BJ `review_title`, `review_followed_won/lost/push`, `review_ignored_won/lost/push`, and HI `review_title`, `review_followed_won/lost/fold`, `review_followed_won_slim`, `review_ignored_won/lost/fold`, `review_beaten_by`: "suggerimento" -> "consiglio". Where the old text said "il suggerimento consigliava", it is now "il consiglio indicava", to avoid "il consiglio consigliava".
- Left on purpose: BJ `stat_tile_resets` "Reimpostazioni" and SP "l'impostazione predefinita". These are different words and don't refer to the Settings screen.

## pt-BR (Brazilian Portuguese)

| Key | File | Old -> New | Fit @1.15 | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | CORE | "Configurações" -> "Ajustes" | 153 / 195 | The old word broke at both scales (shrink clamped at 0.78). "Ajustes" is the familiar pt-BR name for Settings (iOS uses it) and matches es. | "Opções"; "Config." (abbreviation with a dot) |
| stat_tile_vpip | HP | "Entrou voluntariamente antes do flop" -> "Entrou por escolha antes do flop" | 2 lines, widest 379 / 426 | Was clipped (3 lines). "por escolha" = by choice. | "Jogou por escolha antes do flop"; "Entrada voluntária pré-flop" (new term) |

**Updated for consistency (pt-BR)**
- CORE `settings_title`: "Configurações" -> "Ajustes".
- KL-H `help_rules_help`: "desligue isso nas Configurações" -> "desligue isso nos Ajustes".
- SP `help_suits`: "altere-a em Configurações" -> "altere-a em Ajustes".

## nl (Dutch)

| Key | File | Old -> New | Fit @1.15 | Why | Alternatives considered |
|---|---|---|---|---|---|
| action_settings | CORE | "Instellingen" -> "Opties" | 131 / 195 | Common short menu name in Dutch apps. The old word broke at both scales. | "Menu" (different meaning) |
| action_double | BJ | "Verdubbelen" -> "Dubbelen" | 190 / 194 | Broke at both scales. "Dubbelen" is the everyday Dutch blackjack verb ("wanneer moet je dubbelen"). | "Doubleren" (bridge term, too wide); "Double" (English) |
| setting_rest_reminder | CORE | "Pauzeherinneringen" -> "Pauzeherinnering" | about 455 / 492 (bodyLarge model) | Broke at 1.15. The singular is a natural label for an on/off row, keeps the exact term, and matches de "Pausenerinnerung". | "Pauzemeldingen" (narrower, but "melding" means notification) |
| action_undo | CORE | "Ongedaan maken" -> "Zet terug" | 107 / 194 | "Ongedaan" broke at 1.15. "Zet terug" reads both as "take back (the move)" and "move back", close to the chess term "zet terugnemen". | "Terug" (clashes with Back navigation); "Herstel" (means restore/repair); "Terugzetten" (too wide) |

**Updated for consistency (nl)**
- CORE `settings_title`: "Instellingen" -> "Opties". KL-H `help_rules_help`: "uitzet in Instellingen" -> "uitzet in Opties". SP `help_suits`: "wijzig het in Instellingen" -> "wijzig het in Opties".
- BJ `help_double_title`: "Verdubbelen" -> "Dubbelen".
- KL-H `help_rules_help`: "Ongedaan maken is onbeperkt" -> "Zet terug kun je onbeperkt gebruiken". KL-H `help_strategy_5`: "Ongedaan maken kost je…" -> "Zet terug kost je…".
- KL and FC `stuck_body`: "Maak ongedaan of start een nieuw spel." -> "Zet terug of start een nieuw spel."
- Left on purpose: "standaardinstelling" (means "default"); BJ `help_round` and HE `help_leave` "ongedaan maken bestaat niet" (says that BJ and HE have no undo, and there is no button to name); prose "verdubbel/verdubbelen" in BJ strategy, hint reason and HE strategy (normal verb use).

---

## Items left unchanged

None. All 15 findings got a shorter wording.

## Needs a native speaker

- **es** "No ir" for Fold, with "No va" on seats and in TalkBack. This is the PokerStars convention, but check that it reads naturally to players outside that client. "Según el sistema" for both the Language and Theme defaults. "Entró por elección antes del flop".
- **fr** "Relance à 60" (noun) in place of "Relancer à 60", and "Miser ou Relance :" in help (mixes an infinitive and a noun). The fit is only about 1 px at 1.15, so if the label is reworded, "Monter à" is the safe fallback.
- **de** "Doppeln" in place of "Verdoppeln": is it acceptable to German blackjack players, or is "Double" preferred? "Optionen" as the name of the Settings screen.
- **it** "Consiglio" as the name of the Hint feature throughout (button, settings, statistics, reviews), and "il consiglio indicava". "Opzioni".
- **pt-BR** "Ajustes" in place of "Configurações", and the preposition change "nas Configurações" -> "nos Ajustes". "Entrou por escolha antes do flop".
- **nl** "Zet terug" for Undo (the least conventional choice in this group), including "Zet terug kun je onbeperkt gebruiken" and "Zet terug of start een nieuw spel". "Dubbelen", "Opties" and "Pauzeherinnering".

## Verification

- A script applied all edits in place, checking each one (`apply_g1.py`, with backups in the session scratchpad). In total, 65 strings changed in 28 files, all in the six locales above. No other locale was touched.
- Every edited file parses as XML and keeps the same key set, UTF-8 without BOM, and LF line endings. Per changed string, the placeholders (`%1$s`, `%1$d`, `%1$,d`, `%%`) and the escaped apostrophes (`\'`) are identical before and after.
- Not re-run on a device: the text-fit audit (`TextFitTest` at 1.0 and 1.15) should be repeated to confirm.

**Summary**
1. All 15 findings in es/fr/de/it/pt-BR/nl were fixed with shorter common words (Opzioni, Ajustes, Optionen, Opties, Consiglio, Doppeln, Dubbelen, Zet terug, No ir, Relance à, Según el sistema, Pauzeherinnering, "por elección/per scelta/por escolha"), plus 50 consistency edits in help, settings, statistics, reviews and seat labels.
2. Every new bar label fits at 1.15 without shrinking, except fr "Relance à" (about 1 px to spare). Root cause in code: `shrunkToFitWords` does not scale letter spacing.
3. This is unreviewed machine translation. The items above need a native speaker, especially nl "Zet terug", es "No ir", de "Doppeln" and it "Consiglio".
