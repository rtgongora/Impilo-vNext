/**
 * Offline Store — Collection-level CRUD with automatic queue management.
 *
 * Each collection maps to a backend resource (e.g., "visits", "vitals", "prescriptions").
 * Mutations are stored locally and queued for background sync.
 */

import { generateId } from "@impilo/mobile-trust";
import type { LocalStorageAdapter, OfflineRecord, QueuedOperation, SyncStatus } from "./types";

let storageAdapter: LocalStorageAdapter | null = null;

export function configureOfflineStorage(adapter: LocalStorageAdapter): void {
  storageAdapter = adapter;
}

export function getOfflineStorage(): LocalStorageAdapter {
  if (!storageAdapter) {
    throw new Error("Offline storage not configured. Call configureOfflineStorage() first.");
  }
  return storageAdapter;
}

/**
 * Creates a typed collection interface for offline data.
 */
export function createCollection<T>(collectionName: string, apiBasePath: string) {
  const storage = () => getOfflineStorage();

  return {
    /**
     * Get a record by ID from local storage.
     */
    async get(id: string): Promise<OfflineRecord<T> | null> {
      return storage().get<T>(collectionName, id);
    },

    /**
     * Get all records in this collection from local storage.
     */
    async getAll(): Promise<OfflineRecord<T>[]> {
      return storage().getAll<T>(collectionName);
    },

    /**
     * Query records with a predicate.
     */
    async query(predicate: (item: OfflineRecord<T>) => boolean): Promise<OfflineRecord<T>[]> {
      return storage().query<T>(collectionName, predicate);
    },

    /**
     * Create or update a record locally and queue for sync.
     */
    async upsert(id: string | undefined, data: T): Promise<OfflineRecord<T>> {
      const now = new Date().toISOString();
      const recordId = id ?? generateId();
      const existing = id ? await storage().get<T>(collectionName, id) : null;

      const record: OfflineRecord<T> = {
        id: recordId,
        collection: collectionName,
        data,
        version: (existing?.version ?? 0) + 1,
        localVersion: (existing?.localVersion ?? 0) + 1,
        serverVersion: existing?.serverVersion ?? 0,
        status: "pending",
        createdAt: existing?.createdAt ?? now,
        updatedAt: now,
      };

      await storage().put(record);

      // Queue the sync operation
      const op: QueuedOperation = {
        id: generateId(),
        type: existing ? "UPDATE" : "CREATE",
        collection: collectionName,
        recordId,
        payload: data,
        method: existing ? "PUT" : "POST",
        path: existing ? `${apiBasePath}/${recordId}` : apiBasePath,
        createdAt: now,
        retryCount: 0,
        maxRetries: 5,
        status: "pending",
        idempotencyKey: generateId(),
      };

      await storage().enqueue(op);

      return record;
    },

    /**
     * Delete a record locally and queue deletion for sync.
     */
    async remove(id: string): Promise<void> {
      await storage().delete(collectionName, id);

      const op: QueuedOperation = {
        id: generateId(),
        type: "DELETE",
        collection: collectionName,
        recordId: id,
        payload: null,
        method: "DELETE",
        path: `${apiBasePath}/${id}`,
        createdAt: new Date().toISOString(),
        retryCount: 0,
        maxRetries: 5,
        status: "pending",
        idempotencyKey: generateId(),
      };

      await storage().enqueue(op);
    },

    /**
     * Get sync status for a specific record.
     */
    async getSyncStatus(id: string): Promise<SyncStatus> {
      const record = await storage().get<T>(collectionName, id);
      return record?.status ?? "synced";
    },
  };
}
