# ADR 0002: PostgreSQL with pgvector as the single data store

- Status: accepted
- Date: 2026-11-25

## Context
We need relational data, time series at modest scale, and similarity search over news and journal entries.

## Decision
Use PostgreSQL with the pgvector extension for everything in Phase 1. Access with plain JDBC (JdbcClient). Schema managed by Flyway, forward-only migrations.

## Consequences
One system to run, back up and secure. Revisit a dedicated vector store or time-series store only if measured limits appear.
