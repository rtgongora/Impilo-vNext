# Developer / Partner App — Acceptance Pack

**Generated**: 2026-03-15
**Wave**: Developer Platform
**Scope**: `ui/developer-console/`, `services/developer-portal-service/`, `services/schema-registry-service/`, `docs/apps/developer-partner-app/`

## 1. Merge Gate Compliance

| Rule | Status |
|------|--------|
| No mocks, stubs, or TODOs | PASS |
| Real backend services only | PASS |
| All endpoints implemented in developer-portal-service and schema-registry-service | PASS |
| Docs and acceptance pack included | PASS |

## 2. Vertical Slice Verification

### 2.1 UI Layer (`ui/developer-console/`)

| Component | File | Status |
|-----------|------|--------|
| Root layout | `src/app/layout.tsx` | Implemented |
| Sidebar layout + env selector | `src/app/(developer)/layout.tsx` | Implemented |
| Dashboard | `src/app/(developer)/dashboard/page.tsx` | Implemented |
| Client list | `src/app/(developer)/clients/page.tsx` | Implemented |
| Client registration | `src/app/(developer)/clients/register/page.tsx` | Implemented |
| Client detail + key management | `src/app/(developer)/clients/[clientId]/page.tsx` | Implemented |
| Certification | `src/app/(developer)/certification/page.tsx` | Implemented |
| API catalog | `src/app/(developer)/catalog/page.tsx` | Implemented |
| Sandbox | `src/app/(developer)/sandbox/page.tsx` | Implemented |
| Federation | `src/app/(developer)/federation/page.tsx` | Implemented |

### 2.2 Foundation Layer

| Component | File | Status |
|-----------|------|--------|
| Trust-aware API client | `src/lib/apiClient.ts` | Implemented |
| Developer API methods | `src/lib/developerApi.ts` | Implemented |
| Session store | `src/stores/sessionStore.ts` | Implemented |
| Type definitions | `src/types/developer.ts` | Implemented |
| Status badges | `src/components/StatusBadge.tsx` | Implemented |

### 2.3 Backend Layer

| Service | Endpoint | Status |
|---------|----------|--------|
| developer-portal | POST /internal/v1/developer/clients | Existing |
| developer-portal | GET /internal/v1/developer/clients | Existing |
| developer-portal | GET /internal/v1/developer/clients/{id} | Existing |
| developer-portal | POST /internal/v1/developer/clients/{id}/keys | Existing |
| developer-portal | GET /internal/v1/developer/clients/{id}/keys | Existing |
| developer-portal | POST /internal/v1/developer/keys/{id}/rotate | Existing |
| developer-portal | DELETE /internal/v1/developer/keys/{id} | Existing |
| developer-portal | PUT /internal/v1/developer/clients/{id}/sandbox | Existing |
| developer-portal | PUT /internal/v1/developer/clients/{id}/deprecation-posture | Existing |
| developer-portal | POST /internal/v1/developer/clients/{id}/certify | **NEW** |
| developer-portal | GET /internal/v1/developer/clients/{id}/certifications | **NEW** |
| developer-portal | GET /internal/v1/developer/certifications/{id} | **NEW** |
| developer-portal | GET /internal/v1/developer/clients/{id}/federation-readiness | **NEW** |
| developer-portal | GET /internal/v1/developer/dashboard/stats | **NEW** |
| schema-registry | GET /internal/v1/schemas/catalog | **NEW** |

### 2.4 Database Layer

| Migration | Table | Status |
|-----------|-------|--------|
| V001__init.sql | dvp_clients, dvp_api_keys, dvp_sandbox_configs, dvp_event_outbox | Existing |
| V002__certification.sql | dvp_certifications | **NEW** |

### 2.5 v1.1 Compliance

| Check | Status |
|-------|--------|
| Trust headers injected on all requests | PASS |
| Idempotency key on all write operations | PASS |
| Correlation ID on all requests | PASS |
| Error envelope format | PASS (via ErrorEnvelope) |
| Outbox pattern for events | PASS |
| Tenant isolation | PASS (tenantId from RequestContext) |

## 3. Golden Path Tests

| # | Test | Location | Validates |
|---|------|----------|-----------|
| 1 | Client registration with trust headers | `ClientRegistration.test.tsx` | Registration, header injection |
| 2 | Client list retrieval | `ClientRegistration.test.tsx` | GET without idempotency key |
| 3 | API key issuance with imp_ prefix | `KeyRotation.test.tsx` | Key format, prefix convention |
| 4 | Key rotation returns new key | `KeyRotation.test.tsx` | Old key marked ROTATED, new key issued |
| 5 | Key revocation | `KeyRotation.test.tsx` | Status set to REVOKED |
| 6 | Certification run with 7 checks | `CertificationFlow.test.tsx` | All check categories covered |
| 7 | Certification history listing | `CertificationFlow.test.tsx` | Ordered by date |
| 8 | API discovery endpoint listing | `DiscoveryCatalog.test.tsx` | All methods present |
| 9 | Schema catalog with parsed metadata | `DiscoveryCatalog.test.tsx` | Service/entity/action extraction |
| 10 | Schema compatibility checking | `DiscoveryCatalog.test.tsx` | Compatible result with violations |

### Backend Tests

| # | Test | Location | Validates |
|---|------|----------|-----------|
| 11 | Fully compliant client passes certification | `CertificationServiceTest.java` | All 7 checks pass |
| 12 | No active key fails certification | `CertificationServiceTest.java` | Key check failure |
| 13 | NONE deprecation posture fails check | `CertificationServiceTest.java` | Posture validation |
| 14 | Certification emits outbox event | `CertificationServiceTest.java` | Event type, aggregate |
| 15 | Federation readiness — fully ready | `CertificationServiceTest.java` | Overall ready = true |
| 16 | Federation readiness — no cert | `CertificationServiceTest.java` | Overall ready = false |
| 17 | Dashboard stats aggregation | `CertificationServiceTest.java` | Counts clients, keys, certs |

## 4. Prohibited Pattern Audit

| Check | Result |
|-------|--------|
| No mock/stub implementations | PASS |
| No TODO/FIXME markers | PASS |
| No hardcoded credentials | PASS |
| No feature flags bypassing auth | PASS |
| No PII in logs | PASS |

## 5. Compliance Summary

| Standard | Status | Notes |
|----------|--------|-------|
| Trust headers (14 mandatory) | Compliant | Injected via apiClient from session store |
| Idempotency on writes | Compliant | UUID key generated per request |
| Error envelope | Compliant | ErrorEnvelope.of() on all error paths |
| Event outbox | Compliant | dvp_event_outbox for all mutations |
| Tenant isolation | Compliant | tenantId from RequestContext |
| Key security | Compliant | SHA-256 hash stored, raw key shown only at issuance |

## 6. Sign-Off

| Role | Name | Date |
|------|------|------|
| Platform Lead | | |
| Security Lead | | |
| QA Lead | | |
