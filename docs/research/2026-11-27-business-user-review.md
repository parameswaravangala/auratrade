# Business-user review: a swing trader's first morning (2026-11-27)

Research record, not investment advice. One session, 05:20 to about 06:00 IST on Friday
2026-11-27, acting as a retail swing trader who uses AuraTrade for research and paper trading.

## Setup and scope

- Backend: the dev-profile instance already running on 8080 from the main checkout (branch
  `feat/paper-approve-edits`: PR #29 and PR #28's quote-snapshot EOD, both merged since). A second
  instance was not started, because two schedulers on one database would compete for the Upstox
  rate limit.
- UI: `main` on port 5174 (a local launch config), and PR #30 (`feat/ui-paper-trading`, the
  approval queue and paper portfolio) on 5173 from another worktree.
- Paper orders were only read. Account 1 (`relative-strength-1`) is the go-live track record, so
  no proposal was approved or rejected, and no dev account was opened without asking.
- Evidence is quoted from API responses and the rendered page text. Screenshots that show prices
  are not committed (exchange data stays out of git); they are in the session's scratch folder.

Severity: **High** blocks a journey or risks a wrong decision; **Medium** costs time or trust;
**Low** is polish.

## Journeys and pain points

### 1. Morning check (Command center)

| # | Pain point | Severity |
|---|---|---|
| P1 | Nothing on the Command center says that 10 paper orders wait for a decision before 09:15 IST | High |
| P2 | A backend restart leaves a job run RUNNING forever and holds its lock for 3 hours | High |
| P3 | The pipeline card says "0 rejected" while the job detail reports 1 rejected row | Low |
| P4 | Failures show raw Java exception class names | Low |
| P5 | The LLM budget shows "$0.0034 this month" although nothing was spent: local calls are priced at pretend Claude prices, and the page does not say so | Low |

**P1. The 09:15 deadline is invisible.** Steps: open `/` at 05:20 IST. The page shows pipeline,
budget, timeline, resolver, narrative and universe. The only trace of paper trading is the
narrative line "paper-trading succeeded (by hand): ... 10 entries proposed". On `main` there is no
way to decide them in the UI at all (the Approval queue is a "designed · not built yet" page whose
"needs first" list still names the paper order model). PR #30, merged during this review, builds the queue, but
its Command center does not link to it either (searched the page for "proposed": no match).
Evidence: `GET /api/v1/paper/accounts/1` returns 10 PROPOSED BUYs, each `"decideBy":
"2026-11-27T03:45:00Z"`. Missing the deadline expires them silently and, under go-live criterion
(5), hurts the track record. No alert reaches the user (B-027 is parked).

**P2. Zombie job runs after a restart.** Steps: let a job start, restart the backend, open the
Command center. Evidence: `GET /dev/jobs/runs` at 05:31 IST:
`{"id":27,"jobName":"backfill","trigger":"SCHEDULE",...,"status":"RUNNING","startedAt":
"2026-11-26T23:45:00Z","finishedAt":null}`; the JVM now serving 8080 started at 05:16:04 IST, a
minute after that run began, and no later backfill run appears although the job is scheduled
every 15 minutes. Cause: `JobRunner` takes a lease of `auratrade.jobs.lease` (PT3H) and only
releases it in `finally`; a killed process keeps the lease until it expires, and nothing ever
closes the run row. The narrative shows "backfill running: 0 ok" indefinitely, and for 3 hours
every trigger of that job, including startup catch-up of `eod-candles` after a crash in the
evening, is skipped with only an INFO log line. The hand-written note on run 20
("marked FAILED by hand 2026-11-27") shows recovery currently needs SQL.

**P3.** `PipelineMap.tsx` shows `itemsFailed` as "rejected", but `InstrumentMasterJob` reports
rejected rows only in the detail (by design: they must not make the run PARTIAL). Evidence: card
"10,049 ok · 0 rejected"; detail "... 1 rejected [61GJ28 (NSE_EQ|IN1520250085): invalid ISIN
'IN1520250085']".

**P4.** Narrative: "eod-candles failed (by hand): 0 ok. java.lang.IllegalStateException: benchmark
NIFTY has no candle for 2026-11-26 yet". `JobRunner` stores `e.toString()`.

**P5.** `application.yml` gives HEAVY and LIGHT pretend prices so budget logic behaves as in
production; `/dev/llm/spend` reports `"monthSpendUsd":0.004964`. The ring should say "simulated
(local model, nothing spent)".

