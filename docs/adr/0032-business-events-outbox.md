# ADR 0032: Reliable business events through a transactional outbox, built when first needed

- Status: accepted (founder approved Phase 0 on 2026-11-27)
- Date: 2026-11-27

## Context
The brief asks for event schemas, idempotency, DLQs, replay and an outbox, and suggests Kafka. The draft designed a custom outbox. The Enterprise Architect objected that this repeats the ADR 0009 mistake (building before comparing): Spring Modulith offers a JDBC event publication registry and Kafka externalisation. They also found gaps in the draft's semantics: handler transactions, per-aggregate ordering under retries, and LLM work running inside the dispatcher. The Product Manager asked to build it only when first needed.

## Options
| Option | For | Against |
|---|---|---|
| A. Spring Modulith event publication registry | maintained, outbox semantics, module verification, Kafka externalisation later | compatibility with Boot 4.2 **unverified**; less control over ordering and DLQ |
| B. Own `outbox_event` table + dispatcher | full control, about 300 lines | we own retries, DLQ, replay and their bugs |
| C. Kafka now | replay, scale | a cluster to run for one deployable; brief section 6.6 says use it only when justified |

## Decision
- Story R3-00 is a one-day spike: verify Spring Modulith's release that supports Boot 4.2, then run the MIA event flow on it. If it meets the rules below, adopt A. Otherwise B, using the draft design.
- Rules for either option:
  - A handler's writes and its idempotency record commit in one transaction.
  - Delivery for an aggregate stops while an earlier event of that aggregate is pending or dead (strict per-aggregate order).
  - Events whose work is long or LLM-bound only **enqueue** a lease-protected job (`JobRunner`); the dispatcher never calls an LLM.
  - Event payload schemas live in `contracts/events/`, versioned.
- Built in R3 with its first consumer (event processing), not in R1.
- Kafka triggers stay as in technology-stack section 3.

## Consequences
We compare before we build. Ordering and idempotency rules are explicit and testable. If Modulith wins, its tables and conventions become part of our schema.
