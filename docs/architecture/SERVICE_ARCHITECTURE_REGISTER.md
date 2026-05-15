# Impilo vNext Service Architecture Register

## Purpose
This register is the canonical source of truth for Impilo vNext service ownership, architecture classification, and boundary governance across Rings and Planes.

## Governance Doctrine
The Service Architecture Register is the single source of truth for Impilo vNext service ownership. Rings describe operational criticality and dependency level. Planes describe architectural responsibility and system-of-record ownership. Every service must have one Ring and one primary Plane before it can be accepted into the platform.

A service that is not in the register does not officially exist in vNext.

## Relationship Between Rings and Planes
- Rings classify runtime criticality and dependency depth.
- Planes classify architecture responsibility and system-of-record ownership.
- A component may depend on multiple Planes, but it owns exactly one primary Plane.
- Ring assignment does not override Plane ownership.

## Ring Definitions
| Ring | Definition |
|---|---|
| Ring 0 Kernel | Sovereign, foundational platform services with strictest controls. |
| Ring 1 Execution | Operational and care execution services in direct delivery paths. |
| Ring 2 Scale | Scale, integration, analytics, and operational support services. |
| Infrastructure | Runtime platform components and foundational infrastructure. |
| User Interface | Web and mobile applications. |
| Library | Reusable shared packages, SDKs, contracts, and frameworks. |
| External | External systems and dependencies outside the repository runtime boundary. |
| Unclear | Insufficient evidence for definitive classification. |

## Plane Definitions
| Primary Plane | Definition |
|---|---|
| Trust & Governance | Identity, consent, authorization, audit, policy, risk, and trust controls. |
| Registry Spine | Authoritative registries and master/reference data ownership. |
| Clinical Execution | Clinical care workflows and shared health records. |
| Finance & Resource | Finance, claims, settlement, payment and resource costing truth. |
| Integration & Operations | Interoperability, orchestration, jobs, messaging, observability, and data movement operations. |
| Experience | User-facing orchestration and interaction surfaces. |
| Enterprise Resource | Workforce, learning, institutional operations, and enterprise support capabilities. |
| Unclear | Insufficient evidence for definitive primary Plane. |

## Category And Bundle Label Definitions
| Category Display | Meaning |
|---|---|
| Kernel | Sovereign platform kernel services. |
| Clinical | Clinical care and execution capabilities. |
| Data | Public health, intelligence, and analytics capabilities. |
| Integration | Interoperability and routing capabilities. |
| Supply | Supply-chain and logistics capabilities. |
| Experience | User journey and interaction capabilities. |
| Assurance | Identity, trust, risk, and compliance capabilities. |
| Resilience | Reliability and observability capabilities. |
| Finance | Financial lifecycle capabilities. |
| Enterprise | Enterprise administration and workforce capabilities. |
| Registry | Master and reference registry capabilities. |
| Infrastructure | Platform infrastructure capabilities. |
| User Interface | Frontend and mobile applications. |
| External | External dependencies and partner systems. |
| Unclear | Evidence-insufficient category assignment. |

## Classification Rules
- Every entry has exactly one Ring.
- Every entry has exactly one primary Plane.
- Secondary Planes indicate integration/support, not ownership of truth.
- Plane assignment follows system-of-record responsibility, not folder placement or branding.
- Low-confidence and unclear classifications are tracked in unresolved sections.

