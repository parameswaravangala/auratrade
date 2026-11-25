# ADR 0006: LLM budget rules

- Status: accepted
- Date: 2026-11-26

## Context
Agent loops multiply token cost. We need predictable spend, graceful degradation for research work, and no weakening of safety checks.

## Decision
- Monthly budget (initial $50) and thresholds as fractions of it: 60% drops non-critical agents one tier, 75% drops them to their floor, 85% stops them, 100% stops critical agents. The last 15% is reserved for critical agents.
- Critical agents (risk review) are never downgraded and fail closed when stopped.
- A daily token cap per agent applies to every agent, critical or not.
- An agent whose floor is LOCAL keeps working on the free local tier after the stop. No agent has a LOCAL floor by default, because a small VPS cannot host a useful model.
- Month and day boundaries follow IST.
- Cost is computed per token type: fresh input, output, cache reads, cache writes. If a cache price is unknown the input price is used, which overestimates cost.
- Usage is recorded once per agent run in llm_usage.

## Consequences
Spend is observable and capped. Soft limits in code can overshoot slightly under concurrency, so the provider console hard cap remains the backstop. Thresholds are starting values to tune from real usage.
