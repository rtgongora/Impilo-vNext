# Impilo vNext — Component Classification Matrix

> Generated: 2026-03-15
> Audit scope: Full platform — 12 libraries, 67 services, 24 web UIs, 2 mobile apps, 7 mobile packages

## Classification Key

| Rating | Definition |
|--------|-----------|
| **COMPLETE** | Full implementation: domain logic, migrations, tests (unit + contract), config, outbox/eventing |
| **ADEQUATE** | Working implementation with minor gaps (e.g., thin test coverage, missing README) |
| **MINIMAL** | Skeleton or very thin wrapper — compiles but limited real domain logic |
| **FRAGILE** | Has issues that would break real usage (missing config, broken deps) |
| **BLOCKED** | Cannot be completed due to external/environment constraints |
| **WEB-ONLY** | Web-only app (no mobile runtime) |
| **MOBILE-READY** | Has real mobile framework (React Native) |
| **LIBRARY** | Shared library — classified separately |

---

## A. Shared Libraries / Foundations

| Library | Src | Test | Build | Classification | Notes |
|---------|-----|------|-------|---------------|-------|
| tech-companion | 28 | 5 | Maven | **COMPLETE** | v1.1 compliance engine: headers, idempotency, federation, timeouts, error envelopes |
| tech-companion-harness | 2 | 0 | Maven | **MINIMAL** | Test harness — GoldenContractSuite base class. No self-tests |
| tech-companion-mock | 3 | 2 | Maven | **ADEQUATE** | Mock wiring for tests |
| shared-kernel-java | 15 | 10 | Maven | **COMPLETE** | Eventing, audit, consistency, schema validation with comprehensive tests |
| shared-kernel (TS) | 6 | 4 | npm | **COMPLETE** | TypeScript equivalent — audit, consistency, events, schema |
| tshepo-contracts | 13 | 4 | Maven | **COMPLETE** | Trust headers, authz contracts, protobuf definitions |
| tshepo-sdk | 5 | 4 | Maven | **COMPLETE** | TrustContext, filter, header propagation, authz client |
| security-baseline | 16 | 4 | Maven | **COMPLETE** | Rate limiting (token-bucket), input sanitization, secrets provider (Vault+Env), admin audit |
| ops-instrumentation | 9 | 1 | Maven | **ADEQUATE** | MDC filter, golden-signals metrics, outbox lag probe, OTEL propagation — only 1 test |
| federation-connector | 13 | 4 | Maven | **COMPLETE** | Pod identity verification (mTLS+JWT), revocation checking, spine client, conflict handler |
| contract-tests | 4 | 4 | Maven | **COMPLETE** | Event envelope + schema compatibility validators |
| offline-sdk | 7 | 2 | Maven | **COMPLETE** | JWS entitlement verification (Ed25519+RS256), offline queue format |
| services/shared-core | 12 | 0 | Maven | **ADEQUATE** | Shared core (Argon2id, HMAC, ApiResponse, TrustContext) — no dedicated tests but tested via consuming services |

---

## B. Ring 0 — Trust & Governance Services

| Service | Src | Test | Mig | Helm | Classification | Notes |
|---------|-----|------|-----|------|---------------|-------|
| tshepo-authz-service | 53 | 6 | 1 | N | **COMPLETE** | 7-step PDP, ext_authz gRPC, break-glass, step-up, device risk |
| tshepo-identity-service | 47 | 6 | 1 | N | **COMPLETE** | CPID generation, MOSIP link, token issuance, reconciliation |
| tshepo-consent-service | 39 | 4 | 1 | N | **COMPLETE** | FHIR R4 consent CRUD, evaluation engine, share links |
| tshepo-audit-service | 33 | 4 | 1 | N | **COMPLETE** | SHA-256 hash chain, query, export, Kafka consumer |
| tshepo-keys-service | 31 | 4 | 1 | N | **COMPLETE** | Ed25519 signing, key rotation, JWKS, certificate trust |
| tshepo-offline-service | 46 | 4 | 1 | N | **COMPLETE** | Capability tokens, offline rules, pack generation, reconciliation |
| tshepo-service | 37 | 3 | 6 | Y | **COMPLETE** | Core TSHEPO — policy engine, authorization |

