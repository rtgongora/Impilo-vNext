# Impilo vNext — Federation Protocol Outline (v1.1)

**Date**: 2026-02-08

---

## 1. Federation Principles

1. **Pods are governed, not forks** — every pod operates under national policy
2. **Authority is explicit** — each domain declares who is authoritative
3. **Merges are non-destructive** — old identifiers map to new, never overwritten
4. **Revocations propagate immediately** — High-Priority Control Channel, not best-effort
5. **Cross-pod correlation is consent-governed** — no silent identity linkage

---

## 2. Authority Table

### Per-Domain Authority Declarations

| Domain | National Spine Authority | Pod Authority | Default |
|---|---|---|---|
| **Identity (CRID/CPID)** | AUTHORITATIVE | Consumer (reads national truth) | National |
| **Terminology (ZIBO)** | AUTHORITATIVE | Consumer | National |
| **Facility Master IDs (TUSO)** | AUTHORITATIVE | Consumer + local extensions | National |
| **Provider Licensure (VARAPI)** | AUTHORITATIVE | Consumer | National |
| **Product Catalog (MSIKA)** | AUTHORITATIVE | Consumer + local formulary | National |
| **Clinical Records (BUTANO)** | Reference (longitudinal) | Pod-Authoritative (encounter execution) | Pod for execution, National for longitudinal |
| **Encounter Execution (PCT)** | N/A | Pod-Authoritative (local care delivery) | Pod |
| **Orders/Results (OROS)** | N/A | Pod-Authoritative (local workflows) | Pod |
| **Finance/Claims (MUSHEX)** | AUTHORITATIVE (settlement rules) | Pod-Authoritative (local billing) | Hybrid |
| **Consent** | AUTHORITATIVE (national consent registry) | Pod holds local cache | National |
| **Reporting/Surveillance** | Consumer (receives aggregates) | Pod-Authoritative (generates) | Pod produces, National consumes |

### Authority Types

| Type | Definition | Sync Direction |
|---|---|---|
| `NATIONAL_AUTHORITATIVE` | National Spine is the source of truth. Pods consume via federation channel. | National → Pod |
| `POD_AUTHORITATIVE` | Pod is the source of truth for its scope. National receives summaries/links. | Pod → National |
| `HYBRID` | Both maintain state with defined conflict rules. | Bidirectional with conflict resolution |
| `CONSUMER_ONLY` | Entity reads from authoritative source, no local writes. | One-way consumption |

---

## 3. Pod Registration & Lifecycle

### Pod Entity Model

```
Pod {
  pod_id: String (UUID or well-known name)
  name: String
  deployment_level: LEVEL_1 | LEVEL_2 | LEVEL_3
  organization: String (owning entity)
  status: REGISTERED | ACTIVE | SUSPENDED | DECOMMISSIONED

  // Authority declarations
  authority_declarations: [{
    domain: String
    authority_type: NATIONAL_AUTHORITATIVE | POD_AUTHORITATIVE | HYBRID | CONSUMER_ONLY
    sync_direction: NATIONAL_TO_POD | POD_TO_NATIONAL | BIDIRECTIONAL
    conflict_rule: String (reference to conflict resolution policy)
  }]

  // Connectivity
  federation_endpoint: URL (gRPC or REST)
  kafka_consumer_group: String
  trust_channel_subscription: Boolean

  // Compliance
  reporting_obligations: [String] (list of required reporting events)
  last_health_check: Timestamp
  sync_lag_ms: Long

  registered_at: Timestamp
  activated_at: Timestamp
}
```

### Registration Flow

```
1. Pod operator submits registration request to Federation Control
   - Organization identity, deployment justification
   - Authority declarations per domain
   - Technical connectivity details

2. Federation Control validates:
   - Organization is authorized (national policy)
   - Authority declarations don't create dual-authority conflicts
   - Technical endpoint is reachable

3. Federation Control issues:
   - pod_id (UUID)
   - Kafka consumer group credentials
   - Trust channel subscription token
   - Initial synchronization credentials (for bootstrap)

4. Pod performs initial sync:
   - Pulls snapshots from all NATIONAL_AUTHORITATIVE domains
   - Registers consumer groups on relevant topics
   - Subscribes to trust.revocation channel

5. Pod status transitions to ACTIVE
```

