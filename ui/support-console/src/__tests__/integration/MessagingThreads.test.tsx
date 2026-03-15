/**
 * Messaging Thread Tests — Send and list support messages on tickets.
 *
 * Validates that the messaging API functions construct correct URLs
 * and include all required payload fields (senderRef, senderType, body).
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
  sendMessage,
  listMessages,
} from "../../lib/supportApi";

describe("Messaging Threads — API Contract Compliance", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("Send message", () => {
    it("calls correct endpoint for ticket messages", async () => {
      mockApiClient.post.mockResolvedValue({
        messageId: "msg-1",
        ticketId: "tkt-300",
        senderRef: "agent-5",
        senderType: "AGENT",
        body: "We are looking into this issue.",
        createdAt: "2026-03-15T11:00:00Z",
      });

      const msg = await sendMessage(
        "tkt-300",
        "agent-5",
        "AGENT",
        "We are looking into this issue."
      );

      expect(msg.messageId).toBe("msg-1");
      expect(msg.body).toBe("We are looking into this issue.");
      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-300/messages",
        {
          senderRef: "agent-5",
          senderType: "AGENT",
          body: "We are looking into this issue.",
        }
      );
    });

    it("message payload includes senderRef, senderType, and body", async () => {
      mockApiClient.post.mockResolvedValue({
        messageId: "msg-2",
        ticketId: "tkt-301",
        senderRef: "user-99",
        senderType: "REQUESTER",
        body: "I still cannot log in.",
        createdAt: "2026-03-15T11:30:00Z",
      });

      await sendMessage("tkt-301", "user-99", "REQUESTER", "I still cannot log in.");

      const [, payload] = mockApiClient.post.mock.calls[0];
      expect(payload).toHaveProperty("senderRef", "user-99");
      expect(payload).toHaveProperty("senderType", "REQUESTER");
      expect(payload).toHaveProperty("body", "I still cannot log in.");
    });

    it("sends SYSTEM sender type for automated messages", async () => {
      mockApiClient.post.mockResolvedValue({
        messageId: "msg-3",
        ticketId: "tkt-302",
        senderRef: "system",
        senderType: "SYSTEM",
        body: "Ticket has been escalated to level 2.",
        createdAt: "2026-03-15T12:00:00Z",
      });

      await sendMessage(
        "tkt-302",
        "system",
        "SYSTEM",
        "Ticket has been escalated to level 2."
      );

      expect(mockApiClient.post).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-302/messages",
        {
          senderRef: "system",
          senderType: "SYSTEM",
          body: "Ticket has been escalated to level 2.",
        }
      );
    });
  });

  describe("List messages", () => {
    it("calls GET on /tickets/{id}/messages", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [
          {
            messageId: "msg-1",
            ticketId: "tkt-300",
            senderRef: "agent-5",
            senderType: "AGENT",
            body: "Initial investigation started.",
            createdAt: "2026-03-15T10:00:00Z",
          },
          {
            messageId: "msg-2",
            ticketId: "tkt-300",
            senderRef: "user-99",
            senderType: "REQUESTER",
            body: "Thank you for the update.",
            createdAt: "2026-03-15T10:15:00Z",
          },
        ],
        cursor: null,
        limit: 20,
        total_elements: 2,
        has_more: false,
      });

      const result = await listMessages("tkt-300");

      expect(result.items).toHaveLength(2);
      expect(result.items[0].senderRef).toBe("agent-5");
      expect(result.items[1].body).toBe("Thank you for the update.");
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-300/messages"
      );
    });

    it("returns empty list for ticket with no messages", async () => {
      mockApiClient.get.mockResolvedValue({
        items: [],
        cursor: null,
        limit: 20,
        total_elements: 0,
        has_more: false,
      });

      const result = await listMessages("tkt-empty");

      expect(result.items).toHaveLength(0);
      expect(result.total_elements).toBe(0);
      expect(mockApiClient.get).toHaveBeenCalledWith(
        "/internal/v1/support/tickets/tkt-empty/messages"
      );
    });
  });
});
