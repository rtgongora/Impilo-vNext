import { apiClient } from "@impilo/mobile-api-client";
import { getOfflineStorage, type QueuedOperation } from "@impilo/mobile-offline";
import { appStore } from "../stores/appStore";
import { EMERGENCY_QUEUE_PREFIXES } from "../lib/emergencyOfflinePolicy";

function isEmergencyOp(op: QueuedOperation): boolean {
  return EMERGENCY_QUEUE_PREFIXES.some((prefix) => op.path.startsWith(prefix));
}

export async function countPendingEmergencyWrites(): Promise<number> {
  const storage = getOfflineStorage();
  const queue = await storage.listQueue();
  return queue.filter((op) => op.status === "pending" && isEmergencyOp(op)).length;
}

export async function refreshEmergencyOutboxBadge(): Promise<number> {
  const count = await countPendingEmergencyWrites();
  appStore.getState().setPendingSyncCount(count);
  return count;
}

export interface EmergencyOutboxFlushResult {
  replayed: number;
  failed: number;
}

export async function flushEmergencyOutbox(): Promise<EmergencyOutboxFlushResult> {
  const storage = getOfflineStorage();
  const queue = await storage.peekQueue();
  const pending = queue.filter((op) => op.status === "pending" && isEmergencyOp(op));

  let replayed = 0;
  let failed = 0;

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
      failed += 1;
      await storage.updateQueueItem(op.id, {
        retryCount: op.retryCount + 1,
        status: op.retryCount + 1 >= op.maxRetries ? "failed" : "pending",
        error: err instanceof Error ? err.message : "sync failed",
      });
    }
  }

  await refreshEmergencyOutboxBadge();
  return { replayed, failed };
}
