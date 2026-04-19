# Security Baseline — Impilo vNext

> Gateway enforcement posture, JWT audience separation, rate limiting,
> audit hooks, and operational security requirements for all v1.1/v3 services.

## 1. mTLS Posture

### Gateway (Envoy)

| Layer | Configuration |
|-------|---------------|
| External TLS termination | Envoy listener on port 10000 terminates TLS 1.2+ |
| Internal service mesh | mTLS between all services via Envoy sidecar proxies |
| Minimum TLS version | TLS 1.2 (prefer TLS 1.3) |
| Cipher suites | ECDHE-ECDSA-AES256-GCM-SHA384, ECDHE-RSA-AES256-GCM-SHA384 |
| Certificate rotation | Automated via cert-manager / SPIFFE; 24-hour certificates |

### Service-to-Service

| Component | mTLS Status |
|-----------|-------------|
| Envoy → Service | Sidecar-injected mTLS (Kubernetes service mesh) |
| Service → Kafka | SASL_SSL with SCRAM-SHA-512 |
| Service → PostgreSQL | SSL required (`sslmode=verify-full`) |
| Service → Redis | TLS with certificate authentication |

### Configuration Readiness

Services do **not** manage their own TLS certificates. mTLS is enforced at the
infrastructure layer (Envoy sidecar + cert-manager). Services MUST:

- Not disable SSL verification in any client
- Not hardcode certificate paths
- Accept TLS termination at the sidecar boundary
- Use environment-injected connection strings with SSL parameters

## 2. JWT Audience Separation

### Audience Definitions

| Audience | Keycloak Client | Scope | Use Case |
|----------|----------------|-------|----------|
| `impilo-internal` | `impilo-backend` | Service-to-service calls within the platform | Internal APIs (`/internal/v1/`) |
| `impilo-external` | `impilo-portal` | End-user facing applications (One UI Shell, EHR, Portal) | External APIs (`/external/v1/`) |
| `impilo-federation` | `impilo-federation` | Cross-pod federation sync, national → district replication | Federation APIs (`/federation/v1/`) |
| `impilo-public` | `impilo-public` | Unauthenticated public endpoints (credential verification, share slip) | Public APIs (`/v1/public/`) |

### Validation Rules

Every service MUST:

1. Validate JWT `aud` claim matches the expected audience for the endpoint category
2. Reject tokens with mismatched audience — HTTP 403 with error envelope
3. Validate `iss` claim against the configured Keycloak realm issuer URI
4. Verify token `exp` (expiry) — reject expired tokens
5. For internal service-to-service calls, validate the service account `client_id`

### Token Issuance

| Token Type | Lifetime | Refresh |
|------------|----------|---------|
| User access token | 5 minutes | Via `HttpOnly` refresh cookie (30 minutes) |
| Service account token | 60 minutes | Re-issued by client credentials grant |
| Federation sync token | 24 hours | Rotated daily by scheduled job |

### Browser Session Handling

- Experience keeps the short-lived access token in browser memory only.
- Refresh credentials are issued by the Experience BFF as `HttpOnly`, `SameSite=Lax` cookies and are not readable by browser JavaScript.
- Client-side route gating may use a non-secret session marker cookie (`exp_has_session`) plus hydrated user metadata, but protected API access still depends on BFF token refresh and JWT validation.

## 3. Rate Limiting & Abuse Controls

### Gateway-Level Rate Limits (Envoy)

| Endpoint Category | Rate Limit | Window | Action |
|-------------------|-----------|--------|--------|
| Authentication endpoints (`/api/v1/authorize`) | 120 requests | 1 minute | HTTP 429 |
| Public verification (`/v1/public/verify`) | 60 requests | 1 minute | HTTP 429 |
| Internal v1.1 APIs | 600 requests | 1 minute | HTTP 429 |
| External v1.1 APIs | 300 requests | 1 minute | HTTP 429 |
| Break-glass activation | 5 requests | 5 minutes | HTTP 429 + alert |

### Rate Limit Headers (returned to caller)

```
X-RateLimit-Limit: 600
X-RateLimit-Remaining: 542
X-RateLimit-Reset: 1710410400
Retry-After: 30
```

### Abuse Detection

