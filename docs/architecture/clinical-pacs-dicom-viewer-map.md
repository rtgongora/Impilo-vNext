# Clinical PACS, DICOM Viewer, and Imaging Workflow Map

## Ownership

| Capability | Owner |
|---|---|
| Imaging order/result | `oros-service` |
| Imaging study metadata + correlation + viewer session lifecycle | `pacs-adapter-service` |
| DICOM/PACS integration (Orthanc/DICOMweb adapters) | `pacs-adapter-service` |
| UI orchestration and policy-governed access | `experience-bff` |
| Access policy decision + audit authority | `tshepo-authz-service` / `tshepo-audit-service` |
| Patient identity linkage | `vito-service` |
| Encounter linkage | `pct-service` references + OROS/PACS correlation |
| Report/document linkage | `document-service` + PACS report-link references |

## Viewer Launch Flow (Implemented Path)

1. UI requests imaging study/viewer launch via BFF (`/internal/v1/imaging/*`).
2. BFF validates governance policy (`ImagingGovernanceService`) and study-patient match.
3. BFF calls `pacs-adapter-service` for study metadata / hierarchy / viewer session.
4. BFF records imaging access audit event with actor/purpose/facility/correlation.
5. UI receives canonical envelope; no synthetic viewer success is fabricated.

## Route Map

| Layer | Routes |
|---|---|
| Experience BFF (governed) | `/internal/v1/imaging/studies*`, `/internal/v1/imaging/studies/{id}/viewer-sessions`, report-link + hierarchy routes |
| Experience BFF (raw PACS proxy) | `/internal/v1/pacs/*` DICOMweb/Orthanc passthrough for operational imaging tools |
| PACS adapter | `/internal/v1/imaging-studies*` domain APIs |

## Status Matrix

| Area | Status | Blocker |
|---|---|---|
| Study metadata search/list/get | implemented | none |
| Series/instance hierarchy | implemented (bounded) | depends on PACS source completeness |
| Viewer session launch | implemented | viewer client feature depth varies by frontend |
| Study-to-order/report correlation | implemented (bounded) | upstream source synchronization remains iterative |
| Access control and audit | implemented | operational dashboarding depth can be expanded |
| Full production DICOM viewing UX parity | partial | frontend viewer capability roadmap still iterative |

## Explicit Non-Faked Boundary

If PACS adapter or viewer backend is unavailable, BFF routes fail closed with typed upstream errors.
No hardcoded study IDs and no synthetic "viewer launched" success are used in production paths.
