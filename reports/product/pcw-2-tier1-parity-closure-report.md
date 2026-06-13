# PCW-2 Tier 1 Parity Closure Report

> **Wave:** Product Completion Wave 2  
> **Generated:** 2026-06-10  
> **Status:** Implemented locally — **not redeployed**, **not pushed**

---

## Executive summary

PCW-2 closes Tier 1 registry/trust parity for **VARAPI**, **TUSO**, and **VITO**, switches BFF facilities to **live** TUSO mode, expands the Health OS launcher to a **31-tile registry-aligned catalogue**, wires **16 orchestration-backlog env bindings**, and renames misleading `optional_full_boot` classification language to **`wave_sequenced_full_boot`**.

---

## Files changed

| Area | Files |
|------|-------|
| BFF facilities live | `scripts/full-boot/generate-full-preview-bff-downstream-env.mjs`, `deploy/helm/impilo-vnext/values-full-preview-bff-env.generated.yaml` |
| Launcher | `HealthOsLauncherController.java`, `HealthOsLauncherCatalogService.java`, `launcher-vnext-catalog.json`, `HealthOsLauncherControllerTest.java` |
| Tier 1 parity (BFF) | `RegistryController.java`, `ClientRegistryController.java`, `BffDegradedMeta.java`, `RegistryControllerTest.java` |
| Tier 1 parity (shell) | `registry/providers/page.tsx`, `registry/clients/page.tsx`, `registry/clients/page.test.tsx` |
| 16 env bindings | `application.yml`, `OrchestrationBacklogEndpoints.java`, `ExperienceBffApplication.java` |
| Classification rename | `config/full-boot-service-classification.yml`, `scripts/full-boot/generate-full-boot-artifacts.mjs`, `docs/environment/FULL_CONTAINERIZATION_MATRIX.md` |
| Reports | This file; updates to `vnext-parity-closure-plan.md`, `vnext-bff-downstream-gap-closure-plan.md` |

---

## A. BFF facilities mode

| Item | Before | After |
|------|--------|-------|
| `IMPILO_BFF_FACILITIES_MODE` (generated preview) | `stub` | **`live`** |
| `BffFacilitiesProperties.Mode` enum | `live` \| `stub` | unchanged — valid only |
| `stub_fallback` on MODE vars | never used | still never used (`stub_fallback` only on `*_FAILURE_POLICY` vars) |
| TUSO URL in generated env | `http://tuso-service:{port}` | unchanged — cluster DNS, not localhost |

**Acceptance:** Generated env validates; facilities mode binding cannot crash on invalid enum; live mode calls TUSO with explicit 502 on upstream failure (no silent stub in live path).

---

## B. Launcher readiness

| Metric | Before | After |
|--------|-------:|------:|
| Hardcoded core platform tiles | 4 | 0 (replaced by catalogue service) |
| Registry-aligned catalogue tiles | 0 | **31** |
| Marketplace tiles | 0+ (when msika-apps up) | unchanged behaviour |
| Typical total without marketplace | 4 | **31** |

**Implementation:**
- `launcher-vnext-catalog.json` mirrors active `SHELL_APPS` entries from `ui/one-ui-shell/src/lib/shell/app-registry.ts`.
- `HealthOsLauncherCatalogService` loads catalogue and emits readiness metadata: `serviceSlug`, `plane`, `route`, `apiBacked`, `readiness`, `requiredContext`.
- `GET /internal/v1/launcher/apps` and `?facilityId=` both return **200** (null-safe actor map).
- Tests: `HealthOsLauncherControllerTest` — without facilityId, with facilityId, readiness metadata.

---

## C. Tier 1 parity closure

### VARAPI — Provider Registry

| Check | Status |
|-------|--------|
| Shell route `/registry/providers` | ✅ exists |
| BFF `/internal/v1/registry/providers` | ✅ VARAPI downstream |
| Degraded upstream guidance | ✅ `meta.degraded` + `guidance` on VARAPI failure |
| Shell honest empty state | ✅ providers page shows guidance banner |
| Tests | ✅ `RegistryControllerTest.listProviders_returnsEmptyWhenVarapiFails` |

### TUSO — Facility Registry

| Check | Status |
|-------|--------|
| Shell routes `/registry/facilities`, facility context | ✅ exists |
| BFF `/internal/v1/facilities` live mode | ✅ **live** in generated preview env |
| BFF `/internal/v1/registry/facilities` | ✅ TUSO with degraded meta on failure |
| Live upstream failure | ✅ 502 `TUSO_UNAVAILABLE` on `/facilities` (fail-closed) |
| Tests | ✅ facilities mode switch in generated env; registry degraded meta in controller |

### VITO — Client Registry

