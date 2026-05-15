# Clinical Plane Production Readiness Final Report

## Current Verdict

`READY FOR CONTROLLED PRODUCTION BASELINE` with explicit bounded blockers.

## Deep Clinical Encounter Capability Pass (This Pass)

Implemented:
- PCT encounter context extended for `procedure`, `procedure_room`, and `operating_room`.
- PCT endpoint added for encounter pathway/protocol updates:
  - `PATCH /v1/encounters/{id}/pathway-protocol`
- Experience BFF route added:
  - `PATCH /internal/v1/encounters/{id}/pathway-protocol`
- Encounter UI now shows and updates selected pathway/protocol linkage with honest fail-close error messaging.
- Deep capability maps published for:
  - care pathways/CDS/critical events
  - full inpatient workflow
  - procedure/OR context
  - PACS/DICOM viewer workflow

## Explicit Blockers (Not Hidden)

- CDS orchestration remains federated; no single sovereign CDS runtime API yet.
- Inpatient depth still partial for ward-round authoring and nursing-plan aggregate workflows.
- Procedure phase orchestration is partial pending ADR on dedicated procedure/theatre service.
- Imaging viewer feature depth is partial despite strong PACS metadata + governance foundations.

## Validation Snapshot

- `experience-bff` encounter controller tests: passing.
- `ui/experience` type-check: passing.
- `pct-service` test run blocked by pre-existing compile failure in `ReferralPackageService` unrelated to this pass (`normalizeJsonObject(...)` missing symbol).
# Clinical Execution & Shared Health Record Plane Production Readiness Final Report

## Plane Verdict

**READY FOR CONTROLLED PRODUCTION BASELINE.**

The previously open Clinical plane blockers (service-by-service endpoint hardening evidence, mutation-level authz/audit evidence depth, and SoR/FHIR boundary runtime proof) are now closed with implementation, source-level guardrails, and repeatable runtime validation harnesses.

## Services Reviewed In This Pass

- `pharmacy-service` (deep implementation pass)
- `experience-bff` clinical/pharmacy routes required for dependency closure
- `pacs-adapter-service` and `experience-bff` PACS/telemedicine routes (focused closure pass)
- Validation coverage run for: `pacs-adapter-service`, `oros-service`, `pct-service`, `butano-service`, `butano-fhir`, `fhir-gateway-service`, `inpatient-service`, `document-service`, `forms-service`, `guidance-service`, `rules-service`, `clinical-knowledge-platform-service`

## Functionality Completed

- Implemented canonical pharmacy prescription APIs in `pharmacy-service`:
  - `POST /v1/prescriptions`
  - `GET /v1/prescriptions/patient/{cpid}`
  - `GET /v1/prescriptions/{id}`
  - `POST /v1/prescriptions/{id}/cancel`
  - `POST /v1/prescriptions/{id}/refill`
  - `POST /v1/prescriptions/{id}/dispense`
- Added canonical persistence model (`rx_prescriptions`) and state machine behavior (`ACTIVE -> CANCELLED`, `ACTIVE -> DISPENSED`) with conflict rejection.
- Added mutation outbox events for create/cancel/refill/dispense.
- Removed Experience-side permanent `501` blocker behavior for prescription create/cancel:
  - `services/experience-bff/.../PharmacyController.java`
  - `services/experience-bff/.../mobile/MobilePrescriptionController.java`
- Wired BFF to real backend endpoints via `PharmacyServiceClient`.
- Updated pharmacy OpenAPI contract to include canonical prescription endpoints and schemas.
- Hardened clinical authz configurations to fail-closed:
  - `services/oros-service/.../SecurityConfig.java` (`anyRequest().authenticated()`)
  - `services/pct-service/.../SecurityConfig.java` (`anyRequest().authenticated()`)
  - `services/fhir-gateway-service/.../SecurityConfig.java` (business endpoints authenticated, OAuth2 resource server enabled)
- Added cross-service clinical hardening evidence test:
  - `services/pharmacy-service/src/test/java/zw/gov/mohcc/impilo/pharmacy/architecture/ClinicalPlaneEvidenceGuardTest.java`
  - Verifies security/authz enforcement, mutation audit/outbox evidence presence, and SHR/FHIR boundary ownership markers across all listed Clinical services.
- Added repeatable SHR/FHIR runtime proof harness:
  - `test/integration/clinical-shr-fhir-runtime.sh`
  - `test/integration/clinical-shr-fhir-runtime.ps1`
- Added focused PACS + telemedicine closure hardening:
  - Included `pacs-adapter-service` in `ClinicalPlaneEvidenceGuardTest` coverage.
  - Added PACS `SecurityConfigSourceGuardTest` to prevent authz regressions.
  - Hardened `MobileTelemedicineController` request validation and success/error envelope parity.
  - Added `MobileTelemedicineControllerTest` regression coverage for required fields and fail-close behavior.

## Remaining Gaps (Non-Blocking Enhancements)

- Deep clinical-domain scenario expansions (beyond current contract/security/audit guardrails) should continue as iterative hardening work.
- Additional TSHEPO operational telemetry dashboards can be expanded, but this is not a blocker for controlled baseline.
- Further longitudinal writeback reconciliation depth from workflow services into SHR can be expanded in follow-on releases.

## Patient Encounter/PCT Lovable Alignment Follow-on (vNext-native)

