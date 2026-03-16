# Compliance-on-the-Wire Results

## Environment
- Docker Daemon: NOT AVAILABLE
- Status: BLOCKED_EXTERNAL — scripts implemented, awaiting runtime

## Intended Checks

| Check | Endpoint/Service | Expected Result | Actual Result | Evidence | Blocker |
|-------|-----------------|----------------|---------------|----------|---------|
| Missing required header -> 400 | VARAPI GET /api/v1.1/providers (no X-Tenant-ID) | 400 MISSING_REQUIRED_HEADER | NOT TESTED | Script ready | No Docker daemon |
| Missing Idempotency-Key -> 400 | VARAPI POST /api/v1.1/providers (no key) | 400 IDEMPOTENCY_KEY_REQUIRED | NOT TESTED | Script ready | No Docker daemon |
| Same key + same body -> replay | VARAPI POST /api/v1.1/providers | Same status + body | NOT TESTED | Script ready | No Docker daemon |
| Same key + different body -> 409 | VARAPI POST /api/v1.1/providers | 409 IDENTITY_CONFLICT | NOT TESTED | Script ready | No Docker daemon |
| Wrong pod -> 403 | TSHEPO federation endpoint (X-Pod-ID=private-harare) | 403 FEDERATION_AUTHORITY_VIOLATION | NOT TESTED | Script ready | No Docker daemon |

## Code-Level Evidence (static verification)

### Header Enforcement
GoldenContractSuite (`libs/tech-companion-harness`) tests header enforcement for every service that extends it. The TrustContextFilter in shared-core checks for mandatory headers (X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID) and returns 400 with error envelope format.

### Idempotency
GoldenContractSuite tests missing Idempotency-Key -> 400 and same-key-different-body -> 409 for all services with command endpoints.

### Federation
GoldenContractSuite tests private pod -> 403 FEDERATION_AUTHORITY_VIOLATION for services with federation-gated endpoints. The federation-connector lib (COMPLETE, 13 src + 4 tests) provides pod identity verification.

### Error Envelope
All services use the standardized error envelope format: `{ "error": { "code", "message", "details", "request_id", "correlation_id" } }`.

## Runtime Verification Script
`scripts/runtime-validation/run-wire-compliance.sh` — ready to execute when Docker is available.

## Blocker
No Docker daemon. Cannot boot services to test wire-level behavior.
