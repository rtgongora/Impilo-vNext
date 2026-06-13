# vNext Keycloak and Trust Alignment Report

> **Generated:** 2026-06-13  
> **Preview:** `impilo-full-preview` @ `http://41.57.127.235`  
> **Realm seed:** `deploy/helm/impilo-vnext/files/realm-impilo-preview.json`

---

## Doctrine statement

**All internal vNext services are first-party components of the Impilo Health Operating System.** They may be deployed in waves and protected by different authorization policies, but they must **not** be treated as unrelated external applications. External systems may exist, but the vNext adapter/service/surface that represents them inside the platform is part of vNext and must be built, deployed, secured, surfaced, and tested.

---

## A. Service identity inventory

### Browser-facing clients

| Client ID | Type | Product surface | Preview status |
|-----------|------|-----------------|----------------|
| `impilo-ui` | Public OIDC | `one-ui-shell` (canonical web) | Aligned |
| `impilo-mobile-citizen` | Public OIDC | Citizen mobile app | Aligned |
| `impilo-mobile-provider` | Public OIDC | Provider mobile app | Aligned |
| `impilo-ops-console` | Public OIDC | **Retired** — absorbed into shell | Continuity only |
| `impilo-ehr` | Public OIDC | **Retired** — absorbed into shell | Continuity only |
| `impilo-portal` | Public OIDC | **Retired** — absorbed into shell | Continuity only |
| `experience-ui` | Dev ROPC | Local dev only | Preview/dev |

### Service accounts / confidential clients

| Client ID | Caller | Purpose | Preview status |
|-----------|--------|---------|----------------|
| `impilo-bff` | experience-bff | User session, downstream orchestration | Aligned |
| `impilo-backend` | BFF registration, admin ops | `manage-users` for registration | **Fixed** — grant script applied |
| `impilo-admin-cli` | Operator CLI | Realm admin | Ops only |

### Internal microservices (89) — trust posture summary

| Auth mode | Count | Preview | Production target |
|-----------|------:|---------|-------------------|
| Bearer + TSHEPO ext_authz (Envoy) | 89 | OAuth **disabled** globally via helm helper | Full OAuth + policy |
| Service-to-service via BFF | ~72 | Trust headers + preview bypass | mTLS or token exchange |
| gRPC authz (TSHEPO) | tshepo-authz-service | Live | Live |

**Per-service preview OAuth bypass (explicit):**
- `varapi-service` — `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` in SecurityConfig
- `workforce-governance-service` — same pattern
- **All preview pods** — `_helpers.tpl` injects global flag when `environment == full-preview`

### Alignment summary

| Status | Count | Notes |
|--------|------:|-------|
| **aligned** | ~85 | Running with preview bypass (functional) |
| **misaligned** | 4 | Global bypass masks per-service trust gaps |
| **unknown** | 0 | — |

---

## B. Misclassification report

### Internal services treated like external apps

| Issue | Symptom | Fix |
|-------|---------|-----|
| Legacy Keycloak clients (`impilo-portal`, `impilo-ehr`, `impilo-ops-console`) imply parallel products | Confusion about canonical entry | Document as continuity-only; single `impilo-ui` entry |
| `optional_full_boot` classification label | Product teams skip accountability | Rename to wave sequencing; full estate accountability |
| Global `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` on all pods | Trust plane not exercised in preview | Per-service preview profiles + TSHEPO path tests |
| BFF stub modes (`FACILITIES_MODE: stub`) | Facility context not validated against TUSO | Switch to live when seed applied |

### Not misclassified (true externals)

`dhis2`, `external-elmis`, `external-pacs-network`, `banking-rails`, `mosip`, `lims`, `sms-whatsapp-gateway` — contract references only; **adapters are internal**.

---

## C. BFF-to-service trust matrix

### Downstream URL generation (preview)

**Generator:** `scripts/full-boot/generate-full-preview-bff-downstream-env.mjs`  
**Output:** `values-full-preview-bff-env.generated.yaml` — cluster DNS `http://{service}:{port}`

| Check | Result |
|-------|--------|
| localhost in running BFF pod | **Overridden** by helm env (if generator ran) |
| Services mapped | 72 env vars → ~70 unique services |
| Unmapped deployed services | 15 (see accountability matrix) |

### BFF downstream auth (representative)

| Downstream | URL pattern | Expected auth | Preview result |
|------------|-------------|---------------|----------------|
| varapi-service | `http://varapi-service:8083` | Bearer / preview bypass | **200** after OAuth bypass fix |
| vito-service | `http://vito-service:8082` | Bearer + headers | Live with seed |
| workforce-governance | `http://workforce-governance-service:*` | Bearer / preview bypass | **200** assignments |
| tuso-service | `http://tuso-service:8084` | Bearer + headers | Live; BFF facilities stub masks |
| madi-service | `http://madi-service:8300` | Bearer + headers | Live after schema fix |
| guidance-service | `http://guidance-service:8260` | Bearer | Nompilo path |
| llm-orchestration | `http://llm-orchestration-service:*` | Bearer | Offline fallback in BFF |

