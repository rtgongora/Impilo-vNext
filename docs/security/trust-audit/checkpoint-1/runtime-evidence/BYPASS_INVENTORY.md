# Runtime bypass inventory — deployed impilo-full-preview

Classification key: BYPASSABLE = auth disabled/permit-all/anonymous in the running config.

## IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true  (96 services — OAuth resource-server DISABLED)

abis-service, ai-model-registry-service, analytics-pipeline-service, asset-registry-service, audit-ledger-service, booking-service, butano-fhir, butano-service, campaigns-service, card-print-agent, channels-service, clinical-knowledge-platform-service, community-service, connector-fhir-adapter, costing-engine-service, coverage-service, credential-verification-service, daidzai-service, data-access-governance-service, data-governance-service, data-ingestion-service, data-pipeline-service, data-warehouse-service, developer-portal-service, dispatch-service, document-service, fhir-gateway-service, forms-service, general-ledger-service, guidance-service, hr-payroll-service, identity-assurance-service, indawo-service, inpatient-service, integration-hub, inventory-elmis-adapter, inventory-service, iot-ingestion-service, jobs-service, khuluma-service, landela-adapter-service, learning-service, live-service, llm-orchestration-service, madi-service, msika-apps-service, msika-flow-service, msika-service, mushe-wallet-service, mushex-service, mvumo-service, national-data-repository-service, ndila-service, ndr-service, nhume-service, notification-service, observability-service, offline-edge-service, offline-sync-service, organization-registry-service, oros-service, pacs-adapter-service, participation-service, patient-safety-service, pct-service, pharmacy-elmis-adapter, pharmacy-service, procurement-service, product-registry-service, referral-service, reporting-service, rito-quality-safety-service, rtc-gateway-service, rules-service, scheduling-service, schema-registry-service, search-service, security-hardening-service, share-slip-service, simba-service, support-service, surveillance-service, telemonitoring-service, tshepo-consent-service, tshepo-identity-service, tshepo-keys-service, tshepo-offline-service, tuso-service, ubomi-service, varapi-service, vashandi-workforce-service, vito-service, wellness-service, workflow-service, workforce-governance-service, zibo-service

## IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=false (2 services — OAuth enabled)

tshepo-audit-service, tshepo-authz-service

## Additional explicit bypass / permit-all / anonymous flags

- **ai-model-registry-service**: AIR_SECURITY_ALLOW_INSECURE_PERMIT_ALL=true
- **dispatch-service**: DISPATCH_SECURITY_OAUTH2_ENABLED=false
- **experience-bff**: IMPILO_SECURITY_ALLOW_ANONYMOUS=false
- **iot-ingestion-service**: IMPILO_SECURITY_MODE=permit-all
- **llm-orchestration-service**: LLM_SECURITY_ALLOW_INSECURE_PERMIT_ALL=true
- **mushex-service**: MUSHEX_SANDBOX_BYPASS_CREDENTIAL_CHECK=true
- **ndila-service**: NDILA_ALLOW_ANONYMOUS=true
- **product-registry-service**: IMPILO_SECURITY_ALLOW_ANONYMOUS=true

## Notes
- experience-bff IMPILO_SECURITY_ALLOW_ANONYMOUS=false and AUTH_FALLBACK_ENABLED=false are HARDENING (not bypasses).
- Empty issuer URI on oros/pct/product-registry means the JWT resource-server has no issuer to validate against.
- tshepo-authz-service and tshepo-audit-service are the ONLY two services with OAuth validation enabled.
