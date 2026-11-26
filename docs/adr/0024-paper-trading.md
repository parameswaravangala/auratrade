# ADR 0024: Paper trading with a human approving every order

- Status: accepted
- Date: 2026-11-26
- Implements: ADR 0005 (paper first, human approval always)

## Context
Backtests (ADR 0021 to 0023) point to settings worth trying forward. The next step is to trade
them on paper, live, with the same rules, and with a person deciding every order.

## Decision
- A paper account (V9 `paper_account`) holds a screen and its settings (capital, positions, risk
  per trade, maximum position, ATR stop multiple, holding limit, regime filter, slippage). It cannot
  be opened without go-live criteria written up front (ADR 0005).
- Code proposes, a person decides. The `paper-trading` job (20:15 IST, after the EOD candles)
  processes each session in one transaction per account:
  1. PROPOSED orders for that session expire (not decided before the open);
  2. APPROVED orders fill at that session's open with slippage and Zerodha's published delivery
     charges (`CostModel`), sells first; a missing candle or too little cash makes them UNFILLED
     with the reason;
  3. a low at or below a position's stop, or reaching the holding limit, proposes a SELL for the
     next session;
  4. if the regime filter allows (Nifty 50 above its 200-session average), the account's screen
     runs as of that session and BUYs are proposed for free slots, sized like the backtest;
  5. equity is recorded at the close (`paper_equity`), closed trades go to the journal
     (`paper_trade`).
- Decisions (approve or reject, with who and why) are accepted only before 09:15 IST on the fill
  date. Nothing executes without an approval; missed evenings are caught up in date order and
  their proposals expire.
- Trade values are booked in paise, as on a contract note, so cash never accumulates sub-paise
  rounding.
- API: `/api/v1/paper` (accounts, orders, approve, reject, trades, equity). Dev profile only until
  authentication exists, because it can approve orders.

## Consequences
Forward results come from the same rules as the backtests, with real decisions in the loop. Stops
are not intraday orders: a breach is seen at the close and exited at the next open, usually a
worse price than the backtest assumes. No broker connection exists; moving to live trading needs
the written criteria to be met and a separate decision (Phase 2).
