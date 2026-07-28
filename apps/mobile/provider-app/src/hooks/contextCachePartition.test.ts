import { describe, expect, it, vi, beforeEach } from "vitest";
import { getOfflineStorage } from "@impilo/mobile-offline";
import { canPartitionSafely, clearContextScopedCaches } from "./contextCachePartition";

vi.mock("@impilo/mobile-offline", () => ({ getOfflineStorage: vi.fn() }));

function storage(queueSize: number, conflicts: unknown[] = []) {
  return {
    getQueueSize: vi.fn().mockResolvedValue(queueSize),
    getConflicts: vi.fn().mockResolvedValue(conflicts),
    clearAll: vi.fn().mockResolvedValue(undefined),
  };
}

describe("contextCachePartition", () => {
  beforeEach(() => {
    vi.mocked(getOfflineStorage).mockReset();
  });

  it("is safe when nothing is queued and no conflicts are outstanding", async () => {
    vi.mocked(getOfflineStorage).mockReturnValue(storage(0) as never);
    await expect(canPartitionSafely()).resolves.toEqual({ safe: true });
  });

  it("REFUSES when unsynced operations are queued — clearing would destroy that work", async () => {
    vi.mocked(getOfflineStorage).mockReturnValue(storage(3) as never);
    const result = await canPartitionSafely();
    expect(result.safe).toBe(false);
    expect(result.safe === false && result.reason).toContain("3 unsynced changes");
  });

  it("uses singular wording for exactly one queued change", async () => {
    vi.mocked(getOfflineStorage).mockReturnValue(storage(1) as never);
    const result = await canPartitionSafely();
    expect(result.safe === false && result.reason).toContain("1 unsynced change.");
  });

  it("REFUSES when conflicts are unresolved", async () => {
    vi.mocked(getOfflineStorage).mockReturnValue(storage(0, [{ id: "c1" }]) as never);
    const result = await canPartitionSafely();
    expect(result.safe).toBe(false);
    expect(result.safe === false && result.reason).toContain("unresolved conflict");
  });

  it("treats an unconfigured offline store as safe — nothing cached means nothing leaks", async () => {
    vi.mocked(getOfflineStorage).mockImplementation(() => {
      throw new Error("Offline storage not configured");
    });
    await expect(canPartitionSafely()).resolves.toEqual({ safe: true });
  });

  it("clearContextScopedCaches wipes the local cache", async () => {
    const s = storage(0);
    vi.mocked(getOfflineStorage).mockReturnValue(s as never);
    await clearContextScopedCaches();
    expect(s.clearAll).toHaveBeenCalled();
  });

  it("clearContextScopedCaches is a no-op when the store is unconfigured", async () => {
    vi.mocked(getOfflineStorage).mockImplementation(() => {
      throw new Error("Offline storage not configured");
    });
    await expect(clearContextScopedCaches()).resolves.toBeUndefined();
  });
});
