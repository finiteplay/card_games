# String review, group 5: vi, th, ja, ko, zh-rCN, zh-rTW

Scope: Blackjack `values-LOC/strings.xml`; Hold'em `values-LOC/{strings, strings_table, strings_panels, strings_integration}.xml`.
Abbreviations: BJ = blackjack/strings.xml, HS = holdem/strings.xml, HT = holdem/strings_table.xml, HP = holdem/strings_panels.xml, HI = holdem/strings_integration.xml.

Checked after editing with a script: every file parses as XML; key sets match English; each string's positional placeholders (number and type) match English; there are no unescaped `'`, no bare `&`, and no stray `%`. No `translatable="false"` strings, comments or key order were touched.

Cross-check: the shared Hint button label (`core/ui` `action_hint`) is Gợi ý / คำใบ้ / ヒント / 힌트 / 提示. Every locale's review text now uses that same word.

---

## vi (Vietnamese)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | help_split | `Khi hai lá đầu cùng giá trị, …` -> `Khi hai lá đầu là một đôi, …` | English says "same rank". "Cùng giá trị" (same value) would let J+K be split. |
| HS | help_hand_cards | `bài chủ (hole cards)` -> `bài tẩy (hole cards)` | Vietnamese players call hole cards "bài tẩy". "Bài chủ" is not a poker term. |
| HS | help_hand_flop | `Sau khi chia bài chủ …` -> `Sau khi chia bài tẩy …` | Same term fix, so it stays consistent. |
| HT | hand_full_house | `Cù lũ, %1$s ba và %2$s đôi` -> `Cù lũ, ba lá %1$s và đôi %2$s` | Ranks are spelled as words, so the old form gave "bảy ba và tám đôi", which reads like the number "73". |
| HT | end_body_won | `Mất %1$d ván.` -> `Sau %1$d ván.` | On a win screen "Mất" can be read as "lost N hands". |

### Needs a native speaker
- BJ help_split: confirm "là một đôi" reads naturally for blackjack (pair = same rank).
- HT places / leave_body / end_title_place: the capitalised "Nhất/Nhì/Ba…" appears mid-sentence ("Bạn về hạng Nhất"). Decide whether lower case reads better.
- HT action_check / ann_check: "Check" is left in English while "Theo", "Tố" and "Bỏ bài" are translated. Confirm that this mix is what Vietnamese players expect.
- BJ hint_reason_decline_insurance: does "con mười" clearly mean any ten-value card (10/J/Q/K)?
- Out of scope (core/ui): rank_king is "Già" in the shared card names, but Hold'em uses "K".

Fit: HP stat_tile_vpip "Tự nguyện vào trước flop" is as long as the English. Watch it on a small tile.

**Rating: 4/5.** Main weakness: a few poker terms are calqued instead of using player jargon (hole cards, hand-name phrasing).

---

## th (Thai)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | review_title, review_followed_won, review_followed_lost, review_followed_push, review_ignored_won, review_ignored_lost, review_ignored_push (7) | `คำแนะนำ` -> `คำใบ้` | The Hint button and BJ help_strategy_intro say คำใบ้. The review called it คำแนะนำ, so it no longer matched the button. |
| BJ | reset_offer_not_now | `ไม่ตอนนี้` -> `ไว้ทีหลัง` | A literal "not now" that is not idiomatic Thai. |
| BJ | help_split | `…มีค่าเท่ากัน…` -> `…เป็นคู่…` | "Same value" would allow J+K. English means same rank, i.e. a pair. |
| BJ | help_strategy_2 | `เพราะมีโอกาสเกิน` -> `เพราะเจ้ามือมีโอกาสเกินสูง` | Restores "the dealer is *likely* to bust". The old text had no subject and only said there was "a chance". |
| HS | help_hint_note | `เกี่ยวกับคำแนะนำ:` -> `เกี่ยวกับคำใบ้:` | Hint term consistency. The later "คำแนะนำ" (= advice) is kept. |
| HS | help_strategy_1 | `เล่นน้อยมือลงจากที่นั่งช่วงแรก` -> `เล่นให้น้อยมือลงเมื่ออยู่ที่นั่งช่วงแรก` | The old phrasing was ungrammatical. |
| HS | help_strategy_3 | `ส่วนใหญ่ไม่คุ้มที่จะเล่น` -> `มือส่วนใหญ่ไม่คุ้มที่จะเล่น` | The subject "hands" was missing. |
| HS | help_ties | `ดอกไม่เคยตัดสินเสมอ` -> `ดอกไม่ใช้ตัดสินมือที่เสมอกัน` | Ambiguous: เสมอ also means "always", so the old text could read "suits never always decide". |
| HT | hand_full_house | `ฟูลเฮาส์ %1$s เต็ม %2$s` -> `ฟูลเฮาส์ ตอง %1$s คู่ %2$s` | "เต็ม" is a meaningless literal of "full of". ตอง/คู่ is how Thai players name trips and a pair. |
| HI | review_title, review_followed_won, review_followed_lost, review_followed_fold, review_ignored_won (first word only), review_ignored_lost, review_ignored_fold, review_beaten_by, review_followed_won_slim (9) | `คำแนะนำ` -> `คำใบ้` | Hint term consistency. In review_ignored_won, "ฝืนคำแนะนำ" (against advice) is kept. |
| HI | review_hindsight_fold_won | `คุณจะชนะมือนี้…` -> `ถ้าไม่โฟลด์ คุณจะชนะมือนี้…` | Without the condition, "จะ" reads as "you will win". This makes it "if you had not folded". |
| HI | review_hindsight_fold_lost | `คุณจะแพ้มือนี้:…` -> `ถ้าไม่โฟลด์ คุณจะแพ้มือนี้:…` | Same counterfactual fix. |

### Needs a native speaker
- HT hand_full_house: confirm "ตอง X คู่ Y" against how local poker apps show it.
- HT style_tight/loose/aggressive/passive (แน่น/หลวม/ดุดัน/เฉื่อย): these are literal. Thai players may expect ไทต์/ลูส/แอ็กเกรสซีฟ/พาสซีฟ.
- HI hint_reason_call_good_price / hint_reason_fold_bad_price: "ราคาต้องการ %d%%" is a calque of "the price needs". Consider "ต้องมีโอกาสชนะอย่างน้อย %d%%".
- BJ help_goal uses "บัสต์" once, while everything else says "เกิน". Decide on one term.
- BJ/HI review_followed_lost and others: "ให้ตัดสินที่การตัดสินใจ" repeats ตัดสิน. Consider "ให้ประเมินที่การตัดสินใจ".
- Hint term: confirm คำใบ้ is the right name for a coaching hint button (core/ui). If it is renamed, update these review strings too.

Fit: BJ action_double "เพิ่มเดิมพันเท่าตัว" is much longer than "Double" on an action-bar button. Consider "ดับเบิล" or "เพิ่มสองเท่า". HT action_leave_tournament "ออกจากทัวร์นาเมนต์" is long.

**Rating: 3/5.** Main weakness: the Hint name was inconsistent with the button, and several lines are literal calques that read unnaturally.

---

## ja (Japanese)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| HS | help_leave_title | `中断` -> `途中でやめる` | 中断 means pause/suspend. The section is about leaving for good, and pausing (closing the app) is explicitly different. |
| HS | help_strategy_3 | `居心地がいい以上にフォールドしましょう。` -> `抵抗を感じるくらい多めにフォールドしましょう。` | The old line was a literal, unnatural rendering of "more than feels comfortable". |
| HS | help_strategy_7 | `…オールインが倍にする最大のチャンス…` -> `…オールインがチップを倍にする最大のチャンス…` | The object (chips) was missing. |
| HI | hint_reason_value_raise | `…レイズしてチップを増やしましょう。` -> `…レイズして、ポットにもっとチップを入れましょう。` | "Get more chips in" means into the pot. The old text said "increase your chips". |
| HI | hint_reason_fold_bad_price | `…あなたは約%1$d%%です。` -> `…あなたの勝率は約%1$d%%です。` | "You are about 30%" was incomplete. |
| HI | review_followed_lost | `約%1$d%%なら、%2$d%%は負けます。` -> `勝率約%1$d%%でも、%2$d%%の確率で負けます。` | The old line was awkward, and it was unclear what the first percentage measured. |

### Needs a native speaker
- BJ action_insure / help_insurance: 保険 vs the casino term インシュランス.
- HT action_raise: the button "レイズ\n%d" drops "to". The sizing description says "%dにレイズ". Is the button clear enough?
- HT hand_full_house "フルハウス、%1$sと%2$s" does not say which rank is the three. Consider "%1$sフルハウス（%2$sペア）".
- BJ help_values / help_insurance: "払い戻され" for a payout. 配当 / 支払われ may be more standard.
- BJ hint_reason_decline_insurance: "伏せカードが10である" should mean any ten-value card.
- Out of scope (core/ui): rank_ten is "十" (kanji) while the other ranks are digits or katakana, so dealer-card text can show "十".

Fit: HT action_leave_tournament "トーナメントをやめる" is long for a button.

**Rating: 4/5.** Main weakness: some Hold'em help and hint lines were literal and slightly unnatural. Terminology itself is good.

---

## ko (Korean)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | review_ignored_won | `…권한 행동은 「%1$s」였습니다.` -> `…권한 행동(%1$s)과 다르게 선택했습니다.` | Copula bug: 더블/스플릿/거절 + "였습니다" is wrong (it should be 이었습니다). The rewording puts the particle on 행동, so it is correct for any label. 「」 is not standard Korean punctuation. |
| BJ | review_ignored_lost | `힌트가 권한 행동은 「%1$s」였지만, 다른 선택을 해서 졌습니다.` -> `힌트가 권한 행동(%1$s)과 다르게 선택해서 졌습니다.` | Same particle fix. |
| BJ | review_ignored_push | `힌트가 권한 행동은 「%1$s」였지만, 다른 선택을 했습니다.` -> `힌트가 권한 행동(%1$s)과 다르게 선택했습니다.` | Same particle fix. |
| BJ | review_stand_chances | `…약 %2$d%% 이기고, %3$d%% 푸시, %4$d%% 졌습니다.` -> `…승리 약 %2$d%%, 푸시 %3$d%%, 패배 %4$d%%였습니다.` | The old version mixed verb tenses with a bare noun. Now all three are parallel nouns. |
| BJ | help_strategy_2 | `…가능성이 높습니다.` -> `…가능성이 높기 때문입니다.` | Restores the "because" link. |
| HS | help_strategy_3 | `편한 것보다 더 많이 폴드하세요.` -> `불편하게 느껴질 만큼 자주 폴드하세요.` | The old line was a literal, unnatural rendering. |
| HT | hand_two_pair | `투 페어, %1$s와 %2$s` -> `투 페어, %1$s·%2$s` | Particle bug: ranks are digits, so 3/6/7/8/10 (삼, 육, 칠, 팔, 십) need 과, not 와. The middle dot avoids the particle. |
| HI | review_followed_lost | `약 %1$d%%라면 %2$d%%는 집니다.` -> `승률이 약 %1$d%%여도 %2$d%%는 집니다.` | The old line was awkward, and the first percentage had no subject. |
| HI | review_ignored_won | `힌트는 %1$s을(를) 권했습니다.` -> `힌트가 권한 행동(%1$s)과 다르게 선택했습니다.` | Removes the 을(를) fallback and matches Blackjack. |
| HI | review_ignored_lost | `힌트는 %1$s을(를) 권했지만 …` -> `힌트가 권한 행동(%1$s)과 다르게 선택해서 졌습니다.` | Same. |
| HI | review_ignored_fold | `폴드했지만 힌트는 %1$s을(를) 권했습니다.` -> `힌트가 권한 행동(%1$s)과 달리 폴드했습니다.` | Same. |

### Needs a native speaker
- HT hand_full_house "%1$s 풀 오브 %2$s": a transliteration. Is it common, or is "%1$s 풀하우스" / "%1$s 트리플 + %2$s 페어" preferred?
- HT ann_shows / ann_wins still use 이(가). These are TalkBack-only, so I left them, but the other announcements drop the particle.
- BJ action_deal "카드 나누기" vs the common casino word "딜".
- review_ignored_*: confirm the new "힌트가 권한 행동(X)과 다르게 선택했습니다" reads naturally after a 히트/레이즈 120 label.
- Out of scope (core/ui): rank_ten is "십", so BJ hints can read "딜러 오픈 카드: 십."

Fit: OK. Labels are short.

**Rating: 4/5.** Main weakness: particle handling after inserted labels and ranks (fixed where found).

---

## zh-rCN (Simplified Chinese)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | help_split | `当头两张牌点数相同，…` -> `当头两张牌是对子时，…` | 点数相同 (same value) would allow J+K. English means same rank. |
| BJ | hint_reason_hit_dealer_strong | `…的爆牌风险，也要牌。` -> `…的爆牌风险，也应要牌。` | 也要牌 is ambiguous ("also want cards"). |
| HS | help_strategy_1 | `越靠近庄家按钮再越放宽` -> `越靠近庄家按钮就越放宽` | Ungrammatical 越…再越. |
| HI | hint_reason_playable_raise | `这个位置的好牌` -> `这种局面下的好牌` | 位置 is the poker term for *position*. English says "this spot". |
| HI | hint_reason_call_good_price | `而价格只需要 %2$d%%` -> `而跟注只需 %2$d%%` | "价格需要" is a calque of "the price needs". |
| HI | hint_reason_fold_bad_price | `价格需要 %2$d%%，` -> `跟注需要 %2$d%% 的胜率，` | Same, and it now says what the percentage measures. |

### Needs a native speaker
- HI hint_reason_semi_bluff / call_draw: "outs" is left in English. Many players use it, but 补牌 / 出路 is the alternative.
- HS help_strategy_3 "弃牌要比你觉得舒服的多一些": understandable but literal.
- HI hint_notice "胜率" for equity: common, but it overlaps with "win rate" used elsewhere.
- HT hand_straight / hand_flush "%1$s 高": confirm this is preferred over "最大 %1$s".
- HI review_followed_lost "约 X% 的胜率，有 Y% 的几率会输": telegraphic.

Fit: OK.

**Rating: 4/5.** Main weakness: a few English calques in the hint lines (price, spot).

---

## zh-rTW (Traditional Chinese)

### Changes
| File | Key | Old -> New | Reason |
|---|---|---|---|
| BJ | help_split | `當頭兩張牌點數相同，…` -> `當頭兩張牌是一對時，…` | Same rank vs same value (see zh-rCN). |
| BJ | hint_reason_hit_dealer_strong | `…的爆牌風險，也要牌。` -> `…的爆牌風險，也應要牌。` | Ambiguity fix. |
| HS | help_strategy_1 | `越靠近莊家按鈕再越放寬` -> `越靠近莊家按鈕就越放寬` | Grammar fix. |
| HI | hint_reason_playable_raise | `這個位置的好牌` -> `這種局面下的好牌` | 位置 = position, which is wrong for "spot". |
| HI | hint_reason_call_good_price | `而價格只需要 %2$d%%` -> `而跟注只需 %2$d%%` | Calque fix. |
| HI | hint_reason_fold_bad_price | `價格需要 %2$d%%，` -> `跟注需要 %2$d%% 的勝率，` | Calque fix. |

Simplified and Traditional differ in vocabulary, not only script, and that is preserved: 重設/重置, 平手/平局, 機率/几率·概率, 儲存/保存, 復原/撤销, 應用程式/应用, 操作列/操作栏, 資料/数据, 計畫/计划, 依提示/按提示. Shared poker terms (跟注, 加注, 過牌, 棄牌, 全下, 底池, 盲注, 翻牌/轉牌/河牌) are standard in Taiwan as well.

### Needs a native speaker
- 全下 vs "All in" (also common in Taiwan); 黑傑克 vs 21點.
- BJ help_round "點按": fine in Apple-style UI copy. Confirm against the rest of the app (點選 / 輕觸).
- The same open items as zh-rCN: outs, help_strategy_3 wording, 勝率 for equity.

Fit: OK.

**Rating: 4/5.** Main weakness: the hint lines share zh-rCN's calques. Otherwise well localized.

---

## Summary
vi: 5 strings changed · th: 26 strings changed (16 of them are the Hint-term fix) · ja: 6 strings changed
ko: 11 strings changed · zh-rCN: 6 strings changed · zh-rTW: 6 strings changed
Total: 60 strings across 6 locales. All pass the key, placeholder and escaping checks.
