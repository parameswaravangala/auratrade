# ADR 0046: Strategies 2 to 4 (20 EMA pullback, relative strength, 52-week high)

- Status: accepted (definitions only; each gate result is recorded separately)
- Date: 2026-11-28

## Context
Spec section 3 defines five strategies. Strategy 1 is in ADR 0043 and was not approved by the gate
(`docs/research/2026-11-28-momentum-breakout-gate.md`). Strategy 5 needs a fundamentals source that
does not exist yet (plan decision D6). This record covers strategies 2 to 4 and the simplifications
each needs against the spec.

## Decision
Each is a code-based strategy with a validated parameter schema, required and optional conditions
(PASS, FAIL or UNKNOWN), and a trade plan built only when every required condition passes.

- **2. 20 EMA pullback** (`pullback-20ema`). Required: 50-day EMA above the 200-day; close above the
  50-day EMA; the day's low within `pullbackTolerance` (default 2%) of the 20-day EMA; RSI(14)
  between 40 and 50 (spec); bullish reversal bar (up on the day and on the prior close); volume at
  least 1 times the 10-session average; a structural stop within 8%. Entry: buy-stop above the
  reversal high. Target 2R (`targetR`) for half. Trail EMA(20) from 1R. Invalidation: close below the
  reversal low.
  - *Simplification:* the spec's first target, the previous swing high, is not used. The plan model
    takes R-multiples only.
- **3. Relative strength** (`relative-strength`). Required: outperforms Nifty over 21 sessions and
  over 63 sessions (same dates, matched by date); close above the 20-, 50- and 200-day EMAs; higher
  highs and higher lows (swing strength 2); a structural stop within 10%. Entry: buy-stop above the
  day's high. Target 2R. Trail 2 x ATR(14) from 1R (one of the spec's two trailing options, fixed in
  advance).
  - *Simplifications:* the entry is a continuation trigger, not a confirmed consolidation breakout or
    a support pullback. The sector rule (the sector beats Nifty) is kept as an optional UNKNOWN
    condition, because no sector data is verified (D6).
- **4. 52-week high momentum** (`high-52w-momentum`). Required: close within `nearHighTolerance`
  (default 5%) of the prior 252-session high, or above it; close above the 50- and 200-day EMAs;
  outperforms Nifty over 126 sessions; volume on the day at least 1 times the 20-session average;
  a structural stop within 8%. Entry: buy-stop above the day's high. Target 2R. Trail 2 x ATR(14)
  from 1R.
  - *Simplifications:* volume expansion is a ratio to the 20-session average, not a comparison with
    the breakout's own volume. The fundamental check is kept as an optional UNKNOWN condition (D6).

Structural stops for all three use the lower of a level and the last confirmed swing low (strength
2), so a stop is always below the trigger; a stop above the trigger fails a condition and produces no
plan.

Gate grids (declared in `GateGrids` before any result is seen, each containing the defaults):
- Pullback: tolerance 0.01 or 0.02, holding limit 8 or 12 (default 0.02 and 12).
- Relative strength: trailing ATR multiple 2 or 3, holding limit 15 or 20 (default 2 and 20).
- 52-week high: near-high tolerance 0.03 or 0.05, holding limit 10 or 20 (default 0.05 and 20).

## Consequences
- Every spec rule that was simplified is named here and in the code's comments. A strategy's
  result must be read with its simplifications in mind.
- Required conditions that depend on unverified data are not required (sector, fundamentals), so a
  missing source can never make a strategy pass.
- Tests use constructed price histories whose conditions are hand-checked (`OtherStrategiesTest`).
  The RSI fixture needed a long tail to cool the indicator, and that is recorded in the test.
- The gate has to be run separately for each strategy. Passing one does not approve the others, and
  stock selection (ADR 0044) only shows a strategy whose gate has passed.
