# ADR 0048: Market regime (NIFTY 50) and the entry filter

- Status: accepted
- Date: 2026-11-28

## Context
Spec section 5 asks for a three-state market regime and says the regime must reduce or suppress
long entries when the market is broadly bearish. Strategies 1 to 4 were gated without it
(ADR 0043, 0046), and all four failed. Adding the filter is a rule change, so it is a new version.

## Decision
- `MarketRegime` (pure, `com.auratrade.strategy`) classifies NIFTY 50 from its closes, needing 201
  sessions. Bullish: at least three of four bullish votes (close above EMA50, EMA50 above EMA200,
  close above EMA200, EMA50 rising over 10 sessions) and close above EMA200. Bearish: the mirror
  image, with close below EMA200. Otherwise sideways. Each has a score (1, 0.5, 0) and an evidence
  string.
- Each strategy has a new parameter `regimeFilter` (0 or 1, default 0). When 1, a required
  condition `regime_not_bearish` is added: the score must be at least 0.5. With no index history it
  is unknown, so a setup does not pass (fails closed).
- Default 0 keeps the version's behaviour; the gate grids include both 0 and 1 for every strategy,
  so the filter is tested, not assumed. This doubles the grid (momentum now has 16 settings, the
  others 8, within the plan's limit of 18).
- Selection reports the regime of the session in its response.

## Consequences
- Breadth and volatility from spec section 5 are not used: the index alone does not provide them
  and no verified breadth source exists.
- The filter is one more choice per strategy, tested on the same out-of-sample window. Multiple
  testing is larger. The out-of-sample result must be read with that in mind, and the same window
  must not be used to choose among the grid.
- The gate result for each strategy with and without the filter is recorded in the research note.
