# Data architecture

- Owner: Data Architect. Reviewers: Enterprise Architect, Security Architect, Head of Quant Research, Data Governance. Approver: founder.
- Status: **DESIGN**. Existing tables: V1 to V10 on `main`.

## 1. Storage responsibilities (no interchangeable stores)

| Store | Holds | Does not hold |
|---|---|---|
| PostgreSQL 18 (system of record) | instruments, candles, calendar-derived data, source documents, intelligence events, evidence, embeddings (pgvector), users/tenants/roles, watchlists, alerts, paper trading, LLM usage, jobs, outbox, audit | blobs over 1 MB, research scratch data |
| `data/` folder (git-ignored), later S3 | raw provider files, Parquet research exports, `pg_dump` backups | anything committed to git |
| Browser | UI preferences only | tokens in localStorage (the OIDC tokens stay in memory) |
| (Deferred) Valkey | hot caches and rate-limit counters, never a system of record | |

## 2. Data domains and ownership

| Domain | Scope | Tables (existing unless marked new) | Owner module |
|---|---|---|---|
| Reference | GLOBAL | `instrument`, `instrument_provider_key`, `instrument_alias` | marketdata |
| Prices | GLOBAL | `candle` (per source), `backfill_request`, `backfill_item` | marketdata |
| Intelligence | GLOBAL (public sources) or TENANT (uploads) | new: `source`, `source_document`, `document_cluster`, `document_embedding`, `intelligence_event`, `event_instrument`, `evidence` | intelligence |
| Agents and LLM | per tenant | `llm_usage` (exists, add tenant_id), new: `agent_run`, `agent_step`, `model_registry`, `prompt_version` | agents / llmgateway |
| Identity | per tenant | new: `tenant`, `app_user` (Keycloak `sub` link), `role_binding`, `tenant_setting` | identity |
| User workspace | per tenant | new: `watchlist`, `watchlist_item`, `alert_rule`, `alert` | watchlist / notification |
| Paper trading | per tenant | `paper_account`, `paper_order`, `paper_position`, `paper_trade`, `paper_equity` (add tenant_id) | paperbroker |
| Platform | system | `job_run`, `job_lock`, new: `outbox_event`, `processed_event` | platform |
| Audit | per tenant, append-only | new: `audit_event` | audit |

## 3. Multi-tenancy model

> **Review outcome: [ADR 0036](../adr/0036-multi-tenancy.md) replaces the timing and the mechanism below.** R1 adds the `tenant` table, `tenant_id` and composite keys on the paper tables, and explicit `TenantId` parameters. RLS arrives in Phase 4:
> - `app_tenant()` raises when the setting is missing;
> - `set_config(..., true)` runs in `prepareTransactionalConnection`;
> - the owner has BYPASSRLS;
> - the context is a ScopedValue, not a ThreadLocal.
>
> Tenant uploads go in separate `tenant_*` tables, not in global tables with a nullable tenant_id. `llm_usage` stays system-scoped for budgets.
>
> Other changes from the review:
> - The licence registry is unified as `data_source` ([ADR 0034](../adr/0034-storage-search-and-data-licences.md)).
> - `evidence` gets a foreign key and a licence tag.
> - Event status changes are kept in a history table.
> - Embeddings are keyed by (document, model, chunk).
> - `candle_revision` keeps superseded bars so paper decisions stay reproducible.
> - Identity keys follow the existing `BIGSERIAL` convention.
> - `created_at` has no DB default (the Clock rule).
> - The research export is gzip CSV, not Parquet from Java ([ADR 0031](../adr/0031-java-single-source-of-numbers.md)).
>
> The original draft is kept below for traceability.

**Decision (ADR 0036): shared database, shared schema, `tenant_id uuid NOT NULL` on every tenant-scoped table, enforced by PostgreSQL row-level security.** Global market data has no tenant_id and is read-only to tenant roles.

```sql
-- pattern applied to every tenant-scoped table (illustrative; real DDL lands in Flyway after approval)
ALTER TABLE watchlist ENABLE ROW LEVEL SECURITY;
ALTER TABLE watchlist FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON watchlist
  USING (tenant_id = current_setting('app.tenant_id')::uuid)
  WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
```

- The application connects as role `aura_app` (not the owner, no `BYPASSRLS`). Each transaction runs `SET LOCAL app.tenant_id = ?` from the authenticated `TenantContext`, set in a `TransactionSynchronization` hook. A missing context means `current_setting` raises, so the query **fails closed**.
- Jobs that span tenants (paper-trading job) iterate tenants explicitly and set the context per tenant.
- Migrations run as `aura_owner`. `aura_readonly` is for analytics exports.
- Until Phase 4 there is exactly one tenant (`founder`). Existing tables get `tenant_id` with that default in a forward-only migration, then the default is dropped.
- Why not schema-per-tenant: Flyway would run N times, connection pools fragment, and it is overkill below about 500 tenants. Enterprise customers that need isolation get **database-per-tenant** as a premium option later, using the same schema.

