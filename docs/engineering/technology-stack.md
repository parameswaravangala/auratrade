# Technology stack and dependency governance

- Owner: CTO. Reviewers: Enterprise Architect, Security Architect, FinOps. Approver: founder.
- Versions are in [technology-version-matrix.md](technology-version-matrix.md). This file records what each technology does and when the deferred ones get adopted.

## 1. Principle: smallest stack that meets the requirement

The master brief lists over 40 technologies. Adopting a technology costs operations, upgrades, security patching and learning time. Every one here is either **adopted** (with a named job), **deferred** (with a measurable adoption trigger), or **rejected** (with a reason). A deferred technology is not adopted without a new ADR showing that its trigger has fired.

## 2. Adopted

| Concern | Choice | Job in AuraTrade | Why not the alternative |
|---|---|---|---|
| Core services | Java 25 LTS, Spring Boot 4.2, modular monolith (ADR 0001, reaffirmed in ADR 0030) | All business logic, APIs, scheduling, ingestion, agents | One deployable for a team of one human. ArchUnit keeps the modules extractable |
| Persistence | PostgreSQL 18, Flyway, JdbcClient | System of record: instruments, candles, events, evidence, users, tenants, audit | JPA adds hidden queries. JdbcClient keeps SQL explicit (CLAUDE.md) |
| Vector search | pgvector in the same database, **unused until Phase 3** (ADR 0034) | News and filing embeddings for retrieval | Qdrant would be a second store to back up and secure |
| Full-text search | PostgreSQL `tsvector` + GIN | Announcement and news search | OpenSearch is a cluster to operate for a corpus well under 10 M documents |
| Business events | Transactional outbox: Spring Modulith registry or our own table, decided by the R3-00 spike (ADR 0032) | Reliable `intelligence_event` publication to alerts, briefing and UI | Kafka is justified only by multiple independent deployables or replay over retained logs |
| Scheduling | Existing `JobRunner` (ADR 0017): `@Scheduled` + `job_run` + `job_lock` | EOD, instruments, backfill, intelligence polling | Temporal adds a cluster. Our jobs are minutes long and idempotent by window |
| LLM access | Spring AI 2.1 behind `LlmGateway` (ADR 0003, 0004) | Every model call: tiers, budgets, usage log | Unchanged; LiteLLM is revisited at the ADR 0009 triggers |
| Local inference | Ollama 0.40.x | Development and evaluation at zero cost | |
| Agent orchestration | Plain Java pipelines with explicit state (ADR 0033) | Market Intelligence Agent stages | LangGraph is Python. LangGraph4j is deferred until a workflow needs cycles or branching that the model chooses |
| Identity | Spring Security BFF session (form login + passkey, single user) now; Keycloak 26.8 through `oauth2Login` before gate G3 (ADR 0035) | Sign-in, the principal behind paper-order decisions (B-031), later tenants | Keycloak costs 1 to 2 GB of RAM for one user; SPA-held tokens are exposed to XSS |
| Web UI | React 19.3 + Vite 8 SPA (ADR 0019, reaffirmed in ADR 0030) | Authenticated professional workspace | Next.js server rendering helps SEO and public pages, which the app does not need |
| Research workspace | Python 3.14 + JupyterLab + Polars, reading Parquet exports (ADR 0031) | Exploratory quant research, ML prototyping | It does not serve production numbers, so there is no duplicated logic in production |
| CI | GitHub Actions (free for public repos; check the private-repo minutes) | Build, test, scan | |
| Supply chain | Trivy (image, SBOM), Semgrep CE, Dependabot, CycloneDX Maven plugin. **Planned (R1-08); not built yet** | Vulnerabilities, SAST, SBOM | SonarQube Community needs a server; Semgrep plus Spotless covers us for now |

## 3. Deferred, with adoption triggers

| Technology | Adopt when (measurable) |
|---|---|
| Apache Kafka 4.x / MSK | Two or more separately deployed consumers of the same stream, **or** a replay need beyond what the outbox table holds (over 30 days), **or** over 500 events/s sustained |
| Temporal | A workflow lasts longer than 24 h with human waits and needs compensation (for example broker onboarding or KYC), **or** more than 5 job types need cross-job sagas |
| LangGraph4j / LangGraph | An agent needs a model-chosen loop over tools with checkpointed state (Research Coordinator, Phase 3) |
| Redis/Valkey | A measured p95 hot-path read over 50 ms that Postgres tuning cannot fix, **or** distributed rate limiting across more than 1 instance. Use Valkey (BSD) |
| OpenSearch | Over 10 M searchable documents, **or** p95 search over 300 ms on Postgres FTS |
| Kubernetes / EKS | More than 1 paying tenant with an availability SLA over 99.5 %, **or** more than 3 deployables |
| OpenTofu (IaC) | The first cloud resource. Everything in the cloud comes from code from day one |
| OpenTelemetry + Prometheus LTS + Grafana + Loki + Tempo | First non-dev environment (Phase 5). Until then Micrometer and Actuator |
| vLLM | A GPU host is approved (cost) |
| MLflow | The first trained model that serves a production number |
| Iceberg | Research data over 100 GB or a multi-engine query need |
| AWS Bedrock AgentCore | Never by default. It is a paid managed runtime and needs a cost case |

## 4. Rejected for now

| Technology | Reason |
|---|---|
| Microservice per domain | Contradicts the brief's own section 8.1 and ADR 0001; no measured need |
| Next.js for the app | See ADR 0030 |
| Redis OSS 8.x | Tri-licence RSALv2/SSPLv1/AGPLv3. Valkey is the BSD alternative |
| Microsoft Agent Framework | Release status and Java support unconfirmed at the source (404); Spring AI covers the need |
| Python serving production indicators or backtests | Duplicates validated Java logic (brief section 6.3: "Avoid duplicating Java business logic in Python") |

## 5. Dependency governance

1. **Source of truth**: the Maven BOM (Spring Boot parent and Spring AI BOM) and `ui/package-lock.json`. Never override a Boot-managed version without an ADR line saying why.
2. **Exact pins**: every dependency outside a BOM, every Docker image (tag **and** digest for production images), every GitHub Action (commit SHA) and every Ollama model tag.
3. **Age rule**: no release younger than 14 days enters `main`, except security fixes. This guards against compromised or pulled releases.
4. **Update flow**: Dependabot opens weekly grouped PRs (Maven, npm, Actions, Docker). CI must pass `verify` plus `npm run check`. Minor and major updates need a line in the PR about the release notes read.
5. **Monthly review** (CTO role, first Monday): reconcile [technology-version-matrix.md](technology-version-matrix.md) against official pages, record EOL dates, and raise upgrade stories.
6. **Vulnerabilities**: Trivy and `npm audit` in CI. A critical or high finding with a fix fails the build. Without a fix, it goes to the risk register with a review date.
7. **Licences**: the CycloneDX SBOM is attached to each release build. Allowed without review: MIT, BSD, Apache-2.0, MPL-2.0, EPL-2.0. These need a founder decision before use: AGPL, SSPL, BUSL, RSAL, and commercial or unknown licences.
8. **Reproducibility**: Maven wrapper pinned, `npm ci` only, `-Dmaven.repo.local` cache in CI, no `latest` tags anywhere.
