# J-TR-4 honest limitation — OROS critical-result correlation

The OROS critical event payload (ReportService.criticalPayload / CRITICAL_RESULT_POSTED) OMITS
`encounter_ref`. Therefore the trauma-episode correlation for a critical OROS result is resolved by
PCT via its own **order→episode LINK row** (`ed_diagnostic_order.oros_order_id → trauma_episode_id`,
created when PCT places the order with `encounter_ref = trauma_episode_id`), NOT by a self-correlating
OROS event.

This is a deliberate Gate-1 scope decision: enriching the OROS `RESULT_CRITICAL` payload with
`encounterRef` is an **OROS code change we avoided** (read-through only, zero OROS writes). It is a
**post-gate follow-up** so a Kafka-driven consumer can self-correlate without the PCT link.

Everything else in J-TR-4b is real: the rig drives OROS's own order-place + result-post(isCritical)
flow (a genuine OROS critical result + oros_event_outbox row), PCT reconciles read-through, the ack
is a real OROS `oros_acknowledgements` row via OROS's own ack endpoint, and PCT emits the
`pct.ed.critical_result` SAFETY outbox + a daidzai DIAGNOSTICS phase. Kafka off → outbox rows asserted,
not delivery.
