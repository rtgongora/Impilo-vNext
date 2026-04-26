/**
 * Sync Engine — Background synchronization of queued operations.
 *
 * Processes the operation queue in order:
 *   1. Dequeue next pending operation
 *   2. Execute via API client
 *   3. On success: mark completed, update local record to "synced"
 *   4. On conflict (409): create ConflictRecord for user resolution
 *   5. On retryable error (5xx, network): increment retry count, re-enqueue
 *   6. On permanent failure: mark as "error", surface to user
 *
 * Backend: offline-sync-service (/internal/v1/sync/*)
 */

import { apiClient, isRetryable, ApiError } from "@impilo/mobile-api-client";
import { generateId } from "@impilo/mobile-trust";
import { getOfflineStorage } from "./offlineStore";
import type {
  QueuedOperation,
  ConflictRecord,
  SyncProgress,
  SyncStatus,
  EdgeSnapshot,
} from "./types";

export type SyncEngineStatus = "idle" | "syncing" | "error" | "offline";

export interface SyncEngineCallbacks {
  onStatusChange?: (status: SyncEngineStatus) => void;
  onProgress?: (progress: SyncProgress) => void;
  onConflict?: (conflict: ConflictRecord) => void;
  onError?: (error: Error, operation: QueuedOperation) => void;
  onSyncComplete?: () => void;
}

const SYNC_API = "/internal/v1/sync";

export class SyncEngine {
  private status: SyncEngineStatus = "idle";
  private callbacks: SyncEngineCallbacks = {};
  private syncTimer: ReturnType<typeof setTimeout> | null = null;
  private isSyncing = false;

  setCallbacks(callbacks: SyncEngineCallbacks): void {
    this.callbacks = callbacks;
  }

  getStatus(): SyncEngineStatus {
    return this.status;
  }

  /**
   * Start automatic background sync with the given interval.
   */
  startAutoSync(intervalMs: number = 30_000): void {
    this.stopAutoSync();
    this.syncTimer = setInterval(() => {
      this.sync();
    }, intervalMs);
    // Run immediately on start
    this.sync();
  }

  /**
   * Stop automatic background sync.
   */
  stopAutoSync(): void {
    if (this.syncTimer) {
      clearInterval(this.syncTimer);
      this.syncTimer = null;
    }
  }

  /**
   * Run a single sync cycle — process all pending operations in queue order.
   */
  async sync(): Promise<void> {
    if (this.isSyncing) return;
    this.isSyncing = true;

    const storage = getOfflineStorage();
    await storage.resetStaleInFlight("recovered_after_restart_or_interrupt");
    this.setStatus("syncing");

    try {
      const queue = await storage.peekQueue();
      const progress: SyncProgress = {
        total: queue.length,
        completed: 0,
        failed: 0,
        inFlight: 0,
      };
      this.callbacks.onProgress?.(progress);

      let operation = await storage.dequeue();
      while (operation) {
        progress.inFlight = 1;
        this.callbacks.onProgress?.(progress);

        try {
          await this.processOperation(operation);
          await storage.removeQueueItem(operation.id);
          progress.completed++;
        } catch (error) {
          if (error instanceof ApiError && error.status === 409) {
            // Conflict — create conflict record for user resolution
            await this.handleConflict(operation, error);
            await storage.removeQueueItem(operation.id);
            progress.completed++;
          } else if (isRetryable(error) && operation.retryCount < operation.maxRetries) {
            // Retryable — put back in queue with incremented retry count
            await storage.updateQueueItem(operation.id, {
              status: "pending",
              retryCount: operation.retryCount + 1,
              error: error instanceof Error ? error.message : String(error),
            });
          } else {
            // Permanent failure
            await storage.updateQueueItem(operation.id, {
              status: "failed",
              error: error instanceof Error ? error.message : String(error),
            });
            progress.failed++;
            this.callbacks.onError?.(
              error instanceof Error ? error : new Error(String(error)),
              operation
            );
          }
        }

        progress.inFlight = 0;
        this.callbacks.onProgress?.(progress);
        operation = await storage.dequeue();
      }

      this.setStatus("idle");
      this.callbacks.onSyncComplete?.();
    } catch (error) {
      this.setStatus("error");
    } finally {
      this.isSyncing = false;
    }
  }

