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
| 8 | **mvumo-service** | 8195 | `mvumo` | Sovereign digital consent orchestration (Ring 0; pairs with tshepo-consent for FHIR enforcement); templates, sessions, proof | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented / evolving |

#### Core Registries (Ring 0)

| # | Service | Port | Database | Purpose | Tech Stack | Status |
|---|---------|------|----------|---------|------------|--------|
| 9 | **vito-service** | 8082 | `vito` | Client Registry / MPI — CRID/CPID management, dedup/merge, federation merges | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 10 | **varapi-service** | 8083 | `varapi` | Provider Registry — licensure, privileges, credential verification, revocation | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 11 | **tuso-service** | 8084 | `tuso` | Facility Registry — topology, Control Tower, bookings, geo, capacity | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 12 | **zibo-service** | 8085 | `zibo` | Terminology & Semantic Governance — FHIR CodeSystem, ValueSet, ConceptMap | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |
| 13 | **msika-service** | 8086 | `msika` | Product & Service Registry — catalogs, tariffs, packs, formulary | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |

#### Health Record & Finance (Ring 0)

| # | Service | Port | Database | Purpose | Tech Stack | Status |
|---|---------|------|----------|---------|------------|--------|
| 14 | **butano-service** | — | `butano` | BUTANO orchestration layer — SHR write coordination, CPID-only (no PII) | Java 21, Spring Boot 3.3, PostgreSQL, Kafka | Implemented |
| 15 | **butano-fhir** | 8090 | `butano_fhir` | HAPI FHIR R4 server — longitudinal health record, IPS, visit timeline | HAPI FHIR 7.4, PostgreSQL | Implemented |
| 16 | **ubomi-service** | — | `—` | CRVS Interface — births/deaths linkage to national registries | Java 21, Spring Boot 3.3 | Implemented |
| 17 | **mushex-service** | 8102 | `mushex_db` | Finance Engine — payments, claims adjudication, settlement, fraud detection, ledger | Java 21, Spring Boot 3.3, PostgreSQL, Redis, Kafka | Implemented |

#### Ring 0 Platform Primitives

| # | Service | Port | Database | Purpose | Tech Stack | Status |
|---|---------|------|----------|---------|------------|--------|
| 18 | **schema-registry-service** | 8371 | `impilo_schema_registry` | Event schema governance, compatibility gates, CI validation | Java 21, Spring Boot 3.3, PostgreSQL | Skeleton |
| 19 | **developer-portal-service** | 8370 | `impilo_dev_portal` | Client registration, API key issuance, sandbox, deprecation tracking | Java 21, Spring Boot 3.3, PostgreSQL | Skeleton |
| 20 | **observability-service** | 8211 | `observability` | Dashboard & alert registry, CRUD, outbox event publishing | Java 21, Spring Boot 3.3, PostgreSQL | Skeleton |

---

### 1.2 Ring 1 — Clinical Plane

| # | Service | Port | Database | Purpose | Ring 0 Dependencies | Status |
|---|---------|------|----------|---------|---------------------|--------|
| 21 | **pct-service** | 8088 | `pct` | Patient Care Tracker — care journeys, encounters, queues, triage, discharge | TSHEPO, VITO, TUSO, BUTANO, MUSHEX | Implemented |
| 22 | **oros-service** | 8089 | `oros` | Orders & Results Orchestration — orders, worklists, worksteps, SLA tracking | TSHEPO, BUTANO, ZIBO, TUSO | Implemented |
| 23 | **costing-engine-service** | 8101 | `costing` / `costa_db` | COSTA — billing, tariffs, exemptions, claims packing | TSHEPO, MSIKA, MUSHEX | Implemented |
| 24 | **pharmacy-service** | 8096 | `pharmacy` | Dispense workflow — stock, FEFO, barcode, pickup proof | TSHEPO, OROS, MUSHEX, MSIKA | Implemented |
| 25 | **msika-flow-service** | 8100 | `msika_flow` | Health Marketplace — orders, fulfillment, vendor management, booking | TSHEPO, MSIKA, MUSHEX, VARAPI | Implemented |
| 26 | **inpatient-service** | 8121 | `inpatient` | Admissions, ward transfers, bed assignments, discharges | TSHEPO, TUSO, PCT | Skeleton |
| 27 | **coverage-service** | 8140 | `impilo_coverage` | Coverage & eligibility engine, pre-authorization, claims lifecycle | TSHEPO, MUSHEX | Implemented |
| 28 | **indawo-service** | 8150 | `impilo_indawo` | Location & address registry, geocoding, catchment areas | TSHEPO, TUSO | Implemented |

