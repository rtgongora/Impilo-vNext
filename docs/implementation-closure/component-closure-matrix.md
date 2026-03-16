# Component Closure Matrix — Implementation Closure Wave

Generated: 2026-03-16

## Classification Key
- **COMPLETE** — Real code, runtime entrypoint, business logic, config, migrations, tests, docs
- **LIBRARY** — Shared library consumed by other services (no runtime entrypoint)
- **BLOCKED_EXTERNAL** — Requires external system not available in-repo

---

## Backend Services (68 total)

| # | Service | Classification | App | Ctrl | Migration | Tests | Outbox | Notes |
|---|---------|---------------|-----|------|-----------|-------|--------|-------|
| 1 | tshepo-service | COMPLETE | Y | Y | Y | Y | Y | Policy engine with real facility auth |
| 2 | tshepo-authz-service | COMPLETE | Y | Y | Y | Y | Y | gRPC ext_authz |
| 3 | tshepo-identity-service | COMPLETE | Y | Y | Y | Y | Y | |
| 4 | tshepo-consent-service | COMPLETE | Y | Y | Y | Y | Y | |
| 5 | tshepo-audit-service | COMPLETE | Y | Y | Y | Y | Y | |
| 6 | tshepo-keys-service | COMPLETE | Y | Y | Y | Y | Y | |
| 7 | tshepo-offline-service | COMPLETE | Y | Y | Y | Y | Y | |
| 8 | vito-service | COMPLETE | Y | Y | Y | Y | Y | PII registry |
| 9 | varapi-service | COMPLETE | Y | Y | Y | Y | Y | Real MinIO/S3 doc storage |
| 10 | tuso-service | COMPLETE | Y | Y | Y | Y | Y | Facility/workspace/shift |
| 11 | zibo-service | COMPLETE | Y | Y | Y | Y | Y | IoT devices |
| 12 | butano-service | COMPLETE | Y | Y | Y | Y | Y | FHIR IPS bundles |
| 13 | butano-fhir | COMPLETE | Y | Y | Y | Y | Y | HAPI FHIR facade |
| 14 | oros-service | COMPLETE | Y | Y | Y | Y | Y | Orders/results |
| 15 | pct-service | COMPLETE | Y | Y | Y | Y | Y | Clinical pathways |
| 16 | msika-service | COMPLETE | Y | Y | Y | Y | Y | Procurement |
| 17 | msika-flow-service | COMPLETE | Y | Y | Y | Y | Y | Workflow |
| 18 | mushex-service | COMPLETE | Y | Y | Y | Y | Y | MuSHEx |
| 19 | ubomi-service | COMPLETE | Y | Y | Y | Y | Y | Wellness |
| 20 | notification-service | COMPLETE | Y | Y | Y | Y | Y | Real SMTP+SMS providers |
| 21 | jobs-service | COMPLETE | Y | Y | Y | Y | Y | Async jobs |
| 22 | offline-sync-service | COMPLETE | Y | Y | Y | Y | Y | |
| 23 | pharmacy-service | COMPLETE | Y | Y | Y | Y | Y | |
| 24 | inventory-service | COMPLETE | Y | Y | Y | Y | Y | |
| 25 | inpatient-service | COMPLETE | Y | Y | Y | Y | Y | |
| 26 | document-service | COMPLETE | Y | Y | Y | Y | Y | |
| 27 | integration-hub | COMPLETE | Y | Y | Y | Y | Y | |
| 28 | experience-bff | COMPLETE | Y | Y | - | Y | - | BFF, no own persistence |
| 29 | reporting-service | COMPLETE | Y | Y | Y | Y | Y | Real SQL execution engine |
| 30 | national-data-repository-service | COMPLETE | Y | Y | Y | Y | Y | |
| 31 | ndr-service | COMPLETE | Y | Y | Y | Y | Y | |
| 32 | data-warehouse-service | COMPLETE | Y | Y | Y | Y | Y | |
| 33 | data-pipeline-service | COMPLETE | Y | Y | Y | Y | Y | |
| 34 | data-ingestion-service | COMPLETE | Y | Y | Y | Y | Y | |
| 35 | data-governance-service | COMPLETE | Y | Y | Y | Y | Y | |
| 36 | data-access-governance-service | COMPLETE | Y | Y | Y | Y | Y | |
| 37 | campaigns-service | COMPLETE | Y | Y | Y | Y | Y | Real dispatch+enrollment |
| 38 | surveillance-service | COMPLETE | Y | Y | Y | Y | Y | Real signal detection |
| 39 | security-hardening-service | COMPLETE | Y | Y | Y | Y | Y | Policy packs + scans |
| 40 | observability-service | COMPLETE | Y | Y | Y | Y | Y | Dashboards + alerts |
| 41 | identity-assurance-service | COMPLETE | Y | Y | Y | Y | Y | |
| 42 | credential-verification-service | COMPLETE | Y | Y | Y | Y | Y | |
| 43 | card-print-agent | COMPLETE | Y | Y | Y | Y | Y | Real IPP network printer |
| 44 | share-slip-service | COMPLETE | Y | Y | Y | Y | Y | |
| 45 | costing-engine-service | COMPLETE | Y | Y | Y | Y | Y | |
| 46 | coverage-service | COMPLETE | Y | Y | Y | Y | Y | |
| 47 | product-registry-service | COMPLETE | Y | Y | Y | Y | Y | |
| 48 | fhir-gateway-service | COMPLETE | Y | Y | Y | Y | Y | |
| 49 | connector-fhir-adapter | COMPLETE | Y | Y | Y | Y | Y | |
| 50 | pacs-adapter-service | COMPLETE | Y | Y | Y | Y | Y | |
| 51 | pharmacy-elmis-adapter | COMPLETE | Y | Y | Y | Y | Y | |
| 52 | inventory-elmis-adapter | COMPLETE | Y | Y | Y | Y | Y | |
| 53 | landela-adapter-service | COMPLETE | Y | Y | Y | Y | Y | |
| 54 | support-service | COMPLETE | Y | Y | Y | Y | Y | |
| 55 | developer-portal-service | COMPLETE | Y | Y | Y | Y | Y | |
| 56 | channels-service | COMPLETE | Y | Y | Y | Y | Y | |
| 57 | dispatch-service | COMPLETE | Y | Y | Y | Y | Y | |
| 58 | rules-service | COMPLETE | Y | Y | Y | Y | Y | |
| 59 | workflow-service | COMPLETE | Y | Y | Y | Y | Y | |
| 60 | forms-service | COMPLETE | Y | Y | Y | Y | Y | |
| 61 | search-service | COMPLETE | Y | Y | Y | Y | Y | |
| 62 | schema-registry-service | COMPLETE | Y | Y | Y | Y | Y | |
| 63 | audit-ledger-service | COMPLETE | Y | Y | Y | Y | Y | |
| 64 | iot-ingestion-service | COMPLETE | Y | Y | Y | Y | Y | |
| 65 | asset-registry-service | COMPLETE | Y | Y | Y | Y | Y | |
| 66 | offline-edge-service | COMPLETE | Y | Y | Y | Y | Y | |
| 67 | indawo-service | COMPLETE | Y | Y | Y | Y | Y | |
| 68 | shared-core | LIBRARY | - | - | - | - | - | Shared Java library |

