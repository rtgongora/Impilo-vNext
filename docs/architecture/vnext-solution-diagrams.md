# Impilo vNext Solution In Diagrams

**Status:** Living document  
**Audience:** Ministry leadership, data centre engineers, platform engineers, implementation partners, security reviewers  
**Purpose:** Explain the Impilo vNext Health Operating System as an end-to-end solution through diagrams.

This document is intentionally solution-oriented. It shows how people, applications, trust services, registries, clinical workflows, data stores, eventing, deployment, and operations fit together.

Related documents:

- [Health OS doctrine](../doctrine/health-os-doctrine.md)
- [High-level architecture diagrams](vnext-high-level-diagrams.md)
- [Service dependency map](vnext-service-dependency-map.md)
- [Component catalog (full inventory)](vnext-component-catalog.md)
- [Data centre sandbox deployment guide](../deployment/data-centre-sandbox-deployment.md)
- [Service classification matrix](../deployment/service-classification-matrix.md)
- [Data centre enforcement gates](../acceptance/data-centre-enforcement-gates.md)

---

## 0. vNext at a glance (components + layers)

This diagram is meant for readers who have never seen vNext before. It answers three questions quickly:

- **What do users touch?** The experience surfaces (web + mobile) they use every day.
- **What enforces safety?** The gateway + trust layer that governs every request.
- **How is the platform layered?** Ring 0 (kernel), Ring 1 (clinical), Ring 2 (integration/data/supply) sitting on shared infrastructure.

```mermaid
flowchart TB
    subgraph People["People"]
        Citizen["Citizen"]
        Provider["Provider"]
        Ops["Admin/Ops"]
        Partner["Partner/Dev"]
        Regulator["Ministry/Regulator"]
    end

    subgraph Experience["Experience surfaces (UI)"]
        OneUIShell["one-ui-shell"]
        CitizenPortal["portal"]
        MobileApps["citizen-app / provider-app"]
        OpsConsoles["ops-console / developer-console"]
        ExperienceBff["experience-bff"]
    end

    subgraph GovernanceEntry["Governance entry"]
        Envoy["Envoy Gateway (ext_authz)"]
        TShepoAuthz["TSHEPO Authz (+ OPA where used)"]
        Keycloak["Keycloak (OIDC)"]
        TrustHeaders["Trust context headers"]
    end

    subgraph Ring0["Ring 0 — Kernel (authoritative primitives)"]
        subgraph TrustCluster["Trust & governance (TSHEPO cluster)"]
            TShepoIdentity["tshepo-identity-service"]
            TShepoConsent["tshepo-consent-service"]
            TShepoAudit["tshepo-audit-service"]
            TShepoKeys["tshepo-keys-service"]
            TShepoOffline["tshepo-offline-service"]
        end

        subgraph Registries["Registry spine"]
            Vito["vito-service (person/MPI)"]
            Varapi["varapi-service (providers)"]
            Tuso["tuso-service (facilities)"]
            Zibo["zibo-service (terminology)"]
            Msika["msika-service (products/services)"]
        end

        subgraph SharedRecordFinance["Shared record + finance"]
            ButanoSvc["butano-service (SHR orchestration)"]
            ButanoFhir["butano-fhir (HAPI FHIR)"]
            Mushex["mushex-service (finance engine)"]
        end
    end

    subgraph Ring1["Ring 1 — Clinical execution (depends on Ring 0)"]
        Pct["pct-service (care journeys)"]
        Oros["oros-service (orders/results)"]
        Pharmacy["pharmacy-service"]
        Costa["costing-engine-service (COSTA)"]
        Coverage["coverage-service"]
    end

    subgraph Ring2["Ring 2 — Integration, data, supply (depends on Ring 0 + Ring 1)"]
        Integration["integration-hub (adapters)"]
        OfflineSync["offline-sync-service"]
        DocumentSuite["document-service / credential-verification / share-slip"]
        Inventory["inventory-service (+ eLMIS adapters)"]
        DataPlane["reporting / pipeline / warehouse / ndr"]
        NotifyFlow["notification / workflow / jobs / rules / forms"]
    end

    subgraph Foundations["Shared foundations"]
        Postgres["PostgreSQL (per-service databases)"]
        Kafka["Kafka (trust.* / kernel.* / clinical.* / finance.* / analytics.*)"]
        Redis["Redis"]
        ObjectStorage["Object storage (documents)"]
        Imaging["PACS storage (imaging)"]
        Obs["Observability/SecOps"]
    end

    People --> Experience
    Experience --> Envoy
    Envoy --> TShepoAuthz
    TShepoAuthz --> Keycloak
    TShepoAuthz --> TrustHeaders

    TrustHeaders --> Ring0
    TrustHeaders --> Ring1
    TrustHeaders --> Ring2

    Ring1 --> Registries
    Ring1 --> SharedRecordFinance
    Ring2 --> Registries
    Ring2 --> Ring1

    Ring0 --> Foundations
    Ring1 --> Foundations
    Ring2 --> Foundations
```

