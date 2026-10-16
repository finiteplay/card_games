# Google Play listing review v2, group 5: vi, th, ja-JP, ko-KR, zh-CN, zh-TW

> **Status: machine-translated and model-reviewed; no native-speaker review is planned.** The owner accepts the remaining language-quality risk. This report records model QA and automated checks; it does not claim native-speaker validation.

Scope: `translations/drafts/{vi,th,ja-JP,ko-KR,zh-CN,zh-TW}.json` (shared promise/rest/footer and all five games). I checked them against `translations/source/en-US.json`, `TRANSLATION_GUIDE.md`, the family rules in `AGENTS.md`, and the in-app strings (`core/ui`, `games/{klondike,spider,freecell,blackjack,holdem}`, `values-{vi,th,ja,ko,zh-rCN,zh-rTW}`). I made the edits with a script that checks each one: every old string had to match exactly once. The drafts are still valid JSON in the drafters' format (indent 2, CRLF, no trailing newline). I touched no other files.

## Limits after edits

`check_drafts.py`: 6 files, 0 problems. The only warnings are for vi, where Spider and FreeCell write the numbers as `23.675` and `2.000` (Vietnamese format, as intended). Character counts (title / short / composed full):

| lang | Klondike | Spider | FreeCell | Blackjack | Hold'em |
|---|---|---|---|---|---|
| vi | 30 / 77 / 1687 | 28 / 75 / 1655 | 30 / 80 / 1705 | 24 / 75 / 1474 | 30 / 75 / 1989 |
| th | 30 / 73 / 1460 | 28 / 69 / 1396 | 30 / 77 / 1405 | 24 / 71 / 1258 | 30 / 78 / 1750 |
| ja-JP | 24 / 43 / 762 | 23 / 44 / 785 | 23 / 41 / 777 | 22 / 44 / 689 | 26 / 41 / 927 |
| ko-KR | 22 / 44 / 846 | 21 / 46 / 837 | 20 / 43 / 813 | 18 / 39 / 728 | 21 / 41 / 992 |
| zh-CN | 20 / 28 / 564 | 16 / 30 / 558 | 16 / 26 / 556 | 25 / 25 / 467 | 29 / 28 / 648 |
| zh-TW | 18 / 28 / 580 | 16 / 30 / 571 | 24 / 25 / 576 | 26 / 26 / 472 | 29 / 28 / 661 |

## Claims check (all six languages, after edits)

All five games in all six languages keep every guide claim. The promise and rest paragraphs keep: free; no ads, in-app purchases or subscriptions; no accounts, tracking, data collection or sharing, leaderboards or online features; progress and statistics stay local; optional rest reminders with no medical wording; no urgency bonuses or timed daily rewards. The game texts keep:

- **Klondike:** certified Trivial–Expert levels and the separate uncertified Insane level; the dead-end caveat; bounded hints.
- **Spider:** 23,675 deals; 1/2/4 suits.
- **FreeCell:** 2,000 deals; the hint limit.
- **Blackjack:** computer dealer only; virtual chips with no monetary value; no real-money bets, deposits, chip purchases, cash-outs or prizes; 6 decks; dealer stands on soft 17; 3:2; insurance and peek; hints do not guarantee wins; 18+.
- **Hold'em:** six seats against 5 computer opponents; the same no-money list; hints that cannot see hidden cards and guarantee nothing; no matches against real people; 18+.
- **Every game:** 30 interface languages.

No short description contains "free" (miễn phí / ฟรี / 無料 / 무료 / 免费 / 免費). None contains "best", ranking, award, competitor, urgency, price or promotion wording. Every short description keeps "no ads", "offline" and the gameplay detail (draw 1/3, 1/2/4 suits, solvable deals, virtual chips, 5 opponents, hints). The vi/th "personal best" in the feature lists only describes the statistic.

## Cross-cutting decisions

