# PCT Care Tracker — Clinical Journey Capability Map & Gap Report

**Workstream**: WS-P3 (Fable Seven Pipeline Parallel Delivery Board — P3 PCT Patient Care Tracker)
**Branch**: `cursor/e2e-pct-care-tracker` (base: `claude/web-session-anchor-nnnkf6` @ `98d43b1cd`)
**Date**: 2026-07-05
**Status**: Repo-grounded. Every claim below cites the actual implementation.

---

## 1. Capability map (verified 2026-07-05)

Legend: ✅ real and wired · 🟡 partially wired · 🔶 honest seam/stub · ❌ missing · 🔴 blocked/serialized

### Encounter spine
- ✅ Journey state machine (`pct JourneyStateMachine`, 14 states, guarded transitions, outbox
  `JOURNEY_CREATED` / `JOURNEY_STATE_CHANGED`).
- ✅ Encounter lifecycle (`EncounterService`: STARTED/ON_HOLD/COMPLETED, single-active-encounter
  enforcement, shift link V029, outbox events). `holdEncounter` has no REST endpoint (service-only).
- ✅ Cadre Engine (`core/cadre/CadreEngine`): deterministic families, permitted workflow sets,
  PRESCRIBE action exists with `requiresStepUp=true`, nurse-disabled with supervisor escalation;
  break-glass for acuity ≤ 2; decisions audited (`pct_cadre_decisions`).
- ✅ Structured forms (`FormResponseService`): DRAFT→IN_PROGRESS→SUBMITTED→AMENDED/VOIDED,
  AUTHOR + COUNTERSIGN signatures (`pct_form_signature`), deferred extraction pending countersign,
  amendment audit rows.
- 🟡 Form `sensitivity` (STANDARD/SENSITIVE/RESTRICTED) is carried on the catalog but **not
  enforced** in `FormResponseService` / `FormScopeEngine` — no production read of `.sensitivity()`.
  (Gap, documented; not remediated in this workstream.)

### Orders
- ✅ Lab/imaging orders from the encounter cockpit via typed BFF `/internal/v1/lab-orders` → OROS
  (`EncounterLabOrdersPanel`, `EncounterImagingOrdersPanel`); OROS full order state machine.
- ✅ Form-extraction orders: `SERVICE_REQUEST` mappings → `OrosIntegration.submitOrder`, honest
  ROUTED/FAILED provenance rows (`pct_form_extracted_resource`).
- ✅ **NEW (this workstream)**: `MEDICATION_REQUEST` extraction type — the PRESCRIBE seam (see §3).
- 🔴 OROS `/v1/orders` contract divergence — serialized R1, **not fixed here** (see §4).
- ❌ Ward-round structured orders (see §5).
- ❌ FAILED extraction rows are never retried (V024 comments mention retry; no retry job exists).

### Inpatient / procedures / discharge
- ✅ Admission with idempotent PCT handshake (`AdmissionService.admitFromPctApproval`, V013/V014).
- ✅ Procedures: episode pipeline BOOKED→…→COMPLETED, WHO checklist, MVUMO consent gate
  (`GRANTED` + evidence required before theatre start), anaesthesia scoring, theatre OROS orders.
- ✅ Discharge clearances: 9 types seeded, CLEARED/WAIVED gate on finalise.
- ✅ **NEW (this workstream)**: discharge-summary countersign gate (see §2). Previously the only
  finalise gates were summary existence + clearance completion; there was **zero** countersign
  infrastructure anywhere in inpatient-service.
- 🔶 `inpatient.discharge.followup_requested` remains an unconsumed contract event (also noted by
  WS-P2-A). Khuluma follow-up is requested, never sent.

### Experience layer
- ✅ Web encounter cockpit: `/ehr/[patientId]/encounter/[encounterId]` (one-ui-shell) with CDS
  alerts, journey context, orchestration/care-chain rails, lab/imaging/linked-studies panels,
  structured forms panel, discharge panel.
- ✅ **NEW (this workstream)**: medications/pharmacy status panel; forms countersign action;
  discharge-board countersign UI (see §2–§3).
- ✅ `ui/pct-web` ops sidecar (work session, sorting, queues, control tower) — queue surfaces are
  WS-P2 territory; untouched here.
- 🟡 `AdaptiveEncounterCockpit` (cadre-driven tab spine) exists and is mounted on mobile but NOT
  on the web encounter page (deliberate: mounting it re-architects the page layout — deferred,
  needs UX decision, not a mechanical wiring task).
