# Google Play listing review, group 5: vi, th, ja-JP, ko-KR, zh-CN, zh-TW

Scope: `blackjack/<code>.md` and `holdem/<code>.md` for the six languages, checked against `source/*.en-US.md` and the reviewed in-app strings (`values-vi|th|ja|ko|zh-rCN|zh-rTW`).

Limits after edits (listing_limits.py, Unicode characters). The script's single remaining problem is `holdem/tr-TR` (SHORT 86), which is outside this group.

| file | title | short | full |
|---|---|---|---|
| blackjack vi | 23 | 78 | 1042 |
| blackjack th | 23 | 76 | 970 |
| blackjack ja-JP | 23 | 45 | 555 |
| blackjack ko-KR | 23 | 39 | 585 |
| blackjack zh-CN | 23 | 28 | 390 |
| blackjack zh-TW | 23 | 28 | 392 |
| holdem vi | 27 | 80 | 1398 |
| holdem th | 27 | 73 | 1243 |
| holdem ja-JP | 27 | 42 | 705 |
| holdem ko-KR | 27 | 41 | 697 |
| holdem zh-CN | 27 | 25 | 483 |
| holdem zh-TW | 27 | 25 | 485 |

All titles are unchanged ("Blackjack by FinitePlay", "Texas Hold’em by FinitePlay"). Every FULL text keeps all the safety claims: offline; virtual chips with no monetary value; computer dealer or opponents only; no ads; no in-app purchases; no real-money betting, deposits, cash-outs or prizes; no online play or real opponents; no account (Hold'em also: no tracking, and progress and statistics stay on the device). Every Hold'em feature list keeps the source's 10 • items in the same order.

## vi (rating 4/5)

**Blackjack**
- SHORT: `... tiền thật, online.` became `Blackjack offline, chip ảo. Không quảng cáo, mua hàng, tiền thật, chơi online.` (78). A bare "online" at the end of the list was unclear. **Compromise:** the exact word for "multiplayer" (`nhiều người chơi`) does not fit (83 at minimum), so the line says "online play", which matches the FULL claim. To make room, "ngoại tuyến" became "offline", a word Vietnamese store copy uses widely.
- Dealer peek: `nhà cái kiểm tra bài` (vague: "dealer checks cards") became `nhà cái kiểm tra lá bài úp` (checks the face-down card). "Lá bài úp" is the in-app term.
- Hint odds clause: the awkward `nhà cái quá 21 thường xuyên đến đâu ... rút thêm rủi ro thế nào với bạn` became `tần suất nhà cái quá 21 với lá bài đang lộ, và mức rủi ro với bạn nếu rút thêm`. The meaning is now exact ("how often the dealer busts" / "how risky a hit is for you").
- These terms already matched the app: Rút / Dừng / Gấp đôi / Tách / Bảo hiểm / Nhà cái / Gợi ý / Nhìn lại.

**Hold'em**
- SHORT: `... tiền thật, đấu online.` became `Poker offline, chip ảo. Không quảng cáo, mua hàng, tiền thật, người chơi online.` (80). This restores the exact "online players"; it fits only with "offline" in place of "ngoại tuyến".
- Look-back bullet: `điều gì sẽ xảy ra` ("what will happen") became `điều gì lẽ ra đã xảy ra` ("what would have happened").
- These terms match the app: Bỏ bài, Check, Theo, Cược, Tố lên, All-in, blind, equity, pot odds, Gợi ý.

## th (rating 4/5)

**Blackjack**
- SHORT: `หรือออนไลน์` ("or online") became `หรือโหมดผู้เล่นหลายคน` ("or multiplayer mode"). This restores the exact claim (76).
- Game-word spelling: `แบล็กแจ็ก` became `แบล็คแจ็ค` in all 4 places, to match the app's `hand_blackjack` / `stat_tile_blackjacks`. The title is untouched.
- Hint feature name: the listing called the Hint `คำแนะนำ`, while the app uses `คำใบ้` (18 uses, including the review title `ย้อนดูคำใบ้`). Every reference to the Hint feature now says `คำใบ้`. `คำแนะนำกลยุทธ์พื้นฐาน` (basic-strategy guidance) correctly stays as it was.
- Dealer peek: `เจ้ามือเช็กไพ่` became `เจ้ามือตรวจไพ่คว่ำ` (dealer checks the face-down card). Also `การจ่ายแบล็กแจ็ก 3:2` became `การจ่าย 3:2 สำหรับแบล็คแจ็ค`, which is clearer.

**Hold'em**
- SHORT was already faithful ("ผู้เล่นออนไลน์" = online players). No change.
- In the hint bullet and the look-back bullet, the Hint name `คำแนะนำ` became `คำใบ้`, to match the app. In the hint bullet, "advice" stays `คำแนะนำ`, which is correct.
- `วิธีช่วยด้านกลยุทธ์` became `ตัวช่วยด้านกลยุทธ์` ("strategy help").
- Poker terms match the app: โฟลด์ เช็ค คอล เบ็ท เรส ออลอิน, บลายด์, อิควิตี้, พอตออดส์.

## ja-JP (rating 4/5)

**Blackjack**
- SHORT: `対人戦なし` became `マルチプレイなし` (exact "multiplayer"), and `実際のお金` became `リアルマネー`, the natural Japanese store term (45 chars).
- FULL: `実際のお金を使うベット` became `リアルマネーでのベット`.
- Dealer peek: `ディーラーのピーク` was an invented-sounding phrase. It became `ディーラーの伏せカード確認（ピーク）`, which uses `伏せカード` from the app.
- Hint odds sentence: `見えているカードに対してディーラーがどれくらいバストするか…` became `その根拠となる確率として、ディーラーのアップカードから見たバストの頻度と、ヒットした場合のあなたのリスクも示します。` It uses the app's `アップカード` / `バスト` and reads more naturally.

**Hold'em**
- SHORT: `実際のお金` became `リアルマネー`. "Online players" stays `オンライン対戦` ("online matches"), which is natural Japanese and equivalent in substance. A literal `オンラインプレイヤー` reads poorly. This is a minor wording compromise, not a lost claim.
- FULL: both `実際のお金` instances became `リアルマネーでのベット`.
- Look-back bullet: the ambiguous `そうしていた場合の展開` became `そしてどうなっていたか` ("and what would have happened"), and "each" was added (`各ハンド`) to match the source.
- These terms match the app: フォールド チェック コール ベット レイズ オールイン, ブラインド, エクイティ, ポットオッズ, ヒント.

## ko-KR (rating 4/5)

**Blackjack**
- SHORT: `구매` became `인앱 구매`. "Multiplayer" was already exact (`멀티플레이`) (39 chars).
- Dealer peek: `딜러 피크` became `딜러의 블랙잭 확인(피크)`. This is plain blackjack language, with the jargon kept in parentheses.
- Hint odds: `보이는 카드에서` became `딜러의 오픈 카드로 볼 때`, which uses the app's `오픈 카드`.
- Closing line: `사적인 플레이` (odd connotation) became `프라이빗한 플레이`.

**Hold'em**
- SHORT: `구매` became `인앱 구매`. "Online players" was already exact (41 chars).
- `테이블의 마지막 플레이어를 노려 보세요` became `테이블에 마지막까지 남는 플레이어가 되어 보세요` ("aim to be the last player at the table").
- These terms match the app: 폴드 체크 콜 베팅 레이즈 올인, 블라인드, 에퀴티, 팟 오즈, 힌트.

## zh-CN (rating 4/5)

**Blackjack**
- SHORT: `联网对战` ("online battle") became `多人游戏`, the exact "multiplayer" (28 chars). The `21 点` gloss is accurate, because 21点 is the standard Chinese name of the game, and it fits, so I kept it.
- The sentence `规则清晰，发牌公平，可选的基本策略指引，畅玩经典 Blackjack。` was an ungrammatical run-on. It became `畅玩经典 Blackjack：规则清晰，发牌公平，还有可选的基本策略指引。`
- Dealer peek: `庄家查牌` became `庄家查看暗牌`, using the app's `暗牌`. Payout: `3:2 的 Blackjack 赔率` became `3:2 的 Blackjack 赔付` ("payout" rather than "odds").

**Hold'em**
- `也无跟踪` became `也没有跟踪`, a more natural phrasing. Everything else is faithful. These terms match the app: 弃牌 过牌 跟注 下注 加注 全下, 盲注, 胜率 (equity), 底池赔率, 提示.

## zh-TW (rating 4/5)

**Blackjack**
- SHORT: `連線對戰` became `多人遊戲` (exact "multiplayer"). The `21 點` gloss is kept for the same reason as zh-CN.
- The run-on sentence got the same restructure as zh-CN, with 清楚 and 還有.
- Dealer peek: `莊家查牌` became `莊家檢查底牌` (the usual Taiwan term). Payout: `賠率` became `賠付`.

**Hold'em**
- `也無追蹤` became `也沒有追蹤`.
- The text differs from zh-CN in vocabulary, not only in script: 帳號/账号, 儲值/充值, 提領/提现, 裝置/设备, 運作/运行, 機率/几率, 淺顯/通俗, 版面/布局, 直向・橫向/竖屏・横屏, 介面/界面, 應用程式內購買/应用内购买, 線上/在线, 說明/帮助, 底牌/暗牌.

## SHORT compromises (summary)

| file | compromise |
|---|---|
| blackjack vi | "multiplayer" rendered as `chơi online` ("online play"), because `nhiều người chơi` is 3+ characters over the limit. "Offline" is used instead of "ngoại tuyến" to fit. |
| holdem vi | Exact "online players" restored, but only by using "offline" instead of "ngoại tuyến" (exactly 80). |
| holdem ja-JP | "online players" rendered as `オンライン対戦` (online matches) for naturalness. |

All other SHORT lines carry every claim in the English SHORT. The English SHORT itself does not mention the computer dealer or opponents, the lack of monetary value, or "no account". I did not add those, so the SHORT lines stay faithful.

## Needs a native speaker

- **vi:** whether the English loanwords `offline` / `online` in both SHORT lines read well next to the FULL text's `ngoại tuyến` / `trực tuyến`.
- **th:** the spelling choice `แบล็คแจ็ค` (app) versus `แบล็กแจ็ก` (Royal Institute). Also whether `เจ้ามือตรวจไพ่คว่ำ` reads as the familiar term for the dealer peek.
- **ja-JP:** whether `ディーラーの伏せカード確認（ピーク）` and `リアルマネー` suit the intended register.
- **ko-KR:** whether `딜러의 블랙잭 확인(피크)` reads naturally.
- **zh-TW:** `提領` (versus `兌現`) for "cash-outs", and `莊家檢查底牌` for the peek.
- **all:** the store layout of the long Hold'em hint bullet.

## Summary

I fixed all 12 files in place. The fixes restore exact "multiplayer"/"online players" wording in 6 SHORT lines, make the dealer peek normal blackjack language in all 6 Blackjack listings, align the Hint, odds and card terms with the reviewed app strings (most notably คำใบ้ in Thai), and fix a few awkward or inaccurate sentences.
All files are within the limits. The safety claims are complete in every FULL text, and every SHORT keeps all of the English SHORT's claims. The only compromises are vi Blackjack (`chơi online` for "multiplayer") and a minor ja Hold'em wording choice.
Quality after review is 4/5 for all six languages. The remaining questions are register and spelling choices listed above for native speakers.
