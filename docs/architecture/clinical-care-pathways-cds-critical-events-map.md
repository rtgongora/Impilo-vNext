# Clinical Care Pathways, CDS, and Critical Events Map

## Scope

This map covers in-encounter pathway/protocol selection and critical-event decision support across:
- outpatient
- emergency/casualty
- inpatient
- community
- virtual
- procedure/OR

PCT is the workflow coordinator. Guidance/rules/clinical-knowledge/forms own pathway logic and CDS content.

## Capability Matrix

| Capability | Encounter contexts | Owner service | Trigger | Required data | UI/BFF | Backend API | Audit | Status | Blocker |
|---|---|---|---|---|---|---|---|---|---|
| Encounter pathway reference linkage | all | `pct-service` | encounter start or update | encounter id, pathway ref | `/ehr/[patientId]/encounter/[encounterId]` via `PATCH /internal/v1/encounters/{id}/pathway-protocol` | `PATCH /v1/encounters/{id}/pathway-protocol` | PCT outbox (`ENCOUNTER_PATHWAY_PROTOCOL_UPDATED`) | implemented in this pass | none |
| Encounter protocol reference linkage | all | `pct-service` | encounter start or update | encounter id, protocol ref | same as above | same as above | PCT outbox (`ENCOUNTER_PATHWAY_PROTOCOL_UPDATED`) | implemented in this pass | none |
| CDS alerts (danger signs, allergy/condition/vitals checks) | outpatient, emergency, inpatient, virtual | experience + guidance/rules consumers | encounter workspace load | allergies, conditions, meds, vitals | `ui/experience` encounter workspace (`ClinicalAlerts`) | multiple BFF proxy routes (`/internal/v1/allergies`, `/internal/v1/conditions`, `/internal/v1/vitals`, `/internal/v1/pharmacy/prescriptions`) | route-level audit via upstream owners | partial | no single CDS orchestration API |
| Early warning / deterioration pathways | emergency, inpatient | `pct-service` (workflow), rules/guidance (protocol semantics) | triage and vitals capture | vitals + acuity + encounter context | encounter + emergency screens | `/v1/ews`, `/v1/ews/news2`, BFF `/internal/v1/ews*` | TSHEPO/PCT audit chains | partial | protocol engine federation not fully unified |
| Critical event workflow activation (resuscitation/emergency actions) | emergency, procedure, inpatient | `pct-service` | emergency activation and action logging | activation id, action payload | `/clinical/emergency` + encounter context links | `/v1/emergency/*` via BFF `/internal/v1/emergency/*` | explicit via PCT + TSHEPO audit channels | partial | dedicated critical-event protocol catalog linkage pending |
| Guidance/rules fallback handling | all | `experience-bff` | upstream outage | request context + headers | encounter/clinical surfaces | fail-close `502` typed envelopes in active routes | request/correlation IDs + upstream audit | implemented pattern | long-tail route parity remains iterative |

## Critical Event Protocol Coverage

| Protocol category | Current implementation state |
|---|---|
| deterioration / EWS | bounded support via EWS endpoints and encounter triage/vitals capture |
| sepsis pathway | represented as pathway/protocol references; no fabricated sepsis decision engine |
| emergency referral/escalation | represented through referral + emergency activation workflows |
| critical lab result | owned by OROS result criticality; encounter-level linkage remains partial |
| adverse drug event | represented through pharmacy/alerts surfaces; full adverse-event protocol engine pending |
| maternal/perinatal emergency | maternity + emergency endpoints present; protocol catalog linkage partial |
| procedure complication | represented through procedure context + emergency activation linkage |
| cardiac/respiratory emergency | represented through emergency activation/resuscitation endpoints |
| violence/trauma emergency | represented in emergency workflows; formal trauma protocol matrix pending |

## This Pass (bounded implementation)

- Added encounter-level pathway/protocol update endpoint in PCT:
  - `PATCH /v1/encounters/{id}/pathway-protocol`
- Added BFF orchestration route:
  - `PATCH /internal/v1/encounters/{id}/pathway-protocol`
- Added encounter UI section to view/update selected pathway/protocol references.
- Added fail-close behavior for pathway/protocol update when PCT is unavailable.

## Honest Blockers

- No single sovereign CDS runtime service currently executes pathway logic end-to-end.
- Guidance/rules/clinical-knowledge integration is federated and partial across route families.
- Critical-event protocol catalogs are not yet centrally enumerated as machine-executable definitions.
