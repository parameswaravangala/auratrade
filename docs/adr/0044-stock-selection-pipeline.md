# ADR 0044: Stock selection pipeline (daily shortlist from strategies)

- Status: accepted (P1 of the stock-selection plan)
- Date: 2026-11-28

## Context
Strategies exist as code (ADR 0038, 0043) and the gate exists (ADR 0042), but nothing runs them on
the share universe for a session. The spec (section 6) asks for one pipeline: universe, regime,
indicators, strategy evaluation, risk checks, ranking, shortlist. This record covers the first
piece: evaluating every registered strategy and ranking the results.

## Decision
- `Selection` (pure, `com.auratrade.selection`) ranks evaluations. Shortlist: shares with at least
  one PASSED strategy, by the best PASSED score, then symbol, at most 10. Almost qualified: shares
  whose best strategy is PARTIAL (exactly one required condition failed, none unknown), which the
  spec lists as useful (section 15.4). Everything else is counted, not listed.
- `StockSelectionService` reads the newest session (or `asOf`), reads the benchmark only if a
  strategy requires it, applies the screener's share guards (STALE, SHORT_HISTORY, ILLIQUID,
  POSSIBLE_CORPORATE_ACTION) and evaluates every registered strategy at its default version.
  Nothing is written to the database.
- Strategies are registered in `SelectionConfig`. Momentum breakout is the only one for now.
- Dev endpoint `GET /dev/selection?asOf=&limit=`, profile `dev` only (no authentication yet,
  B-031). The response carries each strategy's status, score, conditions (with values and
  thresholds) and trade plan, so a share's result can be explained.
- The ranking is by the strongest single strategy, not a combined score. Combining needs scoring
  profiles (P6), which must use only strategies that passed the gate.

## Consequences
- The selection works for the one strategy that exists, end to end, with no stored state.
- Not yet in the pipeline, so the shortlist is not yet a finished selection:
  - the three-state market regime (plan section 5); only the old on/off filter exists in paper
    trading, so the regime is not applied to these results;
  - the proxy universe (ADR 0039) is not used: the screener's full EQ list is screened instead;
  - no stored runs (`screen_run` or similar, B-042), so the endpoint recomputes on each call;
  - the gate has not run, so no strategy is known to pass. The shortlist can be empty for that
    reason, and the UI must say so.
- Memory: every share's evaluations are held for one call (about 3,600 shares by strategies).
- Testing: the ranking is unit tested. The service has no integration test yet; it needs the
  database and candles, so it is checked by the next integration slice.
