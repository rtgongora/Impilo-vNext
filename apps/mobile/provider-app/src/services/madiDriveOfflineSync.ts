/**
 * Replay queued MADI drive capture operations when connectivity returns.
 */

import { apiClient } from "@impilo/mobile-api-client";
import { getOfflineStorage } from "@impilo/mobile-offline";

export interface DriveSyncReplayResult {
  replayed: number;
  failed: number;
  conflicts: number;
}

export async function replayPendingDriveCaptures(): Promise<DriveSyncReplayResult> {
  const storage = getOfflineStorage();
  const queue = await storage.peekQueue();
  const pending = queue.filter(
    (op) => op.collection === "madi_drive_capture" && op.status === "pending",
  );

  let replayed = 0;
  let failed = 0;
  let conflicts = 0;

  for (const op of pending) {
    try {
      if (op.method === "PUT") {
        await apiClient.put(op.path, op.payload as Record<string, unknown>);
      } else if (op.method === "PATCH") {
        await apiClient.patch(op.path, op.payload as Record<string, unknown>);
      } else {
        await apiClient.post(op.path, op.payload as Record<string, unknown>);
      }
      await storage.removeQueueItem(op.id);
      replayed += 1;
    } catch (err: unknown) {
      const status = (err as { status?: number })?.status;
      if (status === 409) {
        conflicts += 1;
      } else {
        failed += 1;
        await storage.updateQueueItem(op.id, {
          retryCount: op.retryCount + 1,
          status: op.retryCount + 1 >= op.maxRetries ? "failed" : "pending",
          error: err instanceof Error ? err.message : "sync failed",
        });
      }
    }
  }

  return { replayed, failed, conflicts };
}
