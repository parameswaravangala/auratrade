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
| B-030 | Compare snapshot and historical closes for a few weeks (the correction backfill overwrites the quote bar; log or store the difference first). If they always match, drop the correction (ADR 0025). Also: while another backfill is open the correction is skipped and no later run makes it up (only a WARN and the run detail); record skipped sessions if the correction stays | Snapshot close equal to the final close is unverified | A few weeks of evening runs |
| ~~B-028~~ | Done 2026-11-27 (ADR 0025): the evening run takes the day from v3 OHLC quotes, about 7 calls. Was: Shorten the nightly EOD run: it makes two Upstox calls per share (yesterday's final bar from the historical endpoint, today's from the intraday endpoint), about 5,600 calls and 1 h 45 min at our rate limit. Check Upstox's batch market-quote endpoint for today's OHLC (verify its limits and whether it gives the official close) | Paper trading needs today's candles soon after the close | Before paper trading runs for long |
| ~~B-029~~ | Done 2026-11-27 (ADR 0025): snapshot bars name their session, so the clock no longer decides the day. Was: An EOD run that crosses midnight IST loses the session's bar for every share it reaches after midnight: the intraday endpoint then serves the new day, and the historical endpoint did not yet have the old one (seen 2026-11-26: a run started 22:56 IST loaded 1,691 of 2,786 shares, A to M). The coverage gate (now 0.9) makes such a run fail closed. Fix properly with B-028 (shorter run), or by having the job tell the provider which session it wants so an after-midnight run waits for the historical endpoint instead | Startup catch-up late in the evening hits it | B-028, or find out when Upstox publishes the previous day to the historical endpoint (unverified) |
| B-027 | Alert delivery (ADR 0017 JobAlerts and CalendarAlerts are log lines today): a channel behind an interface (Telegram suggested; email or ntfy as alternatives), alerts for FAILED or PARTIAL job runs, stale data (no fresh EOD candles by a deadline), a provider 401, and the Upstox token expiring (the JWT carries `exp`: 2027-11-26) with 30 days' notice | Nobody sees log-only alerts | Channel choice; parked 2026-11-26 to build features first |
| B-031 | Authentication and an audit trail for order decisions: today `/api/v1/paper` (approve, reject, bulk decide) exists only in the dev profile and records `decided_by` as free text with `decided_at`, so nothing ties a decision to a person. Needed: real sign-in, the decider taken from the authenticated principal (not the request body), and an append-only audit of every decision and edit (quantity, limit) | The UI plan said "auth and an audit trail before any approve button exists"; the Approval queue now has those buttons (dev only) | Before any non-dev profile serves the paper API, and before any live trading (Phase 2) |

## From the business-user review (2026-11-27)

Details, evidence and trade-offs: `docs/research/2026-11-27-business-user-review.md` (pain points P1 to P20). B-032 to B-039 are left free.

| ID | Item | Why | Unblocked by |
|---|---|---|---|
| B-040 | "Today" strip on the Command center: paper orders waiting with the time left to 09:15 IST, last EOD session and coverage, failed or abandoned job runs, each linking to its screen | 10 proposals expired unseen is the likeliest way to break go-live criterion (5) (P1) | PR #30 (paper client in `ui/src/api`) |
| B-041 | Grounded copilot: Java builds a fact sheet (last session, close, SMA 50/200, RSI, ATR, 52-week range, screens matched, open order or position), passed as data; reply numbers checked against it | Today it gets only the symbol and answers from model memory (P6) | Same validation pattern as B-007 |
| B-042 | Store shortlists per screen and session after the EOD job (`screen_run`), UI reads them with "computed at" | Shortlists take 14 s to 2 min on every visit (P11) | None |
| B-043 | `GET /api/v1/marketdata/freshness` (sessions behind, via `TradingCalendar`) and `GET /api/v1/calendar/coverage`; then the Data calendar screen with backfill validation failures | Freshness in calendar days; failed backfill shares invisible (P8, P19) | None |
| B-044 | Dossier lenses from Java indicators (`/api/v1/instruments/{symbol}/indicators`) and "where this share appears" (shortlists, orders, positions) | Lenses disabled; no link from a share to its signals (P9) | None |
| B-045 | Approval queue batch summary (total value, total risk, cash after fills, positions used) and order value against 20-session average traded value | Day one put 84% of capital and 10% risk on in one batch without a total (P14, P15) | None (PR #30 merged the paper client) |
| B-046 | Paper portfolio against Nifty and go-live progress per criterion, with criteria stored as thresholds beside the text | No benchmark line; criteria are prose (P16) | None (PR #30 merged the paper client) |
| B-047 | Supported recovery: mark a run failed with a reason (dev endpoint), run buttons for every job, runbook "EOD run failed or partial" | Run 20 was fixed by hand in SQL (P18, P20) | None |
| B-048 | Alias curation: log unresolved names, propose candidates from the fuzzy matcher, confirm in a small UI | HDFC, SBI, L&T, Zomato do not resolve; aliases only via `/dev` POST (P7) | None |
| B-049 | Playbook lab "use paper account settings" preset; show the account's own backtest beside its forward results | Lab defaults differ from account 1 in four settings (P12) | None (PR #30 merged the paper client) |
| B-050 | Say when LLM spend is simulated (pretend prices on a local model) | Budget shows dollars although nothing was spent (P5) | None |
| B-051 | Reconcile account 1's go-live criteria with its backtest: -42.8% drawdown 2019-2026 (-30.3% out of sample) against the -25% criterion. Change the criterion, add a drawdown brake or lower risk and re-run the sweep, or call it a learning run | Criteria (3) and (4) cannot both hold if the backtest is right (P13) | Your decision; record it (ADR 0005) |
| B-052 | Liquidity and gap risk: cap order value at a fraction of average traded value; report gap-through-stop losses separately | 64 of 193 stop exits gapped through the stop; worst trade 21,538 shares near INR 20 (P13, P15) | None |

B-027 (alert delivery) should move up: paper trading now depends on the evening jobs every day, and their failures were found the next morning (P17).
