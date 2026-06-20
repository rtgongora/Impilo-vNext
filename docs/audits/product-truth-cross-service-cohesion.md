# Product Truth — Cross-Service Cohesion Validation

> Generated: 2026-06-20T11:02:04.418Z

End-to-end journey validation scaffold. Each journey must pass: identity context → BFF → domain services → persistence → UI refresh.

| Journey | Services involved | Status | Notes |
|---------|-------------------|--------|-------|
| identity-login-context | tshepo-authz-service, tshepo-identity-service, vito-service, experience-bff | review | Weakest link: mostly-real / mostly-real / real / mostly-real |
| provider-workforce-context | varapi-service, vashandi-workforce-service, workforce-governance-service | review | Weakest link: real / mostly-real / mostly-real |
| facility-workplace-context | tuso-service, indawo-service, experience-bff | review | Weakest link: real / real / mostly-real |
| registry-to-shr | vito-service, butano-service, pct-service | review | Weakest link: real / mostly-real / real |
| orders-inventory-labs-imaging | oros-service, inventory-service, pacs-adapter-service, pharmacy-service | review | Weakest link: real / real / internal-only / real |
| telemedicine-to-pct | rtc-gateway-service, pct-service, live-service | review | Weakest link: real / real / real |
| learning-to-provider-registry | learning-service, varapi-service | review | Weakest link: real / real |
| costing-billing-payments | costing-engine-service, mushex-service, coverage-service | review | Weakest link: real / mostly-real / real |
| facility-licensing-workspace | tuso-service, indawo-service, credential-verification-service | review | Weakest link: real / real / real |
| public-health-surveillance | surveillance-service, campaigns-service, ndila-service | review | Weakest link: real / real / real |
| blood-services-chain | oros-service, inventory-service, pct-service | review | Weakest link: real / real / real |
| maps-geospatial-logistics | ndila-service, nhume-service, dispatch-service | review | Weakest link: real / mostly-real / real |
| marketplace-procurement-claims | msika-service, msika-flow-service, procurement-service, mushex-service | review | Weakest link: real / mostly-real / mostly-real / mostly-real |
| admin-governance-onboarding | tshepo-authz-service, data-access-governance-service, experience-bff | review | Weakest link: mostly-real / mostly-real / mostly-real |
