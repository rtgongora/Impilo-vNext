# vNext Seven-Plane Architecture

> Generated from `docs/registry/services-registry.yaml` + repo scan.
> Regenerate: `node scripts/full-boot/generate-full-boot-artifacts.mjs`

**This document does not invent plane membership.** Uncertain entries are marked in the full catalog.

## Canonical planes

| Plane ID | Name | Evidence | Component count |
|---|---|---|---|
| trust | Trust, Identity Assurance & Governance | `docs/architecture/planes/01-trust-identity-assurance-governance.md` | 11 |
| registry | Registry & Sovereign Identity Spine | `docs/architecture/planes/02-registry-sovereign-identity-spine.md` | 7 |
| clinical | Clinical Execution & Shared Health Record | `docs/architecture/planes/03-clinical-execution-shared-health-record.md` | 18 |
| data | Data, Intelligence & Public Health | `docs/architecture/planes/04-data-intelligence-public-health.md` | 12 |
| integration | Integration, Interoperability & Edge | `docs/architecture/planes/05-integration-interoperability-edge.md` | 45 |
| experience | Experience, Workflow & Orchestration | `docs/architecture/planes/06-experience-workflow-orchestration.md` | 29 |
| enterprise | Enterprise Resource & Market Operations | `docs/architecture/planes/07-enterprise-resource-market-operations.md` | 15 |

## Trust, Identity Assurance & Governance

