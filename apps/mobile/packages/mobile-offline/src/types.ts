/**
 * Offline SDK Types — Local storage, sync, queue, and conflict models.
 */

export type SyncStatus = "synced" | "syncing" | "pending" | "conflict" | "error";

export interface OfflineRecord<T = unknown> {
  id: string;
  collection: string;
  data: T;
  version: number;
  localVersion: number;
  serverVersion: number;
  status: SyncStatus;
  createdAt: string;
  updatedAt: string;
  syncedAt?: string;
  conflictData?: T;
}

export interface QueuedOperation {
  id: string;
  type: "CREATE" | "UPDATE" | "DELETE";
  collection: string;
  recordId: string;
  payload: unknown;
  method: string;
  path: string;
  createdAt: string;
  retryCount: number;
  maxRetries: number;
  status: "pending" | "in_flight" | "failed" | "completed";
  error?: string;
  idempotencyKey: string;
}

export interface ConflictRecord<T = unknown> {
  id: string;
  collection: string;
  recordId: string;
  localData: T;
  serverData: T;
  baseData?: T;
  detectedAt: string;
  resolution?: ConflictResolution;
}

export type ConflictResolution = "KEEP_LOCAL" | "KEEP_SERVER" | "MERGE";

export interface SyncProgress {
  total: number;
  completed: number;
  failed: number;
  inFlight: number;
}

export interface EdgeSnapshot {
  facilityId: string;
  downloadedAt: string;
  recordCount: number;
  sizeBytes: number;
  version: string;
  collections: string[];
  expiresAt: string;
}

/**
 * Local storage adapter — abstraction over SQLite/WatermelonDB/AsyncStorage.
 */
export interface LocalStorageAdapter {
  get<T>(collection: string, id: string): Promise<OfflineRecord<T> | null>;
  getAll<T>(collection: string): Promise<OfflineRecord<T>[]>;
  query<T>(collection: string, predicate: (item: OfflineRecord<T>) => boolean): Promise<OfflineRecord<T>[]>;
  put<T>(record: OfflineRecord<T>): Promise<void>;
  delete(collection: string, id: string): Promise<void>;
  clear(collection: string): Promise<void>;
  clearAll(): Promise<void>;

  // Queue operations
  enqueue(op: QueuedOperation): Promise<void>;
  dequeue(): Promise<QueuedOperation | null>;
  peekQueue(): Promise<QueuedOperation[]>;
  updateQueueItem(id: string, update: Partial<QueuedOperation>): Promise<void>;
  removeQueueItem(id: string): Promise<void>;
  getQueueSize(): Promise<number>;

  // Conflict operations
  addConflict<T>(conflict: ConflictRecord<T>): Promise<void>;
  getConflicts(): Promise<ConflictRecord[]>;
  resolveConflict(id: string, resolution: ConflictResolution): Promise<void>;
  removeConflict(id: string): Promise<void>;
}
