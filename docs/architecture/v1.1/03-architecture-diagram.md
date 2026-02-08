# Impilo vNext — Architecture Diagrams (v1.1 Compliant)

**Date**: 2026-02-08

---

## 1. Logical Architecture (5-Plane Model)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           EXPERIENCE PLANE (VI)                                  │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────────┐    │
│  │  One UI Shell │ │  Ops Console │ │    Portal    │ │  Domain UIs (13+)    │    │
│  │  (3-Zone)     │ │              │ │  (Citizen)   │ │  EHR, PCT, OROS,     │    │
│  │  Work │ Pro │  │              │ │              │ │  Pharmacy, Inventory,│    │
│  │  Life          │              │ │              │ │  MSIKA, COSTA,       │    │
│  │               │ │              │ │              │ │  MUSHEX, ZIBO, etc.  │    │
│  └───────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────────┬───────────┘    │
│          │                │                │                    │                │
└──────────┼────────────────┼────────────────┼────────────────────┼────────────────┘
           │                │                │                    │
           └────────────────┴────────────────┴────────────────────┘
                                    │
                          ┌─────────▼─────────┐
                          │    Envoy Gateway   │  ← ext_authz → TSHEPO
                          │  + OPA Sidecar     │  ← Rate limit, mTLS
                          │  Port 10000        │
                          └─────────┬──────────┘
                                    │
┌───────────────────────────────────┼─────────────────────────────────────────────┐
│                      KERNEL PLANE (I) — Ring 0                                   │
│                                   │                                              │
│  ┌─────────────────── TSHEPO Trust Cluster ───────────────────┐                  │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐  │                  │
│  │  │ Authz     │ │ Identity  │ │ Consent   │ │ Audit     │  │                  │
│  │  │ (8081)    │ │ (8181)    │ │ (8182)    │ │ (8183)    │  │                  │
│  │  │ PolicyEng │ │ CPID Res  │ │ FHIR      │ │ SHA-256   │  │                  │
│  │  │ BreakGlass│ │ MOSIP     │ │ Consent   │ │ HashChain │  │                  │
│  │  │ StepUp    │ │           │ │ Eval+Share│ │ Export    │  │                  │
│  │  │ ClassA/B/C│ │           │ │ Revoke    │ │ Evidence  │  │                  │
│  │  └───────────┘ └───────────┘ └───────────┘ └───────────┘  │                  │
│  │  ┌───────────┐ ┌───────────┐ ┌──────────────────────────┐ │                  │
│  │  │ Keys      │ │ Offline   │ │ Federation Control       │ │                  │
│  │  │ (8184)    │ │ (8185)    │ │ (NEW — authority table,  │ │                  │
│  │  │ Ed25519   │ │ JWT/CBOR  │ │  pod mgmt, routing,     │ │                  │
│  │  │ KMS/HSM   │ │ Entitle   │ │  obligations)            │ │                  │
│  │  │ JWKS      │ │ Recon     │ │                          │ │                  │
│  │  └───────────┘ └───────────┘ └──────────────────────────┘ │                  │
│  └────────────────────────────────────────────────────────────┘                  │
│                                                                                  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐         │
│  │ VITO      │ │ VARAPI    │ │ TUSO      │ │ MSIKA     │ │ ZIBO      │         │
│  │ Client Reg│ │ Provider  │ │ Facility  │ │ Product   │ │ Terminol. │         │
│  │ MPI (8082)│ │ Reg (8083)│ │ Reg (8084)│ │ Reg (8086)│ │ Gov (8085)│         │
│  │ CRID/CPID │ │ Licensure │ │ Topology  │ │ Catalogs  │ │ CodeSys   │         │
│  │ Merge     │ │ Privilege │ │ Ctrl Tower│ │ Tariffs   │ │ ValueSets │         │
│  │ Federation│ │ Revoke    │ │ Bookings  │ │ Packs     │ │ ConceptMap│         │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘         │
│                                                                                  │
│  ┌───────────┐ ┌───────────┐ ┌──────────────────────┐ ┌──────────────────────┐  │
│  │ BUTANO    │ │ UBOMI     │ │ MUSHEX               │ │ Platform Primitives  │  │
│  │ SHR/FHIR  │ │ CRVS      │ │ Finance/Claims (8087)│ │ ┌─────────────────┐ │  │
│  │ R4 (8090) │ │ Interface │ │ Payments, Settlement │ │ │ Schema Registry │ │  │
│  │ CPID-only │ │ Birth/    │ │ Fraud, Ledger        │ │ │ (NEW)           │ │  │
│  │ No PII    │ │ Death     │ │ Claims Switch        │ │ ├─────────────────┤ │  │
│  │ IPS/Visit │ │           │ │                      │ │ │ Vault KMS/HSM   │ │  │
│  │ Timeline  │ │           │ │                      │ │ │ (NEW)           │ │  │
│  └───────────┘ └───────────┘ └──────────────────────┘ │ ├─────────────────┤ │  │
│                                                        │ │ Dev Portal      │ │  │
│                                                        │ │ (NEW)           │ │  │
│                                                        │ └─────────────────┘ │  │
│                                                        └──────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────┼─────────────────────────────────────────────┐
│                    CLINICAL PLANE (II) — Ring 1                                  │
│                                                                                  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐         │
│  │ PCT       │ │ OROS      │ │ COSTA     │ │ Inpatient │ │ Scheduling│         │
│  │ Care Track│ │ Orders    │ │ Costing   │ │ Bed Mgmt  │ │ Capacity  │         │
│  │ (8088)    │ │ Results   │ │ Billing   │ │           │ │           │         │
│  │ Journey   │ │ (8089)    │ │ (8101)    │ │           │ │           │         │
│  │ Queue     │ │ Worklist  │ │ Tariff    │ │           │ │           │         │
│  │ Discharge │ │ SLA       │ │ Exemption │ │           │ │           │         │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘         │
│                                                                                  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐                                     │
│  │ Pharmacy  │ │ MSIKA Flow│ │ Referral/ │                                     │
│  │ Dispense  │ │ Market    │ │ Care Net  │                                     │
│  │ (8096)    │ │ (8100)    │ │ (future)  │                                     │
│  └───────────┘ └───────────┘ └───────────┘                                     │
└──────────────────────────────────────────────────────────────────────────────────┘
                                    │
