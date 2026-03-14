# Eventing Standard — Impilo vNext (Wave 7)

## Overview

Wave 7 standardizes eventing across ALL Impilo services so every outbox publisher emits **Manifest v1.1-compliant EventEnvelope** events (delta-first), while preserving legacy compatibility via `EMIT_MODE` / `DualEmitPolicy`.

## Event Naming Rules

### Event Types

Format: `impilo.{service}.{entity}.{action}.v{version}`

| Component | Description | Example |
|-----------|-------------|---------|
| `impilo` | Platform prefix (always) | `impilo` |
| `{service}` | Canonical service ID | `vito`, `tuso`, `msika` |
| `{entity}` | Domain entity (lowercase) | `client`, `facility`, `artifact` |
| `{action}` | What happened (past tense) | `created`, `updated`, `deleted`, `published` |
| `v{version}` | Schema version number | `v1`, `v2` |

Examples:
```
impilo.vito.client.created.v1
impilo.tuso.facility.updated.v1
impilo.msika.catalog.published.v1
impilo.zibo.artifact.deprecated.v1
impilo.varapi.provider.created.v1
```

### Snapshot Event Types

Format: `impilo.{service}.snapshot.{entity}.v{version}`

```
impilo.vito.snapshot.client.v1
impilo.tuso.snapshot.facility.v1
```

### Kafka Topics

| Type | Format | Example |
|------|--------|---------|
| Domain events | `impilo.{service}.{domain}` | `impilo.vito.identity` |
| Snapshot events | `impilo.{service}.snapshots` | `impilo.vito.snapshots` |
| Legacy events | `{service}.{domain}` | `vito.identity` |

## Delta vs Snapshot Events

### Delta-First (DeltaPayload)

All write operations MUST emit delta-first events. The payload contains:

```json
{
  "op": "CREATE",
  "before": null,
  "after": {
    "given_name": "Jane",
    "family_name": "Doe",
    "status": "ACTIVE"
  },
  "changed_fields": ["given_name", "family_name", "status"]
}
```

Valid operations: `CREATE`, `UPDATE`, `DELETE`, `MERGE`, `REVOKE`

| Op | `before` | `after` | `changed_fields` |
|----|----------|---------|-------------------|
| CREATE | null | full state | all fields |
| UPDATE | previous state | new state | only changed fields |
| DELETE | final state | null | all fields |
| MERGE | source state | target state | merged fields |
| REVOKE | revoked state | null | revoked fields |

### Snapshot Events

Snapshots are NOT delta-first. They represent full-state point-in-time captures:
- Emitted via dedicated snapshot topic (`impilo.{service}.snapshots`)
- Triggered on-demand via `POST /internal/v1/snapshots/{resource}/emit`
- Or read paginated via `GET /internal/v1/snapshots/{resource}?cursor=0&limit=100`

Snapshot response format:
```json
{
  "as_of": "2026-03-14T06:00:00Z",
  "cursor": 0,
  "limit": 100,
  "has_more": true,
  "total": 1250,
  "items": [
    {"id": "...", "type": "Facility", "name": "...", "status": "ACTIVE"}
  ]
}
```

## EMIT_MODE Matrix

The `DualEmitPolicy` controls which event formats are emitted:

| Mode | Legacy Events | v1.1 Events | Use Case |
|------|--------------|-------------|----------|
| `LEGACY_ONLY` | Yes | No | Pre-migration, all consumers legacy |
| `V1_1_ONLY` | No | Yes | Post-migration, all consumers v1.1 |
| `DUAL` | Yes | Yes | Migration period (default) |

### Precedence (highest to lowest)

1. **System property**: `-DEMIT_MODE=V1_1_ONLY`
2. **Environment variable**: `EMIT_MODE=V1_1_ONLY`
3. **Application config**: `{service}.v11.emit-mode: V1_1_ONLY` in application.yml
4. **Default**: `DUAL`

### Configuration Examples

**Helm values.yml (K8s):**
```yaml
env:
  EMIT_MODE: V1_1_ONLY
```

**application.yml (per-service):**
```yaml
vito:
  v11:
    emit-mode: DUAL
```

**JVM argument:**
```bash
java -DEMIT_MODE=LEGACY_ONLY -jar service.jar
```

## Partition Key Rules

Every v1.1 event MUST include `meta.partition_key`:

| Default Rule | Description |
|-------------|-------------|
| `subject_id` | The primary entity identifier (default for all services) |

Service-specific overrides:

| Service | Partition Key | Rationale |
|---------|--------------|-----------|
| VITO | `health_id` (via subject_id) | Co-locate all patient events |
| TUSO | `facility_id` (via subject_id) | Co-locate all facility events |
| MSIKA | `catalog_id` (via subject_id) | Co-locate catalog item events |
| VARAPI | `provider_ref` (via subject_id) | Co-locate provider events |
| ZIBO | `artifact_id` (via subject_id) | Co-locate terminology events |

## v1.1 EventEnvelope Schema

