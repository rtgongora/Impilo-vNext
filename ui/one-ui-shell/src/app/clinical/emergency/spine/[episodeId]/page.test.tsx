import type { ReactNode } from "react";
import { render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import EmergencyEpisodeSpinePage from "./page";

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
  useAuthStore: () => ({ user: { id: "actor-1", displayName: "Test Clinician" } }),
}));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (state: { facility: { id: string; name: string } | null }) => unknown) =>
    selector({ facility: { id: "facility-1", name: "Test Hospital" } }),
}));

vi.mock("@/hooks/useEmergencyRealtime", () => ({
  useEmergencyRealtime: () => undefined,
}));

vi.mock("@/features/emergency/spine/EpisodeCareActionsPanel", () => ({
  EpisodeCareActionsPanel: () => null,
}));

vi.mock("@/features/emergency/spine/IdentityLinkPanel", () => ({
  IdentityLinkPanel: () => null,
}));

vi.mock("@/features/emergency/spine/EpisodeAlertsPanel", () => ({
  EpisodeAlertsPanel: () => null,
}));

vi.mock("@/features/emergency/spine/OrderSetsAndMedsPanels", () => ({
  MedicationAdminPanel: () => null,
  OrderSetsPanel: () => null,
}));

vi.mock("@/hooks/queries/useMentalHealth", () => ({
  useIntakeMentalHealthReferral: () => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}));

const mockEpisode = vi.fn();
const mockHandovers = vi.fn();
const mockRequestHandover = vi.fn(() => ({ mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false }));
const mockAccept = vi.fn(() => ({ mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false }));
const mockDecline = vi.fn(() => ({ mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false }));

const idleMutation = () => ({ mutate: vi.fn(), mutateAsync: vi.fn(), isPending: false });
const emptyQuery = () => ({ data: [], isLoading: false, isError: false });

vi.mock("@/hooks/queries/useEmergencyEpisode", () => ({
  useEmergencyEpisode: () => mockEpisode(),
  useEmergencyHandoverHistory: () => mockHandovers(),
  useEmergencyEpisodeActions: () => ({
    arrive: idleMutation(),
    transition: idleMutation(),
    confirmLocation: idleMutation(),
    requestHandover: mockRequestHandover(),
  }),
  useEmergencyHandoverActions: () => ({
    accept: mockAccept(),
    decline: mockDecline(),
    expire: idleMutation(),
  }),
  useEmergencyIdentityLinks: () => emptyQuery(),
  useLinkEmergencyIdentity: () => idleMutation(),
  useEmergencyOrderSets: () => emptyQuery(),
  useEmergencyOrderSetActions: () => ({
    invoke: idleMutation(),
    orderItem: idleMutation(),
    declineItem: idleMutation(),
  }),
  useEmergencyMedications: () => emptyQuery(),
  useRecordEmergencyMedication: () => idleMutation(),
  useEmergencyEpisodeAlerts: () => emptyQuery(),
  useEmergencyAlertActions: () => ({
    acknowledge: idleMutation(),
    respond: idleMutation(),
    close: idleMutation(),
  }),
}));

const params = { episodeId: "ep-1" };

describe("EmergencyEpisodeSpinePage", () => {
  it("shows the episode state and entry route once loaded", () => {
    mockEpisode.mockReturnValue({
      data: { episode_id: "ep-1", episode_reference: "EM-1", state: "OPEN_IN_CARE", entry_route: "WALK_IN" },
      isLoading: false,
      isError: false,
    });
    mockHandovers.mockReturnValue({ data: [] });

    render(<EmergencyEpisodeSpinePage params={params} />);

    expect(screen.getByText("In care")).toBeInTheDocument();
    expect(screen.getByText("WALK IN")).toBeInTheDocument();
  });

  it("THE HANDSHAKE INVARIANT: a pending handover shows accept/decline, not a closed state", () => {
    mockEpisode.mockReturnValue({
      data: { episode_id: "ep-1", state: "OPEN_AWAITING_ACCEPTANCE", entry_route: "WALK_IN" },
      isLoading: false,
      isError: false,
    });
    mockHandovers.mockReturnValue({
      data: [{ handover_id: "ho-1", target_type: "ADMISSION", status: "PENDING" }],
    });

    render(<EmergencyEpisodeSpinePage params={params} />);

    expect(screen.getAllByText(/awaiting acceptance/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/Requesting does not close this episode/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Your own record id/i)).toBeInTheDocument();
  });

  it("with no pending handover, offers the request-handover control instead", () => {
    mockEpisode.mockReturnValue({
      data: { episode_id: "ep-1", state: "OPEN_IN_CARE", entry_route: "WALK_IN" },
      isLoading: false,
      isError: false,
    });
    mockHandovers.mockReturnValue({ data: [] });

    render(<EmergencyEpisodeSpinePage params={params} />);

    expect(screen.getByRole("button", { name: /Request handover/i })).toBeInTheDocument();
    expect(screen.queryByPlaceholderText(/Your own record id/i)).not.toBeInTheDocument();
  });

  it("lists prior handovers with their resolved status", () => {
    mockEpisode.mockReturnValue({
      data: { episode_id: "ep-1", state: "CLOSED_HANDED_OVER", entry_route: "WALK_IN" },
      isLoading: false,
      isError: false,
    });
    mockHandovers.mockReturnValue({
      data: [{ handover_id: "ho-1", target_type: "ADMISSION", status: "ACCEPTED" }],
    });

    render(<EmergencyEpisodeSpinePage params={params} />);

    const historySection = screen.getByText("Handover history").closest("div") as HTMLElement;
    expect(within(historySection).getByText("ACCEPTED")).toBeInTheDocument();
  });
});
