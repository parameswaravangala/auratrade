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

### Local-only request guard (on in every profile, including prod)
The app has no authentication yet (backlog B-031), so `platform.web.LocalRequestGuard` serves only this machine:
- **Host:** a request whose `Host` header is not `localhost`, `127.0.0.1` or `[::1]` (any port) gets 403 problem+json.
- **Origin:** so does a browser request whose `Origin` is not in `auratrade.security.local-guard.allowed-origins`. The default list is `localhost` and `127.0.0.1` on ports 5173 and 5174 (the Vite UI) and 8080.
- **Dev profile:** the server also listens on 127.0.0.1 only.
- **Prod:** this means a `prod` profile reached through any real hostname answers 403 to everything. That is deliberate and fails closed. Do **not** set `auratrade.security.local-guard.enabled=false` to "fix" prod. Turn the guard off, or allow-list the real host, only once B-031 authentication is in place.
- **Other ports:** to run the UI on another port, add its origin to the property, for example in `.env`.

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
