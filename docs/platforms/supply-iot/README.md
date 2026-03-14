# Supply & IoT Platform — Architecture Overview

## Services

| Service | Port | Description |
|---|---|---|
| **asset-registry-service** | 8310 | Asset & equipment CRUD, lifecycle status, assignment tracking |
| **dispatch-service** | 8320 | Dispatch job creation, assignment, status transitions |
| **iot-ingestion-service** | 8330 | HTTP telemetry ingestion, append-only store, telemetry bus publisher |

## Data Flow

```
Device/Sensor ──HTTP──> iot-ingestion-service ──> iot_telemetry_readings (append-only)
                                │
                                └──> iot_event_outbox ──> impilo.iot.telemetry.reading.ingested.v1

Facility ──HTTP──> asset-registry-service ──> asr_assets (lifecycle state)
                                │
                                └──> asr_event_outbox ──> impilo.asset.asset.{created|updated|retired|status.changed}.v1

Operations ──HTTP──> dispatch-service ──> dsp_dispatch_jobs + dsp_dispatch_events
                                │
                                └──> dsp_event_outbox ──> impilo.dispatch.job.{created|assigned|status.updated}.v1
```

## Bus Discipline

IoT telemetry events are published to a **separate logical bus namespace** (`impilo.iot.telemetry.*`) to prevent interference with clinical event partitions:

| Producer | Event Type Pattern | Partition Key |
|---|---|---|
| asset-registry-service | `impilo.asset.asset.*.v1` | `asset_id` |
| dispatch-service | `impilo.dispatch.job.*.v1` | `job_id` |
| iot-ingestion-service | `impilo.iot.telemetry.*.v1` | `device_id` |

All events use the `EventEnvelope` format from `shared-kernel-java` with `schema_version >= 1`.

## v1.1 Compliance

All three services are v1.1-native:

- **Header enforcement**: `V11HeaderFilter` (order 10) requires `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`
- **Idempotency**: `IdempotencyFilter` (order 11) requires `Idempotency-Key` on POST/PUT/PATCH commands
- **Timeout**: `TimeoutEnforcementFilter` (order 12) respects `X-Client-Timeout-MS`
- **Outbox pattern**: Each service writes to its own `*_event_outbox` table with v1.1 context columns
- **GoldenContractIT**: Each service extends `GoldenContractSuite` for automated v1.1 compliance testing

## Asset Registry Service

### Database Schema

**asr_assets** — Lifecycle-tracked equipment and devices:
- `asset_id` (UUID PK), `tenant_id`, `facility_ref`, `type`, `serial_no`
- `status` (ACTIVE/MAINTENANCE/RETIRED), `assigned_to`, `last_seen_at`
- `metadata_json` (JSONB), `version`, `created_at`, `updated_at`

### API Endpoints

| Method | Path | Description |
|---|---|---|
| PUT | `/internal/v1/assets/{asset_id}` | Upsert asset (create or update) |
| GET | `/internal/v1/assets/{asset_id}` | Get asset by ID |
| GET | `/internal/v1/assets` | List/search assets (filter by facility_id, status) |
| PUT | `/internal/v1/assets/{asset_id}/status` | Update asset lifecycle status |
| DELETE | `/internal/v1/assets/{asset_id}` | Retire asset (soft delete) |
| GET | `/external/v1/assets/{asset_id}` | External: get asset (policy-filtered) |
| GET | `/external/v1/assets` | External: list assets (policy-filtered) |
| GET | `/internal/v1/snapshots/assets` | Point-in-time snapshot |

### Events

- `impilo.asset.asset.created.v1` — Delta CREATE with full after-state
- `impilo.asset.asset.updated.v1` — Delta UPDATE with before/after + changed_fields
- `impilo.asset.asset.retired.v1` — Delta UPDATE with status=RETIRED
- `impilo.asset.asset.status.changed.v1` — Delta UPDATE for status transitions

## Dispatch Service

### Database Schema

**dsp_dispatch_jobs** — Logistics job tracking:
- `job_id` (UUID PK), `tenant_id`, `facility_ref`, `request_ref`
- `status` (NEW/ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED)
- `assigned_agent_ref`, `assigned_vehicle_ref`, `created_at`, `updated_at`

**dsp_dispatch_events** — Event log per job:
- `job_id` (FK), `status`, `notes_json`, `created_at`

### Status Machine

```
NEW ──> ASSIGNED ──> IN_PROGRESS ──> COMPLETED
 │         │              │
 └── CANCELLED ◄──────────┘
```

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/internal/v1/dispatch/jobs` | Create dispatch job |
| POST | `/internal/v1/dispatch/jobs/{job_id}/assign` | Assign agent/vehicle |
| POST | `/internal/v1/dispatch/jobs/{job_id}/status` | Update job status |
| GET | `/internal/v1/dispatch/jobs/{job_id}` | Get job by ID |
| GET | `/internal/v1/dispatch/jobs` | List/search jobs |
| GET | `/internal/v1/snapshots/jobs` | Point-in-time snapshot |

### Events

- `impilo.dispatch.job.created.v1` — Full job state on creation
- `impilo.dispatch.job.assigned.v1` — Job state + assignment details
- `impilo.dispatch.job.status.updated.v1` — previous_status + new_status + job state

## IoT Ingestion Service

### Database Schema

**iot_telemetry_readings** — Append-only telemetry store:
- `reading_id` (UUID), `device_id`, `tenant_id`
- `metric_type`, `metric_value`, `unit`, `schema_version`
- `recorded_at`, `ingested_at`, `source` (HTTP/KAFKA)
- `metadata_json`

**iot_telemetry_dlq** — Dead letter queue for invalid payloads:
- `dlq_id`, `device_id`, `raw_payload`, `error_code`, `error_message`

### API Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/internal/v1/telemetry/ingest` | Ingest single telemetry reading |
| POST | `/internal/v1/telemetry/ingest/batch` | Batch ingest multiple readings |
| GET | `/internal/v1/telemetry/readings` | List readings (filter by device_id, metric_type) |

### Kafka Consumer

Listens on `impilo.iot.telemetry.device.raw` topic for device-pushed telemetry via the Kafka consumer group `iot-ingestion-telemetry-cg`. Disabled by default (`impilo.telemetry.kafka.enabled=false`).

### Events

- `impilo.iot.telemetry.reading.ingested.v1` — Emitted per ingested reading with `partition_key=device_id`

### Schema Version Validation

Readings with unsupported `schema_version` are rejected with HTTP 422 and written to the DLQ. Supported versions: `1`, `2`.

## Test Profiles

All services use H2 in-memory databases with `create-drop` DDL for tests:
- `application-test.yml` disables Flyway and configures H2
- IoT service additionally disables Kafka auto-configuration in test profile