**How to read this:**

- **All requests** enter through **Envoy** and must pass **TSHEPO authorization** before reaching any service.
- **Ring 0** is the kernel: trust + registries + shared record primitives that everything else relies on.
- **Ring 1** is clinical execution: care journeys, orders/results, dispensing, costing, coverage.
- **Ring 2** adds integration, offline, supply chain, and analytics workloads without breaking Ring 0 authority.

---

## 1. Solution Context

Impilo vNext is a Health Operating System, not a single app. It provides a governed runtime for many roles, many facilities, many workflows, and many applications anchored on one person identity model.

```mermaid
flowchart TB
    subgraph PEOPLE["People and organisations"]
        CIT["Citizen / patient"]
        PROV["Provider"]
        FAC["Facility team"]
        ADMIN["Platform administrator"]
        MOH["Ministry / regulator"]
        PARTNER["Partner / developer"]
    end

    subgraph EXPERIENCE["Experience surfaces"]
        SHELL["One UI Shell"]
        EHR["EHR workspace"]
        PORTAL["Citizen portal"]
        MOBILE["Citizen and provider mobile apps"]
        OPS["Ops / support / developer consoles"]
        API["Partner APIs"]
    end

    subgraph HOS["Impilo vNext Health OS"]
        GATEWAY["Gateway and policy enforcement"]
        TRUST["Trust, identity, consent, audit"]
        REGISTRY["Registry spine"]
        CLINICAL["Clinical execution"]
        FINANCE["Finance and benefits"]
        INTEGRATION["Integration and offline"]
        DATA["Data, analytics, reporting"]
    end

    subgraph FOUNDATIONS["Platform foundations"]
        DB["PostgreSQL"]
        KAFKA["Kafka event bus"]
        REDIS["Redis"]
        FHIR["FHIR / shared health record"]
        OBJ["Object and imaging storage"]
        OBS["Observability and SecOps"]
    end

    PEOPLE --> EXPERIENCE
    EXPERIENCE --> GATEWAY
    GATEWAY --> TRUST
    GATEWAY --> REGISTRY
    GATEWAY --> CLINICAL
    GATEWAY --> FINANCE
    GATEWAY --> INTEGRATION
    GATEWAY --> DATA
    TRUST --> DB
    REGISTRY --> DB
    CLINICAL --> DB
    FINANCE --> DB
    INTEGRATION --> KAFKA
    DATA --> DB
    CLINICAL --> FHIR
    CLINICAL --> OBJ
    HOS --> KAFKA
    HOS --> REDIS
    HOS --> OBS
```

**Key point:** users do not connect directly to services or databases. They use governed experience surfaces and APIs that enter through the platform gateway.

---

## 2. Architectural Planes

The platform is organised into planes. Each plane owns a different kind of responsibility and should be deployed as independently scalable workloads.

```mermaid
flowchart TB
    subgraph TRUST["Trust and governance"]
        TSHEPO["TSHEPO policy, consent, identity, audit, keys"]
        KEYCLOAK["Keycloak identity provider"]
        OPA["OPA policy engine where used"]
    end

    subgraph REGISTRY["Registry spine"]
        VITO["VITO client registry"]
        VARAPI["VARAPI provider registry"]
        TUSO["TUSO facility registry"]
        ZIBO["ZIBO terminology"]
        MSIKA["MSIKA product and service registry"]
    end

    subgraph CLINICAL["Clinical execution"]
        PCT["PCT care tracker"]
        OROS["OROS orders and results"]
        PHARMACY["Pharmacy"]
        INPATIENT["Inpatient"]
        WELLNESS["Wellness"]
        DOCS["Document service"]
    end

    subgraph SHR["FHIR and shared record"]
        BUTANO["BUTANO / HAPI FHIR"]
        FHIRGW["FHIR Gateway"]
        PACS["PACS / imaging adapter"]
    end

    subgraph FINANCE["Finance"]
        COSTA["Costing engine"]
        MUSHEX["MUSheX"]
        COVERAGE["Coverage"]
        GL["General ledger / HR / procurement"]
    end

    subgraph INTEGRATION["Integration and operations"]
        HUB["Integration Hub"]
        NOTIFY["Notification"]
        JOBS["Jobs"]
        OFFLINE["Offline sync and edge"]
        ADAPTERS["External adapters"]
    end

    subgraph EXPERIENCE["Experience"]
        BFF["Experience BFF"]
        UI["Web and mobile applications"]
    end

    EXPERIENCE --> TRUST
    EXPERIENCE --> REGISTRY
    EXPERIENCE --> CLINICAL
    CLINICAL --> REGISTRY
    CLINICAL --> SHR
    CLINICAL --> FINANCE
    INTEGRATION --> REGISTRY
    INTEGRATION --> CLINICAL
    FINANCE --> REGISTRY
```

