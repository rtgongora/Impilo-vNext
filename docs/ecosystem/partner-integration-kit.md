# Partner Integration Kit

> Wave 23 — Dual-Mode Ecosystem Enablement | Date: 2026-03-15

## Overview

This kit provides everything a third-party partner needs to integrate with the Impilo vNext health information platform. It covers client registration, API key management, contract validation, sandbox testing, and production certification.

> **Developer Console**: Partners can also use the web-based Developer Console (`ui/developer-console`, port 3007) for a visual interface to all operations described below. See `docs/apps/developer-partner-app/README.md` for details.

## Architecture Context

```
┌──────────────────┐          ┌──────────────────────────────────────────────────┐
│  Partner System  │          │                  Impilo vNext                    │
│                  │          │                                                  │
│  EHR / Lab /     │  HTTPS   │  ┌─────────┐    ┌──────────┐    ┌───────────┐  │
│  Pharmacy /      │ ────────→│  │ Envoy   │───→│ TSHEPO   │───→│ Service   │  │
│  Supply Chain    │          │  │ Gateway │    │ (AuthZ)  │    │ Layer     │  │
│                  │          │  └─────────┘    └──────────┘    └───────────┘  │
│  Uses:           │          │       │                              │          │
│  - Trust headers │          │  Rate limit                    ┌────┴────┐     │
│  - API keys      │          │  API key check                 │ BUTANO  │     │
│  - Event format  │          │  Tenant isolation              │ (FHIR)  │     │
└──────────────────┘          │                                └─────────┘     │
                              └──────────────────────────────────────────────────┘
```

## Quick Start

### 1. Register Your Organization

```bash
./scripts/ecosystem/register-partner.sh \
  --name "Your Organization" \
  --email "dev@yourorg.co.zw" \
  --sandbox
```

### 2. Get API Keys

```bash
./scripts/ecosystem/issue-partner-keys.sh \
  --client-id <your-client-id> \
  --label "Development Key" \
  --sandbox
```

### 3. Make Your First API Call

```bash
curl -X GET "${BASE_URL}/internal/v1/developer/discovery" \
  -H "X-API-Key: imp_<your-key>" \
  -H "X-Tenant-ID: <your-tenant-id>" \
  -H "X-Pod-ID: national-spine" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)"
```

### 4. Run Certification

```bash
./scripts/ecosystem/run-contract-certification.sh \
  --client-id <your-client-id>
```

## API Contract

### Required Headers

Every request to Impilo APIs MUST include these four trust headers:

| Header | Type | Purpose | Example |
|--------|------|---------|---------|
| `X-Tenant-ID` | UUID | Multi-tenant isolation | `aaaa1111-...` |
| `X-Pod-ID` | String | Federation pod identity | `national-spine` |
| `X-Request-ID` | UUID | Unique request identifier | `req-<uuid>` |
| `X-Correlation-ID` | UUID | Cross-service trace | `corr-<uuid>` |

Command endpoints (POST, PUT, DELETE) additionally require:

| Header | Type | Purpose |
|--------|------|---------|
| `Idempotency-Key` | String | Prevents duplicate processing |

### Error Response Format

All error responses follow the envelope format:

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Human-readable description",
    "details": { "field": "patient_id", "reason": "required" },
    "request_id": "req-<uuid>",
    "correlation_id": "corr-<uuid>"
  }
}
```

### Standard Error Codes

| Code | HTTP | Meaning |
|------|------|---------|
| `MISSING_REQUIRED_HEADER` | 400 | Trust header missing or blank |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | Command endpoint missing Idempotency-Key |
| `IDENTITY_CONFLICT` | 409 | Same Idempotency-Key with different body |
| `CLIENT_TIMEOUT_EXCEEDED` | 504 | X-Client-Timeout-MS expired |
| `FEDERATION_AUTHORITY_VIOLATION` | 403 | Pod not authorized for this endpoint |
| `NOT_FOUND` | 404 | Resource not found |

## Developer Portal API

### Client Management

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/developer/clients` | Register partner client |
| GET | `/internal/v1/developer/clients` | List registered clients |
| GET | `/internal/v1/developer/clients/{id}` | Get client details |
| PUT | `/internal/v1/developer/clients/{id}/sandbox` | Configure sandbox |
| PUT | `/internal/v1/developer/clients/{id}/deprecation-posture` | Set deprecation posture |

### API Key Management

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/developer/clients/{id}/keys` | Issue API key |
| GET | `/internal/v1/developer/clients/{id}/keys` | List client keys |
| POST | `/internal/v1/developer/keys/{id}/rotate` | Rotate API key |
| DELETE | `/internal/v1/developer/keys/{id}` | Revoke API key |

### Discovery

| Method | Path | Description |
|--------|------|-------------|
| GET | `/internal/v1/developer/discovery` | API discovery metadata |

## Event Contract

Partners producing events for Impilo consumption must conform to the v1.1 EventEnvelope format.

### Required Fields (15)

| Field | Type | Description |
|-------|------|-------------|
| `eventId` | UUID | Unique event identifier |
| `eventType` | String | `impilo.{service}.{entity}.{action}.v{N}` |
| `schemaVersion` | Integer | Schema version (≥ 1) |
| `correlationId` | UUID | Cross-event correlation |
| `causationId` | UUID | Causing event ID |
| `idempotencyKey` | String | Deduplication key |
| `producer` | String | Producing service name |
| `tenantId` | UUID | Tenant identifier |
| `podId` | String | Producing pod |
| `occurredAt` | ISO 8601 | When the event occurred |
| `emittedAt` | ISO 8601 | When the event was emitted |
| `subjectType` | String | Entity type (e.g., "Encounter") |
| `subjectId` | String | Entity identifier |
| `payload` | Object | Event-specific data |
| `meta` | Object | Additional metadata (may be empty `{}`) |

### Event Type Naming

```
impilo.{service}.{entity}.{action}.v{version}
```

Examples:
- `impilo.partner-ehr.encounter.created.v1`
- `impilo.partner-lab.result.submitted.v1`
- `impilo.partner-pharmacy.order.fulfilled.v1`

## Schema Registry

Partners can register and evolve their event schemas via the schema registry.

### Register Schema

```bash
curl -X POST "${BASE_URL}/internal/v1/schemas" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: ${TENANT_ID}" \
  -H "X-Pod-ID: national-spine" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: schema-$(uuidgen)" \
  -d '{
    "subject": "impilo.partner-ehr.encounter.created",
    "description": "EHR encounter creation event",
    "schema": "{\"type\":\"object\",\"properties\":{\"encounter_type\":{\"type\":\"string\"},\"facility_id\":{\"type\":\"string\"}},\"required\":[\"encounter_type\"]}"
  }'
