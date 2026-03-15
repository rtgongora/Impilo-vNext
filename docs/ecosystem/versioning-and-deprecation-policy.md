# API Versioning and Deprecation Policy

> Status: Enforced | Wave 23 | Date: 2026-03-15

## Versioning Rules

### Path Convention

All Impilo APIs use path-based versioning:

```
/internal/v{N}/{service}/{resource}
```

| Rule | Value | Enforcement |
|------|-------|-------------|
| Path prefix | `/internal/v{N}/` | Envoy route config; `verify-deprecation-window.sh` |
| Max concurrent versions | 2 | Registry check; CI gate |
| Version increment | Major only (v1 → v2) | Code review; no minor versions in path |
| FHIR endpoints | `/fhir/` (versioned via CapabilityStatement) | HAPI FHIR config |

### Event Type Versioning

Events follow the naming convention:

```
impilo.{service}.{entity}.{action}.v{N}
```

| Rule | Value | Enforcement |
|------|-------|-------------|
| Naming pattern | `impilo.{service}.{entity}.{action}.v{N}` | `EventEnvelopeValidator`; `RepoEventTypeContractTest` |
| Backward compatibility | Required for minor versions | `SchemaCompatibilityValidator` |
| Breaking changes | New major version required | Schema registry compatibility check |

## Deprecation Lifecycle

```
┌──────────┐    90 days min    ┌────────────┐    30 days grace    ┌─────────┐
│ CURRENT  │ ───────────────→ │ DEPRECATED │ ──────────────────→ │ SUNSET  │
│          │                   │            │                      │         │
│ Active   │                   │ Sunset     │                      │ Removed │
│ No flags │                   │ header set │                      │ 410 Gone│
└──────────┘                   └────────────┘                      └─────────┘
```

### Deprecation Window Rules

| Rule | Policy | Enforcement |
|------|--------|-------------|
| Minimum deprecation window | 90 days | `verify-deprecation-window.sh` checks registry |
| Grace period after sunset date | 30 days | API returns `410 Gone` after grace period |
| Sunset header on deprecated endpoints | Required | `Sunset: <HTTP-date>` response header |
| Deprecation link header | Required | `Deprecation: true` + `Link: <url>; rel="sunset"` |
| Partner notification | Email 90 days, 30 days, 7 days before sunset | notification-service triggers |
| Max concurrent API versions | 2 (current + deprecated) | Registry check |

### Deprecation Procedure

1. **Register deprecation** in `api-versioning-registry.json`:
   ```json
   {
     "status": "DEPRECATED",
     "deprecated_at": "2026-06-01",
     "sunset_at": "2026-09-01"
   }
   ```
2. **Add Sunset header** to deprecated endpoint responses
3. **Notify partners** via developer portal (automated email)
4. **Monitor usage** of deprecated endpoint (audit logs)
5. **Enforce sunset** — endpoint returns `410 Gone` after sunset date + grace period
6. **Remove code** after confirmed zero traffic (30 days post-sunset)

### Partner Deprecation Posture

Partners can set their deprecation posture via the developer portal:

| Posture | Behavior |
|---------|----------|
| `NONE` | No warnings; partner must check Sunset headers manually |
| `WARN` | Developer portal dashboard shows deprecation warnings |
| `BLOCK` | API calls to deprecated endpoints return 400 with migration instructions |

Set via: `PUT /internal/v1/developer/clients/{id}/deprecation-posture`

## Schema Evolution Rules

| Change Type | Allowed? | Gate |
|-------------|----------|------|
| Add optional field | Yes | `SchemaCompatibilityValidator` |
| Add new enum value | Yes | `SchemaCompatibilityValidator` |
| Widen type (int → number) | Yes | `SchemaCompatibilityValidator` |
| Make required field optional | Yes | `SchemaCompatibilityValidator` |
| Remove field | No (breaking) | `SchemaCompatibilityValidator` blocks |
| Add required field | No (breaking) | `SchemaCompatibilityValidator` blocks |
| Change field type | No (breaking) | `SchemaCompatibilityValidator` blocks |
| Remove enum value | No (breaking) | `SchemaCompatibilityValidator` blocks |
| Rename field | No (breaking) | `SchemaCompatibilityValidator` blocks |

Breaking changes require a new major version (v1 → v2) and must go through the full deprecation lifecycle.

## Enforcement

### Automated Checks

| Check | Tool | Frequency |
|-------|------|-----------|
| Deprecation window ≥ 90 days | `scripts/ecosystem/verify-deprecation-window.sh` | CI on every PR |
| Schema backward compatibility | `SchemaCompatibilityValidator` + schema-registry-service | On schema registration |
| Event type naming | `EventEnvelopeValidator` + `RepoEventTypeContractTest` | CI on every PR |
| Max 2 concurrent versions | `verify-deprecation-window.sh` | CI on every PR |
| Sunset dates not expired without removal | `verify-deprecation-window.sh` | CI on every PR |

### CI Integration

Add to CI pipeline:

```bash
# Verify all deprecation windows comply with policy
./scripts/ecosystem/verify-deprecation-window.sh

# Run contract certification tests
cd libs/contract-tests && mvn test
```

## Registry

The machine-readable source of truth for all API and event versions is:

```
docs/ecosystem/api-versioning-registry.json
```

All version status changes MUST be recorded in this registry before deployment.
