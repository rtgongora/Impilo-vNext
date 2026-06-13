# vNext Keycloak Trust Remediation Plan

> **Wave:** Product Completion Wave 1  
> **Generated:** 2026-06-13  
> **Doctrine:** Internal vNext services are first-party HOS components — not unrelated external apps

---

## Executive summary

| Category | Count |
|----------|------:|
| Internal microservices requiring trust posture | 89 |
| Keycloak browser clients (canonical) | 4 active + 3 continuity |
| Service accounts | 2 (`impilo-bff`, `impilo-backend`) |
| Services on global preview OAuth bypass | **89** (all preview pods) |
| Services with explicit per-service bypass | 2 (varapi, workforce-governance) |
| Misclassified as external/unrelated | 3 legacy clients + `optional_full_boot` language |

---

## A. Internal services — first-party trust posture

### Pattern matrix

| Service class | Keycloak client | Client type | Service account | Token mode | Caller |
|---------------|-----------------|-------------|-----------------|------------|--------|
| Web shell | `impilo-ui` | Public OIDC | No | Authorization code | Browser user |
| Mobile citizen | `impilo-mobile-citizen` | Public OIDC | No | Authorization code | Mobile app |
| Mobile provider | `impilo-mobile-provider` | Public OIDC | No | Authorization code | Mobile app |
| Experience BFF | `impilo-bff` | Confidential | Yes | Client credentials + user token pass-through | Shell/mobile |
| Backend registration | `impilo-backend` | Confidential | Yes | Client credentials | BFF registration |
| Domain microservices (89) | **None** (first-party) | N/A | No* | Bearer JWT + TSHEPO headers | BFF, Envoy, service-to-service |
| Trust authz | tshepo-authz-service | N/A | N/A | gRPC ext_authz | Envoy |

\*Service accounts added per-service only when machine-to-machine without user context is required.

### Per-plane trust summary

| Plane | Services | Preview auth | Production target |
|-------|----------|--------------|-------------------|
| Trust | tshepo-*, identity-assurance, mvumo | Global bypass + TSHEPO | OAuth + ext_authz |
| Registry | vito, varapi, tuso, ubomi, zibo, indawo, product-registry | Global bypass | Bearer + policy |
| Clinical | pct, butano, madi, pharmacy, oros, inpatient, pacs, forms, rules | Global bypass | Bearer + purpose-of-use |
| Data | ndr, pipeline, warehouse, surveillance, campaigns, search | Global bypass | Bearer + governance |
| Integration | connector-fhir, fhir-gateway, integration-hub, offline-* | Global bypass | Bearer + adapter policy |
| Enterprise | msika*, mushex, costing, hr-payroll, workforce-governance | Global bypass | Bearer + role |
| Experience | experience-bff, guidance, llm-orchestration, live, booking | Global bypass | OAuth at edge + BFF |

---

## B. Misclassification report

| Item | Current misclassification | Symptom | Fix |
|------|---------------------------|---------|-----|
| `impilo-portal`, `impilo-ehr`, `impilo-ops-console` Keycloak clients | Implies parallel UX products | Confusion about canonical entry | Document continuity-only; single `impilo-ui` |
| `optional_full_boot` in classification YAML | Implies product optionality | Services skipped in testing/accountability | Rename `wave_sequenced_full_boot` |
| Global `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` | All services appear "working" without OAuth | Trust integration untested | Per-service preview profile + trust test sheet |
| BFF facilities stub | TUSO treated as optional | Facility context not validated | `IMPILO_BFF_FACILITIES_MODE: live` |

**No internal microservice should be registered as a separate Keycloak "external application"** unless it has its own browser login surface (none do except shell/mobile).

---

## C. BFF-to-service trust matrix (post BFF gap closure)

| Downstream | URL (preview) | Auth method | Preview result | Failure class |
|------------|---------------|-------------|----------------|---------------|
| varapi-service | cluster DNS:8083 | Bearer + bypass | 200 lookup | Was auth — fixed |
| workforce-governance | cluster DNS | Bearer + bypass | 200 assignments | Was auth — fixed |
| vito-service | cluster DNS:8082 | Bearer + headers | Live with seed | — |
| tuso-service | cluster DNS:8084 | Bearer (BFF stub mode) | Stub data | Policy/routing — switch live |
| All 88 mapped services | `http://{svc}:{port}` | Bearer + trust headers | Running | Verify post-redeploy |

---

## D. Shell-to-service context matrix (critical paths)

| Surface | Context required | Missing behaviour |
|---------|------------------|-----------------|
| WORK tab | Health ID, providerPublicId, assignment | WORK hidden |
| Registration | Keycloak `impilo-backend` manage-users | 403 register |
| Clinical chart | CPID, purpose-of-use, facility | Empty chart |
| Madi workflows | Facility, tenant | Error panel |
| Telemedicine | RTC availability | Must show Blocked |
| Finance/Mushex | Payer context | Partial data |

---

## E. Preview bypass audit

| Flag | Scope | Preview-only | Production-safe | Risk |
|------|-------|--------------|-----------------|------|
| `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` | All preview pods (helm helper) | Yes | **NO** | Masks OAuth bugs |
| Same on varapi-service | Service SecurityConfig | Yes | No | Needed for BFF internal lookup today |
| Same on workforce-governance | Service SecurityConfig | Yes | No | Needed for assignments API |
| `IMPILO_BFF_FACILITIES_MODE: stub` | BFF | Yes | No | Facility truth not exercised |
| `IMPILO_BFF_CITIZEN_LONGTAIL_MODE: stub` | BFF | Yes | No | Citizen journeys stubbed |
| `stub_fallback` policies | BFF | Yes | No | Must be UI-labelled |

---

## F. Remediation table

### Urgent preview fixes (this wave documentation; some deployed)

| # | Fix | Status |
|---|-----|--------|
| 1 | Keycloak `impilo-backend` manage-users grant | Done (prior wave) |
| 2 | BFF providerPublicId mapping | Done (prior wave) |
| 3 | BFF downstream URL completion | **Done (this wave)** |
| 4 | Document all 89 trust postures | **Done (this plan)** |
| 5 | Switch facilities BFF to live | Pending |
| 6 | Trust test scenarios in workbook | Pending (dataset generated) |

### Product-truth fixes (next wave)

| # | Fix |
|---|-----|
| 1 | Rename `optional_full_boot` → `wave_sequenced_full_boot` |
| 2 | Per-service preview SecurityProfile (not global disable) |
| 3 | Trust header contract tests shell → BFF → service |
| 4 | Registration E2E with real Keycloak (preview) |

### Production hardening (later)

| # | Fix |
|---|-----|
| 1 | Remove global OAuth disable from helm |
| 2 | Enable full TSHEPO ext_authz on all paths |
| 3 | Service account token exchange BFF → downstream |
| 4 | Audience/scope per client enforcement |
| 5 | Remove all BFF stub modes |

---

## Services relying on global OAuth bypass

**All 89 helm-enabled microservices** receive `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` via `deploy/helm/impilo-vnext/templates/_helpers.tpl` when `global.environment == full-preview`.

**Additional explicit bypass:** `varapi-service`, `workforce-governance-service` (SecurityConfig reads same flag).

---

## Acceptance

- [x] No internal service undocumented as external app
- [x] Preview bypass inventory complete
- [x] First-party posture defined for all 89 services
- [x] Remediation table with urgency tiers
- [ ] Global bypass removed (explicitly deferred — requires approval)
