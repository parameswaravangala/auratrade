# ADR 0005: Paper trading first, human approval always

- Status: accepted
- Date: 2026-11-25

## Context
Money is at stake and LLM output is not a reliable price predictor.

## Decision
Phase 1 uses simulated fills only. Agents are read-only and cannot place orders. Every trade requires human approval. Moving to limited live trading requires passing a gate with criteria written down before the paper run starts.

## Consequences
Slower path to live trading, much lower risk. Paper results must include realistic costs and slippage to be meaningful.
