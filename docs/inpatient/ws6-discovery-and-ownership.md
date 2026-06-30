# Workstream #6 — Full Inpatient Suite: Discovery & Ownership Note

> Wave 0 discovery for the Inpatient Suite. Resolves what already exists, who owns each
> capability, and what this workstream EXTENDS vs builds. **No third inpatient service is created.**

## Key ownership decision: PCT vs inpatient-service (the inpatient clinical brain)

The boundary is **already settled and live** in the codebase — this workstream preserves it:

- **PCT (`pct-service`, 8088) owns the admission DECISION** and the patient *journey*: admission
  REQUESTED → APPROVED → ADMITTED, plus discharge/death *case* workflow, clinical notes, problems,
  outpatient care plan, ED, triage, referrals, telemedicine, Cadre Engine (provider-scope decisions).
  `AdmissionWorkflow` documents the handshake explicitly.
- **inpatient-service (8121) owns the physical CENSUS + ward clinical depth**: ward/bed model,
  bed-day accrual, the inpatient `AdmissionEntity` (census), transfers, ward rounds, nursing care
  plans, fluid balance, clinical charting/observations, MAR (eMAR), Early Warning Score, emergency
  activation/resuscitation, APGAR, shift handover (SBAR), ward alerts, discharge clearances,
  procedure episodes, anaesthesia scoring.

**The PCT↔inpatient handshake (pre-existing, preserved):**
1. PCT `POST /v1/journeys/{id}/admit` → `ADMISSION_REQUESTED`.
2. PCT `POST /v1/admissions/{id}/approve` → `ADMISSION_APPROVED` (outbox → `pct.admission.updated`).
3. inpatient `InpatientEventConsumer.consumePctAdmission` materialises the census admission
   (`AdmissionService.admitFromPctApproval`, idempotent on `pct_admission_id`) → emits
   `inpatient.admission.bed_assigned`.
4. PCT `InpatientBedAssignedConsumer` stamps `inpatient_admission_ref` back via `linkInpatientAdmission`.

→ **Decision: the inpatient clinical brain is `inpatient-service`** for ward-level census + clinical
workflow; **PCT remains the journey/decision SoR**. This WS#6 EXTENDS `inpatient-service` (and adds
two thin BFF/web surfaces), and does **NOT** duplicate PCT's decision/journey truth.

## Ownership table

