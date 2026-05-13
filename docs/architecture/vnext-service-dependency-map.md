# Impilo vNext — Service Dependency Map

**Date**: 2026-03-26
**Version**: 1.1
**Status**: Living document — updated as dependencies evolve

---

## 1. Dependency Rules (Enforced)

```
Ring 0 ──→ NOTHING           (zero outbound dependencies to Ring 1+)
Ring 1 ──→ Ring 0 only       (via REST/events, never direct DB access)
Ring 2 ──→ Ring 0 + Ring 1   (via REST/events)

NEVER: Ring 0 → Ring 1       (Kernel must not depend on clinical)
NEVER: Ring 1 → Ring 2       (for critical path — supply/analytics must not block care)
NEVER: Any Ring → Direct DB of another service
```

All inter-service communication flows through:
1. **Synchronous**: REST/gRPC via Envoy gateway (ext_authz → TSHEPO)
2. **Asynchronous**: Kafka events via outbox pattern (event_outbox table per service)
3. **Trust headers**: 14 headers injected by TSHEPO on every authorized request

---

## 2. Request Flow (Every Request)

```
Client (UI/API)
    │
    ▼
Envoy Gateway (:10000)
    │
    ├──→ ext_authz (gRPC) ──→ TSHEPO Authz (:8081)
    │                              │
    │                              ├── Authenticate (Keycloak JWT)
    │                              ├── Classify (A/B/C)
    │                              ├── Evaluate RBAC/ABAC (OPA)
    │                              ├── Log decision evidence
    │                              └── Return verdict + inject 14 trust headers
    │
    ▼ (authorized, headers injected)
Target Service
    │
    ├── TrustContextFilter extracts headers
    ├── Execute domain logic
    ├── Outbox write (event_outbox table)
    └── Audit event → TSHEPO Audit
```

---

## 3. Ring 0 Internal Dependencies

### TSHEPO Trust Cluster (Internal)

```
tshepo-authz-service (:8081)
    ├──→ tshepo-identity-service (:8181)   [CPID resolution]
    ├──→ tshepo-consent-service (:8182)    [consent evaluation]
    ├──→ tshepo-keys-service (:8184)       [token signing/verification]
    ├──→ tshepo-audit-service (:8183)      [decision evidence logging]
    ├──→ OPA (:8181)                       [policy evaluation]
    └──→ Keycloak (:8080)                  [JWT validation, realm info]

tshepo-offline-service (:8185)
    ├──→ tshepo-keys-service (:8184)       [token signing]
    ├──→ tshepo-consent-service (:8182)    [offline consent snapshots]
    └──→ tshepo-authz-service (:8081)      [entitlement verification]

tshepo-consent-service (:8182)
    └──→ Kafka: trust.revocation.consent   [revocation propagation]

tshepo-audit-service (:8183)
    └──→ Kafka: trust.decision_evidence    [audit event stream]
```

### Core Registries (No outbound Ring 1+ dependencies)

```
vito-service (:8082)        ──→ Kafka: kernel.vito.client.*
varapi-service (:8083)      ──→ Kafka: kernel.varapi.provider.*
tuso-service (:8084)        ──→ Kafka: kernel.tuso.facility.*
zibo-service (:8085)        ──→ Kafka: kernel.zibo.artifact.*
msika-service (:8086)       ──→ Kafka: kernel.msika.catalog.*
mushex-service (:8087)      ──→ Kafka: kernel.mushex.payment.*, kernel.mushex.claim.*
butano-fhir (:8090)         ──→ Kafka: clinical.butano.resource.*
```

---

## 4. Ring 1 → Ring 0 Dependencies

### PCT — Patient Care Tracker (:8088)

