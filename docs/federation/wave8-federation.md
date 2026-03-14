# Wave 8 — Federation Identity & Authority

## Overview

Wave 8 makes the Impilo platform federation-ready by enforcing pod identity verification
on all v1.1 API paths (`/internal/v1/**`, `/external/v1/**`).

## Architecture

```
┌────────────┐    ┌─────────────────────┐    ┌──────────────┐
│  Pod (Edge) │───▶│  FederationIdentity │───▶│ V11HeaderFilter│───▶ Service
│  X-Pod-ID   │    │  Filter (order 9)   │    │ (order 10)     │
└────────────┘    └─────────────────────┘    └──────────────┘
```

### Filter Chain Order

| Order | Filter                    | Purpose                              |
|-------|---------------------------|--------------------------------------|
| 9     | FederationIdentityFilter  | Pod identity verification (JWT/mTLS) |
| 10    | V11HeaderFilter           | Mandatory header enforcement         |
| 11    | IdempotencyFilter         | Idempotency-Key enforcement          |
| 12    | TimeoutEnforcementFilter  | Client timeout enforcement           |
| 13    | ConsistencyClassFilter    | Consistency class A/B/C enforcement  |

## Components

### FederationIdentityFilter (`libs/federation-connector`)

- **Scope**: `/internal/v1/**` and `/external/v1/**` only
- **National bypass**: Requests with `X-Pod-ID: national` skip verification
- **Verification flow**:
  1. Extract `X-Pod-ID` header and Bearer JWT token
  2. Delegate to `PodIdentityVerifier.verify(certificate, jwtToken)`
  3. Verify JWT `aud` contains `"federation"`
  4. Verify JWT `pod_id` claim matches `X-Pod-ID` header
  5. On failure → 403 with `FEDERATION_IDENTITY_INVALID` error envelope

### DefaultPodIdentityVerifier

- Validates JWT audience contains `"federation"` via `JwtAudienceVerifier`
- Extracts `pod_id` from JWT claims (fallback: `azp`, then `sub`)
- Optional mTLS certificate CN validation when `X-Client-Certificate` header is present

### FederationAutoConfiguration

- Spring Boot auto-configuration at `libs/federation-connector`
- Registers via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Conditional on `impilo.federation.enabled` (default: true)
- Services override by providing their own `PodIdentityVerifier` bean

## Adopted Services

### Ring 0 (Core Platform)
- TSHEPO, VITO, TUSO, VARAPI, ZIBO, MSIKA, BUTANO, MUSHEX

### Ring 1 (Extended Services)
- PCT, OROS, Pharmacy, COSTA (costing-engine)

## Error Codes

| Code                           | HTTP | Description                           |
|--------------------------------|------|---------------------------------------|
| FEDERATION_IDENTITY_INVALID    | 403  | Pod identity verification failed      |
| FEDERATION_AUTHORITY_VIOLATION | 403  | Non-national pod attempted national op|

## Configuration

```yaml
# Disable federation identity verification (e.g., for local dev)
impilo:
  federation:
    enabled: false
```

## Testing

See `FederationIdentityFilterTest` in `libs/federation-connector/src/test/`.
Covers: national bypass, verified pod, unknown pod, wrong audience, pod ID mismatch.
