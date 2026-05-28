import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
  storage: {
    put: vi.fn(),
    enqueue: vi.fn(),
    listQueue: vi.fn(),
  },
  syncEngine: {
    forcePush: vi.fn(),
  },
}));

vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mocks.apiClient }));
vi.mock("@impilo/mobile-offline", () => ({
  getOfflineStorage: () => mocks.storage,
  syncEngine: mocks.syncEngine,
}));

import {
  fetchCoveragePayerJourney,
  flushCoverageCommandQueue,
  pendingCoverageCommandCount,
  runCoverageCommand,
} from "../../services/coverageCommandService";

describe("coverageCommandService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.storage.listQueue.mockResolvedValue([]);
    mocks.syncEngine.forcePush.mockResolvedValue(undefined);
  });

  it("queues failed commands for provisional offline sync", async () => {
    mocks.apiClient.post.mockRejectedValueOnce(new Error("offline"));

    const result = await runCoverageCommand("claim-submission", {
      coverageId: "cov-1",
      claimType: "OUTPATIENT",
      facilityId: "fac-1",
      totalAmount: "25",
    });

    expect(result.syncStatus).toBe("PROVISIONAL");
    expect(pendingCoverageCommandCount()).toBeGreaterThan(0);
    expect(mocks.storage.enqueue).toHaveBeenCalledWith(
      expect.objectContaining({
        collection: "coverage-commands",
        method: "POST",
        path: "/internal/v1/coverage/claims",
      }),
    );
  });

  it("flushes through the shared offline sync engine first", async () => {
    await flushCoverageCommandQueue();

    expect(mocks.syncEngine.forcePush).toHaveBeenCalled();
    expect(mocks.storage.listQueue).toHaveBeenCalled();
  });

  it("builds payer journey reconciliation from live route rows", async () => {
    mocks.apiClient.get.mockImplementation((path: string) => {
      if (path.includes("/claims")) return Promise.resolve({ data: { data: [{ id: "claim-1" }] } });
      if (path.includes("/remittances")) return Promise.resolve({ data: { data: [{ id: "rem-1" }] } });
      if (path.includes("/appeals")) return Promise.resolve({ data: { data: [] } });
      if (path.includes("/settlements")) return Promise.resolve({ data: { data: [{ id: "set-1" }] } });
      return Promise.resolve({ data: { data: [] } });
    });

    const journey = await fetchCoveragePayerJourney({
      coverageId: "cov-1",
      appellantId: "cpid-1",
      intentId: "intent-1",
    });

    expect(journey.claims).toHaveLength(1);
    expect(journey.remittances).toHaveLength(1);
    expect(journey.settlements).toHaveLength(1);
    expect(journey.reconciliation.map((item) => item.stage)).toEqual([
      "Claims",
      "Remittance",
      "Appeals",
      "Settlements",
    ]);
  });
});
