# Translations: open work

Covers Blackjack and Texas Hold'em, 30 languages. Detail for every item is in the per-group
reports in `review/` (`strings-g1..g5.md` for in-app strings, `listings-g1..g5.md` for store
listings). Groups: g1 es fr de it pt-BR nl · g2 uk pl sv nb da fi · g3 tr el cs ro hu bg ·
g4 hr sk ar iw hi in · g5 vi th ja ko zh-CN zh-TW.

How this was made: Sonnet drafted, Opus reviewed and corrected. No native speaker has seen any of it.

## Blocking: before any translated listing is used

- [ ] **Re-translate the store listings from the final English.** The English listing is being
      changed in `games/blackjack/app/src/main/play/listings/en-US/` (new title "Blackjack 21:
      FinitePlay", new short and full description) and Hold'em's is still uncommitted. The 60 drafts
      here were written from `source/*.en-US.md`, which no longer matches. Update `source/`, then
      re-run the Sonnet draft and Opus review.
- [ ] Re-run `scratchpad/listing_limits.py` equivalent after any listing change: title <= 30,
      short <= 80, full <= 4000 characters. Last run: 60 of 60 within limits.
- [ ] Decide the **short-description compromise**. About 20 of 60 short lines cannot say "no
      multiplayer / online players" in 80 characters and keep offline, virtual chips, no ads, no
      purchases, no real money instead (full text keeps every claim). Lists are in each
      `listings-gN.md` under "SHORT compromises". Known: no-NO and fi-FI Blackjack, el cs ro hu bg
      Blackjack, hr sk Blackjack, es/fr/it/nl/pt Blackjack and Hold'em, ar uses "online play".
- [ ] Get approval for the final copy before anything is saved in Play Console
      (`brand/google-play/AGENTS.md`).

## Native-speaker review, in-app strings (highest first)

Rated 3 to 3.5 by the reviewer, so start here:
- [ ] **th**: rated 3/5; 26 strings changed. Confirm Hint = "คำใบ้" everywhere, the full-house
      name, the hindsight wording, and the long Double button "เพิ่มเดิมพันเท่าตัว".
- [ ] **hi** and **in**: rated 3.5/5. Confirm the Hindi payouts now read 3:2 and 2:1 (they read
      "2 for 3" before), "दहला"/"अट्ठे" for ten and eight, "संकेत" as the Hint name, Indonesian
      "Anda" and "Petunjuk".
- [ ] **pl**: bust label `Fura` or `Ponad 21`; Blackjack Stand is `Pas` but that means Fold in
      Hold'em (switching to `Stój` touches about 8 strings); text addresses the player only in the
      masculine (`wygrałeś`).

The rest (rated 4 or better), specific doubts:
- [ ] **cs**: the files mix "banka" and "bank" (about 20 strings if unified).
- [ ] **sv**: Blackjack dealer "Givare" or "dealern"; bust "Över" or "Tjock"; "Tips-funktionen".
- [ ] **fi**: hand names use partitive plurals ("Pari: ässiä"); decide together with the rank list.
- [ ] **tr**: Fold button is now "Fold" (was "Pas"); confirm. **hu**: Split is now "Felosztás".
- [ ] **tr** and **hu**: the split help says "same value" (değer, érték), the rule is "same rank".
      Needs the right word, otherwise a 10 and a King look splittable.
- [ ] **el**, **bg**: dealer "Κράτης", bust "Υπέρβαση", hand-name wording; Bulgarian "ас" or "асо".
- [ ] **ko**, **ja**: confirm the reworded review sentences (particles after an inserted label),
      Korean two-pair name, Japanese leave-tournament heading.
- [ ] **vi**: "bài tẩy", full-house name "bảy ba", "Mất N ván" on the winning screen.
- [ ] **zh-CN**, **zh-TW**: 局面 for "this spot", split wording ("a pair").
- [ ] **ar**, **iw**: right-to-left reading with numbers and percent signs; Arabic plural forms
      `one` and `two` omit the number on purpose; Hebrew chips: app says "אסימונים", listing says
      "צ'יפים".
- [ ] **fr**: use no-break spaces before `:` `?` `!` `;` (recommended, touches almost every
      string); "gains" for prizes; "checker".
- [ ] **es**, **it**: the Spanish Hold'em review drops a seat label into a sentence ("la pista
      sugería: Se retira"); needs a separate string or code change.
- [ ] **de**, **nl**: Blackjack dealer is "Geber" / "Deler" but "Dealer" in Hold'em.

## Shared card names (`core/ui`, not covered by the reviews)

- [ ] ja, ko show the ten as a character (十, 십) while other ranks are digits, so hints mix them.
- [ ] vi calls the King "Già" there but Hold'em uses "K".
- [ ] es "Reina", it "Regina" there, but Hold'em says "dama", "donna".

## Fit on the device (not yet checked)

- [ ] Run both apps in the longest languages (de, fr, fi, hu, el, pt, th) at 1.15x and 1.3x system
      text. Look at the hint card, the review card, buttons and statistics tiles.
- [ ] Known long strings: fr "Réinitialiser les jetons", pt "Aumentar para", hu "Zsetonok
      visszaállítása", the statistics tile "Voluntarily in before the flop" (Romance languages), ja and th leave-tournament.
- [ ] Hint and review cards: longest case is a review that ignores the Hint, in a long language.

## Process

- [ ] Remaining games (Klondike, Spider, FreeCell) were not reviewed by Opus. Same pass if wanted.
- [ ] Add a unit test that the strings naming the Hint use the locale's own button word (found
      wrong in de, sv, th, sk, in).
- [ ] Add a length-ratio check (translation more than about 1.6x the English) as a warning.
- [ ] After native review, record in each report which items were confirmed or changed.
- [ ] Commit the 100+ changed `values-*` files and this folder when you are ready (uncommitted).
