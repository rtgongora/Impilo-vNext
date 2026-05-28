# Registry Parity Matrix (auto-generated)

> Source: `docs/architecture/services-registry.yaml` + supplemental `docs/registry/services-registry.yaml`
> Generated: 2026-05-28 · Rows: **158**

| plane | domain | capability | backend | webRoute | frontEndStatus | dataSource | priority | action | canonicalName | integrationStatus | remediationStatus |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Clinical Execution | Clinical | Butano | contracts/openapi/butano.custom.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | Document-service now uses provider-neutral storage-provider routing (MINIO active). Preview endpoint and contract are wired; external storage adapters remain backlog. | butano-service | Integrated | Fixed |
| Clinical Execution | Clinical | Butano Fhir | contracts/openapi/butano-fhir.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | butano-fhir | Integrated | Fixed |
| Clinical Execution | Clinical | Clinical Knowledge Platform | contracts/openapi/clinical-knowledge-platform.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | clinical-knowledge-platform-service | Integrated | Fixed |
| Clinical Execution | Clinical | Forms | contracts/openapi/forms.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | forms-service | Integrated | Fixed |
| Clinical Execution | Clinical | Guidance | contracts/openapi/guidance.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | guidance-service | Integrated | Fixed |
| Clinical Execution | Clinical | Inpatient | contracts/openapi/inpatient.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | inpatient-service | Integrated | Fixed |
| Clinical Execution | Clinical | Oros | contracts/openapi/oros.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | oros-service | Integrated | Fixed |
| Clinical Execution | Clinical | Pct | contracts/openapi/pct.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | Telemedicine specialty-specific pathways remain partially modelled through referral/session orchestration and require phased expansion. | pct-service | Integrated | Fixed |
| Clinical Execution | Clinical | Pharmacy | contracts/openapi/pharmacy.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | pharmacy-service | Integrated | Fixed |
| Clinical Execution | Clinical | Referral Service | contracts/openapi/referral.openapi.yaml | ui/one-ui-shell referral workflows | complete | skeleton | P1 | Runtime-backed teleconsult/referral endpoint evidence and ownership convergence with PCT are still required before promotion to Aligned. | referral-service | Integrated | Partially Fixed |
| Clinical Execution | Clinical | Scheduling | contracts/openapi/scheduling.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | scheduling-service | Integrated | Fixed |
| Enterprise Resource | Enterprise | Hr Payroll | contracts/openapi/hr-payroll.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | hr-payroll-service | Integrated | Fixed |
| Enterprise Resource | Enterprise | Learning | contracts/openapi/learning.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | learning-service | Integrated | Fixed |
| Enterprise Resource | Enterprise | Msika Flow | contracts/openapi/msika-flow.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | msika-flow-service | Integrated | Fixed |
| Enterprise Resource | Enterprise | Procurement | contracts/openapi/procurement.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | procurement-service | Integrated | Fixed |
| Enterprise Resource | Enterprise | Simba | contracts/openapi/simba.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | simba-service | Integrated | Fixed |
| Enterprise Resource | Supply | Supply Planning | libs/supply-planning |  | not_required | skeleton | P2 |  | supply-planning | Standalone | Fixed |
| Enterprise Resource | Enterprise | Wellness | contracts/openapi/wellness.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | Compatibility alias retained for migration while canonical ownership is converged to Simba. | wellness-service | Integrated | Fixed |
| Enterprise Resource | Enterprise | Workforce Governance | contracts/openapi/workforce-governance.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | workforce-governance-service | Integrated | Fixed |
| Experience | User Interface | Butano Web | ui/butano-web | /butano-web | complete | live | P2 |  | butano-web | Integrated | Fixed |
| Experience | User Interface | Citizen App | apps/mobile/citizen-app | mobile clients | complete | live | P2 |  | citizen-app | Integrated | Fixed |
| Experience | Experience | Community | contracts/openapi/community.openapi.yaml | Web and mobile clients via experience-bff | complete | live | P2 |  | community-service | Integrated | Fixed |
| Experience | User Interface | Costa Console | ui/costa-console | /costa-console | complete | live | P2 |  | costa-console | Integrated | Fixed |
| Experience | User Interface | Developer Console | ui/developer-console | /developer-console | complete | live | P2 |  | developer-console | Integrated | Fixed |
| Experience | User Interface | Ehr | ui/ehr | /ehr | complete | live | P2 |  | ehr | Integrated | Fixed |
| Experience | Experience | Experience Bff | contracts/openapi/experience-bff.openapi.yaml (including teleconsult and mobile telehealth/session routes) | Web and mobile clients via experience-bff | complete | live | P2 | Specialty-specific telemedicine workbench and consolidated telemedicine ops dashboard remain partial. | experience-bff | Integrated | Fixed |
| Experience | User Interface | Inventory Web | ui/inventory-web | /inventory-web | complete | live | P2 |  | inventory-web | Integrated | Fixed |
| Experience | User Interface | Knowledge Admin | ui/knowledge-admin | /knowledge-admin | complete | live | P2 |  | knowledge-admin | Integrated | Fixed |
| Experience | Experience | Llm Orchestration | Not Declared | ui/one-ui-shell routes and domain consoles | not_required | live | P2 | canonical llm-orchestration OpenAPI contract is not yet published. | llm-orchestration-service | Standalone | Fixed |
| Experience | User Interface | Mobile Api Client | apps/mobile/packages/mobile-api-client |  | not_required | live | P2 |  | mobile-api-client | Standalone | Fixed |
| Experience | User Interface | Mobile Design System | apps/mobile/packages/mobile-design-system |  | not_required | live | P2 |  | mobile-design-system | Standalone | Fixed |
| Experience | User Interface | Mobile Messaging | apps/mobile/packages/mobile-messaging |  | not_required | live | P2 |  | mobile-messaging | Standalone | Fixed |
| Experience | User Interface | Mobile Ndila | apps/mobile/packages/mobile-ndila |  | not_required | live | P2 |  | mobile-ndila | Standalone | Fixed |
| Experience | User Interface | Mobile Offline | apps/mobile/packages/mobile-offline |  | not_required | live | P2 |  | mobile-offline | Standalone | Fixed |
| Experience | User Interface | Mobile Timeline | apps/mobile/packages/mobile-timeline |  | not_required | live | P2 |  | mobile-timeline | Standalone | Fixed |
| Experience | User Interface | Msika Flow Ops | ui/msika-flow-ops | /msika-flow-ops | complete | live | P2 |  | msika-flow-ops | Integrated | Fixed |
| Experience | User Interface | Msika Flow Portal | ui/msika-flow-portal | /msika-flow-portal | complete | live | P2 |  | msika-flow-portal | Integrated | Fixed |
| Experience | User Interface | Msika Flow Vendor | ui/msika-flow-vendor | /msika-flow-vendor | complete | live | P2 |  | msika-flow-vendor | Integrated | Fixed |
| Experience | User Interface | Msika Web | ui/msika-web | /msika-web | complete | live | P2 |  | msika-web | Integrated | Fixed |
| Experience | User Interface | Mushex Finance Console | ui/mushex-finance-console | /mushex-finance-console | complete | live | P2 |  | mushex-finance-console | Integrated | Fixed |
| Experience | User Interface | Mushex Ops Console | ui/mushex-ops-console | /mushex-ops-console | complete | live | P2 |  | mushex-ops-console | Integrated | Fixed |
| Experience | User Interface | Mushex Payer Portal | ui/mushex-payer-portal | /mushex-payer-portal | complete | live | P2 |  | mushex-payer-portal | Integrated | Fixed |
| Experience | User Interface | One Ui Shell | ui/one-ui-shell | /one-ui-shell | complete | live | P2 |  | one-ui-shell | Integrated | Fixed |
| Experience | User Interface | Ops Console | ui/ops-console | /ops-console | complete | live | P2 |  | ops-console | Integrated | Fixed |
| Experience | User Interface | Ops Docs | ui/ops-docs | /ops-docs | complete | live | P2 |  | ops-docs | Integrated | Fixed |
| Experience | User Interface | Oros Web | ui/oros-web | /oros-web | complete | live | P2 |  | oros-web | Integrated | Fixed |
| Experience | User Interface | Pct Web | ui/pct-web | /pct-web | complete | live | P2 |  | pct-web | Integrated | Fixed |
| Experience | User Interface | Pharmacy Web | ui/pharmacy-web | /pharmacy-web | complete | live | P2 |  | pharmacy-web | Integrated | Fixed |
| Experience | User Interface | Portal | ui/portal | /portal | complete | live | P2 |  | portal | Integrated | Fixed |
| Experience | User Interface | Provider App | apps/mobile/provider-app | mobile clients | complete | live | P2 |  | provider-app | Integrated | Fixed |
| Experience | User Interface | Self | ui/self-service | /self-service | complete | live | P2 |  | self-service | Integrated | Fixed |
| Experience | User Interface | Shared Ui | ui/shared-ui | /shared-ui | complete | live | P2 |  | shared-ui | Integrated | Fixed |
| Experience | User Interface | Support Console | ui/support-console | /support-console | complete | live | P2 |  | support-console | Integrated | Fixed |
| Experience | User Interface | Zibo Web | ui/zibo-web | /zibo-web | complete | live | P2 |  | zibo-web | Integrated | Fixed |
| Finance & Resource | Finance | Banking Rails | external |  | not_required | live | P2 | Provider-neutral storage routing exists with MinIO adapter; additional external DMS/storage adapters remain pending for full engine-parity. | banking-rails | Standalone | Fixed |
| Finance & Resource | Finance | Costing Engine | contracts/openapi/costa.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | costing-engine-service | Integrated | Fixed |
| Finance & Resource | Finance | Coverage | contracts/openapi/coverage.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | coverage-service | Integrated | Fixed |
| Finance & Resource | Finance | General Ledger | contracts/openapi/general-ledger.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | general-ledger-service | Integrated | Fixed |
| Finance & Resource | Finance | Mushe Wallet | contracts/openapi/mushe-wallet.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | mushe-wallet-service | Integrated | Fixed |
| Finance & Resource | Finance | Mushex | contracts/openapi/mushex.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | mushex-service | Integrated | Fixed |
| Finance & Resource | Finance | Share Slip | contracts/openapi/share-slip.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | share-slip-service | Integrated | Fixed |
| Integration & Operations | Integration | Ai Model Registry | contracts/openapi/ai-model-registry.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | ai-model-registry-service | Integrated | Fixed |
| Integration & Operations | Data | Analytics Pipeline Service | contracts/openapi/analytics-pipeline.openapi.yaml | experience-bff intelligence and reporting surfaces | complete | skeleton | P1 | Promote to Aligned only after runtime-backed contract verification evidence is attached. | analytics-pipeline-service | Integrated | Partially Fixed |
| Integration & Operations | Integration | Asset Registry | contracts/openapi/asset-registry.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | asset-registry-service | Integrated | Fixed |
| Integration & Operations | Integration | Audit Ledger | contracts/openapi/audit-ledger.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | audit-ledger-service | Integrated | Fixed |
| Integration & Operations | Integration | Campaigns | contracts/openapi/campaigns.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | campaigns-service | Integrated | Fixed |
| Integration & Operations | Integration | Card Print Agent | contracts/openapi/card-print.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | card-print-agent | Integrated | Fixed |
| Integration & Operations | Integration | Channels | contracts/openapi/channels.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | channels-service | Integrated | Fixed |
| Integration & Operations | Resilience | Chaos Testing | libs/chaos-testing |  | not_required | skeleton | P2 |  | chaos-testing | Standalone | Fixed |
| Integration & Operations | Integration | Connector Fhir Adapter | contracts/openapi/connector-fhir.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | connector-fhir-adapter | Integrated | Fixed |
| Integration & Operations | Infrastructure | Contract Tests | libs/contract-tests |  | not_required | live | P2 |  | contract-tests | Standalone | Fixed |
| Integration & Operations | Integration | Credential Verification | contracts/openapi/credential-verification.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | credential-verification-service | Integrated | Fixed |
| Integration & Operations | Integration | Data Access Governance | contracts/openapi/data-access-governance.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | data-access-governance-service | Integrated | Fixed |
| Integration & Operations | Integration | Data Governance | contracts/openapi/data-governance.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | data-governance-service | Integrated | Fixed |
| Integration & Operations | Integration | Data Ingestion | contracts/openapi/data-ingestion.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | data-ingestion-service | Integrated | Fixed |
| Integration & Operations | Integration | Data Pipeline | contracts/openapi/data-pipeline.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | data-pipeline-service | Integrated | Fixed |
| Integration & Operations | Integration | Data Warehouse | contracts/openapi/data-warehouse.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | data-warehouse-service | Integrated | Fixed |
| Integration & Operations | Integration | Developer Portal | contracts/openapi/developer-portal.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | developer-portal-service | Integrated | Fixed |
| Integration & Operations | Data | DHIS2 | external |  | not_required | live | P2 |  | dhis2 | Standalone | Fixed |
| Integration & Operations | Integration | Dispatch | contracts/openapi/dispatch.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | dispatch-service | Integrated | Fixed |
| Integration & Operations | Integration | Document | contracts/openapi/document-store.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | document-service | Integrated | Fixed |
| Integration & Operations | Integration | Envoy Gateway | infra/envoy/envoy.yaml |  | not_required | live | P2 |  | envoy-gateway | Standalone | Fixed |
| Integration & Operations | Supply | External eLMIS | external |  | not_required | live | P2 |  | external-elmis | Standalone | Fixed |
| Integration & Operations | Integration | External PACS Network | external |  | not_required | live | P2 |  | external-pacs-network | Standalone | Fixed |
| Integration & Operations | Infrastructure | Federation Connector | libs/federation-connector |  | not_required | live | P2 |  | federation-connector | Standalone | Fixed |
| Integration & Operations | Integration | Fhir Gateway | contracts/openapi/fhir-gateway.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | fhir-gateway-service | Integrated | Fixed |
| Integration & Operations | Resilience | Grafana | ops/runtime/docker-compose.observability.yml |  | not_required | live | P2 |  | grafana | Standalone | Fixed |
| Integration & Operations | Integration | Integration Hub | contracts/openapi/integration-hub.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | integration-hub | Integrated | Fixed |
| Integration & Operations | Integration | Inventory | contracts/openapi/inventory.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | inventory-service | Integrated | Fixed |
| Integration & Operations | Integration | Inventory Elmis Adapter | contracts/openapi/inventory-elmis.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | inventory-elmis-adapter | Integrated | Fixed |
| Integration & Operations | Integration | Iot Ingestion | contracts/openapi/iot-ingestion.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | iot-ingestion-service | Integrated | Fixed |
| Integration & Operations | Integration | Jobs | contracts/openapi/jobs.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | jobs-service | Integrated | Fixed |
| Integration & Operations | Integration | Kafka | docker-compose.yml |  | not_required | live | P2 |  | kafka | Standalone | Fixed |
| Integration & Operations | Integration | Landela Adapter | contracts/openapi/landela-adapter.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | landela-adapter-service | Integrated | Fixed |
| Integration & Operations | Integration | LIMS | external |  | not_required | live | P2 |  | lims | Standalone | Fixed |
| Integration & Operations | Resilience | Loki | ops/runtime/docker-compose.observability.yml |  | not_required | live | P2 |  | loki | Standalone | Fixed |
| Integration & Operations | Infrastructure | MinIO | docker-compose.yml |  | not_required | live | P2 |  | minio | Standalone | Fixed |
| Integration & Operations | Integration | National Data Repository | contracts/openapi/national-data-repository.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | national-data-repository-service | Integrated | Fixed |
| Integration & Operations | Integration | Ndila | contracts/openapi/ndila.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | ndila-service | Integrated | Fixed |
| Integration & Operations | Integration | Ndr | contracts/openapi/ndr.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | ndr-service | Integrated | Fixed |
| Integration & Operations | Integration | Nhume | Not Declared | ui/one-ui-shell routes and domain consoles | complete | live | P1 | canonical nhume.openapi.yaml is not yet published. | nhume-service | Integrated | Partially Fixed |
| Integration & Operations | Integration | Notification | contracts/openapi/notification.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | notification-service | Integrated | Fixed |
| Integration & Operations | Integration | Observability | contracts/openapi/observability.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | observability-service | Integrated | Fixed |
| Integration & Operations | Integration | Offline Edge | contracts/openapi/offline-edge.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | offline-edge-service | Integrated | Fixed |
| Integration & Operations | Infrastructure | Offline Sdk | libs/offline-sdk |  | not_required | live | P2 |  | offline-sdk | Standalone | Fixed |
| Integration & Operations | Integration | Offline Sync | contracts/openapi/offline-sync.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | offline-sync-service | Integrated | Fixed |
| Integration & Operations | Resilience | OpenTelemetry Collector | ops/runtime/docker-compose.observability.yml |  | not_required | live | P2 |  | otel-collector | Standalone | Fixed |
| Integration & Operations | Infrastructure | Ops Instrumentation | libs/ops-instrumentation |  | not_required | live | P2 |  | ops-instrumentation | Standalone | Fixed |
| Integration & Operations | Integration | Orthanc PACS | Orthanc REST (/system,/studies) and DICOMweb (/dicom-web) |  | not_required | live | P2 | Compose/runtime health probing hardened; Kubernetes Orthanc workload declaration remains a tracked follow-on. | orthanc-pacs | Standalone | Fixed |
| Integration & Operations | Integration | Pacs Adapter | contracts/openapi/pacs-adapter.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | pacs-adapter-service | Integrated | Fixed |
| Integration & Operations | Integration | Pharmacy Elmis Adapter | contracts/openapi/pharmacy-elmis.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | pharmacy-elmis-adapter | Integrated | Fixed |
| Integration & Operations | Infrastructure | PostgreSQL | docker-compose.yml |  | not_required | live | P2 |  | postgresql | Standalone | Fixed |
| Integration & Operations | Resilience | Prometheus | ops/runtime/docker-compose.observability.yml |  | not_required | live | P2 |  | prometheus | Standalone | Fixed |
| Integration & Operations | Resilience | Redis | docker-compose.yml |  | not_required | live | P2 |  | redis | Standalone | Fixed |
| Integration & Operations | Integration | Reporting | contracts/openapi/reporting.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | reporting-service | Integrated | Fixed |
| Integration & Operations | Integration | Rules | contracts/openapi/rules.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | rules-service | Integrated | Fixed |
| Integration & Operations | Integration | Schema Registry | contracts/openapi/schema-registry.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | schema-registry-service | Integrated | Fixed |
| Integration & Operations | Integration | Schema Registry (Apicurio) | docker-compose.yml |  | not_required | live | P2 |  | schema-registry-apicurio | Standalone | Fixed |
| Integration & Operations | Integration | Search | contracts/openapi/search.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | search-service | Integrated | Fixed |
| Integration & Operations | Infrastructure | Security Baseline | libs/security-baseline |  | not_required | live | P2 |  | security-baseline | Standalone | Fixed |
| Integration & Operations | Integration | Security Hardening | contracts/openapi/security-hardening.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | security-hardening-service | Integrated | Fixed |
| Integration & Operations | Infrastructure | Shared Core | services/shared-core |  | not_required | live | P2 |  | shared-core | Standalone | Fixed |
| Integration & Operations | Infrastructure | Shared Kernel | libs/shared-kernel |  | not_required | live | P2 |  | shared-kernel | Standalone | Fixed |
| Integration & Operations | Infrastructure | Shared Kernel Java | libs/shared-kernel-java |  | not_required | live | P2 |  | shared-kernel-java | Standalone | Fixed |
| Integration & Operations | Integration | SMS/WhatsApp Gateway | external |  | not_required | live | P2 |  | sms-whatsapp-gateway | Standalone | Fixed |
| Integration & Operations | Integration | Support | contracts/openapi/support.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | support-service | Integrated | Fixed |
| Integration & Operations | Integration | Surveillance | contracts/openapi/surveillance.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | surveillance-service | Integrated | Fixed |
| Integration & Operations | Infrastructure | Tech Companion | libs/tech-companion |  | not_required | live | P2 |  | tech-companion | Standalone | Fixed |
| Integration & Operations | Infrastructure | Tech Companion Harness | libs/tech-companion-harness |  | not_required | live | P2 |  | tech-companion-harness | Standalone | Fixed |
| Integration & Operations | Infrastructure | Tech Companion Mock | libs/tech-companion-mock |  | not_required | live | P2 |  | tech-companion-mock | Standalone | Fixed |
| Integration & Operations | Integration | Workflow | contracts/openapi/workflow.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | workflow-service | Integrated | Fixed |
| Registry Spine | Registry | Civil Registry System | external |  | not_required | live | P2 |  | civil-registry-system | Standalone | Fixed |
| Registry Spine | Registry | Indawo | contracts/openapi/indawo.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | indawo-service | Integrated | Fixed |
| Registry Spine | Registry | Msika | contracts/openapi/msika-core.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 | OpenAPI contract evidence closed for soft-gate promotion readiness. | msika-service | Integrated | Fixed |
| Registry Spine | Registry | Product Registry | contracts/openapi/product-registry.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | deprecated | P2 | Deprecated alias runtime follows phased sunset: freeze 2026-05-15, cutover 2026-09-30, hard sunset 2026-12-31. | product-registry-service | Integrated | Fixed |
| Registry Spine | Registry | Tuso | contracts/openapi/tuso.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | tuso-service | Integrated | Fixed |
| Registry Spine | Registry | Ubomi | contracts/openapi/ubomi.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | ubomi-service | Integrated | Fixed |
| Registry Spine | Registry | Varapi | contracts/openapi/varapi.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | varapi-service | Integrated | Fixed |
| Registry Spine | Registry | Vito | contracts/openapi/vito.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | vito-service | Integrated | Fixed |
| Registry Spine | Registry | Zibo | contracts/openapi/zibo.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | zibo-service | Integrated | Fixed |
| Trust & Governance | Assurance | External Identity Provider | external |  | not_required | live | P2 |  | external-idp | Standalone | Fixed |
| Trust & Governance | Assurance | Identity Assurance | contracts/openapi/identity-assurance.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | identity-assurance-service | Integrated | Fixed |
| Trust & Governance | Assurance | Keycloak | docker-compose.yml |  | not_required | live | P2 |  | keycloak | Standalone | Fixed |
| Trust & Governance | Assurance | Mobile Auth | apps/mobile/packages/mobile-auth |  | not_required | live | P2 |  | mobile-auth | Standalone | Fixed |
| Trust & Governance | Assurance | Mobile Trust | apps/mobile/packages/mobile-trust |  | not_required | live | P2 |  | mobile-trust | Standalone | Fixed |
| Trust & Governance | Assurance | MOSIP | external |  | not_required | live | P2 |  | mosip | Standalone | Fixed |
| Trust & Governance | Kernel | Mvumo | contracts/openapi/mvumo.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | mvumo-service | Integrated | Fixed |
| Trust & Governance | Kernel | Tshepo | contracts/openapi/tshepo.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | tshepo-service | Integrated | Fixed |
| Trust & Governance | Kernel | Tshepo Audit | contracts/openapi/tshepo-audit.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | tshepo-audit-service | Integrated | Fixed |
| Trust & Governance | Kernel | Tshepo Authz | contracts/openapi/tshepo-authz.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | tshepo-authz-service | Integrated | Fixed |
| Trust & Governance | Kernel | Tshepo Consent | contracts/openapi/tshepo-consent.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | tshepo-consent-service | Integrated | Fixed |
| Trust & Governance | Assurance | Tshepo Contracts | libs/tshepo-contracts |  | not_required | live | P2 |  | tshepo-contracts | Standalone | Fixed |
| Trust & Governance | Kernel | Tshepo Identity | contracts/openapi/tshepo-identity.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | tshepo-identity-service | Integrated | Fixed |
| Trust & Governance | Kernel | Tshepo Keys | contracts/openapi/tshepo-keys.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | tshepo-keys-service | Integrated | Fixed |
| Trust & Governance | Kernel | Tshepo Offline | contracts/openapi/tshepo-offline.openapi.yaml | ui/one-ui-shell routes and domain consoles | complete | live | P2 |  | tshepo-offline-service | Integrated | Fixed |
| Trust & Governance | Assurance | Tshepo Sdk | libs/tshepo-sdk |  | not_required | live | P2 |  | tshepo-sdk | Standalone | Fixed |
| Trust & Governance | Assurance | Vault | ops/runtime/docker-compose.kernel.yml |  | not_required | live | P2 |  | vault | Standalone | Fixed |
| Trust & Governance | Assurance | Vault KMS | libs/vault-kms |  | not_required | skeleton | P2 |  | vault-kms | Standalone | Fixed |
