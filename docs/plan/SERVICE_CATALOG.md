# Impilo vNext — Authoritative Service Catalog

**Version**: 1.0
**Date**: 2026-02-14
**Scope**: All services — existing (legacy + v1.1-native) and Outstanding 27

---

## 1. Legend

| Column | Description |
|---|---|
| Service Name | Canonical name used in code, Helm, Kafka producer field |
| Module Path | Relative path under repository root |
| Port | Local dev HTTP port (unique per service) |
| DB Schema | PostgreSQL database name in `scripts/seed/init-databases.sql` |
| Ring | 0 (Kernel), 1 (Clinical), 2 (Scale/Integration), Infra |
| Status | LIVE (fully implemented), SKELETON (directory exists, partial code), NEW (to be created) |
| Bundle | I=Integration, D=Data, S=IoT/Supply, X=Experience, A=Assurance, R=Resilience, —=Legacy |
| Primary Responsibilities | Brief description of what the service does |

---

## 2. Ring 0 — Kernel Services (Authoritative DPI)

Zero dependencies on Ring 1+. Strictest operational standards.

### 2.1 TSHEPO Trust Cluster

| Service Name | Module Path | Port | DB Schema | Status | Bundle | Primary Responsibilities |
|---|---|---|---|---|---|---|
| tshepo-authz-service | `services/tshepo-authz-service` | 8081 (HTTP) + 9090 (gRPC) | tshepo_authz | LIVE | — | ext_authz policy decision point, RBAC/ABAC, break-glass, step-up, device identity, risk scoring |
| tshepo-identity-service | `services/tshepo-identity-service` | 8181 | tshepo_identity | LIVE | — | CPID resolution, MOSIP integration, O-CPID provisioning, token issuance, reconciliation |
| tshepo-consent-service | `services/tshepo-consent-service` | 8182 | tshepo_consent | LIVE | — | FHIR R4 Consent CRUD, consent evaluation (Redis-cached), share links, revocation |
| tshepo-audit-service | `services/tshepo-audit-service` | 8183 | tshepo_audit | LIVE | — | SHA-256 hash-chain audit ledger, Kafka consumer, query/export/verify, decision evidence |
| tshepo-keys-service | `services/tshepo-keys-service` | 8184 | tshepo_keys | LIVE | — | Ed25519 signing, JWKS, key rotation, certificate trust, KMS/HSM (Vault-ready) |
| tshepo-offline-service | `services/tshepo-offline-service` | 8185 | tshepo_offline | LIVE | — | Offline capability tokens (JWS-signed), offline packs, O-CPID issuance, reconciliation |

### 2.2 Registry Spine

| Service Name | Module Path | Port | DB Schema | Status | Bundle | Primary Responsibilities |
|---|---|---|---|---|---|---|
| vito-service | `services/vito-service` | 8082 | vito | LIVE | — | Client Registry (MPI), CRID/CPID management, dedup/merge, SMART Card, wallet, biometric matching |
| varapi-service | `services/varapi-service` | 8083 | varapi | SKELETON | X | Provider Registry, practitioner identity, licensure, privileging, credentialing, CPD, council sync |
| tuso-service | `services/tuso-service` | 8084 | tuso | SKELETON | X | Facility Registry, hierarchy, capabilities, resource calendar, Control Tower, telemetry, bookings |
| zibo-service | `services/zibo-service` | 8085 | zibo | LIVE | — | Terminology & Semantic Governance, 6 FHIR types, validation, packs, mappings, import/export |
| msika-service | `services/msika-service` | 8086 | msika | SKELETON | X | Product & Service Registry, catalogs, tariffs, packs, formulary, import/export |

### 2.3 Shared Health Record

| Service Name | Module Path | Port | DB Schema | Status | Bundle | Primary Responsibilities |
|---|---|---|---|---|---|---|
| butano-service | `services/butano-service` | 8090 | butano | LIVE | — | HAPI FHIR R4 JPA server, PII-free (CPID-only), IPS, visit summary, timeline, reconciliation |

### 2.4 CRVS & Finance

