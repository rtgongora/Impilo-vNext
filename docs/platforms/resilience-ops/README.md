# Resilience & Operations Platform — Architecture Overview

## Services

| Service | Port | Description |
|---|---|---|
| **observability-service** | 8210 | Dashboard/alert registry, ops health summaries, outbox lag metrics, service heartbeat |
| **audit-ledger-service** | 8350 | Immutable tamper-evident audit ledger with SHA-256 hash chaining |
| **support-service** | 8340 | Helpdesk ticket CRUD, knowledge articles, request/correlation tracking |

## Data Flow

```
Service Instance ──HTTP──> observability-service ──> obs.service_heartbeats (upsert)
                                │
                                └──> obs.event_outbox ──> impilo.obs.dashboard.created.v1
                                                       ──> impilo.obs.alert-rule.created.v1

Any Service ──HTTP──> audit-ledger-service ──> ald_audit_records (append-only, immutable)
                                │
                                └──> ald_event_outbox ──> impilo.audit.record.appended.v1

User/System ──HTTP──> support-service ──> sup_tickets (CRUD, versioned)
                                │
                                └──> sup_event_outbox ──> impilo.support.ticket.{created|updated}.v1
                                                       ──> impilo.support.article.created.v1
```

## Bus Discipline

| Producer | Event Type Pattern | Partition Key |
|---|---|---|
| observability-service | `impilo.obs.dashboard.*.v1` | `dashboard_id` |
| observability-service | `impilo.obs.alert-rule.*.v1` | `alert_rule_id` |
| audit-ledger-service | `impilo.audit.record.*.v1` | `record_id` |
| support-service | `impilo.support.ticket.*.v1` | `ticket_id` |
| support-service | `impilo.support.article.*.v1` | `article_id` |

## v1.1 Compliance

All three services are v1.1-native:

- **Header enforcement**: `V11HeaderFilter` (order 10) requires `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`
- **Idempotency**: `IdempotencyFilter` (order 11) requires `Idempotency-Key` on POST/PUT/PATCH commands
- **Timeout**: `TimeoutEnforcementFilter` (order 12) respects `X-Client-Timeout-MS`
- **Outbox pattern**: Each service writes to its own `*_event_outbox` table with v1.1 context columns
- **GoldenContractIT**: Each service extends `GoldenContractSuite` for automated v1.1 compliance testing

## Observability Service

### Database Schema (obs schema)

**obs.dashboards** — Dashboard definition registry:
- `id` (BIGSERIAL PK), `tenant_id`, `name`, `description`, `dashboard_type`, `config` (JSONB), `status`, `created_by`, `created_at`, `updated_at`

**obs.alert_rules** — Alert rule definitions:
- `id` (BIGSERIAL PK), `tenant_id`, `name`, `description`, `metric_name`, `condition`, `threshold`, `severity`, `status`, `created_by`, `created_at`, `updated_at`