- **Titles.** All titles are ≤ 30 characters. The apps' `app_name`/`game_title` are non-translatable Latin in every locale, so a local-script title is a deliberate local naming convention (guide: "document a deliberate local naming convention").
  - **Thai: keep Latin, recommended.** A Thai-script family does not work as a set. These would fit: `ไคลน์ไดก์ โซลิแทร์: FinitePlay` (30, at the limit), `สไปเดอร์ โซลิแทร์: FinitePlay` (29), `ฟรีเซลล์ โซลิแทร์: FinitePlay` (29) and `แบล็กแจ็ก 21: FinitePlay` (24). But Hold'em does not: `เท็กซัสโฮลเดมโป๊กเกอร์: FinitePlay` is 34 and `โป๊กเกอร์ Texas Holdem: FinitePlay` is 34. Only `เท็กซัสโฮลเดม: FinitePlay` (25) fits, and it drops "Poker". Latin titles match the launcher label and the in-app title, keep one script across the family, and the Thai short and full descriptions carry the Thai search words (แบล็กแจ็ก, โป๊กเกอร์, เท็กซัสโฮลเดม). A Thai ASO reviewer may still prefer `แบล็กแจ็ก 21: FinitePlay` for Blackjack alone.
  - **Chinese.** zh-CN `克朗代克纸牌接龙` (changed from `克朗代克接龙` so it contains the searched term 纸牌接龙), `蜘蛛纸牌` and `空当接龙` are the established mainland names. zh-TW `蜘蛛紙牌` and `克朗代克接龍` (接龍 is the Taiwan word for Solitaire) are fine. **FreeCell must differ:** `空當接龍` is the mainland name, and Taiwan's established name is `新接龍`. Changed to `新接龍 FreeCell: FinitePlay` (24). Blackjack (`21点` / `二十一點`) and Hold'em (`德州扑克` / `德州撲克`) put the common local search term first and the Latin name second. Both fit (25–29).
  - **ja/ko:** katakana/hangul game name + ソリティア/솔리테어, `ブラックジャック21` / `블랙잭 21`, `テキサスホールデム ポーカー` / `텍사스 홀덤 포커`. These are the usual store names. No change.
- **Dealer peek.** In th, ja, ko and zh-TW the draft used a word for "peek" that reads as "peak" or as secretly looking (cheating). I changed each to "dealer checks the face-down/hole card", using the app's own hole-card word.
- **Rank names in Spider intros.** I used the app's rank words (Già/Át, คิง/เอซ, キング/エース, 킹/에이스). The drafts mixed letters and words. zh keeps K/A, as the zh apps do.

## vi (Vietnamese): rating 4/5

**Changes**

| game | field | old -> new | reason |
|---|---|---|---|
| shared | rest | `hay quà hằng ngày theo giờ.` -> `hay phần thưởng hằng ngày có hạn giờ.` | "quà … theo giờ" reads as "hourly gifts"; source is "timed daily rewards" |
| klondike | intro | `Chơi Klondike Solitaire cổ điển ngay cả khi không có mạng.` -> `Chơi Klondike Solitaire cổ điển offline.` | "even when there is no network" implies an online mode exists; the other four games say "offline" |
| klondike | body | `tự động đưa bài lên cột nền` -> `tự động đưa bài lên nền` | app term is "nền" (Tự động chuyển bài vào nền); a foundation is a pile, not a column |
| freecell | body | `tự động đưa bài lên cột nền` -> `tự động đưa bài lên nền` | same as Klondike: app term "nền" |
| freecell | shortDescription | `FreeCell offline gọn nhẹ,` -> `FreeCell offline gọn gàng,` | "gọn nhẹ" = lightweight (app size), not "simple/uncluttered"; now matches the other four games |
| spider | shortDescription | `thân thiện với pin` -> `thân thiện pin` | one wording for "battery-conscious" across the five games (Klondike/FreeCell need the short form to fit) |
| blackjack | shortDescription | `thân thiện với pin` -> `thân thiện pin` | consistency across the five games |
| holdem | shortDescription | `thân thiện với pin` -> `thân thiện pin` | consistency across the five games |
| spider | intro | `từ K về Át` -> `từ Già về Át` | mixed "K"/"Át"; app Spider help says "từ Già đến Át" |
| spider | body | `bị giới hạn bởi giới hạn tìm kiếm` -> `phụ thuộc vào giới hạn tìm kiếm` | redundant "limited by the limits" |
| spider | body | `trên cả ba số lượng chất` -> `ở cả ba chế độ số chất` | calque of "across all three suit counts" |
| spider | body | `hiệu ứng chia hàng và di chuyển bài` -> `hiệu ứng chia một hàng bài và di chuyển bài` | "chia hàng" reads as "distributing goods"; app term "Chia một hàng" |
| blackjack | body | `nguy cơ quắc` -> `nguy cơ quá 21` | "quắc" is slang; app term for Bust is "Quá 21" |
| holdem | body | `Bỏ bài, xem bài, theo` -> `Bỏ bài, check, theo` | app Check button is "Check"; "xem bài" is not the app term |

