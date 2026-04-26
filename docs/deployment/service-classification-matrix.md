# Impilo vNext Service Classification Matrix

## Purpose

This is the initial data-centre sandbox classification baseline for deployable services in the repository. It bridges the deployment doctrine ("do not assume service weight; measure it") to concrete Kubernetes/OpenShift placement and resource planning.

This matrix must be updated from profiling evidence as the sandbox produces runtime data. The source service list is `docs/registry/services-index.md`.

## Resource Profiles

| Profile | Requests | Limits | Intended use |
|---|---:|---:|---|
| ui | 500m CPU / 1Gi RAM | 2 CPU / 4Gi RAM | Next.js/UI services |
| medium | 1 CPU / 2Gi RAM | 4 CPU / 6Gi RAM | normal Java services |
| heavy | 2 CPU / 6Gi RAM | 8 CPU / 16Gi RAM | registry, clinical, finance, integration services under load |
| very-heavy | 4 CPU / 12Gi RAM | 16 CPU / 32Gi RAM | HAPI FHIR, analytics, search/index-heavy workloads |

Criticality values: `ring-0`, `care-critical`, `business-critical`, `platform`, `support`.

Ingress values: `public-gateway`, `internal-gateway`, `internal-only`, `admin-only`.

## Platform Workloads

| Service | Namespace | Criticality | Stateful | Sensitivity | Weight | Replicas | DB | Kafka | Ingress | Scaling behaviour |
|---|---|---|---:|---|---|---:|---|---|---|---|
| Envoy Gateway | impilo-gateway | ring-0 | no | high | heavy | 3 | no | no | public-gateway | HPA on RPS/CPU; anti-affinity |
| OPA | impilo-gateway | ring-0 | no | high | medium | 3 | no | no | internal-only | Scale with gateway authz latency |
| Keycloak | impilo-trust | ring-0 | yes | critical | heavy | 3 | keycloak | optional | public-gateway/admin-only | HPA on login latency; admin restricted |
| PostgreSQL HA | stateful-data | ring-0 | yes | critical | very-heavy | 3-5 | platform databases | no | internal-only | Dedicated DB nodes, PgBouncer, PITR |
| Kafka HA | stateful-data | ring-0 | yes | high | very-heavy | 3-5 | no | platform topics | internal-only | Dedicated brokers, RF=3, ISR=2 |
| Redis HA | stateful-data | ring-0 | yes | high | heavy | 3 | no | no | internal-only | Sentinel/cluster; not source of truth |
| MinIO/S3 | stateful-storage | platform | yes | high | very-heavy | 4-8 | metadata only | audit.* | admin-only/internal-only | Erasure coding, lifecycle policies |
| Orthanc PACS | impilo-imaging | care-critical | yes | high | very-heavy | 2-3 | orthanc | imaging.* | internal-gateway/admin-only | Storage-heavy; DICOM internal only |

## Service Matrix

