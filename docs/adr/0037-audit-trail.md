# ADR 0037: Append-only audit trail with an external anchor

- Status: proposed
- Date: 2026-11-27

## Context
B-031 needs an audit of order decisions and edits. The draft proposed a hash-chained `audit_event` with a trigger that blocks UPDATE and DELETE.
- **Security:** the table owner can drop the trigger and recompute the chain. Without an anchor outside the database, this is tamper evidence against the application, not against the database owner.
- **Data:** an 8-year audit conflicts with DPDP erasure if payloads contain personal data. The chain also needs serial inserts.

## Decision
- An `audit_event` table owned by the `aura_audit` role. Rows are inserted only through a `SECURITY DEFINER` function. `aura_app` has EXECUTE on it and no UPDATE, DELETE or TRUNCATE. The trigger still rejects UPDATE and DELETE.
- One hash chain per tenant, with inserts serialised by a per-tenant advisory lock. `hash = sha256(prev_hash || canonical_json(row))`.
- **Anchor**: a daily job writes each chain head (tenant, last id, hash, date) to a place outside the database. Phase 1: a signed commit in a separate private git repo, or a daily email of the chain heads to the founder (B-027 channel is email), whichever the founder picks. Phase 5: S3 Object Lock in compliance mode.
- Payloads carry no personal data: a pseudonymous actor id plus the business fields (order id, quantity, limit, decision). Mapping the actor to a person lives in identity tables, which can be erased.
- Retention of 8 years is an assumption until counsel confirms it.
- Documents say "tamper-evident against the application; anchored daily" and claim nothing stronger.

## Consequences
Real non-repudiation for paper decisions at no cost. A daily anchor job to watch. Erasure requests do not break the chain.
