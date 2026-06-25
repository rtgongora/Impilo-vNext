import { describe, it, expect, vi, beforeEach } from "vitest";

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));

vi.mock("@impilo/mobile-api-client", () => ({ apiClient: api }));

import {
  fetchInbox,
  fetchMessages,
  sendMessage,
  markConversationRead,
  startCall,
  acceptCall,
  createMeeting,
  joinMeeting,
} from "../../services/khulumaCommsService";

describe("khulumaCommsService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches the inbox from the mobile khuluma surface", async () => {
    api.get.mockResolvedValue({
      data: [{ conversationId: "c1", type: "DIRECT", title: "Dr B", status: "ACTIVE", lastMessagePreview: "hi", lastMessageAt: null, role: "OWNER", muted: false, unreadCount: 2 }],
    });
    const items = await fetchInbox();
    expect(api.get).toHaveBeenCalledWith("/internal/v1/mobile/khuluma/inbox");
    expect(items).toHaveLength(1);
    expect(items[0].conversationId).toBe("c1");
  });

  it("lists messages for a conversation", async () => {
    api.get.mockResolvedValue({ data: [{ messageId: "m1", conversationId: "c1", senderId: "prov-b", body: "Hello" }] });
    const msgs = await fetchMessages("c1");
    expect(api.get).toHaveBeenCalledWith("/internal/v1/mobile/khuluma/conversations/c1/messages");
    expect(msgs[0].body).toBe("Hello");
  });

  it("sends a message with a TEXT content-type and client id", async () => {
    api.post.mockResolvedValue({ data: { messageId: "m9", conversationId: "c1", body: "On my way" } });
    const msg = await sendMessage("c1", "On my way");
    expect(api.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/khuluma/conversations/c1/messages",
      expect.objectContaining({ body: "On my way", contentType: "TEXT" }),
    );
    expect(msg.messageId).toBe("m9");
  });

  it("marks a conversation read", async () => {
    api.post.mockResolvedValue({ data: {} });
    await markConversationRead("c1", "m1");
    expect(api.post).toHaveBeenCalledWith("/internal/v1/mobile/khuluma/conversations/c1/messages/read", { messageId: "m1" });
  });

  it("starts and accepts a call", async () => {
    api.post.mockResolvedValueOnce({ data: { callId: "call1", status: "RINGING", callType: "AUDIO", initiatedBy: "me", mediaAvailable: false } });
    const call = await startCall("c1", "AUDIO", [{ actorId: "prov-b" }]);
    expect(api.post).toHaveBeenCalledWith("/internal/v1/mobile/khuluma/calls", {
      conversationId: "c1",
      callType: "AUDIO",
      callees: [{ actorId: "prov-b" }],
    });
    expect(call.callId).toBe("call1");

    api.post.mockResolvedValueOnce({ data: { callId: "call1", status: "ACTIVE", callType: "AUDIO", initiatedBy: "me", mediaAvailable: false } });
    const accepted = await acceptCall("call1");
    expect(api.post).toHaveBeenCalledWith("/internal/v1/mobile/khuluma/calls/call1/accept", {});
    expect(accepted.status).toBe("ACTIVE");
  });

  it("creates and joins a virtual meeting", async () => {
    api.post.mockResolvedValueOnce({ data: { conversationId: "m-1", eventId: "ev-1", mediaAvailable: true, roomUrl: "ws://livekit:7880", accessToken: "jwt", provider: "RTC_GATEWAY", error: null } });
    const meeting = await createMeeting("Huddle", [{ actorId: "prov-b" }]);
    expect(api.post).toHaveBeenCalledWith("/internal/v1/mobile/khuluma/meetings", { title: "Huddle", participants: [{ actorId: "prov-b" }] });
    expect(meeting.eventId).toBe("ev-1");

    api.post.mockResolvedValueOnce({ data: { conversationId: "m-1", eventId: "ev-1", mediaAvailable: true, roomUrl: "ws://livekit:7880", accessToken: "jwt2", provider: "RTC_GATEWAY", error: null } });
    const joined = await joinMeeting("m-1");
    expect(api.post).toHaveBeenCalledWith("/internal/v1/mobile/khuluma/conversations/m-1/meeting/join", {});
    expect(joined.accessToken).toBe("jwt2");
  });
});
