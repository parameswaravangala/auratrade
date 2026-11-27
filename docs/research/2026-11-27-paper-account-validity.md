# Paper account validity: corporate actions, stop mode, go-live power (2026-11-27)

Research record, not investment advice. Quantitative Research review of paper account 1
`relative-strength-1` (ADR 0024) after the Head-of-Quant review of the Phase 0 draft. Analysis
only: nothing in `src/` or in paper trading was changed, and no paper order was touched.

Data: the local dev database (PostgreSQL on localhost:5433), table `candle`, source `UPSTOX`,
interval `DAY`, NSE series `EQ`, ISIN present and not a fund unit (`INF%`), the same share filter
the screens use. Stored daily share bars run from 2018-01-02 to 2026-11-26 (3,530,997 bars that
have a previous bar). Every bar was ingested on 2026-11-26 or 2026-11-27. The SQL is in the
appendix. The backend was down at the time of writing, so no new backtest was run. Section 3 uses
only the published numbers.

## Summary

1. **Corporate actions are a forward-looking risk, and paper trading has no guard.** The stored
   history has very few clean split or bonus ratios. That suggests the provider's history is
   adjusted for splits and bonuses (an inference, unverified). But every split or bonus from now
   on lands unadjusted in our stored series. Every stop in the account (6.0% to 20.3% below the
   reference price) is tighter than a 1:2 bonus (-33.3%) or a 1:4 bonus (-20.0%). So one such
   event would propose a STOP sell on a price drop that never happened, and booking it would show
   a loss of about 1.6% to 5.5% of equity that never happened. None of the 10 holdings has a
   verified upcoming ex-date. NSE's official pages timed out, so every ex-date is **unverified**.
2. **The two stop rules are different systems.** The backtest sells at the stop on the session
   the low touches it. Paper trading sees the same low-based breach at the close and sells at the
   next open. (The breach is not close-based, as the brief said.) In a proxy over 2019 to 2026,
   the next open was on average 0.8% to 3.8% above the breached level. The spread was wide: the
   5th to 95th percentile ran from -5% to +7% at a 6% level. Paper results can therefore differ
   from the backtest by several percentage points of CAGR in either direction. "Close to the
   backtest" is not testable until the simulator can run in the paper's mode.
3. **The go-live criteria cannot confirm the edge.** From the published per-trade numbers:
   mean +2.03%, stdev at least 23.9%, t = 0.60 at 50 trades, 0.85 at 100, 1.99 at 550, and
   t = 2 needs about 556 trades. If the strategy is exactly as good as the backtest, it still
   beats Nifty in only 4 of 8 calendar years. The -25% drawdown criterion is already broken by
   the backtest of the account's own settings (-30.3% out of sample, -42.8% in sample).

## 1. Corporate-action exposure

### Arithmetic of the ratios

A bonus of a new shares for every b held changes the price by a factor of b / (a + b). A split
from face value F to f changes it by f / F. This is arithmetic, not a market fact:

| Event | Price change | Caught by the backtest's 40% guard (`PortfolioSimulator.MAX_GAP`)? |
|---|---|---|
| Bonus 2:1 | -66.7% | yes |
| Bonus 1:1, split 10 to 5 | -50.0% | yes |
| Bonus 1:2 | -33.3% | **no** |
| Bonus 1:3 | -25.0% | **no** |
| Bonus 1:4, split 10 to 8 | -20.0% | **no** |
| Bonus 1:5 | -16.7% | **no** |

`PaperTradingService` has no guard at all (step 3 checks only `low <= stop` and the holding
limit). The quantity is never adjusted either.

### What the stored bars show (2018-01-02 to 2026-11-26)

The open gap is defined as open / previous close - 1. A "ratio" means the gap is within plus or
minus 1 percentage point of -20%, -33.3%, -50% or -66.7%.

| Count | All shares | Liquid (20-day average traded value at least INR 1 crore, previous close at least INR 20) |
|---|---|---|
| Bars with a previous bar | 3,530,997 | 2,217,347 (2,164 shares) |
| Open gap -15% to -40% | 1,174 | 389 |
| Ratio -50% | 15 | 5 |
| Ratio -33.3% | 16 | 1 |
| Ratio -20% | 373 | 90 (56 not locked at the low) |
| Ratio -66.7% | 3 | 2 |
| Any ratio (shares affected) | 407 (191) | 98 |
| Any ratio that the 40% guard misses (-20% or -33.3%) | 389 | 91 |
| Open gap below -40% | 106 | 38 |
| Close-to-close change -15% to -40% | 2,021 | |

