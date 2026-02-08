# Impilo vNext — v1.1 Migration Plan (Ring 0 First)

**Date**: 2026-02-08
**Strategy**: Phased migration with stop-the-line gates before new feature work resumes

---

## Migration Philosophy

1. **Ring 0 stabilizes before Ring 1 features proceed** — Kernel correctness is prerequisite
2. **Infrastructure primitives first** — Schema Registry, event envelope, observability baseline are foundations
3. **Backward-compatible migrations** — New fields are nullable, old events remain parseable
4. **Dual-emit during transition** — Services emit both old-format and new-format events until all consumers migrate

---

## Phase 0: Infrastructure Primitives (STOP-THE-LINE)

**Duration**: Week 1–2
**Gate**: No new domain features until Phase 0 exits

### 0.1 — Schema Registry Deployment
- [ ] Add Apicurio Schema Registry (or Confluent) to `docker-compose.yml`
- [ ] Add Schema Registry to Helm charts (`helm/schema-registry/`)
- [ ] Define JSON Schema format for event envelope (v1 schema)
- [ ] Configure CI pipeline to validate schema compatibility on PR

### 0.2 — Canonical Event Envelope (shared-core)
Create `shared-core` event infrastructure:
- [ ] `ImpiloEvent<T>` record with all v1.1 mandatory fields:
  ```
  event_id (UUID)
  event_type (String)
  schema_version (String, e.g., "1.0")
  correlation_id (UUID)
  causation_id (UUID)
  idempotency_key (String)
  producer (String, service name)
  tenant_id (UUID)
  pod_id (String, default "national-spine")
  subject_id (String)
  subject_type (String)
  occurred_at (Instant)
  emitted_at (Instant)
  data (T — the payload, typed)
  ```
- [ ] `EventOutboxEntity` base class in shared-core with all required columns
- [ ] `OutboxPublisher` base class with schema version header injection

### 0.3 — Event Outbox Migration (All 24 Services)
For each service with an `event_outbox` table:
- [ ] Flyway migration adding new columns (all nullable for backward compat):
  - `event_uuid UUID DEFAULT gen_random_uuid()`
  - `schema_version VARCHAR(16)`
  - `correlation_id UUID`
  - `causation_id UUID`
  - `idempotency_key VARCHAR(255)`
  - `producer VARCHAR(64)`
  - `tenant_id UUID`
  - `pod_id VARCHAR(64) DEFAULT 'national-spine'`
  - `subject_id VARCHAR(255)`
  - `subject_type VARCHAR(64)`
  - `occurred_at TIMESTAMPTZ`
  - `emitted_at TIMESTAMPTZ`
- [ ] Update each service's `EventOutboxEntity` to extend shared base or include new fields
- [ ] Update each service's event-producing code to populate new fields from `TrustContext`

### 0.4 — TrustContext Extension
- [ ] Add `podId` field to `TrustContext` record in `shared-core`
- [ ] Add `x-pod-id` header constant
- [ ] Update `TrustContextFilter` to extract `pod_id` (default: `"national-spine"`)
- [ ] Update `libs/tshepo-sdk/TrustContext` and `TrustContextFilter`
- [ ] Update `libs/tshepo-contracts/TrustHeaders` to include `X-Pod-Id`
- [ ] Update Envoy config to propagate `x-pod-id` header

### 0.5 — Observability Baseline
- [ ] Add Prometheus to `docker-compose.yml`
- [ ] Add Grafana to `docker-compose.yml` with basic dashboards
- [ ] Add Spring Boot Actuator + Micrometer Prometheus endpoint to parent POM
- [ ] Add OpenTelemetry Java agent configuration to service startup
- [ ] Define SLI definitions for Ring 0 services

### Exit Criteria — Phase 0
- [ ] Schema Registry running in local dev and CI
- [ ] Event envelope schema v1 registered and CI-validated
- [ ] All 24 outbox tables have new columns (migrations applied)
- [ ] At least TSHEPO, VITO, BUTANO producing v1.1-compliant events
- [ ] Prometheus + Grafana operational with basic metrics

---

## Phase 1: Ring 0 Kernel Compliance (CRITICAL)

**Duration**: Week 3–6
**Dependencies**: Phase 0 complete

### 1.1 — Clinical Safety Classes (A/B/C)

