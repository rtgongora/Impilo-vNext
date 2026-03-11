# 04 — API Surface Map (Index & Contract)

> **Document type**: Index document — points to canonical spec sources for API endpoint definitions.
> **Last updated**: 2026-03-11
> **Status**: CANONICAL INDEX — enforced by `scripts/spec-integrity-check.sh`

---

## Purpose

This document indexes the API surface for the Experience Platform. The canonical API conventions are defined in `docs/plan/API_CONVENTIONS_V11.md`. The actual endpoint implementations in the BFF service are the source of truth for available operations.

## Summary Constraints (from original prototype)

- ~60 Supabase tables (original prototype reference — replaced by BFF + PostgreSQL in implementation)
- 30 RPC functions → mapped to BFF controller endpoints
- 30 Edge Functions → mapped to BFF service layer
- 2 storage buckets → deferred to MinIO integration

## Canonical Sources

### v1.1 API Conventions (Authoritative)

The API contract for all v1.1 services is defined in:

- **[API_CONVENTIONS_V11.md](../../plan/API_CONVENTIONS_V11.md)** — 439 lines
  - Header contract: 4 hard-required headers (`X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`)
  - Idempotency: `Idempotency-Key` on POST/PUT/PATCH
  - Error envelope: `{ error: { code, message, details, request_id, correlation_id } }`
  - Pagination: cursor-based with `Link` headers
  - Versioning: `/internal/v1/` and `/external/v1/` prefixes

### BFF Controller Endpoints (Implementation Source of Truth)

All Experience BFF endpoints are defined in:
`services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/`

| Controller | Base Path | Methods | Domain |
|-----------|-----------|---------|--------|
| `AuthController.java` | `/internal/v1/auth` | POST login, DELETE logout | Authentication |
| `FacilityController.java` | `/internal/v1/facilities` | GET list, GET by ID, POST create | Facility management |
| `WorkspaceController.java` | `/internal/v1/workspaces` | GET list, GET by ID, POST create | Workspace management |
| `ShiftController.java` | `/internal/v1/shifts` | GET list, GET by ID, POST open, PUT close | Shift lifecycle |
| `PatientController.java` | `/internal/v1/patients` | GET search, GET by ID | Patient demographics |
| `QueueController.java` | `/internal/v1/queue` | GET list, POST add, PUT update priority | Queue management |
| `EncounterController.java` | `/internal/v1/encounters` | GET list, GET by ID, POST create, PUT close | Clinical encounters |
| `ProviderController.java` | `/internal/v1/providers` | GET list, GET by ID | Provider registry |
| `ReportController.java` | `/internal/v1/reports` | GET list, POST generate | Report generation |
| `AdminController.java` | `/internal/v1/admin/users` | GET list, GET audit-log | Admin/governance |
| `InventoryController.java` | `/internal/v1/inventory` | GET list, GET by ID | Inventory items |
| `PrescriptionController.java` | `/internal/v1/prescriptions` | GET list, GET by ID | Prescriptions |
| `MarketplaceController.java` | `/internal/v1/marketplace/orders` | GET list, POST create | Marketplace orders |

### Header Enforcement

- **V11HeaderFilter** — enforces 4 hard-required headers on all `/internal/v1/**` and `/external/v1/**` paths
- **IdempotencyFilter** — enforces `Idempotency-Key` on POST/PUT/PATCH, with replay/conflict detection
- **GlobalExceptionHandler** — formats all errors as v1.1 error envelope

Filter configuration: `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/FilterConfig.java`

### Eventing Surface

Outbox events produced by the BFF (from [EVENTING_AND_TOPICS.md](../../plan/EVENTING_AND_TOPICS.md)):

| Event Type | Producer | Trigger |
|-----------|----------|---------|
| `impilo.experience.encounter.created.v1` | EncounterController | POST /encounters |
| `impilo.experience.encounter.closed.v1` | EncounterController | PUT /encounters/:id/close |
| `impilo.experience.shift.opened.v1` | ShiftController | POST /shifts |
| `impilo.experience.shift.closed.v1` | ShiftController | PUT /shifts/:id/close |
| `impilo.experience.auth.login.v1` | AuthController | POST /auth/login |

Outbox table: `event_outbox` with columns `tenant_id`, `pod_id`, `correlation_id`, `schema_version`
Schema: `services/experience-bff/src/main/resources/db/migration/`

### Supporting Specs

| Canonical File | What It Provides |
|----------------|-----------------|
| [SERVICE_CATALOG.md](../../plan/SERVICE_CATALOG.md) | Experience BFF in service catalog (port, ring, responsibilities) |
| [IMPILO_VNEXT_BUILD_PLAN.md](../../plan/IMPILO_VNEXT_BUILD_PLAN.md) | Bundle X component list, BFF in Outstanding 27 |
| [EVENTING_AND_TOPICS.md](../../plan/EVENTING_AND_TOPICS.md) | EventEnvelope schema, outbox pattern, topic naming |
| [TESTING_CONVENTIONS.md](../../plan/TESTING_CONVENTIONS.md) | API testing patterns, golden contract suite |
| [05-event-schema-template.md](../../architecture/v1.1/05-event-schema-template.md) | Event envelope field definitions |
| [06-consistency-classes.md](../../architecture/v1.1/06-consistency-classes.md) | Consistency class assignments for API operations |
| [SPEC_DELTA_REPORT.md](../../plan/SPEC_DELTA_REPORT.md) | API surface gaps (snapshot endpoints, external prefixes) |

### Compliance Tests

| Test | What It Verifies |
|------|-----------------|
| `ExperienceV11ComplianceTest.java` | 7 tests: header enforcement, idempotency replay/conflict, outbox fields |
| `GoldenContractIT.java` | Golden contract suite: auto-discovery compliance tests |
| `GoldenPathIntegrationTest.java` | 13 tests across 6 golden paths |
| `V11ComplianceStaticVerifier.java` | 95 static checks for v1.1 wiring across all services |

## Spec Conflicts

- **Conflict #5** (from [compose/experience/SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md)): Original spec mentioned "60 Supabase tables, 30 RPC functions" but provided none. BFF endpoints were designed from golden path flows and zone requirements instead.

## Contract Statement

> The BFF controller implementations are the source of truth for the Experience Platform API surface. The v1.1 API conventions (`API_CONVENTIONS_V11.md`) define the cross-cutting contract (headers, errors, idempotency). Any future fully-detailed API surface map must be generated from or reconciled against the actual Java controller source files and the v1.1 conventions doc.
