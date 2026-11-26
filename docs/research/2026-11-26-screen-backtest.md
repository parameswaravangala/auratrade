# Screen backtest, first run (2026-11-26)

Research record, not investment advice. Method: ADR 0021. Run: signal dates every 5th trading day
from 2019-01-01 to 2026-08-25 (380 dates), 2,770 shares (series EQ, Upstox daily history from
2018), horizons 5, 20 and 60 sessions, entry at the next open, Nifty 50 as benchmark, **before
costs**. Took 49 seconds. Reproduce with
`GET /dev/backtest/screens?from=2019-01-01&to=2026-08-25&step=5&horizons=5,20,60&topN=10`.

## Top 10 per date (the live shortlist)

| Screen | Horizon | Samples | Beat Nifty | Mean excess | Median excess | 10th pct return | Worst |
|---|---|---|---|---|---|---|---|
| 52w-high-breakout | 5 | 2,518 | 41.8% | -0.76% | -1.29% | -10.8% | -53.3% |
| 52w-high-breakout | 20 | 2,518 | 43.7% | -0.04% | -1.84% | -16.7% | -70.6% |
| 52w-high-breakout | 60 | 2,518 | 44.8% | +1.45% | -2.11% | -26.1% | -93.3% |
| pullback-to-50dma | 5 | 3,783 | 45.2% | -0.05% | -0.51% | -7.6% | -37.8% |
| pullback-to-50dma | 20 | 3,783 | 48.3% | +1.11% | -0.44% | -14.4% | -57.5% |
| pullback-to-50dma | 60 | 3,783 | 49.2% | +4.72% | -0.46% | -21.5% | -78.0% |
| relative-strength-leaders | 5 | 3,795 | 46.0% | +0.04% | -0.76% | -11.0% | -53.5% |
| relative-strength-leaders | 20 | 3,795 | 44.8% | +1.12% | -1.90% | -20.9% | -78.3% |
| relative-strength-leaders | 60 | 3,795 | 46.2% | +4.76% | -2.34% | -30.3% | -94.8% |

Mean excess at 20 sessions by year (top 10):

| Screen | 2019 | 2020 | 2021 | 2022 | 2023 | 2024 | 2025 | 2026 |
|---|---|---|---|---|---|---|---|---|
| 52w-high-breakout | -3.05 | -0.34 | 1.87 | -0.91 | 0.48 | -1.69 | 0.12 | 0.83 |
| pullback-to-50dma | 0.11 | 1.03 | 1.60 | 0.21 | 4.15 | 1.68 | -2.30 | 3.14 |
| relative-strength-leaders | -1.78 | 1.51 | 6.09 | -3.18 | 3.18 | 2.70 | -0.58 | 1.06 |

## Reading

- No screen beats the market more often than not; medians are negative everywhere. Positive means
  come from a minority of large winners (positive skew), which survivorship bias also flatters.
- 52w-high-breakout shows no edge in this form.
- pullback-to-50dma is the most consistent: positive mean excess in 7 of 8 years at 20 sessions,
  the shallowest tails, and the best 60-session mean. Worth refining, not yet worth trusting.
- relative-strength-leaders has a similar mean but a much worse median and tails and large swings
  between years.

## Caveats

Survivorship bias (today's listed shares only); prices not adjusted for corporate actions (246
relative-strength samples dropped for split-sized moves); gross of costs; overlapping holding
periods; no slippage or liquidity limits.

## Suggested next steps

Portfolio-level simulation with sizing, stops and realistic costs; parameter sensitivity and an
out-of-sample split; corporate-action adjustment (B-020); delisted instruments for survivorship.
