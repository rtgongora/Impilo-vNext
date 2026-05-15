# Clinical Plane Canonical API Contract

## Encounter Coordinator Contract (Deep Pass Addendum)

### Start Encounter

- `POST /v1/journeys/{id}/encounter/start`
- Includes coordinator metadata:
  - `encounterContext`
  - `entryPoint`
  - `modality`
  - `virtualMode`
  - `careSetting`
  - `priority`
  - `triageCategory`
  - `pathwayRef`
  - `protocolRef`

### Update Encounter Pathway/Protocol Linkage

- `PATCH /v1/encounters/{id}/pathway-protocol`
- Request:
  - `pathwayRef` (optional)
  - `protocolRef` (optional)
- Response:
  - canonical encounter payload with updated linkage.
- Emits coordinator outbox event:
  - `ENCOUNTER_PATHWAY_PROTOCOL_UPDATED`

### Context Enumeration Addendum

Encounter context now explicitly includes:
- `outpatient`
- `emergency`
- `inpatient`
- `community`
- `virtual`
- `procedure`
- `procedure_room`
- `operating_room`
# Clinical Plane Canonical API Contract

## Canonical Rule Set

- Clinical APIs must return typed success/error envelopes; no synthetic success fallback on upstream failure.
- Clinical write paths must be authenticated (`/v1/**`) and trust-context-aware.
- Mutation endpoints must emit auditable events (at minimum outbox domain events).
- BFF must proxy to real clinical backends for intended capabilities (no permanent `501` for intended paths).

## Canonical Prescription Contract (Implemented In This Pass)

Owner: `pharmacy-service`

| Operation | Route | Required Inputs | Typed Failures |
|---|---|---|---|
| Create prescription | `POST /v1/prescriptions` | `patient_id`, `medication_name`, `dosage`, `frequency`, `prescribed_by`; facility resolved from body or trust context | `400 VALIDATION_FAILED` |
| List patient prescriptions | `GET /v1/prescriptions/patient/{cpid}` | `cpid` (+ optional `status`, paging) | `400 VALIDATION_FAILED` |
| Get prescription | `GET /v1/prescriptions/{id}` | `id` | `404 PRESCRIPTION_NOT_FOUND`, `403 FORBIDDEN` |
| Cancel prescription | `POST /v1/prescriptions/{id}/cancel` | `id` (+ optional reason) | `404 PRESCRIPTION_NOT_FOUND`, `409 PRESCRIPTION_STATE_CONFLICT` |
| Request refill | `POST /v1/prescriptions/{id}/refill` | `id` | `404 PRESCRIPTION_NOT_FOUND` |
| Mark dispensed | `POST /v1/prescriptions/{id}/dispense` | `id` (+ optional `dispensed_by`) | `404 PRESCRIPTION_NOT_FOUND`, `409 PRESCRIPTION_STATE_CONFLICT` |

## Experience/BFF Canonical Clinical Mapping

| BFF Route | Backend Route |
|---|---|
| `POST /internal/v1/pharmacy/prescriptions` | `POST /v1/prescriptions` |
| `POST /internal/v1/pharmacy/prescriptions/{id}/cancel` | `POST /v1/prescriptions/{id}/cancel` |
| `POST /internal/v1/mobile/provider/prescriptions` | `POST /v1/prescriptions` |
| `POST /internal/v1/mobile/provider/prescriptions/{id}/cancel` | `POST /v1/prescriptions/{id}/cancel` |

## Canonical Virtual Encounter / Referral Contract (Implemented In This Pass)

Owner: `pct-service` (encounter-linked workflow state)

| Operation | Route | Required Inputs | Typed Failures |
|---|---|---|---|
| Create referral draft | `POST /v1/referrals` | `encounter_id` (numeric), clinical question/reason context | `400 MISSING_ENCOUNTER`, `404 MISSING_ENCOUNTER`, `409 ENCOUNTER_JOURNEY_MISMATCH` |
| Update referral stage | `PUT /v1/referrals/{id}/stage` | `stage` in `[1..7]` + stage payload | `400 INVALID_STAGE`, `400 INVALID_VIRTUAL_MODE`, `400 INVALID_ROUTING_TARGET`, `409 INVALID_TRANSITION` |
| Submit referral package | `POST /v1/referrals/{id}/submit` | required stage-complete payload (letter/routing/mode, consent where required) | `400 MISSING_REFERRAL_LETTER`, `400 INVALID_ROUTING_TARGET`, `409 CONSENT_REQUIRED_MISSING`, `409 INVALID_TRANSITION` |
| Record consent refs | `POST /v1/referrals/{id}/consent` | consent metadata references from MVUMO/TSHEPO | `400 INVALID_CONSENT_TYPE`, `400 INVALID_CONSENT_STATUS`, `409 INVALID_TRANSITION` |
| Record consultation response | `POST /v1/referrals/{id}/respond` | `response_notes` | `400 MISSING_RESPONSE`, `409 INVALID_TRANSITION` |
| Complete referral | `POST /v1/referrals/{id}/complete` | optional outcome metadata | `409 INVALID_TRANSITION` |

