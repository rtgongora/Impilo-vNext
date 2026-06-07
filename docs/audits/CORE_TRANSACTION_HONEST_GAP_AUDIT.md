# Core Transaction Honest Gap Audit

> **Generated:** 2026-06-07 (Phase 4.2 promotion pass)  
> **Baseline:** measured classifier — **14/46 transaction-complete**  
> **Authority:** [`docs/frontend/GAP_CLOSURE_RULES.md`](../frontend/GAP_CLOSURE_RULES.md), [`docs/product/PHASE_4_PRODUCTION_COMPLETION_BAR.md`](../product/PHASE_4_PRODUCTION_COMPLETION_BAR.md)

## Summary

| Classification | Count | Meaning |
|----------------|------:|---------|
| transaction-complete | 14 | Full chain + tests + `COMPLETION_EVIDENCE` registry entry |
| backend-ready-but-frontend-incomplete | 11 | Sovereign/BFF capability exists; UI/mobile write or orchestration gap |
| backend-partial | 18 | Backend depth, BFF proxy, or stub-route signals incomplete |
| mobile-missing | 2 | Web exists; mobile journey not productised |
| trust-security-incomplete | 0 | — |

**13 journeys reclassified** vs stale hard-coded matrix — see [`reports/product/classification-rebaseline.json`](../../reports/product/classification-rebaseline.json).

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

## Chain template (per incomplete journey)

Each row records the **true gap** on:  
`route/screen → hook → BFF → sovereign service → contract → test`

### backend-ready-but-frontend-incomplete (15)

| Journey | True gap |
|---------|----------|
| Provider Login & Role Activation | Device-block admin UX only |
| Facility Context Selection | Digital readiness dashboards thin |
| Patient Search & Selection | Orchestration rail not on search surface |
| Outpatient Consultation | Discharge/imaging write lanes still partial |
| Imaging Order & Result | Order compose from encounter incomplete |
| Prescription & Dispense | Mobile prescribing depth |
| Referral Create & Manage | Incoming-referrals handoff UX thin |
| Fundo / Learning Journey | Mobile learning shell shallow |
| Social / Community / Timeline | Public-health alerts placeholder in rail |
| Wallet Payment | Checkout surfacing uneven |
| Surveillance / Outbreak Response | Ndila map dashboards incomplete |
| AI Guidance / Nompilo Assist | Route context not always passed to guidance BFF |
| Citizen Remote Monitoring | Monitoring depth vs wellness BFF |
| Credential Verification | Verification workflow screens thin (reclassified from backend-partial) |
| Chronic Care Management | Care plan UX depth (reclassified from backend-partial) |

### backend-partial (20)

| Journey | True gap |
|---------|----------|
| Citizen / Client Onboarding | Issuance queue ops + card pickup thin (reclassified) |
| Workspace / Shift Context Selection | Control-tower dashboards + coming-soon stub |
| Inpatient Admission Workflow | Ward movement + discharge correlation |
| Telemedicine Encounter | RTC gateway depth + media transport blocked |
| Appointment Scheduling | Scheduling depth + citizen booking UX |
| Consent Capture | Mvumo template admin depth |
| Payment / Billing / Claim | MusheX integration + payer-ops stubs |
| Document Upload / Scan / Index | Scanning pipeline + indexing UX |
| Dispatch / Delivery (NHUME) | Dual nhume/dispatch BFF path |
| Notification & Communications | Campaign orchestration depth |
| Data / Report / Dashboard Journey | NDR/warehouse depth |
| Registry Administration | Issuance queue ops |
| Integration / Sync / Replay | Adapter template admin thin |
| Device / System Event Journey | Device admin backend-only (by design) |
| Health ID Issuance & Card Ops | Pickup verify BFF routes missing |
| Marketplace Order | Booking list BFF 501 paths |
| Wellness & Lifestyle Journey | `/wellness/routes` coming-soon stub |
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

## Next honest batches (recommended order)

1. **Outpatient consult orders** — discharge + imaging write lanes + tests
2. **Appointment scheduling** — citizen booking + check-in e2e before evidence promotion
3. **Inpatient admission** — discharge correlation + ward movement + test
4. **Emergency encounter** — break-glass UX + trust-security classification lift
5. **Wellness routes map** — replace coming-soon stub
6. **BFF parity** — surface launcher/monitoring/telemedicine analytics in UI

## Governance

- Classifications are **derived** by `classifyJourneyCompletion()` — not hard-coded literals
- `transaction-complete` requires `COMPLETION_EVIDENCE` entry — see [`CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md`](./CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md)
- Quality gate: `scripts/guard/check-core-transaction-completion-evidence.sh`
- Full report: [`PHASE_4_0_REBASELINE_REPORT.md`](../product/PHASE_4_0_REBASELINE_REPORT.md)
