# OROS Orders, Investigations, Diagnostics & Results — Completion Wave: Final Report

**Status: DELIVERED** (waves O1–O10). Generalises the imaging-only diagnostics vertical into a
unified order/result spine across imaging, laboratory, procedures/assessments, blood bank, and
external/paper/QR intake — encounter-first, closed-loop, under one OROS order spine with
per-category extensions. No parallel order engine was created.

## Branch & HEAD
- Branch: `intake/oros-diagnostics-journey`
- HEAD: `fd650cc3053119f45adc12b474206aaa3b6e608e`
- Cadence: one atomic Conventional Commit per slice, each `mvn -o test`-green and pushed; synced 0/0.

## Waves & commits
| Wave | Commit | Summary |
|---|---|---|
| O1 | `ef2758463` | Generic fulfilment workflow spine: `workflow_state` column (V011), `FulfilmentWorkflow` guard interface + `WorkflowGuardRegistry`, `LabWorkflow`/`ProcedureWorkflow`, `FulfilmentWorkflowService`; imaging lockstep preserved |
| O2a | `1d27c567c` | Lab specimen lifecycle (V012 `oros_specimens`, `SpecimenService`/Controller) + report-state sync generalised to all categories |
| O2b | `6e1a33f93` | Structured lab observations (V013 `oros_result_observations`) + per-analyte critical entry (`LabResultService`/Controller) |
| O3 | `e90de1f3a` | Procedure/assessment journey: generic transition + schedule + category worklist (`FulfilmentWorkflowController`) |
| O4 | `5e612291b` | Encounter-first consolidation: fixed `LabOrdersController` ID-mismatch onto the real OROS id; `GET /v1/orders?encounter=`; de-stubbed collect |
| O5a | `6cd9e7af4` | `OrderType.BLOOD_BANK` + OROS blood-order callback sink (`BloodOrderCallbackController`) |
| O5b | `5d47be10a` | MADI bidirectional OROS loop (crossmatch callback), `encounter_ref`+`oros_integration_status` (V006), registry port 8300 |
| O5c | `e7b971473` | MADI returns transfusion outcome to OROS on completion |
| O6 | `716163e39` | Codified external/paper/QR intake generalises to all categories (already generic) |
| O7 | `3549218aa` | BFF exposes structured observations, specimens & order results for the patient file |
| O8 | `98a5fafdd` | Consolidated Investigations UI: generalised lifecycle + expandable structured lab observations |
| O9 | `fd650cc30` | Service Catalogue & Fulfilment Directory (admin + clinician read) |
| O11 | (lab UI) | Lab worklist + specimen collect/dispatch/receive/reject screens; BFF fulfilment-worklist / workflow-transition / specimen proxies |
| O12 | (proc UI) | Procedure worklist screen; lab order-form specimen-type + fasting fields |
| O13 | (admin UI) | `/admin/diagnostics-catalogue` editor (service catalogue / orderables / specimen config) + BFF catalogue proxies |
| O14 | (mobile) | Provider-app lab/procedure/specimen parity (worklist, collect [offline-queued], dispatch/receive/reject, transition, critical-ack) |
| O15 | (fhir) | FHIR Observation writeback for structured lab results (value/unit/refRange/interpretation) |
| O16 | (ops) | MADI local DB seed; donor-deferral pre-flight at donation collection |
| O17 | (hl7) | ORU-outbound emits one OBX per structured observation (flag-gated OFF) |
| O18 | (sla) | MADI blood-order SLA timers (V007) + scheduled breach detection |
| O19 | (kafka) | Event-driven OROS↔MADI: OROS consumes madi.blood.order/transfusion (idempotent) |

## Services changed
oros-service (spine, lab, procedure, blood callbacks, catalogue), madi-service (bidirectional
OROS loop, encounter link), experience-bff (encounter ordering fix, observations/specimens/results
proxies), one-ui-shell (investigations tab + hooks). Registry: `madi-service` port 8300.

## Migrations
- oros: **V011** `workflow_state`, **V012** `oros_specimens`, **V013** `oros_result_observations`.
- madi: **V006** `blood_orders.encounter_ref` + `oros_integration_status`.

## APIs (new/changed)
- OROS: `POST /v1/orders/{id}/specimens` + `GET`; `POST /v1/specimens/{id}/{label,dispatch,receive,reject,recollect}`;
  `POST /v1/orders/{id}/lab-results` + `GET /v1/results/{resultId}/observations`;
  `POST /v1/orders/{id}/workflow/{transition,schedule}` + `GET …/workflow/allowed`;
  `GET /v1/fulfilment/worklist?type=&states=`; `GET /v1/orders?encounter=`;
  `/internal/v1/orders/blood/{submitted,issued,crossmatch-result,transfusion-outcome}`;
  `GET/PUT /v1/admin/{service-catalogue,orderable-catalogue,specimen-config}`;
  `GET /v1/catalogue/{services,orderables,specimen-config,destinations}`.