```
pct-service
    ├──→ tshepo-authz-service      [auth, trust context]
    ├──→ vito-service               [patient identity / CPID lookup]
    ├──→ tuso-service               [facility context, routing]
    ├──→ butano-fhir                [SHR read/write — clinical data]
    ├──→ mushex-service             [payment gate — check coverage before care]
    │
    ├──→ Kafka (produce):
    │       clinical.pct.journey.*
    │       clinical.pct.encounter.*
    │       clinical.pct.triage.*
    │
    └──→ Kafka (consume):
            trust.revocation.consent     [patient consent changes]
            kernel.vito.client.*         [patient updates]
            kernel.tuso.facility.*       [facility updates]
```

### OROS — Orders & Results (:8089)

```
oros-service
    ├──→ tshepo-authz-service      [auth, trust context]
    ├──→ butano-fhir                [result writeback to SHR]
    ├──→ zibo-service               [terminology validation — LOINC, SNOMED]
    ├──→ tuso-service               [lab/facility routing]
    │
    ├──→ Kafka (produce):
    │       clinical.oros.order.*
    │       clinical.oros.result.*
    │       clinical.oros.workstep.*
    │
    └──→ Kafka (consume):
            clinical.pct.encounter.*     [encounter triggers orders]
            kernel.zibo.artifact.*       [terminology updates]
```

### COSTA — Costing Engine (:8101)

```
costing-engine-service
    ├──→ tshepo-authz-service      [auth, trust context]
    ├──→ msika-service              [tariff lookup, product pricing]
    ├──→ mushex-service             [payment intents, claims packing]
    │
    ├──→ Kafka (produce):
    │       kernel.costa.bill.*
    │
    └──→ Kafka (consume):
            clinical.pct.encounter.*     [encounter triggers billing]
            clinical.oros.order.*        [order triggers costing]
            kernel.msika.catalog.*       [tariff updates]
```

### Pharmacy (:8096)

```
pharmacy-service
    ├──→ tshepo-authz-service      [auth, trust context]
    ├──→ oros-service                [prescription/order lookup]
    ├──→ mushex-service             [payment charges]
    ├──→ msika-service              [formulary, product lookup]
    │
    ├──→ Kafka (produce):
    │       clinical.pharmacy.dispense.*
    │
    └──→ Kafka (consume):
            clinical.oros.order.*        [prescription orders]
            kernel.msika.catalog.*       [formulary updates]
```

### MSIKA Flow — Health Marketplace (:8100)

```
msika-flow-service
    ├──→ tshepo-authz-service      [auth, trust context]
    ├──→ msika-service              [product catalog]
    ├──→ mushex-service             [payment processing]
    ├──→ varapi-service             [vendor credential verification]
    │
    └──→ Kafka (consume):
            kernel.msika.catalog.*       [catalog changes]
            kernel.mushex.payment.*      [payment confirmations]
```

### Coverage (:impilo_coverage)

```
coverage-service
    ├──→ tshepo-authz-service      [auth, trust context]
    └──→ mushex-service             [member benefits, eligibility]
```

### Inpatient (Skeleton)

```
inpatient-service
    ├──→ tshepo-authz-service      [auth, trust context]
    ├──→ tuso-service               [ward/bed topology]
    └──→ pct-service                [care journey context]
```

---

## 5. Ring 2 → Ring 0/1 Dependencies

### Inventory (:8098)

```
inventory-service
    ├──→ tshepo-authz-service      [auth]
    ├──→ tuso-service               [facility context]
    ├──→ msika-service              [product master data]
    │
    └──→ Kafka (produce/consume):
            kernel.msika.catalog.*       [product updates]
            telemetry.*.occupancy        [stock level telemetry]
```

### Landela Adapter (:8092)

```
landela-adapter-service
    ├──→ tshepo-authz-service      [auth]
    └──→ MinIO (S3)                 [document storage]
```

### Credential Verification (:8094)

```
credential-verification-service
    ├──→ tshepo-authz-service      [auth]
    ├──→ tshepo-keys-service        [Ed25519 signing keys]
    └──→ varapi-service             [provider credential data]
```

