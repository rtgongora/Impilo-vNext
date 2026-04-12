# AsyncAPI (Kafka) — contract workspace

This directory is reserved for **AsyncAPI 2.x / 3.x** documents describing Kafka channels used by Impilo services.

## Conventions (draft)

- One file per **bounded context** or per **high-traffic integration rail** (e.g. `pct-clinical.asyncapi.yaml`, `finance-mushex.asyncapi.yaml`).
- Channel names should match **actual Kafka topic strings** in Java (`@KafkaListener`, outbox `routeTopic` / `resolveTopic`).
- Message `payload` schemas may reference **JSON Schema** fragments under `contracts/schemas/` when those exist; until then, document minimal JSON examples in AsyncAPI `examples`.

## Inventory

The authoritative **human-maintained inventory** of topics and known producer/consumer edges lives in:

[`docs/architecture/kafka-event-catalog.md`](../../docs/architecture/kafka-event-catalog.md)

## Phase E status

Baseline catalog + this layout landed **2026-04-12**. Adding AsyncAPI per channel is incremental work after topic names are stabilised (see catalog §3 for naming gaps to resolve first).