## Complete Service Inventory
| Service | Type | Ring | Primary Plane | Category | Status | System Of Record For | Frontend Surface | Evidence | Confidence |
|---|---|---|---|---|---|---|---|---|---|
| Banking Rails | external dependency | External | Finance & Resource | Finance | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| DHIS2 | external dependency | External | Integration & Operations | Data | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| External eLMIS | external dependency | External | Integration & Operations | Supply | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| External PACS Network | external dependency | External | Integration & Operations | Integration | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| LIMS | external dependency | External | Integration & Operations | Integration | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| SMS/WhatsApp Gateway | external dependency | External | Integration & Operations | Integration | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| Civil Registry System | external dependency | External | Registry Spine | Registry | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| External Identity Provider | external dependency | External | Trust & Governance | Assurance | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| MOSIP | external dependency | External | Trust & Governance | Assurance | Live | Not Declared | Not Declared | docs/plan/SERVICE_CATALOG.md, contracts/openapi | High |
| Envoy Gateway | infrastructure | Infrastructure | Integration & Operations | Integration | Live | Not Declared | Not Declared | infra/envoy/envoy.yaml | High |
| Grafana | infrastructure | Infrastructure | Integration & Operations | Resilience | Live | Not Declared | Not Declared | ops/runtime/docker-compose.observability.yml | High |
| Kafka | infrastructure | Infrastructure | Integration & Operations | Integration | Live | Not Declared | Not Declared | docker-compose.yml | High |
| Loki | infrastructure | Infrastructure | Integration & Operations | Resilience | Live | Not Declared | Not Declared | ops/runtime/docker-compose.observability.yml | High |
| MinIO | infrastructure | Infrastructure | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | docker-compose.yml | High |
| Orthanc PACS | infrastructure | Infrastructure | Integration & Operations | Integration | Live | Not Declared | Not Declared | docker-compose.yml | High |
| OpenTelemetry Collector | infrastructure | Infrastructure | Integration & Operations | Resilience | Live | Not Declared | Not Declared | ops/runtime/docker-compose.observability.yml | High |
| PostgreSQL | infrastructure | Infrastructure | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | docker-compose.yml | High |
| Prometheus | infrastructure | Infrastructure | Integration & Operations | Resilience | Live | Not Declared | Not Declared | ops/runtime/docker-compose.observability.yml | High |
| Redis | infrastructure | Infrastructure | Integration & Operations | Resilience | Live | Not Declared | Not Declared | docker-compose.yml | High |
| Schema Registry (Apicurio) | infrastructure | Infrastructure | Integration & Operations | Integration | Live | Not Declared | Not Declared | docker-compose.yml | High |
| Keycloak | infrastructure | Infrastructure | Trust & Governance | Assurance | Live | Not Declared | Not Declared | docker-compose.yml | High |
| Vault | infrastructure | Infrastructure | Trust & Governance | Assurance | Live | Not Declared | Not Declared | ops/runtime/docker-compose.kernel.yml | High |
| Supply Planning | shared library | Library | Enterprise Resource | Supply | Skeleton | Not Declared | Not Declared | libs/supply-planning/pom.xml, libs/supply-planning/README.md | Medium |
| Mobile Api Client | shared library | Library | Experience | User Interface | Live | Not Declared | Not Declared | apps/mobile/packages/mobile-api-client | High |
| Mobile Design System | shared library | Library | Experience | User Interface | Live | Not Declared | Not Declared | apps/mobile/packages/mobile-design-system | High |
| Mobile Messaging | shared library | Library | Experience | User Interface | Live | Not Declared | Not Declared | apps/mobile/packages/mobile-messaging | High |
| Mobile Offline | shared library | Library | Experience | User Interface | Live | Not Declared | Not Declared | apps/mobile/packages/mobile-offline | High |
| Mobile Timeline | shared library | Library | Experience | User Interface | Live | Not Declared | Not Declared | apps/mobile/packages/mobile-timeline | High |
| Chaos Testing | shared library | Library | Integration & Operations | Resilience | Skeleton | Not Declared | Not Declared | libs/chaos-testing/pom.xml, libs/chaos-testing/README.md | Medium |
| Contract Tests | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/contract-tests | High |
| Federation Connector | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/federation-connector | High |
| Offline Sdk | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/offline-sdk | High |
| Ops Instrumentation | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/ops-instrumentation | High |
| Security Baseline | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/security-baseline | High |
| Shared Core | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | services/shared-core/pom.xml | High |
| Shared Kernel | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/shared-kernel | High |
| Shared Kernel Java | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/shared-kernel-java | High |
| Tech Companion | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/tech-companion | High |
| Tech Companion Harness | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/tech-companion-harness | High |
| Tech Companion Mock | shared library | Library | Integration & Operations | Infrastructure | Live | Not Declared | Not Declared | libs/tech-companion-mock | High |
| Mobile Auth | shared library | Library | Trust & Governance | Assurance | Live | Not Declared | Not Declared | apps/mobile/packages/mobile-auth | High |
| Mobile Trust | shared library | Library | Trust & Governance | Assurance | Live | Not Declared | Not Declared | apps/mobile/packages/mobile-trust | High |
| Tshepo Contracts | shared library | Library | Trust & Governance | Assurance | Live | Not Declared | Not Declared | libs/tshepo-contracts | High |
| Tshepo Sdk | shared library | Library | Trust & Governance | Assurance | Live | Not Declared | Not Declared | libs/tshepo-sdk | High |
| Vault KMS | shared library | Library | Trust & Governance | Assurance | Skeleton | Not Declared | Not Declared | libs/vault-kms/pom.xml, libs/vault-kms/README.md | Medium |
| Butano Fhir | backend service | Ring 0 Kernel | Clinical Execution | Clinical | Live | Butano Fhir canonical records | ui/one-ui-shell routes and domain consoles | services/butano-fhir/pom.xml, contracts/openapi/butano-fhir.openapi.yaml | High |
| Butano | backend service | Ring 0 Kernel | Clinical Execution | Clinical | Live | Butano canonical records | ui/one-ui-shell routes and domain consoles | services/butano-service/pom.xml | High |
| Indawo | backend service | Ring 0 Kernel | Registry Spine | Registry | Live | Indawo canonical records | ui/one-ui-shell routes and domain consoles | services/indawo-service/pom.xml, contracts/openapi/indawo.openapi.yaml | High |
| Msika | backend service | Ring 0 Kernel | Registry Spine | Registry | Live | Msika canonical records | ui/one-ui-shell routes and domain consoles | services/msika-service/pom.xml | High |
| Product Registry | backend service | Ring 0 Kernel | Registry Spine | Registry | Deprecated | Not Declared | ui/one-ui-shell routes and domain consoles | services/product-registry-service/pom.xml, contracts/openapi/product-registry.openapi.yaml | High |
| Tuso | backend service | Ring 0 Kernel | Registry Spine | Registry | Live | Tuso canonical records | ui/one-ui-shell routes and domain consoles | services/tuso-service/pom.xml, contracts/openapi/tuso.openapi.yaml | High |
| Ubomi | backend service | Ring 0 Kernel | Registry Spine | Registry | Live | Ubomi canonical records | ui/one-ui-shell routes and domain consoles | services/ubomi-service/pom.xml, contracts/openapi/ubomi.openapi.yaml | High |
| Varapi | backend service | Ring 0 Kernel | Registry Spine | Registry | Live | Varapi canonical records | ui/one-ui-shell routes and domain consoles | services/varapi-service/pom.xml, contracts/openapi/varapi.openapi.yaml | High |
| Vito | backend service | Ring 0 Kernel | Registry Spine | Registry | Live | Vito canonical records | ui/one-ui-shell routes and domain consoles | services/vito-service/pom.xml, contracts/openapi/vito.openapi.yaml | High |
| Zibo | backend service | Ring 0 Kernel | Registry Spine | Registry | Live | Zibo canonical records | ui/one-ui-shell routes and domain consoles | services/zibo-service/pom.xml, contracts/openapi/zibo.openapi.yaml | High |
| Identity Assurance | backend service | Ring 0 Kernel | Trust & Governance | Assurance | Live | Identity Assurance canonical records | ui/one-ui-shell routes and domain consoles | services/identity-assurance-service/pom.xml, contracts/openapi/identity-assurance.openapi.yaml | High |
| Mvumo | backend service | Ring 0 Kernel | Trust & Governance | Kernel | Live | Mvumo canonical records | ui/one-ui-shell routes and domain consoles | services/mvumo-service/pom.xml, contracts/openapi/mvumo.openapi.yaml | High |
| Tshepo Audit | backend service | Ring 0 Kernel | Trust & Governance | Kernel | Live | Tshepo Audit canonical records | ui/one-ui-shell routes and domain consoles | services/tshepo-audit-service/pom.xml, contracts/openapi/tshepo-audit.openapi.yaml | High |
| Tshepo Authz | backend service | Ring 0 Kernel | Trust & Governance | Kernel | Live | Tshepo Authz canonical records | ui/one-ui-shell routes and domain consoles | services/tshepo-authz-service/pom.xml, contracts/openapi/tshepo-authz.openapi.yaml | High |
| Tshepo Consent | backend service | Ring 0 Kernel | Trust & Governance | Kernel | Live | Tshepo Consent canonical records | ui/one-ui-shell routes and domain consoles | services/tshepo-consent-service/pom.xml, contracts/openapi/tshepo-consent.openapi.yaml | High |
| Tshepo Identity | backend service | Ring 0 Kernel | Trust & Governance | Kernel | Live | Tshepo Identity canonical records | ui/one-ui-shell routes and domain consoles | services/tshepo-identity-service/pom.xml, contracts/openapi/tshepo-identity.openapi.yaml | High |
| Tshepo Keys | backend service | Ring 0 Kernel | Trust & Governance | Kernel | Live | Tshepo Keys canonical records | ui/one-ui-shell routes and domain consoles | services/tshepo-keys-service/pom.xml, contracts/openapi/tshepo-keys.openapi.yaml | High |
| Tshepo Offline | backend service | Ring 0 Kernel | Trust & Governance | Kernel | Live | Tshepo Offline canonical records | ui/one-ui-shell routes and domain consoles | services/tshepo-offline-service/pom.xml, contracts/openapi/tshepo-offline.openapi.yaml | High |
| Clinical Knowledge Platform | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | Clinical Knowledge Platform canonical records | ui/one-ui-shell routes and domain consoles | services/clinical-knowledge-platform-service/pom.xml, contracts/openapi/clinical-knowledge-platform.openapi.yaml | High |
| Forms | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | Forms canonical records | ui/one-ui-shell routes and domain consoles | services/forms-service/pom.xml, contracts/openapi/forms.openapi.yaml | High |
| Guidance | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | Guidance canonical records | ui/one-ui-shell routes and domain consoles | services/guidance-service/pom.xml, contracts/openapi/guidance.openapi.yaml | High |
| Inpatient | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | Inpatient canonical records | ui/one-ui-shell routes and domain consoles | services/inpatient-service/pom.xml, contracts/openapi/inpatient.openapi.yaml | High |
| Oros | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | Oros canonical records | ui/one-ui-shell routes and domain consoles | services/oros-service/pom.xml, contracts/openapi/oros.openapi.yaml | High |
| Pct | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | Pct canonical records | ui/one-ui-shell routes and domain consoles | services/pct-service/pom.xml, contracts/openapi/pct.openapi.yaml | High |
| Pharmacy | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | Pharmacy canonical records | ui/one-ui-shell routes and domain consoles | services/pharmacy-service/pom.xml, contracts/openapi/pharmacy.openapi.yaml | High |
| Referral Service | backend service | Ring 1 Execution | Clinical Execution | Clinical | Skeleton | Referral intake and routing workflows | ui/one-ui-shell referral workflows | services/referral-service/pom.xml, contracts/openapi/referral.openapi.yaml | Medium |
| Scheduling | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | Scheduling canonical records | ui/one-ui-shell routes and domain consoles | services/scheduling-service/pom.xml | High |
| Simba | backend service | Ring 1 Execution | Clinical Execution | Clinical | Live | wellness journeys; lifestyle plans | ui/one-ui-shell routes and domain consoles | services/simba-service/pom.xml, contracts/openapi/simba.openapi.yaml | High |
| Wellness | backend service | Ring 1 Execution | Clinical Execution | Clinical | Deprecated | Not Declared | ui/one-ui-shell routes and domain consoles | services/wellness-service/pom.xml, contracts/openapi/wellness.openapi.yaml | High |
| Hr Payroll | backend service | Ring 2 Scale | Enterprise Resource | Enterprise | Live | Hr Payroll canonical records | ui/one-ui-shell routes and domain consoles | services/hr-payroll-service/pom.xml, contracts/openapi/hr-payroll.openapi.yaml | High |
| Learning | backend service | Ring 2 Scale | Enterprise Resource | Enterprise | Live | Learning canonical records | ui/one-ui-shell routes and domain consoles | services/learning-service/pom.xml, contracts/openapi/learning.openapi.yaml | High |
| Msika Flow | backend service | Ring 2 Scale | Enterprise Resource | Enterprise | Live | Msika Flow canonical records | ui/one-ui-shell routes and domain consoles | services/msika-flow-service/pom.xml, contracts/openapi/msika-flow.openapi.yaml | High |
| Procurement | backend service | Ring 2 Scale | Enterprise Resource | Enterprise | Live | Procurement canonical records | ui/one-ui-shell routes and domain consoles | services/procurement-service/pom.xml, contracts/openapi/procurement.openapi.yaml | High |
| Workforce Governance | backend service | Ring 2 Scale | Enterprise Resource | Enterprise | Live | Workforce Governance canonical records | ui/one-ui-shell routes and domain consoles | services/workforce-governance-service/pom.xml | High |
| Community | backend service | Ring 2 Scale | Experience | Experience | Live | Community canonical records | Web and mobile clients via experience-bff | services/community-service/pom.xml, contracts/openapi/community.openapi.yaml | High |
| Experience Bff | backend service | Ring 2 Scale | Experience | Experience | Live | Experience Bff canonical records | Web and mobile clients via experience-bff | services/experience-bff/pom.xml, contracts/openapi/experience-bff.openapi.yaml | High |
| Costing Engine | backend service | Ring 2 Scale | Finance & Resource | Finance | Live | Costing Engine canonical records | ui/one-ui-shell routes and domain consoles | services/costing-engine-service/pom.xml | High |
| Coverage | backend service | Ring 2 Scale | Finance & Resource | Finance | Live | Coverage canonical records | ui/one-ui-shell routes and domain consoles | services/coverage-service/pom.xml, contracts/openapi/coverage.openapi.yaml | High |
| General Ledger | backend service | Ring 2 Scale | Finance & Resource | Finance | Live | General Ledger canonical records | ui/one-ui-shell routes and domain consoles | services/general-ledger-service/pom.xml, contracts/openapi/general-ledger.openapi.yaml | High |
| Mushe Wallet | backend service | Ring 2 Scale | Finance & Resource | Finance | Live | Mushe Wallet canonical records | ui/one-ui-shell routes and domain consoles | services/mushe-wallet-service/pom.xml, contracts/openapi/mushe-wallet.openapi.yaml | High |
| Mushex | backend service | Ring 2 Scale | Finance & Resource | Finance | Live | Mushex canonical records | ui/one-ui-shell routes and domain consoles | services/mushex-service/pom.xml, contracts/openapi/mushex.openapi.yaml | High |
| Share Slip | backend service | Ring 2 Scale | Finance & Resource | Finance | Live | Share Slip canonical records | ui/one-ui-shell routes and domain consoles | services/share-slip-service/pom.xml, contracts/openapi/share-slip.openapi.yaml | High |
| Ai Model Registry | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Ai Model Registry canonical records | ui/one-ui-shell routes and domain consoles | services/ai-model-registry-service/pom.xml, contracts/openapi/ai-model-registry.openapi.yaml | High |
| Analytics Pipeline Service | backend service | Ring 2 Scale | Integration & Operations | Data | Skeleton | Analytics pipeline orchestration metadata | experience-bff intelligence and reporting surfaces | services/analytics-pipeline-service/pom.xml, contracts/openapi/analytics-pipeline.openapi.yaml | Medium |
| Asset Registry | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Asset Registry canonical records | ui/one-ui-shell routes and domain consoles | services/asset-registry-service/pom.xml, contracts/openapi/asset-registry.openapi.yaml | High |
| Audit Ledger | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Audit Ledger canonical records | ui/one-ui-shell routes and domain consoles | services/audit-ledger-service/pom.xml, contracts/openapi/audit-ledger.openapi.yaml | High |
| Campaigns | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | public-health campaign definitions; campaign outreach plans and schedules | ui/one-ui-shell routes and domain consoles | services/campaigns-service/pom.xml, contracts/openapi/campaigns.openapi.yaml | High |
| Card Print Agent | worker | Ring 2 Scale | Integration & Operations | Integration | Live | Card Print Agent canonical records | ui/one-ui-shell routes and domain consoles | services/card-print-agent/pom.xml | High |
| Channels | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Channels canonical records | ui/one-ui-shell routes and domain consoles | services/channels-service/pom.xml, contracts/openapi/channels.openapi.yaml | High |
| Connector Fhir Adapter | adapter | Ring 2 Scale | Integration & Operations | Integration | Live | Connector Fhir Adapter canonical records | ui/one-ui-shell routes and domain consoles | services/connector-fhir-adapter/pom.xml, contracts/openapi/connector-fhir.openapi.yaml | High |
| Credential Verification | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Credential Verification canonical records | ui/one-ui-shell routes and domain consoles | services/credential-verification-service/pom.xml, contracts/openapi/credential-verification.openapi.yaml | High |
| Data Access Governance | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Data Access Governance canonical records | ui/one-ui-shell routes and domain consoles | services/data-access-governance-service/pom.xml, contracts/openapi/data-access-governance.openapi.yaml | High |
| Data Governance | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Data Governance canonical records | ui/one-ui-shell routes and domain consoles | services/data-governance-service/pom.xml, contracts/openapi/data-governance.openapi.yaml | High |
| Data Ingestion | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Data Ingestion canonical records | ui/one-ui-shell routes and domain consoles | services/data-ingestion-service/pom.xml, contracts/openapi/data-ingestion.openapi.yaml | High |
| Data Pipeline | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Data Pipeline canonical records | ui/one-ui-shell routes and domain consoles | services/data-pipeline-service/pom.xml, contracts/openapi/data-pipeline.openapi.yaml | High |
| Data Warehouse | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Data Warehouse canonical records | ui/one-ui-shell routes and domain consoles | services/data-warehouse-service/pom.xml, contracts/openapi/data-warehouse.openapi.yaml | High |
| Developer Portal | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Developer Portal canonical records | ui/one-ui-shell routes and domain consoles | services/developer-portal-service/pom.xml, contracts/openapi/developer-portal.openapi.yaml | High |
| Dispatch | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Dispatch canonical records | ui/one-ui-shell routes and domain consoles | services/dispatch-service/pom.xml, contracts/openapi/dispatch.openapi.yaml | High |
| Document | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Document canonical records | ui/one-ui-shell routes and domain consoles | services/document-service/pom.xml | High |
| Fhir Gateway | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Fhir Gateway canonical records | ui/one-ui-shell routes and domain consoles | services/fhir-gateway-service/pom.xml, contracts/openapi/fhir-gateway.openapi.yaml | High |
| Integration Hub | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Integration Hub canonical records | ui/one-ui-shell routes and domain consoles | services/integration-hub/pom.xml, contracts/openapi/integration-hub.openapi.yaml | High |
| Inventory Elmis Adapter | adapter | Ring 2 Scale | Integration & Operations | Integration | Live | Inventory Elmis Adapter canonical records | ui/one-ui-shell routes and domain consoles | services/inventory-elmis-adapter/pom.xml, contracts/openapi/inventory-elmis.openapi.yaml | High |
| Inventory | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Inventory canonical records | ui/one-ui-shell routes and domain consoles | services/inventory-service/pom.xml, contracts/openapi/inventory.openapi.yaml | High |
| Iot Ingestion | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Iot Ingestion canonical records | ui/one-ui-shell routes and domain consoles | services/iot-ingestion-service/pom.xml, contracts/openapi/iot-ingestion.openapi.yaml | High |
| Jobs | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Jobs canonical records | ui/one-ui-shell routes and domain consoles | services/jobs-service/pom.xml, contracts/openapi/jobs.openapi.yaml | High |
| Landela Adapter | adapter | Ring 2 Scale | Integration & Operations | Integration | Live | Landela Adapter canonical records | ui/one-ui-shell routes and domain consoles | services/landela-adapter-service/pom.xml, contracts/openapi/landela-adapter.openapi.yaml | High |
| National Data Repository | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | National Data Repository canonical records | ui/one-ui-shell routes and domain consoles | services/national-data-repository-service/pom.xml, contracts/openapi/national-data-repository.openapi.yaml | High |
| Ndr | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Ndr canonical records | ui/one-ui-shell routes and domain consoles | services/ndr-service/pom.xml, contracts/openapi/ndr.openapi.yaml | High |
| Notification | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Notification canonical records | ui/one-ui-shell routes and domain consoles | services/notification-service/pom.xml, contracts/openapi/notification.openapi.yaml | High |
| Observability | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Observability canonical records | ui/one-ui-shell routes and domain consoles | services/observability-service/pom.xml, contracts/openapi/observability.openapi.yaml | High |
| Offline Edge | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Offline Edge canonical records | ui/one-ui-shell routes and domain consoles | services/offline-edge-service/pom.xml, contracts/openapi/offline-edge.openapi.yaml | High |
| Offline Sync | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Offline Sync canonical records | ui/one-ui-shell routes and domain consoles | services/offline-sync-service/pom.xml, contracts/openapi/offline-sync.openapi.yaml | High |
| Pacs Adapter | adapter | Ring 2 Scale | Integration & Operations | Integration | Live | Pacs Adapter canonical records | ui/one-ui-shell routes and domain consoles | services/pacs-adapter-service/pom.xml, contracts/openapi/pacs-adapter.openapi.yaml | High |
| Pharmacy Elmis Adapter | adapter | Ring 2 Scale | Integration & Operations | Integration | Live | Pharmacy Elmis Adapter canonical records | ui/one-ui-shell routes and domain consoles | services/pharmacy-elmis-adapter/pom.xml, contracts/openapi/pharmacy-elmis.openapi.yaml | High |
| Reporting | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Reporting canonical records | ui/one-ui-shell routes and domain consoles | services/reporting-service/pom.xml, contracts/openapi/reporting.openapi.yaml | High |
| Rules | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Rules canonical records | ui/one-ui-shell routes and domain consoles | services/rules-service/pom.xml, contracts/openapi/rules.openapi.yaml | High |
| Schema Registry | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Schema Registry canonical records | ui/one-ui-shell routes and domain consoles | services/schema-registry-service/pom.xml, contracts/openapi/schema-registry.openapi.yaml | High |
| Search | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Search canonical records | ui/one-ui-shell routes and domain consoles | services/search-service/pom.xml, contracts/openapi/search.openapi.yaml | High |
| Security Hardening | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Security Hardening canonical records | ui/one-ui-shell routes and domain consoles | services/security-hardening-service/pom.xml, contracts/openapi/security-hardening.openapi.yaml | High |
| Support | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Support canonical records | ui/one-ui-shell routes and domain consoles | services/support-service/pom.xml, contracts/openapi/support.openapi.yaml | High |
| Surveillance | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | public-health surveillance signals and case aggregates; surveillance alert definitions and epidemiological counters | ui/one-ui-shell routes and domain consoles | services/surveillance-service/pom.xml, contracts/openapi/surveillance.openapi.yaml | High |
| Workflow | backend service | Ring 2 Scale | Integration & Operations | Integration | Live | Workflow canonical records | ui/one-ui-shell routes and domain consoles | services/workflow-service/pom.xml, contracts/openapi/workflow.openapi.yaml | High |
| Tshepo | backend service | Ring 2 Scale | Trust & Governance | Kernel | Live | Tshepo canonical records | ui/one-ui-shell routes and domain consoles | services/tshepo-service/pom.xml, contracts/openapi/tshepo.openapi.yaml | Medium |
| Butano Web | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /butano-web | ui/butano-web, ui/butano-web/package.json | High |
| Citizen App | mobile app | User Interface | Experience | User Interface | Live | Not Declared | mobile clients | apps/mobile/citizen-app, apps/mobile/citizen-app/package.json | High |
| Costa Console | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /costa-console | ui/costa-console, ui/costa-console/package.json | High |
| Developer Console | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /developer-console | ui/developer-console, ui/developer-console/package.json | High |
| Ehr | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /ehr | ui/ehr, ui/ehr/package.json | High |
| Experience | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /experience | ui/experience, ui/experience/package.json | High |
| Inventory Web | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /inventory-web | ui/inventory-web, ui/inventory-web/package.json | High |
| Knowledge Admin | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /knowledge-admin | ui/knowledge-admin, ui/knowledge-admin/package.json | High |
| Msika Flow Ops | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /msika-flow-ops | ui/msika-flow-ops, ui/msika-flow-ops/package.json | High |
| Msika Flow Portal | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /msika-flow-portal | ui/msika-flow-portal, ui/msika-flow-portal/package.json | High |
| Msika Flow Vendor | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /msika-flow-vendor | ui/msika-flow-vendor, ui/msika-flow-vendor/package.json | High |
| Msika Web | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /msika-web | ui/msika-web, ui/msika-web/package.json | High |
| Mushex Finance Console | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /mushex-finance-console | ui/mushex-finance-console, ui/mushex-finance-console/package.json | High |
| Mushex Ops Console | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /mushex-ops-console | ui/mushex-ops-console, ui/mushex-ops-console/package.json | High |
| Mushex Payer Portal | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /mushex-payer-portal | ui/mushex-payer-portal, ui/mushex-payer-portal/package.json | High |
| One Ui Shell | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /one-ui-shell | ui/one-ui-shell, ui/one-ui-shell/package.json | High |
| Ops Console | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /ops-console | ui/ops-console, ui/ops-console/package.json | High |
| Ops Docs | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /ops-docs | ui/ops-docs, ui/ops-docs/package.json | High |
| Oros Web | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /oros-web | ui/oros-web, ui/oros-web/package.json | High |
| Pct Web | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /pct-web | ui/pct-web, ui/pct-web/package.json | High |
| Pharmacy Web | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /pharmacy-web | ui/pharmacy-web, ui/pharmacy-web/package.json | High |
| Portal | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /portal | ui/portal, ui/portal/package.json | High |
| Provider App | mobile app | User Interface | Experience | User Interface | Live | Not Declared | mobile clients | apps/mobile/provider-app, apps/mobile/provider-app/package.json | High |
| Self | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /self-service | ui/self-service, ui/self-service/package.json | High |
| Shared Ui | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /shared-ui | ui/shared-ui, ui/shared-ui/package.json | High |
| Support Console | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /support-console | ui/support-console, ui/support-console/package.json | High |
| Zibo Web | frontend app | User Interface | Experience | User Interface | Live | Not Declared | /zibo-web | ui/zibo-web, ui/zibo-web/package.json | High |