---

### 1.3 Ring 2 — Supply, Data, Integration

#### Supply Chain

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 29 | **inventory-service** | 8098 | `inventory` | Supply chain, stock visibility, requisitions, handovers | Implemented |
| 30 | **inventory-elmis-adapter** | 8108 | `inventory_elmis` | External eLMIS stock sync, receipt forwarding, reconciliation | Implemented |
| 31 | **pharmacy-elmis-adapter** | 8099 | `pharmacy_elmis` | External pharmacy eLMIS stock sync, order forwarding | Implemented |
| 32 | **product-registry-service** | 8097 | `product_registry` | Product master data (planned merge into MSIKA) | Implemented |

#### Document & Credential Suite

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 33 | **document-service** | 8093 | `document_service` | MinIO/S3 document storage, pre-signed URLs, AV scanning | Implemented |
| 34 | **landela-adapter-service** | 8092 | `landela_adapter` | Document gateway (Landela/MinIO integration) | Implemented |
| 35 | **credential-verification-service** | 8094 | `credential_verification` | Ed25519 signed PDFs + QR verification | Implemented |
| 36 | **card-print-agent** | 8291 | `card_print` | Smart card printing agent | Implemented |
| 37 | **share-slip-service** | 8104 | `share_slip` | OTP-based delegated pickup slips | Implemented |

#### Integration & Interoperability

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 38 | **integration-hub** | 8110 | `integration_hub` | DHIS2, eLMIS, LIMS, iHRIS adapters — event routing & dispatch | Skeleton |
| 39 | **fhir-gateway-service** | 8091 | `fhir_gateway` | FHIR boundary routing & audit gateway | Skeleton |
| 40 | **connector-fhir-adapter** | 8151 | `impilo_connector_fhir` | FHIR bundle validation, header audit, relay routing | Skeleton |
| 41 | **pacs-adapter-service** | 8113 | `pacs` | DICOM/Orthanc PACS integration — imaging metadata gateway | Skeleton |
| 42 | **channels-service** | 8130 | `impilo_channels` | Omnichannel access (USSD, WhatsApp, SMS, IVR), session mgmt | Skeleton |

#### Notifications & Workflow

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 43 | **notification-service** | 8200 | `notification` | Multi-channel notification engine (SMS/email/push), template management | Skeleton |
| 44 | **workflow-service** | 8250 | `impilo_workflow` | Versioned workflow definitions, instance lifecycle, DMN-ish decision surface | Skeleton |
| 45 | **jobs-service** | 8109 | `jobs` | Job definitions, triggers, execution records, scheduling | Skeleton |
| 46 | **dispatch-service** | 8320 | `impilo_dispatch` | Supply chain dispatch, delivery tracking, cold-chain compliance | Skeleton |
| 47 | **rules-service** | 8241 | `impilo_rules` | Lightweight rule evaluation, recursive descent parser, decision logging | Skeleton |
| 48 | **forms-service** | 8240 | `impilo_forms` | JSON Schema-based form definitions, versioning, payload validation | Skeleton |