## 4. Key new tables (sketch)

```sql
source(id smallint PK, code text UNIQUE, kind text CHECK (kind IN ('ANNOUNCEMENT','NEWS','MACRO')),
       licence_tag text NOT NULL, commercial_use_allowed boolean NOT NULL DEFAULT false,
       poll_interval interval, enabled boolean NOT NULL DEFAULT false);

source_document(id bigint PK GENERATED ALWAYS AS IDENTITY, source_id smallint REFERENCES source,
       external_id text NOT NULL, url text, title text, body text, published_at timestamptz NOT NULL,
       fetched_at timestamptz NOT NULL, content_sha256 bytea NOT NULL, simhash bigint,
       cluster_id bigint, language text, tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(title,'')||' '||coalesce(body,''))) STORED,
       tenant_id uuid NULL,                 -- null = global public document
       UNIQUE (source_id, external_id));
CREATE INDEX ON source_document USING gin (tsv);
CREATE INDEX ON source_document (published_at DESC);

document_embedding(document_id bigint PK REFERENCES source_document, model text NOT NULL,
       embedding vector(768) NOT NULL);     -- dimension fixed per model; HNSW index
CREATE INDEX ON document_embedding USING hnsw (embedding vector_cosine_ops);

intelligence_event(event_id uuid PK, schema_version text NOT NULL, event_type text NOT NULL,
       published_at timestamptz NOT NULL, processed_at timestamptz NOT NULL,
       significance numeric(5,4) NOT NULL, confidence text NOT NULL,
       status text NOT NULL CHECK (status IN ('PUBLISHED','QUARANTINED','RETRACTED')),
       payload jsonb NOT NULL,              -- the full v1 document, validated before insert
       tenant_id uuid NULL);
event_instrument(event_id uuid REFERENCES intelligence_event, instrument_id bigint REFERENCES instrument,
       role text NOT NULL, PRIMARY KEY (event_id, instrument_id));
evidence(event_id uuid, evidence_id text, kind text, document_id bigint NULL, ref text NOT NULL,
       PRIMARY KEY (event_id, evidence_id));

audit_event(id bigint PK GENERATED ALWAYS AS IDENTITY, at timestamptz NOT NULL, tenant_id uuid NOT NULL,
       actor_sub text NOT NULL, actor_kind text NOT NULL CHECK (actor_kind IN ('USER','SYSTEM','AGENT')),
       action text NOT NULL, target_type text, target_id text, before jsonb, after jsonb,
       correlation_id text, prev_hash bytea, hash bytea NOT NULL);   -- hash chain for tamper evidence
-- trigger rejects UPDATE and DELETE on audit_event
```

## 5. Data lineage and quality

- **Lineage**: every derived row points to its inputs. A candle carries `source` (exists). An event links evidence to `source_document` (and so to its URL, `fetched_at` and licence) and to candle sessions by `asOfSession`. An agent output points to `agent_run` (model, prompt version). The question "why does the UI say this" is answered by joining, not by logs.
- **Quality gates** (fail closed): `CandleValidator` ERROR/WARNING (exists); the EOD coverage gate at 90 % (exists); document validation (non-empty body, `published_at` not in the future by more than 5 min, known source); event schema validation (stage 8).
- **Freshness**: B-043 (`/api/v1/marketdata/freshness`, WIP on `feat/data-freshness-api`) becomes the freshness contract that every output's "as of" statement uses.
- **Corporate actions** (B-020): until adjusted, every price-derived output carries the limitation "unadjusted for splits/bonuses", and backtests keep the existing 40 % gap exclusion. This is a Phase 2 blocker for fundamentals and long backtests, so it is in the risk register.
- **Survivorship**: the instrument master only covers listed instruments, so delisted ones are missing (CLAUDE.md "Next"). Every backtest report states this limitation until the delisted-instrument story is done.

## 6. Retention

| Data | Retention | Basis |
|---|---|---|
| Candles, instruments | indefinite | research |
| Source documents (third-party text) | per the source's terms. Default: keep metadata + 300-char excerpt indefinitely; full body 90 days unless the licence permits more | licensing |
| Embeddings | as long as the document body | |
| LLM prompts/outputs | 90 days | debugging, eval |
| Audit events | 8 years (assumption; counsel to confirm against SEBI record-keeping if we become an intermediary) | |
| User personal data | account life + 30 days; erasure on request (DPDP; obligations phase in through May 2027) | DPDP |

## 7. Research data flow (Python lab)

Nightly job `research-export` (new) writes `data/exports/{table}/{yyyy-mm-dd}.parquet` for candles, instruments and intelligence_event payloads, using the JDBC result set and Parquet writer in the Java app. Polars reads them in JupyterLab. Findings come back to production **only** as a Java implementation with tests, plus a research note in `docs/research/`.