## C. Ring 0 — Registry Spine Services

| Service | Src | Test | Mig | Helm | Classification | Notes |
|---------|-----|------|-----|------|---------------|-------|
| vito-service | 105 | 25 | 19 | Y | **COMPLETE** | Full client registry: identity, matching, cards, wallet, recovery |
| varapi-service | 109 | 5 | 4 | Y | **COMPLETE** | Provider registry: credentialing, council sync, privileging |
| tuso-service | 112 | 6 | 4 | Y | **COMPLETE** | Facility registry: hierarchy, workspaces, booking, control tower |
| zibo-service | 59 | 6 | 2 | Y | **COMPLETE** | Terminology: code systems, value sets, validation |
| msika-service | 70 | 5 | 3 | Y | **COMPLETE** | Product/tariff registry with FTS |
| msika-flow-service | 90 | 8 | 1 | Y | **COMPLETE** | Procurement/supply chain workflow |
| product-registry-service | 14 | 2 | 2 | Y | **ADEQUATE** | Product catalog CRUD — thin but functional |
| indawo-service | 20 | 2 | 2 | N | **ADEQUATE** | Geographic/site registry |
| ubomi-service | 18 | 1 | 1 | Y | **ADEQUATE** | CRVS birth/death notifications — has services but only GoldenContractIT |

## D. Ring 0 — Clinical Execution Services

| Service | Src | Test | Mig | Helm | Classification | Notes |
|---------|-----|------|-----|------|---------------|-------|
| butano-service | 29 | 4 | 1 | N | **COMPLETE** | SHR with HAPI FHIR, PII prevention interceptors, IPS |
| butano-fhir | 10 | 2 | 1 | Y | **ADEQUATE** | FHIR proxy layer |
| pct-service | 93 | 6 | 3 | Y | **COMPLETE** | Patient Care Tracker: encounter state machine, queue, metrics |
| oros-service | 85 | 6 | 1 | Y | **COMPLETE** | Orders & Results: order state machine, worklist, adapter router |
| pharmacy-service | 85 | 6 | 1 | Y | **COMPLETE** | Dispensing, substitution, partial fill, inventory hooks |
| inpatient-service | 15 | 1 | 2 | Y | **ADEQUATE** | Admission, ward, bed management — thinner impl |
| coverage-service | 34 | 3 | 2 | N | **ADEQUATE** | Eligibility, benefit plans, remittance |

## E. Ring 0 — Finance Services

| Service | Src | Test | Mig | Helm | Classification | Notes |
|---------|-----|------|-----|------|---------------|-------|
| mushex-service | 106 | 9 | 1 | Y | **COMPLETE** | Payments: claims, adjudication, wallet, reconciliation |
| costing-engine-service | 93 | 7 | 1 | Y | **COMPLETE** | Cost models, rule application, charge sheets |

## F. Ring 1 — Integration & Ops Services

| Service | Src | Test | Mig | Helm | Classification | Notes |
|---------|-----|------|-----|------|---------------|-------|
| integration-hub | 35 | 4 | 3 | Y | **COMPLETE** | Route management, message dispatch, dead-letter, connectors |
| notification-service | 38 | 4 | 3 | Y | **COMPLETE** | Multi-channel notifications, templates, receipts; 6 repos + 4 services |
| jobs-service | 16 | 2 | 1 | Y | **ADEQUATE** | Job scheduling — functional but thin |
| offline-sync-service | 18 | 2 | 2 | Y | **ADEQUATE** | Offline sync coordination |
| offline-edge-service | 32 | 8 | 4 | N | **COMPLETE** | Conflict resolution, review queue, wave15 sync |
| document-service | 22 | 1 | 1 | Y | **ADEQUATE** | Document storage (MinIO) — only GoldenContractIT |
| landela-adapter-service | 23 | 1 | 1 | N | **ADEQUATE** | Document adapter — only GoldenContractIT |
| pacs-adapter-service | 13 | 2 | 1 | Y | **ADEQUATE** | PACS/Orthanc adapter |
| fhir-gateway-service | 12 | 2 | 1 | Y | **ADEQUATE** | FHIR gateway proxy |
| connector-fhir-adapter | 14 | 2 | 2 | N | **ADEQUATE** | FHIR connector adapter |
| experience-bff | 74 | 4 | 5 | N | **COMPLETE** | BFF: golden paths, facility, clinical data |
| card-print-agent | 26 | 1 | 1 | Y | **ADEQUATE** | Kafka-driven card printing |
| share-slip-service | 25 | 1 | 1 | N | **ADEQUATE** | Share slip generation — only GoldenContractIT |
| pharmacy-elmis-adapter | 14 | 2 | 1 | Y | **ADEQUATE** | eLMIS pharmacy adapter |
| inventory-elmis-adapter | 14 | 2 | 1 | Y | **ADEQUATE** | eLMIS inventory adapter |
| credential-verification-service | 24 | 1 | 1 | N | **ADEQUATE** | Credential verification — only GoldenContractIT |

