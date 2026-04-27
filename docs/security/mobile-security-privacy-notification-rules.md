# Mobile security, privacy, and push notification rules

**Status**: Active (2026-04-10)  
**Scope**: Citizen and Provider apps (`apps/mobile/*`), `@impilo/mobile-trust`, `@impilo/mobile-api-client`

## 1. Authentication and session

- Tokens live in **Expo SecureStore** via `@impilo/mobile-auth`; no long-lived secrets in plain AsyncStorage for production builds.
- **Step-up** challenges from the API client surface through `onStepUpRequired` → global banner (`GlobalErrorBanner`).
- **Session timeout** and **remote logout** follow Keycloak / BFF behaviour; mobile must clear local caches when refresh fails irrecoverably (implementation evolves with BFF).

## 2. Trust propagation

- Every mutating request must carry the same **trust headers** as web (`TRUST_HEADERS` in `@impilo/mobile-trust`, aligned with `ui/shared-ui/lib/contracts.ts` and Tshepo).
- **Facility / workspace / shift** context must be set before clinical or billing calls; missing context should yield empty states or blocking prompts—not silent defaults.

## 3. Local storage of PHI

- Offline SQLite (provider/citizen) stores **authorised** clinical slices only; encryption at rest depends on OS + Expo secure practices—document limitations in `docs/audits/mobile-offline-readiness-audit.md`.
- Conflict resolution must not leak PHI into logs.

## 4. Push notifications (privacy-safe)

**Never** include in notification **title** or **body** (especially lock-screen visible):

- Condition names, HIV status, pregnancy, mental health, substance use, genetic information, lab values, or medication names.
- Full patient names for provider pushes when the device may be shared.

**Preferred patterns** (implement via backend template + client copy helpers in `@impilo/mobile-trust`):

| Intent | Example body (generic) |
|--------|-------------------------|
| New in-app message | “You have a new message in Impilo. Open the app to read.” |
| Appointment change | “Your appointment was updated. Open Impilo to view details.” |
| Result available | “You have a new health notification. Open Impilo to view.” |
| Consent action needed | “A consent request needs your attention in Impilo.” |
| Billing / wallet | “Your Impilo wallet or billing was updated. Open the app to review.” |

Use **`buildGenericHealthNotificationBody()`** and related helpers from `@impilo/mobile-trust` when constructing any client-scheduled local notifications (future).

## 5. Voice and narrative capture

- **No silent recording.** Any future native STT must show listening state and stop on blur/submit.
- **No auto-submit** of clinical narrative; user reviews before save.
- **No audio retention** by default on device for clinical dictation unless Mvumo + product explicitly allow storage/transmission.
- **Structured fields** (codes, doses, dates) remain structured; dictation assists free text only.

## 6. SOS and safeguarding

- Citizen SOS uses `sosService` API; payloads must not log raw audio or unnecessary location precision.
- Provider escalations and break-glass flows must emit **audit-friendly** metadata without storing prohibited content in local-only queues when server sync is expected.

## 7. Screenshot / screen recording

- Policy (blur sensitive fields, OS screenshot detection) is **future**; until enforced, minimise sensitive data density on persistent tiles and prefer drill-in for detail.
