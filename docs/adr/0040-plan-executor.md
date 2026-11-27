# ADR 0040: Plan executor, the shared exit rules for a trade plan

- Status: accepted
- Date: 2026-11-28

## Context
ADR 0038 defines a `TradePlan` (entry, stop, targets, trailing rule, invalidation, holding limit)
as data. Nothing yet executes one. Without an executor, the backtest and paper trading would
each implement exits and could disagree, and the gate (plan section 3) would measure a rule that
the paper track does not follow.

## Decision
- `PlanExecutor.execute(plan, bars, signalIndex)` is pure. It takes daily bars and the index of the
  signal session, and returns the status (NOT_FILLED, OPEN, CLOSED), the entry, each exit with its
  reason and fraction, the sessions held and the realised R.
- Entry: BUY_STOP fills at the open if the open is at or above the trigger, otherwise at the
  trigger if the high reaches it, within `validSessions`. A fill above the no-chase limit is not
  taken. NEXT_OPEN fills at the next open only.
- Each later session, in order: invalidation flagged on the previous close exits at the open;
  a gap through the stop exits at the open; a gap through a target sells that fraction at the open;
  an intraday low through the stop exits at the stop price; intraday highs sell targets at their
  price. The stop is checked before targets (pessimistic, since intraday order is unknown).
- After the close: the invalidation level is tested; the holding limit exits at the close; the
  trailing stop is raised, never lowered, from bars up to and including that close, so it applies
  from the next session.
- Targets are `trigger + R x (trigger - stop)` from the plan, also when the fill was above the
  trigger, so every strategy in the gate is measured the same way.
- The executor reads no bar after the one it is processing except that bar's own close. A test
  checks this for the trailing stop.

## Consequences
- One definition of the exit rules, used by the backtest and, later, by paper trading.
- Realised R is per share, before costs, slippage and sizing. Those belong to the portfolio layer.
- Not yet covered: a portfolio simulation that sizes and holds several plans at once (the existing
  `PortfolioSimulator` still runs the old screen exits, unchanged, so account 1 is unaffected); the
  walk-forward runner; the gate report. Those are the next PRs in slice 4.
- The ATR trailing rule is implemented but has no unit test yet; the EMA rule and every other exit
  path do.
- Intraday order is unknowable from daily bars. The stop-first rule is conservative; the gate
  should report how many trades touched both (not yet built).
