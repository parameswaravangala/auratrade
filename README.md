# AuraTrade

AI-assisted trading research platform for Indian markets (NSE). Phase 1: research, playbooks and paper trading. Java, Spring Boot 4, Spring AI 2.x, PostgreSQL with pgvector.

## Prerequisites
- JDK 21 or newer
- Maven (only once, to generate the wrapper)
- Docker
- Git

## First-time setup
```
cp .env.example .env
mvn wrapper:wrapper        # generates ./mvnw and .mvn/wrapper, then commit them
./mvnw spotless:apply      # format the code once with google-java-format
./mvnw verify              # unit tests, integration tests (Testcontainers), coverage
```

## Run locally
```
docker compose up -d db
./mvnw spring-boot:run
curl localhost:8080/actuator/health
```

Optional local LLM for later sprints: `docker compose --profile local-llm up -d`.

## Everyday commands
| Task | Command |
|---|---|
| Format | `./mvnw spotless:apply` |
| Unit tests only | `./mvnw test` |
| Everything | `./mvnw verify` |
| Coverage report | `target/site/jacoco/index.html` |

## Layout
- `src/main/java/com/auratrade/<module>`: one package per module (boundaries enforced by `ArchitectureTest`)
- `src/main/resources/db/migration`: Flyway migrations (forward-only)
- `docs/adr`: architecture decision records
- `docs/runbooks`: operational runbooks
- `.github`: CI, Dependabot, PR template

## Rules of the road
- Work through pull requests; CI must be green.
- Conventional Commits, for example `feat(llmgateway): add budget resolver`.
- No secrets in git. Use `.env` locally and environment variables in production.
- Every LLM call goes through `llmgateway` (from Sprint 1).
