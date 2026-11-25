# ADR 0004: Tier-based LLM gateway with budget governance

- Status: accepted
- Date: 2026-11-25

## Context
Agent loops multiply token cost. We need predictable spend and safe degradation.

## Decision
Agents request a tier (HEAVY, LIGHT, LOCAL), never a model. The gateway resolves the tier from budget state and agent policy, records usage per run, and falls back one tier on provider errors. Critical agents (risk) never downgrade; they fail closed.

## Consequences
Cost is observable and capped. Adds a layer every LLM call must pass through; tests must cover each budget state.
