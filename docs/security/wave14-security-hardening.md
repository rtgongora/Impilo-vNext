# Wave 14 — Security Hardening

> Status: Code Complete | Date: 2026-03-14

## Overview

Wave 14 implements security hardening primitives across all Impilo v1.1 services. The hardening layer sits **below** the gateway (Envoy + TSHEPO ext_authz) and provides defense-in-depth at the service level.

### Design Principles

1. **Central policy enforcement respected** — gateway/OPA/PDP remains authoritative; service-level checks are safety nets, not replacements.
2. **No PII leakage** — error messages never include rejected input values or internal identifiers.
3. **Immutable audit** — admin actions produce append-only audit events via the outbox pattern.
4. **No plaintext secrets** — all credentials retrieved via `SecretProvider` abstraction; no secrets in config files.

## Components

### libs/security-baseline

Pure Java 21 library (no Spring dependency). Provides four primitives:

| Component | Package | Purpose |
|-----------|---------|---------|
| `InputSanitizer` | `security.input` | Strict allowlist-based input validation (UUID, text, identifiers, email, phone, event types). Rejects XSS, SQLi, template injection, null bytes. |
| `RateLimitGuard` | `security.ratelimit` | Token-bucket rate limiter with pluggable storage (in-memory default, Redis/JDBC for distributed). |
| `AdminAuditEmitter` | `security.audit` | Emits immutable admin audit events wrapped in v1.1 `EventEnvelope`, persisted to `event_outbox`. |
| `SecretProvider` | `security.secrets` | Contract for runtime secret retrieval. `EnvSecretProvider` for local dev, `VaultSecretProvider` config contract for production. |

### services/shared-core (Spring integration)

