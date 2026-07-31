/**
 * Citizen emergency (Daidzai) service tests — one-tap SOS + tracking against the BFF.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const mockPublicApiClient = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
const mockAuthStore = vi.hoisted(() => ({ getState: vi.fn(() => ({ isAuthenticated: true })) }));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
  publicApiClient: mockPublicApiClient,
}));
vi.mock("@impilo/mobile-auth", () => ({ authStore: mockAuthStore }));

import { createSos, fetchRequest, fetchMissions, fetchRequestByReference } from "./emergencyService";

describe("emergencyService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthStore.getState.mockReturnValue({ isAuthenticated: true });
  });

  it("raises a one-tap SOS for self via the daidzai BFF endpoint when authenticated", async () => {
    mockApiClient.post.mockResolvedValue({ data: { id: "r1", requestReference: "SOS-1" } });
    const r = await createSos({ forSelf: true, emergencyCategory: "CARDIAC", severity: "CRITICAL" });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/daidzai/requests",
      expect.objectContaining({ requesterType: "CITIZEN", channel: "MOBILE", emergencyCategory: "CARDIAC" })
    );
    expect(mockPublicApiClient.post).not.toHaveBeenCalled();
    expect(r.requestReference).toBe("SOS-1");
  });

  it("raises guest SOS via public gateway with callback number", async () => {
    mockAuthStore.getState.mockReturnValue({ isAuthenticated: false });
    mockPublicApiClient.post.mockResolvedValue({ data: { requestReference: "SOS-GUEST", message: "We will call back" } });
    const r = await createSos({
      forSelf: true,
      emergencyCategory: "MEDICAL",
      severity: "CRITICAL",
      callbackNumber: "+263771234567",
    });
    expect(mockPublicApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/public/gateway/sos",
      expect.objectContaining({ callbackNumber: "+263771234567", emergencyCategory: "MEDICAL" })
    );
    expect(mockApiClient.post).not.toHaveBeenCalled();
    expect(r.requestReference).toBe("SOS-GUEST");
  });

  it("requires callback number for guest SOS", async () => {
    mockAuthStore.getState.mockReturnValue({ isAuthenticated: false });
    await expect(createSos({ forSelf: true, emergencyCategory: "MEDICAL", severity: "CRITICAL" })).rejects.toThrow(
      /phone number/i
    );
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

  it("forwards lat/lng when a GPS fix is captured", async () => {
    mockApiClient.post.mockResolvedValue({ data: { id: "r3" } });
    await createSos({ forSelf: true, emergencyCategory: "MEDICAL", severity: "CRITICAL", lat: -17.8, lng: 31.05 });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/daidzai/requests",
      expect.objectContaining({ lat: -17.8, lng: 31.05 }),
    );
  });

  it("tracks by human reference over the PUBLIC gateway lane (PII-free status)", async () => {
    mockPublicApiClient.get.mockResolvedValue({
      data: { data: { attributes: { requestReference: "SOS-1A2B3C", status: "RESPONDING", emergencyCategory: "MEDICAL" } } },
    });
    const s = await fetchRequestByReference(" SOS-1A2B3C ");
    expect(mockPublicApiClient.get).toHaveBeenCalledWith("/internal/v1/public/gateway/sos/SOS-1A2B3C");
    expect(s).toMatchObject({ requestReference: "SOS-1A2B3C", status: "RESPONDING", emergencyCategory: "MEDICAL" });
    expect(mockApiClient.get).not.toHaveBeenCalled();
  });
});