### 2. Find an idea (resolver, dossier, copilot)

| # | Pain point | Severity |
|---|---|---|
| P6 | The copilot is not grounded: it receives only the symbol and answers from the model's memory | High |
| P7 | Common short names do not resolve, and aliases can only be added through a `/dev` POST | Medium |
| P8 | Freshness is counted in calendar days, not sessions, and nothing marks stale data | Medium |
| P9 | The dossier has no indicators and no link to screens, orders or positions for the share | Medium |

**P6.** Steps: dossier for TMCV, ask "Is TMCV above its 50-day average and what was its last
close?". `Copilot.tsx` sends `About the NSE share TMCV: <question>` and `LlmDevController` uses
the system prompt "You are a concise assistant." No candles or indicators are passed. Evidence
(curl, 36 s, `llama3.2:3b`): "I don't have real-time information. However, as of my knowledge
cutoff in December 2023 ... Websites like Zee Business, Moneycontrol, or NSE India provide
real-time stock prices." The panel's own disclaimer is honest, but the feature cannot answer the
questions a trader asks, and a larger model would answer them wrongly with more confidence.

**P7.** `GET /dev/marketdata/resolve?name=` for `HDFC`, `L&T`, `SBI`, `Zomato`: all
`{"reason":"no listed share matches"}`. `Tata Motors` resolves with score 1.0 to TMCV, whose name
in the provider file is "TATA MOTORS LIMITED"; the resolver is faithful to the file, but after a
rename or demerger a trader cannot tell whether that is the company meant. The fix is curated
aliases, which today need `POST /dev/marketdata/aliases` by hand. (Mappings such as SBI to a
symbol must be checked against the instrument file, not typed from memory.)

**P8.** Dossier: "newest bar 2026-11-26 · 1 calendar day ago (trading-day check needs /api/v1)".
On a Monday every share would read "3 calendar days ago".

