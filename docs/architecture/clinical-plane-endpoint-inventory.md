# Clinical Plane Endpoint Inventory

## Encounter Deep Capability Addendum

| Capability | Endpoint | Owner | Status |
|---|---|---|---|
| Encounter start with context/pathway/protocol metadata | `POST /v1/journeys/{id}/encounter/start` | `pct-service` | implemented |
| Encounter pathway/protocol update | `PATCH /v1/encounters/{id}/pathway-protocol` | `pct-service` | implemented in this pass |
| BFF pathway/protocol update route | `PATCH /internal/v1/encounters/{id}/pathway-protocol` | `experience-bff` -> `pct-service` | implemented in this pass |
| Inpatient admission create | `POST /internal/v1/admissions` | `inpatient-service` | implemented |
| Inpatient transfer | `POST /internal/v1/admissions/{admissionRef}/transfer` | `inpatient-service` | implemented |
| Inpatient discharge | `POST /internal/v1/admissions/{admissionRef}/discharge` | `inpatient-service` | implemented |
| Imaging study metadata/search/viewer sessions | `/internal/v1/imaging/studies*` | `experience-bff` -> `pacs-adapter-service` | implemented |
| PACS/DICOM passthrough operations | `/internal/v1/pacs/*` | `experience-bff` proxy | implemented (bounded) |
# Clinical Plane Endpoint Inventory

This inventory is the current production-readiness baseline for the Clinical Execution & Shared Health Record plane.

## Pharmacy Service (Detailed In This Pass)

| Method | Path | Purpose | Implementation | Auth | Audit/Event | Experience Exposure | Status |
|---|---|---|---|---|---|---|---|
| POST | `/v1/prescriptions` | Create canonical prescription | Implemented | required (`/v1/**`) | outbox `PRESCRIPTION_CREATED` | yes (`/internal/v1/pharmacy/prescriptions`, mobile provider prescriptions) | wired |
| GET | `/v1/prescriptions/patient/{cpid}` | List prescriptions by patient | Implemented | required | read-only | yes | wired |
| GET | `/v1/prescriptions/{id}` | Get prescription detail | Implemented | required | read-only | yes (citizen + provider flows) | wired |
| POST | `/v1/prescriptions/{id}/cancel` | Cancel active prescription | Implemented | required | outbox `PRESCRIPTION_CANCELLED` | yes | wired |
| POST | `/v1/prescriptions/{id}/refill` | Request refill | Implemented | required | outbox `PRESCRIPTION_REFILL_REQUESTED` | yes (citizen refill) | wired |
| POST | `/v1/prescriptions/{id}/dispense` | Mark prescription dispensed | Implemented | required | outbox `PRESCRIPTION_DISPENSED` | yes (`/internal/v1/pharmacy/dispense`) | wired |
| GET | `/v1/worklists` | Dispense worklist | Implemented | required | evented via dispense flow | yes (upstream view) | wired |
| GET | `/v1/dispense-orders/patient/{cpid}` | Patient dispense orders | Implemented | required | evented via dispense flow | yes (upstream view) | wired |

## Experience-BFF Clinical/Pharmacy Endpoints (Dependency Scope)

| Method | Path | Backend | Status |
|---|---|---|---|
| GET | `/internal/v1/pharmacy/prescriptions` | `pharmacy-service /v1/prescriptions/patient/{cpid}` | wired |
| POST | `/internal/v1/pharmacy/prescriptions` | `pharmacy-service /v1/prescriptions` | wired (replaced prior 501) |
| POST | `/internal/v1/pharmacy/prescriptions/{id}/cancel` | `pharmacy-service /v1/prescriptions/{id}/cancel` | wired (replaced prior 501) |
| POST | `/internal/v1/pharmacy/dispense` | `pharmacy-service /v1/prescriptions/{id}/dispense` | wired |
| POST | `/internal/v1/mobile/provider/prescriptions` | `pharmacy-service /v1/prescriptions` | wired (replaced prior 501) |
| POST | `/internal/v1/mobile/provider/prescriptions/{id}/cancel` | `pharmacy-service /v1/prescriptions/{id}/cancel` | wired (replaced prior 501) |

## Telemedicine (Experience-BFF -> PCT)

| Method | Path | Backend | Status |
|---|---|---|---|
| GET | `/internal/v1/mobile/provider/telemedicine/sessions` | `pct-service /v1/patient/{cpid}/telehealth` | wired (explicit patient validation + fail-close) |
| POST | `/internal/v1/mobile/provider/telemedicine/sessions` | `pct-service /v1/telehealth` | wired (required payload validation + fail-close) |
| POST | `/internal/v1/mobile/provider/telemedicine/sessions/{id}/join` | `pct-service /v1/telehealth/{id}/join` | wired |
| POST | `/internal/v1/mobile/provider/telemedicine/sessions/{id}/end` | `pct-service /v1/telehealth/{id}/end` | wired |

## PACS Adapter and Imaging (Clinical Scope)

| Method | Path | Purpose | Auth | Audit/Event | Experience Exposure | Status |
|---|---|---|---|---|---|---|
| GET | `/internal/v1/imaging-studies` | List studies (optionally patient-filtered) | required | imaging access audit + outbox access record | yes (`/internal/v1/imaging/*`) | wired |
| POST | `/internal/v1/imaging-studies` | Register imaging study | required | outbox `pacs.study.available` | yes | wired |
| PATCH | `/internal/v1/imaging-studies/{id}/correlate` | Correlate study with OROS order | required | outbox `pacs.study.correlated` | yes | wired |
| POST | `/internal/v1/imaging-studies/{id}/forward` | Forward to Orthanc | required | state transitions + audit evidence | yes | wired |
| POST | `/internal/v1/imaging-studies/{id}/viewer-sessions` | Launch viewer session | required | access audit + outbox `imaging.viewer.launched` | yes | wired |

