# Proposal F5: rewording the go-live criteria of ADR 0024 (2026-11-27)

**Status: decided 2026-11-27, option 2.** Backend Engine's ADR amending ADR 0024 records it and
cites this note.

## Decision

Param chose option 2 himself, in the "Param Inbox (approvals & requests)" session on
2026-11-27. This is his choice as relayed by that session:

> F5: option 2. Fixed -25% drawdown limit, lower risk per trade until the backtest stays within
> it, a new account 2 with a new start date; account 1 continues as a learning run.

What this means for the text below:

- **Account 2** gets the proposed text, with criterion 3(b) replaced by a fixed limit: "maximum
  drawdown no deeper than -25%". Its risk per trade is the largest value that keeps the reference
  run's (R1-12 mode C) maximum drawdown no deeper than -25% in the full, in-sample and
  out-of-sample periods. R1-12 run R-C-risk, decision rule D5, finds it. [X], [Z] and the
  bootstrap bands are computed at that risk per trade. The governance rule applies: criteria
  are written before account 2 opens and frozen from its first closed trade.
- **Account 1** continues unchanged as a learning run: it tests the implementation (fidelity
  replay, process, corporate-action reconciliation). It is not eligible for go-live. Its
  `go_live_criteria` text should say so; that wording belongs in Backend Engine's ADR.
- If no risk per trade passes, or the chosen one no longer beats Nifty, the decision goes back
  to Param before account 2 opens.

The rest of this note is the proposal as it was put to Param, kept for the record.

## Proposal (as submitted)

For Param's decision. Drafted by Quantitative Research from the power analysis in
`2026-11-27-paper-account-validity.md`, section 3 (PR #40). If Param accepts it, Backend Engine
records it as an ADR amending ADR 0024. The ADR should keep the old text and the date, and
update `paper_account.go_live_criteria` for account 1. Research record, not investment advice.

## Current text (account 1, `paper_account.go_live_criteria`, read 2026-11-27)

> Live trading may be considered only if ALL hold: (1) at least 12 months of paper trading and
> at least 50 closed trades; (2) annual return above Nifty 50 over the same period, after costs
> and slippage; (3) maximum drawdown no deeper than -25%; (4) paper results within reach of the
> backtest expectation for the same period [...]; (5) every order decided by a person before the
> open, with no unexplained rule breaks. [...]

## What is wrong with it

| Criterion | Problem (numbers from the validity note) |
|---|---|
| (1) 12 months, 50 trades | 50 trades cannot show an edge: per-trade t is at most 0.60, and t = 2 needs about 556 trades. The criterion is fine as a minimum period of observation. It is not evidence. |
| (2) Beat Nifty over 12 months | The backtest beat Nifty in only 4 of 8 calendar years. A strategy exactly as good as its backtest fails this 35% to 50% of the time, and a weak strategy can pass it by luck. |
| (3) Drawdown no deeper than -25% | The backtest of these exact settings had -30.3% out of sample and -42.8% in sample. The criterion fails the backtest itself, so it contradicts (4). |
| (4) Within reach of the backtest | It is undefined, and the backtest uses different exit rules (stop at the touch against the next open). Until R1-12 exists there is nothing correct to compare with. |
| (5) Process | Sound. Keep it. |

## Principle

Paper trading can check the **implementation** with certainty in 12 months. It can check the
**edge** only for gross inconsistency. Evidence for the edge has to come from the long backtest
under paper rules (R1-12 mode C), and later with corporate-action adjustment and delisted shares.

## Proposed text

> Live trading may be considered only if ALL hold at the review:
>
> 1. **Minimum period:** at least 12 months of paper trading and at least 50 closed trades. This
>    checks the implementation and screens out gross failure. It cannot by itself show an edge.
> 2. **Fidelity:** replaying every paper session through the backtest simulator in paper mode
>    (R1-12 mode C), with the decisions actually made, reproduces every paper trade (symbol,
>    dates, quantity, fill prices) and total P&L within 0.25% of starting capital. Any
>    unexplained difference fails, and the 12 months restart after the fix.
> 3. **Consistency with the backtest:** over the trailing 12 months,
>    (a) excess return over Nifty 50 after costs is at or above **[X]**, the 5th percentile of
>    the reference run's rolling 12-month excess returns;
>    (b) the maximum drawdown is no deeper than **[Y]**, the 5th percentile of its rolling
>    12-month drawdowns;
>    (c) the win rate and the mean per-trade return lie inside the reference run's 90% bootstrap
>    interval for the number of trades actually closed (table below).
> 4. **Backtest evidence:** the reference run (mode C, corporate actions adjusted (B-020),
>    delisted shares included) beats Nifty 50 after costs over the full period and out of
>    sample, with per-trade t of at least 2.
> 5. **Process:** every order is decided by a person before 09:15 IST on its fill date, there are
>    no unexplained rule breaks, and every possible corporate action flagged by the guard (R0-02)
>    is reconciled.
>
> **Hard stop, at any time:** no new entries if the drawdown is deeper than **[Z]**, the 1st
> percentile of the reference run's rolling 12-month drawdowns, or if a fidelity check fails.
> Param decides whether to resume.
>
> Meeting these makes the strategy eligible for a separate go-live decision (ADR 0005). It does
> not start live trading.

## Thresholds

R1-12 (decision rule D4) supplies the final values. The indicative values below come from the
published default-settings run (2 x ATR, 60 sessions, no regime filter), whose stdev is a lower
bound. **Do not use them as pass marks.**

| Threshold | Indicative value | Final source |
|---|---|---|
| [X] 5th percentile of 12-month excess | not computable from 8 calendar years; the lowest calendar year was -41.8 points | R-C rolling windows |
| [Y] 5th percentile of 12-month drawdown | deeper than -30.3% (out-of-sample maximum for these settings) | R-C rolling windows |
| [Z] 1st percentile of 12-month drawdown | deeper than [Y] | R-C rolling windows |
| Win rate band, 50 trades | 15.5% to 35.7% | R-C bootstrap |
| Win rate band, 100 trades | 18.4% to 32.8% | R-C bootstrap |
| Mean per-trade return band, 50 trades | -3.5% to +7.6% | R-C bootstrap |
| Mean per-trade return band, 100 trades | -1.9% to +6.0% | R-C bootstrap |

## Options for Param

1. **Adopt the proposed text** (recommended). The drawdown bound becomes [Y], taken from the
   backtest.
2. **Adopt it but keep a fixed -25% drawdown limit.** Then lower the risk per trade so the
   reference run's [Y] is no deeper than -25%. That changes the account's settings, so it means
   a new account and a new start date, since settings must not change mid-run.
3. **Keep the current text.** Accept that (2) and (3) can fail a strategy that works, and that
   (4) stays undefined until R1-12.

## Governance

- Changing the criteria now is a change of **method, not of results**: no trade has closed
  (account 1 has 10 orders approved for the 2026-11-27 open, and none filled yet when this was
  written).
- **Proposed rule:** after this amendment, the criteria are frozen from the first closed trade.
  Any later change needs a new account and a new start date. Otherwise the criteria could be
  fitted to the results.
- **Until R1-12 reports:** [X], [Y] and [Z] stay as named placeholders. Write the numbers into
  the account the day the results note is merged, before any review.