| Service Name | Module Path | Port | DB Schema | Status | Bundle | Primary Responsibilities |
|---|---|---|---|---|---|---|
| ubomi-service | `services/ubomi-service` | 8186 | ubomi | SKELETON | X | CRVS Interface, birth/death notification, vital event verification, civil registry interop |
| mushex-service | `services/mushex-service` | 8087 | mushex | LIVE | — | Payment intent engine, claims switching, double-entry ledger, remittance, settlement, fraud detection |

---

## 3. Ring 1 — Clinical Plane (Care Execution)

Depends on Ring 0 for identity, registries, terminology, auth. Enforces Clinical Safety Classes.

### 3.1 Existing Clinical Services

| Service Name | Module Path | Port | DB Schema | Status | Bundle | Primary Responsibilities |
|---|---|---|---|---|---|---|
| pct-service | `services/pct-service` | 8088 | pct | LIVE | — | Patient Care Tracker, 13-state journey, queues, triage, admission, discharge, death recording |
| oros-service | `services/oros-service` | 8089 | oros | LIVE | — | Orders & Results Orchestration, 13-state lifecycle, worksteps, SLA timers, reconciliation |
| costing-engine-service | `services/costing-engine-service` | 8101 | costing | LIVE | — | 5 cost engines, charging rules, exemptions, bill lifecycle, claims packing |
| pharmacy-service | `services/pharmacy-service` | 8096 | pharmacy | LIVE | — | Dispense workflow, FEFO stock, substitution, barcode lookup, pickup proof, reversal |
| msika-flow-service | `services/msika-flow-service` | 8100 | msika_flow | LIVE | — | Health Marketplace, 19-state order, fulfillment, vendor, booking, pickup tokens |

### 3.2 New Clinical Services (Outstanding 27)

| Service Name | Module Path | Port | DB Schema | Status | Bundle | Primary Responsibilities |
|---|---|---|---|---|---|---|
| inpatient-service | `services/inpatient-service` | 8120 | inpatient | SKELETON | S | Bed management, ward allocation, transfer, discharge planning, nursing allocation |
| scheduling-service | `services/scheduling-service` | 8121 | scheduling | NEW | S | Appointment booking, capacity management, slot generation, wait-list, resource calendar |
| referral-service | `services/referral-service` | 8122 | referral | NEW | X | Referral routing, care coordination, counter-referral, care networks |
| channels-service | `services/channels-service` | 8130 | channels | SKELETON | X | Omnichannel access gateway: session management, message routing, USSD/WhatsApp/SMS/IVR, escalation, assisted interactions |
| coverage-service | `services/coverage-service` | 8140 | coverage | SKELETON | X | Coverage & eligibility engine: insurance verification, pre-authorization, claims lifecycle, payment coordination |
| indawo-service | `services/indawo-service` | 8150 | indawo | SKELETON | X | Location & address registry: standardized addresses, geocoding, catchment areas, facility-location linking |

---

## 4. Ring 2 — Scale Layer (Supply, Data, Integration)

Operational support, analytics, integration. Must NOT impact Ring 1 latency or safety.

### 4.1 Existing Ring 2 Services

| Service Name | Module Path | Port | DB Schema | Status | Bundle | Primary Responsibilities |
|---|---|---|---|---|---|---|
| inventory-service | `services/inventory-service` | 8098 | inventory | LIVE | — | Append-only stock ledger, FEFO, stock counts, requisitions, handovers |
| inventory-elmis-adapter | `services/inventory-elmis-adapter` | 8099 | — | LIVE | — | External eLMIS integration (REST/CSV/Kafka) |
| pharmacy-elmis-adapter | `services/pharmacy-elmis-adapter` | 8097 | — | LIVE | — | External eLMIS integration (REST/CSV/Kafka) |
| integration-hub | `services/integration-hub` | 8110 | integration_hub | LIVE | — | Central routing and dispatch (v1.1-native) |
| notification-service | `services/notification-service` | 8111 | notification | LIVE | — | Template-driven notification delivery (v1.1-native) |
| rules-service | `services/rules-service` | 8112 | rules | LIVE | — | Rule storage and evaluation with decision logging (v1.1-native) |
| landela-adapter-service | `services/landela-adapter-service` | 8092 | landela_adapter | LIVE | — | Dual-mode document gateway (Landela/MinIO) |
| document-service | `services/document-service` | 8093 | document_service | LIVE | — | MinIO/S3 object store with AV scan hook |
| credential-verification-service | `services/credential-verification-service` | 8094 | credential_verification | LIVE | — | Ed25519 signed PDFs + QR verification |
| card-print-agent | `services/card-print-agent` | 8091 | card_print | LIVE | — | Job-based smart card printing (Kafka consumer) |
| share-slip-service | `services/share-slip-service` | 8095 | share_slip | LIVE | — | OTP-based delegated pickup & share links |