**Key point:** the registry spine and trust layer are not optional helpers. They are core runtime infrastructure for the Health OS.

---

## 3. Request Governance Flow

Every protected request must be governed before it reaches a service.

```mermaid
sequenceDiagram
    autonumber
    participant User as User or client app
    participant UI as UI / Experience BFF
    participant GW as Envoy Gateway
    participant Authz as TSHEPO Authz / OPA
    participant KC as Keycloak
    participant Svc as Target service
    participant Audit as TSHEPO Audit
    participant DB as Service database
    participant Bus as Kafka

    User->>UI: Start workflow
    UI->>GW: API request with session/context
    GW->>Authz: ext_authz decision request
    Authz->>KC: Validate identity token when required
    Authz->>Authz: Evaluate role, purpose, consent, facility, workflow
    Authz->>Audit: Record decision evidence
    Authz-->>GW: Allow or deny + trust context
    GW->>Svc: Forward authorized request with trust headers
    Svc->>Svc: Validate trust context
    Svc->>DB: Read/write own data only
    Svc->>Bus: Publish via outbox pattern
    Svc-->>GW: Response
    GW-->>UI: Governed response
    UI-->>User: Workflow result
```

**Key point:** services do not make policy shortcuts. Trust, consent, audit, and context are part of the runtime contract.

---

## 4. Person, Provider, Facility, And Record Model

Impilo separates identity, context, and clinical record data.

```mermaid
flowchart LR
    subgraph PERSON["Person identity"]
        VITO["VITO<br/>client identity and PII"]
        CPID["CPID<br/>clinical person anchor"]
    end

    subgraph PROVIDER["Provider identity"]
        VARAPI["VARAPI<br/>provider registry"]
        PRIV["Privileges, cadres, councils"]
    end

    subgraph FACILITY["Facility context"]
        TUSO["TUSO<br/>facility, department, ward, workspace"]
        WORK["Operational workspace context"]
    end

    subgraph GOVERNANCE["Governance"]
        CONSENT["Consent"]
        POLICY["RBAC / ABAC policy"]
        AUDIT["Audit evidence"]
    end

    subgraph RECORD["Clinical record"]
        BUTANO["BUTANO / FHIR<br/>clinical resources keyed by CPID"]
        PCT["PCT encounters and care journeys"]
        OROS["Orders and results"]
    end

    VITO --> CPID
    VARAPI --> PRIV
    TUSO --> WORK
    CPID --> BUTANO
    PRIV --> POLICY
    WORK --> POLICY
    CONSENT --> POLICY
    POLICY --> PCT
    POLICY --> OROS
    PCT --> BUTANO
    OROS --> BUTANO
    POLICY --> AUDIT
```

**Key point:** PII belongs in identity/registry services such as VITO. BUTANO/FHIR stores clinical resources against the approved person anchor, not raw demographic PII.

---

## 5. Clinical Workflow Example

This diagram shows a typical care flow from login to encounter, orders, record update, and audit/event publication.

```mermaid
flowchart TB
    START["Provider opens One UI Shell"]
    LOGIN["Authenticate and select role/facility/workspace"]
    AUTHZ["TSHEPO evaluates authorization"]
    LOOKUP["Patient search through VITO"]
    CONTEXT["Facility and workspace context from TUSO"]
    ENCOUNTER["PCT creates or resumes encounter"]
    ORDERS["OROS creates orders/results"]
    DISPENSE["Pharmacy dispenses when applicable"]
    FHIR["BUTANO/HAPI FHIR stores clinical resources"]
    BILL["COSTA/MUSheX/Coverage handle costing and benefits"]
    AUDIT["Audit and outbox events"]
    DASH["Dashboards, reporting, notifications"]

    START --> LOGIN
    LOGIN --> AUTHZ
    AUTHZ --> LOOKUP
    AUTHZ --> CONTEXT
    LOOKUP --> ENCOUNTER
    CONTEXT --> ENCOUNTER
    ENCOUNTER --> ORDERS
    ORDERS --> DISPENSE
    ENCOUNTER --> FHIR
    ORDERS --> FHIR
    DISPENSE --> FHIR
    ENCOUNTER --> BILL
    ORDERS --> BILL
    DISPENSE --> BILL
    ENCOUNTER --> AUDIT
    ORDERS --> AUDIT
    DISPENSE --> AUDIT
    AUDIT --> DASH
```

