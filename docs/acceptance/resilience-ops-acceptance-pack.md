# Resilience & Operations Platform — Acceptance Pack

## Module List

| Module | Type | Port |
|---|---|---|
| `services/observability-service` | Spring Boot | 8210 |
| `services/audit-ledger-service` | Spring Boot | 8350 |
| `services/support-service` | Spring Boot | 8340 |

## Build & Test

```bash
cd services
mvn test -pl observability-service,audit-ledger-service,support-service -Dspring.profiles.active=test
```

## Test Coverage Matrix

### observability-service

| Test Class | Tests | Coverage |
|---|---|---|
| `ObsGoldenContractIT` | 12 | v1.1 headers, idempotency, timeout, error envelope |
| `OpsApiMockMvcTest` | 6 | Missing headers (2), heartbeat create (1), heartbeat update (1), health summary (1), metrics lag (1) |
| `DashboardServiceTest` | 3 | Dashboard creation with outbox, list dashboards |
| `ObsOutboxPublisherTest` | 2 | Topic resolution |

### audit-ledger-service

| Test Class | Tests | Coverage |
|---|---|---|
| `AuditLedgerGoldenContractIT` | 12 | v1.1 headers, idempotency, timeout, error envelope |
| `AuditLedgerApiMockMvcTest` | 8 | Missing headers (1), hash chaining genesis (1), chain continuity (1), chain verification (1), correlation query (1), outbox validation (1), immutability enforcement (1), list records (1) |

### support-service

| Test Class | Tests | Coverage |
|---|---|---|
| `SupportGoldenContractIT` | 12 | v1.1 headers, idempotency, timeout, error envelope |
| `SupportApiMockMvcTest` | 8 | Missing headers (2), ticket create (1), ticket update/resolve (1), ticket not found (1), list tickets (1), outbox validation (1), request/correlation tracking (1) |

## Smoke Test Scripts

### Prerequisites

Each service requires PostgreSQL. For local testing, use Docker Compose or the test profile (H2).

### Observability Service (port 8210)

```bash
# Record heartbeat
curl -s -X POST http://localhost:8210/internal/v1/ops/heartbeat \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-hb-$(date +%s)" \
  -d '{"serviceName":"tshepo-service","instanceId":"tshepo-01","status":"UP","versionTag":"1.0.0"}' | jq .

# Health summary
curl -s http://localhost:8210/internal/v1/ops/health/summary \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Metrics lag
curl -s http://localhost:8210/internal/v1/ops/metrics/lag \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Create dashboard
curl -s -X POST http://localhost:8210/internal/v1/dashboards \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-dash-$(date +%s)" \
  -d '{"name":"Ops Overview","description":"Main ops dashboard","dashboardType":"GRAFANA","config":"{}"}' | jq .

# List dashboards
curl -s http://localhost:8210/internal/v1/dashboards \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

### Audit Ledger Service (port 8350)

```bash
# Append audit record
curl -s -X POST http://localhost:8350/internal/v1/audit/records \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-audit-$(date +%s)" \
  -d '{"correlationId":"'"$(uuidgen)"'","actorId":"admin-001","actorType":"USER","action":"LOGIN","resourceType":"Session","resourceId":"sess-001","outcome":"SUCCESS","detail":{}}' | jq .

# List records
curl -s http://localhost:8350/internal/v1/audit/records?cursor=0&limit=10 \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Verify chain integrity
curl -s "http://localhost:8350/internal/v1/audit/chain/verify?from_seq=1&to_seq=10" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Query by correlation ID
curl -s "http://localhost:8350/internal/v1/audit/query?correlation_id=<UUID>" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

### Support Service (port 8340)

```bash
# Create ticket
curl -s -X POST http://localhost:8340/internal/v1/support/tickets \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-ticket-$(date +%s)" \
  -d '{"title":"EHR System Down","description":"Cannot access patient records","reporterRef":"nurse-001","category":"INCIDENT","priority":"HIGH","facilityRef":"FAC-001"}' | jq .

# Update ticket
curl -s -X PATCH http://localhost:8340/internal/v1/support/tickets/{ticket_id} \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-update-$(date +%s)" \
  -d '{"status":"RESOLVED","resolution":"Database connection restored","assigneeRef":"admin-001"}' | jq .

# List tickets
curl -s http://localhost:8340/internal/v1/support/tickets \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Ticket snapshot
curl -s http://localhost:8340/internal/v1/snapshots/tickets?limit=10 \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

## Failure Triage Map

| Symptom | Likely Cause | Fix |
|---|---|---|
| 400 MISSING_REQUIRED_HEADER | Missing v1.1 headers | Add X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID |
| 400 IDEMPOTENCY_KEY_REQUIRED | Missing Idempotency-Key on POST/PUT/PATCH | Add Idempotency-Key header |
| 409 IDENTITY_CONFLICT | Same Idempotency-Key, different body | Use a new Idempotency-Key |
| 504 CLIENT_TIMEOUT_EXCEEDED | X-Client-Timeout-MS already expired | Increase timeout or remove header |
| Chain verify returns false | Tampered or missing audit records | Investigate affected sequence range |
| Heartbeat status STALE | Service not sending heartbeats | Check service health, restart if needed |
| H2 test failures | DDL mismatch | Ensure entity column annotations match table schema |

## Docker Compose (suggested)

```yaml
services:
  observability-db:
    image: postgres:16
    environment:
      POSTGRES_DB: obs
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5450:5432"]

  audit-ledger-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_audit_ledger
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5451:5432"]

  support-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_support
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5452:5432"]
```
