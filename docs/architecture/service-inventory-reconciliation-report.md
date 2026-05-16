# Service Inventory Reconciliation Report

## Canonical Universe Reconciliation

- services/pom.xml modules: 93 (service modules: 82, reactor libraries: 11)
- services/* directories with pom.xml: 82
- services-registry entries: services=81, libraries=12
- services-index modules: 93

- present-in-pom-only: none
- present-on-disk-only: none
- present-in-registry-only: none
- present-in-generated-index-only: none

## Required Service Matrix (One Row Per `services/pom.xml` Module)

| module name | exists under services | in services-registry | in services-index | primary_plane | domain | production_status | implementation_status | frontend_wiring_status | OpenAPI present | BFF references | UI references | CI/build references | classification status |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `../libs/shared-kernel-java` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | yes | aligned |
| `../libs/security-baseline` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | no | aligned |
| `shared-core` | yes | yes | yes | ? | ? | ? | ? | ? | no | no | no | yes | aligned |
| `tshepo-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `vito-service` | yes | yes | yes | registry | registry-spine | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `varapi-service` | yes | yes | yes | registry | registry-spine | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `tuso-service` | yes | yes | yes | registry | registry-spine | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `zibo-service` | yes | yes | yes | registry | terminology | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `msika-service` | yes | yes | yes | enterprise | marketplace | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `ubomi-service` | yes | yes | yes | registry | registry-spine | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | yes | aligned |
| `fhir-gateway-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `pct-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `scheduling-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | no | yes | yes | no | aligned |
| `simba-service` | yes | yes | yes | enterprise | wellness-personal-health-data | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `oros-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `pharmacy-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `pharmacy-elmis-adapter` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | no | yes | no | no | aligned |
| `inpatient-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `community-service` | yes | yes | yes | experience | workflow-orchestration | baseline-assessed | implemented-or-partial | wired | yes | yes | yes | no | aligned |
| `inventory-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `inventory-elmis-adapter` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | no | yes | no | no | aligned |
| `product-registry-service` | yes | yes | yes | registry | registry-spine | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `mushex-service` | yes | yes | yes | enterprise | finance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `mushe-wallet-service` | yes | yes | yes | enterprise | finance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `costing-engine-service` | yes | yes | yes | enterprise | finance | baseline-assessed | implemented-or-partial | unknown-or-partial | no | yes | yes | no | aligned |
| `document-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `pacs-adapter-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `notification-service` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `jobs-service` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `integration-hub` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `rules-service` | yes | yes | yes | clinical | clinical-knowledge | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `offline-sync-service` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `card-print-agent` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | no | yes | no | no | aligned |
| `landela-adapter-service` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `credential-verification-service` | yes | yes | yes | enterprise | finance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `share-slip-service` | yes | yes | yes | enterprise | finance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `msika-flow-service` | yes | yes | yes | enterprise | marketplace | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `butano-service` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `butano-fhir` | yes | yes | yes | clinical | care-delivery | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `search-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `learning-service` | yes | yes | yes | experience | workflow-orchestration | baseline-assessed | implemented-or-partial | wired | yes | yes | no | no | aligned |
| `forms-service` | yes | yes | yes | clinical | clinical-knowledge | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `data-pipeline-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `national-data-repository-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `reporting-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `data-access-governance-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `surveillance-service` | yes | yes | yes | data | public-health-surveillance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `campaigns-service` | yes | yes | yes | data | public-health-campaigns | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `identity-assurance-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `observability-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `security-hardening-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `channels-service` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `coverage-service` | yes | yes | yes | enterprise | finance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `indawo-service` | yes | yes | yes | registry | registry-spine | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `data-ingestion-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `data-governance-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `ndr-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `asset-registry-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `dispatch-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `iot-ingestion-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `support-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `audit-ledger-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `offline-edge-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `data-warehouse-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `workflow-service` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `connector-fhir-adapter` | yes | yes | yes | integration | interoperability | baseline-assessed | implemented-or-partial | unknown-or-partial | no | yes | no | no | aligned |
| `tshepo-authz-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `tshepo-identity-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `tshepo-consent-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `mvumo-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `tshepo-audit-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | yes | aligned |
| `tshepo-keys-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `tshepo-offline-service` | yes | yes | yes | trust | identity-governance | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `../libs/tshepo-contracts` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | no | aligned |
| `../libs/tshepo-sdk` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | no | aligned |
| `../libs/tech-companion` | no | yes | yes | ? | ? | ? | ? | ? | no | yes | no | yes | aligned |
| `../libs/federation-connector` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | no | aligned |
| `../libs/tech-companion-harness` | no | yes | yes | ? | ? | ? | ? | ? | no | yes | no | no | aligned |
| `../libs/tech-companion-mock` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | no | aligned |
| `../libs/ops-instrumentation` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | no | aligned |
| `../libs/offline-sdk` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | no | aligned |
| `../libs/contract-tests` | no | yes | yes | ? | ? | ? | ? | ? | no | no | no | no | aligned |
| `developer-portal-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `schema-registry-service` | yes | yes | yes | integration | platform-ops | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `wellness-service` | yes | yes | yes | enterprise | wellness-compatibility-alias | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `experience-bff` | yes | yes | yes | experience | workflow-orchestration | baseline-assessed | implemented-or-partial | wired | yes | yes | yes | yes | aligned |
| `workforce-governance-service` | yes | yes | yes | enterprise | workforce-operations | baseline-assessed | implemented-or-partial | unknown-or-partial | no | yes | no | no | aligned |
| `clinical-knowledge-platform-service` | yes | yes | yes | clinical | clinical-knowledge | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `guidance-service` | yes | yes | yes | clinical | clinical-knowledge | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | yes | no | aligned |
| `ai-model-registry-service` | yes | yes | yes | data | intelligence | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `general-ledger-service` | yes | yes | yes | enterprise | enterprise-resource | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `hr-payroll-service` | yes | yes | yes | enterprise | enterprise-resource | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |
| `procurement-service` | yes | yes | yes | enterprise | enterprise-resource | baseline-assessed | implemented-or-partial | unknown-or-partial | yes | yes | no | no | aligned |

## Focused Corrections Applied In This Pass

- `simba-service`: corrected to `primary_plane=enterprise`, `domain=wellness-personal-health-data`; canonical SoR scope now includes connected sources, personal readings, and remote-alert ownership.
- `wellness-service`: reclassified as enterprise compatibility alias (`domain=wellness-compatibility-alias`) with no canonical SoR responsibilities.
- `surveillance-service`: domain corrected to `public-health-surveillance` and secondary touchpoints expanded (`clinical`, `experience`, `integration`, `registry`, `trust`).
- `campaigns-service`: domain corrected to `public-health-campaigns` and secondary touchpoints expanded (`clinical`, `experience`, `integration`, `registry`, `trust`).
- `mushe-wallet-service`: reactor inclusion + parent/build fixes completed; now reconciled and buildable from `services/pom.xml`.
- `shared-core`: module-vs-library posture ratified as intentional (reactor-built shared library); no outstanding classification blocker.
- `public-health-operations`: ratified as a composite capability over surveillance/campaign services (plus registry context), not a missing deployable module.