**Key point:** clinical workflows are not just UI screens. They are governed transactions across identity, facility context, care services, FHIR, finance, audit, and eventing.

---

## 6. Data And Eventing Model

Services own their data. Cross-service integration happens through governed APIs and reliable events.

```mermaid
flowchart LR
    subgraph SERVICES["Service-owned data"]
        S1["VITO service"]
        D1["vito database"]
        O1["event_outbox"]

        S2["PCT service"]
        D2["pct database"]
        O2["event_outbox"]

        S3["OROS service"]
        D3["oros database"]
        O3["event_outbox"]
    end

    subgraph EVENTBUS["Kafka event bus"]
        TRUST["trust.*"]
        KERNEL["kernel.*"]
        CLINICAL["clinical.*"]
        FINANCE["finance.*"]
        ANALYTICS["analytics.*"]
        AUDIT["audit.*"]
    end

    subgraph CONSUMERS["Consumers"]
        NOTIF["Notification"]
        REPORT["Reporting"]
        PIPE["Data pipeline"]
        SEARCH["Search"]
        OFFLINE["Offline sync"]
    end

    S1 --> D1
    S1 --> O1
    O1 --> KERNEL

    S2 --> D2
    S2 --> O2
    O2 --> CLINICAL

    S3 --> D3
    S3 --> O3
    O3 --> CLINICAL

    TRUST --> CONSUMERS
    KERNEL --> CONSUMERS
    CLINICAL --> CONSUMERS
    FINANCE --> CONSUMERS
    ANALYTICS --> CONSUMERS
    AUDIT --> CONSUMERS
```

**Key point:** no service should read another service database directly. Events are published reliably through the outbox pattern.

---

## 7. Experience Shell Model

The user experience is one governed shell with multiple role and workspace surfaces.

```mermaid
flowchart TB
    SHELL["One UI Shell"]

    subgraph MODES["Experience modes"]
        WORK["WORK"]
        EHR["EHR"]
        CONTROL["CONTROL"]
        PROF["MY PROFESSIONAL"]
        LIFE["MY LIFE"]
    end

    subgraph CONTEXT["Runtime context"]
        ROLE["Active role"]
        FAC["Facility"]
        DEPT["Department / ward"]
        WS["Workspace"]
        PURPOSE["Purpose of use"]
        ASSURANCE["Assurance level"]
    end

    subgraph BACKEND["Backend access"]
        BFF["Experience BFF"]
        GW["Gateway"]
        SERVICES["Governed platform services"]
    end

    SHELL --> MODES
    SHELL --> CONTEXT
    MODES --> BFF
    CONTEXT --> BFF
    BFF --> GW
    GW --> SERVICES
```

**Key point:** the shell is not just navigation. It carries operational context that affects authorization, workflow shape, audit, and data visibility.

---

## 8. Data Centre Sandbox Runtime

The data centre sandbox deploys the same solution as independent workloads with node pools and enforcement gates.

```mermaid
flowchart TB
    subgraph EDGE["Gateway / ingress node pool"]
        INGRESS["Ingress controller"]
        ENVOY["Envoy Gateway"]
        RATE["Rate limiting"]
    end

    subgraph TRUSTPOOL["Trust / security node pool"]
        KEYCLOAK["Keycloak"]
        TSHEPO["TSHEPO services"]
        OPA["OPA"]
    end

    subgraph REGPOOL["Registry spine node pool"]
        VITO["VITO"]
        VARAPI["VARAPI"]
        TUSO["TUSO"]
        ZIBO["ZIBO"]
    end

    subgraph CLINPOOL["Clinical execution node pool"]
        PCT["PCT"]
        OROS["OROS"]
        PHARM["Pharmacy"]
        INP["Inpatient"]
        WELL["Wellness"]
    end

    subgraph FHIRPOOL["FHIR / SHR node pool"]
        HAPI["HAPI FHIR"]
        BUTANO["BUTANO"]
        FHIRGW["FHIR Gateway"]
    end

    subgraph STATE["Dedicated stateful infrastructure"]
        PG["PostgreSQL HA"]
        KAFKA["Kafka HA"]
        REDIS["Redis HA"]
        OBJ["Object storage"]
        PACS["PACS storage"]
    end

    subgraph OBS["Observability / SecOps node pool"]
        PROM["Prometheus"]
        LOGS["Loki/OpenSearch"]
        TRACES["Tempo/Jaeger"]
        GRAF["Grafana"]
        ALERT["Alertmanager"]
    end

    subgraph EXPPOOL["Experience node pool"]
        UI["UI apps"]
        BFF["Experience BFF"]
    end

    UI --> ENVOY
    BFF --> ENVOY
    ENVOY --> TSHEPO
    ENVOY --> REGPOOL
    ENVOY --> CLINPOOL
    ENVOY --> FHIRPOOL
    TRUSTPOOL --> STATE
    REGPOOL --> STATE
    CLINPOOL --> STATE
    FHIRPOOL --> STATE
    EXPPOOL --> OBS
    TRUSTPOOL --> OBS
    REGPOOL --> OBS
    CLINPOOL --> OBS
    FHIRPOOL --> OBS
```

