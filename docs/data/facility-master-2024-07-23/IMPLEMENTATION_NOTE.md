# Facility Master Pack — Implementation Note (Phase 1)

## Existing structures (preserved)

| Layer | Ownership | Key identifiers |
|-------|-----------|-----------------|
| Tuso `tuso.facility` | Authoritative facility registry | `id`, `facility_code`, `gofr_id`, `metadata` JSONB |
| Tuso `facility_identifier` | Cross-system IDs | `system` + `value` (new: `MASTER_FACILITY_UID`) |
| Tuso `facility_contact` | Operational phones (access-controlled) | Not exposed on BFF live facility list |
| Ndila `ndila_locations` | Geospatial index | `owner_service=TUSO`, `owner_entity_id=facility id` |
| BFF `/internal/v1/facilities` | Discovery proxy | Summary only — no contact phones in live mode |
| BFF `/internal/v1/registry-intake` | Governed import orchestration | CSV + new `FACILITY_MASTER_PACK` JSON path |

## Pack identifiers

- **`facility_uid`** (`mhf-*`): stable import key stored as Tuso identifier `MASTER_FACILITY_UID`.
- **`facility_code`**: national code when present; 123 missing, 39 duplicates in source — importer warns/skips duplicates unless reconcile enabled.
- **Tuso canonical `id`**: assigned on first import; Ndila `owner_entity_id` uses this after sync.

## Import paths (new)

1. **Generated artefacts** — `docs/data/facility-master-2024-07-23/generated/` (recreated via `scripts/data/extract-facility-master-from-pdf.py`).
2. **Tuso API** — `POST /v1/internal/facilities/import/master-pack` (dry-run + execute, idempotent by `facility_uid`).
3. **Ndila API** — `POST /api/v1/ndila/facilities/sync-master-seed` + `ndila_geocode_review_queue` for missing coordinates.
4. **BFF governed import** — `FACILITY_MASTER_PACK` import type on `/internal/v1/registry-intake/import-jobs` with `inlineJson`.

## What must not break

- Existing `FACILITY_CSV` import on registry intake.
- GOFR sync (`GofrSyncAdapter`) — parallel path, not replaced.
- BFF facility discovery stub/live modes.
- Contact masking via `FacilityRepresentation` and BFF mappers.
- Ndila spatial search — excludes null coordinates (unchanged).

## Data quality handling

- Missing coordinates → searchable in Tuso; Ndila review queue; not used for nearest/routing until resolved.
- Missing codes → synthetic `MHL-{uid-suffix}` with metadata flag.
- Duplicate codes → skip by default; optional reconcile via `reconcileDuplicateCodes=true`.
- Closed/inactive → `status=INACTIVE`, `operational_status=NON_OPERATIONAL`.

## Service integration points

Facilities imported through this pack are consumed by existing APIs:

- **Vito/Varapi/Nhume/Telemedicine/Oros/Simba/Indawo/Nompilo** — via Tuso search + Ndila spatial endpoints (no frontend mock arrays).
- **Maps** — `FacilitiesGeoMapPanel`, mobile `FacilityDirectoryScreen` use BFF `/internal/v1/facilities` + Ndila layers.
