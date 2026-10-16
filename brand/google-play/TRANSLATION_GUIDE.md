# Google Play listing localization guide

Use this guide when preparing a non-English Google Play listing for a FinitePlay card game. The reviewed English listing in that game's `listing.md` is the copy source. The game specifications and this guide are the factual and terminology constraints.

## Workflow

1. Read the current English `listing.md`, this guide, the game-specific design and rules, and the relevant localization entry in `localizations.json`. Check release-specific features before translating a claim that could have changed.
2. Draft in the target language for native readers. Preserve the original meaning and tone; do not translate word-for-word when that sounds unnatural. Keep the game's name recognizable and use the terms players in that locale actually search for, without keyword stuffing or unsupported claims.
3. Have a native speaker familiar with card games review the title, short description, full description, terminology, and content disclosures. Review the rendered listing and current app screens in that language where available. Record unresolved terminology or missing UI support instead of guessing.
4. Check Play's current title, short-description, and full-description character limits (currently 30, 80, and 4,000 characters). Count characters using the actual target-language text, including spaces and punctuation.
5. Update only the requested language fields in `localizations.json`, then export the intended listing. The standard exporter exports every configured locale; confirm with `git status` and `git diff` that locales not in scope remain byte-for-byte unchanged. Exported files are local metadata, not a Console save.
6. Record the translator/reviewer, date, locale, sources checked, and any feature terminology decisions with the listing review. Keep Console save, review submission, approval, and availability as separate statuses.

## Meaning and claims to preserve

- Keep the family promise clear and near the start: free to download and use; no ads, in-app purchases, or subscriptions; no game account, tracking, data collection, or sharing; progress and statistics remain on the device; no online features or leaderboards.
- Preserve offline play. Do not imply cloud saves, multiplayer, online opponents, rankings, or social features.
- For Klondike, distinguish the certified Trivial-to-Expert deals from the separate uncertified Insane level. A verified-solvable starting deal does not mean every later board is winnable. Hints are bounded searches; a search that cannot prove a path is not proof that no path exists.
- For Spider and FreeCell, say included starting deals have verified solutions, while player choices can lead to a dead end. Preserve the stated hint/search limitations. Do not suggest all possible deals are certified.
- For Blackjack, say it is against a computer dealer and uses virtual chips with no monetary value. State there are no real-money bets, deposits, chip purchases, cash-outs, prizes, online play, or matches against real people. Preserve the 18+ audience. Do not imply configurable rules, card counting, or guaranteed wins.
- For Texas Hold'em, say it is a six-seat tournament against five computer opponents, with virtual chips only and no monetary value. Preserve the no-real-money, no-prizes, offline/computer-only, and 18+ statements. Hints use visible information and cannot see opponents' hidden cards; do not promise wins, professional-grade advice, or real-world results.
- Keep rest reminders optional and describe them as a player-controlled pause choice. Do not make medical claims or promise that a game is addiction-proof.
- Numbers, modes, rules, and feature claims must match the current English source and the shipped game. Do not transfer a count or feature from one game to another.

## Wording and store policy

- Use natural local terms for Solitaire/Klondike, Spider, FreeCell, Blackjack, Texas Hold'em, suits, deals, hands, hints, undo, replay, and virtual chips. Keep brand and product names recognizable; document a deliberate local naming convention.
- Prefer plain player benefits over implementation terminology. Explain specialized mechanics in familiar words when useful; do not invent a local equivalent that changes the rule.
- Keep store metadata factual. Do not add “best,” ranking or award claims, competitor names, keyword lists, urgency, prices or promotional claims, guarantees, or claims about an unverified audience or market.
- Preserve the factual “no ads” benefit from the current English short descriptions. Use natural local wording alongside gameplay and offline play; do not turn it into a promotional slogan or add “free” to the short description. Keep the result within 80 characters.
- Translate “simple” as an uncluttered, straightforward experience, without suggesting shallow or inferior gameplay. Avoid “simplistic.” The current English short descriptions use “battery-conscious” based on a 60-second foreground-idle CPU sample on a Pixel 9 Pro XL. This was not a battery-drain or active-play test: translate the qualified design idea naturally, but do not strengthen it into longer battery life, measured power savings, numerical claims, or “battery-optimized.”
- Keep gambling disclosures explicit in the target language. Do not translate virtual chips as money, prizes, redeemable winnings, or purchasable currency.
- Preserve age wording and rating declarations according to the current listing and applicable Play Console settings. A translated description does not itself change audience targeting or regional ratings.

## Scope and review

Do not translate or rewrite other locales as part of an English listing change. Do not machine-translate a release listing without native review. If a locale's app interface does not support a claim's language, flag that mismatch and remove or qualify the claim for that locale rather than implying a translated experience. Confirm every localized string against the corresponding English source and verify character limits before Console submission.