#### Data, Analytics & Reporting

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 49 | **reporting-service** | 8176 | `reporting` | BI reports, operational dashboards | Skeleton |
| 50 | **ndr-service** | 8232 | `ndr` | National Data Repository — aggregated de-identified analytics | Skeleton |
| 51 | **national-data-repository-service** | 8152 | `impilo_ndr` | NDR analytics-ready store — versioned tables, materialized views | Skeleton |
| 52 | **data-warehouse-service** | 8233 | `impilo_data_warehouse` | Gold dataset materializer — bronze events to analytical datasets | Skeleton |
| 53 | **data-pipeline-service** | 8215 | `pipeline` | EventEnvelope ingestion, curated pipeline records, watermarks | Skeleton |
| 54 | **data-ingestion-service** | 8210 | `impilo_data_ingestion` | Facility/external data submission ingestion & routing | Skeleton |
| 55 | **search-service** | 8230 | `impilo_search` | SQL ILIKE-based entity search & indexing with pagination | Skeleton |
| 56 | **surveillance-service** | 8180 | `surv` | Disease surveillance (eIDSR) — signal detection, case registry | Skeleton |
| 57 | **campaigns-service** | 8190 | `camp` | Public health campaign management & outreach | Skeleton |

#### Governance & Security

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 58 | **data-governance-service** | 8220 | `impilo_data_governance` | Data quality rules, lineage tracking, metadata cataloging | Skeleton |
| 59 | **data-access-governance-service** | 8170 | `dags` | Policy registry, access-request workflow, permit-token issuance | Skeleton |
| 60 | **security-hardening-service** | 8221 | `secharden` | Policy pack registry, compliance scan results | Skeleton |
| 61 | **audit-ledger-service** | 8350 | `impilo_audit_ledger` | Append-only SHA-256 hash-chained audit ledger (no UPDATE/DELETE) | Skeleton |
| 62 | **identity-assurance-service** | 8201 | `impilo_identity_assurance` | Step-up checks, device attestation, risk scoring | Skeleton |

#### Offline & Edge

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 63 | **offline-sync-service** | 8095 | `offline_sync` | Queued action intake, replay, conflict resolution | Skeleton |
| 64 | **offline-edge-service** | 8360 | `impilo_offline_edge` | Offline trust controls — event capture, signed entitlements, recon | Skeleton |

#### IoT & Devices

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 65 | **iot-ingestion-service** | 8330 | `impilo_iot_ingestion` | Device telemetry ingestion (HTTP + Kafka), backpressure/DLQ | Skeleton |
| 66 | **asset-registry-service** | 8310 | `impilo_asset_registry` | Medical equipment, cold-chain, vehicle lifecycle tracking | Skeleton |

#### Support

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 67 | **support-service** | 8340 | `impilo_support` | Help desk, ticketing, incident reports, knowledge articles | Skeleton |
| 68 | **experience-bff** | 8160 | `experience_bff` | Backend-for-Frontend aggregation layer (PCT, VITO, Mvumo, …) | Java 21, Spring Boot 3.3 | Implemented |

#### Shared Core

| # | Service | Port | Database | Purpose | Status |
|---|---------|------|----------|---------|--------|
| 69 | **shared-core** | — | — | Shared domain objects, DTOs, utilities used across services | Java 21 library | Implemented |

---

## 2. UI Applications

All UIs use **Next.js 14.2.x**, **TypeScript 5.5**, **TailwindCSS 3.4**, **Radix UI**, **TanStack Query**, and **Zustand** unless noted otherwise.

### 2.1 Shell & Core

