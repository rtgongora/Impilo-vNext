# PACS and DICOM Pipeline Remediation

Focused remediation evidence for the Impilo vNext imaging pipeline:

Orthanc PACS -> PACS Adapter -> OROS Imaging Orders -> BUTANO/FHIR writeback ->
EHR/provider viewer -> audit/security -> ops monitoring.

## Current Pipeline Status

| Step | Status | Notes |
| --- | --- | --- |
| Orthanc PACS | Configured But Untested | Docker compose definitions exist with ports/volume; runtime healthcheck and readiness checks added in this run. |
| PACS Adapter service | Functional | Buildable service with study lifecycle APIs, provider abstraction boundary, viewer policy, and ops/status endpoints. |
| OROS imaging order integration | Functional | Imaging order type and PACS-event consumption exist; envelope compatibility hardened in this run. |
| Study/order/patient correlation | Partial | Correlation endpoints and pending placeholders exist; unmatched/failed visibility added for ops. |
| BUTANO/FHIR writeback | Partial | ImagingStudy archival from PACS events is implemented; DiagnosticReport graphing remains follow-on. |
| EHR/provider imaging surfacing | Partial | EHR imaging routes/viewer pages exist; route registry and PACS workflow discoverability improved. |
| DICOM viewer launch | Functional (custom shell) | Governed viewer-session + viewer-launch-context APIs; viewer engine selection is policy-driven (default DICOMweb stack). |
| Audit/security | Partial | Governance/audit events already in place; actor resolution now prefers trust headers where present. |
| Ops monitoring | Functional | PACS adapter ops endpoints are now surfaced in admin system-monitor cards via governed Experience BFF routes. |
| Contracts | Partial | PACS adapter OpenAPI expanded; imaging viewer launch OpenAPI + imaging AsyncAPI added. |
| Tests/smoke checks | Not Tested | Code-level builds/typechecks executed; Orthanc runtime smoke requires local containers. |

## Implemented in This Run

### Orthanc PACS

- Added Orthanc container healthchecks to `docker-compose.yml` and `ops/runtime/docker-compose.shared.yml` using `GET /system`.
- Added non-lite readiness check in `scripts/runtime/wait-for-readiness.sh` for `http://localhost:8042/system`.
- Removed silent Orthanc start failure swallowing in `scripts/runtime/platformctl.sh`.
- Added explicit PACS env alignment in `.env.example`:
  - `ORTHANC_BASE_URL`
  - `ORTHANC_DICOMWEB_URL`

Orthanc local smoke commands:

```bash
curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:8042/system
curl -sS http://127.0.0.1:8042/studies
curl -sS -H "Accept: application/dicom+json" "http://127.0.0.1:8042/dicom-web/studies?limit=1"
```

### PACS Adapter

- Added ops-facing endpoints in `ImagingStudyController`:
  - `GET /internal/v1/imaging-studies/ops/status`
  - `GET /internal/v1/imaging-studies/ops/unmatched-studies`
  - `GET /internal/v1/imaging-studies/ops/failed-correlations`
  - `GET /internal/v1/imaging-studies/ops/failed-writebacks`
- Added Orthanc connectivity probe in `OrthancClient.systemStatus()`.
- Added repository support for unmatched/failed queues in:
  - `ImagingStudyRepository`
  - `EventOutboxRepository`
- Added service-layer aggregation methods in `ImagingStudyService`.
- Added PACS backend abstraction interface (`PacsBackendClient`) with:
  - default Orthanc provider implementation
  - external PACS provider stub (contract-first, no fake runtime support)
  - runtime capability/status metadata surfaced in ops.
- Added viewer-engine policy abstraction (`ViewerEnginePolicy`) and new launch-context endpoint:
  - `GET /internal/v1/imaging-studies/{id}/viewer-launch-context`
- Removed strict required semantics for `ForwardStudyRequest.orthancUrl` (documented compatibility-only hint).
- PACS Adapter Helm defaults hardened (`values.yaml`) with:
  - `ORTHANC_BASE_URL`
  - liveness/readiness probes
  - explicit `SERVER_PORT`

### OROS / BUTANO / FHIR

