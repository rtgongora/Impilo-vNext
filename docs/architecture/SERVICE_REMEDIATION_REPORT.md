# Service Remediation Report

Final execution report for the activation/remediation program, including the
final hardening wave (frontend surfacing, duplicate reduction posture,
integration/event parity audit, and evidence closure).

## Section 18 Evidence Closure

### 18.1 Final status counts (services-registry baseline)

Total services evaluated: **155**

| Category | Status | Count |
| --- | --- | ---: |
| Remediation | Fixed | 153 |
| Remediation | Partially Fixed | 2 |
| Activation | Activated | 148 |
| Activation | Skeleton | 5 |
| Activation | Duplicated | 2 |
| Contract Alignment | Aligned | 79 |
| Contract Alignment | Not Applicable | 74 |
| Contract Alignment | Partial | 2 |
| Surfacing Quality | Complete | 110 |
| Surfacing Quality | Not Required | 45 |
| Integration Status | Integrated | 110 |
| Integration Status | Standalone | 45 |
| Implementation Status | live | 148 |
| Implementation Status | skeleton | 5 |
| Implementation Status | deprecated | 2 |

### 18.2 Owner-decision closure state

| Metric | Count |
| --- | ---: |
| `owner_decision_required: true` | 0 |
| Remaining policy-blocked items | 0 |

### 18.3 Final hardening wave issue counts (this wave)

| Hardening Category | Fixed | Partially Fixed | Deferred | Needs Owner Decision |
| --- | ---: | ---: | ---: | ---: |
| Frontend surfacing discoverability gaps | 10 | 0 | 0 | 0 |
| Duplicate client/component consolidation | 2 | 2 | 0 | 1 |
| Integration/event contract parity | 4 | 0 | 0 | 0 |
| **Total (wave-level)** | **16** | **2** | **0** | **1** |

## Hardening Wave Changes Applied

### A) Exhaustive frontend surfacing hardening

Implemented in `ui/one-ui-shell`:

- Registered `/dags` and `/dags/policy` in guarded route definitions.
- Added missing work-rail surfacing for Telemedicine, Secure Messaging, Bed Management, and Institutional ERP.
- Added Public Health surfacing in professional navigation and command/app registry.
- Added Intelligence link in life-zone navigation.
- Added command registry coverage for Product Registry and DAGS.
- Extended Admin hub cards for operations admin pages already routed.
- Extended Registry hub cards for trust, consent, locality-review, and facility lifecycle surfaces.

### B) Duplicate consolidation posture (safe reductions)

- Updated duplication register to include service-level alias duplicates and
  frontend duplicate client/component families.
- Kept destructive refactors out of this pass; applied only safe consolidation
  posture updates and explicit extraction candidates.
- Alias duplicates remain on approved sunset path with canonical routing retained.

### C) Integration/event parity audit and targeted fixes

- Added event-topology relationship guidance:
  - `contracts/async/README.md` (new)
  - updated `contracts/asyncapi/README.md`
  - updated `contracts/async/impilo-events.asyncapi.yaml`
- Added parity drift evidence and reconciliation notes:
  - `docs/architecture/kafka-event-catalog.md`
  - `docs/plan/EVENTING_AND_TOPICS.md`
  - `docs/architecture/SERVICE_INTEGRATION_MAP.md`
  - `services/surveillance-service/README.md`
- Enriched `docs/architecture/services-registry.yaml` with concrete
  `events_published`, `events_consumed`, and `event_evidence` for 10
  event-active services.

## Residual Risks and Follow-through

### Runtime-risk changes intentionally deferred

- No runtime Kafka topic renames or consumer retargeting were executed in this
  hardening wave (to avoid regression risk without cross-service test windows).
- Parity drifts remain documented for controlled follow-on slices:
  - none on the four targeted rails after final convergence pass
- Execution sequence, rollback gates, and phase-by-phase closure checkpoints are
  now codified in `docs/architecture/EVENT_CONTRACT_PARITY_CONVERGENCE_PLAN.md`.

### Remaining partially fixed items

- `referral-service` and `analytics-pipeline-service` remain `Partially Fixed`
  pending concrete runtime-backed contract evidence.