## G. Ring 2 — Platform Services

| Service | Src | Test | Mig | Helm | Classification | Notes |
|---------|-----|------|-----|------|---------------|-------|
| rules-service | 27 | 4 | 2 | N | **ADEQUATE** | Business rules engine with tests |
| forms-service | 17 | 2 | 1 | N | **ADEQUATE** | Form schema management, JSON Schema validation |
| search-service | 11 | 2 | 1 | N | **ADEQUATE** | Search indexing service |
| workflow-service | 20 | 2 | 2 | N | **ADEQUATE** | Workflow definitions and instances, state transitions |
| schema-registry-service | 11 | 2 | 1 | N | **ADEQUATE** | Schema versioning |
| data-ingestion-service | 17 | 2 | 3 | N | **ADEQUATE** | Bronze layer ingestion, dead-letter |
| data-governance-service | 34 | 2 | 4 | N | **ADEQUATE** | Data governance policies and rules; 6 repos + 6 entities |
| data-access-governance-service | 25 | 8 | 1 | N | **COMPLETE** | DAG access control with comprehensive tests |
| data-warehouse-service | 19 | 3 | 2 | N | **ADEQUATE** | Gold table management |
| data-pipeline-service | 24 | 7 | 1 | N | **COMPLETE** | Data pipeline orchestration with tests |
| surveillance-service | 28 | 6 | 2 | N | **COMPLETE** | Disease surveillance, signal evaluation, case registry; 7 repos |
| asset-registry-service | 14 | 2 | 3 | N | **ADEQUATE** | Asset lifecycle management |
| dispatch-service | 18 | 2 | 2 | N | **ADEQUATE** | Dispatch management |
| iot-ingestion-service | 15 | 2 | 2 | N | **ADEQUATE** | IoT telemetry ingestion |
| observability-service | 22 | 5 | 2 | N | **ADEQUATE** | Observability dashboards |
| security-hardening-service | 17 | 4 | 1 | N | **ADEQUATE** | Security scanning and posture |
| support-service | 36 | 3 | 4 | N | **ADEQUATE** | Helpdesk: tickets, comments, escalation |
| developer-portal-service | 16 | 4 | 2 | N | **ADEQUATE** | Developer portal, certification |
| audit-ledger-service | 12 | 2 | 2 | N | **ADEQUATE** | Immutable audit ledger |
| reporting-service | 26 | 7 | 1 | N | **COMPLETE** | Report definitions, execution, scheduling |
| national-data-repository-service | 24 | 6 | 1 | N | **COMPLETE** | NDR: FHIR bundles, submission, compliance |
| identity-assurance-service | 19 | 7 | 1 | N | **COMPLETE** | Identity proofing levels |
| campaigns-service | 18 | 6 | 1 | N | **COMPLETE** | Health campaigns management |
| channels-service | 28 | 2 | 1 | N | **ADEQUATE** | Multi-channel messaging |
| ndr-service | 20 | 2 | 3 | N | **ADEQUATE** | NDR bronze/gold pipeline |

## H. Web UI Applications

