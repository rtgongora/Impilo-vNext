# Impilo vNext — Manifest v1.1 Compliance Summary

**Date**: 2026-02-08
**Status**: CRITICAL — Major gaps identified across all v1.1 dimensions
**Scope**: Full architectural audit of current codebase against Manifest v1.1 (Canonical)

---

## 1. Consistency Model (A/B/C) — Status: NOT IMPLEMENTED

### v1.1 Requirement
Every clinical capability MUST be categorized into one of three Clinical Safety Consistency Classes:
- **Class A** (Hard-Truth Required): Controlled substances, high-risk procedures, privilege checks, consent revocation, billing finalization — requires synchronous Kernel validation or signed entitlement + freshness proof
- **Class B** (Bounded-Stale Allowed): Routine documentation, vitals, non-controlled orders — allowed on projections if staleness ≤ defined threshold (5–30 min) with logged decision evidence
- **Class C** (Always Allowed Offline): Emergency care, triage, basic notes — requires signed offline entitlements, audit logging, post-sync reconciliation

### Current State
- **Zero classification exists.** No `@ConsistencyClass` annotations, no enforcement interceptors, no action categorization tables.
- All services operate implicitly as "eventual consistency with no bounds" — the most dangerous possible mode for clinical safety.
- The `tshepo-authz-service` has break-glass support but no mechanism to enforce Class A sync checks before permitting controlled actions.
- Clinical services (PCT, OROS, Pharmacy) make RestTemplate calls to other services for integration but these are opportunistic, not safety-classified.

### Minimum Required Changes
1. Define a `ConsistencyClass` enum (A/B/C) in `shared-core` or `tshepo-contracts`
2. Create per-action classification table in `tshepo-authz-service` (action → class mapping)
3. Implement Class A enforcement interceptor: blocks action if sync Kernel check fails (unless break-glass)
4. Implement Class B staleness tracking: projections must carry `last_synced_at` metadata
5. Implement Class C offline entitlement validation in `tshepo-offline-service`
6. Annotate all clinical flows in PCT, OROS, Pharmacy, COSTA, MUSHEX

---

## 2. Event Strategy (Delta-First + Snapshots + Schema Registry) — Status: NOT IMPLEMENTED

### v1.1 Requirement (Laws 3, 4, 5)
- **Law 3**: All events must use versioned schemas in a Schema Registry with mandatory fields: `event_id`, `event_type`, `schema_version`, `correlation_id`, `causation_id`, `idempotency_key`, `producer`, `tenant_id/pod_id`, `subject_id`, `subject_type`, `occurred_at`, `emitted_at`
- **Law 4**: Delta-first events (changed fields only) as default. Periodic snapshots + on-demand snapshot endpoints for recovery/backfill. "Full state in every event" is PROHIBITED for large domains.
- **Law 5**: Projection consumers must implement: ordering strategy, replay handling, poison message strategy, backfill strategy, staleness reporting

### Current State
- **Outbox pattern**: Implemented in 24 services (good foundation) — `event_outbox` table with polling publisher
- **Event entity schema**: MINIMAL — only 6 fields: `id` (Long auto-increment), `aggregate_type`, `aggregate_id`, `event_type`, `payload` (jsonb blob), `created_at`, `published_at`
- **MISSING from events**: `event_id` (UUID), `schema_version`, `correlation_id`, `causation_id`, `idempotency_key`, `producer`, `tenant_id`, `pod_id`, `subject_id`, `subject_type`, `occurred_at`, `emitted_at`
- **No Schema Registry**: No Avro/Protobuf schemas anywhere. No schema-registry in docker-compose. No CI compatibility gates.
- **No delta events**: Events carry full payload as JSON blob — no changed-field tracking
- **No snapshot endpoints**: No service exposes a `/snapshot` or `/bootstrap` API for consumer recovery
- **No projection infrastructure**: No staleness monitors, no replay handlers, no poison message queues, no backfill mechanisms
- **Events are unversioned**: Payload structure is implicit (inline JSON), not declared or validated