**obs.service_heartbeats** — Service health heartbeats:
- `id` (BIGSERIAL PK), `service_name`, `instance_id`, `tenant_id`, `status`, `version_tag`, `metadata` (JSONB), `last_heartbeat`, `created_at`
- Unique constraint: `(service_name, instance_id)`

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/internal/v1/dashboards` | Create dashboard |
| GET | `/internal/v1/dashboards` | List dashboards for tenant |
| POST | `/internal/v1/alert-rules` | Create alert rule |
| GET | `/internal/v1/alert-rules` | List alert rules for tenant |
| GET | `/internal/v1/ops/health/summary` | Service health overview (total, healthy, stale) |
| GET | `/internal/v1/ops/metrics/lag` | Outbox depth and lag metrics |
| POST | `/internal/v1/ops/heartbeat` | Record service heartbeat (upsert) |

### Health Summary Response

```json
{
  "total_services": 5,
  "healthy": 4,
  "stale": 1,
  "stale_threshold_minutes": 5,
  "services": [
    { "service_name": "tshepo-service", "instances": 2, "status": "UP", "last_heartbeat": "..." }
  ],
  "generated_at": "2026-03-14T12:00:00Z"
}
```

### Metrics Lag Response

```json
{
  "outbox_depth": 3,
  "outbox_lag_seconds": 12,
  "oldest_unpublished_at": "2026-03-14T11:59:48Z",
  "measured_at": "2026-03-14T12:00:00Z"
}
```

## Audit Ledger Service

### Database Schema

**ald_audit_records** — Immutable append-only audit log:
- `id` (BIGSERIAL), `record_id` (UUID, UNIQUE), `tenant_id`, `sequence_num` (BIGINT, per-tenant)
- `correlation_id`, `actor_id`, `actor_type`, `action`, `resource_type`, `resource_id`
- `outcome`, `detail_json`, `occurred_at`
- `prev_hash` (VARCHAR 64), `entry_hash` (VARCHAR 64, NOT NULL)
- All fields `updatable=false` — enforced at JPA + DB trigger level

**ald_chain_heads** — Per-tenant hash chain cursor:
- `tenant_id` (UUID PK), `current_hash`, `current_seq`, `updated_at`

### Hash Chaining Algorithm

```
entry_hash = SHA-256(prev_hash || tenant_id || sequence_num || action || actor_id || resource_type || occurred_at_millis)
```

- Genesis record: `prev_hash = "0000...0000"` (64 zeros)
- Each subsequent record: `prev_hash = previous_record.entry_hash`
- DB triggers prevent UPDATE and DELETE on `ald_audit_records`

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/internal/v1/audit/records` | Append audit record |
| GET | `/internal/v1/audit/records/{record_id}` | Get record by ID |
| GET | `/internal/v1/audit/records` | List records (cursor pagination) |
| GET | `/internal/v1/audit/query?correlation_id=<UUID>` | Query by correlation ID |
| GET | `/internal/v1/audit/chain/verify?from_seq=X&to_seq=Y` | Verify chain integrity |

### Events

- `impilo.audit.record.appended.v1` — Emitted per appended record with `partition_key=record_id`

## Support Service (Helpdesk)

### Database Schema

**sup_tickets** — Helpdesk ticket tracking:
- `ticket_id` (UUID PK), `tenant_id`, `title`, `description`, `category`, `priority`, `status`
- `reporter_ref`, `assignee_ref`, `facility_ref`, `resolution`, `metadata_json`
- `request_id` (VARCHAR 255), `correlation_id` (UUID) — v1.1 request tracking
- `created_at`, `updated_at`, `resolved_at`, `version`

**sup_knowledge_articles** — Knowledge base:
- `article_id` (UUID PK), `tenant_id`, `title`, `body`, `category`, `status`, `author_ref`, `tags`
- `created_at`, `updated_at`, `published_at`, `version`

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/internal/v1/support/tickets` | Create ticket (stores request_id + correlation_id) |
| PATCH | `/internal/v1/support/tickets/{ticket_id}` | Update ticket |
| GET | `/internal/v1/support/tickets/{ticket_id}` | Get ticket |
| GET | `/internal/v1/support/tickets` | List tickets (filter by status, priority) |
| POST | `/internal/v1/support/articles` | Create knowledge article |
| GET | `/internal/v1/support/articles/{article_id}` | Get article |
| GET | `/internal/v1/support/articles` | List articles |
| GET | `/internal/v1/snapshots/tickets` | Point-in-time ticket snapshot |
| GET | `/internal/v1/snapshots/articles` | Point-in-time article snapshot |

### Events

- `impilo.support.ticket.created.v1` — Full ticket state on creation
- `impilo.support.ticket.updated.v1` — Updated ticket state
- `impilo.support.article.created.v1` — Article state on creation

## Test Profiles

All services use H2 in-memory databases with `create-drop` DDL for tests:
- `application-test.yml` disables Flyway and configures H2
- Observability service additionally disables OAuth2 security in test profile
