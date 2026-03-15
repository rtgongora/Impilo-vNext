# Mobile Shared Foundation — Package Scope

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0
> Standard: vNext V3 + Tech Companion Spec 2.0
> Rule: All 4 mobile apps MUST use these shared packages. No re-implementation allowed.

---

## 1. Overview

The shared foundation is a set of workspace packages under `apps/mobile/packages/` that provide cross-cutting capabilities to all mobile apps. These packages are built during M1 (Provider App) and reused in M2–M4.

```
apps/mobile/
├── packages/
│   ├── mobile-auth/          # @impilo/mobile-auth
│   ├── mobile-api-client/    # @impilo/mobile-api-client
│   ├── mobile-trust/         # @impilo/mobile-trust
│   ├── mobile-messaging/     # @impilo/mobile-messaging
│   ├── mobile-timeline/      # @impilo/mobile-timeline
│   ├── mobile-offline/       # @impilo/mobile-offline
│   └── mobile-design-system/ # @impilo/mobile-design-system
├── provider-app/             # M1
├── citizen-app/              # M2
├── support-app/              # M3
└── developer-app/            # M4
```

---

## 2. Package Specifications

### 2.1 `@impilo/mobile-auth` — Auth & Session Management

**Purpose:** Keycloak PKCE authentication, token lifecycle, biometric unlock, session persistence.

| Capability | Details |
|------------|---------|
| Keycloak PKCE Flow | Authorization code flow with PKCE for mobile; no implicit grant |
| Token Storage | Encrypted secure storage (Keychain/Keystore) for access + refresh tokens |
| Token Refresh | Automatic background refresh before expiry; queue requests during refresh |
| Biometric Unlock | FaceID/TouchID/Fingerprint to unlock session without re-auth |
| Session Timeout | Configurable idle timeout with graceful re-auth prompt |
| Offline Token | Extended-lifetime token for offline-edge mode (via tshepo-offline-service) |
| Multi-Tenant | Tenant selection at login; tenant_id stored in session context |
| Logout | Token revocation + local state cleanup |

**Depends on:** Keycloak 25.x, tshepo-service, tshepo-offline-service

**Exports:**
```typescript
useAuth(): { user, login, logout, isAuthenticated, token }
useSession(): { tenantId, facilityId, actorType, actorId }
AuthProvider: React.FC  // wraps app root
withAuth(Component): React.FC  // HOC for protected screens
```

---

### 2.2 `@impilo/mobile-api-client` — API Client

**Purpose:** HTTP client with v1.1 header injection, ApiEnvelope parsing, idempotency, retry, and error handling.

| Capability | Details |
|------------|---------|
| Base URL Config | Per-environment config (dev/staging/prod) pointing to experience-bff |
| v1.1 Header Injection | Automatically injects all trust headers from session context |
| Idempotency | Generates `X-Idempotency-Key` (UUID v7) for all POST/PUT/PATCH |
| ApiEnvelope Parsing | Unwraps `ApiEnvelope<T>` response; throws typed errors on `success: false` |
| Retry with Backoff | Exponential backoff (1s, 2s, 4s) on 5xx and network errors; max 3 retries |
| Request Queuing | Queues requests when offline; replays on reconnect (delegates to mobile-offline) |
| Pagination | Built-in `PagedResponse<T>` cursor/offset helpers |
| Certificate Pinning | Pin experience-bff TLS certificate for MITM protection |
| Correlation ID | Generates `X-Correlation-Id` (UUID v7) per request for end-to-end tracing |

**Depends on:** `@impilo/mobile-auth`, `@impilo/mobile-trust`

**Exports:**
```typescript
apiClient: { get, post, put, patch, delete }
useQuery<T>(key, fetcher): TanStack Query wrapper with ApiEnvelope
useMutation<T>(mutator): TanStack Mutation wrapper with idempotency
ApiError: class { code, message, status, correlationId }
```

---

### 2.3 `@impilo/mobile-trust` — Trust Header Contract

**Purpose:** Single source of truth for trust header names and types on mobile. Mirrors `ui/shared-ui/lib/contracts.ts`.

| Capability | Details |
|------------|---------|
| Header Constants | All 14 trust header names as typed constants |
| Header Builder | Constructs header map from session context |
| Type Exports | `PurposeOfUse`, `ActorType`, `ApiEnvelope<T>`, `PagedResponse<T>` |
| Validation | Runtime validation that required headers are present before request |

**Depends on:** None (leaf package)

**Exports:**
```typescript
TRUST_HEADERS: Record<string, string>  // mirrors contracts.ts
buildTrustHeaders(session): Record<string, string>
type PurposeOfUse = "TREATMENT" | "PAYMENT" | "OPERATIONS" | ...
type ActorType = "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM"
interface ApiEnvelope<T> { success, data, error?, correlationId, timestamp }
interface PagedResponse<T> { items, page, size, totalElements, totalPages, hasNext }
```

---

### 2.4 `@impilo/mobile-messaging` — Push Notifications & Real-Time Channels

**Purpose:** Push notification registration, in-app notification feed, real-time event channels.

| Capability | Details |
|------------|---------|
| Push Registration | Register device token (FCM/APNs) with notification-service |
| Push Handling | Background + foreground push handling with deep link routing |
| Notification Feed | In-app notification list with read/unread state, pagination |
| Real-Time Channel | WebSocket/SSE connection for live updates (task assignments, messages, results) |
| Badge Count | Unread count badge management |
| Preferences | Per-category notification preferences (on/off, push/in-app) |

**Depends on:** `@impilo/mobile-api-client`, notification-service, channels-service

