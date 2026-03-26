# Impilo vNext — Component Catalog

**Date**: 2026-03-26
**Version**: 1.1
**Status**: Living document — updated as components evolve

---

## Overview

Impilo vNext comprises **68 backend services**, **24 UI applications**, and **12 shared libraries**, organized into a ring-based architecture with strict dependency rules. This catalog provides a complete inventory of every component, its purpose, tech stack, port assignment, database, and current implementation status.

### Ring Classification

| Ring | Name | Purpose | Dependency Rule |
|------|------|---------|-----------------|
| **0** | Kernel | Authoritative DPI primitives — identity, trust, registries, terminology, finance | Zero outbound dependencies to Ring 1+ |
| **1** | Clinical | Care execution — encounters, orders, prescriptions, costing | Depends on Ring 0 only |
| **2** | Supply, Data, Integration | Operational support, analytics, interoperability | Depends on Ring 0 + Ring 1 |

---

## 1. Backend Services

### 1.1 Ring 0 — Kernel Plane

#### TSHEPO Trust Cluster (Trust & Governance)

| # | Service | Port | Database | Purpose | Tech Stack | Status |
|---|---------|------|----------|---------|------------|--------|
| 1 | **tshepo-service** | 8081 | `tshepo` | Legacy trust gateway — IAM, RBAC/ABAC, ext_authz endpoint for Envoy | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 2 | **tshepo-authz-service** | 8081 | `tshepo_authz` | Decomposed authz — PolicyEngine, break-glass, step-up, Class A/B/C enforcement | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 3 | **tshepo-identity-service** | 8181 | `tshepo_identity` | CPID resolution, MOSIP integration, keyed pseudonymization | Java 21, Spring Boot 3.3, PostgreSQL, Redis | Implemented |
| 4 | **tshepo-consent-service** | 8182 | `tshepo_consent` | FHIR Consent CRUD, evaluation, share-links, revocation propagation | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 5 | **tshepo-audit-service** | 8183 | `tshepo_audit` | Tamper-evident audit ledger, SHA-256 hash chain, decision evidence | Java 21, Spring Boot 3.3, PostgreSQL, Kafka | Implemented |
| 6 | **tshepo-keys-service** | 8184 | `tshepo_keys` | Ed25519 signing, JWKS endpoint, key rotation, KMS/HSM integration | Java 21, Spring Boot 3.3, PostgreSQL | Implemented |
| 7 | **tshepo-offline-service** | 8185 | `tshepo_offline` | Offline entitlements (JWT/CBOR tokens), capability tokens, reconciliation | Java 21, Spring Boot 3.3, PostgreSQL, Redis | Implemented |

#### Core Registries (Ring 0)

| # | Service | Port | Database | Purpose | Tech Stack | Status |
|---|---------|------|----------|---------|------------|--------|
| 8 | **vito-service** | 8082 | `vito` | Client Registry / MPI — CRID/CPID management, dedup/merge, federation merges | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 9 | **varapi-service** | 8083 | `varapi` | Provider Registry — licensure, privileges, credential verification, revocation | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 10 | **tuso-service** | 8084 | `tuso` | Facility Registry — topology, Control Tower, bookings, geo, capacity | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 11 | **zibo-service** | 8085 | `zibo` | Terminology & Semantic Governance — FHIR CodeSystem, ValueSet, ConceptMap | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 12 | **msika-service** | 8086 | `msika` | Product & Service Registry — catalogs, tariffs, packs, formulary | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |

#### Health Record & Finance (Ring 0)

| # | Service | Port | Database | Purpose | Tech Stack | Status |
|---|---------|------|----------|---------|------------|--------|
| 13 | **butano-service** | — | `butano` | BUTANO orchestration layer — SHR write coordination, CPID-only (no PII) | Java 21, Spring Boot 3.3, PostgreSQL, Kafka | Implemented |
| 14 | **butano-fhir** | 8090 | `butano_fhir` | HAPI FHIR R4 server — longitudinal health record, IPS, visit timeline | HAPI FHIR 7.4, PostgreSQL | Implemented |
| 15 | **ubomi-service** | — | `—` | CRVS Interface — births/deaths linkage to national registries | Java 21, Spring Boot 3.3 | Implemented |
| 16 | **mushex-service** | 8087 | `mushex_db` | Finance Engine — payments, claims adjudication, settlement, fraud detection, ledger | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |

#### Ring 0 Platform Primitives

| # | Service | Port | Database | Purpose | Tech Stack | Status |
|---|---------|------|----------|---------|------------|--------|
| 17 | **schema-registry-service** | 8371 | `impilo_schema_registry` | Event schema governance, compatibility gates, CI validation | Java 21, Spring Boot 3.3, PostgreSQL | Skeleton |
| 18 | **developer-portal-service** | 8370 | `impilo_dev_portal` | Client registration, API key issuance, sandbox, deprecation tracking | Java 21, Spring Boot 3.3, PostgreSQL | Skeleton |
| 19 | **observability-service** | 8210 | `observability` | Dashboard & alert registry, CRUD, outbox event publishing | Java 21, Spring Boot 3.3, PostgreSQL | Skeleton |

---

### 1.2 Ring 1 — Clinical Plane

| # | Service | Port | Database | Purpose | Ring 0 Dependencies | Status |
|---|---------|------|----------|---------|---------------------|--------|
| 20 | **pct-service** | 8088 | `pct` | Patient Care Tracker — care journeys, encounters, queues, triage, discharge | TSHEPO, VITO, TUSO, BUTANO, MUSHEX | Implemented |
| 21 | **oros-service** | 8089 | `oros` | Orders & Results Orchestration — orders, worklists, worksteps, SLA tracking | TSHEPO, BUTANO, ZIBO, TUSO | Implemented |
| 22 | **costing-engine-service** | 8101 | `costing` / `costa_db` | COSTA — billing, tariffs, exemptions, claims packing | TSHEPO, MSIKA, MUSHEX | Implemented |
| 23 | **pharmacy-service** | 8096 | `pharmacy` | Dispense workflow — stock, FEFO, barcode, pickup proof | TSHEPO, OROS, MUSHEX, MSIKA | Implemented |
| 24 | **msika-flow-service** | 8100 | `msika_flow` | Health Marketplace — orders, fulfillment, vendor management, booking | TSHEPO, MSIKA, MUSHEX, VARAPI | Implemented |
| 25 | **inpatient-service** | — | `inpatient` | Bed management, ward allocation | TSHEPO, TUSO, PCT | Skeleton |
| 26 | **coverage-service** | — | `impilo_coverage` | Insurance/coverage verification, member benefits | TSHEPO, MUSHEX | Implemented |
| 27 | **indawo-service** | — | `impilo_indawo` | Location/area management for clinical context | TSHEPO, TUSO | Implemented |

---

### 1.3 Ring 2 — Supply, Data, Integration

#### Supply Chain

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 28 | **inventory-service** | 8098 | `inventory` | Supply chain, stock visibility, requisitions, handovers | Implemented |
| 29 | **inventory-elmis-adapter** | 8098 | `inventory_elmis` | External eLMIS stock sync, receipt forwarding, reconciliation | Implemented |
| 30 | **pharmacy-elmis-adapter** | 8099 | `pharmacy_elmis` | External pharmacy eLMIS stock sync, order forwarding | Implemented |
| 31 | **product-registry-service** | 8097 | `product_registry` | Product master data (planned merge into MSIKA) | Implemented |

#### Document & Credential Suite

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 32 | **document-service** | — | `document_service` | MinIO/S3 object storage with AV scan | Implemented |
| 33 | **landela-adapter-service** | 8092 | `landela_adapter` | Document gateway (Landela/MinIO integration) | Implemented |
| 34 | **credential-verification-service** | 8094 | `credential_verification` | Ed25519 signed PDFs + QR verification | Implemented |
| 35 | **card-print-agent** | 8091 | `card_print` | Smart card printing agent | Implemented |
| 36 | **share-slip-service** | 8095 | `share_slip` | OTP-based delegated pickup slips | Implemented |

