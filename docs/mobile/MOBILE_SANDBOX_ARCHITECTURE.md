# Mobile Sandbox Architecture (impilo-mobile-android-sandbox)

Minimal architecture note for the **active** Android sandbox. Full runbook: [`MOBILE_ANDROID_SANDBOX.md`](./MOBILE_ANDROID_SANDBOX.md).

## Environment identity

| Field | Value |
|-------|-------|
| Name | `impilo-mobile-android-sandbox` |
| Display | MOHCC Maestro VM |
| Host | `41.57.127.218` |
| Hostname | `ministryofhealth-HVM-domU` |

## Architecture slice

```text
  ┌─────────────────────────────────────────┐
  │  impilo-mobile-android-sandbox (218)    │
  │  ┌─────────┐  ┌──────────┐  ┌────────┐ │
  │  │ Android │  │ Maestro  │  │ Reports│ │
  │  │ emulator│→ │ smoke    │→ │ mobile/│ │
  │  └────┬────┘  └──────────┘  └────────┘ │
  │       │ citizen + provider APKs         │
  └───────┼─────────────────────────────────┘
          │ HTTPS  EXPO_PUBLIC_API_BASE_URL
          ▼
  ┌─────────────────────────────────────────┐
  │  impilo-web-preview (235)               │
  │  Envoy → TSHEPO → BFF → services        │
  │  http://41.57.127.235                   │
  └─────────────────────────────────────────┘
```

## Boundaries

- **In scope:** KVM emulator, ADB, APK validation, runtime smoke, logcat, screenshots, Maestro/Detox/Appium if configured.
- **Out of scope:** Backend deploy, k3s, web preview hosting, full integration, production simulation, production, iOS native builds on Ubuntu, Play/App Store publishing.

## Pipeline position

Ladder step **3** of 11 — see [`docs/environment/VNEXT_ENVIRONMENT_LADDER.md`](../environment/VNEXT_ENVIRONMENT_LADDER.md).

Runtime architecture detail: [`MOBILE_RUNTIME_SANDBOX_ARCHITECTURE.md`](./MOBILE_RUNTIME_SANDBOX_ARCHITECTURE.md).