- Hardened OROS PACS consumer to accept envelope payloads in `OrosEventConsumer`.
- Added ImagingStudy to BUTANO timeline aggregation in `TimelineService`.
- Added ImagingStudy inclusion in encounter visit summary bundles in `VisitSummaryGenerator`.
- Added ImagingStudy to clinical protected-resource set in `GatewayRouteController` for visibility-governed reads.

### EHR / Viewer Surfacing

- Added missing imaging routes to route registries:
  - `ui/one-ui-shell/src/lib/routes.ts`
  - `ui/experience/src/lib/routes.ts`
- Added PACS workflow navigation shortcut in facility cluster:
  - `href: /queue/search?workflow=pacs`
- Added command-palette entry for PACS workflow in both shell registries.
- Added viewer shell context banner showing governed viewer engine + backend provider metadata.
- Added admin system-monitor imaging ops cards (status, unmatched, failed correlations, failed writebacks).
- Added writeback retry workflow:
  - `POST /internal/v1/imaging-studies/ops/failed-writebacks/{outboxId}/retry`
  - `POST /internal/v1/imaging-studies/ops/failed-writebacks/retry-all`
  - corresponding governed Experience BFF proxies.

## Contract Updates

- Expanded `contracts/openapi/pacs-adapter.openapi.yaml` to include implemented study/search/series/instances/sync/viewer/link/ops APIs.
- Added `contracts/openapi/imaging-viewer-launch.openapi.yaml` for viewer launch context contract.
- Added `contracts/asyncapi/imaging-pipeline.asyncapi.yaml` for imaging event rails.
- Updated `contracts/asyncapi/README.md` inventory with imaging pipeline spec.
- Updated OROS internal writeback wording in `contracts/openapi/oros.openapi.yaml` to match current ServiceRequest behavior.
- Added Experience imaging endpoint references in `contracts/openapi/experience-bff.openapi.yaml`.

## Security and Governance Notes

- No direct clinical UI link to raw Orthanc admin explorer was added.
- Imaging viewer launch remains routed through governed Experience endpoints.
- Viewer launch denials now emit explicit governed audit events (`IMAGING_VIEWER_ACCESS_DENIED`) in Experience BFF.
- Trust header actor context is now preferred in PACS adapter audit actor resolution.
- Remaining gap: full TSHEPO policy semantics and consent-aware denials require end-to-end runtime verification with auth stack up.

## Embedded / External / Hybrid Configuration

Runtime configuration is now explicitly documented and contract-backed:

- Embedded mode:
  - `PACS_BACKEND_MODE=EMBEDDED`
  - `PACS_BACKEND_PROVIDER=ORTHANC`
  - `ORTHANC_BASE_URL=http://localhost:8042`
- External PACS mode:
  - `PACS_BACKEND_MODE=EXTERNAL`
  - `PACS_BACKEND_PROVIDER=EXTERNAL`
  - `PACS_EXTERNAL_BASE_URL=https://pacs.example.org`
- Hybrid mode:
  - `PACS_BACKEND_MODE=HYBRID`
  - provider selection is policy/routing driven per facility/workspace, with Orthanc as safe default fallback.

Viewer engine policy is also configurable:

- `IMAGING_VIEWER_DEFAULT_ENGINE=DICOMWEB_STACK`
- `IMAGING_VIEWER_SUPPORTED_ENGINES=DICOMWEB_STACK,OHIF,CORNERSTONE`

## Remaining Backlog

1. Add dedicated Orthanc deployment/service manifests for Kubernetes (or document external managed Orthanc endpoint) to close config drift.
2. Implement explicit BUTANO DiagnosticReport writeback linkage from imaging result lifecycle (ServiceRequest linkage integrity).
3. Implement concrete external PACS/VNA connector (provider abstraction exists; runtime connector stub only in this run).
4. Add integration tests for PACS ops endpoints and OROS envelope PACS event parsing.
5. Validate and document TSHEPO break-glass policy behavior for imaging viewer launch/access denied.
6. Complete OHIF/Cornerstone production-grade integration behind the viewer policy abstraction.

## Safety Statement

This run applied non-destructive, runtime-safe wiring and observability improvements.
No destructive schema migrations, no service replacement, and no bypass of trust/audit controls were introduced.
