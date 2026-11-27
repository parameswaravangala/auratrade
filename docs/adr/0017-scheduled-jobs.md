# ADR 0017: Scheduled jobs with run history, state-driven catch-up and a lease lock

- Status: accepted
- Date: 2026-11-26

## Context
Instrument loads and end-of-day candles must run every day without anyone starting them. Runs will
be missed (app down, provider outage, expired token) and must heal on their own, without doing the
same work twice or flooding a provider after a long outage.

Options: Spring's `@Scheduled` (no persistence, missed runs are never replayed), Quartz (JDBC store,
clustering, misfire policies that know a trigger time passed but not whether the work succeeded),
JobRunr (dashboard and persistence, new dependency).

## Decision
- Spring `@Scheduled` with cron in IST triggers jobs; it holds no state.
- `job_run` (V7) records every run: window of business dates, status (RUNNING, SUCCEEDED, PARTIAL,
  FAILED), item counts, detail. Each job plans what is still owed from the end of the last
  SUCCEEDED or PARTIAL window, so missed and failed days are caught up on the next trigger,
  whatever triggered it. A catch-up runs right after startup.
- Catch-up is capped (EOD: 30 trading days by default); an older gap is noted and alerted for a
  deliberate backfill, so a long outage never turns into thousands of provider calls at startup.
- `job_lock` holds a lease per job (default 10 minutes, renewed while running; see the amendment) so a job never runs twice at once, across
  threads or instances; a crashed holder's lease expires. Work is idempotent (upserts).
- FAILED and PARTIAL runs and planning failures go to the JobAlerts logger; a real channel comes
  in the alerting slice.
- Jobs: `instrument-master` daily 07:00 IST (the Upstox file refreshes around 6 AM; only today is
  ever owed), `eod-candles` daily 19:30 IST for trading days after a 19:00 cutoff. The cutoff is
  not verified with Upstox: if most shares have no candle for the day, the run fails and retries.
- Schedules are off by default (`auratrade.jobs.enabled`), on in the dev and prod profiles.

## Consequences
Operations are visible in one table and heal themselves. A full EOD run is one request per share
(about 2,800 for series EQ); at the Upstox limit we use (1,600 per 30 minutes) that is roughly an
hour. A batch quote endpoint could shorten it later. Trading-day logic depends on the calendar
covering the year, so an uncovered year fails closed and alerts.

## Amendment (2026-11-27): heartbeat lease and abandoned runs
A process killed during a run (common with `spring-boot:run` on Windows) kept its 3-hour lease
and left its `job_run` row RUNNING forever; every trigger of that job, startup catch-up included,
was skipped for 3 hours with only an INFO line (seen 2026-11-27, business-user review P2).
Now a heartbeat renews the lease every third of its length while the job runs, and the default
lease is 10 minutes. The first trigger that takes the lease closes any run of that job still
RUNNING as FAILED ("abandoned"), which is safe because holding the lease proves no live process is
running it; the window stays owed and the run heals as before. A shutdown hook was not chosen
because it does not run when the process is killed.