| # | App | Port | Purpose | Backend Dependencies |
|---|-----|------|---------|---------------------|
| 1 | **one-ui-shell** | 3000 | **Impilo web experience** — single actor-facing orchestration layer (Work/Pro/Life zones, clinical + ops + life flows, trust headers, federation host). Not a separate “shell” product from the Experience plane. | Envoy Gateway → all services |
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
| 14 | **mushex-finance-console** | 3017 | _Deprecated reference sidecar. Canonical replacement: `/finance/costa`, `/finance/mushex-platform`, `/finance/billing`, `/finance/payments`, `/finance/settlements`, `/finance/ledger` in `ui/one-ui-shell`. Source retained for parity; see `ui/mushex-finance-console/DEPRECATED.md`._ | MUSHEX service |
| 15 | **mushex-ops-console** | 3018 | _Deprecated reference sidecar. Canonical replacement: `/finance/payer-ops`, `/finance/payer-claims`, `/finance/reconciliation`, `/finance/refunds` in `ui/one-ui-shell`. Source retained for parity; see `ui/mushex-ops-console/DEPRECATED.md`._ | MUSHEX service |
| 16 | **mushex-payer-portal** | 3016 | _Deprecated reference sidecar. Canonical replacement: `/finance/payer-claims`, `/finance/payer-ops`, `/finance/mushex-platform` in `ui/one-ui-shell`. Source retained for parity; see `ui/mushex-payer-portal/DEPRECATED.md`._ | MUSHEX service, Coverage |
| 17 | **costa-console** | — | Costing console — tariffs, billing, exemptions (legacy; canonical entry point is `/finance/costa` in `ui/one-ui-shell`). | Costing Engine service |

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
| 22 | **ops-docs** | 3004 | Operations documentation & document management console | Landela, CVS, Card Print |

### 2.7 Shared UI Library

| # | App | Port | Purpose |
|---|-----|------|---------|
| 23 | **shared-ui** | — | Shared React component library — design system, Radix primitives, form components |

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
| 3021 | pct-web | UI |
| 4242 | Orthanc DICOM | Infra |
| 5432 | PostgreSQL | Infra |
| 6379 | Redis | Infra |
| 8042 | Orthanc Web | Infra |
| 8080 | Keycloak | Infra |
| 8079 | TSHEPO legacy monolith (`tshepo-service`) | Ring 0 |
| 8081 | TSHEPO Authz (HTTP) | Ring 0 |
| 8082 | VITO | Ring 0 |
| 8083 | VARAPI | Ring 0 |
| 8084 | TUSO | Ring 0 |
| 8085 | ZIBO | Ring 0 |
| 8086 | MSIKA | Ring 0 |
| 8087 | UBOMI | Ring 1 |
| 8088 | PCT | Ring 1 |
| 8089 | OROS | Ring 1 |
| 8090 | HAPI FHIR (BUTANO) | Ring 0 / Infra |
| 8091 | FHIR Gateway | Ring 2 |
| 8092 | Landela Adapter | Ring 2 |
| 8093 | Document Service | Ring 2 |
| 8094 | Credential Verification | Ring 2 |
| 8095 | Offline Sync | Ring 2 |
| 8096 | Pharmacy | Ring 1 |
| 8097 | Product Registry | Ring 2 |
| 8098 | Inventory | Ring 2 |
| 8099 | Pharmacy eLMIS Adapter | Ring 2 |
| 8100 | MSIKA Flow | Ring 1 |
| 8101 | COSTA (Costing Engine) | Ring 1 |
| 8102 | MUSheX | Ring 1 |
| 8104 | Share Slip | Ring 2 |
| 8108 | Inventory eLMIS Adapter | Ring 2 |
| 8109 | Jobs Service | Ring 2 |
| 8110 | Integration Hub | Ring 2 |
| 8113 | PACS Adapter | Ring 2 |
| 8121 | Inpatient | Ring 1 |
| 8130 | Channels | Ring 2 |
| 8140 | Coverage | Ring 1 |
| 8150 | INDAWO | Ring 1 |
| 8151 | Connector FHIR Adapter | Ring 2 |
| 8152 | National Data Repository | Ring 2 |
| 8160 | Experience BFF | Experience |
| 8176 | Reporting | Ring 2 |
| 8170 | Data Access Governance (DAGS) | Ring 2 |
| 8180 | Surveillance | Ring 2 |
| 8181 | TSHEPO Identity | Ring 0 |
| 8182 | TSHEPO Consent | Ring 0 |
| 8183 | TSHEPO Audit | Ring 0 |
| 8184 | TSHEPO Keys | Ring 0 |
| 8185 | TSHEPO Offline | Ring 0 |
| 8190 | Campaigns | Ring 2 |
| 8200 | Notification | Ring 2 |
| 8201 | Identity Assurance | Ring 2 |
| 8210 | Data Ingestion | Ring 2 |
| 8211 | Observability | Ring 2 |
| 8215 | Data Pipeline | Ring 2 |
| 8220 | Data Governance | Ring 2 |
| 8221 | Security Hardening | Ring 2 |
| 8230 | Search | Ring 2 |
| 8232 | NDR | Ring 2 |
| 8233 | Data Warehouse | Ring 2 |
| 8240 | Forms | Ring 2 |
| 8241 | Rules | Ring 2 |
| 8250 | Workflow | Ring 2 |
| 8260 | Guidance | Ring 2 |
| 8270 | Clinical Knowledge Platform | Ring 2 |
| 8289 | BUTANO FHIR layer | Ring 2 |
| 8291 | Card Print Agent | Ring 2 |
| 8310 | Asset Registry | Ring 2 |
| 8320 | Dispatch Service | Ring 2 |
| 8330 | IoT Ingestion | Ring 2 |
| 8340 | Support Service | Ring 2 |
| 8350 | Audit Ledger | Ring 2 |
| 8360 | Offline Edge | Ring 2 |
| 8370 | Developer Portal | Ring 0 |
| 8371 | Schema Registry | Ring 0 |
| 9090 | TSHEPO Authz (gRPC) | Ring 0 |
| 9000 | MinIO API | Infra |
| 9001 | MinIO Console | Infra |
| 9092 | Kafka | Infra |
| 9901 | Envoy Admin | Infra |
| 10000 | Envoy Gateway | Infra |

