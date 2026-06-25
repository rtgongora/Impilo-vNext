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

## Remaining gaps / next hardening (honest)
1. **UI surfaces not yet built** beyond the investigations consolidation: dedicated lab worklist +
   specimen collection/dispatch/receipt screens, procedure worklist screen, lab order form with
   specimen/panel/fasting fields, admin catalogue editor pages, and provider-mobile parity for the
   new lab/procedure/specimen flows. Backends + BFF + hooks exist; screens are the residual.
2. **HL7/FHIR interop mappers** still imaging/lab-shaped; generalising the FHIR Observation/Specimen/
   Task mappers for the full lab payload is deferred (adapters remain OFF/honest).
3. **MADI ops maturity**: not yet in `docker-compose.yml`; blood-order SLA timers and donor-deferral
   preflight not added (data model supports them).
4. **Event-driven path**: OROS↔MADI uses REST callbacks; a Kafka consumer path (madi.* → OROS) is a
   resilience upgrade over the current best-effort REST.
5. Ratchet the product-truth baseline down (currently 6; actual 4) once UI residuals land.

## Doctrine compliance
One OROS order spine; coarse `OrderStatus` untouched; per-category guards on a single
`workflow_state`; never overwrite a final result (versioned chain); critical results escalate; no
faked integrations (configured/not-configured surfaced); BLOOD_BANK fulfilled by sovereign MADI,
OROS owns the order. SoR-first verified against the registry throughout.