- 🟡 Billing in clinical view: link-out badge only (`costa_bill_id`); BillingPanel lives in
  finance/workspace-ops. P1-owned (WS-P1-B) — not duplicated here.

---

## 2. Discharge summary countersign gate (DELIVERED)

**Gap closed**: "No countersign gate on discharge summaries" (board P3 finding).

Semantics mirror PCT form-level countersignature:

- `inpatient.discharge_summary` gains `countersign_required`, `authored_by`, `countersigned_by`,
  `countersigned_at`, `countersign_attestation` (**additive** migration `V019`, default FALSE —
  zero behaviour change for existing rows/deployments).
- Requirement is **policy-driven and configurable**, not a hard-coded cadre split: declared on the
  draft payload (`countersignRequired`) or defaulted from
  `impilo.inpatient.discharge-summary.countersign-required-default` (default `false`). Once
  required, sticky — a later draft save cannot silently drop it.
- Countersign is single-shot, must be a **different actor** than the draft author, and is
  auditable (`inpatient.discharge.summary_countersigned` outbox event).
- Editing the draft **invalidates** an existing countersignature
  (`inpatient.discharge.summary_countersign_invalidated`) — a signature attests specific content.
- **Finalise gate**: a summary with `countersign_required=true` and no current countersignature
  409s. It can never be marked FINAL without the required governance.
- Endpoints: inpatient `POST /internal/v1/discharge-summary/{encounterId}/countersign`; BFF
  `POST /internal/v1/inpatient/discharge-summary/{encounterId}/countersign`.
- UI: discharge board (`/clinical/inpatient/discharge-board`) shows countersign status, offers the
  countersign action (attestation input), and disables finalise with an honest label
  ("Countersignature required before finalise") while the signature is missing.

Tests: `DischargeSummaryServiceTest` (8 new cases: gate, unblock, self-sign rejection,
not-required rejection, double-sign rejection, finalised rejection, invalidation on edit,
sticky/config default), BFF `InpatientControllerTest`, discharge-board vitest (2 new cases).

## 3. Prescribing seam (DELIVERED — via existing safe seam, no new integration)

**Gap**: "Prescribing is completely absent from the PCT encounter cockpit; prescriptions live only
in pharmacy-service via OROS."

What was true before this workstream: the Cadre Engine surfaces a PRESCRIBE action (step-up
gated), the form catalog supports `requiredWorkflow=PRESCRIBE` + countersign, but **nothing wired
a prescribe intent to an order**. pharmacy-service consumes OROS `oros.order.placed` (PHARMACY
order type) to create dispense orders, and exposes `/v1/prescriptions` (BFF
`/internal/v1/pharmacy/*`).

Delivered, strictly through existing seams:

1. **`MEDICATION_REQUEST` extraction type** in `pct FormExtractionService`: a PRESCRIBE form
   mapping routes the answer (plain code or structured `{drug, code, dose, route, frequency,
   duration, quantity, instructions}`) to OROS through the **same** `OrosIntegration.submitOrder`
   wire call (`{journeyId, payload}` — shape untouched; R1 stays serialized). Provenance rows are
   honest (ROUTED with the OROS order id / FAILED when OROS is down — never fake success), and a
   `pct.form.medication.requested` outbox event gives the auditable prescribing trace (who, what,
   which encounter).
2. **Governance rides the existing form machinery**: cadre/scope gating via FormScopeEngine;
   where the catalog requires countersign, the medication order is **deferred until
   countersignature** (verified by test). Prescribing permission therefore stays
   competence/training-configurable (form catalog + cadre config), per Zimbabwe prescribing
   doctrine — no brittle medication-by-cadre allowlist was introduced.
3. **Cockpit visibility**: new `EncounterMedicationsPanel` on the web encounter page — read-only
   medication/pharmacy status from the EXISTING BFF `GET /internal/v1/pharmacy/prescriptions`
   endpoint, with honest empty/unreachable states and an explicit explanation that cockpit
   prescribing routes through structured PRESCRIBE forms. No new BFF endpoint, no pct↔pharmacy
   direct integration invented.
4. **Countersign action wired in UI**: `useCountersignFormResponse` existed but was dead code —
   `EncounterFormsPanel` now offers a countersign action (attestation input) for submitted
   countersign-required responses, with server rejections shown honestly.

**Still open (handoff)**: a full prescribing cockpit (drug catalog search, dose decision support,
interaction checking, Dura stock availability at prescribe time, COSTA billability) requires
product/policy work and OROS→pharmacy id round-tripping. The seam is now real and auditable; the
rich cockpit is a follow-on workstream.

## 4. OROS `/v1/orders` contract divergence (R1 — DOCUMENTED, NOT FIXED)