How to read this:

- **Most ratio hits are not corporate actions.** Among all shares, most -33.3% and -50% hits are
  tick-size steps on shares priced under INR 1 (for example 0.15 to 0.10). Among liquid shares,
  the -20% bucket clusters on days when many shares gapped down together: 2020-03-13 and
  2020-03-23 (4 and 7 liquid shares) and 2025-04-07 (21 liquid shares at -20%, plus SIEMENS at
  -50%). Most of those opened at the low
  and recovered within the session. That pattern fits market-wide gaps and price bands, not
  ratios.
- **Clean -50% and -66.7% level shifts in liquid shares are rare:** 7 in nine years (for example
  IIFL 2019-05-30, AGI 2019-08-19, SIEMENS 2025-04-07, RAYMOND 2025-05-14, VRLLOG 2025-08-14).
  NSE lists many splits and bonuses each year, so so few clean ratios suggests our history was
  fetched already adjusted for splits and bonuses, but not for demerger-type events. This is an
  inference. It is not verified with Upstox, and the nature of each listed event is unverified.
- **Persistent drops:** 147 liquid-share events, with an open gap of -15% or worse, fewer than 5
  shares gapping that day, and the close 5 sessions later still below the halfway point. That is
  6.6 per 100,000 share-sessions. Of these, 111 lie between -15% and -40%, inside what the guard
  misses. They are an upper bound for unadjusted corporate actions in the stored history, and
  they include real crashes.

### Exposure estimate for a 10-position book

A fully invested 10-position book holds about 2,450 position-sessions a year (10 x about 245
sessions). The persistent-drop rate gives about **0.16 such events a year**, 0.12 of them inside
the guard's blind spot. Whatever fraction of those are corporate actions has been mostly adjusted
away in the history, so this rate does **not** measure the forward rate. From now on every split
or bonus is stored unadjusted. The forward exposure therefore depends on how often holdings
announce a split or bonus, and our database cannot measure that. Scenarios: if a fraction k of
holdings has a split or bonus within a year, then P(at least one in a 10-position book) =
1 - (1 - k)^10, which is **18% at k = 2%, 40% at 5% and 65% at 10%**. Momentum leaders, after
large price rises, may be more likely than average to split. That is plausible but not measured.

Effect of one event: the account sizes positions at 4.9% to 16.6% of equity (table below). A 1:2
bonus would show a 33% loss on the position, 1.6% to 5.5% of equity. That is a fake loss, a
fake losing trade, and up to about a fifth of the -25% drawdown budget. If Param rejects the
SELL, the position keeps the wrong quantity and the wrong stop, and on every later session where
the low stays under the stop a new SELL is proposed. After a corporate action the history also
mixes adjusted and unadjusted bars, so ATR, the 52-week high and relative-strength ranks are
distorted for that share until it is reloaded.

### Account 1 now (read-only, 2026-11-27)

No positions and no trades yet. Cash is INR 10,00,000, and equity on 2026-11-26 was INR
10,00,000. Ten BUY orders were proposed on 2026-11-26 and APPROVED for a fill at the 2026-11-27
open. They fill when the job processes 2026-11-27 after tonight's EOD run.

| Symbol | Stop distance below the 2026-11-26 close | Value as % of INR 10 lakh | Largest open gap in the stored bars since 2025-11-12 | Known upcoming ex-date |
|---|---|---|---|---|
| NRL | 17.8% | 5.6 | -4.6% (2026-09-15) | unverified |
| CUPID | 11.8% | 8.5 | -18.8% (2026-01-05) | unverified |
| FILATEX | 17.1% | 5.8 | -3.9% | unverified |
| TFCILTD | 7.2% | 13.8 | -7.7% | unverified |
| RATNAVEER | 11.1% | 9.0 | -3.3% | unverified |
| SSWL | 18.2% | 5.5 | -3.6% | unverified |
| RAYMOND | 13.5% | 7.3 | -6.2% | unverified |
| GCSL | 6.0% | 16.6 | -5.0% | unverified |
| SHALPAINTS | 20.3% | 4.9 | -5.0% | unverified |
| WELSPUNLIV | 14.6% | 6.8 | -4.0% | unverified |

