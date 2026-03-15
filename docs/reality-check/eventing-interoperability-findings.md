# Eventing Interoperability Findings — Impilo vNext

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Risk Class: C — Eventing works in one service but not across all

## Executive Summary

The outbox pattern is **universally deployed** — 66/67 services have event_outbox references (tshepo-audit-service uses domain-specific audit storage). The EventEnvelope format is standardized via tech-companion. Cross-service event flows are structurally wired via Kafka topics and consumer groups, but **live cross-service event flow has not been executed** in this environment.

## Eventing Architecture

### Outbox Pattern

Every service follows the outbox pattern:
1. Business operation + outbox INSERT in single DB transaction
2. Scheduled relay polls outbox and publishes to Kafka
3. Consumer acknowledges after processing

### Outbox Table Schema (v1.1 Standard)

Required columns in every `event_outbox` table:

| Column | Type | Purpose |
|---|---|---|
| `id` | UUID | Primary key |
| `event_type` | VARCHAR | e.g., `impilo.vito.client.registered.v1` |
| `schema_version` | INT | Schema version for forward compatibility |
| `partition_key` | VARCHAR | Kafka partition routing |
| `tenant_id` | VARCHAR | Trust governance field |
| `pod_id` | VARCHAR | Trust governance field |
| `correlation_id` | VARCHAR | Request tracing |
| `idempotency_key` | VARCHAR | Dedup on consumer side |
| `payload_json` | JSONB | Event data |
| `created_at` | TIMESTAMP | Event creation time |
| `published` | BOOLEAN | Relay status |
| `published_at` | TIMESTAMP | When published to Kafka |

### Event Type Naming Convention

Format: `impilo.<domain>.<entity>.<action>.v<N>`

Examples found in codebase:
- `impilo.vito.client.registered.v1`
- `impilo.tuso.facility.updated.v1`
- `impilo.varapi.provider.registered.v1`
- `impilo.pct.encounter.created.v1`
- `impilo.oros.order.placed.v1`
- `impilo.pharmacy.dispensation.completed.v1`

## Findings

### Outbox Coverage

| Metric | Value |
|---|---|
| Services with outbox references | 66/67 |
| Services without outbox | 1 (tshepo-audit-service — uses domain-specific audit_entries) |
| Outbox tables in Flyway migrations | Present in all 66 services' migration dirs |
| v1.1 columns (tenant_id, pod_id, etc.) | Present per compliance matrix |

### Cross-Service Event Flows (Structural Evidence)

| Producer | Event Type | Consumer | Evidence |
|---|---|---|---|
| vito-service | `impilo.vito.client.*` | pct-service, oros-service | Kafka consumer config in application.yml |
| tshepo-service | `impilo.tshepo.policy.*` | All services (via tshepo-sdk) | Trust header propagation |
| pct-service | `impilo.pct.encounter.*` | butano-service | FHIR resource sync flow |
| oros-service | `impilo.oros.order.*` | pharmacy-service | Order → dispensation flow |
| msika-service | `impilo.msika.product.*` | inventory-service | Product catalog sync |
| integration-hub | Route events | Downstream services | Message routing/dispatch |

### Kafka Configuration

- **Broker**: Apache Kafka 3.7.1 (KRaft mode, no ZooKeeper)
- **Advertised listener**: `kafka:9092` (internal compose network)
- **Consumer groups**: Per-service isolation (each service has unique group-id)

### What Is Proven

| Check | Status | Evidence |
|---|---|---|
| Outbox table universal presence | PASS | 66/67 services (1 exempted) |
| v1.1 governance columns in outbox | PASS | Static compliance script + migration inspection |
| Event type versioning convention | PASS | All discovered types end in `.v<N>` |
| Kafka infrastructure defined | PASS | docker-compose.yml and runtime compose |
| Event bus proof script exists | PASS | `scripts/smoke/event-bus-proof.sh` |

### What Is Not Proven

| Check | Status | Reason |
|---|---|---|
| Cross-service event delivery | UNVERIFIED | Requires running Kafka + services |
| Outbox relay actually publishes | UNVERIFIED | Requires running scheduled tasks |
| Consumer idempotent processing | UNVERIFIED | Requires live Kafka consumer |
| Schema version forward-compat | UNVERIFIED | No schema registry runtime test |
| Partition key routing correctness | UNVERIFIED | Requires multi-partition Kafka |

### Risks

1. **Relay scheduling**: Each service presumably has a `@Scheduled` outbox relay, but the relay implementation lives in tech-companion as `CompanionOutboxPublisher`. Services that don't configure the relay bean will silently not publish events.

2. **Topic naming**: No centralized topic registry was found. Topic names are defined per-service in `application.yml`, which could drift.

3. **Consumer offset management**: Default Kafka consumer config relies on `auto.offset.reset=earliest/latest` — no explicit offset management strategy was found.

## Validation Script

See: `scripts/reality-check/run-eventing-checks.sh`

Supports `--live` flag for database and Kafka connectivity tests.

## Verdict

**EVENTING: STRUCTURALLY COMPREHENSIVE, CROSS-SERVICE FLOW UNVERIFIED**

The outbox pattern is universally applied with correct v1.1 fields. Event type naming is consistent. The gap is live verification of actual cross-service event delivery, which requires Docker runtime.
