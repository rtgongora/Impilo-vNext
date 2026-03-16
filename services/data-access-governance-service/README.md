# DAGS — Data Access Governance Service

v1.1-native service that provides data-access governance: a policy registry,
access-request workflow (submitted → approved / denied), an immutable audit
log, and enforcement that issues cryptographic permit-token strings.

## Port

| Service | Port |
|---------|------|
| data-access-governance-service | 8170 |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/policies` | Create a governance policy |
| GET | `/internal/v1/policies` | List policies for the tenant |
| POST | `/internal/v1/access-requests` | Submit a data access request |
| GET | `/internal/v1/access-requests` | List access requests (optional `?status=` filter) |
| POST | `/internal/v1/access-requests/{id}/approve` | Approve a submitted request |
| POST | `/internal/v1/access-requests/{id}/deny` | Deny a submitted request |

## Kafka Events (via outbox)

| Event Type | Topic |
|------------|-------|
| POLICY_CREATED | `impilo.dags.policy.created.v1` |
| ACCESS_REQUESTED | `impilo.dags.access.requested.v1` |
| ACCESS_APPROVED | `impilo.dags.access.approved.v1` |
| ACCESS_DENIED | `impilo.dags.access.denied.v1` |

## Database Schema (`dags`)

- `policies` — governance rule registry
- `access_requests` — access request workflow (SUBMITTED → APPROVED / DENIED)
- `access_decisions` — approval/denial decision records
- `audit_log` — immutable decision audit trail
- `event_outbox` — transactional outbox for Kafka
- `idempotency_keys` — request deduplication

## Access Request Workflow

```
SUBMITTED  ──approve──▶  APPROVED  (permit token issued)
     │
     └──deny──▶  DENIED
```

## Enforcement

On approval, a permit-token string is generated and issued in the format:

```
permit-token:<tenantId>:<requesterId>:<requestId>:<uuid>
```

## Running Locally

```bash
cd services
mvn -pl data-access-governance-service spring-boot:run
```

## Running Tests

```bash
cd services
mvn -pl data-access-governance-service test
```
