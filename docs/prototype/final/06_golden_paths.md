# 06 — Golden Paths (Index & Contract)

> **Document type**: Index document — points to canonical spec sources for golden path definitions.
> **Last updated**: 2026-03-11
> **Status**: CANONICAL INDEX — enforced by `scripts/spec-integrity-check.sh`

---

## Purpose

This document indexes the 6 golden path scripts for the Experience Platform. Golden paths define the critical user journeys through the system. The detailed step-by-step implementations live in test code and the acceptance pack.

## Summary Constraints

- **6 golden paths** covering critical user journeys
- Each path: scripted step-by-step with expected UI states and data outcomes
- Paths verified by: `GoldenPathIntegrationTest.java` (13 tests) + manual smoke checklist

## Golden Path Definitions

### Path A — Email Login

| Step | Action | Expected Outcome |
|------|--------|-----------------|
| 1 | Navigate to `/auth/login` | Login form renders with email/password fields |
| 2 | Enter credentials, submit | POST `/internal/v1/auth/login` with `method: "email"` |
| 3 | Receive session token | Redirect to `/home`, access token kept in memory, session marker cookie set, refresh handled via `HttpOnly` cookie |
| 4 | Verify trust headers | Subsequent API calls include all 4 v1.1 headers |

### Path B — Provider ID + Biometric

| Step | Action | Expected Outcome |
|------|--------|-----------------|
| 1 | Navigate to `/auth/login` | Login form with provider ID option |
| 2 | Enter provider ID, trigger biometric | POST `/internal/v1/auth/login` with `method: "provider_id"` |
| 3 | Biometric challenge (mocked in stage-1) | Session token returned |
| 4 | Redirect to home | Full Keycloak integration deferred to Stage-2 (see Conflict #4) |

### Path C — Queue → Encounter → Close

| Step | Action | Expected Outcome |
|------|--------|-----------------|
| 1 | Open `/queue` (requires shift guard) | Queue list renders from BFF |
| 2 | Select patient from queue | Navigate to encounter creation |
| 3 | Start encounter | POST `/internal/v1/encounters` → status `IN_PROGRESS` |
| 4 | Record chief complaint, vitals | PUT encounter with clinical data |
| 5 | Close encounter | PUT `/internal/v1/encounters/:id/close` → status `COMPLETED` |
| 6 | Verify outbox | `impilo.experience.encounter.created.v1` event in `event_outbox` |

### Path D — Admin/TSHEPO Governance

| Step | Action | Expected Outcome |
|------|--------|-----------------|
| 1 | Navigate to `/admin/users` (requires role guard) | User list renders |
| 2 | View user roles and assignments | RBAC-gated content visible for admin role |
| 3 | Check audit log | GET `/internal/v1/admin/audit-log` returns entries |

### Path E — Marketplace Integration

| Step | Action | Expected Outcome |
|------|--------|-----------------|
| 1 | Navigate to `/marketplace` | Marketplace landing renders |
| 2 | Browse product catalog | GET `/internal/v1/marketplace/orders` returns list |
| 3 | Create order | POST `/internal/v1/marketplace/orders` with idempotency key |
| 4 | Verify idempotency | Duplicate POST returns replay response, not duplicate record |

### Path F — Registry Data Management

| Step | Action | Expected Outcome |
|------|--------|-----------------|
| 1 | Navigate to `/registry/providers` | Provider list loads from BFF |
| 2 | Click provider detail | GET `/internal/v1/providers/:id` returns provider record |
| 3 | Verify v1.1 headers | Network tab shows 4 required headers on every request |

## Canonical Sources

### Automated Verification

| Test/Script | Location | Coverage |
|------------|----------|----------|
| `GoldenPathIntegrationTest.java` | `services/experience-bff/src/test/java/.../GoldenPathIntegrationTest.java` | 13 tests across all 6 paths |
| `ExperienceV11ComplianceTest.java` | `services/experience-bff/src/test/java/.../ExperienceV11ComplianceTest.java` | 7 tests: header, idempotency, outbox |
| `bff-smoke.sh` | `scripts/experience/smoke/bff-smoke.sh` | 5 BFF endpoint smoke checks |
| `verify-online.sh` | `scripts/experience/verify-online.sh` | Full online verification |

### Manual Verification (Golden Path Smoke Checklist)

The manual verification checklist is in:

- **[experience-platform-acceptance-pack.md § 6](../../acceptance/experience-platform-acceptance-pack.md)** — 7 manual flows covering all paths plus negative test (error envelope)

### Architectural References

| Canonical File | What It Provides |
|----------------|-----------------|
| [API_CONVENTIONS_V11.md](../../plan/API_CONVENTIONS_V11.md) | Header contract verified in paths A, B, E, F |
| [EVENTING_AND_TOPICS.md](../../plan/EVENTING_AND_TOPICS.md) | Outbox verification in path C |
| [TESTING_CONVENTIONS.md](../../plan/TESTING_CONVENTIONS.md) | Integration test patterns for golden paths |
| [SERVICE_CATALOG.md](../../plan/SERVICE_CATALOG.md) | Service definitions used in path endpoint resolution |
| [IMPILO_VNEXT_BUILD_PLAN.md](../../plan/IMPILO_VNEXT_BUILD_PLAN.md) | Bundle X dependency graph informing path ordering |
| [SPEC_DELTA_REPORT.md](../../plan/SPEC_DELTA_REPORT.md) | Gap analysis identifying path coverage gaps |
| [00-compliance-summary.md](../../architecture/v1.1/00-compliance-summary.md) | Compliance requirements verified through golden paths |
| [06-consistency-classes.md](../../architecture/v1.1/06-consistency-classes.md) | Consistency guarantees validated in path C (encounter lifecycle) |
| [ONLINE_VERIFICATION.md](../../experience/ONLINE_VERIFICATION.md) | End-to-end verification instructions for all golden paths |
| [SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md) | Conflict resolutions affecting path implementations |

## Spec Conflicts

- **Conflict #4** (from [compose/experience/SPEC_CONFLICTS.md](../../../compose/experience/SPEC_CONFLICTS.md)): Full Keycloak/OIDC integration not specified in the original summary documents. Path B uses a simplified auth endpoint; full OIDC deferred to Stage-2.

## Contract Statement

> The `GoldenPathIntegrationTest.java` is the automated source of truth for golden path behavior. The acceptance pack manual checklist (§ 6) supplements with UI-level verification. Any changes to golden path behavior must update both the integration test and this index.
