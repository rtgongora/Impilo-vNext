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
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/hooks/useRoleGroup", () => ({
  useRoleGroup: () => ({ isCitizen: false }),
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

vi.mock("@/lib/api-client", () => ({
  apiClient: {
    get,
    post,
  },
}));

vi.mock("@/hooks/useWebRtcCall", () => ({
  useWebRtcCall: () => ({
    callActive: false,
    callPhase: "idle" as const,
    isVideoCall: false,
    videoEnabled: false,
    audioEnabled: true,
    connectionState: "idle",
    signalingStatus: "disconnected",
    remotePeerId: null,
    error: null,
    localVideoRef: { current: null },
    remoteVideoRef: { current: null },
    startCall: vi.fn(),
    endCall: vi.fn(),
    toggleAudio: vi.fn(),
    toggleVideo: vi.fn(),
  }),
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
});
