# Impilo vNext — Eventing & Topics Convention (v1.1)

**Version**: 1.0
**Date**: 2026-02-14
**Scope**: All Impilo services — legacy and Outstanding 27

---

## 1. v1.1 EventEnvelope

Every domain event emitted by any Impilo service MUST be wrapped in the canonical `EventEnvelope`. The Java implementation lives in `libs/shared-kernel-java` as an immutable record.

### 1.1 Mandatory Fields

| Field | Type | Source | Description |
|---|---|---|---|
| `event_id` | UUID (v7 recommended) | Auto-generated | Globally unique event identifier |
| `event_type` | String | Producer sets | Dot-delimited: `{bus}.{service}.{aggregate}.{action}` |
| `schema_version` | String (semver `major.minor`) | Producer sets | Schema version of this event type (e.g., `1.0`) |
| `correlation_id` | UUID | From `X-Correlation-ID` header | End-to-end request correlation |
| `causation_id` | UUID (nullable) | Producer sets | ID of the event that caused this event; null for user-initiated |
| `idempotency_key` | String (max 255) | Producer computes | Deduplication key: `{producer}:{subject_type}:{subject_id}:{action}:{version}` |
| `producer` | String | Service name | Service that emitted this event (e.g., `pct-service`) |
| `tenant_id` | UUID | From `X-Tenant-ID` header | Tenant (health authority) that owns this data |
| `pod_id` | String | From `X-Pod-ID` header | Pod identifier; `national-spine` for Level 1 national deployment |
| `subject_id` | String | Producer sets | Primary entity ID this event concerns |
| `subject_type` | String | Producer sets | Type of the subject entity (e.g., `Journey`, `Order`, `Client`) |
| `occurred_at` | ISO-8601 timestamp | Producer sets | When the domain event actually happened (business time) |
| `emitted_at` | ISO-8601 timestamp | Auto-generated | When the event was published to the bus (system time) |
| `data` | Object | Producer sets | Event-specific payload — MUST be delta for updates (Law 4) |

### 1.2 Java Record (libs/shared-kernel-java)

```java
public record EventEnvelope(
    String eventId,
    String eventType,
    int schemaVersion,
    String correlationId,
    String causationId,
    String idempotencyKey,
    String producer,
    String tenantId,
    String podId,
    OffsetDateTime occurredAt,
    OffsetDateTime emittedAt,
    String subjectType,
    String subjectId,
    Map<String, Object> payload,
    Map<String, Object> meta
) { }
```

### 1.3 Idempotency Key Formula

```
{producer}:{subject_id}:{event_type_short}:{entity_version}
```

Examples:
- `vito-service:crid-12345:client.updated:7`
- `pct-service:journey-abc:journey.created:1`
- `oros-service:order-xyz:order.placed:1`

Consumers MUST deduplicate by `idempotency_key`. If a consumer sees the same key twice, the second event MUST be silently discarded (no side effects).

---

## 2. Outbox Pattern

### 2.1 Requirements

Every service that emits domain events MUST use the transactional outbox pattern:

1. Domain operation and outbox insert happen in the **same database transaction**
2. A scheduled publisher polls the outbox and publishes to Kafka
3. Published events are marked with `published_at` timestamp
4. Failed publications are retried on next poll cycle

### 2.2 Outbox Table Schema

All new services (Outstanding 27) MUST use this outbox table schema. Legacy services retain their existing schema but SHOULD migrate to this schema when next modified.

```sql
CREATE TABLE {service}_event_outbox (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64) NOT NULL,
    aggregate_id        VARCHAR(255) NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    schema_version      VARCHAR(16) NOT NULL DEFAULT '1.0',
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255) NOT NULL,
    producer            VARCHAR(64) NOT NULL,
    tenant_id           UUID NOT NULL,
    pod_id              VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255) NOT NULL,
    subject_type        VARCHAR(64) NOT NULL,
    occurred_at         TIMESTAMPTZ NOT NULL,
    payload_json        JSONB NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    CONSTRAINT uq_{service}_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_{service}_outbox_unpublished
    ON {service}_event_outbox (created_at)
    WHERE published_at IS NULL;
```

