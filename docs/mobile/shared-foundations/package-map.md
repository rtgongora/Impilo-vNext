# Mobile Shared Foundations — Package Map

> Generated: 2026-03-15 | Branch: claude/review-project-manifest-jb5O0

---

## Directory Structure

```
apps/mobile/packages/
├── mobile-trust/
│   ├── package.json          # @impilo/mobile-trust
│   ├── tsconfig.json
│   ├── vitest.config.ts
│   ├── src/
│   │   ├── index.ts          # Public API re-exports
│   │   ├── headers.ts        # TRUST_HEADERS constants, HARD_REQUIRED_HEADERS, COMMAND_METHODS
│   │   ├── types.ts          # PurposeOfUse, ActorType, ApiEnvelope, PagedResponse, SessionContext
│   │   └── headerBuilder.ts  # buildTrustHeaders(), validateRequiredHeaders(), generateId()
│   └── test/
│       └── headers.test.ts   # 15 test cases covering headers, builder, validation
│
├── mobile-auth/
│   ├── package.json          # @impilo/mobile-auth
│   ├── tsconfig.json
│   ├── vitest.config.ts
│   ├── src/
│   │   ├── index.ts          # Public API re-exports
│   │   ├── keycloakClient.ts # KeycloakClient — PKCE flow, token exchange, refresh, revoke
│   │   ├── tokenManager.ts   # TokenManager — automatic refresh, secure persistence, rate-limiting
│   │   ├── authStore.ts      # Zustand store — login, logout, session context, facility/workspace
│   │   ├── secureStorage.ts  # SecureStorageAdapter abstraction, MemorySecureStorage default
│   │   └── hooks.ts          # useAuth(), useSession(), useAccessToken()
│   └── test/
│       ├── keycloak.test.ts  # PKCE generation, auth URL building, AuthError
│       └── tokenManager.test.ts # Token lifecycle, persistence, restore
│
├── mobile-api-client/
│   ├── package.json          # @impilo/mobile-api-client
│   ├── tsconfig.json
│   ├── vitest.config.ts
│   ├── src/
│   │   ├── index.ts          # Public API re-exports
│   │   ├── client.ts         # apiClient — GET/POST/PUT/PATCH/DELETE with trust headers, retry, envelope
│   │   ├── config.ts         # configureApiClient(), base URL, timeout, retry config
│   │   ├── errors.ts         # ApiError, NetworkError, TimeoutError, StepUpRequiredError, isRetryable()
│   │   └── pagination.ts     # fetchPage(), fetchAllPages(), buildPaginationQuery()
│   └── test/
│       └── errors.test.ts    # Error classes, retryability logic
│
├── mobile-messaging/
│   ├── package.json          # @impilo/mobile-messaging
│   ├── tsconfig.json
│   ├── vitest.config.ts
│   ├── src/
│   │   ├── index.ts          # Public API re-exports
│   │   ├── types.ts          # Notification, Conversation, Message, ChannelEvent, PushRegistration
│   │   ├── notificationService.ts # registerDevice, fetchNotifications, markRead, preferences
│   │   ├── conversationService.ts # fetchConversations, sendMessage, markConversationRead
│   │   ├── channelClient.ts  # RealtimeChannel — SSE with auto-reconnect
│   │   └── hooks.ts          # useNotifications, usePushRegistration, useChannel, useConversations, useMessages
│   └── test/
│
├── mobile-timeline/
│   ├── package.json          # @impilo/mobile-timeline
│   ├── tsconfig.json
│   ├── vitest.config.ts
│   ├── src/
│   │   ├── index.ts          # Public API re-exports
│   │   ├── types.ts          # TimelineEvent, TimelineEventType, TimelineFilters, RawBackendEvent
│   │   ├── normalizers.ts    # Event normalization with registry, built-in normalizers for FHIR resources
│   │   ├── timelineService.ts # fetchTimeline(), fetchMyTimeline()
│   │   └── hooks.ts          # useTimeline(), useMyTimeline()
│   └── test/
│       └── normalizers.test.ts # Event normalization, sorting, custom normalizers
│
├── mobile-offline/
│   ├── package.json          # @impilo/mobile-offline
│   ├── tsconfig.json
│   ├── vitest.config.ts
│   ├── src/
│   │   ├── index.ts          # Public API re-exports
│   │   ├── types.ts          # OfflineRecord, QueuedOperation, ConflictRecord, LocalStorageAdapter
│   │   ├── memoryAdapter.ts  # MemoryStorageAdapter — in-memory implementation for testing
│   │   ├── offlineStore.ts   # configureOfflineStorage(), createCollection() — typed CRUD + queue
│   │   ├── syncEngine.ts     # SyncEngine — background sync, retry, conflict detection, edge snapshots
│   │   └── hooks.ts          # useOfflineStore, useSyncEngine, useEdgeSnapshot, useConflicts
│   └── test/
│       ├── offlineStore.test.ts  # Collection CRUD, versioning, queue management
│       └── memoryAdapter.test.ts # Adapter operations, queue, conflicts
│
└── mobile-design-system/
    ├── package.json          # @impilo/mobile-design-system
    ├── tsconfig.json
    ├── vitest.config.ts
    ├── src/
    │   ├── index.ts          # Public API re-exports
    │   ├── tokens/
    │   │   ├── index.ts      # Aggregated tokens export
    │   │   ├── colors.ts     # Primary, secondary, neutral, semantic, clinical palettes
    │   │   ├── spacing.ts    # 4px grid spacing scale
    │   │   └── typography.ts # Font sizes, weights, line heights, text style presets
    │   ├── theme/
    │   │   └── ThemeProvider.tsx # Light/dark mode, tenant accent color, useTheme()
    │   ├── components/
    │   │   ├── Button.tsx     # Primary interaction component (5 variants, 3 sizes)
    │   │   ├── Card.tsx       # Container with header/body/footer
    │   │   ├── Badge.tsx      # Status label (6 variants)
    │   │   ├── StatusIndicator.tsx # Colored dot + label
    │   │   └── Avatar.tsx     # Profile image with initials fallback
    │   ├── forms/
    │   │   ├── TextField.tsx  # Text input with validation
    │   │   ├── DatePicker.tsx # Date selection
    │   │   ├── Checkbox.tsx   # Boolean toggle
    │   │   ├── Select.tsx     # Dropdown selection
    │   │   └── Switch.tsx     # Toggle switch
    │   ├── clinical/
    │   │   ├── VitalCard.tsx  # Vital sign display
    │   │   ├── DiagnosisBadge.tsx # ICD-11 diagnosis badge
    │   │   ├── RxCard.tsx     # Prescription summary
    │   │   └── LabResultCard.tsx # Lab result with reference range
    │   ├── feedback/
    │   │   ├── LoadingSpinner.tsx # Loading indicator
    │   │   ├── EmptyState.tsx # Empty list/feed placeholder
    │   │   ├── ErrorState.tsx # Error display with retry
    │   │   └── SkeletonLoader.tsx # Skeleton placeholder
    │   └── layout/
    │       ├── Screen.tsx     # Top-level screen wrapper
    │       ├── Header.tsx     # Screen header with back/actions
    │       ├── BottomSheet.tsx # Modal bottom sheet
    │       └── TabBar.tsx     # Bottom tab navigation
    └── test/
        └── tokens.test.ts    # Token values and structure validation

---

## File Count Summary

| Package | Source Files | Test Files | Total |
|---------|-------------|-----------|-------|
| mobile-trust | 4 | 1 | 5 |
| mobile-auth | 6 | 2 | 8 |
| mobile-api-client | 5 | 1 | 6 |
| mobile-messaging | 6 | 0 | 6 |
| mobile-timeline | 5 | 1 | 6 |
| mobile-offline | 6 | 2 | 8 |
| mobile-design-system | 21 | 1 | 22 |
| **Total** | **53** | **8** | **61** |

---

## Export Summary

| Package | Named Exports | Type Exports |
|---------|--------------|-------------|
| mobile-trust | 7 | 10 |
| mobile-auth | 12 | 6 |
| mobile-api-client | 9 | 4 |
| mobile-messaging | 14 | 16 |
| mobile-timeline | 6 | 7 |
| mobile-offline | 10 | 8 |
| mobile-design-system | 28 | 32 |
| **Total** | **86** | **83** |
