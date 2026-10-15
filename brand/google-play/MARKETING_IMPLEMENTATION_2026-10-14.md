# Store listing marketing implementation — 2026-10-14

## Scope and verified outcome

Updated the existing Google Play listings for all five games. No bundle upload, release-track
promotion, audience, country, rating, pricing, or tester changes were made in this marketing work.
Listing publication is not evidence of a production app release.

| Game | English title | Phone screenshots | Latest observed publishing state |
| --- | --- | --- | --- |
| Klondike | Klondike Solitaire: FinitePlay | 4: certified gameplay first, existing gameplay, settings last | Earlier text/localizations published Oct 14; final screenshot change in review/quick checks |
| Spider | Spider Solitaire: FinitePlay | 4: in-progress gameplay first, hint/gameplay, existing gameplay, settings last | Earlier text/localizations published Oct 14; final screenshot change in review/quick checks |
| FreeCell | FreeCell Solitaire: FinitePlay | 4: in-progress gameplay first, dark gameplay, original gameplay, settings last | Last published Oct 14; no pending changes displayed |
| Blackjack | Blackjack 21: FinitePlay | 6: complete hand first, explained hint, existing betting/gameplay/hint, settings last | Last published Oct 14; no pending changes displayed |
| Hold'em | Texas Holdem Poker: FinitePlay | 4: post-flop gameplay first, explained hint, original gameplay, settings last | Six listing changes submitted; Changes in review, automated checks still running |

These are point-in-time Console observations, not promises about later approval or serving.
Hold'em was saved/submitted through Console because the existing publishing service account
returned 403. The other four games were saved using Android Publisher edits; Google required
automatic review submission for those edits. No account permissions were expanded.

## Copy and claim decisions

- Gameplay-focused short descriptions replace the price/promotional language that generated
  warnings. Full descriptions prominently state the FinitePlay promise: completely free,
  no ads, purchases, subscriptions, accounts, tracking, data collection/sharing, online
  features, or leaderboards; progress/statistics remain private and local.
- Blackjack/Hold'em explicitly use virtual chips without monetary value: no real-money bets,
  deposits, chip purchases, cash-outs, prizes, or games against real people. Explanatory hints
  teach choices but do not guarantee wins or see hidden cards.
- Solitaire copy preserves solvable-starting-deal and bounded-hint distinctions. Klondike's
  uncertified Insane exception remains explicit. FreeCell difficulty is not a marketing focus.
- Balanced play is expressed through configurable rest reminders and no urgency bonuses or
  timed daily rewards. Local scores/statistics exist, so copy does not falsely promise "no
  points" or make medical/health-benefit claims.
- No new AI-generated art was introduced. New assets are actual release-app emulator captures.
  Existing icon/feature graphics were retained without adding AI labels, consistent with the
  user's preference not to label unless required.

## Localization pilot

English (US), Spanish (Spain), and German text is saved for all five games. Spanish/German are
an initial pilot, not languages selected by a statistically meaningful demand ranking. The
available acquisition data was too sparse to claim such a ranking. Use acquisition-by-language
data after real traffic arrives before expanding translations or custom country listings.
Translations retain game limitations and the family promise; actual screenshots remain English
and inherit from the default listing where no localized graphics were added.

## Experiment preparation, not a launched test

Saved a Spider English short-description experiment draft:

- Name: `Balanced play vs hints - en-US - Oct 14`
- Experiment ID: `7401135808245564678`
- One variant; default experiment audience allocation 50%.
- Control: `Play 1, 2 or 4 suits offline with solvable deals and optional hints`
- Variant: `Offline Spider with solvable deals and gentle reminders to take a break`
- Target: unique user install clicks. Only the short description changes.
- Console showed an estimate of 47,071 unique user install clicks needed with default settings,
  and no completion-time estimate. This estimate is not a required universal sample size.
- The inspected Spider acquisition view covered Sep 13–Oct 10, 2026: one visitor, zero unique
  user install clicks. This historical range does not establish current traffic for all games.
- The draft is not running. Do not manufacture a winner or launch five low-traffic tests.
  After sufficient eligible traffic and a stable listing baseline, run this pilot for at least
  a full weekly cycle, wait for meaningful uncertainty bounds, and extend the winning *tested*
  message to other games only with game-specific validation. No conversion uplift is claimed.

Evidence: `spider/experiment-draft-2026-10-14.png`,
`holdem/review-submitted-2026-10-14.png`, `blackjack/publishing-2026-10-14.png`.

## Local source and verification

- Canonical English: each game's `listing.md`; Spanish/German: `localizations.json`.
- Explicit screenshot sequence: each game's `screenshot-order.txt`.
- Generated GPP text: `games/<game>/app/src/main/play/listings/<locale>/`.
- `ruby tools/publishing/listings.rb export` validated all 15 text packages against
  30-character title, 80-character short, and 4,000-character full-description limits.
- API readback confirmed three locales, new English titles, screenshot counts, and matching
  leading-image hashes for Blackjack, Klondike, Spider, and FreeCell. Hold'em's review screen
  confirmed its new title/copy and ordered screenshots; Publishing overview confirmed both
  new languages and all six listing changes in review.
- Signed release APKs for Blackjack, Hold'em, Klondike, and Spider built successfully and ran
  on the explicitly selected temporary emulator. New captures are 1080×1920 actual screens.
  This was screenshot verification, not a full regression/performance acceptance campaign.
- Ruby syntax and `git diff --check` passed. No commit or push was made. Existing unrelated WIP
  was preserved. Temporary emulator display override was reset and that emulator was stopped.

## Policy references checked during the review

- Listing assets and metadata: https://support.google.com/googleplay/android-developer/answer/9866151
- Experiments: https://support.google.com/googleplay/android-developer/answer/12053285
- Experiment overview: https://google.play/business/store-listing-experiments/

Approval and promotion eligibility remain Google's decisions. Screenshot counts/dimensions
alone do not guarantee merchandising placement.
