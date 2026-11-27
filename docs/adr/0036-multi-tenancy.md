# ADR 0036: Multi-tenancy with a shared schema; keys now, row-level security at Phase 4

- Status: accepted (founder approved Phase 0 on 2026-11-27)
- Date: 2026-11-27

## Context
The brief requires tenant isolation, and the draft enabled RLS in R1.
- **Security:** RLS is defence in depth, not the primary control. It wants the scaffold and tests early.
- **Enterprise, AI and Product:** RLS on every JdbcClient path for one tenant is over-engineering now.
- **Data Architect:** the draft's mechanism cannot be built as written. `SET LOCAL` cannot take a bind parameter, `TransactionSynchronization` has no begin hook, `FORCE RLS` breaks Flyway data migrations, the budget resolver needs platform-wide `llm_usage`, and a ThreadLocal context does not reach executor pools. Composite keys are the expensive part to retrofit, so they should come now.

## Decision
- **Model**: shared database and shared schema, with `tenant_id uuid NOT NULL` on tenant-scoped tables. Market data, instruments, public documents and intelligence events are **global** (no tenant_id). Tenant uploads go in separate `tenant_*` tables, never mixed with global rows by a nullable `tenant_id`. `llm_usage` and budgets stay in a system scope.
- **Now (R1, one small forward-only migration):**
  - a `tenant` table seeded with the founder tenant (fixed UUID);
  - `tenant_id` on the `paper_*` tables;
  - `UNIQUE (tenant_id, name)` on `paper_account`;
  - `UNIQUE (id, tenant_id)` plus composite foreign keys from the child tables;
  - `job_run_item (run_id, tenant_id, status)` for per-tenant job outcomes.
  - Every tenant-scoped repository method takes a `TenantId` explicitly: the primary control, which code review and tests can see.
- **Phase 4 (behind `TenantIsolationIT` as the exit gate):**
  - Enable RLS policies that use `app_tenant()`. This function raises a clear error when the setting is missing or empty.
  - Set the setting with `set_config('app.tenant_id', ?, true)` in a `DataSourceTransactionManager.prepareTransactionalConnection` override. Tenant repositories run only inside transactions (ArchUnit).
  - Carry the context with `ScopedValue` or explicit parameters, never a ThreadLocal across pools.
  - `aura_owner` (migrations) gets `BYPASSRLS`. The app role does not.
  - Cross-tenant jobs iterate tenants explicitly.
- Database-per-tenant is a possible premium option for enterprise customers later.

## Consequences
The costly retrofit (keys) is done while only one account exists. RLS arrives when there is a second tenant to isolate. Until then, isolation is by explicit parameter and tests.