Ex-dates: we tried NSE's official corporate actions feed and page for 2026-11-27 to 2027-03-31.
Both timed out, and nothing was filled in from memory. Param or Market Data and Licensing should
check the official NSE corporate actions page for these 10 symbols. RAYMOND's stored history has
the -66.4% open gap on 2025-05-14 (listed above; its nature is unverified). CUPID's -18.8% gap on
2026-01-05 is the only recent one among the holdings that looks like a corporate action, and it
is not verified either.

Every stop is tighter than every ratio in the table above, so **any** split or bonus on these
holdings would trigger a STOP proposal.

## 2. Stop-mode mismatch

### The two rules, from the code

| | Backtest (`backtest/PortfolioSimulator.java`, ADR 0022) | Paper (`paperbroker/PaperTradingService.java`, ADR 0024) |
|---|---|---|
| Stop level | fill price (open x (1 + slip)) - k x ATR(14) as of the signal session | signal-session **close** (the estimate) - k x ATR(14), fixed when proposed |
| Sizing price | the fill price; equity at the previous close | the signal-session close; equity at the signal-session close |
| First session checked | the session after the entry | the session after the entry |
| Gap guard | \|open / previous close - 1\| > 40%: sell at the previous close (CORPORATE_ACTION) | none |
| Gap through the stop | open <= stop: sell at **that session's open** x (1 - slip) (STOP_GAP) | low <= stop is seen at the close; SELL proposed for the **next open** |
| Touch | low <= stop: sell at **the stop** x (1 - slip), same session (STOP) | low <= stop is seen at the close; SELL proposed for the **next open** x (1 - slip) |
| Holding limit | sessions >= limit: sell at **that session's close** (TIME) | sessions >= limit: SELL proposed for the **next open** |
| Human step | none (every exit happens) | SELL needs approval before 09:15; if not decided it expires and is proposed again only if a later low is again at or below the stop |

Correction to the brief: the paper breach is based on the **low**, seen at the close, not on the
close. Both systems therefore sell on the same breaches. They differ in the exit price and the
exit day, in the stop level (fill price against the previous close), and in the corporate-action
guard.

### Effect, estimated

Proxy, liquid shares from 2019-01-01 to 2026-11-25: take a level L = previous close x (1 - x).
Count sessions where the open was above L and the low at or below it (an intraday breach). Then
compare the next open with L, that is, the paper's exit against the backtest's.

| Level x below the previous close | Breaches | Mean of next open / L - 1 | Median | 5th / 95th percentile | Closed back above L that session |
|---|---|---|---|---|---|
| 6% | 62,706 | +0.82% | +0.81% | -5.22% / +6.83% | 62.0% |
| 10% | 10,060 | +1.63% | +1.74% | -9.38% / +11.22% | 66.7% |
| 14% | 3,438 | +3.79% | +3.61% | -11.84% / +17.26% | 70.6% |

How to read this: after an intraday breach, prices tend to bounce, so selling at the touch is
selling near the day's low. On average a next-open exit is better. But the spread per trade is
several times larger than the mean. Scale: position value is about 1% of equity divided by the
stop distance d, so each stop exit differs by about delta x 1% / d of equity. With delta about
+1.6% at d about 10%, that is +0.16% of equity per stop. In the published default run, 74% of
762 trades lost, and most of those were stops, which is roughly 50 to 70 stops a year. The
account's 3 x ATR and 120-session settings mean fewer and wider stops. The difference could
therefore be **several percentage points of CAGR**. It probably favours paper trading, but that
is not proven for relative-strength entries, which the proxy does not condition on. The two
smaller differences go either way: the holding-limit exit is one overnight later in paper, and
the stop level depends on the gap between the signal close and the fill open.

### What a `stopMode=NEXT_OPEN` rerun needs (not implemented)

1. A `StopMode {INTRADAY_TOUCH, NEXT_OPEN}` setting in `PortfolioSimulator.Settings`. Add a
   `stopMode=` parameter to `/dev/backtest/portfolio` and `/dev/backtest/sweep`, defaulting to
   today's behaviour so the published numbers still reproduce.
