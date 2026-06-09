# Clinical Inpatient Full Functionality Map

## Boundary

- PCT: admission decision/disposition linkage and journey coordination.
- Inpatient service: admission, bed/ward assignment, transfer, inpatient discharge state.
- OROS/pharmacy/document/forms: specialist clinical domain ownership during stay.

## End-to-End Inpatient Journey

| Stage | Primary owner | API/BFF/UI surface | Status | Blocker |
|---|---|---|---|---|
| Admission decision in encounter | `pct-service` | encounter/discharge/admit orchestration (`/internal/v1/encounters/*`, `/v1/journeys/*`) | partial | encounter-to-admission automation is bounded, not universal |
| Inpatient admission create | `inpatient-service` | BFF `/internal/v1/admissions` -> inpatient `/internal/v1/admissions` | implemented | none |
| Bed/ward allocation | `inpatient-service` + `tuso-service` context | admission create/transfer payload (`wardId`, `bedId`) | implemented (bounded) | bed occupancy/constraint engine depth partial |
| Admission handover/check-in | `inpatient-service` + PCT linkage | admission create + PCT journey/admission refs | partial | unified handover artifact contract pending |
| Inpatient chart opening | `inpatient-service` | BFF `/internal/v1/ward-charts/*` + EHR charts routes | implemented (bounded) | cross-chart aggregation depth pending |
| Daily ward rounds | `inpatient-service` | BFF `/internal/v1/ward-rounds*` -> inpatient sovereign API | implemented | none |
| Nursing care plans | `inpatient-service` | BFF `/internal/v1/care-plans*` -> `inpatient.care_plan` | implemented (bounded) | goal/intervention perform audit depth pending |
| Charting/observations | `inpatient-service` | `/internal/v1/observations`, `/ward-charts/{type}/entries` | implemented (bounded) | FHIR Observation normalization optional |
| Medication administration support | `inpatient-service` MAR | BFF `/internal/v1/mar*`, mobile `/clinical/mar` | implemented (bounded) | pharmacy prescription linkage depth pending |
| Shift handover/takeover | `inpatient-service` | BFF `/internal/v1/inpatient/handover*` | implemented (bounded) | TUSO shift linkage optional |
| Citizen ward alert | `inpatient-service` + notification | `/internal/v1/mobile/citizen/inpatient/ward-alert` | implemented (bounded) | ward staff inbox surfacing in provider app pending |
| Orders/results during admission | OROS | orders/results surfaces and OROS APIs | implemented (bounded) | longitudinal admission-level result bundle view partial |
| Inpatient transfer | `inpatient-service` | `POST /internal/v1/admissions/{ref}/transfer` | implemented | transfer accept workflow in BFF is intentionally `501` |
| Discharge planning | PCT + inpatient + document/forms | discharge workflow + notes/forms | partial | full discharge-plan artifact model pending |
| Final inpatient discharge/check-out | `inpatient-service` | `POST /internal/v1/admissions/{ref}/discharge` | implemented | no synthetic success; fail-close enforced |
| Follow-up/review linkage | PCT/queue/scheduling | encounter disposition + booking pathways | partial | scheduling linkage depth varies by context |

## Bounded Enhancements in This Pass

- Reinforced encounter coordination for inpatient/procedure contexts through explicit encounter context support.
- Kept ownership clear: no duplication of inpatient admission/transfer/discharge logic in PCT.
- Documented explicit `501` gaps in BFF where wiring to inpatient canonical APIs is not complete (ward-round start/entries, transfer accept).

## Explicit Non-Faked Gaps

- Full nursing care-plan lifecycle remains partial.
- Ward-round authoring is not fully owned by inpatient-service in BFF path yet.
- Medication administration workflow depth (MAR) is not complete.
