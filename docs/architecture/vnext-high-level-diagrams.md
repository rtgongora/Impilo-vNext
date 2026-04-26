# Impilo vNext — High-Level Architecture Diagrams

**Date**: 2026-03-28
**Version**: 1.0
**Status**: Living document — updated as architecture evolves

> **Convention**: Each diagram is labeled **CURRENT STATE**, **TARGET STATE**, or **MIXED**.
> Target-state elements not yet implemented are called out explicitly in the notes.

---

## 1. Platform Context Diagram

**State: MIXED**

```mermaid
graph TB
    subgraph External Actors
        CIT["👤 Citizen / Patient"]
        PRV["👨‍⚕️ Health Provider"]
        ADM["🔧 Platform Admin / Ops"]
        PAY["🏦 Payer / Insurer"]
        DEV["💻 Developer / Partner"]
        GOV["🏛️ Government / MOH"]
        EXT_SYS["🔗 External Systems<br/>(DHIS2, eLMIS, LIMS, iHRIS, MOSIP)"]
    end

    subgraph Entry Points
        ONE_UI["one-ui-shell :3000<br/>Primary Clinical UI"]
        EHR_UI["ehr :3002<br/>EHR Workspace"]
        PORTAL["portal :3003<br/>Citizen Portal"]
        MOB_CIT["citizen-app<br/>(React Native)"]
        MOB_PRV["provider-app<br/>(React Native)"]
        OPS["ops-console :3001"]
        DEV_CON["developer-console :3007"]
        FIN_CON["mushex-finance-console :3017"]
        API["External API Consumers"]
    end

    subgraph Platform Boundary
        ENVOY["Envoy Gateway :10000<br/>ext_authz → TSHEPO"]
        TSHEPO_CLUSTER["TSHEPO Trust Cluster<br/>(authz, identity, consent,<br/>audit, keys, offline)"]
        CORE_REG["Core Registries<br/>(VITO, VARAPI, TUSO,<br/>ZIBO, MSIKA, MUSHEX)"]
        CLINICAL["Clinical Services<br/>(PCT, OROS, Pharmacy,<br/>Costing, Inpatient)"]
        SHR["BUTANO / HAPI FHIR<br/>Shared Health Record"]
        SUPPORT_SVC["Support Services<br/>(Notifications, Workflow,<br/>Reporting, Search)"]
        KAFKA["Kafka Event Bus<br/>(5 bus categories)"]
    end

    subgraph Infrastructure
        KC["Keycloak :8080<br/>Identity Provider"]
        PG["PostgreSQL 16"]
        REDIS["Redis 7"]
        MINIO["MinIO :9000<br/>Object Storage"]
        ORTHANC["Orthanc :8042<br/>PACS/DICOM"]
    end

    CIT --> PORTAL & MOB_CIT
    PRV --> ONE_UI & EHR_UI & MOB_PRV
    ADM --> OPS
    PAY --> FIN_CON
    DEV --> DEV_CON & API
    GOV --> OPS

    ONE_UI & EHR_UI & PORTAL & MOB_CIT & MOB_PRV & OPS & DEV_CON & FIN_CON & API --> ENVOY

    ENVOY --> TSHEPO_CLUSTER
    ENVOY --> CORE_REG & CLINICAL & SHR & SUPPORT_SVC

    TSHEPO_CLUSTER --> KC
    CORE_REG --> KAFKA
    CLINICAL --> CORE_REG & SHR & KAFKA
    SUPPORT_SVC --> KAFKA

    CORE_REG & CLINICAL & SHR & SUPPORT_SVC --> PG & REDIS
    SHR --> ORTHANC

    EXT_SYS -.->|"Integration Hub<br/>(skeleton)"| ENVOY

    style EXT_SYS stroke-dasharray: 5 5
```

**Notes:**
- All UI and API traffic enters through **Envoy Gateway** with mandatory ext_authz to TSHEPO before reaching any service.
- The TSHEPO Trust Cluster (7 services) validates every request: authenticate, classify (A/B/C), evaluate RBAC/ABAC, inject 14 trust headers.
- Core registries (VITO, VARAPI, TUSO, ZIBO, MSIKA, MUSHEX) are Ring 0 — no outbound dependencies to Ring 1+.
- BUTANO (SHR) stores **CPID only** — PII remains in VITO.
- **Target-state elements**: Integration Hub (skeleton), channels-service (USSD/WhatsApp — skeleton), mobile apps (early stage), external system adapters (mostly skeleton).