2. In NEXT_OPEN mode, each session first sells positions flagged on the previous session at this
   open x (1 - slip), sells first. Then it flags, for the next session, positions whose low is at
   or below the stop or that reached the holding limit. Flagged positions are still marked at the
   close. There is no same-session STOP or STOP_GAP fill.
3. A paper-faithful option to compute the stop and size from the signal-session close, not from
   the fill price.
4. The same corporate-action rule in both systems, for whichever guard is chosen in R1.
5. Outputs for section 3: per-trade returns (as % of entry), yearly and rolling 12-month excess
   returns against Nifty, and the maximum drawdown of each rolling 12-month window.
6. A fidelity test: replay the paper account's own sessions through the simulator in NEXT_OPEN
   mode, with every proposal approved, and compare the result with `paper_trade`.

Runtime should match today's, about 1 to 2 minutes for full history, since signals and prices
come from `prepare` once.

## 3. Go-live criteria power

### Per-trade statistics (published default run)

Source: `2026-11-26-portfolio-simulation.md`, relative-strength-leaders, 2 x ATR, 60 sessions,
no regime filter: 762 trades, win rate 25.6%, average win +42.8%, average loss -12.0%. Per-trade
returns are a percentage of the entry value. The per-trade spread is not published, so the stdev
below assumes every win is exactly +42.8% and every loss exactly -12.0%. Real wins and losses
vary within each group, so this is a **lower bound** and the t values are upper bounds.

- Mean = 0.256 x 42.8 + 0.744 x (-12.0) = **+2.03%** per trade.
- Stdev >= sqrt(0.256 x 42.8^2 + 0.744 x 12.0^2 - 2.03^2) = sqrt(571.97) = **23.9%**.
- Per-trade Sharpe = 2.03 / 23.9 = 0.0848, and t = 0.0848 x sqrt(n).

| Trades n | t (at most) | P(t > 2) if the backtest is the truth | P(paper mean > 0) |
|---|---|---|---|
| 50 | **0.60** | 8% | 73% |
| 100 | **0.85** | 12% | 80% |
| 550 | **1.99** | 50% | 98% |
| 556 | 2.00 | 50% | 98% |

This agrees with the reviewer's figures. The default run made about 96 trades a year. The
account's settings (120-session holds, regime filter) will trade less often, so 550 trades would
take many years of paper trading. A paper record will not prove the edge on any timetable Param
would accept. The paper settings' own per-trade numbers are not published, so the rerun in
section 2 should report them.

### "Beat Nifty over 12 months"

The yearly table in the same note gives excess returns against Nifty 50 (percentage points) of
-41.8 (2019), +14.4, +96.4, -24.4, +74.2, +81.8, -24.3 (2025) and -10.7 (2026 to 2026-11-25,
part of the year). The strategy beat Nifty in **4 of 8 years (50%)**, and the Wilson 95%
interval runs from 22% to 78%. With a normal approximation, the mean excess is +20.7 points,
the stdev is 55.2, and P(excess > 0) = Phi(0.375) = **65%**. So even if the strategy behaves
exactly like the backtest, it has a **35% to 50% chance of failing this criterion** in a given
12 months. The 5th to 95th percentile under that approximation runs from about -70 to +112
points, too wide to be a useful pass mark.

### Drawdown criterion against the backtest

The sweep note's chosen setting is exactly the account's setting (3 x ATR, 120 sessions, regime
on). Its maximum drawdown was **-30.3% out of sample** (2023 to 2026) and **-42.8% in sample**
(2019 to 2022), and the full 2019 to 2026 default run reached -59.5%. ADR 0024's "drawdown no
deeper than -25%" is therefore stricter than the backtest's own behaviour. A run that is "close
to the backtest" can fail it, so the two criteria contradict each other.

### Proposed criteria (for Param's decision; changes ADR 0024)

The idea: paper trading tests the **implementation** with certainty, and tests the **edge** only
for gross inconsistency. Evidence for the edge comes from the long backtest once it has
corporate-action adjustment (B-020) and delisted shares.

1. **Process (keep):** every order is decided before 09:15 IST on its fill date. Pass mark 100%.
2. **Implementation fidelity (replaces "close to the backtest"):** replay the paper sessions
   through the simulator in NEXT_OPEN, paper-faithful mode with Param's actual decisions. It
   must produce the same entries and exits (symbol, side, date, quantity). The fill prices must
   match the stored open with slippage exactly. Total P&L must be within 0.25% of starting
   equity (rounding and costs). Any mismatch is a bug, and the clock restarts after the fix.
