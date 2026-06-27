# Citizen Runtime Smoke

**Environment:** `impilo-mobile-android-sandbox` (`41.57.127.218`)  
**App:** `zw.gov.impilo.citizen`  
**API:** `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`  
**Status:** **NOT RUN** — 218 not accessible from 235 agent  
**Updated:** 2026-06-27

## Static prerequisites (235)

| Check | Status |
|-------|--------|
| Typecheck | PASS |
| Unit tests | PASS (145) |
| Expo export | PASS |

## Runtime checklist

| # | Journey | Status |
|---|---------|--------|
| 1 | Dev server / bundle | NOT RUN |
| 2 | Emulator connect | NOT RUN |
| 3 | App launch | NOT RUN |
| 4 | Preview API base URL | NOT RUN |
| 5 | Costa blocked state truthful | NOT RUN (expected blocked) |
| 6 | Maestro flow | NOT RUN |

## Command (on 218 after bootstrap)

```bash
cd apps/mobile/citizen-app
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235 pnpm start
# or: pnpm exec expo run:android
```
