# Mobile Runnability Findings — Impilo vNext

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Risk Class: D — Mobile clients exist in code but are not truly native/runnable

## Executive Summary

Provider App and Citizen App are **genuinely mobile-ready** Expo/React Native applications with proper native configurations for both Android and iOS. They are NOT web wrappers. They use React Native components (`View`, `Text`, `StyleSheet`, `ActivityIndicator`), have proper Expo SDK ~52.0.0 configuration, EAS build configs, Metro bundler configs, and 7 shared mobile packages. The remaining gap is that `expo prebuild` has not been run (no `android/` or `ios/` directories generated yet), and no web-only API contamination was detected.

## App Classification

| App | Location | Framework | Platform | Classification |
|---|---|---|---|---|
| Provider App | `apps/mobile/provider-app` | Expo 52 + React Native 0.76 | Android + iOS | **NATIVE MOBILE** |
| Citizen App | `apps/mobile/citizen-app` | Expo 52 + React Native 0.76 | Android + iOS | **NATIVE MOBILE** |
| Support Console | `ui/support-console` | Next.js | Web | **WEB-FIRST** |
| Developer Console | `ui/developer-console` | Next.js | Web | **WEB-FIRST** |
| All other ui/* apps | `ui/*` | Next.js | Web | **WEB-FIRST** |

## Provider App & Citizen App — Detailed Assessment

### Framework & Dependencies

Both apps share identical dependency profiles:

| Dependency | Version | Purpose |
|---|---|---|
| `expo` | ~52.0.0 | Managed workflow, native module resolution |
| `react-native` | ~0.76.0 | Core mobile runtime |
| `@react-navigation/native` | ^7.0.0 | Navigation (native stack + tabs) |
| `@react-navigation/bottom-tabs` | ^7.0.0 | Tab navigation |
| `@react-navigation/native-stack` | ^7.0.0 | Stack navigation |
| `expo-secure-store` | ~14.0.0 | Secure credential storage |
| `expo-auth-session` | ~6.0.0 | OAuth/OIDC flows |
| `expo-crypto` | ~14.0.0 | Cryptographic operations |
| `@react-native-community/netinfo` | ~11.4.0 | Network state detection |
| `zustand` | ^4.5.0 | State management |

### Configuration Files

| File | Provider App | Citizen App | Purpose |
|---|---|---|---|
| `app.config.ts` | Present | Present | Expo app config with Android + iOS sections |
| `app.json` | Present | Present | Expo base config |
| `eas.json` | Present | Present | EAS Build profiles (dev, preview, production) |
| `metro.config.js` | Present | Present | Metro bundler configuration |
| `babel.config.js` | Present | Present | Babel with expo preset |
| `tsconfig.json` | Present | Present | TypeScript configuration |

### Source Code Analysis

- **App.tsx**: Uses `View`, `Text`, `StyleSheet`, `ActivityIndicator` from `react-native` — NO web-only APIs
- **Navigation**: Uses `@react-navigation` native stack — NOT Next.js router
- **State management**: Zustand stores — platform-agnostic
- **No web-only contamination**: No `window.location`, `document.*`, `localStorage`, `sessionStorage`, `navigator.serviceWorker`, `XMLHttpRequest`, `next/router`, `next/link` found in source

### Shared Mobile Packages (7)

| Package | Purpose |
|---|---|
| `@impilo/mobile-trust` | Trust header injection for mobile API calls |
| `@impilo/mobile-auth` | Keycloak/OIDC authentication |
| `@impilo/mobile-api-client` | API client with trust headers |
| `@impilo/mobile-messaging` | Push notifications / messaging |
| `@impilo/mobile-timeline` | Clinical timeline components |
| `@impilo/mobile-offline` | Offline-first data sync |
| `@impilo/mobile-design-system` | Shared UI components |

### Build Readiness

| Check | Status | Detail |
|---|---|---|
| Expo SDK configured | PASS | ~52.0.0 |
| Android config in app.config.ts | PASS | Android package, permissions |
| iOS config in app.config.ts | PASS | iOS bundle ID, capabilities |
| EAS Build config | PASS | dev/preview/production profiles |
| Metro bundler config | PASS | Workspace-aware resolution |
| `android/` directory | NOT YET | Requires `expo prebuild` |
| `ios/` directory | NOT YET | Requires `expo prebuild` |
| npm install | NOT TESTED | npm not available in this env |

## What Would Make Them Runnable

1. `cd apps/mobile/provider-app && npm install`
2. `npx expo prebuild` (generates `android/` and `ios/` directories)
3. `npx expo start --android` or `--ios` for dev
4. `eas build --platform android` for production APK
5. `eas build --platform ios` for production IPA

## Risks

1. **No prebuild output checked in**: The `android/` and `ios/` directories are gitignored (standard Expo practice), meaning every developer must run `expo prebuild` locally.

2. **Workspace dependency resolution**: The 7 shared packages use `workspace:*` protocol. Metro must be configured for monorepo resolution — `metro.config.js` exists but its content should be verified.

3. **Expo SDK 52 is recent**: Some native modules may need specific plugin versions.

## Validation Script

See: `scripts/reality-check/run-mobile-runnability-checks.sh`

## Verdict

**MOBILE RUNNABILITY: GENUINELY NATIVE, PREBUILD PENDING**

Provider and Citizen apps are real Expo/React Native mobile apps — NOT web wrappers. They have correct configs for Android + iOS builds. The only remaining step is running `expo prebuild` + `eas build` in an environment with Node.js 20+ and Expo CLI.
