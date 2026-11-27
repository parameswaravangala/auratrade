# ADR 0038: Strategy module, trade plans and stored evaluations

- Status: accepted
- Date: 2026-11-28

## Context
`docs/product/swing-scanner-spec.md` asks for five swing strategies, each evaluated on its own,
then a combined score built only from strategies that passed a pre-registered gate
(`docs/product/swing-scanner-plan.md`). Today the screens (ADR 0020) return match or no match plus
one ranking number, with no per-condition result and no trade plan, so they cannot be gated or
combined. Indicators sit in `screener.indicators`, which the strategies need without depending on
the screener.

## Decision
- `Indicators` moves to its own top-level package `com.auratrade.indicators`. Behaviour is unchanged;
  the import changes everywhere. New helpers: `ratioToAverage`, confirmed swing highs and lows
  (only bars with `strength` later bars are confirmed, so the latest bars never look ahead),
  `higherHighsAndLows`.
- New module `com.auratrade.strategy`:
  - `Strategy`: code, one class per strategy. Has an id, a `ParameterSchema`, the data it requires
    and `assess()`, which measures each condition as a `ConditionResult` (PASS, FAIL or UNKNOWN).
  - `ParameterSchema` is the only way to create `Parameters`. Values are range-checked, unknown
    names are rejected, missing names take defaults, and all errors are reported at once. The UI
    edits values, never code.
  - `StrategyVersion`: strategy id, semantic version and validated parameters. Immutable. A changed
    parameter means a new version.
  - `StrategyEvaluator` applies the shared rules. Status comes from required conditions only:
    PASSED, PARTIAL (exactly one failure, no unknowns), FAILED, or NOT_EVALUATED (no failure but
    some data missing). Missing data is never a pass. Score is the strategy's 0 to 100 quality
    when PASSED, otherwise 0. A PASSED evaluation must carry a `TradePlan`.
  - `TradePlan`: entry (buy-stop or next open, with a no-chase limit and a validity window), initial
    stop with its reason, targets in R, trailing rule, invalidation and a holding limit. Pure data,
    so the backtest and paper trading run the same plan.
  - `StrategyRegistry` holds code strategies by id and fails at startup on duplicate or malformed
    ids. A stored version whose strategy is missing will fail closed (slice 9).
- Nothing is stored yet. Tables for definitions, versions, runs and evaluations come in slice 9,
  after the strategies have been gated, and writes wait on R1-05 (audit).
- The existing screens are unchanged and remain the baseline for account 1.

## Consequences
- Each strategy's conditions and reasons are visible, as the spec requires. Once the
  evaluation is stored, the same evaluation can be re-ranked under any profile.
- The rule builder (spec 14.7) is deferred. It would need an expression language and its own
  tests; the code-based interface covers the five seeds first.
- Weights, scoring profiles and the combined score are not in this slice. They come after at least
  two strategies pass the gate (plan decision D1).
- Score rules (D4) are a design choice that the backtests should test. A partial pass earns nothing.
- Parameter defaults are conventional starting points, not tuned values. Tuning is only done on the
  training period, per the gate.
