/**
 * React Hooks for Offline SDK — Reactive access to offline store and sync state.
 */

import { useState, useEffect, useCallback, useMemo } from "react";
import type { OfflineRecord, SyncStatus, ConflictRecord, ConflictResolution, EdgeSnapshot, QueuedOperation } from "./types";
import { createCollection, getOfflineStorage } from "./offlineStore";
import { syncEngine } from "./syncEngine";
import type { SyncEngineStatus } from "./syncEngine";

/**
 * Hook for a typed offline collection.
 */
export function useOfflineStore<T>(collectionName: string, apiBasePath: string = "") {
  const [items, setItems] = useState<OfflineRecord<T>[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const collection = useMemo(
    () => createCollection<T>(collectionName, apiBasePath),
    [collectionName, apiBasePath]
  );

  const reload = useCallback(async () => {
    setIsLoading(true);
    const all = await collection.getAll();
    setItems(all);
    setIsLoading(false);
  }, [collection]);

  useEffect(() => {
    reload();
  }, [reload]);

  const upsert = useCallback(
    async (id: string | undefined, data: T) => {
      const record = await collection.upsert(id, data);
      await reload();
      return record;
    },
    [collection, reload]
  );

  const remove = useCallback(
    async (id: string) => {
      await collection.remove(id);
      await reload();
    },
    [collection, reload]
  );

  const getSyncStatus = useCallback(
    async (id: string): Promise<SyncStatus> => {
      return collection.getSyncStatus(id);
    },
    [collection]
  );

  return {
    items,
    isLoading,
    /**
     * Back-compat alias used by older screens.
     * Many screens expect the raw domain records, not the OfflineRecord wrapper.
     */
    data: items.map((r) => r.data),
    upsert,
    delete: remove,
    syncStatus: getSyncStatus,
    refresh: reload,
  };
}

/**
 * Hook for the sync engine.
 */
function mapQueuedOperationForUi(op: QueuedOperation) {
  const statusMap: Record<QueuedOperation["status"], string> = {
    pending: "PENDING",
    in_flight: "SYNCING",
    failed: "FAILED",
    completed: "SYNCED",
  };
  return {
    id: op.id,
    type: op.type,
    resourceType: op.collection,
    resourceId: op.recordId,
    payload: op.payload as Record<string, unknown>,
    status: statusMap[op.status] ?? "PENDING",
    attempts: op.retryCount,
    lastAttemptAt: undefined as string | undefined,
    error: op.error,
    createdAt: op.createdAt,
  };
}

export function useSyncEngine() {
  const [status, setStatus] = useState<SyncEngineStatus>(syncEngine.getStatus());
  const [pendingCount, setPendingCount] = useState(0);
  const [conflictCount, setConflictCount] = useState(0);
  const [queue, setQueue] = useState<ReturnType<typeof mapQueuedOperationForUi>[]>([]);

  const refreshQueue = useCallback(async () => {
    const storage = getOfflineStorage();
    const ops = await storage.listQueue();
    setQueue(ops.map(mapQueuedOperationForUi));
  }, []);

  useEffect(() => {
    syncEngine.setCallbacks({
      onStatusChange: setStatus,
      onSyncComplete: async () => {
        setPendingCount(await syncEngine.getPendingCount());
        setConflictCount(await syncEngine.getConflictCount());
        await refreshQueue();
      },
      onError: async () => {
        await refreshQueue();
      },
    });

    // Load initial counts
    (async () => {
      setPendingCount(await syncEngine.getPendingCount());
      setConflictCount(await syncEngine.getConflictCount());
      await refreshQueue();
    })();

    const interval = setInterval(() => {
      void refreshQueue();
    }, 2000);
    return () => clearInterval(interval);
  }, [refreshQueue]);

  const sync = useCallback(() => syncEngine.sync(), []);
  const forcePush = useCallback(() => syncEngine.forcePush(), []);

  return {
    status,
    pendingCount,
    conflictCount,
    sync,
    forcePush,
    /**
     * Back-compat aliases used by older screens.
     */
    triggerSync: sync,
    retryFailed: sync,
    queue,
    lastSyncAt: null,
  };
}

/**
 * Hook for edge snapshots (extended offline operation).
 */
export function useEdgeSnapshot(facilityId: string) {
  const [snapshot, setSnapshot] = useState<EdgeSnapshot | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const isStale = useMemo(() => {
    if (!snapshot) return true;
    return syncEngine.isSnapshotStale(snapshot);
  }, [snapshot]);

  const download = useCallback(async () => {
    setIsDownloading(true);
    setError(null);
    try {
      const result = await syncEngine.downloadEdgeSnapshot(facilityId);
      setSnapshot(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setIsDownloading(false);
    }
  }, [facilityId]);

  return {
    snapshot,
    isStale,
    isDownloading,
    /**
     * Back-compat alias used by older screens.
     */
    loading: isDownloading,
    error,
    download,
    lastSnapshot: snapshot,
  };
}

/**
 * Hook for conflict resolution.
 */
export function useConflicts() {
  const [conflicts, setConflicts] = useState<ConflictRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const reload = useCallback(async () => {
    setIsLoading(true);
    const storage = getOfflineStorage();
    const all = await storage.getConflicts();
    setConflicts(all);
    setIsLoading(false);
  }, []);

  useEffect(() => {
    reload();
  }, [reload]);

  const resolve = useCallback(
    async (conflictId: string, resolution: ConflictResolution) => {
      const storage = getOfflineStorage();
      const all = await storage.getConflicts();
      const conflict = all.find((c) => c.id === conflictId);
      if (conflict) {
        const record = await storage.get(conflict.collection, conflict.recordId);
        if (record) {
          if (resolution === "KEEP_SERVER" && conflict.serverData != null) {
            await storage.put({
              ...record,
              data: conflict.serverData as typeof record.data,
              status: "synced",
              serverVersion: record.localVersion,
              conflictData: undefined,
              updatedAt: new Date().toISOString(),
            });
          } else if (resolution === "KEEP_LOCAL") {
            await storage.put({
              ...record,
              data: conflict.localData as typeof record.data,
              status: "pending",
              conflictData: undefined,
              updatedAt: new Date().toISOString(),
            });
          } else if (resolution === "MERGE") {
            const merged =
              record.data && typeof record.data === "object" && conflict.localData && typeof conflict.localData === "object"
                ? ({ ...(record.data as object), ...(conflict.localData as object) } as typeof record.data)
                : (conflict.localData as typeof record.data);
            await storage.put({
              ...record,
              data: merged,
              status: "pending",
              conflictData: undefined,
              updatedAt: new Date().toISOString(),
            });
          }
        }
      }
      await storage.resolveConflict(conflictId, resolution);
      await storage.removeConflict(conflictId);
      await reload();
    },
    [reload]
  );

  return {
    conflicts,
    isLoading,
    resolve,
    /**
     * Back-compat alias used by older screens.
     */
    resolveConflict: resolve,
    refresh: reload,
  };
}
