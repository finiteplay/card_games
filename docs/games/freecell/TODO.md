# FreeCell — Deferred

Publisher: [FinitePlay LLC](https://finiteplay.org)

Out of scope for the first release, recorded so each is a decision rather than an omission.
Nothing here starts before `EXECUTION_PLAN.md`'s packages are delivered — that file's Status
table says which are.

- **Free cell count as a difficulty setting.** Three or two free cells make a meaningfully harder
  game on the same deal; four is the base game this release ships (`DESIGN.md`, `RULES.md` "Free
  cells and difficulty"). Worth adding only once the base game is proven, the same order
  Klondike's difficulty ladder followed its own MVP.
- **Foundation withdrawal.** Deliberately omitted (`DESIGN.md` "What FreeCell is not") on the
  reasoning that free cells and empty columns already substitute for it. Revisit only with
  evidence from play that a real position needs it.
- **An explicit single-card-or-whole-run choice for moves into an empty column.** Some
  implementations ask, since an empty destination makes both legal. Here drag already expresses
  the choice by where the run is picked up, and tap takes the longest legal run. Worth adding only
  if play shows the tap choice surprises players.
- **Difficulty grading.** Deals vary in difficulty even though nearly all are winnable — high
  solvability says a win exists, not how hard it is to find. FreeCell has no built-in axis like
  Spider's suit count, so a grade would have to be measured the way Klondike's is. The first
  release ships ungraded.
- **A stronger solver for the resistant remainder.** The plain DFS (`DEALS.md` "The solver")
  solves roughly 70% of candidates at its default budget and a much larger budget does not
  reliably rescue the rest — evidence of a structural search limitation, not merely insufficient
  time. Generation works around this by skipping what does not solve, so it is not a blocker; worth
  revisiting only if that skip rate turns out to make reaching the catalog's target seed count
  impractically slow.
- **Frame-rate budget confirmation on physical hardware.** F7 measured every other
  `docs/PLATFORM.md` "Performance" budget passing on an emulator, but frame-rate compliance
  during repeated drag came back at only ~21% of frames meeting the 60 Hz deadline against the
  ≥95% budget — almost certainly the emulator's own software-rendered compositing overhead
  (`docs/games/freecell/EXECUTION_PLAN.md` F7), the same reason Klondike's own acceptance
  protocol reserves this exact measurement for a physical device. This is a gap against F7's own
  gate, not a decision — no environment with a physical device was available to close it. Track
  here until re-measured on real hardware, then remove this entry (and update F7's status row to
  match, whichever way it comes back).
- **A standalone `ACCEPTANCE.md`.** Klondike has one; Spider does not, relying instead on each
  `EXECUTION_PLAN.md` package's own gate as its acceptance evidence (`AGENTS.md` "Gates are not
  self-checks"). FreeCell follows Spider's precedent for now; revisit if a release-specific
  evidence checklist (a device matrix, archived measurements) turns out to need a home of its
  own.
- **Landscape layout.** `UI_SPEC.md` "Landscape" specifies a full-width status row, two action
  rails, and vertical free-cell/foundation strips flanking the tableau — none of it built. F2
  shipped one adaptive layout in every orientation instead, closer to Spider's own first pass
  than to the twin split this game's own spec calls for; a real device rotation does not lose the
  game (the view model survives it), it simply never draws what the spec describes. This is a
  gap against `UI_SPEC.md`, not a decision — track it here only until a package actually closes
  it, then remove this entry.
- **`PUBLIC_STRATEGY_RESEARCH.md`.** Klondike's and Spider's both collect background strategy
  research as non-contractual material for their Help screens. FreeCell has none yet; worth
  writing once the Help screen's content is drafted.

## Localization — Native-Speaker Review

Deferred the same way Klondike's and Spider's own translations are
(`docs/games/klondike/TODO.md`, `docs/games/spider/TODO.md`): every FreeCell string will be
machine-generated across the platform's locales and structurally complete, but not
native-speaker reviewed, until a dedicated review pass happens.

## iOS

Deferred the same way for every game in this repo (`docs/games/klondike/TODO.md` "iOS").