### Document Service

```
document-service
    ├──→ tshepo-authz-service      [auth]
    └──→ MinIO (S3)                 [object storage + AV scan]
```

### Experience BFF (:8160)

```
experience-bff
    ├──→ tshepo-authz-service      [auth]
    ├──→ vito-service               [patient identity / CPID resolution]
    ├──→ pct-service                [care journeys, patient health summary]
    ├──→ mvumo-service              [consent orchestration; consent-summary for chart UI]
    ├──→ butano-fhir                [health records / IPS proxy]
    └──→ mushex-service             [payment/claims status]
```

Patient chart “consent surface” is composed in the BFF (`EhrPatientSummaryController`: PCT `/v1/patient/{cpid}/summary` + Mvumo `/internal/v1/mvumo/consent-summary`). UIs call `GET /internal/v1/summary/patient/{patientId}` only.

### Adapters (eLMIS, PACS, Integration Hub)

```
inventory-elmis-adapter    ──→ inventory-service + external eLMIS
pharmacy-elmis-adapter     ──→ pharmacy-service + external eLMIS
pacs-adapter-service       ──→ butano-fhir + Orthanc (DICOM)
integration-hub            ──→ Multiple Ring 0 registries + external systems (DHIS2, LIMS, iHRIS)
fhir-gateway-service       ──→ butano-fhir + external FHIR endpoints
connector-fhir-adapter     ──→ fhir-gateway-service
```

---

## 6. Kafka Event Topology

### Producers → Topics → Consumers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            TRUST CHANNEL                                     │
│                                                                              │
│  tshepo-consent ──→ trust.revocation.consent ──→ PCT, OROS, Pharmacy,       │
│                                                    BUTANO, all Ring 1        │
│  tshepo-authz   ──→ trust.revocation.privilege ──→ VARAPI, all Ring 1       │
│  tshepo-audit   ──→ trust.decision_evidence ──→ Audit Ledger, Analytics     │
│  vito-service   ──→ trust.federation.merge ──→ Federation Control           │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                            KERNEL BUS                                        │
│                                                                              │
│  vito-service   ──→ kernel.vito.client.*    ──→ PCT, Experience BFF         │
│  varapi-service ──→ kernel.varapi.provider.* ──→ MSIKA Flow, CVS            │
│  tuso-service   ──→ kernel.tuso.facility.*  ──→ PCT, OROS, Inventory        │
│  zibo-service   ──→ kernel.zibo.artifact.*  ──→ OROS, BUTANO                │
│  msika-service  ──→ kernel.msika.catalog.*  ──→ COSTA, Pharmacy, MSIKA Flow,│
│                                                  Inventory                   │
│  mushex-service ──→ kernel.mushex.payment.* ──→ COSTA, MSIKA Flow, Coverage │
│                 ──→ kernel.mushex.claim.*   ──→ COSTA, Coverage             │
│  costa-engine   ──→ kernel.costa.bill.*     ──→ MUSHEX, Reporting           │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           CLINICAL BUS                                       │
│                                                                              │
│  pct-service    ──→ clinical.pct.journey.*    ──→ BUTANO, Reporting, NDR    │
│                 ──→ clinical.pct.encounter.*  ──→ OROS, COSTA, Reporting    │
│                 ──→ clinical.pct.triage.*     ──→ Surveillance              │
│  oros-service   ──→ clinical.oros.order.*     ──→ Pharmacy, COSTA, BUTANO   │
│                 ──→ clinical.oros.result.*    ──→ BUTANO, Reporting, NDR    │
│                 ──→ clinical.oros.workstep.*  ──→ Reporting                 │
│  pharmacy       ──→ clinical.pharmacy.dispense.* ──→ BUTANO, Inventory,    │
│                                                       Reporting              │
│  butano         ──→ clinical.butano.resource.* ──→ NDR, Surveillance,       │
│                                                     Analytics                │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          TELEMETRY BUS                                       │
│                                                                              │
│  tuso-service   ──→ telemetry.tuso.occupancy.* ──→ Observability            │
│  pct-service    ──→ telemetry.pct.queue.metrics ──→ Observability            │
│  iot-ingestion  ──→ telemetry.iot.*            ──→ Observability, Asset Reg  │
│  devices        ──→ telemetry.device.heartbeat ──→ Observability             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          ANALYTICS BUS                                       │
│                                                                              │
│  reporting-svc  ──→ analytics.reporting.*      ──→ Data Warehouse, NDR      │
│  surveillance   ──→ analytics.surveillance.*   ──→ NDR, Data Warehouse      │
│  ndr-service    ──→ analytics.ndr.*            ──→ Data Warehouse            │
│  mushex-service ──→ analytics.fraud.*          ──→ Data Governance           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Infrastructure Dependencies

