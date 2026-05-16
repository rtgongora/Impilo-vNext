# Core Transaction Event Model

Canonical event names and payload structures are defined in:

- `contracts/core-transaction.ts`
- `contracts/asyncapi/core-transaction-events.asyncapi.yaml`

## Event Doctrine

If an action matters clinically, operationally, financially, legally, or analytically, it emits an event.

## Required Event Envelope Fields

- `transaction_id`, `correlation_id`
- `tenant_id`, `pod_id`
- `facility_id`, `workspace_id`
- `client_ref` or `client_alias`
- `provider_ref` or `actor_id`
- `actor_type`, `purpose_of_use`
- `service_code`, `transaction_type`
- `state_before`, `state_after`
- `timestamp`, `source_system`
- `device_fingerprint` (where available)
- `offline_sync_status`
- `journey_type`, `journey_stage` (where applicable)
- `nompilo_context_id` (where applicable)

## Event Ownership

- Trust events: Tshepo/Mvumo
- Clinical transaction events: clinical services
- Financial transaction events: Costa/MusheX/coverage
- Experience BFF may emit composition events but not source-of-truth state replacement events.

## Nompilo and Feedback Event Family

- `core.feedback.requested`
- `core.feedback.submitted`
- `core.nompilo.guidance.requested`
- `core.nompilo.guidance.delivered`
- `core.nompilo.handoff.requested`

## Runtime Emission Convergence (Current Wave)

- `pct-service` outbox publisher now dual-emits transaction-relevant lifecycle events to `core.transaction.events` while preserving existing domain topics.
- `costing-engine-service` outbox publisher now dual-emits financial lifecycle events to `core.transaction.events` while preserving existing COSTA topics.
- `oros-service` outbox publisher now dual-emits order/result transaction milestones to `core.transaction.events`.
- `pharmacy-service` outbox publisher now dual-emits prescription/dispense/payment milestones to `core.transaction.events`.
- `msika-flow-service` outbox publisher now dual-emits marketplace order fulfilment milestones to `core.transaction.events`.
- `mushex-service` outbox publisher now dual-emits payment/claim/remittance milestones to `core.transaction.events`.
- Existing topic streams remain additive and backward compatible; no existing consumer topic contracts were removed.
