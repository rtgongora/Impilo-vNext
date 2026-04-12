# Kafka event catalog (Phase E — complete baseline)

Living inventory of **Kafka topic names** and **how they are produced/consumed** in this repository. It complements REST OpenAPI under `contracts/openapi/` and closes the agent-led roadmap **Phase E** (event catalog + AsyncAPI anchors) for the **Java services in this repo** as of the inventory date in §3.

**Scope:** transactional outboxes (`routeTopic` / `resolveTopic`), explicit `@KafkaListener` classes, and config-driven topics. Services **without** Kafka code paths (e.g. many REST-only adapters) are omitted here but remain in the **Phase F** completeness playbook. **Schema evolution:** payloads are JSON strings unless noted; prefer `$ref` JSON Schemas in a later wave when envelopes stabilise.

---

## 1. Topic naming families

| Family | Example | Typical use |
|--------|---------|-------------|
| **Short domain topics** | `pct.encounter.started`, `oros.order.status_changed` | Outbox publishers map aggregate/event types to stable Kafka topic names. |
| **Versioned `impilo.*` topics** | `impilo.control.revocation.v1`, `impilo.surv.signal.created.v1` | Cross-cutting control plane or newer versioned streams. |
| **Integration / external** | `elmis.stock.position.snapshot`, `lims.order.status_changed` | Bridge topics from ELMIS/LIMS-style adapters consumed by sovereign services. |

Payloads are overwhelmingly **JSON strings** (`KafkaTemplate<String, String>`) unless a service configures Avro/Protobuf (not catalogued here).

---

## 2. Experience BFF upstream listeners (fan-in hub)

Source: [`UpstreamEventConsumer.java`](../../services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/events/UpstreamEventConsumer.java).  
Group id: **`experience-bff`**. Intended for cache/local projection sync; handlers today mostly **log** parsed fields.

| Kafka topic | Notable JSON paths (from handler) |
|-------------|-----------------------------------|
| `pct.encounter.started` | `encounterRef`, `journeyId`, `patientCpid` (see [`EncounterService`](../../services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/core/EncounterService.java) for full payload incl. `eventId`, `tenantId`) |
| `pct.encounter.completed` | `encounterRef`, `journeyId` |
| `oros.order.status_changed` | `orderId`, `status` |
| `oros.result.available` | `orderId`, `resultId` |
| `pharmacy.dispense.complete` | `prescriptionId` (canonical topic from pharmacy-service `OutboxPublisher`) |
| `costa.bill.finalized` | `billId`, `totalAmount` |
| `mushex.payment.status.changed` | `paymentId`, `status` (mushex-service `OutboxPublisher`) |
| `mushex.refund.status.changed` | `refundId`, `status` |
| `tuso.workspace.updated` | `workspaceId` |
| `tuso.facility.profile.updated` | `facilityId` |
| `pacs.study.available` | `studyInstanceUid`, `modality`, `patientId`, `orderId` |
| `impilo.surv.case.opened.v1` | `id`, `caseType`, `title`, `severity`, `status`, `tenantId` (from surveillance [`IngestService.buildCasePayload`](../../services/surveillance-service/src/main/java/zw/gov/mohcc/impilo/surv/core/IngestService.java)) |

---

## 3. Producer ↔ consumer reconciliation log

| Area | Resolution (2026-04-12) |
|------|-------------------------|
| PCT encounter lifecycle | **Costa** + **Experience BFF** now subscribe to `pct.encounter.started` / `pct.encounter.completed` (matching PCT `OutboxPublisher`). Encounter outbox payloads include **`eventId`** and **`tenantId`** for Costa idempotency and tenant scoping. |
| Surveillance case opened | **Experience BFF** now subscribes to **`impilo.surv.case.opened.v1`** (matching `SurvOutboxPublisher` for `CASE_OPENED`). Removed unused BFF subscription to `surv.outbreak.declared` (no producer in surveillance-service yet). |
| Pharmacy dispense | Pharmacy-service publishes **`pharmacy.dispense.complete`** (`DISPENSE_COMPLETED`). **OROS** `OrosEventConsumer` and **Experience BFF** previously listened on the non-existent typo topic `pharmacy.dispense.completed`; **slice 3** aligns them to **`pharmacy.dispense.complete`** (Costa was already correct). |
| Mushex payment status | Mushex publishes **`mushex.payment.status.changed`** (`STATUS_CHANGED`). **Costa**, **BFF**, **Pharmacy**, **PCT**, and **Msika Flow** previously listened on **`mushex.payment.status_changed`** (underscore); **slice 3** aligns consumers to the producer topic. |