### Minimum Required Changes
1. Add Schema Registry (Confluent or Apicurio) to docker-compose and Helm
2. Define canonical event envelope in `shared-core` with all v1.1 mandatory fields
3. Migrate all 24 `EventOutboxEntity` classes to include new fields
4. Flyway migrations for all 24 `event_outbox` tables to add columns
5. Convert OutboxPublisher to emit delta events with schema version headers
6. Add snapshot REST endpoints to all Ring 0 services
7. Add CI schema compatibility checks
8. Implement dead-letter queue pattern for poison messages

---

## 3. Federation Protocol Requirements — Status: NOT IMPLEMENTED

### v1.1 Requirement (Section 5)
- Pods are governed, not forks. Federation is mandatory.
- Authority boundaries per domain (National-Authoritative vs Pod-Authoritative)
- Merge event propagation (VITO merges → pods reconcile within bounded time)
- Consent/privilege revocation via High-Priority Control Channel
- Cross-pod identity linkage is consent-governed
- Reporting obligations for statutory/surveillance data
- `pod_id` field on all events

### Current State
- **Zero federation code.** The term "federation" does not appear in any Java source file.
- No `pod_id` concept anywhere — events carry `tenant_id` but no pod differentiation
- VITO has no merge event emission mechanism
- No High-Priority Control Channel for revocation propagation
- No authority boundary declarations
- No cross-pod identity linkage governance
- No reporting obligation framework

### Minimum Required Changes
1. Define federation domain model: `Pod`, `AuthorityBoundary`, `FederationChannel`
2. Add `pod_id` field to event envelope and trust context headers
3. Implement merge event emission in VITO
4. Create High-Priority Control Channel (dedicated Kafka topic or gRPC stream) for revocation events
5. Create Federation Control Service (or module within TSHEPO) managing authority tables and routing
6. Add pod registration and capability declaration APIs
7. Define reporting obligation policies per domain

---

## 4. Operational Primitives — Status: PARTIAL

### 4a. Audit Ledger — Status: IMPLEMENTED (with gaps)

**What exists:**
- `tshepo-audit-service` with SHA-256 hash chain (tamper-evident, append-only)
- Per-tenant chain heads with pessimistic locking for gapless sequencing
- Chain verification endpoint (forward-walk recomputation)
- Kafka consumer + REST ingest for audit events
- Export service for compliance
- 3 test classes (AuditChainServiceTest, AuditExportServiceTest, AuditQueryServiceTest)

