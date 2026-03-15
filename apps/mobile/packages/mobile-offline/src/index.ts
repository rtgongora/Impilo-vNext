/**
 * @impilo/mobile-offline — Offline-First SDK
 *
 * Local-first data storage with operation queue, background sync,
 * conflict detection, edge snapshots, and entitlement validation.
 * All 4 mobile apps MUST use this package for offline support.
 */

// Types
export type {
  SyncStatus,
  OfflineRecord,
  QueuedOperation,
  ConflictRecord,
  ConflictResolution,
  SyncProgress,
  EdgeSnapshot,
  LocalStorageAdapter,
} from "./types";

// Storage adapter
export { MemoryStorageAdapter } from "./memoryAdapter";

// Offline store
export { configureOfflineStorage, getOfflineStorage, createCollection } from "./offlineStore";

// Sync engine
export { SyncEngine, syncEngine } from "./syncEngine";
export type { SyncEngineStatus, SyncEngineCallbacks } from "./syncEngine";

// React hooks
export { useOfflineStore, useSyncEngine, useEdgeSnapshot, useConflicts } from "./hooks";
