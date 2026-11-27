# AuraTrade Phase 0 package: consolidated proposal for founder approval

- Prepared 2026-11-27 by the "AuraTrade Phase 0 initialization" session, under the master brief (November 2026 edition), with the founder's decision to do a **full reset** owned by this session.
- Status: **DESIGN, awaiting approval.** No code was changed. Feature work is frozen. Unattended jobs keep running.
- Independence: six reviewers ran as separate subagents (see [review-log.md](review-log.md)). Version checks used three web-fetch agents on official release pages. Regulatory research comes from secondary sources and is being re-checked against primary texts by the Compliance session.

## 1. Deliverables

| # | Deliverable | Documents | Responsible role | Reviewers | Approver |
|---|---|---|---|---|---|
| 1 | Organisational setup | [organization.md](../business/organization.md) | CEO role | Delivery Coordinator | Founder |
| 2 | Product strategy | [business-vision.md](../business/business-vision.md) | CEO / CPO | PM, Quant, Compliance | Founder |
| 3 | Technology modernisation | [technology-version-matrix.md](../engineering/technology-version-matrix.md), [technology-stack.md](../engineering/technology-stack.md) (incl. dependency governance) | CTO | EA, Security | Founder |
| 4 | Enterprise architecture | [architecture-overview.md](../architecture/architecture-overview.md), [domain-model.md](../architecture/domain-model.md), [data-architecture.md](../architecture/data-architecture.md), [ai-agent-architecture.md](../architecture/ai-agent-architecture.md), [security-architecture.md](../security/security-architecture.md) | Enterprise Architect | all six | Founder |
| 5 | Market Intelligence Agent design | [ai-agent-architecture.md](../architecture/ai-agent-architecture.md) section 3, [intelligence-event.v1.schema.json](../../contracts/events/intelligence-event.v1.schema.json), [data-source-register.md](../research/data-source-register.md) | AI Architect | Quant, Data, Security | Founder |
| 6 | Engineering foundation | [engineering-standards.md](../engineering/engineering-standards.md) | CTO / EA | Gatekeeper, Security | Founder |
| 7 | Execution roadmap | [roadmap-90-day.md](../product/roadmap-90-day.md) | Product Owner / Delivery Coordinator | CTO, Quant, Security | Founder |
| 8 | Risks and decisions | [risk-register.md](../governance/risk-register.md), [compliance-assessment.md](../governance/compliance-assessment.md), ADRs [0030](../adr/0030-reset-baseline-monolith-spa.md) to [0037](../adr/0037-audit-trail.md) | CTO / Compliance | all | Founder |

Acceptance criteria for the package: every deliverable exists; every version is sourced or marked unconfirmed; every reviewer disagreement is resolved or put to the founder; planned spend is zero.

## 2. The proposal on one page

- **Product**: an evidence-first research cockpit for NSE equities. The 90-day goal is a **founder-only Market Intelligence MVP** plus a morning decision brief for the paper account. External users wait for gates G1 (SEBI research rules), G2 (data licence) and G3 (DPDP).
- **Architecture**: keep the modular monolith (Java 25, Boot 4.2 target, PostgreSQL 18 target) and the Vite SPA. No Kafka, Temporal, Kubernetes, Redis or OpenSearch until a measured trigger fires. The agent is a deterministic pipeline with LLM interpretation using fact slots and two-phase publication. Authentication is a BFF session. Tenancy keys go in now, row-level security in Phase 4. The audit trail is append-only with an external anchor.
- **Biggest risks**: data licensing for SaaS (R-01, 20); the SEBI research and advice boundary (R-02, 20); prompt injection (R-04, 16); backtest bias (R-06, 16); unknown costs for data and counsel (R-14, 16); the 2027 calendar (R-10, 15, deadline 2026-12-31).
- **Spend**: INR 0 planned in 90 days. Data, counsel and paid-LLM costs are unknown and need quotes and your approval.

## 3. Department sessions now running (started at your request)

Compliance & Legal Research, Market Data & Licensing, Quant Research, Platform & Security (draft PR only, blocked) and AI Evaluation. See [organization.md](../business/organization.md). Each opens its own PR. None merges.

## 4. Decisions needed from you

| # | Decision | Recommendation |
|---|---|---|
| F1 | Approve the Phase 0 architecture and direction (ADRs 0030 to 0037), in full or in part | Approve. Partial approval of R1 is possible while the business docs are revised |
| F2 | Exempt R0 from the freeze: the localhost/Host-Origin hotfix and Dockerfile fix (draft PR), the corporate-action guard on held paper positions, the calendar 2027 load, terms research | Exempt all four. They are live defects or deadlines, not features |
| F3 | Paper account 1: mechanical (no news-based overrides) or discretionary? | Keep it mechanical; log an override reason if you ever deviate; a second discretionary account later if wanted |
| F4 | Days you cannot decide before 09:15 | Proposals expire, as today, and the miss is logged against criterion 5 |
| F5 | Go-live criteria (ADR 0024): the Quant note (PR #40) shows that 50 trades cannot prove an edge (t ≈ 0.6), and that the −25 % drawdown limit is stricter than the strategy's own backtest (−30 % out of sample) | Rewrite as a fidelity replay plus 5th to 95th percentile consistency; set thresholds after the `stopMode=NEXT_OPEN` rerun |
| F6 | Ops alert channel (B-027) | **Decided: email** (founder to Product Owner, relayed by the Delivery Coordinator on 2026-11-27; confirm when you approve). Still open: which SMTP account or provider, and the sender and recipient addresses (secrets in `.env` only). Verify the provider's sending limits and pricing before building |
| F7 | Off-machine backup target (zero cost) | An encrypted dump to a cloud drive you already have |
| F8 | Audit anchor: a separate private git repo, or a daily email | A daily email of the chain heads to your own mailbox. It is outside the database and the laptop, and needs no new account. A signed commit in a private repo is the stronger option if you want the anchor to be verifiable by someone else |
| F9 | A personal tool for 12 months, or a business? Day-90 continue-or-kill criterion | Personal tool until gates G1 and G2 have quotes; decide at Day 90 |
| F10 | Update CLAUDE.md "Stack" to the facts (Java 25, Spring AI 2.1.0) and to the reset decisions after approval | Yes |
| F11 | Software licence for the repo (LICENSE file) | Proprietary, all rights reserved |