Replace `{service}` with the service-specific prefix (e.g., `varapi`, `tuso`, `scheduling`).

### 2.3 Outbox Publisher Configuration

| Parameter | Default | Description |
|---|---|---|
| `impilo.outbox.poll-interval-ms` | `500` | Polling interval for unpublished events |
| `impilo.outbox.batch-size` | `100` | Max events per poll cycle |
| `impilo.outbox.retry-max` | `5` | Max retry attempts before dead-letter |
| `impilo.outbox.dead-letter-topic` | `{bus}.dlq` | Dead-letter topic for poison events |

### 2.4 Publisher Behavior

```
@Scheduled(fixedDelayString = "${impilo.outbox.poll-interval-ms:500}")
@Transactional
void publishPendingEvents() {
    List<OutboxEntity> events = repo.findTopNByPublishedAtIsNull(batchSize);
    for (OutboxEntity event : events) {
        String topic = routeTopic(event.getEventType());
        String key = event.getTenantId() + ":" + event.getSubjectId();

        // Kafka headers (see Section 4.3)
        Headers headers = buildKafkaHeaders(event);

        kafkaTemplate.send(topic, key, event.getPayloadJson(), headers);
        event.setPublishedAt(OffsetDateTime.now());
        repo.save(event);
    }
}
```

### 2.5 Emit-Mode Rules

| Rule | Description | Enforcement |
|---|---|---|
| EM-1 | `CREATE` events carry full initial state in `data.state` | Schema validation |
| EM-2 | `UPDATE` events carry only changed fields in `data.changed_fields` | DeltaTracker validation |
| EM-3 | `DELETE` events carry entity ID and deletion reason in `data` | Schema validation |
| EM-4 | `MERGE` events carry old→new ID mapping in `data` | Schema validation |
| EM-5 | `REVOKE` events carry scope and effective timing in `data` | Schema validation |
| EM-6 | Full-state-in-every-event is PROHIBITED for `UPDATE` on large domains | CI schema gate |
| EM-7 | Events MUST NOT contain PII when the subject is a patient | BUTANO PII-free rule |
| EM-8 | Financial events MUST carry `consistency_class: A` evidence | Decision evidence check |

---

## 3. Topic Naming Convention

### 3.1 Five-Channel Bus Model

Impilo uses five logically separated Kafka topic namespaces ("channels"). In production, these MAY be separate Kafka clusters; in local dev they share a single KRaft instance.

| Channel | Prefix | Purpose | Delivery | Retention | Replication |
|---|---|---|---|---|---|
| **Trust** | `trust.*` | Safety-critical state changes (revocations, merges, decisions) | Exactly-once (acks=all, min.insync=2) | 30 days | 3 |
| **Kernel** | `kernel.*` | National truth updates (registry deltas, catalog changes) | At-least-once (idempotent consumers) | 14 days + archive | 3 |
| **Clinical** | `clinical.*` | Care execution events (journeys, orders, results, dispense) | At-least-once (idempotent consumers) | 7 days + archive | 3 |
| **Telemetry** | `telemetry.*` | Device/IoT data, occupancy, queue metrics | At-least-once | 30 days, compacted | 2 |
| **Analytics** | `analytics.*` | Reporting aggregates, surveillance, BI | At-least-once | 90 days, compacted | 2 |

### 3.2 Topic Naming Pattern

```
{channel}.{service_short}.{aggregate}.{action}
```

Where:
- `{channel}`: one of `trust`, `kernel`, `clinical`, `telemetry`, `analytics`
- `{service_short}`: abbreviated service name (e.g., `vito`, `pct`, `oros`, `mushex`)
- `{aggregate}`: domain aggregate (e.g., `client`, `journey`, `order`, `payment`)
- `{action}`: past-tense verb (e.g., `created`, `updated`, `merged`, `placed`)

### 3.3 Complete Topic Catalog

#### Trust Channel (trust.*)

