# R1-12 spec: `stopMode=NEXT_OPEN` reference rerun (2026-11-27)

Owner: Backend Engine builds and runs it. Quantitative Research wrote this spec and reviews the
results. Background: `2026-11-27-paper-account-validity.md`, section 2 (PR #40). Research
record, not investment advice.

## Why

The backtest (`PortfolioSimulator`, ADR 0022) sells at the stop price in the session the low
touches it. Paper trading (`PaperTradingService`, ADR 0024) sees the same breach at the close and
sells at the next open. The paper stop and the paper position size also come from the
signal-session close, not from the fill price. So the published numbers do not describe the
system that paper account 1 runs. R1-12 produces the reference numbers for that system. The
go-live criteria (proposal F5) and any "close to the backtest" comparison are measured against
those numbers.

## Headline report (Product Owner frame; replaces the old re-measure R-07)

R1-12 also replaces the old re-measure (R-07). The published 2026-11-26 numbers were computed
before #32 removed fund units from the shares that screens read, so they are re-measured here.
The results note must open with this table, stop modes side by side, for account 1's exact
settings:

| Period | Mode | CAGR | Nifty CAGR | Max drawdown | Trades | STOP exits | STOP_GAP exits | TIME exits |
|---|---|---|---|---|---|---|---|---|
| full: 2019-01-01 to T | A (touch) / C (next open) | | | | | | | |
| in-sample: 2019-01-01 to 2022-12-31 | A / C | | | | | | | |
| out-of-sample: 2023-01-01 to T | A / C | | | | | | | |

T is the latest session with completed EOD candles at run time. Record T in the note and use the
same T for every run.

In `NEXT_OPEN` mode, a stop exit is labelled **STOP_GAP** if the open of the flag session was
already at or below the stop, and **STOP** otherwise. This keeps the counts comparable with
mode A.

## 1. What to build

Add two settings to `PortfolioSimulator.Settings`. The defaults keep today's behaviour exactly.

| Setting | Values | Default |
|---|---|---|
| `stopMode` | `INTRADAY_TOUCH` (today) or `NEXT_OPEN` | `INTRADAY_TOUCH` |
| `stopBasis` | `FILL` (today: stop and size from the fill open) or `SIGNAL_CLOSE` (paper: both from the signal-session close) | `FILL` |

Rules in `NEXT_OPEN` mode, each session t:

1. **Exits first.** Every position flagged at t-1 sells at open(t) x (1 - slip). The reason is the
   flag's reason (STOP or TIME), with costs as today. If there is no bar at t (suspension), the
   position stays flagged and sells at the next bar's open. Sells settle before buys, as today.
2. **Flags.** For each position held, entered before t and not already flagged:
   `sessions += 1`, then
   - if low(t) <= stop, flag STOP;
   - otherwise, if sessions >= maxHold, flag TIME.

   This matches `PaperTradingService` step 3: low first, then the holding limit. There is no
   same-session STOP or STOP_GAP fill.
3. **Corporate-action rule.** It must be the same rule that Backend Engine ships for R0-02 in
   `PaperTradingService`. Whatever that rule does to a paper position (hold and flag for review),
   the simulator does the same, and reports the count as `corporateActionFlags`. Until R0-02
   lands, keep today's 40% gap rule in both modes and say so in the result.
4. **Entries.** As today. With `SIGNAL_CLOSE`: stop = close(signal) - k x ATR(14, signal session),
   and size = min(equity x risk / (close(signal) - stop), equity x maxPosition / close(signal)),
   where equity is the signal session's close equity. The cash check runs at the fill open, as in
   paper.
5. **Mark to market.** Flagged positions are still marked at the close until they are sold.
6. **End of run.** Open positions, including flagged ones, close at the last close as END, as
   today.

Expose the settings as `stopMode=` and `stopBasis=` on `/dev/backtest/portfolio` and
`/dev/backtest/sweep`. Below, mode **A** is `INTRADAY_TOUCH` + `FILL` (today). Mode **B** is
`NEXT_OPEN` + `FILL` (exit timing only). Mode **C** is `NEXT_OPEN` + `SIGNAL_CLOSE` (paper rules:
the reference).

## 2. Exact settings

These are the account 1 settings, read from `paper_account` id 1 on 2026-11-27:

| Setting | Value |
|---|---|
| screen | `relative-strength-leaders` |
| capital | INR 10,00,000 |
| maxPositions | 10 |
| riskPct | 1 |
| maxPositionPct | 20 |
| ATR period / atrMultiple | 14 / 3 |
| maxHold | 120 sessions |
| regime | true (Nifty 50 above its 200-session average) |
| slippageBps | 5 |
| costs | true (`CostModel` as in the code at the commit that runs it) |
| benchmark | NIFTY, as the screener reads it (price index, no dividends) |

## 3. Runs and periods

Each run starts with fresh capital. The periods are the same as the sweep note's:

| Run | Mode | Periods |
|---|---|---|
| R-A | A | full 2019-01-01 to T; in-sample 2019-01-01 to 2022-12-31; out-of-sample 2023-01-01 to T |
| R-B | B | same three periods |
| R-C | C | same three periods (**the reference**) |
| G-C | C, sweep grid | `/dev/backtest/sweep` in mode C: stop 1.5, 2 or 3 x ATR; hold 20, 60 or 120; regime off or on (18 combinations), split 2023-01-01 |

Data snapshot: run every run against the same database state. Record the following in the
results note:

```sql
select count(*), sum(close), max(ingested_at) from candle
where bar_interval = 'DAY' and source = 'UPSTOX'
  and ts <= '<T> 00:00+05:30'::timestamptz;
```

Run at a commit that includes #32 (fund units excluded).

## 4. Outputs per run

1. The existing `PortfolioMetrics.Summary`, and returns by year with Nifty's.
2. The trade list, with each trade's net return as a % of entry value (after both sides' costs)
   and its exit reason. Counts by exit reason.
