# ADR 0023: Regime filter, parameter sweep and out-of-sample check

- Status: accepted
- Date: 2026-11-26

## Context
The first portfolio simulation (ADR 0022) used conventional defaults. Choosing parameters on the
same history that judges them overfits. We need a way to pick settings and then test them on years
the choice never saw.

## Decision
- Regime filter (optional): new entries only when Nifty 50 closes above its 200-session average on
  the signal session. With too little benchmark history it allows nothing (fails closed).
- `PortfolioSimulationService.prepare` builds the parameter-independent part once (signals for
  every session, best 30 per day, and compact prices); the simulator, which takes seconds, then runs
  each combination.
- `StrategySweepService` runs a fixed grid of 18 combinations: stop 1.5, 2 or 3 x ATR(14); holding
  limit 20, 60 or 120 sessions; regime filter off or on. Other settings stay at the ADR 0022
  defaults with costs on. Each combination runs on the in-sample period and, separately with fresh
  capital, on the out-of-sample period.
- The combination with the best in-sample Sharpe is reported as chosen, next to its out-of-sample
  result; every combination's out-of-sample result and the count that beat Nifty out of sample are
  listed, so overfitting is visible.

## Consequences
A screen is judged by whether settings chosen on the past held up afterwards, and by how many
settings work at all (robustness), not by its best result. The grid is small on purpose; a larger
grid raises the chance of a lucky winner. Survivorship bias and unadjusted prices remain.
