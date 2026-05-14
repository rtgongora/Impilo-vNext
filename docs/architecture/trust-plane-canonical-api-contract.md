# Trust Plane Canonical API Contract

Date: 2026-05-14

Convergence evidence matrix: `docs/architecture/trust-api-contract-convergence-matrix.md`

## 1) Canonical Route Policy

- Canonical trust service routes remain service-owned:
  - TSHEPO split services: primary externalized contract currently `/v1/*` with `/internal/v1/*` health/probe surfaces.
  - MVUMO internal orchestration: canonical `/internal/v1/mvumo/*`.
- Legacy compatibility aliases are allowed only where explicitly documented and time-bounded.
- `tshepo-service` routes are compatibility-only and under retirement controls.

## 2) Trust Header Contract

Canonical header names (case-insensitive in HTTP transport):

- `X-Tenant-Id`
- `X-Correlation-Id`
- `X-Actor-Id`
- `X-Actor-Type`
- `X-Purpose-Of-Use`
- `X-Facility-Id` (contextual)
- `X-Workspace-Id` (contextual)
- `X-Shift-Id` (contextual)
- `X-Device-Fingerprint` (contextual)

Compatibility:
- MVUMO continues to accept `X-Actor-Ref` for legacy callers but normalizes to `X-Actor-Id`.

## 3) Error Response Contract

Trust APIs should return deterministic non-success semantics:

- `400` validation/trust-context missing or invalid
- `401` unauthenticated / step-up required where applicable
- `403` policy or consent denial
- `404` unknown resource/session/consent id
- `409` invalid state transition/conflict
- `503` dependency unavailable (never allow-on-error)

Envelope:
- `success=false`
- `error.code`
- `error.message`
- `requestId` or correlation id reference

## 4) Decision Response Contract

Decision-bearing APIs should include:

- `decision` / `verdict` / `status`
- `reason` / `code`
- policy or consent basis metadata when available
- correlation id reference
- timestamp
- audit reference/event id when available

## 5) Current Harmonization Changes (Cutover Pass)

1. MVUMO remote session trust mutations now require:
   - `X-Actor-Id` (or legacy `X-Actor-Ref`)
   - `X-Purpose-Of-Use`
   - `X-Correlation-Id`
2. MVUMO remote session and template endpoints are implemented (no permanent 501 placeholders for intended capabilities).
3. Added static OpenAPI artifact:
   - `contracts/openapi/mvumo.openapi.yaml`
4. Added temporary authz compatibility proxy routes to support tshepo-service consumer migration:
   - `/v1/biometric-policy/evaluate`
   - `/v1/patient-share-policy/evaluate`
   - `/v1/council-regulatory/evaluate`
   - all three routes are now explicitly marked `deprecated: true` in OpenAPI.
5. TSHEPO legacy compatibility security remains authenticated by default for non-public routes.
6. Added runtime full-stack trust harness definitions for cutover evidence:
   - `compose/trust/docker-compose.trust-e2e.yml`
   - `test/integration/trust-fullstack-runtime.sh`
   - `test/integration/trust-fullstack-runtime.ps1`

## 6) Compatibility and Deprecation Rules

- Existing callers to legacy route contracts remain temporarily supported where required.
- Compatibility routes must emit deprecation telemetry and be tracked for retirement.
- No silent contract breaking for existing consumers; migration must be explicit, tested, and observable.
- Compatibility proxy routes in `tshepo-authz-service` must be treated as transitional and not net-new long-term APIs.
- Compatibility removal authority is governed by `docs/architecture/tshepo-legacy-retirement-checklist.md`.
