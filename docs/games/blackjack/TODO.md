# Blackjack — TODO

Deferred, out of first-release scope (`CLAUDE.md` "Working agreements": nothing from here during
the first release's execution).

## Planned, in `EXECUTION_PLAN.md`

- **Hint** (B6): the basic-strategy table, generated offline after the rules freeze. The action bar
  and `UI_SPEC.md` already account for the sixth button.
- **Card motion** (B7): flights from the shoe, the hole card's flip as an animation, the deal
  animated card by card.
- **Instrumented tests and release evidence** (B5, B8): they need an emulator and, for some budgets,
  physical hardware.

## Not planned

- **Rest reminder.** The other games have a break reminder; Blackjack's first release does not
  (`DESIGN.md`'s settings list does not include it). A round is under a minute, which makes the
  continuous-play total a different question here than in a solitaire.
- **Table variants** — other dealer rules, surrender, fewer decks, a picker. One fixed rule set is
  the design (`RULES.md` "Table Rules").
- **Side bets, a table-minimum picker, a maximum above 500.**
- **Per-session statistics** and a hand history. The statistics are lifetime and one pool.
- **Insurance advice beyond "always decline"** and any counting aid: a fresh shoe every round leaves
  nothing to count.
