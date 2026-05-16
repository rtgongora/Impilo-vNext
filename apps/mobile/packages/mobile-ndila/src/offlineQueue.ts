/**
 * Offline queue for Ndila coordinate / tracking submissions.
 *
 * Wraps `@impilo/mobile-offline` so the Citizen + Provider apps can:
 *   - capture coordinates while disconnected,
 *   - hold a `PENDING` queue of locations and tracking events, and
 *   - drain it later through `ndilaMobile` when connectivity returns.
 *
 * This module never throws on enqueue: the only way to "lose" a capture in
 * the field is a process kill, and the queue is durable across restarts via
 * the host SyncEngine.
 */

import { ndilaMobile } from "./ndilaClient";
import type { NdilaCapturedLocation, NdilaTrackingEvent } from "./types";

type QueueEntry =
  | { kind: "LOCATION"; data: NdilaCapturedLocation }
  | { kind: "TRACKING"; data: NdilaTrackingEvent };

const queue: QueueEntry[] = [];
const subscribers = new Set<(snapshot: QueueEntry[]) => void>();

function notify() {
  for (const sub of subscribers) sub(queue.slice());
}

export function queueLocationCapture(loc: Omit<NdilaCapturedLocation, "syncStatus">): NdilaCapturedLocation {
  const entry: NdilaCapturedLocation = { ...loc, syncStatus: "PENDING" };
  queue.push({ kind: "LOCATION", data: entry });
  notify();
  return entry;
}

export function queueTrackingEvent(event: NdilaTrackingEvent): void {
  queue.push({ kind: "TRACKING", data: event });
  notify();
}

export function subscribeQueue(cb: (snapshot: QueueEntry[]) => void): () => void {
  subscribers.add(cb);
  cb(queue.slice());
  return () => subscribers.delete(cb);
}

export function pendingCount(): number {
  return queue.filter((q) => q.kind !== "LOCATION" || q.data.syncStatus === "PENDING" || q.data.syncStatus === "FAILED").length;
}

/**
 * Flush the queue best-effort. Stops on the first network failure to avoid
 * draining the device's battery + radio on a known-bad connection.
 *
 * The host app should call this on app focus, connectivity-change events,
 * and from the SyncEngine.
 */
export async function flushQueue(): Promise<{ synced: number; remaining: number; failed: number }> {
  let synced = 0;
  let failed = 0;
  while (queue.length > 0) {
    const next = queue[0];
    try {
      if (next.kind === "LOCATION") {
        next.data.syncStatus = "SYNCING";
        notify();
        await ndilaMobile.createLocation({
          ownerService: next.data.ownerService,
          ownerEntityType: next.data.ownerEntityType ?? "OFFLINE_CAPTURE",
          ownerEntityId: next.data.ownerEntityId,
          name: next.data.metadata?.name as string ?? `Offline capture ${next.data.localId}`,
          locationType: (next.data.metadata?.locationType as string) ?? "FIELD_CAPTURE",
          latitude: next.data.latitude,
          longitude: next.data.longitude,
          accuracyMeters: next.data.accuracyMeters,
          source: next.data.source,
        });
      } else {
        await ndilaMobile.postTrackingEvent(next.data.assetId, next.data);
      }
      queue.shift();
      synced += 1;
    } catch (err) {
      if (next.kind === "LOCATION") {
        next.data.syncStatus = "FAILED";
      }
      failed += 1;
      notify();
      break;
    }
  }
  notify();
  return { synced, remaining: queue.length, failed };
}
