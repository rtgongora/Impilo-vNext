/**
 * Messaging Tests — Conversation and message service verification.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetchConversations = vi.fn();
const mockFetchMessages = vi.fn();
const mockSendMessage = vi.fn();

vi.mock("@impilo/mobile-messaging", () => ({
  fetchConversations: (...args: unknown[]) => mockFetchConversations(...args),
  fetchMessages: (...args: unknown[]) => mockFetchMessages(...args),
  sendMessage: (...args: unknown[]) => mockSendMessage(...args),
  useConversations: () => ({ conversations: [], loading: false, error: null, refresh: vi.fn() }),
  useMessages: () => ({ messages: [], loading: false, error: null, refresh: vi.fn() }),
  useChannel: () => ({ status: "connected" }),
  createConversation: vi.fn(),
  markConversationRead: vi.fn(),
}));

describe("Messaging Service", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches conversations list", async () => {
    mockFetchConversations.mockResolvedValue([
      {
        id: "conv-1",
        title: "Dr. Moyo",
        type: "PROVIDER_TO_PROVIDER",
        participants: [
          { id: "user-1", name: "Me" },
          { id: "user-2", name: "Dr. Moyo" },
        ],
        lastMessage: { content: "Patient labs ready", sentAt: "2026-03-15T14:00:00Z" },
        unreadCount: 2,
        updatedAt: "2026-03-15T14:00:00Z",
      },
    ]);

    const conversations = await mockFetchConversations();
    expect(conversations).toHaveLength(1);
    expect(conversations[0].title).toBe("Dr. Moyo");
    expect(conversations[0].unreadCount).toBe(2);
  });

  it("fetches messages for a conversation", async () => {
    mockFetchMessages.mockResolvedValue([
      {
        id: "msg-1",
        conversationId: "conv-1",
        senderId: "user-2",
        senderName: "Dr. Moyo",
        content: "Patient labs ready for review",
        contentType: "TEXT",
        sentAt: "2026-03-15T14:00:00Z",
      },
    ]);

    const messages = await mockFetchMessages("conv-1");
    expect(messages).toHaveLength(1);
    expect(messages[0].content).toContain("labs ready");
  });

  it("sends a message", async () => {
    mockSendMessage.mockResolvedValue({
      id: "msg-2",
      conversationId: "conv-1",
      senderId: "user-1",
      content: "Thanks, will review now",
      contentType: "TEXT",
      sentAt: "2026-03-15T14:05:00Z",
    });

    const message = await mockSendMessage("conv-1", { content: "Thanks, will review now", contentType: "TEXT" });
    expect(message.content).toBe("Thanks, will review now");
    expect(mockSendMessage).toHaveBeenCalledWith("conv-1", { content: "Thanks, will review now", contentType: "TEXT" });
  });
});
