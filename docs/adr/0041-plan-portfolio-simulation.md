# ADR 0041: Portfolio simulation of trade plans

- Status: accepted
- Date: 2026-11-28

## Context
ADR 0040 executes one plan on one share. The gate (plan section 3) judges a strategy on a
portfolio: slots, cash, risk per trade and costs. The existing `PortfolioSimulator` runs screen
exits only and must stay unchanged, because account 1 depends on it.

## Decision
- `PlanPortfolioSimulator` (package-private, backtest) runs in two passes, like
  `PortfolioSimulationService`:
  1. Each signal is executed with `PlanExecutor` on its own. Signals that never fill are counted as
     rejected.
  2. Day by day: entries first (best score first) subject to a slot limit, cash and a size; then
     the session's exits; then mark to the close. Entries before exits lets a same-day stop book.
- Size: the smaller of equity x risk per trade / planned R, and equity x maximum position / price,
  reduced until cash covers the price plus the buy charges (`CostModel`). Whole shares.
- Fills: entry at the executor's price plus slippage against us; exits at the executor's price less
  slippage. Sell charges come from `CostModel` on each sale. Partial exits sell `floor(quantity x
  fraction)`; the last exit of a closed trade sells what remains.
- Output: closed trades with net P&L after costs, a daily equity curve, rejected count, positions
  still open at the end (marked at their last close), total costs and final equity.

## Consequences
- Plans can be measured on a portfolio, with costs, before any gate report exists.
- Limits, stated and not yet modelled: a signal rejected for a slot or cash is not retried;
  no liquidity cap or partial fill; the executor's prices are the only prices, so a gap through a
  stop is booked at the open, not at the stop.
- Not wired to any endpoint. The walk-forward runner, the gate report and the metrics (win rate,
  profit factor, drawdown, CAGR) are the next PR in slice 4.
- The existing `PortfolioSimulator` and account 1 are unchanged.
