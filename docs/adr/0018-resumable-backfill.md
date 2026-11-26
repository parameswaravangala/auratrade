# ADR 0018: Resumable candle backfill

- Status: accepted
- Date: 2026-11-26

## Context
History for about 2,800 shares has to be loaded once, and again whenever a provider changes or a
calendar year is added. That is thousands of provider calls (one per share per request window),
roughly an hour at the Upstox rate we use. A single long call would lose everything on a crash,
restart, token expiry or provider outage.

## Decision
- A backfill is a request (V8 `backfill_request`) for a date range and share series, split into one
  item per share (`backfill_item`). Items are snapshotted from the active universe when the request
  is created.
- The `backfill` job (ADR 0017 runner, every 15 minutes and at startup) works through pending items
  in batches of 400. Progress is stored per item, so work resumes where it stopped.
- Each item is one provider request for the whole range; candles go through the same validation
  as daily ingestion and are stored per source (ADR 0016).
- Failure rules: validation errors mark the item FAILED (a data problem; retrying will not help).
  Other errors are retried up to 3 attempts. An auth or rate-limit error stops the batch at once and
  leaves items pending, because every item would fail the same way.
- Fail closed: a request is refused unless the calendar covers every year in the range, if it
  reaches today (the daily job owns today), or if another request is still open.
- A finished request is DONE or DONE_WITH_FAILURES with the failed symbols and reasons listed;
  requests can be cancelled.

## Consequences
History loads unattended and survives restarts and outages. Today the calendar covers 2024 to
2026, so backfills are limited to those years until 2018 to 2023 are reconciled. The daily job and
the backfill share one provider instance and therefore one rate limiter, so running both never
exceeds the provider's limits; the backfill simply takes longer.
