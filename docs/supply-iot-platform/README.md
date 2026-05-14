# Supply & IoT Platform — Asset Registry + Dispatch + IoT Ingestion

> **Status:** v1.1-native — fully implemented with domain logic, outbox events, and integration tests.
> **Date:** 2026-03-14

## Strategic Intent

**Track every health asset from procurement to decommission. Ingest device telemetry on a separated bus.**

The Supply & IoT Platform provides asset lifecycle management, logistics
coordination, and device telemetry ingestion for the Impilo health platform.
It bridges the gap between inventory management (what's in stock) and
operational visibility (where equipment is, whether it's working, and what
its sensors are reporting).

## Services

| Service | Port | Artifact ID | Purpose |
|---------|------|-------------|---------|
| Asset Registry | 8310 | `asset-registry-service` | Lifecycle management for medical equipment, devices, cold-chain units, vehicles |
| Dispatch | 8320 | `dispatch-service` | Logistics coordination, job assignment, status tracking |
| IoT Ingestion | 8330 | `iot-ingestion-service` | Device telemetry ingestion via HTTP and Kafka, append-only store, DLQ |

## Architecture Position

These services sit in the **Integration Plane** (`platform-ops` domain) as consolidated Ring-2 services:

```
┌─────────────────────────────────────────────────────┐
│                 Supply Chain Stack                    │
├──────────────────┬──────────────────────────────────┤
│  inventory-      │  What's in stock at each         │
│  service         │  facility (Ring-1)               │
├──────────────────┼──────────────────────────────────┤
│  pharmacy-       │  Medication dispensing &          │
│  service         │  stock management (Ring-1)       │
├──────────────────┼──────────────────────────────────┤
│  asset-registry- │  Equipment & device lifecycle    │
│  service         │  tracking (Ring-2 / Ops)         │
├──────────────────┼──────────────────────────────────┤
│  dispatch-       │  Logistics jobs, assignment,      │
│  service         │  status tracking (Ring-2 / Ops)  │
├──────────────────┼──────────────────────────────────┤
│  iot-ingestion-  │  Device telemetry ingestion,      │
│  service         │  separated bus (Ring-2 / IoT)    │
└──────────────────┴──────────────────────────────────┘
```

## Domain Boundaries

### Asset Registry Service
- **Owns:** Asset entities (equipment, cold-chain units, vehicles, IoT devices)
- **Operations:** Create, update, retire assets; assign to facilities
- **State machine:** ACTIVE → INACTIVE → RETIRED
- **Snapshot:** `GET /internal/v1/snapshots/assets` for bootstrapping registry state

### Dispatch Service
- **Owns:** Dispatch job entities and their event log
- **Operations:** Create, assign, start, complete, cancel jobs
- **State machine:** NEW → ASSIGNED → IN_PROGRESS → COMPLETED/CANCELLED
- **Snapshot:** `GET /internal/v1/snapshots/jobs` for bootstrapping job state

### IoT Ingestion Service
- **Owns:** Telemetry readings (append-only) and DLQ entries
- **Operations:** Ingest single/batch telemetry readings via HTTP or Kafka consumer
- **Constraint:** Telemetry bus is **separated** — topic namespace `impilo.telemetry.*` with its own consumer group `iot-ingestion-telemetry-cg`
- **Backpressure:** Invalid schema versions route to DLQ table, never block ingestion

## v1.1 Compliance

All three services are born v1.1-native:

- **Header enforcement** via `tech-companion` auto-configuration
  - Required: `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`, `Authorization`
- **Idempotency-Key** required on command endpoints (POST/PUT/PATCH) for asset-registry and dispatch
- **Event outbox** table with all v1.1 context columns
- **Delta-first events** with `op`, `before`, `after`, `changed_fields` payloads
- **Error envelope** via `ErrorEnvelope.of(...)` on all error responses
- **GoldenContractIT** extending `GoldenContractSuite` for automated contract verification

## Schema Prefixes

| Service | Prefix | Tables |
|---------|--------|--------|
| Asset Registry | `asr_` | `asr_assets`, `asr_event_outbox`, `asr_idempotency_keys` |
| Dispatch | `dsp_` | `dsp_dispatch_jobs`, `dsp_dispatch_events`, `dsp_event_outbox`, `dsp_idempotency_keys` |
| IoT Ingestion | `iot_` | `iot_telemetry_readings`, `iot_telemetry_dlq`, `iot_event_outbox`, `iot_idempotency_keys` |

## Event Topics & Namespace Discipline

### Asset Registry Events (topic: `impilo.asset.events`)
| Event Type | Trigger |
|-----------|---------|
| `impilo.asset.asset.created.v1` | New asset registered |
| `impilo.asset.asset.updated.v1` | Asset details modified |
| `impilo.asset.asset.retired.v1` | Asset retired (end-of-life) |

### Dispatch Events (topic: `impilo.dispatch.events`)
| Event Type | Trigger |
|-----------|---------|
| `impilo.dispatch.job.created.v1` | New dispatch job created |
| `impilo.dispatch.job.assigned.v1` | Agent/vehicle assigned to job |
| `impilo.dispatch.job.status.updated.v1` | Job status transition |

### Telemetry Events (topic: `impilo.telemetry.device.events` — **SEPARATE BUS**)
| Event Type | Trigger |
|-----------|---------|
| `impilo.telemetry.device.reading.ingested.v1` | Telemetry reading accepted and stored |

> **Critical:** Telemetry events use the `impilo.telemetry.*` namespace and a separate
> consumer group (`iot-ingestion-telemetry-cg`). They must **never** mix with clinical
> or business event buses.

## API Endpoints

### Asset Registry (port 8310)
| Method | Path | Purpose |
|--------|------|---------|
| PUT | `/internal/v1/assets/{asset_id}` | Upsert asset (create or update) |
| DELETE | `/internal/v1/assets/{asset_id}` | Retire asset |
| GET | `/internal/v1/assets/{asset_id}` | Get asset (internal) |
| GET | `/external/v1/assets/{asset_id}` | Get asset (external, stripped) |
| GET | `/internal/v1/assets` | List assets (filtered, paginated) |
| GET | `/internal/v1/snapshots/assets` | Snapshot for bootstrapping |

### Dispatch (port 8320)
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/internal/v1/dispatch/jobs` | Create dispatch job |
| POST | `/internal/v1/dispatch/jobs/{job_id}/assign` | Assign agent/vehicle |
| POST | `/internal/v1/dispatch/jobs/{job_id}/status` | Update job status |
| GET | `/internal/v1/dispatch/jobs/{job_id}` | Get job |
| GET | `/internal/v1/dispatch/jobs` | List jobs (filtered, paginated) |
| GET | `/internal/v1/snapshots/jobs` | Snapshot for bootstrapping |

### IoT Ingestion (port 8330)
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/internal/v1/telemetry/ingest` | Ingest single reading |
| POST | `/internal/v1/telemetry/ingest/batch` | Ingest batch readings |
| GET | `/internal/v1/telemetry/readings` | List readings (filtered, paginated) |

### Kafka Consumer (IoT Ingestion)
| Topic | Consumer Group | Purpose |
|-------|---------------|---------|
| `impilo.telemetry.device.raw` | `iot-ingestion-telemetry-cg` | Ingest telemetry from device gateways |

## How to Run

### Prerequisites
- Java 21
- PostgreSQL 16 (or Docker Compose)
- Kafka 3.7.x (for IoT Kafka consumer — optional, disabled by default)

### Database Setup
```bash
# Create databases
createdb impilo_asset_registry
createdb impilo_dispatch
createdb impilo_iot_ingestion
```

### Start Services
```bash
# Asset Registry (port 8310)
cd services/asset-registry-service
mvn spring-boot:run

# Dispatch (port 8320)
cd services/dispatch-service
mvn spring-boot:run

# IoT Ingestion (port 8330)
cd services/iot-ingestion-service
mvn spring-boot:run
```

### Run Tests
```bash
# All three services
mvn test -pl services/asset-registry-service,services/dispatch-service,services/iot-ingestion-service

# Individual service
mvn test -pl services/iot-ingestion-service
```

### Enable Kafka Consumer (IoT)
```bash
# Set environment variable to enable Kafka consumer
TELEMETRY_KAFKA_ENABLED=true mvn spring-boot:run -pl services/iot-ingestion-service
```

## Test Coverage

| Service | Test Class | Coverage |
|---------|-----------|----------|
| Asset Registry | `AssetRegistryGoldenContractIT` | v1.1 contract compliance |
| Asset Registry | `AssetApiMockMvcTest` | Headers, idempotency, snapshot, outbox, retire lifecycle |
| Dispatch | `DispatchGoldenContractIT` | v1.1 contract compliance |
| Dispatch | `DispatchApiMockMvcTest` | Headers, idempotency, replay/conflict, outbox, status transitions, snapshot |
| IoT Ingestion | `IoTIngestionGoldenContractIT` | v1.1 contract compliance |
| IoT Ingestion | `TelemetryApiMockMvcTest` | Headers, append-only ingestion, DLQ on invalid schema, outbox telemetry events, batch ingest |