### 4.2 New Ring 2 Services (Outstanding 27)

| Service Name | Module Path | Port | DB Schema | Status | Bundle | Primary Responsibilities |
|---|---|---|---|---|---|---|
| fhir-gateway-service | `services/fhir-gateway-service` | 8113 | fhir_gateway | SKELETON | I | FHIR R4 Bundle routing, query translation, CPID resolution, consent enforcement |
| pacs-adapter-service | `services/pacs-adapter-service` | 8114 | pacs_adapter | SKELETON | I | DICOM C-STORE/C-FIND/C-MOVE proxy, study correlation, BUTANO writeback |
| offline-sync-service | `services/offline-sync-service` | 8115 | offline_sync | SKELETON | I | Edge data packs, CRDT-based upload, reconciliation queue |
| jobs-service | `services/jobs-service` | 8116 | jobs | SKELETON | I | Background job scheduling, execution tracking, dead-letter re-queue |
| analytics-pipeline-service | `services/analytics-pipeline-service` | 8117 | analytics_pipeline | NEW | D | ETL engine, NDR aggregation, reporting schedules, ad-hoc queries |
| surveillance-service | `services/surveillance-service` | 8118 | surveillance | NEW | D | eIDSR, case detection, threshold alerting, DHIS2 push |
| data-governance-service | `services/data-governance-service` | 8119 | data_governance | NEW | D | Research exports, de-identification, consent verification, data access lifecycle |
| developer-portal-service | `services/developer-portal-service` | 8123 | developer_portal | NEW | X | API docs aggregation, sandbox, API keys, SDK packaging, onboarding |

---

## 5. Shared Libraries

| Library Name | Module Path | Status | Description |
|---|---|---|---|
| shared-core | `services/shared-core` | LIVE | Argon2id, HMAC, ApiResponse, TrustContext, TrustContextFilter |
| shared-kernel | `libs/shared-kernel` | LIVE | Cross-cutting domain models (TypeScript) |
| shared-kernel-java | `libs/shared-kernel-java` | LIVE | EventEnvelope record, event infrastructure |
| tshepo-contracts | `libs/tshepo-contracts` | LIVE | TrustHeaders (14+), AuthzRequest/Response, Obligations, enums, Protobuf |
| tshepo-sdk | `libs/tshepo-sdk` | LIVE | TrustContext record, TrustContextFilter, TrustHeaderPropagator, AuthzClient |
| tech-companion | `libs/tech-companion` | LIVE | v1.1 enforcement: header filter, idempotency, error envelope, federation, timeout |
| tech-companion-mock | `libs/tech-companion-mock` | LIVE | Mock implementations for testing |
| tech-companion-harness | `libs/tech-companion-harness` | LIVE | GoldenContractSuite, EndpointDiscovery, test utilities |
| federation-connector | `libs/federation-connector` | SKELETON | Federation protocol implementation (pod registration, authority, revocation) |
| supply-planning | `libs/supply-planning` | NEW | Consumption forecasting, reorder calculation, stockout prediction |
| vault-kms | `libs/vault-kms` | NEW | HashiCorp Vault integration, KEK retrieval, key wrapping, CPID derivation |
| chaos-testing | `libs/chaos-testing` | NEW | Fault injection, circuit breaker validation, degradation tests |

---

## 6. Infrastructure Components