Owner: `pct-service` (telehealth session operational state)

| Operation | Route | Required Inputs | Typed Failures |
|---|---|---|---|
| Create telehealth session | `POST /v1/telehealth` | `patient_id` (+ facility/provider context) | `400 MISSING_PATIENT_ID`, `400 INVALID_ENCOUNTER_ID`, `400 INVALID_DATETIME` |
| List patient sessions | `GET /v1/patient/{cpid}/telehealth` | `cpid` | typed read envelope |
| List facility sessions | `GET /v1/telehealth?facilityId=...` | `facilityId` | `400 INVALID_UUID` |
| Join session | `POST /v1/telehealth/{id}/join` | session id | `404 SESSION_NOT_FOUND`, `409 INVALID_TRANSITION` |
| End session | `POST /v1/telehealth/{id}/end` | session id | `404 SESSION_NOT_FOUND`, `409 INVALID_TRANSITION` |

## Experience/BFF Teleconsult Mapping (Implemented In This Pass)

| BFF Route | Backend Route |
|---|---|
| `POST /internal/v1/teleconsult/sessions` | `POST /v1/referrals` |
| `PUT /internal/v1/teleconsult/sessions/{id}/referral` | `PUT /v1/referrals/{id}/stage` |
| `POST /internal/v1/teleconsult/sessions/{id}/consent` | `POST /internal/v1/mvumo/consent-requests` + `POST /v1/referrals/{id}/consent` |
| `POST /internal/v1/teleconsult/sessions/{id}/submit` | `POST /v1/referrals/{id}/submit` |
| `POST /internal/v1/teleconsult/sessions/{id}/response` | `POST /v1/referrals/{id}/respond` |
| `POST /internal/v1/teleconsult/sessions/{id}/complete` | `POST /v1/referrals/{id}/complete` |
| `GET /internal/v1/teleconsult/routing/providers` | `POST /v1/internal/providers/search` |
| `GET /internal/v1/teleconsult/routing/facilities` | `POST /v1/internal/facilities/search` |
| `GET /internal/v1/teleconsult/routing/workspaces` | `GET /v1/internal/facilities/{facilityId}/workspaces` |

### Teleconsult Validation Contract (Focused Closure)

- Attachment references are `document-service` object UUIDs only (references, not content payloads).
- Teleconsult stage/submit paths perform strict document existence/access validation before persisting/submitting referral state.
- Routing target validation:
  - `PRACTITIONER` -> VARAPI provider lookup required
  - `WORKSPACE` -> TUSO workspace lookup required
  - `FACILITY_SERVICE` -> TUSO facility lookup required (service suffix optional metadata)
  - `ON_CALL`/`TEAM`/`SPECIALTY_POOL`/`POOL`/`NATIONAL_POOL` -> explicit `501 ROUTING_TYPE_UNAVAILABLE`.
- Real-time chat/audio/video transport remains out-of-scope and explicitly unavailable.

## Contract Files Updated

- `contracts/openapi/pharmacy.openapi.yaml`
- `contracts/openapi/pct.openapi.yaml` (encounter mastery metadata)

## Encounter Mastery Contract (This Pass)

Owner: `pct-service` (encounter conductor metadata)

| Operation | Route | Required Inputs | Typed Failures |
|---|---|---|---|
| Start encounter with context metadata | `POST /v1/journeys/{id}/encounter/start` | `encounterType` + optional `encounterContext`, `entryPoint`, `modality`, `virtualMode`, `careSetting`, `priority`, `triageCategory`, `pathwayRef`, `protocolRef` | `400 Invalid encounter context/entry/modality/care setting/priority`, `409 Encounter already active for journey`, `404 Journey not found` |

Experience/BFF mapping:

| BFF Route | Backend Route |
|---|---|
| `POST /internal/v1/encounters` | `POST /v1/journeys/{id}/encounter/start` |
| `POST /internal/v1/mobile/provider/encounters` | `POST /v1/journeys/{id}/encounter/start` |

## Cross-Service Enforcement Evidence

- `services/pharmacy-service/src/test/java/zw/gov/mohcc/impilo/pharmacy/architecture/ClinicalPlaneEvidenceGuardTest.java`
  - verifies authenticated clinical business APIs across listed services
  - verifies mutation-capable services expose audit/outbox evidence markers
  - verifies explicit SHR/FHIR boundary ownership markers (`butano-service`, `butano-fhir`, `fhir-gateway-service`)
- `test/integration/clinical-shr-fhir-runtime.(sh|ps1)`
  - executes repeatable runtime proof sequence for SHR/FHIR boundary and clinical mutation/authz evidence
