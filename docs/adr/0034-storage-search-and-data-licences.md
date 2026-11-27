# ADR 0034: Postgres-only storage for Phase 1 and a single data-source licence registry

- Status: accepted (founder approved Phase 0 on 2026-11-27)
- Date: 2026-11-27

## Context
The brief lists Redis, OpenSearch, Qdrant, pgvector, S3, Parquet and Iceberg. On licences: Redis 8.x OSS is tri-licensed RSALv2/SSPLv1/AGPLv3, while Valkey is BSD-3-Clause (checked 2026-11-27).
- The AI Architect wants pgvector deferred: FTS is enough for Phase 1, the embedding model is unchosen, and the gateway has no embedding API.
- The Data Architect found that the draft's `source` table covers documents only, while `candle.source` (Upstox, the largest licensing risk) has no licence record.
- The Data Architect also found that a boolean `commercial_use_allowed` cannot express the real terms.

## Decision
- Phase 1 storage: PostgreSQL only, with full-text search (`tsvector` + GIN). pgvector stays installed but unused until Phase 3, when an ADR picks an embedding model, its dimension and an embedding API on the gateway.
- Caching: none. If a measured trigger fires, use Valkey, never Redis OSS.
- One `data_source` registry for every kind: `PRICE`, `REFERENCE`, `DOCUMENT`, `MACRO`. Its columns are `code`, `kind`, `terms_url`, `terms_read_on`, `may_automate`, `may_store_days`, `may_display_external`, `may_derive`, `attribution_text`, `cost_note`, `status`. `candle.source` and documents reference it by `code`, keeping the existing text code style from V6.
- The API checks `may_display_external` and refuses to serve rows from a restricted source to any principal outside the founder's own tenant. This is a data-entitlement check, not an identity special case (Security review).
- Third-party document text: keep a ≤ 300-char excerpt plus metadata, and purge the body after `may_store_days`. Whether a 300-char excerpt is acceptable is a legal assumption, so it is **unverified** until counsel reviews it.

## Consequences
One store to back up and secure. The licensing check is enforced in code and visible in data. Search loses body terms once bodies are purged (documented).
