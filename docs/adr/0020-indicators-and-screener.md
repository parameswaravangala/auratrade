# ADR 0020: Indicator library and rule-based screener

- Status: accepted
- Date: 2026-11-26

## Context
With daily history loaded (2018 onwards for about 2,800 shares), the first useful feature is a
screener: code finds candidates, an analyst (later an agent) interprets them. CLAUDE.md: code
computes, the LLM interprets; indicators and sizing are plain Java; money is BigDecimal. The
screener module is documented as producing a shortlist of at most 10 symbols.

## Decision
- `screener.indicators.Indicators`: pure functions over BigDecimal (DECIMAL64): SMA, EMA (seeded
  with SMA, k = 2/(n+1)), RSI and ATR with Wilder smoothing, highest/lowest with skip, change over n
  periods, largest one-period move. Too little data returns empty, never a guess. Unit tests use
  hand-worked values.
- `Screen` interface; three built-in screens with conventional, untuned thresholds:
  `52w-high-breakout` (close above the prior 252-session high on at least 2x the 50-day average
  volume), `pullback-to-50dma` (close and 50-day average above the 200-day, within 3% of the
  50-day, RSI(14) 40 to 55), `relative-strength-leaders` (126-session return ahead of Nifty 50,
  close above the 50-day average).
- `ScreenerService` runs a screen over active company shares (series EQ by default) from the
  selected provider's candles and returns a ranked shortlist of at most 10, each hit with its
  numbers and a one-sentence reason, plus counts of everything skipped and why.
- Shared guards before any screen: STALE (no candle on the as-of date), SHORT_HISTORY, ILLIQUID
  (20-day average traded value below INR 1 crore), POSSIBLE_CORPORATE_ACTION (a one-day move over
  40% within the lookback; prices are not split-adjusted yet, backlog B-020).
- `CandleSeriesReader` (marketdata) reads the last N candles of many instruments in chunks of 200
  instruments, because the PostgreSQL driver holds a whole result set in memory.
- The benchmark Nifty 50 (trading symbol NIFTY) is loaded with the daily EOD job and can be
  backfilled through the dev endpoint.

## Consequences
A useful, explainable shortlist is available on real data at no cost and in seconds. Thresholds are
not validated: the backtest slice should measure each screen before anyone relies on it. Until
corporate actions are adjusted, shares with a recent split are excluded rather than mis-screened.