3. **Consistency with the backtest distribution (replaces "beat Nifty" and the -25% drawdown):**
   take the NEXT_OPEN rerun of the account's exact settings over 2019 to 2026 and all rolling
   12-month windows, and require each of these within that distribution's 5th to 95th percentile:
   - 12-month excess return against Nifty 50 after costs: at least the 5th percentile of the
     rolling windows. That figure is not yet computed. The yearly figures put the lowest
     calendar year at -41.8 points.
   - Maximum drawdown in the 12 months: no deeper than the 5th percentile of rolling 12-month
     drawdowns (not yet computed; at least as deep as the -30.3% out-of-sample figure).
   - Win rate and mean per-trade return within the binomial or bootstrap 90% interval for the
     actual number of trades. Indicative values from the default run: win rate **15.5% to 35.7%**
     at 50 trades and **18.4% to 32.8%** at 100. Mean per-trade return **-3.5% to +7.6%** at 50
     trades and **-1.9% to +6.0%** at 100. Recompute both from the rerun.
4. **Minimum sample (keep, reworded):** at least 12 months **and** 50 closed trades before any
   review. Say plainly that this checks the implementation and screens out gross failure. It does
   not prove the strategy beats Nifty (t <= 0.6).
5. **Hard stop (new):** suspend new entries if the paper drawdown falls below the 1st percentile
   of the rolling 12-month drawdowns, or if any fidelity check fails.

Concrete pass marks for items 3 and 5 can be set only after the NEXT_OPEN rerun. Until then,
these are the indicative figures above.

## Limitations

- Section 1 counts events in stored history that appears to be already adjusted, so it
  understates the corporate actions that paper trading will meet. The forward rate can only be
  given as scenarios. Whether Upstox adjusts its history is an inference and is not verified.
- Whether each listed event was a corporate action is not verified at an official source. NSE's
  official pages timed out.
- The section 2 proxy uses a level measured from the previous close, not a 3 x ATR stop measured
  from a relative-strength entry. It shows direction and scale only.
- Section 3 uses the published default-configuration run. The stdev is a lower bound, and the
  paper settings' per-trade numbers are not published.
- Survivorship bias (no delisted shares) flatters every backtest number used here.
- The 2026 row covers part of the year only.

## Recommendations, most urgent first

| # | Recommendation | Owner | Type |
|---|---|---|---|
| R1 | **Corporate-action guard in `PaperTradingService`.** Before proposing a STOP, check the session's open gap against the previous close. If it is -15% or worse, or within 1 point of a split or bonus ratio, propose no automatic STOP. Record a `POSSIBLE_CORPORATE_ACTION` review item with the evidence for Param, and leave the position unchanged until it is reconciled. Fails closed: nothing is booked on an unexplained gap. **We recommend exempting this from the freeze.** It is small, protects the account's data, and an event can happen any day. | Backend Engine | code, freeze exemption (Param to approve) |
| R2 | **Until R1 lands:** before approving any STOP SELL, Param checks the day's open gap. If it is -15% or worse, check the official NSE corporate actions page before deciding. Also check the 10 holdings' upcoming ex-dates there once now (all unverified above). | Param, with Market Data and Licensing for the source | process |
| R3 | **Rewrite the ADR 0024 go-live criteria** as in section 3: fidelity, then backtest-distribution consistency, then the minimum sample, then a hard stop. Remove "beat Nifty over 12 months" and the -25% drawdown limit, which the backtest itself fails. Or keep -25% and lower the risk per trade; that is a separate decision. | Param | decision (ADR 0024 change) |
| R4 | **`stopMode=NEXT_OPEN` and a paper-faithful mode in the simulator**, as specified in section 2, then rerun the sweep and the account's setting to set the thresholds in R3. | Backend Engine, after the freeze | code |
| R5 | **Paper-replay fidelity check** (section 2, item 6) as a test and a dev endpoint. | Backend Engine, after the freeze | code |
| R6 | **Corporate-action data and adjustment (B-020):** an unattended official or licensed feed of ex-dates and ratios. On the ex-date, adjust paper positions (quantity x factor, entry and stop divided by factor) and reload the share's history, so ATR, the 52-week high and RS ranks stay consistent. Change the backtest's 40% guard to a ratio-aware one. | Market Data and Licensing (source), Backend Engine | code and data, after the freeze |
| R7 | Delisted shares in the backtest, to remove survivorship bias before any go-live decision. | Backend Engine | code, after the freeze |

