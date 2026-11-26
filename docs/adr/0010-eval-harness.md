# ADR 0010: Eval harness, written before the agents

- Status: accepted
- Date: 2026-11-26

## Context
Prompt and model changes silently change agent quality, and the budget gateway can switch an agent to a weaker tier at any time. We need a way to measure quality before real agents exist.

## Decision
- Golden sets live in `evals/<agent>/golden.jsonl`, hand-labelled, versioned in git, with the prompt under test beside them.
- Evals run through the real gateway and model, tagged `eval`, and are excluded from normal builds. A Maven profile runs them on demand.
- Scores are compared with a saved baseline; a drop of more than 0.05 on a quality metric is a regression. Strict mode (EVAL_STRICT) fails the run; the default only reports.
- The harness has its own small JSON reader and writer so it does not depend on the application's Jackson version.
- The golden file itself is validated in every normal build.
- Prompts start in `evals/` and move into the agent module when the agent is built (Sprint 4).

## Consequences
Quality changes become visible and the injection case guards a safety property from day one. Small golden sets give noisy scores; the set must grow with real failures.
