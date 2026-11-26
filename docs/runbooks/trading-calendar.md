# Runbook: NSE trading calendar

The trading calendar decides which days NSE equities trade. It fails closed: a year with no
`COVERS` row makes every market-data check throw `CalendarNotCoveredException` (ADR 0012).

- File: `src/main/resources/calendar/nse-calendar.csv` (packaged in the jar).
- Loaded once at startup. Changes take effect only after a restart.
- Override: set `auratrade.marketdata.calendar-file` to another location, for example
  `file:/opt/auratrade/nse-calendar.csv`. Any Spring resource URL works.
- Alerts: logger `com.auratrade.marketdata.CalendarAlerts`, checked at startup. It warns if the
  current year is not covered and, from 1 December, if the next year is not covered.

## Source

Only the official NSE list: the "Holidays" page under Resources, Exchange Communications, product
"Equities", tab "Trading Holidays" (www.nseindia.com, which redirects to www.nseindia.in as of
2026-11-26), or the NSE circular for the year. Never fill it from memory, a blog or an AI assistant
without checking each row against that page.

## Add a new year (every December)

1. Open the official page for the new year, product "Equities", tab "Trading Holidays".
2. On a branch, add a comment block with the source URL and the date you read it, then:
   - one `HOLIDAY,yyyy-mm-dd,description` row per weekday holiday;
   - one `SPECIAL,yyyy-mm-dd,description` row per session on a normally closed day (for example
     Muhurat trading on a weekend);
   - holidays on Saturday or Sunday need no row.
3. Add `COVERS,yyyy` last, only when every row is in. A partly filled year is worse than none.
4. Check each date's weekday against the page, run `./mvnw verify`, open a PR, deploy.
5. Confirm the startup log no longer shows a `CalendarAlerts` warning for that year.

## Verify a past year against NSE's archive

Annual lists miss later changes: election holidays, declared public holidays, and special live
sessions on Saturdays or Sundays (disaster-recovery drills, Budget day). In 2024 alone there were
five. NSE publishes a Capital Market bhavcopy for every day it trades, so the archive is official
evidence of what actually happened (available from 2024-01-01).

1. In Git Bash: `scripts/calendar/probe-nse-trading-days.sh 2027-01-01 2027-12-31 days.csv`
   (one request per date, about 2 minutes per year).
2. `grep -hE "^(HOLIDAY|SPECIAL)," src/main/resources/calendar/nse-calendar.csv > cal.csv`, then
   `awk -F, -f scripts/calendar/reconcile.awk cal.csv days.csv`.
3. Each line printed is a disagreement. Find the NSE circular for it, add the row with the
   circular reference, and re-run until nothing is printed. Update the day counts in
   `NseCalendarFileTest`.

Years before 2024 are in `docs/calendar/nse-holidays-draft.csv` (annual circulars only) and are
not loaded until reconciled against another official source, for example index history.

## Urgent change (holiday announced at short notice)

NSE can declare a holiday by circular a few days ahead (for example the 15 Jan 2026 election
holiday). If the normal PR and deploy cannot land in time:

1. Copy the current CSV to the server, add the new `HOLIDAY` row, and set
   `auratrade.marketdata.calendar-file=file:/path/to/that/copy`.
2. Restart the app and check the startup log.
3. Make the same change in git through a PR the same day, deploy it, and remove the override.
   The override must not outlive the PR; otherwise the server and git drift apart.

If a holiday is missed entirely, candles for that day will be absent and the validator will raise
MISSING_TRADING_DAY warnings for the following day. Add the holiday, restart, and re-run the check.
