# Health OS Doctrine — Implementation Gap Matrix

> Living document. Updated 2026-04-11 after expanded doctrine adoption
> (wellness/lifestyle/diet/sleep/clubs, graduated trust, progressive identity assurance,
> marketplace risk graduation, device/IoT as first-class participants).

## Legend
- **DONE** — Implemented and aligned with doctrine
- **PARTIAL** — Infrastructure exists but incomplete
- **STRUCTURAL** — Requires new architectural components
- **PLANNED** — Identified, not yet started

---

## 1. Identity & Identifier Model

| Requirement | Status | Current State | Gap | Files |
|---|---|---|---|---|
| Health ID as person anchor | DONE | VITO issues Health ID, CPID pseudonym | — | `services/vito-service/` |
| Provider ID as regulated role | DONE | VARAPI issues Provider IDs | Linkage to Health ID not enforced at auth layer | `services/varapi-service/` |
| "Sign in as person, practice as provider" | PARTIAL | Two separate login paths exist; no explicit activation flow | Need Provider ID activation step post-login | `ui/experience/src/app/auth/` |
| Multi-class identifiers (6 classes) | PARTIAL | Actor/Context/Transaction IDs exist; Object/Record/Event IDs partial | Asset/Equipment IDs conflated; Care Plan ID missing | `contracts/health-os-identifiers.ts` |
| Caregiver linkage identifiers | STRUCTURAL | Not implemented | Need caregiver relationship model | — |

## 2. Header Contract

| Requirement | Status | Current State | Gap | Files |
|---|---|---|---|---|
| v1.2 headers in CompanionHeaders | DONE | All 18 headers defined | — | `libs/tech-companion/.../CompanionHeaders.java` |
| v1.2 headers in TrustHeaders | PARTIAL | Missing X-Provider-ID, X-Department-ID, X-Ward-ID, X-Programme-ID, X-Assurance-Level, X-Subject-ID | 6 headers need adding | `libs/tshepo-contracts/.../TrustHeaders.java` |
| Frontend injects v1.2 headers | DONE | api-client.ts sends all v1.2 headers | — | `ui/experience/src/lib/api-client.ts` |
| TypeScript contract types | DONE | health-os-identifiers.ts defines all 6 ID classes | — | `contracts/health-os-identifiers.ts` |
| AuthorizationRequest carries doctrine fields | PARTIAL | Missing providerId, departmentId, wardId, programmeId, assuranceLevel, subjectId | 6 fields need adding | `services/tshepo-service/.../AuthorizationRequest.java` |

## 3. Access Control (10 Dimensions)

| Dimension | Status | Current State | Gap |
|---|---|---|---|
| 1. Person identity | DONE | Actor ID in headers/JWT | — |
| 2. Active role | DONE | Roles from Keycloak JWT | — |
| 3. Attached role identifier | PARTIAL | Provider ID in headers but not in PolicyEngine | PolicyEngine needs Provider ID step |
| 4. Organizational affiliation | DONE | Tenant-scoped, facility check | — |
| 5. Facility/workspace context | DONE | Facility + workspace in headers and guards | — |
| 6. Subject relationship | PARTIAL | Consent check exists for Patient* resources | X-Subject-ID not yet in AuthorizationRequest |
| 7. Purpose of use | DONE | Header injected, PolicyEngine Step 2 validates | — |
| 8. Consent/legal basis | DONE | tshepo-consent-service evaluates consent | — |
| 9. Assurance level | STRUCTURAL | Not implemented | Need LOA1–LOA4 gate in PolicyEngine |
| 10. Workflow state | STRUCTURAL | Not implemented | Need workflow state in auth context |

## 4. Unified Experience Shell