```json
{
  "event_id": "uuid",
  "event_type": "impilo.vito.client.created.v1",
  "schema_version": 1,
  "correlation_id": "uuid",
  "causation_id": "uuid",
  "idempotency_key": "client-provided-key",
  "producer": "vito",
  "tenant_id": "tenant-uuid",
  "pod_id": "national",
  "occurred_at": "2026-03-14T06:00:00Z",
  "emitted_at": "2026-03-14T06:00:01Z",
  "subject_type": "Patient",
  "subject_id": "cli-001",
  "payload": {
    "op": "CREATE",
    "before": null,
    "after": {"given_name": "Jane"},
    "changed_fields": ["given_name"]
  },
  "meta": {
    "partition_key": "cli-001"
  }
}
```

## Outbox Table Schema (v1.1)

Every service's outbox table includes these columns:

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `id` | BIGSERIAL | PK | Auto-increment row ID |
| `aggregate_type` | VARCHAR | Yes | Domain aggregate (e.g. CLIENT, FACILITY) |
| `aggregate_id` | VARCHAR | Yes | Aggregate instance ID |
| `event_type` | VARCHAR | Yes | Event type string |
| `payload` / `payload_json` | JSONB | Yes | Event payload (delta or legacy) |
| `tenant_id` | TEXT | v1.1 | Tenant scope |
| `pod_id` | TEXT | v1.1 | Originating pod |
| `correlation_id` | TEXT | v1.1 | End-to-end correlation |
| `causation_id` | TEXT | v1.1 | Causing event ID |
| `idempotency_key` | TEXT | v1.1 | Client dedup key |
| `schema_version` | INT | v1.1 | Payload schema version (default 1) |
| `producer` | VARCHAR(64) | v1.1 | Service canonical ID |
| `subject_type` | VARCHAR(64) | v1.1 | Subject entity type |
| `subject_id` | VARCHAR(255) | v1.1 | Subject entity ID |
| `partition_key` | VARCHAR(255) | v1.1 | Kafka partition key |
| `occurred_at` | TIMESTAMPTZ | v1.1 | When domain event happened |
| `created_at` | TIMESTAMPTZ | Yes | Row creation time |
| `published_at` | TIMESTAMPTZ | — | NULL until published |
| `publish_error` | TEXT | — | Error message for poison messages |

## Shared-Kernel-Java Components

### OutboxEventBuilder
Fluent builder for constructing `EventEnvelope` with `DeltaPayload`. Auto-generates optional fields.

```java
EventEnvelope envelope = OutboxEventBuilder.forProducer("vito")
    .aggregateType("CLIENT").aggregateId("cli-1")
    .eventType("impilo.vito.client.created.v1")
    .tenantId("t1").podId("national")
    .deltaCreate(afterState)
    .build();
```

### EventTopicRegistry
Canonical topic naming helpers:

```java
EventTopicRegistry reg = new EventTopicRegistry("vito");
reg.eventType("client", "created");     // impilo.vito.client.created.v1
reg.v11Topic("identity");               // impilo.vito.identity
reg.snapshotTopic();                     // impilo.vito.snapshots
reg.snapshotEventType("client");         // impilo.vito.snapshot.client.v1
```

### CompanionOutboxPublisher
Abstract base class for outbox publishers. Services extend and implement:
- `fetchUnpublished()` — fetch outbox rows
- `sendToKafka(topic, key, value)` — send to Kafka
- `markPublished(row, timestamp)` — mark row published
- `markFailed(row, error)` — handle poison messages
- `resolveLegacyTopic(row)` — legacy topic routing

### DualEmitPolicy
Resolves emit mode with full precedence chain (sys prop → env → config → DUAL).

## Ring 0 Snapshot Endpoints

| Service | GET Endpoint | POST Emit Endpoint |
|---------|-------------|-------------------|
| VITO | `/internal/v1/snapshots/clients` | `/internal/v1/snapshots/clients/emit` |
| TUSO | `/internal/v1/snapshots/facilities` | `/internal/v1/snapshots/facilities/emit` |
| VARAPI | `/internal/v1/snapshots/providers` | `/internal/v1/snapshots/providers/emit` |
| MSIKA | `/internal/v1/snapshots/catalogs` | `/internal/v1/snapshots/catalogs/emit` |
| ZIBO | `/internal/v1/snapshots/artifacts` | `/internal/v1/snapshots/artifacts/emit` |

## Running Tests

```bash
# Shared kernel tests
cd libs/shared-kernel-java && mvn test

# Service-level contract tests
cd services/vito-service && mvn test -Dtest=OutboxV11EnvelopeContractTest
cd services/tuso-service && mvn test -Dtest=OutboxV11EnvelopeContractTest
cd services/zibo-service && mvn test -Dtest=OutboxV11EnvelopeContractTest
```

## Migration Path

1. Deploy with `EMIT_MODE=DUAL` (default) — both legacy and v1.1 events emitted
2. Migrate consumers to read from v1.1 topics
3. Switch to `EMIT_MODE=V1_1_ONLY` once all consumers migrated
4. Remove legacy topic routing code in a future wave
