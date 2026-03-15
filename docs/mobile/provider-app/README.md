# Provider App

## Overview

The Provider App is a **native mobile application** targeting **Android and iOS** via Expo SDK 52 and React Native 0.76. It serves frontline healthcare workers within the Impilo platform, supporting four distinct operational modes — **Provider**, **Outreach**, **Supervisor**, and **Offline Edge** — each tailored to a specific workflow within primary healthcare delivery. The app functions reliably in low-connectivity environments, with offline-first capabilities powered by CRDT-based synchronization.

**Platform:** Native mobile (Android + iOS)
**Framework:** Expo SDK 52, React Native 0.76, React Navigation 7
**Entry point:** `apps/mobile/provider-app/src/App.tsx`
**Android package:** `zw.gov.impilo.provider`
**iOS bundle:** `zw.gov.impilo.provider`
**Custom URI scheme:** `impilo.provider://`

## Architecture

The Provider App is built with React Native and Zustand for state management. It consumes seven shared packages that encapsulate cross-cutting concerns:

| Package | Responsibility |
|---|---|
| `@impilo/mobile-trust` | Injects the 14 trust headers into every outbound request |
| `@impilo/mobile-auth` | Keycloak PKCE authentication flow, token lifecycle |
| `@impilo/mobile-api-client` | Typed HTTP client for the Experience BFF |
| `@impilo/mobile-messaging` | In-app messaging, notifications, and real-time channels |
| `@impilo/mobile-timeline` | Patient and encounter timeline rendering |
| `@impilo/mobile-offline` | CRDT-based sync queue, conflict resolution, edge snapshots |
| `@impilo/mobile-design-system` | Shared React Native UI primitives, theme tokens, accessibility helpers |

## Native Mobile Stack

| Layer | Technology |
|---|---|
| Runtime | Expo SDK 52, React Native 0.76 |
| Navigation | React Navigation 7 (native-stack, bottom-tabs) |
| Secure Storage | `expo-secure-store` (Keychain on iOS, EncryptedSharedPreferences on Android) |
| Authentication | `expo-web-browser` + `expo-auth-session` for Keycloak PKCE |
| Network Detection | `@react-native-community/netinfo` |
| Deep Linking | `expo-linking` with `impilo.provider://` scheme |
| Build System | EAS Build (cloud-based native compilation) |
| Configuration | `app.json` + `app.config.ts` (dynamic Expo config) |
| Metro Bundler | `metro.config.js` with monorepo workspace support |

## Build & Run

### Development

```bash
cd apps/mobile/provider-app

# Start Expo dev server
npm start

# Run on Android emulator/device
npm run android

# Run on iOS simulator (macOS only)
npm run ios
```

### Production Builds

```bash
# Generate native projects locally
npm run prebuild

# Build Android APK (development)
npm run build:android

# Build iOS IPA (development)
npm run build:ios

# Build both platforms
npm run build:all
```

### EAS Build Profiles

| Profile | Android | iOS | Distribution |
|---|---|---|---|
| `development` | Debug APK | Simulator build | Internal |
| `preview` | APK | Ad-hoc | Internal |
| `production` | AAB (Play Store) | IPA (App Store) | Public |

## Trust Model

Every HTTP request issued by the Provider App carries the 14 trust headers defined in the platform header contract. The `@impilo/mobile-trust` package constructs and attaches these headers before each request leaves the device.

Authentication uses Keycloak Authorization Code flow with PKCE, handled via `expo-web-browser` (system browser) rather than an in-app WebView. Access tokens are stored in the platform keychain (`expo-secure-store`) and refreshed automatically.

## Modes

### Provider Mode
Primary clinical mode for facility-based healthcare workers: worklist, encounters, vitals, diagnosis, prescriptions, labs, referrals.

### Outreach Mode
Community health worker mode: households, screenings, immunizations, GPS-tagged visits.

### Supervisor Mode
Facility management: KPIs, team oversight, stock management, escalations.

### Offline Edge Mode
Disconnected operation: sync queue, conflict resolution, break-glass emergency access, edge snapshots.

## Backend Integration

All API calls route through the Experience BFF under `/internal/v1/mobile/provider/*`. The BFF enforces v1.1 header contract compliance.

## Offline Capabilities

Local-first architecture powered by CRDT data structures in `@impilo/mobile-offline`. Supports background sync, field-level conflict detection, edge snapshots, and break-glass emergency access with full audit trail.

## Testing

```bash
cd apps/mobile/provider-app
npx vitest
```

13 test files covering navigation, all four modes, messaging, telemedicine, and backend integration.

## Local Development Prerequisites

| Service | Port |
|---|---|
| Keycloak | 8080 |
| Envoy (public) | 10000 |
| Experience BFF | 3000 |
