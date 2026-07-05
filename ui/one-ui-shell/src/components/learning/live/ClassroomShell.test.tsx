import { act, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ClassroomShell } from "./ClassroomShell";

/** Render + flush the async join effect (joinRoom.mutateAsync → setJoined). */
async function renderShell(sessionId = "sess-1") {
  render(<ClassroomShell sessionId={sessionId} />);
  await act(async () => {});
}

/* eslint-disable @typescript-eslint/no-explicit-any */

const state: {
  session: Record<string, unknown> | undefined;
  sessionLoading: boolean;
  subjectId: string;
  providerId?: string;
  roomToken: { roomUrl: string; accessToken: string } | undefined;
} = {
  session: undefined,
  sessionLoading: false,
  subjectId: "",
  providerId: undefined,
  roomToken: { roomUrl: "wss://livekit.example", accessToken: "scoped" },
};

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/components/session/AdaptiveSessionRoom", () => ({
  AdaptiveSessionRoom: ({ layout, controls }: any) => (
    <div data-testid="adaptive-session-room" data-layout={layout} data-controls={JSON.stringify(controls)} />
  ),
}));

vi.mock("@/hooks/useSessionRealtime", () => ({
  useSessionRealtime: () => undefined,
}));

vi.mock("@/hooks/queries/useLive", () => ({
  useLiveParticipant: () => ({
    user: { providerId: state.providerId, providerActivated: Boolean(state.providerId) },
    participantId: state.providerId ?? state.subjectId,
    participantType: state.providerId ? "PROVIDER" : "CITIZEN",
    role: state.providerId ? "PRESENTER" : "ATTENDEE",
  }),
  useLiveEvent: () => ({ data: { id: "evt-1", title: "TB refresher", status: "LIVE", chatEnabled: true } }),
  useLiveJoinRoom: () => ({ mutateAsync: vi.fn().mockResolvedValue({}), isSuccess: true, isPending: false }),
  useLiveAttendanceLeave: () => ({ mutateAsync: vi.fn().mockResolvedValue({}) }),
  useLiveTrackMinutes: () => ({ mutateAsync: vi.fn().mockResolvedValue({}) }),
  useLiveRoomToken: () => ({ data: state.roomToken, isLoading: false, error: state.roomToken ? null : new Error("no token") }),
  useLiveChat: () => ({ data: [] }),
  useLivePolls: () => ({ data: [] }),
  useLiveResources: () => ({ data: [] }),
  useLivePostChat: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useLiveRespondPoll: () => ({ mutate: vi.fn(), isPending: false }),
  useLiveCreatePoll: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useLiveActivatePoll: () => ({ mutateAsync: vi.fn(), mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/queries/useFundoDelivery", () => ({
  useFundoSession: () => ({ data: state.session, isLoading: state.sessionLoading }),
  useFundoFacilitators: () => ({ data: { data: { items: [{ id: "fac-1", subjectId: "PROV-1" }] } } }),
  useSessionAttendance: () => ({ data: { data: { items: [] } }, refetch: vi.fn(), isFetching: false }),
  useCreateCheckinToken: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/queries/useFundoCatalog", () => ({
  useFundoCourseStructure: () => ({
    data: { data: { structure: { id: "course-1", title: "TB Course", description: "Manage TB cases", modules: [] } } },
  }),
}));

vi.mock("@/components/learning/LearningSubjectPicker", () => ({
  useLearningSubject: () => ({
    subjectType: state.providerId ? "PROVIDER_PUBLIC_ID" : "USER_HEALTH_ID",
    subjectId: state.subjectId,
  }),
}));

/* eslint-enable @typescript-eslint/no-explicit-any */

const LIVE_SESSION = {
  id: "sess-1",
  courseId: "course-1",
  title: "TB refresher class",
  sessionType: "VIRTUAL",
  sessionMode: "LIVE",
  liveEventId: "evt-1",
  facilitator: "PROV-1",
  startsAt: "2099-01-01T09:00:00Z",
};

describe("ClassroomShell", () => {
  beforeEach(() => {
    state.session = { ...LIVE_SESSION };
    state.sessionLoading = false;
    state.subjectId = "";
    state.providerId = undefined;
    state.roomToken = { roomUrl: "wss://livekit.example", accessToken: "scoped" };
  });

  it("renders the facilitator variant when the user is the session facilitator", async () => {
    state.providerId = "PROV-1";
    state.subjectId = "PROV-1";
    await renderShell();

    expect(screen.getByTestId("classroom-shell")).toBeInTheDocument();
    expect(screen.getByTestId("classroom-role")).toHaveTextContent(/facilitator/i);
    expect(screen.getByTestId("facilitator-rail")).toBeInTheDocument();
    expect(screen.getByTestId("facilitator-roster")).toBeInTheDocument();
    expect(screen.queryByTestId("raise-hand-button")).not.toBeInTheDocument();

    const room = screen.getByTestId("adaptive-session-room");
    expect(room.getAttribute("data-layout")).toBe("classroom");
    expect(JSON.parse(room.getAttribute("data-controls") ?? "{}").screenShare).toBe(true);
  });

  it("renders the learner variant with the disabled raise-hand seam", async () => {
    state.subjectId = "USER-9";
    await renderShell();

    expect(screen.getByTestId("classroom-role")).toHaveTextContent(/learner/i);
    expect(screen.getByTestId("learner-rail")).toBeInTheDocument();
    expect(screen.getByTestId("objectives-panel")).toBeInTheDocument();

    const raiseHand = screen.getByTestId("raise-hand-button");
    expect(raiseHand).toBeDisabled();
    expect(raiseHand).toHaveAttribute("title", expect.stringMatching(/realtime/i));

    const room = screen.getByTestId("adaptive-session-room");
    expect(JSON.parse(room.getAttribute("data-controls") ?? "{}").screenShare).toBe(false);
  });

  it("both variants keep the check-in shortcut", async () => {
    state.subjectId = "USER-9";
    await renderShell();
    expect(screen.getByTestId("classroom-checkin-shortcut")).toHaveAttribute(
      "href",
      "/learning/sessions/sess-1/checkin"
    );
  });

  it("shows the honest state when the session has no linked live event", async () => {
    state.session = { ...LIVE_SESSION, liveEventId: undefined, sessionMode: "IN_PERSON", sessionType: "IN_PERSON", metadata: {} };
    state.subjectId = "USER-9";
    await renderShell();
    expect(screen.getByTestId("classroom-no-live-event")).toBeInTheDocument();
    expect(screen.queryByTestId("adaptive-session-room")).not.toBeInTheDocument();
  });

  it("keeps the classroom gated while the media token is unavailable", async () => {
    state.subjectId = "USER-9";
    state.roomToken = undefined;
    await renderShell();
    expect(screen.queryByTestId("adaptive-session-room")).not.toBeInTheDocument();
    expect(screen.getByText(/media is not available yet/i)).toBeInTheDocument();
  });
});