| Capability | Existing owner found | Extend or build | Notes |
|---|---|---|---|
| Admission decision (request/approve/admit) | PCT `AdmissionWorkflow` + `AdmissionController` | reuse (no change) | journey SoR; handshake intact |
| Census admission (bed/ward, bed-days) | inpatient `AdmissionService` | reuse | idempotent PCT handshake already wired |
| Ward / bed model + bed board | inpatient `WardEntity`/`BedEntity`/`BedManagementService` | **EXTEND** | add bed-safety constraints (gender/age/isolation/oxygen/monitoring/ICU) — currently MISSING |
| Bed assignment | inpatient `BedManagementService.assignPatient` | **EXTEND** | add safety-constraint gate + event |
| Inpatient encounter | inpatient `AdmissionEntity.encounterId` (+ PCT `EncounterEntity`) | reuse | maps to FHIR Encounter |
| Clerking / ward-round note | inpatient `WardRoundService` + PCT `ClinicalNoteService` | reuse | both exist |
| Problem list | PCT `ProblemService` | reuse | SoR is PCT |
| Nursing admission assessment | inpatient `clinical_chart_entry` (chart_type) | reuse | flexible charting |
| Handover (SBAR) | inpatient `submitHandover`/`acceptTakeover` | reuse | exists |
| Care plan | inpatient `CarePlanService` (+ PCT outpatient) | reuse | ward nursing care plan in inpatient |
| Observations / vitals | inpatient `recordObservation`/`recordChartEntry` | reuse | exists |
| Early Warning Score | inpatient `recordEws` | **EXTEND** | computes risk but emits NO escalation event / NO Rito hook — MISSING |
| Deterioration escalation + response timer | — | **BUILD (thin)** | new escalation event + ward alert + Rito safety link on EWS≥5 |
| MAR / eMAR administration | inpatient `administerMedication` | **EXTEND** | persists MAR but emits NO event + NO inventory consumption hook — MISSING |
| Stock / ward-stock / consumption ledger | inventory-service (Dura, 8098) `POST /v1/internal/consumption/clinical` | reuse (call out) | inpatient becomes a CLIENT; never owns stock |
| Orders (labs/imaging/meds/procedures/blood/diet/consults) | OROS (8089) `POST /v1/orders` | reuse (call out) | inpatient gains an `OrosIntegration` client (mirrors PCT) |
| Discharge clearance checklist | inpatient `initDischargeClearances` etc. | **EXTEND** | add discharge-summary generation + gating + Butano/FHIR Composition mapping — MISSING |
| Discharge summary → SHR | Butano (FHIR/SHR) | reuse (map) | inpatient emits a Composition-shaped summary event |
| Comms / post-discharge follow-up | Khuluma | reuse (request) | request follow-up, do not send |
| Nompilo guidance | guidance-service (domain seeds) | **EXTEND** | add domain='inpatient' guidance seeds |
| Safety / incidents | Rito | reuse (link) | escalation missed/delayed → Rito safety event |
| Trust / access / break-glass | Tshepo + OPA | **EXTEND** | add `impilo.inpatient` OPA package + tshepo `policy_rule` seeds (V027) |
| Provider scope (cadre) | PCT Cadre Engine + Varapi | reuse | admission authz |
| Facility / ward / bed location | Tuso/Indawo | reuse | ward create via BFF `AdminWardController` → Tuso |
| Death / CRVS | Ubomi (+ PCT DeathWorkflow) | reuse / defer | hook only |
| Blood | Madi | defer | hook via OROS BLOOD_BANK |
| Billing / payment | Costa / MusheX | defer | hook only |
| Transfer transport / dispatch | Ndila / Nhume | defer | hook only |

## MVP spine gaps this workstream closes (everything else REUSED or DEFERRED)

1. **Bed-safety constraints** on bed assignment (gender/age/isolation/oxygen/monitoring/ICU) — new bed/ward columns + a `BedSafetyEvaluator` gate + `inpatient.bed.assigned` event.
2. **EWS → deterioration escalation**: EWS≥5 raises an escalation (ward alert + `inpatient.ews.escalation_triggered` event) with a response-due timer; missed/delayed → Rito safety event (`inpatient.safety.event_raised`).
3. **eMAR → inventory stock consumption**: medication administration emits `inpatient.medication.administered` and calls inventory `POST /v1/internal/consumption/clinical` (refType=INPATIENT_EMAR), with 5-rights safety checks.
4. **Inpatient orders → OROS**: a thin `OrosIntegration` client so a ward order (≥1 type) routes to OROS end-to-end.
5. **Discharge summary + gating + FHIR mapping**: block discharge until clearances complete; generate a discharge summary (med reconciliation + discharge meds + follow-up) mapped toward a Butano/FHIR **Composition**; request Khuluma follow-up.
6. **Cross-cutting**: `impilo.inpatient` OPA policy + tshepo `policy_rule` seeds (V027); domain events on the outbox; Nompilo `inpatient` guidance seeds; web bed-board/discharge surfaces wired to the new capabilities; provider-app mobile slice.

## Deferred (honest — wired to owner, not built here)
Theatre depth, blood/Madi transfusion execution, inter-facility transfer transport (Ndila/Nhume),
maternity/neonatal depth beyond APGAR, mental-health/medico-legal, infection-control, billing
(Costa/MusheX), death/CRVS execution (Ubomi), offline-first sync. Each routes to the owner named above.
