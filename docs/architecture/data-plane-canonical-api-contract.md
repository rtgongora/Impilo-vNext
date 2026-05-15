# Data Plane Canonical API Contract

## Route Prefixes

- Internal service routes: `/internal/v1/...`
- External governed routes (when explicitly public): `/external/v1/...`
- Experience orchestration routes: `/internal/v1/public-health/*`, `/internal/v1/ai/*`, `/internal/v1/reports/*`

## Required Trust and Context Headers

- `X-Tenant-ID`
- `X-Pod-ID`
- `X-Request-ID`
- `X-Correlation-ID`
- `Authorization: Bearer <jwt>`
- `X-Purpose-Of-Use` for governed data-access/query operations.

Optional contextual headers (where relevant): actor id, facility/workspace id, idempotency key.

## Envelope Rules

- Success:
  - `{ "data": <payload>, "meta": { "request_id": "...", "correlation_id": "..." } }`
- Error:
  - `{ "error": { "code": "TYPED_CODE", "message": "..." }, "meta": { "request_id": "...", "correlation_id": "..." } }`

## Typed Error Mapping

- `400`: validation/header/state errors.
- `401/403`: authz/policy deny.
- `501`: explicit unavailable route (not yet wired).
- `502`: upstream unavailable in BFF orchestration.
- `503`: governance dependency unavailable / fail-safe path.

## Domain Envelope Conventions

- Dataset/data product responses include dataset key/version/governance status when applicable.
- Reporting responses include run id/status/timestamps.
- Surveillance responses include signal/case identifiers and lifecycle status.
- Campaign responses include campaign id/lifecycle status.
- Access governance responses include decision id/policy reference.

## Timestamp Format

- RFC3339 / ISO-8601 UTC for all timestamps.

## Audit and Governance Linkage

- All governed access/decision endpoints must emit auditable identifiers in response payloads or downstream audit events.
- Data-plane BFF routes must preserve request/correlation IDs end-to-end and fail-close on upstream errors.
