# Phase 0 independent review log

On 2026-11-27, six reviewers ran as **separate subagents**, each reading the draft and the repository cold and independently: Enterprise Architect, AI Architect, Data Architect, Head of Quant Research, Security Architect and Product Manager. This file records the disagreements, how each was resolved, and the findings applied. "Founder" means the decision is waiting for you.

## 1. Disagreements and resolutions

| # | Topic | Positions | Resolution | Record |
|---|---|---|---|---|
| X1 | Tenancy and RLS timing | Draft: RLS in R1. Security: scaffold and tests early; RLS is only defence in depth. Enterprise, AI, Product: defer, it is over-engineering. Data: the draft mechanism cannot be built; the keys are the expensive part | Add keys and `tenant_id` now (one small migration); explicit `TenantId` parameters are the primary control; RLS in Phase 4 using the corrected mechanism | ADR 0036 |
| X2 | Identity provider in R1 | Draft: Keycloak with tokens in the SPA. Security: a BFF with an HttpOnly cookie. Enterprise, Product: Keycloak's RAM is not justified for one user | BFF session with a single local user and passkey now; Keycloak through `oauth2Login` before G3; step-up bound to the decision before G4 | ADR 0035 |
| X3 | Outbox | Draft: build our own. Enterprise: compare with Spring Modulith first (the ADR 0009 lesson). Product: build when first needed | R3-00 spike, then decide; ordering, transaction and LLM-off-dispatcher rules fixed now | ADR 0032 |
| X4 | Numbers in LLM text | Draft: regex check of numbers against facts. AI: it would quarantine almost everything | Fact slots `{F#}`: the model writes no digits | ADR 0033 |
| X5 | Advice boundary | Draft: banned words. AI: that blocks BUYBACK and INSIDER_TRADE | Ban constructions, with a must-pass factual set | ADR 0033 |
| X6 | Price reaction and significance | Draft: 2σ flag with beta 1; market-cap term. Quant: unsound; no market-cap data; the reaction is known only after EOD | Market model with z-score, t0 alignment and clusters; liquidity in place of market cap; logistic score; two-phase publication | ADR 0033 |
| X7 | Eval gate sizes | Draft: about 590 labelled items, with a block in CI. AI, Product: unrealistic, and contradicts ADR 0010 | 100 / 150 / 25 / 30, enforced locally with EVAL_STRICT; sets grow from quarantined items | ADR 0033 |
| X8 | Budget downgrade versus quality | Draft: MIA downgrades at 60 %. AI: it may land on a model that fails the gates | Skip interpretation and publish facts only | ADR 0033 |
| X9 | Embeddings now | Draft: pgvector in R2. AI: defer; no model chosen; licensing of news text | Postgres FTS only in Phase 1; pgvector in Phase 3 under its own ADR | ADR 0034 |
| X10 | Licence registry | Draft: documents-only `source` with a boolean. Data: Upstox prices are the biggest risk and are not covered | One `data_source` registry for every kind, with checklist fields, enforced by the API | ADR 0034 |
| X11 | Parquet from Java | Draft: Java writes Parquet. Data: heavy dependencies | gzip CSV through CopyManager; the lab converts; nightly parity check (Quant) | ADR 0031 |
| X12 | Python for quant | Brief: Python. All reviewers: one source of numbers in Java | Java in production; Python lab read-only | ADR 0031 |
| X13 | Microservices, Next.js, repo tree | Brief: yes. Enterprise: no; an extracted service sharing the monolith's schema is a distributed monolith | Monolith, Vite SPA, one Maven project; an extracted service owns its own schema | ADR 0030 |
| X14 | Audit hash chain | Draft: chain plus trigger. Security: the database owner can rewrite it. Data: conflicts with erasure | SECURITY DEFINER insert, chain per tenant, daily external anchor, no personal data in payloads | ADR 0037 |
| X15 | Full freeze | Founder: full freeze. Product, Quant, Security: live defects and deadline work should be exempt | Proposed R0 exemptions: hotfix, corporate-action guard, calendar 2027, terms research | **Founder** |
| X16 | Quant work after Day 90 | Draft: Phase 2 after Day 90. Quant: the corporate-action guard is a correctness fix *now*. Product: announcements can be the corporate-action source | Guard in R0 (if exempted); R3-07 corporate-action events; survivorship and walk-forward stay in Phase 2 with stated limits | roadmap |
| X17 | Go-live criteria | ADR 0024 criteria. Quant: low power (about 550 trades for t ≈ 2) and two different exit systems | `stopMode=NEXT_OPEN` reference rerun (R1-12); reword the criteria as "within the backtest distribution" | **Founder** (ADR 0024 change) |

## 2. Findings applied to the documents (summary)

- Overview: the ownership conflict between agents and intelligence fixed; the real module graph noted; off-machine backup; ops alerts pulled forward; the wrong "Boot 4.2 exists" label corrected.
- Data: the corrected tenant mechanism; composite keys; `candle_revision` for reproducible decisions; evidence FK and licence tag; status history; embedding key (document, model, chunk); key and Clock conventions.
- Security: live issues T0 and R-19 recorded; CI gates marked "planned"; SSRF tightened; the audit claim worded honestly.
- Product: R1 trimmed of SaaS scaffolding; morning decision brief in R2; thumbs feedback with the feed; one time (08:30) for the briefing; founder time 8 to 10 h a week; interviews before R5 scope; no landing page.
- Vision: claims softened to "honest about its limits"; lead persona P1 at the morning decision.
- Risks: R-19 to R-22 added; R-06 reviewed every sprint.

## 3. Facts found that contradict CLAUDE.md (for the founder)

- The pom has `java.version` 25 and the Spring AI BOM at 2.1.0 on Spring Boot 4.1.2, and CI uses Java 25. CLAUDE.md "Stack" still says Java 21 and Spring AI 2.0.x.
- The Dockerfile runtime is `eclipse-temurin:21-jre`, so the image fails to start (R-22).
- CLAUDE.md is not changed by this PR. It will be updated after approval, because the founder owns it.
