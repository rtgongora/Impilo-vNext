# Product State of the Union

> **Generated:** 2026-06-14 · Branch `claude/staging-ux-orchestration-remediation-Yypyl` · HEAD `c0e65ddb`  
> **Preview:** http://41.57.127.235 · Namespace `impilo-full-preview`  
> **Regenerate:** run generators listed in [Refresh sources](#refresh-sources) then update this doc.

---

## Executive summary

Impilo vNext is a **Health Operating System** with **576 registered web routes**, **90+ microservices**, **two Expo mobile apps (169 screens)**, and a unified **one-ui-shell** experience layer. The platform is **architecturally complete** at the contract and service level but **operationally constrained** on the public preview: until full-boot waves 1–8 deploy, the BFF returns **500 / I/O errors** for ~76 undeployed downstream services — this is **not** a superadmin permissions issue.

| Dimension | Status | Evidence |
|-----------|--------|----------|
| **Backend services** | 91 registry entries; 110 wave-classified; wave 0 only live pre-unblock | [`reports/full-boot/wave-enumeration.json`](../../reports/full-boot/wave-enumeration.json) |
| **Web experience** | 576 routes; shell + role-aware home; error degradation added | [`ui/one-ui-shell/src/lib/routes.ts`](../../ui/one-ui-shell/src/lib/routes.ts) |
| **Preview deploy (wave 8)** | **89/89 microservices** enabled; FULL_BOOT_PASS; appointments + citizen feed **200** | [`wave-build-8-summary.json`](../../reports/full-boot/wave-build-8-summary.json) |
| **Web ↔ backend parity** | Mostly **partial**; several **complete** (Social, PH ops, Ndila, BUTANO summary) | [`FRONTEND_BACKEND_PARITY_MATRIX.md`](../architecture/FRONTEND_BACKEND_PARITY_MATRIX.md) |
| **Mobile parity** | 37 capabilities tracked; **8 complete**, remainder partial/deferred | [`MOBILE_PARITY_MATRIX.md`](../architecture/MOBILE_PARITY_MATRIX.md) |
| **Contracts** | 4478 OpenAPI ops; 4539 implemented bindings; 3 violations | [`CONTRACT_IMPLEMENTATION_MATRIX.md`](./CONTRACT_IMPLEMENTATION_MATRIX.md) |
| **Lovable fidelity** | Component/app parity strong; page/flow audits incomplete on 576-route scale | [`page-by-page-fidelity-matrix.md`](../lovable-fidelity/page-by-page-fidelity-matrix.md) |
| **Commit history** | 1256 commits themed by plane | [`COMMIT_PRODUCT_NARRATIVE.md`](./COMMIT_PRODUCT_NARRATIVE.md) |

### Root cause of “feature crashes” (confirmed)

```
UI → experience-bff → wave-0 services (OK)
                    → undeployed services (500 / connection refused)
```

Login as `super@mohcc.gov.zw` seeds **all roles** — failures are **downstream availability**, not RBAC. VM capacity (~107 GiB RAM free) supports full wave deploy.

---

## Phase 1 — Full-stack unblock (deploy)

| Item | Value |
|------|-------|
| Target namespace | `impilo-full-preview` (public ingress) |
| Fallback slice | `impilo-preview` (4-service, ingress disabled) |
| Deploy auth | `AUTHORIZE FULL BOOT PREVIEW DEPLOY` |
| Wave target | **8** (cumulative 0..8) |
| Build command | `bash scripts/operator/fullboot.sh wave-build 8` |
| Deploy command | `FULL_BOOT_MAX_WAVE=8 FULL_BOOT_SKIP_BUILD=1 BYPASS_CI=1` + auth phrase |

**Critical services for previously-500 endpoints:** `booking-service`, `community-service`, `scheduling-service`, `pharmacy-service`, `notification-service`, `search-service`, `wellness-service`, `workflow-service`.

After deploy, verify:

```bash
bash scripts/test/run-full-boot-smoke-tests.sh
bash scripts/guard/check-full-boot-runtime-completeness.sh
curl -sS http://41.57.127.235/internal/v1/appointments -H 'X-Tenant-ID: default' | head
```

---

## Phase 2 — UI resilience + all-access testing

### Error degradation (implemented)

| Component | Purpose |
|-----------|---------|
| [`service-error.ts`](../../ui/one-ui-shell/src/lib/service-error.ts) | Normalizes BFF/network failures |
| [`ShellErrorBoundary.tsx`](../../ui/one-ui-shell/src/components/ShellErrorBoundary.tsx) | Page-level catch; links home + All Features |
| [`QueryResultPanel.tsx`](../../ui/one-ui-shell/src/components/common/QueryResultPanel.tsx) | Loading / empty / service-unavailable states |
| [`Providers.tsx`](../../ui/one-ui-shell/src/components/Providers.tsx) | Wraps app in error boundary |
| [`home/page.tsx`](../../ui/one-ui-shell/src/app/home/page.tsx) | Timeline feed degrades to seeded posts on error |

### All Features catalog

- **Route:** `/platform/all-features` (576 routes by zone/journey)
- **Access:** superadmin taskbar **Features** button + error-boundary links
- **Login:** `super@mohcc.gov.zw` / `test123` for broadest role coverage

---

## Backend not surfaced (top backlog)

From [`BACKEND_NOT_SURFACED_REGISTER.md`](../audits/BACKEND_NOT_SURFACED_REGISTER.md):

| ID | Severity | Capability | Web | Mobile |
|----|----------|------------|-----|--------|
| BNS-001 | HIGH | Core transaction composition | Fixture only | Missing journey shell |
| BNS-002 | HIGH | Workflow operations | Partial | Partial |
| BNS-003 | HIGH | Dispatch operations | Partial | Partial |
| BNS-005 | HIGH | Registry identity ops | Partial | Partial |
| BNS-006 | HIGH | Coverage/claims commands | Partial | Partial |

Full register: [`BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md`](../frontend/BACKEND_CAPABILITY_TO_FRONTEND_SURFACING_MATRIX.md).

---

## Web ↔ backend parity (snapshot)

**Complete:** Indawo site registry, BUTANO SHR summary, Public Health Ops, Ndila maps, Telemedicine analytics, Data pipeline/NDR, Social timeline.

**Partial (HIGH priority):** TSHEPO trust admin, VITO/VARAPI registry, Core Transaction, Nhume dispatch, Telemedicine RTC, MusheX/COSTA finance, Workflow/dispatch, Nompilo guidance.

Matrix: [`FRONTEND_BACKEND_PARITY_MATRIX.md`](../architecture/FRONTEND_BACKEND_PARITY_MATRIX.md).

---

## Mobile readiness

| Metric | Value |
|--------|-------|
| Apps | citizen-app, provider-app (Expo) |
| Screens | **169** (generator); ~147 in prior audit |
| BFF mobile handlers | ~283 |
| Domain capabilities complete | **8 / 37** |
| Complete domains | Social, MADI (donor/drives/orders/transfusion/haemovigilance), Live events, Monitoring devices |

**Known gaps:** citizen conditions/allergies TODO; Costa finance partial; UBOMI mobile missing; RTC telemedicine blocked by design.

Runtime smoke (VM): `pnpm install`, `pnpm mobile:typecheck`, Expo prebuild + `assembleDebug` — see [`MOBILE_EXPERIENCE_REALITY_CHECK.md`](../audits/MOBILE_EXPERIENCE_REALITY_CHECK.md).

Matrix: [`MOBILE_PARITY_MATRIX.md`](../architecture/MOBILE_PARITY_MATRIX.md).

---

## Lovable fidelity scorecard

| Audit | Status |
|-------|--------|
| Source discovery | PASS |
| Component fidelity | PASS (33/33) |
| App parity | PASS |
| Page fidelity | INCOMPLETE (legacy 98-route matrix; shell now 576 routes) |
| Flow fidelity | INCOMPLETE |

Pre-remediation Lovable matrix showed **12.2%** implementation on 98 prototype routes; remediation wave closed many stubs. Current shell exceeds Lovable scope (MADI, public health, enterprise ops). Treat Lovable as **design reference**, not exhaustive route list.

Docs: [`page-by-page-fidelity-matrix.md`](../lovable-fidelity/page-by-page-fidelity-matrix.md), [`fidelity-divergence-analysis.md`](../lovable-fidelity/fidelity-divergence-analysis.md).

---

## Contract & service coverage

| Metric | Count |
|--------|-------|
| OpenAPI operations | 4478 |
| Async channels | 84 |
| Implemented bindings | 4539 |
| Violations | 3 |
| Unowned contract ops | 23 |
| Service coverage ledger rows | 129 |

Docs: [`CONTRACT_IMPLEMENTATION_MATRIX.md`](./CONTRACT_IMPLEMENTATION_MATRIX.md), [`SERVICE_COVERAGE_LEDGER.md`](./SERVICE_COVERAGE_LEDGER.md).

---

## Per-plane status

| Plane | Highlights | Gap theme |
|-------|------------|-----------|
| **trust** | TSHEPO ext_authz, break-glass, admin trust | Device admin UX; mobile break-glass stubs |
| **registry** | VITO/VARAPI/TUSO hubs | Issuance queues; council reconciliation |
| **clinical** | EHR, BUTANO FHIR, pharmacy, MADI transfusion | Citizen personal clinical sections on mobile |
| **data** | NDR, warehouse, surveillance, pipelines | Provider governance strips on mobile |
| **integration** | FHIR gateway, offline sync, connector hub | Adapter template admin depth |
| **experience** | 576 routes, shell taskbar, Nompilo, All Features | Deploy full waves; core-transaction journey |
| **enterprise** | Msika, Costa, dispatch, workflow | Finance mobile parity; order list 501 paths |

---

## 1256-commit narrative + uncommitted delta

The branch history is themed by plane in [`COMMIT_PRODUCT_NARRATIVE.md`](./COMMIT_PRODUCT_NARRATIVE.md).

**Uncommitted delta (~58 paths):** official visual palette + African print canvas, compact shell chrome (slim header, taskbar accessibility), citizen home wallet widget, service error degradation, All Features catalog, full-boot wave enumeration, SOTU generators.

---

## What must still surface (prioritized)

1. **Deploy full-boot waves 1–8** — unblock BFF downstream I/O (blocking E2E).
2. **Core transaction journey shell** — web + mobile (BNS-001).
3. **Workflow + dispatch detail pages** — operator guided UX (BNS-002/003).
4. **Finance/wallet mobile parity** — MusheX/COSTA (partial everywhere).
5. **Citizen clinical personal sections** — conditions/allergies on mobile.
6. **Telemedicine RTC** — label blocked; scheduling/records live.
7. **UBOMI CRVS** — web partial; mobile missing.
8. **Expand Lovable fidelity audits** to 576-route catalog or sample by zone.

---

## Refresh sources

```bash
bash scripts/architecture/sync-pipeline-inventories.sh
node scripts/architecture/generate-parity-inventories.mjs
node scripts/completeness/generate-contract-implementation-matrix.mjs
node scripts/completeness/generate-service-coverage-ledger.mjs
node scripts/product/generate-product-truth-recovery.mjs
node scripts/product/generate-commit-narrative.mjs
bash scripts/lovable-fidelity/run-all.sh
bash scripts/guard/check-backend-frontend-parity.sh
bash scripts/guard/check-mobile-parity.sh
```

---

## Testing access

| Purpose | How |
|---------|-----|
| Broadest roles | Login `super@mohcc.gov.zw` |
| Route catalog | http://41.57.127.235/platform/all-features |
| Health/version | http://41.57.127.235/health/version |
| Preview generation | `bash scripts/operator/report-preview-generation.sh` |
