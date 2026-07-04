import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MyTelehealthVisitPage from "./page";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("next/navigation", () => ({
  useParams: () => ({ sessionId: "session-1" }),
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
  useAuthStore: (selector?: (state: Record<string, unknown>) => unknown) => {
    const state = { user: { id: "user-1", displayName: "Tatenda Moyo", email: "tatenda@example.com" } };
    return selector ? selector(state) : state;
  },
}));

vi.mock("@/lib/api-client", () => ({ apiClient: { get } }));

vi.mock("@/components/telemedicine/patient/PatientWaitingRoom", () => ({
  PatientWaitingRoom: ({
    onAdmitted,
  }: {
    onAdmitted: (grant: { roomUrl: string; token: string; mediaProfile?: string }) => void;
  }) => (
    <button
      data-testid="mock-waiting-room"
      onClick={() => onAdmitted({ roomUrl: "wss://livekit.test", token: "tok-1" })}
    >
      mock waiting room
    </button>
  ),
}));

vi.mock("@/components/telemedicine/patient/PatientConsultView", () => ({
  PatientConsultView: ({ roomUrl, token, onLeave }: { roomUrl: string; token: string; onLeave: () => void }) => (
    <div data-testid="mock-consult-view">
      <span>{`${roomUrl}|${token}`}</span>
      <button data-testid="mock-leave" onClick={onLeave}>
        leave
      </button>
    </div>
  ),
}));

vi.mock("@/components/telemedicine/patient/PostConsultSummary", () => ({
  PostConsultSummary: () => <div data-testid="mock-post-consult" />,
}));

function mockSession(status: string) {
  get.mockImplementation((path: string) => {
    if (path === "/internal/v1/teleconsult/sessions/session-1") {
      return Promise.resolve({
        data: { id: "session-1", status, specialty: "GENERAL_MEDICINE", consentToken: "consent-1" },
      });
    }
    return Promise.resolve({ data: [] });
  });
}

describe("MyTelehealthVisitPage state machine", () => {
  beforeEach(() => {
    get.mockReset();
  });

  it("starts in the waiting room and flips to the consult when a grant arrives", async () => {
    mockSession("ACTIVE");
    const user = userEvent.setup();

    render(<MyTelehealthVisitPage />);

    await user.click(await screen.findByTestId("mock-waiting-room"));

    expect(screen.getByTestId("mock-consult-view")).toHaveTextContent("wss://livekit.test|tok-1");
    expect(screen.queryByTestId("mock-waiting-room")).not.toBeInTheDocument();
  });

  it("returns to the waiting room with a notice when the person leaves a still-open visit", async () => {
    mockSession("ACTIVE");
    const user = userEvent.setup();

    render(<MyTelehealthVisitPage />);

    await user.click(await screen.findByTestId("mock-waiting-room"));
    await user.click(screen.getByTestId("mock-leave"));

    expect(await screen.findByTestId("mock-waiting-room")).toBeInTheDocument();
    expect(screen.getByTestId("patient-left-notice")).toBeInTheDocument();
  });

  it("shows the post-consult summary for a completed session", async () => {
    mockSession("RESPONDED");

    render(<MyTelehealthVisitPage />);

    expect(await screen.findByTestId("mock-post-consult")).toBeInTheDocument();
    expect(screen.queryByTestId("mock-waiting-room")).not.toBeInTheDocument();
    expect(screen.getByText("Visit summary")).toBeInTheDocument();
  });
});
