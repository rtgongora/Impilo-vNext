# AsyncAPI v3 Event Topology (Canonical Taxonomy)

This folder contains the v3 catalog-level topology view of event channels and operations.

## Relationship to `contracts/asyncapi/`

- `contracts/async/impilo-events.asyncapi.yaml` is the **canonical taxonomy and target bus topology** view.
- `contracts/asyncapi/*.asyncapi.yaml` are **implementation-anchored rails** that map to current in-repo Java topic literals and bounded contexts.

When these views diverge, treat the taxonomy file as design intent and the `contracts/asyncapi/` + code-level listeners/outbox mappings as runtime reality until convergence work is completed.

Use these references together:

- `docs/architecture/kafka-event-catalog.md`
- `docs/plan/EVENTING_AND_TOPICS.md`
- `contracts/asyncapi/README.md`