**Ongoing hygiene:** add a row here whenever a new producer/consumer pair ships; optional CI drift checks belong in **Phase F** (per-service playbook).

---

## 4. Governance and federation (versioned `impilo.*`)

| Topic | Consumers (examples) |
|-------|----------------------|
| `impilo.control.revocation.v1` | PCT `RevocationControlChannelConsumer`; VITO `RevocationControlChannelConsumer` |
| `impilo.federation.pod.revoked.v1` | TSHEPO `FederationPodRevocationConsumer`; VITO `FederationPodRevocationConsumer` |
| `impilo.federation.pod.reinstated.v1` | TSHEPO `FederationPodRevocationConsumer` |

---

## 5. All explicit `@KafkaListener` topics (`services/`, inventory snapshot)

| Topic (literal or default) | Consumer service | Class |
|----------------------------|------------------|-------|
| `pct.encounter.started` | costing-engine | `CostaEventConsumer` |
| `pct.encounter.completed` | costing-engine | `CostaEventConsumer` |
| `oros.order.placed` | costing-engine, pharmacy | `CostaEventConsumer`, `OrosConsumer` |
| `pharmacy.dispense.complete` | costing-engine, oros, experience-bff | `CostaEventConsumer`, `OrosEventConsumer`, `UpstreamEventConsumer` |
| `inventory.ledger.event.created` | costing-engine | `CostaEventConsumer` |
| `mushex.payment.status.changed` | costing-engine, experience-bff, pharmacy, pct, msika-flow | `CostaEventConsumer`, `UpstreamEventConsumer`, `MushexConsumer`, `PctEventConsumer`, `PaymentEventConsumer` |
| `mushex.refund.status.changed` | costing-engine, experience-bff | `CostaEventConsumer`, `UpstreamEventConsumer` |
| `oros.order.status_changed` | experience-bff, pct | `UpstreamEventConsumer`, `PctEventConsumer` |
| `oros.result.available` | experience-bff, pct | `UpstreamEventConsumer`, `PctEventConsumer` |
| `costa.bill.finalized` | experience-bff, mushex | `UpstreamEventConsumer`, `mushex.kafka.CostaEventConsumer` |
| `costa.invoice.issued` | mushex | `mushex.kafka.CostaEventConsumer` |
| `costa.refund.issued` | mushex | `mushex.kafka.CostaEventConsumer` |
| `tuso.workspace.updated` | experience-bff, pct, zibo | `UpstreamEventConsumer`, `PctEventConsumer`, `ZiboEventConsumer` |
| `tuso.facility.profile.updated` | experience-bff, zibo | `UpstreamEventConsumer`, `ZiboEventConsumer` |
| `pacs.study.available` | experience-bff, oros | `UpstreamEventConsumer`, `OrosEventConsumer` |
| `impilo.surv.case.opened.v1` | experience-bff | `UpstreamEventConsumer` |
| `lims.order.status_changed`, `lims.result.available` | oros | `OrosEventConsumer` |
| `elmis.stock.position.snapshot` | inventory | `ElmisConsumer` |
| `pharmacy.stock.movement.requested` | inventory | `PharmacyConsumer` |
| `inventory.reservation.status_changed`, `pharmacy.fulfillment.status_changed` | msika-flow | `InventoryEventConsumer` |
| `msika.flow.order.paid`, `msika.flow.refund.requested` | mushex | `MsikaFlowEventConsumer` |
| `pharmacy.mushex.charge`, `pharmacy.mushex.credit` | mushex | `PharmacyEventConsumer` |
| `impilo.control.revocation.v1` | pct, vito | `RevocationControlChannelConsumer` (each) |
| `impilo.federation.pod.revoked.v1` | tshepo, vito | `FederationPodRevocationConsumer` |
| `impilo.federation.pod.reinstated.v1` | tshepo | `FederationPodRevocationConsumer` |
| `tshepo.audit.events` | tshepo-audit | `AuditKafkaConsumer` |
| `vito.print` | card-print-agent | `PrintJobListener` |
| `${impilo.telemetry.kafka.topic:impilo.iot.telemetry.device.raw}` | iot-ingestion | `TelemetryKafkaConsumer` |
| `${ingestion.kafka.topic-pattern:impilo\..*}` | data-ingestion | `BronzeEventKafkaConsumer` (topic **pattern**) |

---

