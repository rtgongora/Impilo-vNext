# Mobile App Program — Execution Plan

> Generated: 2026-03-15 | Updated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> Posture: App-Led Vertical Slices — no mocks, no stubs, no TODOs
> **Status: Shared foundation packages IMPLEMENTED under `apps/mobile/packages/`**
> **Status: M1 Provider App IMPLEMENTED as native mobile app (Android + iOS) under `apps/mobile/provider-app/`**
> **Status: M2 Citizen App IMPLEMENTED as native mobile app (Android + iOS) under `apps/mobile/citizen-app/`**
> **Status: M3 Support App IMPLEMENTED as web-first console under `ui/support-console/`**
> See: `docs/mobile/app-platform-audit.md` for full platform audit

---

## 1. Platform Classification

| App | Platform | Framework | Android | iOS | Rationale |
|-----|----------|-----------|---------|-----|-----------|
| **Provider App** | **Native mobile** | Expo SDK 52, React Native 0.76 | YES | YES | Field workers need native device access (camera, GPS, biometrics, offline storage) |
| **Citizen App** | **Native mobile** | Expo SDK 52, React Native 0.76 | YES | YES | Citizens interact primarily on smartphones; needs push notifications, secure storage, telehealth |
| **Support App** | **Web-first** | Next.js 14.2 | NO | NO | Desktop-centric ticket triage and incident management workflow |
| **Developer App** | **Web-first** | Next.js 14.2 | NO | NO | Desktop-centric API documentation, sandbox, and key management |

---

## 2. Final App Build Order

| Wave | App | Rationale |
|------|-----|-----------|
| **M1** | **Provider App** (native mobile) | Highest clinical value; exercises broadest backend surface; forces shared foundation |
| **M2** | **Citizen / Patient App** (native mobile) | Citizen-facing; reuses M1 shared foundation |
| **M3** | **Support App** (web-first) | Internal helpdesk; lightest backend surface |
| **M4** | **Developer / Partner App** (web-first) | External developer experience |

---

## 3. Shared Foundation Scope (built during M1, reused in M2–M4)

All shared packages are defined in `docs/mobile/shared-foundation-scope.md`. Summary:

| Package | Purpose | RN-Compatible |
|---------|---------|---------------|
| `@impilo/mobile-auth` | Keycloak PKCE session, token refresh, biometric unlock | Yes |
| `@impilo/mobile-api-client` | v1.1 header injection, ApiEnvelope, idempotency, retry | Yes |
| `@impilo/mobile-messaging` | Push notification registration, Kafka-backed real-time channels | Yes |
| `@impilo/mobile-timeline` | Unified event feed / activity timeline | Yes |
| `@impilo/mobile-offline` | Offline-first CRDT sync, queue, conflict resolution | Yes |
| `@impilo/mobile-design-system` | React Native UI components, theme tokens | Yes |
| `@impilo/mobile-trust` | Trust header contract (mirrors `ui/shared-ui/lib/contracts.ts`) | Yes |

---

## 4. Per-App Vertical Slice Scope

### 4.1 M1 — Provider App ✅ IMPLEMENTED (Native Mobile)

**Platform:** Android + iOS via Expo SDK 52
**Modes:** Provider, Outreach, Supervisor, Offline Edge
**Implementation:** `apps/mobile/provider-app/` — 50+ source files, 12 test files, 15 BFF controllers
**Config:** `app.json` (Android: `zw.gov.impilo.provider`, iOS: `zw.gov.impilo.provider`), `eas.json`, `app.config.ts`
**Docs:** `docs/mobile/provider-app/README.md`, `feature-map.md`, `mode-matrix.md`, `offline-behavior.md`
**Acceptance:** `docs/acceptance/provider-app-acceptance-pack.md`

#### Native Mobile Evidence
- `app.json` with Android package, iOS bundle identifier, permissions, icons
- `eas.json` with dev/preview/production build profiles for both platforms
- `expo-secure-store` for keychain/encrypted storage
- `expo-web-browser` for OAuth PKCE (system browser, not WebView)
- `@react-native-community/netinfo` for network detection
- `expo-linking` for deep link handling (`impilo.provider://`)
- All screens use React Native components (`View`, `Text`, `ScrollView`, `Pressable`, `StyleSheet`)
- Zero web DOM API references

#### Feature Areas

