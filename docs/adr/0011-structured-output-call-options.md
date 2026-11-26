# ADR 0011: Callers choose generation options; extraction runs deterministic with JSON mode

- Status: accepted
- Date: 2026-11-26

## Context
The first triage evals (llama3.2:3b, default settings) showed noisy scores between identical runs, chatty preambles around the JSON, and invented tickers. Extraction and classification do not benefit from creative sampling.

## Decision
- The gateway takes an optional CallOptions (temperature, JSON output, seed). The caller chooses it because it knows what output it needs. The three-argument call keeps provider defaults.
- Extraction and classification agents use CallOptions.structuredJson(): temperature 0, JSON mode, fixed seed.
- Providers that do not support an option ignore it (default method on ModelInvoker).
- Fallback to a cheaper tier keeps the same options.
- Ticker correctness is a validation problem, not a prompting problem: symbols must be checked against the instrument master (Sprint 2), and unknown symbols dropped and counted.

## Consequences
More repeatable evals and fewer format failures. JSON mode guarantees syntax, not correctness; the parser and the symbol check still apply. Other agents (analysis, chat) keep default sampling.