> **Note:** Unique defaults are defined in [`docs/runbooks/port-allocation.md`](../../runbooks/port-allocation.md) (Phase A0). Override with `SERVER_PORT` when needed.

---

## Doctrine References

The MusheX entries in this catalogue (`mushex-service`, the sidecar UIs `mushex-finance-console`, `mushex-ops-console`, `mushex-payer-portal`, and every "MUSHEX" dependency column entry in section 1) are governed by:

- [`../doctrine/mushex-gateway-neutrality.md`](../doctrine/mushex-gateway-neutrality.md) — MusheX dual-mode operating doctrine (orchestration gateway vs. direct/default gateway; gateway neutrality; canonical MusheX surfaces in `ui/one-ui-shell`).
- [`../doctrine/costa-mushex-billing-timing.md`](../doctrine/costa-mushex-billing-timing.md) — costing, billing-timing, and settlement separation between COSTA and MusheX.

The three sidecar UIs above are **deprecated** in favour of `ui/one-ui-shell` finance and wallet surfaces. Each carries a local `DEPRECATED.md` marker that names the canonical replacement pages and BFF route families:

- [`../../ui/mushex-finance-console/DEPRECATED.md`](../../ui/mushex-finance-console/DEPRECATED.md)
- [`../../ui/mushex-ops-console/DEPRECATED.md`](../../ui/mushex-ops-console/DEPRECATED.md)
- [`../../ui/mushex-payer-portal/DEPRECATED.md`](../../ui/mushex-payer-portal/DEPRECATED.md)

Catalogue status (`Implemented`) for those three sidecars is preserved here as a historical record; new feature work must not be added to them. Retirement is tracked under audit gap **G-6** in [`../audits/costa-mushex-experience-layer-wiring-audit.md`](../audits/costa-mushex-experience-layer-wiring-audit.md).
