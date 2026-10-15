# Google Play listings and publishing

Applies to all game listing packages here. The repository root also points agents here
before Play Console work, including work that does not edit these files.

## Start from evidence

- Read this directory's `README.md`, the game's design/rules/acceptance docs, and at least
  two existing `listing.md` files. Hold'em provides the virtual-chip pattern; the solitaire
  listings provide the general offline/privacy pattern. Blackjack's approved Console copy
  may precede its local listing package: inspect the current Console rather than inventing
  a missing local source.
- Verify claims against the release build, manifest, dependencies, and implemented features.
  Existing listings are examples, not proof. Never copy another game's deal counts,
  certification, hints, opponent counts, payouts, Undo/Replay, or language counts blindly.
- Keep local listing text synchronized with approved Console edits. Record whether a change
  is only local, saved in Console, submitted for review, approved, or serving to testers.
- Canonical English copy is in each `listing.md`; translations are in `localizations.json`.
  Run `ruby tools/publishing/listings.rb export` after changes to synchronize generated GPP
  metadata. Follow `screenshot-order.txt`, not filename sorting. Preserve existing assets
  unless removal is explicitly in scope. Do not mistake exported text for a Console save.

## Family copy pattern

- Title: recognizable game name followed by FinitePlay, within the current Play limit.
  The approved marketing pattern is `<Game> Solitaire: FinitePlay` for the solitaires,
  `Blackjack 21: FinitePlay`, and `Texas Holdem Poker: FinitePlay`. Keep it a clear game name;
  put offline/virtual-chip explanations in descriptions instead of promotional title text.
- Short description: one accurate, distinctive summary within the current limit.
- Put the family promises near the start of the full description, when verified: no ads,
  no in-app purchases/subscriptions, offline play, no account or tracking, and progress and
  statistics kept on the device. Follow with the game's actual mechanics and features.
- For simulated casino games, explicitly say virtual chips with no monetary value,
  no real-money betting/deposits/cash-outs/prizes, no online play, and no matches against
  real people. Say "computer dealer" for Blackjack and "computer opponents" for Hold'em.
  Do not imply cloud AI, multiplayer, purchasable chips, or redeemable winnings.
- Suggested opening for future virtual-chip games (adapt only verified claims):

  > Offline [game] with virtual chips and [computer dealer/opponents] only.
  > No ads. No in-app purchases. No real-money betting, deposits, cash-outs, or prizes.
  > No online play or matches against real people.

- Preserve game-specific limitations. Do not promise every deal is solvable unless the
  shipped catalog proves it; do not call a search timeout proof of unsolvability.
- State the family promise explicitly: completely free, no ads/purchases/subscriptions,
  no tracking/data collection/sharing, and completely local game data. No leaderboards or
  online features. Keep this near the start of the full description; short descriptions
  should communicate gameplay and avoid the observed price/promotion warnings.
- Explain the goal of balanced play through configurable rest reminders and the absence
  of urgency bonuses or timed daily rewards. Do not imply medical benefits or zero game
  scores: local scores/statistics exist. No competitive reward pressure is the promise.
- For solitaire, distinguish solvable starting deals from all reachable positions and
  bounded hints. Mention Klondike's uncertified Insane exception; do not highlight FreeCell
  difficulty levels. For Blackjack and Hold'em, highlight explanatory beginner-friendly
  hints and never-real-money play without promising wins.

## Settings baseline and exceptions

These are the user's current family choices, not universal legal requirements. Reconfirm
material departures and inspect current policy before making legal/policy declarations.

- Game -> Card, where accurate; support email `contact@finiteplay.org`, website
  `https://finiteplay.org`, privacy policy `https://finiteplay.org/privacy`.
- Blackjack and Hold'em: 18 and over only, with Google's minor-access restriction enabled.
  Do not automatically impose adults-only targeting on solitaire or unrelated future games.
- Answer IARC from playable content: simulated playable casino gambling is not merely a
  gambling theme, even when entirely offline and virtual. Do not declare real-money gambling
  without real-money stakes/value. Report regional ratings as returned; do not choose them
  manually or equate the audience setting with every region's rating.
- Verify no ads, advertising ID, data collection/sharing, government affiliation, financial
  features, or health features independently. Offline architecture alone is not sufficient
  evidence for every declaration; inspect SDKs and permissions. Do not claim security badges.
- Current agreed Alpha exclusions for Blackjack/Hold'em: Belarus, Cuba, Iran, Russia, and
  Rest of World. North Korea has no separate selector in the observed Console; excluding
  Rest of World excludes other unlisted locations too. This broader exclusion was explicitly
  approved. Recheck the live country list; do not present this as precise physical geoblocking.
- Distinguish developer targeting from Play restrictions. South Korea was unavailable pending
  GRAC review; other region restrictions may also apply. Never bypass or promise removal of them.
- Use the existing `FinitePlay Closed Testers` list for these Alpha tracks when authorized.
  Do not invent tester emails or silently broaden access for future apps.

## Assets and release checks

- Follow the established vector/icon family and editable SVG masters. Prefer actual release
  screenshots; never show mockups as gameplay evidence. Read current Play asset requirements,
  verify dimensions/file sizes/opacity, and ensure the first screenshot shows gameplay.
- Inspect asset provenance and the current asset-specific AI declaration. Do not label every
  asset merely because an agent worked on the task, or assert labels are never required for
  SVG/code-rendered art. The user prefers no labels unless required; if applicability is unclear,
  explain the uncertainty rather than making up a legal rule. Real app captures are distinct
  from generated promotional illustrations.
- Prefer Gradle, Fastlane, or Android Publisher API when supported; use Console for settings
  unavailable through those interfaces. Never reveal or commit publishing credentials.
- Verify package, signing identity, branch, integer versionCode, and release artifact. Run the
  game's required gates. Instrumented tests run on an emulator, not the user's physical phone.
- Specify `--track alpha` explicitly: the checked-in publisher default is internal. A new
  draft app may require `--release-status draft`; successful upload is not a playable release.
- Inspect release errors/warnings and Publishing overview before submission. Explain regional
  restrictions and missing diagnostic artifacts; do not hide warnings or overstate readiness.
- Obtain required approval at the actual save/publish/review/access-changing step, describing
  the destination and exact changes. Never treat a request to inspect as permission to mutate.
- Report upload, draft, review submission, automated checks, approval, and tester availability
  separately. Confirm the actual serving status and tester opt-in link before saying "live".
  Preserve unrelated WIP and do not commit or push without authorization.
