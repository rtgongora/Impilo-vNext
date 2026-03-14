# Resilience & Operations Platform

The Resilience & Operations Platform provides the operational backbone for Impilo vNext. It lives in the **Integration/Ops Plane** (Ring 2) and is designed with a critical constraint: **no sync dependencies into Ring 1 (Clinical Execution)**.

## Services

### support-service (port 8340)
Ticketing, incident reports, and knowledge articles for operational support.

**Endpoints:**
- `POST /internal/v1/support/tickets` — Create ticket
- `PATCH /internal/v1/support/tickets/{id}` — Update ticket (status transitions, assignment)
- `GET /internal/v1/support/tickets/{id}` — Get ticket by ID
- `GET /internal/v1/support/tickets` — List tickets (filterable by status, priority)
- `POST /internal/v1/support/articles` — Create knowledge article
- `GET /internal/v1/support/articles/{id}` — Get article
- `GET /internal/v1/support/articles` — List articles
- `GET /internal/v1/snapshots/tickets` — Temporal snapshot query
- `GET /internal/v1/snapshots/articles` — Temporal snapshot query

**Events:** `impilo.support.ticket.created.v1`, `impilo.support.ticket.updated.v1`, `impilo.support.article.created.v1`

**Database prefix:** `sup_`

### audit-ledger-service (port 8350)
Append-only tamper-evident audit ledger with SHA-256 hash chaining.

**Endpoints:**
- `POST /internal/v1/audit/records` — Append audit record
- `GET /internal/v1/audit/records/{id}` — Get record by ID
- `GET /internal/v1/audit/records` — List records (paged)
- `GET /internal/v1/audit/query?correlation_id=` — Query by correlation
- `GET /internal/v1/audit/chain/verify?from_seq=&to_seq=` — Verify chain integrity

**Events:** `impilo.audit.record.appended.v1`

**Immutability guarantees:**
1. JPA: All `AuditRecordEntity` fields are `updatable = false`
2. Database: Triggers prevent UPDATE and DELETE on `ald_audit_records`
3. Hash chain: Each record's `entry_hash = SHA-256(prev_hash|tenant|seq|action|actor|resource|timestamp)`

**Database prefix:** `ald_`

### offline-edge-service (port 8360)
Offline event capture and replay pipeline for disconnected clinical workflows (Class C).

**Endpoints:**
- `POST /internal/v1/offline/entitlements` — Issue signed offline entitlement
- `GET /internal/v1/offline/entitlements/{id}` — Get entitlement
- `GET /internal/v1/offline/entitlements/{id}/verify?token_hash=` — Verify entitlement token
- `POST /internal/v1/offline/actions` — Capture offline action (requires valid entitlement)
- `GET /internal/v1/offline/actions` — List captured actions
- `POST /internal/v1/offline/replay/{entitlement_id}` — Replay queued actions

**Events:** `impilo.offline.entitlement.issued.v1`, `impilo.offline.action.recorded.v1`, `impilo.offline.action.replayed.v1`

**Security:** Entitlement tokens are HMAC-SHA256 signed. Actions are validated against entitlement expiry and revocation before capture.

**Database prefix:** `ofe_`

## Architecture Constraints

1. **No Ring 1 sync deps** — These services must never introduce synchronous dependencies into clinical execution paths
2. **v1.1 header contract** — All endpoints enforce X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
3. **Idempotency** — All POST/PATCH/PUT commands require Idempotency-Key header
4. **Outbox pattern** — All state changes emit events via the outbox table for reliable Kafka publishing
5. **ErrorEnvelope** — All error responses use the standard error envelope format

## Observability

Configuration artifacts in `tools/ops/observability/`:
- `otel/otel-collector-config.yaml` — OpenTelemetry Collector (OTLP → Jaeger + Prometheus)
- `prometheus/prometheus.yml` — Prometheus scrape targets for all services
- `grafana/dashboards/impilo-golden-signals.json` — Golden signals dashboard (latency, traffic, errors, saturation)

## Runbooks

See `docs/resilience-ops-platform/runbooks/`:
- `incident-response.md` — Incident triage and escalation procedures
- `restore-drill.md` — Database restore drill checklist
- `replay-failures.md` — Offline replay failure investigation and remediation