3. Per-trade statistics: n, mean, stdev, skew, t = mean / stdev x sqrt(n), and n needed for
   t = 2.
4. Rolling 12-month windows: every start session s, window [s, s + 12 calendar months). For each
   window, report strategy return, Nifty return, excess (strategy minus Nifty, in points), maximum
   drawdown, and trades closed. Report the 1st, 5th, 10th, 50th, 90th and 95th percentiles of
   each, and the share of windows with excess > 0. The windows overlap, so these percentiles are
   descriptive and not independent. Say so.
5. A bootstrap of per-trade returns: 10,000 resamples, seed 20261127, at n = 50, 100 and 200.
   Report the 5th and 95th percentiles of the mean return and of the win rate.
6. For R-B against R-A only: for each STOP or STOP_GAP exit, the exit price difference
   (B minus A) as % of A's price, and its mean, median and 5th and 95th percentiles.

## 5. Acceptance criteria (engineering: the rerun is valid)

| # | Check | Pass |
|---|---|---|
| AC1 | Defaults unchanged | At the same commit and data snapshot, mode A with default `stopMode` and `stopBasis` gives byte-identical results to the code without the new settings. Also run the published default settings (2 x ATR, 60 sessions, no regime, 2019-01-01 to 2026-11-25) and R-A out of sample to 2026-11-25. Report them next to the published figures: 762 trades, CAGR 20.9%, maximum drawdown -59.5% (portfolio note); CAGR 20.2%, drawdown -30.3%, Sharpe 1.23 (sweep note). Differences are expected from #32 and are the R-07 re-measure. Explain each one by the fund units removed or by data changes, not leave it unexplained. |
| AC2 | Deterministic | Two identical runs give byte-identical JSON. |
| AC3 | Unit tests (`PortfolioSimulatorTest`, synthetic candles) | (a) low touches the stop at t: sells at open(t+1) x (1 - slip), reason STOP; (b) open below the stop at t: sells at open(t+1), not open(t); (c) holding limit reached at t: sells at open(t+1), reason TIME; (d) a flagged position is marked at the close until sold; (e) no bar at t+1: sells at the next bar's open; (f) `SIGNAL_CLOSE` stop and size use the signal close; (g) INTRADAY_TOUCH + FILL results are identical to today's tests. |
| AC4 | Paper replay | Mode C, fed account 1's sessions from 2026-11-26 and its actual decisions (approved, rejected, expired, with any edited quantity or limit), reproduces `paper_order` fills, `paper_position` and `paper_trade` exactly: symbol, dates, quantity, prices to 4 decimals and P&L to the paisa. Until trades exist, compare positions and proposed exits. Ship this as a test or a dev endpoint, so it can run at every review. |
| AC5 | Results note | `docs/research/<date>-next-open-reference.md`, opening with the headline table, then T, the commit, the reproduce URLs, the snapshot query result and all section 4 outputs. |

## 6. Decision rules (research: what the numbers decide)

| # | Question | Rule | Who acts |
|---|---|---|---|
| D1 | Does the strategy survive paper rules? | **Continue:** R-C beats Nifty CAGR in the full period **and** out of sample, **and** at least 10 of 18 G-C settings beat Nifty out of sample. **Review:** only one of the two periods, or 6 to 9 of 18. **Stop account 1:** neither period, or 5 or fewer of 18. | Param, on Quant's report |
| D2 | How big is the mismatch? | If \|CAGR(R-C) - CAGR(R-A)\| > 3 points in any period, the 2026-11-26 notes get a correction pointer, and every later paper-versus-backtest comparison uses mode C only. | Quant, then Backend Engine (notes) |
| D3 | Is the edge established even in the backtest? | Full-period R-C per-trade t >= 2: say "established in the backtest (survivorship-biased)". Otherwise, "not established". Going live then needs R6 and R7 (B-020 adjustment, delisted shares) and a rerun first. | Quant |
| D4 | Go-live thresholds | Section 4 items 4 and 5 for R-C full period fill the placeholders in `2026-11-27-go-live-criteria-proposal.md`: [X] 5th percentile of 12-month excess, [Y] 5th percentile of 12-month drawdown, [Z] 1st percentile of 12-month drawdown, and the bootstrap bands. | Quant drafts, Param decides (F5) |
| D5 | Is the -25% drawdown limit compatible? | If [Y] is deeper than -25%, the current criterion (3) fails a strategy that behaves exactly like its backtest. Param then chooses option 1 or 2 in the proposal. | Param |

## Out of scope

Corporate-action adjustment of history (B-020), delisted shares, walk-forward selection,
intraday data. R1-12 changes exit timing and stop basis only.