| Check | Status |
|-------|--------|
| Shell route `/registry/clients` | ✅ exists |
| BFF `/internal/v1/client-registry/**` | ✅ VITO downstream |
| Degraded upstream guidance | ✅ list + dashboard return `meta.degraded` |
| Shell honest empty state | ✅ clients page guidance banner |
| Tests | ✅ `registry/clients/page.test.tsx` degraded guidance case |

**Tier 1 matrix status:** VARAPI, TUSO, VITO moved from **partial** → **complete (web Tier 1)** for honest live/stub/degraded orchestration. Health OS Launcher row advanced (catalogue richness); full mobile launcher parity remains Tier 5.

---

## D. 16 BFF env vars — wired vs backlog

| Env var | BFF client today | PCW-2 action |
|---------|------------------|--------------|
| `AUDIT_LEDGER_BASE_URL` | none | bound in `orchestration-backlog` |
| `BUTANO_FHIR_BASE_URL` | none | bound in `orchestration-backlog` |
| `CARD_PRINT_AGENT_BASE_URL` | none | bound in `orchestration-backlog` |
| `CONNECTOR_FHIR_BASE_URL` | none | bound in `orchestration-backlog` |
| `DEVELOPER_PORTAL_BASE_URL` | none | bound in `orchestration-backlog` |
| `IDENTITY_ASSURANCE_BASE_URL` | stub controller only (no downstream client) | bound in `orchestration-backlog` |
| `JOBS_SERVICE_BASE_URL` | none | bound in `orchestration-backlog` |
| `OBSERVABILITY_BASE_URL` | none | bound in `orchestration-backlog` |
| `OFFLINE_EDGE_BASE_URL` | none | bound in `orchestration-backlog` |
| `OFFLINE_SYNC_BASE_URL` | none | bound in `orchestration-backlog` |
| `PHARMACY_ELMIS_BASE_URL` | none (distinct from `INVENTORY_ELMIS`) | bound in `orchestration-backlog` |
| `PRODUCT_REGISTRY_BASE_URL` | `ProductRegistryController` uses **Msika** not product-registry-service | bound in `orchestration-backlog` |
| `REFERRAL_SERVICE_BASE_URL` | referrals use **PCT** not referral-service | bound in `orchestration-backlog` |
| `SCHEMA_REGISTRY_BASE_URL` | none | bound in `orchestration-backlog` |
| `SECURITY_HARDENING_BASE_URL` | none | bound in `orchestration-backlog` |
| `SHARE_SLIP_BASE_URL` | none | bound in `orchestration-backlog` |

**Summary:** **0/16** have active RestTemplate clients; **16/16** bound via `impilo.services.orchestration-backlog` + preview Helm env (no localhost in generated preview). Client implementation tracked as BFF orchestration backlog.

---

## E. `optional_full_boot` rename

| Item | Status |
|------|--------|
| `config/full-boot-service-classification.yml` | ✅ `wave_sequenced_full_boot` |
| `generate-full-boot-artifacts.mjs` emit + `build_required` | ✅ updated |
| Backwards alias `optional_full_boot` → `wave_sequenced_full_boot` | ✅ `normalizeClassification()` documented |
| Deployment semantics | unchanged (language-only) |
| Stale generated docs (`FULL_VNEXT_SERVICE_CATALOG.md`, etc.) | refresh on next `generate-full-boot-artifacts.mjs` run |

---

## F. Tests and quality gates

| Check | Result |
|-------|--------|
| `node scripts/full-boot/generate-full-preview-bff-downstream-env.mjs` | ✅ PASS (97 env vars) |
| `bash scripts/guard/check-bff-downstream-mappings.sh` | ✅ PASS |
| `mvn test -Dtest=HealthOsLauncherControllerTest,RegistryControllerTest` | ✅ PASS |
| `npm test -- src/app/registry/clients/page.test.tsx` | ✅ PASS (3 tests) |
| `python3 scripts/product/generate-91-service-testing-dataset.py` | ✅ 92 rows |
| `python3 scripts/architecture/generate-service-accountability-matrix.py` | ✅ 146 rows |
| Full `run-local-quality-gates.sh` | not run end-to-end this wave (targeted gates above) |
| Preview redeploy | **not performed** |

---

## Preview redeploy

**Required:** Yes — running BFF pod still serves PCW-0/PCW-1 config (`IMPILO_BFF_FACILITIES_MODE: stub` until helm values applied).

**Authorization prompt (next step):**

> Redeploy preview to apply PCW-1/PCW-2 BFF env and image changes. Authorize with explicit approval for preview deploy per workspace CI rules (VM gates + user authorization). For full-boot public stack: **`AUTHORIZE FULL BOOT PREVIEW DEPLOY`** after gates pass.

**Do not deploy until explicitly authorized.**

---

## Boundaries observed

- No preview redeploy
- No push
- No Postgres/PVC/secrets changes
- No Tier 2+ parity broadening
- No fake UI pages
