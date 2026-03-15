# Mobile App Interoperability Architecture

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0

---

## 1. Overview

The Impilo mobile platform comprises 4 apps (Provider, Citizen, Support, Developer) that share a common foundation layer. This document defines how these apps interoperate with each other, with the backend platform, and with the existing web UI ecosystem.

---

## 2. Architecture Layers

```
┌──────────────────────────────────────────────────────────┐
│                    Mobile Apps (4)                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ Provider │ │ Citizen  │ │ Support  │ │Developer │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘   │
│       │             │            │             │          │
│  ┌────┴─────────────┴────────────┴─────────────┴────┐   │
│  │           Shared Foundation Layer                 │   │
│  │  ┌────────┐ ┌──────┐ ┌──────────┐ ┌───────────┐ │   │
│  │  │ trust  │ │ auth │ │api-client│ │  offline  │ │   │
│  │  ├────────┤ ├──────┤ ├──────────┤ ├───────────┤ │   │
│  │  │  msg   │ │ feed │ │  design  │ │           │ │   │
│  │  └────────┘ └──────┘ └──────────┘ └───────────┘ │   │
│  └──────────────────────┬───────────────────────────┘   │
└─────────────────────────┼───────────────────────────────┘
                          │ HTTPS (v1.1 headers)
┌─────────────────────────┼───────────────────────────────┐
│              experience-bff (gateway)                    │
│  /internal/v1/mobile/provider/*                          │
│  /internal/v1/mobile/citizen/*                           │
│  /internal/v1/mobile/support/*                           │
│  /internal/v1/mobile/developer/*                         │
└─────────────────────────┬───────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────┐
│                  Envoy (ext_authz)                        │
│              ┌──────────┴──────────┐                     │
│              │    TSHEPO Service    │                     │
│              │  (Policy Engine)     │                     │
│              └─────────────────────┘                     │
└─────────────────────────┬───────────────────────────────┘
                          │
┌─────────────────────────┼───────────────────────────────┐
│              Backend Services (22+)                      │
│  vito  pct  oros  pharmacy  tuso  indawo  channels  ... │
└─────────────────────────────────────────────────────────┘
```

---

## 3. Trust Header Flow

Every mobile request follows the same trust chain as web requests:

1. **Mobile app** constructs request with v1.1 trust headers via `@impilo/mobile-trust`
2. **`@impilo/mobile-api-client`** injects headers from session context, adds idempotency key for commands
3. **experience-bff** receives request, routes to appropriate backend
4. **Envoy ext_authz** intercepts, sends to **TSHEPO** for policy evaluation
5. **TSHEPO** evaluates RBAC/ABAC, returns ALLOW/DENY/STEP_UP_REQUIRED
6. **Backend service** processes request, emits to `event_outbox`

The 14 trust headers are identical to the web platform:
- 4 hard-required: `X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`
- Session: `Authorization`, `X-Actor-ID`, `X-Actor-Type`
- Context: `X-Facility-ID`, `X-Workspace-ID`, `X-Shift-ID`
- Policy: `X-Purpose-of-Use`, `X-Device-Fingerprint`
- Command: `Idempotency-Key`, `X-Client-Timeout-MS`

---

## 4. Cross-App Interoperability

### 4.1 Shared Session Context

All apps use `@impilo/mobile-auth` for Keycloak PKCE authentication. The session context (tenant, actor, facility) is shared through the same Zustand store pattern, ensuring consistent trust header injection regardless of which app is active.

### 4.2 Deep Link Routing

Timeline events and notifications carry deep links that can target any app:
- `/visits/{id}` → Provider App
- `/prescriptions/{id}` → Provider App or Citizen App (context-dependent)
- `/messages/{conversationId}` → Any app with messaging
- `/tickets/{id}` → Support App

The `@impilo/mobile-timeline` package generates deep links that the consuming app resolves via its navigation stack.

### 4.3 Notification Routing

`@impilo/mobile-messaging` handles push notification routing:
- FCM/APNs token is registered per device, not per app
- Notification category determines which app handles the notification
- Background push can trigger sync operations in `@impilo/mobile-offline`

### 4.4 Offline Data Sharing

Each app maintains its own offline collections, but the sync engine (`@impilo/mobile-offline`) uses a shared queue and conflict resolution mechanism. Data synced by the Provider App is immediately visible to the Citizen App through the backend.

---

## 5. Web ↔ Mobile Parity

| Concern | Web (`ui/shared-ui`) | Mobile (`@impilo/mobile-*`) |
|---------|---------------------|---------------------------|
| Trust headers | `lib/contracts.ts` | `@impilo/mobile-trust` |
| API client | Per-app `apiClient.ts` | `@impilo/mobile-api-client` (shared) |
| Session store | Per-app `sessionStore.ts` | `@impilo/mobile-auth` (shared) |
| Components | `shared-ui/components/` | `@impilo/mobile-design-system` |
| Design tokens | `tokens.css` | `tokens/*.ts` |
| Error handling | Per-app inline | `ApiError` class + `ErrorState` component |

The mobile platform consolidates patterns that are duplicated across 15+ web apps into 7 shared packages.

---

## 6. API Contract Alignment

- Mobile apps talk to **experience-bff** exclusively (no direct backend calls)
- BFF routes follow `/internal/v1/mobile/{app-context}/*` naming
- All responses use `ApiEnvelope<T>` format
- All paginated responses use `PagedResponse<T>` format
- All command endpoints require `Idempotency-Key`
- Error responses carry `correlationId` for end-to-end tracing

---

## 7. Security Model

| Layer | Control |
|-------|---------|
| Transport | TLS 1.3 with certificate pinning to experience-bff |
| Authentication | Keycloak PKCE (no implicit grant, no client secret on mobile) |
| Token storage | Encrypted Keychain (iOS) / Keystore (Android) via `@impilo/mobile-auth` |
| Authorization | TSHEPO ext_authz on every request |
| Step-up | Biometric / TOTP / SMS via `StepUpRequiredError` handling |
| Offline | Extended-lifetime tokens issued by tshepo-offline-service |
| Data at rest | SQLite encryption for offline collections |