## Ring And Plane Matrix
| Ring | Trust & Governance | Registry Spine | Clinical Execution | Finance & Resource | Integration & Operations | Experience | Enterprise Resource | Unclear | Total |
|---|---|---|---|---|---|---|---|---|---|
| Ring 0 Kernel | 8 | 8 | 2 | 0 | 0 | 0 | 0 | 0 | 18 |
| Ring 1 Execution | 0 | 0 | 11 | 0 | 0 | 0 | 0 | 0 | 11 |
| Ring 2 Scale | 1 | 0 | 0 | 6 | 40 | 2 | 5 | 0 | 54 |
| Infrastructure | 2 | 0 | 0 | 0 | 11 | 0 | 0 | 0 | 13 |
| User Interface | 0 | 0 | 0 | 0 | 0 | 27 | 0 | 0 | 27 |
| Library | 5 | 0 | 0 | 0 | 12 | 5 | 1 | 0 | 23 |
| External | 2 | 1 | 0 | 1 | 5 | 0 | 0 | 0 | 9 |
| Unclear | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## System-Of-Record Matrix
| Service | Primary Plane | System Of Record For |
|---|---|---|
| Banking Rails | Finance & Resource | Not Declared |
| DHIS2 | Integration & Operations | Not Declared |
| External eLMIS | Integration & Operations | Not Declared |
| External PACS Network | Integration & Operations | Not Declared |
| LIMS | Integration & Operations | Not Declared |
| SMS/WhatsApp Gateway | Integration & Operations | Not Declared |
| Civil Registry System | Registry Spine | Not Declared |
| External Identity Provider | Trust & Governance | Not Declared |
| MOSIP | Trust & Governance | Not Declared |
| Envoy Gateway | Integration & Operations | Not Declared |
| Grafana | Integration & Operations | Not Declared |
| Kafka | Integration & Operations | Not Declared |
| Loki | Integration & Operations | Not Declared |
| MinIO | Integration & Operations | Not Declared |
| Orthanc PACS | Integration & Operations | Not Declared |
| OpenTelemetry Collector | Integration & Operations | Not Declared |
| PostgreSQL | Integration & Operations | Not Declared |
| Prometheus | Integration & Operations | Not Declared |
| Redis | Integration & Operations | Not Declared |
| Schema Registry (Apicurio) | Integration & Operations | Not Declared |
| Keycloak | Trust & Governance | Not Declared |
| Vault | Trust & Governance | Not Declared |
| Supply Planning | Enterprise Resource | Not Declared |
| Mobile Api Client | Experience | Not Declared |
| Mobile Design System | Experience | Not Declared |
| Mobile Messaging | Experience | Not Declared |
| Mobile Offline | Experience | Not Declared |
| Mobile Timeline | Experience | Not Declared |
| Chaos Testing | Integration & Operations | Not Declared |
| Contract Tests | Integration & Operations | Not Declared |
| Federation Connector | Integration & Operations | Not Declared |
| Offline Sdk | Integration & Operations | Not Declared |
| Ops Instrumentation | Integration & Operations | Not Declared |
| Security Baseline | Integration & Operations | Not Declared |
| Shared Core | Integration & Operations | Not Declared |
| Shared Kernel | Integration & Operations | Not Declared |
| Shared Kernel Java | Integration & Operations | Not Declared |
| Tech Companion | Integration & Operations | Not Declared |
| Tech Companion Harness | Integration & Operations | Not Declared |
| Tech Companion Mock | Integration & Operations | Not Declared |
| Mobile Auth | Trust & Governance | Not Declared |
| Mobile Trust | Trust & Governance | Not Declared |
| Tshepo Contracts | Trust & Governance | Not Declared |
| Tshepo Sdk | Trust & Governance | Not Declared |
| Vault KMS | Trust & Governance | Not Declared |
| Butano Fhir | Clinical Execution | Butano Fhir canonical records |
| Butano | Clinical Execution | Butano canonical records |
| Indawo | Registry Spine | Indawo canonical records |
| Msika | Registry Spine | Msika canonical records |
| Product Registry | Registry Spine | Product Registry canonical records |
| Tuso | Registry Spine | Tuso canonical records |
| Ubomi | Registry Spine | Ubomi canonical records |
| Varapi | Registry Spine | Varapi canonical records |
| Vito | Registry Spine | Vito canonical records |
| Zibo | Registry Spine | Zibo canonical records |
| Identity Assurance | Trust & Governance | Identity Assurance canonical records |
| Mvumo | Trust & Governance | Mvumo canonical records |
| Tshepo Audit | Trust & Governance | Tshepo Audit canonical records |
| Tshepo Authz | Trust & Governance | Tshepo Authz canonical records |
| Tshepo Consent | Trust & Governance | Tshepo Consent canonical records |
| Tshepo Identity | Trust & Governance | Tshepo Identity canonical records |
| Tshepo Keys | Trust & Governance | Tshepo Keys canonical records |
| Tshepo Offline | Trust & Governance | Tshepo Offline canonical records |
| Clinical Knowledge Platform | Clinical Execution | Clinical Knowledge Platform canonical records |
| Forms | Clinical Execution | Forms canonical records |
| Guidance | Clinical Execution | Guidance canonical records |
| Inpatient | Clinical Execution | Inpatient canonical records |
| Oros | Clinical Execution | Oros canonical records |
| Pct | Clinical Execution | Pct canonical records |
| Pharmacy | Clinical Execution | Pharmacy canonical records |
| Referral Service | Clinical Execution | Not Declared |
| Scheduling | Clinical Execution | Scheduling canonical records |
| Simba | Clinical Execution | wellness journeys; lifestyle plans; self-care plans |
| Wellness | Clinical Execution | patient-linked wellness activities; screening prompts; wellness records |
| Hr Payroll | Enterprise Resource | Hr Payroll canonical records |
| Learning | Enterprise Resource | Learning canonical records |
| Msika Flow | Enterprise Resource | Msika Flow canonical records |
| Procurement | Enterprise Resource | Procurement canonical records |
| Workforce Governance | Enterprise Resource | Workforce Governance canonical records |
| Community | Experience | Community canonical records |
| Experience Bff | Experience | Experience Bff canonical records |
| Costing Engine | Finance & Resource | Costing Engine canonical records |
| Coverage | Finance & Resource | Coverage canonical records |
| General Ledger | Finance & Resource | General Ledger canonical records |
| Mushe Wallet | Finance & Resource | Mushe Wallet canonical records |
| Mushex | Finance & Resource | Mushex canonical records |
| Share Slip | Finance & Resource | Share Slip canonical records |
| Ai Model Registry | Integration & Operations | Ai Model Registry canonical records |
| Analytics Pipeline Service | Integration & Operations | Not Declared |
| Asset Registry | Integration & Operations | Asset Registry canonical records |
| Audit Ledger | Integration & Operations | Audit Ledger canonical records |
| Campaigns | Integration & Operations | public-health campaign definitions; campaign outreach plans and schedules; campaign execution state and coverage metrics |
| Card Print Agent | Integration & Operations | Card Print Agent canonical records |
| Channels | Integration & Operations | Channels canonical records |
| Connector Fhir Adapter | Integration & Operations | Connector Fhir Adapter canonical records |
| Credential Verification | Integration & Operations | Credential Verification canonical records |
| Data Access Governance | Integration & Operations | Data Access Governance canonical records |
| Data Governance | Integration & Operations | Data Governance canonical records |
| Data Ingestion | Integration & Operations | Data Ingestion canonical records |
| Data Pipeline | Integration & Operations | Data Pipeline canonical records |
| Data Warehouse | Integration & Operations | Data Warehouse canonical records |
| Developer Portal | Integration & Operations | Developer Portal canonical records |
| Dispatch | Integration & Operations | Dispatch canonical records |
| Document | Integration & Operations | Document canonical records |
| Fhir Gateway | Integration & Operations | Fhir Gateway canonical records |
| Integration Hub | Integration & Operations | Integration Hub canonical records |
| Inventory Elmis Adapter | Integration & Operations | Inventory Elmis Adapter canonical records |
| Inventory | Integration & Operations | Inventory canonical records |
| Iot Ingestion | Integration & Operations | Iot Ingestion canonical records |
| Jobs | Integration & Operations | Jobs canonical records |
| Landela Adapter | Integration & Operations | Landela Adapter canonical records |
| National Data Repository | Integration & Operations | National Data Repository canonical records |
| Ndr | Integration & Operations | Ndr canonical records |
| Notification | Integration & Operations | Notification canonical records |
| Observability | Integration & Operations | Observability canonical records |
| Offline Edge | Integration & Operations | Offline Edge canonical records |
| Offline Sync | Integration & Operations | Offline Sync canonical records |
| Pacs Adapter | Integration & Operations | Pacs Adapter canonical records |
| Pharmacy Elmis Adapter | Integration & Operations | Pharmacy Elmis Adapter canonical records |
| Reporting | Integration & Operations | Reporting canonical records |
| Rules | Integration & Operations | Rules canonical records |
| Schema Registry | Integration & Operations | Schema Registry canonical records |
| Search | Integration & Operations | Search canonical records |
| Security Hardening | Integration & Operations | Security Hardening canonical records |
| Support | Integration & Operations | Support canonical records |
| Surveillance | Integration & Operations | public-health surveillance signals and case aggregates; surveillance alert definitions and epidemiological counters; notifiable event monitoring telemetry |
| Workflow | Integration & Operations | Workflow canonical records |
| Tshepo | Trust & Governance | Tshepo canonical records |
| Butano Web | Experience | Not Declared |
| Citizen App | Experience | Not Declared |
| Costa Console | Experience | Not Declared |
| Developer Console | Experience | Not Declared |
| Ehr | Experience | Not Declared |
| Experience | Experience | Not Declared |
| Inventory Web | Experience | Not Declared |
| Knowledge Admin | Experience | Not Declared |
| Msika Flow Ops | Experience | Not Declared |
| Msika Flow Portal | Experience | Not Declared |
| Msika Flow Vendor | Experience | Not Declared |
| Msika Web | Experience | Not Declared |
| Mushex Finance Console | Experience | Not Declared |
| Mushex Ops Console | Experience | Not Declared |
| Mushex Payer Portal | Experience | Not Declared |
| One Ui Shell | Experience | Not Declared |
| Ops Console | Experience | Not Declared |
| Ops Docs | Experience | Not Declared |
| Oros Web | Experience | Not Declared |
| Pct Web | Experience | Not Declared |
| Pharmacy Web | Experience | Not Declared |
| Portal | Experience | Not Declared |
| Provider App | Experience | Not Declared |
| Self | Experience | Not Declared |
| Shared Ui | Experience | Not Declared |
| Support Console | Experience | Not Declared |
| Zibo Web | Experience | Not Declared |