**Week 3:**
- [ ] Define `ConsistencyClass` enum in `tshepo-contracts`: `CLASS_A`, `CLASS_B`, `CLASS_C`
- [ ] Create `action_classification` table in tshepo-authz DB:
  ```sql
  CREATE TABLE action_classification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service VARCHAR(64) NOT NULL,
    action VARCHAR(128) NOT NULL,
    consistency_class VARCHAR(8) NOT NULL, -- A, B, C
    description TEXT,
    requires_sync_check BOOLEAN DEFAULT false,
    max_staleness_seconds INTEGER, -- for Class B
    offline_allowed BOOLEAN DEFAULT false, -- for Class C
    UNIQUE(service, action)
  );
  ```
- [ ] Populate initial classification table (see `06-consistency-classes.md`)
- [ ] Add `ConsistencyClassInterceptor` to tshepo-authz that checks action class before allowing request

**Week 4:**
- [ ] Implement Class A enforcement:
  - Intercept Class A actions at gateway level
  - Require synchronous TSHEPO policy decision OR valid offline entitlement with freshness proof
  - Block if neither available (unless break-glass activated)
  - Log decision evidence (actor, action, decision, policy_version, reason codes)
- [ ] Implement Class B staleness tracking:
  - Add `x-projection-staleness-ms` header to responses from projection-backed endpoints
  - Add staleness threshold check in consumer services
  - Log actions with staleness evidence

**Week 5:**
- [ ] Integrate Class C with offline entitlements:
  - Validate signed entitlement tokens for offline actions
  - Verify scope, time window, device binding
  - Emit mandatory audit events
  - Queue for post-sync reconciliation

### 1.2 — KMS/HSM Integration

**Week 3–4:**
- [ ] Add `spring-cloud-vault` dependency to parent POM
- [ ] Add HashiCorp Vault to `docker-compose.yml` (dev mode)
- [ ] Implement `VaultKmsProvider` in tshepo-keys-service:
  - KEK retrieval from Vault Transit secrets engine
  - Key wrapping/unwrapping via Vault API
  - Automatic secret rotation
- [ ] Implement CPID keyed pseudonymization:
  - HMAC-SHA256 derivation using HSM-held key
  - Rotation support with re-derivation capability
  - Cross-tenant correlation controls

### 1.3 — Federation Protocol Foundation

**Week 4–5:**
- [ ] Define federation domain model in `tshepo-authz-service`:
  - `PodEntity`: pod_id, name, deployment_level, authority_declarations
  - `AuthorityBoundaryEntity`: pod_id, domain, authority_type (NATIONAL/POD), sync_direction
  - `FederationChannelEntity`: pod_id, channel_type, endpoint, status
- [ ] Create federation tables via Flyway migration
- [ ] Implement `FederationControlService`:
  - Pod registration and capability declaration
  - Authority boundary resolution
  - Reporting obligation enforcement
- [ ] Create High-Priority Control Channel:
  - Kafka topic: `trust.revocation` (consent, privilege, identity)
  - Guaranteed delivery configuration (acks=all, min.insync.replicas=2)
  - Consumer groups per pod

### 1.4 — Decision Evidence Logging

**Week 5:**
- [ ] Add `policy_version` field to `AuditEventEntity` in tshepo-audit-service
- [ ] Create structured `DecisionEvidence` DTO:
  ```
  actor, patient_reference, action, decision (ALLOW/DENY/BREAK_GLASS),
  reason_codes[], policy_version, consistency_class, context{}
  ```
- [ ] Integrate with PolicyEngine: every evaluation emits `DecisionEvidence` to audit service
- [ ] Add `decision_evidence` Kafka topic for real-time compliance monitoring

### 1.5 — Snapshot Endpoints (Ring 0 Services)

**Week 5–6:**
- [ ] Define snapshot API contract:
  ```
  GET /api/v1/{resource}/snapshot?cursor={cursor}&limit={limit}&since={timestamp}
  Response: { items: [...], nextCursor: "...", snapshotTimestamp: "..." }
  ```
- [ ] Implement snapshot endpoints:
  - VITO: `/api/v1/clients/snapshot` (CRID/CPID mappings)
  - VARAPI: `/api/v1/providers/snapshot` (practitioner records)
  - TUSO: `/api/v1/facilities/snapshot` (facility topology)
  - MSIKA: `/msika/v1/items/snapshot` (catalog items)
  - ZIBO: `/v1/artifacts/snapshot` (terminology artifacts)
  - MUSHEX: `/mushex/v1/ledger/snapshot` (ledger state)

### 1.6 — Delta Event Implementation

