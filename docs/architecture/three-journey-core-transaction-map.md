# Three-Journey Core Transaction Map

This map aligns transaction states and layers across three synchronized journeys.

## Person Journey

- Focus: access, understanding, continuity.
- Primary surfaces: One UI Shell person-facing routes, citizen app, omnichannel touchpoints.

## Provider Journey

- Focus: safe care execution, reduced cognitive burden, completion readiness.
- Primary surfaces: provider workspace, queue/worklist, encounter flows.

## Platform Journey

- Focus: trust enforcement, orchestration, financial pathing, record continuity, analytics, audit.
- Primary surfaces: Experience BFF composition, workflow services, outbox events, operations dashboards.

## Synchronization

- Shared key: `transaction_id` and `correlation_id`.
- State transitions are canonical in `contracts/core-transaction.ts`.
- Events are canonical in `contracts/asyncapi/core-transaction-events.asyncapi.yaml`.
- Composed API view is canonical in `contracts/openapi/core-transaction-openapi.yaml`.