---

## 2. Ring / Plane Architecture Diagram

**State: MIXED**

```mermaid
graph TB
    subgraph INFRA["Infrastructure Layer"]
        PG["PostgreSQL 16<br/>106 databases"]
        REDIS["Redis 7"]
        KAFKA["Kafka 3.7 (KRaft)<br/>5 bus categories"]
        MINIO["MinIO S3"]
        ORTHANC["Orthanc PACS"]
    end

    subgraph R0["Ring 0 — Kernel (19 services)"]
        subgraph TRUST["Trust & Governance Plane"]
            TSHEPO["TSHEPO Cluster<br/>authz · identity · consent<br/>audit · keys · offline"]
            KC["Keycloak 25.x"]
            OPA["OPA 0.68"]
        end
        subgraph REG["Registry Spine"]
            VITO["VITO :8082<br/>Client Registry / MPI"]
            VARAPI["VARAPI :8083<br/>Provider Registry"]
            TUSO["TUSO :8084<br/>Facility Registry"]
            ZIBO["ZIBO :8085<br/>Terminology"]
            MSIKA["MSIKA :8086<br/>Product & Service"]
        end
        subgraph FIN["Finance Plane"]
            MUSHEX["MUSHEX :8087<br/>Finance Engine"]
        end
        subgraph HR["Health Record"]
            BUTANO["BUTANO :8090<br/>HAPI FHIR R4 SHR"]
            BUTANO_SVC["butano-service<br/>SHR orchestration"]
        end
        subgraph R0_PLAT["Platform Primitives"]
            SCHEMA_REG["schema-registry-service ⬡"]
            DEV_PORTAL["developer-portal-service ⬡"]
            OBS_SVC["observability-service ⬡"]
        end
    end

    subgraph R1["Ring 1 — Clinical Execution (8 services)"]
        PCT["PCT :8088<br/>Patient Care Tracker"]
        OROS["OROS :8089<br/>Orders & Results"]
        COSTA["Costing Engine :8101"]
        PHARMA["Pharmacy :8096"]
        MSIKA_FLOW["MSIKA Flow :8100<br/>Health Marketplace"]
        INPATIENT["Inpatient :8093 ⬡"]
        COVERAGE["Coverage :8140"]
        INDAWO["Indawo :8150"]
    end

    subgraph R2["Ring 2 — Supply, Data, Integration (41 services)"]
        subgraph R2_SUPPLY["Supply Chain"]
            INV["Inventory :8098"]
            INV_ELMIS["Inventory eLMIS Adapter"]
            PHARMA_ELMIS["Pharmacy eLMIS Adapter"]
            DISPATCH["Dispatch ⬡"]
        end
        subgraph R2_DOC["Document & Credential"]
            DOC_SVC["Document Service"]
            CRED_VER["Credential Verification"]
            CARD_PRINT["Card Print Agent"]
            SHARE_SLIP["Share Slip"]
        end
        subgraph R2_INT["Integration & Interop"]
            INT_HUB["Integration Hub ⬡"]
            FHIR_GW["FHIR Gateway ⬡"]
            CONN_FHIR["Connector FHIR ⬡"]
            PACS_ADAPT["PACS Adapter ⬡"]
            CHANNELS["Channels ⬡"]
        end
        subgraph R2_DATA["Data & Analytics"]
            NDR["NDR ⬡"]
            DW["Data Warehouse ⬡"]
            PIPELINE["Data Pipeline ⬡"]
            REPORTING["Reporting ⬡"]
            SEARCH["Search ⬡"]
            SURV["Surveillance ⬡"]
        end
        subgraph R2_OPS["Ops & Workflow"]
            NOTIF["Notification ⬡"]
            WORKFLOW["Workflow ⬡"]
            JOBS["Jobs ⬡"]
            RULES["Rules ⬡"]
            FORMS["Forms ⬡"]
        end
    end

    subgraph EXP["Experience Layer (24 UIs + 2 Mobile Apps)"]
        SHELL["one-ui-shell :3000"]
        EHR_APP["ehr :3002"]
        PORTAL_APP["portal :3003"]
        OPS_APP["ops-console :3001"]
        EXP_BFF["experience-bff :8160"]
        MOBILE["citizen-app · provider-app"]
    end

    EXP --> |"Envoy :10000"| R0
    R1 --> |"REST / Kafka"| R0
    R2 --> |"REST / Kafka"| R0 & R1
    R0 --> INFRA
    R1 --> INFRA
    R2 --> INFRA

    style SCHEMA_REG stroke-dasharray: 5 5
    style DEV_PORTAL stroke-dasharray: 5 5
    style OBS_SVC stroke-dasharray: 5 5
    style INPATIENT stroke-dasharray: 5 5
    style INT_HUB stroke-dasharray: 5 5
    style FHIR_GW stroke-dasharray: 5 5
    style CONN_FHIR stroke-dasharray: 5 5
    style PACS_ADAPT stroke-dasharray: 5 5
    style CHANNELS stroke-dasharray: 5 5
    style NDR stroke-dasharray: 5 5
    style DW stroke-dasharray: 5 5
    style PIPELINE stroke-dasharray: 5 5
    style REPORTING stroke-dasharray: 5 5
    style SEARCH stroke-dasharray: 5 5
    style SURV stroke-dasharray: 5 5
    style NOTIF stroke-dasharray: 5 5
    style WORKFLOW stroke-dasharray: 5 5
    style JOBS stroke-dasharray: 5 5
    style RULES stroke-dasharray: 5 5
    style FORMS stroke-dasharray: 5 5
    style DISPATCH stroke-dasharray: 5 5
```

