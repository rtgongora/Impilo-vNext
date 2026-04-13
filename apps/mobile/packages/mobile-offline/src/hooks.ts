/**
 * React Hooks for Offline SDK — Reactive access to offline store and sync state.
 */

import { useState, useEffect, useCallback, useMemo } from "react";
import type { OfflineRecord, SyncStatus, ConflictRecord, ConflictResolution, EdgeSnapshot } from "./types";
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
export function useSyncEngine() {
  const [status, setStatus] = useState<SyncEngineStatus>(syncEngine.getStatus());
  const [pendingCount, setPendingCount] = useState(0);
  const [conflictCount, setConflictCount] = useState(0);

  useEffect(() => {
    syncEngine.setCallbacks({
      onStatusChange: setStatus,
      onSyncComplete: async () => {
        setPendingCount(await syncEngine.getPendingCount());
        setConflictCount(await syncEngine.getConflictCount());
      },
    });

    // Load initial counts
    (async () => {
      setPendingCount(await syncEngine.getPendingCount());
      setConflictCount(await syncEngine.getConflictCount());
    })();
  }, []);

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
    queue: [],
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
