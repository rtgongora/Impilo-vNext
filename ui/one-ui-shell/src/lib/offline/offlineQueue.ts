/**
 * Offline queue policy + flush for the experience shell (W16b).
 *
 * Enrollment comes from `/offline-feature-manifest.json` (same file the service worker reads).
 * Triage paths are never queued — Tier B refuses offline acuity with NOT_TRIAGEABLE_OFFLINE.
 */

import { enqueueOperation, listQueue, removeQueueItem, updateQueueItem } from "./outbox";
import type { QueuedOperation } from "./types";

export const NOT_TRIAGEABLE_OFFLINE = "NOT_TRIAGEABLE_OFFLINE";

export const NOT_TRIAGEABLE_OFFLINE_MESSAGE =
  "Triage cannot be completed offline. Reconnect so WHO IITT can score this assessment — no acuity is assigned while disconnected.";

type Manifest = {
  features?: {
    emergency?: {
      enrolled?: boolean;
      apiQueuePrefixes?: string[];
      neverQueueApiSuffixes?: string[];
    };
  };
};

let manifestCache: Manifest | null = null;

/** @internal vitest */
export function resetOfflineManifestCacheForTests(): void {
  manifestCache = null;
}

async function loadManifest(): Promise<Manifest> {
  if (manifestCache) return manifestCache;
  if (typeof fetch === "undefined") return {};
  try {
    const res = await fetch("/offline-feature-manifest.json", { cache: "no-store" });
    if (!res.ok) return {};
    manifestCache = (await res.json()) as Manifest;
    return manifestCache;
  } catch {
    return {};
  }
}

/** True when the browser reports offline or a previous probe failed. */
export function isBrowserOffline(): boolean {
  if (typeof navigator === "undefined") return false;
  return navigator.onLine === false;
}

export function isTriagePath(path: string): boolean {
  const bare = path.split("?")[0] ?? path;
  return bare.endsWith("/triage") || bare.endsWith("/triage/score") || bare.includes("/triage/");
}

export async function isQueueableOfflineWrite(method: string, path: string): Promise<boolean> {
  if (!["POST", "PUT", "PATCH", "DELETE"].includes(method)) return false;
  if (isTriagePath(path)) return false;
  const manifest = await loadManifest();
  const emergency = manifest.features?.emergency;
  if (!emergency?.enrolled) return false;
  const prefixes = emergency.apiQueuePrefixes ?? [];
  const bare = path.split("?")[0] ?? path;
  if (!prefixes.some((p) => bare === p || bare.startsWith(p + "/") || bare.startsWith(p))) {
    return false;
  }
  const blocked = emergency.neverQueueApiSuffixes ?? [];
  return !blocked.some((suffix) => bare.endsWith(suffix));
}

export function methodToOpType(method: string): QueuedOperation["type"] {
  if (method === "DELETE") return "DELETE";
  if (method === "PUT" || method === "PATCH") return "UPDATE";
  return "CREATE";
}

export async function enqueueOfflineWrite(input: {
  method: string;
  path: string;
  body: unknown;
  idempotencyKey: string;
}): Promise<QueuedOperation> {
  return enqueueOperation({
    type: methodToOpType(input.method),
    collection: "emergency",
    payload: input.body ?? {},
    method: input.method,
    path: input.path,
    idempotencyKey: input.idempotencyKey,
  });
}

/**
 * Replay pending outbox rows. Caller supplies the transport so Idempotency-Key
 * stays on the same key minted at enqueue.
 */
export async function flushOfflineQueue(
  send: (op: QueuedOperation) => Promise<void>,
): Promise<{ flushed: number; failed: number }> {
  const pending = (await listQueue()).filter(
    (op) => op.status === "pending" || op.status === "failed",
  );
  let flushed = 0;
  let failed = 0;
  for (const op of pending) {
    await updateQueueItem(op.id, { status: "in_flight" });
    try {
      await send(op);
      await removeQueueItem(op.id);
      flushed += 1;
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      const retryCount = op.retryCount + 1;
      await updateQueueItem(op.id, {
        status: retryCount >= op.maxRetries ? "failed" : "pending",
        retryCount,
        error: message,
      });
      failed += 1;
    }
  }
  return { flushed, failed };
}

/** Tier-B offline triage refusal — never invent an acuity number. */
export function offlineTriageRefusal(): {
  status: 422;
  error: { code: string; message: string };
} {
  return {
    status: 422,
    error: {
      code: NOT_TRIAGEABLE_OFFLINE,
      message: NOT_TRIAGEABLE_OFFLINE_MESSAGE,
    },
  };
}