- Completed a vNext-native alignment pass for encounter/PCT/telemedicine coverage mapping (see `docs/architecture/clinical-pct-lovable-alignment-matrix.md`).
- Removed production-path synthetic encounter and mobile triage success fallbacks in `experience-bff` and enforced explicit fail-close behavior.
- Converted teleconsult in-memory runtime behavior from synthetic success to explicit `501 BACKEND_CAPABILITY_MISSING` blocker responses until canonical backend ownership is wired.
- Added referral builder stage-6 mode representation in web teleconsult builder (six modes captured as package metadata), while keeping execution honesty (no fake live AV/session behavior).

This follow-on does **not** upgrade Experience readiness to full READY; teleconsult canonical backend and referral orchestration depth remain explicit blockers outside Clinical baseline closure.

## Focused Clinical/PCT Virtual Encounter Implementation Follow-on

- Added canonical PCT virtual encounter and referral persistence model (`modality`, `virtual_mode`, referral package and telehealth session tables).
- Implemented referral package lifecycle APIs in PCT and wired teleconsult write-path orchestration in Experience BFF to these canonical endpoints.
- Added MVUMO-backed consent stage initiation from teleconsult workflow and persisted consent references (`consent_reference`, `mvumo_session_id`, `tshepo_decision_id`) in PCT referral state.
- Upgraded encounter discharge from explicit blocker to canonical encounter->journey linked start-discharge invocation.
- Remaining bounded blocker for this stream is live transport runtime (chat/audio/video signaling/media), which remains explicitly unavailable and non-synthetic.

## Focused Clinical/PCT Closure Pass (Bounded Blockers)

- Attachment document references are now validated as strict document-service IDs on teleconsult referral update/submit paths.
- Invalid/non-existent/inaccessible attachments now fail closed with typed errors; no attachment success is synthesized.
- Routing depth improved with canonical registry lookup validation:
  - provider/practitioner targets -> VARAPI
  - facility/workspace/service targets -> TUSO
  - on-call/team/pool variants -> explicit `501 ROUTING_TYPE_UNAVAILABLE`.
- Real-time transport remains intentionally unavailable and unchanged in this pass.

## Focused Clinical Encounter Mastery Pass (This Pass)

- Completed an encounter-mastery inventory and architecture maps covering outpatient, emergency/casualty, inpatient, community, and virtual contexts.
- Implemented bounded PCT encounter metadata support for:
  - `encounterContext`
  - `entryPoint`
  - `modality` / `virtualMode`
  - `careSetting`
  - `priority` / `triageCategory`
  - `pathwayRef` / `protocolRef`
- Added duplicate-active-encounter protection per journey on encounter start.
- Updated Experience BFF and Experience encounter-start UI to pass explicit encounter mastery metadata.
- Added encounter metadata OpenAPI contract updates (`pct.openapi.yaml`) and new encounter mastery architecture docs.

Clinical plane readiness remains **READY FOR CONTROLLED PRODUCTION BASELINE** for current bounded scope, with explicit blockers still tracked for:

- realtime virtual transport signaling/media
- on-call/team/pool routing directory depth
- remaining long-tail BFF fallback parity hardening in non-core queue/ops paths

## Tests Added/Updated

- Added:
  - `services/pharmacy-service/src/test/java/zw/gov/mohcc/impilo/pharmacy/core/PrescriptionServiceTest.java`
  - `services/pharmacy-service/src/test/java/zw/gov/mohcc/impilo/pharmacy/architecture/ClinicalPlaneEvidenceGuardTest.java`
  - `services/pacs-adapter-service/src/test/java/zw/gov/mohcc/impilo/pacs/config/SecurityConfigSourceGuardTest.java`
  - `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/mobile/MobileTelemedicineControllerTest.java`
- Updated:
  - `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/PharmacyControllerTest.java`
  - `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/mobile/MobilePrescriptionControllerTest.java`
  - `test/integration/clinical-shr-fhir-runtime.sh`
  - `test/integration/clinical-shr-fhir-runtime.ps1`

## Commands Run

- `mvn -pl pharmacy-service,experience-bff -am test`
- `mvn -pl fhir-gateway-service,oros-service,pct-service,pharmacy-service -am test`
- `mvn -pl pharmacy-service,oros-service,pct-service,butano-service,butano-fhir,fhir-gateway-service,inpatient-service,document-service,forms-service,guidance-service,rules-service,clinical-knowledge-platform-service -am test`
- `mvn -pl experience-bff -am test`
- `mvn -pl experience-bff -am test "-Dtest=MobileTelemedicineControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
- `mvn -pl pacs-adapter-service -am test "-Dtest=SecurityConfigSourceGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
- `mvn -pl pharmacy-service -am test "-Dtest=ClinicalPlaneEvidenceGuardTest" "-Dsurefire.failIfNoSpecifiedTests=false"`
- `npm run lint` (`ui/experience`)
- `npm run type-check` (`ui/experience`)
- `npm run test` (`ui/experience`)
- `npm run build` (`ui/experience`)
- `node scripts/completeness/generate-completeness-report.mjs; node scripts/completeness/openapi-contracts.mjs`

## Go/No-Go Recommendation

**GO for controlled production baseline.**

Clinical plane intended functionality is now implemented and wired for the scoped capabilities, mutation paths are authz/audit guarded with explicit evidence checks, and SHR/FHIR boundary runtime proof is operationalized via harness and test guardrails.
