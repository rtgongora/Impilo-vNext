# Provider App — Acceptance Pack

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> **Platform: Native mobile (Android + iOS) via Expo SDK 52 / React Native 0.76**

---

## 1. Platform Evidence

### Android
- [x] `app.json` → `expo.android.package`: `zw.gov.impilo.provider`
- [x] `app.json` → `expo.android.versionCode`: `1`
- [x] `app.json` → `expo.android.adaptiveIcon` configured
- [x] `app.json` → Android permissions: CAMERA, LOCATION, BIOMETRIC, INTERNET, NETWORK_STATE
- [x] `eas.json` → development: APK, production: AAB (Play Store)
- [x] `app.config.ts` → `expo-build-properties`: compileSdkVersion 34, targetSdkVersion 34, minSdkVersion 24
- [x] `package.json` → `scripts.android`: `expo start --android`
- [x] `package.json` → `scripts.build:android`: `eas build --platform android`

### iOS
- [x] `app.json` → `expo.ios.bundleIdentifier`: `zw.gov.impilo.provider`
- [x] `app.json` → `expo.ios.buildNumber`: `1`
- [x] `app.json` → `expo.ios.supportsTablet`: `true`
- [x] `app.json` → iOS Info.plist: Camera, Location, FaceID usage descriptions
- [x] `eas.json` → development: simulator, production: auto-increment
- [x] `app.config.ts` → `expo-build-properties`: deploymentTarget 15.1
- [x] `package.json` → `scripts.ios`: `expo start --ios`
- [x] `package.json` → `scripts.build:ios`: `eas build --platform ios`

### React Native
- [x] `expo: ~52.0.0`, `react-native: ~0.76.0` in dependencies
- [x] `expo-secure-store` for keychain/encrypted storage
- [x] `expo-web-browser` for OAuth PKCE (system browser)
- [x] `@react-native-community/netinfo` for network detection
- [x] `expo-linking` for deep links (`impilo.provider://`)
- [x] All screens use `View`, `Text`, `ScrollView`, `Pressable`, `StyleSheet`
- [x] Zero `window.*`, `document.*`, `localStorage` references
- [x] Zero HTML DOM elements (`div`, `span`, `p`, `button`) in source

---

## 2. Scope

The Provider App serves frontline healthcare workers across **4 operational modes**:

| Mode | Role | Description |
|------|------|-------------|
| **Provider** | Clinician / Nurse | Patient lookup, encounter workflow, vitals, diagnosis, prescriptions, labs, referrals |
| **Outreach** | Community Health Worker | Household registration, community visits, screenings, immunizations, follow-ups |
| **Supervisor** | Facility Manager | KPI dashboard, team oversight, stock management, dispatch, escalations |
| **Offline Edge** | Any (disconnected) | Offline-first data capture, sync queue, conflict resolution, break-glass access |

---

## 3. Prerequisites

| Dependency | Endpoint | Purpose |
|------------|----------|---------|
| Keycloak | `:8080` | OIDC identity provider |
| Experience BFF | `:8086` | API gateway |
| PCT | `:8088` | Encounters, vitals, diagnoses, prescriptions, labs, referrals |
| OROS | `:8089` | Stock, dispatch, supervisor operations |
| VITO | `:8082` | Patient identity, PII storage |
| TUSO | `:8084` | Tasks, scheduling, follow-ups |
| PostgreSQL | `:5432` | Persistent storage |
| Redis | `:6379` | Caching, session store |
| Kafka | `:9092` | Event streaming, outbox relay |

---

## 4. Acceptance Criteria

### 4.1 Authentication & Authorization

- [ ] **AC-001**: Keycloak PKCE login via system browser (expo-web-browser)
- [ ] **AC-002**: Token refresh works before expiry
- [ ] **AC-003**: Facility selection after login
- [ ] **AC-004**: Mode switching based on roles
- [ ] **AC-005**: AuthGuard blocks unauthenticated access
- [ ] **AC-006**: Trust headers injected on every API call
- [ ] **AC-007**: Tokens stored in platform keychain (expo-secure-store)

### 4.2 Provider Mode

