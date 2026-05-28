import { beforeEach, describe, expect, it, vi } from "vitest";
import { queueClinicalCreateOnRetryableError } from "./offlineClinicalQueue";
import { isRetryable } from "@impilo/mobile-api-client";
import { getOfflineStorage } from "@impilo/mobile-offline";

vi.mock("@impilo/mobile-trust", () => ({
  generateId: vi.fn(() => "gen-id"),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  isRetryable: vi.fn(),
}));

vi.mock("@impilo/mobile-offline", () => ({
  getOfflineStorage: vi.fn(),
}));

describe("offlineClinicalQueue", () => {
  const storage = {
    get: vi.fn(),
    put: vi.fn(),
    enqueue: vi.fn(),
  };

  beforeEach(() => {
    storage.get.mockClear();
    storage.put.mockClear();
    storage.enqueue.mockClear();
    storage.get.mockResolvedValue(null);
    storage.put.mockResolvedValue(undefined);
    storage.enqueue.mockResolvedValue(undefined);
    vi.mocked(getOfflineStorage).mockReturnValue(storage as unknown as ReturnType<typeof getOfflineStorage>);
  });

  it("queues retryable clinical writes into offline store", async () => {
    vi.mocked(isRetryable).mockReturnValue(true);
    const result = await queueClinicalCreateOnRetryableError(new Error("network"), {
      collection: "clinical_vitals",
      path: "/internal/v1/mobile/provider/vitals",
      payload: { encounter_id: "enc-1", patient_id: "pat-1", vital_type: "HEART_RATE", value: 80, unit: "bpm" },
    });
    expect(result).not.toBeNull();
    expect(result?.queued).toBe(true);
    expect(storage.put).toHaveBeenCalledTimes(1);
    expect(storage.enqueue).toHaveBeenCalledTimes(1);
  });

  it("does not queue non-retryable errors", async () => {
    vi.mocked(isRetryable).mockReturnValue(false);
    const result = await queueClinicalCreateOnRetryableError(new Error("validation"), {
      collection: "clinical_vitals",
      path: "/internal/v1/mobile/provider/vitals",
      payload: { encounter_id: "enc-1" },
    });
    expect(result).toBeNull();
    expect(storage.put).not.toHaveBeenCalled();
    expect(storage.enqueue).not.toHaveBeenCalled();
  });
});