## Shared Libraries (12 total)

| # | Library | Classification | Notes |
|---|---------|---------------|-------|
| 1 | tshepo-contracts | LIBRARY | Proto definitions, trust headers |
| 2 | security-baseline | LIBRARY | Real Vault client, rate limiting, sanitisation |
| 3 | ops-instrumentation | LIBRARY | Structured logging, metrics |
| 4 | federation-connector | LIBRARY | Federation authority handling |
| 5 | shared-kernel | LIBRARY | TypeScript shared kernel |
| 6 | shared-kernel-java | LIBRARY | Java shared kernel (EventEnvelope) |
| 7 | tshepo-sdk | LIBRARY | TSHEPO SDK for service integration |
| 8 | offline-sdk | LIBRARY | Offline-first patterns |
| 9 | tech-companion | LIBRARY | v1.1 auto-configuration |
| 10 | tech-companion-harness | LIBRARY | Golden contract test harness |
| 11 | tech-companion-mock | LIBRARY | Mock companion for testing |
| 12 | contract-tests | LIBRARY | Cross-service contract tests |

## Client Applications

| # | Application | Classification | Framework | Native Mobile |
|---|------------|---------------|-----------|---------------|
| 1 | citizen-app | COMPLETE | Expo/React Native | Android + iOS |
| 2 | provider-app | COMPLETE | Expo/React Native | Android + iOS |
| 3 | mobile-design-system | LIBRARY | React Native | Shared package |
| 4 | mobile-api-client | LIBRARY | TypeScript | Shared package |
| 5 | mobile-auth | LIBRARY | TypeScript | Keycloak integration |
| 6 | mobile-trust | LIBRARY | TypeScript | Trust headers |
| 7 | mobile-messaging | LIBRARY | React Native | Chat/messaging |
| 8 | mobile-timeline | LIBRARY | React Native | Timeline UI |
| 9 | mobile-offline | LIBRARY | TypeScript | Offline storage |

