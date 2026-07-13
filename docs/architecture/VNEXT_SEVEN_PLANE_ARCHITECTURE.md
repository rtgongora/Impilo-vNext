# vNext Seven-Plane Architecture

> Generated from `docs/registry/services-registry.yaml` + repo scan.
> Regenerate: `node scripts/full-boot/generate-full-boot-artifacts.mjs`

**This document does not invent plane membership.** Uncertain entries are marked in the full catalog.

## Canonical planes

| Plane ID | Name | Evidence | Component count |
|---|---|---|---|
| trust | Trust, Identity Assurance & Governance | `docs/architecture/planes/01-trust-identity-assurance-governance.md` | 12 |
| registry | Registry & Sovereign Identity Spine | `docs/architecture/planes/02-registry-sovereign-identity-spine.md` | 8 |
| clinical | Clinical Execution & Shared Health Record | `docs/architecture/planes/03-clinical-execution-shared-health-record.md` | 20 |
| data | Data, Intelligence & Public Health | `docs/architecture/planes/04-data-intelligence-public-health.md` | 12 |
| integration | Integration, Interoperability & Edge | `docs/architecture/planes/05-integration-interoperability-edge.md` | 46 |
| experience | Experience, Workflow & Orchestration | `docs/architecture/planes/06-experience-workflow-orchestration.md` | 34 |
| enterprise | Enterprise Resource & Market Operations | `docs/architecture/planes/07-enterprise-resource-market-operations.md` | 17 |

## Trust, Identity Assurance & Governance

