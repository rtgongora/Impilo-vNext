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
| [`data-pipeline-reporting-aggregate.asyncapi.yaml`](./data-pipeline-reporting-aggregate.asyncapi.yaml) | `analytics.reporting.aggregate` producer/consumer contract between data-pipeline-service and reporting-service |

## Phase E status

**Baseline complete** (agent-led roadmap, **2026-04-12**): catalog covers core in-repo listener literals and major outbox publishers (PCT/OROS/Pharmacy/COSTA/MUSHEX/BUTANO/campaigns/document/msika-flow) plus data-ingestion pattern ingestion.

This directory is **not** a guarantee that every active listener literal in repository code is represented one-to-one in AsyncAPI files. For full reconciliation and drift notes, use:

- `docs/architecture/kafka-event-catalog.md`
- `docs/plan/EVENTING_AND_TOPICS.md`

Further payload JSON Schemas and CI drift checks remain Phase F work.