| Component | Location | Port(s) | Status | Bundle | Description |
|---|---|---|---|---|---|
| PostgreSQL 16 | `docker-compose.yml` | 5432 | LIVE | — | Single instance, per-service databases |
| Redis 7 | `docker-compose.yml` | 6379 | LIVE | — | Caching, sessions |
| Apache Kafka 3.7 (KRaft) | `docker-compose.yml` | 9092 | LIVE | — | Event streaming (no ZooKeeper) |
| Keycloak 25.x | `docker-compose.yml` | 8080 | LIVE | — | Identity provider |
| MinIO | `docker-compose.yml` | 9000, 9001 | LIVE | — | Object storage (S3-compatible) |
| HAPI FHIR R4 | `docker-compose.yml` | 8090 | LIVE | — | FHIR server (butano backend) |
| Orthanc PACS | `docker-compose.yml` | 8042, 4242 | LIVE | — | DICOM/PACS server |
| Envoy Gateway | `infra/envoy/envoy.yaml` | 10000, 9901 | LIVE | — | API gateway, ext_authz, routing |
| Schema Registry (Apicurio) | `docker-compose.yml` | 8180 | NEW | D | Event schema governance, compatibility gates |
| HashiCorp Vault | `docker-compose.yml` | 8200 | NEW | R | Secrets management, KMS, key rotation |
| Prometheus | `docker-compose.yml` | 9090 | NEW | R | Metrics collection |
| Grafana | `docker-compose.yml` | 3100 | NEW | R | Dashboards, alerting |
| OpenTelemetry Collector | `docker-compose.yml` | 4317, 4318 | NEW | R | Trace/metric collection |
| Loki | `docker-compose.yml` | 3200 | NEW | R | Log aggregation |

---

## 7. UI Applications

| App Name | Module Path | Port | Status | Description |
|---|---|---|---|---|
| one-ui-shell | `ui/one-ui-shell` | 3000 | LIVE | Root shell (3-zone: Work, Professional, Life) |
| ops-console | `ui/ops-console` | 3001 | LIVE | Operations dashboard (VITO, TSHEPO admin) |
| ehr | `ui/ehr` | 3002 | LIVE | Electronic Health Record |
| portal | `ui/portal` | 3003 | LIVE | Citizen portal (Request ID, Recovery, QR, Pickup) |
| ops-docs | `ui/ops-docs` | 3004 | LIVE | Operational documentation |
| self-service | `ui/self-service` | 3005 | LIVE | Self-service portal |
| butano-web | `ui/butano-web` | 3006 | LIVE | BUTANO ops (timeline, IPS, reconciliation) |
| pct-web | `ui/pct-web` | 3007 | LIVE | PCT ops (Work, Sorting, Queue, Control Tower) |
| zibo-web | `ui/zibo-web` | 3008 | LIVE | ZIBO admin (Artifacts, Packs, Mappings, Validation) |
| oros-web | `ui/oros-web` | 3009 | LIVE | OROS ops (Worklists, Orders, Results, Catalog) |
| pharmacy-web | `ui/pharmacy-web` | 3010 | LIVE | Pharmacy ops (worklists, dispense, stock) |
| inventory-web | `ui/inventory-web` | 3011 | LIVE | Inventory ops (dashboard, movements, counts) |
| msika-flow-portal | `ui/msika-flow-portal` | 3012 | LIVE | MSIKA Flow citizen portal |
| msika-flow-vendor | `ui/msika-flow-vendor` | 3013 | LIVE | MSIKA Flow vendor portal |
| msika-flow-ops | `ui/msika-flow-ops` | 3014 | LIVE | MSIKA Flow operations |
| costa-console | `ui/costa-console` | 3015 | LIVE | COSTA ops (Tariffs, Rulesets, Bills, Audit) |
| mushex-payer-portal | `ui/mushex-payer-portal` | 3016 | LIVE | MUSHEX payer portal |
| mushex-finance-console | `ui/mushex-finance-console` | 3017 | LIVE | MUSHEX finance operations |
| mushex-ops-console | `ui/mushex-ops-console` | 3018 | LIVE | MUSHEX operations |
| shared-ui | `ui/shared-ui` | — | LIVE | Shared component library (not a standalone app) |
| msika-web | `ui/msika-web` | 3019 | LIVE | MSIKA catalog management |

---

## 8. Port Allocation Map

### Reserved Port Ranges

