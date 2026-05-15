# Clinical Encounter Journey State Machine

## Canonical Journey States (PCT)

Current canonical PCT journey states:

- `REGISTRATION_PENDING`
- `ARRIVED`
- `TRIAGED`
- `QUEUED`
- `IN_SERVICE`
- `ADMITTED`
- `TRANSFERRED`
- `DISCHARGE_PENDING`
- terminal: `DISCHARGED`, `DEATH_RECORDED`, `CANCELLED`, `NO_SHOW`, `LEFT_WITHOUT_BEING_SEEN`

## Contextual Mapping

| Canonical lifecycle intent | PCT state mapping |
|---|---|
| planned | `REGISTRATION_PENDING` / pre-journey scheduling context |
| arrived / checked_in | `ARRIVED` |
| intake / triaged | `TRIAGED` |
| queued | `QUEUED` |
| in_progress | `IN_SERVICE` |
| admit_pending / admitted | admission workflow + `ADMITTED` |
| inpatient_active | `ADMITTED` and transfer cycles |
| discharge_planning | `DISCHARGE_PENDING` |
| discharged | `DISCHARGED` |
| transferred | `TRANSFERRED` |
| closed | terminal states |
| cancelled | `CANCELLED` |

## Valid Transition Graph

Implemented in `JourneyStateMachine`:

- `REGISTRATION_PENDING -> ARRIVED`
- `ARRIVED -> TRIAGED | QUEUED | CANCELLED | NO_SHOW`
- `TRIAGED -> QUEUED | CANCELLED | NO_SHOW`
- `QUEUED -> IN_SERVICE | NO_SHOW | LEFT_WITHOUT_BEING_SEEN | CANCELLED`
- `IN_SERVICE -> ADMITTED | DISCHARGE_PENDING | QUEUED | CANCELLED | DEATH_RECORDED`
- `ADMITTED -> TRANSFERRED | DISCHARGE_PENDING | DEATH_RECORDED`
- `TRANSFERRED -> ADMITTED | DISCHARGE_PENDING`
- `DISCHARGE_PENDING -> DISCHARGED | ADMITTED | TRANSFERRED | DEATH_RECORDED | CANCELLED`
- terminal states have no outgoing transitions

## Encounter State Guardrails Added in This Pass

- Added duplicate active encounter protection (one active encounter per journey) in encounter start service.
- Added structured encounter metadata validation (context, entry point, modality, care setting, priority).

## Context Coverage Notes

- Outpatient: `ARRIVED -> TRIAGED/QUEUED -> IN_SERVICE -> DISCHARGE_PENDING -> DISCHARGED`
- Emergency/casualty: `ARRIVED -> TRIAGED -> QUEUED/IN_SERVICE -> ADMITTED|TRANSFERRED|DISCHARGED|DEATH_RECORDED`
- Inpatient: `ADMITTED <-> TRANSFERRED -> DISCHARGE_PENDING -> DISCHARGED`
- Community: currently represented through referral source + encounter context metadata; dedicated community journey board remains partial.
- Virtual: represented as encounter modality/context linked to journey and referral/telehealth workflow state.

## Test Evidence

- `JourneyStateMachineTest` validates valid/invalid transition rules.
- `PatientJourneyIntegrationTest` validates end-to-end flow through triage, queue, encounter, admit, discharge.
- `EncounterServiceTest` (added this pass) validates metadata rules and duplicate active encounter rejection.

## Remaining Bounded Gaps

- Encounter status state machine depth is still basic (`STARTED`, `ON_HOLD`, `COMPLETED`) and can be expanded in a future bounded pass.
- Follow-up pending/closed journey-level linkage should be extended with explicit follow-up workflow eventing.