| Topic | Producer | Content | Consumers |
|---|---|---|---|
| `trust.revocation.consent` | tshepo-consent-service | Consent revocations | All pods, all data-accessing services |
| `trust.revocation.privilege` | varapi-service | Practitioner privilege revocations | All pods, all clinical services |
| `trust.revocation.identity` | vito-service, ubomi-service | Identity corrections (merge, death) | All pods, all CRID/CPID referencing services |
| `trust.federation.merge` | vito-service | Client merge events with ID mapping | All pods |
| `trust.federation.pod_registered` | federation-control | New pod registration | All existing pods |
| `trust.decision_evidence` | tshepo-authz-service | Policy decision audit trail | tshepo-audit-service, compliance dashboards |

#### Kernel Channel (kernel.*)

| Topic | Producer | Content | Consumers |
|---|---|---|---|
| `kernel.vito.client.registered` | vito-service | New client registration (full state) | All pods, PCT, MUSHEX |
| `kernel.vito.client.updated` | vito-service | Client delta update | All pods |
| `kernel.vito.client.merged` | vito-service | Client merge notification | All pods, BUTANO |
| `kernel.varapi.provider.registered` | varapi-service | New provider registration | All pods, OROS, PCT |
| `kernel.varapi.provider.updated` | varapi-service | Provider delta update | All pods |
| `kernel.varapi.provider.revoked` | varapi-service | Provider privilege/license revocation | All pods (mirror on trust channel) |
| `kernel.tuso.facility.created` | tuso-service | New facility | All pods, scheduling, referral |
| `kernel.tuso.facility.updated` | tuso-service | Facility delta update | All pods |
| `kernel.tuso.facility.deactivated` | tuso-service | Facility deactivation | All pods |
| `kernel.msika.catalog.published` | msika-service | Catalog version published | COSTA, pharmacy, MSIKA Flow |
| `kernel.msika.catalog.updated` | msika-service | Catalog item delta | COSTA, pharmacy |
| `kernel.msika.catalog.deprecated` | msika-service | Catalog item deprecated | COSTA, pharmacy |
| `kernel.zibo.artifact.published` | zibo-service | Terminology artifact published | All services with validation |
| `kernel.zibo.artifact.deprecated` | zibo-service | Terminology artifact deprecated | All services with validation |
| `kernel.zibo.artifact.retired` | zibo-service | Terminology artifact retired | All services with validation |
| `kernel.mushex.payment.created` | mushex-service | Payment intent created | COSTA, MSIKA Flow |
| `kernel.mushex.payment.authorized` | mushex-service | Payment authorized | COSTA, PCT (payment gate) |
| `kernel.mushex.payment.completed` | mushex-service | Payment completed | COSTA, PCT, pharmacy |
| `kernel.mushex.payment.failed` | mushex-service | Payment failed | COSTA, MSIKA Flow |
| `kernel.mushex.claim.submitted` | mushex-service | Claim submitted | COSTA |
| `kernel.mushex.claim.adjudicated` | mushex-service | Claim adjudicated | COSTA |
| `kernel.ubomi.birth.registered` | ubomi-service | Birth registration | VITO (newborn ID issuance) |
| `kernel.ubomi.death.registered` | ubomi-service | Death registration | VITO (deceased flag), trust channel |
| `kernel.costa.bill.finalized` | costing-engine-service | Bill finalized | MUSHEX (payment intent) |
| `kernel.costa.invoice.issued` | costing-engine-service | Invoice issued | MUSHEX |

#### Clinical Channel (clinical.*)

