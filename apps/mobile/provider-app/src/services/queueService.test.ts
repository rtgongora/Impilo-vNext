import { describe, expect, it, vi, beforeEach } from "vitest";
import { apiClient } from "@impilo/mobile-api-client";
import { callNext, completeEntry, fetchQueue, fetchQueueStats } from "./queueService";

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe("queueService", () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset();
    vi.mocked(apiClient.post).mockReset();
  });

  it("fetchQueue unwraps data array", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ id: "q1", patient_name: "Test" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const rows = await fetchQueue("fac-1");
    expect(rows).toEqual([{ id: "q1", patient_name: "Test" }]);
    expect(apiClient.get).toHaveBeenCalledWith("/internal/v1/queue/entries?facility_id=fac-1");
  });

  it("fetchQueueStats unwraps aggregate object", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { waiting: 2, inProgress: 1, completedToday: 5 } },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const s = await fetchQueueStats("fac-1");
    expect(s).toEqual({ waiting: 2, inProgress: 1, completedToday: 5 });
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/queue/entries/stats", { facilityId: "fac-1" });
  });

  it("callNext posts queueId", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { id: "next" } },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const out = await callNext("desk-1");
    expect(out).toEqual({ id: "next" });
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/queue/entries/desk-1/call");
  });

  it("completeEntry posts completion path", async () => {
    vi.mocked(apiClient.post).mockResolvedValue({
      data: undefined,
      status: 204,
      correlationId: "c1",
      headers: {},
    });
    await completeEntry("entry-9");
    expect(apiClient.post).toHaveBeenCalledWith("/internal/v1/queue/entries/entry-9/complete");
  });

  it("fetchQueueDefinitions maps PCT queue definitions for a facility (read-only)", async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: { data: [{ id: "q1", name: "OPD Triage", serviceType: "TRIAGE", status: "ACTIVE" }] },
      status: 200,
      correlationId: "c1",
      headers: {},
    });
    const { fetchQueueDefinitions } = await import("./queueService");
    const defs = await fetchQueueDefinitions("fac-uuid-1");
    expect(defs).toEqual([
      { id: "q1", name: "OPD Triage", serviceType: "TRIAGE", status: "ACTIVE", active: true },
    ]);
    expect(apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/queue/definitions?facility_id=fac-uuid-1",
    );
  });
});
