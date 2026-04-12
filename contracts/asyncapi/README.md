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

## Phase E status

Baseline catalog landed **2026-04-12**; **slice 2** added the AsyncAPI file above after reconciling BFF/Costa/PCT/surveillance topic names (see [`kafka-event-catalog.md`](../../docs/architecture/kafka-event-catalog.md) §3).
