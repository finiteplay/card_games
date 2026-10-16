# FinitePlay website, English listings, and marketing review

Reviewed October 15, 2026, Pacific time. Recommendations and draft copy only.

FinitePlay has a credible offer, but its discovery and installation journey need more work than its titles. Lead with familiar card games and visible gameplay, explain the no-ads/no-purchases promise immediately, and make tester access understandable. Keep the broader mission available for people who want to learn more.

The opportunity is real, but it is competitive: free, offline, private solitaire already exists at meaningful scale. FinitePlay should demonstrate useful hints, well-chosen challenges, dependable play, and a consistent promise across its games. These are positioning hypotheses, not measured conversion winners.

## What was verified

- Viewed the live [homepage](https://finiteplay.org/) at desktop and phone widths, and inspected its local HTML/CSS.
- Read all five canonical English `listing.md` files and the October 14 review and implementation records.
- Read back all five listings through Android Publisher on October 15. English titles, short descriptions, and full descriptions match their canonical files exactly. All have en-US, es-ES, and de-DE listing text.
- Matched uploaded phone screenshot hashes and their order against local files, then visually inspected every ordered image, the icons, and feature graphics. There are 4/4/4/6/4 screenshots for Klondike/Spider/FreeCell/Blackjack/Hold'em.
- Read current release tracks through the API. All five have completed Internal and Alpha releases; all five production tracks are empty. Alpha version codes are 7/6/6/6/4 respectively. This does not prove every visitor is eligible, nor verify current review status in Publishing overview.
- An anonymous visit to the Klondike tester link reached Google sign-in. The site's no-account promise concerns the game; Play distribution still involves a Google account. Tester membership and regional eligibility were not verified end to end.
- Public store pages for FinitePlay could not be retrieved through the web research tool. Their failure to load is not evidence of removal. The listing review uses authenticated API readback.

API inspection created and deleted temporary, uncommitted edits; it did not commit listing changes, submit review, or change releases, testers, or access. No website or canonical listing edits were made. Existing game-development work was left untouched. Builds/tests were unnecessary for a research document; this is not a fresh audit of the uploaded binaries.

Evidence files are in `C:/Users/oleks/dev/FinitePlay/_temp/marketing-review-2026-10-15/`: `publisher-listings.jsonl`, `listing-verification.json`, `tracks.jsonl`, and the five screenshot contact sheets. API listing state, public serving state, and production availability are separate facts. [Google's track definitions](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en) explain that distinction.

## Market findings and positioning

This is a focused competitor and messaging study, not a total-market or search-volume estimate. Download bands below are cumulative Google Play thresholds observed in the research, not active users, annual demand, revenue, or numbers that can be added together. Competitors were not installed or security-audited; their features and privacy statements are their own declarations.

| Competitor | Observed market signal | What it means for FinitePlay |
|---|---|---|
| [MobilityWare Solitaire](https://play.google.com/store/apps/details?id=com.mobilityware.solitaire&hl=en_US) | 100M+ downloads; ads and purchases; draw 1/3, winning deals, hints, offline play, events | Familiar gameplay has established demand. Offline and hints alone are weak differentiators. |
| [MobilityWare Spider](https://play.google.com/store/apps/details?id=com.mobilityware.spider&hl=en_US) | 50M+ downloads; ads and purchases; daily challenges and events | Sell the actual sequence-building experience and suit options, then the uninterrupted-play promise. |
| [MobilityWare FreeCell](https://play.google.com/store/apps/details?id=com.mobilityware.freecell&hl=en_US) | 10M+ downloads; ads and purchases; planning and relaxation messaging | A planning-focused message is familiar. Show how FinitePlay makes planning convenient. |
| [Microsoft Solitaire Collection](https://play.google.com/store/apps/details?id=com.microsoft.microsoftsolitairecollection&hl=en_US) | 10M+ downloads; ads and purchases; several games, rewards, account-based cross-device progress | FinitePlay can offer a focused local game. It should explain that local-only progress does not provide cloud synchronization. |
| [AI Factory Solitaire Pro](https://play.google.com/store/apps/details?id=uk.co.aifactory.solitaire&hl=en_US) | 50K+ downloads; $4.99 displayed; removes advertising; difficulty choices and solutions | Ad-free play and known solutions already have a paid alternative. FinitePlay can remove the purchase requirement, while still needing good gameplay. |
| [PotatoJam Classic Solitaire Klondike](https://play.google.com/store/apps/details?id=com.potatojam.classic.solitaire.klondike&hl=en_US) | 1M+ downloads; advertises free, no ads, offline; declares no collection/sharing | This is a direct counterexample to any claim that free/private/ad-free solitaire is unique. It also demonstrates an established audience for a simpler offer. |
| [Koaura Solitaire](https://play.google.com/store/apps/details?id=com.koaura.solitaire) | Its indexed listing promotes winnable play, readable controls, breaks, no ads/purchases/accounts/analytics | Even the broader calm-play positioning has close competitors. Do not claim exclusivity or make unsupported accessibility comparisons. |
| [Poker Offline: Texas Holdem](https://play.google.com/store/apps/details?id=com.redrocket.poker&hl=en_US) | 5M+ downloads; ads/purchases; offline tournaments, bonuses and progression | FinitePlay can explain a complete local tournament without paid chips or return incentives. Offline poker itself is established. |
| [Blackjack Ace](https://play.google.com/store/apps/details?id=com.blackjack_ace.blackjackace&hl=en_US) | 50K+ downloads; customizable strategy drills; declares no collection/sharing | Do not market FinitePlay as a unique private trainer or a professional casino preparation tool. Its current strength is understandable dealer play with optional guidance. |

The sampled [FreeCell reviews](https://play.google.com/store/apps/details?id=com.mobilityware.freecell&hl=en_US) include complaints about ads and subscription verification when offline. [PotatoJam reviews](https://play.google.com/store/apps/details?id=com.potatojam.classic.solitaire.klondike&hl=en_US) include appreciation for simplicity and privacy, alongside requests about sound and statistics. These are useful interview hypotheses, not a representative survey or evidence that all competitors behave the same way.

Recommended umbrella message:

> Familiar card games. Yours to enjoy.
>
> Play offline with helpful hints, no ads, and no in-app purchases. No game account or tracking. Your progress stays on your device.

For acquisition, use the specific game name before the umbrella brand. On game pages and in outreach, pair one playable benefit with the family promise: draw 1/3, Spider suit choices, FreeCell planning, explained Blackjack decisions, or a six-seat poker tournament. Keep rest reminders as supporting evidence of the mission. Leading with reminders asks someone to care about stopping before they have seen why they would enjoy starting.

Initial audiences to investigate:

| Audience hypothesis | Message and proof | Useful channel |
|---|---|---|
| Existing solitaire players frustrated by interruptions | Familiar game, no ads/purchases; show a real game and a hint | Android and solitaire communities that permit developer introductions |
| People who want a dependable offline game | Full local play and saved progress; demonstrate resuming a game | Searchable game pages, Android app roundups, direct referrals |
| Players returning to classic desktop card games | Recognizable rules, draw choices, undo and replay | Plain-language guides and short gameplay demonstrations |
| Adults exploring Blackjack or poker decisions | Virtual chips, explained guidance, computer play | Adult card-game learning communities; distinct landing pages |

Do not assume a particular age group, country, or language has the highest demand without acquisition evidence. Avoid describing the whole portfolio as suitable for children: Blackjack and Hold'em have an existing adults-only audience decision.

## Homepage: highest-priority changes

The current visual identity is coherent: navy/cyan colors, clear typography, a recognizable logo, and a consistent promise. The problem is hierarchy and the lack of product evidence.

At 1280×720, the projects section starts approximately 3,726 CSS pixels below the top. At 390×844, it starts approximately 6,210 pixels down, and the first game link is about 6,773 pixels down. Roughly 616 words precede the projects section. These are measurements of this layout, not a bounce-rate study; anchor links do offer a shortcut. On the phone view, the header hides the Mission/Promise/Projects links and retains Join us, prioritizing contributors over players.

| Priority | Finding | Recommendation |
|---|---|---|
| P0 | All app links are Alpha opt-in links; no production release exists | Describe the current offer as early-access testing. Explain how to request eligibility, join, install, and report feedback. Check that process using an eligible account before advertising it broadly. |
| P1 | No game, gameplay image, or Android identification in the hero | Add Android, a genuine gameplay preview, and a primary Choose a game action. Keep the corporate headline if it is important to the broader company, but add a visible product-focused section immediately below it. |
| P1 | Several long mission sections precede the games | Put games immediately after the hero, followed by a short promise, then the longer mission and support material. Preserve the company's broader app mission. |
| P1 | Hold'em is absent from the live four-card grid | Add the fifth game with an accurate early-access label, virtual-chip explanation, and verified access route. Separate the three solitaires visually from the two adults-only simulated table games. |
| P1 | Cards repeat values without saying much about their own game | Give each card one concrete benefit and a real board image. Use the suggested blurbs below. |
| P2 | No individual game landing pages | Create a useful page for each game with screenshots, features, rules/help, availability, requirements, privacy, and support. Link to it from the homepage. |
| P2 | Search/share text is generic; social preview uses the wide logo | Give each game page a descriptive title and description. Use a dedicated social image with legible gameplay. Keep the logo as branding. Validate previews and indexing; do not promise ranking gains. |
| P2 | The page spends acquisition space on company structure and how AI reduces development cost | Move detailed company, sustainability, and development explanations to About/Promise. Answer how the games stay free in a short FAQ. |

Suggested product-focused hero copy:

> **Classic card games for Android. On your terms.**
>
> Enjoy solitaire, blackjack, and poker offline, with helpful hints and no ads or in-app purchases. No game account or tracking. Your progress stays on your device.
>
> **Choose a game** · Our promise
>
> Currently in closed testing. Tester eligibility is required. Blackjack and poker are for adults 18+ and use virtual chips only, with no real money or prizes.

Use the early-access message until a production release is actually available; only then switch eligible game CTAs to their public Play listings. A completed Alpha rollout is not the trigger for a public launch campaign.

Suggested card blurbs:

- **Klondike:** Classic solitaire with draw 1 or 3, a choice of challenge, and optional hints.
- **Spider:** Build King-to-Ace runs with 1, 2, or 4 suits. Undo, replay, and ask for a hint.
- **FreeCell:** Every card is visible. Plan with four free cells, move sequences, and explore another approach with undo.
- **Blackjack:** Play a computer dealer with virtual chips and basic-strategy hints that explain your choices. Adults 18+.
- **Texas Hold'em:** Play a six-seat tournament against five computer opponents, with explained hints and saved progress. Virtual chips only. Adults 18+.

For the current closed test, provide a clearly labeled request-access route using the existing support address if email-based enrollment remains the chosen method. Explain that requesting access does not automatically grant it. Do not silently add people to a tester list or change the test's access model.

## English listing review

Keep the current titles. They identify the games and maintain the family brand. They are already a substantial improvement over the earlier titles. Do not add Free, No Ads, repetitive synonyms, or ranking claims to them. Google's current [listing limits](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) are 30/80/4,000 characters for title/short/full descriptions. [Metadata guidance](https://support.google.com/googleplay/android-developer/answer/13393723?hl=en) favors accurate functionality and restricts promotional title text.

| Game | Current title | Title / short / full characters | Assessment |
|---|---|---|---|
| Klondike | Klondike Solitaire: FinitePlay | 30 / 69 / 1,554 | Short description is good. Full copy explains the solver more than the experience. Keep the Insane exception clear. |
| Spider | Spider Solitaire: FinitePlay | 28 / 67 / 1,541 | Strong short description. Replace implementation language with the benefit of a starting deal that has a solution. Keep later-position limits. |
| FreeCell | FreeCell Solitaire: FinitePlay | 30 / 66 / 1,627 | Planning is a good lead. Add the game name to the short description so it also works in isolation. Explain supermoves plainly. |
| Blackjack | Blackjack 21: FinitePlay | 24 / 75 / 1,348 | Strong short description. Full copy repeats the learning benefit and lacks a scannable feature list. Explain the fixed rules rather than implying a configurable trainer. |
| Hold'em | Texas Holdem Poker: FinitePlay | 30 / 80 / 1,758 | Clear format and opponent count. A candidate short description can emphasize explained hints. Retain no-real-money and computer-only disclosures near the start. |

Recommended short-description decisions:

Follow-up decision: the English canonical listings now include the factual “no ads” benefit alongside gameplay and offline play. Their current `listing.md` files supersede the candidates below. Google's short-description guidance does not explicitly prohibit this wording; avoid price claims and promotional slogans. This is an editorial decision, not a measured conversion result. Existing translations remain unchanged pending a separate localization request.

| Game | Recommendation | Candidate text |
|---|---|---|
| Klondike | Keep | Classic solitaire with solvable deals, draw 1 or 3, and offline hints |
| Spider | Keep | Play 1, 2 or 4 suits offline with solvable deals and optional hints |
| FreeCell | Consider replacing | Classic FreeCell with solvable deals, offline hints, undo and replay |
| Blackjack | Keep | Play blackjack offline with virtual chips and optional basic-strategy hints |
| Hold'em | Candidate to evaluate after traffic arrives | Offline poker tournaments with computer opponents and hints that explain why |

Avoid serial copy changes solely to look active. Establish a stable baseline and improve the images and access journey first. A proposed description is not an experimentally proven improvement.

For every full description, use this sequence: recognizable gameplay and main benefit; the compact family promise; game-specific features; concise limitations; balanced-play statement. The existing descriptions are well below the maximum, so the goal is clarity, not filling space.

Suggested shared promise paragraph:

> Completely free, with no ads, in-app purchases, or subscriptions. No game account, tracking, data collection or sharing. Your progress and statistics stay on your device. No online features or leaderboards.

Suggested shared balanced-play paragraph:

> Play at your own pace. Optional rest reminders let you choose when to pause. There are no urgency bonuses or timed daily rewards.

These are editing suggestions for the existing feature set, not a newly verified binary privacy declaration. Keep privacy and Data safety consistent with the release and dependencies. Local-only progress also means no cloud synchronization; explain that consequence in the website FAQ.

### Klondike

Suggested opening:

> Play classic Klondike Solitaire offline. Choose draw 1 or draw 3, pick a comfortable challenge, and use undo, replay, or an optional hint when you want another approach.

Follow with the shared promise. Replace the long certification explanation with:

> Deals from Trivial through Expert start with a verified solution. The separate Insane level contains uncertified deals. A solvable starting deal can still lead to a dead end. Hints search your current board and report when they cannot establish a winning path within their limit.

Retain the actual feature list, including automatic finish and controls. Prefer optional hints over advanced hints or board-aware winning hints: the latter terms require interpretation and can imply more certainty than intended. Do not use Insane screenshots as the main proof of certified-solvable play.

### Spider

Suggested opening:

> Build complete King-to-Ace runs in Spider Solitaire. Start with 1 suit or take on 2 or 4 suits, with undo, replay, and optional hints when you want help.

Follow with the shared promise and a plain-language explanation:

> Every included starting deal has a verified solution. Your choices still matter: a later position may no longer be winnable. Guided hints can follow a known solution; after you choose another path, finding a new solution is subject to search limits.

Keep suit choices prominent. Move the 23,675-deal count below the main benefit, or omit it to avoid unnecessary maintenance; it is not the reason most new players will understand the game. The screenshot sequence should actually show distinct suit counts and a developed run, including 4 suits if that mode is advertised.

### FreeCell

Suggested opening:

> See every card and plan your next move in classic FreeCell. Use four free cells and empty columns to make space, move card sequences, and explore different approaches with undo and replay.

Follow with the shared promise. Explain that included deals start with a verified solution, while a later board may reach a dead end and hints have search limits. Replace unexplained supermoves with moving card sequences when enough free cells and empty columns are available.

Keep the 2,000-hand count as optional supporting detail rather than the opening promise. Show an actual use of free cells or a multi-card move. The dark-theme image is useful as variety, but it should demonstrate readable play rather than almost the same initial arrangement.

### Blackjack

Suggested opening, preserving the simulated-play explanation:

> Play blackjack offline against a computer dealer, with virtual chips and optional basic-strategy hints that explain when to hit, stand, double, or split. Chips have no monetary value. There are no real-money bets, deposits, chip purchases, cash-outs, or prizes.

Follow with the shared promise and say explicitly that there are no games against real people. Replace the repeated learning paragraphs with a short feature list:

- Hit, stand, double, and split.
- One fixed six-deck ruleset, shuffled fresh each round; dealer stands on soft 17.
- 3:2 blackjack payouts, insurance, and dealer peek.
- Optional explained basic-strategy hints and local statistics.

The fixed-rule details come from `docs/games/blackjack/RULES.md`; confirm them against the release when implementing the copy. They matter because specialist trainers advertise configurable rules and counting drills. Do not imply that FinitePlay provides those features. Retain the adults-18+ statement and the limitation that hints do not guarantee wins.

### Texas Hold'em

Suggested opening:

> Play a six-seat Texas Hold'em tournament offline against five computer opponents. Use optional hints to understand a suggested action, then save your tournament and return later. Play uses virtual chips with no monetary value: no real-money bets, deposits, chip purchases, cash-outs, or prizes.

Follow with the shared promise and the explicit computer-only/no-real-people statement. Keep rising blinds, opponent styles, saved tournaments, and explained guidance as the main features. Put equity and pot odds after the everyday benefit. These are useful concepts to explain, but they are not a guarantee of advice quality or winning play.

Retain that hints use visible information and cannot see opponents' hidden cards, plus the adults-18+ statement. Avoid professional trainer, GTO, unbeatable AI, casino-profit claims, or an implication that success predicts real-money results.

## Images: improve the proof before redesigning the brand

The October 14 ordering fix has been applied: every listing now starts with gameplay and ends with settings. Do not repeat the obsolete recommendation to move settings out of first place. The remaining problem is what those images communicate.

| Game | Current issue observed | Replacement sequence to prepare |
|---|---|---|
| Klondike | The lead is a Trivial board after one move; the next two are very similar Insane starts | Developed certified deal; meaningful hint; draw-3 gameplay; theme or automatic finish; settings only if useful |
| Spider | Two captures are nearly the same two-suit opening; large empty table areas; no four-suit example | Developed 1-suit run; 2-suit planning; 4-suit board; useful hint; settings optional |
| FreeCell | Near-start boards dominate; light/dark images provide limited additional gameplay information | Developed board using free cells; useful legal sequence move; hint/undo; contrasting theme |
| Blackjack | Images 3–6 have visibly clipped UI; image 6 shows `0.3.0-dev`. First two newer captures show the complete board | Retain the complete gameplay and explained-hint shots; recapture betting, split/settlement, and settings using the actual current release |
| Hold'em | Two early green-table states and an overlaid hint; no showdown or tournament outcome | Active betting decision; explained hint; showdown/side pot; tournament outcome or actual landscape play |

Uploaded Blackjack images 3–6 are `01-betting.png`, `02-gameplay.png`, `03-hint.png`, and `04-settings.png`; their hashes match the local files. Their dimensions are 1080×1920, but valid dimensions do not make clipped content suitable. Recapture the whole visible interface rather than cropping a taller screenshot until it fits. Keep existing originals until replacements have been reviewed.

The icons have a recognizable family style and can stay. The feature graphics are mostly enlarged symbols that repeat their icons; Blackjack's is more scene-like. A later iteration could show the distinctive game arrangement or decision while preserving the palette. This is lower priority than the screenshot defects.

Functional captions to consider: Draw 1 or 3; Choose 1, 2 or 4 suits; Plan with four free cells; Hints that explain the choice; Five computer opponents. Keep the real UI dominant and text readable at store thumbnail size. Show actual play rather than fabricated controls or results. [Google's preview guidance](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en) recommends gameplay-focused assets, restrained captions, and concise gameplay video. It restricts promotional/ranking text across these surfaces; keep price claims out of the assets and short descriptions.

After fixing screenshots, make one 15–25-second gameplay clip for the flagship: show a move immediately, demonstrate one helpful feature, and return to play. A long logo introduction would delay the evidence. This is a proposed creative brief, not a required video duration or a guarantee of featuring.

## Practical marketing plan

### First: recruit a small, useful test audience

Choose one solitaire as the flagship for outreach, while keeping all five accessible from the site. Klondike is my starting recommendation because the familiar draw-1/draw-3 format is easy to demonstrate; this is an editorial choice, not a measured demand ranking. Spider is a strong alternative if early testers respond better to suit variety and known-solution starts.

Suggested research target: 15–25 relevant Android testers across the solitaires, with a few adult testers for the two simulated table games. This is a manageable interview cohort, not a Google minimum, experiment sample size, or statistical claim. Ask each person to install through the actual access journey, play normally, and report:

1. Was access or installation confusing?
2. Could you read the cards and understand the controls?
3. Did a hint help, and did its limitation make sense?
4. What would make you keep this game instead of your current one?
5. What did the site or listing lead you to expect that the app did not deliver?

Invite honest feedback without requiring positive reviews or repeated use. Obtain permission before quoting a tester on the website. Do not add review prompts that create pressure or reward positive ratings.

### Acquisition channels, in priority order

| Channel | Concrete action | Reason and measurement |
|---|---|---|
| Direct referrals and relevant communities | Prepare a short developer introduction, one gameplay clip, and one game-specific link. Check each community's current promotion rules before posting. | Reach people who can explain why they would switch. Track successful tester enrollment and feedback, not likes. |
| Searchable game pages | Publish useful pages for Klondike, Spider, FreeCell, Blackjack, and Hold'em, each with unique gameplay evidence and honest availability. | Gives each game a focused destination and answers installation questions. Measure aggregate search impressions and visits when available. |
| Android/privacy app editors | Prepare a small press kit: what the game does, three clear images, a clip, the promise, availability, support, and factual release details. Pitch after public installation is possible, or explicitly request an early-access review. | A credible independent demonstration is more useful than another mission paragraph. No placement or reach is assumed. |
| Useful guides and short demonstrations | Explain a real FreeCell sequence move, Spider suit choice, or Blackjack hint using the app. | Attract people interested in the gameplay. Avoid thin search-keyword pages and health/profit claims. |
| Voluntary portfolio discovery | Give players an unobtrusive way to discover the other games, such as the existing company website link or an About section. | Builds on trust without interrupting play. Treat any new in-app feature as separate product work. |
| Paid acquisition | Defer until public access, acceptable product quality, and a working measurement baseline exist. | The apps have no ad or purchase revenue, so paid installs are a funded mission expense. Set a subsidy budget rather than assume acquisition will repay itself. |

No posts, pitches, mailing-list subscriptions, advertising campaigns, or spending were performed during this review.

Do not market all five games equally on day one. A broad company story and five simultaneous campaigns dilute a small effort. Use the flagship to learn which promise people understand, then reuse only the parts that fit each game's audience. Keep Blackjack/Hold'em communications adult-focused and separate from any solitaire family messaging.

### Suggested 30-day sequence

| Timing | Work | Completion evidence |
|---|---|---|
| Days 1–3 | Clarify tester access; bring games forward on mobile; add Hold'em; prepare replacement Blackjack captures | An eligible tester can follow the instructions; all five games are represented; assets have complete controls and match the release |
| Days 4–10 | Recruit the small cohort; observe access, readability, hints, and expectation gaps | Written observations from real testers and a prioritized issue list; no invented conversion percentages |
| Days 11–20 | Prepare game pages and a flagship demo/press kit; fix the most consequential issues | One clear destination per game and a short factual demonstration |
| Days 21–30 | If release readiness permits, launch the flagship publicly and conduct a small, focused outreach round; otherwise continue the closed test | Verified public installability or clearly labeled testing status; source-specific traffic and actual installation figures |

The dates are a suggested work sequence, not a promise that Google review or product readiness will finish within 30 days.

## Measurement and experiments

Use privacy-compatible, aggregate Play reporting without introducing tracking SDKs into these games. Do not infer successful installs from website link clicks. During the closed test, manually count confirmed enrollment/installation with volunteers rather than assuming the public listing metrics cover the whole journey.

Google changed its store-listing reporting in 2026: listing conversion analysis now emphasizes visitors, unique action clicks, and click-through rate. Successful acquisitions are available separately in Grow users, Statistics, and reports. Compare the same metric definition, traffic source, language, and acquisition state across periods. [Current reporting definitions](https://support.google.com/googleplay/android-developer/answer/9859173?hl=en) matter when reading older reports.

Track weekly by game and source:

- Listing visitors and unique Install clicks; calculate the corresponding click-through rate.
- Actual user/device acquisitions separately; use cost per actual acquisition for any future paid pilot.
- Available aggregate installed-audience, loss/retention, and stability signals; suppress or qualify small samples.
- Tester feedback on readability, control errors, hint usefulness, and unwanted pressure.
- Support effort and whether people say the game meets the promise. Longer sessions are not automatically better for this mission.

If adding campaign labels, use source-level UTM values without user identifiers. Do not assume they provide complete attribution, and do not introduce a data-collection scheme that conflicts with the published promise.

Yesterday's implementation record describes extremely sparse Spider traffic and an unlaunched description experiment. Those figures are historical and were not refreshed here. Do not extrapolate them to all five games or treat them as a current traffic report. Start by checking today's baseline.

Once eligible traffic is sufficient, run one experiment at a time on one game: first a distinct gameplay screenshot, then a short-description benefit. Keep the game's actual capabilities and disclosures constant. Use the sample-size and uncertainty guidance in the experiment rather than inventing a universal traffic threshold. [Google's experiment documentation](https://support.google.com/googleplay/android-developer/answer/12053285?hl=en) describes the configuration. Allow a full weekly cycle as a planning minimum and continue longer when uncertainty remains; seven days alone does not establish a winner.

Later, [custom listings](https://support.google.com/googleplay/android-developer/answer/9867158?hl=en) can tailor a destination to a distinct audience or search intent. They are an optimization for real traffic, not a replacement for obtaining it. Existing Spanish/German listing text is already present; prioritize any further translation or localized captures using demand and review capacity.

## Recommended order of investment

1. Make eligibility and installation clear, and keep launch claims aligned with release tracks.
2. Put the games and real gameplay near the top of the homepage; include Hold'em.
3. Replace clipped/development Blackjack captures and repetitive solitaire starts.
4. Tighten explanatory copy while preserving the existing names, promise, and limitations.
5. Learn from a small relevant audience, then launch one clear acquisition campaign when installability is ready.
6. Expand localization, video, creative experiments, and paid reach only when actual traffic and feedback justify them.

The current listings are substantially better than the pre-October-14 versions. The next useful step is a clearer product demonstration and access journey, supported by specific player feedback. Another wholesale title rewrite is a lower-value use of effort.
