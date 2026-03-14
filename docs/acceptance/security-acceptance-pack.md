# Wave 14 — Security Acceptance Pack

> Date: 2026-03-14
> Reviewer: Principal Engineer + Security Lead

## Acceptance Criteria

### A) Input Validation & Consistent Error Envelopes

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| A1 | `InputSanitizer` provides allowlist-based validation for UUID, text, identifier, email, phone, event type | `InputSanitizer.java` — 8 validation methods, all reject-by-default | PASS |
| A2 | XSS, SQLi, template injection, null byte attacks are detected and rejected | `InputSanitizerTest.java` — `rejectsInjectionAttempts` parameterized test covers 7 attack vectors | PASS |
| A3 | `InputValidationException` carries error code + field name, never the rejected value | `InputValidationException.java:10` — message omits value by design | PASS |
| A4 | All validation errors return standard `ApiResponse` envelope with HTTP 400 | `SecurityBaselineExceptionHandler.java` — `@ControllerAdvice` maps to `ApiResponse.error()` | PASS |
| A5 | Error codes are machine-readable, prefixed by domain | `ApiError.java` — `VALIDATION_FAILED` used consistently | PASS |

### B) Rate Limiting

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| B1 | Token-bucket rate limiter implemented with configurable capacity and refill | `RateLimitGuard.java` — constructor takes `maxTokens` + `refillPerSecond` | PASS |
| B2 | Rate limit exceeded returns HTTP 429 with `ApiResponse` error envelope | `RateLimitFilter.java` — writes 429 + `ApiResponse.error()` JSON | PASS |
| B3 | Standard rate-limit headers set on all responses | `RateLimitFilter.java:58-59` — `X-RateLimit-Limit`, `X-RateLimit-Remaining`; line 62 adds `Retry-After` on 429 | PASS |
| B4 | Health/readiness probes excluded from rate limiting | `RateLimitFilter.java:76-79` — `shouldNotFilter` excludes `/actuator/*`, `/health`, `/ready` | PASS |
| B5 | Separate buckets per actor (no cross-actor interference) | `RateLimitGuardTest.java:separateBucketsPerKey` | PASS |
| B6 | Pluggable store for distributed deployments | `RateLimitGuard.RateLimitStore` interface; `InMemoryRateLimitStore` default | PASS |
| B7 | Gateway config documented | `wave14-security-hardening.md` — Envoy `local_ratelimit` config included | PASS |

### C) Admin Action Audit Events

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| C1 | Admin audit events wrapped in v1.1 `EventEnvelope` | `AdminAuditEmitter.java:58-75` — uses `EventEnvelope.builder()` | PASS |
| C2 | Events marked as immutable | `AdminAuditEmitter.java:70` — `payload.immutable = true` | PASS |
| C3 | Events persisted to `event_outbox` table (outbox pattern) | `AdminAuditOutboxSink.java` — JDBC insert into `{schema}.event_outbox` | PASS |
| C4 | Audit emission tested on protected endpoint scenario | `AdminAuditEmitterTest.java:emitsAuditEventOnProtectedEndpoint` — verifies full event structure | PASS |
| C5 | Each event has unique ID, correct producer, tenant, correlation ID | `AdminAuditEmitterTest.java:34-45` — asserts all fields | PASS |
| C6 | In-memory sink provided for test isolation | `InMemoryAdminAuditSink.java` — `List<AdminAuditRecord>` accumulator | PASS |

### D) Secrets Handling Hygiene

| # | Criterion | Evidence | Status |
|---|-----------|----------|--------|
| D1 | No plaintext secrets in repository | Grep for common secret patterns: none found in committed code | PASS |
| D2 | `SecretProvider` interface defines safe contract | `SecretProvider.java` — Javadoc specifies: no logging, no exception values, no toString exposure | PASS |
| D3 | `EnvSecretProvider` maps keys to env vars with prefix | `SecretProviderTest.java:envProvider_mapsKeyToEnvVar` | PASS |
| D4 | `toString()` never reveals cached secrets | `SecretProviderTest.java:envProvider_toStringDoesNotRevealSecrets`, `vaultProvider_toStringDoesNotRevealSecrets` | PASS |
| D5 | Exception messages never contain secret values | `SecretProviderTest.java:envProvider_requireSecretThrowsForMissing` — asserts no "=" in message | PASS |
| D6 | Secret provider never logs secrets during operations | `SecretProviderTest.java:secretProviderNeverLogsSecrets` — captures stdout/stderr, asserts clean | PASS |
| D7 | Vault config contract defined for production integration | `VaultSecretProvider.VaultConfig` record with required fields | PASS |
| D8 | Env var fallback works for local dev | `VaultSecretProvider.java:45-50` — falls back to `System.getenv()` | PASS |

## Test Summary

| Test Class | Tests | Scope |
|------------|-------|-------|
| `InputSanitizerTest` | 16 | Validation, injection detection, truncation, optional fields |
| `RateLimitGuardTest` | 8 | Token bucket, 429 with error data, separate buckets, multi-token, edge cases |
| `AdminAuditEmitterTest` | 5 | Emission on protected endpoint, uniqueness, required fields, optional fields |
| `SecretProviderTest` | 9 | Key mapping, missing secret, toString safety, log safety, Vault fallback |

### Running Tests

```bash
# Security baseline library tests (pure Java — no Spring context required)
cd libs/security-baseline
mvn clean test

# Service-level integration tests require Docker (PostgreSQL, Keycloak)
cd services
mvn clean test -pl shared-core
```

## Architecture Decision Records

### ADR-W14-1: Pure Java Library vs Spring Starter

**Decision**: security-baseline is a pure Java 21 library; Spring integration lives in shared-core.

**Rationale**: Keeps the primitives usable in non-Spring contexts (CLI tools, FHIR gateway, batch jobs). Services already depend on shared-core, so no new dependency chain.

### ADR-W14-2: In-Memory Rate Limiter Default

**Decision**: Default to `ConcurrentHashMap`-based storage, with `RateLimitStore` interface for Redis/JDBC.

**Rationale**: Single-replica services (local dev, edge) work out of the box. Production multi-replica deployments plug in Redis via the store interface.

### ADR-W14-3: Outbox Sink for Admin Audit

**Decision**: Admin audit events written to the existing `event_outbox` table rather than a separate audit table.

**Rationale**: Reuses the proven outbox relay mechanism. Events are reliably published to Kafka without a new infrastructure dependency. The `meta.audit_class = ADMIN_ACTION` field allows consumers to filter.

### ADR-W14-4: Vault Config Contract Only

**Decision**: `VaultSecretProvider` defines the configuration shape and falls back to env vars. No real Vault client dependency.

**Rationale**: Per Wave 14 scope — no real HSM integration; only integration points and config contracts. Production Vault integration requires infrastructure provisioning.

## Sign-Off

- [ ] Security Lead review
- [ ] Architecture review
- [ ] Code review (PR merged)
- [ ] Test suite green in CI
