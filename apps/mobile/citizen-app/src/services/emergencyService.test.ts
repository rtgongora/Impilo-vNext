/**
 * Citizen emergency (Daidzai) service tests — one-tap SOS + tracking against the BFF.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mockApiClient }));

import { createSos, fetchRequest, fetchMissions } from "./emergencyService";

describe("emergencyService", () => {
  beforeEach(() => vi.clearAllMocks());

  it("raises a one-tap SOS for self via the daidzai BFF endpoint", async () => {
    mockApiClient.post.mockResolvedValue({ data: { id: "r1", requestReference: "SOS-1" } });
    const r = await createSos({ forSelf: true, emergencyCategory: "CARDIAC", severity: "CRITICAL" });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/daidzai/requests",
      expect.objectContaining({ requesterType: "CITIZEN", channel: "MOBILE", emergencyCategory: "CARDIAC" })
    );
    expect(r.requestReference).toBe("SOS-1");
  });

  it("raises a caregiver SOS for someone else (unknown subject)", async () => {
    mockApiClient.post.mockResolvedValue({ data: { id: "r2" } });
    await createSos({ forSelf: false, emergencyCategory: "TRAUMA", severity: "HIGH" });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/daidzai/requests",
      expect.objectContaining({ requesterType: "CAREGIVER", subjectIdentityMode: "UNKNOWN" })
    );
  });

  it("reads request status", async () => {
    mockApiClient.get.mockResolvedValue({ data: { id: "r1", status: "RESPONDING" } });
    expect((await fetchRequest("r1")).status).toBe("RESPONDING");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/daidzai/requests/r1");
  });

  it("reads the mission timeline of the linked incident", async () => {
    mockApiClient.get.mockResolvedValue({ data: [{ id: "m1", status: "ON_SCENE" }] });
    const m = await fetchMissions("inc-1");
    expect(m).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/daidzai/incidents/inc-1/missions");
  });
});
