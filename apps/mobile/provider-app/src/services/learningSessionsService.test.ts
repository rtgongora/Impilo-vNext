/**
 * Learning Sessions Service Tests (provider) — W3 LEARNING_LIVE normalization,
 * facilitator (PRESENTER) live-room join → token choreography, and the
 * session-attendance facilitator affordance.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
}));

vi.mock("@impilo/mobile-auth", () => ({
  authStore: {
    getState: () => ({ session: { actorId: "prov-actor-1" } }),
  },
}));

import {
  normalizeLearningSession,
  normalizeClassroomMediaPayload,
  isJoinableLiveSession,
  listSessions,
  fetchSessionAttendance,
  joinLiveClassroom,
} from "./learningSessionsService";

describe("learningSessionsService normalization (provider)", () => {
  it("maps W3 top-level fields and metadata fallbacks", () => {
    const w3 = normalizeLearningSession({
      id: "ses-1",
      title: "ETAT refresher",
      sessionType: "VIRTUAL",
      sessionMode: "LIVE",
      liveEventId: "evt-1",
      joinPath: "/live/event/evt-1",
    });
    expect(w3.sessionMode).toBe("LIVE");
    expect(isJoinableLiveSession(w3)).toBe(true);

    const legacy = normalizeLearningSession({
      id: "ses-2",
      title: "Legacy live row",
      sessionType: "WEBINAR",
      metadata: { impiloLiveEventId: "evt-2", impiloLiveJoinPath: "/live/event/evt-2" },
    });
    expect(legacy.sessionMode).toBe("LIVE");
    expect(legacy.liveEventId).toBe("evt-2");

    const inPerson = normalizeLearningSession({ id: "ses-3", title: "Workshop", sessionType: "IN_PERSON" });
    expect(inPerson.sessionMode).toBe("SCHEDULED");
    expect(isJoinableLiveSession(inPerson)).toBe(false);
  });

  it("normalizes room-token payloads and rejects credential-less shapes", () => {
    expect(
      normalizeClassroomMediaPayload({
        data: { roomUrl: "wss://rtc.impilo.zw", accessToken: "jwt-a", channel: "chan-9" },
      }),
    ).toMatchObject({ serverUrl: "wss://rtc.impilo.zw", token: "jwt-a", channel: "chan-9" });
    expect(normalizeClassroomMediaPayload({ data: {} })).toBeNull();
  });
});

describe("learningSessionsService API calls (provider)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists sessions from the learning v11 endpoint", async () => {
    mockApiClient.get.mockResolvedValue({
      data: { data: { items: [{ id: "ses-1", title: "Live class", sessionType: "VIRTUAL" }] } },
    });
    const sessions = await listSessions();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/learning/v11/sessions?limit=50");
    expect(sessions).toHaveLength(1);
  });

  it("reads the attendance summary from the shared learning attendance route", async () => {
    mockApiClient.get.mockResolvedValue({
      data: { data: { items: [{ subjectId: "a" }, { subjectId: "b" }, { subjectId: "c" }] } },
    });
    const attendance = await fetchSessionAttendance("ses-1");
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/learning/v11/sessions/ses-1/attendance");
    expect(attendance.count).toBe(3);
  });

  it("joins as PRESENTER when facilitating: room join then scoped token", async () => {
    mockApiClient.post
      .mockResolvedValueOnce({ data: { data: { sessionId: "media-ses-1" } } })
      .mockResolvedValueOnce({
        data: { data: { roomUrl: "wss://rtc.impilo.zw", accessToken: "jwt-facilitator" } },
      });

    const credentials = await joinLiveClassroom("evt-1", { asFacilitator: true });

    expect(mockApiClient.post).toHaveBeenNthCalledWith(1, "/internal/v1/live/room/evt-1/join", {
      participantId: "prov-actor-1",
      participantType: "PROVIDER",
      role: "PRESENTER",
      consentGranted: true,
    });
    expect(mockApiClient.post).toHaveBeenNthCalledWith(2, "/internal/v1/live/room/evt-1/token", {
      participantId: "prov-actor-1",
      role: "PRESENTER",
    });
    expect(credentials.token).toBe("jwt-facilitator");
  });

  it("joins as ATTENDEE by default and errors honestly without credentials", async () => {
    mockApiClient.post
      .mockResolvedValueOnce({ data: { data: {} } })
      .mockResolvedValueOnce({ data: { data: {} } });

    await expect(joinLiveClassroom("evt-1")).rejects.toThrow(/not ready yet/i);
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      1,
      "/internal/v1/live/room/evt-1/join",
      expect.objectContaining({ role: "ATTENDEE" }),
    );
  });
});
