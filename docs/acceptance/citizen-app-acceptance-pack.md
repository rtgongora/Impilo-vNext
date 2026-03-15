# Citizen App — Acceptance Pack

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> **Platform: Native mobile (Android + iOS) via Expo SDK 52 / React Native 0.76**

---

## 1. Platform Evidence

### Android
- [x] `app.json` → `expo.android.package`: `zw.gov.impilo.citizen`
- [x] `app.json` → `expo.android.versionCode`: `1`
- [x] `app.json` → `expo.android.adaptiveIcon` configured
- [x] `app.json` → Android permissions: CAMERA, READ_EXTERNAL_STORAGE, BIOMETRIC, INTERNET, NETWORK_STATE, RECORD_AUDIO
- [x] `eas.json` → development: APK, production: AAB (Play Store)
- [x] `app.config.ts` → `expo-build-properties`: compileSdkVersion 34, targetSdkVersion 34, minSdkVersion 24
- [x] `package.json` → `scripts.android`: `expo start --android`
- [x] `package.json` → `scripts.build:android`: `eas build --platform android`

### iOS
- [x] `app.json` → `expo.ios.bundleIdentifier`: `zw.gov.impilo.citizen`
- [x] `app.json` → `expo.ios.buildNumber`: `1`
- [x] `app.json` → `expo.ios.supportsTablet`: `true`
- [x] `app.json` → iOS Info.plist: Camera, PhotoLibrary, FaceID, Microphone usage descriptions
- [x] `eas.json` → development: simulator, production: auto-increment
- [x] `app.config.ts` → `expo-build-properties`: deploymentTarget 15.1
- [x] `package.json` → `scripts.ios`: `expo start --ios`
- [x] `package.json` → `scripts.build:ios`: `eas build --platform ios`

### React Native
- [x] `expo: ~52.0.0`, `react-native: ~0.76.0` in dependencies
- [x] `expo-secure-store` for keychain/encrypted storage
- [x] `expo-web-browser` for OAuth PKCE (system browser)
- [x] `@react-native-community/netinfo` for network detection
- [x] `expo-linking` for deep links (`impilo.citizen://`)
- [x] All screens use `View`, `Text`, `ScrollView`, `Pressable`, `StyleSheet`
- [x] Zero `window.*`, `document.*`, `localStorage` references
- [x] Zero HTML DOM elements in source

---

## 2. Summary

The Citizen App delivers a full vertical-slice implementation of the patient-facing native mobile application within the Impilo platform. It covers five integrated domains: Personal, Social, Marketplace, Messaging, and Telehealth.

---

## 3. Acceptance Criteria

### AC-1: Authentication and Profile Bootstrap

- [x] Keycloak PKCE authentication via system browser (expo-web-browser)
- [x] Auth guard blocks unauthenticated access
- [x] Profile auto-bootstrap on first authentication
- [x] No facility selection required (citizen context)
- [x] Secure token storage via expo-secure-store (platform keychain)

### AC-2: Personal Domain

- [x] Profile view and edit (phone, email, language, avatar)
- [x] Appointment listing with status filter and pagination
- [x] Appointment booking with facility, type, date, reason
- [x] Appointment cancellation with reason
- [x] Prescription listing with active/completed filter
- [x] Prescription refill request
- [x] Lab result listing with status filter
- [x] Lab result detail with expandable values
- [x] Coverage/insurance plan visibility
- [x] Consent preference management
- [x] Notification preference toggle
- [x] Account deletion

### AC-3: Social Domain

- [x] Category-filtered social feed
- [x] Feed item detail view
- [x] Like/unlike feed items
- [x] Like count display

### AC-4: Marketplace Domain

- [x] Service catalog browsing with category filter
- [x] Service search by keyword
- [x] Service detail view with pricing
- [x] Service request creation
- [x] Service request listing and tracking
- [x] Service request cancellation

### AC-5: Messaging Domain

- [x] Conversation listing with type filter
- [x] Conversation creation with initial message
- [x] Message thread view
- [x] Message sending
- [x] Read receipt
- [x] Load older messages (pagination)

### AC-6: Telehealth Domain

- [x] Telehealth session listing with status filter
- [x] Teleconsult request
- [x] Session join with real-time token
- [x] Session end with notes
- [x] Elapsed time display

### AC-7: Support

- [x] Support ticket creation
- [x] Support ticket listing
- [x] Knowledge article browsing

### AC-8: Backend BFF

- [x] 10 citizen-specific REST controllers under `/internal/v1/mobile/citizen/*`
- [x] v1.1 header contract compliance
- [x] Tenant isolation in all queries
- [x] Transactional outbox events
- [x] Flyway migration V5

### AC-9: Testing

- [x] Personal flow tests
- [x] Messaging flow tests
- [x] Marketplace flow tests
- [x] Telehealth flow tests
- [x] Backend integration tests

### AC-10: Native Mobile Platform

- [x] Expo SDK 52 + React Native 0.76 stack
- [x] `app.json` with Android and iOS configuration
- [x] `eas.json` with build profiles for both platforms
- [x] `expo-secure-store` for platform-native secure storage
- [x] `expo-web-browser` for OAuth PKCE (not WebView)
- [x] `@react-native-community/netinfo` for connectivity (not window.addEventListener)
- [x] `expo-linking` for deep link handling
- [x] React Native components exclusively (View, Text, ScrollView, Pressable, StyleSheet)
- [x] No web DOM APIs (`window`, `document`, `localStorage`)
- [x] No HTML elements (`div`, `span`, `p`, `button`)
- [x] `babel.config.js` with `babel-preset-expo`
- [x] `metro.config.js` with monorepo support
- [x] `tsconfig.json` extends `expo/tsconfig.base`

---

## 4. File Inventory

### Frontend (apps/mobile/citizen-app/)

| Category | Count | Key Files |
|---|---|---|
| Expo Config | 5 | `app.json`, `app.config.ts`, `eas.json`, `babel.config.js`, `metro.config.js` |
| Package Config | 2 | `package.json`, `tsconfig.json` |
| Types | 1 | `src/types/index.ts` |
| App Shell | 2 | `src/App.tsx`, `src/main.tsx` |
| Config | 1 | `src/config.ts` (expo-constants + expo-secure-store) |
| Navigation | 3 | `AppNavigator.tsx`, `AuthGuard.tsx`, `CitizenTabs.tsx` |
| Stores | 1 | `src/stores/appStore.ts` |
| Screens | 18 | LoginScreen, HomeScreen, NotificationsScreen, NetworkStatusBar, GlobalErrorBanner, PersonalScreen, ProfileSection, AppointmentsSection, PrescriptionsSection, ResultsSection, CoverageSection, SettingsSection, SocialFeedScreen, MarketplaceScreen, MessagingInboxScreen, ThreadViewScreen, TelehealthListScreen, TelehealthSessionScreen |
| Services | 10 | profileService, appointmentService, prescriptionService, labResultService, coverageService, feedService, marketplaceService, messagingService, telehealthService, supportService |
| Tests | 5 | PersonalFlow, MessagingFlow, MarketplaceFlow, TelehealthFlow, BackendIntegration |

### Backend (services/experience-bff/)

| Category | Count |
|---|---|
| Controllers | 10 (Citizen-specific) |
| Migrations | 1 (V5__citizen_app_tables.sql) |

---

## 5. Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Engineering Lead | | | |
| QA Lead | | | |
| Product Owner | | | |

---

*End of Citizen App Acceptance Pack*
