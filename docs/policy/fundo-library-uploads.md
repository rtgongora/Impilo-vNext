# Fundo Library Uploads & Metadata — Integration Note

Status: **metadata REAL; binary upload = consume document-service (BFF), PARTIAL back-compat** (2026-06-26)

## What landed (B1)
`lrn_library_resource` now carries governance metadata: `review_date`, `expiry_date`,
`target_audience`, `cadre`, `programme`, `facility_relevance`, `access_level`
(default INTERNAL), `ai_usage_permission` (default false — gates whether a resource may be
fed to the AI provider, B2). Create/list persist and return these.

## Binary upload — consume document-service, do not fork MinIO
`storage_ref` is a **document-service objectId**, not an opaque blob pointer. The real
upload flow (consume-not-rebuild):
1. UI → BFF `/internal/v1/learning/v11/library/uploads` (multipart)
2. BFF → `document-service POST /v1/internal/objects` → returns `objectId`
3. BFF → learning `POST /library/resources` with `storageRef = objectId` + metadata
4. Download/preview: learning returns `objectId`; BFF fetches a `document-service` signed URL.

learning-service stores **no** object-storage code/config. The BFF→document-service
multipart wiring is the remaining integration step; until it lands, `storageRef` accepts a
pre-obtained objectId (callers that already hold one work today). Legacy opaque
`storage_ref` rows are read back-compat only — **PARTIAL**, recorded honestly.

## AI egress governance
The real AI provider (B2) must honor `ai_usage_permission`: a library resource may be used
as AI source material only when `ai_usage_permission = true`. Default false = no egress.
