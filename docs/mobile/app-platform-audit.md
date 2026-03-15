# App Platform Audit

**Date:** 2026-03-15
**Branch:** `claude/review-project-manifest-jb5O0`
**Auditor:** Claude Code (automated repo inspection)
**Revision:** 2 — Post-conversion audit (native mobile apps)

## Summary

Four apps were audited: Provider App, Citizen/Patient App, Support App, and Developer/Partner App. The Provider App and Citizen App have been **converted to real native mobile apps** targeting Android and iOS via Expo SDK 52 / React Native 0.76. The Support App and Developer/Partner App remain **web-first** applications.

## Audit Table

| App | Path | Framework | Languages | Android | iOS | Platform Type | Readiness |
|-----|------|-----------|-----------|---------|-----|---------------|-----------|
| Provider App | `apps/mobile/provider-app/` | Expo SDK 52, React Native 0.76, React Navigation 7 | TypeScript, TSX | **YES** | **YES** | **Native mobile** | Production-ready Expo managed workflow. `app.json` with `zw.gov.impilo.provider` Android package and iOS bundle. `eas.json` with dev/preview/production build profiles. All screens use React Native components (`View`, `Text`, `Pressable`, `ScrollView`, `StyleSheet`). No web DOM APIs. |
| Citizen / Patient App | `apps/mobile/citizen-app/` | Expo SDK 52, React Native 0.76, React Navigation 7 | TypeScript, TSX | **YES** | **YES** | **Native mobile** | Production-ready Expo managed workflow. `app.json` with `zw.gov.impilo.citizen` Android package and iOS bundle. `eas.json` with dev/preview/production build profiles. All screens use React Native components. No web DOM APIs. |
| Support App | `ui/support-console/` | Next.js 14.2 (web) | TypeScript, TSX | NO | NO | **Web-first** | Web application for support agents. No mobile build targets. Accessed via browser. |
| Developer / Partner App | `ui/developer-console/` | Next.js 14.2 (web) | TypeScript, TSX | NO | NO | **Web-first** | Web application for developer/partner portal. No mobile build targets. Accessed via browser. |

## Detailed Evidence

### Provider App (`apps/mobile/provider-app/`) — NATIVE MOBILE

**Android Evidence:**
- `app.json` → `expo.android.package`: `"zw.gov.impilo.provider"`
- `app.json` → `expo.android.versionCode`: `1`
- `app.json` → `expo.android.adaptiveIcon` configured
- `app.json` → `expo.android.permissions`: `CAMERA`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `USE_BIOMETRIC`, `USE_FINGERPRINT`, `INTERNET`, `ACCESS_NETWORK_STATE`
- `eas.json` → `build.development.android.buildType`: `"apk"` with `gradleCommand: ":app:assembleDebug"`
- `eas.json` → `build.production.android.buildType`: `"app-bundle"` (AAB for Play Store)
- `package.json` → `scripts.android`: `"expo start --android"`
- `package.json` → `scripts.build:android`: `"eas build --platform android"`
- `app.config.ts` → `expo-build-properties` plugin with `compileSdkVersion: 34`, `targetSdkVersion: 34`, `minSdkVersion: 24`

**iOS Evidence:**
- `app.json` → `expo.ios.bundleIdentifier`: `"zw.gov.impilo.provider"`
- `app.json` → `expo.ios.buildNumber`: `"1"`
- `app.json` → `expo.ios.supportsTablet`: `true`
- `app.json` → `expo.ios.infoPlist`: Camera, Location, FaceID usage descriptions
- `eas.json` → `build.development.ios.simulator`: `true`
- `eas.json` → `build.production.ios.autoIncrement`: `true`
- `package.json` → `scripts.ios`: `"expo start --ios"`
- `package.json` → `scripts.build:ios`: `"eas build --platform ios"`
- `app.config.ts` → `expo-build-properties` plugin with `deploymentTarget: "15.1"`

**React Native Evidence:**
- `package.json` → `expo: "~52.0.0"`, `react-native: "~0.76.0"`
- `package.json` → `react-native-safe-area-context`, `react-native-screens`, `@react-native-community/netinfo`
- `package.json` → `expo-secure-store`, `expo-web-browser`, `expo-linking`, `expo-constants`, `expo-auth-session`
- `babel.config.js` → `babel-preset-expo`
- `metro.config.js` → Expo Metro config with monorepo watchFolders
- `tsconfig.json` → extends `expo/tsconfig.base`
- All `.tsx` files import from `react-native` (`View`, `Text`, `StyleSheet`, `ScrollView`, `Pressable`, `ActivityIndicator`)
- `src/App.tsx` → uses `SafeAreaProvider`, `StatusBar` from expo
- `src/config.ts` → uses `expo-secure-store` for keychain/encrypted storage, `expo-constants` for config
- `src/screens/LoginScreen.tsx` → uses `expo-web-browser` `openAuthSessionAsync` for OAuth PKCE (not `window.location`)
- `src/screens/NetworkStatusBar.tsx` → uses `@react-native-community/netinfo` (not `window.addEventListener`)
- **Zero** `window.*`, `document.*`, `localStorage`, `sessionStorage` references in source code
- **Zero** HTML DOM elements (`"div"`, `"span"`, `"p"`, `"h1"`, `"button"`) in source code
- Custom URI scheme: `impilo.provider://` configured in `app.json` and `app.config.ts`

**Prebuild-ready:**
- `package.json` → `scripts.prebuild`: `"expo prebuild"` — generates native `android/` and `ios/` directories on demand
- EAS Build handles native compilation in the cloud without requiring local native project directories

