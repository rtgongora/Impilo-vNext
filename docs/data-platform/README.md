# Data Platform — Ring-2 v1.1-Native Services

> **Status:** Skeleton — no deep logic yet.
> **Date:** 2026-03-11

## Strategic Intent

**Data plane MUST NOT block care execution.**

These Ring-2 services handle data ingestion, governance, and analytics independently
from the Ring-0 registry and Ring-1 clinical execution planes. They consume events
from upstream services but never sit in the critical path of patient care workflows.

## Services

| Service | Port | Artifact ID | Purpose |
|---------|------|-------------|---------|
| Data Ingestion | 8210 | `data-ingestion-service` | Receives, validates, and routes data submissions from facilities and external systems |
| Data Governance | 8220 | `data-governance-service` | Data quality rules, lineage tracking, metadata cataloging, compliance enforcement |
| NDR | 8230 | `ndr-service` | National Data Repository for aggregated, de-identified health data analytics |

## v1.1 Compliance

All three services are born v1.1-native:

- **Header enforcement** via `tech-companion` auto-configuration (`V11HeaderFilter`)
- **Idempotency** via `IdempotencyFilter` on POST/PUT/PATCH commands
- **Event outbox** table with all v1.1 context columns (correlation_id, causation_id, idempotency_key, producer, tenant_id, pod_id, subject_id, subject_type, partition_key)
- **GoldenContractIT** extending `GoldenContractSuite` for automated contract verification
- **Error envelope** via `ErrorEnvelope.of(...)` on all error responses

## Schema Prefixes

Each service uses a distinct table prefix to avoid collisions:

| Service | Prefix |
|---------|--------|
| Data Ingestion | `din_` |
| Data Governance | `dgv_` |
| NDR | `ndr_` |

## Non-Goals (This Iteration)

- No Kafka consumer/producer wiring
- No Redis caching
- No Testcontainers integration tests
- No deep endpoint logic beyond skeleton structure
- No refactoring of existing legacy data services