## Frontend/API/Event/Database Surface Matrix
| Service | Frontend Surface | API Surface | Events Published | Events Consumed | Database Schema |
|---|---|---|---|---|---|
| Banking Rails | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| DHIS2 | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| External eLMIS | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| External PACS Network | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| LIMS | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| SMS/WhatsApp Gateway | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Civil Registry System | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| External Identity Provider | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| MOSIP | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Envoy Gateway | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Grafana | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Kafka | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Loki | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| MinIO | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Orthanc PACS | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| OpenTelemetry Collector | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| PostgreSQL | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Prometheus | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Redis | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Schema Registry (Apicurio) | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Keycloak | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Vault | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Supply Planning | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Mobile Api Client | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Mobile Design System | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Mobile Messaging | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Mobile Offline | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Mobile Timeline | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Chaos Testing | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Contract Tests | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Federation Connector | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Offline Sdk | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Ops Instrumentation | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Security Baseline | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Shared Core | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Shared Kernel | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Shared Kernel Java | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Tech Companion | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Tech Companion Harness | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Tech Companion Mock | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Mobile Auth | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Mobile Trust | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Tshepo Contracts | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Tshepo Sdk | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Vault KMS | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Butano Fhir | ui/one-ui-shell routes and domain consoles | contracts/openapi/butano-fhir.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Butano | ui/one-ui-shell routes and domain consoles | Not Declared | Not Declared | Not Declared | Not Declared |
| Indawo | ui/one-ui-shell routes and domain consoles | contracts/openapi/indawo.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Msika | ui/one-ui-shell routes and domain consoles | Not Declared | Not Declared | Not Declared | Not Declared |
| Product Registry | ui/one-ui-shell routes and domain consoles | contracts/openapi/product-registry.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Tuso | ui/one-ui-shell routes and domain consoles | contracts/openapi/tuso.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Ubomi | ui/one-ui-shell routes and domain consoles | contracts/openapi/ubomi.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Varapi | ui/one-ui-shell routes and domain consoles | contracts/openapi/varapi.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Vito | ui/one-ui-shell routes and domain consoles | contracts/openapi/vito.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Zibo | ui/one-ui-shell routes and domain consoles | contracts/openapi/zibo.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Identity Assurance | ui/one-ui-shell routes and domain consoles | contracts/openapi/identity-assurance.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Mvumo | ui/one-ui-shell routes and domain consoles | contracts/openapi/mvumo.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Tshepo Audit | ui/one-ui-shell routes and domain consoles | contracts/openapi/tshepo-audit.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Tshepo Authz | ui/one-ui-shell routes and domain consoles | contracts/openapi/tshepo-authz.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Tshepo Consent | ui/one-ui-shell routes and domain consoles | contracts/openapi/tshepo-consent.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Tshepo Identity | ui/one-ui-shell routes and domain consoles | contracts/openapi/tshepo-identity.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Tshepo Keys | ui/one-ui-shell routes and domain consoles | contracts/openapi/tshepo-keys.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Tshepo Offline | ui/one-ui-shell routes and domain consoles | contracts/openapi/tshepo-offline.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Clinical Knowledge Platform | ui/one-ui-shell routes and domain consoles | contracts/openapi/clinical-knowledge-platform.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Forms | ui/one-ui-shell routes and domain consoles | contracts/openapi/forms.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Guidance | ui/one-ui-shell routes and domain consoles | contracts/openapi/guidance.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Inpatient | ui/one-ui-shell routes and domain consoles | contracts/openapi/inpatient.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Oros | ui/one-ui-shell routes and domain consoles | contracts/openapi/oros.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Pct | ui/one-ui-shell routes and domain consoles | contracts/openapi/pct.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Pharmacy | ui/one-ui-shell routes and domain consoles | contracts/openapi/pharmacy.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Referral Service | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Scheduling | ui/one-ui-shell routes and domain consoles | Not Declared | Not Declared | Not Declared | Not Declared |
| Simba | ui/one-ui-shell routes and domain consoles | contracts/openapi/simba.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Wellness | ui/one-ui-shell routes and domain consoles | contracts/openapi/wellness.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Hr Payroll | ui/one-ui-shell routes and domain consoles | contracts/openapi/hr-payroll.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Learning | ui/one-ui-shell routes and domain consoles | contracts/openapi/learning.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Msika Flow | ui/one-ui-shell routes and domain consoles | contracts/openapi/msika-flow.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Procurement | ui/one-ui-shell routes and domain consoles | contracts/openapi/procurement.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Workforce Governance | ui/one-ui-shell routes and domain consoles | Not Declared | Not Declared | Not Declared | Not Declared |
| Community | Web and mobile clients via experience-bff | contracts/openapi/community.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Experience Bff | Web and mobile clients via experience-bff | contracts/openapi/experience-bff.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Costing Engine | ui/one-ui-shell routes and domain consoles | Not Declared | Not Declared | Not Declared | Not Declared |
| Coverage | ui/one-ui-shell routes and domain consoles | contracts/openapi/coverage.openapi.yaml | Not Declared | Not Declared | Not Declared |
| General Ledger | ui/one-ui-shell routes and domain consoles | contracts/openapi/general-ledger.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Mushe Wallet | ui/one-ui-shell routes and domain consoles | contracts/openapi/mushe-wallet.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Mushex | ui/one-ui-shell routes and domain consoles | contracts/openapi/mushex.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Share Slip | ui/one-ui-shell routes and domain consoles | contracts/openapi/share-slip.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Ai Model Registry | ui/one-ui-shell routes and domain consoles | contracts/openapi/ai-model-registry.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Analytics Pipeline Service | Not Declared | Not Declared | Not Declared | Not Declared | Not Declared |
| Asset Registry | ui/one-ui-shell routes and domain consoles | contracts/openapi/asset-registry.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Audit Ledger | ui/one-ui-shell routes and domain consoles | contracts/openapi/audit-ledger.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Campaigns | ui/one-ui-shell routes and domain consoles | contracts/openapi/campaigns.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Card Print Agent | ui/one-ui-shell routes and domain consoles | Not Declared | Not Declared | Not Declared | Not Declared |
| Channels | ui/one-ui-shell routes and domain consoles | contracts/openapi/channels.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Connector Fhir Adapter | ui/one-ui-shell routes and domain consoles | contracts/openapi/connector-fhir.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Credential Verification | ui/one-ui-shell routes and domain consoles | contracts/openapi/credential-verification.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Data Access Governance | ui/one-ui-shell routes and domain consoles | contracts/openapi/data-access-governance.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Data Governance | ui/one-ui-shell routes and domain consoles | contracts/openapi/data-governance.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Data Ingestion | ui/one-ui-shell routes and domain consoles | contracts/openapi/data-ingestion.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Data Pipeline | ui/one-ui-shell routes and domain consoles | contracts/openapi/data-pipeline.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Data Warehouse | ui/one-ui-shell routes and domain consoles | contracts/openapi/data-warehouse.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Developer Portal | ui/one-ui-shell routes and domain consoles | contracts/openapi/developer-portal.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Dispatch | ui/one-ui-shell routes and domain consoles | contracts/openapi/dispatch.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Document | ui/one-ui-shell routes and domain consoles | Not Declared | Not Declared | Not Declared | Not Declared |
| Fhir Gateway | ui/one-ui-shell routes and domain consoles | contracts/openapi/fhir-gateway.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Integration Hub | ui/one-ui-shell routes and domain consoles | contracts/openapi/integration-hub.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Inventory Elmis Adapter | ui/one-ui-shell routes and domain consoles | contracts/openapi/inventory-elmis.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Inventory | ui/one-ui-shell routes and domain consoles | contracts/openapi/inventory.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Iot Ingestion | ui/one-ui-shell routes and domain consoles | contracts/openapi/iot-ingestion.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Jobs | ui/one-ui-shell routes and domain consoles | contracts/openapi/jobs.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Landela Adapter | ui/one-ui-shell routes and domain consoles | contracts/openapi/landela-adapter.openapi.yaml | Not Declared | Not Declared | Not Declared |
| National Data Repository | ui/one-ui-shell routes and domain consoles | contracts/openapi/national-data-repository.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Ndr | ui/one-ui-shell routes and domain consoles | contracts/openapi/ndr.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Notification | ui/one-ui-shell routes and domain consoles | contracts/openapi/notification.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Observability | ui/one-ui-shell routes and domain consoles | contracts/openapi/observability.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Offline Edge | ui/one-ui-shell routes and domain consoles | contracts/openapi/offline-edge.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Offline Sync | ui/one-ui-shell routes and domain consoles | contracts/openapi/offline-sync.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Pacs Adapter | ui/one-ui-shell routes and domain consoles | contracts/openapi/pacs-adapter.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Pharmacy Elmis Adapter | ui/one-ui-shell routes and domain consoles | contracts/openapi/pharmacy-elmis.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Reporting | ui/one-ui-shell routes and domain consoles | contracts/openapi/reporting.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Rules | ui/one-ui-shell routes and domain consoles | contracts/openapi/rules.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Schema Registry | ui/one-ui-shell routes and domain consoles | contracts/openapi/schema-registry.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Search | ui/one-ui-shell routes and domain consoles | contracts/openapi/search.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Security Hardening | ui/one-ui-shell routes and domain consoles | contracts/openapi/security-hardening.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Support | ui/one-ui-shell routes and domain consoles | contracts/openapi/support.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Surveillance | ui/one-ui-shell routes and domain consoles | contracts/openapi/surveillance.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Workflow | ui/one-ui-shell routes and domain consoles | contracts/openapi/workflow.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Tshepo | ui/one-ui-shell routes and domain consoles | contracts/openapi/tshepo.openapi.yaml | Not Declared | Not Declared | Not Declared |
| Butano Web | /butano-web | Not Declared | Not Declared | Not Declared | Not Declared |
| Citizen App | mobile clients | Not Declared | Not Declared | Not Declared | Not Declared |
| Costa Console | /costa-console | Not Declared | Not Declared | Not Declared | Not Declared |
| Developer Console | /developer-console | Not Declared | Not Declared | Not Declared | Not Declared |
| Ehr | /ehr | Not Declared | Not Declared | Not Declared | Not Declared |
| Experience | /experience | Not Declared | Not Declared | Not Declared | Not Declared |
| Inventory Web | /inventory-web | Not Declared | Not Declared | Not Declared | Not Declared |
| Knowledge Admin | /knowledge-admin | Not Declared | Not Declared | Not Declared | Not Declared |
| Msika Flow Ops | /msika-flow-ops | Not Declared | Not Declared | Not Declared | Not Declared |
| Msika Flow Portal | /msika-flow-portal | Not Declared | Not Declared | Not Declared | Not Declared |
| Msika Flow Vendor | /msika-flow-vendor | Not Declared | Not Declared | Not Declared | Not Declared |
| Msika Web | /msika-web | Not Declared | Not Declared | Not Declared | Not Declared |
| Mushex Finance Console | /mushex-finance-console | Not Declared | Not Declared | Not Declared | Not Declared |
| Mushex Ops Console | /mushex-ops-console | Not Declared | Not Declared | Not Declared | Not Declared |
| Mushex Payer Portal | /mushex-payer-portal | Not Declared | Not Declared | Not Declared | Not Declared |
| One Ui Shell | /one-ui-shell | Not Declared | Not Declared | Not Declared | Not Declared |
| Ops Console | /ops-console | Not Declared | Not Declared | Not Declared | Not Declared |
| Ops Docs | /ops-docs | Not Declared | Not Declared | Not Declared | Not Declared |
| Oros Web | /oros-web | Not Declared | Not Declared | Not Declared | Not Declared |
| Pct Web | /pct-web | Not Declared | Not Declared | Not Declared | Not Declared |
| Pharmacy Web | /pharmacy-web | Not Declared | Not Declared | Not Declared | Not Declared |
| Portal | /portal | Not Declared | Not Declared | Not Declared | Not Declared |
| Provider App | mobile clients | Not Declared | Not Declared | Not Declared | Not Declared |
| Self | /self-service | Not Declared | Not Declared | Not Declared | Not Declared |
| Shared Ui | /shared-ui | Not Declared | Not Declared | Not Declared | Not Declared |
| Support Console | /support-console | Not Declared | Not Declared | Not Declared | Not Declared |
| Zibo Web | /zibo-web | Not Declared | Not Declared | Not Declared | Not Declared |