**Exports:**
```typescript
useNotifications(): { notifications, unreadCount, markRead, markAllRead }
usePushRegistration(): { register, unregister, isRegistered }
useChannel(topic): { messages, send, isConnected }
NotificationProvider: React.FC  // wraps app root
```

---

### 2.5 `@impilo/mobile-timeline` — Unified Event Feed / Activity Timeline

**Purpose:** Reusable timeline component that renders heterogeneous events (visits, messages, Rx, labs, referrals) in chronological order.

| Capability | Details |
|------------|---------|
| Event Normalization | Maps diverse backend events (visit, Rx, lab, message) into unified timeline item |
| Infinite Scroll | Cursor-based pagination with pull-to-refresh |
| Filtering | Filter by event type, date range, facility |
| Detail Navigation | Tap event → navigate to detail screen |
| Offline Cache | Timeline cached locally for offline browsing |

**Depends on:** `@impilo/mobile-api-client`, `@impilo/mobile-offline`

**Exports:**
```typescript
useTimeline(patientId, filters): { events, loadMore, refresh, isLoading }
TimelineView: React.FC<{ events }>  // renders timeline UI
interface TimelineEvent { id, type, timestamp, title, summary, metadata }
```

---

### 2.6 `@impilo/mobile-offline` — Offline-First SDK

**Purpose:** Local-first data storage with CRDT-based conflict resolution, background sync, and queue management.

| Capability | Details |
|------------|---------|
| Local Store | SQLite/WatermelonDB local database with schema migrations |
| CRDT Merge | Conflict-free replicated data types for concurrent edits |
| Sync Engine | Background sync with offline-sync-service; exponential backoff on failure |
| Operation Queue | Queue mutations when offline; replay in order on reconnect |
| Conflict UI | Surface merge conflicts that require user resolution |
| Sync Status | Observable sync state (synced, syncing, pending, conflict, error) |
| Edge Snapshot | Download facility-scoped data snapshot for extended offline operation |
| Data Retention | Configurable retention policy for local data; auto-purge after sync |

**Depends on:** `@impilo/mobile-api-client`, offline-sync-service, offline-edge-service

**Exports:**
```typescript
useOfflineStore<T>(collection): { items, upsert, delete, syncStatus }
useSyncEngine(): { sync, forcePush, status, pendingCount, conflictCount }
useEdgeSnapshot(facilityId): { download, lastSnapshot, isStale }
OfflineProvider: React.FC  // wraps app root, initializes local DB
SyncStatus: "synced" | "syncing" | "pending" | "conflict" | "error"
```

---

### 2.7 `@impilo/mobile-design-system` — Design System

**Purpose:** Shared UI primitives, tokens, and components for consistent look and feel across all apps.

| Capability | Details |
|------------|---------|
| Design Tokens | Colors, spacing, typography, shadows, border radii from Impilo brand |
| Core Components | Button, Card, Badge, StatusIndicator, DataTable, Input, Select, Modal, Toast, Avatar, Icon |
| Layout Components | Screen, Header, TabBar, BottomSheet, SafeArea, ScrollView |
| Form Components | TextField, DatePicker, Checkbox, Radio, Switch, FormField with validation |
| Clinical Components | VitalCard, DiagnosisBadge, RxCard, LabResultCard, TimelineItem |
| Feedback Components | LoadingSpinner, EmptyState, ErrorState, SkeletonLoader |
| Accessibility | WCAG 2.1 AA; screen reader labels, focus management, contrast ratios |
| Theming | Light/dark mode support; tenant-configurable accent color |

**Depends on:** None (leaf package, but mirrors `ui/shared-ui` tokens)

**Exports:**
```typescript
// All components exported as named exports
export { Button, Card, Badge, ... } from './components'
export { Screen, Header, TabBar, ... } from './layout'
export { TextField, DatePicker, ... } from './forms'
export { VitalCard, DiagnosisBadge, ... } from './clinical'
export { LoadingSpinner, EmptyState, ... } from './feedback'
export { tokens } from './tokens'
export { ThemeProvider, useTheme } from './theme'
```

---

## 3. Package Dependency Graph

```
mobile-trust (leaf)
    ↑
mobile-auth ← mobile-api-client
                    ↑
              ┌─────┼─────────┐
              ↑     ↑         ↑
    mobile-messaging  mobile-timeline  mobile-offline

mobile-design-system (leaf — used by all app screens)
```

---

## 4. Build Order

| Order | Package | Reason |
|-------|---------|--------|
| 1 | `@impilo/mobile-trust` | Leaf; no deps; needed by everything |
| 2 | `@impilo/mobile-design-system` | Leaf; no deps; needed by all screens |
| 3 | `@impilo/mobile-auth` | Depends on trust; needed by API client |
| 4 | `@impilo/mobile-api-client` | Depends on auth + trust; needed by all data packages |
| 5 | `@impilo/mobile-offline` | Depends on API client; needed by timeline |
| 6 | `@impilo/mobile-messaging` | Depends on API client |
| 7 | `@impilo/mobile-timeline` | Depends on API client + offline |

---

## 5. Relationship to Existing `ui/shared-ui`

| `ui/shared-ui` | Mobile Shared Foundation | Relationship |
|-----------------|--------------------------|-------------|
| `lib/contracts.ts` | `@impilo/mobile-trust` | Mobile trust mirrors web contracts; same header names and types |
| `components/*` | `@impilo/mobile-design-system` | Mobile design system is native equivalent of web shared-ui components |
| `tokens.css` | `@impilo/mobile-design-system/tokens` | Same design tokens, different format (CSS → JS/TS) |

The mobile shared foundation does NOT depend on `ui/shared-ui` at runtime. The contract is maintained by keeping both in sync via the same trust header specification in `TrustHeaders.java`.
