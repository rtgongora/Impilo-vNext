# Developer & Governance Platform

## Overview

The Developer & Governance Platform provides external API onboarding, schema management, and contract enforcement for the Impilo ecosystem. It consists of two services and a shared contract-tests library.

## Components

### 1. Developer Portal Service (port 8370)

Client registration, API key lifecycle, and sandbox configuration for external API consumers.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/developer/clients` | Register a new API client |
| GET | `/internal/v1/developer/clients` | List clients for tenant |
| GET | `/internal/v1/developer/clients/{id}` | Get client details |
| POST | `/internal/v1/developer/clients/{id}/keys` | Issue API key |
| GET | `/internal/v1/developer/clients/{id}/keys` | List keys for client |
| POST | `/internal/v1/developer/keys/{id}/rotate` | Rotate API key |
| GET | `/internal/v1/developer/discovery` | Service discovery |

**Key behaviors:**
- API keys use `imp_` prefix + 32 random characters; only the SHA-256 hash is stored
- Key rotation atomically marks the old key as ROTATED and creates a new one
- Sandbox mode configurable per client (rate limits, allowed endpoints, mock responses)
- Deprecation posture tracked per client for API lifecycle management
- All mutations emit outbox events (`impilo.developer-portal.client.registered.v1`, `impilo.developer-portal.apikey.issued.v1`, `impilo.developer-portal.apikey.rotated.v1`)

**Database tables:** `dvp_clients`, `dvp_api_keys`, `dvp_sandbox_configs`, `dvp_event_outbox`, `dvp_idempotency_keys`

### 2. Schema Registry Service (port 8371)

Minimal self-hosted schema storage with backward compatibility enforcement.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/schemas` | Register a schema version |
| GET | `/internal/v1/schemas/subjects` | List all subjects |
| GET | `/internal/v1/schemas/subjects/{subject}` | Get subject with versions |
| GET | `/internal/v1/schemas/subjects/{subject}/versions/{version}` | Get specific version |
| POST | `/internal/v1/schemas/compatibility` | Check schema compatibility |

**Key behaviors:**
- Registration-time backward compatibility check via `SchemaCompatibilityValidator`
- Incompatible schemas return HTTP 409 with violation details
- Each version gets a SHA-256 fingerprint for deduplication
- Version numbers auto-increment per subject
- Emits `impilo.schema-registry.schema.registered.v1` on successful registration

**Database tables:** `scr_subjects`, `scr_schema_versions`, `scr_event_outbox`, `scr_idempotency_keys`

### 3. Contract Tests Library (`libs/contract-tests`)

Pure Java 21 library (no Spring) providing:

#### Schema Compatibility Validator
- Checks backward compatibility between JSON Schema versions
- Detects: field removal, type changes, new required fields, optional→required promotion, enum value removal
- Allows: adding optional fields, widening `int`→`number`, making `required`→`optional`, adding enum values

#### Event Envelope Validator
- Validates all 15 required EventEnvelope v1.1 fields (both camelCase and snake_case)
- Enforces event type naming convention: `impilo.{service}.{entity}.{action}.v{N}`
- Validates `schemaVersion >= 1`, payload/meta are objects

#### Repo-Level Contract Tests
- Validates all known event types across services follow naming conventions
- Documents legacy non-compliant event types (PCT, pharmacy, inventory, mushex) for migration tracking
- Cross-service invariants: no duplicates, consistent service segments, version suffixes

## Compatibility Rules

### Backward-Compatible Changes (allowed)
- Adding optional properties
- Widening types (e.g., `integer` → `number`)
- Making a required field optional
- Adding enum values

### Breaking Changes (rejected)
- Removing properties
- Changing property types (incompatible)
- Adding new required fields
- Making an optional field required
- Removing enum values

## Event Types

| Service | Event Type |
|---------|-----------|
| developer-portal | `impilo.developer-portal.client.registered.v1` |
| developer-portal | `impilo.developer-portal.apikey.issued.v1` |
| developer-portal | `impilo.developer-portal.apikey.rotated.v1` |
| schema-registry | `impilo.schema-registry.schema.registered.v1` |

## Trust Headers

Both services enforce the standard trust header contract via `RequestContextHolder.require()`:
- `X-Tenant-ID` (required)
- `X-Pod-ID` (required)
- `X-Request-ID` (auto-generated if absent)
- `X-Correlation-ID` (required)
- `Idempotency-Key` (required for POST operations)

## Local Development

```bash
# Build contract-tests library
cd libs/contract-tests && mvn clean install

# Run developer portal
cd services/developer-portal-service && mvn spring-boot:run

# Run schema registry
cd services/schema-registry-service && mvn spring-boot:run
```

## Testing

```bash
# Unit tests
mvn test -pl libs/contract-tests
mvn test -pl services/developer-portal-service
mvn test -pl services/schema-registry-service

# Golden contract tests (v1.1 compliance)
mvn test -pl services/developer-portal-service -Dtest=DeveloperPortalGoldenContractIT
mvn test -pl services/schema-registry-service -Dtest=SchemaRegistryGoldenContractIT

# Repo-level event type contract tests
mvn test -pl libs/contract-tests -Dtest=RepoEventTypeContractTest
```