## Dependency Matrix
| Service | Consumes | Consumed By |
|---|---|---|
| Banking Rails | None Declared | None Declared |
| DHIS2 | None Declared | None Declared |
| External eLMIS | None Declared | None Declared |
| External PACS Network | None Declared | None Declared |
| LIMS | None Declared | None Declared |
| SMS/WhatsApp Gateway | None Declared | None Declared |
| Civil Registry System | None Declared | None Declared |
| External Identity Provider | None Declared | None Declared |
| MOSIP | None Declared | None Declared |
| Envoy Gateway | None Declared | None Declared |
| Grafana | None Declared | None Declared |
| Kafka | None Declared | None Declared |
| Loki | None Declared | None Declared |
| MinIO | None Declared | None Declared |
| Orthanc PACS | None Declared | None Declared |
| OpenTelemetry Collector | None Declared | None Declared |
| PostgreSQL | None Declared | None Declared |
| Prometheus | None Declared | None Declared |
| Redis | None Declared | None Declared |
| Schema Registry (Apicurio) | None Declared | None Declared |
| Keycloak | None Declared | None Declared |
| Vault | None Declared | None Declared |
| Supply Planning | None Declared | None Declared |
| Mobile Api Client | None Declared | None Declared |
| Mobile Design System | None Declared | None Declared |
| Mobile Messaging | None Declared | None Declared |
| Mobile Offline | None Declared | None Declared |
| Mobile Timeline | None Declared | None Declared |
| Chaos Testing | None Declared | None Declared |
| Contract Tests | None Declared | None Declared |
| Federation Connector | None Declared | None Declared |
| Offline Sdk | None Declared | None Declared |
| Ops Instrumentation | None Declared | None Declared |
| Security Baseline | None Declared | None Declared |
| Shared Core | None Declared | None Declared |
| Shared Kernel | None Declared | None Declared |
| Shared Kernel Java | None Declared | None Declared |
| Tech Companion | None Declared | None Declared |
| Tech Companion Harness | None Declared | None Declared |
| Tech Companion Mock | None Declared | None Declared |
| Mobile Auth | None Declared | None Declared |
| Mobile Trust | None Declared | None Declared |
| Tshepo Contracts | None Declared | None Declared |
| Tshepo Sdk | None Declared | None Declared |
| Vault KMS | None Declared | None Declared |
| Butano Fhir | tshepo-authz-service | experience-bff, integration-hub |
| Butano | tshepo-authz-service | experience-bff, integration-hub |
| Indawo | tshepo-authz-service | experience-bff, integration-hub |
| Msika | tshepo-authz-service | experience-bff, integration-hub |
| Product Registry | tshepo-authz-service | experience-bff, integration-hub |
| Tuso | tshepo-authz-service | experience-bff, integration-hub |
| Ubomi | tshepo-authz-service | experience-bff, integration-hub |
| Varapi | tshepo-authz-service | experience-bff, integration-hub |
| Vito | tshepo-authz-service | experience-bff, integration-hub |
| Zibo | tshepo-authz-service | experience-bff, integration-hub |
| Identity Assurance | None Declared | experience-bff, integration-hub |
| Mvumo | None Declared | experience-bff, integration-hub |
| Tshepo Audit | None Declared | experience-bff, integration-hub |
| Tshepo Authz | None Declared | experience-bff, integration-hub |
| Tshepo Consent | None Declared | experience-bff, integration-hub |
| Tshepo Identity | None Declared | experience-bff, integration-hub |
| Tshepo Keys | None Declared | experience-bff, integration-hub |
| Tshepo Offline | None Declared | experience-bff, integration-hub |
| Clinical Knowledge Platform | tshepo-authz-service | experience-bff, integration-hub |
| Forms | tshepo-authz-service | experience-bff, integration-hub |
| Guidance | tshepo-authz-service | experience-bff, integration-hub |
| Inpatient | tshepo-authz-service | experience-bff, integration-hub |
| Oros | tshepo-authz-service | experience-bff, integration-hub |
| Pct | tshepo-authz-service | experience-bff, integration-hub |
| Pharmacy | tshepo-authz-service | experience-bff, integration-hub |
| Referral Service | None Declared | None Declared |
| Scheduling | tshepo-authz-service | experience-bff, integration-hub |
| Simba | tshepo-authz-service | experience-bff, integration-hub |
| Wellness | tshepo-authz-service, simba-service | experience-bff, integration-hub |
| Hr Payroll | tshepo-authz-service | experience-bff, integration-hub |
| Learning | tshepo-authz-service, multiple-domain-services-via-bff | web-mobile-experience |
| Msika Flow | tshepo-authz-service | experience-bff, integration-hub |
| Procurement | tshepo-authz-service | experience-bff, integration-hub |
| Workforce Governance | tshepo-authz-service | experience-bff, integration-hub |
| Community | tshepo-authz-service, multiple-domain-services-via-bff | web-mobile-experience |
| Experience Bff | tshepo-authz-service, multiple-domain-services-via-bff | web-mobile-experience |
| Costing Engine | tshepo-authz-service | experience-bff, integration-hub |
| Coverage | tshepo-authz-service | experience-bff, integration-hub |
| General Ledger | tshepo-authz-service | experience-bff, integration-hub |
| Mushe Wallet | tshepo-authz-service | experience-bff, integration-hub |
| Mushex | tshepo-authz-service | experience-bff, integration-hub |
| Share Slip | tshepo-authz-service | experience-bff, integration-hub |
| Ai Model Registry | tshepo-authz-service | experience-bff, integration-hub |
| Analytics Pipeline Service | None Declared | None Declared |
| Asset Registry | tshepo-authz-service | experience-bff, integration-hub |
| Audit Ledger | tshepo-authz-service | experience-bff, integration-hub |
| Campaigns | tshepo-authz-service | experience-bff, integration-hub |
| Card Print Agent | tshepo-authz-service | experience-bff, integration-hub |
| Channels | tshepo-authz-service | experience-bff, integration-hub |
| Connector Fhir Adapter | tshepo-authz-service | experience-bff, integration-hub |
| Credential Verification | tshepo-authz-service | experience-bff, integration-hub |
| Data Access Governance | tshepo-authz-service | experience-bff, integration-hub |
| Data Governance | tshepo-authz-service | experience-bff, integration-hub |
| Data Ingestion | tshepo-authz-service | experience-bff, integration-hub |
| Data Pipeline | tshepo-authz-service | experience-bff, integration-hub |
| Data Warehouse | tshepo-authz-service | experience-bff, integration-hub |
| Developer Portal | tshepo-authz-service | experience-bff, integration-hub |
| Dispatch | tshepo-authz-service | experience-bff, integration-hub |
| Document | tshepo-authz-service | experience-bff, integration-hub |
| Fhir Gateway | tshepo-authz-service | experience-bff, integration-hub |
| Integration Hub | tshepo-authz-service | experience-bff, integration-hub |
| Inventory Elmis Adapter | tshepo-authz-service | experience-bff, integration-hub |
| Inventory | tshepo-authz-service | experience-bff, integration-hub |
| Iot Ingestion | tshepo-authz-service | experience-bff, integration-hub |
| Jobs | tshepo-authz-service | experience-bff, integration-hub |
| Landela Adapter | tshepo-authz-service | experience-bff, integration-hub |
| National Data Repository | tshepo-authz-service | experience-bff, integration-hub |
| Ndr | tshepo-authz-service | experience-bff, integration-hub |
| Notification | tshepo-authz-service | experience-bff, integration-hub |
| Observability | tshepo-authz-service | experience-bff, integration-hub |
| Offline Edge | tshepo-authz-service | experience-bff, integration-hub |
| Offline Sync | tshepo-authz-service | experience-bff, integration-hub |
| Pacs Adapter | tshepo-authz-service | experience-bff, integration-hub |
| Pharmacy Elmis Adapter | tshepo-authz-service | experience-bff, integration-hub |
| Reporting | tshepo-authz-service | experience-bff, integration-hub |
| Rules | tshepo-authz-service | experience-bff, integration-hub |
| Schema Registry | tshepo-authz-service | experience-bff, integration-hub |
| Search | tshepo-authz-service | experience-bff, integration-hub |
| Security Hardening | tshepo-authz-service | experience-bff, integration-hub |
| Support | tshepo-authz-service | experience-bff, integration-hub |
| Surveillance | tshepo-authz-service | experience-bff, integration-hub |
| Workflow | tshepo-authz-service | experience-bff, integration-hub |
| Tshepo | None Declared | experience-bff, integration-hub |
| Butano Web | experience-bff | None Declared |
| Citizen App | experience-bff | None Declared |
| Costa Console | experience-bff | None Declared |
| Developer Console | experience-bff | None Declared |
| Ehr | experience-bff | None Declared |
| Experience | experience-bff | None Declared |
| Inventory Web | experience-bff | None Declared |
| Knowledge Admin | experience-bff | None Declared |
| Msika Flow Ops | experience-bff | None Declared |
| Msika Flow Portal | experience-bff | None Declared |
| Msika Flow Vendor | experience-bff | None Declared |
| Msika Web | experience-bff | None Declared |
| Mushex Finance Console | experience-bff | None Declared |
| Mushex Ops Console | experience-bff | None Declared |
| Mushex Payer Portal | experience-bff | None Declared |
| One Ui Shell | experience-bff | None Declared |
| Ops Console | experience-bff | None Declared |
| Ops Docs | experience-bff | None Declared |
| Oros Web | experience-bff | None Declared |
| Pct Web | experience-bff | None Declared |
| Pharmacy Web | experience-bff | None Declared |
| Portal | experience-bff | None Declared |
| Provider App | experience-bff | None Declared |
| Self | experience-bff | None Declared |
| Shared Ui | experience-bff | None Declared |
| Support Console | experience-bff | None Declared |
| Zibo Web | experience-bff | None Declared |