**Notes:**
- **Ring 0 (Kernel)** has zero outbound dependencies to Ring 1+. All registries, trust, finance, and health record live here.
- **Ring 1 (Clinical)** depends on Ring 0 only — PCT, OROS, Pharmacy, Costing, Marketplace, Coverage.
- **Ring 2 (Supply/Data/Integration)** depends on Ring 0 + Ring 1 — supply chain, analytics, interoperability, workflow.
- **⬡ = skeleton** services that exist in the repo but have only scaffolding (no business logic yet).
- Dashed-border nodes are skeleton/target-state. Of 68 backend services, ~33 are implemented and ~32 are skeleton.
- Dependency rule enforcement: Ring 0 → nothing outbound; Ring 1 → Ring 0 only; Ring 2 → Ring 0 + Ring 1.

---

## 3. Major Component Landscape Diagram

**State: CURRENT STATE**

```mermaid
graph LR
    subgraph TRUST_GOV["Trust & Governance"]
        T1["tshepo-authz-service"]
        T2["tshepo-identity-service"]
        T3["tshepo-consent-service"]
        T4["tshepo-audit-service"]
        T5["tshepo-keys-service"]
        T6["tshepo-offline-service"]
        T7["tshepo-service (legacy)"]
    end

    subgraph REGISTRY["Registry Spine"]
        R1["vito-service<br/>Client/MPI"]
        R2["varapi-service<br/>Provider"]
        R3["tuso-service<br/>Facility"]
        R4["zibo-service<br/>Terminology"]
        R5["msika-service<br/>Product Catalog"]
    end

    subgraph CLINICAL["Clinical Execution"]
        C1["pct-service<br/>Care Tracker"]
        C2["oros-service<br/>Orders & Results"]
        C3["pharmacy-service<br/>Dispensing"]
        C4["costing-engine-service<br/>Billing"]
        C5["msika-flow-service<br/>Marketplace"]
        C6["coverage-service"]
        C7["indawo-service<br/>Location"]
    end

    subgraph FINANCE["Finance"]
        F1["mushex-service<br/>Payments · Claims · Settlement"]
    end

    subgraph HEALTH_RECORD["Health Record (SHR)"]
        H1["butano-service<br/>Orchestration"]
        H2["butano-fhir<br/>HAPI FHIR R4"]
        H3["ubomi-service<br/>CRVS"]
    end

    subgraph SUPPLY["Supply Chain"]
        S1["inventory-service"]
        S2["inventory-elmis-adapter"]
        S3["pharmacy-elmis-adapter"]
        S4["product-registry-service"]
    end

    subgraph DOC_CRED["Document & Credential"]
        D1["document-service"]
        D2["landela-adapter-service"]
        D3["credential-verification-service"]
        D4["card-print-agent"]
        D5["share-slip-service"]
    end

    subgraph LIBS["Shared Libraries"]
        L1["shared-kernel-java"]
        L2["shared-kernel (TS)"]
        L3["tshepo-contracts"]
        L4["tshepo-sdk"]
        L5["tech-companion"]
        L6["ops-instrumentation"]
        L7["security-baseline"]
        L8["offline-sdk"]
        L9["federation-connector"]
    end

    subgraph UI_APPS["UI Applications (Next.js)"]
        U1["one-ui-shell :3000"]
        U2["ehr :3002"]
        U3["ops-console :3001"]
        U4["experience :3000"]
        U5["pct-web · oros-web · pharmacy-web"]
        U6["inventory-web · msika-web"]
        U7["mushex-finance-console · mushex-ops-console"]
        U8["zibo-web · butano-web"]
        U9["developer-console · support-console"]
        U10["msika-flow-ops · msika-flow-portal · msika-flow-vendor"]
    end

    subgraph MOBILE["Mobile Apps (React Native)"]
        M1["citizen-app"]
        M2["provider-app"]
    end

    subgraph INFRA_COMP["Infrastructure"]
        I1["PostgreSQL 16"]
        I2["Redis 7"]
        I3["Kafka 3.7 (KRaft)"]
        I4["Keycloak 25.x"]
        I5["Envoy 1.31"]
        I6["OPA 0.68"]
        I7["HAPI FHIR 7.4"]
        I8["MinIO"]
        I9["Orthanc PACS"]
    end

    UI_APPS & MOBILE -->|"via Envoy"| TRUST_GOV
    TRUST_GOV --> REGISTRY & CLINICAL & FINANCE & HEALTH_RECORD & SUPPLY & DOC_CRED
    CLINICAL --> REGISTRY & HEALTH_RECORD & FINANCE
    SUPPLY --> CLINICAL & REGISTRY
    LIBS -.->|"consumed by"| TRUST_GOV & REGISTRY & CLINICAL & FINANCE & HEALTH_RECORD & SUPPLY & DOC_CRED & UI_APPS
```

