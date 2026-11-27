# Business vision and product strategy (Deliverable 2)

- Owner: CEO and CPO roles. Reviewers: Product Manager, Head of Quant, Compliance. Approver: founder.
- Status: **DESIGN**. Market claims below are **hypotheses** to test. No market sizing or competitor pricing has been verified in this pass.

## 1. Vision

An evidence-first research cockpit for Indian equities. Every number is computed by code, and every claim links to its source. The AI explains the evidence and never invents it.

## 2. Positioning

For self-directed Indian investors and traders who research before they act, AuraTrade is a market intelligence and strategy-validation workspace. Unlike tip services and finfluencer content, it shows *why* with traceable evidence. Unlike plain screeners and charting tools, it connects events to price reactions and tests ideas honestly (costs, out-of-sample, drawdown) before any money is at risk.

Differentiators that are **already partly built** and hard to copy quickly:
1. **Provenance by construction**: candles per source, validator gates, fail-closed calendar; event evidence model (designed).
2. **Backtesting that is honest about its limits**: no look-ahead, delivery costs from one broker's published charge sheet (not yet checked against statutory rates), out-of-sample sweep, regime filter. Published results include losers (pullback lost money). Known biases are stated: survivors-only universe, unadjusted corporate actions, the out-of-sample period already used to choose the screen, and a price-index benchmark. Paper trading is the only true out-of-sample test.
3. **Human-approved paper trading with go-live criteria** (ADR 0024): a discipline product, not a signal product.
4. **AI with guardrails**: tiered, budgeted, evaluated, injection-tested LLM use.

## 3. Target customers and personas (initial focus in bold)

| # | Persona | Job to be done | Phase |
|---|---|---|---|
| P0 | **Founder** (Java architect, swing/positional trader) | research and paper-trade systematically | now |
| P1 | **Positional/swing trader** (holds days to months, 5 to 30 stocks, evenings research) | "Tell me what changed in my stocks today and whether price confirmed it" | Phase 4 beta |
| P2 | Long-term investor | track filings, results and corporate actions on holdings | Phase 4 |
| P3 | Quant hobbyist / developer | test rule-based strategies on clean NSE data with costs | Phase 4 (API) |
| P4 | Research firms / RIAs / RAs (registered) | evidence-linked intelligence feed for their own analysts | after G1/G2, enterprise |
| P5 | Institutions | data and intelligence APIs | later |

Active intraday F&O traders are **not** an initial target: they need realtime data (paid), and the regulatory and suitability risk is higher.

## 4. Capability map

| Pillar | Capability | Status |
|---|---|---|
| Market intelligence | instrument master, EOD candles, calendar, freshness | exists |
| | corporate announcement and news ingestion, event detection, price-reaction correlation, briefing | **Phase 1 (new)** |
| Quant | indicators, screens, screen backtest, portfolio simulation, sweep/OOS, paper trading | exists |
| | walk-forward, corporate actions, delisted universe, fundamentals | Phase 2 |
| Agentic AI | gateway, budgets, evals | exists |
| | MIA, briefing agent | Phase 1 |
| | research Q&A, coordinator, RAG | Phase 3 |
| SaaS | auth + audit | Phase 1 (pulled forward) |
| | tenancy, plans, billing, metering | Phase 4 |
| Trust | ADRs, runbooks, fail-closed design | exists; security and compliance gates designed |

## 5. MVP definition (Phase 1 exit)

**"Founder-grade Market Intelligence MVP"**: single tenant, founder only (gate G0). Every external-user capability waits for gates G1 to G3.

In scope:
1. Sign-in (Keycloak) and an audit trail on paper-order decisions.
2. At least one corporate announcement source and one news source enabled under verified personal-use terms.
3. MIA pipeline stages 1 to 9 with the evidence model and quarantine.
4. Intelligence feed UI, stock dossier events tab, watchlists, significant-event alerts (in-app plus one channel).
5. Daily briefing (pre-open and post-EOD), with facts and interpretation separated.
6. Eval gates met (ai-agent-architecture section 3.6).

Lead persona: **P1 positional/swing trader**, at the morning decision moment (before 09:15 IST). Interview-based discovery only: no landing-page waitlist, which would collect personal data and needs gate G3.

Success metrics (measured, not assumed): ≥ 95 % of trading days *on which the host was up* with a briefing delivered by 08:30 IST (host uptime reported separately); at least 40 rated alerts; ≤ 5 % quarantine rate after tuning; 0 fabricated numbers in a 30-day replay; founder rates ≥ 70 % of alerts "useful" (thumbs up/down in the UI) over 4 weeks.

## 6. Competitive assessment (qualitative; verify features and prices before any external use)

| Category | Examples (Indian market) | Their strength | Our angle |
|---|---|---|---|
| Screeners / fundamentals | Screener.in, Tickertape, Trendlyne | breadth of fundamentals, large user base | event-to-price evidence, honest backtests |
| Charting | TradingView | charts, community | we embed charts (Lightweight Charts) and do not compete on charting |
| Broker tools | Zerodha (Kite, Console), Upstox, Groww | execution, free with an account | broker-neutral research and discipline layer |
| Strategy platforms | Streak, AlgoTest, Sensibull (F&O) | no-code strategy and options tools | positional equities and evidence, not F&O |
| News and tips apps | many; finfluencers | reach | no tips; evidence and provenance (regulatory tailwind from SEBI finfluencer rules) |

## 7. Revenue model hypotheses (to test, not to promise)

| H | Hypothesis | Test | Kill criterion |
|---|---|---|---|
| H1 | Swing traders pay a monthly subscription for evidence-linked alerts on their watchlist | 10 discovery interviews + a landing-page waitlist (no data shown, no cost) | < 3 of 10 would pay at any price point |
| H2 | Honest backtesting with Indian costs is a paid "Pro" feature | interviews with P3 | no willingness to pay over free tools |
| H3 | Registered RAs/RIAs pay for an intelligence feed and API for their analysts (B2B) | 3 conversations with registered RAs | needs realtime or licensed data we cannot afford |
| H4 | Freemium (delayed/EOD intelligence free, alerts and backtests paid) drives acquisition | after G2 | CAC over 12 months of revenue |

Plan shapes (illustrative only, prices TBD after interviews): Free (EOD feed, 1 watchlist), Pro (alerts, briefing, backtests, paper accounts), Team/Enterprise (API, SSO, audit export). Any plan that shows market data depends on gate G2's licensing cost.