## Validation Evidence

Validation and checks executed in this wave:

- service-registry status recomputation from `services-registry.yaml`
- frontend TypeScript/type-check validation for `one-ui-shell`
- architecture registry validator execution (`validate-service-registry.py`)

All policy closures requested in this wave are now encoded in source and docs.

## PACS/DICOM Focused Remediation (May 2026)

- Orthanc runtime hardening: compose healthchecks, readiness probe coverage, and startup failure visibility.
- PACS adapter hardening: provider abstraction layer, viewer-engine policy, viewer-launch-context endpoint, and ops status/unmatched/failed-correlations/failed-writebacks APIs plus expanded OpenAPI.
- OROS/BUTANO hardening: PACS event envelope compatibility, ImagingStudy timeline/visit-summary inclusion, and FHIR gateway clinical allowlist update.
- EHR surfacing hardening: imaging route registry entries, PACS workflow discoverability improvements, and admin system-monitor imaging ops cards.
- Dedicated evidence and remaining backlog: `docs/architecture/PACS_DICOM_PIPELINE.md`.

## Telemedicine/Virtual Care Focused Remediation (May 2026)

- Corrected an unsafe duplicate class definition in `MvumoServiceClient` (compile/runtime hygiene hardening).
- Hardened teleconsult orchestration behavior:
  - list filtering now supports patient/referrer scoping with status filtering behavior,
  - decline flow now routes through canonical referral response API instead of hard `501`,
  - asynchronous note/message submission now uses referral response rails,
  - message retrieval now returns referral response/message thread where available.
- Hardened provider telemedicine worklist handling by applying provider/referral query filtering to returned session payloads.
- Hardened citizen telehealth session end flow to return canonical upstream payloads (instead of synthetic completion object).
- Added telemedicine contract coverage in:
  - `contracts/openapi/experience-bff.openapi.yaml`
  - `contracts/openapi/mobile-provider.openapi.yaml`
  - `contracts/openapi/mobile-citizen.openapi.yaml`
- Published canonical status snapshot and backlog in `docs/architecture/TELEMEDICINE_PIPELINE.md`.

## Telemedicine + Document Management Neutrality Refinement (May 2026)

- Telemedicine:
  - Added provider-neutral session provisioning in `pct-service` (`MANAGED_PRIMARY`, `ASYNC_NO_VIDEO`, `MANUAL_PHONE`).
  - Mobile provider/citizen telehealth flows now support explicit `sessionProvider` routing hints.
  - Updated telehealth contracts with typed provider-neutral request/response fields.
- Document management:
  - Introduced provider-neutral binary storage contract in `document-service` (`ObjectStorageProvider` + router; current adapter `MINIO`).
  - Added document preview endpoint contract and BFF client support.
  - Added canonical document capability status matrix in `docs/architecture/DOCUMENT_MANAGEMENT_PIPELINE.md`.

## Simba + Wellness + Personal Health Data Refinement (May 2026)

- Reclassified boundary posture:
  - `simba-service` treated as canonical wellness/personal-health-data source-of-record.
  - `wellness-service` treated as compatibility alias during migration.
- Implemented Simba wellness personal-data runtime APIs:
  - connected source registry and source permission controls,
  - manual reading ingestion,
  - citizen/provider wellness summaries,
  - remote monitoring alert creation and provider review.
- Added persistence support for source governance and alert operations:
  - `wellness_connected_sources`,
  - `wellness_source_access_audit`,
  - `wellness_remote_alerts`.
- Fixed integration drift and surfacing wiring:
  - BFF wellness base URL now supports both `WELLNESS_BASE_URL` and `WELLNESS_SERVICE_BASE_URL`,
  - mobile provider vitals monitor path aligned to the implemented BFF route.
- Added architecture evidence file:
  - `docs/architecture/SIMBA_WELLNESS_LIFESTYLE_ASSESSMENT.md`.
- Closed remaining partials in this scope:
  - expanded `simba.openapi.yaml` to reflect implemented wellness endpoints,
  - rewired one-ui-shell wellness goals/clubs/diet/programs/routes pages away from demo-only data to runtime APIs.