| Requirement | Status | Current State | Gap |
|---|---|---|---|
| One coherent experience shell | PARTIAL | `ui/experience/` is primary (161 routes, 15 zones); 22+ sidecar apps exist | Sidecar retirement in progress |
| Role-based adaptation | DONE | AuthGuardProvider with 13 role groups | — |
| Citizen experience | PARTIAL | Citizen pages in experience + separate portal app | Consolidate portal into experience |
| Provider experience | PARTIAL | Clinical/EHR zones in experience + separate EHR app | Consolidate EHR into experience |
| Caregiving experience | STRUCTURAL | Not implemented | Need caregiver zone with delegated views |
| Wellness experience | STRUCTURAL | Not implemented | Need wellness zone (prevention, fitness, health tips) |
| Remote monitoring | STRUCTURAL | IoT ingestion service exists; no user-facing surface | Need monitoring dashboard zone |
| Service discovery | STRUCTURAL | Marketplace exists; no discovery-focused zone | Need provider/facility search zone |
| Device/agent interactions | STRUCTURAL | IoT service exists; no user-facing management | Need device management zone |
| In-session role switching | STRUCTURAL | Users must re-login to change roles | Need role context selector |

## 5. Mobile Experience

| Requirement | Status | Current State | Gap |
|---|---|---|---|
| Unified mobile shell | STRUCTURAL | citizen-app and provider-app are separate React Native codebases | Should merge into single role-adaptive app |
| Shared mobile auth | PARTIAL | Both use shared `packages/auth` | Login is separate per app |
| Provider ID activation on mobile | STRUCTURAL | Not implemented | Need provider activation flow in mobile |

## 6. Audit & Traceability (10 Fields)

| Field | Status | Current State |
|---|---|---|
| 1. Person anchor (Health ID) | DONE | Actor ID in audit events |
| 2. Active role | DONE | Actor type in audit events |
| 3. Role-linked identifier | PARTIAL | Provider ID not yet in audit events |
| 4. Organizational context | DONE | Tenant ID in audit events |
| 5. Facility/workspace | DONE | Facility ID in audit events |
| 6. Subject of action | PARTIAL | Resource ID captured, not explicit Subject ID |
| 7. Transaction/record instance | DONE | Resource type + ID in audit events |
| 8. App/module used | PARTIAL | Service-ID header exists but not app-level |
| 9. Purpose of use | DONE | Purpose of use in audit events |
| 10. Time/channel/event context | DONE | Timestamp, correlation ID, request ID |

## 7. Asset & Equipment

| Requirement | Status | Current State | Gap |
|---|---|---|---|
| Asset ID as first-class | DONE | asset-registry-service exists | — |
| Equipment ID as separate class | STRUCTURAL | Equipment conflated with assets | Need separate equipment model or equipment type |
| Device-equipment linkage | STRUCTURAL | IoT devices not linked to equipment assets | Need device→equipment relationship |

---

## Priority Roadmap

### Phase 1: Trust Layer Alignment (Immediate) — DONE
1. ~~Extend `TrustHeaders.java` with 6 missing v1.2 headers~~ DONE (886f33a)
2. ~~Extend `AuthorizationRequest.java` with providerId, assuranceLevel, subjectId~~ DONE (886f33a)
3. ~~Add PolicyEngine Steps 7–8 (Provider ID + Assurance Level)~~ DONE (886f33a)

### Phase 2: Experience Shell Enrichment (Near-term) — DONE
4. ~~Add wellness, caregiving, remote monitoring, service discovery zones~~ DONE (4 new zones, 24 routes)
5. ~~Implement Provider ID activation flow (post-login step)~~ DONE (/provider/activate page)
6. ~~Add `provider` guard to AuthGuardProvider~~ DONE
7. ~~useAuthStore: activateProvider/deactivateProvider/hasActiveProvider~~ DONE
8. Accelerate sidecar app retirement — IN PROGRESS (tracked in sidecar-retirement-ledger.ts)

### Phase 3: Full Doctrine Completion (Mid-term) — DONE
9. ~~Separate Equipment from Assets~~ DONE (EquipmentEntity, V004 migration, repository)
10. ~~Implement caregiver linkage model~~ DONE (VITO V020 migration, useCaregiverLinkage hook, TS contract)
11. ~~Add workflow state to access control~~ DONE (x-workflow-state header end-to-end: CompanionHeaders, TrustHeaders, AuthorizationRequest, Envoy, TS contract)
12. ~~Mobile auth Provider ID support~~ DONE (activateProvider/deactivateProvider/hasActiveProvider)
13. Merge mobile apps into single role-adaptive codebase — DEFERRED (requires React Navigation restructure)
14. In-session role context switching (role selector in sidebar) — DEFERRED (Phase 4)
