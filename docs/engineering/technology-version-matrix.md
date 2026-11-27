# Technology version matrix

- Owner: CTO. Reviewers: Enterprise Architect, Security Architect. Approver: founder.
- Checked: 2026-11-27, against the official release pages listed. "Unconfirmed" means the page did not show the fact or refused the fetch, so do not rely on that value.
- Rule: production uses GA only. A row moves from "Target" to "In use" only when the build on `main` runs that version with CI green.

## Status legend

- **In use**: what `main` builds with today (pom.xml, ui/package.json, docker-compose.yml, CI).
- **Target**: what the reset adopts in Sprint R1, after approval.
- **Deferred**: not adopted until the trigger named in [technology-stack.md](technology-stack.md) is met.

## Runtimes and languages

| Technology | Brief says | In use (main) | Latest GA, checked 2026-11-27 | LTS / support | Target | Source |
|---|---|---|---|---|---|---|
| Java | 25 LTS, evaluate 27 | 25 (pom `java.version`, CI `java-version: 25`) | JDK 27 GA 2026-09-15 (27.0.1 on 2026-10-21); non-LTS, about 6 months of Oracle support | 25 is LTS per secondary sources. **Unconfirmed at the source**: oracle.com and openjdk.org returned 403 | **25** (latest patch pinned through the Temurin/Corretto image). Try 27 in a CI matrix job that is allowed to fail | [Oracle JDK 27 notes](https://www.oracle.com/java/technologies/javase/27all-relnotes.html), [InfoQ](https://www.infoq.com/news/2026/09/java27-released/) |
| Python | 3.14 | none (no Python in repo) | 3.15.2 and 3.14.10 (both 2026-10-25) | 3.14 EOL 2030-10, 3.15 EOL 2031-10 | **3.14.10** for the research workspace only (ADR 0031). Pick up 3.15 once the PyTorch, Polars and LightGBM wheels for it are confirmed | [devguide](https://devguide.python.org/versions/), [python.org](https://www.python.org/downloads/) |
| Node.js | 26 LTS | engines `>=24`, CI 24 | v26.13.1, labelled "Latest LTS" | 26, 24 and 22 all LTS; the active/maintenance split is unconfirmed | **26** (CI and `engines`) | [nodejs.org](https://nodejs.org/en/about/previous-releases) |
| TypeScript | latest stable | 6.0.3 | 7.0.2 (native compiler, 2026-08-20); 7.1 beta | n/a | **Evaluate 7.0.2** in a spike. Move only if ESLint (typescript-eslint) and Vitest type-checking work with it | [GitHub](https://github.com/microsoft/TypeScript/releases) |

## Backend

| Technology | Brief | In use | Latest GA | Target | Notes |
|---|---|---|---|---|---|
| Spring Boot | 4.2.x | **4.1.2** | **4.2.0** (2026-11-19); 4.1.3 and 4.0.10 the same day | **4.2.0** | OSS support end dates unconfirmed (the spring.io support tab did not render). [releases](https://github.com/spring-projects/spring-boot/releases) |
| Spring AI | 2.1.x | **2.1.0 BOM on Boot 4.1.2** | 2.1.0 GA (2026-11-20); 2.0.3 | **2.1.0 on Boot 4.2.0** | Risk: the 2.1.0 milestone notes say "Upgrade Spring Boot to 4.2", so 2.1 on 4.1 is an untested pairing. Fix it in R1 (Boot to 4.2.0). [releases](https://github.com/spring-projects/spring-ai/releases) |
| Flyway | Flyway or Liquibase | Boot-managed | 13.13.0 (2026-11-19) | Boot-managed | Licence of the community edition unconfirmed; check before any change |
| Testcontainers | yes | Boot-managed | 2.0.5 (year not shown on the page) | Boot-managed | |
| LangGraph4j | (brief: LangGraph) | none | 1.9.10 (2026-11-17), MIT | **Deferred** (ADR 0033) | |
| Temporal | evaluate | none | server 1.32.2, Java SDK 1.41.1 | **Deferred** (ADR 0032) | SDK licence unconfirmed |
| Microsoft Agent Framework | evaluate | none | **Unconfirmed** (releases page 404) | **Rejected for now** (ADR 0033) | Java support unknown |

## Data and storage

| Technology | Brief | In use | Latest GA | Target | Notes |
|---|---|---|---|---|---|
| PostgreSQL | 18, evaluate 19 | **17** (`pgvector/pgvector:pg17`) | 19.0 (2026-11-19); 18.7; 17.12 | **18.7** | 18 supported to 2030-11-14. Re-evaluate 19 at 19.2 or later, once a pgvector image for it exists and RDS offers it (both unconfirmed). [versioning](https://www.postgresql.org/support/versioning/) |
| pgvector | pgvector or Qdrant | image tag pg17 | v0.8.7 (tag, 2026-10-01; no formal GitHub release) | 0.8.7 on PG 18 | |
| Redis | 8.x | none | 8.12.0; licence **RSALv2 / SSPLv1 / AGPLv3** | **Deferred**. If a cache is needed: **Valkey 9.2.1** (BSD-3-Clause) | AGPL and SSPL are a SaaS licence risk (ADR 0034) |
| OpenSearch | 3.x | none | 3.10.0 (2026-11-17) | **Deferred** | Postgres full-text plus pgvector covers MVP search |
| Qdrant | option | none | v1.20.2 | **Deferred** | |
| Kafka | 4.x | none | 4.4.0 (2026-10-22) | **Deferred** (ADR 0032) | |
| Parquet / Iceberg | yes | none | not checked | Parquet for research exports (R3); Iceberg deferred | |

## Frontend

| Technology | Brief | In use | Latest GA | Target | Notes |
|---|---|---|---|---|---|
| React | 19.3.x | 19.3.1 | 19.3.1 (2026-10-14) | 19.3.1 | |
| Next.js | 16.x | none (Vite SPA, ADR 0019) | stable 16.x **unconfirmed**: the releases page shows 16.5 canaries; the blog shows 16.4 (2026-10-06) | **Not adopted** (ADR 0030) | |
| Vite | (not in brief) | 8.3.8 | 8.4.0 (2026-11-26) | 8.4.x after 2 weeks (dependency age rule) | |
| Tailwind CSS | 4.x | 4.3.5 | 4.3.5 | 4.3.5 | |
| Lightweight Charts | licence review | 5.3.0 | 5.3.0, Apache-2.0 | 5.3.0 | **Licence condition**: the TradingView attribution notice from NOTICE plus a link to tradingview.com on a page users can see (the `attributionLogo` option satisfies the link part). Check that the UI does this (R1 story) |

## AI

| Technology | In use | Latest GA | Target | Notes |
|---|---|---|---|---|
| Ollama | image `ollama/ollama` (no tag pinned) | v0.40.18 | **Pin to 0.40.18** | An unpinned image breaks reproducible builds |
| vLLM | none | v0.34.0 | Deferred (needs a GPU) | |
| MLflow | none | 3.18.0 | Deferred (until we train models) | |
| LangGraph (Python) | none | 1.2.24, MIT | Deferred | |
| MCP spec | none | revision 2026-07-28 | Expose read-only research tools over MCP in Phase 3 | |

## Platform and operations (all deferred until Phase 5, except CI)

| Technology | Latest GA | Licence | Decision |
|---|---|---|---|
| Kubernetes | 1.37.2; EKS standard support 1.34 to 1.37 | Apache-2.0 | Deferred. Docker Compose until a paying tenant needs HA |
| Terraform | 1.17.2 | **BUSL-1.1** | Prefer **OpenTofu 1.13.10** (MPL-2.0) when IaC starts. Internal use of BUSL Terraform is likely fine, but OpenTofu avoids the question |
| Prometheus | 3.16.0; LTS 3.13.5 | Apache-2.0 | Use **LTS 3.13.x** when monitoring starts |
| Grafana | 13.3.2 | **AGPL-3.0** | Fine for internal monitoring that is not shown to customers. Never embed Grafana in the customer product |
| Keycloak | 26.8.2 (brief said 26.4) | Apache-2.0 (verify) | **Adopt in R1** for dev sign-in (ADR 0035) |

## Open items

1. Confirm JDK 25 LTS dates from a fetchable source (Adoptium or Corretto support pages).
2. Confirm the Spring Boot 4.1 and 4.2 OSS support end dates.
3. Confirm the latest stable Next.js 16.x. This is informational only, since Next.js is not adopted.
4. Confirm the Flyway community licence and the Testcontainers 2.0.5 release year.
5. Confirm that RDS PostgreSQL 18 and ElastiCache Valkey exist, before Phase 5.
