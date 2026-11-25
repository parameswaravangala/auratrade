# ADR 0003: Spring AI for LLM access

- Status: accepted
- Date: 2026-11-25

## Context
The team knows Java and Spring Boot. Spring AI 2.x targets Spring Boot 4 and provides ChatClient, tool calling, usage metadata, embeddings and vector store support.

## Decision
Use Spring AI behind our own gateway module. Application code never calls Spring AI or a provider directly outside llmgateway.

## Consequences
Provider swaps (Claude, Ollama) are configuration. We depend on Spring AI release cadence; pin versions and re-run evals on upgrades.
