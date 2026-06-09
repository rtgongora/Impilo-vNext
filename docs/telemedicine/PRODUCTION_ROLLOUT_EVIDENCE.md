# Telemedicine Production Rollout Evidence

Operational rollout evidence for governed teleconsult orchestration + RTC.

## 1) Compose / runtime gate

Experience compose includes:

- `pct-service` with `PCT_TELEMED_EXTERNAL_ENABLED=true`
- `livekit` + `rtc-gateway:8196` (port 8196 avoids mvumo 8197 collision)
- BFF `RTC_GATEWAY_BASE_URL=http://rtc-gateway:8196`

Validation:

- `bash compose/experience/smoke-test.sh` Test 10 (sessions + RTC health)
- `PLAYWRIGHT_SKIP_WEBSERVER=1 npx playwright test e2e/telemedicine-compose.spec.ts`
- `docs/production-readiness/telemedicine-hardening-checklist.md` items verified in SIT

## 2) Mandatory assertions

- Referral/session seed for **CPID-ZW-00001** via `GET /internal/v1/teleconsult/sessions?patientId=...`
- RTC ops health: `GET /internal/v1/teleconsult/ops/rtc-health` returns 200 when gateway healthy
- Mobile demo-fallback gated off in production profiles

## 3) Go-live gate

- Smoke Test 10 green
- LiveKit credentials configured for target environment (not dev keys)
- Registry `production_status: pilot-ready-enrolled-telemedicine`