**Short-description compromises:** none needed for content: every short keeps offline, simple, battery-conscious, the gameplay detail, hints and no ads (75–80 characters). For consistency, all five now use the same pair `gọn gàng, thân thiện pin`. The fuller `thân thiện với pin` fits Spider, Blackjack and Hold'em but would push Klondike to 81 and FreeCell to 84. "offline" is the loanword (Vietnamese store copy uses it widely; `ngoại tuyến` is longer).

**Needs a native speaker:**
- `thân thiện pin` for "battery-conscious": should read as "made with the battery in mind", not "saves battery". Do not use `tiết kiệm pin` (= saves battery, a stronger claim). Is the telegraphic form without `với` acceptable in a short line?
- `gọn gàng` for "simple" (uncluttered, not lightweight).
- `Già` for King in the Spider intro follows the Spider app help, but Hold'em and card faces say "K" (known open item in `TODO.md`).
- Hold'em terms left in English as in the app: check, all-in, equity, pot odds, side pot, blind.
- `Quá 21` (app) rather than player slang `quắc` for bust; `17 mềm`; `nhà cái kiểm tra lá úp` for dealer peek.
- Number format `23.675` / `2.000` (Vietnamese) in Spider/FreeCell.

## th (Thai): rating 3.5/5

**Changes**

| game | field | old -> new | reason |
|---|---|---|---|
| klondike | intro | `เล่นไคลน์ไดก์ โซลิแทร์แบบคลาสสิกแบบออฟไลน์` -> `เล่น Klondike Solitaire แบบคลาสสิกได้ออฟไลน์` | only place the name was transliterated (Spider/FreeCell intros and all titles/shorts use Latin); double "แบบ" |
| spider | intro | `จาก K ถึง A` -> `จากคิงถึงเอซ` | app Spider help says "จากคิงไปจนถึงเอซ" |
| spider | body | `ไพ่ที่แจกเริ่มต้นทุกกองมีวิธีแก้` -> `ทุกกระดานเริ่มต้นที่รวมไว้มีวิธีแก้` | "กอง" (pile) used as the word for a deal; app calls a deal "กระดาน" (เลือกกระดาน) |
| spider | body | `ตำแหน่งในภายหลัง` -> `สถานการณ์ในภายหลัง` | "ตำแหน่ง" (location) is a calque of "position" |
| spider | body | `ไพ่ที่รับรองว่าแก้ได้ 23,675 กองในทั้งสามจำนวนดอก` -> `23,675 กระดานที่รับรองว่าแก้ได้ ครอบคลุมทั้งสามแบบจำนวนดอก` | app deal word "กระดาน"; "in all three suit-numbers" was a calque |
| freecell | shortDescription | `ไพ่แก้ได้` -> `กระดานที่แก้ได้` | "ไพ่แก้ได้" = "solvable cards"; source is "solvable deals" (app: กระดาน) |
| freecell | body | `ไพ่ที่แจกทุกกองเริ่มต้นด้วยวิธีแก้` -> `ทุกกระดานที่รวมไว้เริ่มต้นด้วยวิธีแก้` | app deal word "กระดาน"; "included" was dropped |
| freecell | body | `• ไพ่ 2,000 กองที่รับรองว่าแก้ได้` -> `• 2,000 กระดานที่รับรองว่าแก้ได้` | app deal word "กระดาน" |
| blackjack | body | `เจ้ามือแอบดูไพ่ใบคว่ำ` -> `เจ้ามือตรวจดูไพ่ใบคว่ำ` | "แอบดู" = to peek secretly, sounds like cheating; dealer peek is an open rule check |
| holdem | body | `ความแข็งแรงของมือ` -> `ความแข็งของมือ` | "แข็งแรง" = physically strong/healthy; app says "มือที่แข็งที่สุด" |
| holdem | body | `ทิ้งไพ่ เช็ก คอล เบ็ต เรส และออลอิน` -> `โฟลด์ เช็ค คอล เบ็ท เรส และออลอิน` | app buttons are โฟลด์ / เช็ค / คอล / เบ็ท / เรสเป็น / ออลอิน |
| holdem | body | `วิธีช่วยเรื่องกลยุทธ์` -> `ความช่วยเหลือด้านกลยุทธ์` | "ways to help about strategy" was awkward; source "strategy help" |