**Notes:**
- This diagram shows **only implemented components** from the repo — no skeleton services are included.
- The landscape is organized by functional domain, matching the repo directory structure under `services/`, `ui/`, `apps/mobile/`, and `libs/`.
- All backend services are Java 21 + Spring Boot 3.3; all UIs are Next.js 14.2 + TypeScript 5.5.
- Shared libraries (9 Java, 3 TypeScript) provide cross-cutting concerns: trust contracts, compliance enforcement, observability, security.
- The `experience-bff` aggregation layer sits between UIs and backend services.

---

## 4. Runtime Layer / Startup Order Diagram

**State: CURRENT STATE**

```mermaid
graph TB
    subgraph L1_INFRA["Layer 1 — Infrastructure (start first)"]
        direction LR
        PG["PostgreSQL 16 :5432"]
        REDIS["Redis 7 :6379"]
        KAFKA["Kafka 3.7 :9092<br/>(KRaft, no ZooKeeper)"]
        MINIO["MinIO :9000"]
        ORTHANC["Orthanc :8042"]
    end

    subgraph L2_IDP["Layer 2 — Identity Provider"]
        KC["Keycloak 25.x :8080<br/>depends: PostgreSQL"]
    end

    subgraph L3_POLICY["Layer 3 — Policy Engine"]
        OPA["OPA 0.68 :8181"]
    end

    subgraph L4_TRUST["Layer 4 — Ring 0: Trust Cluster (TSHEPO)"]
        direction LR
        KEYS["tshepo-keys :8184"]
        IDENTITY["tshepo-identity :8181"]
        CONSENT["tshepo-consent :8182"]
        AUDIT["tshepo-audit :8183"]
        AUTHZ["tshepo-authz :8081"]
        OFFLINE["tshepo-offline :8185"]
    end

    subgraph L5_GATEWAY["Layer 5 — API Gateway"]
        ENVOY["Envoy 1.31 :10000<br/>ext_authz → TSHEPO"]
    end

    subgraph L6_REG["Layer 6 — Ring 0: Core Registries"]
        direction LR
        VITO["VITO :8082"]
        VARAPI["VARAPI :8083"]
        TUSO["TUSO :8084"]
        ZIBO["ZIBO :8085"]
        MSIKA["MSIKA :8086"]
        MUSHEX["MUSHEX :8087"]
        BUTANO["BUTANO FHIR :8090"]
    end

    subgraph L7_CLINICAL["Layer 7 — Ring 1: Clinical Services"]
        direction LR
        PCT["PCT :8088"]
        OROS["OROS :8089"]
        PHARMA["Pharmacy :8096"]
        COSTA["Costing :8101"]
        MSIKA_FL["MSIKA Flow :8100"]
        COV["Coverage :8140"]
    end

    subgraph L8_R2["Layer 8 — Ring 2: Supply & Integration"]
        direction LR
        INV["Inventory :8098"]
        DOC["Document Service"]
        CRED["Credential Verification"]
        CARD["Card Print Agent"]
    end

    subgraph L9_BFF["Layer 9 — BFF & Edge"]
        EXP_BFF["experience-bff :8160"]
    end

    subgraph L10_UI["Layer 10 — UI Applications"]
        direction LR
        SHELL["one-ui-shell :3000"]
        EHR_UI["ehr :3002"]
        OPS_UI["ops-console :3001"]
        PORTAL_UI["portal :3003"]
        EXP_UI["experience :3000"]
        OTHER_UI["14 more UIs..."]
    end

    L1_INFRA --> L2_IDP --> L3_POLICY --> L4_TRUST --> L5_GATEWAY --> L6_REG --> L7_CLINICAL --> L8_R2 --> L9_BFF --> L10_UI

    AUTHZ -.->|"CPID resolution"| IDENTITY
    AUTHZ -.->|"consent eval"| CONSENT
    AUTHZ -.->|"token signing"| KEYS
    AUTHZ -.->|"decision log"| AUDIT
    ENVOY -.->|"ext_authz gRPC"| AUTHZ
```

