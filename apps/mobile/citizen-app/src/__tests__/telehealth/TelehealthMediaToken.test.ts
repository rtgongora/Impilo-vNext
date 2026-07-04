/**
 * Telehealth Media Token Tests — token body {role, mediaProfile}, refresh
 * route, and WAITING/DENIED status-shape handling on the shared
 * /internal/v1/teleconsult contract.
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
  requestSessionMediaToken,
  refreshSessionMediaToken,
  normalizeMediaTokenPayload,
} from "../../services/telehealthService";

describe("Telehealth media token service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("requests a media token with role and mediaProfile on the shared teleconsult route", async () => {
    mockApiClient.post.mockResolvedValue({
      data: { data: { room_url: "wss://rtc.impilo.zw", token: "jwt-abc" } },
    });

    const result = await requestSessionMediaToken("ses-1", {
      displayName: "Thandi Moyo",
      role: "PATIENT",
      mediaProfile: "AUDIO_ONLY",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/teleconsult/sessions/ses-1/media/token",
      { displayName: "Thandi Moyo", role: "PATIENT", mediaProfile: "AUDIO_ONLY" }
    );
    expect(result).toEqual({ status: "READY", roomUrl: "wss://rtc.impilo.zw", token: "jwt-abc" });
  });

  it("omits mediaProfile from the body when not set", async () => {
    mockApiClient.post.mockResolvedValue({ data: { data: { status: "WAITING" } } });

    const result = await requestSessionMediaToken("ses-1", {
      displayName: "Thandi Moyo",
      role: "PATIENT",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/teleconsult/sessions/ses-1/media/token",
      { displayName: "Thandi Moyo", role: "PATIENT" }
    );
    expect(result).toEqual({ status: "WAITING" });
  });

  it("refreshes a media token on the refresh route", async () => {
    mockApiClient.post.mockResolvedValue({
      data: { data: { roomUrl: "wss://rtc.impilo.zw", accessToken: "jwt-next" } },
    });

    const result = await refreshSessionMediaToken("ses-1", { displayName: "Thandi Moyo", role: "PATIENT" });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/teleconsult/sessions/ses-1/media/token/refresh",
      { displayName: "Thandi Moyo", role: "PATIENT" }
    );
    expect(result).toEqual({ status: "READY", roomUrl: "wss://rtc.impilo.zw", token: "jwt-next" });
  });

  it("normalizes WAITING / DENIED / empty payloads", () => {
    expect(normalizeMediaTokenPayload({ data: { status: "WAITING" } })).toEqual({ status: "WAITING" });
    expect(normalizeMediaTokenPayload({ data: { status: "DENIED", reason: "Not admitted" } })).toEqual({
      status: "DENIED",
      reason: "Not admitted",
    });
    // No decision and no credentials → keep polling.
    expect(normalizeMediaTokenPayload({ data: {} })).toEqual({ status: "WAITING" });
    expect(normalizeMediaTokenPayload(undefined)).toEqual({ status: "WAITING" });
  });
});
