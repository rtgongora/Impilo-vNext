# Clinical Plane Dependency Map

## Deep Encounter Capability Dependencies

| Capability | Runtime dependency chain |
|---|---|
| Pathway/protocol linkage update | UI -> `experience-bff` -> `pct-service` -> PCT DB + outbox |
| CDS/alerts during encounter | UI encounter workspace -> BFF clinical routes -> guidance/rules/clinical-knowledge/forms + PCT/clinical data routes |
| Inpatient admission path | UI/BFF -> `inpatient-service` (+ PCT journey/disposition coordination) |
| Procedure/OR context | UI/BFF -> `pct-service` encounter context + downstream OROS/pharmacy/document/forms/MVUMO dependencies |
| PACS metadata + viewer launch | UI imaging -> `experience-bff` imaging governance -> `pacs-adapter-service` -> Orthanc/PACS + TSHEPO audit/authz |

## Trust/Registry Attach Points

- Trust: TSHEPO authz decisions and imaging/clinical audit events.
- Registry: VITO/VARAPI/TUSO/ZIBO context and identity data in encounter workflows.
- Data/SHR: BUTANO/FHIR boundaries remain external to coordinator logic.
# Clinical Plane Dependency Map

## Core Runtime Dependencies

| Clinical Capability | Primary Service | Identity/Registry Dependency | Trust Dependency | Other Clinical Dependency |
|---|---|---|---|---|
| Prescription write/cancel/refill/dispense | `pharmacy-service` | patient CPID (VITO lineage), facility/workspace context (TUSO lineage) | TSHEPO-authenticated trust headers + correlation context | `oros-service` dispense/order orchestration compatibility paths |
| Order orchestration | `oros-service` | patient/provider/facility references | TSHEPO authz headers + audit expectations | `pharmacy-service`, `pct-service` |
| Longitudinal clinical record | `butano-service` | VITO person/patient identity | TSHEPO policy and consent envelope | `butano-fhir`, `fhir-gateway-service` |
| FHIR exposure | `butano-fhir` + `fhir-gateway-service` | VITO/TUSO contextual references | TSHEPO policy controls | `butano-service` |
| Inpatient workflows | `inpatient-service` | VITO patient + TUSO facility/workspace | TSHEPO authz + audit | `document-service`, `forms-service` |
| Clinical documentation/forms | `document-service`, `forms-service` | patient/provider/facility references | TSHEPO authz + audit | `butano-service` |
| Guidance/rules decisions | `guidance-service`, `rules-service`, `clinical-knowledge-platform-service` | patient/provider context | TSHEPO authz + purpose constraints | `butano-service`, terminology (`zibo-service`) |
| Virtual encounter and referral packages | `pct-service` | VITO patient identity + TUSO facility/workspace + VARAPI provider refs | TSHEPO trust envelope; MVUMO consent orchestration and TSHEPO decisioning | `mvumo-service`, `document-service` (attachment IDs), `butano/fhir` summary boundaries |

## Experience Dependency Closure

| Experience Route | BFF | Clinical Backend | Current State |
|---|---|---|---|
| Provider medications create | `experience-bff /internal/v1/pharmacy/prescriptions` | `pharmacy-service /v1/prescriptions` | wired |
| Provider medications cancel | `experience-bff /internal/v1/pharmacy/prescriptions/{id}/cancel` | `pharmacy-service /v1/prescriptions/{id}/cancel` | wired |
| Mobile provider create/cancel | `experience-bff /internal/v1/mobile/provider/prescriptions*` | `pharmacy-service /v1/prescriptions*` | wired |
| Citizen prescription list/detail/refill | `experience-bff mobile citizen prescriptions` | `pharmacy-service /v1/prescriptions*` | wired |
| Teleconsult create/update/submit/respond/complete | `experience-bff /internal/v1/teleconsult/*` | `pct-service /v1/referrals*` | wired (canonical lifecycle) |
| Teleconsult consent initiation | `experience-bff /internal/v1/teleconsult/sessions/{id}/consent` | `mvumo-service /internal/v1/mvumo/consent-requests` + `pct-service /v1/referrals/{id}/consent` | wired (initiation + reference persistence) |
| Telemedicine hub list/join/end | `experience-bff /internal/v1/mobile/provider/telemedicine/sessions*` | `pct-service /v1/telehealth*` | wired (patient/facility lists) |
| Teleconsult attachment verification | `experience-bff` teleconsult stage/submit paths | `document-service /v1/internal/objects/{id}` metadata lookup | wired (strict ID existence/access checks; references only) |
| Teleconsult routing validation/lookup | `experience-bff /internal/v1/teleconsult/routing/*` + teleconsult stage/submit paths | `varapi-service` provider search/get + `tuso-service` facility/workspace lookup | wired (practitioner/workspace/facility-service), on-call/team/pool explicit future blocker |

## Clinical Plane Dependency Proof Closure

- Mutation-level authz/audit proof is enforced through service security hardening plus `ClinicalPlaneEvidenceGuardTest`.
- BUTANO/BUTANO-FHIR/FHIR-gateway ownership boundaries are now covered by:
  - source-level ownership assertions in `ClinicalPlaneEvidenceGuardTest`
  - repeatable runtime harness in `test/integration/clinical-shr-fhir-runtime.(sh|ps1)`.
- Endpoint hardening evidence for the listed Clinical services is now codified in the endpoint inventory plus automated guard test.
- Real-time audio/video/chat transport remains an explicit integration boundary and is intentionally not implemented in this pass.

## Encounter Mastery Dependency Addendum (This Pass)

- Encounter start now carries canonical context metadata through BFF -> PCT without duplicating specialist-domain ownership.
- Pathway/protocol references are persisted in PCT as linkage metadata only; execution remains with `forms-service`, `guidance-service`, `rules-service`, and `clinical-knowledge-platform-service`.
- Queue/booking/inpatient/order/pharmacy integrations remain orchestrated dependencies and are not re-implemented inside PCT.