| Service | Namespace | Criticality | Stateful | Sensitivity | Weight | Replicas | DB | Kafka topics | Ingress | Scaling behaviour |
|---|---|---|---:|---|---|---:|---|---|---|---|
| asset-registry-service | impilo-devtools | support | yes | medium | medium | 2 | asset_registry | ops.* | internal-gateway | Scale on asset operations |
| audit-ledger-service | impilo-security | ring-0 | yes | critical | heavy | 3 | audit_ledger | audit.* | internal-only | Scale on audit write latency |
| butano-fhir | impilo-fhir | care-critical | yes | high | very-heavy | 3 | butano_fhir | clinical.* | internal-gateway | Scale on FHIR transaction latency |
| butano-service | impilo-fhir | care-critical | yes | high | very-heavy | 3 | butano | clinical.* audit.* | internal-gateway | Dedicated FHIR/SHR pool; scale on query latency |
| campaigns-service | impilo-data | platform | yes | medium | medium | 2 | campaigns | analytics.* integration.* | internal-gateway | Scale on campaign operations |
| card-print-agent | impilo-documents | support | yes | high | medium | 2 | card_print | audit.* integration.* | internal-only | Scale on print queue |
| channels-service | impilo-integration | platform | yes | medium | medium | 2 | channels | integration.* | internal-only | Scale on channel throughput |
| clinical-knowledge-platform-service | impilo-clinical | care-critical | yes | medium | very-heavy | 2 | clinical_knowledge | clinical.* | internal-gateway | Index/query heavy; profile memory |
| connector-fhir-adapter | impilo-integration | platform | yes | high | heavy | 2 | connector_fhir | integration.* clinical.* | internal-only | Scale on adapter queue lag |
| costing-engine-service | impilo-finance | business-critical | yes | high | heavy | 2 | costa | finance.* audit.* | internal-gateway | Scale on costing latency |
| coverage-service | impilo-finance | business-critical | yes | high | heavy | 2 | coverage | finance.* registry.* | internal-gateway | Scale on eligibility checks |
| credential-verification-service | impilo-finance | business-critical | yes | high | medium | 2 | credential_verification | audit.* | public-gateway | Scale on verification requests |
| data-access-governance-service | impilo-data | ring-0 | yes | critical | heavy | 2 | data_access_governance | analytics.* audit.* | internal-gateway | Scale on policy/data access latency |
| data-governance-service | impilo-data | platform | yes | high | heavy | 2 | data_governance | analytics.* audit.* | internal-gateway | Scale on governance workflows |
| data-ingestion-service | impilo-data | platform | yes | high | heavy | 2 | data_ingestion | analytics.* integration.* | internal-only | Scale on ingestion lag |
| data-pipeline-service | impilo-data | platform | yes | high | very-heavy | 2 | data_pipeline | analytics.* | internal-only | Scale on pipeline throughput |
| data-warehouse-service | impilo-data | platform | yes | high | very-heavy | 2 | data_warehouse | analytics.* | internal-gateway | Scale on reporting/query load |
| developer-portal-service | impilo-devtools | support | yes | medium | medium | 2 | developer_portal | ops.* | public-gateway | Scale on developer traffic |
| dispatch-service | impilo-integration | platform | yes | medium | medium | 2 | dispatch | integration.* | internal-only | Scale on dispatch queue |
| document-service | impilo-documents | care-critical | yes | high | heavy | 3 | documents | clinical.* audit.* | internal-gateway | Scale on upload/download throughput |
| experience-bff | impilo-experience | care-critical | no | high | heavy | 3 | none or experience_bff | clinical.* registry.* audit.* | internal-gateway | HPA on request latency/RPS |
| fhir-gateway-service | impilo-fhir | care-critical | no | high | heavy | 3 | no | clinical.* audit.* | internal-gateway | Scale on FHIR gateway latency |
| forms-service | impilo-clinical | care-critical | yes | medium | medium | 2 | forms | clinical.* | internal-gateway | Scale on form rendering/submission |
| guidance-service | impilo-clinical | care-critical | yes | medium | heavy | 2 | guidance | clinical.* | internal-gateway | Scale on guidance request latency |
| identity-assurance-service | impilo-trust | ring-0 | yes | critical | heavy | 2 | identity_assurance | trust.* audit.* | internal-gateway | Scale on assurance workflow latency |
| indawo-service | impilo-registry | care-critical | yes | high | medium | 2 | indawo | registry.* | internal-gateway | Scale on routing/facility context |
| inpatient-service | impilo-clinical | care-critical | yes | high | heavy | 2 | inpatient | clinical.* audit.* | internal-gateway | Scale on bed movement/events |
| integration-hub | impilo-integration | platform | yes | high | heavy | 3 | integration_hub | integration.* | internal-only | Scale on queue lag/replay backlog |
| inventory-elmis-adapter | impilo-integration | platform | yes | medium | medium | 2 | inventory_elmis | integration.* | internal-only | Scale on adapter queue lag |
| inventory-service | impilo-clinical | care-critical | yes | medium | heavy | 2 | inventory | clinical.* integration.* | internal-gateway | Scale on stock movement load |
| iot-ingestion-service | impilo-integration | platform | yes | medium | heavy | 2 | iot_ingestion | integration.* telemetry.* | internal-only | Scale on ingestion throughput |
| jobs-service | impilo-integration | platform | yes | medium | medium | 2 | jobs | integration.* | internal-only | Scale on scheduled job backlog |
| landela-adapter-service | impilo-integration | platform | yes | high | medium | 2 | landela | integration.* | internal-only | Scale on adapter queue lag |
| msika-flow-service | impilo-finance | business-critical | yes | high | medium | 2 | msika_flow | finance.* marketplace.* | internal-gateway | Scale on order flow load |
| msika-service | impilo-registry | business-critical | yes | high | heavy | 2 | msika | registry.* marketplace.* | internal-gateway | Scale on marketplace catalogue load |
| mushex-service | impilo-finance | business-critical | yes | critical | heavy | 3 | mushex | finance.* audit.* | internal-gateway | Strict audit; synthetic payment rails only |
| national-data-repository-service | impilo-data | platform | yes | high | very-heavy | 2 | national_data_repository | analytics.* | internal-gateway | Scale on repository query/load |
| ndr-service | impilo-data | platform | yes | high | very-heavy | 2 | ndr | analytics.* | internal-gateway | Scale on repository query/load |
| notification-service | impilo-integration | platform | yes | medium | medium | 2 | notification | integration.* | internal-only | Scale on outbound queue lag |
| observability-service | impilo-observability | platform | yes | medium | heavy | 2 | observability | telemetry.* | admin-only | Scale on telemetry ingestion |
| offline-edge-service | impilo-offline | care-critical | yes | high | heavy | 2 | offline_edge | offline.* | internal-only | Dedicated edge simulation pool |
| offline-sync-service | impilo-offline | care-critical | yes | high | heavy | 3 | offline_sync | offline.* audit.* | internal-gateway | Scale on sync/replay backlog |
| oros-service | impilo-clinical | care-critical | yes | high | heavy | 3 | oros | clinical.* audit.* | internal-gateway | HPA on order/result latency |
| pacs-adapter-service | impilo-imaging | care-critical | yes | high | heavy | 2 | pacs_adapter | clinical.* imaging.* | internal-gateway | Scale on DICOM workload |
| pct-service | impilo-clinical | care-critical | yes | high | heavy | 3 | pct | clinical.* audit.* | internal-gateway | HPA on encounter latency/write queue |
| pharmacy-elmis-adapter | impilo-integration | platform | yes | medium | medium | 2 | pharmacy_elmis | integration.* | internal-only | Scale on adapter queue lag |
| pharmacy-service | impilo-clinical | care-critical | yes | high | heavy | 3 | pharmacy | clinical.* finance.* audit.* | internal-gateway | HPA on dispense latency |
| product-registry-service | impilo-registry | business-critical | yes | medium | medium | 2 | product_registry | registry.* | internal-gateway | Scale on catalogue requests |
| reporting-service | impilo-data | platform | yes | high | heavy | 2 | reporting | analytics.* | internal-gateway | Scale on report queue/query latency |
| rules-service | impilo-clinical | care-critical | yes | high | heavy | 2 | rules | clinical.* audit.* | internal-gateway | Scale on rules CPU/latency |
| schema-registry-service | impilo-devtools | platform | yes | high | medium | 2 | schema_registry | ops.* | internal-gateway | Scale on schema lookup/publish |
| search-service | impilo-data | platform | yes | high | very-heavy | 3 | search | analytics.* | internal-gateway | Scale on index/query load |
| security-hardening-service | impilo-security | ring-0 | yes | critical | heavy | 2 | security_hardening | audit.* | internal-only | Strict admin access only |
| share-slip-service | impilo-finance | business-critical | yes | high | medium | 2 | share_slip | audit.* finance.* | public-gateway | Scale on public verification requests |
| support-service | impilo-devtools | support | yes | medium | medium | 2 | support | ops.* | internal-gateway | Scale on ticket workload |
| surveillance-service | impilo-data | platform | yes | high | heavy | 2 | surveillance | analytics.* clinical.* | internal-gateway | Scale on outbreak/surveillance queries |
| tshepo-audit-service | impilo-trust | ring-0 | yes | critical | heavy | 3 | tshepo_audit | audit.* trust.* | internal-only | Scale on write latency/outbox lag |
| tshepo-authz-service | impilo-trust | ring-0 | yes | critical | heavy | 3 | tshepo_authz | trust.* audit.* | internal-gateway | HPA on authz latency and CPU |
| tshepo-consent-service | impilo-trust | ring-0 | yes | critical | heavy | 3 | tshepo_consent | trust.* audit.* | internal-gateway | HPA on consent decision latency |
| tshepo-identity-service | impilo-trust | ring-0 | yes | critical | heavy | 3 | tshepo_identity | trust.* audit.* | internal-gateway | HPA on identity lookup latency |
| tshepo-keys-service | impilo-trust | ring-0 | yes | critical | heavy | 3 | tshepo_keys | audit.* | internal-only | Dedicated nodes preferred; strict PDB |
| tshepo-offline-service | impilo-offline | ring-0 | yes | high | heavy | 2 | tshepo_offline | offline.* trust.* | internal-gateway | Scale on replay backlog |
| tshepo-service | impilo-trust | ring-0 | yes | critical | heavy | 2 | tshepo | trust.* audit.* | internal-gateway | Legacy/compatibility only; avoid dual PDP ambiguity |
| tuso-service | impilo-registry | ring-0 | yes | high | heavy | 3 | tuso | registry.* audit.* | internal-gateway | Scale on facility/workspace resolution |
| ubomi-service | impilo-registry | care-critical | yes | high | heavy | 2 | ubomi | registry.* clinical.* | internal-gateway | Scale after profiling |
| varapi-service | impilo-registry | ring-0 | yes | critical | heavy | 3 | varapi | registry.* audit.* | internal-gateway | Scale on provider validation latency |
| vito-service | impilo-registry | ring-0 | yes | critical | heavy | 3 | vito | registry.* audit.* | internal-gateway | Scale on patient lookup latency/cache hit rate |
| wellness-service | impilo-clinical | care-critical | yes | high | heavy | 2 | wellness | clinical.* audit.* | internal-gateway | Scale on citizen wellness traffic |
| workflow-service | impilo-integration | care-critical | yes | high | heavy | 2 | workflow | clinical.* integration.* | internal-gateway | Scale on workflow transition latency |
| zibo-service | impilo-registry | ring-0 | yes | medium | medium | 2 | zibo | registry.* | internal-gateway | Scale on terminology lookup latency |