#### Integration & Interoperability

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 37 | **integration-hub** | 8110 | `integration_hub` | DHIS2, eLMIS, LIMS, iHRIS adapters — event routing & dispatch | Skeleton |
| 38 | **fhir-gateway-service** | 8092 | `fhir_gateway` | FHIR boundary routing & audit gateway | Skeleton |
| 39 | **connector-fhir-adapter** | 8150 | `impilo_connector_fhir` | FHIR bundle validation, header audit, relay routing | Skeleton |
| 40 | **pacs-adapter-service** | 8096 | `pacs` | DICOM/Orthanc PACS integration — imaging metadata gateway | Skeleton |
| 41 | **channels-service** | 8130 | `impilo_channels` | Omnichannel access (USSD, WhatsApp, SMS, IVR), session mgmt | Skeleton |

#### Notifications & Workflow

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 42 | **notification-service** | — | `notification` | SMS, email, push notification hub | Skeleton |
| 43 | **workflow-service** | — | `impilo_workflow` | BPMN/workflow orchestration engine | Skeleton |
| 44 | **jobs-service** | — | `jobs` | Background job scheduling (cron, batch) | Skeleton |
| 45 | **dispatch-service** | — | `impilo_dispatch` | Task dispatch and routing | Skeleton |
| 46 | **rules-service** | — | `impilo_rules` | Business rules engine | Skeleton |
| 47 | **forms-service** | — | `impilo_forms` | Dynamic form builder and renderer | Skeleton |

#### Data, Analytics & Reporting

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 48 | **reporting-service** | — | `reporting` | BI reports, operational dashboards | Skeleton |
| 49 | **ndr-service** | 8230 | `ndr` | National Data Repository — aggregated de-identified analytics | Skeleton |
| 50 | **national-data-repository-service** | 8150 | `impilo_ndr` | NDR analytics-ready store — versioned tables, materialized views | Skeleton |
| 51 | **data-warehouse-service** | 8230 | `impilo_data_warehouse` | Gold dataset materializer — bronze events to analytical datasets | Skeleton |
| 52 | **data-pipeline-service** | 8140 | `pipeline` | EventEnvelope ingestion, curated pipeline records, watermarks | Skeleton |
| 53 | **data-ingestion-service** | 8210 | `impilo_data_ingestion` | Facility/external data submission ingestion & routing | Skeleton |
| 54 | **search-service** | — | `impilo_search` | Full-text search (Elasticsearch/OpenSearch) | Skeleton |
| 55 | **surveillance-service** | 8180 | `surv` | Disease surveillance (eIDSR) — signal detection, case registry | Skeleton |
| 56 | **campaigns-service** | 8190 | `camp` | Public health campaign management & outreach | Skeleton |

#### Governance & Security

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 57 | **data-governance-service** | 8220 | `impilo_data_governance` | Data quality rules, lineage tracking, metadata cataloging | Skeleton |
| 58 | **data-access-governance-service** | 8170 | `dags` | Policy registry, access-request workflow, permit-token issuance | Skeleton |
| 59 | **security-hardening-service** | 8220 | `secharden` | Policy pack registry, compliance scan results | Skeleton |
| 60 | **audit-ledger-service** | 8350 | `impilo_audit_ledger` | Append-only SHA-256 hash-chained audit ledger (no UPDATE/DELETE) | Skeleton |
| 61 | **identity-assurance-service** | 8200 | `impilo_identity_assurance` | Step-up checks, device attestation, risk scoring | Skeleton |

#### Offline & Edge

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 62 | **offline-sync-service** | 8095 | `offline_sync` | Queued action intake, replay, conflict resolution | Skeleton |
| 63 | **offline-edge-service** | 8360 | `impilo_offline_edge` | Offline trust controls — event capture, signed entitlements, recon | Skeleton |

#### IoT & Devices

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 64 | **iot-ingestion-service** | 8330 | `impilo_iot_ingestion` | Device telemetry ingestion (HTTP + Kafka), backpressure/DLQ | Skeleton |
| 65 | **asset-registry-service** | 8310 | `impilo_asset_registry` | Medical equipment, cold-chain, vehicle lifecycle tracking | Skeleton |

#### Support

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 66 | **support-service** | — | `impilo_support` | Help desk, ticketing, knowledge base | Skeleton |
| 67 | **experience-bff** | 8160 | `experience_bff` | Backend-for-Frontend aggregation layer | Java 21, Spring Boot 3.3 | Implemented |

#### Shared Core

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 68 | **shared-core** | — | — | Shared domain objects, DTOs, utilities used across services | Java 21 library | Implemented |

---

## 2. UI Applications

