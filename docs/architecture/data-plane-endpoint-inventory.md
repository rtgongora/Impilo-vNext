# Data Plane Endpoint Inventory

## experience-bff (Data/Public Health)

- `/internal/v1/public-health/signals|cases|alerts|counters|campaigns`
- `/internal/v1/public-health/weekly-idsr` (wired to surveillance counters aggregate)
- `/internal/v1/public-health/outbreaks` (wired to surveillance signal create)
- `/internal/v1/public-health/field-operations` (wired to surveillance ingest)
- `/internal/v1/ai/*` (new in this pass): models, approvals, versions, inference records, drift events
- `/internal/v1/ai-governance/*` (governance proxy; incident/audit routes explicit `501`)
- `/internal/v1/reports/generate`, `/internal/v1/reports/{id}`, `/internal/v1/admin/reports/jobs`
- `/internal/v1/mobile/provider/governance/*`

## surveillance-service

- `/internal/v1/ingest`
- `/internal/v1/signals` (GET/POST)
- `/internal/v1/cases` (GET)
- `/internal/v1/surveillance/counters` (GET/POST)
- `/internal/v1/surveillance/alerts` (GET)
- `/internal/v1/surveillance/alerts/definitions` (GET/POST)

## campaigns-service

- `/internal/v1/campaigns` (GET/POST)
- `/internal/v1/campaigns/{id}` (GET)
- `/internal/v1/campaigns/{id}/close` (POST)
- `/internal/v1/campaigns/{id}/enroll` (POST)
- `/internal/v1/campaigns/{id}/dispatch` (POST)

## reporting-service

- `/internal/v1/reports` (POST create)
- `/internal/v1/reports/{key}/run` (POST)
- `/internal/v1/reports/{key}/runs` (GET)
- `/internal/v1/reports/{key}/schedules` (POST)
- `/internal/v1/reports/tenant-runs` (GET)

## data-warehouse-service

- `/internal/v1/gold/query`
- `/internal/v1/gold/stats`
- `/internal/v1/gold/materialize`
- `/external/v1/gold/datasets`

## ndr-service

- `/internal/v1/ndr/ingest/events`
- `/internal/v1/ndr/query/bronze`
- `/internal/v1/ndr/build/gold/encounters`
- `/internal/v1/ndr/query/gold/encounters`

## national-data-repository-service

- `/internal/v1/datasets` (GET/POST)
- `/internal/v1/datasets/{key}/versions` (POST)
- `/internal/v1/query` (POST)
