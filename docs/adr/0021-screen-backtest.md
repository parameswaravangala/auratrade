# ADR 0021: Backtesting screens by forward returns

- Status: accepted
- Date: 2026-11-26

## Context
The screens of ADR 0020 use untested, conventional thresholds. Before anyone relies on them we need
to know what happened after past matches. A full portfolio simulator (sizing, stops, cash) is a
larger piece; the first question is simpler: did matches beat the market afterwards?

## Decision
- `ScreenReplay` (pure) replays the live screens over one share's stored history. On each signal
  date a screen sees only candles up to that date's close, cut to the same window the live
  screener uses (no look-ahead; a test proves it). The same guards as live apply.
- A match is entered at the next session's open and exited at the close h sessions after the
  signal (default h = 5, 20, 60). Excess return = return minus Nifty 50 over the same dates.
- A sample whose holding period contains a one-day move over 40% (likely an unadjusted split) is
  dropped and counted.
- Two views: all matches, and the top N by score per date (N = 10, the live shortlist).
- Statistics: hit rate versus the benchmark, mean and median excess, mean return, 10th-percentile
  and worst return, and mean excess per year. Costs are a parameter (`costBps`, round trip, default
  0 = gross); current Indian brokerage, STT and fees must be checked at source before choosing one.
- Signal dates are every 5th trading day by default; shares are replayed in parallel with a bounded
  queue. Full history (2,770 shares, 380 dates, 3 horizons) runs in under a minute.
- Every report carries its caveats: survivorship bias, unadjusted prices, gross of costs,
  overlapping holding periods, no slippage or liquidity limits.

## Consequences
Screens can be judged on evidence; see `docs/research/2026-11-26-screen-backtest.md` for the first
run. Survivorship bias cannot be removed until delisted instruments and their history are available.
A portfolio-level simulator with sizing, stops and costs is the next step for the backtest module.
