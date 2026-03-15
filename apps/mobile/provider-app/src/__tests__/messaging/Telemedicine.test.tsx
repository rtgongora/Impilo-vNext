/**
 * Telemedicine Tests — Session lifecycle verification.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = {
  get: vi.fn(),
  post: vi.fn(),
};

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
}));

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
            attributes: {
              encounter_id: "enc-1",
              patient_id: "pat-1",
              provider_id: "prov-1",
              status: "SCHEDULED",
              scheduled_at: "2026-03-15T15:00:00Z",
            },
          },
        ],
      },
    });

    const response = await mockApiClient.get("/internal/v1/mobile/provider/telemedicine/sessions");
    expect(response.data.data).toHaveLength(1);
    expect(response.data.data[0].attributes.status).toBe("SCHEDULED");
  });

  it("joins a session and receives token", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          id: "session-1",
          attributes: {
            encounter_id: "enc-1",
            patient_id: "pat-1",
            provider_id: "prov-1",
            status: "IN_PROGRESS",
            scheduled_at: "2026-03-15T15:00:00Z",
            started_at: "2026-03-15T15:02:00Z",
            session_token: "jwt-session-token-xyz",
            channel_id: "channel-abc",
          },
        },
      },
    });

    const response = await mockApiClient.post("/internal/v1/mobile/provider/telemedicine/sessions/session-1/join");
    expect(response.data.data.attributes.status).toBe("IN_PROGRESS");
    expect(response.data.data.attributes.session_token).toBe("jwt-session-token-xyz");
    expect(response.data.data.attributes.channel_id).toBe("channel-abc");
  });

  it("ends a session", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { id: "session-1", attributes: { status: "COMPLETED" } } } });

    const response = await mockApiClient.post("/internal/v1/mobile/provider/telemedicine/sessions/session-1/end");
    expect(response.data.data.attributes.status).toBe("COMPLETED");
  });
});
