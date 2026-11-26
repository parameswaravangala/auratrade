# ADR 0025: The evening run takes the day from quote snapshots

- Status: accepted
- Date: 2026-11-27
- Amends: ADR 0017 (scheduled jobs), backlog B-028 and B-029

## Context
The EOD run made two Upstox calls per share (history for yesterday, the intraday endpoint for
today): about 5,600 calls and 1 h 45 min at Upstox's published 30-minute limit. On 2026-11-26 a
late start crossed midnight IST, after which the intraday endpoint served the new day and the
historical endpoint did not yet have the old one: 1,095 shares and NIFTY lost the session.

## Facts checked
- Upstox docs (read 2026-11-27): GET /v3/market-quote/ohlc takes up to 500 instrument keys with
  `interval=1d`; the Analytics token covers Market Quote. The docs do not say what the values are
  after the close, and describe `prev_ohlc` both as the previous session and the previous minute.
- Live call at 04:42 IST on 2026-11-27: `live_ohlc` was the 26 Nov session, `ts` its IST midnight,
  `prev_ohlc` null. For ADANIPOWER it matched the stored 26 Nov bar exactly (open, high, low, close,
  volume). NIFTY works the same way (volume 0). The data map is keyed `NSE_EQ:SYMBOL`;
  `instrument_token` holds our provider key.
- Unverified: whether the snapshot close always equals the final historical close.

## Decision
- `SessionBarProvider` is an optional capability of a `MarketDataProvider`: the latest session's
  bar for up to N keys per call. Upstox implements it with the v3 OHLC quote.
- The EOD run, when the provider has snapshots: benchmarks first and required, then the whole
  universe in calls of 500, about 7 calls in all. A bar is stored only if it is dated the window's
  last session, so the clock no longer decides the day. Earlier days of a catch-up window still
  come from history, one call per share.
- Snapshot bars are provisional. A successful run creates a one-day backfill (ADR 0018) for the
  previous session, which re-reads it from history in the background and overwrites the bar. Paper
  trading does not wait for it. If a backfill is already open, no correction is made and the run
  detail says so. `auratrade.jobs.eod-candles.correct-previous-session=false` turns this off.
- Providers without snapshots keep the old path.

## Consequences
The evening run takes about a minute instead of 1 h 45 min, and a run after midnight still loads the
right session until the next open. After the open the snapshot holds the new, unfinished session:
the run sees that in the benchmark probe and takes the whole window from history, one call per
share, as before. The correction backfill costs one call per share
overnight. Comparing snapshot and historical closes over a few weeks will show whether the
correction is needed at all.
