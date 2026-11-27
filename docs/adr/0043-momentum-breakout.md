# ADR 0043: Momentum breakout, the first strategy

- Status: accepted (definition only; the gate result is not yet known)
- Date: 2026-11-28

## Context
Plan slice 5 implements strategy 1 of the spec (section 3), the first of five code-based strategies
(ADR 0038). It is the spec's highest priority, but the 2026-11-26 sweep found breakout
parameters that overfit, so this strategy is judged by the gate (ADR 0042) and not by its rank.

## Decision
`MomentumBreakout` (`com.auratrade.strategy.builtin`, id `momentum-breakout`) implements the spec's
rules as required conditions:
- Close above the 20-day and 50-day EMA.
- Close above the highest high of the prior `resistanceLookback` sessions (20 to 30, default 20).
- Breakout-day volume at least `minVolumeRatio` times the 20-session average (1.5 to 2.0, default
  1.5).
- RSI(14) between `rsiMin` and `rsiMax` (default 55 and 70, the spec's range).
- Stop within `maxStopDistance` of the trigger (2% to 15%, default 8%). This is our limit, not the
  spec's. Without it a very wide structural stop would pass the sizing step.

Preferred, optional: a three-month return ahead of Nifty 50 (same dates). It adds to the quality
score and does not block a setup, as the spec asks ("prefer").

Plan when every required condition passes:
- Buy-stop at the breakout day's high x (1 + `entryBuffer`, default 0.1%), with a no-chase limit
  of trigger x (1 + `maxChase`, default 2%) and a validity window of `entryWindow` sessions
  (default 3).
- Stop: the lower of resistance x (1 - `stopBuffer`, default 0.5%) and the last confirmed swing
  low (strength 2).
- Target: `targetR` (default 2R) for half the position.
- Trail: EMA(20), active from 1R.
- Invalidation: a close back below the breakout level.
- Holding limit: `maxHold` sessions (default 15, the spec's upper bound).

Quality, from 0 to 100: 50, plus up to 20 for volume above the minimum, plus 20 for
outperformance. This is an untuned design choice (plan decision D4); the gate's walk-forward test
judges the whole rule set, not the score.

## Consequences
- Every default is a conventional value or the spec's range, not a fitted value. They are not
  tuned on any period before the gate.
- Not covered by a test: the stop-distance failure path. A series with a wide, confirmed swing
  low is needed to force it; the code path is simple but unexercised.
- Outperformance uses the benchmark close on the share's date 63 sessions back. A date missing
  from the benchmark makes the condition UNKNOWN, never a pass.
- No run on real data yet. The gate needs a runner that reads candles from the database and
  builds the settings grid; that is the next work item after this strategy, not part of this PR.
- Strategies 2 to 4 follow the same pattern; strategy 5 waits for a fundamentals source (plan
  decision D6).
