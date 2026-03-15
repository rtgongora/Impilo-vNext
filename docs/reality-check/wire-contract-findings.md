# Wire Contract Findings — Impilo vNext

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Risk Class: B — Contracts consistent in docs but not on the wire

## Executive Summary

Wire contracts are **well-defined and consistently enforced** through the tech-companion library. The GoldenContractSuite provides 67/67 service coverage for header enforcement, idempotency, and federation authority tests. The primary limitation is that these are **structurally verified** (present in source) but **not dynamically executed** in this environment (requires Maven + Spring context).

## Contract Architecture

### Trust Headers (CompanionHeaders.java)

4 HARD_REQUIRED headers enforced by V11HeaderFilter on all `/internal/v1/**` and `/external/v1/**` paths:

| Header | Java Constant | Required | Enforcement |
|---|---|---|---|
| `X-Tenant-ID` | `TENANT_ID` | Yes (HARD) | 400 if missing |
| `X-Pod-ID` | `POD_ID` | Yes (HARD) | 400 if missing |
| `X-Request-ID` | `REQUEST_ID` | Yes (HARD) | 400 if missing |
| `X-Correlation-ID` | `CORRELATION_ID` | Yes (HARD) | 400 if missing |
| `Idempotency-Key` | `IDEMPOTENCY_KEY` | On POST/PUT/PATCH | 400 if missing on commands |
| `X-Client-Timeout-MS` | `CLIENT_TIMEOUT_MS` | Optional | Used for deadline propagation |
| `X-Policy-Decision` | `POLICY_DECISION` | Set by TSHEPO | Downstream read-only |
| `X-Policy-Version` | `POLICY_VERSION` | Set by TSHEPO | Downstream read-only |
| `X-Decision-Reason` | `DECISION_REASON` | Set by TSHEPO | Downstream read-only |

### Error Envelope (ErrorEnvelope.java)

Standard error response format with 5 required fields:

```json
{
  "error": {
    "code": "MISSING_REQUIRED_HEADER",
    "message": "X-Tenant-ID header is required",
    "details": {},
    "request_id": "...",
    "correlation_id": "..."
  }
}
```

### Error Codes (ErrorCodes.java)

| Code | HTTP Status | Trigger |
|---|---|---|
| `MISSING_REQUIRED_HEADER` | 400 | Any of 4 HARD_REQUIRED headers missing |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | POST/PUT/PATCH without Idempotency-Key |
| `IDENTITY_CONFLICT` | 409 | Same Idempotency-Key, different body |
| `FEDERATION_AUTHORITY_VIOLATION` | 403 | Pod lacks authority for requested scope |
| `CLIENT_TIMEOUT_EXCEEDED` | 408 | Request exceeded X-Client-Timeout-MS |

## Findings

### What Is Proven

| Check | Status | Evidence |
|---|---|---|
| CompanionHeaders defines all headers | PASS | Source inspection of `libs/tech-companion/.../CompanionHeaders.java` |
| V11HeaderFilter enforces HARD_REQUIRED | PASS | Filter checks `isBlank()` for all 4, returns 400 |
| ErrorEnvelope has all 5 fields | PASS | code, message, details, request_id, correlation_id |
| IdempotencyFilter returns 409 on conflict | PASS | Checks IDENTITY_CONFLICT on body hash mismatch |
| FederationAuthority enforces scope levels | PASS | `requireNational()`, `requireProvincial()`, `requireDistrict()` |
| GoldenContractSuite coverage | PASS | 67/67 services have GoldenContractIT extending base suite |
| GoldenContractSuite tests per-header | PASS | Individual test per missing header + all-four-missing test |

### What Is Not Proven in This Environment

| Check | Status | Reason |
|---|---|---|
| Actual 400 response on missing headers | UNVERIFIED | Requires running services |
| Actual 409 on idempotency conflict | UNVERIFIED | Requires running services |
| Actual 403 on federation violation | UNVERIFIED | Requires running services |
| TypeScript ↔ Java header name parity | PARTIAL | TypeScript contracts found in `ui/one-ui-shell/src/lib/apiClient.ts` — injects trust headers, but exact set comparison requires manual review |

### Contract Risks

1. **TypeScript header injection**: The UI's `apiClient.ts` injects headers, but there's no compile-time guarantee that the header names match the Java constants exactly. This is enforced by convention + GoldenContractIT.

2. **External gateway passthrough**: Envoy is configured to pass headers through, but the exact ext_authz → service header propagation chain requires live testing.

## Validation Script

See: `scripts/reality-check/run-wire-checks.sh`

Supports `--live` flag for testing against running services.

## Verdict

**WIRE CONTRACTS: STRUCTURALLY SOUND, DYNAMICALLY UNVERIFIED**

The contract definitions, enforcement filters, and test suites are comprehensive and consistent. All 67 services have GoldenContractIT. Dynamic verification requires Maven builds + running infrastructure.
