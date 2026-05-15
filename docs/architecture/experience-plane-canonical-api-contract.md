# Experience Plane Canonical API Contract

Date: 2026-05-14

## Canonical BFF Route Pattern

- Experience orchestration endpoints are internal:
  - `/internal/v1/*`
- BFF routes should proxy/delegate to backend systems of record; they must not synthesize production-success payloads when dependencies fail.

## Required Header and Context Handling

- `X-Tenant-Id` (required for tenant-scoped routes)
- `X-Request-Id` and `X-Correlation-Id` (required for traceability)
- Trust context headers (actor/purpose/facility/workspace/etc.) where downstream policies require them.

## Error and Failure Semantics

Required semantics for Experience BFF:

- Upstream dependency unavailable: `502` or `503` with typed error code.
- Authentication failure: `401`
- Authorization/policy denial: `403`
- Validation failure: `400`
- Not found: `404`

Do not return empty-success payloads (`200` + empty data) for upstream dependency errors on production routes.

## Contract Hardening Completed In This Pass

- `ProviderActivationController`:
  - removed placeholder fallback payloads;
  - added explicit `VARAPI_UNAVAILABLE` / `PROVIDER_NOT_FOUND`.
- `StaffingController`:
  - removed local seeded/synthetic swap/on-call persistence fallback;
  - explicit `TUSO_UNAVAILABLE` fail-close responses.
- `MobileNoticesController`:
  - now backend-wired to VARAPI notices;
  - explicit upstream failure response.
- `ProviderReportsController`:
  - now backend-wired to reporting service;
  - removed stub report payloads.
- `DataAccessGovernanceController`:
  - removed empty-success fallback for policy/request listing;
  - converged to typed `502 DAGS_UNAVAILABLE` envelope.
- `AccessChannelsController`:
  - removed empty-success fallback from GET proxy helpers;
  - converged to typed `502 ACCESS_CHANNEL_UNAVAILABLE` envelope.
- `OmnichannelController`:
  - callback/channel/journey/disclosure handlers now fail-close;
  - converged to typed `502 COMMUNITY_UNAVAILABLE` envelope on upstream errors.
- `PublicHealthController`:
  - GET/POST proxy helpers now fail-close with typed `502` envelopes;
  - removed `200` empty-success fallback behavior on dependency failures.
- `NotificationController`:
  - list/read/preferences paths now fail-close with typed `502` envelopes;
  - removed synthetic empty-success fallback behavior.
- `FinanceController`:
  - billing/payments/refunds/tariffs/claims list paths now fail-close with typed `502 COSTA_UNAVAILABLE` envelopes;
  - removed empty-success compatibility responses.
- `CoverageController`:
  - coverage list/read routes now fail-close with typed `502 COVERAGE_UNAVAILABLE` envelopes;
  - removed empty-success compatibility responses across list operations.
- `IntegrationHubController`:
  - route/deadletter/mapping template paths now fail-close with typed `502 INTEGRATION_HUB_UNAVAILABLE` envelopes.
- `MobileResultsController`, `MobileLabController`, `MobileScheduleController`, `MobileTelemedicineController`:
  - selected mobile clinical list/write actions now fail-close with typed upstream unavailable envelopes (`OROS_UNAVAILABLE`, `TUSO_UNAVAILABLE`, `PCT_UNAVAILABLE`).
- `MobilePrescriptionController`:
  - synthetic create/cancel success removed; now explicit `501` not-implemented envelopes until backend write/cancel endpoints are wired.

## Contract Blockers Remaining

- Error envelope and status semantics are still mixed across the wider BFF controller set.
- Remaining route groups still require portfolio-level convergence validation for header/meta parity and typed dependency errors.