All UIs use **Next.js 14.2.x**, **TypeScript 5.5**, **TailwindCSS 3.4**, **Radix UI**, **TanStack Query**, and **Zustand** unless noted otherwise.

### 2.1 Shell & Core

| # | App | Port | Purpose | Backend Dependencies |
|---|-----|------|---------|---------------------|
| 1 | **one-ui-shell** | 3000 | Primary UI shell — 3-zone layout (Work/Pro/Life), trust header injection, module federation host | Envoy Gateway → all services |
| 2 | **ops-console** | 3001 | Operations console — system administration, monitoring, configuration | TSHEPO, TUSO, all registries |
| 3 | **portal** | 3003 | Citizen-facing portal — self-service, health records, appointments | VITO, PCT, BUTANO, MUSHEX |
| 4 | **self-service** | 3005 | Patient self-service kiosk / registration | VITO, TSHEPO, PCT |

### 2.2 Clinical UIs

| # | App | Port | Purpose | Backend Dependencies |
|---|-----|------|---------|---------------------|
| 5 | **ehr** | 3002 | Electronic Health Record — clinical workspace | PCT, OROS, BUTANO, VITO, ZIBO |
| 6 | **pct-web** | 3021 | Patient Care Tracker UI — queues, triage, encounters | PCT service |
| 7 | **oros-web** | 3009 | Orders & Results UI — worklists, order entry | OROS service |
| 8 | **pharmacy-web** | 3010 | Pharmacy UI — dispensing, stock, prescriptions | Pharmacy service, OROS |

### 2.3 Supply & Marketplace UIs

| # | App | Port | Purpose | Backend Dependencies |
|---|-----|------|---------|---------------------|
| 9 | **inventory-web** | 3011 | Inventory management UI — stock, requisitions | Inventory service |
| 10 | **msika-web** | 3012 | Product registry management UI | MSIKA service |
| 11 | **msika-flow-ops** | 3014 | Marketplace operations (admin) | MSIKA Flow service |
| 12 | **msika-flow-portal** | 3012 | Marketplace buyer portal — catalog, cart, fulfillment | MSIKA Flow service |
| 13 | **msika-flow-vendor** | 3013 | Marketplace vendor portal — order fulfillment, logistics | MSIKA Flow service |

### 2.4 Finance UIs

| # | App | Port | Purpose | Backend Dependencies |
|---|-----|------|---------|---------------------|
| 14 | **mushex-finance-console** | 3017 | Finance console — payments, settlement, ledger | MUSHEX service |
| 15 | **mushex-ops-console** | 3018 | Finance operations — claims processing | MUSHEX service |
| 16 | **mushex-payer-portal** | 3016 | Payer/insurer portal — claims review, benefits | MUSHEX service, Coverage |
| 17 | **costa-console** | — | Costing console — tariffs, billing, exemptions | Costing Engine service |

### 2.5 Governance & Terminology UIs

| # | App | Port | Purpose | Backend Dependencies |
|---|-----|------|---------|---------------------|
| 18 | **zibo-web** | 3008 | Terminology management UI — code systems, value sets, validation | ZIBO service |
| 19 | **butano-web** | 3006 | SHR administration UI — FHIR bundle browser, IPS, reconciliation | BUTANO / HAPI FHIR |

### 2.6 Platform & Support UIs

| # | App | Port | Purpose | Backend Dependencies |
|---|-----|------|---------|---------------------|
| 20 | **developer-console** | 3007 | Developer portal UI — client registration, federation, certification | Developer Portal service |
| 21 | **support-console** | 3019 | Help desk / support UI — tickets, knowledge base | Support service |
| 22 | **experience** | 3020 | Unified clinical experience — pharmacy, inventory, EHR workflows | Experience BFF (:8160) |
| 23 | **ops-docs** | 3004 | Operations documentation & document management console | Landela, CVS, Card Print |

### 2.7 Shared UI Library

| # | App | Port | Purpose |
|---|-----|------|---------|
| 24 | **shared-ui** | — | Shared React component library — design system, Radix primitives, form components |

---

## 3. Shared Libraries

