/**
 * Learning Sessions Service Tests — W3 LEARNING_LIVE normalization
 * (sessionMode/liveEventId/joinPath incl. metadata fallback), the live-room
 * join → token choreography, and room-token payload normalization.
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
    getState: () => ({ session: { actorId: "cit-actor-1" } }),
  },
}));

import {
  normalizeLearningSession,
  normalizeClassroomMediaPayload,
  isJoinableLiveSession,
  listSessions,
  getSession,
  joinLiveClassroom,
} from "../../services/learningSessionsService";

describe("learningSessionsService normalization", () => {
  it("maps W3 top-level sessionMode/liveEventId/joinPath", () => {
    const session = normalizeLearningSession({
      id: "ses-1",
      title: "Diabetes basics — live class",
      sessionType: "VIRTUAL",
      sessionMode: "LIVE",
      liveEventId: "evt-9",
      joinPath: "/live/event/evt-9",
      startsAt: "2026-07-10T09:00:00Z",
    });
    expect(session.sessionMode).toBe("LIVE");
    expect(session.liveEventId).toBe("evt-9");
    expect(session.joinPath).toBe("/live/event/evt-9");
    expect(isJoinableLiveSession(session)).toBe(true);
  });

  it("falls back to metadata.impiloLiveEventId / impiloLiveJoinPath and derives LIVE mode", () => {
    const session = normalizeLearningSession({
      id: "ses-2",
      title: "CHW refresher",
      sessionType: "VIRTUAL",
      metadata: {
        impiloLiveEventId: "evt-meta-4",
        impiloLiveJoinPath: "/live/event/evt-meta-4",
        objectives: ["Recognize danger signs", "Referral pathways"],
      },
    });
    expect(session.sessionMode).toBe("LIVE");
    expect(session.liveEventId).toBe("evt-meta-4");
    expect(session.joinPath).toBe("/live/event/evt-meta-4");
    expect(session.objectives).toEqual(["Recognize danger signs", "Referral pathways"]);
  });

  it("marks sessions without a live event as SCHEDULED and not joinable", () => {
    const session = normalizeLearningSession({
      id: "ses-3",
      title: "In-person workshop",
      sessionType: "IN_PERSON",
      locationRef: "Harare Central Training Room",
    });
    expect(session.sessionMode).toBe("SCHEDULED");
    expect(session.liveEventId).toBeNull();
    expect(isJoinableLiveSession(session)).toBe(false);
  });

  it("normalizes the live room-token envelope in camelCase and snake_case shapes", () => {
    expect(
      normalizeClassroomMediaPayload({
        data: { roomId: "room-1", roomUrl: "wss://rtc.impilo.zw", accessToken: "jwt-a", provider: "LIVEKIT", channel: "chan-1" },
      }),
    ).toEqual({ serverUrl: "wss://rtc.impilo.zw", token: "jwt-a", roomId: "room-1", provider: "LIVEKIT", channel: "chan-1" });

    expect(
      normalizeClassroomMediaPayload({ room_url: "wss://rtc.impilo.zw", access_token: "jwt-b" }),
    ).toMatchObject({ serverUrl: "wss://rtc.impilo.zw", token: "jwt-b" });

    expect(normalizeClassroomMediaPayload({ data: { status: "PENDING" } })).toBeNull();
    expect(normalizeClassroomMediaPayload(null)).toBeNull();
  });
});

describe("learningSessionsService API calls", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("lists sessions from the learning v11 endpoint and normalizes rows", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          items: [
            { id: "ses-1", title: "Live class", sessionType: "VIRTUAL", metadata: { impiloLiveEventId: "evt-1" } },
            { title: "row without id is dropped" },
          ],
        },
      },
    });

    const sessions = await listSessions();
    expect(mockApiClient.get).toHaveBeenCalledWith("/internal/v1/learning/v11/sessions?limit=50");
    expect(sessions).toHaveLength(1);
    expect(sessions[0].liveEventId).toBe("evt-1");
  });

  it("getSession falls back to the list when the per-session route is unavailable", async () => {
    mockApiClient.get
      .mockRejectedValueOnce(new Error("404"))
      .mockResolvedValueOnce({
        data: { data: { items: [{ id: "ses-7", title: "Fallback session", sessionType: "VIRTUAL" }] } },
      });

    const session = await getSession("ses-7");
    expect(mockApiClient.get).toHaveBeenNthCalledWith(1, "/internal/v1/learning/v11/sessions/ses-7");
    expect(session?.title).toBe("Fallback session");
  });

  it("joins the live classroom: room join (attendance) then scoped token", async () => {
    mockApiClient.post
      .mockResolvedValueOnce({ data: { data: { sessionId: "media-ses-1" } } })
      .mockResolvedValueOnce({
        data: { data: { roomId: "room-1", roomUrl: "wss://rtc.impilo.zw", accessToken: "jwt-class" } },
      });

    const credentials = await joinLiveClassroom("evt-9");

    expect(mockApiClient.post).toHaveBeenNthCalledWith(1, "/internal/v1/live/room/evt-9/join", {
      participantId: "cit-actor-1",
      participantType: "CITIZEN",
      role: "ATTENDEE",
      consentGranted: true,
    });
    expect(mockApiClient.post).toHaveBeenNthCalledWith(2, "/internal/v1/live/room/evt-9/token", {
      participantId: "cit-actor-1",
      role: "ATTENDEE",
    });
    expect(credentials).toMatchObject({ serverUrl: "wss://rtc.impilo.zw", token: "jwt-class" });
  });

  it("throws an honest error when the token route returns no credentials", async () => {
    mockApiClient.post
      .mockResolvedValueOnce({ data: { data: {} } })
      .mockResolvedValueOnce({ data: { data: { status: "PENDING" } } });

    await expect(joinLiveClassroom("evt-9")).rejects.toThrow(/not ready yet/i);
  });
});
