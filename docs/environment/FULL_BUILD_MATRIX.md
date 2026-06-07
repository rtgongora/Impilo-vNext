# Full Build Matrix

> 129 buildable targets. Regenerate after classification.

| Service | Plane | Path | Tool | Command | Artifact | Status | Failure | Log |
|---|---|---|---|---|---|---|---|---|
| ai-model-registry-service | data | services/ai-model-registry-service | maven | cd services/ai-model-registry-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| analytics-pipeline-service | integration | services/analytics-pipeline-service | maven | cd services/analytics-pipeline-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| asset-registry-service | integration | services/asset-registry-service | maven | cd services/asset-registry-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| audit-ledger-service | integration | services/audit-ledger-service | maven | cd services/audit-ledger-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| booking-service | integration | services/booking-service | maven | cd services/booking-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| butano-fhir | clinical | services/butano-fhir | maven | cd services/butano-fhir && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| butano-service | clinical | services/butano-service | maven | cd services/butano-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| butano-web | experience | ui/butano-web | npm | cd ui/butano-web && npm run build | jar\|dist | not_run | — | — |
| campaigns-service | data | services/campaigns-service | maven | cd services/campaigns-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| card-print-agent | integration | services/card-print-agent | maven | cd services/card-print-agent && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| channels-service | integration | services/channels-service | maven | cd services/channels-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| citizen-app | experience | apps/mobile/citizen-app | pnpm | — | jar\|dist | not_run | — | — |
| clinical-knowledge-platform-service | clinical | services/clinical-knowledge-platform-service | maven | cd services/clinical-knowledge-platform-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| community-service | experience | services/community-service | maven | cd services/community-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| connector-fhir-adapter | integration | services/connector-fhir-adapter | maven | cd services/connector-fhir-adapter && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| contract-tests | integration | libs/contract-tests | maven | cd services/contract-tests && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| costa-console | experience | ui/costa-console | npm | cd ui/costa-console && npm run build | jar\|dist | not_run | — | — |
| costing-engine-service | enterprise | services/costing-engine-service | maven | cd services/costing-engine-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| coverage-service | enterprise | services/coverage-service | maven | cd services/coverage-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| credential-verification-service | enterprise | services/credential-verification-service | maven | cd services/credential-verification-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| data-access-governance-service | data | services/data-access-governance-service | maven | cd services/data-access-governance-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| data-governance-service | data | services/data-governance-service | maven | cd services/data-governance-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| data-ingestion-service | data | services/data-ingestion-service | maven | cd services/data-ingestion-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| data-pipeline-service | data | services/data-pipeline-service | maven | cd services/data-pipeline-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| data-warehouse-service | data | services/data-warehouse-service | maven | cd services/data-warehouse-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| developer-console | experience | ui/developer-console | npm | cd ui/developer-console && npm run build | jar\|dist | not_run | — | — |
| developer-portal-service | integration | services/developer-portal-service | maven | cd services/developer-portal-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| dispatch-service | integration | services/dispatch-service | maven | cd services/dispatch-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| document-service | clinical | services/document-service | maven | cd services/document-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| ehr | experience | ui/ehr | npm | cd ui/ehr && npm run build | jar\|dist | not_run | — | — |
| experience-bff | experience | services/experience-bff | maven | cd services/experience-bff && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| federation-connector | integration | libs/federation-connector | maven | cd services/federation-connector && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| fhir-gateway-service | clinical | services/fhir-gateway-service | maven | cd services/fhir-gateway-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| forms-service | clinical | services/forms-service | maven | cd services/forms-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| general-ledger-service | enterprise | services/general-ledger-service | maven | cd services/general-ledger-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| guidance-service | clinical | services/guidance-service | maven | cd services/guidance-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| hr-payroll-service | enterprise | services/hr-payroll-service | maven | cd services/hr-payroll-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| identity-assurance-service | trust | services/identity-assurance-service | maven | cd services/identity-assurance-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| indawo-service | registry | services/indawo-service | maven | cd services/indawo-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| inpatient-service | clinical | services/inpatient-service | maven | cd services/inpatient-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| integration-hub | integration | services/integration-hub | maven | cd services/integration-hub && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| inventory-elmis-adapter | clinical | services/inventory-elmis-adapter | maven | cd services/inventory-elmis-adapter && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| inventory-service | clinical | services/inventory-service | maven | cd services/inventory-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| inventory-web | experience | ui/inventory-web | npm | cd ui/inventory-web && npm run build | jar\|dist | not_run | — | — |
| iot-ingestion-service | integration | services/iot-ingestion-service | maven | cd services/iot-ingestion-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| jobs-service | integration | services/jobs-service | maven | cd services/jobs-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| knowledge-admin | experience | ui/knowledge-admin | npm | cd ui/knowledge-admin && npm run build | jar\|dist | not_run | — | — |
| landela-adapter-service | integration | services/landela-adapter-service | maven | cd services/landela-adapter-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| learning-service | experience | services/learning-service | maven | cd services/learning-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| live-service | experience | services/live-service | maven | cd services/live-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| llm-orchestration-service | integration | services/llm-orchestration-service | maven | cd services/llm-orchestration-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| madi-service | integration | services/madi-service | maven | cd services/madi-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| msika-apps-service | enterprise | services/msika-apps-service | maven | cd services/msika-apps-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| msika-flow-ops | experience | ui/msika-flow-ops | npm | cd ui/msika-flow-ops && npm run build | jar\|dist | not_run | — | — |
| msika-flow-portal | experience | ui/msika-flow-portal | npm | cd ui/msika-flow-portal && npm run build | jar\|dist | not_run | — | — |
| msika-flow-service | enterprise | services/msika-flow-service | maven | cd services/msika-flow-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| msika-flow-vendor | experience | ui/msika-flow-vendor | npm | cd ui/msika-flow-vendor && npm run build | jar\|dist | not_run | — | — |
| msika-service | enterprise | services/msika-service | maven | cd services/msika-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| msika-web | experience | ui/msika-web | npm | cd ui/msika-web && npm run build | jar\|dist | not_run | — | — |
| mushe-wallet-service | enterprise | services/mushe-wallet-service | maven | cd services/mushe-wallet-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| mushex-finance-console | experience | ui/mushex-finance-console | npm | cd ui/mushex-finance-console && npm run build | jar\|dist | not_run | — | — |
| mushex-ops-console | experience | ui/mushex-ops-console | npm | cd ui/mushex-ops-console && npm run build | jar\|dist | not_run | — | — |
| mushex-payer-portal | experience | ui/mushex-payer-portal | npm | cd ui/mushex-payer-portal && npm run build | jar\|dist | not_run | — | — |
| mushex-service | enterprise | services/mushex-service | maven | cd services/mushex-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| mvumo-service | trust | services/mvumo-service | maven | cd services/mvumo-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| national-data-repository-service | data | services/national-data-repository-service | maven | cd services/national-data-repository-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| ndila-service | integration | services/ndila-service | maven | cd services/ndila-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| ndr-service | data | services/ndr-service | maven | cd services/ndr-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| nhume-service | integration | services/nhume-service | maven | cd services/nhume-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| notification-service | integration | services/notification-service | maven | cd services/notification-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| observability-service | integration | services/observability-service | maven | cd services/observability-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| offline-edge-service | integration | services/offline-edge-service | maven | cd services/offline-edge-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| offline-sdk | integration | libs/offline-sdk | maven | cd services/offline-sdk && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| offline-sync-service | integration | services/offline-sync-service | maven | cd services/offline-sync-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| one-ui-shell | experience | ui/one-ui-shell | npm | cd ui/one-ui-shell && npm run build | jar\|dist | not_run | — | — |
| ops-console | experience | ui/ops-console | npm | cd ui/ops-console && npm run build | jar\|dist | not_run | — | — |
| ops-docs | experience | ui/ops-docs | npm | cd ui/ops-docs && npm run build | jar\|dist | not_run | — | — |
| ops-instrumentation | integration | libs/ops-instrumentation | maven | cd services/ops-instrumentation && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| oros-service | clinical | services/oros-service | maven | cd services/oros-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| oros-web | experience | ui/oros-web | npm | cd ui/oros-web && npm run build | jar\|dist | not_run | — | — |
| pacs-adapter-service | clinical | services/pacs-adapter-service | maven | cd services/pacs-adapter-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| pct-service | clinical | services/pct-service | maven | cd services/pct-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| pct-web | experience | ui/pct-web | npm | cd ui/pct-web && npm run build | jar\|dist | not_run | — | — |
| pharmacy-elmis-adapter | clinical | services/pharmacy-elmis-adapter | maven | cd services/pharmacy-elmis-adapter && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| pharmacy-service | clinical | services/pharmacy-service | maven | cd services/pharmacy-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| pharmacy-web | experience | ui/pharmacy-web | npm | cd ui/pharmacy-web && npm run build | jar\|dist | not_run | — | — |
| portal | experience | ui/portal | npm | cd ui/portal && npm run build | jar\|dist | not_run | — | — |
| procurement-service | enterprise | services/procurement-service | maven | cd services/procurement-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| product-registry-service | registry | services/product-registry-service | maven | cd services/product-registry-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| provider-app | experience | apps/mobile/provider-app | pnpm | — | jar\|dist | not_run | — | — |
| referral-service | integration | services/referral-service | maven | cd services/referral-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| reporting-service | data | services/reporting-service | maven | cd services/reporting-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| rtc-gateway-service | integration | services/rtc-gateway-service | maven | cd services/rtc-gateway-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| rules-service | clinical | services/rules-service | maven | cd services/rules-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| scheduling-service | clinical | services/scheduling-service | maven | cd services/scheduling-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| schema-registry-service | integration | services/schema-registry-service | maven | cd services/schema-registry-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| search-service | data | services/search-service | maven | cd services/search-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| security-baseline | integration | libs/security-baseline | maven | cd services/security-baseline && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| security-hardening-service | integration | services/security-hardening-service | maven | cd services/security-hardening-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| self-service | experience | ui/self-service | npm | cd ui/self-service && npm run build | jar\|dist | not_run | — | — |
| share-slip-service | enterprise | services/share-slip-service | maven | cd services/share-slip-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| shared-core | integration | services/shared-core | maven | cd services/shared-core && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| shared-kernel-java | integration | libs/shared-kernel-java | maven | cd services/shared-kernel-java && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| shared-ui | experience | ui/shared-ui | npm | cd ui/shared-ui && npm run build | jar\|dist | not_run | — | — |
| simba-service | enterprise | services/simba-service | maven | cd services/simba-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| support-console | experience | ui/support-console | npm | cd ui/support-console && npm run build | jar\|dist | not_run | — | — |
| support-service | integration | services/support-service | maven | cd services/support-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| surveillance-service | data | services/surveillance-service | maven | cd services/surveillance-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tech-companion | integration | libs/tech-companion | maven | cd services/tech-companion && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tech-companion-harness | integration | libs/tech-companion-harness | maven | cd services/tech-companion-harness && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tech-companion-mock | integration | libs/tech-companion-mock | maven | cd services/tech-companion-mock && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-audit-service | trust | services/tshepo-audit-service | maven | cd services/tshepo-audit-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-authz-service | trust | services/tshepo-authz-service | maven | cd services/tshepo-authz-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-consent-service | trust | services/tshepo-consent-service | maven | cd services/tshepo-consent-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-contracts | integration | libs/tshepo-contracts | maven | cd services/tshepo-contracts && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-identity-service | trust | services/tshepo-identity-service | maven | cd services/tshepo-identity-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-keys-service | trust | services/tshepo-keys-service | maven | cd services/tshepo-keys-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-offline-service | trust | services/tshepo-offline-service | maven | cd services/tshepo-offline-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-sdk | integration | libs/tshepo-sdk | maven | cd services/tshepo-sdk && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tshepo-service | trust | services/tshepo-service | maven | cd services/tshepo-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| tuso-service | registry | services/tuso-service | maven | cd services/tuso-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| ubomi-service | registry | services/ubomi-service | maven | cd services/ubomi-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| varapi-service | registry | services/varapi-service | maven | cd services/varapi-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| vito-service | registry | services/vito-service | maven | cd services/vito-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| wellness-service | enterprise | services/wellness-service | maven | cd services/wellness-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| workflow-service | integration | services/workflow-service | maven | cd services/workflow-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| workforce-governance-service | enterprise | services/workforce-governance-service | maven | cd services/workforce-governance-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| zibo-service | registry | services/zibo-service | maven | cd services/zibo-service && ./mvnw -q package -DskipTests | jar\|dist | not_run | — | — |
| zibo-web | experience | ui/zibo-web | npm | cd ui/zibo-web && npm run build | jar\|dist | not_run | — | — |
