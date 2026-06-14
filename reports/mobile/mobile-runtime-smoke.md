# Mobile Runtime Smoke Report

> **Generated:** 2026-06-14 · Preview API: `http://41.57.127.235`  
> **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl` · HEAD `c0e65ddb`

## Summary

| Check | Result | Notes |
|-------|--------|-------|
| `pnpm install --frozen-lockfile` | **PASS** | Via `npx pnpm@9.15.0` (system `pnpm` not on PATH) |
| `pnpm mobile:typecheck` | **PASS** | citizen-app + provider-app `tsc --noEmit` |
| Expo prebuild (citizen android) | **FAIL** | `app.config.ts` — Missing initializer in const declaration |
| Expo prebuild (provider android) | **SKIP** | Blocked by citizen config error |
| `assembleDebug` | **SKIP** | No Android SDK (`ANDROID_HOME` unset) on VM |
| Maestro E2E | **SKIP** | Requires emulator + successful prebuild |

## Typecheck

```
cd apps/mobile && npx pnpm@9.15.0 mobile:typecheck
# exit 0 — both apps clean
```

## Prebuild failure (citizen-app)

```
SyntaxError: Error reading Expo config at apps/mobile/citizen-app/app.config.ts:
Missing initializer in const declaration
```

**Remediation:** Fix `app.config.ts` const declarations before native prebuild on VM/CI.

## Coverage (static)

| Metric | Value | Source |
|--------|-------|--------|
| Mobile apps | 2 (citizen, provider) | `apps/mobile/` |
| Screens | 169 | `reports/product/service-coverage-ledger.json` |
| Domain capabilities complete | 8 / 37 | `docs/architecture/MOBILE_PARITY_MATRIX.md` |
| BFF mobile handlers | ~283 | product truth rollups |

## Parity highlights

**Complete:** Social, MADI (donor/drives/orders/transfusion/haemovigilance), Live events, remote monitoring devices.

**Partial/blocked:** BUTANO conditions/allergies on citizen app, MusheX/COSTA finance, UBOMI CRVS mobile, telemedicine RTC (intentionally blocked).

## Commands to re-run

```bash
cd apps/mobile
npx pnpm@9.15.0 install --frozen-lockfile
npx pnpm@9.15.0 mobile:typecheck
EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235 \
  npx pnpm@9.15.0 dlx expo prebuild --platform android --no-install --clean
# CI reference: .github/workflows/ci.yml mobile-e2e-maestro job
```