### All Backend Services Depend On

```
Every service ──→ PostgreSQL (:5432)      [own database, never shared]
              ──→ Redis (:6379)            [caching, sessions, locks]
              ──→ Kafka (:9092)            [event publishing via outbox]
              ──→ Keycloak (:8080)         [JWT validation — via TSHEPO]
```

### Specialized Infrastructure Dependencies

```
butano-fhir       ──→ HAPI FHIR (:8090)          [FHIR R4 server]
pacs-adapter      ──→ Orthanc (:8042/:4242)       [DICOM server]
document-service  ──→ MinIO (:9000)               [S3 object storage]
landela-adapter   ──→ MinIO (:9000)               [document storage]
envoy             ──→ OPA (:8181)                 [policy evaluation]
                  ──→ tshepo-authz (:8081)         [ext_authz]
```

---

## 8. UI → Backend Dependencies

```
┌──────────────────────────────────────────────────────────────┐
│                     UI Applications                           │
│                                                               │
│  one-ui-shell ──→ Envoy (:10000) ──→ All services            │
│  ehr          ──→ Envoy ──→ PCT, OROS, BUTANO, VITO, ZIBO    │
│  portal       ──→ Envoy ──→ VITO, PCT, BUTANO, MUSHEX       │
│  experience   ──→ Experience BFF (:8160) ──→ Ring 0/1 svc    │
│                                                               │
│  pct-web      ──→ Envoy ──→ PCT                              │
│  oros-web     ──→ Envoy ──→ OROS                              │
│  pharmacy-web ──→ Envoy ──→ Pharmacy, OROS                   │
│  inventory-web──→ Envoy ──→ Inventory                        │
│                                                               │
│  msika-web    ──→ Envoy ──→ MSIKA                            │
│  msika-flow-* ──→ Envoy ──→ MSIKA Flow                      │
│                                                               │
│  mushex-*     ──→ Envoy ──→ MUSHEX                           │
│  costa-console──→ Envoy ──→ Costing Engine                   │
│                                                               │
│  zibo-web     ──→ Envoy ──→ ZIBO                             │
│  butano-web   ──→ Envoy ──→ BUTANO / HAPI FHIR              │
│                                                               │
│  ops-console  ──→ Envoy ──→ All registries, TSHEPO           │
│  support-*    ──→ Envoy ──→ Support service                  │
│  developer-*  ──→ Envoy ──→ Developer Portal                 │
└──────────────────────────────────────────────────────────────┘

Trust Header Injection:
  apiClient.ts (one-ui-shell) injects trust headers on every request
  → Envoy validates via ext_authz → TSHEPO
  → Target service extracts via TrustContextFilter
```

---

## 9. Dependency Violation Risks

