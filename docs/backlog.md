# AuraTrade backlog

Items parked outside the current sprint. Keep each one small and say what unblocks it. Move an item into a sprint plan when it is picked up.

## From Sprint 1 (LLM gateway and evals)

| ID | Item | Why | Unblocked by |
|---|---|---|---|
| B-001 | Triage prompt v3: extract company names as written in the text, and resolve them to NSE symbols in code | A 3B model invents tickers (SPML, ASP, LARSEN&TUBRO, BAJAJFINANCE). Resolving in code makes wrong tickers impossible by construction | Resolver done (ADR 0014, Sprint 2); prompt v3 and wiring the triage agent remain (Sprint 4) |
| B-002 | Record call settings (temperature, JSON mode, seed, prompt file) in every eval report | Runs A, B and C had to be told apart by guessing | None |
| B-003 | Add 3 or 4 more prompt-injection cases to the golden set | Two cases make injection_resisted a coarse 0.50. n21 succeeded in every run | None |
| B-004 | Review labels n12, n16, n18 (mixed or ambiguous news); test the hypothesis that v2 leans negative on mixed news by adding a mixed-news example | 3 of 5 sentiment errors came from these cases | Your review |
| B-005 | Run prompt v2 twice to confirm temperature 0 gives repeatable scores, then save a baseline | Single runs are noisy; no baseline exists yet | None |
| B-006 | Compare a larger local model on the same golden set | Quality per rupee, and injection resistance | Enough RAM |
| B-007 | Triage output is untrusted data: design the validation step (schema, symbol resolution, never a direct trigger for action) | Prompt wording did not stop injection n21 | Sprint 4 (triage agent) |
| B-008 | Langfuse tracing | Deferred in ADR 0008 | Sprint 4 or 5, when real agents exist |
| B-009 | One-hour LiteLLM spike | Test the hypothesis in ADR 0009 | Any time before Sprint 5 |
| B-010 | Claude adapter, prod profile, real prices (including Haiku cache prices) | Paid key not needed yet | Decision to spend |
| B-011 | Update the Architecture and Design doc: CallOptions, LOCAL floors off by default, evals, calendar fail-closed | The design doc is behind the code | None |

## From Sprint 2 (market data), planned for later sprints

| ID | Item | Why | Unblocked by |
|---|---|---|---|
| B-020 | Corporate-action adjustment (splits, bonuses, dividends) | Raw prices jump at splits; EXTREME_MOVE warnings will fire until adjusted | Corporate-actions data source |
| B-021 | Intraday gap detection (missing candles inside a session) | Only daily gaps are checked today | Intraday data (paid plan) |
| B-022 | Kite as a second `MarketDataProvider` (ADR 0016): Kite instrument feed mapped onto ISIN instruments by symbol and series, `/auth/kite/login` and callback with the daily token stored encrypted, a morning "login needed" alert, historical candles, later websocket ticks | User accepts one Kite login per day; production provider not decided | Kite Connect subscription (INR 500/month) and the production decision |
| B-023 | Static-IP and order-placement requirements | Live phase only | Phase 2 |
| B-026 | Calendar and backfill before 2018: earlier NSE circulars are harder to find, and NSE's new-format bhavcopy archive starts in 2024; reconcile against Nifty 50 history as for 2018 to 2023 (runbook) | Calendar covers 2018 to 2026 only | Need for longer history |
| B-030 | Compare snapshot and historical closes for a few weeks (the correction backfill overwrites the quote bar; log or store the difference first). If they always match, drop the correction (ADR 0025) | Snapshot close equal to the final close is unverified | A few weeks of evening runs |
| ~~B-028~~ | Done 2026-11-27 (ADR 0025): the evening run takes the day from v3 OHLC quotes, about 7 calls. Was: Shorten the nightly EOD run: it makes two Upstox calls per share (yesterday's final bar from the historical endpoint, today's from the intraday endpoint), about 5,600 calls and 1 h 45 min at our rate limit. Check Upstox's batch market-quote endpoint for today's OHLC (verify its limits and whether it gives the official close) | Paper trading needs today's candles soon after the close | Before paper trading runs for long |
| ~~B-029~~ | Done 2026-11-27 (ADR 0025): snapshot bars name their session, so the clock no longer decides the day. Was: An EOD run that crosses midnight IST loses the session's bar for every share it reaches after midnight: the intraday endpoint then serves the new day, and the historical endpoint did not yet have the old one (seen 2026-11-26: a run started 22:56 IST loaded 1,691 of 2,786 shares, A to M). The coverage gate (now 0.9) makes such a run fail closed. Fix properly with B-028 (shorter run), or by having the job tell the provider which session it wants so an after-midnight run waits for the historical endpoint instead | Startup catch-up late in the evening hits it | B-028, or find out when Upstox publishes the previous day to the historical endpoint (unverified) |
| B-027 | Alert delivery (ADR 0017 JobAlerts and CalendarAlerts are log lines today): a channel behind an interface (Telegram suggested; email or ntfy as alternatives), alerts for FAILED or PARTIAL job runs, stale data (no fresh EOD candles by a deadline), a provider 401, and the Upstox token expiring (the JWT carries `exp`: 2027-11-26) with 30 days' notice | Nobody sees log-only alerts | Channel choice; parked 2026-11-26 to build features first |
