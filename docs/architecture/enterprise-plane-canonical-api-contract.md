# Enterprise Plane Canonical API Contract

Date: 2026-05-15

## Route Prefix Policy

- Enterprise BFF routes: `/internal/v1/finance/*`, `/internal/v1/coverage/*`, `/internal/v1/marketplace/*`, `/internal/v1/erp/*`.
- Upstream enterprise services remain service-owned (`/internal/v1/...` or service-native prefixes such as COSTA and MusheX namespaces).

## Required Context Headers

- `X-Tenant-ID`
- `X-Pod-ID`
- `X-Request-ID`
- `X-Correlation-ID`
- `Authorization`
- actor/facility/workspace companion headers as applicable.
- `X-Idempotency-Key` for duplicate-risk mutations (payments/orders/claims where supported).

## Response Envelope Conventions

Success:

```json
{
  "data": {},
  "meta": {
    "request_id": "req-...",
    "correlation_id": "corr-..."
  }
}
```

Failure (typed, fail-close):

```json
{
  "error": {
    "code": "UPSTREAM_UNAVAILABLE",
    "message": "Human-readable failure reason"
  },
  "meta": {
    "request_id": "req-...",
    "correlation_id": "corr-..."
  }
}
```

## Enterprise Mutation Rules

- No synthetic success on upstream failure.
- No silent empty-success fallback for list/read paths when upstream fails.
- Idempotency key should be forwarded where upstream supports duplicate protection.
- Financial mutations must remain auditable through propagated request/correlation metadata.

## Current Pass Convergence Notes

- Marketplace order-create fallback replaced with typed `502 MSIKA_FLOW_UNAVAILABLE`.
- Provider financing list routes now fail-close with typed `502 COVERAGE_UNAVAILABLE`.
- Mobile provider billing placeholder success routes converted to explicit `501 BILLING_ROUTE_UNAVAILABLE`.
- Coverage service production security now requires authentication for non-actuator routes.
- Long-tail enterprise parity hardening added typed `502` fail-close envelopes for:
  - `ErpHrBffController` (`HR_PAYROLL_UNAVAILABLE`)
  - `ErpProcurementBffController` (`PROCUREMENT_UNAVAILABLE`)
  - `PatientAccountFinanceBffController` (`COSTA_UNAVAILABLE`)
  - `PaymentPlanFinanceBffController` (`COSTA_UNAVAILABLE`)
- Runtime proof harness added at `test/integration/enterprise-fullstack-runtime.(sh|ps1)` with explicit enterprise health/fail-close assertions.
