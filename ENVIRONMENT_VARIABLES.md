# Environment Variables

This file captures the most important variable families for production-preparation setup. Use `.env.example` and service `application.yml` files as source of truth for full lists.

## Core Platform

- `DB_*` / datasource-specific variables per service
- `KAFKA_BOOTSTRAP`
- `REDIS_HOST`, `REDIS_PORT`
- `KEYCLOAK_URL`, `KEYCLOAK_REALM` and JWT issuer/audience settings

## Trust / Tshepo

- Tenant and trust context headers are propagated at request level (not env), but service config relies on:
  - Tshepo base URLs (`TSHEPO_*_BASE_URL` family)
  - Consent/audit/identity/keys service URLs where applicable

## Experience BFF Downstream Routing

Configured via `impilo.services.*` in `services/experience-bff`:
- `pctBaseUrl`, `orosBaseUrl`, `pharmacyBaseUrl`, `butanoBaseUrl`
- `vitoBaseUrl`, `tusoBaseUrl`, `varapiBaseUrl`, `ziboBaseUrl`, `ubomiBaseUrl`
- `msikaBaseUrl`, `msikaFlowBaseUrl`, `msikaAppsBaseUrl`
- `mushexBaseUrl`, `costaBaseUrl`, `channelsBaseUrl`, `dispatchBaseUrl`
- `integrationHubBaseUrl`, `workflowBaseUrl`, `fhirGatewayBaseUrl`, `searchBaseUrl`
- `communityBaseUrl`, `wellnessBaseUrl`, `supportBaseUrl`, `workforceGovernanceBaseUrl`

## Ndila / Nhume

- `NHUME_NDILA_*`
- `NHUME_COMMS_HUB_BASE_URL`
- `NHUME_TSHEPO_PDP_URL`
- `NDILA_*` provider and map/routing configuration variables

## AI / Nompilo

- LLM provider keys and endpoints should be configured by provider adapter (Gemini default target doctrine).
- Keep provider credentials out of source; use environment-specific secret stores.

## Mobile

- Expo/EAS config in `apps/mobile/*/app.config.ts` and `eas.json`.
- API base URLs and environment mode toggles in app config modules.

## Recommendation

For production preparation, maintain one validated environment matrix per environment (dev/staging/prod) including:
- required variable
- owning service
- secret/non-secret classification
- default/fallback behavior
- validation method
