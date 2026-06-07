# Full Helm Deployability Matrix

| Service | Plane | Helm | Location | Ingress | Deployability | Blocker | Next |
|---|---|---|---|---|---|---|---|
| ai-model-registry-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| analytics-pipeline-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| asset-registry-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| audit-ledger-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| banking-rails | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| booking-service | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| butano-fhir | clinical | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| butano-service | clinical | yes | chart_in_helm/ | required | partially_deployable | — | add subchart or impilo-vnext template |
| butano-web | experience | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| campaigns-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| card-print-agent | integration | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| channels-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| citizen-app | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| civil-registry-system | registry_spine | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| clinical-knowledge-platform-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| community-service | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| connector-fhir-adapter | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| contract-tests | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| costa-console | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| costing-engine-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| coverage-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| credential-verification-service | enterprise | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| data-access-governance-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| data-governance-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| data-ingestion-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| data-pipeline-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| data-warehouse-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| developer-console | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| developer-portal-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| dhis2 | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| dispatch-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| document-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| ehr | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| envoy | trust | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| experience-bff | experience | yes | deploy/helm/impilo-vnext | required | partially_deployable | — | add subchart or impilo-vnext template |
| external-elmis | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| external-idp | trust_governance | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| external-pacs-network | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| federation-connector | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| fhir-gateway-service | clinical | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| forms-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| general-ledger-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| guidance-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| hapi-fhir | clinical | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| hr-payroll-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| identity-assurance-service | trust | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| indawo-service | registry | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| inpatient-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| integration-hub | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| inventory-elmis-adapter | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| inventory-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| inventory-web | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| iot-ingestion-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| jobs-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| kafka | integration | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| keycloak | trust | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| knowledge-admin | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| landela-adapter-service | integration | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| learning-service | experience | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| lims | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| llm-orchestration-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| madi-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| minio | integration | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| mosip | trust_governance | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| msika-flow-ops | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| msika-flow-portal | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| msika-flow-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| msika-flow-vendor | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| msika-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| msika-web | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| mushe-wallet-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| mushex-finance-console | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| mushex-ops-console | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| mushex-payer-portal | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| mushex-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| mvumo-service | trust | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| national-data-repository-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| ndila-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| ndr-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| nhume-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| notification-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| observability-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| offline-edge-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| offline-sdk | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| offline-sync-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| one-ui-shell | experience | yes | deploy/helm/impilo-vnext | required | partially_deployable | — | add subchart or impilo-vnext template |
| opa | trust | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| ops-console | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| ops-docs | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| ops-instrumentation | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| oros-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| oros-web | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| pacs-adapter-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| pct-service | clinical | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| pct-web | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| pharmacy-elmis-adapter | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| pharmacy-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| pharmacy-web | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| portal | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| postgres | integration | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| procurement-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| product-registry-service | registry | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| provider-app | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| redis | integration | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| referral-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| reporting-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| rules-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| scheduling-service | clinical | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| schema-registry-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| search-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| security-baseline | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| security-hardening-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| self-service | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| share-slip-service | enterprise | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| shared-core | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| shared-kernel-java | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| shared-ui | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| simba-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| sms-whatsapp-gateway | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| support-console | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| support-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| surveillance-service | data | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| tech-companion | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| tech-companion-harness | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| tech-companion-mock | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| tshepo-audit-service | trust | yes | chart_in_helm/ | required | partially_deployable | — | add subchart or impilo-vnext template |
| tshepo-authz-service | trust | yes | chart_in_helm/ | required | partially_deployable | — | add subchart or impilo-vnext template |
| tshepo-consent-service | trust | yes | chart_in_helm/ | required | partially_deployable | — | add subchart or impilo-vnext template |
| tshepo-contracts | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| tshepo-identity-service | trust | yes | chart_in_helm/ | required | partially_deployable | — | add subchart or impilo-vnext template |
| tshepo-keys-service | trust | yes | chart_in_helm/ | required | partially_deployable | — | add subchart or impilo-vnext template |
| tshepo-offline-service | trust | yes | chart_in_helm/ | optional | partially_deployable | — | add subchart or impilo-vnext template |
| tshepo-sdk | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| tshepo-service | trust | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| tuso-service | registry | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| ubomi-service | registry | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| varapi-service | registry | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| vito-service | registry | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| wellness-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| workflow-service | integration | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| workforce-governance-service | enterprise | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
| zibo-service | registry | no | none | required | not_deployable | no chart | add subchart or impilo-vnext template |
| zibo-web | experience | no | none | optional | not_deployable | no chart | add subchart or impilo-vnext template |
