# ADR 0045: Strategy gate runner

- Status: accepted
- Date: 2026-11-28

## Context
ADR 0042 defines the six pass rules, and ADR 0043 defines the first strategy. Nothing yet runs a
strategy's grid over the history and reports the gate. Without that, no strategy can be shown to
pass, and stock selection stays empty by design (ADR 0044).

## Decision
- `GateRunService` (backtest) runs a strategy's grid, read-only and computed on each call:
  1. Every distinct signal-generating setting is replayed once over the full history. A share is
     evaluated on the window ending each session, so there is no look-ahead (`StrategyReplay`).
     Shares go through the screener's guards, as in selection.
  2. The best 15 signals per session are kept (3 x positions, as in the portfolio simulation).
  3. Every grid setting is simulated on `PlanPortfolioSimulator` with the spec-default portfolio:
     INR 10 lakh, 5 positions, 0.75% risk, 20% maximum position, 5 bps slippage, Zerodha delivery
     costs. The holding limit is a plan detail, so settings that differ only in it reuse one replay.
  4. `GateEvaluator` applies the six rules, with the in-sample window ending 31 December 2022 and
     the out-of-sample window from 2023 (both configurable in the request).
- The grid is declared in `GateGrids` before any result is seen. Momentum breakout: resistance
  lookback 20 or 30, minimum volume ratio 1.5 or 2.0, holding limit 10 or 15 sessions: 8 settings,
  within the plan's limit of 18. The spec default (20, 1.5, 15) is in the grid.
- Any share whose replay fails stops the whole run, since a silently dropped share would bias the
  result.
- Endpoint `GET /dev/strategies/{id}/gate`, dev profile only.

## Consequences
- The gate can be run on real data. Its output includes each check's value, the chosen setting, the
  out-of-sample and walk-forward metrics, and the caveats (survivorship, unadjusted prices, daily
  order).
- The run is slow: it replays every share on every session, once per signal set. It runs in the
  request, so the call can take minutes. Storing the result (P2 follow-up) would avoid repeat runs.
- The chosen setting uses the in-sample window, but the signals come from the spec-default portfolio
  settings, so the grid compares settings under one portfolio, not each setting's own best portfolio.
- Not built: storing gate results per strategy version, the UI (P5), and a version registry. A
  change of rule or grid is a new gate run, and its version must be recorded (ADR 0042).