  /**
   * Force push all pending operations (ignoring auto-sync interval).
   */
  async forcePush(): Promise<void> {
    return this.sync();
  }

  /**
   * Get pending operation count.
   */
  async getPendingCount(): Promise<number> {
    const storage = getOfflineStorage();
    return storage.getQueueSize();
  }

  /**
   * Get conflict count.
   */
  async getConflictCount(): Promise<number> {
    const storage = getOfflineStorage();
    const conflicts = await storage.getConflicts();
    return conflicts.length;
  }

  /**
   * Download an edge snapshot for a facility (extended offline operation).
   */
  async downloadEdgeSnapshot(facilityId: string): Promise<EdgeSnapshot> {
    const response = await apiClient.post<EdgeSnapshot>(
      `${SYNC_API}/edge/snapshot`,
      { facilityId }
    );
    return response.data;
  }

  /**
   * Check if an edge snapshot is stale (older than the given hours).
   */
  isSnapshotStale(snapshot: EdgeSnapshot, maxAgeHours: number = 24): boolean {
    const downloadedAt = new Date(snapshot.downloadedAt).getTime();
    const ageMs = Date.now() - downloadedAt;
    return ageMs > maxAgeHours * 60 * 60 * 1000;
  }

  private async processOperation(operation: QueuedOperation): Promise<void> {
    const options = {
      headers: { "idempotency-key": operation.idempotencyKey },
      noRetry: true, // We handle retries at the queue level
    };

    let responseData: unknown;
    switch (operation.method) {
      case "POST":
        responseData = (await apiClient.post(operation.path, operation.payload, options)).data;
        break;
      case "PUT":
        responseData = (await apiClient.put(operation.path, operation.payload, options)).data;
        break;
      case "PATCH":
        responseData = (await apiClient.patch(operation.path, operation.payload, options)).data;
        break;
      case "DELETE":
        await apiClient.delete(operation.path, options);
        responseData = null;
        break;
      default:
        responseData = null;
    }

    // Update local record status to synced
    const storage = getOfflineStorage();
    const record = await storage.get(operation.collection, operation.recordId);
    if (record) {
      let mergedData = record.data;
      if (responseData && typeof responseData === "object") {
        const envelope = responseData as Record<string, unknown>;
        const inner =
          "data" in envelope && envelope.data !== undefined && typeof envelope.data === "object"
            ? (envelope.data as Record<string, unknown>)
            : (envelope as Record<string, unknown>);
        if (operation.method === "POST" && record.data && typeof record.data === "object") {
          mergedData = { ...(record.data as Record<string, unknown>), ...inner } as typeof record.data;
        } else if (operation.method !== "DELETE") {
          mergedData = inner as typeof record.data;
        }
      }
      await storage.put({
        ...record,
        data: mergedData,
        status: "synced" as SyncStatus,
        serverVersion: record.localVersion,
        syncedAt: new Date().toISOString(),
      });
    }
  }

  private async handleConflict(operation: QueuedOperation, error: ApiError): Promise<void> {
    const storage = getOfflineStorage();
    const localRecord = await storage.get(operation.collection, operation.recordId);

    const conflict: ConflictRecord = {
      id: generateId(),
      collection: operation.collection,
      recordId: operation.recordId,
      localData: localRecord?.data ?? operation.payload,
      serverData: (error.details?.serverData as unknown) ?? null,
      baseData: (error.details?.baseData as unknown) ?? null,
      detectedAt: new Date().toISOString(),
    };

    await storage.addConflict(conflict);

    // Update local record status
    if (localRecord) {
      await storage.put({
        ...localRecord,
        status: "conflict" as SyncStatus,
        conflictData: error.details?.serverData,
      });
    }

    this.callbacks.onConflict?.(conflict);
  }

  private setStatus(status: SyncEngineStatus): void {
    this.status = status;
    this.callbacks.onStatusChange?.(status);
  }
}

/** Singleton sync engine instance. */
export const syncEngine = new SyncEngine();
