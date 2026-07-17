# Product Truth — Service Inventory

> Generated: 2026-07-16T10:10:10.405Z
> Scanner: `scripts/completeness/generate-product-truth.mjs`
> Total services: **97** | Libraries: **12** | UI workspaces: **20**

## Summary by product status

| Status | Count |
|--------|------:|
| internal-only | 25 |
| real | 72 |

## Service inventory

| Service | Plane | DB | API | Contract | BFF | Web UI | Mobile | Tests | Product status |
|---------|-------|----|-----|----------|-----|--------|--------|-------|----------------|
| ai-model-registry-service | data | real | real | real | real | absent | absent | real | internal-only |
| analytics-pipeline-service | integration | real | real | real | real | real | absent | real | internal-only |
| asset-registry-service | integration | real | real | real | real | real | n/a | real | real |
| audit-ledger-service | integration | real | real | real | absent | thin | absent | real | internal-only |
| booking-service | experience | real | real | real | real | real | real | real | real |
| butano-fhir | clinical | real | real | real | real | real | n/a | real | real |
| butano-service | clinical | real | real | real | real | real | n/a | real | real |
| campaigns-service | data | real | real | real | real | real | real | real | real |
| card-print-agent | integration | real | real | real | absent | thin | absent | real | internal-only |
| channels-service | integration | real | real | real | real | real | n/a | real | real |
| clinical-knowledge-platform-service | clinical | real | real | real | real | real | n/a | real | real |
| community-service | experience | real | real | real | real | real | real | real | real |
| connector-fhir-adapter | integration | real | real | real | absent | thin | absent | real | internal-only |
| costing-engine-service | enterprise | real | real | real | real | real | n/a | real | real |
| coverage-service | enterprise | real | real | real | real | real | real | real | real |
| daidzai-service | experience | real | real | real | real | real | n/a | real | real |
| credential-verification-service | enterprise | real | real | real | real | real | n/a | real | real |
| data-access-governance-service | data | real | real | real | real | real | n/a | real | real |
| data-governance-service | data | real | real | real | real | real | n/a | real | real |
| data-ingestion-service | data | real | real | real | real | thin | absent | real | internal-only |
| data-pipeline-service | data | real | real | real | real | real | absent | real | internal-only |
| data-warehouse-service | data | real | real | real | real | thin | absent | real | internal-only |
| developer-portal-service | integration | real | real | real | real | real | n/a | real | real |
| dispatch-service | integration | real | real | real | real | real | n/a | real | real |
| document-service | clinical | real | real | real | real | real | n/a | real | real |
| experience-bff | experience | real | real | real | n/a | real | n/a | real | real |
| fhir-gateway-service | clinical | real | real | real | real | thin | absent | real | internal-only |
| forms-service | clinical | real | real | real | real | real | n/a | real | real |
| general-ledger-service | enterprise | real | real | real | real | real | n/a | real | real |
| guidance-service | clinical | real | real | real | real | real | n/a | real | real |
| hr-payroll-service | enterprise | real | real | real | real | real | n/a | real | real |
| identity-assurance-service | trust | real | real | real | real | real | n/a | real | real |
| indawo-service | registry | real | real | real | real | real | n/a | real | real |
| inpatient-service | clinical | real | real | real | real | real | n/a | real | real |
| integration-hub | integration | real | real | real | absent | real | real | real | internal-only |
| inventory-elmis-adapter | clinical | real | real | real | real | thin | absent | real | internal-only |
| inventory-service | clinical | real | real | real | real | real | n/a | real | real |
| iot-ingestion-service | integration | real | real | real | real | real | absent | real | internal-only |
| jobs-service | integration | real | real | real | absent | real | real | real | internal-only |
| landela-adapter-service | integration | real | real | real | absent | real | absent | real | internal-only |
| learning-service | experience | real | real | real | real | real | real | real | real |
| live-service | experience | real | real | real | real | real | real | real | real |
| llm-orchestration-service | integration | real | real | absent | real | absent | real | real | internal-only |
| madi-service | clinical | real | real | real | real | real | n/a | real | real |
| msika-apps-service | enterprise | real | real | real | real | real | n/a | real | real |
| msika-flow-service | enterprise | real | real | real | real | real | n/a | real | real |
| msika-service | enterprise | real | real | real | real | real | n/a | real | real |
| mushe-wallet-service | enterprise | real | real | real | real | real | n/a | real | real |
| mushex-service | enterprise | real | real | real | real | real | n/a | real | real |
| mvumo-service | trust | real | real | real | real | real | n/a | real | real |
| national-data-repository-service | data | real | real | real | absent | thin | absent | real | internal-only |
| ndila-service | integration | real | real | real | real | real | n/a | real | real |
| ndr-service | data | real | real | real | real | real | absent | real | internal-only |
| nhume-service | integration | real | real | real | real | real | n/a | real | real |
| notification-service | integration | real | real | real | real | real | real | real | real |
| observability-service | integration | real | real | real | real | real | absent | real | internal-only |
| offline-edge-service | integration | real | real | real | absent | thin | real | real | internal-only |
| offline-sync-service | integration | real | real | real | absent | real | real | real | internal-only |
| oros-service | clinical | real | real | real | real | real | real | real | real |
| pacs-adapter-service | clinical | real | real | real | absent | real | real | real | internal-only |
| patient-safety-service | clinical | real | real | real | real | real | n/a | real | real |
| pct-service | clinical | real | real | real | real | real | real | real | real |
| pharmacy-elmis-adapter | clinical | real | real | real | absent | thin | absent | real | internal-only |
| pharmacy-service | clinical | real | real | real | real | real | real | real | real |
| procurement-service | enterprise | real | real | real | real | real | n/a | real | real |
| product-registry-service | registry | real | real | real | real | real | n/a | real | real |
| referral-service | integration | real | real | real | real | real | n/a | real | real |
| reporting-service | data | real | real | real | real | real | n/a | real | real |
| rito-quality-safety-service | experience | real | real | real | real | real | n/a | real | real |
| rtc-gateway-service | integration | real | real | real | real | real | n/a | real | real |
| rules-service | clinical | real | real | real | real | real | n/a | real | real |
| scheduling-service | clinical | real | real | real | real | real | n/a | real | real |
| schema-registry-service | integration | real | real | real | absent | thin | absent | real | internal-only |
| search-service | data | real | real | real | real | real | n/a | real | real |
| security-hardening-service | integration | real | real | real | absent | thin | absent | real | internal-only |
| share-slip-service | enterprise | real | real | real | real | real | n/a | real | real |
| simba-service | enterprise | real | real | real | real | real | real | real | real |
| support-service | integration | real | real | real | real | real | n/a | real | real |
| surveillance-service | data | real | real | real | real | real | real | real | real |
| tshepo-audit-service | trust | real | real | real | real | real | n/a | real | real |
| tshepo-authz-service | trust | real | real | real | real | real | n/a | real | real |
| tshepo-consent-service | trust | real | real | real | real | real | n/a | real | real |
| tshepo-identity-service | trust | real | real | real | real | real | n/a | real | real |
| tshepo-keys-service | trust | real | real | real | real | real | n/a | real | real |
| tshepo-offline-service | trust | real | real | real | real | real | n/a | real | real |
| tshepo-service | trust | real | real | real | real | real | n/a | real | real |
| tuso-service | registry | real | real | real | real | real | n/a | real | real |
| ubomi-service | registry | real | real | real | real | real | n/a | real | real |
| varapi-service | registry | real | real | real | real | real | n/a | real | real |
| vito-service | registry | real | real | real | real | real | real | real | real |
| wellness-service | enterprise | real | real | real | real | real | real | real | internal-only |
| workflow-service | integration | real | real | real | real | real | n/a | real | real |
| workforce-governance-service | enterprise | real | real | real | real | real | n/a | real | real |
| vashandi-workforce-service | enterprise | real | real | real | real | real | n/a | real | real |
| organization-registry-service | registry | real | real | real | real | real | n/a | real | real |
| khuluma-service | experience | real | real | real | real | real | n/a | real | real |
| zibo-service | registry | real | real | real | real | real | n/a | real | real |

## Libraries

- **shared-kernel-java** — `libs/shared-kernel-java`
- **security-baseline** — `libs/security-baseline`
- **shared-core** — `services/shared-core`
- **tshepo-contracts** — `libs/tshepo-contracts`
- **tshepo-sdk** — `libs/tshepo-sdk`
- **tech-companion** — `libs/tech-companion`
- **federation-connector** — `libs/federation-connector`
- **tech-companion-harness** — `libs/tech-companion-harness`
- **tech-companion-mock** — `libs/tech-companion-mock`
- **ops-instrumentation** — `libs/ops-instrumentation`
- **offline-sdk** — `libs/offline-sdk`
- **contract-tests** — `libs/contract-tests`