### Citizen / Patient App (`apps/mobile/citizen-app/`) — NATIVE MOBILE

**Android Evidence:**
- `app.json` → `expo.android.package`: `"zw.gov.impilo.citizen"`
- `app.json` → `expo.android.versionCode`: `1`
- `app.json` → `expo.android.adaptiveIcon` configured
- `app.json` → `expo.android.permissions`: `CAMERA`, `READ_EXTERNAL_STORAGE`, `USE_BIOMETRIC`, `USE_FINGERPRINT`, `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`
- `eas.json` → `build.development.android.buildType`: `"apk"`
- `eas.json` → `build.production.android.buildType`: `"app-bundle"`
- `package.json` → `scripts.android`: `"expo start --android"`
- `package.json` → `scripts.build:android`: `"eas build --platform android"`
- `app.config.ts` → `expo-build-properties` plugin with `compileSdkVersion: 34`, `targetSdkVersion: 34`, `minSdkVersion: 24`

**iOS Evidence:**
- `app.json` → `expo.ios.bundleIdentifier`: `"zw.gov.impilo.citizen"`
- `app.json` → `expo.ios.buildNumber`: `"1"`
- `app.json` → `expo.ios.supportsTablet`: `true`
- `app.json` → `expo.ios.infoPlist`: Camera, PhotoLibrary, FaceID, Microphone usage descriptions
- `eas.json` → `build.development.ios.simulator`: `true`
- `eas.json` → `build.production.ios.autoIncrement`: `true`
- `package.json` → `scripts.ios`: `"expo start --ios"`
- `package.json` → `scripts.build:ios`: `"eas build --platform ios"`
- `app.config.ts` → `expo-build-properties` plugin with `deploymentTarget: "15.1"`

**React Native Evidence:**
- Same Expo SDK 52 / React Native 0.76 stack as Provider App
- All `.tsx` files use React Native components exclusively
- `src/config.ts` → uses `expo-secure-store` for keychain/encrypted storage
- `src/screens/LoginScreen.tsx` → uses `expo-web-browser` for OAuth PKCE
- `src/screens/NetworkStatusBar.tsx` → uses `@react-native-community/netinfo`
- **Zero** web DOM API references in source code
- Custom URI scheme: `impilo.citizen://` configured in `app.json` and `app.config.ts`

### Support App (`ui/support-console/`) — WEB-FIRST

- **Platform:** Web-only Next.js 14.2 application
- **Purpose:** Operator-facing helpdesk and incident management console for support agents
- **Rationale for web-first:** Support agents use desktop workstations with large screens for ticket triage, knowledge base management, and incident response. A mobile app would not improve the workflow.
- **No mobile framework references:** No Expo, React Native, Capacitor, Ionic, or Flutter dependencies
- **Access:** Browser at port 3006

### Developer / Partner App (`ui/developer-console/`) — WEB-FIRST

- **Platform:** Web-only Next.js 14.2 application
- **Purpose:** Operator-facing developer portal for API key management, webhook configuration, usage analytics, and sandbox testing
- **Rationale for web-first:** Developers interact with API documentation, code samples, and sandbox consoles — all desktop-centric workflows. A mobile app would not add value.
- **No mobile framework references:** No Expo, React Native, Capacitor, Ionic, or Flutter dependencies
- **Access:** Browser at port 3007

## Shared Mobile Packages

Seven shared packages under `apps/mobile/packages/`, all converted to React Native-compatible code:

| Package | Purpose | RN-Compatible |
|---------|---------|---------------|
| `@impilo/mobile-trust` | Trust header injection for mobile API calls | Yes — pure TypeScript logic |
| `@impilo/mobile-auth` | Keycloak authentication, token management, secure storage | Yes — pure TypeScript with adapter pattern |
| `@impilo/mobile-api-client` | HTTP client with retry, timeout, step-up challenge support | Yes — uses `fetch()` API (available in RN) |
| `@impilo/mobile-messaging` | Push notification and device registration | Yes — pure TypeScript logic |
| `@impilo/mobile-timeline` | Clinical timeline rendering | Yes — pure TypeScript logic |
| `@impilo/mobile-offline` | Offline queue and storage adapters | Yes — pure TypeScript with adapter pattern |
| `@impilo/mobile-design-system` | Shared UI components and design tokens | Yes — converted to React Native (`View`, `Text`, `Pressable`, `StyleSheet`) |

## Conclusions

1. **Provider App and Citizen App are real native mobile apps.** Both target Android and iOS via Expo SDK 52 with React Native 0.76. They have complete Expo configuration (`app.json`, `app.config.ts`, `eas.json`), proper native dependencies, and use React Native components exclusively. They are prebuild-ready and can produce APK/AAB (Android) and IPA (iOS) builds via EAS Build.
2. **No web DOM APIs remain in mobile code.** All `window.*`, `document.*`, `localStorage`, `sessionStorage`, and HTML DOM elements have been replaced with React Native equivalents: `NetInfo` for connectivity, `expo-secure-store` for secure storage, `expo-web-browser` for OAuth, `expo-linking` for deep links.
3. **Support App and Developer/Partner App are intentionally web-first.** They serve operator-facing workflows that are desktop-centric. This is documented and architecturally appropriate.
4. **Shared design system converted.** The `@impilo/mobile-design-system` package now uses React Native components (`View`, `Text`, `Pressable`, `StyleSheet`) instead of HTML DOM elements, ensuring native rendering on both platforms.
