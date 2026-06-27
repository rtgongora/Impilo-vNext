# Android APK Preview Build

**Date:** 2026-06-27  
**Status:** **NOT RUN** on 218; **NOT ATTEMPTED** on 235 (no Android SDK by design)

## Planned artifact location

`reports/mobile/artifacts/android/`

- `impilo-citizen-preview-<commit>.apk`
- `impilo-provider-preview-<commit>.apk`

## Blocker

Android SDK + Gradle build must run on **218** after bootstrap. Native `android/` directories exist in repo (API 35).

## Command (218)

```bash
cd apps/mobile/citizen-app/android && ./gradlew assembleDebug
cd apps/mobile/provider-app/android && ./gradlew assembleDebug
```

Or via `scripts/mobile/maestro-vm-runtime-closure.sh`.

**No publishing** — internal debug artifacts only.
