# Registry Plane Canonical API Contract

Date: 2026-05-14

## Route Pattern Policy

- Internal mutation/ops APIs: prefer `/internal/v1/*`.
- Public lookup APIs: may expose `/v1/public/*` where explicitly intended.
- FHIR interoperability routes remain under `/v1/fhir/*` where applicable.

## Canonical Trust Header Contract

Registry services must accept and propagate (as applicable):

- `X-Tenant-Id`
- `X-Correlation-Id`
- `X-Actor-Id`
- `X-Actor-Type`
- `X-Purpose-Of-Use`
- `X-Facility-Id` (contextual)
- `X-Workspace-Id` (contextual)
- `X-Shift-Id` (contextual)
- `X-Device-Fingerprint` (contextual)

## Error Contract

Registry APIs should converge on deterministic semantics:

- `400` invalid request/validation failure
- `401` unauthenticated
- `403` unauthorized/policy denied
- `404` not found
- `409` conflict (duplicate key, invalid state transition)
- `422` semantically invalid domain payload
- `503` dependency unavailable

Target envelope consistency:

- `success` flag where service-standard applies
- machine-readable error code
- human-readable message
- correlation/request reference

## Lookup and Mutation Response Policy

- Lookup endpoints return authoritative SoR payloads only (no synthetic fallback records).
- Mutation endpoints return stable identifiers and status metadata.
- Soft-deactivation and reconciliation paths must preserve auditability and deterministic state transitions.

## Audit Reference Behavior

Sensitive registry mutations should emit audit evidence with:

- actor
- action
- resource type/id
- tenant/purpose
- timestamp
- correlation id

Where a service uses outbox/event audit pattern, response payloads should include traceable identifiers suitable for downstream audit linkage.

## Current Convergence Status (Registry)

- Detailed matrix: `docs/architecture/registry-api-contract-convergence-matrix.md`.
- Header handling: **substantial-to-partial (service-dependent)**.
- Error envelope: **partial but now explicitly tracked by service gate**.
- Route conventions: **partial with bounded compatibility paths** (`/v1`, `/internal/v1`, and public routes coexist).
- Audit linkage: **partial** (outbox/audit metadata patterns not fully uniform across all registry services).

## BFF Contract Enforcement Updates

- Registry BFF facility and geo routes now fail closed on TUSO dependency failure (`502` + typed error envelope) instead of returning synthetic-success payloads in live mode.
- Route-level tests are present for:
  - `FacilityController` live success and fail-close behavior
  - `RegistryGeoLocalityController` upstream-failure envelope behavior