- BFF: `GET /internal/v1/diagnostics/orders/{id}/results`, `/results/{resultId}/observations`,
  `/orders/{id}/specimens`; lab-orders now return/operate on the real OROS id; `?encounter_id=`.
- MADI: `OrosIntegration.notifyCrossmatchResult` / `notifyTransfusionOutcome`.

## Tests / gates
- oros-service **145**, madi-service **25**, experience-bff **539** (LabOrders 5, DiagnosticsExperience 8),
  one-ui-shell vitest green (EHR 49 + investigations), `tsc --noEmit` clean.
- Guard gates: product-truth **PASS** (Services 92 | Gaps 4 ≤ baseline 6, blockers 0);
  backend-frontend parity **PASS**; route-inventory **PASS**; mocks/stubs **PASS** (1 unrelated
  legacy `landela` warning, non-blocking). Product Truth dataset regenerated.

## Acceptance journeys (spec §20)
- A encounter lab → O1/O2/O4 ✅ · B encounter imaging → existing+O4 ✅ · C encounter procedure → O3/O4 ✅
- D external paper → O6 ✅ · E QR claim → O6 ✅ · F critical lab/imaging → O2b + existing ✅
- G amend/correct → O2 versioning ✅ · H reconciliation across categories → existing + O1 generic states ✅

## Honest integration status
- FHIR ServiceRequest-inbound / ImagingStudy + DiagnosticReport-outbound, HL7 v2 ORM/ORU,
  DICOM MWL: **built, flag-gated OFF** (prior epic) — `/admin/integrations` reports honestly.
- OROS ↔ MADI: **live, bidirectional** (REST callbacks both directions; best-effort, non-blocking).
- MADI → BUTANO (transfusion verify) and MADI → NHUME (adverse-event): pre-existing, intact.

## UI / mobile / ops residuals — now CLOSED (waves O11–O16)
- Lab worklist + specimen collect/dispatch/receive/reject screens (O11); procedure worklist +
  lab order-form specimen/fasting fields (O12); admin catalogue editor (O13); provider-mobile
  lab/procedure/specimen parity (O14) — all with BFF proxies, hooks, routes, and tests.
- FHIR Observation writeback for structured lab results (O15).
- MADI local DB seed + donor-deferral pre-flight at donation collection (O16).

## Former residuals — now CLOSED (waves O17–O19)
- **O17 — HL7 v2 ORU per-observation OBX**: `Hl7OruMapper` emits one OBX per structured observation
  (value type / units / reference range / abnormal flag), threaded through `ReportService.createFinal`
  and `LabResultService`; falls back to the summary OBX when none. Still flag-gated OFF.
- **O18 — MADI blood-order SLA timers**: `blood_order_sla` (V007) + `BloodOrderSlaService` with
  start/complete at each stage (crossmatch/issue) and a `@Scheduled` breach scan emitting
  `SLA_BREACHED`; configurable targets; breach count for the dashboard.
- **O19 — event-driven OROS↔MADI**: `MadiBloodEventConsumer` consumes `madi.blood.order` /
  `madi.transfusion` and applies them via `BloodOrderCallbackService` (idempotent, synthetic system
  trust context) — a resilient alternative to the REST callbacks. MADI events enriched to carry
  `orosOrderRef`.

## Remaining gaps / next hardening (honest)
1. Ratchet the product-truth baseline down (currently 6; actual 4).
2. End-to-end live verification against self-hosted counterparties (hapi-fhir / dcm4chee / HL7 MLLP)
   per the interop runbook — the adapters are unit-tested and flag-gated OFF; a live soak is the
   remaining confidence step.
3. MADI is intentionally **not** in `docker-compose.yml` (infrastructure-only by repo convention;
   Spring services run bare-metal per `port-allocation.md`) — local provisioning is the `madi` DB
   seed (O16).

## Doctrine compliance
One OROS order spine; coarse `OrderStatus` untouched; per-category guards on a single
`workflow_state`; never overwrite a final result (versioned chain); critical results escalate; no
faked integrations (configured/not-configured surfaced); BLOOD_BANK fulfilled by sovereign MADI,
OROS owns the order. SoR-first verified against the registry throughout.
