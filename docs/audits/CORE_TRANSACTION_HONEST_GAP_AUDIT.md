# Core Transaction Honest Gap Audit

> **Generated:** 2026-06-07 (product-truth skeptical pass)  
> **Baseline:** measured classifier — **26/46 transaction-complete** + 1 ops-only waiver (`device-system-event`)  
> **Authority:** [`docs/frontend/GAP_CLOSURE_RULES.md`](../frontend/GAP_CLOSURE_RULES.md), [`PRODUCT_TRUTH_SKEPTICAL_AUDIT.md`](./PRODUCT_TRUTH_SKEPTICAL_AUDIT.md)

## Summary

| Classification | Count | Meaning |
|----------------|------:|---------|
| transaction-complete | 26 | Full chain + runtime tests + `COMPLETION_EVIDENCE` registry entry |
| backend-ready-but-frontend-incomplete | 5 | BFF wired; UX depth or parity gaps |
| backend-partial | 14 | Sovereign or BFF depth incomplete |
| mobile-missing | 2 | Web exists; mobile parity missing |
| trust-security-incomplete | 0 | — |

See [`DEVICE_SYSTEM_EVENT_JOURNEY_WAIVER.md`](./DEVICE_SYSTEM_EVENT_JOURNEY_WAIVER.md) for the ops-only waiver journey.

### Transaction-complete (evidenced — 26)

| Journey | Chain proof |
|---------|-------------|
| Queue / Walk-in Registration | walk-in → BFF queue → PCT |
| Provider Patient Encounter | encounter rail → BFF composition |
| Core Transaction Orchestration Shell | `/core-transaction` → BFF feed |
| Lab Order & Result | encounter panel → OROS |
| Blood Donation / Order / Transfusion / Haemovigilance | MADI web + mobile + service IT |
| Outpatient / Inpatient / Emergency | encounter + beds + ED BFF |
| Imaging Order & Result | IMAGING lane → viewer |
| Appointment Scheduling | scheduling BFF + check-in |
| Wellness Journey | wellness catalogue routes |
| Prescription & Dispense | five-rights + mobile pharmacy |
| Referral Create & Manage | handoff BFF |
| Health ID Issuance & Card Ops | VITO pickup + ops surface |
| Citizen Remote Monitoring | device readings + alerts |
| Wallet Payment | `WalletControllerTest` + Playwright e2e |
| Coverage Enrollment | `CoverageControllerTest` + hook test + Playwright e2e |
| Document Upload / Scan / Index | multipart BFF upload IT + documents page test |
| Patient Search & Selection | `PatientSearchOrchestrationRail` + `PatientControllerTest` + search page/e2e tests |
| Consent Capture | Mvumo admin hooks + `MvumoAdminControllerTest` + teleconsult consent IT |
| Payment / Billing / Claim | `PayerOpsControllerTest` + payer-ops SANDBOX rail UX + e2e |
| Facility Context Selection | `FacilityControllerTest` + context-selection e2e |
| Workspace / Shift Context | `WorkspaceControllerTest` + `ShiftControllerTest` + handover BFF + settings panel |

## Downgraded from false 45/46 (24 journeys)

Removed from `COMPLETION_EVIDENCE` after skeptical audit — see [`PRODUCT_TRUTH_SKEPTICAL_AUDIT.md`](./PRODUCT_TRUTH_SKEPTICAL_AUDIT.md):

- **Blockers:** `payment-billing-claim`, `telemedicine-encounter`, `consent-capture`
- **Metric-only (18):** chronic-care, patient-search, facility/workspace context, provider-login, credential-verification, provider-registry-onboarding, registry-administration, integration-sync, notification-comms, reporting-dashboard, marketplace-order, dispatch-delivery, offline-clinical-queue, ai-guidance-nompilo, surveillance-outbreak, social-community, public-health-outreach, crvs-ubomi, citizen-onboarding

## Chain template (per incomplete journey)

Each row records the **true gap** on:  
`route/screen → hook → BFF → sovereign service → contract → test`

### backend-ready-but-frontend-incomplete (9)

| Journey | True gap |
|---------|----------|
| Provider Login & Role Activation | Device-block admin UX only |
| Facility Context Selection | Digital readiness dashboards thin |
| Fundo / Learning Journey | Provincial UAT sign-off + PKI-signed credentials (credential-verification-service) deferred |
| Social / Community / Timeline | Public-health alerts placeholder in rail |
| Surveillance / Outbreak Response | Ndila map dashboards incomplete |
| AI Guidance / Nompilo Assist | Route context not always passed to guidance BFF |
| Credential Verification | Verification workflow screens thin |
| Chronic Care Management | Care plan UX depth |

### backend-partial (14)

| Journey | True gap |
|---------|----------|
| Citizen / Client Onboarding | Issuance queue orchestration depth |
| Workspace / Shift Context Selection | Control-tower dashboards + coming-soon stub |
| Telemedicine Encounter | RTC gateway depth + media transport blocked |
| Consent Capture | Mvumo template admin depth |
| Payment / Billing / Claim | MusheX integration + payer-ops stubs |
| Document Upload (scanning) | Scanning pipeline depth beyond upload |
| Dispatch / Delivery (NHUME) | Dual nhume/dispatch BFF path |
| Notification & Communications | Campaign orchestration depth |
| Data / Report / Dashboard Journey | NDR/warehouse depth |
| Registry Administration | Reconciliation queue depth |
| Integration / Sync / Replay | Adapter template admin thin |
| Device / System Event Journey | Device admin backend-only (by design — waiver) |
| Marketplace Order | Booking list BFF 501 paths |
| Offline Clinical Queue | Federation depth; conflict UX |
| Provider Registry Onboarding | Council import / reconciliation queue |

### mobile-missing (2)

| Journey | True gap |
|---------|----------|
| Public Health / CHW Outreach | Field ops mobile thinner than web |
| Civil Registration (UBOMI / CRVS) | No mobile CRVS parity |

## Preview verification (2026-06-07)

| Check | Result |
|-------|--------|
| Preview commit | `c5f8ff43` (stale vs repo HEAD) |
| Auth-gated routes | 307 redirect without Keycloak session |
| Deploy | Requires explicit authorization after VM gates PASS |

## Next honest batches

1. **Payment / billing / claim** — remove payer-ops stubs; MusheX browser depth; BFF IT + e2e before re-promotion
2. **Patient search orchestration** — rail on `/queue/search` → encounter spine
3. **Consent capture** — Mvumo template admin parity + runtime tests
4. **Context activation** — workspace/shift stubs before deeper clinical work
5. **Tier B journeys** — replace source-only golden threads with BFF IT or Playwright before re-adding to evidence

## Governance

- Classifications are **derived** by `classifyJourneyCompletion()` — not hard-coded literals
- `transaction-complete` requires `COMPLETION_EVIDENCE` entry — see [`CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md`](./CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md)
- Quality gate: `scripts/guard/check-core-transaction-completion-evidence.sh`
- Skeptical pass: [`PRODUCT_TRUTH_SKEPTICAL_AUDIT.md`](./PRODUCT_TRUTH_SKEPTICAL_AUDIT.md)