Evidence: [`docs/architecture/planes/01-trust-identity-assurance-governance.md`](docs/architecture/planes/01-trust-identity-assurance-governance.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `envoy` | infrastructure | required_full_boot | image_strategy_defined | certain |
| `identity-assurance-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `keycloak` | infrastructure | required_full_boot | image_strategy_defined | certain |
| `mvumo-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `opa` | infrastructure | wave_sequenced_full_boot | image_strategy_defined | certain |
| `tshepo-audit-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `tshepo-authz-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `tshepo-consent-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `tshepo-identity-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `tshepo-keys-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `tshepo-offline-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `tshepo-service` | backend_service | deprecated_retired | no_runtime_image_required | certain |

## Registry & Sovereign Identity Spine

Evidence: [`docs/architecture/planes/02-registry-sovereign-identity-spine.md`](docs/architecture/planes/02-registry-sovereign-identity-spine.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `indawo-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `organization-registry-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `product-registry-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `tuso-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `ubomi-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `varapi-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `vito-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `zibo-service` | backend_service | required_full_boot | image_strategy_defined | certain |

## Clinical Execution & Shared Health Record

Evidence: [`docs/architecture/planes/03-clinical-execution-shared-health-record.md`](docs/architecture/planes/03-clinical-execution-shared-health-record.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `butano-fhir` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `butano-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `clinical-knowledge-platform-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `document-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `fhir-gateway-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `forms-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `guidance-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `hapi-fhir` | infrastructure | required_full_boot | image_strategy_defined | certain |
| `inpatient-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `inventory-elmis-adapter` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `inventory-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `madi-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `oros-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `pacs-adapter-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `patient-safety-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `pct-service` | backend_service | required_full_boot | image_strategy_defined | certain |
| `pharmacy-elmis-adapter` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `pharmacy-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `rules-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `scheduling-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |

## Data, Intelligence & Public Health

Evidence: [`docs/architecture/planes/04-data-intelligence-public-health.md`](docs/architecture/planes/04-data-intelligence-public-health.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `ai-model-registry-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `campaigns-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `data-access-governance-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `data-governance-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `data-ingestion-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `data-pipeline-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `data-warehouse-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `national-data-repository-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `ndr-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `reporting-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `search-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `surveillance-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |

## Integration, Interoperability & Edge

Evidence: [`docs/architecture/planes/05-integration-interoperability-edge.md`](docs/architecture/planes/05-integration-interoperability-edge.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `analytics-pipeline-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `asset-registry-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `audit-ledger-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `card-print-agent` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `channels-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `connector-fhir-adapter` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `contract-tests` | library | internal_package | no_runtime_image_required | certain |
| `developer-portal-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `dhis2` | external_dependency | external_dependency | no_runtime_image_required | high |
| `dispatch-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `external-elmis` | external_dependency | external_dependency | no_runtime_image_required | high |
| `external-pacs-network` | external_dependency | external_dependency | no_runtime_image_required | high |
| `federation-connector` | library | internal_package | no_runtime_image_required | certain |
| `integration-hub` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `iot-ingestion-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `jobs-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `kafka` | infrastructure | required_full_boot | image_strategy_defined | certain |
| `landela-adapter-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `lims` | external_dependency | external_dependency | no_runtime_image_required | high |
| `llm-orchestration-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `minio` | infrastructure | required_full_boot | image_strategy_defined | certain |
| `ndila-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `nhume-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `notification-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `observability-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `offline-edge-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `offline-sdk` | library | internal_package | no_runtime_image_required | certain |
| `offline-sync-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `ops-instrumentation` | library | internal_package | no_runtime_image_required | certain |
| `postgres` | infrastructure | required_full_boot | deployed_and_healthy | certain |
| `redis` | infrastructure | required_full_boot | deployed_and_healthy | certain |
| `referral-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `rtc-gateway-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `schema-registry-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `security-baseline` | library | internal_package | no_runtime_image_required | certain |
| `security-hardening-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `shared-core` | library | internal_package | no_runtime_image_required | certain |
| `shared-kernel-java` | library | internal_package | no_runtime_image_required | certain |
| `sms-whatsapp-gateway` | external_dependency | external_dependency | no_runtime_image_required | high |
| `support-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `tech-companion` | library | internal_package | no_runtime_image_required | certain |
| `tech-companion-harness` | library | internal_package | no_runtime_image_required | certain |
| `tech-companion-mock` | library | internal_package | no_runtime_image_required | certain |
| `tshepo-contracts` | library | internal_package | no_runtime_image_required | certain |
| `tshepo-sdk` | library | internal_package | no_runtime_image_required | certain |
| `workflow-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |

## Experience, Workflow & Orchestration

Evidence: [`docs/architecture/planes/06-experience-workflow-orchestration.md`](docs/architecture/planes/06-experience-workflow-orchestration.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `booking-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `butano-web` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `citizen-app` | mobile_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `community-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `costa-console` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `daidzai-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `developer-console` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `ehr` | frontend_app | deprecated_retired | no_runtime_image_required | certain |
| `experience-bff` | backend_service | required_full_boot | deployed_and_healthy | certain |
| `inventory-web` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `khuluma-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `knowledge-admin` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `learning-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `live-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `msika-flow-ops` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `msika-flow-portal` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `msika-flow-vendor` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `msika-web` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `mushex-finance-console` | frontend_app | deprecated_retired | no_runtime_image_required | certain |
| `mushex-ops-console` | frontend_app | deprecated_retired | no_runtime_image_required | certain |
| `mushex-payer-portal` | frontend_app | deprecated_retired | no_runtime_image_required | certain |
| `one-ui-shell` | frontend_app | required_full_boot | deployed_and_healthy | certain |
| `ops-console` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `ops-docs` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `oros-web` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `pct-web` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `pharmacy-web` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `portal` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `provider-app` | mobile_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `rito-quality-safety-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `self-service` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `shared-ui` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `support-console` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |
| `zibo-web` | frontend_app | wave_sequenced_full_boot | no_runtime_image_required | certain |

## Enterprise Resource & Market Operations

Evidence: [`docs/architecture/planes/07-enterprise-resource-market-operations.md`](docs/architecture/planes/07-enterprise-resource-market-operations.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `banking-rails` | external_dependency | external_dependency | no_runtime_image_required | high |
| `costing-engine-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `coverage-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `credential-verification-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `general-ledger-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `hr-payroll-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `msika-apps-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `msika-flow-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `msika-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `mushe-wallet-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `mushex-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `procurement-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `share-slip-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `simba-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `vashandi-workforce-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `wellness-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
| `workforce-governance-service` | backend_service | wave_sequenced_full_boot | image_strategy_defined | certain |
