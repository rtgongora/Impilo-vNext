# PACS / DICOM imaging layer (Impilo vNext)

This document describes the **governed imaging** foundation added across the PACS adapter, Experience BFF, and clinical UI.

## Bounded contexts

- **pacs-adapter-service** — canonical imaging metadata (study / series / instance), Orthanc hierarchy sync, archive object references, viewer sessions, access audit rows, report and order links, and Kafka outbox events (`imaging.*`, `pacs.*`).
- **experience-bff** — `/internal/v1/imaging/*` Experience-facing API with `{ success, data }` envelopes, patient–study binding checks (`chart_patient_cpid`), and clinical-role enforcement aligned with URL RBAC.
- **Orthanc** (via **experience-bff** `PacsController`) — DICOMweb QIDO/WADO and Orthanc REST previews; not exposed directly to browsers.

## Domain tables (schema `pacs`)

- `imaging_study` — extended with encounter/facility/archive/report/source/body-part and coding columns (Flyway `V003`).
- `imaging_series`, `imaging_instance` — DICOM hierarchy and `storage_ref` pointing at DICOMweb-style paths (`studies/{StudyUID}/series/{SeriesUID}/instances/{SOP}`).
- `imaging_archive_object` — storage linkage (`ORTHANC_DICOMWEB`).
- `imaging_viewer_session`, `imaging_access_audit` — viewer launch and access trail.
- `imaging_report_link`, `imaging_order_link` — workflow anchors beyond a single `oros_order_id` column.

## Primary HTTP surfaces

| Area | Path |
|------|------|
| Experience (UI) | `/internal/v1/imaging/studies`, `/search`, `/studies/{id}/series`, … |
| Sovereign adapter | `/internal/v1/imaging-studies`, … |
| DICOMweb proxy | `/internal/v1/pacs/dicomweb/...` |

## UI flows

- **EHR imaging workspace** — `ui/experience/src/app/ehr/[patientId]/imaging/page.tsx` combines Orthanc-backed previews with governed registry rows; launches **DICOMweb viewer** and **hierarchy sync**.
- **DICOMweb viewer** — `.../imaging/viewer?studyUid=...&governedStudyId=...` loads series/instances via QIDO and frames via WADO-RS rendered PNGs with client-side window/level (CSS) and zoom.

## Tshepo / trust

- Spring Security **clinical roles** gate `/internal/v1/imaging/**` and `/internal/v1/pacs/**`.
- `ImagingAccessPolicyService` adds **chart vs study patient** checks when `chart_patient_cpid` is supplied.
- Outbox events support downstream Tshepo audit projection (`imaging.viewer.launched`, `imaging.access.recorded`, etc.).

## Assumptions and gaps

- Orthanc must be reachable from **pacs-adapter** for `sync-hierarchy`; `forwardStudy` still assigns a placeholder `orthanc_id` in dev — real ingest should set Orthanc’s study UUID.
- Tshepo **authz policy engine** is not yet called per imaging action; the BFF gate is role + optional patient binding.
- Full **CORS** stack scrolling across multi-frame volumes** uses frame `1` only in the viewer MVP; extend to `NumberOfFrames` per instance.
- **H2 / golden contract** tests do not run Flyway; integration against PostgreSQL validates migrations.

## Manual testing

1. Start PostgreSQL (`pacs` DB), Kafka (optional), Orthanc, pacs-adapter, experience-bff, Experience UI.
2. `POST /internal/v1/imaging-studies` (via BFF or adapter) with `tenantId`, `patientCpid`, `studyUid`, `modality`, `studyDate`, and a real `orthanc_id` from Orthanc.
3. `POST /internal/v1/imaging/studies/{id}/sync-hierarchy?chart_patient_cpid=...` — verify series/instance rows in `pacs` schema.
4. Open `/ehr/{patient}/imaging` — governed block shows **DICOMweb viewer**; viewer loads series list.
5. `POST /internal/v1/imaging/studies/{id}/viewer-sessions` — verify `imaging_viewer_session` and outbox `imaging.viewer.launched`.
