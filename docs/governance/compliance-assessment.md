# Compliance assessment (preliminary)

- Owner: Compliance Specialist / Legal Research. Reviewers: CISO, Data Privacy, Product Manager. Approver: founder, **after qualified Indian securities and privacy counsel reviews it**.
- **This is not legal advice.** The claims below were checked against primary texts in [compliance-verification-2026-11.md](compliance-verification-2026-11.md) (PR #37, Compliance & Legal Research session, 2026-11-27). That note holds the sources, clause references and the prioritised **questions for counsel**. Still unverified: the commencement notification for the DPDP Act's sections; the scope of NSE policy §7.4 for broker-sourced data; whether a logged-in SaaS counts as "widely available to the public" under the IA proviso.

## 1. Capability classes

Every product capability is tagged with one class. The class decides who may see it.

| Class | Meaning | Examples | Who may use it |
|---|---|---|---|
| **TOOL** | user-configured computation over data; no opinion from us | screener with user-chosen rules, indicator charts, paper simulator | founder now; external users after G2 and counsel (neither the RA nor the IA Regulations contain a tool exclusion) |
| **INFORMATION** | factual reporting with sources; no recommendation | intelligence feed, event summaries, data freshness, briefing facts section | as TOOL. The facts section likely falls within the RA exclusions for market trends and statistical summaries; verify with counsel |
| **INTERPRETATION** | AI-generated context about securities, **and any published backtest or performance figure** | MIA `interpretation`, research Q&A, published strategy CAGR, equity curves, "beats Nifty" | founder only until G1. A published backtest may be an implied "claim of returns or performance" (Intermediaries reg 16A(1)(ii)) |
| **RECOMMENDATION** | buy/sell/hold, targets, "opportunities", model portfolios, black-box strategies offered to others | brief's "trading opportunities", pre-built shortlists marketed as picks | **blocked** externally unless AuraTrade or a partner is SEBI-registered (RA / IA) |
| **EXECUTION** | orders to a broker | live trading | **blocked** (brief, ADR 0005); gate G4 |

## 2. SEBI

| Topic | What the primary texts say (see the verification note for clauses) | Implication |
|---|---|---|
| Research Analyst Regulations, 2014 (amended 16 Dec 2024; consolidated 10 Oct 2026) | Covers "any written or electronic communication" of research. Requires RAs to disclose use of AI (reg 19(vii)) and remain responsible for AI output (reg 24(7)). Report contents: regs 19, 20, 25. **No exclusion for AI or tools.** The technical-analysis exclusion covers a sector or index only | AI interpretation of a specific security for anyone other than the founder may be research. Gate G1 |
| Intermediaries Regulations, reg 16A and regs 16D-16E | Registered intermediaries may not associate with persons giving unregistered advice or making return/performance claims (16A(1)(ii)). An API integration is an "association" (Explanation 2(c)), and 16A(2) makes the broker responsible for policing us. Return claims are allowed only for IAs, RAs and empanelled algo providers, using PaRRVA-verified metrics (16D-16E). Education exclusion: reg 16A itself names no lag. The FAQ lag is **30 days for sharing and usage, from 1 Jul 2026** (SEBI circular of 8 May 2026), replacing the earlier three months | Our Upstox link makes our external conduct Upstox's concern. Published backtests are high risk. Counsel Q1 and Q2 |
| Investment Adviser Regulations, 2013 | Definition in reg 2(1)(l), with provisos for mass media and trading calls; reg 3(1); reg 15(14) on AI. **No exclusion for general-purpose tools** | Portfolio analysis or allocation features for users may be investment advice. Counsel Q7 |
| Retail algorithmic trading framework | In force for all brokers from **1 Apr 2026** (SEBI circular 30 Sep 2025, para 8). NSE/INVG/67858 sets 10 orders/s per exchange per segment. Below that: no exchange registration, but a generic algo ID. Above it: register with each exchange. Founder algos below 10 OPS still need broker API onboarding: static IP, OAuth/2FA, daily logout, algo tagging, no market orders. Black-box strategies offered to others need RA registration (SEBI circular 4 Feb 2025, §V). Paper trading sends nothing to the exchange, so the framework is not triggered | Any live step, even founder-only and human-approved per order, is an algo order on the text (NSE FAQ Q8). Gate G4 |

## 3. Market data licensing

- **Ownership:** NSE's Data Usage and Data Sharing Policy says NSE is the sole owner of its market data and licenses use and redistribution through NSE Data. Its non-display policy covers derived data. Showing NSE prices or derived values to SaaS customers needs a licence (gate G2).
- **Simulation (new):** NSE policy §7.4 bars market data for "virtual trading or simulation" platforms. This is a risk to *external* paper trading (R-23). Whether it also reaches founder-only use of broker-supplied data is counsel Q4.
- **Upstox:** the R2-01 terms review (PR #36) found no published API data licence, and ai.txt bars resale and republishing. See R-01.

## 4. Data protection (DPDP Act 2023 and DPDP Rules 2025)

- **Rules:** G.S.R. 846(E), notified around 13 to 14 Nov 2025 (exact date to confirm). Commencement per rules 1(2) to 1(4): Board provisions immediately; consent-manager provisions **already in force**; the main fiduciary obligations from about **May 2027**. The Act's section-commencement notification has not been read ("verify").
- **Obligations to design for:**
  - minimum one-year retention of personal data and logs (rules 6(1)(e), 8(3));
  - a detailed breach report to the Board within 72 hours (rule 7(2)(b));
  - a published privacy contact (rule 9);
  - a 90-day grievance response (rule 14(3));
  - penalties up to INR 250 crore (Act, Schedule).
- **Tension:** the one-year log retention against erasure requests (counsel Q8). ADR 0037 keeps personal data out of audit payloads.

## 5. Gates

| Gate | Unlocks | Requires |
|---|---|---|
| G0 | Founder-only use (now) | personal-use terms of each source respected; no client and no consideration |
| G1 | INTERPRETATION / RECOMMENDATION (including published backtests) for anyone but the founder | counsel opinion; SEBI RA registration (or a partner RA), or a redesign to TOOL / INFORMATION only |
| G2 | Any external user seeing market data, **including external paper trading** | licensed data source with display and derived-data rights; NSE §7.4 simulation restriction cleared; contract reviewed |
| G3 | External users at all | DPDP notice and consent, privacy contact, grievance process (90 days), breach process (72 h), terms of use, disclaimers |
| G4 | Any live order | founder authorisation; broker API onboarding under the algo framework (static IP, OAuth/2FA, daily logout, algo tagging, no market orders; exchange registration above 10 OPS); security review; decision-bound step-up |
