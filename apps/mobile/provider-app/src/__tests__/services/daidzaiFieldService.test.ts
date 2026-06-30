/**
 * Provider Daidzai field-mission service tests — incoming dispatch, accept/advance,
 * timeline, hand-over (all against the Daidzai BFF; no record duplication).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient }));

import { listIncidents, recordMissionStatus, fetchMissions, recordHandover } from "../../services/daidzaiFieldService";

describe("daidzaiFieldService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("lists incoming incidents", async () => {
    mockApiClient.get.mockResolvedValue({ data: [{ id: "inc-1", triageCategory: "RED" }] });
    const list = await listIncidents();
    expect(list).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/daidzai/incidents");
  });

  it("records a mission status (accept/advance)", async () => {
    mockApiClient.post.mockResolvedValue({ data: { id: "m1", status: "RESPONDING" } });
    const ev = await recordMissionStatus("inc-1", "RESPONDING", "en route");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/daidzai/incidents/inc-1/mission-events",
      { status: "RESPONDING", note: "en route" }
    );
    expect(ev.status).toBe("RESPONDING");
  });

  it("reads the mission timeline", async () => {
    mockApiClient.get.mockResolvedValue({ data: [{ id: "m1", status: "ON_SCENE" }] });
    expect(await fetchMissions("inc-1")).toHaveLength(1);
  });

  it("records the clinical hand-over as a PCT-encounter link, not a new record", async () => {
    mockApiClient.post.mockResolvedValue({ data: { id: "inc-1", status: "HANDOVER" } });
    const r = await recordHandover("inc-1", "PCT-ENC-9");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/daidzai/incidents/inc-1/handoff",
      { pctEncounterRef: "PCT-ENC-9" }
    );
    expect(r.status).toBe("HANDOVER");
  });
});