| Risk | Description | Mitigation |
|------|-------------|------------|
| **Ring 0 → Ring 1 leak** | If a registry service (e.g., MSIKA) adds a dependency on PCT or Pharmacy | Enforce via ArchUnit tests in shared-kernel-java; CI gate |
| **Ring 1 → Ring 2 critical path** | If PCT blocks on Reporting or Analytics responses | Ring 2 calls must be async (Kafka) or fire-and-forget |
| **Direct DB access** | One service querying another service's PostgreSQL database | Each service has its own DB; network policies block cross-DB access |
| **Circular dependencies** | E.g., OROS → PCT → OROS via events | Use saga pattern; break cycles with intermediate events |
| **Trust bypass** | Direct service-to-service calls bypassing Envoy ext_authz | Service mesh policy; only Envoy endpoint is exposed |

---

## 10. Consolidated Dependency Matrix

| Service (rows depend on columns) | TSHEPO | VITO | VARAPI | TUSO | ZIBO | MSIKA | MUSHEX | BUTANO | PCT | OROS | Pharmacy |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **PCT** | **S** | **S** | - | **S** | - | - | **S** | **S** | - | - | - |
| **OROS** | **S** | - | - | **S** | **S** | - | - | **S** | E | - | - |
| **COSTA** | **S** | - | - | - | - | **S** | **S** | - | E | E | - |
| **Pharmacy** | **S** | - | - | - | - | **S** | **S** | - | - | **S** | - |
| **MSIKA Flow** | **S** | - | **S** | - | - | **S** | **S** | - | - | - | - |
| **Coverage** | **S** | - | - | - | - | - | **S** | - | - | - | - |
| **Inpatient** | **S** | - | - | **S** | - | - | - | - | **S** | - | - |
| **Inventory** | **S** | - | - | **S** | - | **S** | - | - | - | - | - |
| **Experience BFF** | **S** | **S** | - | - | - | - | **S** | **S** | **S** | - | - |

**Legend**: **S** = Synchronous (REST), **E** = Event-driven (Kafka), - = No dependency

---

## 11. Critical Path Analysis

### Patient Visit (Primary Care) — Happy Path

```
1. Patient arrives      → one-ui-shell → Envoy → TSHEPO (auth)
2. Identity check       → VITO (CPID lookup)
3. Queue/triage         → PCT (journey + encounter create)
4. Consultation         → PCT + OROS (order entry) + ZIBO (terminology)
5. Prescription         → OROS → Pharmacy (dispense)
6. Billing              → COSTA (costing) → MUSHEX (payment)
7. SHR writeback        → BUTANO (FHIR resources)
8. Discharge            → PCT (journey close)

Critical path latency budget:
  TSHEPO auth:    < 50ms  (cached, Class C)
  VITO lookup:    < 100ms (indexed, cached)
  PCT operations: < 200ms (local DB)
  OROS/Pharmacy:  < 300ms (includes validation)
  BUTANO write:   < 500ms (FHIR server)
  Total target:   < 2s end-to-end
```

### Consent Revocation — High-Priority Path

```
1. Patient revokes consent → TSHEPO Consent
2. Immediate publish       → trust.revocation.consent (acks=all)
3. All Ring 1 consumers    → Receive within 1s (SLA)
4. BUTANO                  → Mask/redact affected resources
5. Audit                   → tshepo-audit logs revocation evidence
```

---

## Doctrine References

The MusheX dependencies shown above (`mushex-service` Ring 1 fan-in/fan-out, finance-flow row, and the kernel event bus rows `kernel.mushex.payment.*` / `kernel.mushex.claim.*`) are governed by:

- [`../doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) — MusheX dual-mode operating doctrine (orchestration gateway vs. direct/default gateway; gateway neutrality; health-sector envelope).
- [`../doctrine/costa-mushex-billing-timing.md`](../doctrine/costa-mushex-billing-timing.md) — costing, billing-timing, and settlement separation between COSTA and MusheX.

Dependency-map rows for MusheX should be read with those doctrines as the controlling principle: MusheX is neutral about which rail moves the money but always carries the health-sector envelope, regardless of whether it acts as an orchestrator or as the direct/default gateway.
