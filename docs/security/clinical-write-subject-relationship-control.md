# Clinical-write subject-relationship control (P3 consent follow-up)

**Status:** implemented (strict — require an active care context) · **Scope:** pct-service, inpatient-service
**Branch:** `chore/paydown-authz-stores`

## Problem

The V018 TSHEPO rules enabled clinical-write endpoints that were previously deny-by-default.
ext_authz / TSHEPO enforces the RBAC dimensions (actor type, clinical role, facility,
purpose-of-use), but a **POST-to-collection** write carries the subject CPID in the request
**body**, not the path — so the PDP has no resource id to bind and **delegates** the subject-level
check to the owning service (doctrine dimension 6, "subject relationship").

Verification found the services did not enforce anything: pct `CarePlanService.create` /
`ProblemService.add` and inpatient `InpatientClinicalService.*` read `subject_cpid`/`patientId` from
the body and persisted. So the in-service control was the load-bearing one and it was absent — an
actor with the coarse RBAC could mint clinical records against an arbitrary patient CPID.

## Control: strict — require an active care context

A clinical write is permitted only for a patient the actor holds an **active care context** with.
Resolution differs by service but the posture is the same: **no resolvable context → 403**, and
**EMERGENCY / BREAK_GLASS** purpose-of-use waives the requirement (emergency care is never blocked;
the waiver is audited upstream).

### pct (outpatient) — `ClinicalAccessGuard.requireCareRelationship`
The write must reference a **journey** (`journey_id`) or **encounter** (`encounter_id`) that resolves
(in this tenant) to the subject. A context-free write is **denied**. pct's normal flow always
establishes a journey/encounter first — the mobile enforces it at visit-start (`PatientLookupScreen`
requires a `journey_id`) and the care-plan create reroute (`a85885e78`) sends it.

### inpatient — `InpatientClinicalService.requireActiveCareContext(subjectCpid, admissionRef, encounterId)`
Resolved in order:
1. an explicit **`admissionRef`** that exists in this tenant and belongs to the subject; else
2. a supplied **`encounterId`** that resolves (in tenant) to the subject — via
   `AdmissionRepository.findByTenantIdAndEncounterId` (inpatient has no standalone encounter table;
   `encounter_id` is a non-null column on each admission). This lets a write carrying only an
   encounter (newborn APGAR, discharge clearances) resolve without a standalone admission; else
3. the subject must have an **ADMITTED** admission in this tenant
   (`findBySubjectCpidAndStatus(cpid,"ADMITTED")`, tenant-filtered) — the episode anchor. Callers
   that post `patientId` only still pass **when the patient is admitted**.

None resolving → 403.

## Where it is wired

- **pct:** `CarePlanService.create`, `ProblemService.add`.
- **inpatient single-subject writes:** `createCarePlan`, `recordFluid`, `recordChartEntry`
  (`recordObservation`), `recordEws`, `activateEmergency`, `initDischargeClearances`,
  `createWardAlert`, `recordApgar`, `syncMarSchedule`, `administerMedication`. `requestTransfer`
  already requires + tenant-filters its `admissionRef`.
- **inpatient resuscitation children** (`recordResuscitation`, `logEmergencyAction`,
  `startResuscitationPhase`, `endResuscitationPhase`): gated by `requireActivation`, now tightened to
  require the parent activation to exist **and** belong to the current tenant. The subject was
  validated once at `activateEmergency`; an admission is **not** re-resolved mid-resuscitation (a
  live code is never gated).
- **inpatient `submitHandover`** (multi-admission shift handover, no single subject): each referenced
  `admissionRef` is folded in only if it exists in this tenant (cross-tenant refs silently skipped).
- **Mobile:** the provider-app's outpatient care-plan create/list routes to **pct** (the strangler
  target) and sends `journey_id`/`encounter_id`. Inpatient mobile writes send `patientId` and resolve
  via the patient's active admission.

## Residuals (honest)

1. **Newborn APGAR via encounter.** APGAR carries an `encounterId`, not an `admissionRef`; under the
   encounter path it resolves **only if that encounter resolves to the neonate's CPID** (not the
   mother's). The neonatal encounter model should be validated; if APGAR is recorded against the
   mother's encounter it would 403 and need a neonate encounter/admission or an explicit carve-out.
2. **Resuscitation children** are gated by tenant-scoped activation only (subject validated once at
   activation) — deliberate, so live resuscitation is never blocked.
3. **Pre-admission / post-discharge** inpatient writes require an active admission or a resolving
   encounter or break-glass — an operational "admit / start-episode first" expectation.
4. **Inpatient clinical-write authz seeding.** The non-care-plan inpatient write routes have no
   TSHEPO rule (V001–V018) and 403 at the gateway. A future `V0xx` (mirroring V018) must seed them —
   this in-service guard must precede that enablement.
5. **Actor-specific assignment.** The guard binds subject↔care-context at facility-team level (the
   platform standard: role + facility + purpose). Tightening to provider-specific via vashandi
   `WorkforceAssignmentEntity` is a deliberate stronger-than-platform future option, not a gap.

## Verification

- pct `ClinicalAccessGuardTest` 6/6 (journey/encounter allow · context-free deny · unresolvable
  deny · cross-patient deny · emergency bypass); pct suite **88/88**.
- inpatient `InpatientClinicalDepthIT` 6/6 (happy path resolves the active admission · discharge
  resolves via its `encounterId` · cross-patient admission ref → 403 · no-active-admission → 403 ·
  emergency purpose → allowed) · `InpatientTenantIsolationIT` green (seeds an admission so the
  isolation assertion stands) · inpatient suite **18/18** + all 4 ITs (9) green.
- product-truth gate: **95 services, 0 gaps**; phase6 completion gate: **incomplete=0**.