┌───────────────────────────────────┼─────────────────────────────────────────────┐
│                    SUPPLY + DATA + INTEGRATION — Ring 2                           │
│                                                                                  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐         │
│  │ Inventory │ │ FHIR GW   │ │ Notif Hub │ │ Offline   │ │ Jobs      │         │
│  │ Supply    │ │ Interop   │ │           │ │ Sync      │ │ Scheduler │         │
│  │ (8098)    │ │           │ │           │ │           │ │           │         │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘         │
│                                                                                  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐                       │
│  │ Landela   │ │ CVS       │ │ Card Print│ │ Share Slip│                       │
│  │ Doc GW    │ │ Credential│ │ Agent     │ │           │                       │
│  │ (8092)    │ │ (8094)    │ │ (8091)    │ │ (8095)    │                       │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘                       │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Deployment Architecture (Federation Model)

```
┌─────────────────────────────────────────────────────────────┐
│                    NATIONAL SPINE (Level 1)                   │
│                                                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│  │   K8s        │  │   Kafka     │  │  PostgreSQL │          │
│  │   Cluster    │  │   Cluster   │  │  Cluster    │          │
│  │              │  │             │  │             │          │
│  │ Ring 0 + 1   │  │ Clinical    │  │ Per-service │          │
│  │ Services     │  │ Telemetry   │  │ Databases   │          │
│  │              │  │ Analytics   │  │             │          │
│  └──────┬───────┘  └──────┬──────┘  └─────────────┘          │
│         │                 │                                   │
│  ┌──────▼─────────────────▼──────┐                           │
│  │    Federation Control          │                           │
│  │    - Authority Table           │                           │
│  │    - High-Priority Control Ch. │                           │
│  │    - Reporting Obligations     │                           │
│  │    - Pod Registry              │                           │
│  └──────┬────────────────────────┘                           │
│         │                                                     │
└─────────┼─────────────────────────────────────────────────────┘
          │
          │  Federation Protocol
          │  (gRPC + Kafka trust.* topics)
          │
    ┌─────┼──────────────────────┐
    │     │                      │
    ▼     ▼                      ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────────┐
│ Sovereign   │  │ Sovereign   │  │  Export/Country  │
│ Pod A       │  │ Pod B       │  │  Kit (Level 3)   │
│ (Military)  │  │ (Private)   │  │                  │
│             │  │             │  │  Config-injected │
│ Full Stack  │  │ Full Stack  │  │  localization    │
│ Isolated    │  │ Isolated    │  │                  │
│             │  │             │  │  - ID formats    │
│ Authority:  │  │ Authority:  │  │  - Currency      │
│ - Clinical  │  │ - Clinical  │  │  - Locale        │
│   (local)   │  │   (local)   │  │  - Regulatory    │
│ - Identity  │  │ - Identity  │  │                  │
│   (national)│  │   (national)│  │                  │
└─────────────┘  └─────────────┘  └─────────────────┘
```