## Unresolved Or Low-Confidence Services
| Service | Ring | Primary Plane | Confidence | Unresolved Questions |
|---|---|---|---|---|
| None | - | - | - | - |

## Boundary Violation Summary
- Product registry authority converged to MSIKA; Product Registry Service remains an alias/deprecated transition endpoint.
- TSHEPO Service remains a legacy monolith with decomposition boundary risk.
- Wellness authority converged to Simba; Wellness Service remains an alias/deprecated transition endpoint.
- Approved scaffolds are implemented for Referral Service, Analytics Pipeline Service, Supply Planning, Vault KMS, and Chaos Testing.
- Soft-gate promotion now requires zero Missing contract-alignment entries across live backend, adapter, and worker services.
- Alias runtime closure milestones are approved: freeze `2026-05-15`, cutover `2026-09-30`, hard sunset `2026-12-31`.

## Future Service Update Rules
- Every new or materially changed service, app, adapter, worker, library, infrastructure component, or external dependency must update this register and `docs/architecture/services-registry.yaml`.
- A new service is not accepted without Ring and primary Plane assignment.
- Service splits, merges, and deprecations must update boundary notes and dependencies.
- CI validation rolls out in phases: Advisory, then Soft Gate, then Hard Gate.
- OpenAPI evidence is required immediately for new/skeleton backend services and required for all live backend services after the approved legacy deadline.
- Soft-gate promotion cannot proceed while any service remains in `contract_alignment_status: Missing`.