| # | Library | Language | Purpose | Consumers |
|---|---------|----------|---------|-----------|
| 1 | **shared-kernel-java** | Java 21 | Manifest v1.1 enforcement — audit ledger, consistency gates, event envelopes, schema validation | All Java backend services |
| 2 | **shared-kernel** | TypeScript | Compliance primitives for Companion Spec v1.1-canonical | All UI applications |
| 3 | **tshepo-contracts** | Java (Maven) | Shared DTOs, header constants, enums, protobuf definitions for ext_authz (gRPC 1.65, Protobuf 3.25) | TSHEPO cluster, all services |
| 4 | **tshepo-sdk** | Java (Maven) | Client SDK for trust context validation, TSHEPO decision endpoint calls, Redis caching | All services, BFF |
| 5 | **contract-tests** | Java 21 | Schema compatibility & event envelope validation — backward-compatible schema evolution enforcement | CI/CD pipeline |
| 6 | **federation-connector** | Java (Maven) | Pod-to-spine federation connector with mTLS identity verification, Spring auto-config | Federated deployments |
| 7 | **offline-sdk** | Java 21 | Offline/edge operations — JWT entitlement verification (Ed25519/RS256), local queue format | Offline Edge, mobile |
| 8 | **ops-instrumentation** | Java (Maven) | Structured logging MDC, golden-signal metrics, outbox probes, health checks, OpenTelemetry | All services |
| 9 | **security-baseline** | Java 21 | Input sanitization, token-bucket rate limiting, admin audit emission, secrets provider contract | All services |
| 10 | **tech-companion** | Java (Maven) | Manifest v1.1 enforcement — RequestContext, header filters, error envelope, idempotency, timeouts | All services |
| 11 | **tech-companion-harness** | Java (Maven) | Golden contract test suite for v1.1 compliance verification (reusable JUnit 5 test classes) | CI/CD, service tests |
| 12 | **tech-companion-mock** | TypeScript | Mock server for tech companion development | Development |

---

## 4. Infrastructure Components

| # | Component | Port(s) | Purpose |
|---|-----------|---------|---------|
| 1 | **PostgreSQL 16** | 5432 | Primary RDBMS — one instance, per-service databases (106 databases) |
| 2 | **Redis 7** | 6379 | Caching, session store, distributed locks |
| 3 | **Apache Kafka 3.7** | 9092 | Event bus — KRaft mode (no ZooKeeper), 5 bus categories |
| 4 | **Keycloak 25.x** | 8080 | Identity Provider — OAuth2/OIDC, realm management |
| 5 | **Envoy 1.31** | 10000 (public), 9901 (admin) | API Gateway — ext_authz to TSHEPO, rate limiting, mTLS |
| 6 | **OPA 0.68** | 8181 | Open Policy Agent — policy evaluation sidecar for Envoy |
| 7 | **HAPI FHIR 7.4** | 8090 | FHIR R4 server — BUTANO shared health record |
| 8 | **MinIO** | 9000 (API), 9001 (console) | S3-compatible object storage — documents, images |
| 9 | **Orthanc 24.8** | 8042 (web), 4242 (DICOM) | PACS / DICOM server — medical imaging |

### Kafka Bus Architecture (5 Categories)

| Bus | Topic Prefix | Replication | Retention | Purpose |
|-----|-------------|-------------|-----------|---------|
| **Clinical** | `clinical.*` | 3 (ISR: 2) | 7d + archive | Care events — journeys, encounters, orders, results, dispense |
| **Trust** | `trust.*` | 3 (ISR: 2, acks=all) | 30d | High-priority — consent revocation, privilege revocation, federation |
| **Kernel** | `kernel.*` | 3 (ISR: 2) | 14d + archive | Registry events — client, provider, facility, product, terminology |
| **Telemetry** | `telemetry.*` | 2 | 30d (compacted) | Operational metrics — occupancy, queue metrics, device heartbeats |
| **Analytics** | `analytics.*` | 2 | 90d (compacted) | BI & surveillance — reporting, NDR, fraud analytics |

---

## 5. Status Summary

| Category | Total | Implemented | Skeleton | Not Started |
|----------|-------|-------------|----------|-------------|
| Ring 0 Services | 19 | 16 | 3 | 0 |
| Ring 1 Services | 8 | 6 | 1 | 1 |
| Ring 2 Services | 41 | 11 | 28 | 2 |
| UI Applications | 24 | ~24 | 0 | 0 |
| Shared Libraries | 12 | 12 | 0 | 0 |
| Infrastructure | 9 | 9 | 0 | 0 |
| **Total** | **113** | **~78** | **32** | **3** |

