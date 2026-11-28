# Momentum breakout: first gate run (2026-11-28)

Research record, not investment advice. Method: ADR 0042 (gate), ADR 0043 (strategy), ADR 0045
(runner). Reproduce with `GET /dev/strategies/momentum-breakout/gate` on the dev profile.

## Setup
- Strategy: `momentum-breakout@1.0.0`, grid of 8 settings: resistance lookback 20 or 30, minimum
  volume ratio 1.5 or 2.0, holding limit 10 or 15 sessions. Spec default: 20, 1.5, 15.
- History: 2019-01-01 to 2026-11-27 (last session in the database), source UPSTOX, benchmark NIFTY.
  Out of sample from 2023-01-01.
- Portfolio, identical for every setting: INR 10 lakh, 5 positions, 0.75% risk per trade, 20%
  maximum position, 5 bps slippage, Zerodha delivery charges (a broker's published schedule).
- Shares: 897 candidate shares, after the screener's guards (short history, illiquid, split-like
  moves). 1,496 shares were evaluated on the latest session.
- Run time: 391 seconds.

## Result: fails

| Check | Value | Threshold | Result |
|---|---|---|---|
| Out-of-sample trades | 486 | at least 100 | pass |
| Out-of-sample expectancy after costs | -INR 277.60 per trade | above 0 | **fail** |
| Walk-forward expectancy after costs | -INR 277.60 per trade | above 0 | **fail** |
| Out-of-sample profit factor | 0.86 | above 1.2 | **fail** |
| Settings beating Nifty out of sample | 0 of 8 | more than half | **fail** |
| Expectancy without the 5 best trades | -INR 478.84 per trade | above 0 | **fail** |
| Maximum drawdown at spec defaults, out of sample | -31.3% | no deeper than -25% | **fail** |

Out-of-sample win rate 31.3%; total net P&L -INR 1.35 lakh on INR 10 lakh.

The chosen setting (in-sample, 2019 to 2022) was res20-vol2.0-hold10. Walk-forward picked the same
setting every year, so its numbers match the out-of-sample numbers.

## Reading
- The rules as written do not produce positive expectancy after costs on this history. Every
  setting in the grid is negative or worse than Nifty, so the grid does not contain a passing
  variant.
- The strategy has no market-regime filter, although the spec asks for one (section 5). The
  regime is the first thing to test, as a new version with its own gate run. Adding it because
  the result is poor, and then judging by this same out-of-sample window, would be tuning on the
  test set.
- Caveats (also returned by the endpoint): survivorship bias from delisted shares, which flatters
  these results; unadjusted prices around corporate actions; daily bars, so intraday order is
  unknown and a bar that touches both the stop and the target counts as the stop.

## Decision
Momentum breakout is **not approved** and is not used for stock selection. The shortlist will not
include it as a passing strategy (ADR 0044). Any change to its rules, grid or regime filter is a new
version and a new gate run, and the out-of-sample window must not be used to choose it.
