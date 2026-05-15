# Clinical Encounter Mastery Map

## Contexts and Entry Points

- Contexts: outpatient, emergency/casualty, inpatient, community, virtual, procedure, procedure_room, operating_room
- Entry points: scheduled_appointment, walk_in, emergency_arrival, referral, community_outreach, virtual_request, inpatient_admission, transfer

## Lifecycle Coverage Snapshot

| Lifecycle stage | Primary owner(s) | Current status |
|---|---|---|
| demand/booking | scheduling/queue services + BFF | partial |
| arrival/check-in | PCT + queue services + VITO/TUSO | partial |
| intake/triage | PCT + forms/rules/guidance | partial |
| encounter start/context | PCT | implemented (bounded) |
| assessment + CDS | guidance/rules/clinical-knowledge/forms + PCT refs | partial |
| orders/results | OROS | implemented (bounded) |
| treatment/medication | pharmacy + PCT coordination | partial |
| disposition (admit/discharge/transfer/referral) | PCT + inpatient + queue/scheduling | partial |
| inpatient journey | inpatient-service (+ PCT coordination) | partial |
| procedure/OR context | PCT context + specialist service references | partial |
| imaging/PACS workflow | pacs-adapter + OROS + BFF imaging governance | implemented/partial |
| follow-up/review | scheduling/queue + PCT | partial |

## This Pass Delta

- Added explicit procedure contexts in PCT encounter model validation.
- Added PCT endpoint to update encounter pathway/protocol linkage.
- Added BFF and UI wiring for pathway/protocol linkage updates.
- Added deep capability maps for pathways/CDS, inpatient, procedure/OR, and PACS/DICOM.
# Clinical Encounter Mastery Map

## Encounter Contexts and Entry Points

Encounter contexts modeled for vNext:

- outpatient
- emergency/casualty
- inpatient
- community
- virtual

Entry points accounted for:

- appointment/booking (`scheduled_appointment`)
- walk-in (`walk_in`)
- emergency arrival (`emergency_arrival`)
- referral (`referral`)
- community outreach (`community_outreach`)
- virtual consult request (`virtual_request`)
- inpatient admission (`inpatient_admission`)
- transfer (`transfer`)

## Lifecycle Map

| Lifecycle stage | Outpatient | Emergency/casualty | Inpatient | Community | Virtual | Lovable coverage reference | Primary owner | APIs/routes involved | UI/BFF routes involved | Status | Tests | Blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Demand/appointment creation | yes | partial | follow-up booking | partial | yes | prototype route/api indices + golden path references | `tuso-service` + BFF | `/internal/v1/appointments*` | scheduling pages | implemented | BFF tests + UI tests | none |
| Arrival/check-in | yes | yes | admission path | partial | n/a | queue/encounter flow | `pct-service` + queue | `/v1/journeys/start`, queue APIs | `/queue/*` | partial | PCT integration tests | fallback queue behavior still present in BFF |
| Intake | yes | rapid intake | admission intake | partial | intake-lite | triage/intake prototype patterns | `pct-service` + forms | journey/triage/forms APIs | queue triage + encounter form | partial | triage/unit tests | community intake workflow sparse |
| Triage/sorting | where applicable | critical | transfer triage | outreach triage partial | virtual triage optional | triage panel pattern | `pct-service` | `/v1/journeys/{id}/triage` | `/queue/triage`, encounter page triage | implemented | `JourneyStateMachineTest`, UI triage tests | none |
| Queue/service point | yes | yes | ward/worklist routing partial | partial | virtual worklist | queue workboard | `pct-service` | queue/queue-item APIs | `/queue`, `/queue/walk-in`, `/queue/incoming-referrals` | partial | `QueueEngineTest`, BFF queue tests | remove synthetic fallback paths |
| Encounter start | yes | yes | yes | partial | yes | encounter page + path C | `pct-service` | `POST /v1/journeys/{id}/encounter/start` | `/internal/v1/encounters` + mobile encounters | implemented | encounter controller/service tests | none |
| Assessment | yes | yes | daily review partial | partial | yes | encounter workspace patterns | `pct-service` + forms/guidance/rules | notes/forms/guidance/rules APIs | encounter detail + clinical tools | partial | UI encounter tests | standardized context-to-protocol invocation missing |
| Orders/results | yes | urgent | yes | referral-linked | yes | orders/results presentation | `oros-service` | `/v1/orders*`, `/v1/orders/{id}/results*` | `/ehr/[patientId]/orders`, `/results` | implemented | OROS module tests | none |
| Treatment/care plan | yes | yes | yes | yes | yes | care-management patterns | `pharmacy-service`, `pct-service`, `inpatient-service` | pharmacy APIs + care-plan APIs | meds/pharmacy/care-plan pages | partial | pharmacy + pct tests | inpatient med-admin cross-boundary clarity needed |
| Disposition/outcome | discharge/admit/refer | admit/transfer/discharge/death | transfer/discharge | facility referral/follow-up | follow-up/closure | outcome/discharge workflows | `pct-service` + `inpatient-service` + BFF | discharge/admission/transfer/referral APIs | discharge page + encounter close + consults | partial | discharge workflow tests | follow-up action contract depth incomplete |
| Inpatient care | n/a | admission onward | yes | n/a | n/a | inpatient concepts | `inpatient-service` | admissions/transfer/discharge APIs | inpatient/bed pages via BFF | partial | inpatient module tests | ward-round and transfer accept not wired in BFF |
| Follow-up/review | yes | yes | yes | yes | yes | follow-up concepts | `tuso-service` + `pct-service` + BFF | appointments + encounter/discharge APIs | scheduling + discharge + consults | partial | scheduling tests | explicit follow-up linkage payload incomplete |

## Ownership Summary

- `pct-service`: encounter context, journey state, modality, referral linkage, disposition.
- `oros-service`: orders/results and critical result lifecycle.
- `pharmacy-service`: prescriptions/dispense and medication fulfilment.
- `inpatient-service`: admissions/bed/transfers/inpatient discharge.
- `document-service`: attachments and object references.
- `forms-service`, `guidance-service`, `rules-service`, `clinical-knowledge-platform-service`: forms, protocols, guidance, decision support, pathways.
- `mvumo-service`: remote consent workflow/evidence capture.
- `tshepo-*`: authorization, policy/consent decisioning, audit governance.
- `vito`/`varapi`/`tuso`/`zibo`: patient/provider/facility/terminology references.
- `butano-service`/`butano-fhir`/`fhir-gateway-service`: SHR/FHIR boundary.
- `experience-bff`: orchestration, typed error envelopes, context propagation.

## Current Mastery Verdict

- Encounter lifecycle coverage is **broad and functional for controlled baseline**, with explicit bounded gaps.
- Critical non-faked blockers remain explicit:
  - realtime virtual transport (chat/audio/video signaling/media)
  - full on-call/team/pool routing backend
  - complete removal of synthetic fallback behavior in all queue-related BFF paths
  - encounter-driven follow-up linkage contract depth
