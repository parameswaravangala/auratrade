# ADR 0030: Phase 0 reset keeps the modular monolith, one Maven project and the Vite SPA

- Status: proposed (Phase 0, awaiting founder approval)
- Date: 2026-11-27

## Context
The master brief (November 2026 edition) describes microservice-ready domains, a multi-language repository tree (`backend/services`, `ai-platform/`, `quant-engine/`), Next.js 16, Kafka and Temporal. On 2026-11-27 the founder chose a full reset of plan and architecture. The repo already has a working modular monolith (ADR 0001), 25 ADRs and a live paper account. The brief's own section 8.1 says not to create one microservice per domain.

Reviewers: the Enterprise Architect agreed on the monolith, the single Maven project and the SPA, and warned that an extracted service sharing the monolith's schema is a distributed monolith. The Product Manager questioned platform work that delivers no user value.

## Decision
- Keep one Spring Boot deployable and one Maven project at the repo root. The brief's tree maps onto packages (engineering-standards section 1). Maven multi-module comes only when an extraction trigger fires.
- Keep React + Vite for the authenticated app. Next.js is not adopted. A future public marketing site is a separate static site.
- Extraction rule: an extracted service owns its schema and write path from day one. No shared tables across deployables. Triggers are in architecture-overview section 5.
- The architecture diagrams must match the real dependency graph. The first story is to bring the ArchUnit rules (overview section 4, rules 3 to 6) in with a `FreezingArchRule` baseline of today's violations (for example `api` importing repositories). Each violation is then paid down, never added to.

## Consequences
No migration churn. The reset changes direction and adds capabilities without rewriting working code. The cost is that boundaries rely on ArchUnit, so its rules must grow with the modules. Revisit at the first extraction trigger.
