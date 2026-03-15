/**
 * Messaging Flow Tests — Conversations, messages, read receipts.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockApiClient = {
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
};

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: mockApiClient,
  fetchPage: vi.fn(),
}));

import {
  fetchConversations,
  fetchConversation,
  createConversation,
  fetchMessages,
  sendMessage,
  markConversationRead,
} from "../../services/messagingService";

describe("Messaging Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches conversations with type filter", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "conv-1",
            subject: "Follow-up question",
            type: "DIRECT",
            status: "ACTIVE",
            updatedAt: "2026-03-15T10:00:00Z",
            unreadCount: 2,
            participants: [],
          },
        ],
        meta: { page: { number: 0, size: 20, total_elements: 1, total_pages: 1 } },
      },
    });

    const result = await fetchConversations({ type: "DIRECT" });
    expect(result.items).toHaveLength(1);
    expect(result.items[0].subject).toBe("Follow-up question");
    expect(result.totalElements).toBe(1);
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/messaging/conversations?type=DIRECT"
    );
  });

  it("fetches a single conversation", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: {
          id: "conv-1",
          subject: "Follow-up question",
          type: "DIRECT",
          status: "ACTIVE",
        },
      },
    });

    const conv = await fetchConversation("conv-1");
    expect(conv.id).toBe("conv-1");
    expect(conv.type).toBe("DIRECT");
  });

  it("creates a new conversation with initial message", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          id: "conv-2",
          subject: "New inquiry",
          type: "SUPPORT",
          createdAt: "2026-03-15T11:00:00Z",
        },
      },
    });

    const conv = await createConversation({
      recipientId: "provider-1",
      subject: "New inquiry",
      type: "SUPPORT",
      initialMessage: "I have a question about my medication",
    });
    expect(conv.id).toBe("conv-2");
    expect(conv.type).toBe("SUPPORT");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/messaging/conversations",
      {
        recipientId: "provider-1",
        subject: "New inquiry",
        type: "SUPPORT",
        initialMessage: "I have a question about my medication",
      }
    );
  });

  it("fetches messages for a conversation", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [
          {
            id: "msg-1",
            conversationId: "conv-1",
            senderId: "provider-1",
            senderName: "Dr. Moyo",
            body: "Your results look good",
            contentType: "TEXT",
            sentAt: "2026-03-15T09:00:00Z",
            status: "SENT",
            readBy: [],
          },
          {
            id: "msg-2",
            conversationId: "conv-1",
            senderId: "citizen-1",
            senderName: "Tatenda",
            body: "Thank you doctor",
            contentType: "TEXT",
            sentAt: "2026-03-15T09:05:00Z",
            status: "SENT",
            readBy: [],
          },
        ],
        meta: { page: { number: 0, size: 50, total_elements: 2, total_pages: 1 } },
      },
    });

    const result = await fetchMessages("conv-1");
    expect(result.items).toHaveLength(2);
    expect(result.items[0].body).toBe("Your results look good");
    expect(result.items[1].body).toBe("Thank you doctor");
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/messaging/conversations/conv-1/messages"
    );
  });

  it("sends a message", async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        data: {
          id: "msg-3",
          conversationId: "conv-1",
          senderId: "citizen-1",
          body: "When should I come for a follow-up?",
          sentAt: "2026-03-15T10:00:00Z",
        },
      },
    });

    const msg = await sendMessage("conv-1", "When should I come for a follow-up?");
    expect(msg.body).toBe("When should I come for a follow-up?");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/messaging/conversations/conv-1/messages",
      { body: "When should I come for a follow-up?", replyTo: undefined }
    );
  });

  it("marks a conversation as read", async () => {
    mockApiClient.post.mockResolvedValue({});

    await markConversationRead("conv-1");
    expect(mockApiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/messaging/conversations/conv-1/read"
    );
  });

  it("fetches conversations without filters returns all", async () => {
    mockApiClient.get.mockResolvedValue({
      data: {
        data: [],
        meta: { page: { number: 0, size: 20, total_elements: 0, total_pages: 0 } },
      },
    });

    const result = await fetchConversations();
    expect(result.items).toHaveLength(0);
    expect(result.totalElements).toBe(0);
    expect(result.hasNext).toBe(false);
    expect(mockApiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/messaging/conversations"
    );
  });
});
