/**
 * Telehealth Flow Tests — Session lifecycle: request, list, join, end.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}));

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
  fetchPage: vi.fn(),
}));

import {
  fetchSessions,
  fetchSession,
  requestTeleconsult,
  joinSession,
  endSession,
} from "../../services/telehealthService";

import {
  createTicket,
  fetchTickets,
} from "../../services/supportService";

describe("Telehealth Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches telehealth sessions", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "ses-1",
            sessionType: "VIDEO",
            status: "SCHEDULED",
            providerName: "Dr. Ndlovu",
            scheduledAt: "2026-03-20T14:00:00Z",
          },
          {
            id: "ses-2",
            sessionType: "AUDIO",
            status: "COMPLETED",
            providerName: "Dr. Chikwanha",
            scheduledAt: "2026-03-10T10:00:00Z",
          },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 2, total_pages: 1 } },
      },
    });

    const result = await fetchSessions();
    expect(result.items).toHaveLength(2);
    expect(result.items[0].status).toBe("SCHEDULED");
    expect(result.items[1].status).toBe("COMPLETED");
    expect(result.totalElements).toBe(2);
  });

  it("fetches sessions with status filter", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          { id: "ses-1", sessionType: "VIDEO", status: "SCHEDULED", providerName: "Dr. Ndlovu" },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await fetchSessions({ status: "SCHEDULED" });
    expect(result.items).toHaveLength(1);
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/telehealth/sessions?status=SCHEDULED"
    );
  });

  it("fetches a single session", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "ses-1",
          sessionType: "VIDEO",
          status: "SCHEDULED",
          providerName: "Dr. Ndlovu",
          scheduledAt: "2026-03-20T14:00:00Z",
          roomUrl: "session-ses-1",
        },
      },
    });

    const session = await fetchSession("ses-1");
    expect(session.providerName).toBe("Dr. Ndlovu");
    expect(session.roomUrl).toBe("session-ses-1");
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/telehealth/sessions/ses-1"
    );
  });

  it("requests a teleconsult", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          id: "ses-3",
          sessionType: "VIDEO",
          status: "REQUESTED",
          scheduledAt: "2026-03-18T10:00:00Z",
          providerName: "Assigned Provider",
        },
      },
    });

    const session = await requestTeleconsult({
      reason: "Follow-up on lab results",
      preferredDate: "2026-03-18",
      sessionType: "VIDEO",
    });
    expect(session.status).toBe("REQUESTED");
    expect(session.sessionType).toBe("VIDEO");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/telehealth/sessions",
      { reason: "Follow-up on lab results", preferredDate: "2026-03-18", sessionType: "VIDEO" }
    );
  });

  it("joins a telehealth session and receives token/channel", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          attributes: {
            id: "ses-1",
            sessionType: "VIDEO",
            status: "IN_PROGRESS",
            providerName: "Dr. Ndlovu",
            token: "jwt-session-token-abc",
            channel: "session-ses-1",
          },
        },
      },
    });

    const result = await joinSession("ses-1");
    expect(result.token).toBe("jwt-session-token-abc");
    expect(result.channel).toBe("session-ses-1");
    expect(result.session.status).toBe("IN_PROGRESS");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/telehealth/sessions/ses-1/join"
    );
  });

  it("ends a telehealth session", async () => {
    mockApiClient.post.mockResolvedValue({});

    await endSession("ses-1", "Patient felt better");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/telehealth/sessions/ses-1/end",
      { notes: "Patient felt better" }
    );
  });

  it("ends a session without notes", async () => {
    mockApiClient.post.mockResolvedValue({});

    await endSession("ses-1");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/telehealth/sessions/ses-1/end",
      { notes: undefined }
    );
  });
});

describe("Support Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates a support ticket", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          id: "tkt-1",
          category: "BILLING",
          subject: "Payment issue",
          description: "Cannot pay for services",
          status: "OPEN",
          createdAt: "2026-03-15T12:00:00Z",
        },
      },
    });

    const ticket = await createTicket({
      category: "BILLING",
      subject: "Payment issue",
      description: "Cannot pay for services",
    });
    expect(ticket.status).toBe("OPEN");
    expect(ticket.category).toBe("BILLING");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/support/tickets",
      { category: "BILLING", subject: "Payment issue", description: "Cannot pay for services" }
    );
  });

  it("fetches support tickets", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          { id: "tkt-1", category: "BILLING", subject: "Payment issue", status: "OPEN" },
          { id: "tkt-2", category: "TECHNICAL", subject: "App crash", status: "RESOLVED" },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 2, total_pages: 1 } },
      },
    });

    const result = await fetchTickets();
    expect(result.items).toHaveLength(2);
    expect(result.totalElements).toBe(2);
  });
});