Evidence: [`docs/architecture/planes/01-trust-identity-assurance-governance.md`](docs/architecture/planes/01-trust-identity-assurance-governance.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `envoy` | infrastructure | required_full_boot | deployable_but_not_deployed | certain |
| `identity-assurance-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `keycloak` | infrastructure | required_full_boot | deployable_but_not_deployed | certain |
| `mvumo-service` | backend_service | optional_full_boot | deployable_but_not_deployed | certain |
| `tshepo-audit-service` | backend_service | required_full_boot | deployable_but_not_deployed | certain |
| `tshepo-authz-service` | backend_service | required_full_boot | deployable_but_not_deployed | certain |
| `tshepo-consent-service` | backend_service | required_full_boot | deployable_but_not_deployed | certain |
| `tshepo-identity-service` | backend_service | required_full_boot | deployable_but_not_deployed | certain |
| `tshepo-keys-service` | backend_service | required_full_boot | deployable_but_not_deployed | certain |
| `tshepo-offline-service` | backend_service | optional_full_boot | deployable_but_not_deployed | certain |
| `tshepo-service` | backend_service | deprecated_retired | implemented_but_no_deployment_support | certain |

## Registry & Sovereign Identity Spine

Evidence: [`docs/architecture/planes/02-registry-sovereign-identity-spine.md`](docs/architecture/planes/02-registry-sovereign-identity-spine.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `indawo-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `product-registry-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `tuso-service` | backend_service | required_full_boot | implemented_but_no_deployment_support | certain |
| `ubomi-service` | backend_service | required_full_boot | implemented_but_no_deployment_support | certain |
| `varapi-service` | backend_service | required_full_boot | implemented_but_no_deployment_support | certain |
| `vito-service` | backend_service | required_full_boot | implemented_but_no_deployment_support | certain |
| `zibo-service` | backend_service | required_full_boot | implemented_but_no_deployment_support | certain |

## Clinical Execution & Shared Health Record

Evidence: [`docs/architecture/planes/03-clinical-execution-shared-health-record.md`](docs/architecture/planes/03-clinical-execution-shared-health-record.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `butano-fhir` | backend_service | optional_full_boot | deployable_but_not_deployed | certain |
| `butano-service` | backend_service | required_full_boot | deployable_but_not_deployed | certain |
| `clinical-knowledge-platform-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `document-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `fhir-gateway-service` | backend_service | required_full_boot | implemented_but_no_deployment_support | certain |
| `forms-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `guidance-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `hapi-fhir` | infrastructure | optional_full_boot | deployable_but_not_deployed | certain |
| `inpatient-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `inventory-elmis-adapter` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `inventory-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `oros-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `pacs-adapter-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `pct-service` | backend_service | required_full_boot | implemented_but_no_deployment_support | certain |
| `pharmacy-elmis-adapter` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `pharmacy-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `rules-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `scheduling-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |

## Data, Intelligence & Public Health

Evidence: [`docs/architecture/planes/04-data-intelligence-public-health.md`](docs/architecture/planes/04-data-intelligence-public-health.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `ai-model-registry-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `campaigns-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `data-access-governance-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `data-governance-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `data-ingestion-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `data-pipeline-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `data-warehouse-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `national-data-repository-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `ndr-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `reporting-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `search-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `surveillance-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |

## Integration, Interoperability & Edge

Evidence: [`docs/architecture/planes/05-integration-interoperability-edge.md`](docs/architecture/planes/05-integration-interoperability-edge.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `analytics-pipeline-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `asset-registry-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `audit-ledger-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `card-print-agent` | backend_service | optional_full_boot | deployable_but_not_deployed | certain |
| `channels-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `connector-fhir-adapter` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `contract-tests` | library | internal_package | internal_library_only | certain |
| `developer-portal-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `dhis2` | external_dependency | external_dependency | external_dependency | high |
| `dispatch-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `external-elmis` | external_dependency | external_dependency | external_dependency | high |
| `external-pacs-network` | external_dependency | external_dependency | external_dependency | high |
| `federation-connector` | library | internal_package | internal_library_only | certain |
| `integration-hub` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `iot-ingestion-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `jobs-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `kafka` | infrastructure | required_full_boot | deployable_but_not_deployed | certain |
| `landela-adapter-service` | backend_service | optional_full_boot | deployable_but_not_deployed | certain |
| `lims` | external_dependency | external_dependency | external_dependency | high |
| `llm-orchestration-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `minio` | infrastructure | required_full_boot | deployable_but_not_deployed | certain |
| `ndila-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `nhume-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `notification-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `observability-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `offline-edge-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `offline-sdk` | library | internal_package | internal_library_only | certain |
| `offline-sync-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `ops-instrumentation` | library | internal_package | internal_library_only | certain |
| `postgres` | infrastructure | required_full_boot | deployed_and_healthy | certain |
| `redis` | infrastructure | required_full_boot | deployed_and_healthy | certain |
| `referral-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `schema-registry-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `security-baseline` | library | internal_package | internal_library_only | certain |
| `security-hardening-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `shared-core` | library | internal_package | internal_library_only | certain |
| `shared-kernel-java` | library | internal_package | internal_library_only | certain |
| `sms-whatsapp-gateway` | external_dependency | external_dependency | external_dependency | high |
| `support-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `tech-companion` | library | internal_package | internal_library_only | certain |
| `tech-companion-harness` | library | internal_package | internal_library_only | certain |
| `tech-companion-mock` | library | internal_package | internal_library_only | certain |
| `tshepo-contracts` | library | internal_package | internal_library_only | certain |
| `tshepo-sdk` | library | internal_package | internal_library_only | certain |
| `workflow-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |

## Experience, Workflow & Orchestration

Evidence: [`docs/architecture/planes/06-experience-workflow-orchestration.md`](docs/architecture/planes/06-experience-workflow-orchestration.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `butano-web` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `citizen-app` | mobile_app | optional_full_boot | buildable_but_not_containerized | certain |
| `community-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `costa-console` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `developer-console` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `ehr` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `experience-bff` | backend_service | required_full_boot | deployed_and_healthy | certain |
| `inventory-web` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `knowledge-admin` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `learning-service` | backend_service | optional_full_boot | deployable_but_not_deployed | certain |
| `msika-flow-ops` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `msika-flow-portal` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `msika-flow-vendor` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `msika-web` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `mushex-finance-console` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `mushex-ops-console` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `mushex-payer-portal` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `one-ui-shell` | frontend_app | required_full_boot | deployed_and_healthy | certain |
| `ops-console` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `ops-docs` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `oros-web` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `pct-web` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `pharmacy-web` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `portal` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `provider-app` | mobile_app | optional_full_boot | buildable_but_not_containerized | certain |
| `self-service` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `shared-ui` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `support-console` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |
| `zibo-web` | frontend_app | optional_full_boot | buildable_but_not_containerized | certain |

## Enterprise Resource & Market Operations

Evidence: [`docs/architecture/planes/07-enterprise-resource-market-operations.md`](docs/architecture/planes/07-enterprise-resource-market-operations.md)

| Component | Type | Classification | Status | Confidence |
|---|---|---|---|---|
| `banking-rails` | external_dependency | external_dependency | external_dependency | high |
| `costing-engine-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `coverage-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `credential-verification-service` | backend_service | optional_full_boot | deployable_but_not_deployed | certain |
| `general-ledger-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `hr-payroll-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `msika-flow-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `msika-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `mushe-wallet-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `mushex-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `procurement-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `share-slip-service` | backend_service | optional_full_boot | deployable_but_not_deployed | certain |
| `simba-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `wellness-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
| `workforce-governance-service` | backend_service | optional_full_boot | implemented_but_no_deployment_support | certain |