```

### Check Compatibility

```bash
curl -X POST "${BASE_URL}/internal/v1/schemas/compatibility" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "impilo.partner-ehr.encounter.created",
    "schema": "{\"type\":\"object\",\"properties\":{\"encounter_type\":{\"type\":\"string\"},\"facility_id\":{\"type\":\"string\"},\"notes\":{\"type\":\"string\"}},\"required\":[\"encounter_type\"]}"
  }'
```

### Schema Evolution Rules

| Change | Allowed? | Impact |
|--------|----------|--------|
| Add optional field | Yes | Backward-compatible |
| Add enum value | Yes | Backward-compatible |
| Widen type (int → number) | Yes | Backward-compatible |
| Make required → optional | Yes | Backward-compatible |
| Remove field | **No** | Breaking — requires new version |
| Add required field | **No** | Breaking — requires new version |
| Change field type | **No** | Breaking — requires new version |
| Remove enum value | **No** | Breaking — requires new version |

## Versioning & Deprecation

### API Versioning

APIs use path-based versioning: `/internal/v{N}/...`

- Maximum 2 concurrent versions (current + deprecated)
- Minimum 90-day deprecation window before sunset
- 30-day grace period after sunset date

### Deprecation Signals

When an API version is deprecated, responses include:

```http
Sunset: Sat, 01 Sep 2026 00:00:00 GMT
Deprecation: true
Link: <https://docs.impilo.gov.zw/migration/v2>; rel="sunset"
```

### Partner Deprecation Posture

Set how your client handles deprecated APIs:

| Posture | Behavior |
|---------|----------|
| `NONE` | No warnings (default) |
| `WARN` | Dashboard warnings on deprecated API usage |
| `BLOCK` | 400 error on deprecated API calls (forces migration) |

```bash
curl -X PUT "${BASE_URL}/internal/v1/developer/clients/${CLIENT_ID}/deprecation-posture" \
  -d '{"posture": "WARN"}'
```

### Enforcement

Run the deprecation window checker:

```bash
./scripts/ecosystem/verify-deprecation-window.sh
```

## Scripts Reference

| Script | Purpose |
|--------|---------|
| `scripts/ecosystem/register-partner.sh` | Register partner client |
| `scripts/ecosystem/issue-partner-keys.sh` | Issue/rotate API keys |
| `scripts/ecosystem/run-contract-certification.sh` | Run contract certification |
| `scripts/ecosystem/verify-deprecation-window.sh` | Verify deprecation policy compliance |

## Certification Flow

See [sandbox-certification-flow.md](sandbox-certification-flow.md) for the full certification lifecycle:

1. **Register** — Create client account
2. **Sandbox** — Develop and test in sandbox environment
3. **Certify** — Pass automated contract validation
4. **Promote** — Receive production credentials

## Test Coverage

### Contract Test Suite (libs/contract-tests)

| Test Class | Tests | Coverage |
|-----------|-------|----------|
| `PartnerContractCertificationTest` | 20 | Request headers, error envelope, event envelope, schema compatibility, API conventions, certification gate |
| `SchemaCompatibilityValidatorTest` | 8 | Backward compatibility checks (add field, remove field, type changes, enum evolution) |
| `EventEnvelopeValidatorTest` | 9 | Envelope required fields, event type naming, snake/camel case support |
| `RepoEventTypeContractTest` | 30+ | All event types across all services follow naming convention |

### Developer Portal Tests

| Test Class | Tests | Coverage |
|-----------|-------|----------|
| `Wave23PartnerOnboardingTest` | 14 | Registration, key lifecycle, sandbox config, deprecation posture, audit trail |
| `DeveloperPortalServiceTest` | 3 | Registration, key issuance, key rotation (existing) |
| `DeveloperPortalGoldenContractIT` | 15+ | Header enforcement, error envelope, idempotency (via GoldenContractSuite) |

### Schema Registry Tests

| Test Class | Tests | Coverage |
|-----------|-------|----------|
| `SchemaRegistryServiceTest` | 5 | Schema registration, compatibility checks |
| `SchemaRegistryGoldenContractIT` | 15+ | Header enforcement, error envelope, idempotency |

## Security

- API keys are hashed (SHA-256) at rest; raw key shown only at issuance
- Keys use `imp_` prefix for identification
- Sandbox keys expire in 30 days; production keys in 365 days
- Revoked keys are immediately blocked
- All key lifecycle events emitted to audit outbox
- Tenant isolation enforced on every request

## Support

- Technical issues: File on [GitHub Issues](https://github.com/anthropics/claude-code/issues)
- Integration questions: Contact your designated Ministry of Health integration liaison
- Emergency: Break-glass procedure available for critical access needs