## Other Clinical Services (Evidence-Backed In This Pass)

| Service | Endpoint Inventory Depth In This Pass | Status |
|---|---|---|
| `butano-service` | contract + module tests + clinical evidence guard (SoR/FHIR boundary + authz/audit markers) | evidence-backed |
| `butano-fhir` | contract + module tests + clinical evidence guard (dedicated FHIR ownership marker) | evidence-backed |
| `fhir-gateway-service` | contract + module tests + security hardening + clinical evidence guard | evidence-backed |
| `pct-service` | contract + module tests + security hardening + clinical evidence guard | evidence-backed |
| `oros-service` | contract + module tests + security hardening + clinical evidence guard | evidence-backed |
| `pacs-adapter-service` | contract + module tests + security source guard + clinical evidence guard | evidence-backed |
| `inpatient-service` | contract + module tests + clinical evidence guard | evidence-backed |
| `document-service` | contract + module tests + clinical evidence guard | evidence-backed |
| `forms-service` | contract + module tests + clinical evidence guard | evidence-backed |
| `guidance-service` | contract + module tests + clinical evidence guard | evidence-backed |
| `rules-service` | contract + module tests + clinical evidence guard | evidence-backed |
| `clinical-knowledge-platform-service` | contract + module tests + clinical evidence guard | evidence-backed |

## PCT Virtual Encounter / Referral / Telehealth (This Pass)

| Method | Path | Purpose | Implementation | Auth | Audit/Event | Experience Exposure | Status |
|---|---|---|---|---|---|---|---|
| POST | `/v1/referrals` | Create encounter-linked referral package draft | Implemented | required | PCT persistence + trust headers | yes (`/internal/v1/referrals`, `/internal/v1/teleconsult/sessions`) | wired |
| GET | `/v1/referrals/{id}` | Get referral package | Implemented | required | read-only | yes | wired |
| PUT | `/v1/referrals/{id}/stage` | Update stage payload (1..7) | Implemented | required | stage mutation persisted | yes (`/internal/v1/teleconsult/sessions/{id}/referral`) | wired |
| POST | `/v1/referrals/{id}/consent` | Store consent refs/status metadata | Implemented | required | consent metadata mutation persisted | yes (`/internal/v1/teleconsult/sessions/{id}/consent` via MVUMO orchestration) | wired |
| POST | `/v1/referrals/{id}/submit` | Submit referral package | Implemented | required | transition validation + persistence | yes | wired |
| POST | `/v1/referrals/{id}/accept` | Accept inbound referral | Implemented | required | transition mutation persisted | yes | wired |
| POST | `/v1/referrals/{id}/respond` | Record consultation response | Implemented | required | transition mutation persisted | yes | wired |
| POST | `/v1/referrals/{id}/complete` | Close referral loop | Implemented | required | transition mutation persisted | yes | wired |
| POST | `/v1/telehealth` | Create telehealth session | Implemented | required | session persistence | yes (`/internal/v1/mobile/provider/telemedicine/sessions`) | wired |
| GET | `/v1/patient/{cpid}/telehealth` | Patient telehealth sessions | Implemented | required | read-only | yes | wired |
| GET | `/v1/telehealth` | Facility operational telehealth list | Implemented | required | read-only | yes (home telemedicine hub facility lens) | wired |
| POST | `/v1/telehealth/{id}/join` | Mark session in-progress | Implemented | required | transition mutation persisted | yes | wired |
| POST | `/v1/telehealth/{id}/end` | Complete session | Implemented | required | transition mutation persisted | yes | wired |

## Teleconsult Routing + Attachment Verification (Experience-BFF)

| Method | Path | Purpose | Backend | Status |
|---|---|---|---|---|
| GET | `/internal/v1/teleconsult/routing/providers` | Provider/practitioner search for referral routing | `varapi-service /v1/internal/providers/search` | wired |
| GET | `/internal/v1/teleconsult/routing/facilities` | Facility search for referral routing | `tuso-service /v1/internal/facilities/search` | wired |
| GET | `/internal/v1/teleconsult/routing/workspaces` | Facility workspace list for referral routing | `tuso-service /v1/internal/facilities/{id}/workspaces` | wired |
| PUT | `/internal/v1/teleconsult/sessions/{id}/referral` | Stage update with strict attachment + routing validation before PCT update | `document-service` lookup + VARAPI/TUSO lookup + `pct-service /v1/referrals/{id}/stage` | wired |
| POST | `/internal/v1/teleconsult/sessions/{id}/submit` | Pre-submit attachment/routing re-validation against canonical references | `document-service` lookup + VARAPI/TUSO lookup + `pct-service /v1/referrals/{id}/submit` | wired |

## Encounter Mastery Metadata (This Pass)

| Method | Path | Purpose | Implementation | Auth | Experience Exposure | Status |
|---|---|---|---|---|---|---|
| POST | `/v1/journeys/{id}/encounter/start` | Start encounter with explicit context/entry/modality/care-setting/priority/pathway metadata | Implemented and validated in `pct-service` | required | `/internal/v1/encounters`, `/internal/v1/mobile/provider/encounters` | wired |
