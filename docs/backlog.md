# AuraTrade backlog

Items parked outside the current sprint. Keep each one small and say what unblocks it. Move an item into a sprint plan when it is picked up.

## From Sprint 1 (LLM gateway and evals)

| ID | Item | Why | Unblocked by |
|---|---|---|---|
| B-001 | Triage prompt v3: extract company names as written in the text, and resolve them to NSE symbols in code | A 3B model invents tickers (SPML, ASP, LARSEN&TUBRO, BAJAJFINANCE). Resolving in code makes wrong tickers impossible by construction | Instrument master and resolver (Sprint 2) |
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
| B-022 | Kite Connect client: login flow, historical API, websocket ticks | Needs the paid data plan | Decision to subscribe |
| B-023 | Static-IP and order-placement requirements | Live phase only | Phase 2 |
| B-024 | Resolver (B-001) must match only company shares: series EQ, BE, SM, ST, BZ, and exclude ETF iNAV rows (symbol ends in INAV; 363 of 3,149 EQ rows on 2026-11-26) and indices | Series EQ is not only shares in the Kite file | Resolver slice |
| B-025 | Schedule the instrument load daily (after the Kite file refreshes) and alert if it fails or is stale | Loading is manual via POST /dev/marketdata/instruments/load | Staleness-alert slice |
