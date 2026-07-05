import { render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MeetingAdmission, MeetingDetail, MeetingJoinResponse } from "@/hooks/queries/useMeeting";

/* eslint-disable @typescript-eslint/no-explicit-any */

const routerPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: routerPush, replace: vi.fn() }),
  useParams: () => ({ meetingId: "conv-1" }),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (selector: any) => selector({ user: { id: "provider-a", healthId: "provider-a" } }),
}));

// Keep LiveKit out of jsdom: the shell's media surface renders a structural stand-in that
// still mounts the header/footer/overlay slots (that is what the assertions target).
vi.mock("@/components/session/AdaptiveSessionRoom", () => ({
  AdaptiveSessionRoom: ({ headerSlot, footerSlot, overlaySlot, layout, audience, controls }: any) => (
    <div
      data-testid="adaptive-session-room"
      data-layout={layout}
      data-audience={String(audience)}
      data-controls={JSON.stringify(controls)}
    >
      {headerSlot}
      {footerSlot}
      {overlaySlot}
    </div>
  ),
}));
vi.mock("@/components/session/useSessionRoomState", () => ({
  useSessionRoomState: () => ({ connectionState: "connected", isRecording: false, participantCount: 2, canPublish: true }),
}));
vi.mock("@/lib/realtime/khuluma-socket", () => ({
  subscribeKhulumaRealtime: () => () => undefined,
}));
vi.mock("@/lib/live", () => ({
  liveApi: { startRecording: vi.fn().mockResolvedValue({}) },
}));

const joinState: { value: MeetingJoinResponse | undefined } = { value: undefined };
const detailState: { value: MeetingDetail | undefined } = { value: undefined };
const lobbyState: { value: MeetingAdmission[] } = { value: [] };
const decisionMutate = vi.fn();

vi.mock("@/hooks/queries/useMeeting", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/hooks/queries/useMeeting")>();
  return {
    ...original,
    useMeetingJoin: () => ({ data: joinState.value }),
    useMeetingDetail: () => ({ data: detailState.value }),
    useMeetingLobby: (_id: string | null, enabled: boolean) => ({ data: enabled ? lobbyState.value : [] }),
    useLobbyDecision: () => ({ mutate: decisionMutate, isPending: false }),
    useCohostActions: () => ({ assign: vi.fn(), revoke: vi.fn() }),
    useRaiseHand: () => ({ mutate: vi.fn() }),
    useSendReaction: () => ({ mutate: vi.fn() }),
    useMeetingActionItems: () => ({ data: [] }),
    useActionItemMutations: () => ({ create: { mutate: vi.fn() }, update: { mutate: vi.fn() } }),
  };
});

vi.mock("@/hooks/queries/useComms", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/hooks/queries/useComms")>();
  return {
    ...original,
    useConversation: () => ({
      data: {
        conversationId: "conv-1",
        type: "MEETING",
        title: "Ward huddle",
        topic: null,
        status: "ACTIVE",
        createdBy: "provider-a",
        lastMessagePreview: null,
        lastMessageAt: null,
        participants: [
          { participantId: "p1", actorId: "provider-a", actorType: "PROVIDER", displayName: "Dr A", role: "OWNER", active: true, muted: false },
          { participantId: "p2", actorId: "provider-b", actorType: "PROVIDER", displayName: "Dr B", role: "MEMBER", active: true, muted: false },
        ],
        links: [],
      },
    }),
    useMessages: () => ({ data: [] }),
    useSendMessage: () => ({ mutate: vi.fn() }),
  };
});

import { MeetingRoomShell } from "./MeetingRoomShell";

/* eslint-enable @typescript-eslint/no-explicit-any */

function renderShell() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MeetingRoomShell conversationId="conv-1" />
    </QueryClientProvider>,
  );
}

const READY_JOIN: MeetingJoinResponse = {
  conversationId: "conv-1",
  eventId: "ev-1",
  status: "READY",
  role: "HOST",
  rtcSessionId: "rtc-1",
  mediaAvailable: true,
  roomUrl: "wss://livekit.example",
  accessToken: "host-jwt",
  provider: "livekit",
  error: null,
};