- [ ] **AC-010**: Patient search by name
- [ ] **AC-011**: Patient search by NID
- [ ] **AC-012**: QR code scan identifies patient
- [ ] **AC-013**: Encounter creation
- [ ] **AC-014**: Vitals recording (BP, HR, Temp, SpO2, RR, Weight, Height, BMI)
- [ ] **AC-015**: Vitals batch recording
- [ ] **AC-016**: ICD-11 diagnosis search and selection
- [ ] **AC-017**: Prescription creation
- [ ] **AC-018**: Lab order creation with urgency
- [ ] **AC-019**: Referral creation
- [ ] **AC-020**: Clinical notes saved
- [ ] **AC-021**: Encounter close with summary
- [ ] **AC-022**: Activity feed timeline
- [ ] **AC-023**: Task list
- [ ] **AC-024**: Task status update

### 4.3 Messaging & Telemedicine

- [ ] **AC-030**: Conversation list loads
- [ ] **AC-031**: Messages load for conversation
- [ ] **AC-032**: Send message
- [ ] **AC-033**: Real-time message delivery
- [ ] **AC-034**: Telemedicine session list
- [ ] **AC-035**: Join session returns video token
- [ ] **AC-036**: End session updates status

### 4.4 Outreach Mode

- [ ] **AC-040**: Household list loads
- [ ] **AC-041**: Household registration
- [ ] **AC-042**: Community visit with GPS
- [ ] **AC-043**: Screening recording
- [ ] **AC-044**: Immunization recording
- [ ] **AC-045**: Follow-up list sorted by overdue
- [ ] **AC-046**: Offline household access

### 4.5 Supervisor Mode

- [ ] **AC-050**: Dashboard KPI tiles
- [ ] **AC-051**: Team member list
- [ ] **AC-052**: Stock levels with low-stock alerts
- [ ] **AC-053**: Dispatch creation
- [ ] **AC-054**: Escalation acknowledge/resolve
- [ ] **AC-055**: Support ticket creation

### 4.6 Offline Edge Mode

- [ ] **AC-060**: Network status detection (NetInfo)
- [ ] **AC-061**: Sync queue displays pending items
- [ ] **AC-062**: Sync all triggers background sync
- [ ] **AC-063**: Failed items retried
- [ ] **AC-064**: Conflict detection on sync
- [ ] **AC-065**: Conflict resolution (LOCAL_WINS/SERVER_WINS)
- [ ] **AC-066**: Edge snapshot download
- [ ] **AC-067**: Entitlement verification by CPID
- [ ] **AC-068**: Break-glass activation with audit
- [ ] **AC-069**: Break-glass deactivation

### 4.7 Backend Integration

- [ ] **AC-070**: All BFF routes use `/internal/v1/` prefix
- [ ] **AC-071**: 4 hard-required trust headers present
- [ ] **AC-072**: ApiEnvelope response shape
- [ ] **AC-073**: Idempotency keys on writes
- [ ] **AC-074**: Outbox events emitted

### 4.8 Native Mobile Platform

- [ ] **AC-080**: App builds successfully for Android (EAS Build)
- [ ] **AC-081**: App builds successfully for iOS (EAS Build)
- [ ] **AC-082**: Deep link `impilo.provider://callback` handled correctly
- [ ] **AC-083**: Secure storage uses platform keychain (not plaintext)
- [ ] **AC-084**: Network detection uses NetInfo (not window.addEventListener)
- [ ] **AC-085**: OAuth PKCE uses system browser (not WebView)

---

## 5. Test Coverage

| Test File | Area | Tests |
|-----------|------|-------|
| `AuthGuard.test.tsx` | Navigation | 3 |
| `ModeRouter.test.tsx` | Navigation | 4+ |
| `EncounterWorkflow.test.tsx` | Provider | 5+ |
| `PatientLookup.test.tsx` | Provider | 3 |
| `ProviderDashboard.test.tsx` | Provider | 2 |
| `OutreachDashboard.test.tsx` | Outreach | 2 |
| `OfflineCapture.test.tsx` | Outreach | 3 |
| `SupervisorDashboard.test.tsx` | Supervisor | 3 |
| `SyncFlow.test.tsx` | Offline | 7 |
| `ConflictReview.test.tsx` | Offline | 6 |
| `Messaging.test.tsx` | Messaging | 3 |
| `Telemedicine.test.tsx` | Telemedicine | 3 |
| `BackendIntegration.test.tsx` | Integration | 5 |

**Total**: 13 test files, 49+ test cases

---

## 6. Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Engineering Lead | | | |
| QA Lead | | | |
| Product Owner | | | |
| Clinical SME | | | |

---

*End of Provider App Acceptance Pack*
