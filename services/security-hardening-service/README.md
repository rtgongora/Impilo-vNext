# Security Hardening Service

**Port:** 8220
**Schema:** `secharden`
**Version:** v1.1-native

## Purpose

The Security Hardening Service is a lightweight registry for security policy packs
and compliance scan results. It tracks hardening policies and their enforcement
status per tenant.

## Domain Model

### Policy Packs
Versioned bundles of security rules with type (BASELINE, ADVANCED, CUSTOM) and
lifecycle status (ACTIVE, INACTIVE, DEPRECATED). Rules are stored as JSONB arrays.

### Scan Results
Compliance scan outcomes tied to a policy pack, recording passed/failed/skipped
rule counts and detailed results per target.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/policy-packs` | Create a policy pack |
| GET | `/internal/v1/policy-packs` | List policy packs for tenant |
| POST | `/internal/v1/scans` | Record a scan result |
| GET | `/internal/v1/scans` | List scan results for tenant |

## Kafka Events (Outbox)

| Event Type | Topic |
|------------|-------|
| `PACK_CREATED` | `impilo.secharden.pack.created.v1` |
| `SCAN_COMPLETED` | `impilo.secharden.scan.completed.v1` |

## Database Tables

- `secharden.policy_packs` — policy pack definitions
- `secharden.scan_results` — compliance scan results
- `secharden.event_outbox` — transactional outbox
- `secharden.idempotency_keys` — request deduplication
