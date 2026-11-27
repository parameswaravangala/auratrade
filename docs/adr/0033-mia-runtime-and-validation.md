# ADR 0033: Market Intelligence Agent runtime, fact slots and two-phase publication

- Status: proposed
- Date: 2026-11-27

## Context
The draft MIA design (ai-agent-architecture section 3) was reviewed by the AI Architect, the Quant Head and the Product Manager.
- **AI Architect:**
  - Checking "numbers must appear in facts" with a regex would quarantine almost every earnings event, because of Indian formats (crore, lakh, %).
  - A banned-words list blocks BUYBACK and INSIDER_TRADE events.
  - The gateway only takes string prompts and has no tool calling, and one tier per agent does not fit a mixed pipeline.
  - The eval set sizes are unrealistic for one person.
- **Quant Head:**
  - The price-reaction rule (beta 1, a 2-sigma flag, no event-time alignment) is not statistically sound.
  - The significance score needs market cap, which we do not have.
  - The reaction is only known after EOD.
- **Product Manager:** start with smaller eval sets.

## Decision
1. **Runtime**: a plain-Java staged pipeline, with no model-chosen tool calls in Phase 1. "Tools" are Java services that the stages call. Each LLM stage has its own `AgentType` (`MIA_EXTRACT`, `MIA_CLASSIFY` LIGHT; `MIA_INTERPRET` HEAVY; `BRIEFING` LIGHT), so caps and floors apply per stage. `@AgentTool`, model tool calling and LangGraph4j are deferred to Phase 3. Before adopting LangGraph4j, compare it with Spring AI's own tool loop (the ADR 0009 lesson). The annotation will be ours, so only `llmgateway` touches Spring AI.
2. **Fact slots**: the model never writes digits. It writes `{F3}`, a reference to fact id 3 in the facts bundle. Code renders each value with fixed Indian formatting (₹, crore and lakh, %, one decimal place). Validation rejects any digit outside a slot, any slot that is not in the bundle, and any citation that is not in the evidence. The false-quarantine rate is an eval metric.
3. **Advice boundary**: ban advice *constructions* (an imperative plus buy, sell, hold or accumulate; "target price"; "should …"; return promises), not bare words. A pass-set of factual sentences ("promoter sold 2 % stake", "buyback approved") must stay unblocked.
4. **Budget against quality**: when budget rules would move `MIA_INTERPRET` to a tier with no model that passes the gates, skip interpretation and publish facts only (`interpretation: null`). Never downgrade quality silently.
5. **Two-phase publication**: an event is published at ingest with `significance.atIngest` (type, liquidity, novelty) and `marketContext.status = PENDING`. After the EOD job, `intelligence.event.enriched` adds the reaction and `significance.afterReaction`, as a new revision (`revision` field in the schema). Both scores are stored, so later studies have no look-ahead.
6. **Price-reaction method** (code):
   - Market model `r_i = α + β·r_NIFTY`, estimated over sessions −250 to −11. The estimation window excludes the event window and earlier events of the same stock.
   - Abnormal return AR, and CAR over [0, +1] and [0, +5].
   - Report the z-score of CAR against the estimation-window residual stdev × √n, not a yes/no flag.
   - t0 is the first session whose open comes after `publishedAt`, and it includes the overnight gap.
   - Several events for the same stock on the same day form one cluster with one reaction.
   - Volume is normalised by event type (results days are excluded from the baseline).
   - If an ex-date falls inside the window, the reaction is NOT_COMPUTABLE until corporate actions are adjusted (B-020).
7. **Significance**: `logistic(w·x)`, bounded 0 to 1. Inputs are event type, liquidity (median 20-day traded value, which exists, in place of market cap), novelty and the |z| of the reaction (after-reaction score only). Weights are set by hand in config and recorded here once chosen. They are not tuned on a small golden set. A 100-event significance label set (R3) measures them.
8. **Eval gates** are staged and run locally with `-Peval` and an `EVAL_STRICT` flag. They do not run in CI, as ADR 0010 says.
   - R3: 100 entity-resolution cases (precision ≥ 0.98), 150 classification cases scored on types with ≥ 15 examples, 25 injection cases (0 published events that follow the injection), 30 interpretation cases scored by a human.
   - Sets grow from real quarantined items.
9. **Injection claim, stated honestly**: structural controls stop fabricated numbers, tickers, citations and actions. They do **not** stop a semantically false sentence, for example "regulator cleared the company". Hence summaries of official sources are facts only (template plus slots), and every interpretation carries the "AI interpretation" label.
10. **Model registry**: a config file plus eval reports committed to git, with Ollama models pinned by digest. It is not a database table in Phase 1.

11. **Fact-slot typing** (AI Evaluation, PR #38): slots are typed. `{F#}` is a numeric fact, `{P#}` a period ("Q2 FY27"), `{R#}` a ratio ("1:2") and `{N#}` a named entity containing digits ("Nifty 50"). The digit rule only applies outside slots. Number words ("two crore", "doubled") also count as numbers and are rejected outside slots.
12. **Taxonomy v1.1** (AI Evaluation, PR #38):
    - **Multi-label**: one `primaryType` plus up to 3 `secondaryTypes`, because results, dividends and guidance often arrive in one filing.
    - **Renamed**: `REGULATORY_ACTION` becomes `REGULATORY` with `direction: ADVERSE | FAVOURABLE | NEUTRAL`.
    - **Added**: `OPERATIONAL_DISRUPTION`, `GOVERNANCE_RED_FLAG` (for example auditor resignation), `PAYMENT_DEFAULT`, `RESTRUCTURING` (demergers, schemes).
    - **Pre-filter**: routine compliance filings are labelled `ROUTINE_COMPLIANCE` and never reach classification or alerts.
    - **Scoring**: a type is scored only once it has ≥ 15 labelled cases. Which 8 types reach that first is an assumption until real filings are counted (R2).
13. **News-text tickers**: the reworked n21 case still fails *by design*. A real ticker injected into news text resolves legitimately. So only filings from the exchange or the issuer yield `confidence: HIGH` and alerts by default. News-only events are LOW confidence and appear in the feed, not as alerts, until they are corroborated by a filing or by a second independent source.

## Consequences
Hallucinated figures become structurally impossible, and interpretations get blander. Alerts fire on the ingest score and are refined after EOD. Small local models never need to do tool calling. Revisit at Phase 3 (Research Coordinator).
