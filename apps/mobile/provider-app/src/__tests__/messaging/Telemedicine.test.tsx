/**
 * Telemedicine Tests — Session lifecycle verification.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
}));

import {
  endProviderTelemedicineSession,
  joinProviderTelemedicineSession,
  listProviderTelemedicineSessions,
} from "../../services/telemedicineService";

describe("Telemedicine Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists scheduled sessions", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "session-1",
            status: "SCHEDULED",
            patient_id: "pat-1",
            provider_id: "prov-1",
            encounter_id: "enc-1",
            scheduled_at: "2026-03-15T15:00:00Z",
          },
        ],
      },
    });

    const response = await listProviderTelemedicineSessions({ facilityId: "fac-1", providerId: "prov-1" });
    expect(response).toHaveLength(1);
    expect(response[0].status).toBe("SCHEDULED");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/mobile/provider/telemedicine/sessions?facility_id=fac-1&provider_id=prov-1&page=0&size=50");
  });

  it("joins a session and receives token", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          id: "session-1",
          status: "IN_PROGRESS",
          encounter_id: "enc-1",
          patient_id: "pat-1",
          provider_id: "prov-1",
          scheduled_at: "2026-03-15T15:00:00Z",
          started_at: "2026-03-15T15:02:00Z",
          accessToken: "jwt-session-token-xyz",
          channel: "rtc:channel-abc",
          roomUrl: "https://livekit.example/rooms/session-1",
        },
      },
    });

    const response = await joinProviderTelemedicineSession("session-1");
    expect(response.status).toBe("IN_PROGRESS");
    expect(response.sessionToken).toBe("jwt-session-token-xyz");
    expect(response.channelId).toBe("rtc:channel-abc");
    expect(response.roomUrl).toBe("https://livekit.example/rooms/session-1");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/mobile/provider/telemedicine/sessions/session-1/join");
  });

  it("ends a session", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { id: "session-1", attributes: { status: "COMPLETED" } } } });

    await endProviderTelemedicineSession("session-1");
    expect(mockApiClient.post).toHaveBeenCalledWith("/internal/v1/mobile/provider/telemedicine/sessions/session-1/end", undefined);
  });
});