## New Service Acceptance Checklist
- [ ] Service Architecture Register updated
- [ ] `docs/architecture/services-registry.yaml` updated
- [ ] Ring assigned
- [ ] Primary Plane assigned
- [ ] Category display label assigned
- [ ] System-of-record responsibility documented
- [ ] API/frontend/event/database surfaces documented
- [ ] Boundary notes added
- [ ] Validation script passes
- [ ] Any unclear classification added to unresolved services

## Links To Supporting Files
- `docs/architecture/services-registry.yaml`
- `docs/architecture/SERVICE_ACTIVATION_MATRIX.md`
- `docs/architecture/SERVICE_CONTRACT_MAP.md`
- `docs/architecture/SERVICE_SURFACING_MAP.md`
- `docs/architecture/SERVICE_INTEGRATION_MAP.md`
- `docs/architecture/SERVICE_DUPLICATION_AND_CONSOLIDATION_REGISTER.md`
- `docs/architecture/SERVICE_ACTIVATION_BACKLOG.md`
- `docs/architecture/SERVICE_DEFINITION_OF_DONE.md`
- `docs/architecture/SERVICE_REMEDIATION_REPORT.md`
- `docs/architecture/EVENT_CONTRACT_PARITY_CONVERGENCE_PLAN.md`
- `docs/architecture/service-update-policy.md`
- `docs/architecture/ring-plane-taxonomy.md`
- `docs/architecture/service-boundary-violations.md`
- `docs/plan/SERVICE_CATALOG.md`
- `docs/registry/services-registry.yaml`
- `docs/registry/services-index.md`

## Evidence References
Primary evidence was taken from `services/`, `ui/`, `apps/mobile/`, `libs/`, `contracts/openapi/`, `docker-compose.yml`, `ops/runtime/`, `infra/`, `docs/registry/services-registry.yaml`, and `docs/plan/SERVICE_CATALOG.md`.
