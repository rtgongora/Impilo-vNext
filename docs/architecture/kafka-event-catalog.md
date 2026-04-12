# Kafka event catalog (Phase E — inventory)

Living inventory of **Kafka topic names** and **how they are produced/consumed** in this repository. It complements REST OpenAPI under `contracts/openapi/` and supports the agent-led roadmap **Phase E** (event surfaces).

**Not exhaustive:** many services use transactional outbox + `routeTopic` / `resolveTopic` helpers; this document prioritises **cross-service fan-in** (especially Experience BFF) and **governance/control** topics. Extend by copying patterns from each service’s `*OutboxPublisher.java` and `@KafkaListener` usages.

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

**Residual (inventory):** other topic pairs across the fleet may still drift — extend this table as `@KafkaListener` / outbox routing is audited.

---

## 4. Governance and federation (versioned `impilo.*`)

| Topic | Consumers (examples) |
|-------|----------------------|
| `impilo.control.revocation.v1` | PCT `RevocationControlChannelConsumer`; VITO `RevocationControlChannelConsumer` |
| `impilo.federation.pod.revoked.v1` | TSHEPO `FederationPodRevocationConsumer`; VITO `FederationPodRevocationConsumer` |
| `impilo.federation.pod.reinstated.v1` | TSHEPO `FederationPodRevocationConsumer` |

---

## 5. Other `@KafkaListener` anchors (sample)

Use ripgrep to extend: `@KafkaListener` under `services/`.

| Topic | Service / class |
|-------|-----------------|
| `oros.order.placed` | Pharmacy `OrosConsumer` |
| `mushex.payment.status.changed` | Pharmacy `MushexConsumer`; PCT `PctEventConsumer`; Msika Flow `PaymentEventConsumer`; Costa `CostaEventConsumer` |
| `lims.order.status_changed`, `lims.result.available` | OROS `OrosEventConsumer` |
| `pacs.study.available` | OROS `OrosEventConsumer`; Experience BFF (above) |
| `pharmacy.dispense.complete` | OROS `OrosEventConsumer`; Experience BFF; Costa `CostaEventConsumer` |
| `elmis.stock.position.snapshot` | Inventory `ElmisConsumer` |
| `pharmacy.stock.movement.requested` | Inventory `PharmacyConsumer` |
| `inventory.reservation.status_changed`, `pharmacy.fulfillment.status_changed` | Msika Flow `InventoryEventConsumer` |
| `msika.flow.order.paid`, `msika.flow.refund.requested` | Mushex `MsikaFlowEventConsumer` |
| `impilo.iot.telemetry.device.raw` (config default) | IoT ingestion `TelemetryKafkaConsumer` |

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
| Msika Flow | [`OutboxPublisher`](../../services/msika-flow-service/src/main/java/zw/gov/mohcc/impilo/msikaflow/events/OutboxPublisher.java) |

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

## 9. AsyncAPI and schema ownership

Starter specs:

- [`pct-clinical-encounter.asyncapi.yaml`](../../contracts/asyncapi/pct-clinical-encounter.asyncapi.yaml) — PCT encounter + surveillance case opened
- [`finance-oros-pharmacy.asyncapi.yaml`](../../contracts/asyncapi/finance-oros-pharmacy.asyncapi.yaml) — Costa/Mushex/OROS/pharmacy inventory anchors

Conventions and backlog: [`contracts/asyncapi/README.md`](../../contracts/asyncapi/README.md).

---

## Related

- Roadmap: [`agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md) (Phase **E**)
- REST contracts: [`contracts/openapi/`](../../contracts/openapi/)
