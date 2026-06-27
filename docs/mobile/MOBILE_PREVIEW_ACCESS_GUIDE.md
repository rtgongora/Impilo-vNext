# Mobile Preview Access Guide

How product owners and testers access Impilo mobile apps against the **Web Preview API** (`http://41.57.127.235`).

## Environment model

| VM | Role |
|----|------|
| **235** | Web preview + API host |
| **218** | Android emulator + mobile runtime validation |

Mobile apps **consume** `http://41.57.127.235` — they do not host backend.

## Current tested commit

Update after each closure wave — see `reports/mobile/MAESTRO_MOBILE_RUNTIME_CLOSURE_REPORT.md`.

## Access paths

### 1. Android emulator (218 — primary)

After Maestro bootstrap:

```bash
ssh facility@41.57.127.218 -p 2027
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
export EXPO_PUBLIC_APP_VARIANT=preview
cd /opt/impilo/repos/Impilo-vNext/apps/mobile/citizen-app
pnpm start
# Press 'a' for Android emulator
```

### 2. Debug APK install (218)

See `reports/mobile/ANDROID_PREVIEW_INSTALL.md` — internal debug APKs only.

### 3. Expo Go (physical device)

Device must reach `http://41.57.127.235` on network. Use same env vars when starting dev server on 218.

**QR code:** displayed by `expo start` in terminal — scan with Expo Go.

### 4. Web preview (235)

Browser: `http://41.57.127.235` — web shell only; not a substitute for native mobile runtime closure.

## What testers can validate now (235 static + export)

- Typecheck, unit tests, parity guards: **PASS**
- Expo Android bundle export: **PASS**
- Native emulator runtime: **pending 218 setup**

## Known limitations

- Citizen Costa: truthfully **blocked** (no citizen BFF route)
- Provider Costa: **partial** wiring only
- iOS: EAS cloud only — no Ubuntu native builds
- No Play Store / App Store publishing from sandbox

## Pipeline relationship

Maestro runtime closure is ladder step **3** (`impilo-mobile-android-sandbox`). Cross-surface testing (step 6) and full integration (step 7) follow after runtime PASS on 218.