**Short-description compromises:** none. All five keep offline, `เรียบง่าย` (simple), `คำนึงถึงแบตเตอรี่` (takes the battery into account: qualified, not strengthened), the gameplay detail, hints and no ads (69–78). Game names in the solitaire shorts stay Latin, matching the titles; Blackjack and Hold'em use the Thai words แบล็กแจ็ก / โป๊กเกอร์.

**Titles:** kept Latin, see "Cross-cutting decisions" above.

**Needs a native speaker:**
- **App bugs to fix in `core/ui/src/main/res/values-th/strings.xml`** (the listing already uses the correct spellings): `การแจ้งเตือนพักผ้อน` → `พักผ่อน` (line 39), `พักสักครุ่` → `พักสักครู่` (lines 42, 44), `คณเล่น` → `คุณเล่น` (line 43).
- Hold'em action words follow the app (`โฟลด์ เช็ค คอล เบ็ท เรส ออลอิน`). The Royal Institute spellings would be `เช็ก` / `เบ็ต`; if the app changes, change the listing with it.
- `กระดาน` (board) as the word for a deal, taken from the app's deal picker; `เกมที่` is the FreeCell hand label in the app.
- `เจ้ามือตรวจดูไพ่ใบคว่ำ` for dealer peek; `ซอฟต์ 17`; `เพิ่มเดิมพันเท่าตัว` for Double (long, from the app).
- Klondike level names `ง่ายมาก … ผู้เชี่ยวชาญ`, `โหดมาก` for Insane.
- `30 ภาษาของส่วนติดต่อผู้ใช้` is formal; `รองรับ 30 ภาษา` would be more natural, but I left it because the meaning is right.
- The in-app Thai strings were rated 3/5 in the string review, so terminology overall needs the most attention here.

## ja-JP (Japanese): rating 4.5/5

**Changes**

| game | field | old -> new | reason |
|---|---|---|---|
| spider | intro | `KからAまで` -> `キングからエースまで` | app Spider help says "キングからエースまで" |
| spider | body | `行の配り方とカード移動のアニメーション` -> `1列を配るときとカードを動かすときのアニメーション` | "行の配り方" = "how rows are dealt" (not an animation); app term "1列を配る" |
| blackjack | body | `保険とディーラーのピークがあります` -> `保険と、ディーラーが伏せカードを確認するピークがあります` | bare "ピーク" reads as "peak"; app calls the hole card "伏せカード" |
| holdem | body | `ルール、ハンドランキング、戦略ヘルプ` -> `ルール、役の一覧、戦略ヘルプ` | app help tab for hand rankings is "役" |

**Short-description compromises:** none. All five keep シンプル, バッテリーに配慮した (qualified), offline, the gameplay detail, hints and 広告なし (41–44 characters).

**Needs a native speaker:**
- **Klondike level `無謀` ("reckless") reads oddly as a difficulty name.** It is the app's term, so the listing keeps it in 「」. Suggest the app rename it (for example 「超難関」 or 「エクストリーム」), then update the listing.
- `配置` for deal (app: 配置を選択), `組み札` for foundation, `手のハイライト` for move highlighting.
- `ディーラーが伏せカードを確認するピーク` for dealer peek; `役の一覧` for hand rankings (app tab 役).
- `18歳以上の成人向け`.

## ko-KR (Korean): rating 4/5

