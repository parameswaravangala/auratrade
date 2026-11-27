# ADR 0039: Point-in-time proxy universe for backtests

- Status: accepted
- Date: 2026-11-28

## Context
The swing-scanner gate (`docs/product/swing-scanner-plan.md`, section 3) runs every strategy on a
universe. The spec asks for NIFTY 500 with membership history. We have no free, verified source of
that history (plan decision D5), and the official list's download terms are unchecked. Backtests on
today's list alone would look ahead, because the list is chosen with knowledge of the future.

## Decision
- A pure `ProxyUniverse` in the new `com.auratrade.universe` package. It ranks every share by the
  median daily traded value (close x volume) over the last 126 sessions and keeps the top 500.
- Monthly rebalance on the last session of each calendar month. The decision reads only the window
  ending on that session. A membership takes effect on the next session. The last rebalance in the
  calendar has no next session, so it is not emitted.
- Eligibility: a candle on every session of the window (a missing bar excludes the share; it is
  never imputed) and a median above zero. Ties break by instrument id, so output is deterministic.
- Median rather than mean: one spike day does not make a share look liquid.
- `effectiveOn(memberships, session)` gives the membership in force on a session, the point-in-time
  lookup for backtests.
- Persistence and the scheduled job are not in this slice. The builder takes candles and the
  session calendar as input and does no I/O.

## Consequences
- Backtests can select a universe as it was known on each date, with no look-ahead and no list
  picked from the future. A test checks that a later candle does not change an earlier membership.
- It is not the official NIFTY 500. It has no free-float, listing-age or index-rule filters, so
  its members can differ from the index. Every research note says so.
- Survivorship bias remains: shares delisted before the data was loaded are absent. This is the
  next data gap (backlog priority 8, delisted instruments).
- Cost: about 3,600 shares by 126 sessions for each of about 96 monthly rebalances. It is one
  pass per rebalance and fits in memory per run.
- The official list can replace the proxy for the live shortlist once its terms are verified (D5).