**Notes:**
- Startup order reflects Docker Compose `depends_on` and service health dependencies.
- **Layer 1** (PostgreSQL, Redis, Kafka, MinIO, Orthanc) must be healthy before any service starts.
- **Layer 2** (Keycloak) depends on PostgreSQL for its database.
- **Layers 3–4** (OPA + TSHEPO cluster) must be up before Envoy can authorize requests.
- **Layer 5** (Envoy) must have TSHEPO available for ext_authz — no request passes without it.
- **Layers 6–8** follow ring dependency order: registries first, then clinical, then supply/integration.
- **Layer 9–10** (BFF + UIs) start last as they depend on backend services being ready.
- `platformctl.sh up lite` starts infrastructure + Ring 0; `platformctl.sh up full` starts all layers.

---

## 5. Experience / App Ecosystem Diagram

**State: MIXED**

```mermaid
graph TB
    subgraph SHARED_FOUND["Shared Foundations"]
        SHARED_UI["shared-ui<br/>React component library<br/>Radix · Tailwind · Forms"]
        SHARED_KERNEL_TS["shared-kernel (TS)<br/>Compliance primitives"]
        API_CLIENT["apiClient.ts<br/>Trust header injection"]
    end

    subgraph WEB_APPS["Web Applications (Next.js 14.2)"]
        subgraph CLINICAL_UIS["Clinical Experience"]
            SHELL["one-ui-shell :3000<br/>3-zone layout · module federation"]
            EHR_APP["ehr :3002<br/>Clinical workspace"]
            PCT_WEB["pct-web :3021<br/>Queue · Triage · Encounters"]
            OROS_WEB["oros-web :3009<br/>Worklists · Order entry"]
            PHARMA_WEB["pharmacy-web :3010<br/>Dispensing · Stock"]
            EXP_APP["experience :3000<br/>Unified clinical experience"]
        end

        subgraph SUPPLY_UIS["Supply & Marketplace"]
            INV_WEB["inventory-web :3011"]
            MSIKA_WEB["msika-web :3012"]
            MSIKA_FL_OPS["msika-flow-ops :3014"]
            MSIKA_FL_PORT["msika-flow-portal :3012"]
            MSIKA_FL_VEND["msika-flow-vendor :3013"]
        end

        subgraph FINANCE_UIS["Finance"]
            MX_FIN["mushex-finance-console :3017"]
            MX_OPS["mushex-ops-console :3018"]
            COSTA_CON["costa-console"]
        end

        subgraph GOV_UIS["Governance & Terminology"]
            ZIBO_WEB["zibo-web :3008"]
            BUTANO_WEB["butano-web :3006"]
        end

        subgraph PLATFORM_UIS["Platform & Support"]
            OPS_CON["ops-console :3001"]
            DEV_CON["developer-console :3007"]
            SUPPORT_CON["support-console :3019"]
            OPS_DOCS["ops-docs :3004"]
            SELF_SVC["self-service :3005"]
        end
    end

    subgraph MOBILE_APPS["Mobile Apps (React Native)"]
        CIT_APP["citizen-app<br/>Health records · Appointments<br/>Offline-capable"]
        PRV_APP["provider-app<br/>Clinical tasks · Offline visits"]
        subgraph MOB_PKGS["Mobile Shared Packages"]
            MOB_DS["mobile-design-system"]
            MOB_API["mobile-api-client"]
            MOB_AUTH["mobile-auth"]
            MOB_TRUST["mobile-trust"]
            MOB_OFFLINE["mobile-offline"]
            MOB_MSG["mobile-messaging"]
            MOB_TL["mobile-timeline"]
        end
    end

    subgraph BACKEND_DEPS["Key Backend Dependencies"]
        ENVOY["Envoy :10000"]
        BFF["experience-bff :8160"]
        TSHEPO["TSHEPO Trust Cluster"]
        REGISTRIES["VITO · VARAPI · TUSO · ZIBO · MSIKA"]
        CLIN_SVC["PCT · OROS · Pharmacy · Costing"]
        FIN_SVC["MUSHEX · Coverage"]
        SHR["BUTANO / HAPI FHIR"]
    end

    SHARED_FOUND -.->|"imported by"| WEB_APPS
    SHARED_FOUND -.->|"patterns shared"| MOBILE_APPS
    MOB_PKGS -.->|"imported by"| CIT_APP & PRV_APP

    WEB_APPS -->|"HTTPS"| ENVOY
    MOBILE_APPS -->|"HTTPS"| ENVOY
    ENVOY -->|"ext_authz"| TSHEPO
    ENVOY --> BFF & REGISTRIES & CLIN_SVC & FIN_SVC & SHR

    style CIT_APP stroke-dasharray: 5 5
    style PRV_APP stroke-dasharray: 5 5
    style MOB_PKGS stroke-dasharray: 5 5
```

