# Mobile offline readiness audit

**Date**: 2026-04-10  
**Packages**: `@impilo/mobile-offline`, provider `OfflineTabs`, citizen app persistence

## 1. Current implementation

| Capability | Provider | Citizen | Notes |
|------------|----------|---------|------|
| SQLite adapter | Yes (`openExpoSqliteOfflineAdapter`) | Yes (`impilo_citizen_offline.db`) | Falls back to `MemoryStorageAdapter` in tests / failure. |
| Sync engine | Yes (`syncEngine`, queue, conflicts) | Yes (initialised in `App.tsx`) | Citizen UI historically showed **online-only** bar — aligned to show pending/failed queue where applicable. |
| Conflict review | `ConflictReviewScreen` | Limited | Provider offline mode exposes flows. |
| Break-glass | `BreakGlassScreen` | N/A | Provider offline. |
| Pending count in UI | `OfflineDashboardScreen` → `setPendingSyncCount` | **Gap** (partial) | Global bar now can use `useSyncEngine` queue on both apps. |

## 2. Required behaviours (target)

1. **Online/offline indicator** — visible when disconnected or when sync non-idle.
2. **Sync pending** — show count of queued operations from `@impilo/mobile-offline` (not a separate invented counter).
3. **Sync failed** — surface `FAILED` queue entries and offer retry (`syncEngine.sync()` / `forcePush()` where appropriate).
4. **Conflict review** — user must choose resolution before data is marked clean.
5. **Audit** — offline captures must enqueue payloads that sync to BFF with correlation ids and trust headers when connectivity returns.

## 3. Gaps

- **Citizen**: fewer screens enqueue offline mutations than provider; personal health writes should expand offline coverage where product requires.
- **Mvumo offline capture**: provider **offline** mode has patterns; full Mvumo-assisted consent offline pack depends on `tshepo-offline-service` integration (see `SERVICE_CATALOG.md`).
- **Telemedicine pre-consult package**: not yet a first-class offline download in mobile; document as **future phase** tied to BFF packaging APIs.

## 4. Recommendations

1. Centralise **NetworkStatusBar** behaviour across apps (connectivity + sync summary).
2. Wire **push** “sync failed” tap-through to a **Sync status** screen (reuse provider `OfflineDashboardScreen` patterns on citizen where needed).
3. Add automated tests for queue status mapping (`mobile-offline` `mapQueuedOperationForUi`).
