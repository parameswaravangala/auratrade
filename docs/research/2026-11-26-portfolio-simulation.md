# Portfolio simulation, first run (2026-11-26)

Research record, not investment advice. Method: ADR 0022. 2019-01-01 to 2026-11-25, INR 10 lakh,
10 positions, 1% risk per trade, 20% maximum position, stop 2 x ATR(14), 60-session holding limit,
5 bps slippage, Zerodha's published delivery charges. Reproduce with
`GET /dev/backtest/portfolio?screen=<name>&from=2019-01-01&to=2026-11-25`.

| | relative-strength-leaders | 52w-high-breakout | pullback-to-50dma | Nifty 50 buy and hold |
|---|---|---|---|---|
| End equity (INR) | 44,67,805 | 11,26,312 | 9,82,681 | about 23,66,500 |
| Total return | +346.8% | +12.6% | -1.7% | +136.7% |
| CAGR | 20.9% | 1.5% | -0.2% | 11.5% |
| Maximum drawdown | -59.5% | -45.1% | -52.6% | -38.4% |
| Sharpe (rf 0) | 0.87 | 0.18 | 0.12 | |
| Trades | 762 | 710 | 695 | |
| Win rate | 25.6% | 26.5% | 27.2% | |
| Average win / loss | +42.8% / -12.0% | +26.5% / -10.0% | +30.2% / -10.7% | |
| Profit factor | 1.26 | 1.02 | 1.00 | |
| Costs paid (INR) | 3,75,350 | 1,77,122 | 1,77,064 | |

Returns by year (%):

| | 2019 | 2020 | 2021 | 2022 | 2023 | 2024 | 2025 | 2026 |
|---|---|---|---|---|---|---|---|---|
| relative-strength-leaders | -30.3 | 29.3 | 120.5 | -20.1 | 94.2 | 90.6 | -13.8 | -11.9 |
| 52w-high-breakout | -4.2 | -11.7 | 25.6 | -20.6 | 66.6 | 20.5 | -9.0 | -26.9 |
| pullback-to-50dma | -11.2 | -5.9 | 26.4 | -2.1 | 58.4 | 23.0 | -32.1 | -28.2 |
| Nifty 50 | 11.5 | 14.9 | 24.1 | 4.3 | 20.0 | 8.8 | 10.5 | -1.2 |

## Reading

- Relative strength is the only screen that beat Nifty here (20.9% against 11.5% a year), with a
  much deeper drawdown (-59% against -38%), four losing years out of eight, and a 26% win rate: a
  few large winners pay for many small losses. Survivorship bias flatters exactly this kind of
  strategy (failed momentum stocks that were later delisted are missing).
- Pullback, the most consistent screen in forward returns (ADR 0021), lost money as a portfolio:
  2 x ATR stops cut most trades (469 of 695 exits were stops) and costs took about INR 1.77 lakh.
- Costs matter: STT on both sides makes each round trip cost about 0.2% before slippage.
- All three strategies lost money in 2025 and 2026 so far while Nifty was positive in 2025.

## Next steps

Parameter sensitivity (stop multiple, holding limit, number of positions), an out-of-sample split
(choose on 2019-2022, test on 2023-2026), a market-regime filter (for example trade only when
Nifty is above its 200-day average), corporate-action adjustment (B-020) and delisted instruments.
