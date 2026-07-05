import { describe, expect, it } from "vitest";
import {
  isJoinableLiveSession,
  isUpcomingSession,
  resolveClassroomRole,
  resolveJoinPath,
  resolveLiveEventId,
  resolveSessionMode,
} from "./live-session";

describe("resolveSessionMode", () => {
  it("prefers the first-class W3 sessionMode field", () => {
    expect(resolveSessionMode({ sessionMode: "HYBRID", sessionType: "VIRTUAL" })).toBe("HYBRID");
    expect(resolveSessionMode({ sessionMode: "in_person" })).toBe("IN_PERSON");
  });

  it("derives from legacy sessionType text", () => {
    expect(resolveSessionMode({ sessionType: "VIRTUAL" })).toBe("LIVE");
    expect(resolveSessionMode({ sessionType: "WEBINAR_CPD" })).toBe("LIVE");
    expect(resolveSessionMode({ sessionType: "HYBRID_WORKSHOP" })).toBe("HYBRID");
    expect(resolveSessionMode({ sessionType: "IN_PERSON" })).toBe("IN_PERSON");
    expect(resolveSessionMode({ sessionType: "SELF_PACED" })).toBe("RECORDED");
  });

  it("falls back to a linked live event for unknown types", () => {
    expect(resolveSessionMode({ sessionType: "OTHER", metadata: { impiloLiveEventId: "evt-1" } })).toBe("LIVE");
    expect(resolveSessionMode({ sessionType: "OTHER" })).toBeUndefined();
  });
});

describe("resolveLiveEventId / resolveJoinPath", () => {
  it("prefers first-class fields over legacy metadata", () => {
    const session = {
      liveEventId: "evt-new",
      joinPath: "/live/event/evt-new",
      metadata: { impiloLiveEventId: "evt-old", impiloLiveJoinPath: "/live/event/evt-old" },
    };
    expect(resolveLiveEventId(session)).toBe("evt-new");
    expect(resolveJoinPath(session)).toBe("/live/event/evt-new");
  });

  it("falls back to legacy metadata", () => {
    const session = { metadata: { impiloLiveEventId: "evt-old", impiloLiveJoinPath: "/live/event/evt-old" } };
    expect(resolveLiveEventId(session)).toBe("evt-old");
    expect(resolveJoinPath(session)).toBe("/live/event/evt-old");
  });
});

describe("isJoinableLiveSession", () => {
  it("requires a live-capable mode AND a linked event", () => {
    expect(isJoinableLiveSession({ sessionMode: "LIVE", liveEventId: "evt-1" })).toBe(true);
    expect(isJoinableLiveSession({ sessionMode: "HYBRID", metadata: { impiloLiveEventId: "evt-1" } })).toBe(true);
    expect(isJoinableLiveSession({ sessionMode: "LIVE" })).toBe(false);
    expect(isJoinableLiveSession({ sessionMode: "IN_PERSON", liveEventId: "evt-1" })).toBe(false);
  });
});

describe("isUpcomingSession", () => {
  const now = new Date("2026-07-05T12:00:00Z");
  it("uses endsAt when present, else startsAt", () => {
    expect(isUpcomingSession({ startsAt: "2026-07-05T10:00:00Z", endsAt: "2026-07-05T13:00:00Z" }, now)).toBe(true);
    expect(isUpcomingSession({ startsAt: "2026-07-05T10:00:00Z", endsAt: "2026-07-05T11:00:00Z" }, now)).toBe(false);
    expect(isUpcomingSession({ startsAt: "2026-07-06T10:00:00Z" }, now)).toBe(true);
    expect(isUpcomingSession({ startsAt: "2026-07-01T10:00:00Z" }, now)).toBe(false);
  });
});

describe("resolveClassroomRole", () => {
  it("matches the legacy free-text facilitator column", () => {
    expect(resolveClassroomRole({ facilitator: "PROV-1" }, { providerId: "PROV-1" })).toBe("facilitator");
    expect(resolveClassroomRole({ facilitator: "PROV-1" }, { providerId: "PROV-2" })).toBe("learner");
  });

  it("matches the structured facilitators[] list", () => {
    const session = { facilitators: [{ subjectId: "PROV-9", displayName: "Dr M" }] };
    expect(resolveClassroomRole(session, { subjectId: "PROV-9" })).toBe("facilitator");
    expect(resolveClassroomRole(session, { subjectId: "PROV-8" })).toBe("learner");
  });

  it("resolves facilitatorId through the facilitator directory", () => {
    const session = { facilitatorId: "fac-1" };
    const directory = [{ id: "fac-1", subjectId: "PROV-5" }];
    expect(resolveClassroomRole(session, { providerId: "PROV-5" }, directory)).toBe("facilitator");
    expect(resolveClassroomRole(session, { providerId: "PROV-6" }, directory)).toBe("learner");
  });

  it("is learner when the user has no identifiers", () => {
    expect(resolveClassroomRole({ facilitator: "PROV-1" }, {})).toBe("learner");
  });
});
