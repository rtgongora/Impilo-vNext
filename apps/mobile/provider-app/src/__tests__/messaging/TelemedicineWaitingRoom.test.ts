/**
 * Telemedicine Waiting Room Tests — governed waiting-room + media-token
 * service calls over the shared /internal/v1/teleconsult contract.
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
  fetchTelemedicineWaitingRoom,
  admitTelemedicineParticipant,
  denyTelemedicineParticipant,
  requestProviderTelemedicineMediaToken,
  refreshProviderTelemedicineMediaToken,
  normalizeMediaTokenPayload,
} from "../../services/telemedicineService";

describe("Telemedicine waiting room service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches the waiting room from the shared teleconsult route", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          { identity: "cpid-1", displayName: "Thandi M", waiting_since: "2026-07-05T08:00:00Z" },
        ],
      },
    });

    const participants = await fetchTelemedicineWaitingRoom("session-1");
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/teleconsult/sessions/session-1/waiting-room"
    );
    expect(participants).toEqual([
      { identity: "cpid-1", displayName: "Thandi M", waitingSince: "2026-07-05T08:00:00Z" },
    ]);
  });

  it("parses the BFF waiting envelope ({sessionId, waiting: [...]})", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          sessionId: "session-2",
          waiting: [
            { identity: "cpid-2", displayName: "Sipho D", role: "PATIENT", state: "WAITING", requestedAt: "2026-07-05T08:59:00Z" },
          ],
        },
      },
    });

    const participants = await fetchTelemedicineWaitingRoom("session-2");
    expect(participants).toEqual([
      { identity: "cpid-2", displayName: "Sipho D", waitingSince: "2026-07-05T08:59:00Z" },
    ]);
  });

  it("tolerates a participants-envelope waiting room shape", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          participants: [{ participant_id: "cpid-9", name: "Busi N" }],
        },
      },
    });

    const participants = await fetchTelemedicineWaitingRoom("session-2");
    expect(participants).toEqual([
      { identity: "cpid-9", displayName: "Busi N", waitingSince: undefined },
    ]);
  });

  it("admits a participant with the identity body", async () => {
    mockApiClient.post.mockResolvedValue({ data: {} });

    await admitTelemedicineParticipant("session-1", "cpid-1");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/teleconsult/sessions/session-1/admit",
      { identity: "cpid-1" }
    );
  });

  it("denies a participant with identity and optional reason", async () => {
    mockApiClient.post.mockResolvedValue({ data: {} });

    await denyTelemedicineParticipant("session-1", "cpid-1", "Wrong session");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/teleconsult/sessions/session-1/deny",
      { identity: "cpid-1", reason: "Wrong session" }
    );

    await denyTelemedicineParticipant("session-1", "cpid-2");
    expect(mockApiClient.post).toHaveBeenLastCalledWith(
      "/internal/v1/teleconsult/sessions/session-1/deny",
      { identity: "cpid-2" }
    );
  });
});

describe("Provider media token service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("requests a provider media token with role and mediaProfile", async () => {
    mockApiClient.post.mockResolvedValue({
      data: { data: { room_url: "wss://rtc.impilo.zw", token: "jwt-abc", status: "READY" } },
    });

    const result = await requestProviderTelemedicineMediaToken("session-1", {
      displayName: "Dr. Moyo",
      mediaProfile: "AUDIO_ONLY",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/teleconsult/sessions/session-1/media/token",
      { displayName: "Dr. Moyo", role: "PROVIDER", mediaProfile: "AUDIO_ONLY" }
    );
    expect(result).toEqual({ status: "READY", roomUrl: "wss://rtc.impilo.zw", token: "jwt-abc" });
  });

  it("refreshes a provider media token on the refresh route", async () => {
    mockApiClient.post.mockResolvedValue({
      data: { data: { roomUrl: "wss://rtc.impilo.zw", accessToken: "jwt-next" } },
    });

    const result = await refreshProviderTelemedicineMediaToken("session-1", {
      displayName: "Dr. Moyo",
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/teleconsult/sessions/session-1/media/token/refresh",
      { displayName: "Dr. Moyo", role: "PROVIDER" }
    );
    expect(result).toEqual({ status: "READY", roomUrl: "wss://rtc.impilo.zw", token: "jwt-next" });
  });

  it("normalizes WAITING / DENIED / empty payloads", () => {
    expect(normalizeMediaTokenPayload({ data: { status: "WAITING" } })).toEqual({ status: "WAITING" });
    expect(normalizeMediaTokenPayload({ data: { status: "DENIED", reason: "No consent" } })).toEqual({
      status: "DENIED",
      reason: "No consent",
    });
    // No decision and no credentials → keep polling.
    expect(normalizeMediaTokenPayload({ data: {} })).toEqual({ status: "WAITING" });
    expect(normalizeMediaTokenPayload(undefined)).toEqual({ status: "WAITING" });
  });
});