| Feature | Description | Primary Screen(s) |
|---------|-------------|--------------------|
| Patient Lookup | Search/scan patient by name, NID, or QR | Home → Search |
| Clinical Visit | Open visit, vitals, diagnosis (ICD-11), prescriptions | Visit → Vitals → Dx → Rx |
| Forms Engine | Dynamic forms driven by forms-service | Visit → Dynamic Form |
| Task Board | Assigned tasks, overdue reminders, escalation | Dashboard → Tasks |
| Outreach Mode | Community visit logging, GPS, household register | Outreach → Household → Visit |
| Supervisor Dashboard | Team overview, KPI tiles, approval queue | Supervisor → Dashboard |
| Offline Edge | Full visit workflow with local-first storage, background sync | All screens (offline overlay) |
| Prescriptions & Dispensing | Create Rx, view dispensing status | Visit → Rx → Dispensing |
| Referrals | Create/view referrals to other facilities | Visit → Referral |
| Lab Orders | Order labs, view results | Visit → Lab → Results |
| Notifications | Push + in-app for task assignments, results, escalations | Notification tray |

---

### 4.2 M2 — Citizen / Patient App ✅ IMPLEMENTED (Native Mobile)

**Platform:** Android + iOS via Expo SDK 52
**Domains:** Personal, Social, Marketplace, Messaging, Telehealth
**Implementation:** `apps/mobile/citizen-app/` — 30+ source files, 5 test files, 10 BFF controllers
**Config:** `app.json` (Android: `zw.gov.impilo.citizen`, iOS: `zw.gov.impilo.citizen`), `eas.json`, `app.config.ts`
**Docs:** `docs/mobile/citizen-app/README.md`, `feature-map.md`, `domain-matrix.md`, `privacy-and-safety.md`
**Acceptance:** `docs/acceptance/citizen-app-acceptance-pack.md`

#### Native Mobile Evidence
- `app.json` with Android package, iOS bundle identifier, permissions, icons
- `eas.json` with dev/preview/production build profiles for both platforms
- `expo-secure-store` for keychain/encrypted storage
- `expo-web-browser` for OAuth PKCE (system browser, not WebView)
- `@react-native-community/netinfo` for network detection
- `expo-linking` for deep link handling (`impilo.citizen://`)
- All screens use React Native components
- Zero web DOM API references

#### Feature Areas

| Feature | Description |
|---------|-------------|
| Health Profile | View demographics, conditions, medications, allergies |
| Appointments | Book, reschedule, cancel appointments |
| Prescriptions | View active Rx, request refill, track dispensing |
| Lab Results | View results with expandable values |
| Coverage | View coverage status, benefits, claims |
| Messages | Secure messaging with providers |
| Telehealth | Video/audio consultations |
| Marketplace | Browse health services, coverage-linked bookings |
| Social Feed | Health content, campaigns, community engagement |
| Consent Management | Grant/revoke data sharing consent |

---

### 4.3 M3 — Support App ✅ IMPLEMENTED (Web-First)

**Platform:** Web-only (Next.js 14.2)
**Implementation:** `ui/support-console/` — web console for support agents
**Rationale:** Support agents use desktop workstations; mobile would not improve the workflow
**Docs:** `docs/apps/support-app/README.md`
**Acceptance:** `docs/acceptance/support-app-acceptance-pack.md`

---

### 4.4 M4 — Developer / Partner App (Web-First)

**Platform:** Web-only (Next.js 14.2)
**Implementation:** `ui/developer-console/` — web console for developers/partners
**Rationale:** Developers interact with API docs, code samples, sandboxes — desktop-centric workflows
**Docs:** `docs/apps/developer-partner-app/README.md`

---

## 5. Cross-Cutting Concerns (All Waves)

| Concern | Implementation | Verification |
|---------|----------------|--------------|
| v1.1 Trust Headers | `@impilo/mobile-trust` injects all 14 headers | Golden contract test per app |
| Idempotency | `@impilo/mobile-api-client` sends `X-Idempotency-Key` on mutations | Replay test |
| Error Envelope | All errors returned as `ApiEnvelope` | Negative path in golden path |
| Offline Sync | CRDT-based local store with background sync | Airplane mode test |
| Push Notifications | FCM (Android) + APNs (iOS) via notification-service | Push receipt test |
| Secure Storage | `expo-secure-store` (Keychain/EncryptedSharedPreferences) | No plaintext tokens |
| OAuth PKCE | System browser via `expo-web-browser` (not WebView) | No in-app credential entry |
| Accessibility | WCAG 2.1 AA on all screens | Automated a11y scan |
| Security | Certificate pinning, biometric auth, encrypted local storage | Pen test checklist |

---

## 6. Exit Criteria for Full Mobile Program

- All 4 apps pass their golden path acceptance tests
- Provider App and Citizen App are buildable for Android and iOS (EAS Build)
- Support App and Developer App are documented as web-first
- All backend services are COMPLIANT in compliance matrix
- No mocks, stubs, or TODOs remain
