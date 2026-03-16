# Eventing Proof Results

## Environment
- Docker Daemon: NOT AVAILABLE
- Status: BLOCKED_EXTERNAL

## Intended Verification

| Check | Producer | Event Type | Expected Evidence |
|-------|----------|-----------|-------------------|
| Outbox row exists | VITO (patient registration) | impilo.vito.patient.created.v1 | SELECT from event_outbox table |
| EventEnvelope valid | VITO | All events | eventId, eventType, schemaVersion>=1, correlationId, tenantId, podId |
| Partition key present | Any producer | Any | meta.partition_key or subjectId fallback |
| Schema version | Any producer | Any | schemaVersion >= 1 |
| Downstream effect | Kafka consumer | Any | Topic exists, message visible |

## Code-Level Evidence (static verification)

The EventEnvelope record (`libs/shared-kernel-java/src/main/java/.../EventEnvelope.java`) enforces all required fields via compact constructor:
- eventId: Objects.requireNonNull
- eventType: Objects.requireNonNull
- schemaVersion: must be >= 1 (throws SchemaValidationException)
- correlationId: Objects.requireNonNull
- causationId: Objects.requireNonNull
- idempotencyKey: Objects.requireNonNull
- producer: Objects.requireNonNull
- tenantId: Objects.requireNonNull
- podId: Objects.requireNonNull
- occurredAt: Objects.requireNonNull
- emittedAt: Objects.requireNonNull (auto-set to now if not provided)
- subjectType: Objects.requireNonNull
- subjectId: Objects.requireNonNull
- payload: Objects.requireNonNull
- meta.partition_key falls back to subjectId

EventEnvelopeTest (`libs/shared-kernel-java/src/test/...`) validates these constraints with unit tests.

## Runtime Verification Script
`scripts/runtime-validation/run-eventing-proof.sh` — ready to execute when Docker is available.

## Blocker
No Docker daemon in current environment. Cannot start Postgres, VITO, or Kafka.
