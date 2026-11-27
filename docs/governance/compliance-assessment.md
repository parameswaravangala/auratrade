# Compliance assessment (preliminary)

- Owner: Compliance Specialist / Legal Research. Reviewers: CISO, Data Privacy, Product Manager. Approver: founder, **after qualified Indian securities and privacy counsel reviews it**.
- **This is not legal advice.** Sources are mostly secondary commentary (broker blogs, law-firm notes) found 2026-11-27. Every item marked "verify" must be checked against the SEBI, exchange or MeitY text by counsel before any external user is onboarded.

## 1. Capability classes

Every product capability is tagged with one class. The class decides who may see it.

| Class | Meaning | Examples | Who may use it |
|---|---|---|---|
| **TOOL** | user-configured computation over data; no opinion from us | screener with user-chosen rules, backtester, indicator charts, paper simulator | founder now; external users after the data-licence gate (G2) |
| **INFORMATION** | factual reporting with sources; no recommendation | intelligence feed, event summaries, data freshness, daily briefing facts section | as TOOL |
| **INTERPRETATION** | AI-generated context about securities | MIA `interpretation`, research Q&A | founder only until the RA gate (G1) |
| **RECOMMENDATION** | buy/sell/hold, targets, "opportunities", model portfolios | brief's "trading opportunities", pre-built screen shortlists marketed as picks | **blocked** externally unless AuraTrade or a partner is SEBI-registered (Research Analyst / Investment Adviser) |
| **EXECUTION** | orders to a broker | live trading | **blocked** (brief, ADR 0005); also the retail algo framework |

## 2. SEBI

| Topic | What we found | Implication | Verify |
|---|---|---|---|
| Research Analyst Regulations, 2014 (amended) | Commentary says claims about a security's performance or returns may only come from a registered RA or another permitted intermediary, and RA research must carry a rationale, risk disclosure and credentials ([Univest](https://univest.in/blogs/sebi-finfluencer-rules-explained), [AlgoTest](https://algotest.in/blog/research-analysts-demystified-what-your-finfluencer-needs-to-do-now-sebi/)) | INTERPRETATION and RECOMMENDATION for third parties may be "research". Gate G1 | RA Regulations text; whether automated or AI-generated analysis counts as research; whether user-configured screens are tools |
| Association with unregistered persons (Intermediaries Regs, reg 16A, from 2024-08-29) | Registered intermediaries may not associate with persons giving unregistered advice or return claims. An education carve-out reportedly requires price data at least 3 months old ([Moneylife](https://mas360.moneylife.in/article/sebi-tightens-regulations-on-finfluencers-new-guidelines-for-educational-content-and-stock-market-advice/4724.html), [Business Standard](https://www.business-standard.com/amp/finance/news/sebi-announces-norms-to-restrict-finance-influencer-s-recommendations-124062701002_1.html)) | Broker partnerships (for example Upstox or Zerodha integrations, data deals) may require us to show we give no unregistered advice | text of reg 16A; the 3-month rule's exact scope |
| Investment Adviser Regulations, 2013 | not researched in this pass | personalised portfolio analysis or allocation suggestions to users may be investment advice | full assessment before the Portfolio Analysis features |
| Retail algorithmic trading framework | SEBI circular "Safer participation of retail investors in Algorithmic trading" (4 Feb 2025). Implementation extended, with milestones toward Jan 5, 2026 and full compliance reported for April 2026; Algo IDs tag orders; there is reportedly a threshold of 10 orders/s for a generic ID ([SEBI extension PDF](https://www.sebi.gov.in/sebi_data/attachdocs/sep-2025/1759232056254.pdf), [Zerodha explainer](https://zerodha.com/z-connect/business-updates/explaining-the-latest-sebi-algo-trading-regulations), [Business Standard](https://www.business-standard.com/markets/news/sebi-extends-retail-algo-trading-framework-rollout-to-2026-125093000956_1.html)) | Any automated order placement (even founder-only via API) must go through the broker's registered-algo path. Offering strategies to others makes us an algo provider needing exchange empanelment through a broker | current enforcement status; personal-use rules; whether "human approves every order" changes the classification |

## 3. Market data licensing

NSE's Data Usage and Data Sharing Policy states NSE is the sole owner of its market data and licenses use, distribution and redistribution through NSE Data; its non-display policy also covers derived data ([policy](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/NSE_Data_Sharing%26Usage_Policy.pdf), [non-display](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/Non_Display_Policy.pdf)). **Consequence: showing NSE prices, or values derived from them, to SaaS customers almost certainly needs a licence from NSE Data or a licensed vendor whose terms allow it.** Gate G2. Upstox data rights for third-party display are unverified (risk R-01).

## 4. Data protection (DPDP Act 2023 and DPDP Rules 2025)

Rules were notified about 13 to 14 Nov 2025 with phased commencement: Board provisions immediately; consent managers about Nov 2026; the main fiduciary obligations (notice, consent, security safeguards, breach notice, retention, children's data) about **May 2027** ([PIB doc](https://static.pib.gov.in/WriteReadData/specificdocs/documents/2025/nov/doc20251117695301.pdf), [SCC Online](https://www.scconline.com/blog/post/2025/12/26/digital-personal-data-protection-rules-2025-key-highlights/), [YourStory](https://yourstory.com/2025/11/digital-personal-data-protection-dpdp-rules-2025-rollout-over-18-months)). The Phase 4 SaaS launch falls after May 2027, so we must be compliant at launch: consent notice, purpose limitation, erasure, a breach process (notice to the Board and to users), and processor contracts (AWS, LLM provider). GDPR applies only if we target EU residents (not planned).

## 5. Gates

| Gate | Unlocks | Requires |
|---|---|---|
| G0 | Founder-only use (now) | personal-use terms of each source respected |
| G1 | INTERPRETATION / RECOMMENDATION for anyone but the founder | counsel opinion; either SEBI RA registration (or partner RA) or a product redesign to TOOL/INFORMATION only |
| G2 | Any external user seeing market data | licensed data source with redistribution rights; contract reviewed |
| G3 | External users at all | DPDP notice/consent, privacy policy, terms of use, disclaimers, grievance contact |
| G4 | Any live order | founder authorisation, broker algo registration, SEBI framework compliance, security review, step-up auth |
