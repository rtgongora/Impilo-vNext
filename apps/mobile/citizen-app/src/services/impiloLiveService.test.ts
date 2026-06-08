import { describe, expect, it, vi, beforeEach } from "vitest";

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
  },
}));

vi.mock("@impilo/mobile-auth", () => ({
  authStore: {
    getState: () => ({ session: { actorId: "CPID-DEMO-001" } }),
  },
}));

import {
  discoverEvents,
  registerForEvent,
  listMyRegistrations,
  listReplays,
} from "./impiloLiveService";

const BASE = "/internal/v1/mobile/citizen/live";

describe("citizen impiloLiveService", () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  it("discoverEvents reads mobile discover endpoint", async () => {
    mockGet.mockResolvedValue({
      data: {
        data: [
          {
            id: "evt-1",
            title: "Maternal Health Talk",
            eventType: "PUBLIC_HEALTH_EDUCATION",
            status: "SCHEDULED",
          },
        ],
      },
    });

    const events = await discoverEvents();

    expect(mockGet).toHaveBeenCalledWith(`${BASE}/discover?context_type=CITIZEN`);
    expect(events).toHaveLength(1);
    expect(events[0]?.title).toBe("Maternal Health Talk");
  });

  it("registerForEvent posts to mobile register endpoint", async () => {
    mockPost.mockResolvedValue({
      data: { data: { id: "reg-1", eventId: "evt-1", participantId: "CPID-DEMO-001", registrationStatus: "REGISTERED" } },
    });

    const reg = await registerForEvent("evt-1");

    expect(mockPost).toHaveBeenCalledWith(`${BASE}/events/evt-1/register`, {
      inviteSource: "MOBILE_DISCOVER",
    });
    expect(reg?.registrationStatus).toBe("REGISTERED");
  });

  it("listMyRegistrations reads participant registrations", async () => {
    mockGet.mockResolvedValue({
      data: { data: [{ id: "reg-2", eventId: "evt-2", participantId: "CPID-DEMO-001", registrationStatus: "REGISTERED" }] },
    });

    const regs = await listMyRegistrations();

    expect(mockGet).toHaveBeenCalledWith(
      "/internal/v1/live/registrations/participants/CPID-DEMO-001",
    );
    expect(regs).toHaveLength(1);
  });

  it("listReplays reads ended events", async () => {
    mockGet.mockResolvedValue({
      data: { data: [{ id: "evt-3", title: "Replay", status: "ENDED", eventType: "WEBINAR" }] },
    });

    const replays = await listReplays();

    expect(mockGet).toHaveBeenCalledWith("/internal/v1/live/events?status=ENDED");
    expect(replays[0]?.status).toBe("ENDED");
  });
});
