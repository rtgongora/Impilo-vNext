# Mobile Expo Export Closure

**Date:** 2026-06-27  
**Blocker (prior):** Missing `react-native-web@^0.21.0`

## Fix applied

Added to **both app manifests** (Expo SDK 54 export requirement):

| App | Change |
|-----|--------|
| `apps/mobile/citizen-app/package.json` | `react-native-web@^0.21.0`, `react-dom@19.1.0` in dependencies |
| `apps/mobile/provider-app/package.json` | `react-native-web@^0.21.0`, `react-dom@19.1.0` in dependencies |

Rationale: Expo `export` resolves web-compatible deps per-app; native behaviour unchanged — `react-native-web` is standard for Expo export/bundler paths.

Lockfile updated via `npx pnpm@9.15.0 install` in `apps/mobile/`.

## Post-fix validation (235)

| Check | Result |
|-------|--------|
| `pnpm mobile:typecheck` | PASS |
| `pnpm mobile:test` | PASS |
| `pnpm guard:mobile-parity` | PASS |
| Citizen `expo export --platform android` | **PASS** → `citizen-app/dist` |
| Provider `expo export --platform android` | **PASS** → `provider-app/dist` |

Logs: `reports/mobile/expo-export-citizen.log`, `expo-export-provider.log`

## Commands

```bash
cd apps/mobile/citizen-app
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235 npx expo export --platform android

cd ../provider-app
EXPO_PUBLIC_APP_VARIANT=preview EXPO_PUBLIC_API_BASE_URL=http://41.57.127.235 npx expo export --platform android
```
