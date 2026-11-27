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

Known gap (2026-11-27): decisions are not authenticated. `decided_by` is free text from the
request and there is no separate audit log, which is acceptable only because the API is served
in the dev profile alone. Authentication and an append-only audit trail (backlog B-031) are
required before the API is served in any other profile, and before live trading.

## Amendment 2026-11-27: corporate-action guard (R0-02)
Context: prices are not adjusted for splits and bonuses yet (B-020). On an ex-date the open falls
by the ratio, the low passes any stop, and the paper account would book a loss that never
happened (research note `docs/research/2026-11-27-paper-account-validity.md`, recommendation R1).

Decision:
- `CorporateActionGuard` flags a session whose open is 15% or more below the previous close, or
  within one percentage point of the price change of a common split or bonus (1:10 to 4:1
  bonuses; 1:2, 1:4, 1:5, 1:10 splits). A flag is a question for a person, not a fact.
- On a flag for a held position, no automatic STOP or TIME exit is proposed. A
  `POSSIBLE_CORPORATE_ACTION` review (V11 `paper_review`) records the previous close, the open,
  the gap and the fitting ratios. Until it is resolved the position gets no automatic exit and
  is marked at the pre-gap close, so no drawdown is booked.
- An approved order whose fill session opens with a flagged gap is UNFILLED with the reason (and,
  for a held share, a review is opened). Fails closed: nothing trades at an unexplained gap.
- A person resolves the review after checking the exchange's corporate actions:
  `NO_CORPORATE_ACTION` (normal rules resume next session) or `ADJUSTED` with a shares factor
  (quantity times factor, entry and stop divided by it; a fractional result is refused). API:
  `GET /api/v1/paper/accounts/{id}/reviews`, `POST /api/v1/paper/reviews/{id}/resolve`, dev
  profile only (B-031).

Consequences: some real drops of 15% or more, and drops near a ratio such as -10% (1:10 bonus),
also wait for a person; that delay is the price of never booking a fake loss. The backtest's
corporate-action rule (one-day move over 40%) differs and is to be aligned in R1-12. Proper
adjustment from an ex-date feed remains B-020.
