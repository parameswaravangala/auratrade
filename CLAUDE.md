# AuraTrade

AI-assisted trading research platform for Indian equities (NSE). Phase 1 is research, playbooks and paper trading only. Solo project. No live orders, and no paid data or LLM spend yet.

## Working with me
- I am an experienced Java and Spring Boot developer, new to Python and to agentic AI. Explain AI concepts briefly with Java and Spring analogies. Do not assume Python.
- Act as architect: raise alternatives and trade-offs before building. (We once built our own LLM gateway without comparing LiteLLM first; see ADR 0009.)
- Work in small, verified steps. Run the build yourself and fix failures instead of asking me to paste errors.
- Spend nothing without asking: no paid APIs, subscriptions or data plans. LLM development uses local Ollama.
- Do not state facts about exchange rules, tickers, holidays, regulation or prices from memory. Verify at the source, and say plainly when something is unverified.
- My shell is a mix of Git Bash, PowerShell and Command Prompt on Windows. Give commands that work there, or say which shell.

## Stack
Java 21 (move to 25 LTS later), Spring Boot 4.1.x, Spring AI 2.0.x (Ollama starter), PostgreSQL 17 with pgvector (Docker), Flyway, JdbcClient (no JPA), JUnit 5, AssertJ, Testcontainers, ArchUnit, Maven wrapper, GitHub Actions, Docker.

## Commands
- Format: `./mvnw spotless:apply`
- Build and all tests (needs Docker for Testcontainers): `./mvnw verify`
- Local database: `docker compose up -d db`
- Run with the dev endpoints: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run` (PowerShell: set `$env:SPRING_PROFILES_ACTIVE="dev"` first)
- Evals against local Ollama (tagged `eval`, excluded from normal builds): `./mvnw verify -Peval`. See `evals/README.md`.

## Architecture rules
- Modular monolith: one package per module under `com.auratrade.<module>`. The shared kernel `platform` must not depend on feature modules. ArchitectureTest enforces boundaries.
- Only `llmgateway` may use Spring AI types (ArchUnit). Every LLM call goes through `LlmGateway`. Agents ask for a tier (HEAVY, LIGHT, LOCAL), never a model. Callers pass `CallOptions` (use `structuredJson()` for extraction).
- Budget rules (ADR 0006): at 60 percent of the monthly budget non-critical agents drop one tier, at 75 to their floor, at 85 they stop, at 100 critical agents stop. Critical agents (RISK_REVIEW) are never downgraded and fail closed. Daily token caps apply to every agent.
- Agents are read-only and never place orders. A human approves every trade. Paper trading first (ADR 0005).
- LLM output and news text are untrusted data. Prompt injection succeeded in our evals (case n21), so validate everything and never let model output trigger an action directly.
- Code computes, the LLM interprets. Indicators, backtests, sizing and order math are plain Java.
- Money is BigDecimal, never double. Time comes from an injected Clock. Store UTC; schedule and draw day and month boundaries in IST (Asia/Kolkata).
- Fail closed. The trading calendar throws for years it does not cover. Never guess.

## Conventions
- Work on branches and open PRs. Conventional Commits, for example `feat(marketdata): add instrument loader`. CI must be green.
- Spotless with google-java-format. Flyway migrations are forward-only. Record significant decisions as ADRs in `docs/adr`. Runbooks go in `docs/runbooks`.
- Unit tests are `*Test`; integration tests are `*IT` and use Testcontainers. Prefer pure, I/O-free logic that can be unit tested.
- No secrets in git. `.env` is ignored. Never commit exchange data: real files live in a git-ignored `data/` folder; fixtures are synthetic.

## Current state (update this section as work lands)
- Sprint 0 done: skeleton, CI, quality gates.
- Sprint 1 done: LLM gateway (tiers, budget resolver, fallback, usage log, spend views, budget alerts), Ollama adapter, eval harness with a 21-case triage golden set. Eval findings: JSON mode with temperature 0 removed format failures; prompt v2 improved relevance; a 3B model invents tickers; injection case n21 fails every run; sentiment labels on mixed news are ambiguous.
- Sprint 2 (market data) in progress. Slice 1 merged (PR #18): `TradingCalendar` (fail closed), `Candle`, `Interval`, `CandleValidator` with ERROR and WARNING rules, synthetic candle generator for tests, ADR 0012. The calendar `src/main/resources/calendar/nse-calendar.csv` covers 2026 (from the official NSE Equities trading-holiday page); 2027 is not covered yet. `CalendarCoverageCheck` warns at startup (logger `CalendarAlerts`) from 1 December if next year is missing. Runbook: `docs/runbooks/trading-calendar.md`.
- Next Sprint 2 slices: Flyway V3 tables (instrument, candle); instrument master loader (Kite instruments CSV, reportedly no auth, verify); company-name to symbol resolver (backlog B-001); importer for locally downloaded end-of-day files; replay fixtures; staleness alert; client-side rate limiter.
- Backlog: `docs/backlog.md`.

## Background documents
Claude docs "AuraTrade Architecture & Design" and "AuraTrade Development Plan" hold the full design and the standards and sprint plan. They are not in the repo; ask me if you need a section pasted.