const DETAIL: MeetingDetail = {
  conversationId: "conv-1",
  eventId: "ev-1",
  title: "Ward huddle",
  eventStatus: "LIVE",
  scheduledAt: null,
  endTime: null,
  hostId: "provider-a",
  cohostIds: [],
  attendance: [],
};

describe("MeetingRoomShell", () => {
  beforeEach(() => {
    joinState.value = undefined;
    detailState.value = DETAIL;
    lobbyState.value = [];
    decisionMutate.mockClear();
    routerPush.mockClear();
  });

  it("shows the knock lobby while WAITING (no media surface)", () => {
    joinState.value = { ...READY_JOIN, status: "WAITING", role: "PARTICIPANT", accessToken: null };
    renderShell();
    expect(screen.getByTestId("meeting-lobby-waiting")).toBeInTheDocument();
    expect(screen.queryByTestId("adaptive-session-room")).not.toBeInTheDocument();
  });

  it("shows the denied state when the host refuses entry", () => {
    joinState.value = { ...READY_JOIN, status: "DENIED", accessToken: null };
    renderShell();
    expect(screen.getByTestId("meeting-lobby-denied")).toBeInTheDocument();
  });

  it("READY renders the grid media surface with screen-share for the host", () => {
    joinState.value = READY_JOIN;
    renderShell();
    const room = screen.getByTestId("adaptive-session-room");
    expect(room.getAttribute("data-layout")).toBe("grid");
    expect(room.getAttribute("data-audience")).toBe("false");
    const controls = JSON.parse(room.getAttribute("data-controls") ?? "{}");
    expect(controls.screenShare).toBe(true);
    expect(screen.getByTestId("meeting-title").textContent).toContain("Ward huddle");
    // Host affordances: invite link + recording control.
    expect(screen.getByTestId("invite-link-button")).toBeInTheDocument();
    expect(screen.getByTestId("start-recording")).toBeInTheDocument();
  });

  it("OBSERVER role gets audience mode and no screen share", () => {
    joinState.value = { ...READY_JOIN, role: "OBSERVER" };
    detailState.value = { ...DETAIL, hostId: "someone-else" };
    renderShell();
    const room = screen.getByTestId("adaptive-session-room");
    expect(room.getAttribute("data-audience")).toBe("true");
    const controls = JSON.parse(room.getAttribute("data-controls") ?? "{}");
    expect(controls.screenShare).toBe(false);
    // Non-moderators have no invite/record affordances.
    expect(screen.queryByTestId("invite-link-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("start-recording")).not.toBeInTheDocument();
  });

  it("host sees the admit queue and admitting drives the lobby decision", () => {
    joinState.value = READY_JOIN;
    lobbyState.value = [
      {
        actorId: "provider-b", actorType: "PROVIDER", displayName: "Dr B", role: "PARTICIPANT",
        status: "WAITING", requestedAt: null, decidedBy: null, decidedAt: null,
        deniedReason: null, joinedAt: null, leftAt: null,
      },
    ];
    renderShell();
    expect(screen.getByTestId("admit-queue")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("admit-provider-b"));
    expect(decisionMutate).toHaveBeenCalledWith({ actorId: "provider-b", admit: true });
  });

  it("leave navigates to the meeting summary", () => {
    joinState.value = READY_JOIN;
    renderShell();
    fireEvent.click(screen.getByTestId("leave-meeting"));
    expect(routerPush).toHaveBeenCalledWith("/meet/conv-1/summary");
  });

  it("panel tabs switch between participants, chat and notes", () => {
    joinState.value = READY_JOIN;
    renderShell();
    expect(screen.getByTestId("participants-panel")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("tab-notes"));
    expect(screen.getByTestId("notes-panel")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("tab-chat"));
    expect(screen.getByTestId("meeting-chat-panel")).toBeInTheDocument();
  });
});
