# ADR 0001: Modular monolith first

- Status: accepted
- Date: 2026-11-25

## Context
Solo developer, Phase 1 is paper trading for personal use. Distributed services would add operational cost and failure modes without a measured need.

## Decision
One Spring Boot application, one Maven module, one package per feature module. Module boundaries are enforced by ArchUnit tests. Split into services or Maven modules only for a measured reason.

## Consequences
Simple deploys and debugging. Boundaries rely on discipline and tests, so keep ArchUnit rules current.
