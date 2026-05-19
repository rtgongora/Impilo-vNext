# Clinical/PCT Virtual Encounter Implementation Report

## Current vNext implementation found

- PCT previously had journey/encounter/discharge primitives, but no first-class referral package or telehealth session persistence model.
- Experience BFF had encounter and referral routes, but teleconsult web routes were fully fail-closed and not backed by canonical PCT/MVUMO orchestration.
- UI already had two entry points:
  - encounter-level: `Patient Encounter -> Consults & Referrals`
  - home-level: `Home -> Telemedicine Hub`
  but backend contract coverage was partial.

## Lovable features accounted for in this pass

- Encounter-level consult/referral workflow now persists as PCT-owned referral package lifecycle.
- Home-level telemedicine hub now resolves against real PCT telehealth list APIs for patient and facility scopes.
- Seven referral stages are represented and persisted (`stage 1..7`).
- Six virtual care modes are represented and validated as canonical metadata (`async`, `chat`, `audio`, `video`, `scheduled`, `board`).
- Consent stage is now orchestrated through MVUMO initiation from BFF and persisted as PCT consent references.
- Discharge now resolves encounter->journey linkage and invokes canonical PCT discharge start instead of synthetic success.

## Implemented changes

### PCT (`pct-service`)

- Added migration `V007__virtual_encounter_referrals_telehealth.sql`:
  - encounter modality columns (`modality`, `virtual_mode`)
  - `pct_referral_packages`
  - `pct_telehealth_sessions`
- Extended encounter start contract and service logic:
  - supports `modality` and `virtualMode`
  - validates modality/mode values.
- Added canonical referral package domain:
  - `ReferralPackageEntity`
  - `ReferralPackageRepository`
  - `ReferralPackageService`
  - `ReferralController` (`/v1/referrals*`)
- Added canonical telehealth session domain:
  - `TelehealthSessionEntity`
  - `TelehealthSessionRepository`
  - `TelehealthSessionService`
  - `TelehealthController` (`/v1/telehealth*`, `/v1/patient/{cpid}/telehealth`)
- Added typed domain errors via `PctDomainException` for validation and transition failures.
- Hardened discharge start validation:
  - discharge-eligible journey state checks
  - active discharge-case duplication guard.

### Experience BFF (`experience-bff`)

- Added `MvumoServiceClient` (`/internal/v1/mvumo/consent-requests` orchestration).
- Replaced teleconsult fail-close blanket behavior with canonical proxy orchestration:
  - create session -> PCT referral draft
  - stage updates -> PCT referral stage updates
  - consent stage -> MVUMO initiation + PCT consent reference update
  - submit/respond/complete -> PCT referral lifecycle endpoints
  - messaging transport remains explicitly unavailable (`501`) to avoid fake live chat execution
- Extended `PctServiceClient` with:
  - telehealth facility list
  - referral stage update
  - referral consent update
  - referral submit.
- Updated mobile telemedicine list behavior:
  - accepts `patient_id` or `facility_id` (operational hub support).
- Updated encounter discharge route:
  - resolves canonical journey linkage from encounter payload
  - starts PCT discharge with typed validation/errors.
- Relaxed referral create request `referred_by` strictness and fallback to actor header.

### UI (`ui/experience`)

- Referral create hook now serializes canonical snake_case BFF payload (`useReferrals.ts`) to avoid contract mismatch.
- Existing hub/consult UI kept vNext-native (no layout rewrite), now backed by expanded BFF/PCT contracts.

## Remaining blockers

- Real-time transport (chat/audio/video signaling/media) remains intentionally unavailable in canonical backend; no fake execution paths were introduced.
- Provider/facility routing directory depth (VARAPI/TUSO advanced targets like on-call pool/team) remains partial and should be expanded in a bounded follow-on.
- Attachment ID validation against document-service is currently reference-only in PCT; strict cross-service ID verification remains a follow-on integration step.
- OpenAPI documents for all new PCT/BFF routes need a full parity pass across every schema field.

## Tests added/updated

- Added `services/pct-service/src/test/java/zw/gov/mohcc/impilo/pct/core/ReferralPackageServiceTest.java`
- Added `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/TeleconsultControllerTest.java`
- Updated `services/pct-service/src/test/java/zw/gov/mohcc/impilo/pct/integration/PatientJourneyIntegrationTest.java`
- Updated `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/EncounterControllerTest.java`
- Updated `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/mobile/MobileTelemedicineControllerTest.java`

## Commands run

- `mvn -pl pct-service -am test`
- `mvn -pl mvumo-service -am test`
- `mvn -pl experience-bff -am test`
- `npm run lint` (`ui/experience`)
- `npm run type-check` (`ui/experience`)
- `npm run test` (`ui/experience`)
- `npm run build` (`ui/experience`)
- `node scripts/completeness/generate-completeness-report.mjs; node scripts/completeness/openapi-contracts.mjs`

## Focused Closure Pass (Attachment + Routing)

- Added strict attachment document ID verification on teleconsult referral update and submit paths:
  - validates UUID format
  - verifies existence/accessibility through `document-service`
  - returns typed errors (`INVALID_ATTACHMENT_REFERENCE`, `ATTACHMENT_INACCESSIBLE`, `DOCUMENT_SERVICE_UNAVAILABLE`) without synthetic success.
- Added bounded advanced routing validation:
  - `PRACTITIONER` targets validated against VARAPI
  - `WORKSPACE` and `FACILITY_SERVICE` targets validated against TUSO
  - `ON_CALL`/`TEAM`/`SPECIALTY_POOL`/`POOL`/`NATIONAL_POOL` return explicit `ROUTING_TYPE_UNAVAILABLE` (`501`).
- Added teleconsult routing lookup APIs:
  - `GET /internal/v1/teleconsult/routing/providers`
  - `GET /internal/v1/teleconsult/routing/facilities`
  - `GET /internal/v1/teleconsult/routing/workspaces`
- Hardened PCT-side referral payload guardrails:
  - attachment references must be UUID document IDs
  - routing target must include valid `type` and `target_ref`/`target`.
- Real-time chat/audio/video transport remains explicitly unavailable and unchanged in this pass.