| Signal | Action |
|--------|--------|
| > 10 auth failures in 1 minute from same IP | Temporary IP block (15 minutes) + Kafka alert |
| > 5 break-glass activations per user per day | Escalation alert to security team |
| Anomalous data export volume (> 1000 records) | Rate limit + audit event |
| JWT replay detected (same `jti` within TTL) | HTTP 401 + security alert |

## 4. Audit Requirements

### Events Requiring Audit Trail

| Category | Events | Severity |
|----------|--------|----------|
| **Policy decisions** | ALLOW, DENY, BREAK_GLASS — every TSHEPO policy evaluation | HIGH |
| **Break-glass** | Activation, usage, deactivation, reason text | CRITICAL |
| **Data access** | PII lookups (VITO), clinical record access (BUTANO), large exports | HIGH |
| **State changes** | Patient registration/merge, order placement, payment initiation | MEDIUM |
| **Configuration** | Tariff updates, terminology publishing, facility registry mutations | HIGH |
| **Security** | Login, logout, password change, token refresh, role assignment | HIGH |
| **Federation** | Cross-pod sync initiation, conflict resolution, authority violations | HIGH |

### Audit Event Envelope

Every audit event MUST include:

```json
{
  "event_id": "uuid",
  "event_type": "impilo.audit.<domain>.<action>.v1",
  "tenant_id": "uuid",
  "pod_id": "string",
  "actor_id": "string",
  "correlation_id": "uuid",
  "occurred_at": "ISO-8601",
  "subject_type": "string",
  "subject_id": "string",
  "action": "string",
  "outcome": "SUCCESS | FAILURE | DENIED",
  "details": {},
  "source_service": "string",
  "ip_address": "string"
}
```

### Audit Storage

- Audit events are emitted to Kafka topic `platform.audit.events`
- Audit Ledger Service persists to append-only PostgreSQL table
- Retention: minimum 7 years
- Tamper evidence: hash chain (`X-Audit-Chain` header propagation)

## 5. v1.1 Header Enforcement at Gateway

### Required Headers (enforced by OPA policy `impilo.gateway.headers`)

| Header | Enforcement | HTTP on Missing |
|--------|-------------|-----------------|
| `X-Tenant-ID` | HARD — reject if missing | 400 |
| `X-Pod-ID` | HARD — reject if missing | 400 |
| `X-Request-ID` | HARD — reject if missing | 400 |
| `X-Correlation-ID` | HARD — reject if missing | 400 |
| `Authorization` | HARD — reject if missing/invalid | 401 |

### Idempotency-Key Enforcement

Command endpoints (POST, PUT, PATCH, DELETE on v1.1 paths) MUST include
the `Idempotency-Key` header. The tech-companion `IdempotencyFilter` enforces
this at the service level.

See: `tools/ops/gateway/opa/impilo_idempotency.rego` for gateway-level
classification of which endpoints require idempotency.

### Federation Authority (enforced by OPA policy `impilo.gateway.federation`)

National-authoritative operations are restricted to `X-Pod-ID: national`:

| Operation | Service | Method |
|-----------|---------|--------|
| Patient identity merge | VITO | POST `/v1/identity/merge` |
| Tariff updates | COSTA/MSIKA | POST/PUT/DELETE `/v1/tariffs` |
| Terminology publishing | ZIBO | POST `/v1/terminology/publish` |
| Master facility mutations | TUSO | PUT/DELETE `/v1/facilities` |
| Provider credential revocation | VARAPI | POST `/v1/credentials/revoke` |

## 6. Secrets Management Shape

### Secret Categories

| Category | Storage | Rotation |
|----------|---------|----------|
| Database credentials | Kubernetes Secret (from external vault) | Quarterly |
| Kafka SASL credentials | Kubernetes Secret (from external vault) | Quarterly |
| Keycloak client secrets | Kubernetes Secret (from external vault) | Monthly |
| TSHEPO pepper | Kubernetes Secret (from external vault) | Annual (with re-hash migration) |
| API keys (external integrations) | Kubernetes Secret (from external vault) | Per-integration policy |
| TLS certificates | cert-manager auto-rotation | 24-hour certificates |

### Rules

- No secrets in source code, container images, or environment variable defaults
- All secrets injected via Kubernetes Secrets backed by external vault (HashiCorp Vault or Azure Key Vault)
- Secret references in `application.yml` use `${ENV_VAR}` syntax only
- CI/CD pipelines use ephemeral credentials scoped to deployment target
