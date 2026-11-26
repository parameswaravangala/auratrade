# ADR 0022: Portfolio-level simulation of screens

- Status: accepted
- Date: 2026-11-26

## Context
ADR 0021 measured forward returns of screen matches. Whether an account could have made money
depends on more: how many positions, how big, when to exit, and what it costs.

## Decision
- `PortfolioSimulator` (pure) runs one session at a time. Exits first: a one-day gap over 40%
  versus the previous close (likely an unadjusted corporate action) closes at that close; an open
  at or below the stop closes at the open; a low at or below the stop closes at the stop; reaching
  the holding limit closes at the close. Then entries from the previous session's signals, best
  score first, at the open plus slippage. Then mark to market.
- Sizing: whole shares, the smaller of (equity x risk per trade) / (entry - stop) and
  (equity x maximum position) / entry, limited by cash with a 1% reserve for charges. Stop =
  entry - ATR multiple x ATR(14) at the signal session.
- Defaults (conventional, not optimised): INR 10 lakh, 10 positions, 1% risk per trade, 20%
  maximum position, stop 2 x ATR(14), 60-session holding limit, 5 bps slippage.
- `CostModel`: NSE equity delivery charges from Zerodha's published schedule, read 2026-11-26
  (brokerage 0, STT 0.1% buy and sell, transaction 0.00307%, SEBI INR 10 per crore, stamp 0.015%
  on buys, GST 18% on brokerage, transaction and SEBI, DP INR 15.34 per sell). A broker's
  schedule, not checked against NSE, SEBI or the Stamp Act; every rate is a parameter.
- `PortfolioSimulationService` runs in two passes so memory stays bounded: replay the screen for
  every share and session (no look-ahead, same guards as live) keeping the best 3 x positions
  candidates per day, then load prices only for candidates into `PriceTable` (exact fixed-point
  longs, four decimals) and simulate.
- Metrics: total return, CAGR, maximum drawdown, volatility and Sharpe (ratios in double; money in
  BigDecimal), trade statistics, exits by reason, costs, returns by year, and Nifty 50
  buy-and-hold for comparison. Every report carries its caveats.

## Consequences
Strategies can be compared as an account would have experienced them, costs included. The first
run (`docs/research/2026-11-26-portfolio-simulation.md`) shows that forward-return averages alone
are misleading: the screen with the best forward-return consistency lost money as a portfolio.
Survivorship bias and unadjusted prices remain; parameter sensitivity and out-of-sample checks are
next.
