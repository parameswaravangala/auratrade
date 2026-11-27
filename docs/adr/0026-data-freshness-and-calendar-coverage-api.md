# ADR 0026: Data freshness and calendar coverage API

- Status: accepted
- Date: 2026-11-27
- Roadmap: R1-09 (backlog B-043). Contract agreed with UI Cockpit before the build.

## Context
The UI showed data age in calendar days, which is wrong over weekends and holidays, and it had
no view of what the trading calendar covers. These are the first read-only `/api/v1` endpoints
outside paper trading.

## Decision
- `GET /api/v1/marketdata/freshness`: the expected session is the newest trading day whose EOD
  cutoff (`auratrade.jobs.eod-candles.cutoff`, 19:00 IST and unverified, so configurable) has
  passed. For the selected candle source it reports the newest session, the active shares (the
  EOD universe) current or behind, bucketed 1, 2-5 and 6+ sessions, shares with no data, the 20
  most behind, the benchmarks' lag, and other sources' newest session. Lag is counted in trading
  sessions with `TradingCalendar`, never calendar days. Per-share newest bars are read with one
  primary-key probe each.
- `GET /api/v1/calendar/coverage`: covered years, first and last covered date, today, last
  closed and next session, next-year coverage, and the same warning as `CalendarCoverageCheck`,
  plus per-year trading-day counts and the named holidays and special sessions.
- Fail closed: an expected session in an uncovered year is a 409 problem
  (`type` `https://auratrade.dev/problems/calendar-not-covered`, `code`
  `CALENDAR_NOT_COVERED`, `year`); a gap reaching into an uncovered year is reported as null,
  never guessed. Coverage itself never fails: reporting an uncovered year is its purpose. An
  empty universe is a 503 (`NO_INSTRUMENTS`).
- Served in every profile: read-only, no order actions, no secrets. The paper API stays
  dev-only until authentication (B-031, R1-04/R1-05).

## Consequences
The UI can say "1 session behind" correctly across holidays and show the calendar's end before it
bites. Backfill validation failures per request, share and rule (part 2 of B-043) are a separate
change.
