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

## Telemedicine RTC Gateway

PCT remains the clinical telemedicine workflow owner. The RTC Gateway only provisions media rooms and scoped participant tokens.

- `RTC_PROVIDER` — first concrete provider is `LIVEKIT`.
- `RTC_FAIL_CLOSED` — required to be `true` outside development so fake media rooms are not synthesized.
- `RTC_DEV_MODE_ENABLED` — local-only dev mode for tests and non-production demos.
- `RTC_TOKEN_TTL_SECONDS` — scoped media token lifetime.
- `RTC_REQUIRE_CONSENT_REFERENCE_FOR_MEDIA` — requires consent reference for VIDEO/AUDIO media provisioning.
- `RTC_ALLOW_EMERGENCY_WITHOUT_CONSENT` — allows emergency-purpose sessions to bypass media consent reference requirement.
- `RTC_RECORDING_ENABLED`, `RTC_EGRESS_ENABLED` — disabled by default until recording consent and retention policy are implemented.
- `LIVEKIT_ENABLED`, `LIVEKIT_URL`, `LIVEKIT_CLIENT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` — LiveKit runtime binding for `rtc-gateway-service`. `LIVEKIT_URL` is the server-to-server API URL; `LIVEKIT_CLIENT_URL` is the ws/wss URL returned to web and mobile SDK clients.
- `PCT_TELEMED_EXTERNAL_ENABLED`, `PCT_TELEMED_EXTERNAL_BASE_URL`, `PCT_TELEMED_EXTERNAL_SESSION_PATH`, `PCT_TELEMED_EXTERNAL_API_KEY` — PCT external media adapter binding. For Wave A this should point to `rtc-gateway-service`.
- `PCT_TELEMED_FALLBACK_TO_MANAGED_PRIMARY` — set to `false` in production so missing RTC infrastructure fails closed instead of returning placeholder rooms.
- `PCT_TELEMED_REQUIRE_CONSENT_REFERENCE_FOR_MEDIA` — enforces consent reference for governed media sessions at PCT workflow boundary.
- `PCT_TELEMED_ALLOW_EMERGENCY_WITHOUT_CONSENT` — allows emergency sessions to proceed without consent reference.
- `IMPILO_TELEMEDICINE_REQUIRE_TSHEPO_AUTHORIZE` — enforces Tshepo PDP authorization for telemedicine reads/mutations.
- `IMPILO_TELEMEDICINE_TSHEPO_PDP_FALLBACK_ALLOW` — must be `false` in production to avoid allow-on-failure policy.
- `IMPILO_TELEMEDICINE_AUDIT_INGEST_ENABLED` — controls telemedicine trust/audit ingest events.
- `IMPILO_TELEMEDICINE_REQUIRE_MEDIA_CONSENT_REFERENCE` — BFF-side consent reference requirement for VIDEO/AUDIO session initiation.
- `IMPILO_TELEMEDICINE_ALLOW_EMERGENCY_WITHOUT_CONSENT` — permits emergency purpose-of-use sessions without consent reference.
- `IMPILO_TELEMEDICINE_ALLOWED_PURPOSE_OF_USE` — canonical allowed purpose-of-use values for telemedicine governance.

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
