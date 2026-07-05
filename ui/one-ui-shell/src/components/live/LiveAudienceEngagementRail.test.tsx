import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { LiveAudienceEngagementRail } from "./LiveAudienceEngagementRail";
import type { LiveStageRole } from "@/lib/live";

const requestStageMutate = vi.fn();

vi.mock("@/hooks/queries/useLive", () => ({
  useLiveParticipant: () => ({
    participantId: "citizen-9",
    participantType: "CITIZEN",
    role: "ATTENDEE",
    user: null,
  }),
  useLiveChat: () => ({
    data: [
      { id: "m1", message: "hello", participantType: "CITIZEN", kind: "CHAT", status: "VISIBLE" },
      {
        id: "m2",
        message: "Doors open in 5",
        participantType: "PROVIDER",
        kind: "ANNOUNCEMENT",
        status: "VISIBLE",
      },
    ],
  }),
  useLiveQuestions: () => ({ data: [] }),
  useLivePolls: () => ({ data: [] }),
  useLiveResources: () => ({ data: [] }),
  useLiveAnnouncements: () => ({
    data: [
      { id: "m2", message: "Doors open in 5", participantType: "PROVIDER", kind: "ANNOUNCEMENT" },
    ],
  }),
  useLivePostChat: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useLiveSubmitQuestion: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useLiveRespondPoll: () => ({ mutate: vi.fn(), isPending: false }),
  useLiveRequestStage: () => ({ mutate: requestStageMutate, isPending: false }),
}));

function stageRole(overrides: Partial<LiveStageRole> = {}): LiveStageRole {
  return {
    eventId: "evt-1",
    participantId: "citizen-9",
    tier: "AUDIENCE",
    stageManaged: true,
    stageRequest: null,
    ...overrides,
  };
}

describe("LiveAudienceEngagementRail", () => {
  beforeEach(() => {
    requestStageMutate.mockClear();
  });

  it("pins the latest announcement above the tabs", () => {
    render(<LiveAudienceEngagementRail eventId="evt-1" stageRole={stageRole()} />);

    expect(screen.getByTestId("live-announcements")).toHaveTextContent("Doors open in 5");
  });

  it("lets a stage-managed audience member request the stage", () => {
    render(<LiveAudienceEngagementRail eventId="evt-1" stageRole={stageRole()} />);

    const button = screen.getByTestId("request-stage-button");
    fireEvent.click(button);
    expect(requestStageMutate).toHaveBeenCalledWith("evt-1");
  });

  it("shows a pending state instead of the button once requested", () => {
    render(
      <LiveAudienceEngagementRail
        eventId="evt-1"
        stageRole={stageRole({ stageRequest: { id: "r1", status: "REQUESTED" } })}
      />,
    );

    expect(screen.getByTestId("stage-request-pending")).toBeInTheDocument();
    expect(screen.queryByTestId("request-stage-button")).not.toBeInTheDocument();
  });

  it("hides the stage-request flow for publish-granted tiers", () => {
    render(
      <LiveAudienceEngagementRail
        eventId="evt-1"
        stageRole={stageRole({ tier: "SPEAKER" })}
        showStageRequest={false}
      />,
    );

    expect(screen.queryByTestId("request-stage-button")).not.toBeInTheDocument();
  });

  it("renders announcement-kind chat messages with the announcement badge", () => {
    render(<LiveAudienceEngagementRail eventId="evt-1" stageRole={stageRole()} />);

    expect(screen.getAllByText(/Announcement/).length).toBeGreaterThan(0);
    expect(screen.getByText("hello")).toBeInTheDocument();
  });
});