**P9.** The Trend, Volatility, Volume profile and Events lenses are disabled ("Needs indicators
computed in Java"), although `screener.indicators.Indicators` already computes SMA, EMA, RSI and
ATR. The page does not say whether the share is on a shortlist, in a proposal or held.

### 3. Playbook lab

| # | Pain point | Severity |
|---|---|---|
| P10 | ETFs and other fund units are screened as company shares | High |
| P11 | Shortlists take 15 s to 2 min to appear, computed on every visit | Medium |
| P12 | The lab's defaults are not the paper account's settings | Medium |
| P13 | The paper account's settings backtest to a drawdown its own go-live criterion forbids | High (business) |

**P10.** Evidence: `GET /dev/screener/52w-high-breakout` shortlist includes `"symbol":"HEALTHY",
"name":"BIRLASLAMC - HEALTHY"` (ISIN `INF209KB10C2`). In our instrument table 367 of 2,786 active
EQ-series rows have ISINs starting `INF`; a sample of 25 are all named as AMC or ETF units
(NIFTYBEES, GOLDBEES, SBI10YGILT, ...), and only one of the 367 lacks a fund keyword in its name
(PSUBANK, "KOTAK PSU BANK", by its name an index product; unverified). `CandleSeriesReader.
forEachShare` ("every active company share") filters by series only, so the screener, the
backtests, the sweep and the paper account all see fund units. An index ETF on a
relative-strength list measured against Nifty is noise at best.

**P11.** `GET /dev/screener/52w-high-breakout?limit=10`: 122 s (while the UI made the same request);
`relative-strength-leaders`: 14 s. The panel shows a skeleton and "Waiting for the shortlist to
learn the newest stored session" with no progress. The shortlist only changes once a day.

**P12.** The lab opens on 52w-high-breakout with stop 2 x ATR, holding 60, regime off. The sweep
(docs/research/2026-11-26-sweep-out-of-sample.md) found that screen overfits and the robust
settings to be 3 x ATR, 120, regime on, which is what account 1 trades. There is no "load my
paper account's settings" and the portfolio backtest takes about 2 minutes per run (129 s).

**P13.** `GET /dev/backtest/portfolio?screen=relative-strength-leaders&from=2019-01-01&to=2026-11-26
&regime=true&atrMultiple=3&maxHold=120` (account 1's settings): CAGR 20.67% against Nifty 11.55%,
but `"maxDrawdownPct":-42.81`, and by year `2025: -3.91` (Nifty 10.51) and `2026: -17.04` (Nifty
-0.94). Go-live criterion (3) requires a drawdown no deeper than -25%, and criterion (4) requires
paper results close to the backtest. A strategy that matches its backtest is therefore likely to
fail (3); one that passes (3) probably does not match its backtest. Of 193 stop exits, 64 were
gaps through the stop (`"STOP":129,"STOP_GAP":64`), so real losses run past the planned 1% risk.
The worst trade was 21,538 shares at about INR 19.66 (ABINFRA), which raises a liquidity
question the screen's INR 1 crore filter may not answer. This is a strategy decision for the
user, not a code bug.

### 4. Paper trading (PR #30 UI, read only)

| # | Pain point | Severity |
|---|---|---|
| P14 | The queue has no batch view: total value, total risk, cash left, concentration | Medium |
| P15 | No liquidity context per order (order value against average daily traded value) | Medium |
| P16 | Portfolio has no Nifty line and go-live criteria are prose, not tracked progress | Medium |

**P14.** The 10 proposals add up to INR 8.38 lakh (84% of capital) and INR 99,614 of risk to stop
(about 10% of equity), all on day one, in 10 small and mid caps. Each card shows its own value
and risk; the sum, the cash left and any sector clustering are not shown. PR #29 and #30 already
cover approving a smaller quantity, a limit and bulk decisions, so these are not repeated here.

**P15.** A card shows "ref price ₹174.94 ... value about ₹1,38,028.00" for TFCILTD, but not how
that compares with the share's typical daily turnover.

**P16.** Portfolio shows "SINCE START +0.00%" with no benchmark, and the five go-live criteria as a
paragraph. The trader cannot see "4 of 50 trades, 1 of 12 months, drawdown -3% of -25% allowed".

### 5. Operations (EOD, backfill, failures, recovery)

| # | Pain point | Severity |
|---|---|---|
| P17 | Failures are only log lines and narrative entries; nobody is told (B-027) | High |
| P18 | Recovery from a bad run needs SQL; there is no supported "mark failed / rerun" | Medium |
| P19 | Backfill validation failures (10 shares) have no place to be seen or resolved | Low |
| P20 | Only two jobs have run buttons on the timeline | Low |

**P17.** The evening of 2026-11-26 had a 429 (run 19), a run that crossed midnight (run 20, 61%
coverage), and two failures for a missing NIFTY bar (runs 22, 23) before a success at 04:54 IST.
All of it was found by reading the narrative the next morning. Paper trading now depends on the
EOD job finishing every evening; an unattended feed needs an alert when it does not.

**P18.** Run 20's detail ends "[marked FAILED by hand 2026-11-27: 61% coverage and no NIFTY bar;
under the 0.9 gate of PR #25 this run fails]". That was an UPDATE in psql.

**P19.** Backfill #2: `"status":"DONE_WITH_FAILURES"`, failures `AHLADA ... DUPLICATE_TIMESTAMP`,
`IDEA: NEGATIVE_VOLUME`, `MAZDOCK: NON_POSITIVE_PRICE` and 7 more. They are visible only through
`GET /dev/marketdata/backfills`; those shares silently lack history (SHORT_HISTORY skips).

**P20.** The timeline offers run buttons for `instrument-master` and `eod-candles`; `backfill` and
`paper-trading` need `POST /dev/jobs/{name}/run`.

### 6. The five unbuilt screens

Research desk, Agent observatory and Data calendar are plan pages. Approval queue and Paper
portfolio are built in PR #30. Of the remaining three, the Data calendar is the one a trader needs
first, because it answers P8 and P19 ("is my data complete and fresh?"). The Research desk depends
on news ingestion and the triage agent (B-001, B-007), and the Agent observatory matters once
real agents run.

## Proposals

Ordered by value for the paper-trading phase. B-numbers are added to `docs/backlog.md`.

### Quick wins (built in this review, one PR each)

| Item | Fixes | PR |
|---|---|---|
| Job leases renewed by a heartbeat and much shorter (PT10M); a run left RUNNING by a stopped process is closed as FAILED ("abandoned") when the next trigger takes the lease; failure details without Java package names | P2, P4 | #31 |
| Screens, backtests and paper entries skip fund units (ISIN prefix INF) | P10 | #32 |
| Pipeline card shows the rejected count from the run detail | P3 | #33 |

Trade-offs. Heartbeat versus "release on shutdown": a shutdown hook does not help when the process
is killed, which is how `spring-boot:run` usually ends on Windows; a heartbeat makes a dead
holder's lease expire within minutes and keeps the at-most-once guarantee. Filtering fund units by
ISIN prefix versus by the provider's instrument type: the Upstox file labels them `EQ` like
company shares (checked in our table), so the ISIN is the field that tells them apart; this
changes backtest numbers slightly, so the 2026-11-26 research notes predate it.

### Medium

| ID | Item | Fixes | Notes and alternatives |
|---|---|---|---|
| B-040 | "Today" strip on the Command center: orders waiting with the time left to 09:15 IST, last EOD session and coverage, failed or abandoned runs, each linking to its screen | P1 | Builds on the paper client from PR #30. Alternative: a badge on the rail icon only (cheaper, easy to miss) |
| B-041 | Grounded copilot: Java assembles a fact sheet (last session, close, SMA 50/200, RSI, ATR, 52-week range, screens matched, open order or position) and passes it as data; the prompt says to answer only from it; numbers in the reply are checked against the sheet | P6 | Same pattern the triage agent needs (B-007). Alternative: hide the copilot until then. Larger model (B-006) helps wording, not truth |
| B-042 | Precompute shortlists after the EOD job (a `screen_run` table keyed by screen and session); UI reads the stored list, with "computed at" | P11 | Alternative: cache in memory per session (no migration, lost on restart) |
| B-043 | Freshness and coverage API: `GET /api/v1/marketdata/freshness` (latest session per source, shares behind by sessions, using `TradingCalendar`) and `GET /api/v1/calendar/coverage`; then build the Data calendar screen, with backfill failures listed | P8, P19 | Unblocks the Data calendar screen |
| B-044 | Dossier lenses from Java: `GET /api/v1/instruments/{symbol}/indicators` (SMA 20/50/200, RSI 14, ATR 14) and a "where this share appears" panel (shortlists, orders, positions) | P9 | Keep all maths in Java (CLAUDE.md) |
| B-045 | Batch summary on the approval queue: total value, total risk to stop, cash after fills, positions used, and order value as a share of 20-session average traded value | P14, P15 | Needs average traded value in the order view; compute in Java |
| B-046 | Paper portfolio against Nifty (indexed equity curves) and go-live progress per criterion (months, trades, return gap, drawdown against -25%, decided-before-open count) | P16 | Criteria are prose today; store them as structured thresholds next to the text |
| B-047 | Supported recovery: `POST /dev/jobs/runs/{id}/fail` with a reason, run buttons for every job on the timeline, and a runbook "EOD run failed or partial" | P18, P20 | Alternative: runbook with SQL only (no code, error-prone) |
| B-048 | Alias curation: log names that did not resolve (from copilot, resolver, later news), and a small UI to map them to a share, checked against the instrument table | P7 | Never seed aliases from memory; propose candidates from the fuzzy matcher |
| B-049 | Playbook lab "use paper account settings" preset and the account's own backtest shown beside its forward results | P12 | Uses the paper client from PR #30 (merged) |
| B-050 | Label simulated LLM spend: spend report says which tiers ran at pretend prices; ring reads "simulated" | P5 | |

### Larger

| ID | Item | Why | Trade-offs |
|---|---|---|---|
| B-027 | Alert delivery (existing item): raise its priority now that paper trading runs every evening | P17 | Telegram, email or ntfy behind one interface; a desktop toast via the UI is cheaper but needs the page open |
| B-051 | Reconcile the go-live criteria with the backtest for account 1: the settings backtest to -42.8% drawdown (2019-2026) and -30.3% out of sample, while criterion (3) allows -25%. Options: change the criterion, add a portfolio-level drawdown brake or lower risk per trade and re-run the sweep, or accept that the account is a learning run | P13 | A decision for the user; changing criteria after the start must be recorded (ADR 0005) |
| B-052 | Liquidity and gap risk in sizing: cap order value at a fraction of average daily traded value, and report gap-through-stop losses separately in backtests and paper results | P13, P15 | 64 of 193 stop exits gapped through the stop |

## Top five

1. P1 + P17: decisions and failures are invisible; the 09:15 IST deadline passes unnoticed and
   evening job failures are found the next morning (B-040, B-027).
2. P13: account 1's own backtest breaks its go-live drawdown criterion; decide before the
   track record grows (B-051).
3. P6: the copilot answers from model memory, not from our data (B-041).
4. P2: a restart leaves runs RUNNING and blocks the job for 3 hours (fixed in a quick-win PR).
5. P10: ETFs are screened as company shares (fixed in a quick-win PR).