### Failure classification guide

| Symptom | Likely cause |
|---------|--------------|
| 401 from downstream | Auth bypass not wired or token missing |
| 403 from TSHEPO | Policy/role/context gap |
| 404 | Routing or wrong base path |
| 500 BFF | Composition error or missing seed |
| Connection refused | BFF localhost default (generator not applied) |
| Empty UI with 200 | Stub mode or partial parity |

---

## D. Shell-to-service user context matrix

| Shell surface | Route | Context required | Token/context passed | Missing context behaviour |
|---------------|-------|------------------|----------------------|---------------------------|
| WORK tab | Session shell | Health ID, Provider ID, work assignment | BFF session contract | WORK hidden / error |
| Provider hub | `/registry/providers` | Provider ID, licenses | VARAPI via BFF | Partial data / blocked |
| Facility hub | `/facility/*` | Facility ID, workspace | TUSO (stub mode risk) | Stub facilities |
| Clinical chart | `/ehr/[patientId]` | Patient CPID, purpose-of-use | PCT + BUTANO | Empty chart |
| Madi bank | `/madi/blood-bank/*` | Facility, tenant | MADI + headers | Error surface |
| Citizen portal | `/citizen/*` | Health ID | VITO + BFF | Registration blocked if Keycloak grant missing |
| Nompilo | `/ask` | Role, optional patient context | Guidance + LLM | Offline fallback label |
| Launcher | Start menu | Role, facility | BFF `/launcher/apps` | Reduced tile set |

**Session contract fix (deployed):** BFF maps `providerPublicId` from VARAPI (not `providerId`) — required for WORK tab.

---

## E. Preview bypass audit

| Flag / mode | Service / layer | Preview-only | Production-safe | Risk |
|-------------|-----------------|--------------|-----------------|------|
| `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true` | **All preview pods** (helm helper) | Intended | **NO** — must not ship | Masks broken OAuth integration |
| Same flag | varapi-service SecurityConfig | Yes | No | Required for BFF internal lookup in preview |
| Same flag | workforce-governance SecurityConfig | Yes | No | Required for work assignments API |
| `IMPILO_BFF_FACILITIES_MODE: stub` | experience-bff | Yes | No | Facility truth not exercised |
| `IMPILO_BFF_CITIZEN_LONGTAIL_MODE: stub` | experience-bff | Yes | No | Citizen longtail not exercised |
| `IMPILO_BFF_*_FAILURE_POLICY: stub_fallback` | experience-bff | Yes | No | Silent degradation if mislabelled |
| Trust header injection | Shell `api-client.ts` | N/A | Yes | Required for TSHEPO |
| Sovereign seed scripts | Postgres | Preview data | N/A | Required for walkthrough |

**Required cleanup for production:**
- Remove global OAuth disable from helm template
- Per-service security profiles with explicit preview test profile only
- Replace BFF stubs with live downstream where seed exists
- Audit all `SecurityConfig` classes for `disableOAuthForTests` pattern

---

## F. Required fixes

### Urgent preview walkthrough
1. Confirm Keycloak `impilo-backend` `manage-users` grant persists across realm reload
2. Switch `IMPILO_BFF_FACILITIES_MODE` to `live` when TUSO seed confirmed
3. Document preview test credentials in workbook (superadmin, clinicians, citizens)
4. Validate WORK tab with `PROV-ZW-ADMIN-001` + active assignment

### Product-truth fixes
1. Rename `optional_full_boot` classification language
2. Add 15 missing BFF downstream URLs
3. Per-service Keycloak client map where browser redirect needed (not global disable)
4. Shell honest labels for stub/blocked paths

### Production-hardening
1. Remove `IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS` from production values
2. Enable TSHEPO ext_authz on all ingress paths
3. Service account token exchange for BFF → downstream
4. Audience/scope enforcement per client

### Documentation / workbook
1. Keycloak/trust test scenarios per role
2. BFF orchestration tests with auth on
3. Preview bypass inventory in retest log template

---

## References

| File | Purpose |
|------|---------|
| `deploy/helm/impilo-vnext/files/realm-impilo-preview.json` | Realm clients |
| `deploy/helm/impilo-vnext/templates/_helpers.tpl` | Global OAuth bypass injection |
| `services/varapi-service/.../SecurityConfig.java` | Per-service bypass |
| `scripts/deploy/keycloak-grant-backend-registration-roles.sh` | Registration fix |
| `ui/one-ui-shell/src/lib/api-client.ts` | Trust headers |
| `services/tshepo-authz-service/.../PolicyEngine.java` | Authorization |