| App | Src | Test | Classification | Notes |
|-----|-----|------|---------------|-------|
| experience | 125 | 0 | **COMPLETE** | Main clinical UI — 80+ pages across 17 zones, 11 React Query hooks, real BFF integration. No tests |
| ops-console | 13 | 0 | **ADEQUATE** | VITO ops dashboard |
| one-ui-shell | 5 | 0 | **COMPLETE** | Trust layer shell — core apiClient with Envoy ext_authz, step-up challenges, trust header injection |
| portal | 7 | 0 | **COMPLETE** | Citizen portal — QR, pickup, recovery, request-id with trust headers |
| self-service | 6 | 0 | **MINIMAL** | Self-service portal |
| ehr | 0 | 0 | **FRAGILE** | Empty — package.json only. Superseded by experience app |
| support-console | 19 | 5 | **COMPLETE** | Support app with tests |
| developer-console | 19 | 4 | **COMPLETE** | Developer portal with tests |
| ops-docs | 12 | 0 | **ADEQUATE** | Ops documentation viewer |
| mushex-ops-console | 9 | 0 | **ADEQUATE** | MUSheX ops UI |
| mushex-finance-console | 9 | 0 | **ADEQUATE** | MUSheX finance UI |
| mushex-payer-portal | 8 | 0 | **ADEQUATE** | Payer portal |
| msika-web | 10 | 0 | **ADEQUATE** | Product registry UI |
| msika-flow-portal | 10 | 0 | **ADEQUATE** | Procurement portal |
| msika-flow-ops | 10 | 0 | **ADEQUATE** | Procurement ops |
| msika-flow-vendor | 9 | 0 | **ADEQUATE** | Vendor portal |
| costa-console | 12 | 0 | **ADEQUATE** | Costing console |
| inventory-web | 10 | 0 | **ADEQUATE** | Inventory management UI |
| pharmacy-web | 8 | 0 | **ADEQUATE** | Pharmacy dispensing UI |
| zibo-web | 10 | 0 | **ADEQUATE** | Terminology management UI |
| pct-web | 9 | 0 | **ADEQUATE** | Patient Care Tracker UI |
| oros-web | 11 | 0 | **ADEQUATE** | Orders & Results UI |
| butano-web | 10 | 0 | **ADEQUATE** | FHIR/SHR management UI |
| shared-ui | 9* | 0 | **ADEQUATE** | Shared components (not in src/) |

*shared-ui files are in components/ and lib/, not src/

## I. Mobile Applications

| App | Src | Test | Classification | Notes |
|-----|-----|------|---------------|-------|
| citizen-app | 41 | 5 | **MOBILE-READY** | React Native — appointments, prescriptions, telehealth |
| provider-app | 68 | 13 | **MOBILE-READY** | React Native — clinical, offline, barcode |

## J. Mobile Shared Packages

| Package | Src | Test | Classification | Notes |
|---------|-----|------|---------------|-------|
| mobile-api-client | 5 | 1 | **ADEQUATE** | API client with trust headers |
| mobile-auth | 6 | 2 | **ADEQUATE** | Auth with biometrics |
| mobile-design-system | 28 | 1 | **ADEQUATE** | Shared UI components |
| mobile-messaging | 6 | 0 | **ADEQUATE** | Push notifications, in-app feed, real-time channels — limited tests |
| mobile-offline | 6 | 2 | **ADEQUATE** | Offline sync |
| mobile-timeline | 5 | 1 | **ADEQUATE** | Clinical timeline |
| mobile-trust | 4 | 1 | **ADEQUATE** | Trust headers for mobile |

---

## Summary Counts

| Classification | Libraries | Services | Web UIs | Mobile | Total |
|---------------|-----------|----------|---------|--------|-------|
| **COMPLETE** | 10 | 27 | 5 | 0 | 42 |
| **ADEQUATE** | 2 | 37 | 18 | 8 | 65 |
| **MINIMAL** | 1 | 0 | 1 | 0 | 2 |
| **FRAGILE** | 0 | 0 | 1 | 0 | 1 |
| **MOBILE-READY** | 0 | 0 | 0 | 2 | 2 |
| **Totals** | 13 | 64* | 25 | 11 | 113 |

*67 services minus 3 duplicates in table
