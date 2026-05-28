# Telemedicine Hardening Checklist

Use this checklist before promoting telemedicine RTC changes to production.

## Governance Gates

- `IMPILO_TELEMEDICINE_REQUIRE_TSHEPO_AUTHORIZE=true`
- `IMPILO_TELEMEDICINE_TSHEPO_PDP_FALLBACK_ALLOW=false`
- `IMPILO_TELEMEDICINE_REQUIRE_MEDIA_CONSENT_REFERENCE=true`
- `IMPILO_TELEMEDICINE_ALLOWED_PURPOSE_OF_USE` includes only approved values
- Mobile/web session creation rejects unsupported `purposeOfUse`

## Fail-Closed Media Gates

- `RTC_FAIL_CLOSED=true`
- `PCT_TELEMED_FALLBACK_TO_MANAGED_PRIMARY=false`
- `RTC_REQUIRE_CONSENT_REFERENCE_FOR_MEDIA=true`
- `PCT_TELEMED_REQUIRE_CONSENT_REFERENCE_FOR_MEDIA=true`
- `LIVEKIT_CLIENT_URL` is valid `ws://` or `wss://`

## Runtime Configuration Gates

- `LIVEKIT_URL`, `LIVEKIT_API_KEY`, and `LIVEKIT_API_SECRET` configured for server-to-server RTC Gateway calls
- `PCT_TELEMED_EXTERNAL_BASE_URL` points to `rtc-gateway-service`
- `PCT_TELEMED_EXTERNAL_SESSION_PATH=/internal/v1/rtc/sessions`
- `RTC_RECORDING_ENABLED=false` until recording consent/retention workflows are formally approved

## Observability Gates

- `impilo_rtc_session_provisioned_total` emits and trends normally
- `impilo_rtc_session_provision_failed_total` has alert threshold configured
- `impilo_rtc_token_issued_total` emits for participant joins
- Telemedicine lifecycle outbox events present (`room_provisioned`, `media_started`, `media_ended`)

## Client Resilience Gates

- Web and mobile clients show governed fallback when room URL or token is missing
- Web and mobile clients reject invalid non-websocket media endpoint values
- Session continues as clinical workflow (notes/messages/signal actions) when media fails

## Verification Commands

- `mvn -f services/rtc-gateway-service/pom.xml test`
- `mvn -f services/experience-bff/pom.xml "-Dtest=MobileTelemedicineControllerTest,TeleconsultControllerTest" test`
- `mvn -f services/pct-service/pom.xml -DskipTests compile`
- `pnpm --dir ui/one-ui-shell type-check`
- `pnpm --dir ui/one-ui-shell vitest run src/components/telemedicine/LiveKitConsultRoom.test.ts src/app/telemedicine/session/[sessionId]/page.test.tsx`
- `pnpm --dir apps/mobile --filter @impilo/provider-app type-check`
- `pnpm --dir apps/mobile --filter @impilo/provider-app exec vitest run src/__tests__/messaging/LiveKitMobileConsultRoom.test.ts src/__tests__/messaging/Telemedicine.test.tsx src/__tests__/messaging/TelemedicineHealth.test.ts`
- `pnpm --dir apps/mobile --filter @impilo/citizen-app type-check`
- `pnpm --dir apps/mobile --filter @impilo/citizen-app exec vitest run src/__tests__/telehealth/TelehealthFlow.test.tsx`
