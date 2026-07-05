import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

// onPress→onClick so DOM clicks drive RN TouchableOpacity handlers in jsdom.
vi.mock("react-native", async () => {
  const ReactMod = await import("react");
  const make =
    (tag: string) =>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ({ children, testID, onPress, style: _style, numberOfLines: _n, ...props }: any) => {
      const p: Record<string, unknown> = { ...props };
      if (testID) p["data-testid"] = testID;
      if (onPress) p.onClick = onPress;
      return ReactMod.createElement(tag, p, children);
    };
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const TextInput = ({ testID, onChangeText, value, placeholder, style: _s, ...props }: any) =>
    ReactMod.createElement("input", {
      ...props,
      value,
      placeholder,
      "data-testid": testID,
      onChange: (e: { target: { value: string } }) => onChangeText?.(e.target.value),
    });
  return {
    View: make("div"),
    Text: make("span"),
    ScrollView: make("div"),
    TouchableOpacity: make("button"),
    ActivityIndicator: make("div"),
    TextInput,
    StyleSheet: { create: (s: unknown) => s },
    Platform: { OS: "ios", select: (m: Record<string, unknown>) => m.ios ?? m.default },
  };
});

vi.mock("../../screens/telehealth/LiveKitMobileConsultRoom", () => ({
  LiveKitMobileConsultRoom: () => null,
}));

const svc = vi.hoisted(() => ({
  fetchInbox: vi.fn(),
  fetchConversation: vi.fn(),
  fetchMessages: vi.fn(),
  sendMessage: vi.fn(),
  markConversationRead: vi.fn(),
  startCall: vi.fn(),
  acceptCall: vi.fn(),
  declineCall: vi.fn(),
  endCall: vi.fn(),
  createMeeting: vi.fn(),
  joinMeeting: vi.fn(),
  endMeeting: vi.fn(),
  raiseHand: vi.fn(),
  sendMeetingReaction: vi.fn(),
  resolveMeetingInvite: vi.fn(),
  meetingToCall: vi.fn((m: { conversationId: string; eventId: string | null }) => ({
    callId: m.eventId ?? m.conversationId,
    conversationId: m.conversationId,
    callType: "VIDEO",
    status: "ACTIVE",
    initiatedBy: "",
    roomName: null,
    roomUrl: "ws://livekit:7880",
    provider: "RTC_GATEWAY",
    mediaAvailable: true,
    accessToken: "jwt",
    mediaError: null,
  })),
}));

vi.mock("../../services/khulumaCommsService", () => svc);

// MeetingScreen renders the shared native session room — keep LiveKit out of jsdom.
vi.mock("@impilo/mobile-session", () => ({
  AdaptiveSessionRoomNative: () => null,
}));

import { CommsHubScreen } from "../../screens/comms/CommsHubScreen";