## 6. Outbox publishers (starting points for full matrices)

Each file maps **outbox `eventType`** (or aggregate) → **Kafka topic**. Tests often assert the routing table.

| Service (module) | Publisher type |
|------------------|----------------|
| PCT | [`OutboxPublisher.routeTopic`](../../services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/events/OutboxPublisher.java) |
| OROS | [`OutboxPublisher.routeTopic`](../../services/oros-service/src/main/java/zw/gov/mohcc/impilo/oros/events/OutboxPublisher.java) |
| Pharmacy | [`OutboxPublisher.routeTopic`](../../services/pharmacy-service/src/main/java/zw/gov/mohcc/impilo/pharmacy/events/OutboxPublisher.java) |
| Mushex | [`OutboxPublisher.routeTopic`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/kafka/OutboxPublisher.java) |
| Costa | [`OutboxPublisher.routeTopic`](../../services/costing-engine-service/src/main/java/zw/gov/mohcc/impilo/costa/kafka/OutboxPublisher.java) |
| BUTANO | [`OutboxPublisher`](../../services/butano-service/src/main/java/zw/gov/mohcc/impilo/butano/events/OutboxPublisher.java) |
| Surveillance | [`SurvOutboxPublisher.resolveTopic`](../../services/surveillance-service/src/main/java/zw/gov/mohcc/impilo/surv/events/SurvOutboxPublisher.java) |
| Document store | [`OutboxPublisher`](../../services/document-service/src/main/java/zw/gov/mohcc/impilo/docstore/events/OutboxPublisher.java) |
| Campaigns | [`CampaignsOutboxPublisher`](../../services/campaigns-service/src/main/java/zw/gov/mohcc/impilo/campaigns/events/CampaignsOutboxPublisher.java) |
| Msika Flow | [`OutboxPublisher.routeTopic`](../../services/msika-flow-service/src/main/java/zw/gov/mohcc/impilo/msikaflow/events/OutboxPublisher.java) |

---

## 7. Finance rail — Mushex production & Costa consumption

**Mushex** publishes from [`mushex/kafka/OutboxPublisher.routeTopic`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/kafka/OutboxPublisher.java):

| Outbox `eventType` (examples) | Kafka topic |
|-------------------------------|-------------|
| `INTENT_CREATED` | `mushex.payment.intent.created` |
| `STATUS_CHANGED` | `mushex.payment.status.changed` |
| `REFUND_*` | `mushex.refund.status.changed` |
| `CLAIM_SUBMITTED` / `CLAIM_ADJUDICATED` | `mushex.claim.submitted` / `mushex.claim.adjudicated` |
| `SETTLEMENT_BATCH_RELEASED`, `FRAUD_FLAGGED`, `REMITTANCE_*` | `mushex.settlement.batch.released`, `mushex.fraud.flagged`, `mushex.remittance.issued` / `claimed` |
| default | `mushex.events` |

**Costa** (`CostaEventConsumer`, group `costa-costing-engine`) consumes Mushex topics for payment/refund integration: **`mushex.payment.status.changed`**, **`mushex.refund.status.changed`** (field names: `paymentIntentId`, `status`, `paidAmount`, `refundId`, `intentId`, `billId`, `amount`, …).

**Costa** emits billing lifecycle topics from [`costa/kafka/OutboxPublisher.routeTopic`](../../services/costing-engine-service/src/main/java/zw/gov/mohcc/impilo/costa/kafka/OutboxPublisher.java) — e.g. `costa.bill.finalized`, `costa.invoice.issued`, `costa.payment.status_changed`, `costa.refund.issued`, `costa.claim.pack.created`, default `costa.events`. Downstream listeners include **Experience BFF** on **`costa.bill.finalized`**.

---

## 8. OROS orders/results & pharmacy dispense (cross-rail)

**OROS** emits order/result topics from its outbox (see §6). Notable consumers:

| Topic | Role |
|-------|------|
| `oros.order.placed` | **Costa** posts bill lines; **Pharmacy** `OrosConsumer` |
| `oros.order.status_changed` | PCT, Experience BFF, … |
| `oros.result.available` | PCT, Experience BFF, OROS internal pipeline |

**Pharmacy** dispense completion is published as **`pharmacy.dispense.complete`** (not `…completed`). Consumers: **Costa** (bill lines), **OROS** (result synthesis), **Experience BFF** (log / future projection).

**Inventory** posts **`inventory.ledger.event.created`** (`LEDGER_EVENT_CREATED`); **Costa** consumes it for stock-cost signals (see `CostaEventConsumer`).

