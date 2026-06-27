# EAS / iOS Readiness

**Date:** 2026-06-27  
**Host assessed:** 235 (config review only)

## Expo / EAS configuration

| Item | Status |
|------|--------|
| `eas.json` (citizen) | Present — profiles: development, preview, staging, production |
| `eas.json` (provider) | Present — same structure |
| EAS CLI on 218 | **NOT INSTALLED** |
| EAS credentials | **NOT VERIFIED** — no secrets in repo |

## Android EAS readiness

| Item | Status |
|------|--------|
| Config for APK builds | Yes (`buildType: apk` in dev/preview) |
| Local Gradle path | `android/` folders exist; build on 218 after SDK install |
| Cloud EAS build | Not run — requires EAS account + credentials |

## iOS readiness

| Item | Status |
|------|--------|
| Native iOS build on Ubuntu Maestro VM | **NOT POSSIBLE** — do not claim |
| EAS iOS cloud build | Config present (`simulator: true` in dev); requires **macOS/EAS cloud** + Apple credentials |
| TestFlight / App Store | **OUT OF SCOPE** — not attempted |

## Preview profile note

EAS `preview` profile uses `https://api-preview.impilo.gov.zw` in `eas.json`. **Maestro runtime smoke** must override with:

`EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235`

when testing against Web Preview VM.
