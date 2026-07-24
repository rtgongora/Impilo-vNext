import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TelemedicineSessionPage from "./page";

const { push, replace, get, post } = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useParams: () => ({ sessionId: "session-1" }),
  useRouter: () => ({ push, replace }),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: () => ({ user: { id: "user-1", displayName: "Dr. Moyo", email: "moyo@example.com" } }),
}));

vi.mock("@/hooks/queries/useSummary", () => ({
  usePatientSummary: () => ({ data: undefined, isLoading: false, isError: false }),
}));

vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({
  NompiloContextualGuidance: () => <div data-testid="mock-nompilo-guidance" />,
}));

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get,
    post,
  },
}));

vi.mock("@/components/telemedicine/TelemedicineRtcHealthPanel", () => ({
  TelemedicineRtcHealthPanel: () => <div data-testid="telemedicine-rtc-health-panel" />,
}));

vi.mock("@/components/telemedicine/WaitingRoomAdmitControl", () => ({
  WaitingRoomAdmitControl: () => <div data-testid="waiting-room-admit-control" />,
}));

vi.mock("@/components/session/AdaptiveSessionRoom", () => ({
  AdaptiveSessionRoom: () => <div data-testid="adaptive-session-room" />,
}));

vi.mock("@/hooks/queries/useTelemedicine", () => ({
  useTelemedicineSessions: () => ({
    data: {
      data: [
        {
          id: "session-1",
          attributes: {
            encounter_id: "enc-1",
            patient_id: "patient-1",
            provider_id: "provider-1",
            facility_id: "facility-1",
            session_type: "VIDEO",
            status: "RESPONDED",
            room_url: null,
            scheduled_at: "2026-04-08T08:30:00.000Z",
            started_at: "2026-04-08T09:00:00.000Z",
            ended_at: null,
            duration_seconds: null,
            notes: null,
            referral_id: "ref-9",
            created_at: "2026-04-08T08:00:00.000Z",
            updated_at: "2026-04-08T09:00:00.000Z",
          },
        },
      ],
    },
    isLoading: false,
  }),
  useJoinTelemedicineSession: () => ({
    mutate: vi.fn(),
    isPending: false,
    isSuccess: false,
    data: undefined,
  }),
  useEndTelemedicineSession: () => ({
    mutate: vi.fn(),
    isPending: false,
  }),
  useTelemedicineMediaToken: () => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({ data: { room_url: "wss://livekit.test", token: "tok" } }),
    isPending: false,
  }),
  useReferralAllowedActions: () => ({
    data: { data: { referralId: "session-1", status: "RESPONDED", allowedTargets: [] } },
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useReferralLifecycleAction: () => ({ mutate: vi.fn(), isPending: false, isError: false }),
  useReferralTasks: () => ({ data: { data: [] }, isLoading: false, isError: false }),
  useCreateReferralTask: () => ({ mutateAsync: vi.fn(), isPending: false }),
  usePlaceReferralOrder: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

describe("TelemedicineSessionPage", () => {
  beforeEach(() => {
    push.mockReset();
    replace.mockReset();
    get.mockReset();
    post.mockReset();
    get.mockImplementation((path: string) => {
      if (path === "/internal/v1/teleconsult/sessions/session-1") {
        return Promise.resolve({
          data: {
            id: "session-1",
            patientId: "patient-1",
            urgency: "High",
            specialty: "Pulmonology",
            routingType: "REFERRAL",
            stage: 6,
            status: "RESPONDED",
            referralId: "ref-9",
            consentToken: "consent-1",
            createdAt: "2026-04-08T08:00:00.000Z",
            respondedAt: "2026-04-08T09:15:00.000Z",
          },
        });
      }
      if (path === "/internal/v1/teleconsult/sessions/session-1/messages") {
        return Promise.resolve({ data: [] });
      }
      return Promise.resolve({ data: [] });
    });
    post.mockResolvedValue({});
  });

  it("submits the stage 7 completion payload and closes the session", async () => {
    const user = userEvent.setup();

    render(<TelemedicineSessionPage />);

    await user.click(await screen.findByRole("button", { name: "Complete & Close" }));

    expect(screen.getByText("Stage 7 — Completion Note & Loop Closure")).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText("Medications administered, tests done, procedures, monitoring, counseling..."), "Patient improved after nebulisers.");
    await user.selectOptions(screen.getByLabelText("Patient outcome *"), "RETURNED_FOR_REVIEW");
    await user.selectOptions(screen.getByLabelText("Follow-up execution"), "COMPLETED");
    await user.type(screen.getByPlaceholderText("Brief summary of the case and its resolution..."), "Continue inhaled therapy and review tomorrow.");
    await user.click(screen.getByRole("button", { name: "Close Case & Archive" }));

    await waitFor(() => expect(post).toHaveBeenCalled());
    const completionCall = post.mock.calls.find(
      (call) => call[0] === "/internal/v1/teleconsult/sessions/session-1/complete",
    );

    expect(completionCall?.[1]).toEqual({
      actionsTaken: "Patient improved after nebulisers.",
      patientOutcome: "RETURNED_FOR_REVIEW",
      followUpExecution: "COMPLETED",
      closureNarrative: "Continue inhaled therapy and review tomorrow.",
    });

    expect(await screen.findByText("CLOSED")).toBeInTheDocument();
  }, 30000);

  it("keeps the response note in the centre when no call is live", async () => {
    render(<TelemedicineSessionPage />);

    expect(await screen.findByText("Response Note")).toBeInTheDocument();
    expect(screen.queryByTestId("session-video-stage")).not.toBeInTheDocument();
    expect(screen.queryByTestId("right-tab-note")).not.toBeInTheDocument();
    // Waiting-room control is mounted regardless of call state.
    expect(screen.getByTestId("waiting-room-admit-control")).toBeInTheDocument();
  });

  it("puts the video front and centre during a live call and moves the note to a right-side tab", async () => {
    const user = userEvent.setup();
    get.mockImplementation((path: string) => {
      if (path === "/internal/v1/teleconsult/sessions/session-1") {
        return Promise.resolve({
          data: {
            id: "session-1",
            patientId: "patient-1",
            urgency: "Routine",
            specialty: "GENERAL_MEDICINE",
            routingType: "SPECIALTY_POOL",
            stage: 5,
            status: "ACTIVE",
            consentToken: "consent-1",
            createdAt: "2026-07-05T08:00:00.000Z",
          },
        });
      }
      return Promise.resolve({ data: [] });
    });

    render(<TelemedicineSessionPage />);

    // ACTIVE session auto-issues governed media on load → video takes the centre pane.
    expect(await screen.findByTestId("session-video-stage")).toBeInTheDocument();
    expect(screen.getByTestId("adaptive-session-room")).toBeInTheDocument();

    // Response note now lives in the right-side tab (default tab) …
    expect(screen.getByTestId("session-side-panel")).toBeInTheDocument();
    expect(screen.getByTestId("right-tab-note")).toBeInTheDocument();
    expect(screen.getByText("Response Note")).toBeInTheDocument();

    // … alongside the patient info tab.
    await user.click(screen.getByTestId("right-tab-patient"));
    expect(screen.getByText("Referral")).toBeInTheDocument();
    expect(screen.queryByText("Response Note")).not.toBeInTheDocument();
  }, 30000);
});