| Component | Purpose |
|-----------|---------|
| `RateLimitFilter` | Servlet filter that enforces rate limiting per actor/IP with standard response headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`). Returns 429 with `ApiResponse` error envelope. |
| `SecurityBaselineExceptionHandler` | `@ControllerAdvice` that catches `InputValidationException` → 400 and `RateLimitExceededException` → 429, both wrapped in the standard error envelope. |
| `AdminAuditOutboxSink` | JDBC implementation of `AdminAuditSink` that writes to the service's `event_outbox` table. |

## Adoption

Each service has a `SecurityBaselineConfig` class that wires:

- `RateLimitGuard` bean with service-appropriate token bucket settings
- `RateLimitFilter` registered on `/v1/*` at `HIGHEST_PRECEDENCE + 5`
- `AdminAuditEmitter` connected to the service's outbox via `AdminAuditOutboxSink`
- `SecretProvider` with service-specific env var prefix
- `SecurityBaselineExceptionHandler` for consistent error envelopes

### Service Rate Limits

| Service | Max Tokens (burst) | Refill/sec | Env Prefix |
|---------|-------------------|------------|------------|
| TSHEPO | configurable via `tshepo.rate-limit.max-requests-per-minute` | auto | `IMPILO_TSHEPO_` |
| VITO | 200 | 3 | `IMPILO_VITO_` |
| TUSO | 150 | 2 | `IMPILO_TUSO_` |
| VARAPI | 100 | 2 | `IMPILO_VARAPI_` |
| ZIBO | 150 | 2 | `IMPILO_ZIBO_` |
| MSIKA | 200 | 3 | `IMPILO_MSIKA_` |
| UBOMI | 150 | 2 | `IMPILO_UBOMI_` |
| PCT | 100 | 2 | `IMPILO_PCT_` |
| OROS | 100 | 2 | `IMPILO_OROS_` |
| Pharmacy | 150 | 2 | `IMPILO_PHARMACY_` |
| Inventory | 100 | 2 | `IMPILO_INVENTORY_` |
| MuSHeX | 100 | 2 | `IMPILO_MUSHEX_` |
| Document | 100 | 2 | `IMPILO_DOCSTORE_` |

### Gateway Configuration (Envoy)

The service-level rate limiter is a safety net. Primary rate limiting should be enforced at the Envoy gateway using the `envoy.filters.http.local_ratelimit` filter:

```yaml
# envoy.yaml — rate_limit_filter (document, do not deploy without review)
http_filters:
  - name: envoy.filters.http.local_ratelimit
    typed_config:
      "@type": type.googleapis.com/envoy.extensions.filters.http.local_ratelimit.v3.LocalRateLimit
      stat_prefix: http_local_rate_limiter
      token_bucket:
        max_tokens: 1000
        tokens_per_fill: 100
        fill_interval: 1s
      response_headers_to_add:
        - append_action: OVERWRITE_IF_EXISTS_OR_ADD
          header:
            key: x-ratelimit-limit
            value: "1000"
```

## Input Validation

### Available Validators

| Method | Pattern | Max Length |
|--------|---------|-----------|
| `requireUuid` | UUID v4 (hex + hyphens) | 36 |
| `requireSafeText` | Unicode letters/numbers + safe punctuation | configurable (default 4096) |
| `requireIdentifier` | `[a-zA-Z0-9_.-]` | 255 |
| `requireEventType` | `[a-z][a-z0-9_.]` dot-notation | 128 |
| `requireNumericId` | Positive integer | 19 digits |
| `requireEmail` | Simplified RFC 5321 | 254 |
| `requirePhone` | E.164 format | 16 |

### Injection Detection

All validators reject patterns matching:
- XSS: `<script`, `javascript:`, `on*=`
- Template injection: `${...}`, `{{...}}`
- SQL injection: `UNION SELECT`, `; DROP/DELETE/UPDATE/INSERT/ALTER/EXEC`
- Null byte: `\x00`

### Usage in Controllers

```java
@PostMapping("/v1/policies")
public ApiResponse<PolicyDto> createPolicy(@RequestBody CreatePolicyRequest req) {
    String name = InputSanitizer.requireSafeText(req.name(), "name", 200);
    String code = InputSanitizer.requireIdentifier(req.code(), "code");
    // InputValidationException → 400 via SecurityBaselineExceptionHandler
    // ...
}
```

## Admin Audit

### Emission Pattern

```java
@Autowired AdminAuditEmitter auditEmitter;

public void updatePolicy(PolicyDto dto, TrustContext ctx) {
    // ... perform update ...
    auditEmitter.emit(
        "POLICY_UPDATE",
        ctx.actorId(), ctx.actorType(),
        ctx.tenantId().toString(), ctx.facilityId().toString(),
        ctx.correlationId().toString(),
        "Policy", dto.id(),
        Map.of("old_version", oldVersion, "new_version", newVersion)
    );
}
```

### Event Schema

Admin audit events use the standard v1.1 `EventEnvelope` with:
- `eventType`: `impilo.{service}.admin_audit.{action}.v1`
- `meta.audit_class`: `ADMIN_ACTION`
- `payload.immutable`: `true`
- `payload.action`: the admin action name
- `payload.actor_id`: who performed the action
- `payload.details`: action-specific details (no secrets)

## Secrets Management

### Architecture

```
┌─────────────────┐
│   Application   │
│   Code          │
│                 │
│  SecretProvider  │ ◄── interface
│       │         │
│  ┌────┴────┐    │
│  │ EnvSP   │    │ ◄── local dev (env vars)
│  │ VaultSP │    │ ◄── production (Vault HTTP API)
│  └─────────┘    │
└─────────────────┘
```

### EnvSecretProvider Key Mapping

Keys are transformed: dots → underscores, hyphens → underscores, uppercased, prefixed.

| Secret Key | Env Var (TSHEPO) |
|------------|------------------|
| `db.password` | `IMPILO_TSHEPO_DB_PASSWORD` |
| `jwt.signing-key` | `IMPILO_TSHEPO_JWT_SIGNING_KEY` |
| `kafka.sasl.password` | `IMPILO_TSHEPO_KAFKA_SASL_PASSWORD` |

### VaultSecretProvider Config Contract

For production Vault integration, configure:

```yaml
impilo:
  secrets:
    vault:
      address: https://vault.impilo.internal:8200
      secrets-engine: impilo-kv
      secret-path: services/tshepo
      cache-ttl-seconds: 300
```

The `VaultSecretProvider` class defines the `VaultConfig` record as the contract shape. Actual Vault HTTP client integration is deferred to infrastructure provisioning (non-scope for Wave 14).

## Error Envelope Consistency

All error responses use the standard `ApiResponse` envelope:

```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMITED",
    "message": "Too many requests. Retry after 5 seconds.",
    "status": 429
  },
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-03-14T10:30:00.000Z"
}
```

### Error Codes Added by Wave 14

| Code | HTTP Status | Trigger |
|------|------------|---------|
| `VALIDATION_FAILED` | 400 | `InputValidationException` (any field fails sanitization) |
| `RATE_LIMITED` | 429 | Token bucket exhausted |

## File Inventory

### New Files

| Path | Purpose |
|------|---------|
| `libs/security-baseline/pom.xml` | Library build definition |
| `libs/security-baseline/src/main/java/.../security/input/InputSanitizer.java` | Input validation utilities |
| `libs/security-baseline/src/main/java/.../security/input/InputValidationException.java` | Validation exception |
| `libs/security-baseline/src/main/java/.../security/ratelimit/RateLimitGuard.java` | Token-bucket rate limiter |
| `libs/security-baseline/src/main/java/.../security/ratelimit/RateLimitResult.java` | Rate limit result record |
| `libs/security-baseline/src/main/java/.../security/ratelimit/RateLimitExceededException.java` | Rate limit exception |
| `libs/security-baseline/src/main/java/.../security/audit/AdminAuditEmitter.java` | Admin audit event emitter |
| `libs/security-baseline/src/main/java/.../security/audit/AdminAuditRecord.java` | Audit record for outbox |
| `libs/security-baseline/src/main/java/.../security/audit/InMemoryAdminAuditSink.java` | Test/dev audit sink |
| `libs/security-baseline/src/main/java/.../security/secrets/SecretProvider.java` | Secrets interface |
| `libs/security-baseline/src/main/java/.../security/secrets/SecretNotFoundException.java` | Missing secret exception |
| `libs/security-baseline/src/main/java/.../security/secrets/EnvSecretProvider.java` | Env var implementation |
| `libs/security-baseline/src/main/java/.../security/secrets/VaultSecretProvider.java` | Vault config contract |
| `services/shared-core/src/main/java/.../shared/security/RateLimitFilter.java` | Servlet rate limit filter |
| `services/shared-core/src/main/java/.../shared/security/SecurityBaselineExceptionHandler.java` | Error envelope handler |
| `services/shared-core/src/main/java/.../shared/security/AdminAuditOutboxSink.java` | JDBC outbox sink |

### Modified Files

| Path | Change |
|------|--------|
| `services/shared-core/pom.xml` | Added `security-baseline` dependency |
| 13 services | Added `SecurityBaselineConfig.java` |

### Test Files

| Path | Coverage |
|------|----------|
| `libs/security-baseline/src/test/.../input/InputSanitizerTest.java` | Input validation, injection detection |
| `libs/security-baseline/src/test/.../ratelimit/RateLimitGuardTest.java` | Rate limiting, 429 error data |
| `libs/security-baseline/src/test/.../audit/AdminAuditEmitterTest.java` | Audit emission on protected endpoint |
| `libs/security-baseline/src/test/.../secrets/SecretProviderTest.java` | Secret provider never logs secrets |
