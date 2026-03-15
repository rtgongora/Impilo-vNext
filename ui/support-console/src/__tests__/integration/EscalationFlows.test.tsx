/**
 * Escalation Flow Tests — Ticket escalation and incident filtering.
 *
 * Validates that escalation API calls construct correct URLs and payloads,
 * and that incident-category filtering works as expected.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = {
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
};

vi.mock("@/lib/apiClient", () => ({
  apiClient: mockApiClient,
}));

import {
  escalateTicket,
  listTickets,
} from "../../lib/supportApi";

describe("Escalation Flows — API Contract Compliance", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Escalate ticket", () => {
    it("sends POST to /tickets/{id}/escalate", async () => {
      mockApiClient.post.mockResolvedValue({
        ticketId: "tkt-500",
        status: "OPEN",
        priority: "CRITICAL",
        escalationLevel: 2,
        escalatedBy: "lead-1",
        escalatedAt: "2026-03-15T14:00:00Z",
      });

      const ticket = await escalateTicket("tkt-500", "lead-1", 2);

      expect(ticket.ticketId).toBe("tkt-500");
      expect(ticket.escalationLevel).toBe(2);
      expect(ticket.escalatedBy).toBe("lead-1");
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-500/escalate",
        { escalatedBy: "lead-1", targetLevel: 2 }
      );
    });

    it("includes correct escalatedBy and targetLevel in payload", async () => {
      mockApiClient.post.mockResolvedValue({
        ticketId: "tkt-501",
        escalationLevel: 3,
        escalatedBy: "coord-9",
        escalatedAt: "2026-03-15T15:00:00Z",
      });

      await escalateTicket("tkt-501", "coord-9", 3);

      const [url, body] = mockApiClient.post.mock.calls[0];
      expect(url).toBe("/internal/v1/support/tickets/tkt-501/escalate");
      expect(body).toEqual({ escalatedBy: "coord-9", targetLevel: 3 });
      expect(body.escalatedBy).toBe("coord-9");
      expect(body.targetLevel).toBe(3);
    });

    it("escalates from level 1 to level 2", async () => {
      mockApiClient.post.mockResolvedValue({
        ticketId: "tkt-502",
        escalationLevel: 2,
        escalatedBy: "agent-15",
        escalatedAt: "2026-03-15T16:00:00Z",
      });

      const result = await escalateTicket("tkt-502", "agent-15", 2);
      expect(result.escalationLevel).toBe(2);
      expect(mockApiClient.post).toHaveBeenCalledTimes(1);
    });
  });

  describe("Incident category filter", () => {
    it("lists tickets with INCIDENT category filter", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [
          {
            ticketId: "tkt-inc-1",
            title: "System outage in Harare region",
            category: "INCIDENT",
            priority: "CRITICAL",
            status: "OPEN",
            escalationLevel: 1,
          },
          {
            ticketId: "tkt-inc-2",
            title: "Database connectivity lost",
            category: "INCIDENT",
            priority: "HIGH",
            status: "IN_PROGRESS",
            escalationLevel: 2,
          },
        ],
        cursor: null,
        limit: 20,
        total_elements: 2,
        has_more: false,
      });

      const result = await listTickets({ category: "INCIDENT" });

      expect(result.items).toHaveLength(2);
      expect(result.items[0].category).toBe("INCIDENT");
      expect(result.items[1].category).toBe("INCIDENT");
      expect(result.total_elements).toBe(2);
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets?category=INCIDENT&cursor=0&limit=20"
      );
    });

    it("returns empty list when no INCIDENT tickets exist", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [],
        cursor: null,
        limit: 20,
        total_elements: 0,
        has_more: false,
      });

      const result = await listTickets({ category: "INCIDENT" });

      expect(result.items).toHaveLength(0);
      expect(result.total_elements).toBe(0);
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets?category=INCIDENT&cursor=0&limit=20"
      );
    });
  });
});