**Changes**

| game | field | old -> new | reason |
|---|---|---|---|
| klondike | shortDescription | `단순하고` -> `깔끔하고` | "단순하다" can read as simplistic; guide asks for uncluttered |
| spider | shortDescription | `단순하고` -> `깔끔하고` | same |
| freecell | shortDescription | `단순하고` -> `깔끔하고` | same |
| blackjack | shortDescription | `단순하고` -> `깔끔하고` | same |
| holdem | shortDescription | `단순하고` -> `깔끔하고` | same |
| spider | shortDescription | `1/2/4무늬` -> `1/2/4가지 무늬` | "N무늬" is not natural Korean; app says "한 가지 무늬" etc. |
| klondike | body | `매우 쉬움부터 전문가까지의 판은` -> `'매우 쉬움'부터 '전문가'까지의 판은` | quote level names so they read as names (as ja/zh do) |
| klondike | body | `별도의 미친 레벨에는` -> `별도의 '미친' 레벨에는` | app level name "미친" ("crazy") reads badly in prose; quoting marks it as a name (app term kept, flagged) |
| klondike | body | `여섯 가지 난이도, 매우 쉬움부터 전문가까지는` -> `여섯 가지 난이도, '매우 쉬움'부터 '전문가'까지는` | quote level names |
| klondike | body | `기초 더미 자동 이동` -> `파운데이션 자동 이동` | app term is "파운데이션 자동 이동" |
| freecell | body | `기초 더미 자동 이동` -> `파운데이션 자동 이동` | app term is "파운데이션 자동 이동" |
| spider | intro | `K부터 A까지` -> `킹부터 에이스까지` | app Spider help says "킹부터 에이스까지" |
| spider | intro | `1무늬로 시작하거나 2무늬, 4무늬에 도전할` -> `한 가지 무늬로 시작하거나 두 가지, 네 가지 무늬에 도전할` | app suit-count options are "한 가지 / 두 가지 / 네 가지 무늬" |
| spider | body | `• 1무늬, 2무늬, 4무늬 스파이더` -> `• 한 가지, 두 가지, 네 가지 무늬 스파이더` | app term (see above) |
| blackjack | body | `보험과 딜러 피크가 포함됩니다` -> `보험과 딜러의 뒤집힌 카드 확인(피크)이 포함됩니다` | bare "피크" reads as "peak"; app describes the hole card as "뒤집힌 카드" |
| holdem | body | `규칙, 핸드 순위, 전략 도움말` -> `규칙, 족보, 전략 도움말` | app help tab for hand rankings is "족보" |

**Short-description compromises:** none. All five keep `깔끔하고` (changed from `단순하고`, which can mean simplistic), `배터리를 고려한` (qualified), offline, the gameplay detail, hints and `광고 없음` (39–46).

**Needs a native speaker:**
- **Klondike level `미친` ("crazy/insane" as a bare adjective) reads badly in prose and as a label.** It is the app's term, so the listing now quotes it ('미친' 레벨). Suggest the app rename it (for example `극한` or `지옥`), then update the listing.
- Quoted level names `'매우 쉬움'` … `'전문가'`: check that quoting reads naturally.
- `파운데이션` (app) for foundation, `한 가지 / 두 가지 / 네 가지 무늬` for suit counts, `1/2/4가지 무늬` in the short line.
- `딜러의 뒤집힌 카드 확인(피크)` for dealer peek; `족보` for hand rankings (app tab); `만 18세 이상`.

## zh-CN (Simplified Chinese): rating 4.5/5

**Changes**

| game | field | old -> new | reason |
|---|---|---|---|
| klondike | title | `克朗代克接龙: FinitePlay` -> `克朗代克纸牌接龙: FinitePlay` | "纸牌接龙" is the mainland search term for Solitaire; matches the intro and the English "Klondike Solitaire" |
| klondike | body | `未经验证的牌局` -> `未经认证的牌局` | source says "uncertified"; the feature list uses 经认证, zh-TW uses 未經認證 |
| spider | body | `发牌一行和移牌的动画` -> `发一行牌和移牌的动画` | word order; app term "发一行牌" |