Finding sharpened during this workstream: the divergence is worse than "two shapes".

- `pct OrosIntegration.submitOrder` posts `{journeyId, payload}` (`OrosIntegration.java:54`).
- `inpatient OrosOrderClient.placeOrder` posts `{orderType, priority, patientCpid, encounterRef,
  clinicalNotes, items[]}` (`OrosOrderClient.java:50-56`).
- OROS `PlaceOrderRequest` requires `orderType` (`@NotNull`) and `patientCpid` (`@NotBlank`)
  (`PlaceOrderRequest.java:21-35`).

**Consequence**: the PCT shape fails OROS bean validation against a real OROS — every PCT
form-extracted order (lab and now medication) degrades to a FAILED provenance row. The inpatient
shape is the compliant one. Unifying means changing pct-service's wire call and possibly adding
journey provenance to OROS — cross-service contract work, serialized as **R1** with the Fable
coordinator; explicitly out of lease for this workstream.

**Pinned by test**: `pct OrosIntegrationContractTest` documents the exact divergent shape and the
graceful-degradation behaviour. If the shape changes without going through R1, the pin fails.

## 5. Ward-round free-text orders (DOCUMENTED, DEFERRED)

`inpatient AddWardRoundEntryRequest.newOrders` / `.escalation` are free-text columns
(`V005__ward_rounds.sql:25-26`); `WardRoundService` persists them with **no events, no OROS
routing, no task creation**. They are honest free-text round notes today.

Deferred because structured ward-round orders would either (a) call `OrosOrderClient` with the
compliant shape while PCT extraction uses the divergent one — deepening the R1 split — or
(b) require the R1 unification first. Recommendation: after R1, route ward-round order intents
through the unified order client and emit a `inpatient.ward_round.order_requested` event.
No UI change was made that presents these fields as structured orders.

## 6. BUTANO PENDING extractions (DOCUMENTED, DEFERRED)

`FormExtractionService.extractObservation` records OBSERVATION/PROCEDURE provenance as `PENDING`
and emits `pct.form.observation.extracted` for the SHR bridge. No consumer writes these to BUTANO
yet, and no retry job exists for `FAILED` rows (repository method `findByStatusIn` exists,
unused). This is event-only by design ("deferred write") but the bridge consumer is missing —
flagged for the data/SHR pipeline owner. Not remediated here (cross-service consumer work).

## 7. Journey status vs the 12 target journeys

| Journey | Status |
|---|---|
| J1 Outpatient walk-in | ✅ pre-existing (queue WS-P2 + encounter + forms + lab orders + closure); billing link-out only (P1) |
| J2 Emergency/triage | ✅ pre-existing (EdTriageDiscriminatorEngine, ED visit flow); untouched |
| J3 Community/outreach | 🟡 community cadre + forms exist; Dura CHW stock flow is P4-owned; untouched |
| J4 Imaging request | ✅ pre-existing (imaging orders panel, `ImagingLinkService`, PACS viewer — P6-owned) |
| J5 Lab request | ✅ pre-existing (BFF lab-orders → OROS); form-extraction path pinned re R1 |
| J6 Prescribing/pharmacy handoff | ✅ **delivered this workstream** (seam + visibility + honest gaps, §3) |
| J7 Inpatient admission/ward | ✅ pre-existing (handshake, beds, MAR/EWS); ward-round orders stay free-text (§5) |
| J8 Procedure | ✅ pre-existing (WHO checklist, MVUMO gate, anaesthesia, theatre OROS) |
| J9 Discharge with countersign | ✅ **delivered this workstream** (§2) |
| J10 Physical→telemedicine escalation | 🔴 W0-leased; not touched |
| J11 Billing visibility | 🟡 P1-owned (WS-P1-B EHR billing panel); not duplicated here |
| J12 Sensitive/restricted encounter | 🟡 catalog sensitivity not enforced (§1) — gap logged, needs policy design |

## 8. Cross-pipeline handoffs raised

1. **R1 (Fable coordinator)**: PCT wire shape fails OROS validation — R1 is not cosmetic; PCT
   form-extracted orders cannot reach a real OROS until unified. Pinned by
   `OrosIntegrationContractTest`.
2. **Data/SHR owner**: BUTANO observation bridge consumer missing (§6).
3. **P1 finance**: encounter cockpit still lacks an inline billing panel (WS-P1-B scope).
4. **Forms/policy owner**: form `sensitivity` is unenforced metadata (§1 / J12).
5. **W0**: no telemedicine-side changes made; encounter→teleconsult linkage untouched.