**Week 6:**
- [ ] Implement `DeltaTracker` utility in shared-core:
  - Compares old and new entity state
  - Produces `Map<String, ChangedField>` (fieldName → {old, new})
  - Only non-null changes included
- [ ] Update Ring 0 OutboxPublishers to use delta format
- [ ] Define delta event schema:
  ```json
  {
    "subject_type": "Client",
    "subject_id": "uuid",
    "change_type": "UPDATE",
    "changed_fields": {
      "lastName": { "old": "Smith", "new": "Jones" },
      "phone": { "old": null, "new": "+263..." }
    },
    "version": 5
  }
  ```

### Exit Criteria — Phase 1
- [ ] Class A/B/C enforcement operational for all clinical actions
- [ ] Break-glass + decision evidence logging working end-to-end
- [ ] Vault KMS integrated for key management
- [ ] Federation protocol foundation: pod registration, authority boundaries, revocation channel
- [ ] Snapshot endpoints functional for all Ring 0 services
- [ ] Delta events being emitted by at least VITO, MSIKA, ZIBO

---

## Phase 2: Ring 1 Clinical Compliance

**Duration**: Week 7–10
**Dependencies**: Phase 1 complete

### 2.1 — PCT Compliance
- [ ] Annotate all PCT actions with consistency class
- [ ] Integrate Class A check for death recording, controlled substance delegation
- [ ] Add offline entitlement support for Class C triage/vitals
- [ ] Migrate outbox to v1.1 envelope (if not done in Phase 0)
- [ ] Add snapshot endpoint for journey/encounter state

### 2.2 — OROS Compliance
- [ ] Annotate all OROS actions with consistency class
- [ ] Integrate Class A check for controlled substance orders, high-risk procedures
- [ ] Add snapshot endpoint for order state
- [ ] Ensure SLA timer events use v1.1 envelope

### 2.3 — COSTA Compliance
- [ ] Class A enforcement for bill finalization, claims submission
- [ ] Ensure tariff lookups declare bounded staleness
- [ ] Add snapshot endpoint for billing state

### 2.4 — MUSHEX Compliance
- [ ] Class A enforcement for payment finalization, settlement release, claim adjudication
- [ ] Integrate step-up + break-glass for high-value operations
- [ ] Add snapshot endpoint for ledger state

### 2.5 — Bus Separation
- [ ] Define topic namespace convention:
  - `clinical.{service}.{event}` — safety-critical
  - `telemetry.{service}.{event}` — device/IoT
  - `analytics.{event}` — reporting/BI
- [ ] Reconfigure Kafka:
  - Clinical topics: higher replication, strict ordering, low retention + archive
  - Telemetry topics: high throughput, longer retention
  - Analytics topics: compacted, bulk-friendly
- [ ] Update all OutboxPublishers to route to correct namespace
- [ ] Update all consumers to subscribe to new topic names
- [ ] Transition strategy: dual-publish to old + new topics for 2 weeks

### Exit Criteria — Phase 2
- [ ] All Ring 1 clinical actions classified and enforced
- [ ] End-to-end "care → record → finance" verified with Class A/B/C
- [ ] Bus separation complete (no clinical events on telemetry bus or vice versa)
- [ ] Offline entitlements tested for emergency care flows
- [ ] Break-glass tested and audit-reviewed

---

## Phase 3: Operational Hardening

**Duration**: Week 11–14
**Dependencies**: Phase 2 complete

### 3.1 — DR/Backup
- [ ] Define RPO/RTO per ring:
  - Ring 0: RPO ≤ 5 min, RTO ≤ 15 min
  - Ring 1: RPO ≤ 15 min, RTO ≤ 30 min
  - Ring 2: RPO ≤ 1 hour, RTO ≤ 2 hours
- [ ] Configure automated Postgres backups (WAL archiving + base backups)
- [ ] Configure Kafka topic backup/mirror
- [ ] Create restore runbooks
- [ ] Schedule and execute DR drill

### 3.2 — SLOs and Error Budgets
- [ ] Define SLOs for each Ring 0 service:
  - Availability: 99.95% (Ring 0), 99.9% (Ring 1)
  - Latency p99: 200ms (auth), 500ms (registry), 1s (clinical)
  - Projection freshness: per-domain (see consistency class thresholds)
- [ ] Implement SLO dashboards in Grafana
- [ ] Configure alerting (PagerDuty/Slack integration)

### 3.3 — Security Hardening
- [ ] mTLS between services (Envoy + service mesh)
- [ ] Secrets rotation automation (Vault)
- [ ] Rate limiting at gateway (Envoy)
- [ ] Security event pipeline to SIEM
- [ ] PAM for admin access