---

## 4. Conflict Resolution Rules

### Identity Conflicts (VITO)

| Scenario | Resolution | Propagation |
|---|---|---|
| **National merge (two CRIDs unified)** | Surviving CRID absorbs retired CRID. Merge event emitted with mapping. | All pods must reconcile within 24 hours. |
| **Pod discovers local duplicate** | Pod emits merge candidate to national. National performs authoritative merge. | National → all pods via trust.federation.merge |
| **CPID collision across pods** | Prohibited by design (CPID derivation is nationally controlled via HSM). | N/A |
| **Pod has records under retired CRID** | Pod updates local references using merge mapping. Emits reconciliation-complete event. | Pod → National |

### Clinical Record Conflicts

| Scenario | Resolution | Propagation |
|---|---|---|
| **Same patient seen at pod and national simultaneously** | Each encounter is separate (encounter ID is globally unique). Longitudinal record links both. | Pod → BUTANO via clinical events |
| **Offline encounter uploaded after connectivity restored** | Conflict-free CRDT approach: encounter data is additive. Observations merged by timestamp. | Offline sync → BUTANO with reconciliation |
| **Order placed at pod, result available at national** | OROS reconciliation engine matches by order ID. Pod-placed orders carry pod_id for routing. | Bidirectional via kernel.oros.* topics |

### Financial Conflicts

| Scenario | Resolution | Propagation |
|---|---|---|
| **Bill finalized at pod, tariff updated at national** | Bill uses tariff effective at time of finalization (immutable after Class A check). | No conflict — point-in-time pricing |
| **Claim submitted by pod, adjudicated at national** | Claims flow: Pod → National MUSHEX for adjudication. Settlement flows back to pod. | Pod → National → Pod |
| **Double-payment detected** | MUSHEX reconciliation engine flags. Refund initiated per policy. | Bidirectional |

---

## 5. Propagation Channels

### Channel 1: High-Priority Control (trust.*)

**Purpose**: Safety-critical state changes that must propagate immediately.
**Delivery guarantee**: Exactly-once semantics (idempotency + acks=all + min.insync.replicas=2)
**Latency SLA**: < 5 seconds end-to-end

| Topic | Content | Producers | Consumers |
|---|---|---|---|
| `trust.revocation.consent` | Consent revocations | tshepo-consent-service | All pods, all services accessing patient data |
| `trust.revocation.privilege` | Practitioner privilege revocations | varapi-service | All pods, all clinical services |
| `trust.revocation.identity` | Identity corrections (merge, death, correction) | vito-service, ubomi-service | All pods, all services referencing CRID/CPID |
| `trust.federation.merge` | Identity merge events with mapping | vito-service | All pods |
| `trust.federation.pod_registered` | New pod registration | federation-control | All existing pods |
| `trust.decision_evidence` | Policy decision audit trail | tshepo-authz-service | tshepo-audit-service, compliance |

### Channel 2: Kernel Sync (kernel.*)

**Purpose**: National truth updates for registries and reference data.
**Delivery guarantee**: At-least-once (idempotent consumers required)
**Latency SLA**: < 30 seconds end-to-end

| Topic Pattern | Content | Producers |
|---|---|---|
| `kernel.vito.client.*` | Client registry changes (delta) | vito-service |
| `kernel.varapi.provider.*` | Provider registry changes (delta) | varapi-service |
| `kernel.tuso.facility.*` | Facility registry changes (delta) | tuso-service |
| `kernel.msika.catalog.*` | Product catalog changes (delta) | msika-service |
| `kernel.zibo.artifact.*` | Terminology changes (delta) | zibo-service |
| `kernel.mushex.payment.*` | Payment lifecycle events | mushex-service |

### Channel 3: Clinical Events (clinical.*)

**Purpose**: Care execution events for longitudinal record and integration.
**Delivery guarantee**: At-least-once (idempotent consumers required)
**Latency SLA**: < 10 seconds end-to-end

### Channel 4: Reporting Obligations (analytics.reporting.*)

**Purpose**: Mandatory statutory/surveillance reporting from pods to national.
**Delivery guarantee**: At-least-once with acknowledgement
**Content**: Aggregated/anonymized data per legal requirements