**What's missing:**
- No HMAC/KMS signing of individual audit entries (uses software SHA-256 only)
- No `policy_version` field for decision evidence (v1.1 Section 6.3 requires it)
- Audit chain uses SHA-256 concatenation but not a Merkle tree — acceptable but not optimal for partial verification
- No cross-service audit correlation (services don't consistently emit audit events)

### 4b. KMS/HSM Integration — Status: PARTIAL

**What exists:**
- `tshepo-keys-service` with Ed25519 key management, AES-256-GCM encryption at rest
- Key rotation (configurable interval, rotation log)
- JWKS endpoint, certificate trust chain management
- Token signing service
- KEK (Key Encryption Key) for protecting private keys at rest

**What's missing:**
- KEK is loaded from application config (`tshepo.keys.kek`), NOT from HSM/Vault — the code comment explicitly says "In production this MUST be sourced from Vault or a KMS"
- No Vault/HSM integration code (no `spring-cloud-vault` dependency, no PKCS#11 interface)
- No CPID derivation via keyed pseudonymization (v1.1 Law 1 requires HSM-backed)
- No secrets rotation automation

### 4c. DR/SLOs — Status: NOT IMPLEMENTED

- No SLO definitions for any service
- No RPO/RTO specifications
- No backup automation or restore drills
- No runbooks (empty `docs/runbooks/` directory)
- No error budget tracking

### 4d. Observability — Status: NOT IMPLEMENTED

- No Prometheus/Grafana in docker-compose
- No OpenTelemetry instrumentation
- No SLI/SLO dashboards
- No alerting configuration
- Basic SLF4J logging only

---

## 5. Workload Partitioning (Clinical vs Telemetry vs Analytics Buses) — Status: NOT IMPLEMENTED

### v1.1 Requirement (Law 8)
Three separate buses:
- **Clinical Bus**: Safety-critical domain events (low latency, high reliability, bounded payloads)
- **Telemetry/IoT Bus**: High-throughput device events (separate partitions, retention policies)
- **Analytics Bus / Lake ingestion**: Bulk pipelines, ETL, near-real-time analytics

### Current State
- Single Kafka instance with all topics co-mingled
- No topic namespace separation beyond service name prefix
- No differentiated retention policies
- No priority differentiation between safety-critical events and analytics/telemetry
- TUSO has telemetry endpoints but events flow on the same Kafka instance as clinical events

### Minimum Required Changes
1. Define topic namespace convention: `clinical.*`, `telemetry.*`, `analytics.*`
2. Configure separate Kafka clusters or at minimum separate topic partitioning and retention policies
3. Add clinical bus SLA enforcement (latency, delivery guarantees)
4. Route telemetry events (TUSO device data, IoT) to telemetry bus
5. Route analytics events (reporting, surveillance) to analytics bus
6. Update all producers to route to correct bus

---

## 6. Additional v1.1 Gaps

### 6a. OPA Policy Engine — Status: NOT IMPLEMENTED
- v1.1 Law 2 requires Gateway/Envoy + OPA for central policy enforcement
- Current: Envoy ext_authz routes to custom Java `tshepo-authz-service` (not OPA)
- No `.rego` policy files exist
- **Decision**: Whether to migrate to OPA or keep Java PolicyEngine is an architectural choice, but v1.1 explicitly names OPA. Remediation must either add OPA or formally justify the Java engine as equivalent.

### 6b. Developer Portal — Status: NOT IMPLEMENTED
- v1.1 Section 9 requires: developer portal, SDKs, sandbox, mock servers, API keys, contract tests, versioning/deprecation policy, onboarding certification
- Only existing SDK artifact: `libs/tshepo-sdk` (TrustContext propagation) and `libs/tshepo-contracts` (shared DTOs/enums)
- No OpenAPI specs, no sandbox environment, no developer-facing documentation

### 6c. Offline Entitlements — Status: PARTIAL
- `tshepo-offline-service` exists (dedicated sub-service) but needs verification of:
  - Signed JWT/CBOR token issuance with scope, time window, device binding
  - Patient context constraints
  - Conflict resolution rules per domain
  - Mandatory audit events for offline actions

### 6d. API Lifecycle Governance — Status: NOT IMPLEMENTED
- No API versioning strategy beyond URL path (v1/)
- No deprecation policy
- No contract testing harness
- No CI compatibility gates

---

## Compliance Scorecard

| v1.1 Requirement | Status | Severity |
|---|---|---|
| Clinical Safety Classes (A/B/C) | NOT IMPLEMENTED | CRITICAL |
| Schema Registry | NOT IMPLEMENTED | CRITICAL |
| Event Envelope (mandatory fields) | NOT IMPLEMENTED | CRITICAL |
| Delta-First Events | NOT IMPLEMENTED | HIGH |
| Snapshot Endpoints | NOT IMPLEMENTED | HIGH |
| Federation Protocol | NOT IMPLEMENTED | CRITICAL |
| OPA / Central Policy Engine | PARTIAL (Java, not OPA) | HIGH |
| Audit Ledger (tamper-evident) | IMPLEMENTED | LOW (minor gaps) |
| KMS/HSM Integration | PARTIAL (no Vault/HSM) | HIGH |
| Break-Glass | IMPLEMENTED | LOW |
| Offline Entitlements | PARTIAL | MEDIUM |
| Workload Partitioning (3 buses) | NOT IMPLEMENTED | HIGH |
| DR/SLOs/RPO/RTO | NOT IMPLEMENTED | CRITICAL |
| Observability (metrics/traces) | NOT IMPLEMENTED | HIGH |
| Developer Portal | NOT IMPLEMENTED | MEDIUM |
| Projection Infrastructure | NOT IMPLEMENTED | HIGH |
| API Lifecycle Governance | NOT IMPLEMENTED | MEDIUM |
| Decision Evidence Logging | NOT IMPLEMENTED | HIGH |

**Overall v1.1 Compliance: ~15%**
- 2/18 requirements fully met (Audit Ledger, Break-Glass)
- 3/18 partially met (KMS, OPA/PolicyEngine, Offline)
- 13/18 not implemented at all
