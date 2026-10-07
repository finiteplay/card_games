# Blackjack — TODO

Deferred, out of first-release scope (`CLAUDE.md` "Working agreements": nothing from here during
the first release's execution).

## Planned, in `EXECUTION_PLAN.md`

- **Card motion, the rest** (B7): the hole card's turn as a flip animation rather than a step.
- **The hardware budgets** (B8): frame deadline, idle CPU and cold-start p95 are open until measured
  on a physical device (`ACCEPTANCE.md`).

## Not planned

- **Table variants** — other dealer rules, surrender, fewer decks, a picker. One fixed rule set is
  the design (`RULES.md` "Table Rules").
- **Side bets, a table-minimum picker, a maximum above 500.**
- **Per-session statistics** and a hand history. The statistics are lifetime and one pool.
- **Insurance advice beyond "always decline"** and any counting aid: a fresh shoe every round leaves
  nothing to count.