## Appendix: SQL (run with `docker exec auratrade-db-1 psql -U aura -d auratrade -f <file>`)

Shared base (share filter as in `CandleSeriesReader`):

```sql
create temp table mv as
with bars as (
  select c.instrument_id, i.trading_symbol sym, (c.ts at time zone 'Asia/Kolkata')::date d,
         c.open, c.high, c.low, c.close, c.volume,
         lag(c.close) over w prev_close, lead(c.close, 5) over w close_5,
         avg(c.close * c.volume) over (partition by c.instrument_id order by c.ts
                                       rows between 20 preceding and 1 preceding) atv
  from candle c join instrument i on i.id = c.instrument_id
  where c.bar_interval = 'DAY' and c.source = 'UPSTOX' and i.exchange = 'NSE' and i.series = 'EQ'
    and i.isin is not null and i.isin not like 'INF%'
  window w as (partition by c.instrument_id order by c.ts)
)
select *, open / prev_close - 1 g from bars where prev_close is not null;
```

Counts in section 1 (the second table column adds `where atv >= 1e7 and prev_close >= 20`):

```sql
select count(*) bars, min(d), max(d),
  count(*) filter (where g between -0.40 and -0.15) gap_15_40,
  count(*) filter (where abs(g + 0.5) <= 0.01) r50,
  count(*) filter (where abs(g + 1/3.0) <= 0.01) r33,
  count(*) filter (where abs(g + 0.2) <= 0.01) r20,
  count(*) filter (where abs(g + 2/3.0) <= 0.01) r667,
  count(*) filter (where abs(g + 0.2) <= 0.01 and not (open = low and close = low)) r20_not_locked,
  count(*) filter (where g < -0.40) beyond_40
from mv;
```

Persistent drops:

```sql
create temp table wide as select d, count(*) n from mv where g < -0.15 group by d;
select count(*) events, count(*) filter (where g >= -0.40) inside_guard
from mv left join wide using (d)
where atv >= 1e7 and prev_close >= 20 and g <= -0.15 and coalesce(wide.n, 0) < 5
  and close_5 is not null and close_5 < prev_close * (1 + g / 2);
```

Account 1 (read-only):

```sql
select symbol, quantity, stop_price, reference_price,
       round(100 * (1 - stop_price / reference_price), 1) stop_dist_pct,
       round(quantity * reference_price / 10000, 1) pct_of_10_lakh, status, fill_date
from paper_order where account_id = 1 order by id;
select * from paper_position where account_id = 1;
select * from paper_equity where account_id = 1;
```

Stop-mode proxy (2019-01-01 to 2026-11-25, liquid shares):

```sql
create temp table b as
select c.instrument_id, (c.ts at time zone 'Asia/Kolkata')::date d, c.open, c.low, c.close,
       lag(c.close) over w pc, lead(c.open) over w next_open,
       avg(c.close * c.volume) over (partition by c.instrument_id order by c.ts
                                     rows between 20 preceding and 1 preceding) atv
from candle c join instrument i on i.id = c.instrument_id
where c.bar_interval = 'DAY' and c.source = 'UPSTOX' and i.exchange = 'NSE' and i.series = 'EQ'
  and i.isin is not null and i.isin not like 'INF%'
  and c.ts >= timestamptz '2019-01-01 00:00+05:30' and c.ts < timestamptz '2026-11-26 00:00+05:30'
window w as (partition by c.instrument_id order by c.ts);
select x, count(*) n, avg(next_open / l - 1) mean_delta,
  percentile_cont(0.5) within group (order by next_open / l - 1) median,
  percentile_cont(0.05) within group (order by next_open / l - 1) p05,
  percentile_cont(0.95) within group (order by next_open / l - 1) p95,
  avg(case when close > l then 1 else 0 end) closed_back_above
from (select b.*, pc * (1 - v.x) l, v.x from b cross join (values (0.06), (0.10), (0.14)) v(x)) t
where atv >= 1e7 and pc >= 20 and next_open is not null and open > l and low <= l
group by x order by x;
```
