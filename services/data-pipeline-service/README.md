# Data Pipeline Service

v1.1-native ingestion and watermarking service for the Impilo platform.

## Overview

The Data Pipeline Service ingests v1.1 EventEnvelope events via an internal POST API, writes curated pipeline records, and maintains per-source watermarks for exactly-once processing guarantees. It is designed as a v1.1-native service — all endpoints live under `/internal/v1/` and are subject to the full Tech Companion filter chain (header enforcement, idempotency, timeout).

## Port

- **Local dev**: `8140`
- **Database**: `pipeline` (PostgreSQL schema)

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/v1/ingest` | Ingest a v1.1 EventEnvelope |
| GET | `/internal/v1/watermarks` | List per-source watermarks for the tenant |
| POST | `/internal/v1/sources` | Register a new ingestion source |
| GET | `/internal/v1/sources` | List registered sources |

### POST /internal/v1/ingest

Accepts a v1.1 `EventEnvelope` JSON payload:

```json
{
  "eventId": "evt-001",
  "tenantId": "uuid",
  "sourceId": "pct-service",
  "eventType": "JOURNEY_CREATED",
  "aggregateType": "Journey",
  "aggregateId": "J-001",
  "occurredAt": "2026-01-15T10:30:00Z",
  "podId": "national",
  "correlationId": "corr-uuid",
  "causationId": null,
  "payload": { "key": "value" },
  "schemaVersion": "1.1"
}
```

**Behavior:**
- Deduplicates by `eventId` (returns `DUPLICATE` if already ingested)
- Validates the `sourceId` is registered and enabled (returns `REJECTED` if not)
- Persists the event as a curated `dp_ingested_events` record
- Advances the per-source watermark
- Emits `EVENT_INGESTED` and `WATERMARK_UPDATED` outbox events

### Required Headers (v1.1)

All endpoints require:
- `X-Tenant-ID`
- `X-Pod-ID`
- `X-Request-ID`
- `X-Correlation-ID`

POST/PUT/PATCH endpoints additionally require:
- `Idempotency-Key`

## Database Schema

5 tables prefixed with `dp_`:

| Table | Purpose |
|-------|---------|
| `dp_ingestion_sources` | Registered event sources |
| `dp_ingested_events` | Curated pipeline records from EventEnvelopes |
| `dp_pipeline_watermarks` | Per-source high-water marks |
| `dp_event_outbox` | Transactional outbox for Kafka publishing |
| `idempotency_keys` | v1.1 idempotency support |

## Kafka Topics

| Topic | Event Type |
|-------|-----------|
| `impilo.pipeline.event.ingested.v1` | `EVENT_INGESTED` |
| `impilo.pipeline.watermark.updated.v1` | `WATERMARK_UPDATED` |

## Testing

```bash
# Run all tests
cd services/data-pipeline-service
mvn test

# Tests include:
# - DataPipelineGoldenContractIT (v1.1 compliance)
# - IngestionServiceTest (5 behavior tests)
# - WatermarkServiceTest (4 behavior tests)
# - SourceServiceTest (4 behavior tests)
# - OutboxPublisherTest (6 behavior tests)
# - IngestControllerTest (6 integration tests)
```

## Architecture

```
zw.gov.mohcc.impilo.pipeline/
├── DataPipelineApplication.java
├── api/
│   ├── controller/
│   │   ├── IngestController.java
│   │   ├── WatermarkController.java
│   │   └── SourceController.java
│   └── dto/
│       ├── EventEnvelope.java
│       ├── IngestResponse.java
│       ├── CreateSourceRequest.java
│       └── WatermarkResponse.java
├── config/
│   ├── PipelineProperties.java
│   └── SecurityConfig.java
├── core/
│   ├── IngestionService.java
│   ├── WatermarkService.java
│   └── SourceService.java
├── domain/
│   ├── IngestionStatus.java
│   └── SourceType.java
├── events/
│   └── OutboxPublisher.java
└── persistence/
    ├── entity/
    │   ├── IngestedEventEntity.java
    │   ├── IngestionSourceEntity.java
    │   ├── PipelineWatermarkEntity.java
    │   └── EventOutboxEntity.java
    └── repository/
        ├── IngestedEventRepository.java
        ├── IngestionSourceRepository.java
        ├── PipelineWatermarkRepository.java
        └── EventOutboxRepository.java
```
