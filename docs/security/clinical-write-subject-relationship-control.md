# Clinical-write subject-relationship control (P3 consent follow-up)

**Status:** implemented (verify-when-present) · **Scope:** pct-service, inpatient-service
**Branch:** `chore/paydown-authz-stores` · **Commits:** `f3d54c321`, `a85885e78`, `8ad442c93`

## Problem

The V018 TSHEPO rules enabled clinical-write endpoints that were previously
deny-by-default. ext_authz / TSHEPO enforces the RBAC dimensions (actor type,
clinical role, facility, purpose-of-use), but a **POST-to-collection** write
carries the subject CPID in the request **body**, not the path — so the PDP has
no resource id to bind and **delegates** the subject-level check to the owning
service (doctrine dimension 6, "subject relationship").

Verification found the services did not enforce anything: `CarePlanService.create`
/ `ProblemService.add` (pct) and `InpatientClinicalService.createCarePlan` +
peers (inpatient) read `subject_cpid`/`patientId` from the body and persisted. So
the in-service control was the load-bearing one and it was absent.

## Decision: verify-when-present (not strict-require)

The platform's baseline access model is **facility-team-level** — a clinician
authenticated for a role at a facility with a valid purpose may write for any
patient at that facility. A `subjectCpid`-only write is therefore **valid by
design** (e.g. `InpatientTenantIsolationIT` creates a care plan for a patient
with no admission and expects `201`).

A first pass implemented a **strict** guard (require + verify an active care
context). It broke that contract — `InpatientTenantIsolationIT` and the
documented `patientId`-only inpatient write flow began returning `403`. Strict is
a deliberate **stronger-than-platform** posture; adopting it would require a
broad contract change across every clinical-write caller, not a localized fix.

The control was reconciled to **verify-when-present**:

- A write with **no** care-context reference is **permitted** — facility-team
  RBAC (enforced upstream) is the control.
- When a caller **supplies** a care-context reference, it must resolve **in this
  tenant** to the write's subject:
  - **pct** — a `journey_id` or `encounter_id` whose patient is the subject
    (`ClinicalAccessGuard.requireCareRelationship`).
  - **inpatient** — an `admission_ref` whose `subjectCpid` is the subject
    (`InpatientClinicalService.requireAdmissionRelationship`).
- **EMERGENCY / BREAK_GLASS** purpose-of-use waives the check (audited upstream).

This closes the realistic IDOR — an actor writing for patient A while
**referencing patient B's** journey / encounter / admission — without imposing a
stronger-than-platform requirement on context-free writes.

## Where it is wired

- **pct:** `CarePlanService.create`, `ProblemService.add`.
- **inpatient:** `createCarePlan`, `recordFluid`, `recordChartEntry`
  (`recordObservation`), `recordEws`, `activateEmergency`,
  `initDischargeClearances`, `createWardAlert`. (All except care-plans are
  deny-by-default at the gateway today — no live flow is affected; the control is
  in place before their authz is ever seeded.)
- **Mobile reroute:** the provider-app's outpatient care-plan create/list was
  repointed from inpatient to **pct** (the strangler target) and now sends
  `journey_id`/`encounter_id`, so the consistency check has a context to verify.

## Residuals (honest)

1. **Actor-specific assignment — not a deficiency.** The guard verifies
   subject↔care-context at **facility-team level**, which **meets** the platform
   standard (role + facility + purpose). `EncounterEntity.assignedProviderId`
   exists but is unused; tightening to provider-specific via vashandi
   `WorkforceAssignmentEntity` is a feasible but deliberate stronger-than-platform
   future option, not a gap.

2. **Deferred inpatient writes (no single-admission body field):**
   - `recordApgar`, `administerMedication` (MAR) — need an encounter- or
     prescription-based relationship check (no `admissionRef` on the payload).
   - `submitHandover` — multi-admission shift handover (no single subject);
     verify each referenced admission is tenant-scoped, separately.
   - `startResuscitationPhase` — gate via the parent activation's subject
     (`requireActivation` only checks existence today).

3. **Inpatient clinical-write authz seeding.** The 11 non-care-plan inpatient
   write routes have **no** TSHEPO rule (V001–V018) and 403 at the gateway. A
   future `V0xx` (mirroring V018) must seed them — and this guard must precede
   that enablement so the writes are never reachable without the consistency
   check.

## Verification

- pct `ClinicalAccessGuardTest` 6/6 (context-free allow · unresolvable-context
  deny · cross-patient deny · emergency bypass); pct suite **88/88**.
- inpatient `InpatientClinicalDepthIT` 4/4 (incl. cross-patient admission
  reference → 403) · `InpatientTenantIsolationIT` green · inpatient suite
  **18/18** + all 4 ITs (7) green.
- product-truth gate: **95 services, 0 gaps**; phase6 completion gate:
  **incomplete=0**.
