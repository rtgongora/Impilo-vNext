# Shared Service Read-Models & API Contracts (FROZEN)

> **Status:** DESIGN GATE — frozen contracts. Branch `intake/provider-clinical-place-design`.
> These are the **read-model shapes the T1/T3/T4 lanes share** so they compose without colliding. Freeze
> them up front; lanes build against these shapes in parallel. Each contract names its **single producer**
> (the SoR) and its consumers. Shapes are illustrative TypeScript — the wire format is JSON; the Java DTOs
> mirror these field-for-field.
>
> **Doctrine:** these are **read-models / composition contracts**, not new systems-of-record. Every field
> traces to an existing entity. The experience-bff composes them; it persists nothing (stateless).

## Contract index

| # | Read-model | Producer (SoR) | Primary consumers | New code? |
|---|------------|----------------|-------------------|-----------|
| C1 | Provider Professional Profile | Varapi | T1 shell, T3 cockpit, Cadre Engine | reuse `standing-summary` |
| C2 | Workforce / Assignment / Check-in / Affiliation | Vashandi (+ workforce-governance) | T1 context picker, Cadre Engine | add "active-context" query |
| C3 | Person / Identity | VITO (via tshepo-identity) | all lanes | extend resolution |
| C4 | Facility / Dept / Service-point / Place | TUSO + Indawo | T4 cockpit, T1 picker, OROS routing | add `FacilityModeContext` |
| C5 | Fundo Readiness | Fundo | T4 setup wizard | new (Fundo lane) |
| C6 | Core-Transaction State object | composition (BFF) over PCT/COSTA/Coverage | all surfaces | compose |
| C7 | Referral / Consult Package | referral-service + PCT | T3 referral, telemedicine | freeze payload schema |
| C8 | Access / Compensation Value-Event | COSTA + MUSheX + Coverage | Lane C, patient notify | reuse outbox |
| C9 | Cadre Engine Decision | PCT (new) | T3 cockpit, all clinical actions | **new** |

---

## C1 — Provider Professional Profile (Varapi)