## UI Workloads

| UI/app | Namespace | Criticality | Stateful | Weight | Replicas | Ingress | Scaling behaviour |
|---|---|---|---:|---|---:|---|---|
| one-ui-shell | impilo-experience | care-critical | no | ui | 3 | public-gateway | HPA on RPS/CPU |
| experience-ui | impilo-experience | care-critical | no | ui | 3 | public-gateway | HPA on RPS/CPU |
| ehr | impilo-experience | care-critical | no | ui | 3 | public-gateway | HPA on RPS/CPU |
| portal/self-service | impilo-experience | support | no | ui | 2 | public-gateway | HPA on RPS/CPU |
| ops-console | impilo-experience | platform | no | ui | 2 | admin-only | Restricted admin access |
| support-console | impilo-experience | support | no | ui | 2 | admin-only | Restricted admin access |
| developer-console | impilo-devtools | support | no | ui | 2 | admin-only | Restricted admin access |
| pharmacy/inventory/finance UIs | impilo-experience | business-critical | no | ui | 2 | public-gateway | HPA on RPS/CPU |

## Update Rules

Update this matrix when:

- A service is added, removed, split, merged, or renamed.
- A service changes namespace, plane, or criticality.
- A service gains or loses a database, Kafka topic, cache, object bucket, public route, or external integration.
- Profiling data changes the service weight or replica baseline.
- A stress test identifies a new bottleneck or scaling mode.

Each update should cite the evidence source where practical: k6 run, Prometheus dashboard, JVM profile, database slow-query report, Kafka lag report, or incident/failure-test report.