## Web Applications (24 total)

| # | Application | Classification | Files |
|---|------------|---------------|-------|
| 1 | one-ui-shell | COMPLETE | 6 |
| 2 | ehr | COMPLETE | 14 |
| 3 | experience | COMPLETE | 126 |
| 4 | ops-console | COMPLETE | 13 |
| 5 | mushex-ops-console | COMPLETE | 10 |
| 6 | support-console | COMPLETE | 20 |
| 7 | developer-console | COMPLETE | 20 |
| 8 | portal | COMPLETE | 8 |
| 9 | self-service | COMPLETE | 7 |
| 10 | pharmacy-web | COMPLETE | 9 |
| 11 | inventory-web | COMPLETE | 11 |
| 12 | zibo-web | COMPLETE | 11 |
| 13 | butano-web | COMPLETE | 11 |
| 14 | pct-web | COMPLETE | 10 |
| 15 | oros-web | COMPLETE | 12 |
| 16 | msika-web | COMPLETE | 11 |
| 17 | msika-flow-portal | COMPLETE | 11 |
| 18 | msika-flow-ops | COMPLETE | 11 |
| 19 | msika-flow-vendor | COMPLETE | 10 |
| 20 | costa-console | COMPLETE | 13 |
| 21 | mushex-finance-console | COMPLETE | 10 |
| 22 | mushex-payer-portal | COMPLETE | 9 |
| 23 | ops-docs | COMPLETE | 13 |
| 24 | shared-ui | LIBRARY | 7 |

## Infrastructure

| Component | Classification | Notes |
|-----------|---------------|-------|
| docker-compose.yml | COMPLETE | Dev infrastructure (PG, Redis, Kafka) |
| docker-compose.runtime.yml | COMPLETE | Full runtime (457 lines) |
| docker-compose.build.yml | COMPLETE | Build pipeline |
| infra/envoy/ | COMPLETE | Envoy proxy + ext_authz config |
| scripts/seed/ | COMPLETE | Database init for all services |
| Keycloak | BLOCKED_EXTERNAL | Requires realm import (not in repo) |
| MinIO | COMPLETE | Configured in docker-compose |
| HAPI FHIR | BLOCKED_EXTERNAL | External FHIR server |
| Orthanc PACS | BLOCKED_EXTERNAL | External PACS server |

## Summary

| Category | Total | COMPLETE | LIBRARY | BLOCKED_EXTERNAL |
|----------|-------|----------|---------|-------------------|
| Backend Services | 68 | 67 | 1 | 0 |
| Shared Libraries | 12 | 0 | 12 | 0 |
| Mobile Apps | 9 | 2 | 7 | 0 |
| Web Apps | 24 | 23 | 1 | 0 |
| Infrastructure | 7 | 4 | 0 | 3 |
| **TOTAL** | **120** | **96** | **21** | **3** |
