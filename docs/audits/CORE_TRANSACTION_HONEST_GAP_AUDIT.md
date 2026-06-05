# Core Transaction Honest Gap Audit

> **Generated:** 2026-06-05 (post-recovery)  
> **Baseline:** evidence-gated generator — **4/42 transaction-complete** (after outpatient orders batch)  
> **Authority:** [`docs/frontend/GAP_CLOSURE_RULES.md`](../frontend/GAP_CLOSURE_RULES.md)

## Summary

| Classification | Count | Meaning |
|----------------|------:|---------|
| transaction-complete | 3 | Full chain + tests + `COMPLETION_EVIDENCE` registry entry |
| backend-ready-but-frontend-incomplete | 25 | Sovereign/BFF capability exists; UI/mobile write or orchestration gap |
| backend-partial | 11 | Backend depth or BFF proxy incomplete |
| mobile-missing | 2 | Web exists; mobile journey not productised |
| trust-security-incomplete | 1 | Break-glass / emergency authz path incomplete |

### Transaction-complete (evidenced)

| Journey | Chain proof |
|---------|-------------|
| Queue / Walk-in Registration | `/queue/walk-in` → `apiClient.post /internal/v1/queue/entries` → PCT journey meta → `JourneyOrchestrationRail` on encounters |
| Provider Patient Encounter | `/ehr/.../encounter/[id]` → `useEncounterCoreTransaction` → BFF `encounter-{id}` composition |
| Core Transaction Orchestration Shell | `/core-transaction` → `useCoreTransactionFeed` → BFF `/internal/v1/core-transactions` |
| Lab Order & Result | `EncounterLabOrdersPanel` / orders page → `useCreateLabOrder` → `POST /internal/v1/lab-orders` → OROS `placeOrder` |

## Chain template (per incomplete journey)

Each row below records the **true gap** on:  
`route/screen → hook → BFF → sovereign service → contract → test`

### backend-ready-but-frontend-incomplete (25)

| Journey | True gap |
|---------|----------|
| Citizen / Client Onboarding | Card ops / pickup verification thin on `/id-services` |
| Provider Login & Role Activation | Device-block admin UX only |
| Workspace / Shift Context Selection | Workspace settings stub; context headers wired |
| Facility Context Selection | Digital readiness dashboards thin |
| Patient Search & Selection | Orchestration rail not on search surface; chart link only |
| Outpatient Consultation | EHR orders read-only; typed lab-order write from encounter missing |
| Telemedicine Encounter | RTC media intentionally blocked; referral/consent path partial |
| Lab Order & Result | Orders page read-only; BFF POST exists but encounter UI not wired |
| Imaging Order & Result | Viewer exists; order compose from encounter incomplete |
| Prescription & Dispense | Web wired; mobile prescribing depth missing |
| Referral Create & Manage | BFF + routes exist; incoming-referrals handoff UX thin |
| Consent Capture | Admin workflow parity vs mvumo templates |
| Payment / Billing / Claim | Payer-ops stubs; MusheX paths not in browser shell |
| Notification & Communications | Campaign admin thin |
| Fundo / Learning Journey | Mobile learning shell shallow |
| Registry Administration | Issuance/card ops not fully surfaced |
| Marketplace Order | Order list 501 on some BFF paths |
| Wellness & Lifestyle Journey | Routes map coming-soon |
| Social / Community / Timeline | Public-health alerts placeholder in rail |
| Coverage Enrollment | Intelligence surfaces partial |
| Wallet Payment | Typed fail-close exists; checkout surfacing uneven |
| Surveillance / Outbreak Response | Ndila map dashboards incomplete |
| AI Guidance / Nompilo Assist | Route context not always passed to guidance BFF |
| Provider Registry Onboarding | Council import / reconciliation queue thin |
| Citizen Remote Monitoring | Monitoring depth vs wellness BFF |

### backend-partial (11)

| Journey | True gap |
|---------|----------|
| Inpatient Admission Workflow | Admission/discharge orchestration rail added; ward movement depth partial |
| Appointment Scheduling | Check-in BFF added; citizen self-booking UX thin |
| Document Upload / Scan / Index | Indexing UX partial vs document-service |
| Dispatch / Delivery (NHUME) | Dual nhume/dispatch BFF path; offline queue UX |
| Data / Report / Dashboard Journey | NDR/warehouse depth |
| Integration / Sync / Replay | Adapter template admin thin |
| Device / System Event Journey | Device admin backend-only (by design) |
| Health ID Issuance & Card Ops | Pickup verify BFF routes missing |
| Offline Clinical Queue | Federation depth; conflict UX |
| Credential Verification | Verification workflow screens thin |
| Chronic Care Management | Care plan UX depth |

### mobile-missing (2)

| Journey | True gap |
|---------|----------|
| Public Health / CHW Outreach | Field ops mobile thinner than web |
| Civil Registration (UBOMI / CRVS) | No mobile CRVS parity |

### trust-security-incomplete (1)

| Journey | True gap |
|---------|----------|
| Emergency / ED Encounter | Break-glass audit path exists; ED flow depth vs backend |

## Outpatient + inpatient spine batch (this recovery wave)

| Step | Status |
|------|--------|
| Walk-in → journey transaction meta | **Complete** (transaction-complete) |
| Appointment check-in → queue spine | **Partial** (BFF + scheduled UI; not transaction-complete) |
| Admission → `admission-{ref}` transaction id | **Partial** (BFF meta + admission rail; not transaction-complete) |
| Outpatient consult orders write | **Complete** for lab lane (`EncounterLabOrdersPanel` + typed BFF body) |
| Inpatient discharge correlation | **Open** |

## Next honest batches (recommended order)

1. **Outpatient consult orders** — wire `LabOrdersController` POST from encounter orders tab + test.
2. **Appointment scheduling** — citizen booking + check-in e2e test before `transaction-complete`.
3. **Inpatient admission** — discharge correlation + ward movement + test before `transaction-complete`.
4. **Emergency encounter** — break-glass UX + trust-security classification lift.

## Governance

- Generator enforces `COMPLETION_EVIDENCE` — see [`CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md`](./CORE_TRANSACTION_METRIC_FRAUD_INCIDENT_2026-06-05.md).
- Quality gate: `scripts/guard/check-core-transaction-completion-evidence.sh`.
