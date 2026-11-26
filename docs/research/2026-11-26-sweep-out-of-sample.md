# Parameter sweep with out-of-sample check (2026-11-26)

Research record, not investment advice. Method: ADR 0023. In-sample 2019-01-01 to 2022-12-31,
out-of-sample 2023-01-01 to 2026-11-25, each with fresh INR 10 lakh, 10 positions, 1% risk per
trade, 20% maximum position, 5 bps slippage, Zerodha delivery charges. Grid: stop 1.5, 2 or 3 x
ATR(14); holding limit 20, 60 or 120 sessions; regime filter off or on (18 combinations). Reproduce
with `GET /dev/backtest/sweep?screen=<name>&from=2019-01-01&split=2023-01-01&to=2026-11-25`.

Nifty 50 buy and hold: in-sample CAGR 13.5% (maximum drawdown -38.4%), out-of-sample CAGR 9.4%
(maximum drawdown -15.8%).

| Screen | Chosen on in-sample Sharpe | In-sample CAGR / DD | Out-of-sample CAGR / DD / Sharpe | Combinations beating Nifty out of sample | Out-of-sample CAGR across all 18 (min / median / max) |
|---|---|---|---|---|---|
| relative-strength-leaders | 3 x ATR, 120 sessions, regime on | 16.4% / -42.8% | 20.2% / -30.3% / 1.23 | 13 of 18 | -8.3% / 19.4% / 28.1% |
| pullback-to-50dma | 3 x ATR, 60 sessions, regime on | 16.9% / -19.7% | 22.7% / -21.1% / 1.37 | 8 of 18 | -13.5% / 8.9% / 28.2% |
| 52w-high-breakout | 3 x ATR, 120 sessions, regime off | 22.4% / -18.2% | 3.8% / -35.1% / 0.29 | 1 of 18 | -35.7% / -0.1% / 14.9% |

## Reading

- Wider stops (3 x ATR) and the regime filter come out at or near the top in-sample for every
  screen. The ADR 0022 defaults (2 x ATR, no filter) were among the weaker settings.
- relative-strength-leaders is the most robust: most settings beat Nifty out of sample, and the
  chosen one held up (20.2% against 9.4%), though with about twice Nifty's drawdown.
- pullback-to-50dma does well with the chosen settings but is parameter-sensitive: fewer than half
  of the settings beat Nifty out of sample.
- 52w-high-breakout overfits: its best in-sample setting did poorly out of sample and only one
  setting beat Nifty. Not worth pursuing in this form.
- The out-of-sample period (2023 to 2026) contains a strong 2023 and 2024 for smaller shares;
  four years is one market environment, not proof. Survivorship bias flatters all of these.

## Next steps

A walk-forward test (re-choose settings every year, test on the next), corporate-action
adjustment (B-020) and delisted instruments to remove survivorship bias, and paper trading of the
relative-strength settings with a human approving each trade (ADR 0005).