**Producer:** `varapi-service` (`GET /v1/internal/providers/{id}/standing-summary` exists — extend, don't fork).
**Consumers:** provider shell (My Professional), Cadre Engine (`cadre`/`profession`/`licenceValid`).

```ts
interface ProviderProfessionalProfile {
  providerPublicId: string;        // ULID — NEVER an auth credential
  impiloHealthId: string;          // person anchor (VITO)
  profession: string;              // e.g. "MEDICAL_PRACTITIONER"
  cadre: string;                   // drives Cadre Engine scope
  title: string | null;
  licenceStatus: "ACTIVE"|"SUSPENDED"|"EXPIRED"|"REVOKED"|"PENDING_RENEWAL";
  licenceValid: boolean;           // derived gate
  lifecycleStatus: string;
  professionalStandingStatus: string;
  councils: Array<{
    councilId: string;
    councilCode: string;           // e.g. MDPCZ, NMCZ — NOT hardcoded single regulator
    registrationNumber: string;    // EC/council number (resolvable, never authenticates)
    registrationStatus: string;
  }>;
  picEligible: boolean;            // person-in-charge eligibility
}
```
**Invariant:** `providerPublicId` and council registration numbers are **resolvable identifiers**, never
authentication credentials (policy `LOGIN-PROVIDERID-DENY`).

---

## C2 — Workforce / Assignment / Check-in / Affiliation (Vashandi)

**Producer:** `vashandi-workforce-service` (assignments + attendance Live; **add** an "active work context"
query — the audit found no "active assignments for actor" endpoint). Org affiliation from
`workforce-governance-service`.
**Consumers:** T1 context picker (WHERE/WHAT), Cadre Engine (`scope`), check-in state.

```ts
interface WorkforceContext {
  workforceProfileId: string;
  impiloHealthId: string;
  activeAssignments: Array<{
    assignmentId: string;
    status: "DRAFT"|"PENDING"|"ACTIVE"|"SUSPENDED"|"ENDED";
    organisationId: string;
    facilityId: string;
    departmentId: string | null;
    unitId: string | null;          // ward / service-point
    workspaceId: string | null;
    programmeId: string | null;
    roleTemplateId: string;
    supervisorProfileId: string | null;
    scope: "FACILITY"|"DEPARTMENT"|"WARD"|"SERVICE_POINT"|"VIRTUAL_POOL"|"ABOVE_SITE";
    eligibilityStatus: string;      // OPA precheck result
  }>;
  checkIn: {
    shiftId: string | null;
    state: "CHECKED_OUT"|"CHECKED_IN";
    at: string | null;              // ISO timestamp
    facilityId: string | null;
    supervisorConfirmed: boolean;
  };
  affiliations: Array<{             // facility<->org temporal links
    organisationId: string;
    relationshipType: string;
    primary: boolean;
    validFrom: string; validTo: string | null;
  }>;
  requiresContextChooser: boolean;  // true when >1 active assignment
}
```
**New endpoint to add:** `GET /v1/internal/vashandi/work-context?actorId={healthId}` → `WorkforceContext`.

---

## C3 — Person / Identity (VITO via tshepo-identity)

**Producer:** `vito-service` (person SoR) + `tshepo-identity-service` (resolution + CPID mapping).
**Consumers:** all lanes. **PII stays in VITO; SHR uses CPID only** (doctrine).

```ts
interface PersonRef {
  healthId: string;                 // canonical
  impiloId: string | null;
  cpid: string | null;             // for SHR/BUTANO; deterministic UUIDv5
  displayName: string | null;      // may be masked per visibility
  provisional: boolean;            // true for emergency unidentified
  assuranceLevel: "VERIFIED"|"UNVERIFIED";
}

// Resolution request (silent chain — the gap to close)
interface IdentifierResolution {
  input: { kind: "HEALTH_ID"|"PHONE"|"EMAIL"|"IMPILO_ID"|"PROVIDER_ID"|"COUNCIL_NUMBER"|"INVITE"; value: string };
  // anti-enumeration: response never reveals existence on failure; uniform timing/shape
  result: { resolved: boolean; personRef?: PersonRef; /* never echo why-not */ };
}
```
**Invariants:** resolution is **silent** (Tshepo→Varapi→Vashandi→VITO→TUSO) and **anti-enumeration**
(uniform failure shape/timing). `PROVIDER_ID`/`COUNCIL_NUMBER` resolve a profile but **never authenticate**.

---

## C4 — Facility / Department / Service-point / Place (TUSO + Indawo)

**Producer:** `tuso-service` (facility) + `indawo-service` (place). This includes the **`FacilityModeContext`**
the Facility-Mode cockpit consumes (see [ownership split](facility-mode-ownership-split.md)).
**Consumers:** T4 cockpit (produces), T1 context picker (reads), OROS routing (facility capability).

```ts
interface FacilityNode {
  facilityId: string;
  tenantId: string;
  code: string; name: string; type: string;
  regulatoryStatus: "DRAFT"|"REGISTERED"|"SUSPENDED"|"REVOKED";
  departments: Array<{ departmentId: string; name: string; serviceLine: string }>;
  servicePoints: Array<{ servicePointId: string; departmentId: string; name: string; queueId: string|null }>;
  organisationLinks: Array<{ organisationId: string; relationshipType: string; primary: boolean }>;
}

interface PlaceNode {                // Indawo public-health place
  siteId: string; tenantId: string;
  type: string; category: string; riskClass: string;
  lifecycleStatus: string; regulatoryStatus: string; operationalStatus: string;
}

interface FacilityModeContext {      // produced by T4 FacilityModeController; read by T1 shell
  facility: FacilityNode;
  setupState: {                      // setup-wizard progress
    departmentsConfigured: boolean; servicePointsConfigured: boolean;
    queuesConfigured: boolean; workflowsConfigured: boolean;
    workforceLinked: boolean; orosRoutingConfigured: boolean;
    khulumaChannelsConfigured: boolean; fundoReady: boolean; goLive: boolean;
  };
  ops: { openEncounters: number; queueDepth: number; bedOccupancy: number|null };  // from control-tower (scoped)
  placeMode: PlaceNode | null;       // present if facility is also an Indawo place
  visibility: "ROW_DETAIL"|"AGGREGATE_ONLY";  // cross-tenant guard (AggregateVisibilityGuard)
}
```
**Invariants:** tenant-scoped (X-Tenant-ID); `AGGREGATE_ONLY` hides row-level PII; **one producer** for
`FacilityModeContext`.

---

## C5 — Fundo Readiness (Fundo)

**Producer:** Fundo learning-service (separate lane `intake/fundo-lms`). Consumed by the **facility setup
wizard** (`fundoReady` gate) and the provider My-Professional tab (CPD).

```ts
interface FundoReadiness {
  subjectId: string;                 // provider or facility
  requiredCompetencies: Array<{ code: string; met: boolean; certifiedAt: string|null }>;
  cpdSummary: { points: number; period: string } | null;
  ready: boolean;                    // all required competencies met
}
```
**Note:** if Fundo isn't ready at build time, the producer is an **honest stub** that returns
`ready:false` + `requiredCompetencies:[]` — never a fake "ready:true".

---

## C6 — Core-Transaction State object (composition over PCT/COSTA/Coverage)

**Producer:** experience-bff composition (over PCT journey, COSTA access decision, Coverage eligibility) —
**derived, not authoritative.** See journey map §1.
**Consumers:** every clinical/patient surface.

```ts
interface CoreTransactionState {
  transactionId: string;             // correlation id across the journey
  // AUTHORITATIVE: this is the `CoreTransactionState` union from contracts/core-transaction.ts
  // (54 states: DRAFT, INITIATED, IDENTITY_PENDING ... READY_FOR_PROVIDER, IN_SERVICE ...
  //  POST_SERVICE_BILLING_PENDING, RECONCILIATION_PENDING, COMPLETED, CLOSED + branch/exception states).
  // Do NOT invent a parallel enum; import it. Use isValidCoreTransactionTransition / getAllowedNextStates.
  state: CoreTransactionState;       // from contracts/core-transaction.ts
  context: "OUTPATIENT"|"INPATIENT"|"CASUALTY"|"PROCEDURE"|"COMMUNITY"|"VIRTUAL";
  personRef: PersonRef;
  encounterId: string | null;        // PCT
  journeyState: string;              // PCT JourneyEntity native state (source of truth for clinical sub-state)
  accessDecision: {                  // COSTA ServiceAccessDecision
    status: "ALLOWED_WITHOUT_PAYMENT"|"PAYMENT_REQUIRED_BEFORE_SERVICE"|"DEPOSIT_REQUIRED"|
            "AUTHORISATION_REQUIRED"|"COVERED_BY_PAYER"|"EXEMPT"|"WAIVER_REQUIRED"|
            "DEFERRED_PAYMENT_ALLOWED"|"BLOCKED_PENDING_PAYMENT"|"BLOCKED_PENDING_AUTHORISATION"|
            "EMERGENCY_OVERRIDE";
    decidedBy: string|null; reason: string|null; auditReference: string|null;
  };
  eligibility: { checked: boolean; eligible: boolean|null; schemeId: string|null };
  outcome: string | null;
  valueEventRefs: string[];          // links to C8 value events
}
```
**Invariant:** clinical sub-state is owned by PCT `JourneyEntity`; this object **maps**, never overrides it.

---

## C7 — Referral / Consult Package (referral-service + PCT)

**Producer:** `referral-service` (`ReferralEntity`, payload JSONB) + PCT telemedicine stage tracking.
**Consumers:** T3 referral builder, telemedicine 7-stage, incoming-referrals worklist.
**Freeze the payload schema** (today it's a freeform `Map` — that's the gap):

```ts
interface ReferralConsultPackage {
  referralId: string; tenantId: string;
  patientRef: PersonRef;
  fromProvider: { providerPublicId: string; facilityId: string };
  toTarget: { kind: "PROVIDER"|"SERVICE"|"POOL"|"ON_CALL"|"FACILITY"; id: string };
  reason: string; clinicalSummary: string;     // auto-generated from encounter
  attachments: Array<{ documentId: string; kind: string }>;  // SHR doc refs, not blobs
  consent: { granted: boolean; consentId: string|null };     // gap: consent modal
  modality: "ASYNC"|"CHAT"|"AUDIO"|"VIDEO"|"SCHEDULED"|"MDT"|"IN_PERSON";
  stage: 1|2|3|4|5|6|7;                          // telemedicine 7-stage
  status: "PENDING"|"ACCEPTED"|"RESPONDED"|"COMPLETED"|"REJECTED";
  routingTarget: object | null;                  // capacity/availability resolved
  response: { diagnosis: string; actionPlan: string; redFlags: string; followUp: string } | null;  // structured (gap)
}
```

---

## C8 — Access / Compensation Value-Event (COSTA + MUSheX + Coverage)

**Producer:** COSTA + MUSheX outboxes (already dual-emit to `core.transaction.events`).
**Consumers:** Lane C surfaces, patient notifications, finance dashboards.

```ts
interface ValueEvent {
  eventId: string; transactionId: string;        // ties to CoreTransactionState
  kind: "CHARGE_CREATED"|"BILL_DRAFT_CREATED"|"BILL_FINALIZED"|"INVOICE_ISSUED"|
        "PAYMENT_INTENT_CREATED"|"PAYMENT_STATUS_CHANGED"|"CLAIM_PACK_CREATED"|
        "CLAIM_ADJUDICATED"|"SETTLEMENT"|"REFUND_CREATED"|"WAIVER_APPLIED"|
        "EMERGENCY_DEFERRED_CHARGE";             // new — for reconciliation
  sourceServiceEvent: string;                    // e.g. "ENCOUNTER_COMPLETED","BEDDAY_ACCRUED"
  amount: { value: number; currency: string } | null;
  payer: { kind: "SELF"|"SCHEME"|"SUBSIDY"|"WAIVER"|"DEFERRED"; id: string|null };
  deferred: boolean;                             // true for EMERGENCY_OVERRIDE charges
  reconciliationState: "N/A"|"PENDING"|"RECONCILED" | null;
}
```
**Invariant:** every billable service event maps to exactly one value event (no leakage / no double-charge);
emergency-deferred charges carry `deferred:true` + `reconciliationState` for the new reconcile endpoint.

---

## C9 — Cadre Engine Decision (PCT — NEW)

**Producer:** `pct-service` Cadre Engine (net-new; PCT V015). **Consumers:** Encounter Cockpit (adaptive
spine), every clinical action. Consumes Tshepo authz decisions; **does not author policy**.

```ts
interface CadreDecisionRequest {
  role: string; cadre: string;                   // from C1
  scope: WorkforceContext["activeAssignments"][number]["scope"];  // from C2
  visitType: string;                             // from Sorting Desk (new)
  acuity: 1|2|3|4|5;                             // from PCT TriageRecord
  context: CoreTransactionState["context"];
  accessState: CoreTransactionState["accessDecision"]["status"];
}

interface CadreDecision {
  permittedWorkflows: string[];                  // e.g. ["ASSESS","ORDER_LAB","REFER","DISCHARGE"]
  cockpitSpine: Array<{                           // adaptive tabs/actions
    tab: "OVERVIEW"|"ASSESSMENT"|"PROBLEMS"|"ORDERS_RESULTS"|"CARE"|"CONSULTS_REFERRALS"|"NOTES"|"VISIT_OUTCOME";
    enabled: boolean;
    actions: Array<{ action: string; enabled: boolean; requiresStepUp: boolean }>;
  }>;
  escalation: { breakGlassAvailable: boolean; supervisorRequiredFor: string[] };
  auditRef: string;                              // every resolution is audited
}
```
**Invariant:** the cockpit renders strictly from `cockpitSpine`; a disabled action is not shown as a dead
button (no fake completions). Authz-gated actions still call the existing Tshepo ext_authz path at execution.

---

## Cross-contract rules

1. **Single producer per read-model** (table above). No lane assembles another lane's read-model.
2. **All shapes carry tenant scope** implicitly via trust headers; cross-tenant reads obey
   `AggregateVisibilityGuard`.
3. **No new SoR.** Every field traces to an existing entity (or a named net-new build in one owning SoR).
4. **BFF persists nothing** — it composes these from sovereign services.
5. **Changes to a frozen shape** require updating this doc + notifying every consumer lane before build.
