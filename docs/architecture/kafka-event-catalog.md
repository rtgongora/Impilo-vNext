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
| `pct.encounter.opened` | `encounterId`, `patientId` |
| `pct.encounter.closed` | `encounterId` |
| `oros.order.status_changed` | `orderId`, `status` |
| `oros.result.available` | `orderId`, `resultId` |
| `pharmacy.dispense.completed` | `prescriptionId` |
| `costa.bill.finalized` | `billId`, `totalAmount` |
| `mushex.payment.status_changed` | `paymentId`, `status` |
| `mushex.refund.status.changed` | `refundId`, `status` |
| `tuso.workspace.updated` | `workspaceId` |
| `tuso.facility.profile.updated` | `facilityId` |
| `pacs.study.available` | `studyInstanceUid`, `modality`, `patientId`, `orderId` |
| `surv.case.reported` | `caseId`, `disease` |
| `surv.outbreak.declared` | `outbreakId`, `disease` |

---

## 3. Known producer ↔ consumer naming gaps (code-derived)

These are **mismatches visible from static analysis** — they may be intentional bridges elsewhere or future work.

| Consumer expects | Producer (current code) | Notes |
|------------------|-------------------------|--------|
| `pct.encounter.opened` / `pct.encounter.closed` | PCT `OutboxPublisher` emits `pct.encounter.started` / `pct.encounter.completed` for `ENCOUNTER_STARTED` / `ENCOUNTER_COMPLETED` | Costa `CostaEventConsumer` also listens on `opened` / `closed`. Align naming or add dual-publish. |
| `surv.case.reported`, `surv.outbreak.declared` | Surveillance `SurvOutboxPublisher` maps to `impilo.surv.signal.created.v1`, `impilo.surv.signal.hit.v1`, `impilo.surv.case.opened.v1`, fallback `impilo.surv.unknown` | BFF surveillance listeners may never fire until topics are reconciled. |

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
| `mushex.payment.status_changed` | Pharmacy `MushexConsumer`; PCT `PctEventConsumer`; Msika Flow `PaymentEventConsumer` |
| `lims.order.status_changed`, `lims.result.available` | OROS `OrosEventConsumer` |
| `pacs.study.available` | OROS `OrosEventConsumer`; Experience BFF (above) |
| `pharmacy.dispense.completed` | OROS `OrosEventConsumer`; Experience BFF |
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
| OROS | (search `oros` + `OutboxPublisher`) |
| Pharmacy | [`OutboxPublisher.routeTopic`](../../services/pharmacy-service/src/main/java/zw/gov/mohcc/impilo/pharmacy/events/OutboxPublisher.java) |
| Mushex | [`OutboxPublisher.routeTopic`](../../services/mushex-service/src/main/java/zw/gov/mohcc/impilo/mushex/kafka/OutboxPublisher.java) |
| Costa | [`OutboxPublisher.routeTopic`](../../services/costing-engine-service/src/main/java/zw/gov/mohcc/impilo/costa/kafka/OutboxPublisher.java) |
| BUTANO | [`OutboxPublisher`](../../services/butano-service/src/main/java/zw/gov/mohcc/impilo/butano/events/OutboxPublisher.java) |
| Surveillance | [`SurvOutboxPublisher.resolveTopic`](../../services/surveillance-service/src/main/java/zw/gov/mohcc/impilo/surv/events/SurvOutboxPublisher.java) |
| Document store | [`OutboxPublisher`](../../services/document-service/src/main/java/zw/gov/mohcc/impilo/docstore/events/OutboxPublisher.java) |
| Campaigns | [`CampaignsOutboxPublisher`](../../services/campaigns-service/src/main/java/zw/gov/mohcc/impilo/campaigns/events/CampaignsOutboxPublisher.java) |
| Msika Flow | [`OutboxPublisher`](../../services/msika-flow-service/src/main/java/zw/gov/mohcc/impilo/msikaflow/events/OutboxPublisher.java) |

---

## 7. AsyncAPI and schema ownership

Machine-readable AsyncAPI specs are **not** generated for most topics yet. Placeholder layout and conventions: [`contracts/asyncapi/README.md`](../../contracts/asyncapi/README.md).

---

## Related

- Roadmap: [`agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md) (Phase **E**)
- REST contracts: [`contracts/openapi/`](../../contracts/openapi/)
