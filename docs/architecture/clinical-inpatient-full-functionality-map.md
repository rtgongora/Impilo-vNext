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
| Inpatient chart opening | `pct-service`/document/forms | encounter + notes/forms routes | partial | dedicated inpatient chart aggregate missing |
| Daily ward rounds | `pct-service` route family today | BFF `/internal/v1/ward-rounds*` -> PCT; not yet wired to inpatient service | partial | inpatient-service ward-round API not yet canonical in BFF |
| Nursing care plans | forms + guidance + PCT coordination | care-plan and forms routes | partial | no dedicated nursing-plan aggregate service contract |
| Charting/observations | PCT + forms/document | `/internal/v1/observations`, clinical-notes routes | implemented (bounded) | cross-service normalization depth pending |
| Medication administration support | pharmacy + inpatient context | prescriptions/dispense + inpatient references | partial | MAR-specific workflow contract not yet explicit |
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