**Short-description compromises:** none. All five keep 简洁, 兼顾电量 (qualified), offline, the gameplay detail, hints and 无广告 (25–30).

**Needs a native speaker:**
- Title `克朗代克纸牌接龙` versus the plain `纸牌接龙` that most mainland players search for. I kept the game name, as the English does.
- `兼顾电量` for "battery-conscious" (should not read as 省电).
- `21点 Blackjack` versus `21点` only; `黑杰克` for a natural blackjack (app term).
- Klondike `入门 … 专家`, `疯狂`; `翻1/3张` (app 单张翻牌 / 三张翻牌).

## zh-TW (Traditional Chinese): rating 4/5

**Changes**

| game | field | old -> new | reason |
|---|---|---|---|
| freecell | title | `空當接龍: FinitePlay` -> `新接龍 FreeCell: FinitePlay` | "空當接龍" is the mainland name; Taiwan players know FreeCell as "新接龍" (Windows zh-TW); Latin name added because 新接龍 alone reads as "new solitaire" |
| freecell | shortDescription | `離線空當接龍` -> `離線新接龍` | Taiwan name (see title) |
| freecell | intro | `在經典的空當接龍中` -> `在經典的新接龍（FreeCell）中` | Taiwan name (see title); FreeCell added so "classic new solitaire" is not misread |
| blackjack | body | `Blackjack 賠率為 3:2` -> `黑傑克賠率為 3:2` | app term is "黑傑克" (zh-CN draft already uses 黑杰克) |
| blackjack | body | `莊家偷看底牌` -> `莊家查看暗牌` | "偷看" = peek secretly (sounds like cheating); app calls the hole card "暗牌" |

**Short-description compromises:** none. All five keep 簡潔, 顧及耗電 (qualified), offline, the gameplay detail, hints and 無廣告 (25–30).

**zh-TW versus zh-CN:** the vocabulary does differ properly, not only the script: 抽牌 vs 翻牌, 復原 vs 撤销, 帳號 vs 账号, 裝置 vs 设备/本机, 資料 vs 数据, 蒐集 vs 收集, 佈景主題 vs 主题, 基礎牌組 vs 基础牌堆, 發一列牌 vs 发一行牌, 儲值 vs 充值, 二十一點 vs 21点. This review adds 新接龍 vs 空当接龙.

**Needs a native speaker:**
- **`新接龍 FreeCell` as the FreeCell title/name** (Taiwan Windows name), instead of the mainland `空當接龍`. The in-app title is Latin `FreeCell`, so there is no app conflict.
- `克朗代克接龍` versus `接龍` for Klondike; `二十一點` versus `21點` in the Blackjack title.
- `莊家查看暗牌` for dealer peek (app hole-card word 暗牌); `基礎牌組` (app) for foundation, though 牌組 usually means "deck" in Taiwan.
- `顧及耗電` for "battery-conscious".

## Out of scope, noticed

- The **Spider in-app help** (`games/spider/.../values/strings.xml` `catalog_status_notice`, shown in `HelpScreen.kt`) says "Four-suit deals are not yet certified". The English listing says "23,675 certified-solvable deals across all three suit counts". `docs/games/spider/FOUR_SUIT_CATALOG.md` suggests a four-suit catalog now exists, so the help string may just be stale. Either way, check it against the shipped catalog before any listing (English included) goes out. I translated the listing claim as written.

## Summary

1. I made 54 small edits across the six drafts: 14 vi, 12 th, 4 ja, 16 ko, 3 zh-CN, 5 zh-TW. They fix meaning drift, calques, a "simplistic" reading (ko), a "lightweight" reading (vi), cheating-sounding dealer-peek wording, terms that differed from the app, and the zh-TW FreeCell name (新接龍, not the mainland 空當接龍).
2. Every title, short description and full description is within the limits (30/80/4000), every guide claim and number is present, and no short description says "free" or uses a slogan. Thai titles stay Latin, because a Thai Hold'em title does not fit without dropping "Poker".
3. Model quality ratings: vi 4, th 3.5, ja 4.5, ko 4, zh-CN 4.5, zh-TW 4. No native-speaker review is planned; the owner accepts the residual language-quality risk.
