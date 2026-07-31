/**
 * Offline operation contract — mirrored verbatim from
 * `apps/mobile/packages/mobile-offline/src/types.ts` so web and mobile can
 * converge on one outbox shape. Do not add web-only fields here.
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
  serverData: T | null;
  baseData?: T;
  detectedAt: string;
  resolution?: ConflictResolution;
}

export type ConflictResolution = "KEEP_LOCAL" | "KEEP_SERVER" | "MERGE";
