# Full-Platform Compliance Matrix — Impilo vNext

> Generated: 2026-03-14 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0

## Legend

| Column | Meaning |
|---|---|
| has_pom | Service has a pom.xml |
| has_code | Service has src/main/java |
| has_internal_v1 | /internal/v1/** routes exist in main source |
| has_external_v1 | /external/v1/** routes exist in main source |
| four_header_enforcement | V11HeaderFilter active via tech-companion auto-config |
| idempotency_on_commands | IdempotencyFilter active via tech-companion auto-config |
| golden_contract_test | GoldenContractIT extends GoldenContractSuite |
| outbox_present | event_outbox table defined in migrations |
| outbox_v11_columns_present | tenant_id, pod_id, request_id, correlation_id, idempotency_key in outbox |
| event_envelope_emission | EventEnvelope / OutboxEventBuilder / CompanionOutboxPublisher in use |
| snapshot_endpoint_present | SnapshotController exists |
| federation_authority_enforced | FederationAuthority.requireNational() or FederationAuthorityGuard in use |
| consistency_class_declared | ConsistencyClass annotations or ActionRegistry present |
| health_endpoint_present | Spring Boot Actuator health (auto-provided by spring-boot-starter-actuator) |
| status | COMPLIANT / PARTIAL / BLOCKED / STUB |

## Ring 0 — Trust & Governance Plane

| service_name | module_path | has_pom | has_code | has_internal_v1 | has_external_v1 | four_header_enforcement | idempotency_on_commands | golden_contract_test | outbox_present | outbox_v11_columns_present | event_envelope_emission | snapshot_endpoint_present | federation_authority_enforced | consistency_class_declared | health_endpoint_present | status | blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| tshepo-service | services/tshepo-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| tshepo-audit-service | services/tshepo-audit-service | Y | Y | Y¹ | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper |
| tshepo-authz-service | services/tshepo-authz-service | Y | Y | Y¹ | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper |
| tshepo-consent-service | services/tshepo-consent-service | Y | Y | Y¹ | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper |
| tshepo-identity-service | services/tshepo-identity-service | Y | Y | Y¹ | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper |
| tshepo-keys-service | services/tshepo-keys-service | Y | Y | Y¹ | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper |
| tshepo-offline-service | services/tshepo-offline-service | Y | Y | Y¹ | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper |

## Ring 1 — Registry Spine Plane

| service_name | module_path | has_pom | has_code | has_internal_v1 | has_external_v1 | four_header_enforcement | idempotency_on_commands | golden_contract_test | outbox_present | outbox_v11_columns_present | event_envelope_emission | snapshot_endpoint_present | federation_authority_enforced | consistency_class_declared | health_endpoint_present | status | blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| vito-service | services/vito-service | Y | Y | Y | Y | Y (custom+tc) | Y (custom+tc) | Y | Y | Y (V017+V018) | Y | Y | Y | N | Y (actuator) | COMPLIANT | — |
| tuso-service | services/tuso-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (V004) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |
| varapi-service | services/varapi-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (V004) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |
| zibo-service | services/zibo-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (V002) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |
| msika-service | services/msika-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (V003) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |
| indawo-service | services/indawo-service | Y | Y | Y | Y | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |

## Ring 2 — Clinical Execution Plane

| service_name | module_path | has_pom | has_code | has_internal_v1 | has_external_v1 | four_header_enforcement | idempotency_on_commands | golden_contract_test | outbox_present | outbox_v11_columns_present | event_envelope_emission | snapshot_endpoint_present | federation_authority_enforced | consistency_class_declared | health_endpoint_present | status | blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| pct-service | services/pct-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper; no snapshot |
| oros-service | services/oros-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper; no snapshot |
| pharmacy-service | services/pharmacy-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper |
| butano-service | services/butano-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /v1/ routes; needs /internal/v1 wrapper |
| ubomi-service | services/ubomi-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy routes; needs /internal/v1 wrapper |

## Ring 3 — Finance Plane

| service_name | module_path | has_pom | has_code | has_internal_v1 | has_external_v1 | four_header_enforcement | idempotency_on_commands | golden_contract_test | outbox_present | outbox_v11_columns_present | event_envelope_emission | snapshot_endpoint_present | federation_authority_enforced | consistency_class_declared | health_endpoint_present | status | blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| mushex-service | services/mushex-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy /mushex/v1/ routes; needs /internal/v1 wrapper |
| costing-engine-service | services/costing-engine-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy routes; needs /internal/v1 wrapper |
| coverage-service | services/coverage-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |

## Ring 4 — Integration / Operations Plane

| service_name | module_path | has_pom | has_code | has_internal_v1 | has_external_v1 | four_header_enforcement | idempotency_on_commands | golden_contract_test | outbox_present | outbox_v11_columns_present | event_envelope_emission | snapshot_endpoint_present | federation_authority_enforced | consistency_class_declared | health_endpoint_present | status | blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| integration-hub | services/integration-hub | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| notification-service | services/notification-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| channels-service | services/channels-service | Y | Y | Y | Y | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |
| connector-fhir-adapter | services/connector-fhir-adapter | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| dispatch-service | services/dispatch-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |
| document-service | services/document-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy routes; needs /internal/v1 wrapper |
| landela-adapter-service | services/landela-adapter-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy routes; needs /internal/v1 wrapper |
| credential-verification-service | services/credential-verification-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy routes; needs /internal/v1 wrapper |
| share-slip-service | services/share-slip-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy routes; needs /internal/v1 wrapper |
| identity-assurance-service | services/identity-assurance-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| inventory-service | services/inventory-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy routes; needs /internal/v1 wrapper |
| msika-flow-service | services/msika-flow-service | Y | Y | N | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | PARTIAL | Legacy routes; needs /internal/v1 wrapper |
| offline-edge-service | services/offline-edge-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |

## Ring 5 — Experience Plane

| service_name | module_path | has_pom | has_code | has_internal_v1 | has_external_v1 | four_header_enforcement | idempotency_on_commands | golden_contract_test | outbox_present | outbox_v11_columns_present | event_envelope_emission | snapshot_endpoint_present | federation_authority_enforced | consistency_class_declared | health_endpoint_present | status | blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| experience-bff | services/experience-bff | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — ✅ 15 mobile provider BFF controllers added |

## Platform Services (Cross-Cutting)

| service_name | module_path | has_pom | has_code | has_internal_v1 | has_external_v1 | four_header_enforcement | idempotency_on_commands | golden_contract_test | outbox_present | outbox_v11_columns_present | event_envelope_emission | snapshot_endpoint_present | federation_authority_enforced | consistency_class_declared | health_endpoint_present | status | blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| audit-ledger-service | services/audit-ledger-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| campaigns-service | services/campaigns-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| data-access-governance-service | services/data-access-governance-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| data-governance-service | services/data-governance-service | Y | Y | Y | Y | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |
| data-ingestion-service | services/data-ingestion-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| data-pipeline-service | services/data-pipeline-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| data-warehouse-service | services/data-warehouse-service | Y | Y | Y | Y | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| developer-portal-service | services/developer-portal-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| forms-service | services/forms-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| iot-ingestion-service | services/iot-ingestion-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| ndr-service | services/ndr-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| national-data-repository-service | services/national-data-repository-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| observability-service | services/observability-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| reporting-service | services/reporting-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| rules-service | services/rules-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| schema-registry-service | services/schema-registry-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| search-service | services/search-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| security-hardening-service | services/security-hardening-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| support-service | services/support-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |
| surveillance-service | services/surveillance-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | N | N | N | Y (actuator) | COMPLIANT | — |
| workflow-service | services/workflow-service | Y | Y | Y | N | Y (tc) | Y (tc) | Y | Y | Y (init) | Y | Y | N | N | Y (actuator) | COMPLIANT | — |

## Adapter / Agent Services (Limited Compliance Scope)

| service_name | module_path | has_pom | has_code | has_internal_v1 | has_external_v1 | four_header_enforcement | idempotency_on_commands | golden_contract_test | outbox_present | outbox_v11_columns_present | event_envelope_emission | snapshot_endpoint_present | federation_authority_enforced | consistency_class_declared | health_endpoint_present | status | blockers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| card-print-agent | services/card-print-agent | Y | Y | N | N | N (no tc) | N (no tc) | N | Y | Y (init) | N | N | N | N | Y (actuator) | PARTIAL | Agent process, not HTTP-facing; no tech-companion; legacy pattern |
| inventory-elmis-adapter | services/inventory-elmis-adapter | Y | Y | N | N | N (no tc) | N (no tc) | N | N | N | N | N | N | N | Y (actuator) | PARTIAL | Adapter, no API surface; no tech-companion |
| pharmacy-elmis-adapter | services/pharmacy-elmis-adapter | Y | Y | N | N | N (no tc) | N (no tc) | N | N | N | N | N | N | N | Y (actuator) | PARTIAL | Adapter, no API surface; no tech-companion |

## Shared Library (Not a Service)

| service_name | module_path | has_pom | has_code | status | notes |
|---|---|---|---|---|---|
| shared-core | services/shared-core | Y | Y | N/A | Shared library, not a deployable service |

## STUB Services (No Runtime Code)

| service_name | module_path | has_pom | has_code | status | notes |
|---|---|---|---|---|---|
| butano-fhir | services/butano-fhir | N | N | STUB | HAPI FHIR config only, no custom Java code |
| fhir-gateway-service | services/fhir-gateway-service | N | N | STUB | No source code; infrastructure-only |
| inpatient-service | services/inpatient-service | N | N | STUB | DB migration only; no runtime service code |
| jobs-service | services/jobs-service | N | N | STUB | No source code |
| offline-sync-service | services/offline-sync-service | N | N | STUB | DB migration only; no runtime service code |
| pacs-adapter-service | services/pacs-adapter-service | N | N | STUB | No source code; Orthanc adapter placeholder |
| product-registry-service | services/product-registry-service | N | N | STUB | No source code; registry placeholder |

## Summary

| Category | Count |
|---|---|
| **COMPLIANT** | 36 |
| **PARTIAL** (needs /internal/v1 wrapper) | 20 |
| **STUB** (no code) | 7 |
| **N/A** (shared library) | 1 |
| **Total services** | 64 |

## Notes

1. ¹ = has GoldenContractIT that references /internal/v1 but the main controllers use legacy /v1/ routes. The GoldenContractIT auto-discovers endpoints and may skip tests if no /internal/v1 endpoints are found in main source.
2. **tech-companion auto-configuration** provides V11HeaderFilter (order 10), IdempotencyFilter (order 11), TimeoutEnforcementFilter (order 12), CorrelationMdcFilter (order 15), and CompanionExceptionHandler — all activated when `impilo.companion.enabled=true` (default).
3. **health_endpoint_present**: All Spring Boot services include `spring-boot-starter-actuator` which provides `/actuator/health` by default.
4. **Four header enforcement** and **idempotency** are automatically active via TechCompanionAutoConfiguration for any service that depends on `tech-companion`. They apply to `/internal/v1/**` and `/external/v1/**` paths — so services with legacy `/v1/` routes do NOT get enforcement on those routes.
5. **Outbox v1.1 columns**: Services onboarded before Wave 3 (vito, tuso, varapi, msika, zibo) have separate V00x__outbox_v11_columns.sql migrations. All newer services have v1.1 columns in their V001__init.sql.
6. **Federation authority**: Only vito-service currently implements FederationAuthorityGuard on merge operations. Other services do not have national-only operations.
7. **Consistency class**: No services currently declare ConsistencyClass via ActionRegistry. This is a future enhancement tracked in the spec conflicts log.