| Range | Assignment |
|---|---|
| 8080–8089 | Ring 0 core (Keycloak, TSHEPO authz, VITO, VARAPI, TUSO, ZIBO, MSIKA, MUSHEX, PCT) |
| 8090–8099 | Ring 0/1 clinical + document suite (BUTANO, card-print, landela, doc-store, CVS, share-slip, pharmacy, pharmacy-elmis, inventory, inventory-elmis) |
| 8100–8109 | Ring 1 orchestration (MSIKA Flow, COSTA) |
| 8110–8119 | Ring 2 integration + data (integration-hub, notification, rules, fhir-gw, pacs, offline-sync, jobs, analytics, surveillance, data-gov) |
| 8120–8129 | Ring 1 clinical extensions + experience (inpatient, scheduling, referral, developer-portal) |
| 8130–8159 | Omnichannel, coverage, location (channels, coverage, indawo) |
| 8180–8189 | Ring 0 TSHEPO cluster + infra (identity, consent, audit, keys, offline, UBOMI, schema-registry) |
| 3000–3019 | UI applications |
| 9000–9999 | Infrastructure (MinIO, Kafka, gRPC, Prometheus) |
| 10000 | Envoy public listener |

### Complete Port Assignment

| Port | Service |
|---|---|
| 5432 | PostgreSQL |
| 6379 | Redis |
| 8042 | Orthanc REST |
| 8080 | Keycloak |
| 8081 | tshepo-authz-service (HTTP) |
| 8082 | vito-service |
| 8083 | varapi-service |
| 8084 | tuso-service |
| 8085 | zibo-service |
| 8086 | msika-service |
| 8087 | mushex-service |
| 8088 | pct-service |
| 8089 | oros-service |
| 8090 | butano-service (HAPI FHIR) |
| 8091 | card-print-agent |
| 8092 | landela-adapter-service |
| 8093 | document-service |
| 8094 | credential-verification-service |
| 8095 | share-slip-service |
| 8096 | pharmacy-service |
| 8097 | pharmacy-elmis-adapter |
| 8098 | inventory-service |
| 8099 | inventory-elmis-adapter |
| 8100 | msika-flow-service |
| 8101 | costing-engine-service (COSTA) |
| 8110 | integration-hub |
| 8111 | notification-service |
| 8112 | rules-service |
| 8113 | fhir-gateway-service |
| 8114 | pacs-adapter-service |
| 8115 | offline-sync-service |
| 8116 | jobs-service |
| 8117 | analytics-pipeline-service |
| 8118 | surveillance-service |
| 8119 | data-governance-service |
| 8120 | inpatient-service |
| 8121 | scheduling-service |
| 8122 | referral-service |
| 8123 | developer-portal-service |
| 8130 | channels-service |
| 8140 | coverage-service |
| 8150 | indawo-service |
| 8180 | Schema Registry (Apicurio) |
| 8181 | tshepo-identity-service |
| 8182 | tshepo-consent-service |
| 8183 | tshepo-audit-service |
| 8184 | tshepo-keys-service |
| 8185 | tshepo-offline-service |
| 8186 | ubomi-service |
| 8200 | HashiCorp Vault |
| 9000 | MinIO API |
| 9001 | MinIO Console |
| 9090 | tshepo-authz-service (gRPC) / Prometheus |
| 9092 | Kafka |
| 9901 | Envoy admin |
| 10000 | Envoy public |

---

## 9. Service Consolidation Decisions

| Decision | Rationale | Action |
|---|---|---|
| Merge `product-registry-service` into `msika-service` | v1.1 defines MSIKA as the canonical product registry. Two services for one domain violates Law 9. | Deprecate `product-registry-service/`, add README redirect to `msika-service` |
| Keep Federation Control as module in `libs/federation-connector` | Law 9 (module-first). Not enough scale justification for separate service extraction. | Library consumed by TSHEPO sub-services and new services |
| Keep Pharmacy + Inventory eLMIS adapters as separate services | They bridge external systems — clear boundary justification for extraction | No change |
| Keep `tshepo-service` (legacy) as redirect during migration | Existing consumers may reference old endpoints | Phase out over 1 release cycle |
| Do NOT create separate IoT Gateway service | Telemetry ingestion handled by TUSO Control Tower + existing Kafka topics | Route IoT data through `telemetry.*` bus |
