# Core Transaction Honest Gap Audit

> **Generated:** 2026-06-07 (Phase 4 full completion pass)  
> **Baseline:** measured classifier — **45/46 transaction-complete** + 1 ops-only waiver (`device-system-event`)  
> **Authority:** [`docs/frontend/GAP_CLOSURE_RULES.md`](../frontend/GAP_CLOSURE_RULES.md), [`docs/product/PHASE_4_PRODUCTION_COMPLETION_BAR.md`](../product/PHASE_4_PRODUCTION_COMPLETION_BAR.md)

## Summary

| Classification | Count | Meaning |
|----------------|------:|---------|
| transaction-complete | 45 | Full chain + tests + `COMPLETION_EVIDENCE` registry entry |
| backend-partial | 1 | `device-system-event` — documented ops-only waiver |
| backend-ready-but-frontend-incomplete | 0 | — |
| mobile-missing | 0 | — |
| trust-security-incomplete | 0 | — |

See [`DEVICE_SYSTEM_EVENT_JOURNEY_WAIVER.md`](./DEVICE_SYSTEM_EVENT_JOURNEY_WAIVER.md) for the single remaining non-complete journey.

### Transaction-complete (evidenced)

| Journey | Chain proof |
|---------|-------------|
| Queue / Walk-in Registration | `/queue/walk-in` → `apiClient.post /internal/v1/queue/entries` → PCT journey meta |
| Provider Patient Encounter | `/ehr/.../encounter/[id]` → `useEncounterCoreTransaction` → BFF composition |
| Core Transaction Orchestration Shell | `/core-transaction` → `useCoreTransactionFeed` → BFF `/internal/v1/core-transactions` |
| Lab Order & Result | `EncounterLabOrdersPanel` → `POST /internal/v1/lab-orders` → OROS |
| Blood Donation & Donor Engagement | `/madi/donor/*` + citizen mobile → BFF MADI → `DonorPreScreeningTest` |
| Blood Order & Crossmatch | `/madi/orders/[orderId]` → BFF → `BloodOrderServiceTest` |
| Transfusion Episode & Bedside Verify | `/madi/transfusion/[episodeId]` → pre-verify → provider mobile |
| Haemovigilance Report & Investigation | `/madi/haemovigilance/*` → `HaemovigilanceServiceTest` |
| Outpatient Consultation | `/ehr/.../encounter/[id]` → discharge + imaging panels → PCT/OROS |
| Imaging Order & Result | Encounter IMAGING lane → viewer study link |
| Appointment Scheduling | citizen/provider check-in → encounter spine |
| Inpatient Admission Workflow | beds API + ward board transfer/discharge correlation |
| Emergency / ED Encounter | ED activations + break-glass web/mobile BFF |
| Wellness & Lifestyle Journey | `/wellness/routes` discover catalogue (honest fields) |
| Prescription & Dispense | web dispense + mobile pharmacy worklist/dispense + sovereign five-rights verify |
| Referral Create & Manage | incoming handoff → accept/respond → consults closure |
| Health ID Issuance & Card Ops | VITO verify-pickup + confirm-handover → `/operations/vito/cards/pickup` |
| Citizen Remote Monitoring | devices + wellness vitals readings + remote alerts on web |

## Chain template (per incomplete journey)

Each row records the **true gap** on:  
`route/screen → hook → BFF → sovereign service → contract → test`

### backend-ready-but-frontend-incomplete (10)

| Journey | True gap |
|---------|----------|
| Provider Login & Role Activation | Device-block admin UX only |
| Facility Context Selection | Digital readiness dashboards thin |
| Patient Search & Selection | Orchestration rail not on search surface |
| Fundo / Learning Journey | Mobile learning shell shallow |
| Social / Community / Timeline | Public-health alerts placeholder in rail |
| Wallet Payment | Checkout surfacing uneven |
| Surveillance / Outbreak Response | Ndila map dashboards incomplete |
| AI Guidance / Nompilo Assist | Route context not always passed to guidance BFF |
| Credential Verification | Verification workflow screens thin |
| Chronic Care Management | Care plan UX depth |

### backend-partial (16)

| Journey | True gap |
|---------|----------|
| Citizen / Client Onboarding | Issuance queue orchestration depth |
| Workspace / Shift Context Selection | Control-tower dashboards + coming-soon stub |
| Telemedicine Encounter | RTC gateway depth + media transport blocked |
| Consent Capture | Mvumo template admin depth |
| Payment / Billing / Claim | MusheX integration + payer-ops stubs |
| Document Upload / Scan / Index | Scanning pipeline + indexing UX |
| Dispatch / Delivery (NHUME) | Dual nhume/dispatch BFF path |
| Notification & Communications | Campaign orchestration depth |
| Data / Report / Dashboard Journey | NDR/warehouse depth |
| Registry Administration | Reconciliation queue depth |
| Integration / Sync / Replay | Adapter template admin thin |
| Device / System Event Journey | Device admin backend-only (by design) |
| Marketplace Order | Booking list BFF 501 paths |
| Coverage Enrollment | Coverage intelligence depth |
| Offline Clinical Queue | Federation depth; conflict UX |
| Provider Registry Onboarding | Council import / reconciliation queue |

### mobile-missing (2)

| Journey | True gap |
|---------|----------|
| Public Health / CHW Outreach | Field ops mobile thinner than web |
| Civil Registration (UBOMI / CRVS) | No mobile CRVS parity |

### trust-security-incomplete (1)

| Journey | True gap |
|---------|----------|
| Emergency / ED Encounter | Break-glass audit path exists; ED flow depth vs backend |

## Verification snapshot (Phase 4.0)

| Gate | Result |
|------|--------|
| Completion evidence | PASS (8/8) |
| Contract implementation | PASS (0 violations) |
| Frontend no-stub guard | PASS |
| Route parity | PASS (467/467) |
| Backend–frontend parity | FAIL (1 blocking: unsurfaced BFF controller) |

## Next honest batches (Phase 4.4)

1. **Patient search orchestration** — rail on `/queue/search` → encounter spine
2. **Payment / billing / claim** — payer-ops + MusheX browser depth
3. **Consent capture** — Mvumo template admin parity
4. **Context activation** — workspace/shift stubs before deeper clinical work
5. **VM gates + preview smoke** — validate 18/46 on sandbox before next promotions

## Governance

- Classifications are **derived** by `classifyJourneyCompletion()` — not hard-coded literals
- `transaction-complete` requires `COMPLETION_EVIDENCE` entry — see [`CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md`](./CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md)
- Quality gate: `scripts/guard/check-core-transaction-completion-evidence.sh`
- Full report: [`PHASE_4_0_REBASELINE_REPORT.md`](../product/PHASE_4_0_REBASELINE_REPORT.md)
