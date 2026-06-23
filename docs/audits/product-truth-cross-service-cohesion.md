# Product Truth — Cross-Service Cohesion Validation

> Generated: 2026-06-23T17:23:54.424Z

End-to-end journey validation. Each journey must pass: identity context → BFF → domain services → persistence → UI refresh.

**Summary:** 14/14 pass | 0 needs-work | 0 missing-test

| Journey | Services involved | Status | Golden-thread tests | Notes |
|---------|-------------------|--------|---------------------|-------|
| identity-login-context | tshepo-authz-service, tshepo-identity-service, vito-service, experience-bff | **pass** | identity-login-context-golden-thread.test.ts | services: real / real / real / real; tests: 1 |
| provider-workforce-context | varapi-service, vashandi-workforce-service, workforce-governance-service | **pass** | provider-workforce-context-golden-thread.test.ts | services: real / real / real; tests: 1 |
| facility-workplace-context | tuso-service, indawo-service, experience-bff | **pass** | facility-workplace-context-golden-thread.test.ts | services: real / real / real; tests: 1 |
| registry-to-shr | vito-service, butano-service, pct-service | **pass** | registry-to-shr-golden-thread.test.ts | services: real / real / real; tests: 1 |
| orders-inventory-labs-imaging | oros-service, inventory-service, pacs-adapter-service, pharmacy-service | **pass** | orders-inventory-labs-imaging-golden-thread.test.ts, imaging-order-result-golden-thread.test.ts | services: real / real / internal-only / real; tests: 2 |
| telemedicine-to-pct | rtc-gateway-service, pct-service, live-service | **pass** | telemedicine-encounter-golden-thread.test.ts | services: real / real / real; tests: 1 |
| learning-to-provider-registry | learning-service, varapi-service | **pass** | fundo-learning-golden-thread.test.ts, provider-registry-onboarding-golden-thread.test.ts | services: real / real; tests: 2 |
| costing-billing-payments | costing-engine-service, mushex-service, coverage-service | **pass** | payment-billing-claim-golden-thread.test.ts | services: real / real / real; tests: 1 |
| facility-licensing-workspace | tuso-service, indawo-service, credential-verification-service | **pass** | facility-context-selection-golden-thread.test.ts, credential-verification-golden-thread.test.ts | services: real / real / real; tests: 2 |
| public-health-surveillance | surveillance-service, campaigns-service, ndila-service | **pass** | surveillance-outbreak-golden-thread.test.ts | services: real / real / real; tests: 1 |
| blood-services-chain | oros-service, inventory-service, pct-service | **pass** | blood-services-chain-golden-thread.test.ts, madi-golden-thread.test.ts | services: real / real / real; tests: 2 |
| maps-geospatial-logistics | ndila-service, nhume-service, dispatch-service | **pass** | maps-geospatial-logistics-golden-thread.test.ts, dispatch-delivery-golden-thread.test.ts | services: real / real / real; tests: 2 |
| marketplace-procurement-claims | msika-service, msika-flow-service, procurement-service, mushex-service | **pass** | marketplace-procurement-claims-golden-thread.test.ts, marketplace-order-golden-thread.test.ts | services: real / real / real / real; tests: 2 |
| admin-governance-onboarding | tshepo-authz-service, data-access-governance-service, experience-bff | **pass** | admin-governance-onboarding-golden-thread.test.ts | services: real / real / real; tests: 1 |

## Journey definitions

- **identity-login-context**: UI /auth/login/provider-id, /registry → BFF /internal/v1/auth, /internal/v1/identity
- **provider-workforce-context**: UI /work/vashandi/workforce, /registry/providers → BFF /internal/v1/vashandi, /internal/v1/registry
- **facility-workplace-context**: UI /facility, /home → BFF /internal/v1/facilities
- **registry-to-shr**: UI /registry, /ehr → BFF /internal/v1/identity, /internal/v1/fhir, /internal/v1/patients
- **orders-inventory-labs-imaging**: UI /lab, /pharmacy, /imaging → BFF /internal/v1/lab, /internal/v1/pharmacy, /internal/v1/imaging
- **telemedicine-to-pct**: UI /telemedicine/session → BFF /internal/v1/teleconsult, /internal/v1/encounters
- **learning-to-provider-registry**: UI /learning, /registry/providers → BFF /internal/v1/learning, /internal/v1/registry
- **costing-billing-payments**: UI /finance/payer-ops, /coverage → BFF /internal/v1/finance, /internal/v1/coverage
- **facility-licensing-workspace**: UI /facility, /verify/credential → BFF /internal/v1/facilities, /internal/v1/credential
- **public-health-surveillance**: UI /public-health → BFF /internal/v1/public-health
- **blood-services-chain**: UI /madi/orders, /madi/transfusion → BFF /internal/v1/madi
- **maps-geospatial-logistics**: UI /nhume/map, /operations/dispatch → BFF /internal/v1/nhume, /internal/v1/dispatch
- **marketplace-procurement-claims**: UI /marketplace, /marketplace/cart, /finance/payer-ops → BFF /internal/v1/marketplace, /internal/v1/commerce, /internal/v1/finance
- **admin-governance-onboarding**: UI /work/administration-governance, /work/administration-governance/access-requests → BFF /internal/v1/admin/trust, /internal/v1/admin-governance
