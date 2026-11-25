# ADR 0007: Provider adapter behind the gateway, local-first development

- Status: accepted
- Date: 2026-11-26

## Context
We do not want to spend on LLM APIs yet, and provider choice must stay swappable.

## Decision
- Everything that touches Spring AI lives in one class, SpringAiModelInvoker, behind the ModelInvoker interface. ArchUnit forbids Spring AI types outside llmgateway.
- Until a paid key is added, every tier maps to one local Ollama model. HEAVY and LIGHT carry pretend prices equal to the planned Claude prices so budget and fallback logic are exercised realistically.
- Adding Claude later means a second ModelInvoker and a profile that maps tiers to Claude models and real prices. Agents and budget code do not change.
- Provider failures fall back one tier for non-critical agents. Critical agents retry the same tier once and then fail closed.

## Consequences
Prompt-cache savings and Claude tool-calling quality cannot be measured until the Claude adapter exists. Pretend prices mean dev spend figures are illustrative only.