---

## 6. Revocation Propagation Protocol

### Consent Revocation

```
1. Patient revokes consent via TSHEPO Consent Service
2. Consent service:
   a. Updates local consent record (immediate)
   b. Emits trust.revocation.consent event
   c. Calls Federation Control to identify affected pods

3. Federation Control:
   a. Looks up which pods hold data for this patient
   b. Ensures event is routed to all affected pod consumer groups
   c. Monitors acknowledgement

4. Each pod:
   a. Receives revocation event
   b. Updates local consent cache (immediate)
   c. Blocks further data access under revoked scope
   d. Emits acknowledgement event

5. Federation Control:
   a. Tracks acknowledgements
   b. Escalates if pod doesn't acknowledge within SLA (5 minutes)
   c. Can suspend pod's data access if non-responsive
```

### Privilege Revocation

```
1. VARAPI revokes practitioner privilege
2. VARAPI emits trust.revocation.privilege event
3. All pods update local privilege cache
4. Any in-flight Class A action by that practitioner is flagged for review
5. Active sessions may require re-authentication (step-up)
```

---

## 7. Pod Sync & Bootstrap

### Initial Sync (New Pod)

```
1. Pod registered and activated
2. Pod calls snapshot endpoints for all NATIONAL_AUTHORITATIVE domains:
   - GET /api/v1/clients/snapshot (VITO) — paginated
   - GET /api/v1/providers/snapshot (VARAPI) — paginated
   - GET /api/v1/facilities/snapshot (TUSO) — paginated
   - GET /msika/v1/items/snapshot (MSIKA) — paginated
   - GET /v1/artifacts/snapshot (ZIBO) — paginated
3. Pod populates local projections from snapshots
4. Pod subscribes to kernel.* topics (delta events)
5. Pod subscribes to trust.* topics (revocations)
6. Pod catches up on any events between snapshot timestamp and current offset
7. Pod reports sync-complete to Federation Control
8. Pod status: ACTIVE
```

### Ongoing Sync

```
Continuous:
- Pod consumes kernel.* events → updates local projections
- Pod consumes trust.* events → applies revocations immediately
- Pod reports sync lag to Federation Control (heartbeat every 60s)

Periodic:
- Daily: Pod validates local projection checksums against national snapshots
- Weekly: Full reconciliation audit (sample-based)

Recovery:
- If pod is offline > 24 hours:
  1. Re-bootstrap from latest snapshots
  2. Replay events from last known offset
  3. Reconcile any conflicts
  4. Report recovery-complete to Federation Control
```

---

## 8. Reporting Obligations

### Mandatory Reports (All Pods)

| Report | Frequency | Content | Legal Basis |
|---|---|---|---|
| Notifiable disease events | Real-time | Anonymized case data | Public Health Act |
| Birth/death events | Real-time | Event data to UBOMI | CRVS regulations |
| Aggregate service statistics | Daily | Patient counts, service types | Health Information Act |
| Drug utilization reports | Weekly | Dispensing aggregates | Pharmacy regulations |
| Financial settlement reports | Monthly | Claims/payment summaries | Finance Act |

### Reporting Protocol

```
1. Pod generates report per obligation schedule
2. Pod publishes to analytics.reporting.* topic
3. National analytics pipeline consumes and aggregates
4. Federation Control tracks reporting compliance per pod
5. Non-compliance triggers escalation:
   - 1 missed report: warning
   - 3 consecutive misses: review
   - 7 consecutive misses: pod suspension consideration
```

---

## 9. Security Considerations

| Concern | Mitigation |
|---|---|
| Pod impersonation | mTLS between pods and national spine; pod certificates issued by TSHEPO Keys |
| Data leakage via federation | All cross-pod data flows governed by consent; purpose limitation enforced |
| Compromised pod | Federation Control can suspend pod access; revoke federation credentials |
| Man-in-the-middle on federation channel | mTLS + event signing (Ed25519 via TSHEPO Keys) |
| Stale revocation cache at pod | Trust channel SLA monitoring; automatic suspension for non-responsive pods |
| Cross-pod correlation attack | CPIDs are pod-scoped by default; cross-pod linkage requires explicit consent |
