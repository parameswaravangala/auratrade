# ADR 0009: Keep our own thin LLM gateway; revisit LiteLLM at defined triggers

- Status: accepted
- Date: 2026-11-26
- Related: ADR 0003 (Spring AI), ADR 0004 (tier-based gateway), ADR 0006 (budget rules), ADR 0007 (provider adapter), ADR 0008 (spend visibility)

## Context
We built a Java gateway (tiers, per-agent policies, budget resolver, fallback, usage log, alerts) without first comparing it to a self-hosted LiteLLM proxy. That comparison should have been made explicitly. LiteLLM is an open-source proxy that offers one OpenAI-compatible endpoint over many providers, retries and fallbacks, a maintained model price list, virtual keys with budgets, spend tracking, alerts, an admin UI and observability integrations.

## Options considered
1. **Own thin gateway behind a ModelInvoker seam** (current).
2. **Self-hosted LiteLLM**, with Spring AI pointed at its OpenAI-compatible endpoint.
3. **Hybrid:** LiteLLM for provider routing, prices and observability; our code keeps the agent-aware policy decisions.

## Decision
Keep option 1 for Phases 1 and 2. Treat the gateway as feature-complete after Sprint 1: no provider routing, no admin UI, no multi-tenant budgets. Revisit options 2 or 3 at the triggers below.

## Reasons
- **Our rules are agent-aware.** One shared monthly pool with stepped degradation (60, 75, 85 percent), a slice reserved for critical agents, fail-closed behaviour for the risk agent, and daily token caps per agent. LiteLLM budgets are dollar caps per key, team, user or model. One key per agent could approximate some of this, but the shared pool with percentage steps and the reserve do not map cleanly. As of November 2026 its documentation says per-model key budgets with fallback chains and tag budgets need an Enterprise license (verify before relying on this).
- **Operational weight.** LiteLLM budgets are enforced from its database, so a database-less deployment does not cap spend, and spend tracking across instances uses Redis. That is a proxy plus its own Postgres (and possibly Redis) next to our app on a small VPS, and a new component holding provider API keys.
- **Small, tested core.** The decision logic is a few hundred lines of pure Java with unit tests, and it encodes the trading-safety rules.
- **No spend yet.** Development runs on local Ollama, so the benefits of a managed price list and provider routing are not yet realised.

## Where we knowingly duplicate LiteLLM
- Provider abstraction (Spring AI already provides it).
- Model prices, which we maintain by hand in configuration (Haiku cache prices are still unfilled).
- Spend views, alerts and dashboards (a thin version).
- Per-customer budgets and virtual keys, which Phase 3 (SaaS) will need.

## Consequences
- We own price maintenance and alerting channels.
- Switching later is cheap because everything provider-specific sits behind ModelInvoker, and agents only know tiers.
- A wrong assumption about LiteLLM's feature fit is possible; the spike below is meant to test it.

## Revisit triggers
Reopen this decision when any of these happens:
1. We add a second paid provider, or need routing between providers.
2. We start Phase 3 and need per-user or per-customer budgets and keys.
3. Keeping prices current (including cache prices) becomes a recurring chore or a source of errors.
4. A budget or fallback rule we need can no longer be expressed cleanly in our own code.

## Optional spike (about one hour, any time before Sprint 5)
Run LiteLLM in Docker against the local Ollama model. Try: one virtual key per agent type with a budget, a budget-based fallback to a cheaper model, and the spend endpoints. Record what maps to our rules and what does not, then update this ADR.
