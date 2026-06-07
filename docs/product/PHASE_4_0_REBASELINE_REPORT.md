# Phase 4.0 Re-baseline Report

> **Generated:** 2026-06-07  
> **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl` @ `9c98e0cd`  
> **Authority:** [`PHASE_4_PRODUCTION_COMPLETION_BAR.md`](./PHASE_4_PRODUCTION_COMPLETION_BAR.md)

## Executive summary

Phase 4.0 replaces the stale hard-coded completion matrix with **measured classifications** derived from contract signals, route stub scans, and PO gap notes. The evidence gate for `transaction-complete` is unchanged: **8/46 journeys** remain production-ready with full chain proof.

Prior phases are **not assumed complete** — this re-baseline re-measures against current code after recent contract/AsyncAPI remediation.

---

## Stale matrix fix

| Before (hard-coded literals) | After (measured classifier) |
|------------------------------|----------------------------|
| Classifications asserted in `JOURNEYS` array | `classifyJourneyCompletion()` derives status from signals |
| Only `transaction-complete` evidence-gated | All classifications derived; `COMPLETION_EVIDENCE` still sole promotion path |
| Gap fields mixed with assertions | `JOURNEY_GAP_OVERRIDES` preserves PO gap notes separately |

**Generator changes:** [`scripts/product/generate-core-transaction-maps.mjs`](../../scripts/product/generate-core-transaction-maps.mjs)  
**Guard update:** [`scripts/guard/check-core-transaction-completion-evidence.sh`](../../scripts/guard/check-core-transaction-completion-evidence.sh) — validates runtime `--check-only` count instead of grep for hard-coded literals.

---

## Classification counts (measured)

| Classification | Stale baseline | Measured (Phase 4.0) | Δ |
|----------------|---------------:|---------------------:|--:|
| transaction-complete | 8 | 8 | 0 |
| backend-ready-but-frontend-incomplete | 24 | 15 | −9 |
| backend-partial | 11 | 20 | +9 |
| mobile-missing | 2 | 2 | 0 |
| trust-security-incomplete | 1 | 1 | 0 |

Full matrix: [`CORE_TRANSACTION_COMPLETION_MATRIX.md`](./CORE_TRANSACTION_COMPLETION_MATRIX.md)  
Delta JSON: [`reports/product/classification-rebaseline.json`](../../reports/product/classification-rebaseline.json)

### Journeys reclassified (13 deltas)

| Journey | Stale | Measured | Reason |
|---------|-------|----------|--------|
| Citizen / Client Onboarding | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: issuance queue ops depth |
| Workspace / Shift Context | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: control-tower dashboards thin |
| Telemedicine Encounter | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: RTC gateway depth |
| Consent Capture | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: mvumo template depth |
| Payment / Billing / Claim | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: MusheX integration depth |
| Notification & Communications | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: campaign orchestration depth |
| Registry Administration | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: issuance queue depth |
| Marketplace Order | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: booking BFF 501 paths |
| Wellness & Lifestyle | backend-ready-but-FE-incomplete | backend-partial | Stub route `/wellness/routes` + coming-soon |
| Coverage Enrollment | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: coverage intelligence depth |
| Provider Registry Onboarding | backend-ready-but-FE-incomplete | backend-partial | `missingBackend`: council import depth |
| Credential Verification | backend-partial | backend-ready-but-FE-incomplete | Backend contracts clean; frontend screens thin |
| Chronic Care Management | backend-partial | backend-ready-but-FE-incomplete | Backend contracts clean; care plan UX depth |

---

## Phase 1→3 generator re-run

| Generator | Key output | Notable delta |
|-----------|------------|---------------|
| `generate-product-truth-recovery.mjs` | 2503 entries, 467 routes | +routes vs prior scan |
| `generate-contract-implementation-matrix.mjs` | 4451 OpenAPI ops, **0 violations** | Contract wave complete |
| `generate-core-transaction-maps.mjs` | 46 journeys, 8 complete | Measured classifier active |
| `generate-experience-orchestration.mjs` | 494 routes, 124 orphans, 9 stubs | Orphan routes flagged |

---

## Verification results

| Gate | Result | Notes |
|------|--------|-------|
| `check-core-transaction-completion-evidence.sh` | **PASS** | 8 evidence entries = 8 transaction-complete |
| `check-contract-implementation.sh` | **PASS** | 0 OpenAPI/AsyncAPI violations |
| `check-frontend-mocks-and-stubs.sh` | **PASS** | 493 pages scanned; legacy warn on `/wellness/routes` |
| `npm run type-check` | **PASS** | |
| `npm run test:no-stubs` | **PASS** | |
| `npm run test:routes` | **PASS** | 467/467 routes have pages |
| `stub-audit/run-all.sh` | **PASS** | Scan output in `docs/stub-audit/scan-output/` |
| `check-backend-frontend-parity.sh` | **PASS** (with parity doc commit) | Launcher wired to `/internal/v1/launcher/apps`; monitoring already on `/monitoring/devices` |

**Parity matrix:** Live 12, Partial 20

---

## Transaction-complete (evidence-gated, unchanged)

| Journey | Evidence |
|---------|----------|
| Queue / Walk-in Registration | BFF queue + walk-in tests |
| Provider Patient Encounter | Encounter composition + orchestration rail |
| Core Transaction Orchestration Shell | `/core-transaction` feed + BFF |
| Lab Order & Result | Encounter lab orders panel + BFF POST |
| Blood Donation & Donor Engagement | MADI golden thread + e2e |
| Blood Order & Crossmatch | MADI orders + service tests |
| Transfusion Episode & Bedside Verify | Pre-verify + provider mobile |
| Haemovigilance Report & Investigation | Reaction report + national dashboard |

---

## Ranked Phase 4.1+ batches

1. **Outpatient consult completion** — discharge + imaging write lanes (`outpatient-consultation`)
2. **Appointment scheduling** — citizen booking + check-in e2e before evidence promotion
3. **Inpatient admission** — discharge correlation + ward movement tests
4. **Emergency encounter** — break-glass UX + trust-security lift
5. **Wellness routes map** — remove coming-soon stub at `/wellness/routes`
6. **BFF parity debt** — surface new BFF controllers (launcher, monitoring, telemedicine analytics) in UI
7. **Health ID card ops** — pickup verify BFF + `/id-services` depth

---

## Related artifacts

- [`PHASE_4_PRODUCTION_COMPLETION_BAR.md`](./PHASE_4_PRODUCTION_COMPLETION_BAR.md) — production readiness criteria
- [`FRONTEND_MOBILE_COMPLETION_BACKLOG.md`](./FRONTEND_MOBILE_COMPLETION_BACKLOG.md) — ranked remediation backlog
- [`docs/audits/CORE_TRANSACTION_HONEST_GAP_AUDIT.md`](../audits/CORE_TRANSACTION_HONEST_GAP_AUDIT.md) — honest gap audit (updated)
- [`reports/product/classification-rebaseline.json`](../../reports/product/classification-rebaseline.json) — machine-readable deltas
