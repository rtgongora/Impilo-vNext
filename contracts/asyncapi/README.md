# AsyncAPI (Kafka) — contract workspace

This directory is reserved for **AsyncAPI 2.x / 3.x** documents describing Kafka channels used by Impilo services.

## Conventions (draft)

- One file per **bounded context** or per **high-traffic integration rail** (e.g. `pct-clinical.asyncapi.yaml`, `finance-mushex.asyncapi.yaml`).
- Channel names should match **actual Kafka topic strings** in Java (`@KafkaListener`, outbox `routeTopic` / `resolveTopic`).
- Message `payload` schemas may reference **JSON Schema** fragments under `contracts/schemas/` when those exist; until then, document minimal JSON examples in AsyncAPI `examples`.

## Inventory

The authoritative **human-maintained inventory** of topics and known producer/consumer edges lives in:

[`docs/architecture/kafka-event-catalog.md`](../../docs/architecture/kafka-event-catalog.md)

## Published specs

| Document | Topics |
|----------|--------|
| [`pct-clinical-encounter.asyncapi.yaml`](./pct-clinical-encounter.asyncapi.yaml) | `pct.encounter.started`, `pct.encounter.completed`, `impilo.surv.case.opened.v1` |
| [`finance-oros-pharmacy.asyncapi.yaml`](./finance-oros-pharmacy.asyncapi.yaml) | `mushex.payment.status.changed`, `mushex.refund.status.changed`, `costa.bill.finalized`, `oros.order.placed`, `pharmacy.dispense.complete`, `inventory.ledger.event.created` |

## Phase E status

Baseline catalog landed **2026-04-12**; **slice 2** added PCT/surveillance AsyncAPI; **slice 3** added finance/OROS/pharmacy catalog sections, `finance-oros-pharmacy.asyncapi.yaml`, and reconciled **`pharmacy.dispense.complete`** + **`mushex.payment.status.changed`** consumers (see [`kafka-event-catalog.md`](../../docs/architecture/kafka-event-catalog.md) §3).
