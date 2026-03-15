import { describe, it, expect, beforeEach } from "vitest";
import { MemoryStorageAdapter } from "../src/memoryAdapter";
import type { QueuedOperation, ConflictRecord } from "../src/types";

describe("MemoryStorageAdapter", () => {
  let adapter: MemoryStorageAdapter;

  beforeEach(() => {
    adapter = new MemoryStorageAdapter();
  });

  describe("collection operations", () => {
    it("put and get a record", async () => {
      await adapter.put({
        id: "r1", collection: "col", data: { x: 1 },
        version: 1, localVersion: 1, serverVersion: 0,
        status: "pending", createdAt: "2026-01-01", updatedAt: "2026-01-01",
      });
      const r = await adapter.get("col", "r1");
      expect(r).not.toBeNull();
      expect(r!.data).toEqual({ x: 1 });
    });

    it("getAll returns all records in collection", async () => {
      await adapter.put({ id: "a", collection: "c", data: {}, version: 1, localVersion: 1, serverVersion: 0, status: "synced", createdAt: "", updatedAt: "" });
      await adapter.put({ id: "b", collection: "c", data: {}, version: 1, localVersion: 1, serverVersion: 0, status: "synced", createdAt: "", updatedAt: "" });
      const all = await adapter.getAll("c");
      expect(all).toHaveLength(2);
    });

    it("delete removes a record", async () => {
      await adapter.put({ id: "a", collection: "c", data: {}, version: 1, localVersion: 1, serverVersion: 0, status: "synced", createdAt: "", updatedAt: "" });
      await adapter.delete("c", "a");
      expect(await adapter.get("c", "a")).toBeNull();
    });

    it("clear removes all records in a collection", async () => {
      await adapter.put({ id: "a", collection: "c", data: {}, version: 1, localVersion: 1, serverVersion: 0, status: "synced", createdAt: "", updatedAt: "" });
      await adapter.clear("c");
      expect(await adapter.getAll("c")).toHaveLength(0);
    });
  });

  describe("queue operations", () => {
    const op: QueuedOperation = {
      id: "op-1", type: "CREATE", collection: "c", recordId: "r1",
      payload: {}, method: "POST", path: "/api/v1/c",
      createdAt: "2026-01-01", retryCount: 0, maxRetries: 3,
      status: "pending", idempotencyKey: "ik-1",
    };

    it("enqueue and dequeue", async () => {
      await adapter.enqueue(op);
      const dequeued = await adapter.dequeue();
      expect(dequeued).not.toBeNull();
      expect(dequeued!.id).toBe("op-1");
      expect(dequeued!.status).toBe("in_flight");
    });

    it("dequeue returns null when empty", async () => {
      expect(await adapter.dequeue()).toBeNull();
    });

    it("getQueueSize counts pending and in_flight", async () => {
      await adapter.enqueue(op);
      expect(await adapter.getQueueSize()).toBe(1);
    });

    it("removeQueueItem removes from queue", async () => {
      await adapter.enqueue(op);
      await adapter.removeQueueItem("op-1");
      expect(await adapter.getQueueSize()).toBe(0);
    });
  });

  describe("conflict operations", () => {
    it("addConflict and getConflicts", async () => {
      const conflict: ConflictRecord = {
        id: "conf-1", collection: "c", recordId: "r1",
        localData: { a: 1 }, serverData: { a: 2 },
        detectedAt: "2026-01-01",
      };
      await adapter.addConflict(conflict);
      const conflicts = await adapter.getConflicts();
      expect(conflicts).toHaveLength(1);
      expect(conflicts[0].localData).toEqual({ a: 1 });
    });

    it("resolveConflict and removeConflict", async () => {
      await adapter.addConflict({
        id: "conf-1", collection: "c", recordId: "r1",
        localData: {}, serverData: {},
        detectedAt: "2026-01-01",
      });
      await adapter.resolveConflict("conf-1", "KEEP_LOCAL");
      await adapter.removeConflict("conf-1");
      expect(await adapter.getConflicts()).toHaveLength(0);
    });
  });
});
