# Provider Runtime Smoke

**Environment:** `impilo-mobile-android-sandbox` (`41.57.127.218`)  
**App:** `zw.gov.impilo.provider`  
**API:** `EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`  
**Status:** **NOT RUN** — blocked by `ANDROID_EMULATOR_SANDBOX_PROVISIONING_BLOCKER`  
**Blocker:** [`android-emulator-sandbox-provisioning-blocker-20260628T070318Z.md`](./android-emulator-sandbox-provisioning-blocker-20260628T070318Z.md)  
**Updated:** 2026-06-28

## Static prerequisites (235)

| Check | Status |
|-------|--------|
| Typecheck | PASS |
| Unit tests | PASS (135) |
| Expo export | PASS |

## Runtime checklist

| # | Journey | Status |
|---|---------|--------|
| 1 | Dev server / bundle | NOT RUN |
| 2 | Emulator connect | NOT RUN |
| 3 | App launch → Work tab | NOT RUN |
| 4 | Preview API base URL | NOT RUN |
| 5 | Provider Costa partial wiring truthful | NOT RUN |
| 6 | Maestro flow | NOT RUN |

## Command (on 218 after bootstrap)

```bash
cd apps/mobile/provider-app
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235 pnpm start
```