---

## 3. Event Bus Architecture (3-Bus Model)

```
┌────────────────────────────────────────────────────────────────────┐
│                     KAFKA CLUSTER                                   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  CLINICAL BUS (clinical.*)                                    │   │
│  │  Replication: 3  │  Min ISR: 2  │  Retention: 7d + archive  │   │
│  │  Ordering: per-entity (tenant_id + subject_id)                │   │
│  │                                                                │   │
│  │  Topics:                                                       │   │
│  │    clinical.pct.journey.*     clinical.oros.order.*            │   │
│  │    clinical.pct.encounter.*   clinical.oros.result.*           │   │
│  │    clinical.pct.triage.*      clinical.oros.workstep.*        │   │
│  │    clinical.butano.resource.* clinical.pharmacy.dispense.*    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  TRUST CHANNEL (trust.*)  — HIGH PRIORITY                    │   │
│  │  Replication: 3  │  Min ISR: 2  │  acks=all  │  Ret: 30d    │   │
│  │                                                                │   │
│  │  Topics:                                                       │   │
│  │    trust.revocation.consent    trust.federation.merge          │   │
│  │    trust.revocation.privilege  trust.federation.pod_registered │   │
│  │    trust.revocation.identity   trust.decision_evidence         │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  KERNEL BUS (kernel.*)                                        │   │
│  │  Replication: 3  │  Min ISR: 2  │  Retention: 14d + archive │   │
│  │                                                                │   │
│  │  Topics:                                                       │   │
│  │    kernel.vito.client.*       kernel.msika.catalog.*          │   │
│  │    kernel.varapi.provider.*   kernel.zibo.artifact.*          │   │
│  │    kernel.tuso.facility.*     kernel.mushex.payment.*         │   │
│  │    kernel.mushex.claim.*      kernel.costa.bill.*             │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  TELEMETRY BUS (telemetry.*)                                  │   │
│  │  Replication: 2  │  Retention: 30d  │  Compaction: enabled   │   │
│  │                                                                │   │
│  │  Topics:                                                       │   │
│  │    telemetry.tuso.occupancy.*  telemetry.pct.queue.metrics   │   │
│  │    telemetry.device.heartbeat  telemetry.iot.*                │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  ANALYTICS BUS (analytics.*)                                  │   │
│  │  Replication: 2  │  Retention: 90d  │  Compaction: enabled   │   │
│  │                                                                │   │
│  │  Topics:                                                       │   │
│  │    analytics.reporting.*       analytics.surveillance.*        │   │
│  │    analytics.ndr.*             analytics.fraud.*               │   │
│  └──────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────┘
```

---

## 4. Request Flow (v1.1 Compliant)

```
                    ┌───────────┐
                    │  Client   │
                    │  (UI/API) │
                    └─────┬─────┘
                          │
                          ▼
                    ┌───────────┐
                    │  Envoy    │
                    │  Gateway  │──────┐
                    └─────┬─────┘      │
                          │            │ ext_authz (gRPC)
                          │            ▼
                          │      ┌───────────────────┐
                          │      │ TSHEPO Authz       │
                          │      │                    │
                          │      │ 1. Authenticate    │
                          │      │ 2. Check Class     │
                          │      │    A/B/C           │
                          │      │ 3. If A: sync      │
                          │      │    Kernel check    │
                          │      │ 4. If B: staleness │
                          │      │    validation      │
                          │      │ 5. If C: entitle-  │
                          │      │    ment validation │
                          │      │ 6. Evaluate RBAC/  │
                          │      │    ABAC policy     │
                          │      │ 7. Log decision    │
                          │      │    evidence        │
                          │      │ 8. Return verdict  │
                          │      │    + obligations   │
                          │      └───────────────────┘
                          │            │
                          │◄───────────┘ (headers injected)
                          │
                          ▼
                    ┌───────────┐
                    │  Target   │
                    │  Service  │
                    │           │
                    │ TrustCtx  │──── Extract headers
                    │ Filter    │     (tenant, actor, pod,
                    │           │      correlation, class)
                    │           │
                    │ Business  │──── Execute domain logic
                    │ Logic     │
                    │           │
                    │ Outbox    │──── Persist event with
                    │ Write     │     v1.1 envelope fields
                    │           │
                    │ Audit     │──── Emit decision evidence
                    │ Event     │     to audit service
                    └───────────┘
```
