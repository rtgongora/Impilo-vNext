# Android Preview Install (internal)

**Audience:** Product owner / testers on Maestro VM  
**Not for Play Store or public distribution**

## Prerequisites

1. Maestro VM bootstrap complete (`scripts/mobile/maestro-vm-bootstrap.sh`)
2. Debug APK built (see `android-apk-preview-build.md`)
3. Emulator running or physical device via `adb`

## Install debug APK

```bash
adb install -r reports/mobile/artifacts/android/impilo-citizen-preview-<commit>.apk
adb install -r reports/mobile/artifacts/android/impilo-provider-preview-<commit>.apk
```

## Environment

```bash
export EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235
export EXPO_PUBLIC_APP_VARIANT=preview
```

Apps must reach the **Web Preview API** on 235 — not localhost.

## Uninstall

```bash
adb uninstall zw.gov.impilo.citizen
adb uninstall zw.gov.impilo.provider
```