| Topic | Producer | Content | Consumers |
|---|---|---|---|
| `clinical.pct.journey.created` | pct-service | Journey started | BUTANO, analytics |
| `clinical.pct.journey.updated` | pct-service | Journey state change | BUTANO, analytics |
| `clinical.pct.journey.completed` | pct-service | Journey completed (discharged/etc.) | BUTANO, analytics |
| `clinical.pct.encounter.started` | pct-service | Encounter started | BUTANO, OROS |
| `clinical.pct.encounter.completed` | pct-service | Encounter completed | BUTANO, COSTA |
| `clinical.pct.triage.completed` | pct-service | Triage recorded | BUTANO |
| `clinical.pct.admission.created` | pct-service | Patient admitted | inpatient, BUTANO |
| `clinical.pct.admission.discharged` | pct-service | Patient discharged | inpatient, BUTANO, analytics |
| `clinical.pct.death.recorded` | pct-service | Death recorded | UBOMI, VITO, trust channel |
| `clinical.oros.order.placed` | oros-service | Order placed | pharmacy, PACS, BUTANO |
| `clinical.oros.order.accepted` | oros-service | Order accepted | PCT, BUTANO |
| `clinical.oros.order.completed` | oros-service | Order completed | PCT, COSTA, BUTANO |
| `clinical.oros.order.cancelled` | oros-service | Order cancelled | pharmacy, PCT |
| `clinical.oros.result.available` | oros-service | Result available | PCT, BUTANO |
| `clinical.oros.result.reviewed` | oros-service | Result reviewed by clinician | BUTANO, analytics |
| `clinical.oros.result.released` | oros-service | Result released to patient | BUTANO, analytics |
| `clinical.oros.workstep.started` | oros-service | Workstep execution started | analytics |
| `clinical.oros.workstep.completed` | oros-service | Workstep execution completed | analytics |
| `clinical.butano.resource.created` | butano-service | FHIR resource created (reference-only) | analytics |
| `clinical.butano.resource.updated` | butano-service | FHIR resource updated (reference-only) | analytics |
| `clinical.pharmacy.dispense.accepted` | pharmacy-service | Dispense order accepted | PCT, OROS |
| `clinical.pharmacy.dispense.dispensed` | pharmacy-service | Medication dispensed | PCT, COSTA, BUTANO |
| `clinical.pharmacy.dispense.reversed` | pharmacy-service | Dispense reversal | COSTA |
| `clinical.msika_flow.order.created` | msika-flow-service | Marketplace order created | MUSHEX |
| `clinical.msika_flow.order.completed` | msika-flow-service | Marketplace order fulfilled | analytics |
| `clinical.inpatient.admission.created` | inpatient-service | Inpatient admission | PCT, BUTANO |
| `clinical.inpatient.transfer.completed` | inpatient-service | Ward transfer | PCT, BUTANO |
| `clinical.inpatient.discharge.completed` | inpatient-service | Inpatient discharge | PCT, BUTANO |
| `clinical.scheduling.appointment.created` | scheduling-service | Appointment booked | PCT |
| `clinical.scheduling.appointment.completed` | scheduling-service | Appointment completed | PCT, analytics |
| `clinical.referral.created` | referral-service | Referral initiated | target facility |
| `clinical.referral.accepted` | referral-service | Referral accepted | referring facility |
| `clinical.referral.completed` | referral-service | Referral completed | analytics |
| `clinical.fhir.bundle.processed` | fhir-gateway-service | FHIR Bundle processed | analytics |

#### Telemetry Channel (telemetry.*)

| Topic | Producer | Content | Consumers |
|---|---|---|---|
| `telemetry.tuso.occupancy.snapshot` | tuso-service | Facility occupancy snapshot | analytics, Control Tower |
| `telemetry.tuso.device.heartbeat` | tuso-service | Device/equipment heartbeat | analytics |
| `telemetry.pct.queue.metrics` | pct-service | Queue depth, wait times | analytics, Control Tower |
| `telemetry.inpatient.bed.status` | inpatient-service | Bed availability status | scheduling, Control Tower |
| `telemetry.pharmacy.stock.level` | pharmacy-service | Stock level snapshot | analytics, supply-planning |
| `telemetry.inventory.stock.level` | inventory-service | Aggregate stock levels | analytics, supply-planning |

#### Analytics Channel (analytics.*)

| Topic | Producer | Content | Consumers |
|---|---|---|---|
| `analytics.reporting.aggregate` | analytics-pipeline-service | Aggregated reporting data | BI dashboards |
| `analytics.reporting.crvs` | ubomi-service | Birth/death aggregate reports | national statistics |
| `analytics.surveillance.event` | surveillance-service | Detected surveillance case | DHIS2 adapter, dashboards |
| `analytics.surveillance.alert` | surveillance-service | Threshold breach alert | notification-service |
| `analytics.governance.export.requested` | data-governance-service | Data export request | audit trail |
| `analytics.governance.export.delivered` | data-governance-service | Data export delivered | audit trail |
| `analytics.fraud.flagged` | mushex-service | Fraud flag raised | compliance dashboards |

