# Citizen App

## Overview

The Citizen App is a **native mobile application** targeting **Android and iOS** via Expo SDK 52 and React Native 0.76. It gives citizens direct access to their health data, appointments, prescriptions, lab results, insurance coverage, and care team communications. It supports five integrated domains — **Personal**, **Social**, **Marketplace**, **Messaging**, and **Telehealth** — providing a comprehensive patient engagement layer within the Impilo platform.

**Platform:** Native mobile (Android + iOS)
**Framework:** Expo SDK 52, React Native 0.76, React Navigation 7
**Entry point:** `apps/mobile/citizen-app/src/App.tsx`
**Android package:** `zw.gov.impilo.citizen`
**iOS bundle:** `zw.gov.impilo.citizen`
**Custom URI scheme:** `impilo.citizen://`

## Architecture

The Citizen App shares the same foundational architecture as the Provider App. It is built with React Native and Zustand for state management, consuming the shared mobile packages:

| Package | Responsibility |
|---|---|
| `@impilo/mobile-auth` | Keycloak PKCE authentication flow, token lifecycle |
| `@impilo/mobile-api-client` | Typed HTTP client for the Experience BFF |
| `@impilo/mobile-messaging` | In-app messaging, notifications, and real-time channels |
| `@impilo/mobile-design-system` | Shared React Native UI primitives, theme tokens, accessibility helpers |
| `@impilo/mobile-timeline` | Patient timeline rendering |

## Native Mobile Stack

| Layer | Technology |
|---|---|
| Runtime | Expo SDK 52, React Native 0.76 |
| Navigation | React Navigation 7 (native-stack, bottom-tabs) |
| Secure Storage | `expo-secure-store` (Keychain on iOS, EncryptedSharedPreferences on Android) |
| Authentication | `expo-web-browser` + `expo-auth-session` for Keycloak PKCE |
| Network Detection | `@react-native-community/netinfo` |
| Deep Linking | `expo-linking` with `impilo.citizen://` scheme |
| Build System | EAS Build (cloud-based native compilation) |
| Configuration | `app.json` + `app.config.ts` (dynamic Expo config) |

## Build & Run

### Development

```bash
cd apps/mobile/citizen-app

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

Every HTTP request carries the v1.1 trust headers via the API client. Authentication uses Keycloak Authorization Code flow with PKCE, handled via `expo-web-browser` (system browser). The citizen identity is resolved server-side from the `X-Actor-ID` header, which maps to a CPID in the patient registry.

Unlike the Provider App, the Citizen App does **not** require facility selection. The auth guard bootstraps the citizen profile automatically upon authentication.

## Domains

### Personal
Core health management: profile, appointments, prescriptions, lab results, coverage, settings, consent.

### Social
Community health content and engagement: category-filtered health feed, likes, campaigns, announcements.

### Marketplace
Health service discovery and booking: service catalog, requests, order tracking, pricing.

### Messaging
Secure provider-to-citizen communication: conversations, messages, read receipts.

### Telehealth
Virtual consultation lifecycle: session list, teleconsult requests, join/end sessions.

## Navigation

Bottom tab navigation with five tabs:

| Tab | Icon | Domain | Screen |
|---|---|---|---|
| Home | home | Dashboard | `HomeScreen` |
| Health | heart | Personal | `PersonalScreen` |
| Feed | globe | Social | `SocialFeedScreen` |
| Services | shopping-bag | Marketplace | `MarketplaceScreen` |
| Messages | message-circle | Messaging | `MessagingInboxScreen` |

Telehealth is accessible from quick actions on the Home screen.

## Backend Integration

All BFF routes are prefixed with `/internal/v1/mobile/citizen/` and enforce v1.1 header contract compliance.

## Testing

```bash
cd apps/mobile/citizen-app
npx vitest
```

5 test files covering personal flow, messaging, marketplace, telehealth, and backend integration.
