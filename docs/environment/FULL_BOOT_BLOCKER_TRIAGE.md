# Full Boot Blocker Triage

> Updated: 2026-05-31T18:04:14.715293Z

| Plane | Service | Type | Evidence | Log | Fix | Priority | Status |
|-------|---------|------|----------|-----|-----|----------|--------|
| clinical | `butano-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| clinical | `butano-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| trust | `envoy` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| trust | `envoy` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (infrastructure) | P0 | open |
| clinical | `fhir-gateway-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| clinical | `fhir-gateway-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| integration | `kafka` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| integration | `kafka` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (infrastructure) | P0 | open |
| trust | `keycloak` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| trust | `keycloak` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (infrastructure) | P0 | open |
| integration | `minio` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| integration | `minio` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (infrastructure) | P0 | open |
| clinical | `pct-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| clinical | `pct-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| trust | `tshepo-audit-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| trust | `tshepo-audit-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| trust | `tshepo-authz-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| trust | `tshepo-authz-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| trust | `tshepo-consent-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| trust | `tshepo-consent-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| trust | `tshepo-identity-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| trust | `tshepo-identity-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| trust | `tshepo-keys-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| trust | `tshepo-keys-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| registry | `tuso-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| registry | `tuso-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| registry | `ubomi-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| registry | `ubomi-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| registry | `varapi-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| registry | `varapi-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| registry | `vito-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| registry | `vito-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| registry | `zibo-service` | missing_infrastructure | not in impilo-preview | `—` | deploy in impilo-full-preview | P0 | open |
| registry | `zibo-service` | classification | not_deployed_in_preview | `—` | Auto-classified from registry + repo scan (backend_service) | P0 | open |
| clinical | `inventory-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/inventory-service.log` | fix compile/test deps | P0 | open |
| enterprise | `general-ledger-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/general-ledger-service.log` | fix compile/test deps | P0 | open |
| integration | `audit-ledger-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/audit-ledger-service.log` | fix compile/test deps | P0 | open |
| integration | `card-print-agent` | build failure | mvn/npm failed | `reports/full-boot/build-logs/card-print-agent.log` | fix compile/test deps | P0 | open |
| integration | `llm-orchestration-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/llm-orchestration-service.log` | fix compile/test deps | P0 | open |
| data | `data-access-governance-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/data-access-governance-service.log` | fix compile/test deps | P0 | open |
| enterprise | `wellness-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/wellness-service.log` | fix compile/test deps | P0 | open |
| trust | `tshepo-keys-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/tshepo-keys-service.log` | fix compile/test deps | P0 | open |
| registry | `product-registry-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/product-registry-service.log` | fix compile/test deps | P0 | open |
| registry | `zibo-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/zibo-service.log` | fix compile/test deps | P0 | open |
| experience | `experience-bff` | build failure | mvn/npm failed | `reports/full-boot/build-logs/experience-bff.log` | fix compile/test deps | P0 | open |
| data | `data-ingestion-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/data-ingestion-service.log` | fix compile/test deps | P0 | open |
| integration | `observability-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/observability-service.log` | fix compile/test deps | P0 | open |
| data | `data-pipeline-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/data-pipeline-service.log` | fix compile/test deps | P0 | open |
| integration | `iot-ingestion-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/iot-ingestion-service.log` | fix compile/test deps | P0 | open |
| enterprise | `coverage-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/coverage-service.log` | fix compile/test deps | P0 | open |
| clinical | `forms-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/forms-service.log` | fix compile/test deps | P0 | open |
| enterprise | `share-slip-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/share-slip-service.log` | fix compile/test deps | P0 | open |
| data | `data-governance-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/data-governance-service.log` | fix compile/test deps | P0 | open |
| clinical | `pacs-adapter-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/pacs-adapter-service.log` | fix compile/test deps | P0 | open |
| enterprise | `mushex-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/mushex-service.log` | fix compile/test deps | P0 | open |
| integration | `connector-fhir-adapter` | build failure | mvn/npm failed | `reports/full-boot/build-logs/connector-fhir-adapter.log` | fix compile/test deps | P0 | open |
| clinical | `pct-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/pct-service.log` | fix compile/test deps | P0 | open |
| clinical | `oros-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/oros-service.log` | fix compile/test deps | P0 | open |
| clinical | `inventory-elmis-adapter` | build failure | mvn/npm failed | `reports/full-boot/build-logs/inventory-elmis-adapter.log` | fix compile/test deps | P0 | open |
| trust | `identity-assurance-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/identity-assurance-service.log` | fix compile/test deps | P0 | open |
| experience | `shared-ui` | build failure | mvn/npm failed | `reports/full-boot/build-logs/shared-ui.log` | fix compile/test deps | P0 | open |
| clinical | `inpatient-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/inpatient-service.log` | fix compile/test deps | P0 | open |
| clinical | `clinical-knowledge-platform-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/clinical-knowledge-platform-service.log` | fix compile/test deps | P0 | open |
| trust | `tshepo-authz-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/tshepo-authz-service.log` | fix compile/test deps | P0 | open |
| enterprise | `simba-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/simba-service.log` | fix compile/test deps | P0 | open |
| registry | `vito-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/vito-service.log` | fix compile/test deps | P0 | open |
| clinical | `butano-fhir` | build failure | mvn/npm failed | `reports/full-boot/build-logs/butano-fhir.log` | fix compile/test deps | P0 | open |
| clinical | `pharmacy-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/pharmacy-service.log` | fix compile/test deps | P0 | open |
| data | `ndr-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/ndr-service.log` | fix compile/test deps | P0 | open |
| experience | `learning-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/learning-service.log` | fix compile/test deps | P0 | open |
| trust | `mvumo-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/mvumo-service.log` | fix compile/test deps | P0 | open |
| enterprise | `procurement-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/procurement-service.log` | fix compile/test deps | P0 | open |
| integration | `ndila-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/ndila-service.log` | fix compile/test deps | P0 | open |
| integration | `notification-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/notification-service.log` | fix compile/test deps | P0 | open |
| data | `surveillance-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/surveillance-service.log` | fix compile/test deps | P0 | open |
| enterprise | `workforce-governance-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/workforce-governance-service.log` | fix compile/test deps | P0 | open |
| data | `reporting-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/reporting-service.log` | fix compile/test deps | P0 | open |
| enterprise | `credential-verification-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/credential-verification-service.log` | fix compile/test deps | P0 | open |
| integration | `dispatch-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/dispatch-service.log` | fix compile/test deps | P0 | open |
| registry | `ubomi-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/ubomi-service.log` | fix compile/test deps | P0 | open |
| integration | `jobs-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/jobs-service.log` | fix compile/test deps | P0 | open |
| trust | `tshepo-identity-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/tshepo-identity-service.log` | fix compile/test deps | P0 | open |
| registry | `indawo-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/indawo-service.log` | fix compile/test deps | P0 | open |
| integration | `channels-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/channels-service.log` | fix compile/test deps | P0 | open |
| integration | `developer-portal-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/developer-portal-service.log` | fix compile/test deps | P0 | open |
| integration | `integration-hub` | build failure | mvn/npm failed | `reports/full-boot/build-logs/integration-hub.log` | fix compile/test deps | P0 | open |
| integration | `schema-registry-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/schema-registry-service.log` | fix compile/test deps | P0 | open |
| clinical | `butano-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/butano-service.log` | fix compile/test deps | P0 | open |
| enterprise | `mushe-wallet-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/mushe-wallet-service.log` | fix compile/test deps | P0 | open |
| clinical | `rules-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/rules-service.log` | fix compile/test deps | P0 | open |
| clinical | `pharmacy-elmis-adapter` | build failure | mvn/npm failed | `reports/full-boot/build-logs/pharmacy-elmis-adapter.log` | fix compile/test deps | P0 | open |
| data | `data-warehouse-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/data-warehouse-service.log` | fix compile/test deps | P0 | open |
| enterprise | `hr-payroll-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/hr-payroll-service.log` | fix compile/test deps | P0 | open |
| registry | `varapi-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/varapi-service.log` | fix compile/test deps | P0 | open |
| trust | `tshepo-consent-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/tshepo-consent-service.log` | fix compile/test deps | P0 | open |
| enterprise | `costing-engine-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/costing-engine-service.log` | fix compile/test deps | P0 | open |
| integration | `security-hardening-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/security-hardening-service.log` | fix compile/test deps | P0 | open |
| integration | `nhume-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/nhume-service.log` | fix compile/test deps | P0 | open |
| trust | `tshepo-audit-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/tshepo-audit-service.log` | fix compile/test deps | P0 | open |
| integration | `referral-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/referral-service.log` | fix compile/test deps | P0 | open |
| experience | `community-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/community-service.log` | fix compile/test deps | P0 | open |
| integration | `support-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/support-service.log` | fix compile/test deps | P0 | open |
| trust | `tshepo-offline-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/tshepo-offline-service.log` | fix compile/test deps | P0 | open |
| integration | `asset-registry-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/asset-registry-service.log` | fix compile/test deps | P0 | open |
| integration | `workflow-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/workflow-service.log` | fix compile/test deps | P0 | open |
| data | `national-data-repository-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/national-data-repository-service.log` | fix compile/test deps | P0 | open |
| clinical | `fhir-gateway-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/fhir-gateway-service.log` | fix compile/test deps | P0 | open |
| integration | `offline-sync-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/offline-sync-service.log` | fix compile/test deps | P0 | open |
| enterprise | `msika-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/msika-service.log` | fix compile/test deps | P0 | open |
| clinical | `guidance-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/guidance-service.log` | fix compile/test deps | P0 | open |
| registry | `tuso-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/tuso-service.log` | fix compile/test deps | P0 | open |
| integration | `offline-edge-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/offline-edge-service.log` | fix compile/test deps | P0 | open |
| data | `campaigns-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/campaigns-service.log` | fix compile/test deps | P0 | open |
| clinical | `scheduling-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/scheduling-service.log` | fix compile/test deps | P0 | open |
| data | `search-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/search-service.log` | fix compile/test deps | P0 | open |
| enterprise | `msika-flow-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/msika-flow-service.log` | fix compile/test deps | P0 | open |
| integration | `analytics-pipeline-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/analytics-pipeline-service.log` | fix compile/test deps | P0 | open |
| data | `ai-model-registry-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/ai-model-registry-service.log` | fix compile/test deps | P0 | open |
| integration | `landela-adapter-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/landela-adapter-service.log` | fix compile/test deps | P0 | open |
| clinical | `document-service` | build failure | mvn/npm failed | `reports/full-boot/build-logs/document-service.log` | fix compile/test deps | P0 | open |