#### Integration Channel (integration.*)

| Topic | Producer | Content | Consumers |
|---|---|---|---|
| `integration.hub.dispatch.requested` | integration-hub | Dispatch request | target adapters |
| `integration.hub.route.upserted` | integration-hub | Route definition change | integration hub internals |
| `integration.notify.send.requested` | notification-service | Notification request | delivery engine |
| `integration.rules.decision.recorded` | rules-service | Rule evaluation result | audit, analytics |
| `integration.sync.pack.generated` | offline-sync-service | Offline data pack ready | mobile devices |
| `integration.sync.upload.reconciled` | offline-sync-service | Offline upload reconciled | source services |
| `integration.jobs.executed` | jobs-service | Job executed | analytics |
| `integration.jobs.failed` | jobs-service | Job failed | ops alerting |
| `integration.portal.client.registered` | developer-portal-service | API client registered | audit |
| `integration.portal.key.issued` | developer-portal-service | API key issued | audit |
| `integration.pacs.study.received` | pacs-adapter-service | DICOM study received | OROS, BUTANO |
| `integration.pacs.study.correlated` | pacs-adapter-service | Study correlated to order | OROS |

---

## 4. Kafka Configuration

### 4.1 Producer Configuration (All Services)

```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
```

### 4.2 Consumer Configuration (All Services)

```yaml
spring:
  kafka:
    consumer:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        isolation.level: read_committed
```

### 4.3 Kafka Header Contract

All events published to Kafka MUST include these headers:

| Header | Type | Required | Source |
|---|---|---|---|
| `X-Event-Id` | String (UUID) | Yes | `event_id` from envelope |
| `X-Event-Type` | String | Yes | `event_type` from envelope |
| `X-Schema-Version` | String | Yes | `schema_version` from envelope |
| `X-Correlation-Id` | String (UUID) | Yes | `correlation_id` from envelope |
| `X-Causation-Id` | String (UUID) | If present | `causation_id` from envelope |
| `X-Producer` | String | Yes | `producer` from envelope |
| `X-Tenant-Id` | String (UUID) | Yes | `tenant_id` from envelope |
| `X-Pod-Id` | String | Yes | `pod_id` from envelope |
| `X-Idempotency-Key` | String | Yes | `idempotency_key` from envelope |

### 4.4 Kafka Partition Key

Format: `{tenant_id}:{subject_id}`

This ensures all events for the same entity land on the same partition, guaranteeing ordering per entity.

| Domain | Key Format | Example |
|---|---|---|
| Identity events | `{tenant_id}:{crid}` | `moh-zw:crid-12345` |
| Clinical events | `{tenant_id}:{cpid}` | `moh-zw:cpid-abcde` |
| Order events | `{tenant_id}:{order_id}` | `moh-zw:01HXYZ123` |
| Financial events | `{tenant_id}:{intent_id}` | `moh-zw:pi-67890` |
| Facility events | `{tenant_id}:{facility_id}` | `moh-zw:fac-001` |
| Trust events | `{pod_id}:{subject_id}` | `national-spine:consent-67890` |

---

## 5. Delta Event Format

### 5.1 CREATE Events

For entity creation, `data` contains the full initial state:

```json
{
  "change_type": "CREATE",
  "entity_version": 1,
  "state": { /* full entity state */ }
}
```

### 5.2 UPDATE Events

For entity updates, `data` contains only changed fields:

```json
{
  "change_type": "UPDATE",
  "entity_version": 7,
  "changed_fields": {
    "phone": { "old": "+263771234567", "new": "+263779876543" },
    "address_district": { "old": "Harare South", "new": "Chitungwiza" }
  }
}
```

### 5.3 DELETE Events

```json
{
  "change_type": "DELETE",
  "entity_version": 8,
  "state": {
    "entity_id": "crid-12345",
    "reason": "DUPLICATE_MERGE",
    "merged_into": "crid-67890"
  }
}
```

