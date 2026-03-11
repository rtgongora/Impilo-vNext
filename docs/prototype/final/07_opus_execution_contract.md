# 07 — Opus Execution Contract (Index & Contract)

> **Document type**: Index document — points to canonical spec sources for execution phases and build planning.
> **Last updated**: 2026-03-11
> **Status**: CANONICAL INDEX — enforced by `scripts/spec-integrity-check.sh`

---

## Purpose

This document indexes the execution contract for the Impilo vNext Experience Platform. The original prototype described 11 implementation phases. In practice, the implementation followed a phase order derived from the plan specs and adjusted based on dependency analysis. This file maps the execution contract to its canonical sources.

## Summary Constraints (from original prototype)

- **11 implementation phases** with strict no-deviation rules
- Verification checklist per phase
- Progression: foundation → core layouts → auth → navigation → data layer → specialized pages → domain features

## Actual Execution Order (as implemented)

The implementation followed this phase order, derived from the plan specs and dependency graph:

| Phase | Description | Primary Spec Source | Key Artifacts |
|-------|------------|-------------------|---------------|
| 1 | Foundation Infrastructure | [IMPILO_VNEXT_BUILD_PLAN.md](../../plan/IMPILO_VNEXT_BUILD_PLAN.md) | PostgreSQL schema, Flyway migrations, Spring Boot skeleton |
| 2 | v1.1 Compliance Wiring | [API_CONVENTIONS_V11.md](../../plan/API_CONVENTIONS_V11.md) | V11HeaderFilter, IdempotencyFilter, GlobalExceptionHandler |
| 3 | Core Layouts | Summary metadata (4 layout variants) | AppLayout, EHRLayout, AuthLayout, MinimalLayout |
| 4 | Authentication | Golden paths A, B | AuthController, auth store, session storage |
| 5 | Navigation & Routing | Summary metadata (98 routes, 15 zones) | routes.ts, zone directories, route guards |
| 6 | Data Layer (BFF) | [SERVICE_CATALOG.md](../../plan/SERVICE_CATALOG.md) | 13 controllers, domain entities, repositories |
| 7 | Outbox & Eventing | [EVENTING_AND_TOPICS.md](../../plan/EVENTING_AND_TOPICS.md) | event_outbox table, EventEnvelope, outbox writes |
| 8 | Specialized Pages | Golden paths C-F | 98 page.tsx files across 15 zones |
| 9 | Testing & Verification | [TESTING_CONVENTIONS.md](../../plan/TESTING_CONVENTIONS.md) | GoldenContractIT, GoldenPathIntegrationTest, ExperienceV11ComplianceTest |
| 10 | Docker Compose & Smoke | Acceptance pack | docker-compose.yml, smoke-test.sh, bff-smoke.sh |
| 11 | Documentation & Acceptance | All plan docs | Acceptance pack, SPEC_CONFLICTS, ONLINE_VERIFICATION |

## Canonical Sources

### Build Planning

| Canonical File | Lines | What It Provides |
|---------------|-------|-----------------|
| [IMPILO_VNEXT_BUILD_PLAN.md](../../plan/IMPILO_VNEXT_BUILD_PLAN.md) | 727 | 27 components, 6 bundles, dependency graph, rollout sequence |
| [EXECUTION_PLAN_P20_P22.md](../../plan/EXECUTION_PLAN_P20_P22.md) | 160 | Prompts 20-22 execution plan with dependency ordering |
| [SERVICE_CATALOG.md](../../plan/SERVICE_CATALOG.md) | 282 | Service definitions, ports, rings, status |

### Quality Gates

| Gate | Tool | Location |
|------|------|----------|
| Static v1.1 compliance | V11ComplianceStaticVerifier | `tools/static-verifier/V11ComplianceStaticVerifier.java` |
| Unit + Integration tests | Maven + TestContainers | `services/experience-bff/src/test/java/` |
| Route parity | route-parity-check.mjs | `ui/experience/scripts/route-parity-check.mjs` |
| Type safety | TypeScript strict mode | `ui/experience/tsconfig.json` |
| Docker smoke | smoke-test.sh | `compose/experience/smoke-test.sh` |
| BFF smoke | bff-smoke.sh | `scripts/experience/smoke/bff-smoke.sh` |
| Full online verification | verify-online.sh | `scripts/experience/verify-online.sh` |
| Spec integrity | spec-integrity-check.sh | `scripts/spec-integrity-check.sh` |

### Verification Evidence

| Check | Expected Result |
|-------|----------------|
| Static verifier | 95/95 checks pass |
| ExperienceV11ComplianceTest | 7/7 tests pass |
| GoldenContractIT | All contract tests pass |
| GoldenPathIntegrationTest | 13/13 tests pass |
| Route parity | 98/98 routes matched |
| Spec integrity | All 8 index docs pass integrity check |

### Spec References

| Canonical File | What It Provides |
|----------------|-----------------|
| [TESTING_CONVENTIONS.md](../../plan/TESTING_CONVENTIONS.md) | Testing pyramid, CI gate definitions |
| [00-compliance-summary.md](../../architecture/v1.1/00-compliance-summary.md) | v1.1 compliance audit methodology |
| [experience-platform-acceptance-pack.md](../../acceptance/experience-platform-acceptance-pack.md) | Full acceptance criteria with commands and expected outputs |

## Spec Conflicts

- **Conflict #8** (from [compose/experience/SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md)): Original `07_opus_execution_contract.md` described "11 phases" with no details. Implementation followed a derived phase order from plan specs. A reconciliation pass is required when the original detailed phases become available.

## Contract Statement

> The execution contract is enforced through the quality gates listed above. Each gate must pass before a phase is considered complete. The static verifier, integration tests, and acceptance pack together form the verification evidence chain. Any future detailed 11-phase execution contract must be reconciled against the actual implementation sequence documented here.
