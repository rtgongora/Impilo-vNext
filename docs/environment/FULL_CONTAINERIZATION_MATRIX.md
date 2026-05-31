# Full Containerization Matrix

| Service | Plane | Dockerfile | Path | Context | Image | Tags | Blocker | Remediation |
|---|---|---|---|---|---|---|---|---|
| ai-model-registry-service | data | present | services/ai-model-registry-service/Dockerfile | services/ai-model-registry-service | impilo/ai-model-registry-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| analytics-pipeline-service | integration | present | services/analytics-pipeline-service/Dockerfile | services/analytics-pipeline-service | impilo/analytics-pipeline-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| asset-registry-service | integration | present | services/asset-registry-service/Dockerfile | services/asset-registry-service | impilo/asset-registry-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| audit-ledger-service | integration | present | services/audit-ledger-service/Dockerfile | services/audit-ledger-service | impilo/audit-ledger-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| butano-fhir | clinical | present | services/butano-fhir/Dockerfile | services/butano-fhir | impilo/butano-fhir | preview, preview-<sha> | — | build-full-vnext-images.sh |
| butano-service | clinical | present | services/butano-service/Dockerfile | services/butano-service | impilo/butano-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| butano-web | experience | missing | — | ui/butano-web | impilo/butano-web | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| campaigns-service | data | present | services/campaigns-service/Dockerfile | services/campaigns-service | impilo/campaigns-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| card-print-agent | integration | present | services/card-print-agent/Dockerfile | services/card-print-agent | impilo/card-print-agent | preview, preview-<sha> | — | build-full-vnext-images.sh |
| channels-service | integration | present | services/channels-service/Dockerfile | services/channels-service | impilo/channels-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| clinical-knowledge-platform-service | clinical | present | services/clinical-knowledge-platform-service/Dockerfile | services/clinical-knowledge-platform-service | impilo/clinical-knowledge-platform-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| community-service | experience | present | services/community-service/Dockerfile | services/community-service | impilo/community-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| connector-fhir-adapter | integration | present | services/connector-fhir-adapter/Dockerfile | services/connector-fhir-adapter | impilo/connector-fhir-adapter | preview, preview-<sha> | — | build-full-vnext-images.sh |
| costa-console | experience | missing | — | ui/costa-console | impilo/costa-console | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| costing-engine-service | enterprise | present | services/costing-engine-service/Dockerfile | services/costing-engine-service | impilo/costing-engine-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| coverage-service | enterprise | present | services/coverage-service/Dockerfile | services/coverage-service | impilo/coverage-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| credential-verification-service | enterprise | present | services/credential-verification-service/Dockerfile | services/credential-verification-service | impilo/credential-verification-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| data-access-governance-service | data | present | services/data-access-governance-service/Dockerfile | services/data-access-governance-service | impilo/data-access-governance-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| data-governance-service | data | present | services/data-governance-service/Dockerfile | services/data-governance-service | impilo/data-governance-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| data-ingestion-service | data | present | services/data-ingestion-service/Dockerfile | services/data-ingestion-service | impilo/data-ingestion-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| data-pipeline-service | data | present | services/data-pipeline-service/Dockerfile | services/data-pipeline-service | impilo/data-pipeline-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| data-warehouse-service | data | present | services/data-warehouse-service/Dockerfile | services/data-warehouse-service | impilo/data-warehouse-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| developer-console | experience | missing | — | ui/developer-console | impilo/developer-console | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| developer-portal-service | integration | present | services/developer-portal-service/Dockerfile | services/developer-portal-service | impilo/developer-portal-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| dispatch-service | integration | present | services/dispatch-service/Dockerfile | services/dispatch-service | impilo/dispatch-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| document-service | clinical | present | services/document-service/Dockerfile | services/document-service | impilo/document-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| ehr | experience | missing | — | ui/ehr | impilo/ehr | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| experience-bff | experience | present | services/experience-bff/Dockerfile | services/experience-bff | impilo/experience-bff | preview, preview-<sha> | — | build-full-vnext-images.sh |
| fhir-gateway-service | clinical | present | services/fhir-gateway-service/Dockerfile | services/fhir-gateway-service | impilo/fhir-gateway-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| forms-service | clinical | present | services/forms-service/Dockerfile | services/forms-service | impilo/forms-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| general-ledger-service | enterprise | present | services/general-ledger-service/Dockerfile | services/general-ledger-service | impilo/general-ledger-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| guidance-service | clinical | present | services/guidance-service/Dockerfile | services/guidance-service | impilo/guidance-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| hr-payroll-service | enterprise | present | services/hr-payroll-service/Dockerfile | services/hr-payroll-service | impilo/hr-payroll-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| identity-assurance-service | trust | present | services/identity-assurance-service/Dockerfile | services/identity-assurance-service | impilo/identity-assurance-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| indawo-service | registry | present | services/indawo-service/Dockerfile | services/indawo-service | impilo/indawo-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| inpatient-service | clinical | present | services/inpatient-service/Dockerfile | services/inpatient-service | impilo/inpatient-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| integration-hub | integration | present | services/integration-hub/Dockerfile | services/integration-hub | impilo/integration-hub | preview, preview-<sha> | — | build-full-vnext-images.sh |
| inventory-elmis-adapter | clinical | present | services/inventory-elmis-adapter/Dockerfile | services/inventory-elmis-adapter | impilo/inventory-elmis-adapter | preview, preview-<sha> | — | build-full-vnext-images.sh |
| inventory-service | clinical | present | services/inventory-service/Dockerfile | services/inventory-service | impilo/inventory-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| inventory-web | experience | missing | — | ui/inventory-web | impilo/inventory-web | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| iot-ingestion-service | integration | present | services/iot-ingestion-service/Dockerfile | services/iot-ingestion-service | impilo/iot-ingestion-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| jobs-service | integration | present | services/jobs-service/Dockerfile | services/jobs-service | impilo/jobs-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| knowledge-admin | experience | missing | — | ui/knowledge-admin | impilo/knowledge-admin | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| landela-adapter-service | integration | present | services/landela-adapter-service/Dockerfile | services/landela-adapter-service | impilo/landela-adapter-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| learning-service | experience | present | services/learning-service/Dockerfile | services/learning-service | impilo/learning-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| llm-orchestration-service | integration | present | services/llm-orchestration-service/Dockerfile | services/llm-orchestration-service | impilo/llm-orchestration-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| msika-flow-ops | experience | missing | — | ui/msika-flow-ops | impilo/msika-flow-ops | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| msika-flow-portal | experience | missing | — | ui/msika-flow-portal | impilo/msika-flow-portal | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| msika-flow-service | enterprise | present | services/msika-flow-service/Dockerfile | services/msika-flow-service | impilo/msika-flow-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| msika-flow-vendor | experience | missing | — | ui/msika-flow-vendor | impilo/msika-flow-vendor | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| msika-service | enterprise | present | services/msika-service/Dockerfile | services/msika-service | impilo/msika-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| msika-web | experience | missing | — | ui/msika-web | impilo/msika-web | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| mushe-wallet-service | enterprise | present | services/mushe-wallet-service/Dockerfile | services/mushe-wallet-service | impilo/mushe-wallet-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| mushex-finance-console | experience | missing | — | ui/mushex-finance-console | impilo/mushex-finance-console | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| mushex-ops-console | experience | missing | — | ui/mushex-ops-console | impilo/mushex-ops-console | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| mushex-payer-portal | experience | missing | — | ui/mushex-payer-portal | impilo/mushex-payer-portal | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| mushex-service | enterprise | present | services/mushex-service/Dockerfile | services/mushex-service | impilo/mushex-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| mvumo-service | trust | present | services/mvumo-service/Dockerfile | services/mvumo-service | impilo/mvumo-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| national-data-repository-service | data | present | services/national-data-repository-service/Dockerfile | services/national-data-repository-service | impilo/national-data-repository-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| ndila-service | integration | present | services/ndila-service/Dockerfile | services/ndila-service | impilo/ndila-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| ndr-service | data | present | services/ndr-service/Dockerfile | services/ndr-service | impilo/ndr-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| nhume-service | integration | present | services/nhume-service/Dockerfile | services/nhume-service | impilo/nhume-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| notification-service | integration | present | services/notification-service/Dockerfile | services/notification-service | impilo/notification-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| observability-service | integration | present | services/observability-service/Dockerfile | services/observability-service | impilo/observability-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| offline-edge-service | integration | present | services/offline-edge-service/Dockerfile | services/offline-edge-service | impilo/offline-edge-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| offline-sync-service | integration | present | services/offline-sync-service/Dockerfile | services/offline-sync-service | impilo/offline-sync-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| one-ui-shell | experience | missing | — | ui/one-ui-shell | impilo/one-ui-shell | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| ops-console | experience | missing | — | ui/ops-console | impilo/ops-console | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| ops-docs | experience | missing | — | ui/ops-docs | impilo/ops-docs | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| oros-service | clinical | present | services/oros-service/Dockerfile | services/oros-service | impilo/oros-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| oros-web | experience | missing | — | ui/oros-web | impilo/oros-web | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| pacs-adapter-service | clinical | present | services/pacs-adapter-service/Dockerfile | services/pacs-adapter-service | impilo/pacs-adapter-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| pct-service | clinical | present | services/pct-service/Dockerfile | services/pct-service | impilo/pct-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| pct-web | experience | missing | — | ui/pct-web | impilo/pct-web | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| pharmacy-elmis-adapter | clinical | present | services/pharmacy-elmis-adapter/Dockerfile | services/pharmacy-elmis-adapter | impilo/pharmacy-elmis-adapter | preview, preview-<sha> | — | build-full-vnext-images.sh |
| pharmacy-service | clinical | present | services/pharmacy-service/Dockerfile | services/pharmacy-service | impilo/pharmacy-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| pharmacy-web | experience | missing | — | ui/pharmacy-web | impilo/pharmacy-web | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| portal | experience | missing | — | ui/portal | impilo/portal | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| procurement-service | enterprise | present | services/procurement-service/Dockerfile | services/procurement-service | impilo/procurement-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| product-registry-service | registry | present | services/product-registry-service/Dockerfile | services/product-registry-service | impilo/product-registry-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| referral-service | integration | present | services/referral-service/Dockerfile | services/referral-service | impilo/referral-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| reporting-service | data | present | services/reporting-service/Dockerfile | services/reporting-service | impilo/reporting-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| rules-service | clinical | present | services/rules-service/Dockerfile | services/rules-service | impilo/rules-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| scheduling-service | clinical | present | services/scheduling-service/Dockerfile | services/scheduling-service | impilo/scheduling-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| schema-registry-service | integration | present | services/schema-registry-service/Dockerfile | services/schema-registry-service | impilo/schema-registry-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| search-service | data | present | services/search-service/Dockerfile | services/search-service | impilo/search-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| security-hardening-service | integration | present | services/security-hardening-service/Dockerfile | services/security-hardening-service | impilo/security-hardening-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| self-service | experience | missing | — | ui/self-service | impilo/self-service | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| share-slip-service | enterprise | present | services/share-slip-service/Dockerfile | services/share-slip-service | impilo/share-slip-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| shared-ui | experience | missing | — | ui/shared-ui | impilo/shared-ui | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| simba-service | enterprise | present | services/simba-service/Dockerfile | services/simba-service | impilo/simba-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| support-console | experience | missing | — | ui/support-console | impilo/support-console | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
| support-service | integration | present | services/support-service/Dockerfile | services/support-service | impilo/support-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| surveillance-service | data | present | services/surveillance-service/Dockerfile | services/surveillance-service | impilo/surveillance-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| tshepo-audit-service | trust | present | services/tshepo-audit-service/Dockerfile | services/tshepo-audit-service | impilo/tshepo-audit-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| tshepo-authz-service | trust | present | services/tshepo-authz-service/Dockerfile | services/tshepo-authz-service | impilo/tshepo-authz-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| tshepo-consent-service | trust | present | services/tshepo-consent-service/Dockerfile | services/tshepo-consent-service | impilo/tshepo-consent-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| tshepo-identity-service | trust | present | services/tshepo-identity-service/Dockerfile | services/tshepo-identity-service | impilo/tshepo-identity-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| tshepo-keys-service | trust | present | services/tshepo-keys-service/Dockerfile | services/tshepo-keys-service | impilo/tshepo-keys-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| tshepo-offline-service | trust | present | services/tshepo-offline-service/Dockerfile | services/tshepo-offline-service | impilo/tshepo-offline-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| tshepo-service | trust | present | services/tshepo-service/Dockerfile | services/tshepo-service | impilo/tshepo-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| tuso-service | registry | present | services/tuso-service/Dockerfile | services/tuso-service | impilo/tuso-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| ubomi-service | registry | present | services/ubomi-service/Dockerfile | services/ubomi-service | impilo/ubomi-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| varapi-service | registry | present | services/varapi-service/Dockerfile | services/varapi-service | impilo/varapi-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| vito-service | registry | present | services/vito-service/Dockerfile | services/vito-service | impilo/vito-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| wellness-service | enterprise | present | services/wellness-service/Dockerfile | services/wellness-service | impilo/wellness-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| workflow-service | integration | present | services/workflow-service/Dockerfile | services/workflow-service | impilo/workflow-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| workforce-governance-service | enterprise | present | services/workforce-governance-service/Dockerfile | services/workforce-governance-service | impilo/workforce-governance-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| zibo-service | registry | present | services/zibo-service/Dockerfile | services/zibo-service | impilo/zibo-service | preview, preview-<sha> | — | build-full-vnext-images.sh |
| zibo-web | experience | missing | — | ui/zibo-web | impilo/zibo-web | preview, preview-<sha> | missing Dockerfile | Add Dockerfile |