---

## 9. BUTANO (Shared Health Record) outbox

Producer: [`butano/.../OutboxPublisher`](../../services/butano-service/src/main/java/zw/gov/mohcc/impilo/butano/events/OutboxPublisher.java).

| Event type | Kafka topic |
|------------|-------------|
| `RESOURCE_CREATED` | `butano.resource.created` |
| `RESOURCE_UPDATED` | `butano.resource.updated` |
| `RECONCILE_COMPLETED` | `butano.reconcile.completed` |
| default | `butano.events` |

Downstream analytics, NDR, and connector adapters typically subscribe via **`data-ingestion-service`** topic patterns (`impilo.*`) rather than per-topic listeners in-repo.

---

## 10. Campaigns (versioned `impilo.campaigns.*`)

Producer: [`CampaignsOutboxPublisher.resolveTopic`](../../services/campaigns-service/src/main/java/zw/gov/mohcc/impilo/campaigns/events/CampaignsOutboxPublisher.java).

| Event type | Kafka topic |
|------------|-------------|
| `CAMPAIGN_CREATED` | `impilo.campaigns.created.v1` |
| `ENROLLMENT_CREATED` | `impilo.campaigns.enrolled.v1` |
| `CAMPAIGN_DISPATCHED` | `impilo.campaigns.dispatched.v1` |
| default | `impilo.campaigns.unknown` |

---

## 11. Document store outbox

Producer: [`docstore/.../OutboxPublisher`](../../services/document-service/src/main/java/zw/gov/mohcc/impilo/docstore/events/OutboxPublisher.java).

| Aggregate | Kafka topic |
|-----------|-------------|
| `DOCUMENT` | `docstore.documents` |
| default | `docstore.events` |

---

## 12. Msika Flow (marketplace order rail)

Producer: [`msikaflow/.../OutboxPublisher.routeTopic`](../../services/msika-flow-service/src/main/java/zw/gov/mohcc/impilo/msikaflow/events/OutboxPublisher.java) — topics `msika.flow.order.*`, `msika.flow.pickup.*`, `msika.flow.vendor.*`, `msika.flow.refund.*`, default `msika.flow.events`. **Mushex** consumes `msika.flow.order.paid` and `msika.flow.refund.requested` via [`MsikaFlowEventConsumer`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/kafka/MsikaFlowEventConsumer.java).

---

## 13. Data ingestion (bronze lake)

[`BronzeEventKafkaConsumer`](../../services/data-ingestion-service/src/main/java/zw/gov/mohcc/impilo/dataingestion/kafka/BronzeEventKafkaConsumer.java) uses **`topicPattern`** `${ingestion.kafka.topic-pattern}` (default regex **`impilo\..*`**) to ingest **EventEnvelope** JSON into the bronze store — catch-all for many `impilo.*` versioned streams (campaigns, surveillance, future normalised types).

---

## 14. AsyncAPI and schema ownership

Machine-readable anchors (incrementally enrich payloads under `components/messages`):

- [`pct-clinical-encounter.asyncapi.yaml`](../../contracts/asyncapi/pct-clinical-encounter.asyncapi.yaml) — PCT encounter + surveillance case opened
- [`finance-oros-pharmacy.asyncapi.yaml`](../../contracts/asyncapi/finance-oros-pharmacy.asyncapi.yaml) — Costa / Mushex / OROS / pharmacy / inventory
- [`trust-governance.asyncapi.yaml`](../../contracts/asyncapi/trust-governance.asyncapi.yaml) — revocation + federation + tshepo audit
- [`butano-shr.asyncapi.yaml`](../../contracts/asyncapi/butano-shr.asyncapi.yaml) — BUTANO SHR resource lifecycle
- [`campaigns-outbound.asyncapi.yaml`](../../contracts/asyncapi/campaigns-outbound.asyncapi.yaml) — `impilo.campaigns.*`
- [`document-store.asyncapi.yaml`](../../contracts/asyncapi/document-store.asyncapi.yaml) — `docstore.*`
- [`data-ingestion-bronze.asyncapi.yaml`](../../contracts/asyncapi/data-ingestion-bronze.asyncapi.yaml) — topic pattern consumer

Conventions: [`contracts/asyncapi/README.md`](../../contracts/asyncapi/README.md).

---

## Related

- Roadmap: [`agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md) (Phase **E**)
- REST contracts: [`contracts/openapi/`](../../contracts/openapi/)
