# Forbidden Responsibilities Map

| Service ID | Primary plane | Forbidden responsibilities |
|---|---|---|
| `ai-model-registry-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `asset-registry-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `audit-ledger-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `butano-fhir` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `butano-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `campaigns-service` | data | must-not-own-individual-clinical-encounter-record, must-not-own-patient-identity-source-of-truth, must-not-bypass-data-governance-or-consent-policy, must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries |
| `card-print-agent` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `channels-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `clinical-knowledge-platform-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `community-service` | experience | must-not-own-domain-source-data, must-not-bypass-bff-authz-audit-controls |
| `connector-fhir-adapter` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `costing-engine-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `coverage-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `credential-verification-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `data-access-governance-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `data-governance-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `data-ingestion-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `data-pipeline-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `data-warehouse-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `developer-portal-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `dispatch-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `document-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `experience-bff` | experience | must-not-own-domain-source-data, must-not-bypass-bff-authz-audit-controls |
| `fhir-gateway-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `forms-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `general-ledger-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `guidance-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `hr-payroll-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `identity-assurance-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `indawo-service` | registry | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `inpatient-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `integration-hub` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `inventory-elmis-adapter` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `inventory-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `iot-ingestion-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `jobs-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `landela-adapter-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `learning-service` | experience | must-not-own-domain-source-data, must-not-bypass-bff-authz-audit-controls |
| `msika-flow-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `msika-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `mushe-wallet-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `mushex-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `mvumo-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `national-data-repository-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `ndr-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `notification-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `observability-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `offline-edge-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `offline-sync-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `oros-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `pacs-adapter-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `pct-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `pharmacy-elmis-adapter` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `pharmacy-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `procurement-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `product-registry-service` | registry | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `reporting-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `rules-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `scheduling-service` | clinical | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `schema-registry-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `search-service` | data | must-not-handle-care-transaction-orchestration, must-not-bypass-consent-governance |
| `security-hardening-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `share-slip-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `simba-service` | clinical | must-not-own-clinical-encounter-lifecycle, must-not-own-acute-care-orders-or-results, must-not-own-prescription-dispensing, must-not-own-inpatient-care-state, must-not-own-patient-identity-source-of-truth, must-not-own-provider-identity-source-of-truth, must-not-own-facility-registry, must-not-own-consent-policy-authority, must-not-own-payment-ledgers, must-not-own-public-health-surveillance-source-of-truth |
| `support-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `surveillance-service` | data | must-not-own-individual-clinical-encounter-record, must-not-own-patient-identity-source-of-truth, must-not-bypass-data-governance-or-consent-policy, must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries |
| `tshepo-audit-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-authz-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-consent-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-identity-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-keys-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-offline-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tshepo-service` | trust | must-not-own-clinical-record-content, must-not-own-billing-ledgers |
| `tuso-service` | registry | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `ubomi-service` | registry | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `varapi-service` | registry | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `vito-service` | registry | must-not-authorize-access-decisions, must-not-own-clinical-encounters |
| `wellness-service` | clinical | must-not-own-public-health-surveillance-source-of-truth, must-not-own-clinical-encounter-lifecycle, must-not-own-marketplace-or-payment-ledgers, must-not-own-patient-identity-source-of-truth, must-not-own-provider-identity-source-of-truth, must-not-own-facility-registry |
| `workflow-service` | integration | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `workforce-governance-service` | enterprise | must-not-store-clinical-records-as-source-of-truth, must-not-own-identity-assurance-policy |
| `zibo-service` | registry | must-not-authorize-access-decisions, must-not-own-clinical-encounters |