**Key point:** Docker Compose is local/dev bootstrap. The data centre sandbox is Kubernetes/OpenShift with node pools, HA stateful services, network policy, GitOps, observability, and acceptance gates.

---

## 9. Minimum Viable Sandbox Phase

Phase 1 is the first production-shaped sandbox that can produce meaningful evidence before the full target estate exists.

```mermaid
flowchart LR
    subgraph PHASE1["Phase 1 minimum viable sandbox"]
        W["6-8 Kubernetes/OpenShift workers"]
        DB["3 PostgreSQL HA nodes"]
        K["3 Kafka brokers"]
        R["3 Redis nodes"]
        S3["4 object storage nodes or managed S3"]
        OBS["2-3 observability/SecOps nodes"]
        CICD["2 CI/CD runner nodes"]
    end

    subgraph PROVES["What Phase 1 proves"]
        P1["Gateway-only access"]
        P2["TSHEPO/OPA enforcement"]
        P3["Trust header propagation"]
        P4["Ring 0 registry and trust path"]
        P5["Clinical and FHIR control path"]
        P6["Outbox/Kafka and audit"]
        P7["Baseline load and failure tests"]
        P8["Backup and restore"]
    end

    PHASE1 --> PROVES
```

**Key point:** Phase 1 is not a demo. It is a smaller but production-shaped environment sized for discovery.

---

## 10. Acceptance Gates

The sandbox is accepted only when it proves Health OS governance, isolation, and observability.

```mermaid
flowchart TB
    START["Candidate sandbox deployment"]
    G1["Gateway-only public access"]
    G2["Envoy -> TSHEPO/OPA -> service enforcement"]
    G3["Mandatory trust headers"]
    G4["No direct service/database/cache/bus exposure"]
    G5["Per-service DB credentials"]
    G6["Audit + outbox events"]
    G7["Synthetic-only PII"]
    G8["FHIR/BUTANO PII separation"]
    G9["Logs, metrics, traces"]
    PASS["Accepted data-centre sandbox"]
    FAIL["Not accepted / remediate"]

    START --> G1
    G1 --> G2
    G2 --> G3
    G3 --> G4
    G4 --> G5
    G5 --> G6
    G6 --> G7
    G7 --> G8
    G8 --> G9
    G9 --> PASS

    G1 -.failure.-> FAIL
    G2 -.failure.-> FAIL
    G3 -.failure.-> FAIL
    G4 -.failure.-> FAIL
    G5 -.failure.-> FAIL
    G6 -.failure.-> FAIL
    G7 -.failure.-> FAIL
    G8 -.failure.-> FAIL
    G9 -.failure.-> FAIL
```

**Key point:** infrastructure health is necessary, but not enough. The sandbox must prove Impilo-specific governance and safety rules.

---

## 11. DevOps Feedback Loop

The deployment documents must evolve as the platform is measured.

```mermaid
flowchart LR
    PLAN["Service classification and deployment plan"]
    DEPLOY["GitOps deploy to sandbox"]
    OBSERVE["Collect metrics, logs, traces"]
    TEST["Run smoke, stress, failure tests"]
    LEARN["Identify bottlenecks and unsafe failure modes"]
    UPDATE["Update docs, Helm values, limits, policies"]

    PLAN --> DEPLOY
    DEPLOY --> OBSERVE
    OBSERVE --> TEST
    TEST --> LEARN
    LEARN --> UPDATE
    UPDATE --> PLAN
```

**Key point:** the service classification matrix is not a static guess. It is a living baseline updated by evidence from the sandbox.