function renderScreen() {
  const container = document.createElement("div");
  const root = createRoot(container);
  act(() => {
    root.render(React.createElement(CommsHubScreen));
  });
  return { container, root };
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

function byTestId(container: HTMLElement, id: string): HTMLElement {
  const el = container.querySelector(`[data-testid="${id}"]`);
  if (!el) throw new Error(`Missing testID: ${id}`);
  return el as HTMLElement;
}

describe("CommsHubScreen", () => {
  let mounted: { container: HTMLElement; root: Root } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    svc.fetchInbox.mockResolvedValue([
      { conversationId: "c1", type: "DIRECT", title: "Dr B", status: "ACTIVE", lastMessagePreview: "hi", lastMessageAt: null, role: "OWNER", muted: false, unreadCount: 2 },
    ]);
    svc.fetchConversation.mockResolvedValue({
      conversationId: "c1", type: "DIRECT", title: "Dr B", topic: null, status: "ACTIVE",
      participants: [{ actorId: "prov-b", actorType: "PROVIDER", displayName: "Dr B", role: "MEMBER", active: true }],
    });
    svc.fetchMessages.mockResolvedValue([
      { messageId: "m1", conversationId: "c1", senderId: "prov-b", senderType: "PROVIDER", senderDisplayName: "Dr B", contentType: "TEXT", body: "Hello there", status: "SENT", sentAt: null },
    ]);
    svc.markConversationRead.mockResolvedValue(undefined);
    svc.sendMessage.mockResolvedValue({ messageId: "m2", conversationId: "c1", senderId: "me", senderType: "CITIZEN", senderDisplayName: null, contentType: "TEXT", body: "On my way", status: "SENT", sentAt: null });
    svc.startCall.mockResolvedValue({ callId: "call1", conversationId: "c1", callType: "VIDEO", status: "RINGING", initiatedBy: "me", roomName: null, roomUrl: null, provider: null, mediaAvailable: false, accessToken: null, mediaError: null });
    const meeting = { conversationId: "m-1", eventId: "ev-1", mediaAvailable: true, roomUrl: "ws://livekit:7880", accessToken: "jwt", provider: "RTC_GATEWAY", error: null };
    svc.createMeeting.mockResolvedValue(meeting);
    svc.joinMeeting.mockResolvedValue(meeting);
  });

  afterEach(() => {
    if (mounted) act(() => mounted?.root.unmount());
    mounted = undefined;
  });

  it("renders the inbox, opens a conversation, marks read and sends a message", async () => {
    mounted = renderScreen();
    await flush();

    // Inbox shows the conversation.
    const convButton = byTestId(mounted.container, "conversation-c1");
    expect(convButton.textContent).toContain("Dr B");
    expect(byTestId(mounted.container, "unread-c1").textContent).toContain("2");

    // Open it.
    act(() => convButton.dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();
    expect(byTestId(mounted.container, "message-list").textContent).toContain("Hello there");
    expect(svc.markConversationRead).toHaveBeenCalledWith("c1", "m1");

    // Type + send.
    const input = byTestId(mounted.container, "message-input") as HTMLInputElement;
    const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")?.set;
    act(() => {
      nativeSetter?.call(input, "On my way");
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await flush();
    act(() => byTestId(mounted!.container, "send-message").dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();
    expect(svc.sendMessage).toHaveBeenCalledWith("c1", "On my way");
  });

  it("starts a video call from a conversation", async () => {
    mounted = renderScreen();
    await flush();
    act(() => byTestId(mounted!.container, "conversation-c1").dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();

    act(() => byTestId(mounted!.container, "start-video").dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();
    expect(svc.startCall).toHaveBeenCalledWith("c1", "VIDEO", expect.any(Array));
  });

  it("creates a virtual meeting and opens the join-capable MeetingScreen (W5)", async () => {
    mounted = renderScreen();
    await flush();
    act(() => byTestId(mounted!.container, "conversation-c1").dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();

    act(() => byTestId(mounted!.container, "start-meeting").dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();
    expect(svc.createMeeting).toHaveBeenCalledWith("Dr B", expect.any(Array));
    // MeetingScreen owns the join (KNOCK lobby): it knocks on mount for the new meeting.
    expect(svc.joinMeeting).toHaveBeenCalledWith("m-1");
    // READY join (token present) → the meeting room surface renders.
    expect(byTestId(mounted.container, "meeting-room")).toBeTruthy();
  });

  it("a WAITING join keeps the participant in the meeting lobby", async () => {
    svc.joinMeeting.mockResolvedValue({
      conversationId: "m-1", eventId: "ev-1", status: "WAITING", role: "PARTICIPANT",
      mediaAvailable: false, roomUrl: null, accessToken: null, provider: null, error: null,
    });
    mounted = renderScreen();
    await flush();
    act(() => byTestId(mounted!.container, "conversation-c1").dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();
    act(() => byTestId(mounted!.container, "start-meeting").dispatchEvent(new MouseEvent("click", { bubbles: true })));
    await flush();
    expect(byTestId(mounted.container, "meeting-waiting")).toBeTruthy();
  });
});
