import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { LiveBackstageConsole } from "./LiveBackstageConsole";

const approveMutate = vi.fn();
const demoteMutate = vi.fn();
const postAnnouncementMutateAsync = vi.fn().mockResolvedValue({});

let mockedTier = "PRODUCER";

vi.mock("@/components/session/AdaptiveSessionRoom", () => ({
  AdaptiveSessionRoom: () => <div data-testid="adaptive-room" />,
}));

vi.mock("@/components/live/LiveModerationPanel", () => ({
  LiveModerationPanel: ({ eventId }: { eventId: string }) => (
    <div data-testid="moderation-panel">{eventId}</div>
  ),
}));

vi.mock("@/hooks/queries/useLive", () => ({
  useLiveParticipant: () => ({
    participantId: "producer-1",
    participantType: "PROVIDER",
    role: "PRESENTER",
    user: null,
  }),
  useLiveEvent: () => ({ data: { id: "evt-1", title: "National briefing", status: "LIVE" } }),
  useLiveStageRole: () => ({
    data: { eventId: "evt-1", participantId: "producer-1", tier: mockedTier, stageManaged: true },
    isLoading: false,
  }),
  useLiveStageRequests: () => ({
    data: [
      { id: "r1", eventId: "evt-1", participantId: "citizen-9", participantType: "CITIZEN", status: "REQUESTED" },
      { id: "r2", eventId: "evt-1", participantId: "nurse-2", participantType: "PROVIDER", status: "APPROVED" },
    ],
  }),
  useLiveAnnouncements: () => ({ data: [] }),
  useLiveMediaHealth: () => ({
    data: { providerType: "RTC_GATEWAY", mediaHealth: "HEALTHY", eventStatus: "LIVE" },
  }),
  useLiveApproveStage: () => ({ mutate: approveMutate, isPending: false }),
  useLiveDenyStage: () => ({ mutate: vi.fn(), isPending: false }),
  useLiveDemoteParticipant: () => ({ mutate: demoteMutate, isPending: false }),
  useLivePostAnnouncement: () => ({ mutateAsync: postAnnouncementMutateAsync, isPending: false }),
  useLiveCreatePoll: () => ({ mutateAsync: vi.fn().mockResolvedValue({ id: "poll-1" }), isPending: false }),
  useLiveActivatePoll: () => ({ mutateAsync: vi.fn().mockResolvedValue({}), isPending: false }),
  useLiveBackstageJoin: () => ({ mutate: vi.fn(), isPending: false, isSuccess: false }),
  useLiveBackstageToken: () => ({ data: undefined }),
}));

describe("LiveBackstageConsole", () => {
  beforeEach(() => {
    mockedTier = "PRODUCER";
    approveMutate.mockClear();
    demoteMutate.mockClear();
  });

  it("shows the speaker queue and approves a pending request", () => {
    render(<LiveBackstageConsole eventId="evt-1" />);

    expect(screen.getByTestId("speaker-queue")).toBeInTheDocument();
    fireEvent.click(screen.getByTestId("approve-citizen-9"));
    expect(approveMutate).toHaveBeenCalledWith({ eventId: "evt-1", requestId: "r1" });
  });

  it("demotes an on-stage speaker back to the audience", () => {
    render(<LiveBackstageConsole eventId="evt-1" />);

    fireEvent.click(screen.getByTestId("demote-nurse-2"));
    expect(demoteMutate).toHaveBeenCalledWith({ eventId: "evt-1", participantId: "nurse-2" });
  });

  it("renders the honest stream-health placeholder and moderation panel", () => {
    render(<LiveBackstageConsole eventId="evt-1" />);

    expect(screen.getByTestId("stream-health")).toHaveTextContent(/Not configured in this estate/);
    expect(screen.getByTestId("moderation-panel")).toHaveTextContent("evt-1");
  });

  it("locks the console for non-crew tiers", () => {
    mockedTier = "SPEAKER";
    render(<LiveBackstageConsole eventId="evt-1" />);

    expect(screen.getByTestId("backstage-denied")).toBeInTheDocument();
    expect(screen.queryByTestId("speaker-queue")).not.toBeInTheDocument();
  });
});