### 5.4 MERGE Events

```json
{
  "change_type": "MERGE",
  "surviving_crid": "crid-12345",
  "retired_crid": "crid-99999",
  "cpid_mapping": { "surviving_cpid": "cpid-abcde", "retired_cpid": "cpid-xyxyz" },
  "merge_reason": "DUPLICATE_DETECTED",
  "reconciliation_deadline": "2026-02-08T23:00:00.000Z",
  "affected_pods": ["national-spine", "pod-military-01"]
}
```

### 5.5 REVOKE Events

```json
{
  "change_type": "REVOKE",
  "cpid": "cpid-abcde",
  "consent_scope": "TREATMENT",
  "revoked_by": "patient",
  "effective_immediately": true,
  "propagation_required": true,
  "target_pods": ["national-spine", "pod-military-01"]
}
```

---

## 6. Snapshot Endpoint Contract

### 6.1 Request

```
GET /internal/v1/{resource}/snapshot
  Headers: X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID
  Query params:
    cursor  — opaque pagination token (from previous response nextCursor)
    limit   — page size (default 100, max 1000)
    since   — ISO-8601 timestamp for incremental snapshots
```

### 6.2 Response

```json
{
  "snapshot_timestamp": "2026-02-08T10:00:00.000Z",
  "schema_version": "1.0",
  "producer": "vito-service",
  "tenant_id": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "pod_id": "national-spine",
  "resource_type": "Client",
  "total_count": 15234567,
  "items": [
    {
      "subject_id": "crid-00001",
      "entity_version": 3,
      "last_modified_at": "2026-02-07T14:30:00.000Z",
      "state": { /* full entity state */ }
    }
  ],
  "next_cursor": "eyJpZCI6ImNyaWQtMDAxMDAifQ==",
  "has_more": true
}
```

### 6.3 Snapshot Endpoints by Service

| Service | Endpoint | Resource Type |
|---|---|---|
| vito-service | `/internal/v1/clients/snapshot` | Client (CRID/CPID mappings) |
| varapi-service | `/internal/v1/providers/snapshot` | Practitioner |
| tuso-service | `/internal/v1/facilities/snapshot` | Facility |
| msika-service | `/internal/v1/catalog/snapshot` | CatalogItem |
| zibo-service | `/internal/v1/artifacts/snapshot` | TerminologyArtifact |
| mushex-service | `/internal/v1/ledger/snapshot` | LedgerAccount |

---

## 7. Consumer Requirements (v1.1 Law 5)

Every service that consumes events MUST implement:

| # | Requirement | Description |
|---|---|---|
| CR-1 | Idempotent processing | Deduplicate by `idempotency_key`; processing same event twice produces no additional side effects |
| CR-2 | Ordering strategy | Process events per partition in order; handle out-of-order by checking `entity_version` |
| CR-3 | Replay handling | Support replaying events from any offset without data corruption |
| CR-4 | Poison message strategy | Events that fail processing after max retries are sent to dead-letter topic (`{channel}.dlq`) |
| CR-5 | Backfill strategy | Can bootstrap from snapshot endpoint when consumer starts fresh or falls behind |
| CR-6 | Staleness reporting | Track consumer lag; report staleness via `x-projection-staleness-ms` header on responses |

---

## 8. Schema Registry Integration

### 8.1 Registry Configuration

- **Product**: Apicurio Registry (open source)
- **Port**: 8180
- **Compatibility mode**: BACKWARD (default) — new schema can read old data
- **CI gate**: PR cannot merge if schema change breaks backward compatibility

### 8.2 Artifact Naming

```
{channel}.{service}.{aggregate}.{action}-value
```

Example: `kernel.vito.client.updated-value`

### 8.3 Schema Evolution Rules

| Action | Allowed? | Condition |
|---|---|---|
| Add optional field | Yes | Backward compatible |
| Remove optional field | Yes | With 1-release deprecation notice |
| Change field type | No | Breaking — requires new schema major version |
| Remove required field | No | Breaking |
| Add required field | No | Add as optional with default value |
| Major version bump | Yes | Support old version for 2 release cycles, publish migration guide |
