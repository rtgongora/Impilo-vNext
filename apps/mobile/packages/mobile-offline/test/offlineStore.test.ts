import { describe, it, expect, beforeEach } from "vitest";
import { MemoryStorageAdapter } from "../src/memoryAdapter";
import { configureOfflineStorage, createCollection } from "../src/offlineStore";

interface TestRecord {
  name: string;
  value: number;
}

describe("OfflineStore", () => {
  beforeEach(() => {
    configureOfflineStorage(new MemoryStorageAdapter());
  });

  it("creates a new record and queues for sync", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    const record = await col.upsert(undefined, { name: "test-1", value: 42 });

    expect(record.id).toBeTruthy();
    expect(record.data.name).toBe("test-1");
    expect(record.data.value).toBe(42);
    expect(record.status).toBe("pending");
    expect(record.version).toBe(1);
    expect(record.localVersion).toBe(1);
    expect(record.serverVersion).toBe(0);
  });

  it("retrieves a record by ID", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    const record = await col.upsert("rec-1", { name: "test-1", value: 42 });
    const fetched = await col.get("rec-1");

    expect(fetched).not.toBeNull();
    expect(fetched!.data.name).toBe("test-1");
  });

  it("returns null for non-existent record", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    const fetched = await col.get("non-existent");
    expect(fetched).toBeNull();
  });

  it("updates an existing record and increments version", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    await col.upsert("rec-1", { name: "test-1", value: 42 });
    const updated = await col.upsert("rec-1", { name: "test-1-updated", value: 99 });

    expect(updated.data.name).toBe("test-1-updated");
    expect(updated.version).toBe(2);
    expect(updated.localVersion).toBe(2);
  });

  it("lists all records in a collection", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    await col.upsert("a", { name: "a", value: 1 });
    await col.upsert("b", { name: "b", value: 2 });
    await col.upsert("c", { name: "c", value: 3 });

    const all = await col.getAll();
    expect(all).toHaveLength(3);
  });

  it("queries records with a predicate", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    await col.upsert("a", { name: "a", value: 10 });
    await col.upsert("b", { name: "b", value: 50 });
    await col.upsert("c", { name: "c", value: 20 });

    const high = await col.query((item) => item.data.value > 15);
    expect(high).toHaveLength(2);
  });

  it("deletes a record and queues DELETE for sync", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    await col.upsert("rec-1", { name: "test", value: 1 });
    await col.remove("rec-1");

    const fetched = await col.get("rec-1");
    expect(fetched).toBeNull();
  });

  it("reports sync status for a record", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    await col.upsert("rec-1", { name: "test", value: 1 });
    const status = await col.getSyncStatus("rec-1");
    expect(status).toBe("pending");
  });

  it("reports synced for non-existent record", async () => {
    const col = createCollection<TestRecord>("tests", "/api/v1/tests");
    const status = await col.getSyncStatus("non-existent");
    expect(status).toBe("synced");
  });
});