### 3.4 — Release Governance
- [ ] Canary deployment configuration in Helm charts
- [ ] Feature flag framework (LaunchDarkly or custom)
- [ ] API versioning policy document
- [ ] Schema compatibility gates in CI (BACKWARD by default)
- [ ] Ring-based release train: Ring 0 slow/stable, Ring 1 moderate, Ring 2 fast

### 3.5 — Developer Portal Baseline
- [ ] OpenAPI spec generation for all Ring 0 services (springdoc-openapi)
- [ ] API documentation site (Redocly or Swagger UI)
- [ ] Sandbox environment definition
- [ ] SDK packaging (tshepo-sdk published to Maven/npm)
- [ ] API key / client registration prototype

### Exit Criteria — Phase 3
- [ ] DR drill completed with documented RPO/RTO results
- [ ] SLOs defined and dashboards operational
- [ ] mTLS enforced between all services
- [ ] Developer portal with API docs live

---

## Stop-the-Line List

These changes MUST land before any new domain features continue:

| # | Change | Blocks |
|---|---|---|
| STL-1 | Schema Registry deployed and CI-integrated | All new event producers |
| STL-2 | Canonical event envelope in shared-core | All new event producers |
| STL-3 | Event outbox migrations (24 services) | All new event producers |
| STL-4 | `ConsistencyClass` enum + classification table | All clinical features |
| STL-5 | `pod_id` in TrustContext + headers | All federation work |
| STL-6 | Observability baseline (Prometheus/Grafana) | Production readiness |
| STL-7 | Decision evidence logging | Audit compliance |

---

## Integration Contracts

### Event Names (Canonical)

**Clinical Bus** (`clinical.*`):
- `clinical.pct.journey.{created|updated|completed}`
- `clinical.pct.encounter.{started|completed}`
- `clinical.pct.triage.completed`
- `clinical.pct.admission.{created|discharged}`
- `clinical.pct.death.recorded`
- `clinical.oros.order.{placed|accepted|completed|cancelled}`
- `clinical.oros.result.{available|reviewed|released}`
- `clinical.oros.workstep.{started|completed}`
- `clinical.butano.resource.{created|updated}`
- `clinical.pharmacy.dispense.{accepted|dispensed|reversed}`

**Kernel Bus** (`kernel.*`):
- `kernel.vito.client.{registered|updated|merged}`
- `kernel.varapi.provider.{registered|updated|revoked}`
- `kernel.tuso.facility.{created|updated|deactivated}`
- `kernel.msika.catalog.{published|updated|deprecated}`
- `kernel.zibo.artifact.{published|deprecated|retired}`
- `kernel.mushex.payment.{created|authorized|completed|failed}`
- `kernel.mushex.claim.{submitted|adjudicated}`

**Trust Channel** (`trust.*`) — High Priority:
- `trust.revocation.consent`
- `trust.revocation.privilege`
- `trust.revocation.identity`
- `trust.federation.merge`
- `trust.federation.pod_registered`

**Telemetry Bus** (`telemetry.*`):
- `telemetry.tuso.occupancy.snapshot`
- `telemetry.tuso.device.heartbeat`
- `telemetry.pct.queue.metrics`

**Analytics Bus** (`analytics.*`):
- `analytics.reporting.aggregate`
- `analytics.surveillance.event`

### Partition Keys
- **Identity events**: `tenant_id + crid`
- **Clinical events**: `tenant_id + cpid`
- **Order events**: `tenant_id + order_id`
- **Financial events**: `tenant_id + intent_id` or `claim_id`
- **Facility events**: `tenant_id + facility_id`
- **Trust events**: `pod_id + subject_id`

### Required Headers on All Events
```
X-Event-Id: UUID
X-Schema-Version: String
X-Correlation-Id: UUID
X-Causation-Id: UUID
X-Producer: String
X-Tenant-Id: UUID
X-Pod-Id: String
```

### Snapshot Endpoint Contract
```
GET /{service}/v1/{resource}/snapshot
  Query params:
    cursor: String (opaque, for pagination)
    limit: Integer (default 100, max 1000)
    since: ISO-8601 timestamp (incremental snapshots)
  Response:
    {
      "snapshotTimestamp": "2026-02-08T10:00:00Z",
      "items": [...],
      "nextCursor": "abc123" | null,
      "totalCount": 1234
    }
```
