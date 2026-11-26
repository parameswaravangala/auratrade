# ADR 0008: Spend visibility and budget alerts

- Status: accepted
- Date: 2026-11-26

## Context
A budget is only useful if spend is visible and we are told before limits bite. Langfuse and similar platforms bring several extra services; that weight is not justified before real agents exist.

## Decision
- Spend is read from llm_usage: SQL views llm_usage_daily and llm_usage_monthly (IST) for humans, and SpendService for code. A dev endpoint exposes it as JSON.
- Budget alerts fire once per level (50, 75, 90, 100 percent) per month, highest level crossed only, checked after every recorded run. The sink is a WARN log line for now.
- Alert state is in memory; one repeat alert is possible after a restart. An alert failure never affects the agent run.
- Langfuse tracing is deferred to Sprint 4 or 5, when real prompts exist to trace.

## Consequences
Cost visibility without extra infrastructure. Alerts reach a log only until a phone or email channel is added. Revisit tracing when agents are live.
