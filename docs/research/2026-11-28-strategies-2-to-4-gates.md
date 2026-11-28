# Strategies 2 to 4: first gate runs (2026-11-28)

Research record, not investment advice. Method: ADR 0042 (gate), ADR 0045 (runner), ADR 0046
(strategies). Each gate was run once on the dev profile against the local database, history from
2019-01-01 to 2026-11-27, out of sample from 2023-01-01, benchmark NIFTY, spec-default portfolio
(INR 10 lakh, 5 positions, 0.75% risk, 20% maximum position, 5 bps slippage, Zerodha delivery costs).

| Strategy | Settings | Candidate shares | OOS trades | OOS expectancy (INR/trade) | OOS profit factor | Beating Nifty | Drawdown at defaults | Result |
|---|---|---|---|---|---|---|---|---|
| 20 EMA pullback (`pullback-20ema`) | 4 | 537 | 43 (need 100) | -958.49 | 0.70 | 0 of 4 | -33.0% | fail |
| Relative strength (`relative-strength`) | 4 | 1,595 | 423 | -809.08 | 0.77 | 0 of 4 | see endpoint | fail |
| 52-week high (`high-52w-momentum`) | 4 | 1,057 | 343 | -979.67 | 0.70 | 0 of 4 | see endpoint | fail |

Momentum breakout (strategy 1) also failed; see `docs/research/2026-11-28-momentum-breakout-gate.md`.

## Reading
- None of the four strategies, as specified and parameterised here, has positive expectancy after
  costs out of sample, and none beats Nifty in any setting of its grid. The result is consistent
  across strategies, which points at the entry and exit rules and the costs, not at one bad rule.
- The pullback strategy is the weakest on trade count (43 out of sample): its RSI and touch
  conditions rarely hold together.
- Nothing was tuned on the out-of-sample window. Any change is a new version and a new gate run.
- Caveats as in the gate report: survivorship bias, unadjusted prices, daily-bar intraday order.

## Decision
No strategy is approved. The selection shortlist stays empty (`auratrade.selection.approved-strategies`
is blank). Stock selection (ADR 0044, 0047) is complete as a mechanism but has nothing to rank yet.
