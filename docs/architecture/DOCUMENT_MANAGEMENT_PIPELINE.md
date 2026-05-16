# Impilo vNext Document Management Pipeline

## Purpose
Canonical readiness snapshot for native vNext document-management capability with provider-neutral storage/preview integration and governed linkage to clinical workflows.

## Architecture Position
- vNext owns: metadata, lifecycle state, linkage, routing, access control, audit, and contract surfaces.
- Pluggable engines own: binary storage, OCR/extraction engine, rendering, e-signature, external DMS connectivity.
- Existing sovereign services remain intact (`document-service`, `landela-adapter-service`, `TSHEPO`, `BUTANO`, `PCT`, `OROS`).

## Runtime Components Audited
- `services/document-service`
  - Object metadata lifecycle, hash/dedupe, signed URL, soft delete, scan hook.
  - New provider-neutral storage contract (`ObjectStorageProvider` + router).
  - Current provider implementations: `MINIO`, `LANDELA_ADAPTER`.
  - New preview payload endpoint: `/v1/internal/objects/{objectId}/preview`.
  - New OCR job APIs: `POST/GET /v1/internal/objects/{objectId}/ocr`.
  - New signature job APIs: `POST/GET /v1/internal/objects/{objectId}/signature` and `GET /v1/internal/objects/signatures/{jobId}/verify`.
- `services/landela-adapter-service`
  - Existing dual-mode storage router (`INTERNAL`/`LANDELA`) retained.
- `services/experience-bff`
  - `ClinicalDocumentsController` and `DocumentServiceClient` linkage.
  - Preview client call support added.

## Functional Acceptance Status
1. Document domain service: **Functional**  
2. Storage provider abstraction: **Functional**  
3. MinIO/S3 integration: **Functional**  
4. Landela integration: **Partial** (now unified via provider adapter, pending environment validation)  
5. External DMS abstraction: **Partial**  
6. Upload/download: **Functional**  
7. Preview/viewer: **Functional** (signed preview URL envelope)  
8. OCR/text extraction: **Functional** (provider abstraction + OCR job API with default NOOP/text fallback provider)  
9. E-signature/signing: **Functional** (provider abstraction + signature job + verification APIs with default NOOP provider)  
10. QR/verification: **Partial** (available in adjacent services, not unified in document domain)  
11. Document workflow: **Partial** (lifecycle actions exist; richer review/approval workflow limited)  
12. Metadata/index/search: **Partial**  
13. Patient/encounter linkage: **Functional**  
14. Telemedicine document linkage: **Functional**  
15. Imaging/report document linkage: **Partial**  
16. Referral document linkage: **Functional**  
17. Fundo/certificate linkage: **Partial**  
18. Citizen document surface: **Partial**  
19. Provider/EHR document surface: **Functional**  
20. Admin/workflow console: **Partial**  
21. Ops monitoring: **Partial**  
22. Access control: **Functional**  
23. Audit/security/consent: **Functional**  
24. Contracts: **Partial**  
25. Tests/smoke checks: **Partial**

## Contracts Updated in This Refinement
- `contracts/openapi/document-store.openapi.yaml`
  - Clarified provider-neutral storage posture.
  - Added preview endpoint contract.
  - Added OCR and signature workflow endpoints.

## CI / Integration Stub Profile
- `services/document-service/src/main/resources/application-integration-stub.yml` provides an env-specific wiring profile for adapter-path testing.
- Activate with `SPRING_PROFILES_ACTIVE=integration-stub`.
- In this profile:
  - storage provider defaults to `LANDELA_ADAPTER`
  - OCR provider defaults to `EXTERNAL_STUB`
  - signature provider defaults to `EXTERNAL_STUB`
- Endpoint base URLs and API keys remain overridable via environment variables for CI.

## Remaining Backlog
- Add production OCR provider adapter(s) beyond NOOP and finalize confidence/field-mapping model.
- Add production e-sign provider adapter(s) and TSHEPO keys-backed evidence chain integration.
- Validate Landela adapter mode in integrated environment and publish runbook evidence.
- Expand workflow APIs for review/approval/sign/issue/return stages.
- Add deterministic integration smoke tests for preview/access-denied/audit-failure paths.
