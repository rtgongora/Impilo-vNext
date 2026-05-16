import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  createConversation,
  fetchConversations,
  fetchMessages,
  markConversationRead,
  sendMessage,
} from "../src/conversationService";

const getMock = vi.fn();
const postMock = vi.fn();

vi.mock("@impilo/mobile-api-client", () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

describe("conversationService provider messaging routes", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("uses provider messaging family for conversations", async () => {
    getMock.mockResolvedValue({
      data: {
        data: [
          {
            id: "conv-1",
            attributes: {
              subject: "Care Team",
              conversation_type: "GROUP",
              participant_ids: ["provider-1"],
              created_at: "2026-01-01T00:00:00Z",
              updated_at: "2026-01-01T00:00:00Z",
            },
          },
        ],
      },
    });

    const result = await fetchConversations();
    expect(getMock).toHaveBeenCalledWith("/internal/v1/mobile/provider/messaging/conversations");
    expect(result.items[0]?.id).toBe("conv-1");
  });

  it("maps envelope message payloads and uses provider routes", async () => {
    getMock.mockResolvedValue({
      data: {
        data: [
          {
            id: "msg-1",
            attributes: {
              conversation_id: "conv-1",
              sender_id: "provider-1",
              content: "Hello",
              message_type: "TEXT",
              sent_at: "2026-01-01T01:00:00Z",
            },
          },
        ],
      },
    });

    const result = await fetchMessages("conv-1");
    expect(getMock).toHaveBeenCalledWith("/internal/v1/mobile/provider/messaging/conversations/conv-1/messages");
    expect(result.items[0]?.body).toBe("Hello");
  });

  it("sends and marks read with normalized provider payload keys", async () => {
    postMock.mockResolvedValue({
      data: {
        data: {
          id: "msg-2",
          attributes: {
            conversation_id: "conv-1",
            sender_id: "provider-1",
            content: "Ack",
            message_type: "TEXT",
            sent_at: "2026-01-01T01:10:00Z",
          },
        },
      },
    });

    const sent = await sendMessage({ conversationId: "conv-1", body: "Ack" });
    expect(postMock).toHaveBeenCalledWith(
      "/internal/v1/mobile/provider/messaging/conversations/conv-1/messages",
      expect.objectContaining({
        sender_id: "provider-app",
        content: "Ack",
      }),
    );
    expect(sent.body).toBe("Ack");

    await markConversationRead("conv-1", "provider-1");
    expect(postMock).toHaveBeenLastCalledWith(
      "/internal/v1/mobile/provider/messaging/conversations/conv-1/read",
      { participant_id: "provider-1" },
    );
  });

  it("creates conversation with provider payload naming", async () => {
    postMock.mockResolvedValue({
      data: {
        data: {
          id: "conv-2",
          attributes: {
            subject: "Handover",
            conversation_type: "DIRECT",
            participant_ids: ["provider-2"],
            created_at: "2026-01-01T00:00:00Z",
            updated_at: "2026-01-01T00:00:00Z",
          },
        },
      },
    });

    const created = await createConversation({ participantIds: ["provider-2"], subject: "Handover" });
    expect(postMock).toHaveBeenCalledWith(
      "/internal/v1/mobile/provider/messaging/conversations",
      expect.objectContaining({
        participant_ids: ["provider-2"],
      }),
    );
    expect(created.id).toBe("conv-2");
  });
});
