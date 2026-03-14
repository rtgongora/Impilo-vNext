# Supply & IoT Platform — Acceptance Pack

## Module List

| Module | Type | Port |
|---|---|---|
| `services/asset-registry-service` | Spring Boot | 8310 |
| `services/dispatch-service` | Spring Boot | 8320 |
| `services/iot-ingestion-service` | Spring Boot | 8330 |

## Build & Test

```bash
cd services
mvn test -pl asset-registry-service,dispatch-service,iot-ingestion-service -Dspring.profiles.active=test
```

## Test Coverage Matrix

### asset-registry-service

| Test Class | Tests | Coverage |
|---|---|---|
| `AssetRegistryGoldenContractIT` | 12 | v1.1 headers, idempotency, timeout, error envelope |
| `AssetApiMockMvcTest` | 12 | Missing headers (3), idempotency replay (2), snapshot (2), outbox validation (2), retire lifecycle (2), status change (2), external listing (1) |

### dispatch-service

| Test Class | Tests | Coverage |
|---|---|---|
| `DispatchGoldenContractIT` | 12 | v1.1 headers, idempotency, timeout, error envelope |
| `DispatchApiMockMvcTest` | 10 | Missing headers (2), missing idempotency-key (1), idempotency replay (1), identity conflict (1), outbox validation (3), invalid status transition (1), snapshot (2) |

### iot-ingestion-service

| Test Class | Tests | Coverage |
|---|---|---|
| `IoTIngestionGoldenContractIT` | 12 | v1.1 headers, idempotency, timeout, error envelope |
| `TelemetryApiMockMvcTest` | 9 | Missing headers (2), telemetry ingestion (2), DLQ on invalid schema (2), outbox telemetry events (1), batch ingest (1), list readings (1) |

## Smoke Test Scripts

### Prerequisites

Each service requires PostgreSQL. For local testing, use Docker Compose or the test profile (H2).

### Asset Registry (port 8310)

```bash
# Create asset
curl -s -X PUT http://localhost:8310/internal/v1/assets/$(uuidgen) \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-asset-$(date +%s)" \
  -d '{"facilityRef":"FAC-001","type":"COLD_CHAIN","serialNo":"CC-12345","status":"ACTIVE"}' | jq .

# List assets
curl -s http://localhost:8310/internal/v1/assets \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Update status
curl -s -X PUT http://localhost:8310/internal/v1/assets/{asset_id}/status \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-status-$(date +%s)" \
  -d '{"status":"MAINTENANCE"}' | jq .

# External listing (policy-filtered)
curl -s http://localhost:8310/external/v1/assets \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .

# Snapshot
curl -s http://localhost:8310/internal/v1/snapshots/assets?limit=10 \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

### Dispatch (port 8320)

```bash
# Create job
curl -s -X POST http://localhost:8320/internal/v1/dispatch/jobs \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-job-$(date +%s)" \
  -d '{"facilityRef":"FAC-001","requestRef":"REQ-123"}' | jq .

# Assign job
curl -s -X POST http://localhost:8320/internal/v1/dispatch/jobs/{job_id}/assign \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-assign-$(date +%s)" \
  -d '{"agentRef":"AGENT-001","vehicleRef":"VEH-001"}' | jq .

# Update status
curl -s -X POST http://localhost:8320/internal/v1/dispatch/jobs/{job_id}/status \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -H "Idempotency-Key: smoke-status-$(date +%s)" \
  -d '{"status":"IN_PROGRESS"}' | jq .

# List jobs
curl -s http://localhost:8320/internal/v1/dispatch/jobs \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

### IoT Ingestion (port 8330)

```bash
# Ingest single telemetry reading
curl -s -X POST http://localhost:8330/internal/v1/telemetry/ingest \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -d '{"deviceId":"SENSOR-001","metricType":"TEMPERATURE","metricValue":2.5,"unit":"C","schemaVersion":"1","recordedAt":"2026-03-14T10:00:00Z"}' | jq .

# Batch ingest
curl -s -X POST http://localhost:8330/internal/v1/telemetry/ingest/batch \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -d '{"readings":[{"deviceId":"SENSOR-001","metricType":"TEMPERATURE","metricValue":2.5,"unit":"C","schemaVersion":"1","recordedAt":"2026-03-14T10:00:00Z"},{"deviceId":"SENSOR-002","metricType":"HUMIDITY","metricValue":65.0,"unit":"%","schemaVersion":"1","recordedAt":"2026-03-14T10:00:00Z"}]}' | jq .

# Ingest with invalid schema (should return 422 + DLQ)
curl -s -X POST http://localhost:8330/internal/v1/telemetry/ingest \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" \
  -d '{"deviceId":"SENSOR-BAD","metricType":"TEMPERATURE","metricValue":2.5,"unit":"C","schemaVersion":"999","recordedAt":"2026-03-14T10:00:00Z"}' | jq .

# List readings
curl -s http://localhost:8330/internal/v1/telemetry/readings?device_id=SENSOR-001 \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: $(uuidgen)" \
  -H "X-Correlation-ID: $(uuidgen)" | jq .
```

## Failure Triage Map

| Symptom | Likely Cause | Fix |
|---|---|---|
| 400 MISSING_REQUIRED_HEADER | Missing v1.1 headers | Add X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID |
| 400 IDEMPOTENCY_KEY_REQUIRED | Missing Idempotency-Key on POST/PUT | Add Idempotency-Key header |
| 409 IDENTITY_CONFLICT | Same Idempotency-Key, different body | Use a new Idempotency-Key |
| 422 INVALID_STATUS_TRANSITION | Invalid dispatch state machine transition | Check allowed transitions: NEW->ASSIGNED->IN_PROGRESS->COMPLETED |
| 422 rejected (IoT) | Invalid schema_version | Use schema_version "1" or "2" |
| 504 CLIENT_TIMEOUT_EXCEEDED | X-Client-Timeout-MS already expired | Increase timeout or remove header |
| H2 test failures | DDL mismatch | Ensure entity column annotations match table schema |

## Docker Compose (suggested)

```yaml
services:
  asset-registry-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_asset_registry
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5440:5432"]

  dispatch-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_dispatch
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5441:5432"]

  iot-ingestion-db:
    image: postgres:16
    environment:
      POSTGRES_DB: impilo_iot_ingestion
      POSTGRES_USER: impilo
      POSTGRES_PASSWORD: impilo
    ports: ["5442:5432"]
```
