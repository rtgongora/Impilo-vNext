# AsyncAPI (Kafka) — contract workspace

This directory holds **AsyncAPI 2.x** documents for Kafka channels used by Impilo Java services.

## Conventions

- One file per **bounded context** or **integration rail**; channel names match **literal** topics in Java (`@KafkaListener`, outbox `routeTopic` / `resolveTopic`) unless explicitly documented as a **pattern consumer** (`data-ingestion-bronze.asyncapi.yaml`).
- Payloads use permissive `additionalProperties: true` until JSON Schema fragments land under `contracts/schemas/` (Phase **F** polish).

## Inventory

Authoritative topic matrix and reconciliation log: [`docs/architecture/kafka-event-catalog.md`](../../docs/architecture/kafka-event-catalog.md).

## Published specs (Phase E complete)

| Document | Topics / scope |
|----------|----------------|
| [`pct-clinical-encounter.asyncapi.yaml`](./pct-clinical-encounter.asyncapi.yaml) | `pct.encounter.started`, `pct.encounter.completed`, `impilo.surv.case.opened.v1` |
| [`finance-oros-pharmacy.asyncapi.yaml`](./finance-oros-pharmacy.asyncapi.yaml) | `mushex.payment.status.changed`, `mushex.refund.status.changed`, `costa.bill.finalized`, `oros.order.placed`, `pharmacy.dispense.complete`, `inventory.ledger.event.created` |
| [`trust-governance.asyncapi.yaml`](./trust-governance.asyncapi.yaml) | `impilo.control.revocation.v1`, `impilo.federation.pod.revoked.v1`, `impilo.federation.pod.reinstated.v1`, `tshepo.audit.events` |
| [`butano-shr.asyncapi.yaml`](./butano-shr.asyncapi.yaml) | `butano.resource.created`, `butano.resource.updated`, `butano.reconcile.completed`, `butano.events` |
| [`campaigns-outbound.asyncapi.yaml`](./campaigns-outbound.asyncapi.yaml) | `impilo.campaigns.created.v1`, `impilo.campaigns.enrolled.v1`, `impilo.campaigns.dispatched.v1`, `impilo.campaigns.unknown` |
| [`document-store.asyncapi.yaml`](./document-store.asyncapi.yaml) | `docstore.documents`, `docstore.events` |
| [`data-ingestion-bronze.asyncapi.yaml`](./data-ingestion-bronze.asyncapi.yaml) | Pattern consumer (`impilo\..*` default) — illustrative channel + envelope |

## Phase E status

**Complete** (agent-led roadmap, **2026-04-12**): catalog covers all in-repo `@KafkaListener` literals, major outbox publishers, BUTANO/campaigns/document/msika-flow rails, and data-ingestion pattern ingestion. Further **payload** JSON Schemas and CI drift checks are **Phase F** work.