**Notes:**
- **24 web UIs** exist in the `ui/` directory, all using Next.js 14.2 + TypeScript + TailwindCSS + Radix + TanStack Query + Zustand.
- **`one-ui-shell`** is the primary entry point — a 3-zone layout (Work/Pro/Life) that acts as module federation host.
- **`shared-ui`** provides the shared component library (Radix primitives, form components, design tokens).
- **`experience`** is a unified clinical experience app with its own BFF (`experience-bff`).
- **Mobile apps** (citizen-app + provider-app) are React Native with 7 shared packages under `apps/mobile/packages/`.
- **Target-state elements**: Mobile apps exist in the repo with directory structure and shared packages, but are early-stage (dashed border). Most web UIs have implemented scaffolding.
- All UIs inject trust headers via `apiClient.ts` — every request flows through Envoy → TSHEPO before reaching backend services.

---

## Appendix: Diagram Legend

| Symbol | Meaning |
|--------|---------|
| Solid border | Implemented — has business logic |
| Dashed border / ⬡ | Skeleton — repo scaffolding only, no business logic |
| Solid arrow | Synchronous dependency (REST/gRPC) |
| Dashed arrow | Asynchronous or indirect dependency (Kafka events, library import) |
| `:NNNN` | Local development port assignment |

---

## References

- [Component Catalog](vnext-component-catalog.md) — full inventory of all 68 services, 24 UIs, 12 libraries
- [Service Dependency Map](vnext-service-dependency-map.md) — detailed inter-service dependency graph
- [Platform Operations Runbook](../runtime/platform-operations-runbook.md) — platformctl startup/shutdown