---

## 6. Port Assignment Registry

| Port | Service | Ring |
|------|---------|------|
| 3000 | one-ui-shell | UI |
| 3001 | ops-console | UI |
| 3002 | ehr | UI |
| 3003 | portal | UI |
| 3004 | ops-docs | UI |
| 3005 | self-service | UI |
| 3006 | butano-web | UI |
| 3007 | developer-console | UI |
| 3008 | zibo-web | UI |
| 3009 | oros-web | UI |
| 3010 | pharmacy-web | UI |
| 3011 | inventory-web | UI |
| 3012 | msika-web / msika-flow-portal | UI |
| 3013 | msika-flow-vendor | UI |
| 3014 | msika-flow-ops | UI |
| 3016 | mushex-payer-portal | UI |
| 3017 | mushex-finance-console | UI |
| 3018 | mushex-ops-console | UI |
| 3019 | support-console | UI |
| 3020 | experience UI | UI |
| 3021 | pct-web | UI |
| 4242 | Orthanc DICOM | Infra |
| 5432 | PostgreSQL | Infra |
| 6379 | Redis | Infra |
| 8042 | Orthanc Web | Infra |
| 8080 | Keycloak | Infra |
| 8081 | TSHEPO (authz) | Ring 0 |
| 8082 | VITO | Ring 0 |
| 8083 | VARAPI | Ring 0 |
| 8084 | TUSO | Ring 0 |
| 8085 | ZIBO | Ring 0 |
| 8086 | MSIKA | Ring 0 |
| 8087 | MUSHEX | Ring 0 |
| 8088 | PCT | Ring 1 |
| 8089 | OROS | Ring 1 |
| 8090 | HAPI FHIR (BUTANO) | Ring 0 / Infra |
| 8091 | Card Print Agent | Ring 2 |
| 8092 | Landela Adapter / FHIR Gateway | Ring 2 |
| 8094 | Credential Verification | Ring 2 |
| 8095 | Share Slip / Offline Sync | Ring 2 |
| 8096 | Pharmacy / PACS Adapter | Ring 1 / Ring 2 |
| 8097 | Product Registry | Ring 2 |
| 8098 | Inventory / Inventory eLMIS Adapter | Ring 2 |
| 8099 | Pharmacy eLMIS Adapter | Ring 2 |
| 8100 | MSIKA Flow | Ring 1 |
| 8101 | COSTA (Costing Engine) | Ring 1 |
| 8110 | Integration Hub | Ring 2 |
| 8130 | Channels | Ring 2 |
| 8140 | Data Pipeline | Ring 2 |
| 8150 | Connector FHIR Adapter / NDR | Ring 2 |
| 8160 | Experience BFF | Experience |
| 8170 | Data Access Governance (DAGS) | Ring 2 |
| 8180 | Surveillance | Ring 2 |
| 8181 | TSHEPO Identity / OPA | Ring 0 / Infra |
| 8182 | TSHEPO Consent | Ring 0 |
| 8183 | TSHEPO Audit | Ring 0 |
| 8184 | TSHEPO Keys | Ring 0 |
| 8185 | TSHEPO Offline | Ring 0 |
| 8190 | Campaigns | Ring 2 |
| 8200 | Identity Assurance | Ring 2 |
| 8210 | Data Ingestion / Observability | Ring 2 |
| 8220 | Data Governance / Security Hardening | Ring 2 |
| 8230 | NDR / Data Warehouse | Ring 2 |
| 8310 | Asset Registry | Ring 2 |
| 8330 | IoT Ingestion | Ring 2 |
| 8350 | Audit Ledger | Ring 2 |
| 8360 | Offline Edge | Ring 2 |
| 8370 | Developer Portal | Ring 0 |
| 8371 | Schema Registry | Ring 0 |
| 9000 | MinIO API | Infra |
| 9001 | MinIO Console | Infra |
| 9092 | Kafka | Infra |
| 9901 | Envoy Admin | Infra |
| 10000 | Envoy Gateway | Infra |

> **Note**: Some port numbers are shared between services that would not run simultaneously in local dev, or represent port conflicts to be resolved in deployment configuration.
