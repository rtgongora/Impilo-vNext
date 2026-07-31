/**
 * IndexedDB outbox for offline clinical writes (W16b).
 *
 * Stores {@link QueuedOperation} rows with the same shape as the mobile offline SDK.
 * Idempotency-Key is minted at enqueue and must be replayed unchanged on flush.
 */

import { randomUUID } from "@/lib/uuid";
import type { QueuedOperation } from "./types";

const DB_NAME = "impilo-offline-outbox";
const DB_VERSION = 1;
const STORE = "queued_operations";

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === "undefined") {
      reject(new Error("IndexedDB unavailable"));
      return;
    }
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE)) {
        const store = db.createObjectStore(STORE, { keyPath: "id" });
        store.createIndex("status", "status", { unique: false });
        store.createIndex("createdAt", "createdAt", { unique: false });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("IndexedDB open failed"));
  });
}

function txDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error("IndexedDB transaction failed"));
    tx.onabort = () => reject(tx.error ?? new Error("IndexedDB transaction aborted"));
  });
}

export interface EnqueueInput {
  type: QueuedOperation["type"];
  collection: string;
  recordId?: string;
  payload: unknown;
  method: string;
  path: string;
  /** Minted by the caller (api-client) and replayed unchanged on flush. */
  idempotencyKey: string;
  maxRetries?: number;
}

export async function enqueueOperation(input: EnqueueInput): Promise<QueuedOperation> {
  const op: QueuedOperation = {
    id: randomUUID(),
    type: input.type,
    collection: input.collection,
    recordId: input.recordId ?? `local-${randomUUID()}`,
    payload: input.payload,
    method: input.method,
    path: input.path,
    createdAt: new Date().toISOString(),
    retryCount: 0,
    maxRetries: input.maxRetries ?? 5,
    status: "pending",
    idempotencyKey: input.idempotencyKey,
  };

  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).put(op);
    await txDone(tx);
  } finally {
    db.close();
  }
  return op;
}

export async function listQueue(): Promise<QueuedOperation[]> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readonly");
    const store = tx.objectStore(STORE);
    const req = store.getAll();
    const rows = await new Promise<QueuedOperation[]>((resolve, reject) => {
      req.onsuccess = () => resolve((req.result as QueuedOperation[]) ?? []);
      req.onerror = () => reject(req.error ?? new Error("getAll failed"));
    });
    await txDone(tx);
    return rows.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  } finally {
    db.close();
  }
}

export async function updateQueueItem(
  id: string,
  update: Partial<QueuedOperation>,
): Promise<void> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readwrite");
    const store = tx.objectStore(STORE);
    const existing = await new Promise<QueuedOperation | undefined>((resolve, reject) => {
      const req = store.get(id);
      req.onsuccess = () => resolve(req.result as QueuedOperation | undefined);
      req.onerror = () => reject(req.error ?? new Error("get failed"));
    });
    if (!existing) {
      await txDone(tx);
      return;
    }
    store.put({ ...existing, ...update, id });
    await txDone(tx);
  } finally {
    db.close();
  }
}

export async function removeQueueItem(id: string): Promise<void> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).delete(id);
    await txDone(tx);
  } finally {
    db.close();
  }
}

export async function pendingCount(): Promise<number> {
  const rows = await listQueue();
  return rows.filter((r) => r.status === "pending" || r.status === "in_flight" || r.status === "failed").length;
}

/** Test helper — wipe the outbox store. */
export async function clearOutboxForTests(): Promise<void> {
  const db = await openDb();
  try {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).clear();
    await txDone(tx);
  } finally {
    db.close();
  }
}
